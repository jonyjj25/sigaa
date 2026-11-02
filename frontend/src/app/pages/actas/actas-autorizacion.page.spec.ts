import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { AlertController } from '@ionic/angular/standalone';
import { EMPTY, of } from 'rxjs';
import { ActasPage } from './actas.page';
import { Acta } from './actas.models';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { DashboardService } from '../../services/dashboard.service';

const acta = { id: '20', estado: 'Borrador' } as Acta;

class ActasAutorizacionTestPage extends ActasPage {
  preparar(): void { this.actas.set([acta]); this.selectedActa.set(acta); this.nuevoEstado.set('Firmada'); }
  editar(): void { this.openEditModal(acta); }
  abrirEstado(): void { this.openEstadoModal(acta); }
  cambiarEstado(): void { this.guardarCambioEstado(); }
  eliminar(): Promise<void> { return this.eliminarActa(acta.id); }
  modalEstadoAbierto(): boolean { return this.showEstadoModal(); }
  actasRestantes(): Acta[] { return this.actas(); }
}

describe('Actas: permisos funcionales de modificación', () => {
  let page: ActasAutorizacionTestPage;
  let api: jasmine.SpyObj<ApiService>;
  let permiso: jasmine.Spy;
  let router: jasmine.SpyObj<Router>;
  let alertas: jasmine.SpyObj<AlertController>;

  beforeEach(() => {
    api = jasmine.createSpyObj<ApiService>('ApiService', ['put', 'delete']);
    api.put.and.returnValue(EMPTY);
    api.delete.and.returnValue(of(undefined));
    permiso = jasmine.createSpy('hasPermission').and.returnValue(false);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    alertas = jasmine.createSpyObj<AlertController>('AlertController', ['create']);
    alertas.create.and.resolveTo({ present: jasmine.createSpy().and.resolveTo() } as unknown as HTMLIonAlertElement);
    TestBed.configureTestingModule({ providers: [
      { provide: ApiService, useValue: api },
      { provide: AuthService, useValue: { hasPermission: permiso } },
      { provide: DashboardService, useValue: {} },
      { provide: AlertController, useValue: alertas },
      { provide: ActivatedRoute, useValue: {} },
      { provide: Router, useValue: router },
    ] });
    page = TestBed.runInInjectionContext(() => new ActasAutorizacionTestPage());
    page.preparar();
  });

  function conceder(accionPermitida: string): void {
    permiso.and.callFake((modulo: string, accion: string) => modulo === 'Actas' && accion === accionPermitida);
  }

  it('solo Crear no permite editar ni cambiar estado', () => {
    conceder('Crear');
    page.editar(); page.abrirEstado(); page.cambiarEstado();
    expect(router.navigate).not.toHaveBeenCalled();
    expect(page.modalEstadoAbierto()).toBeFalse();
    expect(api.put).not.toHaveBeenCalled();
  });

  it('Editar permite abrir el formulario existente', () => {
    conceder('Editar');
    page.editar();
    expect(router.navigate).toHaveBeenCalledWith(['/actas/nueva'], { queryParams: { id: '20' } });
  });

  it('Editar permite cambiar estado mediante la actualización protegida', () => {
    conceder('Editar');
    page.abrirEstado();
    expect(page.modalEstadoAbierto()).toBeTrue();
    page.cambiarEstado();
    expect(api.put).toHaveBeenCalledWith('/api/actas/20', { estado: 'Borrador' });
  });

  it('Editar no permite eliminar', async () => {
    conceder('Editar');
    await page.eliminar();
    expect(alertas.create).not.toHaveBeenCalled();
    expect(api.delete).not.toHaveBeenCalled();
  });

  it('Eliminar permite confirmar y enviar la eliminación al endpoint', async () => {
    conceder('Eliminar');
    await page.eliminar();
    const botones = alertas.create.calls.mostRecent().args[0]!.buttons!;
    const boton = botones[1];
    if (typeof boton === 'string') throw new Error('Falta el botón de confirmación');
    boton.handler!(undefined);
    expect(api.delete).toHaveBeenCalledWith('/api/actas/20');
    expect(page.actasRestantes()).toEqual([]);
  });

  it('no envía eliminación si pierde el permiso antes de confirmar', async () => {
    conceder('Eliminar');
    await page.eliminar();
    permiso.and.returnValue(false);
    const botones = alertas.create.calls.mostRecent().args[0]!.buttons!;
    const boton = botones[1];
    if (typeof boton === 'string') throw new Error('Falta el botón de confirmación');
    boton.handler!(undefined);
    expect(api.delete).not.toHaveBeenCalled();
    expect(page.actasRestantes()).toEqual([acta]);
  });
});
