import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertController, ToastController } from '@ionic/angular/standalone';
import { Subject, of } from 'rxjs';
import { InventarioPage } from './inventario.page';
import { InventarioActivoRequest, InventarioItem } from './inventario.models';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { DashboardService } from '../../services/dashboard.service';

class InventarioUnderTest extends InventarioPage {
  readonly campos = {
    nombre: this.formNombre, categoria: this.formCategoria, ubicacion: this.formUbicacion,
    unidad: this.formUnidad, responsable: this.formResponsable,
    tipo: this.formTipoAsignacion, personaId: this.formAsignadoUsuarioId, unidadId: this.formAsignadoUnidadId,
    asignables: this.asignables, catalogoError: this.asignablesError,
    personasOpciones: this.personasAsignables,
    fecha: this.formFechaAdquisicion, valido: this.formValido, errores: this.formErrores,
    guardando: this.saving, abierto: this.showModal, error: this.saveError,
    codigo: this.nextCode, activos: this.activos, seleccionado: this.selectedActivo,
    busqueda: this.filtroSearch, filtroUnidad: this.filtroUnidad, filtrados: this.filteredActivos,
  };
  nuevo(): void { this.openNewModal(); }
  editar(item: InventarioItem): void { this.openEditModal(item); }
  guardar(): void { this.guardarActivo(); }
  cerrar(): Promise<void> { return this.requestCloseModal(); }
  asignar(tipo: 'SIN_ASIGNAR' | 'PERSONA' | 'UNIDAD'): void { this.cambiarTipoAsignacion(tipo); }
  seleccionarUnidadAdministrativa(id: number | null): void { this.seleccionarUnidad(id); }
}

