import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { AlertController } from '@ionic/angular/standalone';
import { of, Subject, throwError } from 'rxjs';
import { AsistenciaQrPage } from './asistencia-qr.page';
import { ApiService } from '../../services/api.service';
import { AsistenciaService } from '../../services/asistencia.service';
import { AuthService } from '../../services/auth.service';
import { DashboardService } from '../../services/dashboard.service';

class SesionQrTestPage extends AsistenciaQrPage {
  abrir(): void { this.crearSesion(); }
  cancelar(): void { this.cerrarNuevaSesion(); }
  guardar(): Promise<void> { return this.guardarNuevaSesion(); }
  datos(nombre: string, fecha = '2026-10-08'): void {
    this.nombreNuevaSesion.set(nombre);
    this.fechaNuevaSesion.set(fecha);
  }
  estado() {
    return {
      abierta: this.mostrarNuevaSesion(), guardando: this.creandoSesion(),
      nombre: this.nombreNuevaSesion(), fecha: this.fechaNuevaSesion(), error: this.errorNuevaSesion(),
    };
  }
}

describe('Configuración QR: formulario de nueva sesión', () => {
  let page: SesionQrTestPage;
  let api: jasmine.SpyObj<ApiService>;
  const permisoCrear = signal(true);
  const permisoAdministrar = signal(false);

  beforeEach(() => {
    permisoCrear.set(true);
    permisoAdministrar.set(false);
    api = jasmine.createSpyObj<ApiService>('ApiService', ['get', 'post']);
    api.get.and.returnValue(of([]));
    api.post.and.returnValue(of({ id: 1 }));
    TestBed.configureTestingModule({ providers: [
      { provide: ApiService, useValue: api },
      { provide: AsistenciaService, useValue: { getHistorial: () => of([]) } },
      { provide: AuthService, useValue: {
        hasPermission: (_module: string, action: string) => action === 'Crear' ? permisoCrear() : permisoAdministrar(),
      } },
      { provide: DashboardService, useValue: {} },
      { provide: AlertController, useValue: {} },
      { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({
        reunionTitulo: 'Reunión de prueba', reunionFecha: '2026-10-08T10:00:00',
      }) } } },
    ] });
    page = TestBed.runInInjectionContext(() => new SesionQrTestPage());
    page.ngOnInit();
    api.get.calls.reset();
  });

  afterEach(() => page.ngOnDestroy());

  it('conserva el nombre y la fecha de la reunión al abrir el formulario', () => {
    page.abrir();
    expect(page.estado()).toEqual({
      abierta: true, guardando: false, nombre: 'Reunión de prueba', fecha: '2026-10-08', error: '',
    });
  });

  it('cancelar cierra el formulario sin crear una sesión', () => {
    page.abrir(); page.cancelar();
    expect(page.estado().abierta).toBeFalse();
    expect(api.post).not.toHaveBeenCalled();
  });

  it('rechaza nombres vacíos y fechas sin completar', async () => {
    page.abrir(); page.datos('   ');
    await page.guardar();
    page.datos('Sesión de prueba', '');
    await page.guardar();
    expect(api.post).not.toHaveBeenCalled();
    expect(page.estado().abierta).toBeTrue();
  });

  it('crea mediante el endpoint existente y actualiza las listas al terminar', async () => {
    page.abrir(); page.datos('  Sesión de prueba  ');
    await page.guardar();
    expect(api.post).toHaveBeenCalledOnceWith('/api/asistencia/sesiones', {
      nombre: 'Sesión de prueba', fecha: '2026-10-08', tipo: 'reunion',
    });
    expect(api.get).toHaveBeenCalledWith('/api/asistencia/sesiones');
    expect(api.get).toHaveBeenCalledWith('/api/asistencia/sesiones?archivadas=true');
    expect(page.estado().abierta).toBeFalse();
    expect(page.estado().guardando).toBeFalse();
  });

  it('evita envíos duplicados y el cierre mientras se guarda', async () => {
    const respuesta = new Subject<object>();
    api.post.and.returnValue(respuesta);
    page.abrir(); page.datos('Sesión de prueba');
    const guardado = page.guardar();
    await page.guardar(); page.cancelar();
    expect(page.estado().guardando).toBeTrue();
    expect(page.estado().abierta).toBeTrue();
    expect(api.post).toHaveBeenCalledTimes(1);
    respuesta.next({ id: 1 }); respuesta.complete();
    await guardado;
    expect(page.estado().guardando).toBeFalse();
    expect(page.estado().abierta).toBeFalse();
  });

  it('muestra el error sin perder los datos y permite reintentar', async () => {
    api.post.and.returnValue(throwError(() => ({ status: 503 })));
    page.abrir(); page.datos('Sesión de prueba');
    await page.guardar();
    expect(page.estado().abierta).toBeTrue();
    expect(page.estado().nombre).toBe('Sesión de prueba');
    expect(page.estado().fecha).toBe('2026-10-08');
    expect(page.estado().error).toContain('No fue posible crear');
    expect(page.estado().guardando).toBeFalse();
    expect(api.get).not.toHaveBeenCalled();
    api.post.and.returnValue(of({ id: 1 }));
    await page.guardar();
    expect(page.estado().error).toBe('');
    expect(page.estado().abierta).toBeFalse();
  });

  it('requiere permiso también al guardar un formulario ya abierto', async () => {
    page.abrir(); page.datos('Sesión de prueba');
    permisoCrear.set(false);
    await page.guardar();
    expect(api.post).not.toHaveBeenCalled();
    page.cancelar(); page.abrir();
    expect(page.estado().abierta).toBeFalse();
  });

  it('conserva la creación para quien tiene el permiso de administrar', async () => {
    permisoCrear.set(false); permisoAdministrar.set(true);
    page.abrir(); page.datos('Sesión de prueba');
    await page.guardar();
    expect(api.post).toHaveBeenCalledTimes(1);
    expect(page.estado().abierta).toBeFalse();
  });
});
