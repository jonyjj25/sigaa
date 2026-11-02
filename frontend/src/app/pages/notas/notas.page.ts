import { FileActionsService } from '../../services/file-actions.service';
import { ChangeDetectionStrategy, Component, DestroyRef, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription, catchError, finalize, of, switchMap } from 'rxjs';
import { ActivatedRoute, Router } from '@angular/router';
import { AlertController, IonIcon } from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import {
  addOutline, checkmarkCircleOutline, closeOutline, documentTextOutline,
  downloadOutline, createOutline, folderOpenOutline, lockClosedOutline,
  peopleOutline, personOutline, refreshOutline, searchOutline, timeOutline,
  cloudUploadOutline, listOutline,
  eyeOutline,
} from 'ionicons/icons';
import { DashboardSidebarComponent } from '../../components/dashboard-sidebar/dashboard-sidebar.component';
import { DashboardTopbarComponent } from '../../components/dashboard-topbar/dashboard-topbar.component';
import { CorrespondenciaTabsComponent } from '../../components/correspondencia-tabs/correspondencia-tabs.component';
import { DashboardService } from '../../services/dashboard.service';
import { AuthService } from '../../services/auth.service';
import { ApiService, UsuarioSeleccionableRespuesta, mensajeErrorApi } from '../../services/api.service';
import { Notification } from '../../models/dashboard.models';
import { Nota, NotaBusqueda, NotaEstado, NotaInstitucion, NotaNumero, NotaRelacionTipo, NotaUsuario, UsuarioSeleccionable, NotaRespuesta, NotaNumeroRespuesta, NotaBusquedaRespuesta, NotaUsuarioRespuesta, NotaInstitucionRespuesta, NotaContextoBorrador } from './notas.models';
import { NotaDocumentPreviewComponent, NotaPreviewData } from './nota-document-preview.component';
import { NotaListadoRespuesta, insertarSaltoPagina } from './notas.models';
import { NotaDestinatarioForm } from './nota-destinatario.form';

