import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { AlertController } from '@ionic/angular/standalone';
import { EMPTY } from 'rxjs';
import { ActaFormPage } from './acta-form.page';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { DashboardService } from '../../services/dashboard.service';

class ActaFormTestPage extends ActaFormPage {
  preparar(id: string | null = null): void {
    this.formTitulo.set('Acta de prueba');
    this.formTipo.set('Ordinaria');
    this.formFecha.set('2026-01-05');
    this.editandoId.set(id);
  }
  borrador(): void { this.guardarBorrador(); }
  terminar(): void { this.guardar(); }
  puedeGuardarActa(): boolean { return this.puedeGuardar(); }
  prepararFechas(): void {
    this.acuerdos.set([{ descripcion: 'Acuerdo de prueba', responsable: 'Responsable de prueba', estado: 'Pendiente', fechaLimite: '2099-01-10' }]);
    this.compromisos.set([{ actividad: 'Compromiso de prueba', responsable: 'Responsable de prueba', estado: 'Pendiente', fechaLimite: '2099-02-10' }]);
  }
}

describe('ActaFormPage: estado al guardar', () => {
  let page: ActaFormTestPage;
  let api: jasmine.SpyObj<ApiService>;
  let permiso: jasmine.Spy;
  let queryParams: Record<string, string>;
  beforeEach(() => {
    api = jasmine.createSpyObj('ApiService', ['post', 'put']);
    api.post.and.returnValue(EMPTY);
    api.put.and.returnValue(EMPTY);
    permiso = jasmine.createSpy('hasPermission').and.returnValue(true);
    queryParams = {};
    TestBed.configureTestingModule({ providers: [
      { provide: ApiService, useValue: api },
      { provide: AuthService, useValue: { currentUser: () => null, hasPermission: permiso } },
      { provide: DashboardService, useValue: {} }, { provide: Router, useValue: {} },
      { provide: ActivatedRoute, useValue: { snapshot: { queryParams } } }, { provide: AlertController, useValue: {} },
    ] });
    page = TestBed.runInInjectionContext(() => new ActaFormTestPage());
  });
  it('conserva las fechas límite existentes al guardar acuerdos y compromisos', () => {
    page.preparar('7'); page.prepararFechas(); page.borrador();
    const body = api.put.calls.mostRecent().args[1] as { acuerdosRel: { fechaLimite: string }[] };
    expect(body.acuerdosRel.map(item => item.fechaLimite)).toEqual(['2099-01-10', '2099-02-10']);
  });
  for (const id of [null, '7']) {
    for (const estado of ['Borrador', 'Terminada'] as const) {
      it(`sends ${estado} when ${id ? 'updating' : 'creating'} an acta`, () => {
        page.preparar(id);
        if (estado === 'Borrador') page.borrador(); else page.terminar();
        const request = (id ? api.put : api.post).calls.mostRecent();
        expect(request.args[0]).toBe(id ? '/api/actas/7' : '/api/actas');
        expect((request.args[1] as { estado: string }).estado).toBe(estado);
      });
    }
  }

  it('permite actualizar con Editar aunque no tenga Crear', () => {
    permiso.and.callFake((modulo: string, accion: string) => modulo === 'Actas' && accion === 'Editar');
    page.preparar('7');
    expect(page.puedeGuardarActa()).toBeTrue();
    page.terminar();
    expect(api.put).toHaveBeenCalled();
    expect(api.post).not.toHaveBeenCalled();
  });

  it('impide actualizar cuando solo tiene Crear', () => {
    permiso.and.callFake((modulo: string, accion: string) => modulo === 'Actas' && accion === 'Crear');
    page.preparar('7');
    expect(page.puedeGuardarActa()).toBeFalse();
    page.borrador();
    expect(api.put).not.toHaveBeenCalled();
    expect(api.post).not.toHaveBeenCalled();
  });

  it('conserva la creación desde Reuniones con Generar acta', () => {
    permiso.and.callFake((modulo: string, accion: string) => modulo === 'Reuniones' && accion === 'Generar acta');
    page.preparar();
    page.borrador();
    expect(api.post).toHaveBeenCalledWith('/api/actas', jasmine.objectContaining({ estado: 'Borrador' }));
  });

  it('Editar no concede permiso para crear actas', () => {
    permiso.and.callFake((modulo: string, accion: string) => modulo === 'Actas' && accion === 'Editar');
    page.preparar();
    page.terminar();
    expect(api.post).not.toHaveBeenCalled();
  });

  it('no convierte en creación una edición cuyo recurso no ha podido cargarse', () => {
    queryParams['id'] = '7';
    page.preparar();
    expect(page.puedeGuardarActa()).toBeFalse();
    page.terminar();
    expect(api.post).not.toHaveBeenCalled();
    expect(api.put).not.toHaveBeenCalled();
  });
});
