import { TestBed } from '@angular/core/testing';
import { AlertController } from '@ionic/angular/standalone';
import { of, Subject, throwError } from 'rxjs';
import { ReportesPage } from './reportes.page';
import { ReporteItem, ReportePlantilla } from './reportes.models';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { DashboardService } from '../../services/dashboard.service';

const reporte: ReporteItem = { id: '17', nombre: 'Informe sintético', modulo: 'Tareas', tipo: 'ejecutivo',
  fecha: '2026-10-05', generadoPor: 'Usuario de prueba', formato: 'PDF', descripcion: 'Descripción sintética' };
const plantilla: ReportePlantilla = { modulo: 'Tareas', nombre: 'Resumen de tareas', descripcion: 'Plantilla de prueba' };
type AlertOptions = NonNullable<Parameters<AlertController['create']>[0]>;

class ReportesTestPage extends ReportesPage {
  access() {
    return {
      reportes: this.reportes, programaciones: this.programaciones, plantillas: this.plantillas,
      errorHistorial: this.errorReportes, recargarHistorial: () => this.loadReportes(),
      compartir: (item: ReporteItem) => this.compartirReporte(item), correo: this.compartirEmail,
      prepararCorreo: () => this.enviarCompartir(),
      selected: this.selectedReporte, vista: this.vistaCentro,
      nombre: this.genNombre, descripcion: this.genDescripcion, adjuntos: this.genArchivos,
      modal: this.showGenerarModal, editando: this.editandoId,
      abrir: () => this.openGenerarModal(), editar: (item: ReporteItem) => this.editarReporte(item),
      guardar: () => this.guardarReporte(), eliminar: (item: ReporteItem) => this.eliminarReporte(item),
      seleccionar: (item: ReporteItem) => this.selectReporte(item),
      quitarAdjunto: (index: number) => this.eliminarArchivoGenerar(index),
      desde: this.plantillaDesde, hasta: this.plantillaHasta, formato: this.plantillaFormato,
      generando: this.generandoPlantilla, modalPlantilla: this.showPlantillaModal,
      abrirPlantilla: () => this.abrirPlantilla(plantilla), generar: () => this.generarDesdePlantilla(),
      search: this.filtroSearch, modulo: this.filtroModulo, tipo: this.filtroTipo, formatoFiltro: this.filtroFormato,
      filtrados: this.reportesFiltrados, paginados: this.paginatedReportes, pagina: this.currentPage,
      filtrar: () => this.onFilterChange(), limpiar: () => this.limpiarFiltros(), ir: (page: number) => this.goToPage(page),
      exportar: (item: ReporteItem, formato?: string) => this.descargarReporte(item, formato),
      archivos: (item: ReporteItem) => this.archivos(item),
      puedeProgramar: () => this.puedeProgramar(),
      archivoNombre: (nombre: string) => this.archivoNombre(nombre),
      descargarArchivo: (item: ReporteItem, indice: number) => this.descargarArchivo(item, indice),
      progNombre: this.progNombre, progFecha: this.progFechaInicio, progHora: this.progHora,
      destinatarioId: this.progDestinatarioId, usuarios: this.usuariosDestinatarios,
      destinatario: this.destinatarioSeleccionado, progAdjuntos: this.progArchivos, modalProgramar: this.showProgramarModal,
      abrirProgramar: () => this.openProgramarModal(), programar: () => this.programarReporte(),
      cambiarEstado: (item: Parameters<ReportesTestPage['cambiarEstadoProgramacion']>[0]) => this.cambiarEstadoProgramacion(item),
      eliminarProgramacion: (item: Parameters<ReportesTestPage['eliminarProgramacion']>[0]) => this.eliminarProgramacion(item),
    };
  }
}

