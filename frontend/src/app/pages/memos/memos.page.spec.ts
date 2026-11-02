import { TestBed } from '@angular/core/testing';
import { AlertController } from '@ionic/angular/standalone';
import { of, Subject, throwError } from 'rxjs';
import { MemosPage } from './memos.page';
import { Memo } from './memos.models';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { DashboardService } from '../../services/dashboard.service';

const memo: Memo = {
  id: '1', numeroId: '10', numero: '001', asunto: 'Memo de prueba', destino: 'Unidad de prueba',
  descripcion: 'Datos sintéticos', creadoPor: { id: '1', nombre: 'Usuario de prueba' },
  seguimientoUsuario: { id: '2', nombre: 'Responsable de prueba' }, estado: 'EN_SEGUIMIENTO',
  completadoPor: null, completadoAt: null, anuladoPor: null, anuladoAt: null, anulacionMotivo: null,
  createdAt: '2026-01-01T10:00:00', updatedAt: '2026-01-01T10:00:00', historial: [],
};
const reserva = { id: '10', numero: '001', estado: 'RESERVADO', reservaPropia: true };

class MemosTestPage extends MemosPage {
  access() {
    return {
      lista: this.memos, numeros: this.numeros, errorNumeros: this.numerosError,
      detalle: this.seleccionado, cargando: this.cargandoDetalle,
      abrir: (item: Memo) => this.abrirDetalle(item), cerrar: () => this.cerrarDetalle(),
      descargar: (item: Memo) => this.descargarWord(item),
      numero: this.formNumeroId, asunto: this.formAsunto, destino: this.formDestino,
      responsable: this.formResponsableId, registro: this.showRegistro, guardando: this.saving,
      reservar: (id: string) => this.seleccionarNumero(id), cerrando: () => this.cerrarRegistro(),
      guardar: () => this.guardar(), cargarNumeros: () => this.cargarNumeros(),
    };
  }
}

