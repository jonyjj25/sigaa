import { FileActionsService } from '../../services/file-actions.service';
import { Component, inject, OnInit, signal, computed, ChangeDetectionStrategy, DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { lastValueFrom, takeUntil } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  IonIcon, IonButton, AlertController,
  IonModal, IonHeader, IonToolbar, IonTitle, IonButtons,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import {
  barChartOutline, downloadOutline, shareOutline, eyeOutline,
  addOutline, searchOutline, filterOutline,
  createOutline, trashOutline, closeOutline,
  ellipsisVerticalOutline, menuOutline, notificationsOutline, logOutOutline,
  chevronBackOutline, chevronForwardOutline, documentTextOutline,
  folderOutline, timeOutline, calendarOutline, peopleOutline,
  gridOutline, pricetagOutline, pencilOutline, documentAttachOutline,
  shieldCheckmarkOutline, checkboxOutline, arrowForwardOutline, hourglassOutline,
} from 'ionicons/icons';
import { DashboardSidebarComponent } from '../../components/dashboard-sidebar/dashboard-sidebar.component';
import { DashboardTopbarComponent } from '../../components/dashboard-topbar/dashboard-topbar.component';
import { DashboardService } from '../../services/dashboard.service';
import { AuthService } from '../../services/auth.service';
import { ApiService } from '../../services/api.service';
import { Notification } from '../../models/dashboard.models';
import { ReporteItem, ReporteModulo, ReportePlantilla } from './reportes.models';

interface ReporteProgramacion {
  id: number;
  nombre: string;
  modulo: string;
  periodicidad: 'diario' | 'semanal' | 'mensual';
  activo: boolean;
  proximaEjecucion: string;
  ultimaEjecucion?: string;
  ultimoEstado: string;
  ultimoError?: string;
}

interface UsuarioDestinatario {
  id: number;
  nombre: string;
  correo: string;
}

@Component({
  selector: 'app-reportes',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    DashboardSidebarComponent, DashboardTopbarComponent,
    IonIcon, IonButton,
    IonModal, IonHeader, IonToolbar, IonTitle, IonButtons,
  ],
  templateUrl: './reportes.page.html',
  styleUrls: ['./reportes.page.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReportesPage implements OnInit {
  private readonly fileActions = inject(FileActionsService);
  private readonly dashboardService = inject(DashboardService);
  protected readonly authService = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly alertCtrl = inject(AlertController);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly menuItems = computed(() => this.dashboardService.getMenuItems());
  protected readonly notifications = signal<Notification[]>([]);

  protected readonly errorReportes = signal(false);
  protected readonly errorPlantillas = signal(false);
  protected readonly errorProgramaciones = signal(false);
  protected readonly guardandoReporte = signal(false);
  protected readonly guardandoProgramacion = signal(false);
  private reporteCreado: { id: string; datos: string } | null = null;
  private programacionCreada: { id: string; datos: string } | null = null;
  protected readonly reportes = signal<ReporteItem[]>([]);
  protected readonly programaciones = signal<ReporteProgramacion[]>([]);
  protected readonly plantillas = signal<ReportePlantilla[]>([]);
  protected readonly vistaCentro = signal<'plantillas' | 'historial' | 'programaciones'>('plantillas');
  protected readonly selectedReporte = signal<ReporteItem | null>(null);
  protected readonly filtroSearch = signal('');
  protected readonly filtroModulo = signal('Todos');
  protected readonly filtroTipo = signal('Todos');
  protected readonly filtroFormato = signal('Todos');

  protected readonly openMenuId = signal<string | null>(null);
  protected readonly showPlantillaModal = signal(false);
  protected readonly plantillaSeleccionada = signal<ReportePlantilla | null>(null);
  protected readonly plantillaDesde = signal('');
  protected readonly plantillaHasta = signal('');
  protected readonly plantillaFormato = signal<'PDF' | 'CSV'>('PDF');
  protected readonly generandoPlantilla = signal(false);

  /* Edit mode */
  protected readonly editandoId = signal<string | null>(null);

  /* Compartir modal */
  protected readonly showCompartirModal = signal(false);
  protected readonly compartirEmail = signal('');
  protected readonly compartirItem = signal<ReporteItem | null>(null);

  protected readonly currentPage = signal(1);
  protected get pageSize(): number { return this.authService.preferences().recordsPerPage; }

  protected readonly reportesFiltrados = computed(() => {
    let items = this.reportes();
    const search = this.filtroSearch().toLowerCase().trim();
    const fMod = this.filtroModulo();
    const fTipo = this.filtroTipo();
    const fFmt = this.filtroFormato();

    if (search) items = items.filter(r => r.nombre.toLowerCase().includes(search));
    if (fMod !== 'Todos') items = items.filter(r => r.modulo === fMod);
    if (fTipo !== 'Todos') items = items.filter(r => this.tipoLabel(r.tipo) === fTipo);
    if (fFmt !== 'Todos') items = items.filter(r => r.formato === fFmt);
    return items;
  });

  protected readonly totalCount = computed(() => this.reportesFiltrados().length);

  protected readonly paginatedReportes = computed(() => {
    const start = (this.currentPage() - 1) * this.pageSize;
    return this.reportesFiltrados().slice(start, start + this.pageSize);
  });

  protected readonly totalPages = computed(() =>
    Array.from({ length: Math.ceil(this.reportesFiltrados().length / this.pageSize) }, (_, i) => i + 1)
  );

  protected readonly Math = Math;

  /* KPI */
  protected readonly totalReportes = computed(() => this.reportes().length);

  constructor() {
    addIcons({
      barChartOutline, downloadOutline, shareOutline, eyeOutline,
      addOutline, searchOutline, filterOutline,
      createOutline, trashOutline, closeOutline,
      ellipsisVerticalOutline, menuOutline, notificationsOutline, logOutOutline,
      chevronBackOutline, chevronForwardOutline, documentTextOutline,
      folderOutline, timeOutline, calendarOutline, peopleOutline,
      gridOutline, pricetagOutline, pencilOutline, documentAttachOutline,
      shieldCheckmarkOutline, checkboxOutline, arrowForwardOutline, hourglassOutline,
    });
  }

  private sessionRevision = 0;
  private operacionActual(): () => boolean {
    const revision = this.sessionRevision, usuarioId = this.authService.currentUser()?.id;
    return () => revision === this.sessionRevision && usuarioId === this.authService.currentUser()?.id;
  }

  ngOnInit(): void {
    this.authService.sessionChanged$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.sessionRevision++;
      this.reportes.set([]); this.plantillas.set([]); this.programaciones.set([]); this.selectedReporte.set(null);
      this.reporteCreado = null; this.programacionCreada = null;
      this.showGenerarModal.set(false); this.showProgramarModal.set(false); this.showPlantillaModal.set(false);
      this.showCompartirModal.set(false); this.compartirItem.set(null); this.compartirEmail.set('');
      this.genArchivos.set([]); this.progArchivos.set([]); this.usuariosDestinatarios.set([]);
      this.guardandoReporte.set(false); this.guardandoProgramacion.set(false); this.generandoPlantilla.set(false);
      if (this.authService.currentUser()?.id && this.authService.hasPermission('Reporte', 'Ver')) {
        this.loadReportes(); this.loadPlantillas(); this.loadProgramaciones();
      }
    });
    this.loadReportes();
    this.loadPlantillas();
    this.loadProgramaciones();
    this.dashboardService.getNotificaciones().pipe(takeUntilDestroyed(this.destroyRef)).subscribe(d => this.notifications.set(d));
  }

  protected loadReportes(): void {
    this.errorReportes.set(false);
    this.api.get<ReporteItem[]>('/api/reportes').pipe(takeUntilDestroyed(this.destroyRef), takeUntil(this.authService.sessionChanged$)).subscribe({
      next: (data) => this.reportes.set(data),
      error: () => this.errorReportes.set(true),
    });
  }

  protected puedeProgramar(): boolean {
    return this.authService.isAdmin() && this.authService.hasPermission('Reporte', 'Configurar');
  }

  protected loadProgramaciones(): void {
    this.errorProgramaciones.set(false);
    if (!this.puedeProgramar()) return;
    this.api.get<ReporteProgramacion[]>('/api/reportes/programaciones').pipe(takeUntilDestroyed(this.destroyRef), takeUntil(this.authService.sessionChanged$)).subscribe({
      next: (data) => this.programaciones.set(data),
      error: () => this.errorProgramaciones.set(true),
    });
  }

  protected loadPlantillas(): void {
    this.errorPlantillas.set(false);
    this.api.get<ReportePlantilla[]>('/api/reportes/plantillas').pipe(takeUntilDestroyed(this.destroyRef), takeUntil(this.authService.sessionChanged$)).subscribe({
      next: plantillas => this.plantillas.set(plantillas),
      error: () => this.errorPlantillas.set(true),
    });
  }

  protected async cambiarEstadoProgramacion(item: ReporteProgramacion): Promise<void> {
    try {
      await lastValueFrom(this.api.patch<ReporteProgramacion>(`/api/reportes/programaciones/${item.id}/estado`, { activo: !item.activo }));
      this.loadProgramaciones();
    } catch {
      const alert = await this.alertCtrl.create({ header: 'Error', message: 'No se pudo actualizar la programación.', buttons: ['OK'] });
      await alert.present();
    }
  }

  protected async eliminarProgramacion(item: ReporteProgramacion): Promise<void> {
    const confirm = await this.alertCtrl.create({
      header: 'Eliminar programación',
      message: `¿Eliminar la programación “${item.nombre}”? Los reportes ya generados se conservarán.`,
      buttons: [{ text: 'Cancelar', role: 'cancel' }, { text: 'Eliminar', role: 'destructive' }],
    });
    await confirm.present();
    if ((await confirm.onDidDismiss()).role !== 'destructive') return;
    await lastValueFrom(this.api.delete(`/api/reportes/programaciones/${item.id}`));
    this.loadProgramaciones();
  }

  protected tipoLabel(tipo: ReporteItem['tipo']): string {
    switch (tipo) {
      case 'analitico': return 'Analítico';
      case 'ejecutivo': return 'Ejecutivo';
      case 'detallado': return 'Detallado';
    }
  }

  protected onFilterChange(): void {
    this.currentPage.set(1);
  }

  protected limpiarFiltros(): void {
    this.filtroSearch.set('');
    this.filtroModulo.set('Todos');
    this.filtroTipo.set('Todos');
    this.filtroFormato.set('Todos');
    this.currentPage.set(1);
  }

  protected toggleMenu(id: string): void {
    this.openMenuId.update(v => v === id ? null : id);
  }

  protected closeMenu(): void {
    this.openMenuId.set(null);
  }

  protected selectReporte(item: ReporteItem): void {
    this.selectedReporte.set(item);
    this.openMenuId.set(null);
  }

  protected closeDetail(): void {
    this.selectedReporte.set(null);
  }

  /* File helpers */
  protected archivos(item: ReporteItem): string[] {
    if (item.archivosNombres) return item.archivosNombres;
    if (!item.archivosRuta || item.archivosRuta.trim() === '') return [];
    return item.archivosRuta.split(';').filter(r => r.trim() !== '');
  }

  protected archivoNombre(ruta: string): string {
    const parts = ruta.split(/[/\\]/);
    const raw = parts[parts.length - 1];
    return parts.length > 1 ? raw.replace(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}_/i, '') : raw;
  }

  protected descargarArchivo(item: ReporteItem, index: number): void {
    if (!Number.isInteger(index) || index < 0 || index >= this.archivos(item).length) return;
    this.api.getBlob(`/api/reportes/${item.id}/descargar-archivo/${index}`).pipe(takeUntilDestroyed(this.destroyRef), takeUntil(this.authService.sessionChanged$)).subscribe({
      next: (blob) => {
        void this.fileActions.save(blob, this.archivoNombre(this.archivos(item)[index]));
      },
      error: async () => { const alert = await this.alertCtrl.create({ header: 'Archivo no disponible', message: 'No se pudo descargar el adjunto. Revisa tu conexión y tus permisos.', buttons: ['OK'] }); await alert.present(); },
    });
  }

  /* Action handlers */
  protected verDetalle(item: ReporteItem): void {
    this.closeMenu();
    this.selectedReporte.set(item);
  }

  protected descargarReporte(item: ReporteItem, formato?: string): void {
    this.closeMenu();
    const formatoExportacion = formato === 'PDF' || formato === 'CSV' ? formato : item.formato === 'PDF' ? 'PDF' : 'CSV';
    this.api.getBlob(`/api/reportes/${item.id}/exportar?formato=${formatoExportacion}`).pipe(takeUntilDestroyed(this.destroyRef), takeUntil(this.authService.sessionChanged$)).subscribe({
      next: blob => {
        void this.fileActions.save(blob, `${item.nombre}.${formatoExportacion.toLowerCase()}`);
      },
      error: async () => {
        const alert = await this.alertCtrl.create({ header: 'Error', message: 'No se pudo exportar el informe.', buttons: ['OK'] });
        await alert.present();
      },
    });
  }

  protected cambiarVistaCentro(vista: 'plantillas' | 'historial' | 'programaciones'): void {
    this.vistaCentro.set(vista);
    this.closeDetail();
    this.closeMenu();
  }

  protected abrirPlantilla(plantilla: ReportePlantilla): void {
    const hoy = new Date();
    const desde = new Date(hoy);
    desde.setDate(hoy.getDate() - 30);
    this.plantillaSeleccionada.set(plantilla);
    this.plantillaDesde.set(desde.toISOString().slice(0, 10));
    this.plantillaHasta.set(hoy.toISOString().slice(0, 10));
    this.plantillaFormato.set('PDF');
    this.showPlantillaModal.set(true);
  }

  protected cerrarPlantilla(): void {
    this.showPlantillaModal.set(false);
    this.plantillaSeleccionada.set(null);
    this.generandoPlantilla.set(false);
  }

  protected async generarDesdePlantilla(): Promise<void> {
    const vigente = this.operacionActual();
    const plantilla = this.plantillaSeleccionada();
    if (!plantilla || !this.plantillaDesde() || !this.plantillaHasta()) return;
    this.generandoPlantilla.set(true);
    try {
      const reporte = await lastValueFrom(this.api.post<ReporteItem>('/api/reportes/generar', {
        plantilla: plantilla.modulo,
        fechaDesde: this.plantillaDesde(),
        fechaHasta: this.plantillaHasta(),
        formato: this.plantillaFormato(),
      }));
      if (!vigente()) return;
      this.reportes.update(actuales => [reporte, ...actuales]);
      this.cerrarPlantilla();
      this.vistaCentro.set('historial');
      const alert = await this.alertCtrl.create({ header: 'Informe generado', message: 'La instantánea con datos del período ya está disponible en el historial.', buttons: ['OK'] });
      await alert.present();
    } catch (error: any) {
      if (!vigente()) return;
      const mensaje = error?.error?.message || error?.error?.error || 'No se pudo generar el informe.';
      const alert = await this.alertCtrl.create({ header: 'Error', message: mensaje, buttons: ['OK'] });
      await alert.present();
    } finally {
      if (vigente()) this.generandoPlantilla.set(false);
    }
  }

  protected descargarTodo(): void {
    const items = this.reportesFiltrados();
    const rows = [['Nombre', 'Módulo', 'Tipo', 'Fecha', 'Generado por', 'Formato', 'Descripción']];
    items.forEach(r => rows.push([r.nombre, r.modulo, r.tipo, r.fecha, r.generadoPor, r.formato, r.descripcion]));
    const csv = rows.map(r => r.map(c => `"${String(c ?? '').replace(/"/g, '""')}"`).join(',')).join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    void this.fileActions.save(blob, 'reportes_todos.csv');
  }

  protected editarReporte(item: ReporteItem): void {
    this.closeMenu();
    this.editandoId.set(item.id);
    this.genNombre.set(item.nombre);
    this.genModulo.set(item.modulo as ReporteModulo);
    this.genTipo.set(item.tipo);
    this.genFormato.set(item.formato);
    this.genDescripcion.set(item.descripcion);
    this.genArchivos.set([]);
    this.showGenerarModal.set(true);
  }

  protected async eliminarReporte(item: ReporteItem): Promise<void> {
    this.closeMenu();
    const alert = await this.alertCtrl.create({
      header: 'Confirmar eliminación',
      message: `¿Estás seguro de que deseas eliminar el reporte “${item.nombre}”? Esta acción no se puede deshacer.`,
      buttons: [
        { text: 'Cancelar', role: 'cancel' },
        {
          text: 'Eliminar',
          role: 'destructive',
          handler: async () => {
            try {
              await lastValueFrom(this.api.delete<any>(`/api/reportes/${item.id}`));
              if (this.selectedReporte()?.id === item.id) this.selectedReporte.set(null);
              this.loadReportes();
              const ok = await this.alertCtrl.create({ header: 'Éxito', message: 'Reporte eliminado correctamente', buttons: ['OK'] });
              await ok.present();
            } catch (err: any) {
              console.error('Error eliminando reporte:', err);
              const err2 = await this.alertCtrl.create({ header: 'Error', message: 'No se pudo eliminar el reporte', buttons: ['OK'] });
              await err2.present();
            }
          }
        }
      ]
    });
    await alert.present();
  }

  protected compartirReporte(item: ReporteItem): void {
    this.closeMenu();
    this.compartirItem.set(item);
    this.compartirEmail.set('');
    this.showCompartirModal.set(true);
  }

  protected closeCompartirModal(): void {
    this.showCompartirModal.set(false);
    this.compartirItem.set(null);
  }

  protected async enviarCompartir(): Promise<void> {
    const email = this.compartirEmail().trim();
    const item = this.compartirItem();
    if (!email || !item) return;
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      const alert = await this.alertCtrl.create({ header: 'Correo inválido', message: 'Introduce un correo electrónico válido.', buttons: ['OK'] });
      await alert.present(); return;
    }
    const formato = item.formato === 'PDF' ? 'PDF' : 'CSV';
    const enlace = new URL(`${environment.apiUrl}/api/reportes/${encodeURIComponent(item.id)}/exportar?formato=${formato}`, window.location.origin);
    const asunto = encodeURIComponent(`Informe SIGAA: ${item.nombre}`);
    const cuerpo = encodeURIComponent(`Consulta el informe ${item.nombre}:\n${enlace}\n\nDebes iniciar sesión en SIGAA y tener autorización para consultarlo.`);
    window.open(`mailto:${encodeURIComponent(email)}?subject=${asunto}&body=${cuerpo}`, '_self');
    this.closeCompartirModal();
  }

  protected openProgramarConDatos(item: ReporteItem, periodicidad?: string): void {
    if (!this.puedeProgramar()) return;
    this.programacionCreada = null;
    this.closeMenu();
    this.progNombre.set(item.nombre);
    this.progModulo.set(item.modulo as ReporteModulo);
    this.progTipo.set(item.tipo);
    this.progFormato.set(item.formato === 'PDF' ? 'PDF' : 'CSV');
    this.progPeriodicidad.set((periodicidad as any) || 'semanal');
    this.progFechaInicio.set(item.fecha);
    this.progHora.set('');
    this.progDestinatarioId.set('');
    this.progArchivos.set([]);
    this.cargarDestinatarios();
    this.showProgramarModal.set(true);
  }

  /* Generar Reporte modal */
  protected readonly showGenerarModal = signal(false);
  protected readonly genNombre = signal('');
  protected readonly genModulo = signal<ReporteModulo>('Reuniones');
  protected readonly genTipo = signal<'analitico' | 'ejecutivo' | 'detallado'>('ejecutivo');
  protected readonly genFormato = signal<'PDF' | 'Excel' | 'Word' | 'CSV'>('PDF');
  protected readonly genDescripcion = signal('');
  protected readonly genArchivos = signal<File[]>([]);

  protected onFileSelectedGenerar(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files) {
      this.genArchivos.update(list => [...list, ...Array.from(input.files!)]);
      input.value = '';
    }
  }

  protected eliminarArchivoGenerar(index: number): void {
    this.genArchivos.update(list => list.filter((_, i) => i !== index));
  }

  protected openGenerarModal(): void {
    this.reporteCreado = null;
    this.genNombre.set('');
    this.genModulo.set('Reuniones');
    this.genTipo.set('ejecutivo');
    this.genFormato.set('PDF');
    this.genDescripcion.set('');
    this.genArchivos.set([]);
    this.showGenerarModal.set(true);
  }

  protected closeGenerarModal(): void {
    if (this.guardandoReporte()) return;
    this.showGenerarModal.set(false);
    this.editandoId.set(null);
    this.genNombre.set('');
    this.genModulo.set('Reuniones');
    this.genTipo.set('ejecutivo');
    this.genFormato.set('PDF');
    this.genDescripcion.set('');
    this.genArchivos.set([]);
  }

  protected async guardarReporte(): Promise<void> {
    const vigente = this.operacionActual();
    if (this.guardandoReporte() || !this.authService.hasPermission('Reporte', this.editandoId() ? 'Editar' : 'Crear')) return;
    if (this.genArchivos().length && !this.authService.hasPermission('Reporte', 'Subir archivos')) {
      const alert = await this.alertCtrl.create({ header: 'Sin permiso', message: 'No tienes permiso para adjuntar archivos a reportes.', buttons: ['OK'] });
      await alert.present(); return;
    }
    if (!this.genNombre().trim()) {
      const alert = await this.alertCtrl.create({ header: 'Error', message: 'El nombre es obligatorio', buttons: ['OK'] });
      await alert.present();
      return;
    }
    const body = {
      nombre: this.genNombre(),
      modulo: this.genModulo(),
      tipo: this.genTipo(),
      formato: this.genFormato(),
      descripcion: this.genDescripcion(),
      fecha: new Date().toISOString().slice(0, 10),
      generadoPor: this.authService.currentUser()?.nombre || 'Sistema',
    };
    const id = this.editandoId();
    const datosFormulario = JSON.stringify([body.nombre, body.modulo, body.tipo, body.formato, body.descripcion]);
    this.guardandoReporte.set(true);
    try {
      let reporteId: string;
      if (id) {
        const res = await lastValueFrom(this.api.put<any>(`/api/reportes/${id}`, body));
        if (!vigente()) return;
        reporteId = res.id;
      } else if (this.reporteCreado) {
        if (this.reporteCreado.datos !== datosFormulario) throw { error: { message: 'El reporte ya fue registrado. Reintenta los adjuntos sin cambiar los datos; para editarlos utiliza el historial.' } };
        reporteId = this.reporteCreado.id;
      } else {
        const res = await lastValueFrom(this.api.post<any>('/api/reportes', body));
        if (!vigente()) return;
        reporteId = res.id;
        this.reporteCreado = { id: String(res.id), datos: datosFormulario };
      }
      const archivos = this.genArchivos();
      if (archivos.length > 0) {
        const formData = new FormData();
        archivos.forEach(f => formData.append('files', f));
        await lastValueFrom(this.api.post<any>(`/api/reportes/${reporteId}/archivos`, formData));
      }
      if (!vigente()) return;
      this.loadReportes();
      this.loadProgramaciones();
      this.guardandoReporte.set(false);
      this.closeGenerarModal();
      const alert = await this.alertCtrl.create({ header: 'Éxito', message: id ? 'Reporte actualizado correctamente' : 'Reporte generado correctamente', buttons: ['OK'] });
      await alert.present();
    } catch (err: any) {
      if (!vigente()) return;
      const msg = err?.error?.error || err?.error?.message || (this.reporteCreado ? 'El reporte está registrado, pero no se completó la carga. Puedes reintentar los adjuntos.' : id ? 'No se pudo actualizar el reporte' : 'No se pudo generar el reporte');
      const alert = await this.alertCtrl.create({ header: 'Error', message: msg, buttons: ['OK'] });
      await alert.present();
    } finally { if (vigente()) this.guardandoReporte.set(false); }
  }

  /* Programar Reporte modal */
  protected readonly showProgramarModal = signal(false);
  protected readonly progNombre = signal('');
  protected readonly progModulo = signal<ReporteModulo>('Reuniones');
  protected readonly progTipo = signal<'analitico' | 'ejecutivo' | 'detallado'>('ejecutivo');
  protected readonly progFormato = signal<'PDF' | 'Excel' | 'Word' | 'CSV'>('PDF');
  protected readonly progPeriodicidad = signal<'diario' | 'semanal' | 'mensual'>('semanal');
  protected readonly progFechaInicio = signal('');
  protected readonly progHora = signal('');
  protected readonly usuariosDestinatarios = signal<UsuarioDestinatario[]>([]);
  protected readonly progDestinatarioId = signal('');
  protected readonly destinatarioSeleccionado = computed(() =>
    this.usuariosDestinatarios().find(usuario => String(usuario.id) === this.progDestinatarioId()) ?? null
  );
  protected readonly progArchivos = signal<File[]>([]);

  protected onFileSelectedProgramar(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files) {
      this.progArchivos.update(list => [...list, ...Array.from(input.files!)]);
      input.value = '';
    }
  }

  protected eliminarArchivoProgramar(index: number): void {
    this.progArchivos.update(list => list.filter((_, i) => i !== index));
  }

  protected openProgramarModal(): void {
    if (!this.puedeProgramar()) return;
    this.programacionCreada = null;
    this.progNombre.set('');
    this.progModulo.set('Reuniones');
    this.progTipo.set('ejecutivo');
    this.progFormato.set('PDF');
    this.progPeriodicidad.set('semanal');
    this.progFechaInicio.set('');
    this.progHora.set('');
    this.progDestinatarioId.set('');
    this.progArchivos.set([]);
    this.cargarDestinatarios();
    this.showProgramarModal.set(true);
  }

  protected closeProgramarModal(): void {
    if (this.guardandoProgramacion()) return;
    this.showProgramarModal.set(false);
    this.progNombre.set('');
    this.progModulo.set('Reuniones');
    this.progTipo.set('ejecutivo');
    this.progFormato.set('PDF');
    this.progPeriodicidad.set('semanal');
    this.progFechaInicio.set('');
    this.progHora.set('');
    this.progDestinatarioId.set('');
    this.progArchivos.set([]);
  }

  private cargarDestinatarios(): void {
    this.api.get<any[]>('/api/usuarios').pipe(takeUntilDestroyed(this.destroyRef), takeUntil(this.authService.sessionChanged$)).subscribe({
      next: usuarios => this.usuariosDestinatarios.set(usuarios
        .filter(usuario => usuario.activo !== false && usuario.bloqueado !== true && !!usuario.correo)
        .map(usuario => ({
          id: Number(usuario.id),
          nombre: [usuario.nombre, usuario.segundoNombre, usuario.apellido].filter(Boolean).join(' ').trim(),
          correo: usuario.correo,
        }))
        .filter(usuario => usuario.nombre)
        .sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'))),
      error: () => this.usuariosDestinatarios.set([]),
    });
  }

  protected async programarReporte(): Promise<void> {
    const vigente = this.operacionActual();
    if (this.guardandoProgramacion() || !this.puedeProgramar()) return;
    if (!this.progNombre().trim()) {
      const alert = await this.alertCtrl.create({ header: 'Error', message: 'El nombre es obligatorio', buttons: ['OK'] });
      await alert.present();
      return;
    }
    const destinatario = this.destinatarioSeleccionado();
    if (!destinatario) {
      const alert = await this.alertCtrl.create({ header: 'Error', message: 'Seleccione un usuario destinatario', buttons: ['OK'] });
      await alert.present();
      return;
    }
    const body = {
      nombre: this.progNombre(),
      modulo: this.progModulo(),
      tipo: this.progTipo(),
      formato: this.progFormato(),
      periodicidad: this.progPeriodicidad(),
      fechaInicio: this.progFechaInicio(),
      hora: this.progHora(),
      destinatarios: destinatario.correo,
    };
    this.guardandoProgramacion.set(true);
    try {
      if (!this.progFechaInicio() || !this.progHora()) {
        throw { error: { message: 'La fecha y la hora de inicio son obligatorias' } };
      }
      if (this.programacionCreada && this.programacionCreada.datos !== JSON.stringify(body)) throw { error: { message: 'La programación ya fue registrada. Reintenta sus adjuntos sin cambiar los datos.' } };
      const res = this.programacionCreada || await lastValueFrom(this.api.post<any>('/api/reportes/programaciones', body));
      if (!vigente()) return;
      this.programacionCreada = { id: String(res.id), datos: JSON.stringify(body) };
      const archivos = this.progArchivos();
      if (archivos.length > 0) {
        const formData = new FormData();
        archivos.forEach(f => formData.append('files', f));
        await lastValueFrom(this.api.post<any>(`/api/reportes/programaciones/${res.id}/archivos`, formData));
      }
      if (!vigente()) return;
      this.loadReportes();
      this.loadProgramaciones();
      this.guardandoProgramacion.set(false);
      this.closeProgramarModal();
      const alert = await this.alertCtrl.create({ header: 'Éxito', message: 'Reporte programado correctamente', buttons: ['OK'] });
      await alert.present();
    } catch (err: any) {
      if (!vigente()) return;
      const msg = err?.error?.error || err?.error?.message || (this.programacionCreada
        ? 'La programación está registrada, pero no se completó la carga de adjuntos. Puedes reintentarlos.' : 'No se pudo programar el reporte');
      const alert = await this.alertCtrl.create({ header: 'Error', message: msg, buttons: ['OK'] });
      await alert.present();
    } finally { if (vigente()) this.guardandoProgramacion.set(false); }
  }

  protected goToPage(page: number): void {
    if (page >= 1 && page <= this.totalPages().length) {
      this.currentPage.set(page);
    }
  }

  protected onMenuToggle(): void {
    this.authService.sidebarCollapsed.update(v => !v);
  }

  protected onLogout(): void {
    this.authService.logout();
  }
}
