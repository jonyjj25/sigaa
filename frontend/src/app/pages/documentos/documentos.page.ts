import { FileActionsService } from '../../services/file-actions.service';
import { Component, inject, OnInit, signal, computed, ChangeDetectionStrategy, DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { lastValueFrom, finalize } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  IonIcon, IonModal, IonHeader, IonToolbar, IonTitle, IonButtons, IonButton, AlertController, ActionSheetController,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import {
  documentOutline, folderOutline, fileTrayFullOutline, cloudUploadOutline,
  checkmarkCircleOutline, timeOutline, closeCircleOutline, lockClosedOutline,
  lockOpenOutline, pencilOutline, archiveOutline, peopleOutline, globeOutline,
  shieldCheckmarkOutline, documentTextOutline, layersOutline,
  refreshOutline, searchOutline, notificationsOutline, addOutline, filterOutline,
  downloadOutline, shareOutline, eyeOutline, ellipsisVerticalOutline,
  chevronBackOutline, chevronForwardOutline, menuOutline, logOutOutline,
  closeOutline, trashOutline, calendarOutline, alertCircleOutline, syncOutline,
  folderOpenOutline, cloudOutline, removeOutline, expandOutline,
} from 'ionicons/icons';
import { DashboardSidebarComponent } from '../../components/dashboard-sidebar/dashboard-sidebar.component';
import { DashboardTopbarComponent } from '../../components/dashboard-topbar/dashboard-topbar.component';
import { DashboardService } from '../../services/dashboard.service';
import { AuthService } from '../../services/auth.service';
import { ApiService, UsuarioSeleccionableRespuesta, mensajeErrorApi } from '../../services/api.service';
import { Notification } from '../../models/dashboard.models';
import { DocumentoItem, DocumentoClasificacion, DocumentoCompartido, DocumentosFiltros, CarpetaItem, DocumentoHistorial, DocumentoVersionItem } from './documentos.models';

@Component({
  selector: 'app-documentos',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    IonIcon, IonModal, IonHeader, IonToolbar, IonTitle, IonButtons, IonButton,
    DashboardSidebarComponent, DashboardTopbarComponent,
  ],
  templateUrl: './documentos.page.html',
  styleUrls: ['./documentos.page.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DocumentosPage implements OnInit {
  private readonly fileActions = inject(FileActionsService);
  private readonly dashboardService = inject(DashboardService);
  protected readonly authService = inject(AuthService);

  protected can(accion: string): boolean {
    return this.authService.hasPermission('Documentos', accion);
  }
  private readonly api = inject(ApiService);
  private readonly alertCtrl = inject(AlertController);
  private readonly actionSheetCtrl = inject(ActionSheetController);
  private readonly destroyRef = inject(DestroyRef);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly route = inject(ActivatedRoute);
  private horizontalDragTarget: HTMLElement | null = null;
  private horizontalDragPointerId: number | null = null;
  private horizontalDragStartX = 0;
  private horizontalDragStartY = 0;
  private horizontalDragStartScrollLeft = 0;
  private horizontalDragActive = false;

  protected beginHorizontalDrag(event: PointerEvent, scroller: HTMLElement): void {
    if (!event.isPrimary || (event.pointerType === 'mouse' && event.button !== 0)) return;
    this.horizontalDragTarget = scroller;
    this.horizontalDragPointerId = event.pointerId;
    this.horizontalDragStartX = event.clientX;
    this.horizontalDragStartY = event.clientY;
    this.horizontalDragStartScrollLeft = scroller.scrollLeft;
    this.horizontalDragActive = false;
  }

  protected moveHorizontalDrag(event: PointerEvent): void {
    const scroller = this.horizontalDragTarget;
    if (!scroller || event.pointerId !== this.horizontalDragPointerId) return;

    const deltaX = event.clientX - this.horizontalDragStartX;
    const deltaY = event.clientY - this.horizontalDragStartY;
    if (!this.horizontalDragActive && Math.abs(deltaX) > 6 && Math.abs(deltaX) > Math.abs(deltaY)) {
      this.horizontalDragActive = true;
      // Capturar desde el inicio convertía al contenedor en el destino del click
      // y hacía que filas y botones de la tabla parecieran inactivos. Solo se
      // captura cuando el gesto ya es inequívocamente un arrastre horizontal.
      scroller.setPointerCapture?.(event.pointerId);
    }
    if (!this.horizontalDragActive) return;

    if (event.cancelable) event.preventDefault();
    scroller.scrollLeft = this.horizontalDragStartScrollLeft - deltaX;
  }

  protected endHorizontalDrag(event: PointerEvent): void {
    if (event.pointerId !== this.horizontalDragPointerId) return;
    const scroller = this.horizontalDragTarget;
    if (scroller?.hasPointerCapture?.(event.pointerId)) scroller.releasePointerCapture(event.pointerId);
    this.horizontalDragTarget = null;
    this.horizontalDragPointerId = null;
    this.horizontalDragActive = false;
  }
  private documentoEnlaceId: string | null = null;
  // Ionic conserva esta pantalla entre navegaciones. Esta revisión evita que una
  // respuesta iniciada por la cuenta anterior llegue después del cambio de sesión.
  private sessionRevision = 0;

  /* Layout */
  protected readonly menuItems = computed(() => this.dashboardService.getMenuItems());
  protected readonly notifications = signal<Notification[]>([]);

  /* Data */
  protected readonly documentos = signal<DocumentoItem[]>([]);
  protected readonly selectedDoc = signal<DocumentoItem | null>(null);
  protected readonly tabActivo = signal<string>('todas');
  protected readonly filtros = signal<DocumentosFiltros>({ search: '', tipo: 'Todos', area: 'Todos', fecha: 'Todos', estado: 'Todos' });
  protected readonly currentPage = signal(1);
  protected readonly pageSize = 5;
  protected readonly compartirOpen = signal(false);
  protected readonly usuariosCompartir = signal<Array<{ id: number; nombre: string; correo: string; cargo: string }>>([]);
  protected readonly compartidosActuales = signal<DocumentoCompartido[]>([]);
  protected readonly compartirUsuarioId = signal<number | null>(null);
  protected readonly compartirPermiso = signal<'LECTURA' | 'EDICION'>('LECTURA');
  protected readonly compartirExpira = signal('');
  protected readonly nuevoDocOpen = signal(false);
  protected readonly guardandoDocumento = signal(false);
  private creacionId: string | null = null;
  private datosCreacion = '';
  protected readonly nuevaCarpetaOpen = signal(false);
  protected readonly formCarpetaNombre = signal('');
  protected readonly formCarpetaTipo = signal<'PERSONAL' | 'COMPARTIDA'>('PERSONAL');
  protected readonly formCarpetaMiembroIds = signal<number[]>([]);
  protected readonly usuariosCarpeta = signal<{ id: number; nombre: string; correo: string }[]>([]);
  protected readonly modalTitle = signal('Nuevo Documento');
  protected readonly carpetas = signal<CarpetaItem[]>([]);
  protected readonly carpetaActiva = signal<string | null>(null);
  protected readonly archivosSubir = signal<{ nombre: string; tamano: string; archivo?: File }[]>([]);
  protected readonly permisoActual = signal<string>('solo_lectura');
  /* Preview */
  protected readonly previewUrl = signal<SafeResourceUrl | null>(null);
  private previewBlobUrl: string | null = null;

  /* Edit mode */
  protected readonly editandoId = signal<string | null>(null);

  /* Form signals */
  protected readonly formNombre = signal('');
  protected readonly formDescripcion = signal('');
  protected readonly formCategoria = signal('Acta');
  protected readonly formArea = signal('Personal');
  protected readonly unidades = signal<{ id: number; nombre: string }[]>([]);
  protected readonly formPropietario = signal('');
  protected readonly formClasificacion = signal<DocumentoClasificacion>('CONFIDENCIAL');
  protected readonly formCarpetaId = signal<string | null>(null);
  protected readonly catalogoCategorias = signal<string[]>([]);
  protected readonly historial = signal<DocumentoHistorial | null>(null);
  protected readonly historialOpen = signal(false);
  protected readonly cargandoHistorial = signal(false);
  protected readonly errorHistorial = signal(false);
  protected readonly guardandoSolicitud = signal(false);
  protected readonly preparandoSolicitud = signal(false);
  protected readonly versionSolicitudId = signal<number | null>(null);
  protected readonly versionSolicitudLabel = signal('');
  protected readonly resolviendoSolicitud = signal<ReadonlySet<string>>(new Set());
  protected readonly descargandoVersion = signal<ReadonlySet<number>>(new Set());
  private cargaHistorial: { id: string; revision: number; promise: Promise<DocumentoHistorial | null> } | null = null;
  protected readonly versionFormulario = computed(() => this.editandoId()
    ? this.documentos().find(d => d.id === this.editandoId())?.version || 'Sin archivo' : 'Sin archivo');

  /* Role checks */
  protected readonly showSolicitarAprobacion = computed(() => {
    const doc = this.selectedDoc();
    return !!doc && this.authService.isAuthenticated() && this.can('Ver')
      && this.puedeEditarDocumento(doc) && this.tieneArchivo(doc);
  });
  protected readonly puedeResolverAprobaciones = computed(() => {
    const doc = this.selectedDoc();
    return !!doc && this.esAprobadorSeleccionado(doc);
  });
  protected readonly puedeCambiarAccesoFormulario = computed(() => !this.editandoId()
    || this.documentos().find(d => d.id === this.editandoId())?.puedeAdministrarAcceso !== false);
  protected readonly solicitudAprobacionOpen = signal(false);
  protected readonly aprobadoresDisponibles = signal<Array<{ id: number; nombre: string; correo: string; cargo: string }>>([]);
  protected readonly aprobadorSeleccionadoId = signal<number | null>(null);
  protected readonly comentarioSolicitud = signal('');

  /* KPI */
  protected readonly kpiTotal = computed(() => this.documentos().length);
  protected readonly kpiCompartidos = computed(() => this.documentos().filter(d => d.compartido).length);
  protected readonly kpiPendientes = computed(() => this.documentos().filter(d => d.estado === 'en_revision').length);
  protected readonly kpiCarpetasActivas = computed(() => this.carpetas().length);
  protected readonly kpiEspacioUtilizado = computed(() => {
    let bytes = 0;
    for (const d of this.documentos()) {
      const t = d.tamano;
      if (!t) continue;
      if (t.includes('GB')) bytes += parseFloat(t) * 1024 * 1024 * 1024;
      else if (t.includes('MB')) bytes += parseFloat(t) * 1024 * 1024;
      else if (t.includes('KB')) bytes += parseFloat(t) * 1024;
      else bytes += parseInt(t, 10) || 0;
    }
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
    return (bytes / (1024 * 1024 * 1024)).toFixed(1) + ' GB';
  });

  /* Categorías: una única fuente para filtros, creación y pestañas. */
  private readonly categoriasLegadasPorPestana: Record<string, string> = { Actas: 'Acta', Reuniones: 'Reunión', Proyectos: 'Proyecto' };
  protected readonly categoriasDisponibles = computed(() => {
    const existentes = this.documentos()
      .map(documento => documento.categoria?.trim())
      .filter((categoria): categoria is string => !!categoria);
    const unicas = new Map<string, string>();
    for (const categoria of [...this.catalogoCategorias(), ...existentes]) {
      const nombre = this.categoriasLegadasPorPestana[categoria] ?? categoria;
      const clave = nombre.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase().trim();
      if (!unicas.has(clave)) unicas.set(clave, nombre.trim());
    }
    return [...unicas.values()];
  });

  protected cantidadPestana(tab: string): number {
    if (tab === 'mis_carpetas') return this.carpetas().length;
    return this.documentos().filter(documento => this.coincidePestana(documento, tab)).length;
  }

  /* Filters + Tabs combined */
  protected readonly documentosFiltrados = computed(() => {
    let items = this.documentos();
    const tab = this.tabActivo();
    const f = this.filtros();
    const search = f.search.toLowerCase().trim();

    if (tab !== 'mis_carpetas') items = items.filter(documento => this.coincidePestana(documento, tab));
    if (search) items = items.filter(d => d.nombre.toLowerCase().includes(search));
    if (f.tipo !== 'Todos') items = items.filter(d => d.categoria === f.tipo);
    if (f.area !== 'Todos') items = items.filter(d => d.area === f.area);
    if (f.fecha !== 'Todos') items = items.filter(d => this.coincideFecha(d.fecha, f.fecha));
    if (f.estado !== 'Todos') {
      const estadoMap: Record<string, string> = { 'Aprobado': 'aprobado', 'En revisión': 'en_revision', 'Borrador': 'borrador', 'Rechazado': 'rechazado' };
      items = items.filter(d => d.estado === estadoMap[f.estado]);
    }
    return items;
  });

  private coincidePestana(documento: DocumentoItem, tab: string): boolean {
    if (tab === 'todas') return true;
    const origen = documento.origen?.toUpperCase();
    switch (tab) {
      case 'mis_documentos':
        return origen === 'PROPIO' || (!origen && documento.propietarioId === this.authService.currentUser()?.id);
      case 'compartidos': return origen === 'COMPARTIDO';
      case 'tareas': return origen === 'TAREA';
      case 'unidades': return origen === 'UNIDAD';
      default: return false;
    }
  }

  private coincideFecha(fecha: string, periodo: string): boolean {
    const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(fecha || '');
    if (!match) return false;
    const documento = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
    if (Number.isNaN(documento.getTime())) return false;

    const hoy = new Date();
    hoy.setHours(0, 0, 0, 0);
    documento.setHours(0, 0, 0, 0);
    if (periodo === 'Hoy') return documento.getTime() === hoy.getTime();

    let inicio: Date;
    let fin: Date;
    if (periodo === 'Esta semana') {
      inicio = new Date(hoy);
      const diaDesdeLunes = (inicio.getDay() + 6) % 7;
      inicio.setDate(inicio.getDate() - diaDesdeLunes);
      fin = new Date(inicio);
      fin.setDate(fin.getDate() + 7);
    } else if (periodo === 'Este mes') {
      inicio = new Date(hoy.getFullYear(), hoy.getMonth(), 1);
      fin = new Date(hoy.getFullYear(), hoy.getMonth() + 1, 1);
    } else if (periodo === 'Este trimestre') {
      const primerMes = Math.floor(hoy.getMonth() / 3) * 3;
      inicio = new Date(hoy.getFullYear(), primerMes, 1);
      fin = new Date(hoy.getFullYear(), primerMes + 3, 1);
    } else {
      return true;
    }
    return documento >= inicio && documento < fin;
  }

  protected readonly totalCount = computed(() => this.documentosFiltrados().length);
  protected readonly paginatedDocumentos = computed(() => {
    const start = (this.currentPage() - 1) * this.pageSize;
    return this.documentosFiltrados().slice(start, start + this.pageSize);
  });
  protected readonly totalPages = computed(() =>
    Array.from({ length: Math.ceil(this.documentosFiltrados().length / this.pageSize) }, (_, i) => i + 1)
  );

  constructor() {
    addIcons({
      documentOutline, folderOutline, fileTrayFullOutline, cloudUploadOutline,
      checkmarkCircleOutline, timeOutline, closeCircleOutline, lockClosedOutline,
      lockOpenOutline, pencilOutline, archiveOutline, peopleOutline, globeOutline,
      shieldCheckmarkOutline, documentTextOutline, layersOutline,
      refreshOutline, searchOutline, notificationsOutline, addOutline, filterOutline,
      downloadOutline, shareOutline, eyeOutline, ellipsisVerticalOutline,
      chevronBackOutline, chevronForwardOutline, menuOutline, logOutOutline,
      closeOutline, trashOutline, calendarOutline, alertCircleOutline, syncOutline,
      folderOpenOutline, cloudOutline, removeOutline, expandOutline,
    });
  }

  ngOnInit(): void {
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      this.documentoEnlaceId = params.get('documento');
      this.seleccionarDocumentoEnlazado();
    });
    this.authService.sessionChanged$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.recargarDatosDeSesion());
    this.recargarDatosDeSesion();
  }

  private recargarDatosDeSesion(): void {
    this.sessionRevision++;
    this.documentos.set([]);
    this.carpetas.set([]);
    this.carpetaActiva.set(null);
    this.nuevoDocOpen.set(false);
    this.editandoId.set(null);
    this.archivosSubir.set([]);
    this.creacionId = null;
    this.historial.set(null); this.historialOpen.set(false); this.cargaHistorial = null;
    this.cargandoHistorial.set(false); this.errorHistorial.set(false);
    this.guardandoSolicitud.set(false); this.preparandoSolicitud.set(false); this.versionSolicitudId.set(null);
    this.solicitudAprobacionOpen.set(false); this.resolviendoSolicitud.set(new Set());
    this.descargandoVersion.set(new Set()); this.catalogoCategorias.set([]);
    this.closeDetail();
    this.notifications.set([]);

    if (!this.authService.isAuthenticated()) return;

    this.loadDocumentos();
    this.cargarCarpetas();
    const revision = this.sessionRevision;
    const usuarioId = this.authService.currentUser()?.id;
    this.api.get<string[]>('/api/documentos/categorias').pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: categorias => { if (this.esRespuestaDeSesionActual(revision, usuarioId)) this.catalogoCategorias.set(categorias); },
      error: () => { if (this.esRespuestaDeSesionActual(revision, usuarioId)) void this.mostrarErrorAprobacion('No se pudo cargar el catálogo de categorías. Vuelve a cargar el módulo.'); },
    });
    this.api.get<{ id: number; nombre: string }[]>('/api/organizacion/activos').pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: unidades => {
        if (this.esRespuestaDeSesionActual(revision, usuarioId)) this.unidades.set(unidades);
      },
      error: () => {
        if (this.esRespuestaDeSesionActual(revision, usuarioId)) void this.mostrarErrorAprobacion('No se pudieron consultar las unidades. Intenta cargar el módulo nuevamente.');
      },
    });
    this.dashboardService.getNotificaciones().pipe(takeUntilDestroyed(this.destroyRef)).subscribe(d => {
      if (this.esRespuestaDeSesionActual(revision, usuarioId)) this.notifications.set(d);
    });
  }

  private esRespuestaDeSesionActual(revision: number, usuarioId: number | undefined): boolean {
    return revision === this.sessionRevision && usuarioId === this.authService.currentUser()?.id;
  }

  private cargarCarpetas(): void {
    const revision = this.sessionRevision;
    const usuarioId = this.authService.currentUser()?.id;
    this.api.get<CarpetaItem[]>('/api/documentos/carpetas?incluirDocumentos=false').pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (data) => {
        if (this.esRespuestaDeSesionActual(revision, usuarioId)) this.carpetas.set(data);
      },
      error: (err) => {
        console.error('Error cargando carpetas:', err);
        if (this.esRespuestaDeSesionActual(revision, usuarioId)) void this.mostrarErrorAprobacion('No se pudieron consultar las carpetas. Intenta cargar el módulo nuevamente.');
      },
    });
  }

  private loadDocumentos(): void {
    const revision = this.sessionRevision;
    const usuarioId = this.authService.currentUser()?.id;
    this.api.get<DocumentoItem[]>('/api/documentos').pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (data) => {
        if (!this.esRespuestaDeSesionActual(revision, usuarioId)) return;
        this.documentos.set(data);
        this.seleccionarDocumentoEnlazado();
      },
      error: (err) => {
        if (!this.esRespuestaDeSesionActual(revision, usuarioId)) return;
        console.error('Error cargando documentos:', err);
        this.alertCtrl.create({
          header: 'Error',
          message: 'No se pudieron cargar los documentos. Verifique su sesión.',
          buttons: ['OK'],
        }).then(a => a.present());
      },
    });
  }

  /* State label */
  protected estadoLabel(estado: string): string {
    switch (estado) {
      case 'aprobado': return 'Aprobado';
      case 'en_revision': return 'En revisión';
      case 'borrador': return 'Borrador';
      case 'rechazado': return 'Rechazado';
      default: return estado;
    }
  }

  protected clasificacionLabel(clasificacion: DocumentoClasificacion): string {
    const labels: Record<DocumentoClasificacion, string> = {
      PUBLICO: 'Institucional',
      USO_INTERNO: 'Por unidad',
      RESTRINGIDO: 'Por unidad',
      CONFIDENCIAL: 'Confidencial',
      DATOS_SENSIBLES: 'Confidencial',
    };
    return labels[clasificacion] || 'Confidencial';
  }

  /* Filter methods */
  protected updateFilter(key: keyof DocumentosFiltros, value: string): void {
    this.filtros.update(f => ({ ...f, [key]: value }));
    this.currentPage.set(1);
  }

  protected limpiarFiltros(): void {
    this.filtros.set({ search: '', tipo: 'Todos', area: 'Todos', fecha: 'Todos', estado: 'Todos' });
    this.tabActivo.set('todas');
    this.currentPage.set(1);
  }

  /* Tab methods */
  protected setTab(tab: string): void {
    this.tabActivo.set(tab);
    this.currentPage.set(1);
  }

  /* Selection */
  protected selectDocumento(doc: DocumentoItem): void {
    if (this.selectedDoc()?.id !== doc.id) {
      this.historial.set(null); this.historialOpen.set(false); this.errorHistorial.set(false);
    }
    this.selectedDoc.set(doc);
    this.permisoActual.set(doc.permiso || 'solo_lectura');
    this.revokePreview();
    this.previewVisible.set(false);
  }

  private seleccionarDocumentoEnlazado(): void {
    if (!this.documentoEnlaceId) return;
    const documento = this.documentos().find(item => item.id === this.documentoEnlaceId);
    if (!documento) return;

    this.tabActivo.set('todas');
    this.filtros.set({ search: '', tipo: 'Todos', area: 'Todos', fecha: 'Todos', estado: 'Todos' });
    const indice = this.documentos().findIndex(item => item.id === documento.id);
    this.currentPage.set(Math.floor(indice / this.pageSize) + 1);
    this.selectDocumento(documento);
  }

  /* Vista previa */
  protected readonly previewVisible = signal(false);

  protected canPreview(doc: DocumentoItem): boolean {
    return !!this.urlVistaPrevia(doc);
  }

  protected tieneArchivo(doc: DocumentoItem): boolean {
    return !!this.urlDescarga(doc);
  }

  protected puedeDescargarDocumento(doc: DocumentoItem): boolean {
    return doc.puedeDescargar === true && (doc.origen === 'TAREA'
      ? this.authService.hasPermission('Tareas', 'Ver') : this.can('Descargar'));
  }

  protected puedeEditarDocumento(doc: DocumentoItem): boolean {
    return doc.origen !== 'TAREA' && doc.puedeEditar === true;
  }

  protected puedeCompartirDocumento(doc: DocumentoItem): boolean {
    return doc.origen !== 'TAREA' && doc.puedeAdministrarAcceso === true && this.can('Ver');
  }

  protected puedeEliminarDocumento(doc: DocumentoItem): boolean {
    return doc.origen !== 'TAREA' && doc.puedeEliminar === true && this.can('Eliminar');
  }

  protected esDocumentoGestionable(doc: DocumentoItem): boolean {
    return doc.origen !== 'TAREA';
  }

  private urlDescarga(doc: DocumentoItem): string | null {
    const url = doc.descargaUrl?.trim();
    if (url) return url;
    if (doc.origen === 'TAREA' || !doc.archivoRuta) return null;
    return `/api/documentos/${doc.id}/descargar`;
  }

  private urlVistaPrevia(doc: DocumentoItem): string | null {
    const url = doc.vistaPreviaUrl?.trim();
    if (url) return url;
    if (doc.origen === 'TAREA' || !doc.archivoRuta) return null;
    return `/api/documentos/${doc.id}/vista-previa`;
  }

  protected togglePreview(): void {
    const doc = this.selectedDoc();
    if (!doc) return;
    if (this.previewVisible()) {
      this.revokePreview();
      this.previewVisible.set(false);
      return;
    }
    const url = this.urlVistaPrevia(doc);
    if (!url) return;
    const revision = this.sessionRevision, usuarioId = this.authService.currentUser()?.id;
    const vigente = () => this.esRespuestaDeSesionActual(revision, usuarioId) && this.selectedDoc()?.id === doc.id
      && this.selectedDoc()?.versionActualId === doc.versionActualId && this.previewVisible();
    this.previewVisible.set(true);
    this.api.getBlob(url).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (blob) => {
        if (!vigente()) return;
        this.previewBlobUrl = URL.createObjectURL(blob);
        this.previewUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(this.previewBlobUrl));
      },
      error: async () => {
        if (!vigente()) return;
        this.previewUrl.set(null);
        this.previewVisible.set(false);
        const alert = await this.alertCtrl.create({
          header: 'Error',
          message: 'No se pudo cargar la vista previa del archivo',
          buttons: ['OK'],
        });
        await alert.present();
      },
    });
  }

  private revokePreview(): void {
    if (this.previewBlobUrl) {
      URL.revokeObjectURL(this.previewBlobUrl);
      this.previewBlobUrl = null;
    }
    this.previewUrl.set(null);
  }

  protected selectDocFromCarpeta(doc: DocumentoItem): void {
    this.selectDocumento(doc);
  }

  protected closeDetail(): void {
    this.revokePreview();
    this.previewVisible.set(false);
    this.selectedDoc.set(null);
    this.historial.set(null); this.historialOpen.set(false);
  }

  protected async abrirHistorial(): Promise<void> {
    const doc = this.selectedDoc();
    if (!doc || !this.esDocumentoGestionable(doc)) return;
    this.historialOpen.set(true);
    await this.obtenerHistorial(doc);
  }

  protected async recargarHistorial(): Promise<void> {
    const doc = this.selectedDoc();
    if (doc) await this.obtenerHistorial(doc, true);
  }

  private obtenerHistorial(doc: DocumentoItem, forzar = false): Promise<DocumentoHistorial | null> {
    if (!this.esDocumentoGestionable(doc)) return Promise.resolve(null);
    const revision = this.sessionRevision;
    const usuarioId = this.authService.currentUser()?.id;
    if (this.cargaHistorial?.id === doc.id && this.cargaHistorial.revision === revision) return this.cargaHistorial.promise;
    const existente = this.historial();
    if (!forzar && existente?.documento.id === doc.id && existente.documento.versionActualId === doc.versionActualId) return Promise.resolve(existente);
    this.cargandoHistorial.set(true); this.errorHistorial.set(false);
    const vigente = () => this.esRespuestaDeSesionActual(revision, usuarioId) && this.selectedDoc()?.id === doc.id;
    const promise = lastValueFrom(this.api.get<DocumentoHistorial>(`/api/documentos/${doc.id}/versiones`).pipe(takeUntilDestroyed(this.destroyRef)))
      .then(data => {
        if (!vigente()) return null;
        if (this.selectedDoc()?.versionActualId !== data.documento.versionActualId) { this.revokePreview(); this.previewVisible.set(false); }
        this.historial.set(data);
        this.documentos.update(lista => lista.map(d => d.id === doc.id ? { ...d, ...data.documento } : d));
        this.selectedDoc.update(d => d?.id === doc.id ? { ...d, ...data.documento } : d);
        return data;
      }).catch((error: unknown) => {
        if (vigente()) {
          this.errorHistorial.set(true);
          void this.mostrarErrorAprobacion(mensajeErrorApi(error, 'No se pudo consultar el historial. Puedes volver a intentarlo.'));
        }
        return null;
      }).finally(() => {
        if (this.cargaHistorial?.promise === promise) { this.cargaHistorial = null; this.cargandoHistorial.set(false); }
      });
    this.cargaHistorial = { id: doc.id, revision, promise };
    return promise;
  }

  protected descargarVersion(version: DocumentoVersionItem): void {
    const doc = this.selectedDoc();
    if (!doc || !version.disponible || !this.can('Descargar') || this.descargandoVersion().has(version.id)) return;
    const revision = this.sessionRevision, usuarioId = this.authService.currentUser()?.id;
    const vigente = () => this.esRespuestaDeSesionActual(revision, usuarioId);
    this.descargandoVersion.update(ids => new Set([...ids, version.id]));
    this.api.getBlob(`/api/documentos/${doc.id}/versiones/${version.id}/descargar`).pipe(
      finalize(() => { if (vigente()) this.descargandoVersion.update(ids => { const nuevos = new Set(ids); nuevos.delete(version.id); return nuevos; }); }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: blob => { if (vigente()) this.guardarDescarga(blob, version.archivoNombre || `${doc.nombre}-${version.version}`); },
      error: (error: unknown) => { if (vigente()) void this.mostrarErrorAprobacion(mensajeErrorApi(error, 'No se pudo descargar esta versión. Comprueba tu acceso y vuelve a intentarlo.')); },
    });
  }

  private guardarDescarga(blob: Blob, nombre: string): void {
    void this.fileActions.save(blob, nombre);
  }

  protected aprobacionLabel(estado: string): string {
    return ({ PENDIENTE: 'En revisión', APROBADA: 'Aprobada', RECHAZADA: 'Rechazada', SUSTITUIDA: 'Sustituida' } as Record<string, string>)[estado] || estado;
  }

  /* Acciones: hoja nativa que no queda atrapada dentro de la tabla. */
  protected async abrirAcciones(doc: DocumentoItem, event: Event): Promise<void> {
    event.stopPropagation();
    const buttons: any[] = [
      { text: 'Ver detalle', icon: 'eye-outline', cssClass: 'document-action-view', handler: () => this.selectDocumento(doc) },
    ];
    if (this.puedeDescargarDocumento(doc) && this.tieneArchivo(doc)) buttons.push({ text: 'Descargar', icon: 'download-outline', cssClass: 'document-action-download', handler: () => this.descargarDocumento(doc) });
    if (this.puedeCompartirDocumento(doc)) buttons.push({ text: 'Compartir', icon: 'share-outline', cssClass: 'document-action-share', handler: () => this.openShareModal(doc) });
    if (this.puedeEditarDocumento(doc) && this.can('Editar')) buttons.push({ text: 'Editar', icon: 'pencil-outline', cssClass: 'document-action-edit', handler: () => this.editarDocumento(doc) });
    if (this.esAprobadorSeleccionado(doc)) {
      buttons.push({ text: 'Aprobar', icon: 'checkmark-circle-outline', cssClass: 'document-action-approve', handler: () => this.resolverAprobacion(doc, 'aprobar') });
      buttons.push({ text: 'Rechazar', icon: 'close-circle-outline', cssClass: 'document-action-reject', handler: () => this.resolverAprobacion(doc, 'rechazar') });
    }
    if (this.puedeEliminarDocumento(doc)) buttons.push({ text: 'Eliminar', icon: 'trash-outline', role: 'destructive', cssClass: 'document-action-delete', handler: () => this.eliminarDocumento(doc) });
    buttons.push({ text: 'Cancelar', icon: 'close-outline', role: 'cancel', cssClass: 'document-action-cancel' });

    const actionSheet = await this.actionSheetCtrl.create({
      header: 'Acciones del documento',
      subHeader: doc.nombre,
      cssClass: 'document-actions-sheet',
      buttons,
    });
    await actionSheet.present();
  }

  protected closeMenu(): void { }

  /* Pagination */
  protected goToPage(page: number): void {
    if (page >= 1 && page <= this.totalPages().length) {
      this.currentPage.set(page);
    }
  }

  /* Share modal */
  protected openShareModal(documento?: DocumentoItem): void {
    const doc = documento || this.selectedDoc();
    if (!doc || !this.puedeCompartirDocumento(doc)) return;
    this.selectDocumento(doc);
    this.compartirUsuarioId.set(null);
    this.compartirPermiso.set('LECTURA');
    this.compartirExpira.set('');
    this.api.get<UsuarioSeleccionableRespuesta[]>('/api/usuarios/seleccionables').pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: usuarios => this.usuariosCompartir.set(usuarios
        .filter(usuario => usuario.activo !== false && usuario.bloqueado !== true && Number(usuario.id) !== doc.propietarioId)
        .map(usuario => ({
          id: Number(usuario.id),
          nombre: [usuario.nombre, usuario.segundoNombre, usuario.apellido].filter(Boolean).join(' '),
          correo: usuario.correo || '',
          cargo: usuario.cargo || '',
        }))),
      error: () => { this.usuariosCompartir.set([]); void this.mostrarErrorAprobacion('No se pudo consultar la lista de usuarios para compartir.'); },
    });
    this.cargarCompartidos(doc.id);
    this.compartirOpen.set(true);
  }

  protected closeShareModal(): void {
    this.compartirOpen.set(false);
  }

  private cargarCompartidos(documentoId: string): void {
    this.api.get<DocumentoCompartido[]>(`/api/documentos/${documentoId}/compartidos`).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: accesos => this.compartidosActuales.set(accesos),
      error: () => { this.compartidosActuales.set([]); void this.mostrarErrorAprobacion('No se pudieron consultar los accesos vigentes del documento.'); },
    });
  }

  protected seleccionarUsuarioCompartir(value: string): void {
    this.compartirUsuarioId.set(value ? Number(value) : null);
  }

  protected async guardarComparticion(): Promise<void> {
    const doc = this.selectedDoc();
    const usuarioId = this.compartirUsuarioId();
    if (!doc || !usuarioId || doc.origen === 'TAREA') return;
    try {
      const actualizado = await lastValueFrom(this.api.post<DocumentoItem>(`/api/documentos/${doc.id}/compartidos`, {
        usuarioId,
        permiso: this.compartirPermiso(),
        fechaExpiracion: this.compartirExpira() || null,
      }));
      this.documentos.update(items => items.map(item => item.id === doc.id ? actualizado : item));
      this.selectedDoc.set(actualizado);
      this.compartirUsuarioId.set(null);
      this.cargarCompartidos(doc.id);
    } catch {
      const alert = await this.alertCtrl.create({ header: 'Acceso no concedido', message: 'No se pudo compartir el documento con el usuario seleccionado.', buttons: ['OK'] });
      await alert.present();
    }
  }

  protected async retirarComparticion(acceso: DocumentoCompartido): Promise<void> {
    const doc = this.selectedDoc();
    if (!doc || doc.origen === 'TAREA') return;
    try {
      await lastValueFrom(this.api.delete<void>(`/api/documentos/${doc.id}/compartidos/${acceso.usuarioId}`));
      this.compartidosActuales.update(items => items.filter(item => item.usuarioId !== acceso.usuarioId));
      const refreshed = await lastValueFrom(this.api.get<DocumentoItem[]>('/api/documentos'));
      this.documentos.set(refreshed);
      this.selectedDoc.set(refreshed.find(item => item.id === doc.id) || null);
    } catch {
      const alert = await this.alertCtrl.create({ header: 'Acceso no retirado', message: 'No se pudo retirar el acceso seleccionado.', buttons: ['OK'] });
      await alert.present();
    }
  }

  /* Permissions */
  protected selectPermiso(nivel: string): void {
    const doc = this.selectedDoc();
    if (!doc || doc.origen === 'TAREA') return;
    const anterior = this.permisoActual();
    this.permisoActual.set(nivel);
    this.api.patch<DocumentoItem>(`/api/documentos/${doc.id}/permiso`, { nivel }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (updated) => {
        this.documentos.update(list => list.map(d => d.id === doc.id ? { ...d, permiso: updated.permiso } : d));
        this.selectedDoc.update(d => d?.id === doc.id ? { ...d, permiso: updated.permiso } : d);
      },
      error: async () => {
        this.permisoActual.set(anterior);
        const alert = await this.alertCtrl.create({ header: 'Error', message: 'No se pudo actualizar el permiso', buttons: ['OK'] });
        await alert.present();
      },
    });
  }

  /* Carpeta modal */
  protected openNuevaCarpetaModal(): void {
    this.formCarpetaNombre.set('');
    this.formCarpetaTipo.set('PERSONAL');
    this.formCarpetaMiembroIds.set([]);
    this.api.get<{ id: number; nombre: string; correo: string }[]>('/api/documentos/carpetas/usuarios').pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: x => this.usuariosCarpeta.set(x), error: () => { this.usuariosCarpeta.set([]); void this.mostrarErrorAprobacion('No se pudo consultar la lista de usuarios para la carpeta.'); } });
    this.nuevaCarpetaOpen.set(true);
  }

  protected closeNuevaCarpetaModal(): void {
    this.nuevaCarpetaOpen.set(false);
  }

  protected async crearCarpeta(): Promise<void> {
    const nombre = this.formCarpetaNombre().trim();
    if (!nombre) return;
    try {
      await lastValueFrom(this.api.post<CarpetaItem>('/api/documentos/carpetas', { nombre, tipo: this.formCarpetaTipo(), miembroIds: this.formCarpetaMiembroIds() }));
      this.cargarCarpetas();
      this.closeNuevaCarpetaModal();
      const alert = await this.alertCtrl.create({ header: 'Éxito', message: 'Carpeta creada correctamente', buttons: ['OK'] });
      await alert.present();
    } catch {
      const alert = await this.alertCtrl.create({ header: 'Error', message: 'No se pudo crear la carpeta', buttons: ['OK'] });
      await alert.present();
    }
  }

  protected toggleMiembroCarpeta(id: number, checked: boolean): void { this.formCarpetaMiembroIds.update(ids => checked ? [...new Set([...ids, id])] : ids.filter(x => x !== id)); }

  /* Nuevo Documento modal */
  protected openNuevoDocModal(title = 'Nuevo Documento', doc?: DocumentoItem): void {
    if (this.guardandoDocumento()) return;
    this.creacionId = null;
    this.datosCreacion = '';
    this.modalTitle.set(title);
    this.archivosSubir.set([]);
    if (doc) {
      this.editandoId.set(doc.id);
      this.formNombre.set(doc.nombre);
      this.formDescripcion.set(doc.descripcion);
      this.formCategoria.set(doc.categoria);
      this.formArea.set(doc.area);
      this.formPropietario.set(doc.propietario);
      this.formClasificacion.set(doc.politicaAcceso || (doc.clasificacion === 'USO_INTERNO' ? 'RESTRINGIDO'
        : doc.clasificacion === 'DATOS_SENSIBLES' ? 'CONFIDENCIAL' : doc.clasificacion || 'CONFIDENCIAL'));
      this.formCarpetaId.set(doc.carpetaId ? String(doc.carpetaId) : null);
    } else {
      this.editandoId.set(null);
      this.formNombre.set('');
      this.formDescripcion.set('');
      this.formCategoria.set('Acta');
      this.formArea.set('Personal');
      this.formPropietario.set('');
      this.formClasificacion.set('CONFIDENCIAL');
      this.formCarpetaId.set(null);
    }
    this.nuevoDocOpen.set(true);
  }

  protected closeNuevoDocModal(forzar = false): void {
    if (this.guardandoDocumento() && !forzar) return;
    this.nuevoDocOpen.set(false);
    this.archivosSubir.set([]);
    this.editandoId.set(null);
    this.formCarpetaId.set(null);
  }

  private static readonly ALLOWED_EXTENSIONS = new Set([
    'pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx',
    'jpg', 'jpeg', 'png', 'gif',
    'txt', 'csv', 'zip', 'rar',
  ]);

  protected onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;
    if (input.files.length > 1 || this.archivosSubir().length > 0) {
      void this.mostrarErrorAprobacion('Cada documento admite un archivo. Retira el archivo seleccionado antes de elegir otro.');
      input.value = '';
      return;
    }
    const rechazados: string[] = [];
    for (const file of Array.from(input.files)) {
      const ext = file.name.split('.').pop()?.toLowerCase() ?? '';
      if (!DocumentosPage.ALLOWED_EXTENSIONS.has(ext)) {
        rechazados.push(file.name);
        continue;
      }
      if (file.size === 0 || file.size > 10 * 1024 * 1024) {
        void this.mostrarErrorAprobacion('El archivo debe contener datos y pesar como máximo 10 MB.');
        continue;
      }
      const size = file.size < 1024 * 1024
        ? `${(file.size / 1024).toFixed(0)} KB`
        : `${(file.size / (1024 * 1024)).toFixed(1)} MB`;
      this.archivosSubir.update(files => [...files, { nombre: file.name, tamano: size, archivo: file }]);
    }
    if (rechazados.length > 0) {
      this.alertCtrl.create({
        header: 'Formato no permitido',
        message: `Los siguientes archivos no son compatibles: ${rechazados.join(', ')}`,
        buttons: ['OK'],
      }).then(a => a.present());
    }
    input.value = '';
  }

  protected eliminarArchivo(nombre: string): void {
    this.archivosSubir.update(files => files.filter(f => f.nombre !== nombre));
  }

  protected selectCarpeta(nombre: string): void {
    this.closeDetail();
    this.carpetaActiva.update(v => v === nombre ? null : nombre);
  }

  protected documentosEnCarpetaActiva = computed(() => {
    const nombre = this.carpetaActiva();
    if (!nombre) return [];
    const carpeta = this.carpetas().find(c => c.nombre === nombre);
    return carpeta ? this.documentos().filter(d => String(d.carpetaId) === carpeta.id) : [];
  });

  protected async eliminarCarpeta(carpeta: CarpetaItem, event: Event): Promise<void> {
    event.stopPropagation();
    if (!carpeta.puedeEliminar) return;

    const avisoDocumentos = carpeta.count > 0
      ? ` También se retirarán sus ${carpeta.count} documento(s) del listado. El historial y sus archivos se conservarán.`
      : '';
    const avisoCompartida = carpeta.tipo === 'COMPARTIDA'
      ? ' Las personas con acceso dejarán de ver esta carpeta.'
      : '';

    const alert = await this.alertCtrl.create({
      header: 'Eliminar carpeta',
      cssClass: 'documento-folder-delete-alert',
      message: `¿Deseas eliminar la carpeta "${carpeta.nombre}"?${avisoDocumentos}${avisoCompartida} Esta acción no se puede deshacer.`,
      buttons: [
        { text: 'Cancelar', role: 'cancel', cssClass: 'documento-folder-delete-cancel' },
        {
          text: 'Eliminar',
          cssClass: 'documento-folder-delete-confirm',
          role: 'destructive',
          handler: async () => {
            try {
              await lastValueFrom(this.api.delete<void>(`/api/documentos/carpetas/${carpeta.id}`));
              const idsEliminados = new Set(this.documentos().filter(d => String(d.carpetaId) === carpeta.id).map(d => d.id));
              this.documentos.update(documentos => documentos.filter(documento => !idsEliminados.has(documento.id)));
              this.carpetas.update(carpetas => carpetas.filter(item => item.id !== carpeta.id));
              if (this.carpetaActiva() === carpeta.nombre) this.carpetaActiva.set(null);
              if (this.selectedDoc() && idsEliminados.has(this.selectedDoc()!.id)) this.closeDetail();
            } catch (error: unknown) {
              const errorAlert = await this.alertCtrl.create({
                header: 'No se pudo eliminar',
                message: error instanceof HttpErrorResponse && error.status === 403
                  ? 'Solo puedes eliminar tus propias carpetas.'
                  : 'No se pudo eliminar la carpeta y sus documentos.',
                buttons: ['OK'],
              });
              await errorAlert.present();
            }
          },
        },
      ],
    });
    await alert.present();
  }

  /* CRUD: Guardar */
  protected async guardarDocumento(): Promise<void> {
    if (this.guardandoDocumento() || !this.nuevoDocOpen() || !this.formNombre().trim()) return;
    this.guardandoDocumento.set(true);
    const revision = this.sessionRevision;
    const usuarioId = this.authService.currentUser()?.id;
    const editId = this.editandoId();
    const carpetaAnterior = this.documentos().find(d => d.id === editId)?.carpetaId ?? null;
    try {
      const body: Record<string, unknown> = {
        nombre: this.formNombre().trim(),
        descripcion: this.formDescripcion(),
        categoria: this.formCategoria(),
        area: this.formArea(),
        propietario: this.formPropietario() || this.authService.currentUser()?.nombre || '',
        clasificacion: this.formClasificacion(),
        carpetaId: this.formCarpetaId() ? Number(this.formCarpetaId()) : null,
      };
      if (!editId) {
        Object.assign(body, { fecha: new Date().toISOString().slice(0, 10), estado: 'borrador' });
        const datos = JSON.stringify(body);
        if (!this.creacionId || this.datosCreacion !== datos) {
          const bytes = crypto.getRandomValues(new Uint8Array(16));
          bytes[6] = (bytes[6] & 15) | 64;
          bytes[8] = (bytes[8] & 63) | 128;
          const hex = Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('');
          this.creacionId = `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
          this.datosCreacion = datos;
        }
        body['creacionId'] = this.creacionId;
      }

      const request$ = editId ? this.api.put<DocumentoItem>(`/api/documentos/${editId}`, body) : this.api.post<DocumentoItem>('/api/documentos', body);
      const result = await lastValueFrom(request$);
      if (!this.esRespuestaDeSesionActual(revision, usuarioId)) return;
      const docId = result.id;
      this.editandoId.set(docId);
      this.documentos.update(list => [result, ...list.filter(d => d.id !== docId)]);

      const archivos = this.archivosSubir().filter(a => a.archivo);
      const fallidos: string[] = [];
      let lastUploadResult: (DocumentoItem & { contenidoIdentico?: boolean }) | null = null;
      for (const adj of archivos) {
        const formData = new FormData();
        formData.append('file', adj.archivo!);
        try {
          lastUploadResult = await lastValueFrom(this.api.post<DocumentoItem & { contenidoIdentico?: boolean }>(`/api/documentos/${docId}/archivo`, formData));
        } catch {
          fallidos.push(adj.nombre);
        }
      }
      if (!this.esRespuestaDeSesionActual(revision, usuarioId)) return;

      if (lastUploadResult) {
        this.documentos.update(list => list.map(d => d.id === docId
          ? { ...d, ...lastUploadResult } : d));
        this.selectedDoc.update(d => d?.id === docId
          ? { ...d, ...lastUploadResult } : d);
      }

      const updated = this.documentos().find(d => d.id === docId);
      if (updated) this.selectedDoc.set(updated);
      this.historial.set(null); this.historialOpen.set(false);
      if (!editId || String(carpetaAnterior ?? '') !== String(updated?.carpetaId ?? '')) this.cargarCarpetas();
      if (fallidos.length > 0) {
        const alert = await this.alertCtrl.create({ header: 'Archivo pendiente', message: 'El documento se guardó, pero el archivo no pudo cargarse. Intenta guardar nuevamente para reintentar la carga.', buttons: ['OK'] });
        await alert.present();
        return;
      }
      this.guardandoDocumento.set(false);
      this.closeNuevoDocModal(true);

      const alert = await this.alertCtrl.create({
        header: 'Éxito',
        message: lastUploadResult?.contenidoIdentico ? 'El contenido es idéntico; se conserva la versión actual y su aprobación.'
          : lastUploadResult ? `Versión ${lastUploadResult.version} guardada. Puedes solicitar la aprobación de este archivo.`
          : editId ? 'Metadatos actualizados. La versión del archivo no cambió.' : 'Borrador creado. Adjunta un archivo para generar su primera versión.',
        buttons: ['OK'],
      });
      await alert.present();
    } catch (error: unknown) {
      if (!this.esRespuestaDeSesionActual(revision, usuarioId)) return;
      const alert = await this.alertCtrl.create({
        header: 'Error',
        message: mensajeErrorApi(error, 'Ocurrió un error al guardar el documento. Puedes intentarlo nuevamente.'),
        buttons: ['OK'],
      });
      await alert.present();
    } finally {
      this.guardandoDocumento.set(false);
    }
  }

  /* CRUD: Editar */
  protected editarDocumento(doc: DocumentoItem): void {
    if (!this.puedeEditarDocumento(doc)) return;
    this.closeMenu();
    this.openNuevoDocModal('Editar Documento', doc);
  }

  /* CRUD: Eliminar */
  protected async eliminarDocumento(doc: DocumentoItem): Promise<void> {
    if (!this.puedeEliminarDocumento(doc)) return;
    this.closeMenu();
    const alert = await this.alertCtrl.create({
      header: 'Confirmar eliminación',
      message: `¿Estás seguro de eliminar "${doc.nombre}"? Esta acción no se puede deshacer.`,
      buttons: [
        { text: 'Cancelar', role: 'cancel' },
        {
          text: 'Eliminar',
          role: 'destructive',
          handler: async () => {
            try {
              await lastValueFrom(this.api.delete<void>(`/api/documentos/${doc.id}`));
              this.documentos.update(list => list.filter(d => d.id !== doc.id));
              if (this.selectedDoc()?.id === doc.id) this.selectedDoc.set(null);
            } catch {
              const errAlert = await this.alertCtrl.create({
                header: 'Error',
                message: 'No se pudo eliminar el documento',
                buttons: ['OK'],
              });
              await errAlert.present();
            }
          },
        },
      ],
    });
    await alert.present();
  }

  /* CRUD: Descargar */
  protected descargarDocumento(doc: DocumentoItem): void {
    if (!this.puedeDescargarDocumento(doc)) return;
    const descargaUrl = this.urlDescarga(doc);
    if (!descargaUrl) return;
    this.closeMenu();
    const revision = this.sessionRevision, usuarioId = this.authService.currentUser()?.id;
    this.api.getBlob(descargaUrl).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (blob) => {
        if (this.esRespuestaDeSesionActual(revision, usuarioId)) this.guardarDescarga(blob, doc.nombre);
      },
      error: async () => {
        if (!this.esRespuestaDeSesionActual(revision, usuarioId)) return;
        const alert = await this.alertCtrl.create({ header: 'Error', message: 'No se pudo descargar el archivo', buttons: ['OK'] });
        await alert.present();
      },
    });
  }

  /** Abre el archivo adjunto original en una pestaña nueva. */
  protected abrirDocumentoCompleto(): void {
    const doc = this.selectedDoc();
    if (!doc || !this.puedeDescargarDocumento(doc)) return;
    const descargaUrl = this.urlDescarga(doc);
    if (!descargaUrl) return;

    const revision = this.sessionRevision, usuarioId = this.authService.currentUser()?.id;
    const fileWindow = this.fileActions.native ? null : window.open('', '_blank');
    this.api.getBlob(descargaUrl).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (blob) => {
        if (!this.esRespuestaDeSesionActual(revision, usuarioId)) { fileWindow?.close(); return; }
        if (this.fileActions.native) { void this.fileActions.open(blob, doc.nombre); return; }
        const url = URL.createObjectURL(blob);
        if (fileWindow) {
          fileWindow.location.href = url;
          window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
        } else {
          window.open(url, '_blank', 'noopener');
          window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
        }
      },
      error: async () => {
        fileWindow?.close();
        if (!this.esRespuestaDeSesionActual(revision, usuarioId)) return;
        const alert = await this.alertCtrl.create({
          header: 'Error',
          message: 'No se pudo abrir el archivo.',
          buttons: ['OK'],
        });
        await alert.present();
      },
    });
  }

  /* Solicitar Aprobacion */
  protected async abrirSolicitudAprobacion(): Promise<void> {
    if (this.preparandoSolicitud() || this.guardandoSolicitud()) return;
    const doc = this.selectedDoc();
    if (!doc) { void this.mostrarErrorAprobacion('Selecciona primero el documento que deseas enviar a aprobación.'); return; }
    if (!this.esDocumentoGestionable(doc)) { void this.mostrarErrorAprobacion('Este adjunto se gestiona desde su tarea de origen.'); return; }
    if (!this.can('Ver') || !this.puedeEditarDocumento(doc)) {
      void this.mostrarErrorAprobacion('No tienes permiso para solicitar la aprobación de este documento.'); return;
    }
    if (!doc.archivoRuta) { void this.mostrarErrorAprobacion('Adjunta un archivo antes de solicitar aprobación.'); return; }
    const revision = this.sessionRevision, usuarioId = this.authService.currentUser()?.id;
    this.preparandoSolicitud.set(true);
    try {
      const historial = await this.obtenerHistorial(doc);
      if (!historial?.archivoActualDisponible || !historial.documento.versionActualId) {
        if (historial) void this.mostrarErrorAprobacion('El archivo actual no está disponible. Vuelve a adjuntarlo antes de solicitar aprobación.');
        return;
      }
      const usuarios = await lastValueFrom(this.api.get<UsuarioSeleccionableRespuesta[]>('/api/usuarios/seleccionables').pipe(takeUntilDestroyed(this.destroyRef)));
      if (!this.esRespuestaDeSesionActual(revision, usuarioId) || this.selectedDoc()?.id !== doc.id) return;
      this.versionSolicitudId.set(historial.documento.versionActualId);
      this.versionSolicitudLabel.set(historial.documento.version);
      this.aprobadorSeleccionadoId.set(null); this.comentarioSolicitud.set('');
      this.aprobadoresDisponibles.set(usuarios.filter(u => u.activo !== false && u.bloqueado !== true).map(u => ({ id: Number(u.id), nombre: [u.nombre, u.segundoNombre, u.apellido].filter(Boolean).join(' '), correo: u.correo || '', cargo: u.cargo || '' })));
      this.solicitudAprobacionOpen.set(true);
    } catch (error: unknown) {
      if (this.esRespuestaDeSesionActual(revision, usuarioId)) void this.mostrarErrorAprobacion(mensajeErrorApi(error, 'No se pudo cargar la lista de aprobadores.'));
    } finally {
      if (this.esRespuestaDeSesionActual(revision, usuarioId)) this.preparandoSolicitud.set(false);
    }
  }

  protected cerrarSolicitudAprobacion(): void { if (!this.guardandoSolicitud()) this.solicitudAprobacionOpen.set(false); }
  protected seleccionarAprobador(valor: string): void { this.aprobadorSeleccionadoId.set(valor ? parseInt(valor, 10) : null); }

  protected solicitarAprobacion(): void {
    if (this.guardandoSolicitud()) return;
    this.closeMenu();
    const doc = this.selectedDoc();
    if (!doc) return;
    const aprobadorId = this.aprobadorSeleccionadoId();
    if (!aprobadorId) { this.mostrarErrorAprobacion('Selecciona un aprobador para continuar.'); return; }
    const versionId = this.versionSolicitudId();
    if (!versionId) { void this.mostrarErrorAprobacion('Consulta la versión actual antes de enviar la solicitud.'); return; }
    const revision = this.sessionRevision, usuarioId = this.authService.currentUser()?.id;
    this.guardandoSolicitud.set(true);
    this.api.patch<DocumentoItem>(`/api/documentos/${doc.id}/solicitar-aprobacion`, { aprobadorId, comentario: this.comentarioSolicitud(), versionId }).pipe(
      finalize(() => { if (this.esRespuestaDeSesionActual(revision, usuarioId)) this.guardandoSolicitud.set(false); }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: (updated) => {
        if (!this.esRespuestaDeSesionActual(revision, usuarioId)) return;
        this.documentos.update(list => list.map(d => d.id === doc.id ? { ...d, ...updated } : d));
        this.selectedDoc.update(d => d?.id === doc.id ? { ...d, ...updated } : d); this.solicitudAprobacionOpen.set(false);
        this.historial.set(null); if (this.historialOpen()) void this.obtenerHistorial(updated, true);
      },
      error: async (response) => {
        const alert = await this.alertCtrl.create({
          header: 'Error',
          message: response?.error?.error || 'No se pudo enviar la solicitud. Intente de nuevo.',
          buttons: ['OK'],
        });
        alert.present();
      },
    });
  }

  private async mostrarErrorAprobacion(message: string): Promise<void> {
    const alert = await this.alertCtrl.create({
      header: 'Error',
      message,
      cssClass: 'documento-feedback-alert',
      buttons: [{ text: 'Entendido', cssClass: 'documento-feedback-accept' }],
    });
    await alert.present();
  }

  protected resolverAprobacion(doc: DocumentoItem, decision: 'aprobar' | 'rechazar'): void {
    if (!this.esDocumentoGestionable(doc)) return;
    this.closeMenu();
    const solicitud = doc.solicitudAprobacion;
    if (!solicitud?.versionId || this.resolviendoSolicitud().has(doc.id)) {
      if (!solicitud?.versionId) void this.mostrarErrorAprobacion('La solicitud anterior no tiene una versión verificable. Debe enviarse una nueva solicitud del archivo actual.');
      return;
    }
    const revision = this.sessionRevision, usuarioId = this.authService.currentUser()?.id;
    this.resolviendoSolicitud.update(ids => new Set([...ids, doc.id]));
    this.api.patch<DocumentoItem>(`/api/documentos/${doc.id}/resolver-aprobacion`, { decision, solicitudId: solicitud.id, versionId: solicitud.versionId }).pipe(
      finalize(() => this.resolviendoSolicitud.update(ids => { const nuevos = new Set(ids); nuevos.delete(doc.id); return nuevos; })),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: (updated) => {
        if (!this.esRespuestaDeSesionActual(revision, usuarioId)) return;
        this.documentos.update(list => list.map(d => d.id === doc.id ? { ...d, ...updated } : d));
        this.selectedDoc.update(d => d?.id === doc.id ? { ...d, ...updated } : d);
        this.historial.set(null); if (this.historialOpen()) void this.obtenerHistorial(updated, true);
      },
      error: async (response) => {
        const alert = await this.alertCtrl.create({ header: 'No se pudo resolver', message: response?.error?.error || 'No tienes autorización para resolver esta solicitud.', buttons: ['OK'] });
        await alert.present();
      },
    });
  }

  protected esAprobadorSeleccionado(doc: DocumentoItem): boolean {
    return doc.solicitudAprobacion?.estado === 'PENDIENTE'
      && !!doc.solicitudAprobacion.versionId && doc.solicitudAprobacion.versionId === doc.versionActualId
      && (this.authService.isAdmin() || Number(this.authService.currentUser()?.id) === Number(doc.solicitudAprobacion.aprobadorId));
  }

  /* Layout */
  protected onMenuToggle(): void {
    this.authService.sidebarCollapsed.update(v => !v);
  }

  protected onLogout(): void {
    this.authService.logout();
  }
}
