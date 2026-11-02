import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { AlertController } from '@ionic/angular/standalone';
import { Subject, of, throwError } from 'rxjs';
import { TareasPage } from './tareas.page';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { DashboardService } from '../../services/dashboard.service';
import { TareaRespuesta, TareaMovimiento } from './tareas.models';
import { mapTareaRespuesta, prepararTareaMultipart } from './tareas-data';

class TareasTestPage extends TareasPage {
  access() {
    return {
      formTitulo: this.formTitulo, formResponsables: this.formResponsables,
      showModal: this.showModal, editandoId: this.editandoId, formAdjuntos: this.formAdjuntos,
      guardandoTarea: this.guardandoTarea,
      selectedTarea: this.selectedTarea, historialTarea: this.historialTarea,
      cargandoHistorial: this.cargandoHistorial, errorHistorial: this.errorHistorial,
      selectTarea: (id: string) => this.selectTarea(mapTareaRespuesta({ id, titulo: 'Tarea ficticia' })),
      closeDetail: () => this.closeDetail(), reintentarHistorial: () => this.reintentarHistorial(),
      tipoMovimiento: (accion: string) => this.tipoMovimiento(accion),
      formFechaInicio: this.formFechaInicio, formFechaFinalizacion: this.formFechaFinalizacion,
      nuevoChecklist: this.nuevoChecklist,
      editar: (tarea: Parameters<TareasTestPage['openEditModal']>[0]) => this.openEditModal(tarea),
      agregarAdjunto: (event: Event) => this.agregarAdjunto(event),
      guardarTarea: () => this.guardarTarea(), closeModal: () => this.closeModal(),
      quitarAdjunto: (index: number) => this.quitarAdjunto(index),
      puedePrevisualizar: (adjunto: Parameters<TareasTestPage['abrirAdjunto']>[1]) => this.puedePrevisualizar(adjunto),
      abrirAdjunto: (id: string, adjunto: Parameters<TareasTestPage['abrirAdjunto']>[1]) => this.abrirAdjunto(id, adjunto),
    };
  }
}