@Component({
  selector: 'app-notas',
  standalone: true,
  imports: [CommonModule, FormsModule, DashboardSidebarComponent, DashboardTopbarComponent, CorrespondenciaTabsComponent, NotaDocumentPreviewComponent, IonIcon],
  templateUrl: './notas.page.html',
  styleUrls: ['./notas.page.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotasPage implements OnInit, OnDestroy {
  private readonly fileActions = inject(FileActionsService);
  protected readonly insertarSaltoPagina = insertarSaltoPagina;
  private readonly dashboardService = inject(DashboardService);
  protected readonly authService = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly alertCtrl = inject(AlertController);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly destinatarioForm = new NotaDestinatarioForm(this.api, this.destroyRef);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private editarSolicitadaId: string | null = null;
  private listadoSubscription: Subscription | null = null;
  private filtrosAplicados: Record<string, string> = {};
  private readonly numeroCollator = new Intl.Collator('es', { numeric: true, sensitivity: 'base' });

  protected readonly menuItems = computed(() => this.dashboardService.getMenuItems());
  protected readonly notifications = signal<Notification[]>([]);
  protected readonly loading = signal(true);
  protected readonly loadError = signal(false);
  protected readonly saving = signal(false);
  protected readonly reserving = signal(false);

  protected readonly notas = signal<Nota[]>([]);
  protected readonly numeros = signal<NotaNumero[]>([]);
  protected readonly usuarios = signal<UsuarioSeleccionable[]>([]);
  protected readonly instituciones = signal<NotaInstitucion[]>([]);
  protected readonly institucionBusqueda = signal('');
  protected readonly fechaOficialTexto = signal('');
  protected readonly directoraActual = signal<{ id: string; nombre: string; cargo: string; institucion: string } | null>(null);
  protected readonly advertenciaDirectora = signal('');
  protected readonly notaEnEdicion = signal<Nota | null>(null);
  protected readonly vistaPrevia = signal<NotaPreviewData | null>(null);

  protected readonly filtroTexto = signal('');
  protected readonly filtroEstado = signal<NotaEstado | 'TODAS'>('TODAS');
  protected readonly filtroAnio = signal('');
  protected readonly filtroFechaDesde = signal('');
  protected readonly filtroFechaHasta = signal('');
  protected readonly pagina = signal(0);
  protected readonly totalPaginas = signal(0);
  protected readonly totalNotas = signal(0);
  protected readonly resumen = signal<Partial<Record<NotaEstado, number>>>({});
  protected readonly filtrando = signal(false);
  protected readonly showNotaModal = signal(false);
  protected readonly showNumerosModal = signal(false);
  protected readonly editandoId = signal<string | null>(null);
  protected readonly cargaNumeros = signal('');

  protected readonly formNumeroId = signal('');
  protected readonly formAsunto = signal('');
  protected readonly formDestino = this.destinatarioForm.destino;
  protected readonly formDestinatarioNombre = this.destinatarioForm.nombre;
  protected readonly formDestinatarioCargo = this.destinatarioForm.cargo;
  protected readonly formInstitucionId = this.destinatarioForm.institucionId;
  protected readonly formDepartamentoId = this.destinatarioForm.departamentoId;
  protected readonly formContenido = signal('');
  protected readonly formSeguimientoId = signal('');
  protected readonly formDescripcion = signal('');
  protected readonly formSensitiva = signal(false);
  protected readonly formSubmitted = signal(false);
  protected readonly relacionBusqueda = signal('');
  protected readonly relacionTipo = signal<NotaRelacionTipo>('RELACIONADA_CON');
  protected readonly relacionResultados = signal<NotaBusqueda[]>([]);
  protected readonly relacionesSeleccionadas = signal<Array<{ nota: NotaBusqueda; tipo: NotaRelacionTipo }>>([]);
  protected readonly buscandoRelaciones = signal(false);

  protected readonly notasFiltradas = this.notas;
  protected readonly institucionesFiltradas = computed(() => {
    const texto = this.institucionBusqueda().trim().toLocaleLowerCase();
    const original = this.destinatarioForm.institucionOriginal();
    const instituciones = original ? [original, ...this.instituciones().filter(item => item.id !== original.id)] : this.instituciones();
    if (!texto) return instituciones;
    return instituciones.filter(institucion =>
      `${institucion.nombre} ${institucion.siglas || ''}`.toLocaleLowerCase().includes(texto));
  });
  protected readonly numerosDisponibles = computed(() => this.numeros().filter(numero => numero.estado === 'DISPONIBLE'));
  protected readonly numerosSeleccionables = computed(() => {
    const ordenados = [...this.numeros()].sort((a, b) => this.numeroCollator.compare(a.numero, b.numero));
    const conservado = ordenados.find(numero => numero.id === this.formNumeroId()
      && (numero.reservaPropia || (this.editandoId() && numero.notaId === this.editandoId())));
    const reservaPropia = conservado || ordenados.find(numero => numero.reservaPropia);
    const ocupados = ordenados.filter(numero => numero.estado === 'OCUPADO');
    const actual = ocupados.length ? ocupados[ocupados.length - 1] : undefined;
    const disponibles = ordenados.filter(numero => numero.estado === 'DISPONIBLE'
      && (!actual || this.numeroCollator.compare(numero.numero, actual.numero) > 0)).slice(0, 1);
    const primeroDisponible = disponibles[0];
    // Liberar esta reserva la convertiría de nuevo en el primer disponible.
    if (reservaPropia?.estado === 'RESERVADO' && reservaPropia.reservaPropia
      && (!actual || this.numeroCollator.compare(reservaPropia.numero, actual.numero) > 0)
      && primeroDisponible && this.numeroCollator.compare(reservaPropia.numero, primeroDisponible.numero) < 0) {
      return [reservaPropia];
    }
    return reservaPropia ? [reservaPropia, ...disponibles] : disponibles;
  });
  protected readonly totalDisponibles = computed(() => this.numerosDisponibles().length);
  protected readonly enSeguimiento = computed(() => this.resumen().EN_SEGUIMIENTO || 0);
  protected readonly borradores = computed(() => this.resumen().BORRADOR || 0);
  protected readonly firmadas = computed(() => this.resumen().FIRMADA || 0);
  protected readonly cerradas = computed(() => this.resumen().CERRADA || 0);
  protected readonly modalTitle = computed(() => this.editandoId() ? 'Editar borrador' : 'Crear borrador');
  protected readonly formValido = computed(() =>
    !!this.formSeguimientoId() && (!!this.editandoId() || !!this.formNumeroId())
  );

  constructor() {
    addIcons({
      addOutline, checkmarkCircleOutline, closeOutline, documentTextOutline,
      downloadOutline, createOutline, folderOpenOutline, lockClosedOutline,
      peopleOutline, personOutline, refreshOutline, searchOutline, timeOutline,
      cloudUploadOutline, listOutline,
      eyeOutline,
    });
  }

  ngOnInit(): void {
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      this.editarSolicitadaId = params.get('editar');
      this.abrirEdicionSolicitada();
    });
    this.cargarDatos();
    this.dashboardService.getNotificaciones().pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(notificaciones => this.notifications.set(notificaciones));
    if (this.can('Crear') || this.can('Editar')) {
      this.cargarUsuarios();
      this.cargarContextoBorrador();
      this.cargarInstituciones();
    }
  }

  ngOnDestroy(): void {
    this.listadoSubscription?.unsubscribe();
    const numeroId = !this.editandoId() ? this.formNumeroId() : '';
    if (numeroId && this.showNotaModal()) this.api.delete<void>(`/api/notas/numeros/${numeroId}/reserva`).subscribe({ error: () => undefined });
  }

  protected can(accion: string): boolean {
    return this.authService.hasPermission('Notas', accion);
  }

  protected abrirVistaPrevia(nota: Nota): void {
    this.vistaPrevia.set(this.datosVistaPrevia(nota));
  }

  protected abrirVistaPreviaFormulario(): void {
    const actual = this.notaEnEdicion();
    const numero = actual?.numero || this.numeros().find(item => item.id === this.formNumeroId())?.numero || '';
    const institucion = this.destinatarioForm.institucionOriginal() || this.instituciones().find(item => item.id === this.formInstitucionId());
    const firmante = actual?.firmanteNombre
      ? { nombre: actual.firmanteNombre, cargo: actual.firmanteCargo || '', institucion: actual.firmanteInstitucion || actual.firmante?.institucion || '' }
      : this.directoraActual();
    this.vistaPrevia.set({
      notaId: actual?.id, numeroId: actual?.numeroId || this.formNumeroId(),
      institucionId: this.formInstitucionId() || undefined, institucionDefinida: true,
      fechaOficial: actual?.fechaOficial,
      estado: actual?.estado === 'ANULADA' ? 'ANULADA' : actual && actual.estado !== 'BORRADOR' ? 'OFICIALIZADA' : 'BORRADOR',
      fecha: actual?.fechaOficialTexto || this.fechaOficialTexto(),
      numero,
      asunto: this.formAsunto().trim(),
      destinatarioNombre: this.formDestinatarioNombre().trim(),
      destinatarioCargo: this.formDestinatarioCargo().trim(),
      institucion: institucion?.nombre || this.formDestino(),
      contenido: this.formContenido().trim(),
      firmanteNombre: firmante?.nombre || '',
      firmanteCargo: firmante?.cargo || '',
      firmanteInstitucion: firmante?.institucion || '',
    });
  }

  protected cerrarVistaPrevia(): void { this.vistaPrevia.set(null); }

  private datosVistaPrevia(nota: Nota): NotaPreviewData {
    const firmanteBorrador = nota.estado === 'BORRADOR' ? this.directoraActual() : null;
    return {
      notaId: nota.id, numeroId: nota.numeroId, institucionId: nota.institucion?.id, fechaOficial: nota.fechaOficial,
      estado: nota.estado === 'ANULADA' ? 'ANULADA' : nota.estado === 'BORRADOR' ? 'BORRADOR' : 'OFICIALIZADA',
      fecha: nota.fechaOficialTexto,
      numero: nota.numero,
      asunto: nota.asunto,
      destinatarioNombre: nota.destinatarioNombre,
      destinatarioCargo: nota.destinatarioCargo,
      institucion: nota.institucion?.nombre || nota.destino,
      contenido: nota.contenido,
      firmanteNombre: nota.firmanteNombre || firmanteBorrador?.nombre || '',
      firmanteCargo: nota.firmanteCargo || firmanteBorrador?.cargo || '',
      firmanteInstitucion: nota.firmanteInstitucion || nota.firmante?.institucion || firmanteBorrador?.institucion || '',
    };
  }

  protected cargarDatos(actualizarNumeros = true): void {
    this.listadoSubscription?.unsubscribe();
    this.filtrando.set(true);
    this.loadError.set(false);
    const params = new URLSearchParams({ page: String(this.pagina()), size: '25' });
    for (const [campo, valor] of Object.entries(this.filtrosAplicados)) if (valor.trim()) params.set(campo, valor.trim());
    this.listadoSubscription = this.api.get<NotaListadoRespuesta>(`/api/notas/listado?${params.toString()}`)
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: resultado => {
        this.notas.set((resultado.content || []).map(nota => this.mapNota(nota)));
        this.resumen.set(resultado.resumen || {});
        this.totalNotas.set(resultado.totalElements || 0);
        this.totalPaginas.set(resultado.totalPages || 0);
        this.pagina.set(resultado.page || 0);
        this.loading.set(false);
        this.filtrando.set(false);
        this.abrirEdicionSolicitada();
        if (actualizarNumeros) this.cargarNumeros();
      },
      error: () => {
        this.notas.set([]);
        this.loadError.set(true);
        this.loading.set(false);
        this.filtrando.set(false);
      },
    });
  }

  protected aplicarFiltros(): void {
    const anio = this.filtroAnio().trim();
    if (anio && (!/^\d{1,4}$/.test(anio) || Number(anio) < 1)) {
      void this.mostrarAlerta('Año no válido', 'Indica un año entre 1 y 9999.');
      return;
    }
    if (this.filtroFechaDesde() && this.filtroFechaHasta() && this.filtroFechaDesde() > this.filtroFechaHasta()) {
      void this.mostrarAlerta('Fechas no válidas', 'La fecha inicial no puede ser posterior a la fecha final.');
      return;
    }
    this.filtrosAplicados = {
      query: this.filtroTexto(),
      estado: this.filtroEstado() === 'TODAS' ? '' : this.filtroEstado(), anio,
      fechaDesde: this.filtroFechaDesde(), fechaHasta: this.filtroFechaHasta(),
    };
    this.pagina.set(0);
    this.cargarDatos(false);
  }

  protected limpiarFiltros(): void {
    this.filtroTexto.set('');
    this.filtroEstado.set('TODAS'); this.filtroAnio.set(''); this.filtroFechaDesde.set('');
    this.filtroFechaHasta.set('');
    this.aplicarFiltros();
  }

  protected cambiarPagina(pagina: number): void {
    if (this.filtrando() || pagina < 0 || pagina >= this.totalPaginas()) return;
    this.pagina.set(pagina);
    this.cargarDatos(false);
  }

  private cargarNumeros(): void {
    this.api.get<NotaNumeroRespuesta[]>('/api/notas/numeros').pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: numeros => this.numeros.set(numeros.map(numero => this.mapNumero(numero))),
      error: () => this.numeros.set([]),
    });
  }

  private cargarUsuarios(): void {
    this.api.get<UsuarioSeleccionableRespuesta[]>('/api/usuarios/seleccionables').pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: usuarios => this.usuarios.set(usuarios
        .filter(usuario => usuario.activo !== false && usuario.bloqueado !== true)
        .map(usuario => ({
          id: String(usuario.id),
          nombre: [usuario.nombre, usuario.segundoNombre, usuario.apellido].filter(Boolean).join(' ').trim(),
        }))
        .filter(usuario => !!usuario.id && !!usuario.nombre)
        .sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'))),
      error: () => this.usuarios.set([]),
    });
  }

  private cargarContextoBorrador(): void {
    this.api.get<NotaContextoBorrador>('/api/notas/contexto-borrador').pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: contexto => {
        this.fechaOficialTexto.set(contexto.fechaOficialTexto || '');
        this.directoraActual.set(contexto.directora ? {
          id: String(contexto.directora.id), nombre: contexto.directora.nombre || '', cargo: contexto.directora.cargo || '',
          institucion: contexto.directora.institucion || '',
        } : null);
        this.advertenciaDirectora.set(contexto.advertenciaDirectora || '');
      },
      error: () => { this.fechaOficialTexto.set(''); this.directoraActual.set(null); },
    });
  }

  private cargarInstituciones(): void {
    this.api.get<NotaInstitucionRespuesta[]>('/api/notas/instituciones').pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: items => this.instituciones.set(items.map(item => ({
        id: String(item.id), nombre: item.nombre || '', siglas: item.siglas || '',
      })).filter(item => !!item.id && !!item.nombre)),
      error: () => this.instituciones.set([]),
    });
  }

  protected onMenuToggle(): void { this.authService.sidebarCollapsed.update(value => !value); }
  protected onLogout(): void { this.authService.logout(); }
  protected verNota(nota: Nota): void { void this.router.navigate(['/correspondencia/notas', nota.id]); }
  protected estadoLabel(estado: NotaEstado): string {
    return ({ BORRADOR: 'Borrador', EN_SEGUIMIENTO: 'Oficializada', FIRMADA: 'Firmada', CERRADA: 'Cerrada', ANULADA: 'Anulada' })[estado];
  }
  protected estadoClase(estado: NotaEstado): string { return `estado-${estado.toLowerCase().replace('_', '-')}`; }
  protected formatFecha(value: string | null): string { return value ? this.authService.formatDate(value, { hour: '2-digit', minute: '2-digit' }) : '—'; }
  protected formatBytes(bytes: number | null): string {
    if (!bytes) return '—';
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  protected openNuevaNota(): void {
    if (!this.can('Crear')) return;
    if (!this.numerosSeleccionables().length) {
      void this.mostrarAlerta('No hay un consecutivo disponible', 'El siguiente número está reservado o debes cargar nuevos consecutivos antes de registrar una nota.');
      return;
    }
    this.editandoId.set(null);
    this.notaEnEdicion.set(null);
    this.formNumeroId.set('');
    this.formAsunto.set('');
    this.destinatarioForm.inicializar();
    this.institucionBusqueda.set('');
    this.formContenido.set('');
    this.formSeguimientoId.set('');
    this.formDescripcion.set('');
    this.formSensitiva.set(false);
    this.formSubmitted.set(false);
    this.relacionBusqueda.set('');
    this.relacionResultados.set([]);
    this.relacionesSeleccionadas.set([]);
    this.showNotaModal.set(true);
  }

  protected openEditarNota(nota: Nota): void {
    if (!this.can('Editar') || !['BORRADOR', 'EN_SEGUIMIENTO'].includes(nota.estado)) return;
    this.editandoId.set(nota.id);
    this.notaEnEdicion.set(nota);
    this.formNumeroId.set(nota.numeroId);
    this.formAsunto.set(nota.asunto);
    this.destinatarioForm.inicializar(nota);
    this.institucionBusqueda.set('');
    this.formContenido.set(nota.contenido);
    this.formSeguimientoId.set(nota.seguimientoUsuario.id);
    this.formDescripcion.set('');
    this.formSensitiva.set(nota.sensitiva);
    this.formSubmitted.set(false);
    this.relacionBusqueda.set('');
    this.relacionResultados.set([]);
    this.relacionesSeleccionadas.set([]);
    this.showNotaModal.set(true);
  }

  protected closeNotaModal(): void {
    if (this.saving() || this.reserving()) return;
    if (!this.editandoId() && this.formNumeroId()) {
      this.liberarReserva(this.formNumeroId(), true);
      return;
    }
    this.showNotaModal.set(false);
  }

  protected onNumeroSeleccionado(numeroId: string): void {
    if (!numeroId || this.reserving() || numeroId === this.formNumeroId()) return;
    const anterior = this.formNumeroId();
    this.reserving.set(true);
    const liberarAnterior$ = anterior
      ? this.api.delete<void>(`/api/notas/numeros/${anterior}/reserva`).pipe(catchError(() => of(undefined)))
      : of(undefined);
    liberarAnterior$.pipe(
      switchMap(() => this.api.post<NotaNumeroRespuesta>(`/api/notas/numeros/${numeroId}/reservar`, {})),
      finalize(() => this.reserving.set(false)), takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: response => {
        const reservado = this.mapNumero(response);
        this.formNumeroId.set(reservado.id);
        this.numeros.update(lista => lista.map(item => item.id === reservado.id ? reservado : item.id === anterior ? { ...item, estado: 'DISPONIBLE', reservadoPor: null, reservadoDesde: null, reservadoHasta: null, reservaPropia: false } : item));
        this.cargarNumeros();
      },
      error: error => {
        this.formNumeroId.set('');
        this.cargarNumeros();
        void this.mostrarAlerta('Número no disponible', this.mensajeError(error, 'Otra persona reservó este número. Selecciona uno diferente.'));
      },
    });
  }

  protected guardarNota(): void {
    this.formSubmitted.set(true);
    if (!this.formValido() || this.saving()) return;
    const payload = {
      asunto: this.formAsunto().trim(),
      destino: this.formDestino().trim(),
      destinatarioNombre: this.formDestinatarioNombre().trim(),
      destinatarioCargo: this.formDestinatarioCargo().trim(),
      institucionId: this.formInstitucionId() ? Number(this.formInstitucionId()) : null,
      departamentoId: this.formDepartamentoId() ? Number(this.formDepartamentoId()) : null,
      contenido: this.formContenido().trim(),
      seguimientoUsuarioId: Number(this.formSeguimientoId()),
      descripcion: this.formDescripcion().trim(),
      sensitiva: this.formSensitiva(),
      relaciones: this.editandoId() ? undefined : this.relacionesSeleccionadas().map(item => ({ notaRelacionadaId: Number(item.nota.id), tipo: item.tipo })),
    };
    if (!this.editandoId()) Object.assign(payload, { numeroId: Number(this.formNumeroId()) });
    const formData = new FormData();
    formData.append('datos', new Blob([JSON.stringify(payload)], { type: 'application/json' }), 'datos.json');

    this.saving.set(true);
    const id = this.editandoId();
    const request = id ? this.api.put<NotaRespuesta>(`/api/notas/${id}`, formData) : this.api.post<NotaRespuesta>('/api/notas', formData);
    request.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: response => {
        const nota = this.mapNota(response);
        this.notas.update(lista => id ? lista.map(item => item.id === id ? nota : item) : [nota, ...lista]);
        this.notaEnEdicion.set(nota);
        this.saving.set(false);
        this.formNumeroId.set('');
        this.showNotaModal.set(false);
        this.cargarNumeros();
        this.cargarDatos(false);
      },
      error: error => {
        this.saving.set(false);
        if (!id && error?.status === 409) {
          this.formNumeroId.set('');
          this.cargarNumeros();
        }
        void this.mostrarAlerta('No se pudo guardar la nota', this.mensajeError(error, 'Revisa los datos e inténtalo nuevamente.'));
      },
    });
  }

  protected buscarNotasRelacionables(): void {
    const query = this.relacionBusqueda().trim();
    if (query.length < 2 || this.buscandoRelaciones()) return;
    this.buscandoRelaciones.set(true);
    const excluirId = this.editandoId() ? `&excluirId=${this.editandoId()}` : '';
    this.api.get<{ content?: NotaBusquedaRespuesta[] }>(`/api/notas/buscar?query=${encodeURIComponent(query)}&page=0&size=20${excluirId}`)
      .pipe(finalize(() => this.buscandoRelaciones.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: response => this.relacionResultados.set((response.content || []).map((item) => this.mapBusqueda(item))
          .filter((item: NotaBusqueda) => !this.relacionesSeleccionadas().some(seleccionada => seleccionada.nota.id === item.id))),
        error: () => this.relacionResultados.set([]),
      });
  }

  protected agregarRelacion(nota: NotaBusqueda, tipo: NotaRelacionTipo): void {
    if (this.relacionesSeleccionadas().some(item => item.nota.id === nota.id && item.tipo === tipo)) return;
    this.relacionesSeleccionadas.update(items => [...items, { nota, tipo }]);
    this.relacionResultados.update(items => items.filter(item => item.id !== nota.id));
  }

  protected quitarRelacion(index: number): void {
    this.relacionesSeleccionadas.update(items => items.filter((_, itemIndex) => itemIndex !== index));
  }

  protected openNumeros(): void {
    if (!this.can('Gestionar números')) return;
    this.cargaNumeros.set('');
    this.showNumerosModal.set(true);
  }

  protected closeNumeros(): void {
    if (!this.saving()) this.showNumerosModal.set(false);
  }

  protected cargarLoteNumeros(): void {
    const numeros = this.cargaNumeros().split(/[\n,;\t]+/).map(valor => valor.trim()).filter(Boolean);
    if (!numeros.length || this.saving()) {
      void this.mostrarAlerta('Indica los números', 'Pega al menos un número de nota, uno por línea o separado por coma.');
      return;
    }
    this.saving.set(true);
    this.api.post<{ cargados: number }>('/api/notas/numeros/cargar', { numeros }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: respuesta => {
        this.saving.set(false);
        this.cargaNumeros.set('');
        this.cargarNumeros();
        void this.mostrarAlerta('Números cargados', `Se incorporaron ${respuesta.cargados} números disponibles al sistema.`);
      },
      error: error => {
        this.saving.set(false);
        void this.mostrarAlerta('No se pudo cargar el lote', this.mensajeError(error, 'Verifica que no existan números repetidos.'));
      },
    });
  }

  protected firmar(nota: Nota): void {
    if (!this.can('Firmar') || nota.estado !== 'EN_SEGUIMIENTO') return;
    void this.confirmar('Marcar como firmada', `La nota ${nota.numero} quedará firmada y no podrá editarse.`, () =>
      this.cambiarEstado(nota, 'firmar', 'firmar'));
  }

  protected oficializar(nota: Nota): void {
    if (!this.can('Firmar') || nota.estado !== 'BORRADOR') return;
    void this.confirmar('Finalizar y oficializar', `La nota ${nota.numero} dejará de ser borrador y conservará al Director o Directora activo como firmante histórico.`, () =>
      this.cambiarEstado(nota, 'oficializar', 'oficializar'));
  }

  protected cerrar(nota: Nota): void {
    if (!this.can('Cerrar') || nota.estado !== 'FIRMADA') return;
    void this.confirmar('Cerrar nota', `La nota ${nota.numero} se marcará como cerrada.`, () =>
      this.cambiarEstado(nota, 'cerrar', 'cerrar'));
  }

  private cambiarEstado(nota: Nota, accion: 'oficializar' | 'firmar' | 'cerrar', verbo: string): void {
    this.api.post<NotaRespuesta>(`/api/notas/${nota.id}/${accion}`, {}).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: response => {
        this.notaEnEdicion.set(this.mapNota(response));
        this.cargarDatos(false);
      },
      error: error => void this.mostrarAlerta(`No se pudo ${verbo} la nota`, this.mensajeError(error, 'Actualiza la información e inténtalo nuevamente.')),
    });
  }

  protected abrirPdf(nota: Nota): void {
    if (!this.can('Descargar') || !nota.tienePdf) return;
    this.api.getBlob(`/api/notas/${nota.id}/pdf`).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: archivo => {
        void this.fileActions.open(archivo, 'NOTA.pdf');
      },
      error: error => void this.mostrarAlerta('No se pudo abrir el PDF', this.mensajeError(error, 'El archivo no está disponible.')),
    });
  }

  protected descargarWord(nota: Nota, formato: 'docx' | 'pdf' = 'docx'): void {
    if (!this.can('Descargar') || nota.estado === 'BORRADOR' || nota.estado === 'ANULADA') return;
    this.api.getBlob(`/api/notas/${nota.id}/${formato === 'docx' ? 'word' : 'documento.pdf'}`).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: archivo => {
        void this.fileActions.save(archivo, (nota.documentoNombre || 'NOTA.DOCX').replace(/\.docx$/i, `.${formato.toUpperCase()}`));
      },
      error: error => void this.mostrarAlerta('No se pudo generar el documento', this.mensajeError(error, 'Verifica que la nota esté oficializada.')),
    });
  }

  private mapNota(data: NotaRespuesta): Nota {
    return {
      id: String(data.id), numeroId: String(data.numeroId), numero: data.numero || '', asunto: data.asunto || '',
      documentoNombre: data.documentoNombre,
      destino: data.destino || '', fechaOficial: data.fechaOficial || '', fechaOficialTexto: data.fechaOficialTexto || '',
      destinatarioNombre: data.destinatarioNombre || '', destinatarioCargo: data.destinatarioCargo || '',
      institucion: data.institucion ? { id: String(data.institucion.id), nombre: data.institucion.nombre || '', siglas: data.institucion.siglas || '' } : null,
      departamento: data.departamento ? { id: String(data.departamento.id), nombre: data.departamento.nombre || '' } : null,
      contenido: data.contenido || '', descripcion: data.descripcion || '', sensitiva: data.sensitiva === true, creadoPor: this.mapUsuario(data.creadoPor)!,
      seguimientoUsuario: this.mapUsuario(data.seguimientoUsuario)!, estado: (data.estado || 'BORRADOR') as NotaEstado,
      tienePdf: data.tienePdf === true, pdfNombre: data.pdfNombre || null,
      pdfTamanoBytes: data.pdfTamanoBytes == null ? null : Number(data.pdfTamanoBytes),
      firmadaPor: this.mapUsuario(data.firmadaPor), firmadaAt: data.firmadaAt || null,
      cerradaPor: this.mapUsuario(data.cerradaPor), cerradaAt: data.cerradaAt || null,
      oficializadaPor: this.mapUsuario(data.oficializadaPor), oficializadaAt: data.oficializadaAt || null,
      firmante: data.firmante ? { id: String(data.firmante.id), nombre: data.firmante.nombre || '', cargo: data.firmante.cargo || '', institucion: data.firmante.institucion || '' } : null,
      firmanteNombre: data.firmanteNombre || null, firmanteCargo: data.firmanteCargo || null,
      firmanteInstitucion: data.firmanteInstitucion || null,
      anuladaPor: this.mapUsuario(data.anuladaPor), anuladaAt: data.anuladaAt || null,
      anulacionMotivo: data.anulacionMotivo || null,
      createdAt: data.createdAt || '', updatedAt: data.updatedAt || '',
      historial: (data.historial || []).map((item) => ({
        id: String(item.id), usuario: this.mapUsuario(item.usuario), accion: item.accion || '',
        estadoAnterior: item.estadoAnterior || null, estadoNuevo: item.estadoNuevo || null,
        detalle: item.detalle || '', createdAt: item.createdAt || '',
      })),
      pdfs: (data.pdfs || []).map((item) => ({
        id: String(item.id), numeroVersion: Number(item.numeroVersion), nombreOriginal: item.nombreOriginal || 'nota.pdf',
        tipoMime: item.tipoMime || 'application/pdf', tamanoBytes: Number(item.tamanoBytes || 0),
        usuario: this.mapUsuario(item.usuario), createdAt: item.createdAt || '', vigente: item.vigente === true,
      })),
      relaciones: (data.relaciones || []).map((item) => ({
        id: String(item.id), tipo: item.tipo as NotaRelacionTipo, direccion: item.direccion === 'ENTRANTE' ? 'ENTRANTE' : 'SALIENTE',
        nota: this.mapBusqueda(item.nota), creadoPor: this.mapUsuario(item.creadoPor), createdAt: item.createdAt || '',
      })),
    };
  }

  private mapNumero(data: NotaNumeroRespuesta): NotaNumero {
    return {
      id: String(data.id), numero: data.numero || '',
      estado: data.estado === 'OCUPADO' ? 'OCUPADO' : data.estado === 'RESERVADO' ? 'RESERVADO' : 'DISPONIBLE',
      notaId: data.notaId == null ? null : String(data.notaId), tomadoPor: this.mapUsuario(data.tomadoPor), tomadoAt: data.tomadoAt || null,
      reservadoPor: this.mapUsuario(data.reservadoPor), reservadoDesde: data.reservadoDesde || null,
      reservadoHasta: data.reservadoHasta || null, reservaPropia: data.reservaPropia === true,
    };
  }

  private mapBusqueda(data: NotaBusquedaRespuesta | null | undefined): NotaBusqueda {
    return {
      id: String(data?.id || ''), numero: data?.numero || '', asunto: data?.asunto || '',
      estado: (data?.estado || 'EN_SEGUIMIENTO') as NotaEstado, sensitiva: data?.sensitiva === true,
      creadoPor: this.mapUsuario(data?.creadoPor), seguimientoUsuario: this.mapUsuario(data?.seguimientoUsuario),
    };
  }

  private liberarReserva(numeroId: string, cerrar: boolean): void {
    this.reserving.set(true);
    this.api.delete<void>(`/api/notas/numeros/${numeroId}/reserva`)
      .pipe(finalize(() => this.reserving.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => { this.formNumeroId.set(''); this.cargarNumeros(); if (cerrar) this.showNotaModal.set(false); },
        error: error => void this.mostrarAlerta('No se pudo liberar la reserva', this.mensajeError(error, 'La reserva vencerá automáticamente en unos minutos.')),
      });
  }

  private mapUsuario(data: NotaUsuarioRespuesta | null | undefined): NotaUsuario | null {
    return data?.id == null ? null : { id: String(data.id), nombre: data.nombre || 'Usuario' };
  }

  private abrirEdicionSolicitada(): void {
    const id = this.editarSolicitadaId;
    if (!id || this.loading()) return;
    const nota = id ? this.notas().find(item => item.id === id) : null;
    this.editarSolicitadaId = null;
    if (nota) this.openEditarNota(nota);
    else this.api.get<NotaRespuesta>(`/api/notas/${encodeURIComponent(id)}`).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: response => this.openEditarNota(this.mapNota(response)),
      error: error => void this.mostrarAlerta('No se pudo abrir la nota', this.mensajeError(error, 'Verifica que conservas acceso a la nota.')),
    });
    void this.router.navigate(['/correspondencia/notas'], { replaceUrl: true });
  }

  private async confirmar(titulo: string, mensaje: string, confirmar: () => void): Promise<void> {
    const alerta = await this.alertCtrl.create({
      header: titulo, message: mensaje,
      buttons: [{ text: 'Cancelar', role: 'cancel' }, { text: 'Confirmar', handler: confirmar }],
    });
    await alerta.present();
  }

  private async mostrarAlerta(titulo: string, mensaje: string): Promise<void> {
    const alerta = await this.alertCtrl.create({ header: titulo, message: mensaje, buttons: ['Aceptar'] });
    await alerta.present();
  }

  private mensajeError(error: unknown, fallback: string): string {
    return mensajeErrorApi(error, fallback);
  }
}
