import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { AlertController } from '@ionic/angular/standalone';
import { Subject, of, throwError } from 'rxjs';
import { NotasPage } from './notas.page';
import { NotaDetallePage } from './nota-detalle.page';
import { Nota, NotaListadoRespuesta, NotaNumero, NotaRespuesta } from './notas.models';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { DashboardService } from '../../services/dashboard.service';

class NotasTestPage extends NotasPage {
  access() {
    return {
      numero: this.formNumeroId, seguimiento: this.formSeguimientoId, asunto: this.formAsunto,
      saving: this.saving, modal: this.showNotaModal,
      guardar: () => this.guardarNota(), reservar: (id: string) => this.onNumeroSeleccionado(id),
      filtros: { texto: this.filtroTexto,
        estado: this.filtroEstado, anio: this.filtroAnio, desde: this.filtroFechaDesde,
        hasta: this.filtroFechaHasta },
      filtrar: () => this.aplicarFiltros(), limpiar: () => this.limpiarFiltros(),
      cambiarPagina: (indice: number) => this.cambiarPagina(indice), total: this.totalNotas, resumen: this.resumen,
      notas: this.notas, numeros: this.numeros, seleccionables: this.numerosSeleccionables, editando: this.editandoId,
      destinatario: this.destinatarioForm, destinatarioNombre: this.formDestinatarioNombre,
      destinatarioCargo: this.formDestinatarioCargo,
    };
  }
}

class NotaDetalleTestPage extends NotaDetallePage {
  access() {
    return {
      nota: this.nota, adjuntar: (event: Event) => this.adjuntarPdf(event),
      oficializar: (nota: Nota) => this.oficializar(nota), confirmarOficializacion: () => this.confirmarOficializacion(),
      pendienteOficializar: this.notaPendienteOficializar, oficializando: this.oficializando,
      relacionar: () => this.crearRelacion({ id: '9', numero: 'N-9', asunto: 'Prueba',
        estado: 'BORRADOR', sensitiva: false, creadoPor: null, seguimientoUsuario: null }),
    };
  }
}

