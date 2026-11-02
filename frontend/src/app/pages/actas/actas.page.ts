import { FileActionsService } from '../../services/file-actions.service';
import { Component, inject, OnInit, signal, computed, ChangeDetectionStrategy, DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { AlertController } from '@ionic/angular/standalone';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  IonIcon, IonButton,
  IonModal, IonHeader, IonToolbar, IonTitle, IonButtons,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import {
  documentTextOutline, downloadOutline, eyeOutline, ellipsisVerticalOutline,
  searchOutline, filterOutline, printOutline, mailOutline, linkOutline,
  chevronBackOutline, chevronForwardOutline, menuOutline, notificationsOutline,
  calendarOutline, timeOutline, locationOutline, peopleOutline,
  checkmarkCircleOutline, clipboardOutline, attachOutline, pencilOutline,
  logOutOutline, closeOutline, addOutline, createOutline, trashOutline,
  qrCodeOutline, gridOutline, barChartOutline, imageOutline, documentOutline,
} from 'ionicons/icons';
import { DashboardSidebarComponent } from '../../components/dashboard-sidebar/dashboard-sidebar.component';
import { DashboardTopbarComponent } from '../../components/dashboard-topbar/dashboard-topbar.component';
import { DashboardService } from '../../services/dashboard.service';
import { AuthService } from '../../services/auth.service';
import { ApiService } from '../../services/api.service';
import { Notification } from '../../models/dashboard.models';
import jsPDF from 'jspdf';
import { Acta, ActaAdjunto } from './actas.models';
import { buildActaPdf } from './acta-pdf.generator';

@Component({
  selector: 'app-actas',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    DashboardSidebarComponent, DashboardTopbarComponent,
    IonIcon, IonButton,
    IonModal, IonHeader, IonToolbar, IonTitle, IonButtons,
  ],
  templateUrl: './actas.page.html',
  styleUrls: ['./actas.page.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ActasPage implements OnInit {
  private readonly fileActions = inject(FileActionsService);
  private readonly dashboardService = inject(DashboardService);
  protected readonly authService = inject(AuthService);

  protected can(accion: string): boolean {
    return this.authService.hasPermission('Actas', accion);
  }
  private readonly apiService = inject(ApiService);
  private readonly alertCtrl = inject(AlertController);
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly menuItems = computed(() => this.dashboardService.getMenuItems());
  protected readonly notifications = signal<Notification[]>([]);

  protected readonly actas = signal<Acta[]>([]);
  protected readonly loading = signal(true);
  protected readonly loadError = signal(false);
  protected readonly detailMode = signal(false);
  protected readonly selectedActa = signal<Acta | null>(null);
  protected readonly activeTab = signal<'Información' | 'Institución Participantes' | 'Acuerdos' | 'Adjuntos' | 'Firmas' | 'Asistencia' | 'Historial'>('Información');

  protected readonly asistentes = signal<any[]>([]);
  protected readonly asistentesLoading = signal(false);
  protected readonly asistentesError = signal(false);

  protected readonly filtroSearch = signal('');
  protected readonly filtroTipo = signal('Todos');
  protected readonly filtroEstado = signal('Todos');
  protected readonly filtroFecha = signal('');

  protected readonly currentPage = signal(1);
  protected get pageSize(): number { return this.authService.preferences().recordsPerPage; }

  /* Context menu (table) */
  protected readonly openMenuId = signal<string | null>(null);
  protected readonly menuPos = signal<{ top: number; left: number } | null>(null);
  protected readonly selectedMenuItem = signal<Acta | null>(null);

  /* Estado modal */
  protected readonly showEstadoModal = signal(false);
  protected readonly nuevoEstado = signal<string>('');

  protected readonly actasFiltradas = computed(() => {
    let items = this.actas();
    const search = this.filtroSearch().toLowerCase();
    if (search) {
      items = items.filter(a =>
        a.titulo.toLowerCase().includes(search) ||
        a.codigo.toLowerCase().includes(search)
      );
    }
    const tipo = this.filtroTipo();
    if (tipo !== 'Todos') {
      items = items.filter(a => a.tipo === tipo);
    }
    const estado = this.filtroEstado();
    if (estado !== 'Todos') {
      items = items.filter(a => a.estado === estado);
    }
    const fecha = this.filtroFecha();
    if (fecha) {
      items = items.filter(a => a.fecha === fecha);
    }
    return items;
  });

  protected readonly paginatedActas = computed(() => {
    const start = (this.currentPage() - 1) * this.pageSize;
    return this.actasFiltradas().slice(start, start + this.pageSize);
  });

  protected readonly totalPages = computed(() =>
    Array.from({ length: Math.ceil(this.actasFiltradas().length / this.pageSize) }, (_, i) => i + 1)
  );

  protected readonly Math = Math;

  protected readonly totalFirmadas = computed(() =>
    this.actas().filter(a => a.estado === 'Firmada' || a.estado === 'Terminada').length
  );
  protected readonly totalPendientes = computed(() =>
    this.actas().filter(a => a.estado === 'Pendiente Firma').length
  );
  protected readonly totalBorradores = computed(() =>
    this.actas().filter(a => a.estado === 'Borrador').length
  );

  private readonly imageCache: Record<string, string> = {};

  constructor() {
    addIcons({
      documentTextOutline, downloadOutline, eyeOutline, ellipsisVerticalOutline,
      searchOutline, filterOutline, printOutline, mailOutline, linkOutline,
      chevronBackOutline, chevronForwardOutline, menuOutline, notificationsOutline,
      calendarOutline, timeOutline, locationOutline, peopleOutline,
      checkmarkCircleOutline, clipboardOutline, attachOutline, pencilOutline,
      logOutOutline, closeOutline, addOutline, createOutline, trashOutline,
      qrCodeOutline, gridOutline, barChartOutline, imageOutline, documentOutline,
    });
  }

  ngOnInit(): void {
    this.detailMode.set(this.route.snapshot.paramMap.has('id'));
    this.loadActas();
    this.dashboardService.getNotificaciones().pipe(takeUntilDestroyed(this.destroyRef)).subscribe(d => this.notifications.set(d));
  }

  private loadActas(): void {
    this.loading.set(true);
    this.loadError.set(false);
    this.apiService.get<any[]>('/api/actas').pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (data) => {
        const mapped: Acta[] = data.map((a: any) => this.mapActa(a));
        this.actas.set(mapped);
        const requestedId = this.route.snapshot.paramMap.get('id') ?? this.route.snapshot.queryParamMap.get('acta');
        const requestedActa = requestedId ? mapped.find(a => a.id === requestedId) : null;
        this.selectedActa.set(this.detailMode() ? (requestedActa ?? mapped[0] ?? null) : null);
        this.loading.set(false);
      },
      error: () => {
        this.actas.set([]);
        this.selectedActa.set(null);
        this.loadError.set(true);
        this.loading.set(false);
      },
    });
  }

  protected retryLoad(): void { this.loadActas(); }

  protected seleccionarActa(acta: Acta): void {
    this.selectedActa.set(acta);
    this.activeTab.set('Información');
    this.asistentes.set([]);
    this.asistentesLoading.set(false);
    this.asistentesError.set(false);
  }

  private loadAsistentes(): void {
    const acta = this.selectedActa();
    if (!acta?.sesion?.codigo) {
      this.asistentes.set([]);
      return;
    }
    this.asistentesLoading.set(true);
    this.asistentesError.set(false);
    this.apiService.get<any>(`/api/asistencia/sesiones/${acta.sesion.codigo}/asistentes`).pipe(
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: (data) => {
        const all = [
          ...(data.registrados || []),
          ...(data.invitados || []),
        ];
        this.asistentes.set(all);
        this.asistentesLoading.set(false);
        this.asistentesError.set(false);
      },
      error: (e) => {
        console.error('[loadAsistentes] Error loading asistentes:', e?.status, e?.error, e);
        this.asistentes.set([]);
        this.asistentesLoading.set(false);
        this.asistentesError.set(true);
      },
    });
  }

  protected cambiarTab(tab: 'Información' | 'Institución Participantes' | 'Acuerdos' | 'Adjuntos' | 'Firmas' | 'Asistencia' | 'Historial'): void {
    this.activeTab.set(tab);
    if (tab === 'Asistencia') {
      this.loadAsistentes();
    }
  }

  protected onMenuToggle(): void {
    this.authService.sidebarCollapsed.update(v => !v);
  }

  protected onLogout(): void {
    this.authService.logout();
  }

  protected limpiarFiltros(): void {
    this.filtroSearch.set('');
    this.filtroTipo.set('Todos');
    this.filtroEstado.set('Todos');
    this.filtroFecha.set('');
    this.currentPage.set(1);
    this.sincronizarSeleccionConFiltros();
  }

  protected sincronizarSeleccionConFiltros(): void {
    if (!this.detailMode()) return;
    const visibles = this.actasFiltradas();
    const seleccionada = this.selectedActa();
    if (!seleccionada || !visibles.some(a => a.id === seleccionada.id)) {
      this.selectedActa.set(visibles[0] ?? null);
      this.activeTab.set('Información');
    }
  }

  protected getEstadoClass(estado: string): string {
    switch (estado) {
      case 'Firmada': return 'estado-firmada';
      case 'Pendiente Firma': return 'estado-pendiente';
      case 'Borrador': return 'estado-borrador';
      case 'Terminada': return 'estado-firmada';
      default: return '';
    }
  }

  /* ── Table context menu ── */

  protected toggleMenu(id: string, event: MouseEvent): void {
    event.stopPropagation();
    if (this.openMenuId() === id) { this.closeMenu(); return; }
    this.openMenuId.set(id);
    const item = this.actas().find(a => a.id === id);
    this.selectedMenuItem.set(item ?? null);
    const btn = event.currentTarget as HTMLElement;
    const rect = btn.getBoundingClientRect();
    const menuWidth = 200; const menuHeight = this.authService.isAdmin() ? 224 : 104; const gap = 8;
    let top = rect.bottom + gap; let left = rect.right - menuWidth;
    if (top + menuHeight > window.innerHeight - gap) { top = rect.top - gap - menuHeight; }
    if (left < gap) { left = gap; }
    if (left + menuWidth > window.innerWidth - gap) { left = window.innerWidth - menuWidth - gap; }
    this.menuPos.set({ top, left });
  }

  protected closeMenu(): void {
    this.openMenuId.set(null);
    this.menuPos.set(null);
    this.selectedMenuItem.set(null);
  }

  /* ── Table button actions ── */

  protected verActa(acta: Acta): void {
    this.router.navigate(['/actas', acta.id]);
  }

  protected volverALista(): void {
    this.router.navigate(['/actas']);
  }

  protected async descargarActa(acta: Acta): Promise<void> {
    if (!this.can('Descargar')) return;
    try {
      const pdf = await this.generarPDF(acta);
      await this.fileActions.save(pdf.output('blob'), `Acta-${acta.codigo}.pdf`);
    } catch {
      await this.mostrarError('No se pudo descargar el acta', 'Ocurrió un error al generar el archivo PDF.');
    }
  }

  private mapActa(a: any): Acta {
    return {
      id: String(a.id),
      codigo: a.codigo || '',
      fecha: a.fecha ? a.fecha.slice(0, 10) : '',
      hora: a.hora || '',
      lugar: a.lugar || '',
      titulo: a.titulo || '',
      tipo: a.tipo || 'Ordinaria',
      estado: a.estado || 'Borrador',
      responsable: a.responsable || '',
      objetivos: a.objetivos || '',
      institucionParticipantes: a.institucionParticipantes || '',
      temas: a.temas || [],
      acuerdos: a.acuerdos || [],
      participantes: (a.participantes || []).map((p: any) => ({
        id: String(p.id),
        nombre: p.nombre || '',
        apellido: p.apellido || '',
        area: p.area || '',
        asistio: p.asistio === true,
        tipo: p.tipo || 'Manual',
        correo: p.correo || '',
        institucion: p.institucion || '',
        cargo: p.cargo || '',
      })),
      acuerdosRel: (a.acuerdosRel || []).map((aa: any) => ({
        id: String(aa.id),
        descripcion: aa.descripcion || '',
        responsable: aa.responsable || '',
        fechaLimite: aa.fechaLimite || '',
        estado: aa.estado || 'Pendiente',
      })),
      adjuntos: (a.adjuntos || []).map((adj: any) => ({
        id: String(adj.id),
        nombreOriginal: adj.nombreOriginal || '',
        nombreArchivo: adj.nombreArchivo || '',
        tipoMime: adj.tipoMime || '',
        tamano: adj.tamano || 0,
      })),
      firmas: (a.firmas || []).map((f: any) => ({
        id: String(f.id),
        nombre: f.nombre || '',
        rol: f.rol || '',
        fecha: f.fecha || '',
      })),
      historial: (a.historial || []).map((h: any) => ({
        id: String(h.id),
        usuario: h.usuario || '',
        accion: h.accion || '',
        detalle: h.detalle || '',
        fecha: h.fecha || '',
      })),
      sesion: a.sesion ? {
        id: String(a.sesion.id),
        codigo: a.sesion.codigo || '',
        nombre: a.sesion.nombre || '',
        fecha: a.sesion.fecha || '',
        tipo: a.sesion.tipo || '',
        activo: a.sesion.activo ?? true,
      } : null,
    };
  }

  /* ── Edit → navigate to /actas/nueva ── */

  protected openEditModal(item: Acta): void {
    if (!this.can('Editar')) return;
    this.closeMenu();
    this.router.navigate(['/actas/nueva'], { queryParams: { id: item.id } });
  }

  /* ── Eliminar ── */

  protected async eliminarActa(id: string): Promise<void> {
    if (!this.can('Eliminar')) return;
    const alert = await this.alertCtrl.create({
      cssClass: 'acta-delete-alert',
      header: 'Eliminar acta',
      message: '¿Está seguro de eliminar esta acta? Esta acción no se puede deshacer.',
      buttons: [
        { text: 'Cancelar', role: 'cancel', cssClass: 'acta-delete-cancel' },
        {
          text: 'Eliminar',
          cssClass: 'acta-delete-confirm',
          handler: () => {
            if (!this.can('Eliminar')) return;
            this.apiService.delete(`/api/actas/${id}`).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
              next: () => {
                this.actas.update(list => list.filter(a => a.id !== id));
                const sel = this.selectedActa();
                if (sel?.id === id) {
                  this.selectedActa.set(this.actas()[0] ?? null);
                }
              },
              error: () => {
                void this.mostrarError('No se pudo eliminar el acta', 'El servidor no completó la operación. Intenta nuevamente.');
              },
            });
          },
        },
      ],
    });
    alert.present();
  }

  /* ── Cambiar Estado ── */

  protected openEstadoModal(itemSeleccionado?: Acta): void {
    if (!this.can('Editar')) return;
    if (itemSeleccionado) {
      this.seleccionarActa(itemSeleccionado);
    }
    const item = itemSeleccionado ?? this.selectedActa();
    if (!item) return;
    this.nuevoEstado.set(item.estado);
    this.showEstadoModal.set(true);
  }

  protected closeEstadoModal(): void {
    this.showEstadoModal.set(false);
  }

  protected guardarCambioEstado(): void {
    if (!this.can('Editar')) return;
    const item = this.selectedActa();
    if (!item) return;
    const nuevoEstado = this.nuevoEstado();
    this.apiService.put<any>(`/api/actas/${item.id}`, { estado: nuevoEstado }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (updated) => {
        const mapped = this.mapActa(updated);
        this.actas.update(list => list.map(a => a.id === item.id ? mapped : a));
        this.selectedActa.set(mapped);
        this.closeEstadoModal();
      },
      error: () => {
        void this.mostrarError('No se pudo cambiar el estado', 'El acta conserva su estado anterior. Intenta nuevamente.');
      },
    });
  }

  private async loadImage(url: string): Promise<string> {
    if (this.imageCache[url]) return this.imageCache[url];
    const response = await fetch(url);
    const blob = await response.blob();
    return new Promise((resolve) => {
      const reader = new FileReader();
      reader.onloadend = () => {
        this.imageCache[url] = reader.result as string;
        resolve(this.imageCache[url]);
      };
      reader.readAsDataURL(blob);
    });
  }

  private async generarPDF(acta: Acta): Promise<jsPDF> {
    const [headerBar, logo, footerBar] = await Promise.all([
      this.loadImage('assets/pdf/header-bar.png'),
      this.loadImage('assets/pdf/logo.png'),
      this.loadImage('assets/pdf/footer-bar.png'),
    ]);
    return buildActaPdf(acta, { headerBar, logo, footerBar });
  }

  /* ── Detail action buttons ── */

  protected ejecutarHerramienta(event: Event): void {
    const select = event.target as HTMLSelectElement;
    const action = select.value;
    select.value = '';
    if (action === 'pdf') void this.descargarPDF();
    else if (action === 'imprimir') void this.imprimir();
    else if (action === 'correo') this.enviarCorreo();
    else if (action === 'enlace') void this.copiarEnlace();
  }

  protected async descargarPDF(): Promise<void> {
    const acta = this.selectedActa();
    if (!acta || !this.can('Descargar')) return;
    try {
      const pdf = await this.generarPDF(acta);
      await this.fileActions.save(pdf.output('blob'), `Acta-${acta.codigo}.pdf`);
    } catch {
      const alert = await this.alertCtrl.create({
        header: 'Error',
        message: 'Ocurrió un error al generar el PDF.',
        buttons: ['OK'],
      });
      alert.present();
    }
  }

  protected enviarCorreo(): void {
    const acta = this.selectedActa();
    if (!acta) return;
    const subject = encodeURIComponent(`Acta: ${acta.titulo}`);
    const body = encodeURIComponent(`Adjunto encontrará el acta ${acta.codigo} - ${acta.titulo}`);
    window.location.href = `mailto:?subject=${subject}&body=${body}`;
  }

  protected async imprimir(): Promise<void> {
    const acta = this.selectedActa();
    if (!acta || !this.can('Descargar')) return;
    try {
      const pdf = await this.generarPDF(acta);
      await this.fileActions.open(pdf.output('blob'), `Acta-${acta.codigo}.pdf`);
    } catch {
      const alert = await this.alertCtrl.create({
        header: 'Error',
        message: 'Ocurrió un error al generar el PDF para impresión.',
        buttons: ['OK'],
      });
      alert.present();
    }
  }

  protected async copiarEnlace(): Promise<void> {
    const acta = this.selectedActa();
    if (!acta) return;
    const url = this.router.serializeUrl(this.router.createUrlTree(['/actas', acta.id]));
    const absoluteUrl = new URL(url, window.location.origin).toString();
    try {
      await navigator.clipboard.writeText(absoluteUrl);
      const alert = await this.alertCtrl.create({
        header: 'Enlace copiado',
        message: 'El enlace del acta se ha copiado al portapapeles.',
        buttons: ['OK'],
      });
      alert.present();
    } catch {
      await this.mostrarError('No se pudo copiar el enlace', 'Copia la dirección directamente desde la barra del navegador.');
    }
  }

  protected descargarAdjunto(acta: Acta, adjunto: ActaAdjunto, abrir = false): void {
    if (!this.can('Descargar')) return;
    this.apiService.getBlob(`/api/actas/${acta.id}/adjuntos/${adjunto.id}/descargar`).pipe(
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: (blob) => {
        if (abrir) void this.fileActions.open(blob, adjunto.nombreOriginal);
        else void this.fileActions.save(blob, adjunto.nombreOriginal);
      },
      error: () => {
        void this.mostrarError('No se pudo abrir el adjunto', 'El archivo no está disponible o no tienes permiso para descargarlo.');
      },
    });
  }

  private async mostrarError(header: string, message: string): Promise<void> {
    const alert = await this.alertCtrl.create({ header, message, buttons: ['Aceptar'] });
    await alert.present();
  }

  protected formatTamano(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  protected getFileIcon(tipoMime: string): string {
    if (tipoMime.includes('pdf')) return 'document-text-outline';
    if (tipoMime.includes('word') || tipoMime.includes('doc')) return 'clipboard-outline';
    if (tipoMime.includes('sheet') || tipoMime.includes('xls')) return 'grid-outline';
    if (tipoMime.includes('presentation') || tipoMime.includes('ppt')) return 'bar-chart-outline';
    if (tipoMime.includes('image')) return 'image-outline';
    return 'document-outline';
  }
}
