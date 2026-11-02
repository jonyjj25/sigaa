import { TestBed, fakeAsync, tick, discardPeriodicTasks } from '@angular/core/testing';
import { Router } from '@angular/router';
import { HTTP_INTERCEPTORS, HttpClient, HttpErrorResponse, provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AuthService } from './auth.service';
import { AuthErrorInterceptor } from './auth-error.interceptor';

describe('AuthService', () => {
  const router = { navigate: jasmine.createSpy('navigate') };
  let service: AuthService;
  let httpTesting: HttpTestingController;

  const token = (exp: number): string => `header.${btoa(JSON.stringify({ exp }))}.signature`;

  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    router.navigate.calls.reset();
    TestBed.configureTestingModule({ providers: [
      provideHttpClient(withInterceptorsFromDi()), provideHttpClientTesting(),
      { provide: HTTP_INTERCEPTORS, useClass: AuthErrorInterceptor, multi: true },
      { provide: Router, useValue: router },
    ] });
    service = TestBed.inject(AuthService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  it('persists a valid token and rejects an expired or malformed one', () => {
    service.saveToken(token(Math.floor(Date.now() / 1000) + 60));
    expect(service.isAuthenticated()).toBeTrue();

    service.saveToken(token(Math.floor(Date.now() / 1000) - 60));
    expect(service.isAuthenticated()).toBeFalse();

    service.saveToken('not-a-jwt');
    expect(service.isAuthenticated()).toBeFalse();
  });

  it('does not authenticate from locally stored user metadata alone', () => {
    service.saveUserInfo({ nombre: 'Ana', correo: 'ana@example.test', roles: 'Miembro' });

    expect(service.currentUser()).not.toBeNull();
    expect(service.isAuthenticated()).toBeFalse();
  });

  it('normalizes roles, permissions and available home pages', () => {
    service.saveUserInfo({
      nombre: 'Ana', correo: 'ana@example.test', roles: 'Jefe de Unidad, Miembro', permisos: ['Tareas:Crear', 'Reporte:Ver'],
    });

    expect(service.hasRole('jefe de unidad')).toBeTrue();
    expect(service.isUnitAdmin()).toBeTrue();
    expect(service.hasPermission('Tareas', 'Crear')).toBeTrue();
    expect(service.allowedHomePage('reporte')).toBe('reporte');
    expect(service.allowedHomePage('inventario')).toBe('dashboard');
  });

  afterEach(() => httpTesting.verify());

  it('sends the bearer token when logging out from the native or web session', () => {
    const currentToken = token(Math.floor(Date.now() / 1000) + 60);
    service.saveToken(currentToken);
    service.logout();
    const request = httpTesting.expectOne('/api/auth/logout');
    expect(request.request.headers.get('Authorization')).toBe(`Bearer ${currentToken}`);
    expect(request.request.withCredentials).toBeTrue();
    expect(service.getToken()).toBeNull();
    request.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('denies Reports as home page without Reporte:Ver', () => {
    service.saveUserInfo({ nombre: 'Usuario de prueba', correo: 'usuario@example.test',
      roles: 'Jefe de Unidad, Miembro', permisos: ['Tareas:Crear'] });
    expect(service.allowedHomePage('reporte')).toBe('dashboard');
  });

  it('allows Correspondencia as home page when the user can view notes or memos', () => {
    service.saveUserInfo({
      nombre: 'Ana', correo: 'ana@example.test', roles: 'Miembro', permisos: ['Memos:Ver'],
    });

    expect(service.allowedHomePage('correspondencia')).toBe('correspondencia');
  });

  it('refreshes roles and permissions for an open session', () => {
    service.saveToken(token(Math.floor(Date.now() / 1000) + 60));
    service.saveUserInfo({ nombre: 'Ana', correo: 'ana@example.test', roles: 'Miembro', permisos: [] });

    service.refreshCurrentUser().subscribe();
    httpTesting.expectOne('/api/auth/me').flush({
      id: 8, nombre: 'Ana', correo: 'ana@example.test', roles: 'Miembro', permisos: ['Grupos:Ver'],
    });

    expect(service.hasPermission('Grupos', 'Ver')).toBeTrue();
    expect(service.currentUser()?.id).toBe(8);
  });

  it('replaces stale permissions and profile fields when me returns the restricted identity', () => {
    const currentToken = token(Math.floor(Date.now() / 1000) + 60);
    service.saveToken(currentToken);
    service.saveUserInfo({ id: 8, nombre: 'Usuario', correo: 'usuario@example.test', roles: 'administrador',
      permisos: ['Documentos:Ver'], fotografiaRuta: '/uploads/fotografias/test.png', has2fa: true });
    const refreshed = jasmine.createSpy('refreshed');
    service.refreshCurrentUser().subscribe(refreshed);
    httpTesting.expectOne('/api/auth/me').flush({ nombre: 'Usuario', correo: 'usuario@example.test', cambioContrasenaRequerido: true });

    const restricted = { nombre: 'Usuario', correo: 'usuario@example.test', roles: '', permisos: [], cambioContrasenaRequerido: true };
    expect(service.currentUser()).toEqual(restricted);
    expect(refreshed).toHaveBeenCalledWith(restricted);
    expect(JSON.parse(localStorage.getItem('sigaa_user')!)).toEqual(restricted);
    expect(service.getToken()).toBe(currentToken);
    expect(service.isSuperAdmin()).toBeFalse();
    expect(router.navigate).toHaveBeenCalledWith(['/config/password'], { queryParams: { required: 'true' } });
  });

  it('does not retain functional permissions from a temporary-password login', () => {
    service.saveUserInfo({ nombre: 'Usuario', correo: 'usuario@example.test', roles: 'administrador',
      permisos: ['Documentos:Ver'], cambioContrasenaRequerido: true });
    expect(service.currentUser()?.roles).toBe('');
    expect(service.currentUser()?.permisos).toEqual([]);
    expect(service.hasPermission('Documentos', 'Ver')).toBeFalse();
  });

  it('clears user, token, preferences and stored metadata when the cookie session receives 401', () => {
    service.saveToken(token(Math.floor(Date.now() / 1000) + 60));
    service.saveUserInfo({ nombre: 'Ana', correo: 'ana@example.test', roles: 'Miembro', permisos: ['Grupos:Ver'] });
    localStorage.setItem('sigaa_preferences', '{}');
    const changed = jasmine.createSpy('sessionChanged');
    const subscription = service.sessionChanged$.subscribe(changed);
    const refreshed = jasmine.createSpy('refreshed');

    service.refreshCurrentUser().subscribe(refreshed);
    const request = httpTesting.expectOne('/api/auth/me');
    expect(request.request.withCredentials).toBeTrue();
    expect(request.request.headers.get('Authorization')).toBe(`Bearer ${service.getToken()}`);
    request.flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(refreshed).toHaveBeenCalledWith(null);
    expect(service.currentUser()).toBeNull();
    expect(service.getToken()).toBeNull();
    expect(service.isAuthenticated()).toBeFalse();
    expect(service.hasPermission('Grupos', 'Ver')).toBeFalse();
    expect(localStorage.getItem('sigaa_user')).toBeNull();
    expect(localStorage.getItem('sigaa_preferences')).toBeNull();
    expect(changed).toHaveBeenCalledTimes(1);
    expect(router.navigate).toHaveBeenCalledWith(['/home'], { queryParams: { sessionExpired: 'true' } });
    subscription.unsubscribe();
  });

  for (const status of [0, 500, 503]) {
    it(`preserves the session after a temporary ${status} error and recovers on the next refresh`, () => {
      const currentToken = token(Math.floor(Date.now() / 1000) + 60);
      const user = { nombre: 'Ana', correo: 'ana@example.test', roles: 'Miembro', permisos: [] };
      service.saveToken(currentToken);
      service.saveUserInfo(user);
      const failed = jasmine.createSpy('failed');

      service.refreshCurrentUser().subscribe({ error: failed });
      const request = httpTesting.expectOne('/api/auth/me');
      if (status === 0) request.error(new ProgressEvent('error'));
      else request.flush({}, { status, statusText: 'Temporary error' });

      expect(failed).toHaveBeenCalledWith(jasmine.any(HttpErrorResponse));
      expect(service.currentUser()).toEqual(user);
      expect(service.getToken()).toBe(currentToken);
      expect(localStorage.getItem('sigaa_user')).not.toBeNull();
      expect(router.navigate).not.toHaveBeenCalled();

      service.refreshCurrentUser().subscribe();
      httpTesting.expectOne('/api/auth/me').flush({ ...user, permisos: ['Grupos:Ver'] });
      expect(service.hasPermission('Grupos', 'Ver')).toBeTrue();
    });
  }

  it('does not restore stale user data from a response arriving after a rejected session', () => {
    service.saveToken(token(Math.floor(Date.now() / 1000) + 60));
    service.saveUserInfo({ nombre: 'Ana', correo: 'ana@example.test', roles: 'Miembro' });
    service.refreshCurrentUser().subscribe();
    // Otro endpoint puede rechazar la sesión mientras /me sigue en curso.
    TestBed.inject(HttpClient).get('/api/notificaciones/mias', { withCredentials: true }).subscribe({ error: () => undefined });
    httpTesting.expectOne('/api/notificaciones/mias').flush({}, { status: 401, statusText: 'Unauthorized' });
    httpTesting.expectOne('/api/auth/me').flush({ nombre: 'Ana antigua', correo: 'ana@example.test', roles: 'Miembro' });

    expect(service.currentUser()).toBeNull();
    expect(localStorage.getItem('sigaa_user')).toBeNull();
    expect(service.getToken()).toBeNull();
  });

  it('does not overwrite a new login with an older refresh response', () => {
    service.saveToken(token(Math.floor(Date.now() / 1000) + 60));
    service.saveUserInfo({ nombre: 'Ana', correo: 'ana@example.test', roles: 'Miembro' });
    service.refreshCurrentUser().subscribe();
    const request = httpTesting.expectOne('/api/auth/me');
    const newUser = { nombre: 'Luis', correo: 'luis@example.test', roles: 'Miembro' };
    service.saveToken(token(Math.floor(Date.now() / 1000) + 120));
    service.saveUserInfo(newUser);
    request.flush({ nombre: 'Ana', correo: 'ana@example.test', roles: 'Miembro' });

    expect(service.currentUser()).toEqual(newUser);
  });

  it('shares a rejected refresh and clears both consumers without retaining a completed result', () => {
    service.saveToken(token(Math.floor(Date.now() / 1000) + 60));
    service.saveUserInfo({ nombre: 'Persona ficticia', correo: 'sesion@example.test', roles: 'Miembro' });
    const first = jasmine.createSpy('first'); const second = jasmine.createSpy('second');
    service.refreshCurrentUser().subscribe(first); service.refreshCurrentUser().subscribe(second);
    httpTesting.expectOne('/api/auth/me').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(first).toHaveBeenCalledOnceWith(null); expect(second).toHaveBeenCalledOnceWith(null);
    expect(service.currentUser()).toBeNull(); expect(service.getToken()).toBeNull();
  });

  it('shares an authenticated refresh and requests fresh permissions after completion', () => {
    const currentToken = token(Math.floor(Date.now() / 1000) + 60);
    service.saveToken(currentToken);
    const user = { nombre: 'Persona ficticia', correo: 'sesion@example.test', roles: 'Miembro', permisos: ['Grupos:Ver'] };
    const first = jasmine.createSpy('first'); const second = jasmine.createSpy('second');
    service.refreshCurrentUser().subscribe(first); service.refreshCurrentUser().subscribe(second);
    const request = httpTesting.expectOne('/api/auth/me');
    expect(request.request.headers.get('Authorization')).toBe(`Bearer ${currentToken}`);
    expect(request.request.withCredentials).toBeTrue();
    request.flush(user);
    expect(first).toHaveBeenCalledOnceWith(user); expect(second).toHaveBeenCalledOnceWith(user);
    service.refreshCurrentUser().subscribe();
    httpTesting.expectOne('/api/auth/me').flush({ ...user, permisos: [] });
    expect(service.hasPermission('Grupos', 'Ver')).toBeFalse();
  });

  it('releases a canceled shared refresh so the next consumer can recover', () => {
    service.saveToken(token(Math.floor(Date.now() / 1000) + 60));
    const subscription = service.refreshCurrentUser().subscribe();
    const pending = httpTesting.expectOne('/api/auth/me'); subscription.unsubscribe();
    expect(pending.cancelled).toBeTrue();
    service.refreshCurrentUser().subscribe();
    httpTesting.expectOne('/api/auth/me').flush({ nombre: 'Persona ficticia', correo: 'sesion@example.test', roles: 'Miembro' });
    expect(service.currentUser()?.correo).toBe('sesion@example.test');
  });

  it('times out a stalled refresh without clearing the user and permits a fresh retry', fakeAsync(() => {
    spyOn(console, 'warn');
    service.saveToken(token(Math.floor(Date.now() / 1000) + 120));
    const user = { nombre: 'Persona ficticia', correo: 'sesion@example.test', roles: 'Miembro' };
    service.saveUserInfo(user);
    const failed = jasmine.createSpy('failed'); service.refreshCurrentUser().subscribe({ error: failed });
    const pending = httpTesting.expectOne('/api/auth/me'); tick(15_001);
    expect(pending.cancelled).toBeTrue(); expect(failed).toHaveBeenCalledTimes(1);
    expect(service.currentUser()).toEqual(user); expect(service.isAuthenticated()).toBeTrue();
    service.refreshCurrentUser().subscribe();
    httpTesting.expectOne('/api/auth/me').flush({ ...user, permisos: ['Tareas:Ver'] });
    expect(service.hasPermission('Tareas', 'Ver')).toBeTrue();
    service.ngOnDestroy(); discardPeriodicTasks();
  }));

  it('clears persisted session data and returns to home on logout', () => {
    service.saveToken(token(Math.floor(Date.now() / 1000) + 60));
    service.saveUserInfo({ nombre: 'Ana', correo: 'ana@example.test', roles: 'Miembro' });

    service.logout();
    httpTesting.expectOne('/api/auth/logout').flush({});

    expect(service.getToken()).toBeNull();
    expect(service.currentUser()).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith(['/home']);
  });

  it('clears the local session even when the logout request fails', () => {
    service.saveToken(token(Math.floor(Date.now() / 1000) + 60));
    service.saveUserInfo({ nombre: 'Ana', correo: 'ana@example.test', roles: 'Miembro' });

    service.logout();
    httpTesting.expectOne('/api/auth/logout').flush('error', { status: 500, statusText: 'Error' });

    expect(service.getToken()).toBeNull();
    expect(service.currentUser()).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith(['/home']);
  });

  it('handles admin permissions, profile updates, 2FA and authorization headers', () => {
    service.saveToken(token(Math.floor(Date.now() / 1000) + 60));
    service.saveUserInfo({
      nombre: 'Superadmin', correo: 'admin@example.test', roles: 'Superadmin',
      permisos: [], has2fa: true,
    });

    expect(service.isAdmin()).toBeTrue();
    expect(service.isSuperAdmin()).toBeTrue();
    expect(service.hasPermission('Inventario', 'Eliminar')).toBeTrue();
    expect(service.has2fa()).toBeTrue();
    expect(service.getAuthHeaders()['Authorization']).toContain('Bearer ');
    expect(service.allowedHomePage('/inventario')).toBe('inventario');

    service.updatePhoto('/uploads/admin.png');
    expect(service.currentUser()?.fotografiaRuta).toBe('/uploads/admin.png');
  });

  it('separates technical administration, operational direction and scoped subdirection', () => {
    for (const roles of ['Administrador', 'administrador']) {
      service.saveUserInfo({ nombre: 'Prueba', correo: 'prueba@example.test', roles, permisos: [] });
      expect(service.isSuperAdmin()).toBeFalse();
      expect(service.isAdmin()).toBeFalse();
      expect(service.hasPermission('Configuración', 'Gestionar roles')).toBeFalse();
    }
    service.saveUserInfo({ nombre: 'Prueba', correo: 'prueba@example.test', roles: 'Directora', permisos: ['Actas:Editar'] });
    expect(service.isAdmin()).toBeTrue();
    expect(service.isSuperAdmin()).toBeFalse();
    expect(service.hasPermission('Actas', 'Editar')).toBeTrue();
    expect(service.hasPermission('Configuración', 'Gestionar roles')).toBeFalse();
    service.saveUserInfo({ nombre: 'Prueba', correo: 'prueba@example.test', roles: 'Subdirectora', permisos: ['Actas:Editar'] });
    expect(service.isAdmin()).toBeFalse();
    expect(service.isSuperAdmin()).toBeFalse();
    expect(service.isInstitutionalManager()).toBeTrue();
  });

  it('applies and persists regional, visual and navigation preferences', () => {
    service.saveUserInfo({ nombre: 'Ana', correo: 'ana@example.test', roles: 'Miembro' });
    service.updatePreferences({
      theme: 'dark', language: 'en', timezone: 'UTC', dateFormat: 'YYYY-MM-DD',
      timeFormat: 'hh:mm A', homePage: 'documentos', recordsPerPage: 50,
      sidebarExpanded: false, animationsEnabled: false, emailNotifications: true,
      pushNotifications: false, soundEnabled: false, confirmationsEnabled: true,
      fontSize: 'large', highContrast: true,
    });

    expect(service.effectiveTheme()).toBe('dark');
    expect(document.documentElement.lang).toBe('en');
    expect(document.documentElement.getAttribute('data-high-contrast')).toBe('true');
    expect(document.documentElement.getAttribute('data-no-animations')).toBe('true');
    expect(document.documentElement.style.fontSize).toBe('16px');
    expect(service.formatDate('2026-08-24T15:00:00Z')).toContain('2026');
    expect(service.formatTime('2026-08-24T15:00:00Z')).toBeTruthy();
    expect(JSON.parse(localStorage.getItem('sigaa_preferences') || '{}').homePage).toBe('documentos');
  });

  it('recovers safely from malformed stored user and preference values', () => {
    localStorage.setItem('sigaa_user', '{invalid');
    localStorage.setItem('sigaa_preferences', '{invalid');

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(), provideHttpClientTesting(), { provide: Router, useValue: router },
      ]
    });
    const recovered = TestBed.inject(AuthService);

    expect(recovered.currentUser()).toBeNull();
    expect(localStorage.getItem('sigaa_user')).toBeNull();
    expect(localStorage.getItem('sigaa_preferences')).toBeNull();
  });
});
