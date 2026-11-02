import { FileActionsService } from '../../services/file-actions.service';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';
import { AlertController, IonIcon, IonModal } from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import {
  arrowBackOutline, checkmarkCircleOutline, closeOutline, createOutline,
  documentTextOutline, downloadOutline, folderOpenOutline, peopleOutline,
  personOutline, refreshOutline, timeOutline, cloudUploadOutline, linkOutline, trashOutline, searchOutline,
  lockClosedOutline,
  banOutline,
  eyeOutline,
} from 'ionicons/icons';
import { DashboardSidebarComponent } from '../../components/dashboard-sidebar/dashboard-sidebar.component';
import { DashboardTopbarComponent } from '../../components/dashboard-topbar/dashboard-topbar.component';
import { CorrespondenciaTabsComponent } from '../../components/correspondencia-tabs/correspondencia-tabs.component';
import { DashboardService } from '../../services/dashboard.service';
import { AuthService } from '../../services/auth.service';
import { ApiService, UsuarioSeleccionableRespuesta, mensajeErrorApi } from '../../services/api.service';
import { Notification } from '../../models/dashboard.models';
import { Nota, NotaBusqueda, NotaEstado, NotaInstitucion, NotaPdf, NotaRelacionTipo, NotaUsuario, UsuarioSeleccionable, NotaRespuesta, NotaBusquedaRespuesta, NotaUsuarioRespuesta, NotaInstitucionRespuesta, NotaContextoBorrador } from './notas.models';
import { NotaDocumentPreviewComponent, NotaPreviewData } from './nota-document-preview.component';
import { insertarSaltoPagina } from './notas.models';
import { NotaDestinatarioForm } from './nota-destinatario.form';

