import { Component, inject, computed } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs';
import { CommonModule } from '@angular/common';
import { Router, NavigationStart, RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { IonIcon } from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import {
  peopleOutline, shieldOutline, gridOutline,
  lockClosedOutline, documentTextOutline, notificationsOutline, serverOutline,
  bookOutline, folderOutline, layersOutline, chevronBackOutline, chevronForwardOutline,
  homeOutline, timeOutline, businessOutline, personOutline, fingerPrintOutline,
  optionsOutline, menuOutline, gitPullRequestOutline,
} from 'ionicons/icons';
import { AuthService } from '../../../services/auth.service';
import { ConfigDataService } from '../services/config-data.service';

interface NavItem {
  label: string;
  path: string;
  icon: string;
  perm?: { modulo: string; accion: string };
}

@Component({
  selector: 'app-config-layout',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive, IonIcon],
  template: `
    <div class="layout-container">
      @if (!authService.sidebarCollapsed()) {
        <button
          type="button"
          class="mobile-nav-backdrop"
          aria-label="Cerrar navegación de configuración"
          (click)="authService.sidebarCollapsed.set(true)"
        ></button>
      }
      <aside class="sidebar" [class.collapsed]="authService.sidebarCollapsed()">
        <div class="sidebar-header">
          <div class="logo-section">
            <div class="logo-icon">⚙</div>
            <span class="logo-text" *ngIf="!authService.sidebarCollapsed()">Configuración</span>
          </div>
          <button type="button" class="toggle-btn" [attr.aria-label]="authService.sidebarCollapsed() ? 'Expandir navegación de configuración' : 'Contraer navegación de configuración'" (click)="authService.sidebarCollapsed.set(!authService.sidebarCollapsed())" [title]="authService.sidebarCollapsed() ? 'Expandir navegación de configuración' : 'Contraer navegación de configuración'">
            <ion-icon [name]="authService.sidebarCollapsed() ? 'chevron-forward-outline' : 'chevron-back-outline'" aria-hidden="true" />
          </button>
        </div>

        <nav class="sidebar-nav">
           <div class="nav-section-label" *ngIf="!authService.sidebarCollapsed()">Personal</div>
           <a *ngFor="let item of personalNavItems()"
             [routerLink]="item.path"
             routerLinkActive="active"
             [routerLinkActiveOptions]="{exact: item.path === ''}"
             class="nav-item"
             [attr.aria-label]="item.label"
             [title]="item.label">
            <div class="nav-icon-wrapper">
              <ion-icon [name]="item.icon" class="nav-icon" />
            </div>
            <span class="nav-label" *ngIf="!authService.sidebarCollapsed()">{{ item.label }}</span>
          </a>

          @if (adminNavItems().length > 0) {
            <div class="nav-divider"></div>
            <div class="nav-section-label" *ngIf="!authService.sidebarCollapsed()">Administración</div>
            <a *ngFor="let item of adminNavItems()"
              [routerLink]="item.path"
              routerLinkActive="active"
              [routerLinkActiveOptions]="{exact: item.path === ''}"
              class="nav-item"
              [attr.aria-label]="item.label"
              [title]="item.label">
              <div class="nav-icon-wrapper">
                <ion-icon [name]="item.icon" class="nav-icon" />
              </div>
              <span class="nav-label" *ngIf="!authService.sidebarCollapsed()">{{ item.label }}</span>
            </a>
          }
        </nav>
        <a routerLink="/dashboard" class="dashboard-link" aria-label="Volver al panel principal" title="Volver al panel principal">
          <div class="nav-icon-wrapper">
            <ion-icon name="home-outline" class="nav-icon" />
          </div>
          <span class="nav-label" *ngIf="!authService.sidebarCollapsed()">Volver al panel</span>
        </a>
      </aside>

      <main class="main-content" [class.expanded]="authService.sidebarCollapsed()">
        <button type="button" class="mobile-nav-toggle" (click)="authService.sidebarCollapsed.set(!authService.sidebarCollapsed())" [attr.aria-label]="authService.sidebarCollapsed() ? 'Abrir navegación de configuración' : 'Cerrar navegación de configuración'" [title]="authService.sidebarCollapsed() ? 'Abrir navegación de configuración' : 'Cerrar navegación de configuración'">
          <ion-icon name="menu-outline" aria-hidden="true" />
        </button>
        <p *ngIf="dataService.consultasConError().length" class="consulta-error" role="alert">
          No se pudo cargar toda la información de esta sección. Los datos pueden estar incompletos.
          Actualiza la página para intentarlo nuevamente.
          <button type="button" (click)="reintentarSeccion()">Reintentar</button>
        </p>
        <router-outlet />
      </main>
    </div>
  `,
  styles: [`
    .layout-container {
      display: flex;
      height: 100%;
      min-height: calc(100vh - 56px);
      background: var(--surface-page);
      font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
    }

    .sidebar {
      width: 260px;
      background: linear-gradient(180deg, var(--sidebar-bg-start) 0%, var(--sidebar-bg-end) 100%);
      color: var(--sidebar-text-strong);
      display: flex;
      flex-direction: column;
      transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
      overflow: hidden;
      flex-shrink: 0;
      border-right: 1px solid var(--sidebar-border);
    }

    .sidebar.collapsed {
      width: 64px;
    }

    .sidebar-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 16px 12px;
      border-bottom: 1px solid var(--sidebar-border);
      min-height: 64px;
    }

    .sidebar.collapsed .sidebar-header {
      justify-content: center;
      padding-inline: 0;
    }

    .sidebar.collapsed .logo-section {
      display: none;
    }

    .logo-section {
      display: flex;
      align-items: center;
      gap: 10px;
      overflow: hidden;
    }

    .logo-icon {
      width: 32px;
      height: 32px;
      background: var(--institutional-blue);
      border-radius: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 18px;
      flex-shrink: 0;
    }

    .logo-text {
      font-size: 15px;
      font-weight: 600;
      white-space: nowrap;
      color: var(--sidebar-text-strong);
      letter-spacing: 0.3px;
    }

    .toggle-btn {
      background: rgba(255, 255, 255, 0.06);
      border: 1px solid var(--sidebar-border);
      color: var(--sidebar-text);
      width: 28px;
      height: 28px;
      border-radius: 6px;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
      transition: all 0.2s;
    }

    .toggle-btn:hover {
      background: rgba(255, 255, 255, 0.12);
      color: var(--sidebar-text-strong);
    }

    .sidebar-nav {
      flex: 1;
      padding: 12px 8px;
      overflow-y: auto;
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    .nav-section-label {
      font-size: 10px;
      font-weight: 700;
      color: var(--sidebar-section-text);
      text-transform: uppercase;
      letter-spacing: 0.8px;
      padding: 8px 12px 4px;
      user-select: none;
    }

    .nav-divider {
      height: 1px;
      background: var(--sidebar-border);
      margin: 8px 12px;
    }

    .nav-item {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 10px 12px;
      border-radius: 8px;
      text-decoration: none;
      color: var(--sidebar-text);
      transition: all 0.2s;
      cursor: pointer;
      position: relative;
      white-space: nowrap;
    }

    .nav-icon-wrapper {
      width: 20px;
      height: 20px;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }

    .nav-icon {
      font-size: 20px;
      color: var(--sidebar-icon);
      transition: color 0.2s;
    }

    .nav-label {
      font-size: 14px;
      font-weight: 500;
      color: inherit;
    }

    .nav-item:hover {
      background: rgba(255, 255, 255, 0.06);
      color: var(--sidebar-text-strong);
    }

    .nav-item:hover .nav-icon {
      color: var(--sidebar-text);
    }

    .nav-item.active {
      background: color-mix(in srgb, var(--institutional-blue) 24%, transparent);
      color: #fff;
    }

    .nav-item.active .nav-icon {
      color: #fff;
    }

    .nav-item.active::before {
      content: '';
      position: absolute;
      left: -8px;
      top: 50%;
      transform: translateY(-50%);
      width: 3px;
      height: 20px;
      background: var(--institutional-blue);
      border-radius: 0 3px 3px 0;
    }

    .main-content {
      flex: 1;
      padding: 24px;
      overflow-y: auto;
      transition: margin-left 0.3s cubic-bezier(0.4, 0, 0.2, 1);
      background: var(--surface-page);
    }

    .consulta-error { padding: 14px 16px; border: 1px solid #f0c5c5; border-radius: 10px; background: #fff4f4; color: #8c2424; }

    .sidebar-nav::-webkit-scrollbar {
      width: 4px;
    }

    .sidebar-nav::-webkit-scrollbar-track {
      background: transparent;
    }

    .sidebar-nav::-webkit-scrollbar-thumb {
      background: var(--sidebar-border);
      border-radius: 4px;
    }

    .dashboard-link {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px;
      margin: 8px;
      border-radius: 8px;
      text-decoration: none;
      color: var(--sidebar-text);
      transition: all 0.2s;
      cursor: pointer;
      white-space: nowrap;
      border-top: 1px solid var(--sidebar-border);
    }

    .dashboard-link:hover {
      background: rgba(255, 255, 255, 0.08);
      color: var(--sidebar-text-strong);
    }

    .dashboard-link:hover .nav-icon {
      color: var(--sidebar-text);
    }

    .mobile-nav-toggle,
    .mobile-nav-backdrop { display: none; }

    @media (max-width: 768px) {
      .layout-container { display: block; min-height: 100vh; }
      .mobile-nav-backdrop { display: block; position: fixed; inset: 0; z-index: 1100; border: 0; background: rgba(4, 15, 35, .66); backdrop-filter: blur(2px); }
      .sidebar, .sidebar.collapsed { position: fixed; inset: 0 auto 0 0; z-index: 1200; width: min(280px, 86vw); transform: translateX(-105%); transition: transform .25s cubic-bezier(.4, 0, .2, 1); box-shadow: none; }
      .sidebar:not(.collapsed) { transform: translateX(0); box-shadow: 12px 0 32px rgba(15, 23, 42, .28); }
      .main-content, .main-content.expanded { width: 100%; min-width: 0; padding: 12px; }
      .mobile-nav-toggle { display: inline-grid; place-items: center; width: 44px; height: 44px; margin: 0 0 12px; border: 1px solid var(--border-default); border-radius: 10px; background: var(--surface-panel); color: var(--institutional-blue); font-size: 22px; }
    }
  `],
})
export class ConfigLayoutComponent {
  protected readonly authService = inject(AuthService);
  protected readonly dataService = inject(ConfigDataService);
  private readonly router = inject(Router);
  protected reintentarSeccion(): void { window.location.reload(); }

