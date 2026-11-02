import { FileActionsService } from '../../services/file-actions.service';
import { ChangeDetectionStrategy, Component, DestroyRef, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, of, switchMap, takeUntil, tap } from 'rxjs';
import { AlertController, IonIcon } from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { addOutline, checkmarkCircleOutline, closeOutline, documentTextOutline, listOutline, peopleOutline, refreshOutline, searchOutline, timeOutline, trashOutline } from 'ionicons/icons';
import { DashboardSidebarComponent } from '../../components/dashboard-sidebar/dashboard-sidebar.component';
import { DashboardTopbarComponent } from '../../components/dashboard-topbar/dashboard-topbar.component';
import { CorrespondenciaTabsComponent } from '../../components/correspondencia-tabs/correspondencia-tabs.component';
import { DashboardService } from '../../services/dashboard.service';
import { AuthService } from '../../services/auth.service';
import { ApiService } from '../../services/api.service';
import { Notification } from '../../models/dashboard.models';
import { Memo, MemoEstado, MemoNumero, MemoUsuario, MemoUsuarioSeleccionable } from './memos.models';
import { MemoPreviewData, NotaDocumentPreviewComponent } from '../notas/nota-document-preview.component';

@Component({
  selector: 'app-memos', standalone: true,
  imports: [CommonModule, FormsModule, DashboardSidebarComponent, DashboardTopbarComponent, CorrespondenciaTabsComponent, IonIcon, NotaDocumentPreviewComponent],
  templateUrl: './memos.page.html', styleUrls: ['./memos.page.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MemosPage implements OnInit, OnDestroy {
  private readonly fileActions = inject(FileActionsService);
  private readonly dashboard = inject(DashboardService);
  protected readonly authService = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly alerts = inject(AlertController);
  private readonly destroyRef = inject(DestroyRef);
  private readonly collator = new Intl.Collator('es', { numeric: true, sensitivity: 'base' });
  private detalleRevision = 0;
  private numerosRevision = 0;

  protected readonly menuItems = computed(() => this.dashboard.getMenuItems());
  protected readonly notifications = signal<Notification[]>([]);
  protected readonly loading = signal(true);
  protected readonly loadError = signal(false);
  protected readonly numerosError = signal(false);
  protected readonly saving = signal(false);
  protected readonly reserving = signal(false);
  protected readonly memos = signal<Memo[]>([]);
  protected readonly numeros = signal<MemoNumero[]>([]);
  protected readonly usuarios = signal<MemoUsuarioSeleccionable[]>([]);
  protected readonly seleccionado = signal<Memo | null>(null);
  protected readonly vistaPrevia = signal<MemoPreviewData | null>(null);
  protected readonly cargandoDetalle = signal(false);

  protected readonly filtroTexto = signal('');
  protected readonly filtroEstado = signal<MemoEstado | 'TODOS'>('TODOS');
  protected readonly showRegistro = signal(false);
  protected readonly showNumeros = signal(false);
  protected readonly showAnulacion = signal(false);
  protected readonly cargaNumeros = signal('');
  protected readonly formNumeroId = signal('');
  protected readonly formAsunto = signal('');
  protected readonly formDestino = signal('');
  protected readonly formResponsableId = signal('');
  protected readonly formDescripcion = signal('');
  protected readonly formSubmitted = signal(false);
  protected readonly seguimiento = signal('');
  protected readonly seguimientoSubmitted = signal(false);
  protected readonly motivoAnulacion = signal('');

  protected readonly memosFiltrados = computed(() => {
    const q = this.filtroTexto().trim().toLocaleLowerCase(); const estado = this.filtroEstado();
    return this.memos().filter(memo => (!q || [memo.numero, memo.asunto, memo.destino, memo.creadoPor.nombre, memo.seguimientoUsuario.nombre]
      .some(value => value.toLocaleLowerCase().includes(q))) && (estado === 'TODOS' || memo.estado === estado));
  });
  protected readonly numerosSeleccionables = computed(() => {
    const ordenados = [...this.numeros()].sort((a, b) => this.collator.compare(a.numero, b.numero));
    const propia = ordenados.find(item => item.reservaPropia);
    const ocupados = ordenados.filter(item => item.estado === 'OCUPADO');
    const actual = ocupados.length ? ocupados[ocupados.length - 1] : undefined;
    const disponibles = ordenados.filter(item => item.estado === 'DISPONIBLE' && (!actual || this.collator.compare(item.numero, actual.numero) > 0)).slice(0, 5);
    return propia ? [propia, ...disponibles] : disponibles;
  });
  protected readonly disponibles = computed(() => this.numeros().filter(item => item.estado === 'DISPONIBLE').length);
  protected readonly enSeguimiento = computed(() => this.memos().filter(item => item.estado === 'EN_SEGUIMIENTO').length);
  protected readonly completados = computed(() => this.memos().filter(item => item.estado === 'COMPLETADO').length);
  protected readonly anulados = computed(() => this.memos().filter(item => item.estado === 'ANULADO').length);
  protected readonly formValido = computed(() => !!this.formNumeroId() && !!this.formAsunto().trim() && !!this.formDestino().trim() && !!this.formResponsableId());

  constructor() { addIcons({ addOutline, checkmarkCircleOutline, closeOutline, documentTextOutline, listOutline, peopleOutline, refreshOutline, searchOutline, timeOutline, trashOutline }); }

  ngOnInit(): void {
    this.authService.sessionChanged$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.detalleRevision++;
      this.numerosRevision++;
      this.memos.set([]); this.numeros.set([]); this.usuarios.set([]); this.notifications.set([]);
      this.seleccionado.set(null); this.vistaPrevia.set(null); this.formNumeroId.set('');
      this.showRegistro.set(false); this.showNumeros.set(false); this.showAnulacion.set(false);
      this.saving.set(false); this.reserving.set(false); this.cargandoDetalle.set(false);
      if (this.authService.currentUser()?.id && this.can('Ver')) {
        this.cargarDatos();
        if (this.can('Crear')) this.cargarUsuarios();
      }
    });
    this.cargarDatos();
    this.dashboard.getNotificaciones().pipe(takeUntilDestroyed(this.destroyRef)).subscribe(value => this.notifications.set(value));
    if (this.can('Crear')) this.cargarUsuarios();
  }
  ngOnDestroy(): void {
    const id = this.formNumeroId();
    if (id && this.showRegistro()) this.api.delete<void>(`/api/memos/numeros/${id}/reserva`).subscribe({ error: () => undefined });
  }

  protected can(action: string): boolean { return this.authService.hasPermission('Memos', action); }
  protected onMenuToggle(): void { this.authService.sidebarCollapsed.update(value => !value); }
  protected onLogout(): void { this.authService.logout(); }
  protected estadoLabel(estado: MemoEstado): string { return ({ EN_SEGUIMIENTO: 'En seguimiento', COMPLETADO: 'Completado', ANULADO: 'Anulado' })[estado]; }
  protected estadoClase(estado: MemoEstado): string { return `estado-${estado.toLowerCase().replace('_', '-')}`; }
  protected formatFecha(value: string | null): string { return value ? this.authService.formatDate(value, { hour: '2-digit', minute: '2-digit' }) : '—'; }

  protected cargarDatos(): void {
    this.loading.set(true); this.loadError.set(false);
    this.api.get<any[]>('/api/memos').pipe(takeUntilDestroyed(this.destroyRef), takeUntil(this.authService.sessionChanged$)).subscribe({
      next: data => { this.memos.set(data.map(item => this.mapMemo(item))); this.cargarNumeros(); this.loading.set(false); },
      error: () => { this.loadError.set(true); this.loading.set(false); },
    });
  }
  protected cargarNumeros(): void {
    const revision = ++this.numerosRevision;
    this.numerosError.set(false);
    this.api.get<any[]>('/api/memos/numeros').pipe(takeUntilDestroyed(this.destroyRef), takeUntil(this.authService.sessionChanged$)).subscribe({
      next: data => {
        if (revision !== this.numerosRevision) return;
        const numeros = data.map(item => this.mapNumero(item)); this.numeros.set(numeros);
        if (this.formNumeroId() && !numeros.some(item => item.id === this.formNumeroId() && item.reservaPropia)) this.formNumeroId.set('');
      },
      error: () => { if (revision === this.numerosRevision) this.numerosError.set(true); },
    });
  }
  private cargarUsuarios(): void {
    this.api.get<any[]>('/api/usuarios/seleccionables').pipe(takeUntilDestroyed(this.destroyRef), takeUntil(this.authService.sessionChanged$)).subscribe({
      next: data => this.usuarios.set(data.filter(item => item.activo !== false && item.bloqueado !== true).map(item => ({ id: String(item.id), nombre: [item.nombre, item.segundoNombre, item.apellido].filter(Boolean).join(' ').trim() })).filter(item => item.id && item.nombre).sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'))),
      error: () => this.usuarios.set([]),
    });
  }

  protected abrirRegistro(): void {
    if (!this.can('Crear')) return;
    if (this.numerosError()) { this.cargarNumeros(); void this.alerta('No se pudo consultar los números', 'Se está reintentando la consulta. Vuelve a abrir el registro cuando termine.'); return; }
    if (!this.numerosSeleccionables().length) { void this.alerta('No hay números disponibles', 'Carga nuevos consecutivos o espera a que venza una reserva activa.'); return; }
    this.formNumeroId.set(''); this.formAsunto.set(''); this.formDestino.set(''); this.formResponsableId.set(''); this.formDescripcion.set(''); this.formSubmitted.set(false); this.showRegistro.set(true);
  }
  protected cerrarRegistro(): void {
    if (this.saving() || this.reserving()) return;
    const id = this.formNumeroId();
    if (!id) { this.showRegistro.set(false); return; }
    this.reserving.set(true);
    this.api.delete<void>(`/api/memos/numeros/${id}/reserva`).pipe(finalize(() => this.reserving.set(false)), takeUntilDestroyed(this.destroyRef), takeUntil(this.authService.sessionChanged$)).subscribe({
      next: () => { this.formNumeroId.set(''); this.showRegistro.set(false); this.cargarNumeros(); },
      error: error => { this.cargarNumeros(); void this.alerta('No se pudo liberar la reserva', this.mensajeError(error, 'Intenta cerrar el registro nuevamente.')); },
    });
  }
  protected seleccionarNumero(id: string): void {
    if (!id || id === this.formNumeroId() || this.reserving() || this.saving()) return;
    const anterior = this.formNumeroId(); this.reserving.set(true);
    const liberar$ = anterior ? this.api.delete<void>(`/api/memos/numeros/${anterior}/reserva`).pipe(tap(() => this.formNumeroId.set(''))) : of(undefined);
    liberar$.pipe(switchMap(() => this.api.post<any>(`/api/memos/numeros/${id}/reservar`, {})), finalize(() => this.reserving.set(false)), takeUntilDestroyed(this.destroyRef), takeUntil(this.authService.sessionChanged$)).subscribe({
      next: response => { this.formNumeroId.set(String(response.id)); this.cargarNumeros(); },
      error: error => { this.cargarNumeros(); void this.alerta('No se pudo cambiar el número', this.mensajeError(error, 'Comprueba la conexión y vuelve a intentarlo.')); },
    });
  }
  protected guardar(): void {
    this.formSubmitted.set(true); if (!this.formValido() || this.saving() || this.reserving()) return;
    this.saving.set(true);
    this.api.post<any>('/api/memos', { numeroId: Number(this.formNumeroId()), asunto: this.formAsunto().trim(), destino: this.formDestino().trim(), seguimientoUsuarioId: Number(this.formResponsableId()), descripcion: this.formDescripcion().trim() })
      .pipe(takeUntilDestroyed(this.destroyRef), takeUntil(this.authService.sessionChanged$)).subscribe({
        next: response => { const memo = this.mapMemo(response); this.memos.update(items => [memo, ...items]); this.saving.set(false); this.formNumeroId.set(''); this.showRegistro.set(false); this.cargarNumeros(); },
        error: error => { this.saving.set(false); this.cargarNumeros(); void this.alerta('No se pudo registrar el memo', this.mensajeError(error, 'Revisa los datos e inténtalo nuevamente.')); },
      });
  }

  protected abrirDetalle(memo: Memo): void {
    if (this.saving()) return;
    const revision = ++this.detalleRevision;
    this.seleccionado.set(memo); this.cargandoDetalle.set(true); this.seguimiento.set(''); this.seguimientoSubmitted.set(false);
    this.api.get<any>(`/api/memos/${memo.id}`).pipe(finalize(() => { if (revision === this.detalleRevision) this.cargandoDetalle.set(false); }), takeUntilDestroyed(this.destroyRef), takeUntil(this.authService.sessionChanged$)).subscribe({
      next: data => { if (revision === this.detalleRevision) this.seleccionado.set(this.mapMemo(data)); },
      error: error => { if (revision === this.detalleRevision) { this.seleccionado.set(null); void this.alerta('No se pudo abrir el memo', this.mensajeError(error, 'Actualiza la pantalla.')); } },
    });
  }
  protected cerrarDetalle(): void { if (!this.saving()) { this.detalleRevision++; this.seleccionado.set(null); this.cargandoDetalle.set(false); } }
  protected visualizarDocumento(memo: Memo): void {
    if (!this.can('Ver')) return;
    this.vistaPrevia.set({ tipoDocumento: 'memo', memoId: memo.id, numero: memo.numero,
      estado: memo.estado === 'ANULADO' ? 'ANULADA' : 'OFICIALIZADA' });
  }
  protected descargarWord(memo: Memo): void {
    if (!this.can('Ver') || memo.estado === 'ANULADO') return;
    this.api.getBlob(`/api/memos/${memo.id}/word`).pipe(takeUntilDestroyed(this.destroyRef), takeUntil(this.authService.sessionChanged$)).subscribe({
      next: archivo => {
        void this.fileActions.save(archivo, `MEMO-${memo.numero.replace(/[^A-Za-z0-9_-]/g, '-')}.DOCX`);
      },
      error: error => void this.alerta('No se pudo descargar el memo', this.mensajeError(error, 'Intenta nuevamente.')),
    });
  }
  protected agregarSeguimiento(): void {
    const memo = this.seleccionado(); const detalle = this.seguimiento().trim(); this.seguimientoSubmitted.set(true);
    if (!memo || detalle.length < 3 || this.saving()) return;
    this.saving.set(true); this.api.post<any>(`/api/memos/${memo.id}/seguimientos`, { descripcion: detalle }).pipe(takeUntilDestroyed(this.destroyRef), takeUntil(this.authService.sessionChanged$)).subscribe({
      next: data => { const updated = this.mapMemo(data); this.actualizarMemo(updated); this.seleccionado.set(updated); this.seguimiento.set(''); this.seguimientoSubmitted.set(false); this.saving.set(false); },
      error: error => { this.saving.set(false); void this.alerta('No se pudo guardar el seguimiento', this.mensajeError(error, 'Inténtalo nuevamente.')); },
    });
  }
  protected completar(): void {
    const memo = this.seleccionado(); if (!memo || this.saving()) return;
    const revision = this.detalleRevision;
    void this.confirmar('Completar seguimiento', `El memo ${memo.numero} quedará marcado como completado.`, () => {
      if (revision !== this.detalleRevision || this.seleccionado()?.id !== memo.id || this.saving()) return;
      this.saving.set(true); this.api.post<any>(`/api/memos/${memo.id}/completar`, {}).pipe(takeUntilDestroyed(this.destroyRef), takeUntil(this.authService.sessionChanged$)).subscribe({
        next: data => { const updated = this.mapMemo(data); this.actualizarMemo(updated); this.seleccionado.set(updated); this.saving.set(false); },
        error: error => { this.saving.set(false); void this.alerta('No se pudo completar', this.mensajeError(error, 'Inténtalo nuevamente.')); },
      });
    });
  }
  protected abrirAnulacion(): void { this.motivoAnulacion.set(''); this.showAnulacion.set(true); }
  protected cerrarAnulacion(): void { if (!this.saving()) this.showAnulacion.set(false); }
  protected anular(): void {
    const memo = this.seleccionado(); const motivo = this.motivoAnulacion().trim(); if (!memo || motivo.length < 5 || this.saving()) return;
    this.saving.set(true); this.api.post<any>(`/api/memos/${memo.id}/anular`, { motivo }).pipe(takeUntilDestroyed(this.destroyRef), takeUntil(this.authService.sessionChanged$)).subscribe({
      next: data => { const updated = this.mapMemo(data); this.actualizarMemo(updated); this.seleccionado.set(updated); this.showAnulacion.set(false); this.saving.set(false); },
      error: error => { this.saving.set(false); void this.alerta('No se pudo anular', this.mensajeError(error, 'Revisa el motivo y tus permisos.')); },
    });
  }

  protected abrirNumeros(): void { this.cargaNumeros.set(''); this.showNumeros.set(true); }
  protected cerrarNumeros(): void { if (!this.saving()) this.showNumeros.set(false); }
  protected cargarLote(): void {
    const numeros = this.cargaNumeros().split(/[\n,;\t]+/).map(value => value.trim()).filter(Boolean);
    if (!numeros.length || this.saving()) { void this.alerta('Indica los números', 'Pega al menos un número de memo.'); return; }
    this.saving.set(true); this.api.post<{ cargados: number }>('/api/memos/numeros/cargar', { numeros }).pipe(takeUntilDestroyed(this.destroyRef), takeUntil(this.authService.sessionChanged$)).subscribe({
      next: response => { this.saving.set(false); this.cargaNumeros.set(''); this.cargarNumeros(); void this.alerta('Números cargados', `Se incorporaron ${response.cargados} consecutivos.`); },
      error: error => { this.saving.set(false); void this.alerta('No se pudo cargar el lote', this.mensajeError(error, 'Verifica que no existan números repetidos.')); },
    });
  }

  private actualizarMemo(memo: Memo): void { this.memos.update(items => items.map(item => item.id === memo.id ? memo : item)); }
  private mapMemo(data: any): Memo { return { id: String(data.id), numeroId: String(data.numeroId), numero: data.numero || '', asunto: data.asunto || '', destino: data.destino || '', descripcion: data.descripcion || '', creadoPor: this.mapUsuario(data.creadoPor)!, seguimientoUsuario: this.mapUsuario(data.seguimientoUsuario)!, estado: (data.estado || 'EN_SEGUIMIENTO') as MemoEstado, completadoPor: this.mapUsuario(data.completadoPor), completadoAt: data.completadoAt || null, anuladoPor: this.mapUsuario(data.anuladoPor), anuladoAt: data.anuladoAt || null, anulacionMotivo: data.anulacionMotivo || null, createdAt: data.createdAt || '', updatedAt: data.updatedAt || '', historial: (data.historial || []).map((item: any) => ({ id: String(item.id), usuario: this.mapUsuario(item.usuario), accion: item.accion || '', estadoAnterior: item.estadoAnterior || null, estadoNuevo: item.estadoNuevo || null, detalle: item.detalle || '', createdAt: item.createdAt || '' })) }; }
  private mapNumero(data: any): MemoNumero { return { id: String(data.id), numero: data.numero || '', estado: data.estado === 'OCUPADO' ? 'OCUPADO' : data.estado === 'RESERVADO' ? 'RESERVADO' : 'DISPONIBLE', memoId: data.memoId == null ? null : String(data.memoId), reservadoPor: this.mapUsuario(data.reservadoPor), reservadoDesde: data.reservadoDesde || null, reservadoHasta: data.reservadoHasta || null, reservaPropia: data.reservaPropia === true }; }
  private mapUsuario(data: any): MemoUsuario | null { return data?.id == null ? null : { id: String(data.id), nombre: data.nombre || 'Usuario' }; }
  private mensajeError(error: any, fallback: string): string { return error?.error?.message || error?.error?.detalle || error?.error?.error || fallback; }
  private async alerta(header: string, message: string): Promise<void> { const alert = await this.alerts.create({ header, message, buttons: ['Aceptar'] }); await alert.present(); }
  private async confirmar(header: string, message: string, handler: () => void): Promise<void> { const alert = await this.alerts.create({ header, message, buttons: [{ text: 'Cancelar', role: 'cancel' }, { text: 'Confirmar', handler }] }); await alert.present(); }
}