describe('TareasPage: archivos adjuntos', () => {
  let page: ReturnType<TareasTestPage['access']>;
  let api: jasmine.SpyObj<ApiService>;
  let alerts: jasmine.Spy;
  let request: Subject<TareaRespuesta>;

  beforeEach(() => {
    request = new Subject();
    api = jasmine.createSpyObj('ApiService', ['post', 'put', 'getBlob', 'get']);
    api.get.and.returnValue(of([]));
    api.post.and.returnValue(request);
    api.put.and.returnValue(request);
    alerts = jasmine.createSpy('create').and.resolveTo({ present: () => Promise.resolve() });
    TestBed.configureTestingModule({
      providers: [
        { provide: ApiService, useValue: api },
        { provide: AuthService, useValue: { currentUser: () => ({ id: 1, nombre: 'Usuario' }) } },
        { provide: DashboardService, useValue: {} },
        { provide: ActivatedRoute, useValue: {} },
        { provide: AlertController, useValue: { create: alerts } },
      ]
    });
    page = TestBed.runInInjectionContext(() => new TareasTestPage().access());
    page.formTitulo.set('Tarea de prueba');
    page.formResponsables.set([1]);
    page.formFechaInicio.set('2026-10-20');
    page.showModal.set(true);
  });

  it('sends original file bytes as multipart instead of only a name and size', async () => {
    const file = new File(['%PDF-1.4\nprueba'], 'prueba.pdf', { type: 'application/pdf' });
    const transfer = new DataTransfer();
    transfer.items.add(file);
    const input = document.createElement('input');
    input.type = 'file';
    input.files = transfer.files;
    const event = new Event('change');
    Object.defineProperty(event, 'target', { value: input });
    page.agregarAdjunto(event);
    page.guardarTarea();
    const data = api.post.calls.mostRecent().args[1] as FormData;
    expect(data instanceof FormData).toBeTrue();
    expect(await (data.get('archivos') as File).text()).toBe(await file.text());
    const body = JSON.parse(await (data.get('datos') as Blob).text());
    expect(body.adjuntos).toEqual([{ archivoIndice: 0 }]);
  });

  it('retains stored attachment IDs during edits', async () => {
    page.editandoId.set('4');
    page.formAdjuntos.set([{ id: '7', nombre: 'guardado.pdf', tamano: '1 KB', disponible: true }]);
    page.guardarTarea();
    const data = api.put.calls.mostRecent().args[1] as FormData;
    const body = JSON.parse(await (data.get('datos') as Blob).text());
    expect(body.adjuntos[0].id).toBe('7');
    expect(data.getAll('archivos')).toEqual([]);
  });

  it('sends the start and finish dates without completing the task', async () => {
    page.formFechaFinalizacion.set('2026-10-31');
    page.guardarTarea();
    const data = api.post.calls.mostRecent().args[1] as FormData;
    const body = JSON.parse(await (data.get('datos') as Blob).text());
    expect(body.fechaCreacion).toBe('2026-10-20');
    expect(body.fechaLimite).toBe('2026-10-31');
    expect(body.estado).toBeUndefined();
  });

  it('rejects a finish date before the start date', () => {
    page.formFechaFinalizacion.set('2026-10-19');
    page.guardarTarea();
    expect(api.post).not.toHaveBeenCalled();
    expect(page.showModal()).toBeTrue();
  });

  it('preserves completed checklist items while editing', async () => {
    page.editandoId.set('4');
    page.nuevoChecklist.set([
      { id: '7', texto: 'Ya realizado', completado: true },
      { id: '8', texto: 'Pendiente', completado: false },
    ]);
    page.guardarTarea();
    const data = api.put.calls.mostRecent().args[1] as FormData;
    const body = JSON.parse(await (data.get('datos') as Blob).text());
    expect(body.checklist).toEqual([
      { texto: 'Ya realizado', completado: true },
      { texto: 'Pendiente', completado: false },
    ]);
  });

  it('preserves the originating module and project when editing a task from minutes', async () => {
    page.editar(mapTareaRespuesta({ id: 44, titulo: 'Acuerdo', tipo: 'Acta', proyecto: 'Acta ACT-001',
      asignadoUsuarioId: 1, fechaCreacion: '2026-10-20', fechaLimite: '2026-11-01' }));
    page.guardarTarea();
    const data = api.put.calls.mostRecent().args[1] as FormData;
    const body = JSON.parse(await (data.get('datos') as Blob).text());
    expect(body.tipo).toBe('Acta');
    expect(body.proyecto).toBe('Acta ACT-001');
  });

  it('keeps selected files and the form open when saving fails', () => {
    const file = new File(['%PDF-1.4'], 'prueba.pdf');
    page.formAdjuntos.set([{ nombre: file.name, tamano: '8 B', archivo: file }]);
    api.post.and.returnValue(throwError(() => ({ error: { message: 'Sin espacio' } })));
    page.guardarTarea();
    expect(page.guardandoTarea()).toBeFalse();
    expect(page.showModal()).toBeTrue();
    expect(page.formAdjuntos()[0].archivo).toBe(file);
    expect(alerts).toHaveBeenCalled();
  });

  it('prevents duplicate saves and closing the form while files are uploading', () => {
    page.guardarTarea();
    page.guardarTarea();
    page.closeModal();
    expect(api.post).toHaveBeenCalledTimes(1);
    expect(page.showModal()).toBeTrue();
  });

  it('removes one selected file even when filenames match', () => {
    page.formAdjuntos.set([{ nombre: 'a.pdf', tamano: '1 KB' }, { nombre: 'a.pdf', tamano: '2 KB' }]);
    page.quitarAdjunto(0);
    expect(page.formAdjuntos()).toEqual([{ nombre: 'a.pdf', tamano: '2 KB' }]);
  });

  it('does not offer preview or request downloads for legacy references', () => {
    const reference = { id: '7', nombre: 'a.pdf', tamano: '1 KB', disponible: false };
    expect(page.puedePrevisualizar(reference)).toBeFalse();
    page.abrirAdjunto('4', reference);
    expect(api.getBlob).not.toHaveBeenCalled();
    expect(page.puedePrevisualizar({ ...reference, disponible: true, tipoContenido: 'text/html' })).toBeFalse();
  });

  it('does not send an invalid form', () => {
    page.formTitulo.set('');
    page.guardarTarea();
    expect(api.post).not.toHaveBeenCalled();
    expect(page.guardandoTarea()).toBeFalse();
  });

  it('normalizes API IDs, dates and optional fields consistently', () => {
    const tarea = mapTareaRespuesta({ id: 4, titulo: 'Prueba', asignadoUsuarioId: '2',
      participanteUsuarioIds: ['2', 3], fechaCreacion: '2026-01-05T10:00:00', progreso: 0 });
    expect(tarea.id).toBe('4');
    expect(tarea.asignadoUsuarioId).toBe(2);
    expect(tarea.participanteUsuarioIds).toEqual([2, 3]);
    expect(tarea.fechaCreacion).toBe('2026-01-05');
    expect(tarea.checklist).toEqual([]);
    expect(tarea.estado).toBe('por_hacer');
    expect(tarea.progreso).toBe(0);
  });

  it('indexes only new files when stored and new attachments are mixed', async () => {
    const body = { titulo: 'Prueba', descripcion: '', tipo: '' as const, proyecto: '', asignado: '',
      asignadoUsuarioId: null, participanteUsuarioIds: [], prioridad: 'media' as const,
      fechaCreacion: '2026-10-20', fechaLimite: '', checklist: [], unidades: '' };
    const first = new File(['abc'], 'a.pdf');
    const second = new File(['de'], 'b.pdf');
    const { data, totalBytes } = prepararTareaMultipart(body, [
      { id: '7', nombre: 'guardado.pdf', tamano: '1 KB' },
      { nombre: first.name, tamano: '3 B', archivo: first },
      { id: '8', nombre: 'otro.pdf', tamano: '2 KB' },
      { nombre: second.name, tamano: '2 B', archivo: second },
    ]);
    const payload = JSON.parse(await (data.get('datos') as Blob).text()) as { adjuntos: { id?: string; archivoIndice?: number }[] };
    expect(payload.adjuntos.map(item => item.archivoIndice)).toEqual([undefined, 0, undefined, 1]);
    expect(payload.adjuntos.map(item => item.id)).toEqual(['7', undefined, '8', undefined]);
    expect(data.getAll('archivos').length).toBe(2);
    expect(totalBytes).toBe(5);
    expect('adjuntos' in body).toBeFalse();
  });
});