describe('MemosPage: consultas, reservas y recuperación', () => {
  let instance: MemosTestPage;
  let page: ReturnType<MemosTestPage['access']>;
  let api: jasmine.SpyObj<ApiService>;
  let sesiones: Subject<void>;
  let crearAlerta: jasmine.Spy;

  beforeEach(() => {
    sesiones = new Subject<void>();
    api = jasmine.createSpyObj<ApiService>('ApiService', ['get', 'post', 'delete', 'getBlob']);
    api.get.and.returnValue(of([])); api.post.and.returnValue(of(memo)); api.delete.and.returnValue(of(undefined));
    crearAlerta = jasmine.createSpy('create').and.resolveTo({ present: async () => undefined });
    TestBed.configureTestingModule({ providers: [
      { provide: ApiService, useValue: api },
      { provide: AuthService, useValue: { hasPermission: () => true, currentUser: () => ({ id: 1 }), sessionChanged$: sesiones } },
      { provide: DashboardService, useValue: { getNotificaciones: () => of([]) } },
      { provide: AlertController, useValue: { create: crearAlerta } },
    ] });
    instance = TestBed.runInInjectionContext(() => new MemosTestPage()); page = instance.access();
  });

  function prepararRegistro(): void {
    page.numero.set('10'); page.asunto.set('Memo de prueba'); page.destino.set('Unidad de prueba');
    page.responsable.set('2'); page.registro.set(true);
  }

  it('mantiene el detalle más reciente cuando las respuestas llegan desordenadas', () => {
    const anterior = new Subject<Memo>(), actual = new Subject<Memo>();
    api.get.and.returnValues(anterior, actual);
    page.abrir(memo); page.abrir({ ...memo, id: '2', numero: '002' });
    anterior.next(memo); anterior.complete();
    expect(page.detalle()?.id).toBe('2'); expect(page.cargando()).toBeTrue();
    actual.next({ ...memo, id: '2' }); actual.complete();
    expect(page.detalle()?.id).toBe('2'); expect(page.cargando()).toBeFalse();
  });

  it('no reabre un detalle cerrado por una respuesta tardía', () => {
    const pendiente = new Subject<Memo>(); api.get.and.returnValue(pendiente);
    page.abrir(memo); page.cerrar(); pendiente.next(memo); pendiente.complete();
    expect(page.detalle()).toBeNull(); expect(page.cargando()).toBeFalse();
  });

  it('ignora el error de un detalle sustituido por otro', () => {
    const pendiente = new Subject<Memo>(); api.get.and.returnValues(pendiente, of({ ...memo, id: '2' }));
    page.abrir(memo); page.abrir({ ...memo, id: '2' }); pendiente.error({ status: 500 });
    expect(page.detalle()?.id).toBe('2'); expect(crearAlerta).not.toHaveBeenCalled();
  });

  it('cancela la descarga cuando cambia la sesión', () => {
    const archivo = new Subject<Blob>(); api.getBlob.and.returnValue(archivo);
    const crearUrl = spyOn(URL, 'createObjectURL');
    page.descargar(memo); sesiones.next(); archivo.next(new Blob(['prueba']));
    expect(crearUrl).not.toHaveBeenCalled();
  });

  it('limpia el detalle y evita publicar datos de una sesión anterior', () => {
    instance.ngOnInit();
    const pendiente = new Subject<Memo>(); api.get.withArgs('/api/memos/1').and.returnValue(pendiente);
    page.abrir(memo); sesiones.next(); pendiente.next(memo);
    expect(page.detalle()).toBeNull(); expect(page.lista()).toEqual([]);
  });

  it('no reserva otro número cuando falla liberar el anterior', () => {
    prepararRegistro(); api.delete.and.returnValue(throwError(() => ({ status: 500 })));
    api.get.and.returnValue(of([reserva]));
    page.reservar('11');
    expect(api.post).not.toHaveBeenCalled(); expect(page.numero()).toBe('10');
    expect(crearAlerta).toHaveBeenCalled();
  });

  it('libera antes de reservar y conserva únicamente la nueva selección', () => {
    prepararRegistro(); api.post.and.returnValue(of({ id: '11' }));
    api.get.and.returnValue(of([{ ...reserva, id: '11', numero: '002' }]));
    page.reservar('11');
    expect(api.delete).toHaveBeenCalledWith('/api/memos/numeros/10/reserva');
    expect(api.post).toHaveBeenCalledWith('/api/memos/numeros/11/reservar', {});
    expect(page.numero()).toBe('11');
  });

  it('permite reintentar el cierre si no pudo liberar la reserva', () => {
    prepararRegistro(); api.delete.and.returnValue(throwError(() => ({ status: 0 })));
    api.get.and.returnValue(of([reserva]));
    page.cerrando(); expect(page.registro()).toBeTrue(); expect(page.numero()).toBe('10');
    api.delete.and.returnValue(of(undefined)); page.cerrando();
    expect(page.registro()).toBeFalse(); expect(page.numero()).toBe('');
  });

  it('conserva una reserva válida después de un guardado fallido', () => {
    prepararRegistro(); api.post.and.returnValue(throwError(() => ({ status: 500 })));
    api.get.and.returnValue(of([reserva])); page.guardar();
    expect(page.numero()).toBe('10'); expect(page.registro()).toBeTrue(); expect(page.guardando()).toBeFalse();
  });

  it('descarta una selección que el servidor confirma ocupada', () => {
    prepararRegistro(); api.post.and.returnValue(throwError(() => ({ status: 409 })));
    api.get.and.returnValue(of([{ ...reserva, estado: 'OCUPADO', reservaPropia: false }])); page.guardar();
    expect(page.numero()).toBe(''); expect(page.registro()).toBeTrue();
  });

  it('informa un fallo de catálogo sin borrar los datos y permite recuperarlo', () => {
    api.get.and.returnValue(of([reserva])); page.cargarNumeros();
    api.get.and.returnValue(throwError(() => ({ status: 0 }))); page.cargarNumeros();
    expect(page.numeros().length).toBe(1); expect(page.errorNumeros()).toBeTrue();
    api.get.and.returnValue(of([reserva])); page.cargarNumeros(); expect(page.errorNumeros()).toBeFalse();
  });

  it('no descarta una nueva reserva por un catálogo anterior que llega tarde', () => {
    const anterior = new Subject<unknown[]>(); api.get.and.returnValue(anterior);
    page.cargarNumeros();
    api.get.and.returnValue(of([reserva])); page.numero.set('10'); page.cargarNumeros();
    anterior.next([]); anterior.complete();
    expect(page.numero()).toBe('10'); expect(page.numeros().length).toBe(1);
  });

  it('no duplica guardados ni cambia la reserva durante el guardado', () => {
    prepararRegistro(); const pendiente = new Subject<Memo>(); api.post.and.returnValue(pendiente);
    page.guardar(); page.guardar(); page.reservar('11');
    expect(api.post).toHaveBeenCalledTimes(1); expect(api.delete).not.toHaveBeenCalled();
    pendiente.next(memo); expect(page.registro()).toBeFalse(); expect(page.lista().length).toBe(1);
  });
});
