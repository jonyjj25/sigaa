import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, RouterOutlet } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AuthService } from '../../../services/auth.service';
import { configRoutes } from './config-routes';

@Component({ selector: 'app-config-layout-test', standalone: true, imports: [RouterOutlet], template: '<router-outlet />' })
class ConfigLayoutTestComponent {}

@Component({ selector: 'app-config-dashboard-test', standalone: true, template: 'Configuración' })
class ConfigDashboardTestComponent {}

@Component({ selector: 'app-config-profile-test', standalone: true, template: 'Perfil' })
class ConfigProfileTestComponent {}

@Component({ selector: 'app-login-test', standalone: true, template: 'Inicio de sesión' })
class LoginTestComponent {}

describe('retired parameters route compatibility', () => {
  let authenticated: boolean;
  let administrator: boolean;

  beforeEach(() => {
    authenticated = true;
    administrator = true;
    const config = configRoutes.find(route => route.path === 'config')!;

    TestBed.configureTestingModule({ providers: [
      provideRouter([
        {
          ...config,
          loadComponent: () => ConfigLayoutTestComponent,
          children: config.children!.map(route => {
            if (route.path === '') return { ...route, loadComponent: () => ConfigDashboardTestComponent };
            if (route.path === 'profile') return { ...route, loadComponent: () => ConfigProfileTestComponent };
            return route;
          }),
        },
        { path: 'home', component: LoginTestComponent },
      ]),
      { provide: AuthService, useValue: {
        isAuthenticated: () => authenticated,
        isSuperAdmin: () => administrator,
        currentUser: () => null,
      } },
    ] });
  });

  it('opens configuration from an old parameters bookmark for an administrator', async () => {
    const harness = await RouterTestingHarness.create('/config/parameters');

    expect(TestBed.inject(Router).url).toBe('/config');
    expect(harness.routeNativeElement?.textContent).toContain('Configuración');
  });

  it('requires authentication when opening an old parameters bookmark', async () => {
    authenticated = false;
    const harness = await RouterTestingHarness.create('/config/parameters');

    expect(TestBed.inject(Router).url).toBe('/home');
    expect(harness.routeNativeElement?.textContent).toContain('Inicio de sesión');
  });

  it('keeps the configuration dashboard restricted to administrators', async () => {
    administrator = false;
    const harness = await RouterTestingHarness.create('/config/parameters');

    expect(TestBed.inject(Router).url).toBe('/config/profile');
    expect(harness.routeNativeElement?.textContent).toContain('Perfil');
  });
});