describe('TareasPage: historial', () => {
  let page: ReturnType<TareasTestPage['access']>;
  let api: jasmine.SpyObj<ApiService>;
  let requests: Subject<TareaMovimiento[]>[];
  const movimiento: TareaMovimiento = {
    id: '1', accion: 'CAMBIO_AVANCE', usuario: 'actor@example.test', fecha: '2026-01-02T10:00:00',
    detalle: 'Avance anterior: 0%. Nuevo avance: 50%.',
  };

  beforeEach(() => {
    requests = Array.from({ length: 3 }, () => new Subject<TareaMovimiento[]>());
    api = jasmine.createSpyObj('ApiService', ['get']);
    api.get.and.returnValues(...requests);
    TestBed.configureTestingModule({ providers: [
      { provide: ApiService, useValue: api },
      { provide: AuthService, useValue: {} },
      { provide: DashboardService, useValue: {} },
      { provide: ActivatedRoute, useValue: {} },
      { provide: AlertController, useValue: {} },
    ] });
    page = TestBed.runInInjectionContext(() => new TareasTestPage().access());
  });

  it('loads history only for the selected task and retains the server chronology', () => {
    TestBed.tick(); expect(api.get).not.toHaveBeenCalled();
    page.selectTarea('4'); TestBed.tick();
    expect(api.get).toHaveBeenCalledOnceWith('/api/tareas/4/historial');
    expect(page.cargandoHistorial()).toBeTrue();
    const history = [movimiento, { ...movimiento, id: '2', fecha: '2026-01-02T11:00:00' }];
    requests[0].next(history); requests[0].complete(); TestBed.tick();
    expect(page.historialTarea()).toEqual(history);
    expect(page.cargandoHistorial()).toBeFalse(); expect(page.errorHistorial()).toBeFalse();
    expect(api.get).toHaveBeenCalledTimes(1);
  });

  it('cancels a previous request and clears stale rows when selecting another task', () => {
    page.selectTarea('4'); TestBed.tick(); requests[0].next([movimiento]);
    page.selectTarea('5'); TestBed.tick();
    expect(requests[0].observed).toBeFalse(); expect(page.historialTarea()).toEqual([]);
    requests[0].next([{ ...movimiento, id: 'old' }]);
    requests[1].next([{ ...movimiento, id: 'new' }]); requests[1].complete();
    expect(page.historialTarea().map(item => item.id)).toEqual(['new']);
  });

  it('refreshes after the selected task is updated without creating any frontend movements', () => {
    page.selectTarea('4'); TestBed.tick(); requests[0].next([movimiento]); requests[0].complete();
    page.selectedTarea.update(task => task ? { ...task, progreso: 80 } : task); TestBed.tick();
    expect(api.get).toHaveBeenCalledTimes(2); expect(page.historialTarea()).toEqual([]);
    requests[1].next([movimiento, { ...movimiento, id: '2', detalle: 'Avance anterior: 50%. Nuevo avance: 80%.' }]);
    requests[1].complete(); expect(page.historialTarea()).toHaveSize(2);
  });

  it('shows a load error and allows a later request to recover', () => {
    page.selectTarea('4'); TestBed.tick(); requests[0].error({ status: 500 });
    expect(page.errorHistorial()).toBeTrue(); expect(page.cargandoHistorial()).toBeFalse();
    expect(page.historialTarea()).toEqual([]);
    page.reintentarHistorial(); TestBed.tick();
    expect(page.errorHistorial()).toBeFalse(); expect(page.cargandoHistorial()).toBeTrue();
    requests[1].next([movimiento]); requests[1].complete();
    expect(page.historialTarea()).toEqual([movimiento]); expect(page.errorHistorial()).toBeFalse();
  });

  it('clears history after closing and ignores a late response', () => {
    page.selectTarea('4'); TestBed.tick(); requests[0].next([movimiento]);
    page.closeDetail(); TestBed.tick();
    requests[0].next([movimiento]);
    expect(requests[0].observed).toBeFalse(); expect(page.historialTarea()).toEqual([]);
    expect(page.cargandoHistorial()).toBeFalse(); expect(api.get).toHaveBeenCalledTimes(1);
  });

  it('unsubscribes when the page is destroyed', () => {
    page.selectTarea('4'); TestBed.tick(); expect(requests[0].observed).toBeTrue();
    TestBed.resetTestingModule(); expect(requests[0].observed).toBeFalse();
  });

  it('distinguishes an empty history from a failed request and formats change types', () => {
    page.selectTarea('4'); TestBed.tick(); requests[0].next([]); requests[0].complete();
    expect(page.historialTarea()).toEqual([]); expect(page.errorHistorial()).toBeFalse();
    expect(page.tipoMovimiento('CAMBIO_RESPONSABLE')).toBe('Cambio de responsable');
    expect(page.tipoMovimiento('CAMBIO_ESTADO')).toBe('Cambio de estado');
    expect(page.tipoMovimiento('CAMBIO_AVANCE')).toBe('Cambio de avance');
  });
});
