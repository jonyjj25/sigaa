import { Component, inject, signal, ChangeDetectionStrategy, OnInit, DestroyRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, ActivatedRoute, Router } from '@angular/router';
import { IonIcon } from '@ionic/angular/standalone';
import { AlertController } from '@ionic/angular';
import { addIcons } from 'ionicons';
import {
  createOutline, timeOutline, arrowBackOutline, mailOutline,
  briefcaseOutline, peopleOutline, businessOutline,
  calendarOutline, logInOutline, layersOutline, trashOutline, trashBinOutline,
} from 'ionicons/icons';
import { ConfigDataService } from '../services/config-data.service';
import { ConfigUser } from '../models/config.models';

@Component({
  selector: 'app-user-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, IonIcon],
  template: `
    <div class="page-container">
      <div class="page-header">
        <div class="header-left">
          <button type="button" class="btn-back" routerLink="/config/users" title="Volver" aria-label="Volver a usuarios">
            <ion-icon name="arrow-back-outline" aria-hidden="true" />
          </button>
          <div class="header-icon"><ion-icon name="people-outline" /></div>
          <div>
            <h1>{{user()?.nombre}} {{user()?.apellido}}</h1>
            <p class="subtitle">Detalle del usuario</p>
          </div>
        </div>
      </div>

      @if (user(); as u) {
        <div class="profile-header">
          <div class="profile-avatar">{{u.nombre.charAt(0)}}{{u.apellido.charAt(0)}}</div>
          <div class="profile-info">
            <h2>{{u.nombre}} {{u.apellido}}</h2>
            @if (u.rol.toLowerCase() === 'superadmin') {
              <p class="profile-rol">Superadmin del sistema</p>
            } @else if (u.rol.toLowerCase() === 'administrador') {
              <p class="profile-rol">Administrador</p>
            }
            <span class="status-badge" [class.active]="u.estado === 'activo'" [class.inactive]="u.estado === 'inactivo'" [class.blocked]="u.estado === 'bloqueado'">
              {{u.estado === 'activo' ? 'Activo' : u.estado === 'inactivo' ? 'Inactivo' : 'Bloqueado'}}
            </span>
          </div>
        </div>

        <div class="detail-grid">
          <div class="detail-card">
            <div class="card-header">
              <ion-icon name="briefcase-outline" />
              <span>Información General</span>
            </div>
            <div class="card-body">
              <div class="info-row">
                <div class="info-icon"><ion-icon name="people-outline" /></div>
                <div class="info-content">
                  <span class="info-label">Nombre de usuario</span>
                  <span class="info-value">{{u.username}}</span>
                </div>
              </div>
              <div class="info-row">
                <div class="info-icon"><ion-icon name="mail-outline" /></div>
                <div class="info-content">
                  <span class="info-label">Correo Electrónico</span>
                  <span class="info-value">{{u.correo}}</span>
                </div>
              </div>
              <div class="info-row">
                <div class="info-icon"><ion-icon name="people-outline" /></div>
                <div class="info-content">
                  <span class="info-label">Rol Global</span>
                  @if (u.rol) {
                    <span class="badge" [class.badge-admin]="u.rol.toLowerCase().includes('admin')" [class.badge-secondary]="!u.rol.toLowerCase().includes('admin')">
                      {{capitalize(u.rol)}}
                    </span>
                  } @else {
                    <span class="info-value">Ninguno</span>
                  }
                </div>
              </div>
            </div>
          </div>

          <div class="detail-card">
            <div class="card-header">
              <ion-icon name="calendar-outline" />
              <span>Actividad</span>
            </div>
            <div class="card-body">
              <div class="info-row">
                <div class="info-icon"><ion-icon name="calendar-outline" /></div>
                <div class="info-content">
                  <span class="info-label">Fecha de Creación</span>
                  <span class="info-value">{{u.creado | date:'mediumDate'}}</span>
                </div>
              </div>
              <div class="info-row">
                <div class="info-icon"><ion-icon name="log-in-outline" /></div>
                <div class="info-content">
                  <span class="info-label">Último Acceso</span>
                  <span class="info-value">{{u.ultimoAcceso ? (u.ultimoAcceso | date:'mediumDate') : 'Nunca'}}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="detail-card full-width">
          <div class="card-header">
            <ion-icon name="layers-outline" />
            <span>Unidades Asignadas</span>
          </div>
          <div class="card-body">
            @if (u.unidades.length === 0) {
              <p class="text-muted">No tiene unidades asignadas</p>
            }
            @for (m of u.unidades; track m.unidadId) {
              <div class="membresia-row">
                <div class="membresia-icon">
                  <ion-icon name="business-outline" />
                </div>
                <div class="membresia-info">
                  <span class="membresia-unidad">{{m.unidadNombre || 'Sistema'}}</span>
                  <span class="membresia-rol">{{m.rolNombre}}</span>
                </div>
              </div>
            }
          </div>
        </div>

        <div class="action-bar">
          <a class="btn-primary" [routerLink]="['/config/users', u.id, 'edit']">
            <ion-icon name="create-outline" />
            Editar
          </a>
          <a class="btn-secondary" [routerLink]="['/config/users', u.id, 'history']">
            <ion-icon name="time-outline" />
            Historial
          </a>
          <button class="btn-danger" (click)="deleteUser(u)" [disabled]="u.rol.toLowerCase() === 'superadmin'">
            <ion-icon name="trash-outline" />
            Desactivar
          </button>
          <button class="btn-danger-permanent" (click)="deleteUserPermanently(u)" [disabled]="u.rol.toLowerCase() === 'superadmin'">
            <ion-icon name="trash-bin-outline" />
            Eliminar permanentemente
          </button>
          <a class="btn-ghost" routerLink="/config/users">
            <ion-icon name="arrow-back-outline" />
            Volver
          </a>
        </div>
      } @else {
        <div class="loading-state">
          <p>Cargando información del usuario...</p>
        </div>
      }
    </div>
  `,
  styles: [`
    .page-container {
      font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
      max-width: 900px; margin: 0 auto;
    }
    .page-header {
      display: flex; justify-content: space-between; align-items: center;
      margin-bottom: 28px;
    }
    .header-left { display: flex; align-items: center; gap: 16px; }
    .btn-back {
      width: 40px; height: 40px; border: 1px solid #e2e8f0;
      border-radius: 10px; background: #fff; cursor: pointer;
      display: flex; align-items: center; justify-content: center;
      color: #64748b; font-size: 20px; transition: all 0.2s;
      flex-shrink: 0; text-decoration: none;
    }
    .btn-back:hover { background: #f8fafc; color: var(--institutional-blue); border-color: var(--institutional-blue); transform: translateY(-1px); }
    .header-icon {
      width: 48px; height: 48px; background: linear-gradient(135deg, var(--institutional-blue), var(--institutional-blue));
      border-radius: 12px; display: flex; align-items: center; justify-content: center;
      font-size: 24px; color: #fff; flex-shrink: 0;
      box-shadow: 0 4px 12px rgba(59,130,246,0.25);
    }
    .page-header h1 {
      font-size: 24px; font-weight: 700; color: #0f172a;
      margin: 0; letter-spacing: -0.3px;
    }
    .subtitle { color: #64748b; font-size: 14px; margin: 4px 0 0 0; }
    .profile-header {
      display: flex; align-items: center; gap: 24px;
      background: #fff; border-radius: 16px; padding: 32px;
      border: 1px solid #e2e8f0; margin-bottom: 24px;
      box-shadow: 0 1px 3px rgba(0,0,0,0.04), 0 4px 16px rgba(0,0,0,0.03);
      transition: box-shadow 0.25s;
    }
    .profile-header:hover { box-shadow: 0 2px 6px rgba(0,0,0,0.06), 0 8px 24px rgba(0,0,0,0.06); }
    .profile-avatar {
      width: 76px; height: 76px; border-radius: 50%;
      background: linear-gradient(135deg, var(--brand-soft), var(--brand-soft)); color: var(--institutional-blue);
      display: flex; align-items: center; justify-content: center;
      font-size: 28px; font-weight: 700; flex-shrink: 0;
      box-shadow: 0 4px 14px rgba(59,130,246,0.2);
    }
    .profile-info h2 { margin: 0; font-size: 22px; font-weight: 700; color: #0f172a; letter-spacing: -0.2px; }
    .profile-rol { margin: 4px 0 12px; color: #64748b; font-size: 14px; }
    .status-badge {
      display: inline-flex; align-items: center; padding: 5px 14px;
      border-radius: 20px; font-size: 13px; font-weight: 600;
    }
    .status-badge.active { background: #f0fdf4; color: #16a34a; }
    .status-badge.inactive { background: #fef3c7; color: #d97706; }
    .status-badge.blocked { background: #fef2f2; color: #dc2626; }
    .detail-grid {
      display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-bottom: 20px;
    }
    .detail-card {
      background: #fff; border-radius: 14px;
      border: 1px solid #e2e8f0; overflow: hidden;
      box-shadow: 0 1px 3px rgba(0,0,0,0.04);
      transition: box-shadow 0.25s, transform 0.25s;
    }
    .detail-card:hover { box-shadow: 0 2px 6px rgba(0,0,0,0.06), 0 6px 16px rgba(0,0,0,0.05); }
    .detail-card.full-width { grid-column: 1 / -1; margin-bottom: 20px; }
    .card-header {
      display: flex; align-items: center; gap: 8px;
      padding: 16px 20px; border-bottom: 1px solid #f1f5f9;
      font-size: 15px; font-weight: 600; color: #0f172a;
    }
    .card-header ion-icon { font-size: 18px; color: var(--institutional-blue); }
    .card-body { padding: 8px 20px; }
    .info-row {
      display: flex; align-items: center; gap: 14px;
      padding: 14px 0; border-bottom: 1px solid #f1f5f9;
    }
    .info-row:last-child { border-bottom: none; }
    .info-icon {
      width: 38px; height: 38px; border-radius: 10px;
      background: #f8fafc; display: flex; align-items: center;
      justify-content: center; flex-shrink: 0;
    }
    .info-icon ion-icon { font-size: 18px; color: #64748b; }
    .info-content { display: flex; flex-direction: column; gap: 3px; }
    .info-label {
      font-size: 11px; color: #94a3b8; text-transform: uppercase;
      letter-spacing: 0.4px; font-weight: 600;
    }
    .info-value { font-size: 14px; color: #334155; font-weight: 500; }
    .roles-list { display: flex; flex-wrap: wrap; gap: 4px; margin-top: 4px; }
    .badge {
      display: inline-flex; align-items: center;
      padding: 3px 10px; border-radius: 6px;
      font-size: 12px; font-weight: 600; white-space: nowrap;
    }
    .badge-secondary { background: #f1f5f9; color: #64748b; }
    .badge-admin { background: #fef2f2; color: #dc2626; }
    .text-muted { color: #94a3b8; font-size: 14px; padding: 12px 0; margin: 0; }
    .membresia-row {
      display: flex; align-items: center; gap: 14px;
      padding: 14px 0; border-bottom: 1px solid #f1f5f9;
    }
    .membresia-row:last-child { border-bottom: none; }
    .membresia-icon {
      width: 38px; height: 38px; border-radius: 10px;
      background: #f0fdf4; display: flex; align-items: center;
      justify-content: center; flex-shrink: 0;
    }
    .membresia-icon ion-icon { font-size: 18px; color: #16a34a; }
    .membresia-info { display: flex; flex-direction: column; gap: 3px; }
    .membresia-unidad { font-size: 14px; color: #334155; font-weight: 600; }
    .membresia-rol { font-size: 12px; color: #64748b; }
    .action-bar {
      display: flex; gap: 12px; justify-content: center; flex-wrap: wrap;
      margin-top: 8px;
    }
    a.btn-primary, a.btn-secondary, a.btn-ghost, button.btn-danger, button.btn-danger-permanent {
      display: inline-flex; align-items: center; gap: 8px;
      padding: 10px 20px; border-radius: 10px;
      font-size: 14px; font-weight: 600; cursor: pointer;
      transition: all 0.25s; text-decoration: none; font-family: inherit;
    }
    a.btn-primary {
      background: linear-gradient(135deg, var(--institutional-blue), var(--institutional-blue)); color: #fff; border: none;
      box-shadow: 0 4px 14px rgba(59,130,246,0.3);
    }
    a.btn-primary:hover { transform: translateY(-1px); box-shadow: 0 6px 20px rgba(59,130,246,0.4); }
    a.btn-secondary { background: #fff; color: var(--institutional-blue); border: 1px solid var(--institutional-blue); }
    a.btn-secondary:hover { background: var(--brand-soft); transform: translateY(-1px); }
    button.btn-danger { background: #fff; color: #dc2626; border: 1px solid #fecaca; }
    button.btn-danger:hover:not(:disabled) { background: #fef2f2; border-color: #dc2626; transform: translateY(-1px); }
    button.btn-danger:disabled { opacity: 0.3; cursor: not-allowed; }
    button.btn-danger-permanent { background: #dc2626; color: #fff; border: 1px solid #dc2626; }
    button.btn-danger-permanent:hover:not(:disabled) { background: #b91c1c; border-color: #b91c1c; transform: translateY(-1px); box-shadow: 0 4px 12px rgba(220,38,38,0.35); }
    button.btn-danger-permanent:disabled { opacity: 0.3; cursor: not-allowed; }
    a.btn-ghost { background: #fff; color: #64748b; border: 1px solid #e2e8f0; }
    a.btn-ghost:hover { background: #f8fafc; color: #334155; transform: translateY(-1px); }
    .loading-state { text-align: center; padding: 60px; color: #94a3b8; }
    @media (max-width: 640px) {
      .detail-grid { grid-template-columns: 1fr; }
      .profile-header { flex-direction: column; text-align: center; }
      .action-bar { flex-direction: column; align-items: stretch; }
      .action-bar a, .action-bar button { justify-content: center; }
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserDetailComponent implements OnInit {
  private configDataService = inject(ConfigDataService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private alertCtrl = inject(AlertController);
  private destroyRef = inject(DestroyRef);

  protected user = signal<ConfigUser | null>(null);

  protected capitalize(val: string): string {
    return val.charAt(0).toUpperCase() + val.slice(1);
  }

  protected async deleteUser(user: ConfigUser): Promise<void> {
    const alert = await this.alertCtrl.create({
      header: 'Desactivar usuario',
      message: `¿Estás seguro de desactivar a ${user.nombre ?? ''} ${user.apellido ?? ''}? El usuario no podrá iniciar sesión, pero sus datos se conservarán.`,
      buttons: [
        { text: 'Cancelar', role: 'cancel' },
        {
          text: 'Desactivar', role: 'destructive', handler: () => {
            this.configDataService.deleteUser(user.id).subscribe({
              next: () => this.router.navigate(['/config/users']),
              error: (err: any) => console.error('Error al desactivar usuario', err),
            });
          }
        },
      ],
    });
    await alert.present();
  }

  protected async deleteUserPermanently(user: ConfigUser): Promise<void> {
    const alert = await this.alertCtrl.create({
      header: 'Eliminar permanentemente',
      message: `¿Estás seguro de eliminar permanentemente a ${user.nombre ?? ''} ${user.apellido ?? ''}? Esta acción es irreversible: se eliminarán todos los datos del usuario, incluidas sus membresías y roles.`,
      buttons: [
        { text: 'Cancelar', role: 'cancel' },
        {
          text: 'Eliminar permanentemente', role: 'destructive', handler: () => {
            this.configDataService.deleteUserPermanently(user.id).subscribe({
              next: () => this.router.navigate(['/config/users']),
              error: async (err: any) => {
                const msg = err.error?.error || err.error || err.message || 'Error al eliminar el usuario';
                const errorAlert = await this.alertCtrl.create({
                  header: 'Error',
                  message: typeof msg === 'string' ? msg : JSON.stringify(msg),
                  buttons: ['OK'],
                });
                await errorAlert.present();
              },
            });
          }
        },
      ],
    });
    await alert.present();
  }

  constructor() {
    addIcons({
      createOutline, timeOutline, arrowBackOutline, mailOutline,
      briefcaseOutline, peopleOutline, businessOutline,
      calendarOutline, logInOutline, layersOutline, trashOutline, trashBinOutline,
    });
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      const sub = this.configDataService.getUsers().subscribe(users => {
        const found = users.find(u => u.id === id);
        if (found) this.user.set(found);
      });
      this.destroyRef.onDestroy(() => sub.unsubscribe());
    }
  }
}
