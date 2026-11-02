import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { AsistenciaPublicaPage } from './asistencia-publica.page';
import { AsistenciaService } from '../../services/asistencia.service';
import { AuthService } from '../../services/auth.service';

class AsistenciaPublicaTestPage extends AsistenciaPublicaPage {
  registrarPropio(): void { this.registrarUsuario(); }
  invitado(): void {
    this.formData.nombre = 'Invitado ficticio'; this.formData.correo = 'invitado@example.test';
    this.formData.aceptaTerminos = true; this.registrarInvitado();
  }
  nombreSesion(): string { return this.sessionName(); }
  completado(): boolean { return this.registrado(); }
}

describe('Asistencia pública: registro sin consultar datos de terceros', () => {
  const codigo = 'SESION-PRUEBA';
  const pendiente = 'sigaa_pending_attendance_code';
  let page: AsistenciaPublicaTestPage;
  let service: jasmine.SpyObj<AsistenciaService>;
  let auth: jasmine.Spy;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    sessionStorage.removeItem(pendiente);
    service = jasmine.createSpyObj<AsistenciaService>('AsistenciaService', [
      'getSesionPorCodigo', 'registrarInvitado', 'escanearQr', 'getAsistentesPorSesion', 'getHistorial',
    ]);
    service.getSesionPorCodigo.and.returnValue(of({ nombre: 'Sesión ficticia', fecha: '2026-10-08', horaInicio: '10:00' }));
    service.registrarInvitado.and.returnValue(of({ success: true, message: 'Asistencia registrada correctamente' }));
    service.escanearQr.and.returnValue(of({ success: true, message: 'Asistencia registrada correctamente' }));
    auth = jasmine.createSpy('isAuthenticated').and.returnValue(false);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    TestBed.configureTestingModule({ providers: [
      { provide: AsistenciaService, useValue: service },
      { provide: AuthService, useValue: { isAuthenticated: auth, currentUser: () => ({ id: 7 }) } },
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => codigo } } } },
      { provide: Router, useValue: router },
    ] });
    page = TestBed.runInInjectionContext(() => new AsistenciaPublicaTestPage());
  });

  afterEach(() => sessionStorage.removeItem(pendiente));

  it('carga solo la información pública mínima de la sesión', () => {
    page.ngOnInit();
    expect(service.getSesionPorCodigo).toHaveBeenCalledOnceWith(codigo);
    expect(page.nombreSesion()).toBe('Sesión ficticia');
    expect(service.getAsistentesPorSesion).not.toHaveBeenCalled();
    expect(service.getHistorial).not.toHaveBeenCalled();
  });

  it('conserva el registro público de invitados', () => {
    page.ngOnInit(); page.invitado();
    expect(service.registrarInvitado).toHaveBeenCalledWith(jasmine.objectContaining({
      codigoSesion: codigo, nombre: 'Invitado ficticio', correo: 'invitado@example.test',
    }));
    expect(page.completado()).toBeTrue();
    expect(service.getAsistentesPorSesion).not.toHaveBeenCalled();
  });

  it('registra al usuario autenticado usando su propia identidad', () => {
    auth.and.returnValue(true);
    page.ngOnInit(); page.registrarPropio();
    expect(service.escanearQr).toHaveBeenCalledOnceWith(7, codigo);
    expect(page.completado()).toBeTrue();
    expect(service.getAsistentesPorSesion).not.toHaveBeenCalled();
  });

  it('requiere iniciar sesión para registrar al usuario interno', () => {
    page.ngOnInit(); page.registrarPropio();
    expect(service.escanearQr).not.toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/home'], { queryParams: { returnUrl: `/asistencia/publica/${codigo}` } });
    expect(sessionStorage.getItem(pendiente)).toBe(codigo);
  });

  it('recupera el registro pendiente después del inicio de sesión', () => {
    auth.and.returnValue(true);
    sessionStorage.setItem(pendiente, codigo);
    page.ngOnInit();
    expect(service.escanearQr).toHaveBeenCalledOnceWith(7, codigo);
    expect(sessionStorage.getItem(pendiente)).toBeNull();
  });
});
