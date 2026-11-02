import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, ParamMap, Router, convertToParamMap } from '@angular/router';
import { AlertController } from '@ionic/angular/standalone';
import { BehaviorSubject, Subject, of, throwError } from 'rxjs';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { DashboardService } from '../../services/dashboard.service';
import { NotaDetallePage } from './nota-detalle.page';
import { NotaEstado, NotaRespuesta } from './notas.models';

class SeguimientoTestPage extends NotaDetallePage {
  access() {
    return { nota: this.nota, estado: this.seguimientoEstado, comentario: this.seguimientoComentario,
      guardando: this.guardandoSeguimiento, error: this.errorSeguimiento,
      registrar: () => this.registrarSeguimiento(), destinatario: this.destinatarioForm,
      responsableEdicion: this.editSeguimientoId, editar: () => this.editar(), guardar: () => this.guardarEdicion(),
      permitePdf: () => { const nota = this.nota(); return !!nota && this.puedeAdjuntarPdf(nota); },
      adjuntarPdf: (event: Event) => this.adjuntarPdf(event) };
  }
}

describe('NotaDetallePage: seguimiento en la trazabilidad', () => {
  let api: jasmine.SpyObj<ApiService>;
  let page: ReturnType<SeguimientoTestPage['access']>;
  let respuesta: NotaRespuesta;
  let request: Subject<NotaRespuesta>;
  let ruta: BehaviorSubject<ParamMap>;

  beforeEach(() => {
    respuesta = { id: 10, numeroId: 1, numero: 'OAT-001-2026', estado: 'FIRMADA',
      creadoPor: { id: 2, nombre: 'Elaborador' }, seguimientoUsuario: { id: 3, nombre: 'Responsable' },
      historial: [{ id: 4, usuario: { id: 2, nombre: 'Elaborador' }, accion: 'FIRMAR',
        accionTexto: 'Firma de la directora o director', estadoAnterior: 'EN_SEGUIMIENTO', estadoNuevo: 'FIRMADA',
        detalle: 'Firma de dirección registrada', createdAt: '2026-10-06T12:00:00' }],
      seguimiento: { estado: 'PENDIENTE_ENTREGA', estadoTexto: 'Pendiente de entrega',
        usuario: { id: 2, nombre: 'Elaborador' }, createdAt: '2026-10-06T12:00:00', puedeComentar: true,
        opciones: [{ estado: 'RECIBIDA', texto: 'Recibida' }] } };
    request = new Subject<NotaRespuesta>();
    ruta = new BehaviorSubject(convertToParamMap({ id: '10' }));
    api = jasmine.createSpyObj('ApiService', ['get', 'post', 'put']);
    api.get.and.callFake(<T>(path: string) => of((path === '/api/notas/10' ? respuesta : {}) as T));
    api.post.and.returnValue(request);
    TestBed.configureTestingModule({ providers: [
      { provide: ApiService, useValue: api },
      { provide: AuthService, useValue: { hasPermission: () => false } },
      { provide: DashboardService, useValue: { getNotificaciones: () => of([]) } },
      { provide: ActivatedRoute, useValue: { paramMap: ruta } },
      { provide: Router, useValue: {} },
      { provide: AlertController, useValue: { create: jasmine.createSpy('create') } },
    ] });
    const component = TestBed.runInInjectionContext(() => new SeguimientoTestPage());
    component.ngOnInit(); page = component.access();
  });

  it('normalizes the current followup and retains the director signature event', () => {
    expect(page.nota()?.seguimiento?.usuario?.id).toBe('2');
    expect(page.nota()?.seguimiento?.estado).toBe('PENDIENTE_ENTREGA');
    expect(page.nota()?.historial[0].accionTexto).toBe('Firma de la directora o director');
  });

  it('editar el destinatario conserva el responsable interno y envía ambos datos por separado', async () => {
    spyOn(TestBed.inject(AuthService), 'hasPermission').and.returnValue(true);
    api.get.and.returnValue(of([{ id: 12, nombre: 'Área actual', director: 'Persona externa nueva',
      directorCargo: 'Cargo externo nuevo', activo: true }]));
    api.put.and.returnValue(of(respuesta));
    page.nota.update(nota => ({ ...nota!, estado: 'BORRADOR', asunto: 'Nota de prueba', fechaOficial: '2026-10-14',
      institucion: { id: '1', nombre: 'Institución guardada', siglas: 'TEST' },
      departamento: { id: '11', nombre: 'Área guardada' }, destino: 'Institución guardada',
      destinatarioNombre: 'Persona guardada', destinatarioCargo: 'Cargo guardado' }));
    page.editar();
    expect(page.destinatario.nombre()).toBe('Persona guardada');
    expect(page.destinatario.cargo()).toBe('Cargo guardado');
    expect(page.responsableEdicion()).toBe('3');
    page.destinatario.cambiarInstitucion('2');
    expect(page.destinatario.departamentoId()).toBe(''); expect(page.destinatario.nombre()).toBe('');
    expect(page.responsableEdicion()).toBe('3');
    page.destinatario.cambiarDepartamento('12');
    expect(page.destinatario.nombre()).toBe('Persona externa nueva'); expect(page.responsableEdicion()).toBe('3');
    page.guardar();
    expect(api.put).toHaveBeenCalledTimes(1);
    const [path, formData] = api.put.calls.mostRecent().args;
    expect(path).toBe('/api/notas/10');
    const payload = JSON.parse(await ((formData as FormData).get('datos') as Blob).text());
    expect(payload).toEqual(jasmine.objectContaining({ institucionId: 2, departamentoId: 12,
      destinatarioNombre: 'Persona externa nueva', destinatarioCargo: 'Cargo externo nuevo', seguimientoUsuarioId: 3 }));
    expect(api.post).not.toHaveBeenCalled();
  });

  it('sends a comment to the same note without supplying an author or date', () => {
    page.comentario.set('  Coordinación de la entrega  '); page.registrar();
    expect(api.post).toHaveBeenCalledOnceWith('/api/notas/10/seguimientos',
      { estado: null, comentario: 'Coordinación de la entrega' });
  });

  it('blocks duplicate submissions and updates history only after a successful response', () => {
    page.estado.set('RECIBIDA'); page.comentario.set('Recibida en recepción');
    page.registrar(); page.registrar();
    expect(api.post).toHaveBeenCalledTimes(1); expect(page.guardando()).toBeTrue();
    expect(page.nota()?.historial.length).toBe(1);
    request.next({ ...respuesta,
      seguimiento: { ...respuesta.seguimiento!, estado: 'RECIBIDA', estadoTexto: 'Recibida' },
      historial: [...respuesta.historial!, { id: 5, usuario: { id: 3, nombre: 'Responsable' }, accion: 'RECIBIDA',
        accionTexto: 'Recibida', estadoAnterior: 'FIRMADA', estadoNuevo: 'FIRMADA',
        detalle: 'Recibida en recepción', createdAt: '2026-10-06T13:00:00' }] });
    expect(page.nota()?.estado).toBe('FIRMADA'); expect(page.nota()?.historial.length).toBe(2);
    expect(page.nota()?.historial[0].accion).toBe('FIRMAR'); expect(page.nota()?.seguimiento?.estado).toBe('RECIBIDA');
    expect(page.comentario()).toBe(''); expect(page.estado()).toBe(''); expect(page.guardando()).toBeFalse();
  });

  it('preserves the comment and history on a failure so the action can be retried', () => {
    api.post.and.returnValue(throwError(() => ({ status: 500, error: { message: 'Fallo temporal' } })));
    page.comentario.set('Esperando confirmación'); page.registrar();
    expect(page.comentario()).toBe('Esperando confirmación'); expect(page.nota()?.historial.length).toBe(1);
    expect(page.guardando()).toBeFalse(); expect(page.error()).toBe('Fallo temporal');
    api.post.and.returnValue(of(respuesta)); page.registrar();
    expect(api.post).toHaveBeenCalledTimes(2); expect(page.error()).toBe('');
  });

  it('does not apply a pending followup response to a different note after navigation', () => {
    page.comentario.set('Entrega en curso'); page.registrar();
    api.get.and.returnValue(of({ ...respuesta, id: 11 }));
    ruta.next(convertToParamMap({ id: '11' }));
    expect(request.observed).toBeFalse(); expect(page.guardando()).toBeFalse();
    request.next(respuesta);
    expect(page.nota()?.id).toBe('11'); expect(page.comentario()).toBe('');
  });

  it('does not permit a receptor signature unless the backend offers that action', () => {
    page.estado.set('FIRMADA_RECEPTOR'); page.registrar(); expect(api.post).not.toHaveBeenCalled();
    page.nota.update(nota => ({ ...nota!, seguimiento: { ...nota!.seguimiento!, puedeComentar: false,
      opciones: [{ estado: 'FIRMADA_RECEPTOR', texto: 'Firmada por el receptor' }] } }));
    page.registrar();
    expect(api.post).toHaveBeenCalledOnceWith('/api/notas/10/seguimientos', { estado: 'FIRMADA_RECEPTOR', comentario: '' });
  });

  it('does not send followup for drafts, unsigned notes, closed notes or annulled notes', () => {
    for (const estado of ['BORRADOR', 'EN_SEGUIMIENTO', 'CERRADA', 'ANULADA'] as NotaEstado[]) {
      page.nota.update(nota => ({ ...nota!, estado })); page.comentario.set('No debe registrarse'); page.registrar();
    }
    expect(api.post).not.toHaveBeenCalled();
  });

  it('solo habilita el PDF al confirmar el backend la firma del receptor', () => {
    spyOn(TestBed.inject(AuthService), 'hasPermission').and.returnValue(true);
    expect(page.permitePdf()).toBeFalse();
    page.nota.update(nota => ({ ...nota!, seguimiento: { ...nota!.seguimiento!, puedeComentar: false,
      opciones: [{ estado: 'FIRMADA_RECEPTOR', texto: 'Firmada por el receptor' }] } }));
    page.estado.set('FIRMADA_RECEPTOR'); expect(page.permitePdf()).toBeFalse();
    page.registrar(); expect(page.permitePdf()).toBeFalse();
    request.next({ ...respuesta, puedeAdjuntarPdf: true });
    expect(page.permitePdf()).toBeTrue();
    const upload = new Subject<NotaRespuesta>(); api.post.and.returnValue(upload);
    page.adjuntarPdf(eventoPdf());
    const [url, formData] = api.post.calls.mostRecent().args;
    expect(url).toBe('/api/notas/10/pdf');
    expect((formData as FormData).get('pdf')).toEqual(jasmine.any(File));
  });

  it('no envía archivos antes de registrar la firma del receptor ni después de un fallo', () => {
    spyOn(TestBed.inject(AuthService), 'hasPermission').and.returnValue(true);
    page.adjuntarPdf(eventoPdf()); expect(api.post).not.toHaveBeenCalled();
    page.nota.update(nota => ({ ...nota!, seguimiento: { ...nota!.seguimiento!, puedeComentar: false,
      opciones: [{ estado: 'FIRMADA_RECEPTOR', texto: 'Firmada por el receptor' }] } }));
    api.post.and.returnValue(throwError(() => ({ status: 500 })));
    page.estado.set('FIRMADA_RECEPTOR'); page.registrar();
    expect(page.permitePdf()).toBeFalse(); page.adjuntarPdf(eventoPdf());
    expect(api.post).toHaveBeenCalledTimes(1);
    expect(api.post.calls.mostRecent().args[0]).toBe('/api/notas/10/seguimientos');
  });

  it('el permiso de adjuntar sigue siendo necesario aunque la nota tenga firma del receptor', () => {
    page.nota.update(nota => ({ ...nota!, puedeAdjuntarPdf: true }));
    expect(page.permitePdf()).toBeFalse(); page.adjuntarPdf(eventoPdf());
    expect(api.post).not.toHaveBeenCalled();
  });

  it('requires permission for a standalone comment and rejects empty or short comments', () => {
    page.comentario.set('a'); page.registrar(); expect(api.post).not.toHaveBeenCalled();
    page.comentario.set('Comentario válido');
    page.nota.update(nota => ({ ...nota!, seguimiento: { ...nota!.seguimiento!, puedeComentar: false } }));
    page.registrar(); expect(api.post).not.toHaveBeenCalled();
  });
});

function eventoPdf(): Event {
  const transfer = new DataTransfer(); transfer.items.add(new File(['%PDF-1.7'], 'firma-receptor.pdf', { type: 'application/pdf' }));
  const input = document.createElement('input'); input.type = 'file'; input.files = transfer.files;
  const event = new Event('change'); Object.defineProperty(event, 'target', { value: input }); return event;
}
