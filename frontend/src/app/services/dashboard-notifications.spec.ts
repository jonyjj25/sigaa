import { signal } from '@angular/core';
import { TestBed, fakeAsync, tick, discardPeriodicTasks } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Subject, Subscription } from 'rxjs';
import { AuthService } from './auth.service';
import { DashboardService } from './dashboard.service';
import { ApiService } from './api.service';
import { DashboardTopbarComponent } from '../components/dashboard-topbar/dashboard-topbar.component';
import { provideRouter } from '@angular/router';
import { DEFAULT_PREFERENCES, UserInfo } from '../models/dashboard.models';

describe('Shared dashboard notifications', () => {
  let service: DashboardService;
  let http: HttpTestingController;
  let session: string | null;
  let changed: Subject<void>;
  let user: ReturnType<typeof signal<UserInfo | null>>;
  const subscriptions: Subscription[] = [];
  const notification = { id: '1', title: 'Prueba', message: 'Registro ficticio', type: 'info', timestamp: '2026-10-12T10:00:00', read: false };

  beforeEach(() => {
    session = 'TEST_ONLY_SESSION'; changed = new Subject<void>();
    user = signal<UserInfo | null>({ id: 1, nombre: 'Usuario', correo: 'usuario@example.test', roles: 'Miembro' });
    TestBed.configureTestingModule({
      imports: [DashboardTopbarComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: AuthService, useValue: { getToken: () => session, isAuthenticated: () => !!session, currentUser: user,
          hasRole: () => false, isAdmin: () => false, isSuperAdmin: () => false, hasPermission: () => false, sessionChanged$: changed,
          preferences: signal({ ...DEFAULT_PREFERENCES, pushNotifications: false }), effectiveTheme: signal('light') } }],
    }).overrideComponent(DashboardTopbarComponent, { set: { template: '' } });
    service = TestBed.inject(DashboardService); http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/config').flush({});
    http.expectOne('/api/modulos/menu').flush([]);
  });

  afterEach(() => { subscriptions.splice(0).forEach(subscription => subscription.unsubscribe()); http.verify(); });
  function notificationTest(test: () => void): () => void {
    return fakeAsync(() => {
      try { test(); } finally { subscriptions.splice(0).forEach(subscription => subscription.unsubscribe()); discardPeriodicTasks(); }
    }, { flush: false });
  }
  const watch = (service: DashboardService, subscriptions: Subscription[]) => subscriptions.push(service.notificaciones$.subscribe());

  it('shares one initial request and one timer between the dashboard and multiple topbars', notificationTest(() => {
    watch(service, subscriptions); // Consumidor del panel.
    const first = TestBed.createComponent(DashboardTopbarComponent);
    const second = TestBed.createComponent(DashboardTopbarComponent);
    first.detectChanges(); second.detectChanges(); tick(0);
    http.expectOne('/api/notificaciones/mias').flush([notification]);
    expect(service.notificaciones().length).toBe(1);
    const topbarState = first.componentInstance as unknown as { liveNotifications: () => unknown; unreadCount: number };
    expect(topbarState.liveNotifications()).toBe(service.notificaciones());
    expect(topbarState.unreadCount).toBe(1);
    tick(10_000); http.expectOne('/api/notificaciones/mias').flush([]);
    tick(10_000); http.expectOne('/api/notificaciones/mias').flush([]);
    first.destroy(); second.destroy(); subscriptions.pop()!.unsubscribe();
    tick(10_000); http.expectNone('/api/notificaciones/mias');
  }));

  it('deduplicates one-shot callers with the shared consumer and reuses the current snapshot', notificationTest(() => {
    service.getNotificaciones().subscribe(); service.getNotificaciones().subscribe(); watch(service, subscriptions);
    tick(0); http.expectOne('/api/notificaciones/mias').flush([notification]);
    service.getNotificaciones().subscribe(items => expect(items.length).toBe(1));
    http.expectNone('/api/notificaciones/mias');
  }));

  it('does not overlap a slow request with timer or manual refresh events', notificationTest(() => {
    watch(service, subscriptions); tick(0);
    const request = http.expectOne('/api/notificaciones/mias');
    tick(20_000); service.refreshNotificaciones();
    http.expectNone('/api/notificaciones/mias');
    request.flush([notification]);
    expect(service.notificaciones().length).toBe(1);
  }));

  it('preserves the previous list on temporary failure and recovers in the same cycle', notificationTest(() => {
    watch(service, subscriptions); tick(0); http.expectOne('/api/notificaciones/mias').flush([notification]);
    tick(10_000); http.expectOne('/api/notificaciones/mias').flush({}, { status: 503, statusText: 'Unavailable' });
    expect(service.notificaciones().length).toBe(1);
    expect(service.notificacionesError()).not.toBe('');
    tick(10_000); http.expectOne('/api/notificaciones/mias').flush([]);
    expect(service.notificaciones()).toEqual([]); expect(service.notificacionesError()).toBe('');
  }));

  it('cancels an old session response and clears data on logout', notificationTest(() => {
    watch(service, subscriptions); tick(0); const old = http.expectOne('/api/notificaciones/mias');
    session = null; user.set(null); changed.next();
    expect(old.cancelled).toBeTrue(); expect(service.notificaciones()).toEqual([]);
    tick(30_000); http.expectNone('/api/notificaciones/mias');
  }));

  it('starts one fresh cycle when a different user logs in', notificationTest(() => {
    watch(service, subscriptions); tick(0); http.expectOne('/api/notificaciones/mias').flush([notification]);
    session = 'TEST_ONLY_NEW_SESSION';
    user.set({ id: 2, nombre: 'Otro', correo: 'otro@example.test', roles: 'Miembro' }); changed.next();
    http.expectOne('/api/modulos/menu').flush([]);
    expect(service.notificaciones()).toEqual([]);
    tick(0); http.expectOne('/api/notificaciones/mias').flush([]);
  }));

  it('does not poll a session restricted by a required password change', notificationTest(() => {
    user.update(value => ({ ...value!, cambioContrasenaRequerido: true }));
    watch(service, subscriptions); tick(30_000); http.expectNone('/api/notificaciones/mias');
    expect(service.notificaciones()).toEqual([]);
  }));

  it('pauses polling in the background and refreshes once when visible', notificationTest(() => {
    const hidden = spyOnProperty(document, 'hidden', 'get').and.returnValue(true);
    watch(service, subscriptions); tick(20_000); http.expectNone('/api/notificaciones/mias');
    hidden.and.returnValue(false); document.dispatchEvent(new Event('visibilitychange'));
    http.expectOne('/api/notificaciones/mias').flush([notification]);
    expect(service.notificaciones().length).toBe(1);
  }));

  it('shares confirmed read state without reloading the entire dashboard', notificationTest(() => {
    watch(service, subscriptions); tick(0); http.expectOne('/api/notificaciones/mias').flush([notification]);
    const changedData = jasmine.createSpy('dataChanged');
    subscriptions.push(TestBed.inject(ApiService).dataChanged$.subscribe(changedData));
    service.marcarComoLeida('1').subscribe(); http.expectOne('/api/notificaciones/1/leer').flush({});
    expect(service.notificaciones()[0].read).toBeTrue(); expect(changedData).not.toHaveBeenCalled();
    tick(10_000); http.expectOne('/api/notificaciones/mias').flush([notification]);
    expect(service.notificaciones()[0].read).toBeTrue();
  }));

  it('does not mark a new users notifications as read from an older pending mutation', notificationTest(() => {
    watch(service, subscriptions); tick(0); http.expectOne('/api/notificaciones/mias').flush([notification]);
    service.marcarTodasComoLeidas().subscribe(); const oldMutation = http.expectOne('/api/notificaciones/leer-todas');
    session = 'TEST_ONLY_NEW_SESSION'; changed.next(); http.expectOne('/api/modulos/menu').flush([]);
    tick(0); http.expectOne('/api/notificaciones/mias').flush([{ ...notification, id: '2' }]);
    oldMutation.flush({}); expect(service.notificaciones()[0].read).toBeFalse();
  }));
});
