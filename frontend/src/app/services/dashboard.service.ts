import { DestroyRef, Injectable, signal, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, Subject, defer, fromEvent, merge, of, timer } from 'rxjs';
import { map, catchError, distinctUntilChanged, exhaustMap, filter, finalize, shareReplay, startWith, switchMap, take, tap } from 'rxjs/operators';
import {
  KpiCard, ChartData, CalendarEvent, Notification,
  Activity, SidebarItem, DonutData,
} from '../models/dashboard.models';
import { ApiService } from './api.service';
import { AuthService } from './auth.service';
import { INSTITUTIONAL_BLUE } from '../shared/ui-colors';

interface BackendModule {
  id: number; nombre: string; ruta: string; icono: string; activo: boolean; orden: number;
}

const MODULE_ICON_BY_ROUTE: Readonly<Record<string, string>> = {
  '/dashboard': 'grid-outline',
  '/reuniones': 'calendar-outline',
  '/actas': 'document-text-outline',
  '/asistencia-qr': 'qr-code-outline',
  '/tareas': 'clipboard-outline',
  '/inventario': 'cube-outline',
  '/documentos': 'folder-outline',
  '/grupos': 'people-outline',
  '/reporte': 'bar-chart-outline',
  '/reportes': 'bar-chart-outline',
  '/correspondencia': 'mail-outline',
  '/notas': 'reader-outline',
  '/memos': 'mail-outline',
  '/config': 'settings-outline',
};

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly api = inject(ApiService);
  private readonly authService = inject(AuthService);
  private readonly menuItems = signal<SidebarItem[]>(this.getDefaultItems());
  private menuRefreshVersion = 0;
  private readonly destroyRef = inject(DestroyRef);
  private readonly notificationRefresh = new Subject<void>();
  private notificationSession: string | null = null;
  private notificationAttemptAt = 0;
  private notificationLoaded = false;
  private notificationInitialized = false;
  private knownNotificationIds = new Set<string>();
  private readNotificationIds = new Set<string>();
  private readonly notificationState = signal<Notification[]>([]);
  readonly notificaciones = this.notificationState.asReadonly();
  readonly notificacionesLoading = signal(true);
  readonly notificacionesError = signal('');

  // Todos los consumidores comparten una petición en curso y un solo reloj.
  // El estado pertenece a la sesión; una respuesta anterior no puede restaurarlo.
  readonly notificaciones$ = defer(() => this.authService.sessionChanged$.pipe(
    startWith(undefined),
    map(() => this.authService.isAuthenticated() && !this.authService.currentUser()?.cambioContrasenaRequerido
      ? this.authService.getToken() : null),
    distinctUntilChanged(),
    switchMap(session => {
      if (session !== this.notificationSession) {
        this.notificationSession = session;
        this.notificationState.set([]);
        this.notificacionesError.set('');
        this.notificationAttemptAt = 0;
        this.notificationLoaded = false;
        this.notificationInitialized = false;
        this.knownNotificationIds.clear();
        this.readNotificationIds.clear();
      }
      if (!session) { this.notificacionesLoading.set(false); return of([]); }
      const wait = this.notificationLoaded ? Math.max(0, 10_000 - (Date.now() - this.notificationAttemptAt)) : 0;
      const updates = merge(
        timer(wait, 10_000), this.notificationRefresh,
        fromEvent(document, 'visibilitychange').pipe(filter(() => !document.hidden)),
      ).pipe(
        filter(() => !document.hidden),
        exhaustMap(() => {
          this.notificationAttemptAt = Date.now();
          this.notificacionesLoading.set(!this.notificationLoaded);
          return this.fetchNotificaciones().pipe(
            tap(items => {
              if (session !== this.authService.getToken()) return;
              this.applyNotificaciones(items);
              this.notificacionesError.set('');
            }),
            catchError(() => {
              this.notificationLoaded = true;
              this.notificacionesError.set('No se pudieron actualizar las notificaciones. Se reintentará automáticamente.');
              return of(this.notificationState());
            }),
            finalize(() => {
              if (session === this.notificationSession) {
                this.notificacionesLoading.set(false);
              }
            }),
          );
        }),
      );
      return this.notificationLoaded ? merge(of(this.notificationState()), updates) : updates;
    }),
    takeUntilDestroyed(this.destroyRef),
  )).pipe(shareReplay({ bufferSize: 1, refCount: true }));

  readonly resumen = signal<null>(null);
  readonly inventario = signal<any[]>([]);

  getMenuItems(): SidebarItem[] {
    return this.menuItems();
  }

  private getDefaultItems(): SidebarItem[] {
    const items: SidebarItem[] = [
      { label: 'Panel', icon: 'grid-outline', route: '/dashboard' },
      { label: 'Actas', icon: 'document-text-outline', route: '/actas' },
      { label: 'Inventario', icon: 'cube-outline', route: '/inventario' },
      { label: 'Reportes', icon: 'bar-chart-outline', route: '/reportes' },
    ];
    if (this.authService.hasPermission('Configuracion QR', 'Ver')) {
      items.splice(2, 0, { label: 'Configuración QR', icon: 'qr-code-outline', route: '/asistencia-qr' });
    }
    items.push(
      { label: '', icon: '', route: '', divider: true },
      { label: 'Configuración', icon: 'settings-outline', route: '/config' },
    );
    return items;
  }

  refreshMenuItems(): void {
    const refreshVersion = ++this.menuRefreshVersion;
    if (!this.authService.isAuthenticated()) {
      this.menuItems.set(this.getDefaultItems());
      return;
    }

    const isAdmin = this.authService.isSuperAdmin();
    const isJefeUnidad = this.authService.hasRole('Jefe de Unidad');
    const modulesEndpoint = '/api/modulos/menu';
    this.api.get<BackendModule[]>(modulesEndpoint).pipe(
      catchError(() => of([] as BackendModule[])),
    ).subscribe(modules => {
      if (refreshVersion !== this.menuRefreshVersion) return;
      const allowedModules = new Set<string>();
      allowedModules.add('panel');
      allowedModules.add('dashboard');
      allowedModules.add('asistencia');
      allowedModules.add('notificaciones');
      if (this.authService.hasPermission('Configuracion QR', 'Ver')) allowedModules.add('asistencia-qr');
      if (this.authService.hasPermission('Inventario', 'Ver')) allowedModules.add('inventario');
      if (this.authService.hasPermission('Reporte', 'Ver')) {
        allowedModules.add('reporte');
        allowedModules.add('reportes');
      }
      if (this.authService.hasPermission('Notas', 'Ver') || this.authService.hasPermission('Memos', 'Ver')) {
        allowedModules.add('correspondencia');
      }

      if (isAdmin) {
        modules.forEach(m => allowedModules.add(m.ruta.replace('/', '')));
      } else if (['Director', 'Subdirector', 'Secretaria'].some(role => this.authService.hasRole(role))) {
        const functionalModules: Record<string, string> = {
          actas: 'Actas', reuniones: 'Reuniones', tareas: 'Tareas', documentos: 'Documentos', grupos: 'Grupos',
        };
        Object.entries(functionalModules).forEach(([route, permissionModule]) => {
          if (this.authService.hasPermission(permissionModule, 'Ver')) allowedModules.add(route);
        });
      } else if (isJefeUnidad) {
        ['actas', 'reporte', 'reportes', 'reuniones', 'tareas', 'documentos', 'grupos'].forEach(m => allowedModules.add(m));
        if (this.authService.hasPermission('Notas', 'Ver')) allowedModules.add('notas');
        if (this.authService.hasPermission('Memos', 'Ver')) allowedModules.add('memos');
      } else {
        ['reuniones', 'tareas', 'documentos', 'grupos'].forEach(m => allowedModules.add(m));
        if (this.authService.hasPermission('Notas', 'Ver')) allowedModules.add('notas');
        if (this.authService.hasPermission('Memos', 'Ver')) allowedModules.add('memos');
      }

      const configItem = modules.find(m => m.ruta === '/config');
      const others = modules.filter(m => m.ruta !== '/config' && m.ruta.replace(/\/$/, '') !== '/solicitudes'
        && m.activo && allowedModules.has(m.ruta.replace('/', '')));
      others.sort((a, b) => a.orden - b.orden);
      const items: SidebarItem[] = others.map(m => ({ label: this.menuLabel(m), icon: this.moduleIcon(m), route: m.ruta }));
      items.push({ label: '', icon: '', route: '', divider: true });
      if (configItem?.activo) {
        items.push({ label: configItem.nombre, icon: this.moduleIcon(configItem), route: configItem.ruta });
      }
      this.menuItems.set(items);
    });
  }

  private menuLabel(module: BackendModule): string {
    const route = module.ruta.replace(/\/$/, '');
    if (route === '/dashboard') return 'Panel';
    if (route === '/reporte' || route === '/reportes') return 'Reportes';
    return module.nombre;
  }

  private moduleIcon(module: BackendModule): string {
    const configured = module.icono?.trim();
    if (configured) return configured;
    return MODULE_ICON_BY_ROUTE[module.ruta.replace(/\/$/, '')] || 'folder-outline';
  }

  constructor() {
    this.refreshMenuItems();
    this.authService.sessionChanged$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.refreshMenuItems());
  }

  getKpis(): Observable<KpiCard[]> {
    return this.api.get<any[]>('/api/dashboard/kpis').pipe(
      map(list => list.map(k => ({
        label: k.label, value: k.value, subtitle: k.subtitle, icon: k.icon,
        trend: k.trend, trendValue: k.trendValue, trendDescription: k.trendDescription,
        color: k.color, route: this.kpiRoute(k.label),
      } as KpiCard))),
    );
  }

  getChartData(): Observable<ChartData> {
    return this.api.get<ChartData>('/api/dashboard/chart-data');
  }

  getActividades(): Observable<Activity[]> {
    return this.api.get<any[]>('/api/dashboard/actividades').pipe(
      map(list => list.map(a => ({
        id: a.id, user: a.user, action: a.action, target: a.target,
        timestamp: new Date(a.timestamp), avatar: a.avatar || '',
      } as Activity))),
    );
  }

  getNotificaciones(): Observable<Notification[]> {
    return this.notificaciones$.pipe(take(1), map(() => this.notificationState()));
  }

  refreshNotificaciones(): void { this.notificationRefresh.next(); }

  private fetchNotificaciones(): Observable<Notification[]> {
    return this.api.get<any[]>('/api/notificaciones/mias').pipe(
      map(list => list.map(n => ({
        id: n.id, title: n.title, message: n.message, type: n.type,
        timestamp: new Date(n.timestamp), read: n.read,
      } as Notification))),
    );
  }

  private applyNotificaciones(items: Notification[]): void {
    this.notificationLoaded = true;
    items = items.map(item => this.readNotificationIds.has(item.id) ? { ...item, read: true } : item);
    this.notificationState.set(items);
    const incoming = items.filter(item => !item.read && !this.knownNotificationIds.has(item.id));
    items.forEach(item => this.knownNotificationIds.add(item.id));
    if (!this.notificationInitialized) { this.notificationInitialized = true; return; }
    if (incoming.length && this.authService.preferences().pushNotifications
        && 'Notification' in window && globalThis.Notification.permission === 'granted') {
      new globalThis.Notification(incoming[0].title, { body: incoming[0].message });
    }
  }

  marcarComoLeida(id: string): Observable<void> {
    const session = this.authService.getToken();
    return this.api.put(`/api/notificaciones/${id}/leer`, {}, { notifyChanges: false }).pipe(
      tap(() => {
        if (session !== this.authService.getToken()) return;
        this.readNotificationIds.add(id);
        this.notificationState.update(items => items.map(item => item.id === id ? { ...item, read: true } : item));
      }),
      map(() => { }),
    );
  }

  marcarTodasComoLeidas(): Observable<void> {
    const session = this.authService.getToken();
    return this.api.put('/api/notificaciones/leer-todas', {}, { notifyChanges: false }).pipe(
      tap(() => {
        if (session !== this.authService.getToken()) return;
        this.notificationState().forEach(item => this.readNotificationIds.add(item.id));
        this.notificationState.update(items => items.map(item => ({ ...item, read: true })));
      }),
      map(() => { }),
    );
  }

  notificationUrl(notification: Notification): string {
    const text = `${notification.title} ${notification.message}`;
    if (/\bSOL-\d{4}-\d{6}\b/i.test(text)) return '/dashboard';

    const normalized = text.toLowerCase();
    if (normalized.includes('tarea') || normalized.includes('actividad')) return '/tareas';
    if (normalized.includes('reuni')) return '/reuniones';
    if (normalized.includes('document')) return '/documentos';
    if (normalized.includes('grupo') || normalized.includes('mención')) return '/grupos';
    return '/dashboard';
  }

  getTareasPorEstado(): Observable<DonutData> {
    return this.api.get<DonutData>('/api/dashboard/tareas-por-estado');
  }

  getCalendario(): Observable<CalendarEvent[]> {
    return this.api.get<CalendarEvent[]>('/api/dashboard/calendario');
  }

  getInventarioPorEstado(): Observable<DonutData> {
    return this.api.get<any[]>('/api/dashboard/inventario').pipe(
      map(list => ({
        items: list.map((i: any, idx: number) => ({
          label: i.nombre || i.estado || '',
          value: i.cantidad ?? i.total ?? 0,
          color: ([INSTITUTIONAL_BLUE, '#22c55e', '#f59e0b', '#a855f7', '#ef4444', '#64748b'])[idx % 6],
        })),
      })),
    );
  }

  getReunionesPorEstado(): Observable<DonutData> {
    return this.api.get<DonutData>('/api/dashboard/reuniones-por-estado');
  }

  getUsuarioKpis(): Observable<KpiCard[]> {
    return this.api.get<any[]>('/api/dashboard/kpis-usuario').pipe(
      map(list => list.map(k => ({
        label: k.label, value: k.value, subtitle: k.subtitle, icon: k.icon,
        trend: k.trend, trendValue: k.trendValue, trendDescription: k.trendDescription,
        color: k.color, route: this.kpiRoute(k.label),
      } as KpiCard))),
    );
  }

  private kpiRoute(label: string): string {
    const routes: Record<string, string> = {
      Usuarios: '/config/users', Activos: '/config/users', Bloqueados: '/config/users', Roles: '/config/roles',
      Tareas: '/tareas', Reuniones: '/reuniones', Hoy: '/reuniones', Completadas: '/tareas',
    };
    return routes[label] || '/dashboard';
  }
}
