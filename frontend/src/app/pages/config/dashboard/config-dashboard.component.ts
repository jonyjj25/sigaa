import { Component, OnInit, signal, inject, DestroyRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { IonIcon } from '@ionic/angular/standalone';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ConfigDataService } from '../services/config-data.service';
import { QuickAccessCard } from '../models/config.models';

@Component({
  selector: 'app-config-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, IonIcon],
  template: `
    <div class="dashboard">
      <div class="dashboard-header">
        <div>
          <h1>Panel de configuración</h1>
          <p class="subtitle">Panel de control del módulo de administración</p>
        </div>
      </div>

      <div class="stats-row">
        <div class="stat-card users">
          <div class="stat-icon"><ion-icon name="people-outline" /></div>
          <div class="stat-info">
            <span class="stat-value">{{ stats().usuarios }}</span>
            <span class="stat-label">Usuarios</span>
          </div>
        </div>
        <div class="stat-card roles">
          <div class="stat-icon"><ion-icon name="shield-outline" /></div>
          <div class="stat-info">
            <span class="stat-value">{{ stats().roles }}</span>
            <span class="stat-label">Roles</span>
          </div>
        </div>
        <div class="stat-card backup">
          <div class="stat-icon"><ion-icon name="server-outline" /></div>
          <div class="stat-info">
            <span class="stat-value">Hoy</span>
            <span class="stat-label">Último Respaldo</span>
          </div>
        </div>
        <div class="stat-card alerts">
          <div class="stat-icon"><ion-icon name="lock-closed-outline" /></div>
          <div class="stat-info">
            <span class="stat-value">{{ stats().alertas }}</span>
            <span class="stat-label">Alertas</span>
          </div>
        </div>
        <div class="stat-card status">
          <div class="stat-icon"><ion-icon name="checkmark-circle-outline" /></div>
          <div class="stat-info">
            <span class="stat-value">{{ stats().estado }}</span>
            <span class="stat-label">Estado</span>
          </div>
        </div>
      </div>

      <div class="section-header">
        <h2>Acceso Rápido</h2>
        <p class="section-subtitle">Módulos de configuración del sistema</p>
      </div>

      <div class="cards-grid">
        <a *ngFor="let card of cards()"
           [routerLink]="card.ruta"
           class="quick-card"
           [style.--card-color]="card.color">
          <div class="card-icon-wrapper" [style.background]="card.color + '18'">
            <ion-icon [name]="card.icono" class="card-icon" [style.color]="card.color" />
          </div>
          <div class="card-body">
            <h3>{{ card.titulo }}</h3>
            <p>{{ card.descripcion }}</p>
          </div>
          <div class="card-arrow">→</div>
        </a>
      </div>
    </div>
  `,
  styles: [`
    .dashboard {
      max-width: 1200px;
      margin: 0 auto;
      font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
    }

    .dashboard-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 28px;
    }

    .dashboard-header h1 {
      font-size: 24px;
      font-weight: 700;
      color: var(--text-primary);
      margin: 0;
      letter-spacing: -0.3px;
    }

    .subtitle {
      color: var(--text-secondary);
      font-size: 14px;
      margin: 4px 0 0 0;
    }

    .stats-row {
      display: grid;
      grid-template-columns: repeat(5, 1fr);
      gap: 16px;
      margin-bottom: 32px;
    }

    .stat-card {
      background: var(--bg-surface);
      border-radius: 12px;
      padding: 20px;
      display: flex;
      align-items: center;
      gap: 16px;
      border: 1px solid var(--border-color);
      transition: all 0.25s;
      box-shadow: 0 1px 3px rgba(0,0,0,0.04);
    }

    .stat-card:hover {
      transform: translateY(-2px);
      box-shadow: 0 8px 24px rgba(0,0,0,0.08);
      border-color: var(--border-color);
    }

    .stat-icon {
      width: 44px;
      height: 44px;
      border-radius: 10px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 22px;
      flex-shrink: 0;
    }

    .stat-card.users .stat-icon {
      background: var(--brand-soft);
      color: var(--institutional-blue);
    }

    .stat-card.roles .stat-icon {
      background: #f5f3ff;
      color: #8b5cf6;
    }

    .stat-card.backup .stat-icon {
      background: #f0fdfa;
      color: #14b8a6;
    }

    .stat-card.alerts .stat-icon {
      background: #fef2f2;
      color: #ef4444;
    }

    .stat-card.status .stat-icon {
      background: #f0fdf4;
      color: #22c55e;
    }

    .stat-info {
      display: flex;
      flex-direction: column;
    }

    .stat-value {
      font-size: 20px;
      font-weight: 700;
      color: var(--text-primary);
      line-height: 1.2;
    }

    .stat-label {
      font-size: 13px;
      color: var(--text-secondary);
      font-weight: 500;
    }

    .section-header {
      margin-bottom: 16px;
    }

    .section-header h2 {
      font-size: 18px;
      font-weight: 600;
      color: var(--text-primary);
      margin: 0;
    }

    .section-subtitle {
      color: var(--text-secondary);
      font-size: 13px;
      margin: 4px 0 0 0;
    }

    .cards-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
      gap: 16px;
    }

    .quick-card {
      background: var(--bg-surface);
      border-radius: 12px;
      padding: 20px;
      display: flex;
      align-items: flex-start;
      gap: 16px;
      border: 1px solid var(--border-color);
      box-shadow: 0 1px 3px rgba(0,0,0,0.04);
      transition: all 0.25s;
      cursor: pointer;
      text-decoration: none;
      position: relative;
    }

    .quick-card:hover {
      transform: translateY(-3px);
      box-shadow: 0 12px 32px rgba(0,0,0,0.08);
      border-color: var(--card-color, var(--institutional-blue));
    }

    .card-icon-wrapper {
      width: 48px;
      height: 48px;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }

    .card-icon {
      font-size: 24px;
    }

    .card-body {
      flex: 1;
      min-width: 0;
    }

    .card-body h3 {
      font-size: 15px;
      font-weight: 600;
      color: var(--text-primary);
      margin: 0 0 4px 0;
    }

    .card-body p {
      font-size: 13px;
      color: var(--text-secondary);
      margin: 0;
      line-height: 1.4;
    }

    .card-stats {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      margin-top: 10px;
    }

    .stat-chip {
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 6px;
      padding: 3px 10px;
      font-size: 12px;
      color: #475569;
    }

    .stat-chip strong {
      color: #0f172a;
      font-weight: 600;
    }

    .card-arrow {
      color: #cbd5e1;
      transition: all 0.2s;
      flex-shrink: 0;
      margin-top: 2px;
    }

    .quick-card:hover .card-arrow {
      color: var(--card-color, var(--institutional-blue));
      transform: translateX(2px);
    }
  `],
})
export class ConfigDashboardComponent implements OnInit {
  protected cards = signal<QuickAccessCard[]>([]);
  protected stats = signal<{ usuarios: number; roles: number; ultimoRespaldo: string; alertas: number; estado: string }>({
    usuarios: 0,
    roles: 0,
    ultimoRespaldo: '',
    alertas: 0,
    estado: '',
  });

  private dataService = inject(ConfigDataService);
  private destroyRef = inject(DestroyRef);

  ngOnInit() {
    this.cards.set(this.dataService.getQuickAccessCards());
    this.dataService.getDashboardStats().pipe(takeUntilDestroyed(this.destroyRef)).subscribe((s: { usuarios: number; roles: number; ultimoRespaldo: string; alertas: number; estado: string }) => this.stats.set(s));
  }

}
