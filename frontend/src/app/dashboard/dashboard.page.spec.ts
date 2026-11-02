import { signal } from '@angular/core';
import { ComponentFixture, DeferBlockBehavior, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { EMPTY, Subject } from 'rxjs';
import { ApiService } from '../services/api.service';
import { AuthService } from '../services/auth.service';
import { DashboardService } from '../services/dashboard.service';
import { ConfigDataService } from '../pages/config/services/config-data.service';
import { DashboardPage } from './dashboard.page';
import { DEFAULT_PREFERENCES, DashboardSectionStates, KpiCard, Notification, UserInfo } from '../models/dashboard.models';

describe('Dashboard progressive loading', () => {
  let fixture: ComponentFixture<DashboardPage>;
  let streams: Record<string, Subject<any>>;
  let dashboard: jasmine.SpyObj<DashboardService>;
  let changed: Subject<void>;
  let user: ReturnType<typeof signal<UserInfo | null>>;
  let authenticated: boolean;
  let state: { kpis: () => KpiCard[]; sectionStates: () => DashboardSectionStates; notifications: () => Notification[]; refreshDashboard: () => void };

  beforeEach(async () => {
    authenticated = true; changed = new Subject<void>();
    user = signal<UserInfo | null>({ id: 1, nombre: 'Usuario', correo: 'usuario@example.test', roles: 'Superadmin' });
    streams = {};
    dashboard = jasmine.createSpyObj('DashboardService', ['getMenuItems', 'getKpis', 'getUsuarioKpis', 'getChartData',
      'getCalendario', 'getActividades', 'getInventarioPorEstado', 'getTareasPorEstado', 'getReunionesPorEstado', 'refreshNotificaciones']);
    dashboard.getMenuItems.and.returnValue([]);
    for (const method of ['getKpis', 'getUsuarioKpis', 'getChartData', 'getCalendario', 'getActividades', 'getInventarioPorEstado', 'getTareasPorEstado', 'getReunionesPorEstado']) {
      streams[method] = new Subject(); (dashboard as any)[method].and.returnValue(streams[method]);
    }
    Object.assign(dashboard, { notificaciones: signal([]), notificacionesLoading: signal(false), notificacionesError: signal(''), notificaciones$: EMPTY });
    await TestBed.configureTestingModule({ imports: [DashboardPage], deferBlockBehavior: DeferBlockBehavior.Manual,
      providers: [provideRouter([]), { provide: DashboardService, useValue: dashboard },
        { provide: ApiService, useValue: { dataChanged$: new Subject<void>() } },
        { provide: ConfigDataService, useValue: {} },
        { provide: AuthService, useValue: { currentUser: user, isAuthenticated: () => authenticated,
          isAdmin: () => user()?.roles === 'Superadmin', isSuperAdmin: () => user()?.roles === 'Superadmin',
          hasPermission: () => true, sidebarCollapsed: signal(false),
          effectiveTheme: signal('light'), preferences: signal(DEFAULT_PREFERENCES), sessionChanged$: changed } }],
    }).compileComponents();
    fixture = TestBed.createComponent(DashboardPage);
    state = fixture.componentInstance as unknown as typeof state;
    fixture.detectChanges();
  });

  afterEach(() => fixture.destroy());
  function kpis(): void {
    streams['getKpis'].next([{ label: 'Usuarios', value: 10, subtitle: 'Prueba', icon: 'people-outline', trend: 'neutral', color: '#2563eb' }]);
    streams['getKpis'].complete(); fixture.detectChanges();
  }

  it('renders useful indicators while a secondary API is still pending', () => {
    kpis();
    expect(fixture.nativeElement.querySelector('.section-kpis app-dashboard-kpis')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.section-kpis').textContent).toContain('10');
    expect(state.sectionStates().activities.loading).toBeTrue();
  });

  it('shows a section error without blocking successful indicators or calendar', () => {
    streams['getActividades'].error({ status: 503 }); kpis();
    streams['getCalendario'].next([]); streams['getCalendario'].complete(); fixture.detectChanges();
    expect(state.sectionStates().activities.error).not.toBe('');
    expect(fixture.nativeElement.querySelector('.section-activities [role=alert]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('app-dashboard-calendar')).not.toBeNull();
    expect(state.kpis()[0].value).toBe(10);
  });

  it('requests each section once and delegates manual notification refresh to the shared service', () => {
    expect(dashboard.getKpis).toHaveBeenCalledTimes(1); expect(dashboard.getCalendario).toHaveBeenCalledTimes(1);
    expect(dashboard.getTareasPorEstado).toHaveBeenCalledTimes(1); expect(dashboard.getActividades).toHaveBeenCalledTimes(1);
    expect(state.notifications).toBe(dashboard.notificaciones);
    state.refreshDashboard(); expect(dashboard.refreshNotificaciones).toHaveBeenCalledTimes(1);
  });

  it('cancels pending section requests on logout and clears already displayed data', () => {
    kpis(); authenticated = false; user.set(null); changed.next(); fixture.detectChanges();
    expect(state.kpis()).toEqual([]);
    expect(streams['getActividades'].observed).toBeFalse();
    streams['getActividades'].next([{ id: 'old' }]);
    expect(state.sectionStates().activities.loading).toBeFalse();
  });

  it('preserves the non-administrator API scope when the session changes', () => {
    user.set({ id: 2, nombre: 'Miembro', correo: 'miembro@example.test', roles: 'Miembro' }); changed.next();
    expect(dashboard.getUsuarioKpis).toHaveBeenCalledTimes(1);
    expect(dashboard.getKpis).toHaveBeenCalledTimes(1); expect(dashboard.getActividades).toHaveBeenCalledTimes(1);
    expect(dashboard.getInventarioPorEstado).toHaveBeenCalledTimes(1); expect(dashboard.getChartData).toHaveBeenCalledTimes(1);
    expect(state.sectionStates().inventory.loading).toBeFalse();
  });

  it('updates profile metadata without repeating all section requests', () => {
    user.update(value => ({ ...value!, nombre: 'Usuario actualizado', fotografiaRuta: '/uploads/fotografias/test.png' }));
    changed.next(); fixture.detectChanges();
    expect(dashboard.getKpis).toHaveBeenCalledTimes(1);
    expect(dashboard.getCalendario).toHaveBeenCalledTimes(1);
    expect(fixture.nativeElement.querySelector('h1').textContent).toContain('Usuario actualizado');
  });
});
