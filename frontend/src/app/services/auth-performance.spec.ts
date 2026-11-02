import { TestBed, fakeAsync, tick, discardPeriodicTasks } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';

describe('Session request measurements', () => {
  it('measures simultaneous timer, visibility and consumer refreshes without caching later permissions', fakeAsync(() => {
    localStorage.clear(); sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(),
      { provide: Router, useValue: { navigate: jasmine.createSpy('navigate') } }] });
    const auth = TestBed.inject(AuthService);
    const http = TestBed.inject(HttpTestingController);
    auth.saveToken(`header.${btoa(JSON.stringify({ exp: Date.now() / 1000 + 120 }))}.signature`);
    auth.saveUserInfo({ nombre: 'Persona ficticia', correo: 'rendimiento@example.test', roles: 'Miembro', permisos: [] });
    const visible = spyOnProperty(document, 'hidden', 'get').and.returnValue(false);
    tick(10_000);
    document.dispatchEvent(new Event('visibilitychange'));
    auth.refreshCurrentUser().subscribe();
    const requests = http.match('/api/auth/me');
    console.info(`PERF_AUTH_ME_CONCURRENT_REQUESTS=${requests.length}`);
    expect(requests.length).toBe(1);
    for (const request of requests) request.flush({ nombre: 'Persona ficticia', correo: 'rendimiento@example.test', roles: 'Miembro', permisos: ['Tareas:Ver'] });
    expect(auth.hasPermission('Tareas', 'Ver')).toBeTrue();
    auth.refreshCurrentUser().subscribe();
    http.expectOne('/api/auth/me').flush({ nombre: 'Persona ficticia', correo: 'rendimiento@example.test', roles: 'Miembro', permisos: [] });
    expect(auth.hasPermission('Tareas', 'Ver')).toBeFalse();
    visible.and.callThrough(); auth.ngOnDestroy(); discardPeriodicTasks(); http.verify();
    localStorage.clear(); sessionStorage.clear();
  }));
});