describe('NotasPage: guardado y reservas', () => {
  let page: ReturnType<NotasTestPage['access']>;
  let api: jasmine.SpyObj<ApiService>;
  let alerts: jasmine.Spy;
  let request: Subject<NotaRespuesta>;
  let permission: jasmine.Spy;

  beforeEach(() => {
    request = new Subject<NotaRespuesta>();
    api = jasmine.createSpyObj('ApiService', ['get', 'post', 'put', 'delete']);
    api.get.and.returnValue(of([]));
    api.post.and.returnValue(request);
    api.delete.and.returnValue(of(undefined));
    alerts = jasmine.createSpy('create').and.resolveTo({ present: () => Promise.resolve() });
    permission = jasmine.createSpy('hasPermission').and.returnValue(false);
    TestBed.configureTestingModule({ providers: [
      { provide: ApiService, useValue: api },
      { provide: AuthService, useValue: { hasPermission: permission } },
      { provide: DashboardService, useValue: { getNotificaciones: () => of([]) } },
      { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ id: '4' })) } },
      { provide: Router, useValue: {} },
      { provide: AlertController, useValue: { create: alerts } },
    ] });
    page = TestBed.runInInjectionContext(() => new NotasTestPage().access());
    page.modal.set(true);
  });

  it('requires a reserved number and a responsible user before creating', () => {
    page.guardar();
    page.numero.set('1');
    page.guardar();
    expect(api.post).not.toHaveBeenCalled();
    expect(page.saving()).toBeFalse();
  });

  it('seleccionar institución y área no cambia el usuario interno de seguimiento y guarda los IDs elegidos', async () => {
    permission.and.returnValue(true);
    api.get.and.returnValue(of([{ id: 11, nombre: 'Área de prueba', director: 'Persona externa', directorCargo: 'Jefatura', activo: true }]));
    page.numero.set('1'); page.seguimiento.set('2');
    page.destinatario.cambiarInstitucion('3'); page.destinatario.cambiarDepartamento('11');
    expect(page.seguimiento()).toBe('2'); expect(page.destinatarioNombre()).toBe('Persona externa');
    page.guardar();
    const datos = api.post.calls.mostRecent().args[1] as FormData;
    expect(datos.has('pdf')).toBeFalse();
    const payload = JSON.parse(await (datos.get('datos') as Blob).text());
    expect(payload.institucionId).toBe(3); expect(payload.departamentoId).toBe(11);
    expect(payload.destinatarioCargo).toBe('Jefatura'); expect(payload.seguimientoUsuarioId).toBe(2);
  });

  it('blocks duplicate submissions while the request is pending', () => {
    page.numero.set('1');
    page.seguimiento.set('2');
    page.guardar();
    page.guardar();
    expect(api.post).toHaveBeenCalledTimes(1);
    expect(page.saving()).toBeTrue();
  });

  it('preserves the draft form on a server failure without sending attachments', () => {
    page.numero.set('1');
    page.seguimiento.set('2');
    page.asunto.set('Prueba');
    api.post.and.returnValue(throwError(() => ({ status: 500, error: { message: 'Intenta nuevamente' } })));
    page.guardar();
    expect(page.saving()).toBeFalse();
    expect(page.modal()).toBeTrue();
    expect((api.post.calls.mostRecent().args[1] as FormData).has('pdf')).toBeFalse();
    expect(page.asunto()).toBe('Prueba');
    expect(page.numero()).toBe('1');
    expect(alerts.calls.mostRecent().args[0].message).toBe('Intenta nuevamente');
  });

  it('clears a conflicting reservation and reloads available numbers', () => {
    page.numero.set('1');
    page.seguimiento.set('2');
    api.post.and.returnValue(throwError(() => ({ status: 409, error: { error: 'Reserva vencida' } })));
    page.guardar();
    expect(page.numero()).toBe('');
    expect(page.modal()).toBeTrue();
    expect(api.get).toHaveBeenCalledWith('/api/notas/numeros');
    expect(alerts.calls.mostRecent().args[0].message).toBe('Reserva vencida');
  });

  it('does not retain a number reserved concurrently by another user', () => {
    api.post.and.returnValue(throwError(() => ({ status: 409, error: { error: 'Número ocupado' } })));
    page.reservar('3');
    expect(page.numero()).toBe('');
    expect(api.get).toHaveBeenCalledWith('/api/notas/numeros');
    expect(alerts.calls.mostRecent().args[0].message).toBe('Número ocupado');
  });

  it('normalizes the detail response and tolerates optional empty collections', () => {
    const response: NotaRespuesta = { id: 4, numeroId: 1,
      creadoPor: { id: 2, nombre: 'Usuario de prueba' },
      seguimientoUsuario: { id: 3, nombre: 'Responsable de prueba' } };
    api.get.and.returnValues(of(response), of({}));
    const detail = TestBed.runInInjectionContext(() => new NotaDetalleTestPage());
    detail.ngOnInit();
    const nota = detail.access().nota();
    expect(nota?.id).toBe('4');
    expect(nota?.creadoPor.id).toBe('2');
    expect(nota?.seguimientoUsuario.id).toBe('3');
    expect(nota?.historial).toEqual([]);
    expect(nota?.pdfs).toEqual([]);
    expect(nota?.relaciones).toEqual([]);
  });

  it('does not send relation requests without permission', () => {
    const detail = TestBed.runInInjectionContext(() => new NotaDetalleTestPage().access());
    detail.relacionar();
    expect(permission).toHaveBeenCalledWith('Notas', 'Relacionar');
    expect(api.post).not.toHaveBeenCalled();
  });

  it('oficializa un borrador desde la pantalla de detalle', () => {
    permission.and.callFake((_modulo: string, accion: string) => accion === 'Firmar');
    const detail = TestBed.runInInjectionContext(() => new NotaDetalleTestPage().access());
    const borrador = { id: '4', numero: 'OAT-004-2026', estado: 'BORRADOR' } as Nota;
    detail.nota.set(borrador);
    detail.oficializar(borrador);
    expect(detail.pendienteOficializar()).toBe(borrador);
    api.post.and.returnValue(of({ id: 4, numeroId: 1, estado: 'EN_SEGUIMIENTO',
      creadoPor: { id: 2, nombre: 'Usuario de prueba' }, seguimientoUsuario: { id: 3, nombre: 'Responsable' } }));
    detail.confirmarOficializacion();
    expect(api.post).toHaveBeenCalledWith('/api/notas/4/oficializar', {});
    expect(detail.oficializando()).toBeFalse();
    expect(detail.pendienteOficializar()).toBeNull();
    expect(detail.nota()?.estado).toBe('EN_SEGUIMIENTO');
  });

  it('rejects a non-PDF attachment before making an API request', () => {
    permission.and.returnValue(true);
    const response: NotaRespuesta = { id: 4, numeroId: 1, estado: 'FIRMADA', puedeAdjuntarPdf: true,
      creadoPor: { id: 2, nombre: 'Usuario de prueba' }, seguimientoUsuario: { id: 3, nombre: 'Responsable de prueba' } };
    api.get.and.callFake(<T>(path: string) => of((path === '/api/notas/4' ? response : []) as T));
    const component = TestBed.runInInjectionContext(() => new NotaDetalleTestPage()); component.ngOnInit();
    const detail = component.access();
    const transfer = new DataTransfer();
    transfer.items.add(new File(['prueba'], 'texto.txt'));
    const input = document.createElement('input');
    input.type = 'file';
    input.files = transfer.files;
    const event = new Event('change');
    Object.defineProperty(event, 'target', { value: input });
    detail.adjuntar(event);
    expect(api.post).not.toHaveBeenCalled();
    expect(alerts.calls.mostRecent().args[0].header).toBe('PDF no válido');
  });

  it('sends only the remaining filters without fetching users or the complete inventory', () => {
    api.get.and.returnValue(of({ content: [], page: 0, size: 25, totalElements: 0, totalPages: 0, resumen: {} }));
    page.filtros.texto.set('Coordinación');
    page.filtros.estado.set('EN_SEGUIMIENTO'); page.filtros.anio.set('2026');
    page.filtros.desde.set('2026-01-01'); page.filtros.hasta.set('2026-10-06');
    page.filtrar();
    const url = new URL(api.get.calls.mostRecent().args[0], 'http://localhost');
    expect(url.pathname).toBe('/api/notas/listado');
    const parametros: Record<string, string> = {};
    url.searchParams.forEach((valor, clave) => parametros[clave] = valor);
    expect(parametros).toEqual({ page: '0', size: '25',
      query: 'Coordinación', estado: 'EN_SEGUIMIENTO', anio: '2026',
      fechaDesde: '2026-01-01', fechaHasta: '2026-10-06' });
    expect(api.get).toHaveBeenCalledTimes(1);
  });

  it('keeps aggregate counts correct across pages and preserves the applied filter until submitting again', () => {
    api.get.and.returnValue(of({ content: [], page: 0, size: 25, totalElements: 60, totalPages: 3,
      resumen: { BORRADOR: 40, EN_SEGUIMIENTO: 20 } }));
    page.filtros.estado.set('EN_SEGUIMIENTO'); page.filtrar();
    expect(page.total()).toBe(60); expect(page.resumen().BORRADOR).toBe(40);
    page.filtros.estado.set('BORRADOR'); page.cambiarPagina(1);
    const url = new URL(api.get.calls.mostRecent().args[0], 'http://localhost');
    expect(url.searchParams.get('page')).toBe('1');
    expect(url.searchParams.get('estado')).toBe('EN_SEGUIMIENTO');
  });

  it('clears the remaining filters and reloads the first page without filter parameters', () => {
    api.get.and.returnValue(of({ content: [], page: 0, size: 25, totalElements: 60, totalPages: 3, resumen: {} }));
    page.filtros.texto.set('Coordinación'); page.filtros.estado.set('EN_SEGUIMIENTO');
    page.filtros.anio.set('2026'); page.filtros.desde.set('2026-01-01'); page.filtros.hasta.set('2026-10-06');
    page.filtrar(); page.cambiarPagina(1);
    api.get.calls.reset();

    page.limpiar();

    expect(page.filtros.texto()).toBe(''); expect(page.filtros.estado()).toBe('TODAS');
    expect(page.filtros.anio()).toBe(''); expect(page.filtros.desde()).toBe(''); expect(page.filtros.hasta()).toBe('');
    expect(api.get).toHaveBeenCalledOnceWith('/api/notas/listado?page=0&size=25');
  });

  it('cancels previous filtering requests so a late response cannot replace the current list', () => {
    const oldRequest = new Subject<NotaListadoRespuesta>(); const currentRequest = new Subject<NotaListadoRespuesta>();
    api.get.and.returnValues(oldRequest, currentRequest);
    page.filtros.texto.set('anterior'); page.filtrar();
    page.filtros.texto.set('actual'); page.filtrar();
    expect(oldRequest.observed).toBeFalse();
    currentRequest.next({ content: [], page: 0, size: 25, totalElements: 3, totalPages: 1, resumen: { BORRADOR: 3 } });
    oldRequest.next({ content: [], page: 0, size: 25, totalElements: 20, totalPages: 1, resumen: { BORRADOR: 20 } });
    expect(page.total()).toBe(3);
  });

  it('rejects invalid years and inverted dates without issuing requests', () => {
    page.filtros.anio.set('0'); page.filtrar();
    expect(api.get).not.toHaveBeenCalled();
    page.filtros.anio.set('2026'); page.filtros.desde.set('2026-10-06'); page.filtros.hasta.set('2026-01-01');
    page.filtrar(); expect(api.get).not.toHaveBeenCalled();
  });

  it('offers only the first available consecutive while allowing an existing own reservation', () => {
    const numero = (id: string, estado: NotaNumero['estado'], propia = false): NotaNumero => ({
      id, numero: `OAT-${id.padStart(3, '0')}-2026`, estado, notaId: null, tomadoPor: null, tomadoAt: null,
      reservadoPor: null, reservadoDesde: null, reservadoHasta: null, reservaPropia: propia,
    });
    page.numeros.set([numero('1', 'OCUPADO'), numero('2', 'DISPONIBLE'), numero('3', 'DISPONIBLE'), numero('4', 'RESERVADO', true)]);
    expect(page.seleccionables().map(item => item.id)).toEqual(['4', '2']);
    page.numero.set('4');
    expect(page.seleccionables().map(item => item.id)).toEqual(['4', '2']);
    page.numeros.set([numero('1', 'OCUPADO'), numero('2', 'RESERVADO'), numero('3', 'DISPONIBLE')]);
    expect(page.seleccionables().map(item => item.id)).toEqual(['3']);
  });

  it('does not offer a later number when releasing the own reservation would make it first again', () => {
    const numero = (id: string, estado: NotaNumero['estado'], propia = false): NotaNumero => ({
      id, numero: `OAT-${id.padStart(3, '0')}-2026`, estado, notaId: null, tomadoPor: null, tomadoAt: null,
      reservadoPor: null, reservadoDesde: null, reservadoHasta: null, reservaPropia: propia,
    });
    page.numero.set('1');
    page.numeros.set([numero('1', 'RESERVADO', true), numero('2', 'DISPONIBLE')]);
    expect(page.seleccionables().map(item => item.id)).toEqual(['1']);

    page.numero.set('2');
    page.numeros.set([numero('1', 'DISPONIBLE'), numero('2', 'RESERVADO', true)]);
    expect(page.seleccionables().map(item => item.id)).toEqual(['2', '1']);

    page.numero.set('1');
    page.numeros.set([numero('1', 'RESERVADO', true), numero('2', 'DISPONIBLE'),
      numero('3', 'OCUPADO'), numero('4', 'DISPONIBLE')]);
    expect(page.seleccionables().map(item => item.id)).toEqual(['1', '4']);
    expect(api.delete).not.toHaveBeenCalled();
    expect(api.post).not.toHaveBeenCalled();
  });
});