@Component({
  selector: 'app-nota-detalle',
  standalone: true,
  imports: [CommonModule, FormsModule, DashboardSidebarComponent, DashboardTopbarComponent, CorrespondenciaTabsComponent, NotaDocumentPreviewComponent, IonIcon, IonModal],
  templateUrl: './nota-detalle.page.html',
  styleUrls: ['./nota-detalle.page.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotaDetallePage implements OnInit {
  private readonly fileActions = inject(FileActionsService);
  protected readonly insertarSaltoPagina = insertarSaltoPagina;
  private readonly dashboardService = inject(DashboardService);
  protected readonly authService = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly alertCtrl = inject(AlertController);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly destinatarioForm = new NotaDestinatarioForm(this.api, this.destroyRef);
  private notaId = '';
  private seguimientoEnCurso?: Subscription;

  protected readonly menuItems = computed(() => this.dashboardService.getMenuItems());
  protected readonly notifications = signal<Notification[]>([]);
  protected readonly nota = signal<Nota | null>(null);
  protected readonly loading = signal(true);
  protected readonly loadError = signal(false);
  protected readonly uploading = signal(false);
  protected readonly deletingPdfId = signal<string | null>(null);
  protected readonly pdfPendienteEliminar = signal<NotaPdf | null>(null);
  protected readonly motivoEliminacionPdf = signal('');
  protected readonly eliminacionPdfIntentada = signal(false);
  protected readonly notaPendienteAnular = signal<Nota | null>(null);
  protected readonly motivoAnulacion = signal('');
  protected readonly anulacionIntentada = signal(false);
  protected readonly anulando = signal(false);
  protected readonly notaPendienteOficializar = signal<Nota | null>(null);
  protected readonly oficializando = signal(false);
  protected readonly errorOficializacion = signal('');
  protected readonly relacionando = signal(false);
  protected readonly seguimientoEstado = signal('');
  protected readonly seguimientoComentario = signal('');
  protected readonly guardandoSeguimiento = signal(false);
  protected readonly errorSeguimiento = signal('');
  protected readonly relacionBusqueda = signal('');
  protected readonly relacionTipo = signal<NotaRelacionTipo>('RELACIONADA_CON');
  protected readonly relacionResultados = signal<NotaBusqueda[]>([]);
  protected readonly usuarios = signal<UsuarioSeleccionable[]>([]);
  protected readonly instituciones = signal<NotaInstitucion[]>([]);
  protected readonly directoraActual = signal<{ nombre: string; cargo: string; institucion: string } | null>(null);
  protected readonly vistaPrevia = signal<NotaPreviewData | null>(null);
  protected readonly institucionBusqueda = signal('');
  protected readonly showEditModal = signal(false);
  protected readonly savingEdit = signal(false);
  protected readonly editSubmitted = signal(false);
  protected readonly editAsunto = signal('');
  protected readonly editFechaOficial = signal('');
  protected readonly editDestino = this.destinatarioForm.destino;
  protected readonly editDestinatarioNombre = this.destinatarioForm.nombre;
  protected readonly editDestinatarioCargo = this.destinatarioForm.cargo;
  protected readonly editInstitucionId = this.destinatarioForm.institucionId;
  protected readonly editDepartamentoId = this.destinatarioForm.departamentoId;
  protected readonly editContenido = signal('');
  protected readonly editSeguimientoId = signal('');
  protected readonly editDescripcion = signal('');
  protected readonly editSensitiva = signal(false);
  protected readonly institucionesFiltradas = computed(() => {
    const texto = this.institucionBusqueda().trim().toLocaleLowerCase();
    const original = this.destinatarioForm.institucionOriginal();
    const instituciones = original ? [original, ...this.instituciones().filter(item => item.id !== original.id)] : this.instituciones();
    if (!texto) return instituciones;
    return instituciones.filter(institucion =>
      `${institucion.nombre} ${institucion.siglas || ''}`.toLocaleLowerCase().includes(texto));
  });

  constructor() {
    addIcons({
      arrowBackOutline, checkmarkCircleOutline, closeOutline, createOutline,
      documentTextOutline, downloadOutline, folderOpenOutline, peopleOutline,
      personOutline, refreshOutline, timeOutline, cloudUploadOutline, linkOutline, trashOutline, searchOutline,
      lockClosedOutline,
      banOutline,
      eyeOutline,
    });
  }

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      this.seguimientoEnCurso?.unsubscribe();
      this.guardandoSeguimiento.set(false);
      this.notaId = params.get('id') || '';
      this.relacionBusqueda.set('');
      this.relacionResultados.set([]);
      this.seguimientoEstado.set('');
      this.seguimientoComentario.set('');
      this.errorSeguimiento.set('');
      this.cargarNota();
    });
    this.dashboardService.getNotificaciones().pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(notificaciones => this.notifications.set(notificaciones));
    this.cargarContextoBorrador();
    if (this.can('Editar')) { this.cargarUsuarios(); this.cargarInstituciones(); }
  }

  protected can(accion: string): boolean { return this.authService.hasPermission('Notas', accion); }
  protected onMenuToggle(): void { this.authService.sidebarCollapsed.update(value => !value); }
  protected onLogout(): void { this.authService.logout(); }
  protected volver(): void { void this.router.navigate(['/correspondencia/notas']); }
  protected abrirVistaPrevia(nota: Nota): void {
    const firmanteBorrador = nota.estado === 'BORRADOR' ? this.directoraActual() : null;
    this.vistaPrevia.set({
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
    });
  }
  protected abrirVistaPreviaEdicion(nota: Nota): void {
    const institucion = this.destinatarioForm.institucionOriginal() || this.instituciones().find(item => item.id === this.editInstitucionId());
    const firmanteBorrador = nota.estado === 'BORRADOR' ? this.directoraActual() : null;
    this.vistaPrevia.set({
      notaId: nota.id, numeroId: nota.numeroId, institucionId: this.editInstitucionId() || undefined, institucionDefinida: true,
      fechaOficial: this.editFechaOficial() || nota.fechaOficial,
      estado: nota.estado === 'ANULADA' ? 'ANULADA' : nota.estado === 'BORRADOR' ? 'BORRADOR' : 'OFICIALIZADA',
      fecha: this.editFechaOficial() ? new Intl.DateTimeFormat('es-PA', {
        timeZone: 'UTC', day: 'numeric', month: 'long', year: 'numeric',
      }).format(new Date(`${this.editFechaOficial()}T00:00:00Z`)) : nota.fechaOficialTexto,
      numero: nota.numero,
      asunto: this.editAsunto().trim(),
      destinatarioNombre: this.editDestinatarioNombre().trim(),
      destinatarioCargo: this.editDestinatarioCargo().trim(),
      institucion: institucion?.nombre || this.editDestino(),
      contenido: this.editContenido().trim(),
      firmanteNombre: nota.firmanteNombre || firmanteBorrador?.nombre || '',
      firmanteCargo: nota.firmanteCargo || firmanteBorrador?.cargo || '',
      firmanteInstitucion: nota.firmanteInstitucion || nota.firmante?.institucion || firmanteBorrador?.institucion || '',
    });
  }
  protected cerrarVistaPrevia(): void { this.vistaPrevia.set(null); }
  protected editar(): void {
    const nota = this.nota();
    if (!nota || !this.can('Editar') || nota.estado !== 'BORRADOR' || nota.oficializadaAt || nota.oficializadaPor) return;
    this.editAsunto.set(nota.asunto);
    this.editFechaOficial.set(nota.fechaOficial);
    this.destinatarioForm.inicializar(nota);
    this.institucionBusqueda.set('');
    this.editContenido.set(nota.contenido);
    this.editSeguimientoId.set(nota.seguimientoUsuario.id);
    this.editDescripcion.set('');
    this.editSensitiva.set(nota.sensitiva);
    this.editSubmitted.set(false);
    this.showEditModal.set(true);
  }

  protected cerrarEdicion(): void {
    if (!this.savingEdit()) this.showEditModal.set(false);
  }

  protected guardarEdicion(): void {
    const nota = this.nota();
    if (!nota || nota.estado !== 'BORRADOR' || nota.oficializadaAt || nota.oficializadaPor || !this.can('Editar')) return;
    this.editSubmitted.set(true);
    if (this.savingEdit() || !this.editSeguimientoId() || !this.editFechaOficial()) return;
    const datos = {
      asunto: this.editAsunto().trim(),
      destino: this.editDestino().trim(),
      destinatarioNombre: this.editDestinatarioNombre().trim(),
      destinatarioCargo: this.editDestinatarioCargo().trim(),
      institucionId: this.editInstitucionId() ? Number(this.editInstitucionId()) : null,
      departamentoId: this.editDepartamentoId() ? Number(this.editDepartamentoId()) : null,
      contenido: this.editContenido().trim(),
      seguimientoUsuarioId: Number(this.editSeguimientoId()),
      descripcion: this.editDescripcion().trim(),
      sensitiva: this.editSensitiva(),
      ...(this.nota()?.estado === 'BORRADOR' ? { fechaOficial: this.editFechaOficial() } : {}),
    };
    const formData = new FormData();
    formData.append('datos', new Blob([JSON.stringify(datos)], { type: 'application/json' }), 'datos.json');
    this.savingEdit.set(true);
    this.api.put<NotaRespuesta>(`/api/notas/${this.notaId}`, formData).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: response => {
        this.nota.set(this.mapNota(response));
        this.savingEdit.set(false);
        this.showEditModal.set(false);
      },
      error: error => {
        this.savingEdit.set(false);
        void this.mostrarAlerta('No se pudo actualizar la nota', this.mensajeError(error, 'Revisa los datos e inténtalo nuevamente.'));
      },
    });
  }
  protected estadoLabel(estado: NotaEstado): string { return ({ BORRADOR: 'Borrador', EN_SEGUIMIENTO: 'Oficializada', FIRMADA: 'Firmada', CERRADA: 'Cerrada', ANULADA: 'Anulada' })[estado]; }
  protected estadoClase(estado: NotaEstado): string { return `estado-${estado.toLowerCase().replace('_', '-')}`; }
  protected formatFecha(value: string | null): string { return value ? this.authService.formatDate(value, { hour: '2-digit', minute: '2-digit' }) : '—'; }
  protected formatBytes(bytes: number | null): string {
    if (!bytes) return '—';
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  protected cargarNota(): void {
    if (!this.notaId) {
      this.loadError.set(true);
      this.loading.set(false);
      return;
    }
    this.loading.set(true);
    this.loadError.set(false);
    this.api.get<NotaRespuesta>(`/api/notas/${this.notaId}`).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: nota => { this.nota.set(this.mapNota(nota)); this.loading.set(false); },
      error: () => { this.nota.set(null); this.loadError.set(true); this.loading.set(false); },
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
      next: contexto => this.directoraActual.set(contexto.directora ? {
        nombre: contexto.directora.nombre || '', cargo: contexto.directora.cargo || '', institucion: contexto.directora.institucion || '',
      } : null),
      error: () => this.directoraActual.set(null),
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

  protected oficializar(nota: Nota): void {
    if (!this.can('Firmar') || nota.estado !== 'BORRADOR') return;
    this.errorOficializacion.set('');
    this.notaPendienteOficializar.set(nota);
  }

  protected cerrarOficializacion(): void {
    if (!this.oficializando()) this.notaPendienteOficializar.set(null);
  }

  protected confirmarOficializacion(): void {
    const nota = this.notaPendienteOficializar();
    if (!nota || this.oficializando() || !this.can('Firmar')) return;
    this.oficializando.set(true);
    this.errorOficializacion.set('');
    this.api.post<NotaRespuesta>(`/api/notas/${nota.id}/oficializar`, {}).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: response => {
        this.nota.set(this.mapNota(response));
        this.oficializando.set(false);
        this.notaPendienteOficializar.set(null);
      },
      error: error => {
        this.oficializando.set(false);
        this.errorOficializacion.set(this.mensajeError(error, 'No se pudo oficializar la nota. Inténtalo nuevamente.'));
      },
    });
  }

  protected firmar(nota: Nota): void {
    if (!this.can('Firmar') || nota.estado !== 'EN_SEGUIMIENTO') return;
    void this.confirmar('Registrar firma de dirección', `Se registrará la firma de la directora o director en la nota ${nota.numero}. Después podrás registrar su entrega, recepción y respuesta en la trazabilidad.`, () => this.cambiarEstado('firmar'));
  }

  protected registrarSeguimiento(): void {
    const nota = this.nota();
    const estado = this.seguimientoEstado();
    const comentario = this.seguimientoComentario().trim();
    const permisos = nota?.seguimiento;
    if (!nota || nota.id !== this.notaId || nota.estado !== 'FIRMADA' || this.guardandoSeguimiento() || !permisos) return;
    if (estado ? !permisos.opciones.some(opcion => opcion.estado === estado) : !permisos.puedeComentar) return;
    this.errorSeguimiento.set('');
    if ((!estado && !comentario) || (comentario && (comentario.length < 3 || comentario.length > 5000))) {
      this.errorSeguimiento.set('Escribe un comentario de entre 3 y 5000 caracteres.');
      return;
    }
    if (comentario && !permisos.puedeComentar) return;
    this.guardandoSeguimiento.set(true);
    this.seguimientoEnCurso = this.api.post<NotaRespuesta>(`/api/notas/${nota.id}/seguimientos`, { estado: estado || null, comentario })
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: response => {
          this.nota.set(this.mapNota(response));
          this.seguimientoEstado.set(''); this.seguimientoComentario.set('');
          this.guardandoSeguimiento.set(false);
        },
        error: error => {
          this.guardandoSeguimiento.set(false);
          this.errorSeguimiento.set(this.mensajeError(error, 'No se pudo registrar el seguimiento. Revisa tus permisos e inténtalo nuevamente.'));
        },
      });
  }

  protected cerrar(nota: Nota): void {
    if (!this.can('Cerrar') || nota.estado !== 'FIRMADA') return;
    void this.confirmar('Cerrar nota', `La nota ${nota.numero} se marcará como cerrada.`, () => this.cambiarEstado('cerrar'));
  }

  protected anular(nota: Nota): void {
    if (!this.can('Anular') || nota.estado === 'ANULADA') return;
    this.notaPendienteAnular.set(nota);
    this.motivoAnulacion.set('');
    this.anulacionIntentada.set(false);
  }

  protected cerrarAnulacion(): void {
    if (this.anulando()) return;
    this.notaPendienteAnular.set(null);
    this.motivoAnulacion.set('');
    this.anulacionIntentada.set(false);
  }

  protected confirmarAnulacion(): void {
    const motivo = this.motivoAnulacion().trim();
    this.anulacionIntentada.set(true);
    if (!this.notaPendienteAnular() || motivo.length < 5 || this.anulando()) return;
    this.enviarAnulacion(motivo);
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

  protected abrirVersion(pdf: NotaPdf): void {
    if (!this.can('Descargar')) return;
    this.api.getBlob(`/api/notas/${this.notaId}/pdfs/${pdf.id}`).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: archivo => {
        void this.fileActions.open(archivo, 'NOTA.pdf');
      },
      error: error => void this.mostrarAlerta('No se pudo abrir esta versión', this.mensajeError(error, 'El archivo no está disponible.')),
    });
  }

  protected eliminarPdf(pdf: NotaPdf): void {
    if (!this.can('Eliminar PDF') || this.deletingPdfId()) return;
    this.pdfPendienteEliminar.set(pdf);
    this.motivoEliminacionPdf.set('');
    this.eliminacionPdfIntentada.set(false);
  }

  protected cerrarEliminacionPdf(): void {
    if (this.deletingPdfId()) return;
    this.pdfPendienteEliminar.set(null);
    this.motivoEliminacionPdf.set('');
    this.eliminacionPdfIntentada.set(false);
  }

  protected confirmarEliminacionPdf(): void {
    const pdf = this.pdfPendienteEliminar();
    const motivo = this.motivoEliminacionPdf().trim();
    this.eliminacionPdfIntentada.set(true);
    if (!pdf || motivo.length < 5 || this.deletingPdfId()) return;
    this.enviarEliminacionPdf(pdf, motivo);
  }

  private enviarEliminacionPdf(pdf: NotaPdf, motivo: string): void {
    this.deletingPdfId.set(pdf.id);
    this.api.post<NotaRespuesta>(`/api/notas/${this.notaId}/pdfs/${pdf.id}/eliminar`, { motivo })
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: response => {
          this.nota.set(this.mapNota(response));
          this.deletingPdfId.set(null);
          this.pdfPendienteEliminar.set(null);
          this.motivoEliminacionPdf.set('');
          this.eliminacionPdfIntentada.set(false);
        },
        error: error => {
          this.deletingPdfId.set(null);
          void this.mostrarAlerta('No se pudo eliminar el archivo', this.mensajeError(error, 'Revisa el motivo y tus permisos.'));
        },
      });
  }

  protected puedeAdjuntarPdf(nota: Nota): boolean {
    return this.can('Adjuntar PDF') && nota.puedeAdjuntarPdf === true;
  }

  protected adjuntarPdf(event: Event): void {
    const input = event.target as HTMLInputElement;
    const archivo = input.files?.item(0) ?? null;
    input.value = '';
    const nota = this.nota();
    if (!archivo || !nota || this.uploading() || !this.puedeAdjuntarPdf(nota)) return;
    if (!archivo.name.toLocaleLowerCase().endsWith('.pdf') || !archivo.size || archivo.size > 10 * 1024 * 1024) {
      void this.mostrarAlerta('PDF no válido', 'Selecciona un archivo PDF válido de hasta 10 MB.');
      return;
    }
    const formData = new FormData();
    formData.append('pdf', archivo, archivo.name);
    this.uploading.set(true);
    this.api.post<NotaRespuesta>(`/api/notas/${this.notaId}/pdf`, formData).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: response => { this.nota.set(this.mapNota(response)); this.uploading.set(false); },
      error: error => { this.uploading.set(false); void this.mostrarAlerta('No se pudo adjuntar el PDF', this.mensajeError(error, 'Verifica el archivo y tus permisos.')); },
    });
  }

  protected buscarRelacionables(): void {
    const query = this.relacionBusqueda().trim();
    if (query.length < 2 || this.relacionando()) return;
    this.relacionando.set(true);
    this.api.get<{ content?: NotaBusquedaRespuesta[] }>(`/api/notas/buscar?query=${encodeURIComponent(query)}&excluirId=${this.notaId}&page=0&size=20`)
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: response => {
          const vinculadas = new Set(this.nota()?.relaciones.map(item => item.nota.id) || []);
          this.relacionResultados.set((response.content || []).map(item => this.mapBusqueda(item)).filter((item: NotaBusqueda) => !vinculadas.has(item.id)));
          this.relacionando.set(false);
        },
        error: () => { this.relacionResultados.set([]); this.relacionando.set(false); },
      });
  }

  protected crearRelacion(relacionada: NotaBusqueda): void {
    if (!this.can('Relacionar') || this.relacionando()) return;
    this.relacionando.set(true);
    this.api.post<NotaRespuesta>(`/api/notas/${this.notaId}/relaciones`, { notaRelacionadaId: Number(relacionada.id), tipo: this.relacionTipo() })
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: response => { this.nota.set(this.mapNota(response)); this.relacionResultados.update(items => items.filter(item => item.id !== relacionada.id)); this.relacionando.set(false); },
        error: error => { this.relacionando.set(false); void this.mostrarAlerta('No se pudo relacionar la nota', this.mensajeError(error, 'Verifica que la relación no exista.')); },
      });
  }

  protected eliminarRelacion(relacionId: string): void {
    if (!this.can('Relacionar')) return;
    void this.confirmar('Eliminar relación', 'Se quitará el enlace, pero ninguna nota será eliminada.', () => {
      this.api.delete<void>(`/api/notas/${this.notaId}/relaciones/${relacionId}`).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: () => this.cargarNota(),
        error: error => void this.mostrarAlerta('No se pudo eliminar la relación', this.mensajeError(error, 'Actualiza la información e inténtalo nuevamente.')),
      });
    });
  }

  protected relacionLabel(tipo: NotaRelacionTipo, direccion: 'SALIENTE' | 'ENTRANTE'): string {
    if (tipo === 'RELACIONADA_CON') return 'Relacionada con';
    if (tipo === 'RESPONDE_A') return direccion === 'SALIENTE' ? 'Responde a' : 'Recibe respuesta de';
    return direccion === 'SALIENTE' ? 'Antecedente de' : 'Tiene como antecedente';
  }

  protected verRelacionada(id: string): void { void this.router.navigate(['/correspondencia/notas', id]); }

  private cambiarEstado(accion: 'oficializar' | 'firmar' | 'cerrar'): void {
    this.api.post<NotaRespuesta>(`/api/notas/${this.notaId}/${accion}`, {}).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: nota => this.nota.set(this.mapNota(nota)),
      error: error => void this.mostrarAlerta(`No se pudo ${accion} la nota`, this.mensajeError(error, 'Actualiza la información e inténtalo nuevamente.')),
    });
  }

  private enviarAnulacion(motivo: string): void {
    this.anulando.set(true);
    this.api.post<NotaRespuesta>(`/api/notas/${this.notaId}/anular`, { motivo }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: nota => {
        this.nota.set(this.mapNota(nota));
        this.anulando.set(false);
        this.notaPendienteAnular.set(null);
        this.motivoAnulacion.set('');
        this.anulacionIntentada.set(false);
      },
      error: error => {
        this.anulando.set(false);
        void this.mostrarAlerta('No se pudo anular la nota', this.mensajeError(error, 'Revisa el motivo y tus permisos.'));
      },
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
      tienePdf: data.tienePdf === true, puedeAdjuntarPdf: data.puedeAdjuntarPdf === true, pdfNombre: data.pdfNombre || null,
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
      seguimiento: data.seguimiento ? { ...data.seguimiento, usuario: this.mapUsuario(data.seguimiento.usuario),
        opciones: data.seguimiento.opciones || [] } : undefined,
      historial: (data.historial || []).map((item) => ({
        id: String(item.id), usuario: this.mapUsuario(item.usuario), accion: item.accion || '', accionTexto: item.accionTexto,
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

  private mapBusqueda(data: NotaBusquedaRespuesta | null | undefined): NotaBusqueda {
    return {
      id: String(data?.id || ''), numero: data?.numero || '', asunto: data?.asunto || '',
      estado: (data?.estado || 'EN_SEGUIMIENTO') as NotaEstado, sensitiva: data?.sensitiva === true,
      creadoPor: this.mapUsuario(data?.creadoPor), seguimientoUsuario: this.mapUsuario(data?.seguimientoUsuario),
    };
  }

  private mapUsuario(data: NotaUsuarioRespuesta | null | undefined): NotaUsuario | null {
    return data?.id == null ? null : { id: String(data.id), nombre: data.nombre || 'Usuario' };
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

  private mensajeError(error: unknown, fallback: string): string { return mensajeErrorApi(error, fallback); }
}