describe('ReportesPage: operaciones y recuperación de errores', () => {
  let page: ReturnType<ReportesTestPage['access']>;
  let instance: ReportesTestPage;
  let api: jasmine.SpyObj<ApiService>;
  let alerts: jasmine.Spy;
  let admin: boolean;
  let configurar: boolean;
  let dismissal: string;
  let options: AlertOptions[];
  let sesiones: Subject<void>;

  beforeEach(() => {
    admin = false; configurar = true; dismissal = 'cancel'; options = [];
    sesiones = new Subject<void>();
    api = jasmine.createSpyObj<ApiService>('ApiService', ['get', 'post', 'put', 'patch', 'delete', 'getBlob']);
    api.get.and.returnValue(of([])); api.post.and.returnValue(of(reporte)); api.put.and.returnValue(of(reporte));
    api.delete.and.returnValue(of(undefined));
    alerts = jasmine.createSpy('create').and.callFake(async (config: AlertOptions) => {
      options.push(config);
      return { present: async () => undefined, onDidDismiss: async () => ({ role: dismissal }) };
    });
    TestBed.configureTestingModule({ providers: [
      { provide: ApiService, useValue: api },
      { provide: AuthService, useValue: { isAdmin: () => admin, hasPermission: () => configurar, currentUser: () => ({ id: 1, nombre: 'Usuario de prueba' }), preferences: () => ({ recordsPerPage: 2 }), sessionChanged$: sesiones } },
      { provide: DashboardService, useValue: { getNotificaciones: () => of([]) } },
      { provide: AlertController, useValue: { create: alerts } },
    ] });
    spyOn(console, 'error');
    instance = TestBed.runInInjectionContext(() => new ReportesTestPage());
    page = instance.access();
  });

  it('requires the scheduling permission even for operational direction', () => {
    admin = true; configurar = false;
    expect(page.puedeProgramar()).toBeFalse();
    instance.ngOnInit();
    expect(api.get).not.toHaveBeenCalledWith('/api/reportes/programaciones');
    configurar = true;
    expect(page.puedeProgramar()).toBeTrue();
  });

  it('honors an explicit CSV export even when the report default is PDF', () => {
    api.getBlob.and.returnValue(of(new Blob(['TEST_ONLY_CSV'])));
    spyOn(URL, 'createObjectURL').and.returnValue('blob:test-only');
    spyOn(URL, 'revokeObjectURL'); spyOn(HTMLAnchorElement.prototype, 'click');
    page.exportar(reporte, 'CSV');
    expect(api.getBlob).toHaveBeenCalledWith('/api/reportes/17/exportar?formato=CSV');
  });

  it('shows load failures and recovers on the next query', () => {
    api.get.and.returnValue(throwError(() => ({ status: 503 })));
    page.recargarHistorial(); expect(page.errorHistorial()).toBeTrue();
    api.get.and.returnValue(of([reporte]));
    page.recargarHistorial(); expect(page.errorHistorial()).toBeFalse();
    expect(page.reportes()).toEqual([reporte]);
  });

  it('does not start a second save while the first one is pending', async () => {
    page.abrir(); page.nombre.set('Informe ficticio');
    const response = new Subject<ReporteItem>(); api.post.and.returnValue(response);
    const first = page.guardar(); const second = page.guardar();
    expect(api.post).toHaveBeenCalledTimes(1);
    response.next(reporte); response.complete(); await Promise.all([first, second]);
  });

  it('retries failed attachments without creating the report again', async () => {
    page.abrir(); page.nombre.set('Informe ficticio'); page.adjuntos.set([new File(['TEST_ONLY'], 'prueba.txt')]);
    api.post.and.callFake(<T>(url: string) => url === '/api/reportes'
      ? of(reporte as unknown as T) : throwError(() => ({ status: 503 })));
    await page.guardar();
    api.post.and.returnValue(of(reporte)); await page.guardar();
    expect(api.post.calls.allArgs().filter(args => args[0] === '/api/reportes').length).toBe(1);
    expect(api.post.calls.allArgs().filter(args => args[0] === '/api/reportes/17/archivos').length).toBe(2);
  });

  it('drops a file response after the session changes', () => {
    const response = new Subject<Blob>(); api.getBlob.and.returnValue(response);
    const createUrl = spyOn(URL, 'createObjectURL');
    page.exportar(reporte); sesiones.next();
    response.next(new Blob(['TEST_ONLY'])); response.complete();
    expect(createUrl).not.toHaveBeenCalled();
  });

  it('prepares an email without falsely confirming delivery', async () => {
    const open = spyOn(window, 'open');
    page.compartir(reporte); page.correo.set('destinatario@example.test');
    await page.prepararCorreo();
    expect(open).toHaveBeenCalledWith(jasmine.stringMatching(/^mailto:/), '_self');
    expect(options.some(option => option.header === 'Enviado')).toBeFalse();
  });

  async function confirmarEliminacion(): Promise<void> {
    const button = options[0].buttons?.find(item => typeof item !== 'string' && item.role === 'destructive');
    if (!button || typeof button === 'string' || !button.handler) throw new Error('Falta la acción de confirmación');
    await button.handler(undefined);
  }

  it('retries schedule attachments without creating the schedule again', async () => {
    admin = true; page.abrirProgramar(); page.progNombre.set('Resumen ficticio');
    page.progFecha.set('2099-01-01'); page.progHora.set('12:00');
    page.usuarios.set([{ id: 8, nombre: 'Persona de prueba', correo: 'persona@example.test' }]);
    page.destinatarioId.set('8'); page.progAdjuntos.set([new File(['TEST_ONLY'], 'prueba.txt')]);
    api.post.and.callFake(<T>(url: string) => url === '/api/reportes/programaciones'
      ? of({ id: 25 } as unknown as T) : throwError(() => ({ status: 503 })));
    await page.programar(); api.post.and.returnValue(of({ id: 25 })); await page.programar();
    expect(api.post.calls.allArgs().filter(args => args[0] === '/api/reportes/programaciones').length).toBe(1);
    expect(api.post.calls.allArgs().filter(args => args[0] === '/api/reportes/programaciones/25/archivos').length).toBe(2);
  });

  it('does not publish an old session save into the next session', async () => {
    instance.ngOnInit(); page.abrir(); page.nombre.set('Informe ficticio');
    const response = new Subject<ReporteItem>(); api.post.and.returnValue(response);
    const saving = page.guardar(); sesiones.next();
    page.adjuntos.set([new File(['TEST_ONLY_NEW_SESSION'], 'nuevo.txt')]);
    response.next(reporte); response.complete(); await saving;
    expect(api.post).toHaveBeenCalledTimes(1);
    expect(page.reportes()).toEqual([]);
    expect(page.modal()).toBeFalse();
    expect(options.some(option => option.header === 'Éxito')).toBeFalse();
  });

  it('muestra los nombres de adjuntos sin depender de rutas físicas y descarga por el endpoint autorizado', () => {
    const item: ReporteItem = { ...reporte, archivosRuta: '/api/reportes/17/descargar-archivo/0',
      archivosNombres: ['informe_final.txt'] };
    expect(page.archivos(item)).toEqual(['informe_final.txt']);
    expect(page.archivoNombre(page.archivos(item)[0])).toBe('informe_final.txt');
    api.getBlob.and.returnValue(of(new Blob(['contenido sintético'])));
    spyOn(URL, 'createObjectURL').and.returnValue('blob:adjunto-autorizado');
    const revocar = spyOn(URL, 'revokeObjectURL');
    const click = spyOn(HTMLAnchorElement.prototype, 'click').and.callFake(function (this: HTMLAnchorElement) {
      expect(this.href).toBe('blob:adjunto-autorizado');
      expect(this.download).toBe('informe_final.txt');
    });
    page.descargarArchivo(item, 0);
    expect(api.getBlob).toHaveBeenCalledWith('/api/reportes/17/descargar-archivo/0');
    expect(click).toHaveBeenCalledTimes(1);
    expect(revocar).toHaveBeenCalledWith('blob:adjunto-autorizado');
  });

  it('mantiene la descarga autorizada para respuestas anteriores sin usar su ruta física como enlace', () => {
    const item: ReporteItem = { ...reporte,
      archivosRuta: '/uploads/reportes/12345678-1234-1234-1234-123456789abc_informe_final.txt' };
    expect(page.archivoNombre(page.archivos(item)[0])).toBe('informe_final.txt');
    api.getBlob.and.returnValue(of(new Blob(['contenido sintético'])));
    spyOn(URL, 'createObjectURL').and.returnValue('blob:adjunto-legacy');
    spyOn(URL, 'revokeObjectURL'); spyOn(HTMLAnchorElement.prototype, 'click');
    page.descargarArchivo(item, 0);
    expect(api.getBlob).toHaveBeenCalledWith('/api/reportes/17/descargar-archivo/0');
  });

  it('consulta programaciones solo para administradores y carga los datos recibidos', () => {
    api.get.and.callFake(<T>(url: string) => of((url === '/api/reportes' ? [reporte] : url === '/api/reportes/plantillas' ? [plantilla] : []) as T));
    instance.ngOnInit();
    expect(page.reportes()).toEqual([reporte]); expect(page.plantillas()).toEqual([plantilla]);
    expect(api.get).not.toHaveBeenCalledWith('/api/reportes/programaciones');
    admin = true; instance.ngOnInit();
    expect(api.get).toHaveBeenCalledWith('/api/reportes/programaciones');
  });

  it('no envía nombres vacíos y conserva el formulario para corregirlos', async () => {
    page.abrir(); page.nombre.set('   '); await page.guardar();
    expect(api.post).not.toHaveBeenCalled(); expect(page.modal()).toBeTrue();
    expect(options[0].message).toBe('El nombre es obligatorio');
  });

  it('crea el reporte y sube los bytes originales antes de cerrar el formulario', async () => {
    const file = new File(['contenido sintético'], 'prueba.txt', { type: 'text/plain' });
    page.abrir(); page.nombre.set(reporte.nombre); page.adjuntos.set([file]);
    api.get.and.returnValue(of([reporte])); await page.guardar();
    expect(api.post.calls.argsFor(0)).toEqual(['/api/reportes', {
      nombre: reporte.nombre, modulo: 'Reuniones', tipo: 'ejecutivo', formato: 'PDF', descripcion: '',
      fecha: new Date().toISOString().slice(0, 10), generadoPor: 'Usuario de prueba',
    }]);
    const data = api.post.calls.argsFor(1)[1] as FormData;
    expect(api.post.calls.argsFor(1)[0]).toBe('/api/reportes/17/archivos');
    expect(await (data.get('files') as File).text()).toBe(await file.text());
    expect(page.reportes()).toEqual([reporte]); expect(page.modal()).toBeFalse(); expect(page.adjuntos()).toEqual([]);
  });

  it('edita por ID sin crear otro reporte y conserva sus datos al abrir', async () => {
    page.editar(reporte);
    expect(page.nombre()).toBe(reporte.nombre); expect(page.descripcion()).toBe(reporte.descripcion);
    page.nombre.set('Nombre corregido'); await page.guardar();
    expect(api.put).toHaveBeenCalledWith('/api/reportes/17', jasmine.objectContaining({ nombre: 'Nombre corregido', modulo: 'Tareas' }));
    expect(api.post).not.toHaveBeenCalled(); expect(page.editando()).toBeNull();
  });

  it('conserva el formulario y los archivos cuando la API falla y permite reintentar', async () => {
    const file = new File(['prueba'], 'prueba.txt');
    page.editar(reporte); page.adjuntos.set([file]);
    api.put.and.returnValue(throwError(() => ({ error: { message: 'No se pudo confirmar la operación' } })));
    await page.guardar();
    expect(page.modal()).toBeTrue(); expect(page.editando()).toBe('17'); expect(page.adjuntos()).toEqual([file]);
    expect(options[0].message).toBe('No se pudo confirmar la operación'); expect(api.post).not.toHaveBeenCalled();
    api.put.and.returnValue(of(reporte)); await page.guardar();
    expect(page.modal()).toBeFalse(); expect(api.put).toHaveBeenCalledTimes(2);
  });

  it('retira únicamente el adjunto elegido aunque los nombres coincidan', () => {
    const first = new File(['uno'], 'igual.txt'); const second = new File(['dos'], 'igual.txt');
    page.adjuntos.set([first, second]); page.quitarAdjunto(0);
    expect(page.adjuntos()).toEqual([second]);
  });

  it('mantiene el estado de generación mientras espera y actualiza el historial al confirmar', async () => {
    page.abrirPlantilla(); page.desde.set('2026-10-01'); page.hasta.set('2026-10-05'); page.formato.set('CSV');
    const result = new Subject<ReporteItem>(); api.post.and.returnValue(result);
    const pending = page.generar();
    expect(page.generando()).toBeTrue(); expect(page.modalPlantilla()).toBeTrue();
    expect(api.post).toHaveBeenCalledWith('/api/reportes/generar', { plantilla: 'Tareas', fechaDesde: '2026-10-01', fechaHasta: '2026-10-05', formato: 'CSV' });
    result.next(reporte); result.complete(); await pending;
    expect(page.generando()).toBeFalse(); expect(page.modalPlantilla()).toBeFalse();
    expect(page.reportes()).toEqual([reporte]); expect(page.vista()).toBe('historial');
  });

  it('una generación fallida conserva el período y libera el estado ocupado', async () => {
    page.abrirPlantilla(); page.desde.set('2026-10-01'); page.hasta.set('2026-10-05');
    api.post.and.returnValue(throwError(() => ({ error: { message: 'Período inválido' } }))); await page.generar();
    expect(page.generando()).toBeFalse(); expect(page.modalPlantilla()).toBeTrue(); expect(page.reportes()).toEqual([]);
    expect(page.desde()).toBe('2026-10-01'); expect(options[0].message).toBe('Período inválido');
  });

  it('no genera sin plantilla o sin las fechas requeridas', async () => {
    await page.generar(); page.abrirPlantilla(); page.desde.set(''); await page.generar();
    expect(api.post).not.toHaveBeenCalled(); expect(page.generando()).toBeFalse();
  });

  it('no elimina antes de confirmar y conserva la ficha si la API falla', async () => {
    page.seleccionar(reporte); await page.eliminar(reporte);
    expect(api.delete).not.toHaveBeenCalled();
    api.delete.and.returnValue(throwError(() => ({ status: 500 }))); await confirmarEliminacion();
    expect(page.selected()).toEqual(reporte); expect(api.get).not.toHaveBeenCalled();
    expect(options[1].message).toBe('No se pudo eliminar el reporte');
  });

  it('eliminar confirmado refresca el listado y cierra la ficha correspondiente', async () => {
    page.seleccionar(reporte); await page.eliminar(reporte); await confirmarEliminacion();
    expect(api.delete).toHaveBeenCalledWith('/api/reportes/17');
    expect(api.get).toHaveBeenCalledWith('/api/reportes'); expect(page.selected()).toBeNull();
    expect(options[1].message).toBe('Reporte eliminado correctamente');
  });

  it('aplica filtros combinados y reinicia la página al cambiar o limpiar filtros', () => {
    page.reportes.set([reporte, { ...reporte, id: '18', nombre: 'Otra acta', modulo: 'Actas', tipo: 'analitico', formato: 'CSV' },
      { ...reporte, id: '19', nombre: 'Otro informe' }]);
    page.ir(2); expect(page.paginados().map(item => item.id)).toEqual(['19']);
    page.search.set(' INFORME '); page.modulo.set('Tareas'); page.tipo.set('Ejecutivo'); page.formatoFiltro.set('PDF'); page.filtrar();
    expect(page.pagina()).toBe(1); expect(page.filtrados().map(item => item.id)).toEqual(['17', '19']);
    page.ir(9); expect(page.pagina()).toBe(1);
    page.limpiar(); expect(page.filtrados().map(item => item.id)).toEqual(['17', '18', '19']);
  });

  it('exporta mediante la API y libera el recurso local de descarga', () => {
    const blob = new Blob(['datos'], { type: 'text/csv' }); api.getBlob.and.returnValue(of(blob));
    spyOn(URL, 'createObjectURL').and.returnValue('blob:prueba'); const revoke = spyOn(URL, 'revokeObjectURL');
    const click = spyOn(HTMLAnchorElement.prototype, 'click');
    page.exportar({ ...reporte, formato: 'CSV' });
    expect(api.getBlob).toHaveBeenCalledWith('/api/reportes/17/exportar?formato=CSV');
    expect(URL.createObjectURL).toHaveBeenCalledWith(blob); expect(click).toHaveBeenCalledTimes(1); expect(revoke).toHaveBeenCalledWith('blob:prueba');
  });

  it('avisa cuando falla la exportación sin iniciar una descarga', async () => {
    api.getBlob.and.returnValue(throwError(() => ({ status: 503 })));
    const click = spyOn(HTMLAnchorElement.prototype, 'click'); page.exportar(reporte); await Promise.resolve();
    expect(api.getBlob).toHaveBeenCalledWith('/api/reportes/17/exportar?formato=PDF');
    expect(click).not.toHaveBeenCalled(); expect(options[0].message).toBe('No se pudo exportar el informe.');
  });

  it('solo ofrece destinatarios activos con nombre y correo, ordenados por nombre', () => {
    admin = true;
    api.get.and.returnValue(of([
      { id: 2, nombre: 'Inactivo', correo: 'inactivo@example.test', activo: false },
      { id: 3, nombre: 'Bloqueado', correo: 'bloqueado@example.test', bloqueado: true },
      { id: 4, nombre: 'Sin correo' }, { id: 5, correo: 'sin-nombre@example.test' },
      { id: '6', nombre: 'Zoe', correo: 'zoe@example.test' },
      { id: 7, nombre: 'Ana', segundoNombre: 'María', apellido: 'Prueba', correo: 'ana@example.test' },
    ]));
    page.abrirProgramar();
    expect(page.usuarios()).toEqual([{ id: 7, nombre: 'Ana María Prueba', correo: 'ana@example.test' }, { id: 6, nombre: 'Zoe', correo: 'zoe@example.test' }]);
    page.destinatarioId.set('7'); expect(page.destinatario()?.correo).toBe('ana@example.test');
  });

  it('requiere nombre, destinatario, fecha y hora antes de programar', async () => {
    admin = true;
    page.abrirProgramar(); await page.programar(); expect(options[0].message).toBe('El nombre es obligatorio');
    page.progNombre.set('Resumen'); await page.programar(); expect(options[1].message).toBe('Seleccione un usuario destinatario');
    page.usuarios.set([{ id: 8, nombre: 'Persona de prueba', correo: 'persona@example.test' }]); page.destinatarioId.set('8');
    await page.programar(); expect(options[2].message).toBe('La fecha y la hora de inicio son obligatorias');
    expect(api.post).not.toHaveBeenCalled(); expect(page.modalProgramar()).toBeTrue();
  });

  it('programa con el correo seleccionado y adjunta el archivo al ID recibido', async () => {
    admin = true;
    page.abrirProgramar(); page.progNombre.set('Resumen'); page.progFecha.set('2099-01-01'); page.progHora.set('12:00');
    page.usuarios.set([{ id: 8, nombre: 'Persona de prueba', correo: 'persona@example.test' }]); page.destinatarioId.set('8');
    const file = new File(['adjunto'], 'prueba.txt'); page.progAdjuntos.set([file]); api.post.and.returnValue(of({ id: 25 }));
    await page.programar();
    expect(api.post.calls.argsFor(0)).toEqual(['/api/reportes/programaciones', {
      nombre: 'Resumen', modulo: 'Reuniones', tipo: 'ejecutivo', formato: 'PDF', periodicidad: 'semanal',
      fechaInicio: '2099-01-01', hora: '12:00', destinatarios: 'persona@example.test',
    }]);
    expect(api.post.calls.argsFor(1)[0]).toBe('/api/reportes/programaciones/25/archivos');
    expect(await ((api.post.calls.argsFor(1)[1] as FormData).get('files') as File).text()).toBe('adjunto');
    expect(page.modalProgramar()).toBeFalse(); expect(page.destinatarioId()).toBe('');
  });

  it('un fallo de programación conserva datos y muestra el mensaje del servidor', async () => {
    admin = true;
    page.abrirProgramar(); page.progNombre.set('Resumen'); page.progFecha.set('2099-01-01'); page.progHora.set('12:00');
    page.usuarios.set([{ id: 8, nombre: 'Persona de prueba', correo: 'persona@example.test' }]); page.destinatarioId.set('8');
    api.post.and.returnValue(throwError(() => ({ error: { message: 'Fecha fuera del rango permitido' } }))); await page.programar();
    expect(page.modalProgramar()).toBeTrue(); expect(page.progFecha()).toBe('2099-01-01'); expect(page.destinatarioId()).toBe('8');
    expect(options[0].message).toBe('Fecha fuera del rango permitido');
  });

  it('pausa una programación solo al confirmar la API y conserva el estado ante errores', async () => {
    admin = true;
    const item = { id: 25, nombre: 'Resumen', modulo: 'Tareas', periodicidad: 'semanal' as const,
      activo: true, proximaEjecucion: '2099-01-01T12:00:00', ultimoEstado: 'PENDIENTE' };
    page.programaciones.set([item]); api.patch.and.returnValue(throwError(() => ({ status: 503 })));
    await page.cambiarEstado(item);
    expect(page.programaciones()).toEqual([item]); expect(api.get).not.toHaveBeenCalled();
    expect(options[0].message).toBe('No se pudo actualizar la programación.');
    api.patch.and.returnValue(of({ ...item, activo: false })); api.get.and.returnValue(of([{ ...item, activo: false }]));
    await page.cambiarEstado(item);
    expect(api.patch).toHaveBeenCalledWith('/api/reportes/programaciones/25/estado', { activo: false });
    expect(page.programaciones()[0].activo).toBeFalse();
  });

  it('cancelar la eliminación de una programación no envía DELETE', async () => {
    const item = { id: 25, nombre: 'Resumen', modulo: 'Tareas', periodicidad: 'semanal' as const,
      activo: true, proximaEjecucion: '2099-01-01T12:00:00', ultimoEstado: 'PENDIENTE' };
    await page.eliminarProgramacion(item); expect(api.delete).not.toHaveBeenCalled();
    admin = true; dismissal = 'destructive'; await page.eliminarProgramacion(item);
    expect(api.delete).toHaveBeenCalledWith('/api/reportes/programaciones/25');
    expect(api.get).toHaveBeenCalledWith('/api/reportes/programaciones');
  });
});
