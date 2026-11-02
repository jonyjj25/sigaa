import { FileActionsService } from '../../services/file-actions.service';
import { Component, inject, OnInit, signal, computed, ChangeDetectionStrategy, DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import {
  IonIcon, IonButton,
  IonModal, IonHeader, IonToolbar, IonTitle, IonButtons,
  AlertController, ToastController,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import {
  cubeOutline, searchOutline, filterOutline, downloadOutline,
  addOutline, eyeOutline, createOutline, timeOutline,
  chevronBackOutline, chevronForwardOutline, closeOutline,
  menuOutline, notificationsOutline, logOutOutline, saveOutline,
  locationOutline, pricetagOutline, calendarOutline, personOutline,
  businessOutline, documentTextOutline, checkmarkCircleOutline,
  constructOutline, warningOutline, trashOutline,
} from 'ionicons/icons';
import { DashboardSidebarComponent } from '../../components/dashboard-sidebar/dashboard-sidebar.component';
import { DashboardTopbarComponent } from '../../components/dashboard-topbar/dashboard-topbar.component';
import { DashboardService } from '../../services/dashboard.service';
import { AuthService } from '../../services/auth.service';
import { ApiService } from '../../services/api.service';
import { Notification } from '../../models/dashboard.models';
import { InventarioItem, InventarioEstado, InventarioActivoRequest, InventarioAsignacion, InventarioAsignables, AuditLogEntry } from './inventario.models';
import { INSTITUTIONAL_BLUE } from '../../shared/ui-colors';

@Component({
  selector: 'app-inventario',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    DashboardSidebarComponent, DashboardTopbarComponent,
    IonIcon, IonButton,
    IonModal, IonHeader, IonToolbar, IonTitle, IonButtons,
  ],
  templateUrl: './inventario.page.html',
  styleUrls: ['./inventario.page.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InventarioPage implements OnInit {
  private readonly fileActions = inject(FileActionsService);
  private readonly dashboardService = inject(DashboardService);
  protected readonly authService = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly alertCtrl = inject(AlertController);
  private readonly toastCtrl = inject(ToastController);

  private readonly destroyRef = inject(DestroyRef);
  protected readonly menuItems = computed(() => this.dashboardService.getMenuItems());
  protected readonly notifications = signal<Notification[]>([]);
  protected readonly loading = signal(true);
  protected readonly loadError = signal(false);
  protected readonly saving = signal(false);

  protected readonly activos = signal<InventarioItem[]>([]);
  protected readonly selectedActivo = signal<InventarioItem | null>(null);
  protected readonly filtroSearch = signal('');
  protected readonly filtroCategoria = signal('Todas');
  protected readonly filtroEstado = signal('Todos');
  protected readonly filtroUbicacion = signal('Todas');
  protected readonly filtroUnidad = signal('Todas');
  protected readonly showModal = signal(false);
  protected readonly editandoId = signal<string | null>(null);
  protected readonly editModalTitle = computed(() =>
    this.editandoId() ? 'Editar Activo' : 'Nuevo Activo'
  );

  /* Form signals */
  protected readonly formNombre = signal('');
  protected readonly formCategoria = signal('');
  protected readonly formMarcaModelo = signal('');
  protected readonly formSerie = signal('');
  protected readonly formSerieInstitucional = signal('');
  protected readonly formFechaAdquisicion = signal('');
  protected readonly formUbicacion = signal('');
  protected readonly formUnidad = signal('');
  protected readonly formTipoAsignacion = signal<InventarioItem['tipoAsignacion']>('SIN_ASIGNAR');
  protected readonly formAsignadoUsuarioId = signal<number | null>(null);
  protected readonly formAsignadoUnidadId = signal<number | null>(null);
  protected readonly asignables = signal<InventarioAsignables>({ personas: [], unidades: [] });
  protected readonly asignablesError = signal(false);
  protected readonly asignablesLoading = signal(false);
  private readonly personaActual = signal<{ id: number; nombre: string } | null>(null);
  private readonly unidadActual = signal<{ id: number; nombre: string } | null>(null);
  protected readonly personasAsignables = computed(() => {
    const opciones = this.asignables().personas;
    const actual = this.personaActual();
    return actual && !opciones.some(p => p.id === actual.id) ? [actual, ...opciones] : opciones;
  });
  protected readonly unidadesAsignables = computed(() => {
    const opciones = this.asignables().unidades;
    const actual = this.unidadActual();
    return actual && !opciones.some(u => u.id === actual.id) ? [actual, ...opciones] : opciones;
  });
  protected readonly formLockVersion = signal<number | null>(null);
  protected readonly saveError = signal('');
  protected readonly formResponsable = signal('');
  protected readonly formProveedor = signal('');
  protected readonly formEstado = signal<InventarioEstado>('en buen estado');
  protected readonly formDescripcion = signal('');
  protected readonly formSubmitted = signal(false);
  protected readonly formDirty = signal(false);
  protected readonly fechaMaxima = new Date().toLocaleDateString('sv-SE');
  protected readonly formErrores = computed(() => {
    const errores: string[] = [];
    const campos: [string, string, number, boolean][] = [
      ['Nombre', this.formNombre(), 300, true], ['Categoría', this.formCategoria(), 200, true],
      ['Ubicación física', this.formUbicacion(), 200, true], ['Unidad', this.formUnidad(), 200, false],
      ['Responsable', this.formResponsable(), 452, false], ['Marca / modelo', this.formMarcaModelo(), 200, false],
      ['Serie', this.formSerie(), 200, false], ['Serie institucional', this.formSerieInstitucional(), 200, false],
      ['Proveedor', this.formProveedor(), 200, false], ['Descripción', this.formDescripcion(), 5000, false],
    ];
    for (const [nombre, valor, maximo, obligatorio] of campos) {
      if (obligatorio && !valor.trim()) errores.push(nombre + ': es obligatorio.');
      if (valor.length > maximo) errores.push(nombre + ': admite hasta ' + maximo + ' caracteres.');
    }
    const tipo = this.formTipoAsignacion();
    const personaId = this.formAsignadoUsuarioId();
    const unidadId = this.formAsignadoUnidadId();
    if (!['SIN_ASIGNAR', 'PERSONA', 'UNIDAD', 'PENDIENTE_REVISION'].includes(tipo)) errores.push('Selecciona una asignación válida.');
    if (tipo === 'PENDIENTE_REVISION') errores.push('Revisa la asignación anterior y selecciona persona, unidad o sin asignar.');
    else if (tipo === 'PERSONA' && (!personaId || personaId < 1 || !Number.isInteger(personaId) || unidadId !== null)) {
      errores.push('Selecciona una persona de SIGAA.');
    } else if (tipo === 'UNIDAD' && (!unidadId || unidadId < 1 || !Number.isInteger(unidadId) || personaId !== null)) {
      errores.push('Selecciona una unidad de SIGAA.');
    } else if (tipo === 'SIN_ASIGNAR' && (personaId !== null || unidadId !== null)) {
      errores.push('Un activo sin asignar no puede tener persona ni unidad asignadas.');
    }
    if ([this.formSerie(), this.formSerieInstitucional()].some(serie => /[\x00-\x1f\x7f-\x9f]/.test(serie))) {
      errores.push('Los números de serie no admiten caracteres de control.');
    }
    const fecha = this.formFechaAdquisicion();
    if (fecha && (!/^\d{4}-\d{2}-\d{2}$/.test(fecha) || Number.isNaN(Date.parse(fecha)) ||
        new Date(fecha).toISOString().slice(0, 10) !== fecha || fecha > this.fechaMaxima)) {
      errores.push('Fecha de adquisición: usa una fecha válida que no sea futura.');
    }
    if (!['en buen estado', 'mantenimiento', 'fuera de servicio', 'dañado'].includes(this.formEstado())) {
      errores.push('Selecciona un estado de funcionamiento válido.');
    }
    return errores;
  });
  protected readonly formValido = computed(() => this.formErrores().length === 0);
  protected readonly canDismissModal = () => this.confirmDiscardChanges();

  protected readonly showHistorialModal = signal(false);
  protected readonly historialActivo = signal<InventarioItem | null>(null);
  protected readonly historialLogs = signal<AuditLogEntry[]>([]);

  protected readonly currentPage = signal(1);
  protected get pageSize(): number { return this.authService.preferences().recordsPerPage; }

  protected readonly filteredActivos = computed(() => {
    let items = this.activos();
    const search = this.filtroSearch().toLowerCase().trim();
    const cat = this.filtroCategoria();
    const est = this.filtroEstado();
    const ubi = this.filtroUbicacion();

    if (search) {
      items = items.filter(a =>
        a.nombre.toLowerCase().includes(search) ||
        a.codigo.toLowerCase().includes(search) ||
        (a.unidad || '').toLowerCase().includes(search) ||
        (a.responsable || '').toLowerCase().includes(search)
      );
    }
    if (cat !== 'Todas') items = items.filter(a => a.categoria === cat);
    if (est !== 'Todos') items = items.filter(a => this.estadoLabel(a.estado) === est);
    if (ubi !== 'Todas') items = items.filter(a => a.ubicacion === ubi);
    if (this.filtroUnidad() !== 'Todas') items = items.filter(a => (a.unidad || '') === this.filtroUnidad());

    return items;
  });

  protected readonly totalCount = computed(() => this.activos().length);
  protected readonly filteredCount = computed(() => this.filteredActivos().length);
  protected readonly buenEstadoCount = computed(() => this.activos().filter(a => a.estado === 'en buen estado').length);
  protected readonly mantenimientoCount = computed(() => this.activos().filter(a => a.estado === 'mantenimiento').length);
  protected readonly fueraServicioCount = computed(() => this.activos().filter(a => a.estado === 'fuera de servicio').length);
  protected readonly danadoCount = computed(() => this.activos().filter(a => a.estado === 'dañado').length);

  protected readonly categoriaOptions = computed(() =>
    Array.from(new Set(this.activos().map(a => a.categoria).filter(Boolean))).sort((a, b) => a.localeCompare(b, 'es'))
  );
  protected readonly ubicacionOptions = computed(() =>
    Array.from(new Set(this.activos().map(a => a.ubicacion).filter(Boolean))).sort((a, b) => a.localeCompare(b, 'es'))
  );

  protected readonly unidadOptions = computed(() =>
    Array.from(new Set(this.activos().map(a => a.unidad || '').filter(Boolean))).sort((a, b) => a.localeCompare(b, 'es'))
  );

  protected readonly paginatedActivos = computed(() => {
    const start = (this.currentPage() - 1) * this.pageSize;
    return this.filteredActivos().slice(start, start + this.pageSize);
  });

  protected readonly totalPages = computed(() =>
    Array.from({ length: Math.ceil(this.filteredActivos().length / this.pageSize) }, (_, i) => i + 1)
  );

  protected readonly Math = Math;

  protected readonly nextCode = computed(() => {
    const id = this.editandoId();
    return id ? this.activos().find(a => a.id === id)?.codigo || '' : 'Se asignará al guardar';
  });

  constructor() {
    addIcons({
      cubeOutline, searchOutline, filterOutline, downloadOutline,
      addOutline, eyeOutline, createOutline, timeOutline,
      chevronBackOutline, chevronForwardOutline, closeOutline,
      menuOutline, notificationsOutline, logOutOutline, saveOutline,
      locationOutline, pricetagOutline, calendarOutline, personOutline,
      businessOutline, documentTextOutline, checkmarkCircleOutline,
      constructOutline, warningOutline, trashOutline,
    });
  }

  ngOnInit(): void {
    this.loadActivos();
    this.dashboardService.getNotificaciones().pipe(takeUntilDestroyed(this.destroyRef)).subscribe(d => this.notifications.set(d));
  }

  protected loadAsignables(): void {
    this.asignablesLoading.set(true);
    this.asignablesError.set(false);
    this.api.get<InventarioAsignables>('/api/inventario/asignables').pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: data => { this.asignables.set(data); this.asignablesLoading.set(false); },
      error: () => { this.asignablesError.set(true); this.asignablesLoading.set(false); },
    });
  }

  protected cambiarTipoAsignacion(tipo: InventarioAsignacion): void {
    this.formTipoAsignacion.set(tipo);
    this.formAsignadoUsuarioId.set(null);
    this.formAsignadoUnidadId.set(null);
    this.formUnidad.set('');
    this.formResponsable.set('');
    this.formDirty.set(true);
  }

  protected seleccionarUnidad(unidadId: number | null): void {
    this.formAsignadoUnidadId.set(unidadId);
    if (unidadId === null) {
      if (this.formTipoAsignacion() === 'UNIDAD') this.formTipoAsignacion.set('SIN_ASIGNAR');
      this.formUnidad.set('');
    } else {
      this.formTipoAsignacion.set('UNIDAD');
      this.formAsignadoUsuarioId.set(null);
      this.formResponsable.set('');
      this.formUnidad.set(this.unidadesAsignables().find(unidad => unidad.id === unidadId)?.nombre ?? '');
    }
    this.formDirty.set(true);
  }

  protected asignacionLabel(tipo: InventarioItem['tipoAsignacion']): string {
    switch (tipo) {
      case 'PERSONA': return 'Persona';
      case 'UNIDAD': return 'Unidad / departamento';
      case 'PENDIENTE_REVISION': return 'Asignación anterior pendiente de revisión';
      default: return 'Sin asignar';
    }
  }

  private loadActivos(): void {
    this.loading.set(true);
    this.loadError.set(false);
    this.api.get<InventarioItem[]>('/api/inventario').pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: data => {
        this.activos.set(data);
        this.selectedActivo.set(data[0] ?? null);
        this.loading.set(false);
      },
      error: () => {
        this.activos.set([]);
        this.selectedActivo.set(null);
        this.loadError.set(true);
        this.loading.set(false);
      },
    });
  }

  protected retryLoad(): void { this.loadActivos(); }

  protected estadoLabel(estado: InventarioEstado): string {
    switch (estado) {
      case 'en buen estado': return 'En buen estado';
      case 'mantenimiento': return 'Mantenimiento';
      case 'fuera de servicio': return 'Fuera de servicio';
      case 'dañado': return 'Dañado';
    }
  }

  protected estadoColor(estado: InventarioEstado): string {
    switch (estado) {
      case 'en buen estado': return 'var(--state-success-text)';
      case 'mantenimiento': return 'var(--state-warning-text)';
      case 'fuera de servicio': return 'var(--state-danger-text)';
      case 'dañado': return 'var(--state-danger-text)';
    }
  }

  protected estadoBg(estado: InventarioEstado): string {
    switch (estado) {
      case 'en buen estado': return 'var(--state-success-bg)';
      case 'mantenimiento': return 'var(--state-warning-bg)';
      case 'fuera de servicio': return 'var(--state-danger-bg)';
      case 'dañado': return 'var(--state-danger-bg)';
    }
  }

  protected accionIcon(accion: string): string {
    switch (accion) {
      case 'CREAR': return 'checkmark-circle-outline';
      case 'EDITAR': return 'create-outline';
      case 'ELIMINAR': return 'trash-outline';
      default: return 'time-outline';
    }
  }

  protected accionColor(accion: string): string {
    switch (accion) {
      case 'CREAR': return '#22c55e';
      case 'EDITAR': return INSTITUTIONAL_BLUE;
      case 'ELIMINAR': return '#ef4444';
      default: return '#64748b';
    }
  }

  protected accionLabel(accion: string): string {
    switch (accion) {
      case 'CREAR': return 'Creación';
      case 'EDITAR': return 'Edición';
      case 'ELIMINAR': return 'Eliminación';
      default: return accion;
    }
  }

  protected selectActivo(item: InventarioItem): void {
    this.selectedActivo.set(item);
  }

  protected onFilterChange(): void {
    this.currentPage.set(1);
    this.syncSelectionWithResults();
  }

  protected limpiarFiltros(): void {
    this.filtroSearch.set('');
    this.filtroCategoria.set('Todas');
    this.filtroEstado.set('Todos');
    this.filtroUbicacion.set('Todas');
    this.filtroUnidad.set('Todas');
    this.currentPage.set(1);
    this.syncSelectionWithResults();
  }

  private syncSelectionWithResults(): void {
    const visible = this.filteredActivos();
    const selected = this.selectedActivo();
    if (!selected || !visible.some(item => item.id === selected.id)) {
      this.selectedActivo.set(visible[0] ?? null);
    }
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

  protected openNewModal(): void {
    this.personaActual.set(null);
    this.unidadActual.set(null);
    this.loadAsignables();
    this.formTipoAsignacion.set('SIN_ASIGNAR');
    this.formAsignadoUsuarioId.set(null);
    this.formAsignadoUnidadId.set(null);
    this.editandoId.set(null);
    this.formNombre.set('');
    this.formCategoria.set('');
    this.formMarcaModelo.set('');
    this.formSerie.set('');
    this.formSerieInstitucional.set('');
    this.formFechaAdquisicion.set('');
    this.formUbicacion.set('');
    this.formUnidad.set('');
    this.formLockVersion.set(null);
    this.saveError.set('');
    this.formResponsable.set('');
    this.formProveedor.set('');
    this.formEstado.set('en buen estado');
    this.formDescripcion.set('');
    this.formSubmitted.set(false);
    this.formDirty.set(false);
    this.showModal.set(true);
  }

  protected openEditModal(item: InventarioItem): void {
    this.personaActual.set(item.asignadoUsuarioId === null ? null : {
      id: item.asignadoUsuarioId, nombre: (item.responsable || 'Persona') + ' (asignación actual)',
    });
    this.unidadActual.set(item.asignadoUnidadId === null ? null : {
      id: item.asignadoUnidadId, nombre: (item.unidad || 'Unidad') + ' (asignación actual)',
    });
    this.loadAsignables();
    this.formTipoAsignacion.set(item.tipoAsignacion);
    this.formAsignadoUsuarioId.set(item.asignadoUsuarioId);
    this.formAsignadoUnidadId.set(item.asignadoUnidadId);
    this.editandoId.set(item.id);
    this.formNombre.set(item.nombre);
    this.formCategoria.set(item.categoria);
    this.formMarcaModelo.set(item.marcaModelo ?? '');
    this.formSerie.set(item.serie ?? '');
    this.formSerieInstitucional.set(item.serieInstitucional ?? '');
    this.formFechaAdquisicion.set(this.toDateInputValue(item.fechaAdquisicion));
    this.formUbicacion.set(item.ubicacion);
    this.formUnidad.set(item.unidad || '');
    this.formLockVersion.set(item.lockVersion);
    this.saveError.set('');
    this.formResponsable.set(item.responsable ?? '');
    this.formProveedor.set(item.proveedor ?? '');
    this.formEstado.set(item.estado);
    this.formDescripcion.set(item.descripcion ?? '');
    this.formSubmitted.set(false);
    this.formDirty.set(false);
    this.showModal.set(true);
  }

  protected closeModal(): void {
    this.showModal.set(false);
    this.editandoId.set(null);
    this.formDirty.set(false);
  }

  protected async requestCloseModal(): Promise<void> {
    if (await this.confirmDiscardChanges()) this.closeModal();
  }

  private async confirmDiscardChanges(): Promise<boolean> {
    if (this.saving()) return false;
    if (!this.formDirty()) return true;
    const alert = await this.alertCtrl.create({
      header: 'Cambios sin guardar',
      message: 'Hay información modificada. ¿Deseas cerrar sin guardar?',
      buttons: [
        { text: 'Continuar editando', role: 'cancel' },
        { text: 'Descartar cambios', role: 'destructive' },
      ],
    });
    await alert.present();
    const result = await alert.onDidDismiss();
    return result.role === 'destructive';
  }

  protected guardarActivo(): void {
    this.formSubmitted.set(true);
    if (!this.formValido() || this.saving()) return;
    const editId = this.editandoId();
    this.saveError.set('');
    const body: InventarioActivoRequest = {
      nombre: this.formNombre(),
      categoria: this.formCategoria(),
      marcaModelo: this.formMarcaModelo(),
      serie: this.formSerie(),
      serieInstitucional: this.formSerieInstitucional(),
      fechaAdquisicion: this.formFechaAdquisicion() || null,
      ubicacion: this.formUbicacion(),
      tipoAsignacion: this.formTipoAsignacion() as InventarioAsignacion,
      asignadoUsuarioId: this.formAsignadoUsuarioId(),
      asignadoUnidadId: this.formAsignadoUnidadId(),
      proveedor: this.formProveedor(),
      estado: this.formEstado(),
      descripcion: this.formDescripcion(),
    };

    const request$ = editId
      ? this.api.put<InventarioItem>(`/api/inventario/${editId}`, { ...body, lockVersion: this.formLockVersion() })
      : this.api.post<InventarioItem>('/api/inventario', body);
    this.saving.set(true);
    request$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: async result => {
        if (editId) this.activos.update(list => list.map(a => a.id === editId ? result : a));
        else this.activos.update(list => [result, ...list]);
        this.selectedActivo.set(result);
        this.formDirty.set(false);
        this.saving.set(false);
        this.closeModal();
        const toast = await this.toastCtrl.create({
          header: editId ? 'Activo actualizado' : 'Activo registrado',
          message: `${result.codigo} · Los cambios se guardaron correctamente.`,
          icon: 'checkmark-circle-outline',
          cssClass: 'inventario-save-toast',
          position: 'top',
          duration: 4000,
          buttons: [{ text: 'Cerrar', role: 'cancel' }],
        });
        toast.present();
      },
      error: async (err: HttpErrorResponse) => {
        this.saving.set(false);
        const payload = err.error as { fields?: Record<string, string>; message?: string; error?: string } | null;
        const fields = payload?.fields ? Object.values(payload.fields).filter(value => typeof value === 'string') : [];
        const message = fields.length ? fields.join(' · ') : payload?.message || payload?.error || 'No se pudo guardar el activo';
        this.saveError.set(message);
        const toast = await this.toastCtrl.create({ message, duration: 3200, color: 'danger' });
        toast.present();
      },
    });
  }

  protected async eliminarActivo(item: InventarioItem): Promise<void> {
    const alert = await this.alertCtrl.create({
      header: 'Eliminar activo',
      message: `¿Deseas eliminar ${item.codigo} · ${item.nombre}? Esta acción no se puede deshacer.`,
      buttons: [
        { text: 'Cancelar', role: 'cancel' },
        {
          text: 'Eliminar', role: 'destructive', handler: () => {
            this.api.delete(`/api/inventario/${item.id}`).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
              next: async () => {
                this.activos.update(list => list.filter(a => a.id !== item.id));
                this.selectedActivo.set(this.filteredActivos()[0] ?? null);
                const toast = await this.toastCtrl.create({
                  header: 'Activo eliminado',
                  message: `${item.codigo} · Se eliminó del inventario correctamente.`,
                  icon: 'checkmark-circle-outline',
                  cssClass: 'inventario-save-toast',
                  position: 'top',
                  duration: 4000,
                  buttons: [{ text: 'Cerrar', role: 'cancel' }],
                });
                toast.present();
              },
              error: async err => {
                const message = err?.error?.message || 'No se pudo eliminar el activo';
                const toast = await this.toastCtrl.create({ message, duration: 3000, color: 'danger' });
                toast.present();
              },
            });
          },
        },
      ],
    });
    await alert.present();
  }

  protected verDetalle(item: InventarioItem): void {
    this.selectedActivo.set(item);
    const detail = document.querySelector('.detail-section');
    detail?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  protected verHistorial(item: InventarioItem): void {
    this.historialActivo.set(item);
    this.historialLogs.set([]);
    this.api.get<(Omit<AuditLogEntry, 'fecha'> & { fecha: string })[]>(`/api/audit-logs/filter?entidad=InventarioActivo&entidadId=${item.id}`)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (logs) => this.historialLogs.set(logs.map(l => ({
          ...l,
          fecha: new Date(l.fecha),
        }))),
        error: () => this.historialLogs.set([]),
      });
    this.showHistorialModal.set(true);
  }

  protected closeHistorialModal(): void {
    this.showHistorialModal.set(false);
    this.historialActivo.set(null);
    this.historialLogs.set([]);
  }

  private toDateInputValue(dateStr: string): string {
    if (!dateStr) return '';
    const parts = dateStr.split('-');
    if (parts.length === 3 && parts[2].length === 4) {
      return `${parts[2]}-${parts[1]}-${parts[0]}`;
    }
    return dateStr;
  }

  protected exportCSV(): void {
    const headers = ['Código,Nombre,Categoría,Ubicación,Estado,Fecha Adquisición'];
    const rows = this.activos().map(a =>
      [a.codigo, a.nombre, a.categoria, a.ubicacion, this.estadoLabel(a.estado), a.fechaAdquisicion]
        .map(value => this.csvCell(value)).join(',')
    );
    const csv = [...headers, ...rows].join('\n');
    const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8;' });
    void this.fileActions.save(blob, `inventario_${new Date().toISOString().slice(0, 10)}.csv`);
  }

  private csvCell(value: unknown): string {
    const text = String(value ?? '').replace(/"/g, '""');
    return `"${text}"`;
  }

  protected formatFecha(fecha: string): string {
    if (!fecha) return '—';
    const [year, month, day] = fecha.slice(0, 10).split('-').map(Number);
    if (!year || !month || !day) return fecha;
    return new Intl.DateTimeFormat('es-PA', { day: '2-digit', month: 'short', year: 'numeric' })
      .format(new Date(year, month - 1, day));
  }
}
