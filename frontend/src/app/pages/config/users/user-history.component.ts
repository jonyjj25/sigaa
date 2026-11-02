import { Component, inject, signal, ChangeDetectionStrategy, OnInit, DestroyRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, ActivatedRoute } from '@angular/router';
import {
  IonHeader, IonToolbar, IonTitle, IonButtons, IonButton, IonIcon,
  IonContent, IonCard, IonCardContent,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { arrowBackOutline, timeOutline, personAddOutline, createOutline, lockClosedOutline, logInOutline, alertCircleOutline, logOutOutline, keyOutline } from 'ionicons/icons';
import { ConfigDataService } from '../services/config-data.service';
import { INSTITUTIONAL_BLUE } from '../../../shared/ui-colors';

interface HistoryEntry {
  id: string;
  accion: string;
  detalle: string;
  fecha: Date;
  icono: string;
  color: string;
}

@Component({
  selector: 'app-user-history',
  standalone: true,
  imports: [
    CommonModule, RouterLink,
    IonHeader, IonToolbar, IonTitle, IonButtons, IonButton, IonIcon,
    IonContent, IonCard, IonCardContent,
  ],
  template: `
    <ion-header>
      <ion-toolbar color="primary">
        <ion-buttons slot="start">
          <ion-button aria-label="Volver al detalle del usuario" [routerLink]="['/config/users', userId()]" title="Volver al detalle del usuario">
            <ion-icon name="arrow-back-outline" slot="icon-only" aria-hidden="true"></ion-icon>
          </ion-button>
        </ion-buttons>
        <ion-title>Historial de {{userName()}}</ion-title>
      </ion-toolbar>
    </ion-header>
    <ion-content>
      <div class="history-container">
        @if (userName()) {
          <div class="user-summary">
            <div class="summary-avatar">{{userName().charAt(0)}}</div>
            <div class="summary-info">
              <h2>{{userName()}}</h2>
              <p>{{userEmail()}}</p>
            </div>
          </div>
        }

        <ion-card>
          <ion-card-content class="timeline-card">
            <div class="timeline">
              @for (entry of history(); track entry.id) {
                <div class="timeline-item">
                  <div class="timeline-dot" [style.background]="entry.color">
                    <ion-icon [name]="entry.icono"></ion-icon>
                  </div>
                  <div class="timeline-content">
                    <div class="timeline-header">
                      <span class="timeline-action">{{entry.accion}}</span>
                      <span class="timeline-date">{{entry.fecha | date:'medium'}}</span>
                    </div>
                    <p class="timeline-detail">{{entry.detalle}}</p>
                  </div>
                </div>
              }
            </div>
          </ion-card-content>
        </ion-card>

        <div class="back-action">
          <ion-button color="medium" [routerLink]="['/config/users', userId()]" fill="outline">
            <ion-icon slot="start" name="arrow-back-outline"></ion-icon>
            Volver al usuario
          </ion-button>
        </div>
      </div>
    </ion-content>
  `,
  styles: [`
    :host {
      --gray-50: #f8fafc;
      --gray-100: #f1f5f9;
      --gray-200: #e2e8f0;
      --gray-300: #cbd5e1;
      --gray-400: #94a3b8;
      --gray-500: #64748b;
      --gray-600: #475569;
      --gray-700: #334155;
      --gray-800: #1e293b;
      --primary: var(--institutional-blue);
      --primary-light: var(--brand-soft);
      --radius: 12px;
      --shadow: 0 1px 3px rgba(0,0,0,0.06), 0 2px 8px rgba(0,0,0,0.04);
    }
    ion-content { --background: #f8fafc; }
    .history-container {
      font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
      max-width: 800px;
      margin: 0 auto;
      padding: 28px;
      background: #f8fafc;
      min-height: 100%;
    }
    .user-summary {
      display: flex;
      align-items: center;
      gap: 16px;
      background: #fff;
      border-radius: 16px;
      padding: 24px;
      box-shadow: var(--shadow);
      margin-bottom: 24px;
      border: 1px solid var(--gray-200);
      transition: box-shadow 0.25s;
    }
    .user-summary:hover { box-shadow: 0 2px 6px rgba(0,0,0,0.08), 0 8px 24px rgba(0,0,0,0.06); }
    .summary-avatar {
      width: 52px;
      height: 52px;
      border-radius: 50%;
      background: linear-gradient(135deg, var(--brand-soft), var(--brand-soft));
      color: var(--primary);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 22px;
      font-weight: 700;
      flex-shrink: 0;
      box-shadow: 0 4px 12px rgba(37,99,235,0.15);
    }
    .summary-info h2 {
      margin: 0;
      font-size: 18px;
      font-weight: 700;
      color: var(--gray-800);
      letter-spacing: -0.2px;
    }
    .summary-info p {
      margin: 4px 0 0;
      font-size: 13px;
      color: var(--gray-400);
    }
    ion-card {
      border-radius: var(--radius);
      box-shadow: var(--shadow);
      margin: 0 0 24px;
      border: 1px solid var(--gray-200);
    }
    .timeline-card {
      padding: 28px;
    }
    .timeline {
      position: relative;
    }
    .timeline::before {
      content: '';
      position: absolute;
      left: 23px;
      top: 0;
      bottom: 0;
      width: 2px;
      background: var(--gray-200);
    }
    .timeline-item {
      display: flex;
      gap: 16px;
      padding-bottom: 28px;
      position: relative;
    }
    .timeline-item:last-child { padding-bottom: 0; }
    .timeline-dot {
      width: 48px;
      height: 48px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
      z-index: 1;
      position: relative;
      box-shadow: 0 2px 8px rgba(0,0,0,0.15);
      transition: transform 0.2s;
    }
    .timeline-item:hover .timeline-dot { transform: scale(1.08); }
    .timeline-dot ion-icon {
      font-size: 20px;
      color: #fff;
    }
    .timeline-content {
      flex: 1;
      background: #fff;
      border-radius: var(--radius);
      padding: 16px 20px;
      border: 1px solid var(--gray-100);
      transition: box-shadow 0.2s;
    }
    .timeline-item:hover .timeline-content { box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
    .timeline-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 12px;
      margin-bottom: 6px;
    }
    .timeline-action {
      font-weight: 600;
      font-size: 14px;
      color: var(--gray-700);
    }
    .timeline-date {
      font-size: 12px;
      color: var(--gray-400);
      white-space: nowrap;
    }
    .timeline-detail {
      margin: 0;
      font-size: 13px;
      color: var(--gray-500);
      line-height: 1.5;
    }
    .back-action {
      text-align: center;
    }
    @media (max-width: 640px) {
      .history-container { padding: 16px; }
      .timeline-header { flex-direction: column; align-items: flex-start; gap: 4px; }
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserHistoryComponent implements OnInit {
  private configDataService = inject(ConfigDataService);
  private route = inject(ActivatedRoute);
  private destroyRef = inject(DestroyRef);

  protected userId = signal<string | null>(null);
  protected userName = signal('');
  protected userEmail = signal('');

  protected history = signal<HistoryEntry[]>([]);

  constructor() {
    addIcons({
      arrowBackOutline, timeOutline, personAddOutline, createOutline,
      lockClosedOutline, logInOutline, alertCircleOutline, logOutOutline, keyOutline,
    });
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.userId.set(id);
      const sub = this.configDataService.getUsers().subscribe(users => {
        const user = users.find(u => u.id === id);
        if (user) {
          this.userName.set(`${user.nombre} ${user.apellido}`);
          this.userEmail.set(user.correo);
        }
      });
      this.destroyRef.onDestroy(() => sub.unsubscribe());

      const historySub = this.configDataService.getUserHistory(id).subscribe(logs => {
        const actionMap: Record<string, { icono: string; color: string }> = {
          'CREAR': { icono: 'person-add-outline', color: '#10b981' },
          'ACTUALIZAR': { icono: 'create-outline', color: INSTITUTIONAL_BLUE },
          'TOGGLE_BLOCK': { icono: 'lock-closed-outline', color: '#f59e0b' },
          'TOGGLE_ACTIVO': { icono: 'alert-circle-outline', color: '#8b5cf6' },
          'CAMBIAR_CONTRASENA': { icono: 'key-outline', color: '#06b6d4' },
          'FORZAR_CIERRE_SESION': { icono: 'log-out-outline', color: '#ef4444' },
          'ELIMINAR_LOGICO': { icono: 'trash-outline', color: '#ef4444' },
          'ELIMINAR_DEFINITIVO': { icono: 'trash-outline', color: '#dc2626' },
        };
        const entries: HistoryEntry[] = logs.map(log => {
          const mapping = actionMap[log.accion] || { icono: 'time-outline', color: '#64748b' };
          return {
            id: String(log.id),
            accion: log.accion,
            detalle: log.detalle || '',
            fecha: new Date(log.fecha),
            icono: mapping.icono,
            color: mapping.color,
          };
        });
        this.history.set(entries);
      });
      this.destroyRef.onDestroy(() => historySub.unsubscribe());
    }
  }
}