  private readonly allNavItems: NavItem[] = [
    { label: 'Mi Perfil', path: '/config/profile', icon: 'person-outline' },
    { label: 'Contraseña', path: '/config/password', icon: 'finger-print-outline' },
    { label: 'Preferencias', path: '/config/preferences', icon: 'options-outline' },
    { label: 'Panel', path: '/config', icon: 'home-outline', perm: { modulo: 'Dashboard', accion: 'Ver' } },
    { label: 'Usuarios', path: '/config/users', icon: 'people-outline', perm: { modulo: 'Usuarios', accion: 'Ver' } },
    { label: 'Roles', path: '/config/roles', icon: 'shield-outline', perm: { modulo: 'Roles', accion: 'Ver' } },
    { label: 'Módulos', path: '/config/modules', icon: 'grid-outline', perm: { modulo: 'Configuración', accion: 'Acceder' } },
    { label: 'Seguridad', path: '/config/security', icon: 'lock-closed-outline', perm: { modulo: 'Configuración', accion: 'Acceder' } },
    { label: 'Auditoría', path: '/config/audit', icon: 'document-text-outline', perm: { modulo: 'Configuración', accion: 'Acceder' } },
    { label: 'Notificaciones', path: '/config/notifications', icon: 'notifications-outline', perm: { modulo: 'Configuración', accion: 'Acceder' } },
    { label: 'Respaldos', path: '/config/backups', icon: 'server-outline', perm: { modulo: 'Configuración', accion: 'Acceder' } },
    { label: 'Organización', path: '/config/organization', icon: 'layers-outline', perm: { modulo: 'Configuración', accion: 'Acceder' } },
    { label: 'Guía', path: '/config/guia', icon: 'book-outline' },
    { label: 'Ubicaciones', path: '/config/ubicaciones', icon: 'business-outline', perm: { modulo: 'Configuración', accion: 'Acceder' } },
  ];

  protected readonly personalNavItems = computed(() =>
    this.allNavItems.filter(item => !item.perm)
  );

  protected readonly adminNavItems = computed(() =>
    this.authService.isSuperAdmin()
      ? this.allNavItems.filter(item => item.perm)
      : this.allNavItems.filter(item => item.path === '/config/users' && this.authService.hasPermission('Usuarios', 'Ver'))
  );

  constructor() {
    this.dataService.limpiarErroresConsulta();
    this.router.events.pipe(filter(event => event instanceof NavigationStart), takeUntilDestroyed())
      .subscribe(() => this.dataService.limpiarErroresConsulta());
    addIcons({
      peopleOutline, shieldOutline, gridOutline,
      lockClosedOutline, documentTextOutline, notificationsOutline, serverOutline,
      bookOutline, folderOutline, layersOutline, chevronBackOutline, chevronForwardOutline,
      homeOutline, timeOutline, businessOutline, personOutline, fingerPrintOutline,
      optionsOutline, menuOutline, gitPullRequestOutline,
    });
  }
}
