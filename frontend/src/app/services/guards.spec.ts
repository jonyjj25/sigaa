import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot } from '@angular/router';
import { AdminGuard } from './admin.guard';
import { AuthGuard } from './auth.guard';
import { AuthService } from './auth.service';
import { RoleGuard } from './role.guard';
import { PermissionGuard } from './permission.guard';

describe('route guards', () => {
  const router = { navigate: jasmine.createSpy('navigate') };
  const auth = {
    isAuthenticated: jasmine.createSpy('isAuthenticated'),
    isAdmin: jasmine.createSpy('isAdmin'),
    isSuperAdmin: jasmine.createSpy('isSuperAdmin'),
    hasRole: jasmine.createSpy('hasRole'),
    hasPermission: jasmine.createSpy('hasPermission'),
  };

  beforeEach(() => {
    router.navigate.calls.reset();
    auth.isAuthenticated.calls.reset();
    auth.isAdmin.calls.reset();
    auth.isSuperAdmin.calls.reset();
    auth.hasRole.calls.reset();
    auth.hasPermission.calls.reset();
    TestBed.configureTestingModule({
      providers: [
        { provide: Router, useValue: router },
        { provide: AuthService, useValue: auth },
      ],
    });
  });

  it('redirects unauthenticated users to home', () => {
    auth.isAuthenticated.and.returnValue(false);
    const state = { url: '/dashboard' } as RouterStateSnapshot;
    expect(TestBed.inject(AuthGuard).canActivate({} as ActivatedRouteSnapshot, state)).toBeFalse();
    expect(router.navigate).toHaveBeenCalledWith(['/home']);
  });

  it('allows the Superadmin and rejects regular users from global configuration', () => {
    auth.isAuthenticated.and.returnValue(true);
    auth.isSuperAdmin.and.returnValue(true);
    expect(TestBed.inject(AdminGuard).canActivate()).toBeTrue();

    auth.isSuperAdmin.and.returnValue(false);
    expect(TestBed.inject(AdminGuard).canActivate()).toBeFalse();
    expect(router.navigate).toHaveBeenCalledWith(['/config/profile']);
  });

  it('applies the inventory role restriction', () => {
    auth.isAuthenticated.and.returnValue(true);
    auth.hasRole.and.returnValue(false);
    const route = { routeConfig: { path: 'inventario' } } as ActivatedRouteSnapshot;
    expect(TestBed.inject(RoleGuard).canActivate(route)).toBeFalse();
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
  });

  it('allows the Superadmin to open inventory and reports', () => {
    auth.isAuthenticated.and.returnValue(true);
    auth.hasRole.and.callFake((role: string) => role === 'Superadmin');

    const inventory = { routeConfig: { path: 'inventario' } } as ActivatedRouteSnapshot;
    const reports = { routeConfig: { path: 'reporte' } } as ActivatedRouteSnapshot;

    expect(TestBed.inject(RoleGuard).canActivate(inventory)).toBeTrue();
    expect(TestBed.inject(RoleGuard).canActivate(reports)).toBeTrue();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('does not accept the retired Administrator role for operational routes', () => {
    auth.isAuthenticated.and.returnValue(true);
    auth.hasRole.and.callFake(role => role === 'Administrador');
    const guard = TestBed.inject(RoleGuard);
    expect(guard.canActivate({ routeConfig: { path: 'inventario' } } as ActivatedRouteSnapshot)).toBeFalse();
    expect(guard.canActivate({ routeConfig: { path: 'reporte' } } as ActivatedRouteSnapshot)).toBeFalse();
  });

  it('allows the QR route only with its view permission', () => {
    auth.isAuthenticated.and.returnValue(true);
    const route = { data: { permissionModule: 'Configuracion QR', permissionAction: 'Ver' } } as unknown as ActivatedRouteSnapshot;

    auth.hasPermission.and.returnValue(true);
    expect(TestBed.inject(PermissionGuard).canActivate(route)).toBeTrue();

    auth.hasPermission.and.returnValue(false);
    expect(TestBed.inject(PermissionGuard).canActivate(route)).toBeFalse();
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard'], { queryParams: { acceso: 'denegado' } });
  });
});
