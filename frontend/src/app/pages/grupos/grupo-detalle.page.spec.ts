import { TestBed, fakeAsync, tick, discardPeriodicTasks } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { GrupoDetallePage } from './grupo-detalle.page';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { DashboardService } from '../../services/dashboard.service';

class GrupoDetalleTestPage extends GrupoDetallePage {
  estadoChat() { return { mensajes: this.mensajes(), error: this.chatLoadError() }; }
}

describe('GrupoDetallePage: recuperación del chat', () => {
  it('keeps polling after a temporary error and still responds to route changes', fakeAsync(() => {
    const route = new BehaviorSubject(convertToParamMap({ id: '7' }));
    const api = jasmine.createSpyObj<ApiService>('ApiService', ['get']);
    let chatRequests = 0;
    api.get.and.callFake(<T>(url: string) => {
      if (url.includes('/mensajes?')) {
        chatRequests++;
        return chatRequests === 1 ? throwError(() => ({ status: 503 })) : of({ content: [
          { id: chatRequests, autorId: 1, autorNombre: 'Usuario de prueba', contenido: 'Prueba', creadoEn: '' },
        ] } as T);
      }
      return of([] as unknown as T);
    });
    TestBed.configureTestingModule({ providers: [
      { provide: ApiService, useValue: api }, { provide: AuthService, useValue: { currentUser: () => null } },
      { provide: DashboardService, useValue: { getNotificaciones: () => of([]) } },
      { provide: ActivatedRoute, useValue: { paramMap: route } }, { provide: Router, useValue: {} },
    ] });
    const page = TestBed.runInInjectionContext(() => new GrupoDetalleTestPage());
    page.ngOnInit();
    expect(page.estadoChat().error).toContain('No se pudieron actualizar');
    tick(10000);
    expect(page.estadoChat().error).toBe('');
    expect(page.estadoChat().mensajes.length).toBe(1);
    route.next(convertToParamMap({ id: '9' }));
    expect(api.get).toHaveBeenCalledWith('/api/grupos/9/mensajes?tamano=50');
    expect(chatRequests).toBe(3);
    tick();
    discardPeriodicTasks();
  }));
});
