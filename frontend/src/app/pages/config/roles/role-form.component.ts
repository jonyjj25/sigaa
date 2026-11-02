import { Component, OnInit, ChangeDetectionStrategy, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, ActivatedRoute } from '@angular/router';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { IonIcon, AlertController } from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { saveOutline, closeOutline, shieldOutline } from 'ionicons/icons';
import { ConfigDataService } from '../services/config-data.service';

@Component({
  selector: 'app-role-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, IonIcon],
  template: `
    <div class="page-container">
      <div class="page-header">
        <div class="header-left">
          <div class="header-icon">
            <ion-icon name="shield-outline" />
          </div>
          <div>
            <h1>{{ isEditing() ? 'Editar Rol' : 'Nuevo Rol' }}</h1>
            <p class="subtitle">{{ isEditing() ? 'Modifique los datos del rol' : 'Complete los datos para crear un nuevo rol' }}</p>
          </div>
        </div>
      </div>

      <div class="form-card">
        <form [formGroup]="roleForm" (ngSubmit)="onSubmit()">
          <div class="form-grid">
            <div class="form-group">
              <label for="nombre">Nombre</label>
              <input
                id="nombre"
                type="text"
                formControlName="nombre"
                placeholder="Ej: Administrador"
                class="form-input"
              />
            </div>

            <div class="form-group">
              <label for="descripcion">Descripción</label>
              <textarea
                id="descripcion"
                formControlName="descripcion"
                placeholder="Describa el propósito del rol"
                class="form-input form-textarea"
                rows="4"
              ></textarea>
            </div>

            <div class="form-group toggle-group">
              <label>Estado</label>
              <div class="toggle-wrapper">
                <span class="toggle-label">{{ roleForm.controls.estado.value ? 'Activo' : 'Inactivo' }}</span>
                <button
                  type="button"
                  class="toggle-btn"
                  [class.toggled]="roleForm.controls.estado.value"
                  (click)="roleForm.controls.estado.setValue(!roleForm.controls.estado.value)"
                >
                  <div class="toggle-thumb"></div>
                </button>
              </div>
            </div>
          </div>

          <div class="form-actions">
            <button type="button" class="btn-cancel" (click)="onCancel()">
              <ion-icon name="close-outline" />
              Cancelar
            </button>
            <button type="submit" class="btn-primary" [disabled]="roleForm.invalid">
              <ion-icon name="save-outline" />
              {{ isEditing() ? 'Actualizar' : 'Crear' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  `,
  styles: [`
    .page-container {
      font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
      max-width: 720px;
      margin: 0 auto;
    }

    .page-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 24px;
    }

    .header-left {
      display: flex;
      align-items: center;
      gap: 16px;
    }

    .header-icon {
      width: 48px;
      height: 48px;
      background: #f5f3ff;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 24px;
      color: #8b5cf6;
    }

    .page-header h1 {
      font-size: 24px;
      font-weight: 700;
      color: #0f172a;
      margin: 0;
      letter-spacing: -0.3px;
    }

    .subtitle {
      color: #64748b;
      font-size: 14px;
      margin: 4px 0 0 0;
    }

    .form-card {
      background: #fff;
      border-radius: 12px;
      border: 1px solid #e2e8f0;
      padding: 32px;
      box-shadow: 0 1px 3px rgba(0,0,0,0.04);
    }

    .form-grid {
      display: flex;
      flex-direction: column;
      gap: 24px;
    }

    .form-group {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }

    .form-group label {
      font-size: 13px;
      font-weight: 600;
      color: #374151;
    }

    .form-input {
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      padding: 10px 14px;
      font-size: 14px;
      color: #0f172a;
      background: #f8fafc;
      outline: none;
      transition: all 0.2s;
      font-family: inherit;
    }

    .form-input:focus {
      border-color: #8b5cf6;
      background: #fff;
      box-shadow: 0 0 0 3px rgba(139, 92, 246, 0.1);
    }

    .form-textarea {
      resize: vertical;
    }

    .toggle-group {
      gap: 10px;
    }

    .toggle-wrapper {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .toggle-label {
      font-size: 14px;
      color: #475569;
      font-weight: 500;
      min-width: 60px;
    }

    .toggle-btn {
      width: 44px;
      height: 24px;
      border-radius: 12px;
      border: none;
      background: #cbd5e1;
      cursor: pointer;
      position: relative;
      transition: all 0.3s;
      padding: 0;
    }

    .toggle-btn.toggled {
      background: #8b5cf6;
    }

    .toggle-thumb {
      width: 20px;
      height: 20px;
      border-radius: 50%;
      background: #fff;
      position: absolute;
      top: 2px;
      left: 2px;
      transition: all 0.3s;
      box-shadow: 0 1px 3px rgba(0,0,0,0.15);
    }

    .toggle-btn.toggled .toggle-thumb {
      left: 22px;
    }

    .form-actions {
      display: flex;
      justify-content: flex-end;
      gap: 12px;
      margin-top: 32px;
      padding-top: 24px;
      border-top: 1px solid #e2e8f0;
    }

    .btn-primary {
      display: flex;
      align-items: center;
      gap: 8px;
      background: #8b5cf6;
      color: #fff;
      border: none;
      padding: 10px 20px;
      border-radius: 8px;
      font-size: 14px;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.2s;
      font-family: inherit;
    }

    .btn-primary:hover:not(:disabled) {
      background: #7c3aed;
      transform: translateY(-1px);
      box-shadow: 0 4px 12px rgba(139, 92, 246, 0.35);
    }

    .btn-primary:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }

    .btn-primary ion-icon {
      font-size: 18px;
    }

    .btn-cancel {
      display: flex;
      align-items: center;
      gap: 8px;
      background: #fff;
      color: #64748b;
      border: 1px solid #e2e8f0;
      padding: 10px 20px;
      border-radius: 8px;
      font-size: 14px;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.2s;
      font-family: inherit;
    }

    .btn-cancel:hover {
      background: #f8fafc;
      border-color: #94a3b8;
    }

    .btn-cancel ion-icon {
      font-size: 18px;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RoleFormComponent implements OnInit {
  private fb = inject(FormBuilder);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private dataService = inject(ConfigDataService);
  private alertCtrl = inject(AlertController);

  protected isEditing = signal(false);
  private roleId: string | null = null;

  protected roleForm = this.fb.group({
    nombre: ['', Validators.required],
    descripcion: [''],
    estado: [true],
  });

  constructor() {
    addIcons({ saveOutline, closeOutline, shieldOutline });
  }

  ngOnInit() {
    this.roleId = this.route.snapshot.paramMap.get('id');
    if (this.roleId) {
      this.isEditing.set(true);
      this.dataService.getRoles().subscribe(roles => {
        const role = roles.find(r => r.id === this.roleId);
        if (role) {
          this.roleForm.setValue({ nombre: role.nombre, descripcion: role.descripcion, estado: role.estado });
        }
      });
    }
  }

  protected onSubmit() {
    if (this.roleForm.invalid) return;
    const formValue = this.roleForm.value;
    const data = { nombre: formValue.nombre!, descripcion: formValue.descripcion!, activo: formValue.estado! };

    if (this.isEditing()) {
      this.dataService.updateRole(this.roleId!, data).subscribe({
        next: () => this.router.navigate(['/config/roles']),
        error: err => {
          const msg = err.error?.message || err.message || 'Error al actualizar rol';
          this.alertCtrl.create({ header: 'Error', message: msg, buttons: ['OK'] }).then(a => a.present());
        },
      });
    } else {
      this.dataService.createRole(data).subscribe({
        next: () => this.router.navigate(['/config/roles']),
        error: err => {
          const msg = err.error?.message || err.message || 'Error al crear rol';
          this.alertCtrl.create({ header: 'Error', message: msg, buttons: ['OK'] }).then(a => a.present());
        },
      });
    }
  }

  protected onCancel() {
    this.router.navigate(['/config/roles']);
  }
}
