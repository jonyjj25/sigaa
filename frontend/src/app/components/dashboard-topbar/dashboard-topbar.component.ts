import { Component, inject, input, output, signal, computed, ChangeDetectionStrategy, HostListener, OnChanges, OnInit, SimpleChanges, ElementRef, DestroyRef, ViewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, NavigationStart } from '@angular/router';
import { CommonModule } from '@angular/common';
import { IonIcon } from '@ionic/angular/standalone';
import { Notification } from '../../models/dashboard.models';
import { DashboardService } from '../../services/dashboard.service';
import { AuthService } from '../../services/auth.service';
import { ConfigDataService } from '../../pages/config/services/config-data.service';
import { addIcons } from 'ionicons';
import { notificationsOutline, personCircleOutline, logOutOutline, menuOutline, clipboardOutline, calendarOutline, documentTextOutline, peopleOutline, informationCircleOutline, moonOutline, sunnyOutline } from 'ionicons/icons';

@Component({selector: 'app-dashboard-topbar', standalone: true, imports: [CommonModule, IonIcon], templateUrl: './dashboard-topbar.component.html', styleUrls: ['./dashboard-topbar.component.scss'], changeDetection: ChangeDetectionStrategy.OnPush})
export class DashboardTopbarComponent implements OnChanges, OnInit {
  private readonly dashboardService = inject(DashboardService); private readonly authService = inject(AuthService); private readonly configDataService = inject(ConfigDataService); private readonly router = inject(Router); private readonly destroyRef = inject(DestroyRef);
  readonly notifications = input<Notification[]>([]); readonly userName = input('Administrador'); readonly userRole = input('admin'); readonly userPhoto = input(''); readonly collapsed = input(false); readonly menuToggle = output<void>(); readonly logout = output<void>();
  protected showNotifications = signal(false); protected showUserMenu = signal(false);
  protected readonly loadingNotifications = this.dashboardService.notificacionesLoading;
  private readonly notificationActionError = signal('');
  protected readonly notificationError = computed(() => this.notificationActionError() || this.dashboardService.notificacionesError());
  protected readonly liveNotifications = this.dashboardService.notificaciones;
  protected readonly avatarImageFailed = signal(false);
  protected readonly themeSaving = signal(false);
  protected readonly isDarkTheme = computed(() => this.authService.effectiveTheme() === 'dark');
  protected readonly resolvedUserPhoto = computed(() => this.userPhoto() || this.authService.currentUser()?.fotografiaRuta || '');
  @ViewChild('userTrigger') private userTrigger?: ElementRef<HTMLElement>;
  @ViewChild('userMenu') private userMenu?: ElementRef<HTMLElement>;
  @ViewChild('notificationTrigger') private notificationTrigger?: ElementRef<HTMLElement>;
  @ViewChild('notificationPanel') private notificationPanel?: ElementRef<HTMLElement>;
  protected readonly visibleNotifications = computed(() => this.liveNotifications().filter(n => !this.isRead(n)));
  protected get unreadCount(): number { return this.visibleNotifications().length; }
  constructor() { addIcons({notificationsOutline, personCircleOutline, logOutOutline, menuOutline, clipboardOutline, calendarOutline, documentTextOutline, peopleOutline, informationCircleOutline, moonOutline, sunnyOutline}); this.router.events.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(e => { if (e instanceof NavigationStart) this.closeAllMenus(); }); const listener = (event: PointerEvent) => this.onDocumentPointerDown(event); document.addEventListener('pointerdown', listener); this.destroyRef.onDestroy(() => document.removeEventListener('pointerdown', listener)); }
  ngOnInit(): void { this.dashboardService.notificaciones$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(); }
  @HostListener('document:keydown', ['$event']) protected onKeydown(e: KeyboardEvent): void { if (e.key === 'Escape') this.closeAllMenus(); }
  private onDocumentPointerDown(event: PointerEvent): void { const target = event.target as Node; const inUser = this.userTrigger?.nativeElement.contains(target) || this.userMenu?.nativeElement.contains(target); const inNotifications = this.notificationTrigger?.nativeElement.contains(target) || this.notificationPanel?.nativeElement.contains(target); if (!inUser) this.showUserMenu.set(false); if (!inNotifications) this.closeNotifications(); }
  protected toggleNotifications(e: Event): void { e.stopPropagation(); this.showNotifications.update(v => !v); this.notificationActionError.set(''); this.showUserMenu.set(false); }
  protected closeNotifications(): void { this.showNotifications.set(false); }
  protected toggleUserMenu(event: Event): void { event.stopPropagation(); this.showUserMenu.update(v => !v); this.closeNotifications(); }
  protected onAvatarError(): void { this.avatarImageFailed.set(true); }
  protected navigateFromUserMenu(route: string): void { this.closeAllMenus(); this.router.navigateByUrl(route); }
  protected logoutFromUserMenu(): void { this.closeAllMenus(); this.logout.emit(); }
  protected toggleTheme(): void {
    if (this.themeSaving()) return;
    const previous = this.authService.preferences();
    const updated = { ...previous, theme: this.isDarkTheme() ? 'light' : 'dark' };
    this.authService.updatePreferences(updated);
    this.themeSaving.set(true);
    this.configDataService.saveMyPreferences(updated).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: saved => { this.authService.updatePreferences(saved); this.themeSaving.set(false); },
      error: () => { this.authService.updatePreferences(previous); this.themeSaving.set(false); },
    });
  }
  private closeAllMenus(): void { this.closeNotifications(); this.showUserMenu.set(false); }
  protected isRead(n: Notification): boolean { return n.read; }
  ngOnChanges(changes: SimpleChanges): void { if (changes['userPhoto']) this.avatarImageFailed.set(false); }
  protected openNotification(n: Notification): void {
    this.notificationActionError.set('');
    const url = this.dashboardService.notificationUrl(n);
    this.closeNotifications();
    void this.router.navigateByUrl(url);
    this.dashboardService.marcarComoLeida(n.id).subscribe({
      error: () => this.notificationActionError.set('No se pudo marcar la notificación como leída.'),
    });
  }
  protected markAllAsRead(): void { this.dashboardService.marcarTodasComoLeidas().subscribe({next: () => this.closeNotifications(), error: () => this.notificationActionError.set('No se pudieron marcar las notificaciones como leídas.')}); }
  protected notificationIcon(n: Notification): string { const t = `${n.title} ${n.message}`.toLowerCase(); return t.includes('tarea') ? 'clipboard-outline' : t.includes('reuni') ? 'calendar-outline' : t.includes('document') || t.includes('aprob') ? 'document-text-outline' : t.includes('grupo') ? 'people-outline' : 'information-circle-outline'; }
}
