import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { AlertController } from '@ionic/angular/standalone';
import { of } from 'rxjs';
import { ActasPage } from './actas.page';
import { Acta, ActaAdjunto } from './actas.models';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { DashboardService } from '../../services/dashboard.service';

class ActasArchivosTestPage extends ActasPage {
  adjunto(abrir = false): void { this.descargarAdjunto({ id: '20' } as Acta, archivo, abrir); }
}

const archivo: ActaAdjunto = { id: '21', nombreOriginal: 'informe_privado.txt',
  nombreArchivo: 'archivo-interno.txt', tipoMime: 'text/plain', tamano: 10 };

describe('Actas: archivos privados', () => {
  let page: ActasArchivosTestPage;
  let api: jasmine.SpyObj<ApiService>;
  let permiso: jasmine.Spy;

  beforeEach(() => {
    api = jasmine.createSpyObj<ApiService>('ApiService', ['getBlob']);
    api.getBlob.and.returnValue(of(new Blob(['fixture sintético'])));
    permiso = jasmine.createSpy('hasPermission').and.returnValue(true);
    TestBed.configureTestingModule({ providers: [
      { provide: ApiService, useValue: api },
      { provide: AuthService, useValue: { hasPermission: permiso } },
      { provide: DashboardService, useValue: {} },
      { provide: AlertController, useValue: {} },
      { provide: ActivatedRoute, useValue: {} },
      { provide: Router, useValue: {} },
    ] });
    page = TestBed.runInInjectionContext(() => new ActasArchivosTestPage());
    spyOn(URL, 'createObjectURL').and.returnValue('blob:adjunto-autorizado');
    spyOn(URL, 'revokeObjectURL');
  });

  it('descarga por el endpoint y conserva el nombre original', () => {
    const click = spyOn(HTMLAnchorElement.prototype, 'click').and.callFake(function (this: HTMLAnchorElement) {
      expect(this.href).toBe('blob:adjunto-autorizado');
      expect(this.download).toBe('informe_privado.txt');
    });
    page.adjunto();
    expect(api.getBlob).toHaveBeenCalledWith('/api/actas/20/adjuntos/21/descargar');
    expect(click).toHaveBeenCalledTimes(1);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:adjunto-autorizado');
  });

  it('abre el blob recibido mediante la descarga autorizada', fakeAsync(() => {
    const abrir = spyOn(window, 'open');
    page.adjunto(true);
    expect(api.getBlob).toHaveBeenCalledWith('/api/actas/20/adjuntos/21/descargar');
    expect(abrir).toHaveBeenCalledWith('blob:adjunto-autorizado', '_blank', 'noopener,noreferrer');
    tick(60_000);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:adjunto-autorizado');
  }));

  it('no inicia descarga sin el permiso del módulo', () => {
    permiso.and.returnValue(false);
    page.adjunto();
    expect(api.getBlob).not.toHaveBeenCalled();
    expect(URL.createObjectURL).not.toHaveBeenCalled();
  });
});
