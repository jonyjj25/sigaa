import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { HTTP_INTERCEPTORS, HttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { AuthErrorInterceptor } from './auth-error.interceptor';
import { AuthService } from './auth.service';

describe('AuthErrorInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let auth: AuthService;
  let router: jasmine.SpyObj<Router>;

  const token = (suffix: string): string => {
    const payload = btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600, suffix }));
    return `header.${payload}.signature`;
  };

  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: HTTP_INTERCEPTORS, useClass: AuthErrorInterceptor, multi: true },
        { provide: Router, useValue: router },
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => httpMock.verify());

  it('does not erase a new login because an older unauthenticated request returns 401', () => {
    http.get('/api/notificaciones/mias').subscribe({ error: () => undefined });
    const request = httpMock.expectOne('/api/notificaciones/mias');

    const newToken = token('new-session');
    auth.saveToken(newToken);
    request.flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(auth.getToken()).toBe(newToken);
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('closes the session when the backend rejects the token currently in use', () => {
    const currentToken = token('current-session');
    auth.saveToken(currentToken);
    http.get('/api/protected', {
      headers: { Authorization: `Bearer ${currentToken}` },
    }).subscribe({ error: () => undefined });

    httpMock.expectOne('/api/protected').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(auth.getToken()).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith(['/home'], { queryParams: { sessionExpired: 'true' } });
  });

  it('invalidates a cookie request even when no Bearer token is attached', () => {
    auth.saveUserInfo({ nombre: 'Ana', correo: 'ana@example.test', roles: 'Miembro' });
    http.get('/api/auth/me', { withCredentials: true }).subscribe({ error: () => undefined });
    httpMock.expectOne('/api/auth/me').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(auth.currentUser()).toBeNull();
    expect(localStorage.getItem('sigaa_user')).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith(['/home'], { queryParams: { sessionExpired: 'true' } });
  });

  it('does not invalidate a new session when an old cookie request is rejected', () => {
    auth.saveToken(token('old-session'));
    auth.saveUserInfo({ nombre: 'Ana', correo: 'ana@example.test', roles: 'Miembro' });
    http.get('/api/auth/me', { withCredentials: true }).subscribe({ error: () => undefined });
    const request = httpMock.expectOne('/api/auth/me');
    const newToken = token('new-session');
    auth.saveToken(newToken);
    auth.saveUserInfo({ nombre: 'Luis', correo: 'luis@example.test', roles: 'Miembro' });
    request.flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(auth.getToken()).toBe(newToken);
    expect(auth.currentUser()?.nombre).toBe('Luis');
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('does not invalidate a new session when an old Bearer request is rejected', () => {
    const oldToken = token('old-session');
    auth.saveToken(oldToken);
    http.get('/api/protected', { headers: { Authorization: `Bearer ${oldToken}` } }).subscribe({ error: () => undefined });
    const request = httpMock.expectOne('/api/protected');
    const newToken = token('new-session');
    auth.saveToken(newToken);
    request.flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.getToken()).toBe(newToken);
    expect(router.navigate).not.toHaveBeenCalled();
  });

  for (const cookie of [false, true]) {
    it(`redirects a required password change without closing the ${cookie ? 'cookie' : 'Bearer'} session`, () => {
      const currentToken = token('restricted-session');
      auth.saveToken(currentToken);
      auth.saveUserInfo({ nombre: 'Usuario', correo: 'usuario@example.test', roles: 'administrador', permisos: ['Documentos:Ver'] });
      http.get('/api/documentos', cookie ? { withCredentials: true } : {
        headers: { Authorization: `Bearer ${currentToken}` },
      }).subscribe({ error: () => undefined });
      httpMock.expectOne('/api/documentos').flush({ code: 'PASSWORD_CHANGE_REQUIRED' }, { status: 403, statusText: 'Forbidden' });

      expect(auth.getToken()).toBe(currentToken);
      expect(auth.currentUser()?.cambioContrasenaRequerido).toBeTrue();
      expect(auth.currentUser()?.roles).toBe('');
      expect(auth.currentUser()?.permisos).toEqual([]);
      expect(auth.hasPermission('Documentos', 'Ver')).toBeFalse();
      expect(router.navigate).toHaveBeenCalledWith(['/config/password'], { queryParams: { required: 'true' } });
    });
  }

  it('does not restrict a newer login because an older request required a password change', () => {
    auth.saveToken(token('old-session'));
    auth.saveUserInfo({ nombre: 'Usuario', correo: 'usuario@example.test', roles: 'Miembro' });
    http.get('/api/documentos', { withCredentials: true }).subscribe({ error: () => undefined });
    const request = httpMock.expectOne('/api/documentos');
    const newToken = token('new-session');
    auth.saveToken(newToken);
    auth.saveUserInfo({ nombre: 'Otro', correo: 'otro@example.test', roles: 'Miembro' });
    request.flush({ code: 'PASSWORD_CHANGE_REQUIRED' }, { status: 403, statusText: 'Forbidden' });

    expect(auth.getToken()).toBe(newToken);
    expect(auth.currentUser()?.cambioContrasenaRequerido).toBeUndefined();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('preserves a normal session and navigation on an ordinary permission denial', () => {
    auth.saveToken(token('normal-session'));
    const user = { nombre: 'Usuario', correo: 'usuario@example.test', roles: 'Miembro' };
    auth.saveUserInfo(user);
    http.get('/api/documentos', { withCredentials: true }).subscribe({ error: () => undefined });
    httpMock.expectOne('/api/documentos').flush({ error: 'Forbidden' }, { status: 403, statusText: 'Forbidden' });
    expect(auth.currentUser()).toEqual(user);
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('clears an expired temporary session through the existing 401 handling', () => {
    auth.saveToken(token('expired-temporary-session'));
    auth.saveUserInfo({ nombre: 'Usuario', correo: 'usuario@example.test', roles: '', cambioContrasenaRequerido: true });
    http.get('/api/auth/me', { withCredentials: true }).subscribe({ error: () => undefined });
    httpMock.expectOne('/api/auth/me').flush({ code: 'TEMPORARY_PASSWORD_EXPIRED' }, { status: 401, statusText: 'Unauthorized' });
    expect(auth.currentUser()).toBeNull();
    expect(auth.getToken()).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith(['/home'], { queryParams: { sessionExpired: 'true' } });
  });

  it('preserves the current session when login or second-factor credentials are rejected', () => {
    const currentToken = token('current-session');
    auth.saveToken(currentToken);
    for (const url of ['/api/auth/login', '/api/auth/2fa/authenticate']) {
      http.post(url, {}, { withCredentials: true }).subscribe({ error: () => undefined });
      httpMock.expectOne(url).flush({}, { status: 401, statusText: 'Unauthorized' });
    }
    expect(auth.getToken()).toBe(currentToken);
    expect(router.navigate).not.toHaveBeenCalled();
  });
});
