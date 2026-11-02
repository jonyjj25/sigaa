import { FileActionsService } from '../../services/file-actions.service';
import { Component, inject, OnInit, signal, computed, effect, untracked, ChangeDetectionStrategy, DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { finalize, forkJoin } from 'rxjs';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import {
  IonIcon, IonButton,
  IonModal, IonHeader, IonToolbar, IonTitle, IonButtons,
  AlertController,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import {
  clipboardOutline, checkmarkCircleOutline, timeOutline, alertCircleOutline,
  addOutline, searchOutline,
  eyeOutline, createOutline, closeOutline,
  ellipsisVerticalOutline, menuOutline, notificationsOutline, logOutOutline,
  chevronBackOutline, chevronForwardOutline,
  flagOutline, documentTextOutline, downloadOutline,
  folderOutline, personOutline, calendarOutline,
  checkboxOutline, squareOutline, checkmarkDoneOutline, personAddOutline,
} from 'ionicons/icons';
import { DashboardSidebarComponent } from '../../components/dashboard-sidebar/dashboard-sidebar.component';
import { DashboardTopbarComponent } from '../../components/dashboard-topbar/dashboard-topbar.component';
import { DashboardService } from '../../services/dashboard.service';
import { AuthService } from '../../services/auth.service';
import { ApiService, UsuarioSeleccionableRespuesta, mensajeErrorApi } from '../../services/api.service';
import { Unidad } from '../config/models/config.models';
import { mapTareaRespuesta, prepararTareaMultipart } from './tareas-data';
import { Notification } from '../../models/dashboard.models';
import { Tarea, TareaAdjunto, TareaEstado, TareaPrioridad, TareaTipo, TareaRespuesta, TareaGuardarRequest, TareaFormularioAdjunto, TareaMovimiento } from './tareas.models';

interface UsuarioTarea {
  id: number;
  nombre: string;
}

@Component({
  selector: 'app-tareas',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    DashboardSidebarComponent, DashboardTopbarComponent,
    IonIcon, IonButton,
    IonModal, IonHeader, IonToolbar, IonTitle, IonButtons,
  ],
  templateUrl: './tareas.page.html',
  styleUrls: ['./tareas.page.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TareasPage implements OnInit {
  private readonly fileActions = inject(FileActionsService);
  private readonly dashboardService = inject(DashboardService);
  protected readonly authService = inject(AuthService);
  private readonly apiService = inject(ApiService);
  private readonly alertCtrl = inject(AlertController);
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute);
  private readonly sanitizer = inject(DomSanitizer);
  protected readonly guardandoTarea = signal(false);
  protected readonly cargandoAdjunto = signal(false);
  protected readonly vistaAdjunto = signal<{ nombre: string; tipo: string; url: string; recurso: SafeResourceUrl } | null>(null);
  private tareaEnlazadaId: string | null = null;

  protected readonly menuItems = computed(() => this.dashboardService.getMenuItems());
  protected readonly notifications = signal<Notification[]>([]);

  protected readonly tareas = signal<Tarea[]>([]);
  protected readonly loading = signal(true);
  protected readonly loadError = signal(false);
  protected readonly selectedTarea = signal<Tarea | null>(null);
  protected readonly historialTarea = signal<TareaMovimiento[]>([]);
  protected readonly cargandoHistorial = signal(false);
  protected readonly errorHistorial = signal(false);
  private readonly recargaHistorial = signal(0);
  protected readonly selectedIds = signal<Set<string>>(new Set());

  protected readonly filtroSearch = signal('');

  protected readonly tabActivo = signal<TareaEstado | 'todas'>('todas');

  protected readonly openMenuId = signal<string | null>(null);
  protected readonly showModal = signal(false);
  protected readonly showAsignarModal = signal(false);

  /* Checklist editable */
  protected readonly nuevoChecklist = signal<{ id: string; texto: string; completado: boolean }[]>([
    { id: this.nextChecklistId(), texto: '', completado: false },
  ]);

  private checkIdCounter = 0;
  private nextChecklistId(): string { return `chk-${++this.checkIdCounter}`; }

  protected addChecklistItem(): void {
    this.nuevoChecklist.update(list => [...list, { id: this.nextChecklistId(), texto: '', completado: false }]);
  }

  protected removeChecklistItem(id: string): void {
    this.nuevoChecklist.update(list => list.filter(i => i.id !== id));
  }

  protected isInChecklist(texto: string): boolean {
    return this.nuevoChecklist().some(c => c.texto === texto);
  }

  protected toggleChecklistDefault(texto: string): void {
    const exists = this.nuevoChecklist().find(c => c.texto === texto);
    if (exists) {
      this.nuevoChecklist.update(list => list.filter(c => c.texto !== texto));
    } else {
      this.nuevoChecklist.update(list => [...list, { id: this.nextChecklistId(), texto, completado: false }]);
    }
  }

  protected updateChecklistText(id: string, texto: string): void {
    this.nuevoChecklist.update(list => list.map(i => i.id === id ? { ...i, texto } : i));
  }

  /* Nueva Tarea form signals */
  protected readonly formTitulo = signal('');
  protected readonly formDescripcion = signal('');
  protected readonly formResponsables = signal<number[]>([]);
  protected readonly formPrioridad = signal<TareaPrioridad>('media');
  protected readonly formFechaInicio = signal('');
  protected readonly formFechaFinalizacion = signal('');
  protected readonly formAdjuntos = signal<TareaFormularioAdjunto[]>([]);
  protected readonly editandoId = signal<string | null>(null);

  protected readonly DEFAULT_CHECKLIST_ITEMS = [
    'Planificar la tarea',
    'Investigar requisitos',
    'Ejecutar la tarea',
    'Revisar el trabajo',
    'Validar con el solicitante',
    'Cerrar la tarea',
  ];
  protected readonly formSubmitted = signal(false);

  protected readonly fechasValidas = computed(() => {
    const inicio = this.formFechaInicio();
    const finalizacion = this.formFechaFinalizacion();
    return inicio !== '' && (finalizacion === '' || finalizacion >= inicio);
  });
  protected readonly formValido = computed(() =>
    this.formTitulo().trim() !== ''
      && this.formResponsables().length > 0 && this.fechasValidas()
  );

  /* Asignar Tarea modal — asignar a tarea existente */
  protected readonly asignarTareaId = signal('');
  protected readonly asignarResponsables = signal<number[]>([]);
  protected readonly asignarPrioridad = signal<'alta' | 'media' | 'baja'>('media');

  protected readonly tareasSinAsignar = computed(() =>
    this.tareas().filter(t => t.estado !== 'completada' && t.estado !== 'vencida')
  );

  protected readonly usuarios = signal<UsuarioTarea[]>([]);
  protected readonly unidadesDisponibles = signal<string[]>([]);
  protected readonly formUnidades = signal<string[]>([]);
  protected toggleUnidad(nombre: string): void {
    this.formUnidades.update(list =>
      list.includes(nombre) ? list.filter(u => u !== nombre) : [...list, nombre]
    );
  }

  protected isUnidadSelected(nombre: string): boolean {
    return this.formUnidades().includes(nombre);
  }

  protected openAsignarModal(): void {
    this.asignarTareaId.set('');
    this.asignarResponsables.set([]);
    this.asignarPrioridad.set('media');
    this.showAsignarModal.set(true);
  }

  protected closeAsignarModal(): void {
    this.showAsignarModal.set(false);
  }

  protected readonly currentPage = signal(1);
  protected get pageSize(): number { return this.authService.preferences().recordsPerPage; }

  protected readonly tareasFiltradas = computed(() => {
    let items = this.tareas();
    const tab = this.tabActivo();
    const search = this.filtroSearch().toLowerCase().trim();

    if (tab !== 'todas') {
      items = items.filter(t => t.estado === tab);
    }
    if (search) {
      items = items.filter(t =>
        t.titulo.toLowerCase().includes(search) ||
        t.asignado.toLowerCase().includes(search) ||
        t.codigo.toLowerCase().includes(search)
      );
    }
    return items;
  });

  protected readonly totalCount = computed(() => this.tareas().length);
  protected readonly porHacerCount = computed(() => this.tareas().filter(t => t.estado === 'por_hacer').length);
  protected readonly enProgresoCount = computed(() => this.tareas().filter(t => t.estado === 'en_progreso').length);
  protected readonly completadasCount = computed(() => this.tareas().filter(t => t.estado === 'completada').length);
  protected readonly vencidasCount = computed(() => this.tareas().filter(t => t.estado === 'vencida').length);

  protected readonly paginatedTareas = computed(() => {
    const start = (this.currentPage() - 1) * this.pageSize;
    return this.tareasFiltradas().slice(start, start + this.pageSize);
  });

  protected readonly totalPages = computed(() =>
    Array.from({ length: Math.ceil(this.tareasFiltradas().length / this.pageSize) }, (_, i) => i + 1)
  );

  protected readonly allSelected = computed(() =>
    this.paginatedTareas().length > 0 && this.paginatedTareas().every(t => this.selectedIds().has(t.id))
  );

  protected readonly Math = Math;

  constructor() {
    effect(onCleanup => {
      const tarea = this.selectedTarea();
      this.recargaHistorial();
      untracked(() => {
        this.historialTarea.set([]);
        this.errorHistorial.set(false);
        this.cargandoHistorial.set(!!tarea);
        if (!tarea) return;
        const subscription = this.apiService.get<TareaMovimiento[]>(`/api/tareas/${tarea.id}/historial`)
          .pipe(finalize(() => this.cargandoHistorial.set(false))).subscribe({
            next: movimientos => this.historialTarea.set(movimientos),
            error: () => this.errorHistorial.set(true),
          });
        // Cancela la petición anterior al cambiar o cerrar la tarea, y al destruir la vista.
        onCleanup(() => subscription.unsubscribe());
      });
    });
    this.destroyRef.onDestroy(() => this.cerrarVistaAdjunto());
    addIcons({
      clipboardOutline, checkmarkCircleOutline, timeOutline, alertCircleOutline,
      addOutline, searchOutline,
      eyeOutline, createOutline, closeOutline,
      ellipsisVerticalOutline, menuOutline, notificationsOutline, logOutOutline,
      chevronBackOutline, chevronForwardOutline,
      flagOutline, documentTextOutline, downloadOutline,
      folderOutline, personOutline, calendarOutline,
      checkboxOutline, squareOutline, checkmarkDoneOutline, personAddOutline,
    });
  }

  ngOnInit(): void {
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      this.tareaEnlazadaId = params.get('tarea');
      this.seleccionarTareaEnlazada();
    });
    this.loadTareas();
    this.loadUsuarios();
    this.loadUnidades();
    this.dashboardService.getNotificaciones().pipe(takeUntilDestroyed(this.destroyRef)).subscribe(d => this.notifications.set(d));
  }

  private loadUsuarios(): void {
    if (!this.can('Asignar')) return;
    this.apiService.get<UsuarioSeleccionableRespuesta[]>('/api/usuarios/seleccionables').pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (data) => this.usuarios.set(data
        .map((u) => ({ id: Number(u.id), nombre: [u.nombre, u.segundoNombre, u.apellido].filter(Boolean).join(' ').trim() }))
        .filter((u: UsuarioTarea) => Number.isFinite(u.id) && !!u.nombre)),
      error: () => this.usuarios.set([]),
    });
  }

  protected can(accion: string): boolean {
    return this.authService.hasPermission('Tareas', accion);
  }

  private loadUnidades(): void {
    this.apiService.get<Pick<Unidad, 'nombre'>[]>('/api/unidades').pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (data) => this.unidadesDisponibles.set(data.map((u) => u.nombre).filter(Boolean)),
      error: () => this.unidadesDisponibles.set([]),
    });
  }

  private loadTareas(): void {
    this.loading.set(true);
    this.loadError.set(false);
    this.apiService.get<TareaRespuesta[]>('/api/tareas').pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (data) => {
        const mapped = data.map(mapTareaRespuesta);
        this.tareas.set(mapped);
        this.selectedTarea.set(mapped[0] ?? null);
        this.seleccionarTareaEnlazada();
        this.loading.set(false);
      },
      error: () => {
        this.tareas.set([]);
        this.selectedTarea.set(null);
        this.loadError.set(true);
        this.loading.set(false);
      },
    });
  }

  protected retryLoad(): void { this.loadTareas(); }

  private mapTarea(t: TareaRespuesta): Tarea { return mapTareaRespuesta(t); }

  protected estadoLabel(estado: TareaEstado): string {
    switch (estado) {
      case 'por_hacer': return 'Por hacer';
      case 'en_progreso': return 'En progreso';
      case 'completada': return 'Completada';
      case 'vencida': return 'Vencida';
    }
  }

  protected estadoColor(estado: TareaEstado): string {
    switch (estado) {
      case 'por_hacer': return 'var(--state-warning-text)';
      case 'en_progreso': return 'var(--state-info-text)';
      case 'completada': return 'var(--state-success-text)';
      case 'vencida': return 'var(--state-danger-text)';
    }
  }

  protected estadoBg(estado: TareaEstado): string {
    switch (estado) {
      case 'por_hacer': return 'var(--state-warning-bg)';
      case 'en_progreso': return 'var(--state-info-bg)';
      case 'completada': return 'var(--state-success-bg)';
      case 'vencida': return 'var(--state-danger-bg)';
    }
  }

  protected prioridadLabel(p: TareaPrioridad): string {
    switch (p) {
      case 'alta': return 'Alta';
      case 'media': return 'Media';
      case 'baja': return 'Baja';
    }
  }

  protected prioridadColor(p: TareaPrioridad): string {
    switch (p) {
      case 'alta': return '#ef4444';
      case 'media': return '#f59e0b';
      case 'baja': return '#22c55e';
    }
  }

  protected prioridadBg(p: TareaPrioridad): string {
    switch (p) {
      case 'alta': return '#fef2f2';
      case 'media': return '#fef3c7';
      case 'baja': return '#f0fdf4';
    }
  }

  protected selectTarea(item: Tarea): void {
    this.selectedTarea.set(item);
    this.openMenuId.set(null);
  }

  protected closeDetail(): void {
    this.selectedTarea.set(null);
  }

  protected reintentarHistorial(): void { this.recargaHistorial.update(value => value + 1); }

  protected tipoMovimiento(accion: string): string {
    const tipos: Record<string, string> = {
      CAMBIO_RESPONSABLE: 'Cambio de responsable',
      CAMBIO_ESTADO: 'Cambio de estado',
      CAMBIO_AVANCE: 'Cambio de avance',
    };
    return tipos[accion] ?? accion;
  }

  protected setTab(tab: TareaEstado | 'todas'): void {
    this.tabActivo.set(tab);
    this.currentPage.set(1);
    this.sincronizarSeleccionConFiltros();
  }

  private seleccionarTareaEnlazada(): void {
    if (!this.tareaEnlazadaId) return;
    const tarea = this.tareas().find(item => item.id === this.tareaEnlazadaId);
    if (!tarea) return;
    this.filtroSearch.set('');
    this.tabActivo.set('todas');
    const indice = this.tareas().findIndex(item => item.id === tarea.id);
    this.currentPage.set(Math.floor(indice / this.pageSize) + 1);
    this.selectTarea(tarea);
  }

  protected sincronizarSeleccionConFiltros(): void {
    const visibles = this.tareasFiltradas();
    const seleccionada = this.selectedTarea();
    if (!seleccionada || !visibles.some(t => t.id === seleccionada.id)) {
      this.selectedTarea.set(visibles[0] ?? null);
    }
  }

  protected toggleMenu(id: string): void {
    this.openMenuId.update(v => v === id ? null : id);
  }

  protected closeMenu(): void {
    this.openMenuId.set(null);
  }

  protected toggleSelect(id: string): void {
    this.selectedIds.update(s => {
      const next = new Set(s);
      if (next.has(id)) { next.delete(id); } else { next.add(id); }
      return next;
    });
  }

  protected toggleSelectAll(): void {
    this.selectedIds.update(s => {
      const pageIds = this.paginatedTareas().map(t => t.id);
      const allOnPage = pageIds.every(id => s.has(id));
      const next = new Set(s);
      if (allOnPage) {
        pageIds.forEach(id => next.delete(id));
      } else {
        pageIds.forEach(id => next.add(id));
      }
      return next;
    });
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

  private origenEdicion: { tipo: string; proyecto: string } | null = null;

  protected openNewModal(): void {
    this.origenEdicion = null;
    this.checkIdCounter = 0;
    this.editandoId.set(null);
    this.formTitulo.set('');
    this.formDescripcion.set('');
    // Todo creador queda como responsable inicial; luego puede agregar participantes.
    this.formResponsables.set(this.authService.currentUser()?.id
      ? [this.authService.currentUser()!.id!] : []);
    this.formPrioridad.set('media');
    this.formFechaInicio.set(this.fechaLocalActual());
    this.formFechaFinalizacion.set('');
    this.formAdjuntos.set([]);
    this.formUnidades.set([]);
    this.formSubmitted.set(false);
    this.nuevoChecklist.set([]);
    this.showModal.set(true);
  }

  protected openEditModal(item: Tarea): void {
    this.origenEdicion = { tipo: item.tipo, proyecto: item.proyecto };
    this.checkIdCounter = 0;
    this.editandoId.set(item.id);
    this.formTitulo.set(item.titulo);
    this.formDescripcion.set(item.descripcion);
    this.formResponsables.set([
      ...(item.asignadoUsuarioId == null ? [] : [item.asignadoUsuarioId]),
      ...item.participanteUsuarioIds,
    ]);
    this.formPrioridad.set(item.prioridad);
    this.formFechaInicio.set(item.fechaCreacion || this.fechaLocalActual());
    this.formFechaFinalizacion.set(item.fechaLimite);
    this.formAdjuntos.set(item.adjuntos.map(a => ({ ...a })));
    this.formUnidades.set(item.unidades ? item.unidades.split(',').map(s => s.trim()).filter(Boolean) : []);
    this.formSubmitted.set(false);
    this.nuevoChecklist.set(
      item.checklist.length > 0
        ? item.checklist.map(c => ({ id: c.id, texto: c.texto, completado: c.completado }))
        : [{ id: this.nextChecklistId(), texto: '', completado: false }]
    );
    this.showModal.set(true);
  }

  protected closeModal(): void {
    if (this.guardandoTarea()) return;
    this.showModal.set(false);
    this.editandoId.set(null);
  }

  /* ── Tag responsables (crear/editar) ── */
  protected agregarResponsableForm(event: Event): void {
    const select = event.target as HTMLSelectElement;
    const value = Number(select.value);
    if (!Number.isFinite(value) || value <= 0) return;
    this.formResponsables.update(list => list.includes(value) ? list : [...list, value]);
    select.value = '';
  }

  protected quitarResponsableForm(usuarioId: number): void {
    this.formResponsables.update(list => list.filter(id => id !== usuarioId));
  }

  /* ── Tag responsables (asignar) ── */
  protected agregarResponsableAsignar(event: Event): void {
    const select = event.target as HTMLSelectElement;
    const value = Number(select.value);
    if (!Number.isFinite(value) || value <= 0) return;
    this.asignarResponsables.update(list => list.includes(value) ? list : [...list, value]);
    select.value = '';
  }

  protected quitarResponsableAsignar(usuarioId: number): void {
    this.asignarResponsables.update(list => list.filter(id => id !== usuarioId));
  }

  protected nombreUsuario(usuarioId: number): string {
    if (usuarioId === this.authService.currentUser()?.id) return this.authService.currentUser()?.nombre || 'Usuario actual';
    return this.usuarios().find(usuario => usuario.id === usuarioId)?.nombre || 'Usuario seleccionado';
  }

  /* ── Adjuntos ── */
  protected agregarAdjunto(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = input?.files;
    if (!files || files.length === 0) return;
    if (this.guardandoTarea()) return;
    const nuevos: (TareaAdjunto & { archivo: File })[] = [];
    let rejected = false;
    for (let i = 0; i < files.length; i++) {
      const f = files[i];
      if (!f.size || f.size > 10 * 1024 * 1024 || !/\.(pdf|png|jpe?g|gif|webp|docx?|xlsx?|pptx?|txt|csv|zip|rar)$/i.test(f.name)) {
        rejected = true;
        continue;
      }
      const size = f.size < 1024
        ? `${f.size} B`
        : f.size < 1024 * 1024
          ? `${(f.size / 1024).toFixed(1)} KB`
          : `${(f.size / (1024 * 1024)).toFixed(1)} MB`;
      nuevos.push({ nombre: f.name, tamano: size, archivo: f });
    }
    this.formAdjuntos.update(list => [...list, ...nuevos]);
    input.value = '';
    if (rejected) void this.mostrarError('Archivo no admitido', 'Selecciona documentos, imágenes o archivos ZIP/RAR con contenido y de hasta 10 MB cada uno.');
  }

  protected quitarAdjunto(index: number): void {
    if (this.guardandoTarea()) return;
    this.formAdjuntos.update(list => list.filter((_, i) => i !== index));
  }

  protected guardarTarea(): void {
    this.formSubmitted.set(true);
    if (!this.formValido() || this.guardandoTarea()) return;

    const editId = this.editandoId();
    const nonEmptyChecklist = this.nuevoChecklist().filter(c => c.texto.trim());
    const responsables = this.formResponsables();
    const asignadoStr = responsables.map(id => this.nombreUsuario(id)).join(', ');

    const body: TareaGuardarRequest = {
      titulo: this.formTitulo(),
      descripcion: this.formDescripcion(),
      tipo: this.origenEdicion?.tipo ?? '',
      proyecto: this.origenEdicion?.proyecto ?? '',
      asignado: asignadoStr,
      asignadoUsuarioId: responsables[0] ?? null,
      participanteUsuarioIds: responsables.slice(1),
      prioridad: this.formPrioridad(),
      fechaCreacion: this.formFechaInicio(),
      fechaLimite: this.formFechaFinalizacion(),
      checklist: nonEmptyChecklist.map(c => ({ texto: c.texto, completado: c.completado })),

      unidades: this.formUnidades().join(', '),
    };

    const { data, totalBytes } = prepararTareaMultipart(body, this.formAdjuntos());
    if (totalBytes > 45 * 1024 * 1024) {
      void this.mostrarError('Demasiados archivos', 'Adjunta hasta 45 MB en cada guardado.');
      return;
    }

    this.guardandoTarea.set(true);
    if (editId) {
      this.apiService.put<TareaRespuesta>(`/api/tareas/${editId}`, data).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.guardandoTarea.set(false))).subscribe({
        next: (updated) => {
          this.tareas.update(list =>
            list.map(t => t.id === editId ? this.mapTarea(updated) : t)
          );
          this.selectedTarea.update(sel => sel?.id === editId ? this.mapTarea(updated) : sel);
          this.guardandoTarea.set(false);
          this.closeModal();
        },
        error: (response: unknown) => { void this.mostrarError('No se pudo guardar la tarea', mensajeErrorApi(response, 'Los cambios no fueron aplicados. Intenta nuevamente.')); },
      });
    } else {
      this.apiService.post<TareaRespuesta>('/api/tareas', data).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.guardandoTarea.set(false))).subscribe({
        next: (created) => {
          const mapped = this.mapTarea(created);
          this.tareas.update(list => [mapped, ...list]);
          this.selectedTarea.set(mapped);
          this.guardandoTarea.set(false);
          this.closeModal();
        },
        error: (response: unknown) => { void this.mostrarError('No se pudo crear la tarea', mensajeErrorApi(response, 'Verifica los datos y archivos seleccionados y vuelve a intentarlo.')); },
      });
    }
  }

  private fechaLocalActual(): string {
    const fecha = new Date();
    const year = fecha.getFullYear();
    const month = String(fecha.getMonth() + 1).padStart(2, '0');
    const day = String(fecha.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  protected asignarTarea(): void {
    const tareaId = this.asignarTareaId();
    const nuevosResp = this.asignarResponsables();
    if (!tareaId || nuevosResp.length === 0) return;

    const body: Pick<TareaGuardarRequest, 'prioridad' | 'asignadoUsuarioId' | 'participanteUsuarioIds'> = {
      prioridad: this.asignarPrioridad(),
      asignadoUsuarioId: nuevosResp[0], participanteUsuarioIds: nuevosResp.slice(1),
    };

    this.apiService.patch<TareaRespuesta>(`/api/tareas/${tareaId}/asignacion`, body).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (updated) => {
        const mapped = this.mapTarea(updated);
        this.tareas.update(list => list.map(t => t.id === tareaId ? mapped : t));
        this.selectedTarea.update(sel => sel?.id === tareaId ? mapped : sel);
        this.closeAsignarModal();
      },
      error: () => { void this.mostrarError('No se pudo asignar la tarea', 'Verifica los permisos y los datos seleccionados.'); },
    });
  }

  protected importarTareas(): void {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.csv';
    input.addEventListener('change', (e) => this.handleFileImport(e));
    input.click();
  }

  private handleFileImport(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input?.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = () => {
      const text = reader.result as string;
      const lines = text.split('\n').filter(l => l.trim());
      if (lines.length < 2) return;

      const headers = lines[0].split(',').map(h => h.trim().replace(/"/g, '').toLowerCase());

      for (let i = 1; i < lines.length; i++) {
        const values = this.parseCSVLine(lines[i]);
        if (values.length !== headers.length) continue;

        const row: Record<string, string> = {};
        headers.forEach((h, idx) => { row[h] = values[idx]; });

        const tipo = (row['proyecto'] || '') as TareaTipo;
        const prioridad = (['alta', 'media', 'baja'].includes(row['prioridad']) ? row['prioridad'] : 'media') as TareaPrioridad;

        const body = {
          titulo: row['titulo'] || 'Sin título',
          descripcion: row['descripcion'] || '',
          proyecto: tipo,
          tipo,
          asignado: row['asignado'] || row['responsable'] || '',
          prioridad,
          fechaLimite: row['fechalimite'] || row['fecha_limite'] || '',
          checklist: [],
        };
        this.apiService.post<TareaRespuesta>('/api/tareas/importar', body).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
          next: (created) => {
            const mapped = this.mapTarea(created);
            this.tareas.update(list => [mapped, ...list]);
            if (!this.selectedTarea()) this.selectedTarea.set(mapped);
          },
          error: async (response) => {
            await this.mostrarError('No se pudo importar la tarea', response?.error?.error || 'Revisa el formato del archivo CSV y tus permisos.');
          },
        });
      }
    };
    reader.readAsText(file);
    input.value = '';
  }

  private parseCSVLine(line: string): string[] {
    const result: string[] = [];
    let current = '';
    let inQuotes = false;
    for (let i = 0; i < line.length; i++) {
      const ch = line[i];
      if (ch === '"') { inQuotes = !inQuotes; continue; }
      if (ch === ',' && !inQuotes) { result.push(current.trim()); current = ''; continue; }
      current += ch;
    }
    result.push(current.trim());
    return result;
  }

  /* ── Marcar Completadas (bulk) ── */

  protected marcarCompletadas(): void {
    const ids = this.selectedIds();
    if (ids.size === 0) return;
    const requests = Array.from(ids).map(id =>
      this.apiService.patch<TareaRespuesta>(`/api/tareas/${id}/estado`, { estado: 'completada', progreso: 100 })
    );
    forkJoin(requests).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.tareas.update(list =>
          list.map(t => ids.has(t.id)
            ? { ...t, estado: 'completada' as TareaEstado, progreso: 100, checklist: t.checklist.map(c => ({ ...c, completado: true })) }
            : t)
        );
        this.selectedTarea.update(t => t && ids.has(t.id)
          ? { ...t, estado: 'completada' as TareaEstado, progreso: 100, checklist: t.checklist.map(c => ({ ...c, completado: true })) }
          : t);
        this.selectedIds.set(new Set());
      },
      error: () => { void this.mostrarError('No se pudieron completar las tareas', 'Ningún cambio visual se conservará hasta que el servidor confirme la operación.'); },
    });
  }

  /* ── Cambiar estado ── */

  protected async cambiarEstado(item: Tarea): Promise<void> {
    const alert = await this.alertCtrl.create({
      header: 'Cambiar Estado',
      message: `Seleccione el nuevo estado para "${item.titulo}"`,
      inputs: [
        { label: 'Por hacer', type: 'radio', value: 'por_hacer', checked: item.estado === 'por_hacer' },
        { label: 'En progreso', type: 'radio', value: 'en_progreso', checked: item.estado === 'en_progreso' },
        { label: 'Completada', type: 'radio', value: 'completada', checked: item.estado === 'completada' },
        { label: 'Vencida', type: 'radio', value: 'vencida', checked: item.estado === 'vencida' },
      ],
      buttons: [
        { text: 'Cancelar', role: 'cancel' },
        {
          text: 'Cambiar',
          handler: (valor: TareaEstado) => {
            if (!valor) return;
            this.apiService.patch<TareaRespuesta>(`/api/tareas/${item.id}/estado`, { estado: valor }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
              next: (updated) => {
                const mapped = this.mapTarea(updated);
                this.tareas.update(list => list.map(t => t.id === item.id ? mapped : t));
                this.selectedTarea.update(sel => sel?.id === item.id ? mapped : sel);
              },
              error: () => { void this.mostrarError('No se pudo cambiar el estado', 'La tarea conserva su estado anterior.'); },
            });
          },
        },
      ],
    });
    alert.present();
  }

  /* ── Duplicar ── */

  protected duplicarTarea(item: Tarea): void {
    const body = {
      titulo: `${item.titulo} - DUPLICADO`,
      descripcion: item.descripcion,
      tipo: item.tipo,
      proyecto: item.proyecto,
      asignado: item.asignado,
      asignadoUsuarioId: item.asignadoUsuarioId,
      participanteUsuarioIds: item.participanteUsuarioIds,
      prioridad: item.prioridad,
      checklist: item.checklist.map((c) => ({ texto: c.texto, completado: false })),
    };
    this.apiService.post<TareaRespuesta>('/api/tareas', body).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (created) => {
        const mapped = this.mapTarea(created);
        this.tareas.update(list => [mapped, ...list]);
        if (!this.selectedTarea()) this.selectedTarea.set(mapped);
      },
      error: () => { void this.mostrarError('No se pudo duplicar la tarea', 'Verifica tus permisos e intenta nuevamente.'); },
    });
  }

  /* ── Eliminar ── */

  protected async eliminarTarea(item: Tarea): Promise<void> {
    const alert = await this.alertCtrl.create({
      header: 'Eliminar Tarea',
      message: `¿Está seguro de eliminar "${item.titulo}"?`,
      buttons: [
        { text: 'Cancelar', role: 'cancel' },
        {
          text: 'Eliminar',
          handler: () => {
            this.apiService.delete(`/api/tareas/${item.id}`).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
              next: () => {
                this.tareas.update(list => list.filter(t => t.id !== item.id));
                this.selectedTarea.update(sel => sel?.id === item.id ? null : sel);
                this.openMenuId.set(null);
              },
              error: async (response) => {
                const errorAlert = await this.alertCtrl.create({
                  header: 'No se pudo eliminar la tarea',
                  message: response?.error?.error || 'No tienes permiso para eliminar esta tarea o ya no existe.',
                  buttons: ['OK'],
                });
                await errorAlert.present();
              },
            });
          },
        },
      ],
    });
    alert.present();
  }

  /* ── Detail panel actions ── */

  protected marcarComoCompletada(item: Tarea): void {
    const allChecked = item.checklist.map(c => ({ ...c, completado: true }));
    this.apiService.patch<TareaRespuesta>(`/api/tareas/${item.id}/estado`, {
      estado: 'completada',
      progreso: 100,
      checklist: allChecked.map((c) => ({ texto: c.texto, completado: true })),
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (updated) => {
        const completed = { ...this.mapTarea(updated), estado: 'completada' as TareaEstado, progreso: 100, checklist: allChecked };
        this.tareas.update(list => list.map(t => t.id === item.id ? completed : t));
        this.selectedTarea.update(sel => sel?.id === item.id ? completed : sel);
      },
      error: () => { void this.mostrarError('No se pudo completar la tarea', 'La tarea conserva su progreso anterior.'); },
    });
  }

  protected toggleChecklist(tareaId: string, checklistId: string): void {
    this.apiService.patch<TareaRespuesta>(`/api/tareas/${tareaId}/checklist/${checklistId}`, {}).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (updated) => {
        const mapped = this.mapTarea(updated);
        this.tareas.update(list => list.map(t => t.id === tareaId ? mapped : t));
        this.selectedTarea.update(sel => sel?.id === tareaId ? mapped : sel);
      },
      error: () => { void this.mostrarError('No se pudo actualizar el checklist', 'El cambio no fue guardado. Intenta nuevamente.'); },
    });
  }

  protected puedePrevisualizar(adjunto: TareaAdjunto): boolean {
    return !!adjunto.disponible && ['application/pdf', 'image/png', 'image/jpeg', 'image/gif', 'image/webp'].includes(adjunto.tipoContenido || '');
  }

  protected abrirAdjunto(tareaId: string, adjunto: TareaAdjunto, descargar = false): void {
    if (!adjunto.id || !adjunto.disponible || this.cargandoAdjunto()) return;
    this.cargandoAdjunto.set(true);
    this.apiService.getBlob(`/api/tareas/${tareaId}/adjuntos/${adjunto.id}/descargar`)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.cargandoAdjunto.set(false)))
      .subscribe({
        next: blob => {
          if (descargar || this.fileActions.native || !this.puedePrevisualizar({ ...adjunto, tipoContenido: blob.type })) {
            void this.fileActions.save(blob, adjunto.nombre);
          } else {
            const url = URL.createObjectURL(blob);
            this.cerrarVistaAdjunto();
            this.vistaAdjunto.set({
              nombre: adjunto.nombre, tipo: blob.type, url,
              recurso: this.sanitizer.bypassSecurityTrustResourceUrl(url)
            });
          }
        },
        error: () => { void this.mostrarError('Archivo no disponible', 'No se pudo abrir el archivo. Comprueba tu conexión y tus permisos. Si falta el original, vuelve a adjuntarlo al editar la tarea.'); },
      });
  }

  protected cerrarVistaAdjunto(): void {
    const vista = this.vistaAdjunto();
    if (vista) URL.revokeObjectURL(vista.url);
    this.vistaAdjunto.set(null);
  }

  protected descargarVistaAdjunto(): void {
    const vista = this.vistaAdjunto();
    if (vista) this.descargarUrl(vista.url, vista.nombre);
  }

  private descargarUrl(url: string, nombre: string): void {
    void this.fileActions.saveLocalUrl(url, nombre);
  }

  private async mostrarError(header: string, message: string): Promise<void> {
    const alert = await this.alertCtrl.create({ header, message, buttons: ['Aceptar'] });
    await alert.present();
  }
}
