import { FileActionsService } from '../../services/file-actions.service';
import { Component, inject, OnInit, OnDestroy, signal, computed, ChangeDetectionStrategy, DestroyRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { IonIcon, IonModal, IonSpinner } from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import {
  menuOutline, notificationsOutline, qrCodeOutline, timeOutline,
  checkmarkCircleOutline, closeCircleOutline, calendarOutline,
  arrowForwardOutline, addOutline, trashOutline, refreshOutline, peopleOutline,
  copyOutline, downloadOutline, printOutline, radioOutline,
  searchOutline, archiveOutline, folderOpenOutline, eyeOutline,
  arrowUndoOutline, chevronDownOutline, closeOutline,
} from 'ionicons/icons';
import { QRCodeComponent } from 'angularx-qrcode';
import { DashboardSidebarComponent } from '../../components/dashboard-sidebar/dashboard-sidebar.component';
import { DashboardTopbarComponent } from '../../components/dashboard-topbar/dashboard-topbar.component';
import { DashboardService } from '../../services/dashboard.service';
import { AuthService } from '../../services/auth.service';
import { AsistenciaService } from '../../services/asistencia.service';
import { RegistroAsistencia } from '../../models/asistencia.models';
import { ApiService } from '../../services/api.service';
import { AlertController } from '@ionic/angular/standalone';
import { ActivatedRoute } from '@angular/router';
import { firstValueFrom, map, switchMap } from 'rxjs';

@Component({
  selector: 'app-asistencia-qr',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    DashboardSidebarComponent, DashboardTopbarComponent,
    IonIcon, IonModal, IonSpinner, QRCodeComponent,
  ],
  templateUrl: './asistencia-qr.page.html',
  styleUrls: ['./asistencia-qr.page.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AsistenciaQrPage implements OnInit, OnDestroy {
  private readonly fileActions = inject(FileActionsService);
  private readonly dashboardService = inject(DashboardService);
  protected readonly authService = inject(AuthService);
  private readonly asistenciaService = inject(AsistenciaService);
  private readonly api = inject(ApiService);
  private readonly alertCtrl = inject(AlertController);
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute);
  private reunionTitulo = '';
  private reunionFecha = '';

  protected readonly menuItems = computed(() => this.dashboardService.getMenuItems());
  protected readonly user = computed(() => this.authService.currentUser());

  protected readonly sesiones = signal<any[]>([]);
  protected readonly sesionesArchivadas = signal<any[]>([]);
  protected readonly sesionesLoading = signal(true);
  protected readonly archivoExpandido = signal(true);
  protected readonly archivoMesExpandido = signal<string | null>(null);
  protected readonly loadError = signal('');
  protected readonly actionError = signal('');
  protected readonly mostrarNuevaSesion = signal(false);
  protected readonly nombreNuevaSesion = signal('');
  protected readonly fechaNuevaSesion = signal('');
  protected readonly creandoSesion = signal(false);
  protected readonly errorNuevaSesion = signal('');
  protected readonly puedeCerrarNuevaSesion = async () => !this.creandoSesion();
  protected readonly selectedSesion = signal<any | null>(null);
  protected readonly historial = signal<RegistroAsistencia[]>([]);

  protected readonly asistentes = signal<any>({ registrados: [], invitados: [], total: 0 });
  protected readonly asistentesLoading = signal(false);
  protected readonly lastUpdated = signal<Date | null>(null);
  protected readonly asistenteSearch = signal('');

  private refreshTimer: ReturnType<typeof setInterval> | null = null;

  protected readonly canCreateSession = computed(() =>
    this.authService.hasPermission('Configuracion QR', 'Crear') ||
    this.authService.hasPermission('Configuracion QR', 'Administrar'));
  protected readonly canDeleteSession = computed(() =>
    this.authService.hasPermission('Configuracion QR', 'Eliminar') ||
    this.authService.hasPermission('Configuracion QR', 'Administrar'));
  protected readonly canCloseSession = computed(() =>
    this.authService.hasPermission('Configuracion QR', 'Editar') ||
    this.authService.hasPermission('Configuracion QR', 'Administrar'));
  protected readonly canArchiveSession = this.canCloseSession;

  protected readonly qrUrl = computed(() => {
    const s = this.selectedSesion();
    if (!s) return '';
    return `${this.api.qrBaseUrl()}/asistencia/publica/${s.codigo}`;
  });

  protected readonly totalAsistentes = computed(() => {
    const a = this.asistentes();
    return (a.registrados?.length || 0) + (a.invitados?.length || 0);
  });

  protected readonly visibleRegistrados = computed(() => this.asistentes().registrados?.filter((r: any) => this.matchesAsistente(this.attendeeName(r))) || []);
  protected readonly visibleInvitados = computed(() => this.asistentes().invitados?.filter((r: any) => this.matchesAsistente(r.nombre || '')) || []);
  protected readonly sesionesArchivadasPorMes = computed(() => {
    const agrupadas = new Map<string, any[]>();
    for (const sesion of this.sesionesArchivadas()) {
      const mes = /^\d{4}-\d{2}/.test(sesion.fecha || '') ? sesion.fecha.slice(0, 7) : 'sin-fecha';
      agrupadas.set(mes, [...(agrupadas.get(mes) || []), sesion]);
    }
    return Array.from(agrupadas.entries())
      .sort(([mesA], [mesB]) => mesB.localeCompare(mesA))
      .map(([mes, sesiones]) => ({ mes, etiqueta: this.formatearMesArchivo(mes), sesiones }));
  });

  constructor() {
    addIcons({
      menuOutline, notificationsOutline, qrCodeOutline, timeOutline,
      checkmarkCircleOutline, closeCircleOutline, calendarOutline,
      arrowForwardOutline, addOutline, trashOutline, refreshOutline, peopleOutline,
      copyOutline, downloadOutline, printOutline, radioOutline,
      searchOutline, archiveOutline, folderOpenOutline, eyeOutline,
      arrowUndoOutline, chevronDownOutline, closeOutline,
    });
  }

  ngOnInit(): void {
    this.reunionTitulo = this.route.snapshot.queryParamMap.get('reunionTitulo') || '';
    this.reunionFecha = (this.route.snapshot.queryParamMap.get('reunionFecha') || '').slice(0, 10);
    this.cargarSesiones();
    this.cargarHistorial();
  }

  ngOnDestroy(): void {
    this.detenerAutoRefresh();
  }

  ionViewWillEnter(): void {
    if (this.selectedSesion() && this.authService.isAuthenticated()) {
      this.cargarAsistentes();
      if (!this.selectedSesion()?.archivado) this.iniciarAutoRefresh();
    }
  }

  ionViewDidLeave(): void {
    this.detenerAutoRefresh();
  }

  protected cargarSesiones(): void {
    this.sesionesLoading.set(true);
    this.loadError.set('');
    this.api.get<any[]>('/api/asistencia/sesiones').pipe(
      switchMap(vigentes => this.api.get<any[]>('/api/asistencia/sesiones?archivadas=true').pipe(
        map(archivadas => ({ vigentes, archivadas })),
      )),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: ({ vigentes, archivadas }) => {
        this.sesiones.set(vigentes);
        this.sesionesArchivadas.set(archivadas);
        this.sesionesLoading.set(false);
        const seleccionada = this.selectedSesion();
        const seleccionActualizada = seleccionada
          ? [...vigentes, ...archivadas].find(s => s.id === seleccionada.id)
          : null;
        if (seleccionActualizada) {
          this.selectSesion(seleccionActualizada);
        } else if (vigentes.length > 0) {
          const relacionada = this.reunionTitulo
            ? vigentes.find(s => s.nombre === this.reunionTitulo && (!this.reunionFecha || s.fecha === this.reunionFecha))
            : null;
          this.selectSesion(relacionada || vigentes.find(s => s.activo) || vigentes[0]);
        } else if (archivadas.length > 0 && seleccionada) {
          this.selectSesion(archivadas[0]);
        } else {
          this.selectSesion(null);
        }
      },
      error: (error) => {
        this.sesiones.set([]);
        this.sesionesArchivadas.set([]);
        this.sesionesLoading.set(false);
        this.loadError.set(error?.status === 403
          ? 'Tu cuenta no tiene permiso para consultar las sesiones QR.'
          : 'No fue posible cargar las sesiones QR. Verifica la conexión e inténtalo nuevamente.');
      },
    });
  }

  private cargarHistorial(): void {
    this.asistenciaService.getHistorial()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(h => this.historial.set(h));
  }

  protected selectSesion(s: any): void {
    this.detenerAutoRefresh();
    this.selectedSesion.set(s);
    if (s) {
      this.cargarAsistentes();
      if (!s.archivado) this.iniciarAutoRefresh();
    } else {
      this.asistentes.set({ registrados: [], invitados: [], total: 0 });
    }
  }

  protected cargarAsistentes(): void {
    const s = this.selectedSesion();
    if (!s || !this.authService.isAuthenticated()) return;
    this.asistentesLoading.set(true);
    this.asistenciaService.getAsistentesPorSesion(s.codigo)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => {
          this.asistentes.set(data);
          this.lastUpdated.set(new Date());
          this.asistentesLoading.set(false);
        },
        error: () => {
          this.asistentes.set({ registrados: [], invitados: [], total: 0 });
          this.asistentesLoading.set(false);
        },
      });
  }

  private iniciarAutoRefresh(): void {
    this.detenerAutoRefresh();
    this.refreshTimer = setInterval(() => {
      if (this.authService.isAuthenticated()) this.cargarAsistentes();
      else this.detenerAutoRefresh();
    }, 30_000);
  }

  private detenerAutoRefresh(): void {
    if (this.refreshTimer) {
      clearInterval(this.refreshTimer);
      this.refreshTimer = null;
    }
  }

  protected crearSesion(): void {
    if (!this.canCreateSession() || this.creandoSesion()) return;
    this.actionError.set('');
    this.errorNuevaSesion.set('');
    this.nombreNuevaSesion.set(this.reunionTitulo);
    const ahora = new Date();
    const fechaLocal = new Date(ahora.getTime() - ahora.getTimezoneOffset() * 60_000).toISOString().slice(0, 10);
    this.fechaNuevaSesion.set(this.reunionFecha || fechaLocal);
    this.mostrarNuevaSesion.set(true);
  }

  protected cerrarNuevaSesion(): void {
    if (this.creandoSesion()) return;
    this.mostrarNuevaSesion.set(false);
  }

  protected async guardarNuevaSesion(): Promise<void> {
    const nombre = this.nombreNuevaSesion().trim();
    const fecha = this.fechaNuevaSesion();
    if (!this.canCreateSession() || this.creandoSesion() || !nombre || !fecha) return;
    this.errorNuevaSesion.set('');
    this.creandoSesion.set(true);
    try {
      await firstValueFrom(this.api.post('/api/asistencia/sesiones', { nombre, fecha, tipo: 'reunion' }));
      this.mostrarNuevaSesion.set(false);
      this.cargarSesiones();
    } catch {
      this.errorNuevaSesion.set('No fue posible crear la sesión. Revisa los datos o tus permisos e inténtalo nuevamente.');
    } finally {
      this.creandoSesion.set(false);
    }
  }

  protected async eliminarSesion(s: any): Promise<void> {
    if (!this.canDeleteSession()) return;
    this.actionError.set('');
    const alert = await this.alertCtrl.create({
      header: 'Eliminar sesión',
      message: `¿Eliminar "${s.nombre}"?`,
      buttons: [
        { text: 'Cancelar', role: 'cancel' },
        {
          text: 'Eliminar',
          role: 'destructive',
          handler: async () => {
            try {
              await this.api.delete<any>(`/api/asistencia/sesiones/${s.id}`).toPromise();
              if (this.selectedSesion()?.id === s.id) this.selectSesion(null);
              this.cargarSesiones();
            } catch (e: any) {
              console.error('Error eliminando sesión:', e);
              if (e?.status === 409 && e?.error?.message) {
                this.actionError.set(e.error.message);
              } else if (e?.status === 403) {
                this.actionError.set('No tienes permiso para eliminar esta sesión.');
              } else {
                this.actionError.set('No fue posible eliminar la sesión. Inténtalo nuevamente.');
              }
            }
          },
        },
      ],
    });
    await alert.present();
  }

  protected async cerrarSesion(s: any): Promise<void> {
    if (!this.canCloseSession() || !s?.activo) return;
    this.actionError.set('');
    const alert = await this.alertCtrl.create({
      header: 'Cerrar sesión',
      message: `¿Cerrar "${s.nombre}"? El código QR dejará de aceptar nuevos registros, pero la asistencia existente se conservará.`,
      buttons: [
        { text: 'Cancelar', role: 'cancel' },
        {
          text: 'Cerrar sesión',
          role: 'destructive',
          handler: async () => {
            try {
              const cerrada = await this.api.patch<any>(`/api/asistencia/sesiones/${s.id}/cerrar`, {}).toPromise();
              if (cerrada) {
                this.sesiones.update(items => items.map(item => item.id === s.id ? { ...item, ...cerrada } : item));
                if (this.selectedSesion()?.id === s.id) this.selectSesion({ ...s, ...cerrada });
              }
            } catch (e: any) {
              console.error('Error cerrando sesión:', e);
              this.actionError.set(e?.status === 403
                ? 'No tienes permiso para cerrar esta sesión.'
                : 'No fue posible cerrar la sesión. Inténtalo nuevamente.');
            }
          },
        },
      ],
    });
    await alert.present();
  }

  protected async archivarSesion(s: any): Promise<void> {
    if (!this.canArchiveSession() || !s || s.archivado) return;
    this.actionError.set('');
    const alert = await this.alertCtrl.create({
      header: 'Archivar sesión QR',
      message: `¿Archivar "${s.nombre}"? El QR quedará cerrado y la asistencia se conservará en el archivo.`,
      buttons: [
        { text: 'Cancelar', role: 'cancel' },
        {
          text: 'Archivar',
          handler: async () => {
            try {
              const archivada = await this.api.patch<any>(`/api/asistencia/sesiones/${s.id}/archivar`, {}).toPromise();
              if (!archivada) return;
              this.sesiones.update(items => items.filter(item => item.id !== s.id));
              this.sesionesArchivadas.update(items => [archivada, ...items.filter(item => item.id !== s.id)]);
              this.archivoExpandido.set(true);
              this.archivoMesExpandido.set(/^\d{4}-\d{2}/.test(archivada.fecha || '') ? archivada.fecha.slice(0, 7) : 'sin-fecha');
              this.selectSesion(archivada);
            } catch (e: any) {
              console.error('Error archivando sesión:', e);
              this.actionError.set(e?.status === 403
                ? 'No tienes permiso para archivar esta sesión.'
                : 'No fue posible archivar la sesión. Inténtalo nuevamente.');
            }
          },
        },
      ],
    });
    await alert.present();
  }

  protected async restaurarSesion(s: any): Promise<void> {
    if (!this.canArchiveSession() || !this.puedeRestaurar(s)) return;
    this.actionError.set('');
    try {
      const restaurada = await this.api.patch<any>(`/api/asistencia/sesiones/${s.id}/restaurar`, {}).toPromise();
      if (!restaurada) return;
      this.sesionesArchivadas.update(items => items.filter(item => item.id !== s.id));
      this.sesiones.update(items => [restaurada, ...items.filter(item => item.id !== s.id)]);
      this.selectSesion(restaurada);
    } catch (e: any) {
      console.error('Error restaurando sesión:', e);
      this.actionError.set(e?.error?.message || (e?.status === 403
        ? 'No tienes permiso para restaurar esta sesión.'
        : 'No fue posible restaurar la sesión. Inténtalo nuevamente.'));
    }
  }

  protected puedeRestaurar(s: any): boolean {
    return this.canArchiveSession() && !this.esSesionAntigua(s);
  }

  protected esSesionAntigua(s: any): boolean {
    const ahora = new Date();
    const fechaLocal = new Date(ahora.getTime() - ahora.getTimezoneOffset() * 60_000).toISOString().slice(0, 10);
    return !s?.fecha || s.fecha < fechaLocal;
  }

  protected verSesionArchivada(s: any): void {
    this.selectSesion(s);
    document.querySelector('.page-header')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  protected toggleMesArchivo(mes: string): void {
    this.archivoMesExpandido.update(actual => actual === mes ? null : mes);
  }

  private formatearMesArchivo(mes: string): string {
    if (mes === 'sin-fecha') return 'Sin fecha registrada';
    const valor = new Date(`${mes}-01T00:00:00`);
    if (Number.isNaN(valor.getTime())) return mes;
    const etiqueta = valor.toLocaleDateString('es-PA', {
      month: 'long', year: 'numeric',
    });
    return etiqueta.charAt(0).toUpperCase() + etiqueta.slice(1);
  }

  protected onMenuToggle(): void {
    this.authService.sidebarCollapsed.update(v => !v);
  }

  protected onLogout(): void {
    this.authService.logout();
  }

  protected attendeeName(attendee: any): string {
    const user = attendee?.usuario;
    return [user?.nombre || attendee?.nombre || user?.email || attendee?.email || 'Usuario registrado', user?.apellido || ''].filter(Boolean).join(' ');
  }

  protected copyQrUrl(): void { navigator.clipboard?.writeText(this.qrUrl()); }

  protected downloadQr(): void {
    const canvas = document.querySelector('.qr-code canvas') as HTMLCanvasElement | null;
    const image = document.querySelector('.qr-code img') as HTMLImageElement | null;
    const url = canvas?.toDataURL('image/png') || image?.src;
    if (!url) return;
    void this.fileActions.saveLocalUrl(url, `qr-${this.selectedSesion()?.codigo || 'asistencia'}.png`);
  }

  protected printQr(): void {
    const session = this.selectedSesion();
    const canvas = document.querySelector('.qr-code canvas') as HTMLCanvasElement | null;
    const image = document.querySelector('.qr-code img') as HTMLImageElement | null;
    const qrImage = canvas?.toDataURL('image/png') || image?.src;

    if (!session || !qrImage) {
      this.actionError.set('No fue posible preparar el código QR para impresión. Inténtalo nuevamente.');
      return;
    }

    if (this.fileActions.native) { void this.fileActions.saveLocalUrl(qrImage, `QR-${session.codigo}.png`); return; }
    const printWindow = window.open('', '_blank', 'width=640,height=820');
    if (!printWindow) {
      this.actionError.set('El navegador bloqueó la ventana de impresión. Habilita las ventanas emergentes e inténtalo nuevamente.');
      return;
    }

    const sessionDetails = [session.fecha, session.horaInicio && session.horaFin ? `${session.horaInicio} – ${session.horaFin}` : '', session.tipo]
      .filter(Boolean)
      .join(' · ');
    const escapeHtml = (value: unknown) => String(value ?? '').replace(/[&<>'"]/g, character => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;',
    }[character]!));

    printWindow.document.title = `QR ${session.codigo}`;
    printWindow.document.write(`<!doctype html>
      <html lang="es">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>QR ${escapeHtml(session.codigo)}</title>
          <style>
            @page { size: A4 portrait; margin: 16mm; }
            * { box-sizing: border-box; }
            body { margin: 0; color: #10233f; background: #fff; font-family: Arial, Helvetica, sans-serif; }
            .sheet { max-width: 520px; margin: 0 auto; border: 1px solid #d8e3f0; border-radius: 18px; overflow: hidden; text-align: center; }
            .header { padding: 26px 30px 22px; color: #fff; background: #0f3f78; }
            .brand { margin: 0 0 7px; font-size: 13px; font-weight: 700; letter-spacing: .13em; }
            h1 { margin: 0; font-size: 26px; line-height: 1.2; }
            .content { padding: 30px; }
            .details { margin: 9px 0 24px; color: #526a88; font-size: 14px; line-height: 1.5; }
            .qr { display: block; width: 280px; height: 280px; margin: 0 auto 22px; image-rendering: pixelated; }
            .code { display: inline-block; padding: 7px 12px; border-radius: 999px; color: #0f3f78; background: #eaf2fc; font: 700 15px/1.2 monospace; letter-spacing: .08em; }
            .instruction { margin: 24px 0 0; padding-top: 20px; border-top: 1px solid #e3ebf5; color: #334e70; font-size: 15px; line-height: 1.55; }
            .url { overflow-wrap: anywhere; margin: 10px 0 0; color: #6b7f99; font-size: 10px; line-height: 1.45; }
            .footer { padding: 13px 20px; color: #6b7f99; background: #f7faff; font-size: 11px; }
          </style>
        </head>
        <body>
          <main class="sheet">
            <header class="header">
              <p class="brand">SIGAA · GESTIÓN INSTITUCIONAL</p>
              <h1>Registro de asistencia</h1>
            </header>
            <section class="content">
              <h2>${escapeHtml(session.nombre || 'Sesión de asistencia')}</h2>
              ${sessionDetails ? `<p class="details">${escapeHtml(sessionDetails)}</p>` : ''}
              <img class="qr" src="${escapeHtml(qrImage)}" alt="Código QR para registrar asistencia">
              <div class="code">${escapeHtml(session.codigo)}</div>
              <p class="instruction">Escanea este código con la cámara de tu dispositivo para registrar tu asistencia.</p>
              <p class="url">${escapeHtml(this.qrUrl())}</p>
            </section>
            <footer class="footer">Generado por SIGAA</footer>
          </main>
        </body>
      </html>`);
    printWindow.document.close();
    printWindow.onafterprint = () => printWindow.close();
    window.setTimeout(() => {
      printWindow.focus();
      printWindow.print();
    }, 250);
  }

  protected formatLastUpdated(): string { return this.lastUpdated()?.toLocaleTimeString('es-PA', { hour: '2-digit', minute: '2-digit', second: '2-digit' }) || 'Pendiente'; }

  private matchesAsistente(value: string): boolean { return value.toLowerCase().includes(this.asistenteSearch().trim().toLowerCase()); }
}
