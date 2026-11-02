import { Component, inject, OnInit, signal, computed, ChangeDetectionStrategy, DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, Observable, Subscription, merge, of } from 'rxjs';
import { catchError, finalize, tap } from 'rxjs/operators';
import { ApiService } from '../services/api.service';
import { CommonModule } from '@angular/common';
import { AuthService } from '../services/auth.service';
import { DashboardService } from '../services/dashboard.service';
import { addIcons } from 'ionicons';
import { calendarOutline, checkmarkCircleOutline, chevronForwardOutline, flashOutline, refreshOutline, timeOutline } from 'ionicons/icons';
import { IonIcon } from '@ionic/angular/standalone';
import { Router, RouterLink } from '@angular/router';
import { DashboardSidebarComponent } from '../components/dashboard-sidebar/dashboard-sidebar.component';
import { DashboardTopbarComponent } from '../components/dashboard-topbar/dashboard-topbar.component';
import { DashboardKpisComponent } from '../components/dashboard-kpis/dashboard-kpis.component';
import { DashboardChartsComponent } from '../components/dashboard-charts/dashboard-charts.component';
import { DashboardCalendarComponent } from '../components/dashboard-calendar/dashboard-calendar.component';
import { DashboardActivitiesComponent } from '../components/dashboard-activities/dashboard-activities.component';
import { DashboardNotificationsComponent } from '../components/dashboard-notifications/dashboard-notifications.component';
import {
  KpiCard, ChartData, CalendarEvent, Notification,
  Activity, UserInfo, DonutData, DashboardSection, DashboardSectionStates,
} from '../models/dashboard.models';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    DashboardSidebarComponent,
    DashboardTopbarComponent,
    DashboardKpisComponent,
    DashboardChartsComponent,
    DashboardCalendarComponent,
    DashboardActivitiesComponent,
    DashboardNotificationsComponent,
    IonIcon,
  ],
  templateUrl: './dashboard.page.html',
  styleUrls: ['./dashboard.page.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardPage implements OnInit {
  protected readonly authService = inject(AuthService);
  private readonly dashboardService = inject(DashboardService);
  private readonly api = inject(ApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly router = inject(Router);

  protected readonly user = signal<UserInfo | null>(null);
  protected readonly userPhoto = computed(() => this.authService.currentUser()?.fotografiaRuta || '');
  protected readonly kpis = signal<KpiCard[]>([]);
  protected readonly chartData = signal<ChartData>({ categories: [], series: [] });
  protected readonly events = signal<CalendarEvent[]>([]);
  protected readonly activities = signal<Activity[]>([]);
  protected readonly notifications = this.dashboardService.notificaciones;
  protected readonly notificationsLoading = this.dashboardService.notificacionesLoading;
  protected readonly notificationsError = this.dashboardService.notificacionesError;
  protected readonly menuItems = computed(() => this.dashboardService.getMenuItems());
  protected readonly tareasPorEstado = signal<DonutData>({ items: [] });
  protected readonly inventarioPorEstado = signal<DonutData>({ items: [] });
  protected readonly reunionesPorEstado = signal<DonutData>({ items: [] });
  protected readonly showInventarioChart = computed(() => this.authService.isAdmin());
  protected readonly sectionStates = signal<DashboardSectionStates>(this.initialSectionStates());
  protected readonly loading = computed(() => Object.values(this.sectionStates()).some(section => section.loading));
  private loadSubscription?: Subscription;
  private loadVersion = 0;
  private accessSignature = '';
  protected readonly lastUpdated = signal<Date | null>(null);
  protected readonly greeting = computed(() => {
    const hour = new Date().getHours();
    return hour < 12 ? 'Buenos días' : hour < 18 ? 'Buenas tardes' : 'Buenas noches';
  });
  protected readonly todayItems = computed(() => this.events()
    .filter(event => this.isToday(event.date) && this.isActionable(event))
    .sort((left, right) => left.date.localeCompare(right.date))
    .slice(0, 3));
  constructor() {
    addIcons({ calendarOutline, checkmarkCircleOutline, chevronForwardOutline, flashOutline, refreshOutline, timeOutline });
  }

  ngOnInit(): void {
    if (!this.authService.isAuthenticated()) {
      this.authService.logout();
      return;
    }
    this.loadUser();
    this.accessSignature = this.userAccessSignature();
    this.loadAll();
    this.api.dataChanged$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.loadAll());
    this.authService.sessionChanged$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.loadUser();
      const signature = this.userAccessSignature();
      if (signature === this.accessSignature) return;
      this.accessSignature = signature;
      if (this.authService.isAuthenticated() && !this.authService.currentUser()?.cambioContrasenaRequerido) this.loadAll();
      else this.clearDashboard();
    });

    this.dashboardService.notificaciones$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe();
  }

  private loadUser(): void {
    this.user.set(this.authService.currentUser());
  }

  private loadAll(): void {
    const version = ++this.loadVersion;
    this.loadSubscription?.unsubscribe();
    const sessionUserId = this.authService.currentUser()?.id;
    const admin = this.authService.isSuperAdmin();
    this.sectionStates.set(this.initialSectionStates(true));
    const current = () => version === this.loadVersion && this.authService.currentUser()?.id === sessionUserId;
    let received = false;
    const section = <T>(key: DashboardSection, source: Observable<T>, update: (data: T) => void): Observable<T> => source.pipe(
      tap(data => { if (current()) { received = true; update(data); } }),
      catchError(() => {
        if (current()) this.updateSectionState(key, { loading: false, error: 'No se pudo cargar esta sección. Puedes reintentar con Actualizar.' });
        return EMPTY;
      }),
      finalize(() => { if (current()) this.updateSectionState(key, { loading: false }); }),
    );
    // Cada respuesta actualiza su sección; una nueva carga cancela la anterior.
    this.loadSubscription = merge(
      section('kpis', admin ? this.dashboardService.getKpis() : this.dashboardService.getUsuarioKpis(), data => this.kpis.set(data)),
      section('events', this.dashboardService.getCalendario(), data => this.events.set(data)),
      section('tasks', this.dashboardService.getTareasPorEstado(), data => this.tareasPorEstado.set(data)),
      section('meetings', this.dashboardService.getReunionesPorEstado(), data => this.reunionesPorEstado.set(data)),
      section('chartData', admin ? this.dashboardService.getChartData() : of({ categories: [], series: [] } as ChartData), data => this.chartData.set(data)),
      section('activities', admin ? this.dashboardService.getActividades() : of([] as Activity[]), data => this.activities.set(data)),
      section('inventory', admin ? this.dashboardService.getInventarioPorEstado() : of({ items: [] } as DonutData), data => this.inventarioPorEstado.set(data)),
    ).pipe(
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({ complete: () => { if (current() && received) this.lastUpdated.set(new Date()); } });
  }

  private userAccessSignature(): string {
    const user = this.authService.currentUser();
    return JSON.stringify({ id: user?.id, correo: user?.correo, roles: user?.roles,
      permisos: [...(user?.permisos || [])].sort(), required: !!user?.cambioContrasenaRequerido,
      authenticated: this.authService.isAuthenticated() });
  }

  private initialSectionStates(loading = false): DashboardSectionStates {
    const state = () => ({ loading, error: '' });
    return { kpis: state(), chartData: state(), events: state(), activities: state(), inventory: state(), tasks: state(), meetings: state() };
  }

  private updateSectionState(key: DashboardSection, update: Partial<DashboardSectionStates[DashboardSection]>): void {
    this.sectionStates.update(states => ({ ...states, [key]: { ...states[key], ...update } }));
  }

  private clearDashboard(): void {
    ++this.loadVersion;
    this.loadSubscription?.unsubscribe();
    this.sectionStates.set(this.initialSectionStates());
    this.kpis.set([]);
    this.chartData.set({ categories: [], series: [] });
    this.events.set([]);
    this.activities.set([]);
    this.inventarioPorEstado.set({ items: [] });
    this.tareasPorEstado.set({ items: [] });
    this.reunionesPorEstado.set({ items: [] });
    this.lastUpdated.set(null);
  }

  protected refreshDashboard(): void { this.loadAll(); this.dashboardService.refreshNotificaciones(); }

  protected onMenuToggle(): void {
    this.authService.sidebarCollapsed.update(v => !v);
  }

  protected onLogout(): void {
    this.authService.logout();
  }

  protected openNotification(notification: Notification): void {
    void this.router.navigateByUrl(this.dashboardService.notificationUrl(notification));
    this.dashboardService.marcarComoLeida(notification.id).subscribe();
  }

  protected itemRoute(event: CalendarEvent): string {
    return event.type === 'reunion' ? '/reuniones' : event.type === 'tarea' ? '/tareas' : '/dashboard';
  }

  protected itemKind(event: CalendarEvent): string {
    return event.type === 'reunion' ? 'Reunión' : event.type === 'tarea' ? 'Tarea' : 'Compromiso';
  }

  protected itemTime(event: CalendarEvent): string {
    if (event.allDay || !event.date.includes('T')) return 'Todo el día';
    return event.date.split('T')[1].slice(0, 5);
  }

  private isToday(value: string): boolean {
    const today = new Date();
    const localDate = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
    return value.slice(0, 10) === localDate;
  }

  private isActionable(event: CalendarEvent): boolean {
    return !['completada', 'cancelada'].includes(event.estado || '');
  }
}