describe('Inventario de oficina', () => {
  let page: InventarioUnderTest;
  let api: jasmine.SpyObj<ApiService>;
  let result: Subject<InventarioItem>;
  const activo: InventarioItem = {
    id: '1', codigo: 'INV-2025-012', nombre: 'Silla de prueba', categoria: 'Mobiliario',
    ubicacion: 'Oficina de prueba', unidad: 'Unidad de prueba', responsable: 'usuario_prueba',
    estado: 'en buen estado', fechaAdquisicion: '', lockVersion: 2,
    tipoAsignacion: 'PERSONA', asignadoUsuarioId: 8, asignadoUnidadId: null,
  };

  beforeEach(() => {
    result = new Subject<InventarioItem>();
    api = jasmine.createSpyObj<ApiService>('ApiService', ['get', 'post', 'put']);
    api.get.and.returnValue(of({ personas: [{ id: 8, nombre: 'usuario_prueba' }], unidades: [{ id: 7, nombre: 'Unidad de prueba' }] }));
    api.post.and.returnValue(result);
    api.put.and.returnValue(result);
    TestBed.configureTestingModule({ providers: [
      { provide: ApiService, useValue: api },
      { provide: AuthService, useValue: { preferences: () => ({ recordsPerPage: 10 }) } },
      { provide: DashboardService, useValue: { getMenuItems: () => [] } },
      { provide: AlertController, useValue: {} },
      { provide: ToastController, useValue: { create: () => Promise.resolve({ present: () => Promise.resolve() }) } },
    ] });
    page = TestBed.runInInjectionContext(() => new InventarioUnderTest());
    page.nuevo();
    page.campos.nombre.set('Silla de prueba');
    page.campos.categoria.set('Mobiliario');
    page.campos.ubicacion.set('Oficina de prueba');
  });

  it('permite registrar un activo sin proveedor ni asignaciones', () => {
    expect(page.campos.valido()).toBeTrue();
    page.guardar();
    const body = api.post.calls.mostRecent().args[1] as InventarioActivoRequest;
    expect(body.tipoAsignacion).toBe('SIN_ASIGNAR');
    expect(body.asignadoUsuarioId).toBeNull();
    expect(body.asignadoUnidadId).toBeNull();
  });

  it('no envía fichas con campos obligatorios vacíos', () => {
    page.campos.nombre.set('   ');
    page.guardar();
    expect(api.post).not.toHaveBeenCalled();
    expect(page.campos.errores()).toContain('Nombre: es obligatorio.');
  });

  it('rechaza fechas inexistentes y futuras', () => {
    page.campos.fecha.set('2026-02-30');
    expect(page.campos.valido()).toBeFalse();
    page.campos.fecha.set('2999-01-01');
    expect(page.campos.valido()).toBeFalse();
    page.campos.fecha.set('2020-01-01');
    expect(page.campos.valido()).toBeTrue();
  });

  it('rechaza textos que exceden el límite del backend', () => {
    page.campos.unidad.set('a'.repeat(201));
    page.guardar();
    expect(api.post).not.toHaveBeenCalled();
    expect(page.campos.errores()).toContain('Unidad: admite hasta 200 caracteres.');
  });

  it('deja la asignación del código al servidor', () => {
    expect(page.campos.codigo()).toBe('Se asignará al guardar');
    page.guardar();
    expect(api.post.calls.mostRecent().args[1]).not.toEqual(jasmine.objectContaining({ codigo: jasmine.anything() }));
  });

  it('conserva el código y la persona por ID al editar y envía la versión leída', () => {
    page.campos.activos.set([activo]);
    page.editar(activo);
    expect(page.campos.codigo()).toBe(activo.codigo);
    expect(page.campos.unidad()).toBe('Unidad de prueba');
    expect(page.campos.responsable()).toBe('usuario_prueba');
    page.guardar();
    expect(api.put.calls.mostRecent().args).toEqual([
      '/api/inventario/1', jasmine.objectContaining({ tipoAsignacion: 'PERSONA', asignadoUsuarioId: 8, asignadoUnidadId: null, lockVersion: 2 }),
    ]);
  });

  it('usa el catálogo de Inventario sin necesitar permisos de otros módulos', () => {
    expect(api.get).toHaveBeenCalledWith('/api/inventario/asignables');
    expect(page.campos.asignables().personas[0].id).toBe(8);
  });

  it('permite elegir directamente una unidad administrativa y la guarda por ID', () => {
    page.campos.personaId.set(8);
    page.seleccionarUnidadAdministrativa(7);
    expect(page.campos.tipo()).toBe('UNIDAD');
    expect(page.campos.personaId()).toBeNull();
    expect(page.campos.unidadId()).toBe(7);
    expect(page.campos.unidad()).toBe('Unidad de prueba');
    expect(page.campos.valido()).toBeTrue();
    page.guardar();
    expect(api.post.calls.mostRecent().args[1]).toEqual(jasmine.objectContaining({
      tipoAsignacion: 'UNIDAD', asignadoUsuarioId: null, asignadoUnidadId: 7,
    }));
  });

  it('impide asignaciones incompletas o con ambos destinos', () => {
    page.asignar('PERSONA');
    page.guardar();
    expect(api.post).not.toHaveBeenCalled();
    page.campos.personaId.set(8);
    page.campos.unidadId.set(7);
    expect(page.campos.valido()).toBeFalse();
    page.campos.unidadId.set(null);
    expect(page.campos.valido()).toBeTrue();
    page.guardar();
    expect(api.post.calls.mostRecent().args[1]).toEqual(jasmine.objectContaining({
      tipoAsignacion: 'PERSONA', asignadoUsuarioId: 8, asignadoUnidadId: null,
    }));
  });

  it('limpia el destino anterior al pasar de persona a unidad o sin asignar', () => {
    page.editar(activo);
    page.asignar('UNIDAD');
    expect(page.campos.personaId()).toBeNull();
    expect(page.campos.responsable()).toBe('');
    page.campos.unidadId.set(7);
    expect(page.campos.valido()).toBeTrue();
    page.asignar('SIN_ASIGNAR');
    expect(page.campos.unidadId()).toBeNull();
    expect(page.campos.valido()).toBeTrue();
  });

  it('requiere revisar la asignación anterior sin inventar identidades', () => {
    page.editar({ ...activo, tipoAsignacion: 'PENDIENTE_REVISION', asignadoUsuarioId: null });
    page.guardar();
    expect(api.put).not.toHaveBeenCalled();
    expect(page.campos.responsable()).toBe('usuario_prueba');
    page.asignar('SIN_ASIGNAR');
    page.guardar();
    expect(api.put.calls.mostRecent().args[1]).toEqual(jasmine.objectContaining({
      tipoAsignacion: 'SIN_ASIGNAR', asignadoUsuarioId: null, asignadoUnidadId: null,
    }));
  });

  it('conserva la opción actual sin cambiar su identidad al elegir otra persona', () => {
    page.editar(activo);
    page.campos.asignables.set({ personas: [{ id: 9, nombre: 'Otro usuario de prueba' }], unidades: [] });
    page.campos.personaId.set(9);
    expect(page.campos.personasOpciones().map(p => p.id)).toEqual([8, 9]);
    expect(page.campos.personasOpciones()[1].nombre).toBe('Otro usuario de prueba');
  });

  it('impide cerrar y guardar dos veces mientras espera la respuesta', async () => {
    page.guardar();
    page.guardar();
    await page.cerrar();
    expect(api.post).toHaveBeenCalledTimes(1);
    expect(page.campos.abierto()).toBeTrue();
  });

  it('conserva el formulario y muestra errores específicos del servidor', () => {
    page.campos.unidad.set('Unidad de prueba');
    page.guardar();
    result.error(new HttpErrorResponse({ status: 400, error: { fields: { unidad: 'Unidad inválida' } } }));
    expect(page.campos.error()).toBe('Unidad inválida');
    expect(page.campos.unidad()).toBe('Unidad de prueba');
    expect(page.campos.abierto()).toBeTrue();
    expect(page.campos.guardando()).toBeFalse();
  });

  it('actualiza la ficha con el código y versión devueltos al guardar', () => {
    page.guardar();
    result.next(activo);
    expect(page.campos.seleccionado()).toEqual(activo);
    expect(page.campos.abierto()).toBeFalse();
    expect(page.campos.guardando()).toBeFalse();
  });

  it('busca por responsable y filtra por unidad', () => {
    page.campos.activos.set([activo, { ...activo, id: '2', unidad: 'Otra unidad de prueba', responsable: '' }]);
    page.campos.busqueda.set('usuario_prueba');
    expect(page.campos.filtrados().map(item => item.id)).toEqual(['1']);
    page.campos.busqueda.set('');
    page.campos.filtroUnidad.set('Otra unidad de prueba');
    expect(page.campos.filtrados().map(item => item.id)).toEqual(['2']);
  });
});
