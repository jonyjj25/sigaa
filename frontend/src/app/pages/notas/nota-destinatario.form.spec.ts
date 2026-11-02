import { DestroyRef, inject } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { ApiService } from '../../services/api.service';
import { NotaDestinatarioForm, NotaDepartamentoOpcion } from './nota-destinatario.form';

describe('Destinatario de Notas: institución y departamento', () => {
  let api: jasmine.SpyObj<ApiService>;
  let form: NotaDestinatarioForm;
  const departamento = (id: string, director = 'Responsable externo'): NotaDepartamentoOpcion => ({
    id, nombre: `Área ${id}`, director, directorCargo: 'Jefatura del área', activo: true,
  });
  const guardada = () => ({
    institucion: { id: '1', nombre: 'Institución guardada', siglas: 'TEST' },
    departamento: { id: '10', nombre: 'Área guardada' }, destinatarioNombre: 'Persona guardada',
    destinatarioCargo: 'Cargo guardado', destino: 'Institución guardada',
  });
  beforeEach(() => {
    api = jasmine.createSpyObj('ApiService', ['get']);
    api.get.and.returnValue(of([]));
    TestBed.configureTestingModule({});
    form = TestBed.runInInjectionContext(() => new NotaDestinatarioForm(api, inject(DestroyRef)));
  });

  it('carga departamentos solo después de elegir institución y copia su persona y cargo', () => {
    api.get.and.returnValue(of([departamento('10')]));
    form.inicializar();
    expect(api.get).not.toHaveBeenCalled();
    form.cambiarInstitucion('1');
    expect(api.get).toHaveBeenCalledOnceWith('/api/notas/instituciones/1/departamentos');
    expect(form.nombre()).toBe(''); expect(form.cargo()).toBe('');
    form.cambiarDepartamento('10');
    expect(form.nombre()).toBe('Responsable externo'); expect(form.cargo()).toBe('Jefatura del área');
    expect(api.get).toHaveBeenCalledTimes(1);
  });

  it('limpia área, destinatario y cargo al cambiar institución y descarta respuestas anteriores', () => {
    const anterior = new Subject<NotaDepartamentoOpcion[]>(), actual = new Subject<NotaDepartamentoOpcion[]>();
    api.get.and.returnValues(anterior, actual);
    form.cambiarInstitucion('1'); form.nombre.set('Dato anterior'); form.cargo.set('Cargo anterior');
    form.departamentoId.set('10');
    form.cambiarInstitucion('2');
    expect(anterior.observed).toBeFalse();
    expect(form.departamentoId()).toBe(''); expect(form.nombre()).toBe(''); expect(form.cargo()).toBe('');
    actual.next([departamento('20')]); anterior.next([departamento('10')]);
    expect(form.departamentos().map(item => item.id)).toEqual(['20']);
    form.cambiarDepartamento('10'); expect(form.departamentoId()).toBe('');
    form.cambiarDepartamento('20'); expect(form.departamentoId()).toBe('20');
  });

  it('no duplica consultas al repetir una selección o volver a una institución en el mismo formulario', () => {
    api.get.and.returnValues(of([departamento('10')]), of([departamento('20')]), of([departamento('10', 'Persona actualizada')]));
    form.cambiarInstitucion('1'); form.cambiarInstitucion('1');
    form.cambiarDepartamento('10'); form.cambiarInstitucion('2'); form.cambiarInstitucion('1');
    expect(api.get).toHaveBeenCalledTimes(2);
    expect(form.departamentos().map(item => item.id)).toEqual(['10']);
    // Otra apertura debe obtener el catálogo vigente, sin conservar datos de una sesión de edición anterior.
    form.inicializar(); form.cambiarInstitucion('1'); expect(api.get).toHaveBeenCalledTimes(3);
    expect(form.departamentos()[0].director).toBe('Persona actualizada');
  });

  it('abrir una nota histórica no recalcula destinatario, cargo ni nombre del área', () => {
    api.get.and.returnValue(of([{ ...departamento('10', 'Persona nueva'), nombre: 'Área renombrada' }]));
    form.inicializar(guardada());
    expect(form.nombre()).toBe('Persona guardada'); expect(form.cargo()).toBe('Cargo guardado');
    expect(form.opcionesDepartamentos()[0].nombre).toBe('Área guardada');
    expect(form.institucionOriginal()?.nombre).toBe('Institución guardada');
    form.cambiarDepartamento('10'); expect(form.nombre()).toBe('Persona guardada');
  });

  it('conserva un área guardada que fue desactivada y solo permite nuevas selecciones activas', () => {
    api.get.and.returnValue(of([departamento('20'), { ...departamento('30'), activo: false }]));
    form.inicializar(guardada());
    expect(form.opcionesDepartamentos().map(item => item.id)).toEqual(['10', '20']);
    expect(form.nombre()).toBe('Persona guardada');
    form.cambiarDepartamento('20');
    expect(form.nombre()).toBe('Responsable externo');
    expect(form.opcionesDepartamentos().map(item => item.id)).toEqual(['20']);
  });

  it('no asocia automáticamente un departamento a una nota antigua que nunca lo guardó', () => {
    api.get.and.returnValue(of([departamento('10')]));
    form.inicializar({ ...guardada(), departamento: null });
    expect(form.departamentoId()).toBe(''); expect(form.nombre()).toBe('Persona guardada');
    expect(form.cargo()).toBe('Cargo guardado');
  });

  it('muestra errores temporales, conserva datos históricos y permite reintentar', () => {
    api.get.and.returnValues(throwError(() => ({ status: 503 })), of([departamento('10', 'Persona nueva')]));
    form.inicializar(guardada());
    expect(form.error()).not.toBe(''); expect(form.cargando()).toBeFalse();
    expect(form.departamentoId()).toBe('10'); expect(form.nombre()).toBe('Persona guardada');
    form.reintentar();
    expect(form.error()).toBe(''); expect(form.nombre()).toBe('Persona guardada');
    expect(api.get).toHaveBeenCalledTimes(2);
  });

  it('limpiar institución cancela la carga y deja vacía toda la cadena', () => {
    const request = new Subject<NotaDepartamentoOpcion[]>(); api.get.and.returnValue(request);
    form.cambiarInstitucion('1'); form.cambiarInstitucion('');
    expect(request.observed).toBeFalse(); expect(form.cargando()).toBeFalse();
    expect(form.institucionId()).toBe(''); expect(form.departamentos()).toEqual([]);
    expect(form.nombre()).toBe(''); expect(form.cargo()).toBe('');
  });

  it('cancela peticiones cuando se destruye el formulario', () => {
    const request = new Subject<NotaDepartamentoOpcion[]>(); api.get.and.returnValue(request);
    form.cambiarInstitucion('1'); TestBed.resetTestingModule();
    expect(request.observed).toBeFalse();
  });
});
