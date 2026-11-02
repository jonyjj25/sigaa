import { TestBed } from '@angular/core/testing';
import { signal, WritableSignal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { AlertController, ActionSheetController } from '@ionic/angular/standalone';
import { Subject, of, throwError } from 'rxjs';
import { DocumentosPage } from './documentos.page';
import { DocumentoItem, DocumentoCompartido, CarpetaItem, DocumentoHistorial, DocumentoVersionItem } from './documentos.models';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { DashboardService } from '../../services/dashboard.service';

const documento: DocumentoItem = { id: '17', nombre: 'Prueba', descripcion: '', categoria: 'Acta', area: 'Personal',
  clasificacion: 'USO_INTERNO', fecha: '2026-10-05', estado: 'borrador', version: '1.0', propietario: 'Usuario de prueba',
  propietarioId: 1, origen: 'PROPIO', paginas: 0, tamano: '', compartido: false };

class DocumentosTestPage extends DocumentosPage {
  preparar(doc?: DocumentoItem) { this.openNuevoDocModal('Prueba', doc); this.formNombre.set('Prueba'); }
  guardar() { return this.guardarDocumento(); }
  estado() { return { guardando: this.guardandoDocumento(), abierto: this.nuevoDocOpen(), id: this.editandoId(), documentos: this.documentos() }; }
  adjuntar() { this.archivosSubir.set([{ nombre: 'prueba.txt', tamano: '5 B', archivo: new File(['nuevo'], 'prueba.txt') }]); }
  seleccionar(doc: DocumentoItem) { this.documentos.set([doc]); this.selectDocumento(doc); }
  compartir(doc: DocumentoItem) { this.openShareModal(doc); }
  usuariosSeleccionables() { return this.usuariosCompartir(); }
  cambiarPermiso(nivel: string) { this.selectPermiso(nivel); }
  permiso() { return this.permisoActual(); }
  resolver(doc: DocumentoItem, decision: 'aprobar' | 'rechazar') { this.resolverAprobacion(doc, decision); }
  seleccionado() { return this.selectedDoc(); }
  campos() {
    return {
      mostrarSolicitud: this.showSolicitarAprobacion,
      puedeDescargar: (doc: DocumentoItem) => this.puedeDescargarDocumento(doc),
      completo: () => this.abrirDocumentoCompleto(),
      nombre: this.formNombre, archivos: this.archivosSubir, documentos: this.documentos,
      cerrar: () => this.closeNuevoDocModal(), archivo: (event: Event) => this.onFileSelected(event),
      eliminarArchivo: (nombre: string) => this.eliminarArchivo(nombre),
      eliminar: (doc: DocumentoItem) => this.eliminarDocumento(doc),
      preview: () => this.togglePreview(), previewVisible: this.previewVisible, previewUrl: this.previewUrl,
      cerrarDetalle: () => this.closeDetail(),
      usuarioCompartir: this.compartirUsuarioId, permisoCompartir: this.compartirPermiso, expira: this.compartirExpira,
      accesos: this.compartidosActuales, guardarAcceso: () => this.guardarComparticion(),
      retirarAcceso: (acceso: DocumentoCompartido) => this.retirarComparticion(acceso),
      abrirSolicitud: () => this.abrirSolicitudAprobacion(), solicitar: () => this.solicitarAprobacion(),
      aprobador: this.aprobadorSeleccionadoId, comentario: this.comentarioSolicitud,
      solicitudOpen: this.solicitudAprobacionOpen, puedeResolver: this.puedeResolverAprobaciones,
      carpetas: this.carpetas, carpetaActiva: this.carpetaActiva,
      eliminarCarpeta: (carpeta: CarpetaItem, event: Event) => this.eliminarCarpeta(carpeta, event),
      acciones: (doc: DocumentoItem) => this.abrirAcciones(doc, new Event('click')),
      filtros: this.filtros, filtrados: this.documentosFiltrados, paginados: this.paginatedDocumentos,
      pagina: this.currentPage, ir: (numero: number) => this.goToPage(numero),
      filtrar: (key: Parameters<DocumentosTestPage['updateFilter']>[0], value: string) => this.updateFilter(key, value),
      pestana: (tab: string) => this.setTab(tab), limpiar: () => this.limpiarFiltros(),
      abrirCarpeta: () => this.openNuevaCarpetaModal(), crearCarpeta: () => this.crearCarpeta(),
      nombreCarpeta: this.formCarpetaNombre, tipoCarpeta: this.formCarpetaTipo, miembros: this.formCarpetaMiembroIds,
      carpetaOpen: this.nuevaCarpetaOpen,
      iniciarArrastre: (event: PointerEvent, scroller: HTMLElement) => this.beginHorizontalDrag(event, scroller),
      moverArrastre: (event: PointerEvent) => this.moveHorizontalDrag(event),
      historial: this.historial, historialOpen: this.historialOpen, cargandoHistorial: this.cargandoHistorial,
      errorHistorial: this.errorHistorial, abrirHistorial: () => this.abrirHistorial(),
      recargarHistorial: () => this.recargarHistorial(), descargarVersion: (version: DocumentoVersionItem) => this.descargarVersion(version),
      versionActual: this.versionFormulario, categorias: this.catalogoCategorias, categoriasDisponibles: this.categoriasDisponibles,
      clasificacion: this.formClasificacion,
    };
  }
}

describe('DocumentosPage: guardado seguro', () => {
  let api: jasmine.SpyObj<ApiService>;
  let page: DocumentosTestPage;
  let alerts: jasmine.Spy;
  let sesion: Subject<void>;
  let usuario: WritableSignal<{ id: number; nombre: string }>;
  let actionSheets: jasmine.Spy;
  let admin: boolean;
  beforeEach(() => {
    admin = false;
    actionSheets = jasmine.createSpy('create').and.resolveTo({ present: async () => undefined });
    sesion = new Subject<void>(); usuario = signal({ id: 1, nombre: 'Usuario de prueba' });
    alerts = jasmine.createSpy('create').and.resolveTo({ present: async () => undefined });
    api = jasmine.createSpyObj<ApiService>('ApiService', ['get', 'post', 'put', 'patch', 'delete', 'getBlob']);
    api.get.and.returnValue(of([])); api.post.and.returnValue(of(documento)); api.put.and.returnValue(of(documento));
    TestBed.configureTestingModule({ providers: [
      { provide: ApiService, useValue: api },
      { provide: AuthService, useValue: { currentUser: usuario, isAuthenticated: () => true, isAdmin: () => admin,
        isInstitutionalManager: () => admin, hasPermission: () => true, sessionChanged$: sesion } },
      { provide: DashboardService, useValue: { getNotificaciones: () => of([]) } },
      { provide: ActivatedRoute, useValue: { queryParamMap: of(convertToParamMap({})) } },
      { provide: AlertController, useValue: { create: alerts } },
      { provide: ActionSheetController, useValue: { create: actionSheets } },
    ] });
    page = TestBed.runInInjectionContext(() => new DocumentosTestPage()); page.preparar();
  });

  it('shows approval only for a manageable selected document with content', () => {
    expect(page.campos().mostrarSolicitud()).toBeFalse();
    page.seleccionar({ ...documento, archivoRuta: 'prueba.pdf', puedeEditar: false });
    expect(page.campos().mostrarSolicitud()).toBeFalse();
    page.seleccionar({ ...documento, archivoRuta: 'prueba.pdf', puedeEditar: true });
    expect(page.campos().mostrarSolicitud()).toBeTrue();
    page.seleccionar({ ...documento, origen: 'TAREA', archivoRuta: 'prueba.pdf', puedeEditar: true });
    expect(page.campos().mostrarSolicitud()).toBeFalse();
  });

  it('requires the download permission used by each existing endpoint', () => {
    spyOn(TestBed.inject(AuthService), 'hasPermission').and.callFake((module, action) => module === 'Tareas' && action === 'Ver');
    expect(page.campos().puedeDescargar({ ...documento, puedeDescargar: true })).toBeFalse();
    expect(page.campos().puedeDescargar({ ...documento, origen: 'TAREA', puedeDescargar: true })).toBeTrue();
  });

  it('does not open a completed download after the session changes', () => {
    const response = new Subject<Blob>(); api.getBlob.and.returnValue(response);
    const close = jasmine.createSpy('close');
    spyOn(window, 'open').and.returnValue({ close, location: { href: '' } } as unknown as Window);
    const createUrl = spyOn(URL, 'createObjectURL');
    page.seleccionar({ ...documento, archivoRuta: 'prueba.pdf', puedeDescargar: true });
    page.campos().completo();
    usuario.set({ id: 2, nombre: 'Otro usuario de prueba' }); sesion.next();
    response.next(new Blob(['TEST_ONLY_DOCUMENT'])); response.complete();
    expect(close).toHaveBeenCalled();
    expect(createUrl).not.toHaveBeenCalled();
  });

  async function confirmar(): Promise<void> {
    const options = alerts.calls.first().args[0] as NonNullable<Parameters<AlertController['create']>[0]>;
    const button = options.buttons?.find(item => typeof item !== 'string' && item.role === 'destructive');
    if (!button || typeof button === 'string' || !button.handler) throw new Error('Falta la confirmación');
    await button.handler(undefined);
  }

  function historialDe(doc: DocumentoItem): DocumentoHistorial {
    return { documento: doc, versiones: [], registrosAnteriores: [], aprobacionesSinVersion: [], archivoActualDisponible: !!doc.archivoRuta };
  }

  async function prepararSolicitud(): Promise<DocumentoItem> {
    const doc = { ...documento, archivoRuta: '/api/documentos/17/descargar', versionActualId: 11, puedeEditar: true };
    page.seleccionar(doc);
    api.get.and.callFake(<T>(url: string) => of((url.endsWith('/versiones') ? historialDe(doc) : []) as T));
    await page.campos().abrirSolicitud(); return doc;
  }

  function seleccionarArchivo(...files: File[]): HTMLInputElement {
    const input = document.createElement('input'); input.type = 'file';
    const transfer = new DataTransfer(); files.forEach(file => transfer.items.add(file)); input.files = transfer.files;
    const event = new Event('change'); Object.defineProperty(event, 'target', { value: input });
    page.campos().archivo(event); return input;
  }

  it('no consulta historial al seleccionar y comparte una única consulta simultánea', async () => {
    const respuesta = new Subject<DocumentoHistorial>(); api.get.and.returnValue(respuesta);
    page.seleccionar(documento); expect(api.get).not.toHaveBeenCalled();
    const primera = page.campos().abrirHistorial(), segunda = page.campos().abrirHistorial();
    expect(api.get).toHaveBeenCalledOnceWith('/api/documentos/17/versiones');
    respuesta.next(historialDe(documento)); respuesta.complete(); await Promise.all([primera, segunda]);
    expect(page.campos().historial()?.documento.id).toBe('17'); expect(page.campos().cargandoHistorial()).toBeFalse();
  });

  it('el historial puede recuperarse después de un error temporal', async () => {
    page.seleccionar(documento); api.get.and.returnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
    await page.campos().abrirHistorial(); expect(page.campos().errorHistorial()).toBeTrue();
    api.get.and.returnValue(of(historialDe(documento))); await page.campos().recargarHistorial();
    expect(page.campos().errorHistorial()).toBeFalse(); expect(page.campos().historial()?.documento.id).toBe('17');
  });

  it('un historial tardío no reemplaza el documento seleccionado después', async () => {
    const respuesta = new Subject<DocumentoHistorial>(); api.get.and.returnValue(respuesta);
    page.seleccionar(documento); const primera = page.campos().abrirHistorial();
    const otro = { ...documento, id: '18', nombre: 'Otro documento' }; page.seleccionar(otro);
    respuesta.next(historialDe(documento)); respuesta.complete(); await primera;
    expect(page.seleccionado()).toEqual(otro); expect(page.campos().historial()).toBeNull();
  });

  it('una cuenta nueva no recibe el historial solicitado por la anterior', async () => {
    page.ngOnInit(); page.seleccionar(documento);
    const respuesta = new Subject<DocumentoHistorial>(); api.get.and.returnValue(respuesta);
    const consulta = page.campos().abrirHistorial();
    usuario.set({ id: 2, nombre: 'Segunda cuenta ficticia' }); api.get.and.returnValue(of([])); sesion.next();
    respuesta.next(historialDe(documento)); respuesta.complete(); await consulta;
    expect(page.campos().historial()).toBeNull(); expect(page.seleccionado()).toBeNull();
  });

  it('sin archivo no consulta aprobadores ni permite iniciar una aprobación', async () => {
    page.seleccionar({ ...documento, puedeEditar: true }); await page.campos().abrirSolicitud();
    expect(api.get).not.toHaveBeenCalled(); expect(page.campos().solicitudOpen()).toBeFalse();
    expect(alerts.calls.mostRecent().args[0].message).toContain('Adjunta un archivo');
  });

  it('evita solicitudes de aprobación duplicadas mientras se guarda', async () => {
    await prepararSolicitud(); const respuesta = new Subject<DocumentoItem>(); api.patch.and.returnValue(respuesta);
    page.campos().aprobador.set(4); page.campos().solicitar(); page.campos().solicitar();
    expect(api.patch).toHaveBeenCalledTimes(1); respuesta.complete();
  });

  it('no permite resolver solicitudes antiguas sin versión verificable', () => {
    const doc: DocumentoItem = { ...documento, solicitudAprobacion: { id: 3, estado: 'PENDIENTE',
      solicitanteId: 2, solicitante: 'Solicitante', aprobadorId: 1, aprobador: 'Revisor', fecha: '2026-10-05', comentario: '' } };
    page.resolver(doc, 'aprobar'); expect(api.patch).not.toHaveBeenCalled();
    expect(alerts.calls.mostRecent().args[0].message).toContain('versión verificable');
  });

  it('un reemplazo utiliza la versión y estado devueltos por backend', async () => {
    const aprobado = { ...documento, version: '1.0', versionActualId: 11, estado: 'aprobado' as const, archivoRuta: '/api/documentos/17/descargar' };
    page.seleccionar(aprobado); page.preparar(aprobado); page.adjuntar();
    api.put.and.returnValue(of(aprobado));
    api.post.and.returnValue(of({ ...aprobado, version: '2.0', versionActualId: 12, estado: 'borrador', contenidoIdentico: false }));
    await page.guardar();
    expect(page.seleccionado()?.version).toBe('2.0'); expect(page.seleccionado()?.estado).toBe('borrador');
    expect(api.put.calls.mostRecent().args[1]).not.toEqual(jasmine.objectContaining({ version: jasmine.anything() }));
  });

  it('el catálogo evita variantes duplicadas y conserva la política de clasificaciones heredadas', () => {
    page.campos().categorias.set(['Acta', 'ACTA', ' acta ', 'Informe']); page.seleccionar({ ...documento, categoria: 'Actas' });
    expect(page.campos().categoriasDisponibles()).toEqual(['Acta', 'Informe']);
    page.preparar({ ...documento, clasificacion: 'DATOS_SENSIBLES' }); expect(page.campos().clasificacion()).toBe('CONFIDENCIAL');
  });

  it('descarga una versión por su endpoint autorizado y conserva el nombre del archivo', () => {
    page.seleccionar(documento); api.getBlob.and.returnValue(of(new Blob(['histórico'], { type: 'text/plain' })));
    spyOn(URL, 'createObjectURL').and.returnValue('blob:test'); spyOn(URL, 'revokeObjectURL');
    const click = spyOn(HTMLAnchorElement.prototype, 'click');
    const version: DocumentoVersionItem = { id: 11, version: '1.0', autor: 'Autor ficticio', fecha: '2099-01-01T10:00:00',
      lineaBase: false, cambios: '', actual: false, disponible: true, archivoNombre: 'anterior.txt', aprobaciones: [] };
    page.campos().descargarVersion(version);
    expect(api.getBlob).toHaveBeenCalledWith('/api/documentos/17/versiones/11/descargar');
    expect((click.calls.mostRecent().object as unknown as HTMLAnchorElement).download).toBe('anterior.txt');
  });

  it('una descarga histórica tardía no se entrega a la siguiente sesión', () => {
    page.ngOnInit(); page.seleccionar(documento);
    const respuesta = new Subject<Blob>(); api.getBlob.and.returnValue(respuesta);
    const click = spyOn(HTMLAnchorElement.prototype, 'click');
    page.campos().descargarVersion({ id: 11, version: '1.0', autor: 'Autor ficticio', fecha: '2099-01-01',
      lineaBase: false, cambios: '', actual: false, disponible: true, aprobaciones: [] });
    usuario.set({ id: 2, nombre: 'Segunda cuenta ficticia' }); sesion.next();
    respuesta.next(new Blob(['anterior'])); respuesta.complete();
    expect(click).not.toHaveBeenCalled();
  });

  it('una vista previa tardía no se muestra en otro documento', () => {
    const respuesta = new Subject<Blob>(); api.getBlob.and.returnValue(respuesta);
    page.seleccionar({ ...documento, archivoRuta: '/api/documentos/17/descargar', versionActualId: 11 });
    page.campos().preview();
    page.seleccionar({ ...documento, id: '18', versionActualId: 12 });
    respuesta.next(new Blob(['anterior'])); respuesta.complete();
    expect(page.campos().previewUrl()).toBeNull(); expect(page.campos().previewVisible()).toBeFalse();
  });

  it('blocks a second click until the first request finishes', async () => {
    const respuesta = new Subject<DocumentoItem>(); api.post.and.returnValue(respuesta);
    const primera = page.guardar(); await page.guardar();
    expect(api.post).toHaveBeenCalledTimes(1); expect(page.estado().guardando).toBeTrue();
    respuesta.next(documento); respuesta.complete(); await primera;
    expect(page.estado().guardando).toBeFalse(); expect(page.estado().abierto).toBeFalse();
    expect(api.get).not.toHaveBeenCalledWith('/api/documentos');
  });

  it('unlocks on failure and reuses the creation identifier on retry', async () => {
    api.post.and.returnValue(throwError(() => ({ status: 503 })));
    await page.guardar(); const primero = api.post.calls.mostRecent().args[1] as { creacionId: string };
    expect(page.estado().guardando).toBeFalse(); expect(page.estado().abierto).toBeTrue();
    api.post.and.returnValue(of(documento)); await page.guardar();
    expect((api.post.calls.mostRecent().args[1] as { creacionId: string }).creacionId).toBe(primero.creacionId);
  });

  it('saves with a valid creation UUID when randomUUID is unavailable', async () => {
    const cryptoOriginal = window.crypto;
    spyOnProperty(window, 'crypto', 'get').and.returnValue({
      getRandomValues: cryptoOriginal.getRandomValues.bind(cryptoOriginal),
    } as Crypto);
    await page.guardar();
    expect(api.post).toHaveBeenCalledTimes(1);
    const body = api.post.calls.mostRecent().args[1] as { creacionId: string };
    expect(body.creacionId).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
    expect(page.estado().guardando).toBeFalse();
    expect(page.estado().abierto).toBeFalse();
  });

  it('retries an upload using the existing document instead of creating another', async () => {
    page.adjuntar(); api.post.and.returnValues(of(documento), throwError(() => ({ status: 500 })));
    await page.guardar();
    expect(page.estado().id).toBe('17'); expect(page.estado().abierto).toBeTrue(); expect(page.estado().guardando).toBeFalse();
    api.post.and.returnValue(of({ archivoRuta: 'prueba.txt', tamano: '5 B', paginas: 0 }));
    await page.guardar();
    expect(api.put).toHaveBeenCalledWith('/api/documentos/17', jasmine.any(Object));
    expect(api.post.calls.allArgs().filter(args => args[0] === '/api/documentos').length).toBe(1);
  });

  it('preserves state, date and version when editing an approved document', async () => {
    page.preparar({ ...documento, estado: 'aprobado', fecha: '2025-01-01', version: '3.0' });
    await page.guardar(); const body = api.put.calls.mostRecent().args[1] as Record<string, unknown>;
    expect(body['estado']).toBeUndefined(); expect(body['fecha']).toBeUndefined(); expect(body['version']).toBeUndefined();
    expect(body).toEqual({ nombre: 'Prueba', descripcion: '', categoria: 'Acta', area: 'Personal', propietario: 'Usuario de prueba', clasificacion: 'RESTRINGIDO', carpetaId: null });
    expect(api.put).toHaveBeenCalledWith('/api/documentos/17', body);
  });

  it('loads each initial resource once and requests folders without duplicated documents', () => {
    page.ngOnInit();
    expect(api.get.calls.allArgs().filter(args => args[0] === '/api/documentos').length).toBe(1);
    expect(api.get.calls.allArgs().filter(args => args[0] === '/api/documentos/carpetas?incluirDocumentos=false').length).toBe(1);
    expect(api.get.calls.allArgs().filter(args => args[0] === '/api/documentos/categorias').length).toBe(1);
    expect(api.get.calls.allArgs().length).toBe(4);
  });

  it('maps selectable users and excludes the owner, inactive and blocked accounts', () => {
    const doc = { ...documento, propietarioId: 1, puedeAdministrarAcceso: true };
    api.get.and.callFake(<T>(url: string) => of((url === '/api/usuarios/seleccionables' ? [
      { id: 1, nombre: 'Propietario' }, { id: 2, nombre: 'Inactivo', activo: false },
      { id: 3, nombre: 'Bloqueado', bloqueado: true },
      { id: '4', nombre: 'Ana', segundoNombre: 'María', apellido: 'Prueba', correo: 'ana@example.test', cargo: 'Responsable' },
      { id: 5, nombre: 'Persona sin campos opcionales' },
    ] : []) as T));
    page.compartir(doc);
    expect(page.usuariosSeleccionables()).toEqual([
      { id: 4, nombre: 'Ana María Prueba', correo: 'ana@example.test', cargo: 'Responsable' },
      { id: 5, nombre: 'Persona sin campos opcionales', correo: '', cargo: '' },
    ]);
  });

  it('does not request sharing data without permission to manage access', () => {
    page.compartir({ ...documento, puedeAdministrarAcceso: false });
    expect(api.get).not.toHaveBeenCalled();
  });

  it('keeps the previous permission and document data when the API rejects the update', () => {
    const anterior = { ...documento, permiso: 'solo_lectura' };
    page.seleccionar(anterior);
    api.patch.and.returnValue(throwError(() => ({ status: 403 })));
    page.cambiarPermiso('editar');
    expect(api.patch).toHaveBeenCalledWith('/api/documentos/17/permiso', { nivel: 'editar' });
    expect(page.permiso()).toBe('solo_lectura');
    expect(page.seleccionado()).toEqual(anterior);
    expect(page.estado().documentos).toEqual([anterior]);
  });

  it('uses the complete approval response without replacing another document', () => {
    const otro = { ...documento, id: '18', nombre: 'Otro documento' };
    page.seleccionar(otro);
    const aprobado: DocumentoItem = { ...documento, versionActualId: 11, estado: 'aprobado', solicitudAprobacion: null, archivoRuta: null };
    api.patch.and.returnValue(of(aprobado));
    const solicitado: DocumentoItem = { ...documento, versionActualId: 11, solicitudAprobacion: {
      id: 3, estado: 'PENDIENTE', versionId: 11, version: '1.0', solicitanteId: 2, solicitante: 'Solicitante',
      aprobadorId: 1, aprobador: 'Usuario de prueba', fecha: '2026-10-05', comentario: '',
    } };
    page.resolver(solicitado, 'aprobar');
    expect(api.patch).toHaveBeenCalledWith('/api/documentos/17/resolver-aprobacion', { decision: 'aprobar', solicitudId: 3, versionId: 11 });
    expect(page.seleccionado()).toEqual(otro);
    expect(page.estado().documentos).toEqual([otro]);
    page.seleccionar(solicitado); page.resolver(solicitado, 'aprobar');
    expect(page.seleccionado()).toEqual(aprobado);
    expect(page.estado().documentos).toEqual([aprobado]);
  });

  it('no envía un nombre vacío y evita cerrar el formulario mientras se guarda', async () => {
    page.campos().nombre.set('   '); await page.guardar(); expect(api.post).not.toHaveBeenCalled();
    page.campos().nombre.set('Prueba'); const result = new Subject<DocumentoItem>(); api.post.and.returnValue(result);
    const pending = page.guardar(); page.campos().cerrar(); expect(page.estado().abierto).toBeTrue();
    result.next(documento); result.complete(); await pending; expect(page.estado().abierto).toBeFalse();
  });

  it('acepta un solo archivo compatible y permite reemplazar la selección después de retirarla', () => {
    const first = new File(['uno'], 'primero.txt'); const second = new File(['dos'], 'segundo.txt');
    seleccionarArchivo(first); expect(page.campos().archivos()[0].archivo).toBe(first);
    seleccionarArchivo(second); expect(page.campos().archivos()[0].archivo).toBe(first);
    expect(alerts.calls.mostRecent().args[0].message).toContain('Cada documento admite un archivo');
    page.campos().eliminarArchivo(first.name); seleccionarArchivo(second);
    expect(page.campos().archivos()[0].archivo).toBe(second);
  });

  it('rechaza extensiones, archivos vacíos, demasiado grandes y selecciones múltiples', () => {
    seleccionarArchivo(new File(['ejecutable'], 'prueba.exe')); expect(page.campos().archivos()).toEqual([]);
    seleccionarArchivo(new File([], 'vacio.txt')); expect(page.campos().archivos()).toEqual([]);
    const grande = new File(['datos'], 'grande.txt'); Object.defineProperty(grande, 'size', { value: 10 * 1024 * 1024 + 1 });
    seleccionarArchivo(grande); expect(page.campos().archivos()).toEqual([]);
    seleccionarArchivo(new File(['a'], 'a.txt'), new File(['b'], 'b.txt')); expect(page.campos().archivos()).toEqual([]);
    expect(alerts).toHaveBeenCalledTimes(4);
  });

  it('una respuesta de la cuenta anterior no reemplaza los documentos de la sesión actual', () => {
    const anterior = new Subject<DocumentoItem[]>(); const actual = new Subject<DocumentoItem[]>(); let consultas = 0;
    api.get.and.callFake(<T>(url: string) => url === '/api/documentos'
      ? (++consultas === 1 ? anterior : actual) as unknown as Subject<T> : of([] as T));
    page.ngOnInit(); usuario.set({ id: 2, nombre: 'Otra cuenta de prueba' }); sesion.next();
    const nuevo = { ...documento, id: '18', propietario: 'Otra cuenta de prueba' };
    actual.next([nuevo]); anterior.next([documento]);
    expect(page.estado().documentos).toEqual([nuevo]); expect(page.seleccionado()).toBeNull();
  });

  it('un guardado tardío de otra cuenta no incorpora sus datos ni inicia adjuntos', async () => {
    const result = new Subject<DocumentoItem>(); api.post.and.returnValue(result); page.adjuntar();
    const pending = page.guardar(); usuario.set({ id: 2, nombre: 'Otra cuenta' });
    result.next(documento); result.complete(); await pending;
    expect(page.estado().documentos).toEqual([]); expect(page.seleccionado()).toBeNull();
    expect(api.post).toHaveBeenCalledTimes(1); expect(page.estado().guardando).toBeFalse();
  });

  it('eliminar requiere confirmación y conserva el listado si falla la API', async () => {
    const eliminable = { ...documento, puedeEliminar: true };
    page.seleccionar(eliminable); await page.campos().eliminar(eliminable); expect(api.delete).not.toHaveBeenCalled();
    api.delete.and.returnValue(throwError(() => ({ status: 503 }))); await confirmar();
    expect(page.estado().documentos).toEqual([eliminable]); expect(page.seleccionado()).toEqual(eliminable);
    expect(alerts.calls.mostRecent().args[0].message).toBe('No se pudo eliminar el documento');
  });

  it('eliminar confirmado retira solo el documento correspondiente', async () => {
    const eliminable = { ...documento, puedeEliminar: true };
    const otro = { ...documento, id: '18' }; page.seleccionar(eliminable); page.campos().documentos.set([eliminable, otro]);
    api.delete.and.returnValue(of(undefined)); await page.campos().eliminar(eliminable); await confirmar();
    expect(api.delete).toHaveBeenCalledWith('/api/documentos/17'); expect(page.estado().documentos).toEqual([otro]);
    expect(page.seleccionado()).toBeNull();
  });

  it('la vista previa libera su URL al cerrar y puede recuperarse de un fallo temporal', async () => {
    page.seleccionar({ ...documento, archivoRuta: '/api/documentos/17/descargar' });
    api.getBlob.and.returnValue(throwError(() => ({ status: 503 }))); page.campos().preview(); await Promise.resolve();
    expect(page.campos().previewVisible()).toBeFalse(); expect(page.campos().previewUrl()).toBeNull();
    expect(alerts.calls.mostRecent().args[0].message).toContain('No se pudo cargar la vista previa');
    api.getBlob.and.returnValue(of(new Blob(['contenido']))); spyOn(URL, 'createObjectURL').and.returnValue('blob:prueba');
    const revoke = spyOn(URL, 'revokeObjectURL'); page.campos().preview(); expect(page.campos().previewVisible()).toBeTrue();
    expect(api.getBlob).toHaveBeenCalledWith('/api/documentos/17/vista-previa');
    page.campos().cerrarDetalle(); expect(revoke).toHaveBeenCalledWith('blob:prueba'); expect(page.campos().previewUrl()).toBeNull();
  });

  it('usa las URLs del backend y mantiene los adjuntos de tareas en solo lectura', async () => {
    const tarea: DocumentoItem = {
      ...documento,
      id: 'tarea-8-adjunto-3',
      origen: 'TAREA',
      descargaUrl: '/api/tareas/8/adjuntos/3/descargar',
      vistaPreviaUrl: '/api/tareas/8/adjuntos/3/vista-previa',
      puedeDescargar: true,
      puedeEditar: true,
      puedeAdministrarAcceso: true,
      puedeEliminar: true,
    };
    page.seleccionar(tarea);
    api.getBlob.and.returnValue(of(new Blob(['contenido'])));
    spyOn(URL, 'createObjectURL').and.returnValue('blob:tarea');
    const revoke = spyOn(URL, 'revokeObjectURL');

    page.campos().preview();
    expect(api.getBlob).toHaveBeenCalledWith('/api/tareas/8/adjuntos/3/vista-previa');
    await page.campos().acciones(tarea);
    const config = actionSheets.calls.mostRecent().args[0] as NonNullable<Parameters<ActionSheetController['create']>[0]>;
    const labels = config.buttons?.map(button => typeof button === 'string' ? button : button.text);
    expect(labels).toEqual(['Ver detalle', 'Descargar', 'Cancelar']);

    const descargar = config.buttons?.find(button => typeof button !== 'string' && button.text === 'Descargar');
    if (!descargar || typeof descargar === 'string' || !descargar.handler) throw new Error('Acción Descargar ausente');
    const click = spyOn(HTMLAnchorElement.prototype, 'click');
    descargar.handler();
    expect(api.getBlob).toHaveBeenCalledWith('/api/tareas/8/adjuntos/3/descargar');
    expect(click).toHaveBeenCalled();
    expect(revoke).toHaveBeenCalledWith('blob:tarea');

    api.get.calls.reset(); alerts.calls.reset();
    page.compartir(tarea);
    await page.campos().eliminar(tarea);
    expect(api.get).not.toHaveBeenCalled();
    expect(alerts).not.toHaveBeenCalled();
    await page.campos().abrirHistorial();
    await page.campos().abrirSolicitud();
    expect(api.get).not.toHaveBeenCalled();
    expect(api.patch).not.toHaveBeenCalled();
    expect(page.campos().historialOpen()).toBeFalse();
    expect(alerts.calls.mostRecent().args[0].message).toContain('tarea de origen');
  });

  it('compartir requiere un destinatario y conserva la selección si la API rechaza el acceso', async () => {
    page.seleccionar(documento); await page.campos().guardarAcceso(); expect(api.post).not.toHaveBeenCalled();
    page.campos().usuarioCompartir.set(4); page.campos().permisoCompartir.set('EDICION'); page.campos().expira.set('2026-12-01');
    api.post.and.returnValue(throwError(() => ({ status: 403 }))); await page.campos().guardarAcceso();
    expect(page.campos().usuarioCompartir()).toBe(4); expect(page.seleccionado()).toEqual(documento);
    expect(api.post).toHaveBeenCalledWith('/api/documentos/17/compartidos', { usuarioId: 4, permiso: 'EDICION', fechaExpiracion: '2026-12-01' });
    expect(alerts.calls.mostRecent().args[0].header).toBe('Acceso no concedido');
  });

  it('compartir confirmado actualiza el documento y recarga los accesos vigentes', async () => {
    page.seleccionar(documento); page.campos().usuarioCompartir.set(4); const actualizado = { ...documento, compartido: true };
    api.post.and.returnValue(of(actualizado)); await page.campos().guardarAcceso();
    expect(page.seleccionado()).toEqual(actualizado); expect(page.estado().documentos).toEqual([actualizado]);
    expect(page.campos().usuarioCompartir()).toBeNull(); expect(api.get).toHaveBeenCalledWith('/api/documentos/17/compartidos');
  });

  it('retirar un acceso conserva sus datos si falla y refresca el documento al confirmar', async () => {
    const acceso: DocumentoCompartido = { usuarioId: 4, nombre: 'Persona de prueba', correo: 'persona@example.test', permiso: 'LECTURA' };
    page.seleccionar(documento); page.campos().accesos.set([acceso]); api.delete.and.returnValue(throwError(() => ({ status: 403 })));
    await page.campos().retirarAcceso(acceso); expect(page.campos().accesos()).toEqual([acceso]);
    expect(alerts.calls.mostRecent().args[0].header).toBe('Acceso no retirado');
    api.delete.and.returnValue(of(undefined)); api.get.and.returnValue(of([documento])); await page.campos().retirarAcceso(acceso);
    expect(api.delete).toHaveBeenCalledWith('/api/documentos/17/compartidos/4'); expect(page.campos().accesos()).toEqual([]);
    expect(page.seleccionado()).toEqual(documento);
  });

  it('solicitar aprobación exige aprobador y solo permite resolver a la persona seleccionada', async () => {
    const doc = await prepararSolicitud();
    const pendiente: DocumentoItem = { ...doc, estado: 'en_revision', solicitudAprobacion: {
      id: 3, estado: 'PENDIENTE', solicitanteId: 2, solicitante: 'Solicitante', aprobadorId: 1,
      aprobador: 'Usuario de prueba', fecha: '2026-10-05', comentario: '', versionId: 11, version: '1.0',
    } };
    page.campos().solicitar(); expect(api.patch).not.toHaveBeenCalled();
    page.campos().aprobador.set(1); page.campos().comentario.set('Revisar contenido');
    api.patch.and.returnValue(of(pendiente)); page.campos().solicitar();
    expect(api.patch).toHaveBeenCalledWith('/api/documentos/17/solicitar-aprobacion', { aprobadorId: 1, comentario: 'Revisar contenido', versionId: 11 });
    expect(page.campos().solicitudOpen()).toBeFalse(); expect(page.campos().puedeResolver()).toBeTrue();
    usuario.set({ id: 4, nombre: 'Otra persona' }); expect(page.campos().puedeResolver()).toBeFalse();
  });

  it('un fallo de aprobación conserva la solicitud abierta para reintentar', async () => {
    const doc = await prepararSolicitud(); page.campos().aprobador.set(4);
    api.patch.and.returnValue(throwError(() => ({ error: { error: 'Aprobador no disponible' } }))); page.campos().solicitar();
    expect(page.campos().solicitudOpen()).toBeTrue(); expect(page.campos().aprobador()).toBe(4);
    expect(page.seleccionado()).toEqual(doc); expect(alerts.calls.mostRecent().args[0].message).toBe('Aprobador no disponible');
  });

  it('no ofrece eliminar carpetas sin permiso', async () => {
    const carpeta: CarpetaItem = { id: '9', nombre: 'Carpeta', tipo: 'COMPARTIDA', puedeEliminar: true, count: 0, documentos: [] };
    await page.campos().eliminarCarpeta({ ...carpeta, puedeEliminar: false }, new Event('click'));
    expect(alerts).not.toHaveBeenCalled(); expect(api.delete).not.toHaveBeenCalled();
  });

  it('permite al propietario confirmar la eliminación de una carpeta compartida', async () => {
    const carpeta: CarpetaItem = { id: '9', nombre: 'Equipo', tipo: 'COMPARTIDA', puedeEliminar: true, count: 2, documentos: [] };
    api.delete.and.returnValue(of(undefined));
    await page.campos().eliminarCarpeta(carpeta, new Event('click'));
    expect(alerts.calls.first().args[0].message).toContain('2 documento(s)');
    expect(alerts.calls.first().args[0].message).toContain('personas con acceso');
    await confirmar();
    expect(api.delete).toHaveBeenCalledWith('/api/documentos/carpetas/9');
  });

  it('distingue un rechazo de permisos al eliminar carpeta y conserva los documentos', async () => {
    page.seleccionar(documento);
    const carpeta: CarpetaItem = { id: '9', nombre: 'Carpeta', tipo: 'PERSONAL', puedeEliminar: true, count: 1, documentos: [documento] };
    api.delete.and.returnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
    await page.campos().eliminarCarpeta(carpeta, new Event('click')); await confirmar();
    expect(page.estado().documentos).toEqual([documento]);
    expect(alerts.calls.mostRecent().args[0].message).toBe('Solo puedes eliminar tus propias carpetas.');
  });

  it('las acciones disponibles respetan los permisos y el aprobador del documento', async () => {
    await page.campos().acciones(documento);
    type Options = NonNullable<Parameters<ActionSheetController['create']>[0]>;
    const first = actionSheets.calls.first().args[0] as Options;
    expect(first.buttons?.map(button => typeof button === 'string' ? button : button.text)).toEqual(['Ver detalle', 'Cancelar']);
    admin = true;
    await page.campos().acciones({ ...documento, versionActualId: 11, archivoRuta: 'prueba.pdf', puedeDescargar: true, puedeEditar: true,
      puedeAdministrarAcceso: true, puedeEliminar: true,
      solicitudAprobacion: { id: 3, estado: 'PENDIENTE', solicitanteId: 2, solicitante: 'Solicitante',
        aprobadorId: 1, aprobador: 'Usuario de prueba', fecha: '2026-10-05', comentario: '', versionId: 11, version: '1.0' } });
    const authorized = actionSheets.calls.mostRecent().args[0] as Options;
    expect(authorized.buttons?.map(button => typeof button === 'string' ? button : button.text))
      .toEqual(['Ver detalle', 'Descargar', 'Compartir', 'Editar', 'Aprobar', 'Rechazar', 'Eliminar', 'Cancelar']);
    usuario.set({ id: 4, nombre: 'Otra cuenta' }); await page.campos().acciones(documento);
    const other = actionSheets.calls.mostRecent().args[0] as Options;
    expect(other.buttons?.map(button => typeof button === 'string' ? button : button.text)).not.toContain('Aprobar');
  });

  it('las acciones de detalle, edición y descarga operan sobre el documento elegido', async () => {
    const doc = { ...documento, archivoRuta: 'prueba.pdf', puedeEditar: true, puedeDescargar: true, puedeAdministrarAcceso: true };
    await page.campos().acciones(doc);
    const config = actionSheets.calls.mostRecent().args[0] as NonNullable<Parameters<ActionSheetController['create']>[0]>;
    const ejecutar = (text: string) => {
      const button = config.buttons?.find(item => typeof item !== 'string' && item.text === text);
      if (!button || typeof button === 'string' || !button.handler) throw new Error('Acción ausente: ' + text);
      button.handler();
    };
    ejecutar('Ver detalle'); expect(page.seleccionado()).toEqual(doc);
    ejecutar('Editar'); expect(page.estado().id).toBe('17'); expect(page.estado().abierto).toBeTrue();
    ejecutar('Compartir'); expect(api.get).toHaveBeenCalledWith('/api/documentos/17/compartidos');
    api.getBlob.and.returnValue(of(new Blob(['contenido'])));
    const click = spyOn(HTMLAnchorElement.prototype, 'click'); spyOn(URL, 'createObjectURL').and.returnValue('blob:descarga');
    const revoke = spyOn(URL, 'revokeObjectURL'); ejecutar('Descargar');
    expect(api.getBlob).toHaveBeenCalledWith('/api/documentos/17/descargar'); expect(click).toHaveBeenCalledTimes(1);
    expect(revoke).toHaveBeenCalledWith('blob:descarga');
  });

  it('no captura clicks simples y conserva el desplazamiento horizontal', () => {
    const scroller = document.createElement('div');
    const row = document.createElement('div');
    const button = document.createElement('button');
    row.append(button); scroller.append(row);
    const capture = jasmine.createSpy('setPointerCapture');
    Object.defineProperty(scroller, 'setPointerCapture', { value: capture });
    Object.defineProperty(scroller, 'scrollLeft', { value: 0, writable: true });
    const pointer = (target: Element, x: number, y: number) => ({
      isPrimary: true, pointerType: 'mouse', button: 0, pointerId: 7,
      clientX: x, clientY: y, target, cancelable: true, preventDefault: jasmine.createSpy('preventDefault'),
    } as unknown as PointerEvent);

    page.campos().iniciarArrastre(pointer(button, 100, 20), scroller);
    page.campos().moverArrastre(pointer(button, 97, 20));
    expect(capture).not.toHaveBeenCalled();

    page.campos().iniciarArrastre(pointer(row, 100, 20), scroller);
    expect(capture).not.toHaveBeenCalled();
    page.campos().moverArrastre(pointer(row, 70, 20));
    expect(capture).toHaveBeenCalledOnceWith(7);
    expect(scroller.scrollLeft).toBe(30);
  });

  it('separa documentos propios, compartidos, de tareas y de unidades sin duplicar unidades ajenas', () => {
    const compartido = { ...documento, id: '18', origen: 'COMPARTIDO' as const, propietarioId: 2 };
    const tarea = { ...documento, id: '19', origen: 'TAREA' as const, propietarioId: 1 };
    const unidad = { ...documento, id: '20', origen: 'UNIDAD' as const, propietarioId: 2 };
    const legadoPropio = { ...documento, id: '21', origen: undefined, propietarioId: 1 };
    page.campos().documentos.set([documento, compartido, tarea, unidad, legadoPropio]);

    page.campos().pestana('mis_documentos');
    expect(page.campos().filtrados().map(doc => doc.id)).toEqual(['17', '21']);
    page.campos().pestana('compartidos');
    expect(page.campos().filtrados().map(doc => doc.id)).toEqual(['18']);
    page.campos().pestana('tareas');
    expect(page.campos().filtrados().map(doc => doc.id)).toEqual(['19']);
    page.campos().pestana('unidades');
    expect(page.campos().filtrados().map(doc => doc.id)).toEqual(['20']);
  });

  it('combina búsqueda, categoría, unidad y estado y restablece el listado', () => {
    page.campos().documentos.set([documento,
      { ...documento, id: '18', nombre: 'Informe distinto', categoria: 'Informe', area: 'Otra unidad', estado: 'aprobado' },
      { ...documento, id: '19', nombre: 'Otro documento' }]);
    page.campos().pestana('todas'); page.campos().filtrar('search', ' PRUEBA ');
    page.campos().filtrar('tipo', 'Acta'); page.campos().filtrar('area', 'Personal');
    page.campos().filtrar('estado', 'Borrador');
    expect(page.campos().filtrados().map(doc => doc.id)).toEqual(['17']);
    expect(page.campos().paginados().map(doc => doc.id)).toEqual(['17']);
    page.campos().ir(4); expect(page.campos().pagina()).toBe(1);
    page.campos().limpiar(); expect(page.campos().filtrados().map(doc => doc.id)).toEqual(['17', '18', '19']);
  });

  it('aplica Hoy, Esta semana, Este mes y Este trimestre con límites reales de calendario', () => {
    jasmine.clock().install();
    try {
      jasmine.clock().mockDate(new Date(2026, 9, 27, 9, 30));
      page.campos().documentos.set([
        { ...documento, id: 'hoy', fecha: '2026-10-27' },
        { ...documento, id: 'semana', fecha: '2026-10-26' },
        { ...documento, id: 'mes', fecha: '2026-10-10' },
        { ...documento, id: 'trimestre', fecha: '2026-11-15' },
        { ...documento, id: 'anterior', fecha: '2026-09-30' },
      ]);
      page.campos().filtrar('fecha', 'Hoy');
      expect(page.campos().filtrados().map(doc => doc.id)).toEqual(['hoy']);
      page.campos().filtrar('fecha', 'Esta semana');
      expect(page.campos().filtrados().map(doc => doc.id)).toEqual(['hoy', 'semana']);
      page.campos().filtrar('fecha', 'Este mes');
      expect(page.campos().filtrados().map(doc => doc.id)).toEqual(['hoy', 'semana', 'mes']);
      page.campos().filtrar('fecha', 'Este trimestre');
      expect(page.campos().filtrados().map(doc => doc.id)).toEqual(['hoy', 'semana', 'mes', 'trimestre']);
    } finally {
      jasmine.clock().uninstall();
    }
  });

  it('crear una carpeta requiere nombre y conserva los datos si la API falla', async () => {
    page.campos().abrirCarpeta(); page.campos().nombreCarpeta.set(' '); await page.campos().crearCarpeta();
    expect(api.post).not.toHaveBeenCalled();
    page.campos().nombreCarpeta.set(' Carpeta de prueba '); page.campos().tipoCarpeta.set('COMPARTIDA'); page.campos().miembros.set([4]);
    api.post.and.returnValue(throwError(() => ({ status: 503 }))); await page.campos().crearCarpeta();
    expect(api.post).toHaveBeenCalledWith('/api/documentos/carpetas', { nombre: 'Carpeta de prueba', tipo: 'COMPARTIDA', miembroIds: [4] });
    expect(page.campos().carpetaOpen()).toBeTrue(); expect(page.campos().miembros()).toEqual([4]);
    api.post.and.returnValue(of({ id: '9' })); await page.campos().crearCarpeta();
    expect(page.campos().carpetaOpen()).toBeFalse(); expect(api.get).toHaveBeenCalledWith('/api/documentos/carpetas?incluirDocumentos=false');
  });

  it('eliminar una carpeta personal limpia solo sus documentos y la selección correspondiente', async () => {
    const doc = { ...documento, carpetaId: '9' }; const otro = { ...documento, id: '18', carpetaId: '10' };
    const carpeta: CarpetaItem = { id: '9', nombre: 'Carpeta personal', tipo: 'PERSONAL', puedeEliminar: true, count: 1, documentos: [doc] };
    const otra: CarpetaItem = { ...carpeta, id: '10', nombre: 'Otra carpeta', documentos: [otro] };
    page.seleccionar(doc); page.campos().documentos.set([doc, otro]); page.campos().carpetas.set([carpeta, otra]);
    page.campos().carpetaActiva.set(carpeta.nombre); api.delete.and.returnValue(of(undefined));
    await page.campos().eliminarCarpeta(carpeta, new Event('click')); await confirmar();
    expect(api.delete).toHaveBeenCalledWith('/api/documentos/carpetas/9');
    expect(page.estado().documentos).toEqual([otro]); expect(page.campos().carpetas()).toEqual([otra]);
    expect(page.campos().carpetaActiva()).toBeNull(); expect(page.seleccionado()).toBeNull();
  });
});
