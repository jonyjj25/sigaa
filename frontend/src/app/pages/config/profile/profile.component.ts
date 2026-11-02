import { Component, inject, signal, ChangeDetectionStrategy, OnInit, DestroyRef, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { IonIcon } from '@ionic/angular/standalone';
import { AlertController } from '@ionic/angular';
import { addIcons } from 'ionicons';
import {
  personOutline, mailOutline, phonePortraitOutline, locationOutline,
  briefcaseOutline, saveOutline, arrowBackOutline, idCardOutline,
  cameraOutline, createOutline, trashOutline,
} from 'ionicons/icons';
import { ConfigDataService } from '../services/config-data.service';
import { AuthService } from '../../../services/auth.service';
import { ApiService } from '../../../services/api.service';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, IonIcon],
  template: `
    <div class="page-container">
      <div class="page-header">
        <div class="header-left">
          <div class="header-icon"><ion-icon name="person-outline" /></div>
          <div>
            <span class="eyebrow">Cuenta institucional</span>
            <h1>Mi Perfil</h1>
            <p class="subtitle">Revisa tus datos y mantén actualizada tu información de contacto.</p>
          </div>
        </div>
        <a class="header-back" routerLink="/config"><ion-icon name="arrow-back-outline" />Configuración</a>
      </div>

      @if (loading()) {
        <div class="loading-state">
          <p>Cargando perfil...</p>
        </div>
      } @else if (profile(); as p) {
        <div class="profile-hero">
          <div class="hero-avatar-wrapper">
            @if (previewUrl()) {
              <img class="hero-avatar-img" [src]="previewUrl()" alt="Foto de perfil" />
            } @else if (p.fotografiaRuta) {
              <img class="hero-avatar-img" [src]="p.fotografiaRuta + '?t=' + photoTimestamp()" alt="Foto de perfil" />
            } @else {
              <div class="hero-avatar-initials">{{p.nombre?.charAt(0) ?? ''}}{{p.apellido?.charAt(0) ?? ''}}</div>
            }
            <button type="button" class="photo-change-btn" aria-label="Cambiar foto de perfil" (click)="fileInput.click()" [disabled]="uploadingPhoto()" title="Cambiar foto de perfil">
              <ion-icon name="camera-outline" aria-hidden="true" />
            </button>
            @if (p.fotografiaRuta && !uploadingPhoto()) {
              <button type="button" class="photo-remove-btn" (click)="removePhoto()" aria-label="Eliminar foto de perfil" title="Eliminar foto de perfil"><ion-icon name="trash-outline" aria-hidden="true" /></button>
            }
            @if (uploadingPhoto()) {
              <div class="photo-loading-overlay">
                <div class="spinner"></div>
              </div>
            }
          </div>
          <input #fileInput type="file" accept="image/jpeg,image/png,image/webp" hidden (change)="onPhotoSelected($event)" />
          <div class="hero-info">
            <h2>{{p.nombre}} {{p.apellido}}</h2>
            <p class="hero-email">{{p.correo}}</p>
            <div class="hero-badges">
              @for (role of roles(); track role) {
                <span class="badge" [class.badge-admin]="role.toLowerCase().includes('admin')" [class.badge-unit]="!role.toLowerCase().includes('admin')">{{role}}</span>
              }
            </div>
          </div>
        </div>

        <div class="profile-content">
        <div class="form-card contact-card">
          <div class="card-header">
            <ion-icon name="person-outline" />
            <span>Información de Contacto</span>
          </div>
          <div class="card-body">
            <div class="form-grid">
              <div class="form-field">
                <label class="field-label">Nombre</label>
                <input type="text" class="field-input" [(ngModel)]="editData.nombre" />
              </div>
              <div class="form-field">
                <label class="field-label">Segundo nombre</label>
                <input type="text" class="field-input" [(ngModel)]="editData.segundoNombre" />
              </div>
              <div class="form-field">
                <label class="field-label">Apellido</label>
                <input type="text" class="field-input" [(ngModel)]="editData.apellido" />
              </div>
              <div class="form-field">
                <label class="field-label">Correo Principal</label>
                <input type="email" class="field-input" [value]="p.correo" disabled />
              </div>
              <div class="form-field">
                <label class="field-label">Usuario</label>
                <input type="text" class="field-input" [value]="p.username" disabled />
              </div>
              <div class="form-field">
                <label class="field-label">Correo Alterno</label>
                <input type="email" class="field-input" [(ngModel)]="editData.correoAlterno" placeholder="correo@alternativo.com" />
              </div>
              <div class="form-field">
                <label class="field-label">Teléfono</label>
                <input type="tel" class="field-input" [(ngModel)]="editData.telefono" placeholder="+507 123-4567" />
              </div>
              <div class="form-field">
                <label class="field-label">Celular</label>
                <input type="tel" class="field-input" [(ngModel)]="editData.celular" placeholder="+507 6123-4567" />
              </div>
            </div>
            <div class="form-field full-width">
              <label class="field-label">Dirección</label>
              <input type="text" class="field-input" [(ngModel)]="editData.direccion" placeholder="Dirección completa" />
            </div>
          </div>
        </div>

        <div class="profile-side">
        <div class="form-card">
          <div class="card-header">
            <ion-icon name="id-card-outline" />
            <span>Información Laboral</span>
          </div>
          <div class="card-body">
            <div class="form-grid professional-grid">
              <div class="form-field">
                <label class="field-label">Documento de identidad</label>
                <input type="text" class="field-input" [(ngModel)]="editData.documentoIdentidad" />
              </div>
              <div class="form-field">
                <label class="field-label">Tipo de documento</label>
                <input type="text" class="field-input" [(ngModel)]="editData.tipoDocumento" />
              </div>
              <div class="form-field">
                <label class="field-label">Cargo</label>
                <input type="text" class="field-input" [(ngModel)]="editData.cargo" />
              </div>
              <div class="form-field">
                <label class="field-label">Institución</label>
                <input type="text" class="field-input" [(ngModel)]="editData.institucion" />
              </div>
              <div class="form-field">
                <label class="field-label">Departamento</label>
                <input type="text" class="field-input" [(ngModel)]="editData.departamento" />
              </div>
              <div class="form-field">
                <label class="field-label">Unidad administrativa</label>
                <input type="text" class="field-input" [(ngModel)]="editData.unidadAdministrativa" />
              </div>
              <div class="form-field full-width">
                <label class="field-label">Código interno</label>
                <input type="text" class="field-input" [(ngModel)]="editData.codigoInterno" />
              </div>
              <div class="form-field full-width">
                <label class="field-label">Observaciones</label>
                <textarea class="field-input field-textarea" rows="3" [(ngModel)]="editData.observaciones"></textarea>
              </div>
            </div>
          </div>
        </div>

        <div class="form-card">
          <div class="card-header">
            <ion-icon name="briefcase-outline" />
            <span>Unidades Asignadas</span>
          </div>
          <div class="card-body">
            @if (p.membresias && p.membresias.length > 0) {
              @for (m of p.membresias; track m.unidadId) {
                <div class="membresia-row">
                  <div class="membresia-icon">
                    <ion-icon name="briefcase-outline" />
                  </div>
                  <div class="membresia-info">
                    <span class="membresia-unidad">{{m.unidadNombre || 'Sistema'}}</span>
                    <span class="membresia-rol">{{m.rolNombre}}</span>
                  </div>
                </div>
              }
            } @else {
              <p class="text-muted">No tiene unidades asignadas</p>
            }
          </div>
        </div>
        </div>
        </div>

        <div class="action-bar">
          <button class="btn-primary" (click)="saveProfile()" [disabled]="saving()">
            <ion-icon name="save-outline" />
            {{saving() ? 'Guardando...' : 'Guardar Cambios'}}
          </button>
          <a class="btn-ghost" routerLink="/dashboard">
            <ion-icon name="arrow-back-outline" />
            Volver
          </a>
        </div>
      }
    </div>
  `,
  styles: [`
    .page-container {
      font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
      max-width: 980px; margin: 0 auto; padding-bottom: 28px;
    }
    .page-header { display: flex; justify-content: space-between; align-items: center; gap: 18px; margin-bottom: 20px; }
    .header-left { display: flex; align-items: center; gap: 14px; }
    .header-icon {
      width: 50px; height: 50px; background: var(--institutional-blue);
      border-radius: 15px; display: flex; align-items: center; justify-content: center;
      font-size: 23px; color: #fff; box-shadow: 0 9px 20px rgba(37,99,235,.22);
    }
    .eyebrow { display: block; margin-bottom: 3px; color: var(--institutional-blue); font-size: 10px; font-weight: 800; letter-spacing: .1em; text-transform: uppercase; }
    .page-header h1 { font-size: 26px; font-weight: 750; color: var(--text-strong); margin: 0; letter-spacing: -0.5px; }
    .subtitle { color: var(--text-secondary); font-size: 13px; margin: 4px 0 0; }
    .header-back { display: inline-flex; align-items: center; gap: 7px; min-height: 40px; padding: 0 14px; border: 1px solid var(--control-border); border-radius: 10px; color: var(--text-secondary); background: var(--surface-panel); font-size: 12px; font-weight: 700; text-decoration: none; }
    .header-back:hover { color: var(--institutional-blue); border-color: var(--institutional-blue); background: var(--surface-subtle); }
    .profile-hero {
      position: relative; display: flex; align-items: center; gap: 22px;
      background: linear-gradient(135deg, var(--surface-panel), var(--surface-subtle)); border-radius: 18px; padding: 28px;
      border: 1px solid var(--border-default); margin-bottom: 18px; overflow: hidden;
      box-shadow: 0 12px 28px rgba(15,23,42,.07);
    }
    .profile-hero::after { content: ''; position: absolute; width: 220px; height: 220px; right: -90px; top: -125px; border-radius: 50%; background: var(--brand-soft); opacity: .6; }
    .hero-avatar-wrapper {
      position: relative; z-index: 1; width: 82px; height: 82px; flex-shrink: 0;
    }
    .hero-avatar-img {
      width: 82px; height: 82px; border-radius: 50%; object-fit: cover; border: 3px solid var(--surface-panel);
      box-shadow: 0 7px 18px rgba(37,99,235,.2);
    }
    .hero-avatar-initials {
      width: 82px; height: 82px; border-radius: 50%; border: 3px solid var(--surface-panel);
      background: var(--brand-soft); color: var(--institutional-blue);
      display: flex; align-items: center; justify-content: center;
      font-size: 29px; font-weight: 750;
      box-shadow: 0 7px 18px rgba(37,99,235,.2);
    }
    .photo-change-btn {
      position: absolute; bottom: 0; right: 0;
      width: 28px; height: 28px; border-radius: 50%;
      background: var(--institutional-blue); color: #fff; border: 2px solid var(--surface-panel);
      display: flex; align-items: center; justify-content: center;
      cursor: pointer; transition: all 0.2s; font-size: 14px;
      box-shadow: 0 2px 6px rgba(0,0,0,0.15);
    }
    .photo-change-btn:hover:not(:disabled) { background: var(--institutional-blue); transform: scale(1.1); }
    .photo-change-btn:disabled { opacity: 0.5; cursor: not-allowed; }
    .photo-remove-btn { position:absolute; top:0; right:0; width:26px; height:26px; border-radius:50%; background:#ef4444; color:#fff; border:2px solid var(--surface-panel); display:flex; align-items:center; justify-content:center; cursor:pointer; font-size:13px; box-shadow:0 2px 6px rgba(0,0,0,.15); }
    .photo-remove-btn:hover { background:#dc2626; transform:scale(1.08); }
    .photo-loading-overlay {
      position: absolute; inset: 0; border-radius: 50%;
      background: rgba(255,255,255,0.7); display: flex;
      align-items: center; justify-content: center;
    }
    .spinner {
      width: 24px; height: 24px; border: 3px solid #e2e8f0;
      border-top-color: var(--institutional-blue); border-radius: 50%;
      animation: spin 0.6s linear infinite;
    }
    @keyframes spin { to { transform: rotate(360deg); } }
    .hero-info { position: relative; z-index: 1; }
    .hero-info h2 { margin: 0; font-size: 23px; font-weight: 750; color: var(--text-strong); letter-spacing: -0.35px; }
    .hero-email { margin: 5px 0 11px; color: var(--text-secondary); font-size: 13px; }
    .hero-badges { display: flex; gap: 8px; flex-wrap: wrap; }
    .badge {
      display: inline-flex; align-items: center; padding: 4px 12px;
      border-radius: 20px; font-size: 12px; font-weight: 600;
    }
    .badge-admin { background: var(--state-danger-bg); color: var(--state-danger-text); }
    .badge-unit { background: var(--brand-soft); color: var(--institutional-blue); }
    .profile-content { display: grid; grid-template-columns: minmax(0, 1.2fr) minmax(280px, .8fr); gap: 18px; align-items: start; }
    .profile-side { display: grid; gap: 18px; }
    .form-card {
      background: var(--surface-panel); border-radius: 15px; border: 1px solid var(--border-default);
      overflow: hidden; margin: 0; box-shadow: 0 5px 14px rgba(15,23,42,.04);
    }
    .card-header {
      display: flex; align-items: center; gap: 8px;
      padding: 15px 18px; border-bottom: 1px solid var(--border-subtle);
      font-size: 13px; font-weight: 750; color: var(--text-strong);
    }
    .card-header ion-icon { font-size: 18px; color: var(--institutional-blue); }
    .card-body { padding: 18px; }
    .form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 16px; }
    .form-field { display: flex; flex-direction: column; gap: 6px; }
    .form-field.full-width { grid-column: 1 / -1; }
    .field-label { font-size: 10px; font-weight: 800; color: var(--text-secondary); text-transform: uppercase; letter-spacing: .06em; }
    .field-input {
      min-height: 44px; padding: 10px 12px; border: 1px solid var(--control-border); border-radius: 10px;
      font-size: 13px; color: var(--control-text); background: var(--control-bg);
      outline: none; transition: border-color 0.2s, box-shadow 0.2s; font-family: inherit;
    }
    .field-input:focus { border-color: var(--institutional-blue); box-shadow: 0 0 0 3px rgba(59,130,246,0.1); }
    .field-input:disabled { background: var(--surface-subtle); color: var(--text-secondary); cursor: not-allowed; }
    .field-input::placeholder { color: var(--control-placeholder); }
    .field-textarea { min-height: 88px; resize: vertical; }
    .professional-grid { grid-template-columns: 1fr; margin-bottom: 0; }
    .info-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
    .info-item { display: flex; flex-direction: column; gap: 5px; min-height: 70px; padding: 11px; border: 1px solid var(--border-subtle); border-radius: 10px; background: var(--surface-subtle); }
    .info-label { font-size: 9px; color: var(--text-secondary); text-transform: uppercase; letter-spacing: .06em; font-weight: 800; }
    .info-value { font-size: 13px; color: var(--text-strong); font-weight: 650; overflow-wrap: anywhere; }
    .membresia-row {
      display: flex; align-items: center; gap: 14px;
      padding: 11px; border: 1px solid var(--border-subtle); border-radius: 11px; background: var(--surface-subtle); margin-bottom: 9px;
    }
    .membresia-row:last-child { border-bottom: none; }
    .membresia-icon {
      width: 38px; height: 38px; border-radius: 10px;
      background: var(--state-success-bg); display: flex; align-items: center;
      justify-content: center; flex-shrink: 0;
    }
    .membresia-icon ion-icon { font-size: 18px; color: #16a34a; }
    .membresia-info { display: flex; flex-direction: column; gap: 3px; }
    .membresia-unidad { font-size: 13px; color: var(--text-strong); font-weight: 700; }
    .membresia-rol { font-size: 11px; color: var(--text-secondary); }
    .text-muted { color: var(--text-secondary); font-size: 13px; margin: 0; }
    .action-bar { display: flex; gap: 10px; justify-content: flex-end; flex-wrap: wrap; margin-top: 18px; }
    button.btn-primary, a.btn-ghost {
      display: inline-flex; align-items: center; gap: 8px;
      padding: 11px 22px; border-radius: 10px;
      font-size: 14px; font-weight: 600; cursor: pointer;
      transition: all 0.25s; text-decoration: none; font-family: inherit;
    }
    button.btn-primary {
      background: linear-gradient(135deg, var(--institutional-blue), var(--institutional-blue)); color: #fff; border: none;
      box-shadow: 0 4px 14px rgba(59,130,246,0.3);
    }
    button.btn-primary:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 6px 20px rgba(59,130,246,0.4); }
    button.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }
    a.btn-ghost { background: var(--surface-panel); color: var(--text-secondary); border: 1px solid var(--control-border); }
    a.btn-ghost:hover { background: var(--surface-subtle); color: var(--text-strong); transform: translateY(-1px); }
    .loading-state { text-align: center; padding: 60px; color: var(--text-secondary); }
    @media (max-width: 820px) { .profile-content { grid-template-columns: 1fr; } }
    @media (max-width: 640px) {
      .page-header { align-items: flex-start; }
      .header-back { min-width: 40px; padding: 0; justify-content: center; font-size: 0; }
      .header-back ion-icon { font-size: 18px; }
      .form-grid, .info-grid { grid-template-columns: 1fr; }
      .profile-hero { flex-direction: column; padding: 24px 20px; text-align: center; }
      .hero-badges { justify-content: center; }
      .action-bar { display: grid; grid-template-columns: 1fr 1fr; }
      .action-bar a, .action-bar button { justify-content: center; width: 100%; }
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfileComponent implements OnInit {
  private configDataService = inject(ConfigDataService);
  private authService = inject(AuthService);
  private apiService = inject(ApiService);
  private alertCtrl = inject(AlertController);
  private destroyRef = inject(DestroyRef);

  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;

  protected profile = signal<any>(null);
  protected roles = signal<string[]>([]);
  protected loading = signal(true);
  protected saving = signal(false);
  protected uploadingPhoto = signal(false);
  protected previewUrl = signal<string | null>(null);
  protected photoTimestamp = signal(Date.now());
  private selectedFile: File | null = null;

  private readonly ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
  private readonly MAX_SIZE = 5 * 1024 * 1024;

  protected editData = {
    nombre: '',
    segundoNombre: '',
    apellido: '',
    telefono: '',
    celular: '',
    correoAlterno: '',
    documentoIdentidad: '',
    tipoDocumento: '',
    cargo: '',
    institucion: '',
    departamento: '',
    direccion: '',
    unidadAdministrativa: '',
    codigoInterno: '',
    observaciones: '',
    fotografiaRuta: '',
  };

  constructor() {
    addIcons({
      personOutline, mailOutline, phonePortraitOutline, locationOutline,
      briefcaseOutline, saveOutline, arrowBackOutline, idCardOutline,
      cameraOutline, createOutline, trashOutline,
    });
  }

  ngOnInit(): void {
    const sub = this.configDataService.getMyProfile().subscribe({
      next: (p: any) => {
        this.profile.set(p);
        this.roles.set(p.roles || []);
        this.editData = {
          nombre: p.nombre || '',
          segundoNombre: p.segundoNombre || '',
          apellido: p.apellido || '',
          telefono: p.telefono || '',
          celular: p.celular || '',
          correoAlterno: p.correoAlterno || '',
          documentoIdentidad: p.documentoIdentidad || '',
          tipoDocumento: p.tipoDocumento || '',
          cargo: p.cargo || '',
          institucion: p.institucion || '',
          departamento: p.departamento || '',
          direccion: p.direccion || '',
          unidadAdministrativa: p.unidadAdministrativa || '',
          codigoInterno: p.codigoInterno || '',
          observaciones: p.observaciones || '',
          fotografiaRuta: p.fotografiaRuta || '',
        };
        this.loading.set(false);
      },
      error: async () => {
        this.loading.set(false);
        const alert = await this.alertCtrl.create({
          header: 'Error',
          message: 'No se pudo cargar el perfil.',
          buttons: ['OK'],
        });
        await alert.present();
      },
    });
    this.destroyRef.onDestroy(() => sub.unsubscribe());
  }

  protected onPhotoSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files || input.files.length === 0) return;

    const file = input.files[0];
    input.value = '';

    if (!this.ALLOWED_TYPES.includes(file.type)) {
      this.showPhotoError('Formato no permitido. Use JPG, JPEG, PNG o WEBP.');
      return;
    }
    if (file.size > this.MAX_SIZE) {
      this.showPhotoError('La imagen excede el limite de 5MB.');
      return;
    }

    this.selectedFile = file;
    if (this.previewUrl()) {
      URL.revokeObjectURL(this.previewUrl()!);
    }
    this.previewUrl.set(URL.createObjectURL(file));
    this.uploadPhoto();
  }

  private uploadPhoto(): void {
    if (!this.selectedFile) return;

    this.uploadingPhoto.set(true);
    const formData = new FormData();
    formData.append('file', this.selectedFile);

    const sub = this.apiService.post<{ fotografiaRuta: string }>('/api/auth/me/photo', formData).subscribe({
      next: (res) => {
        this.uploadingPhoto.set(false);
        this.selectedFile = null;
        this.editData.fotografiaRuta = res.fotografiaRuta;
        this.photoTimestamp.set(Date.now());
        this.profile.update(p => ({ ...p, fotografiaRuta: res.fotografiaRuta }));
        this.authService.updatePhoto(res.fotografiaRuta);
        if (this.previewUrl()) {
          URL.revokeObjectURL(this.previewUrl()!);
          this.previewUrl.set(null);
        }
      },
      error: async (err) => {
        this.uploadingPhoto.set(false);
        this.selectedFile = null;
        if (this.previewUrl()) {
          URL.revokeObjectURL(this.previewUrl()!);
          this.previewUrl.set(null);
        }
        const msg = err.error?.error || 'Error al subir la imagen';
        const alert = await this.alertCtrl.create({
          header: 'Error',
          message: typeof msg === 'string' ? msg : JSON.stringify(msg),
          buttons: ['OK'],
        });
        await alert.present();
      },
    });
    this.destroyRef.onDestroy(() => sub.unsubscribe());
  }

  protected removePhoto(): void {
    this.uploadingPhoto.set(true);
    this.apiService.delete<{ fotografiaRuta: string }>('/api/auth/me/photo').subscribe({
      next: () => {
        this.uploadingPhoto.set(false);
        this.editData.fotografiaRuta = '';
        this.profile.update(p => ({ ...p, fotografiaRuta: '' }));
        this.authService.updatePhoto('');
      },
      error: async (err) => {
        this.uploadingPhoto.set(false);
        const alert = await this.alertCtrl.create({ header: 'Error', message: err.error?.error || 'No se pudo eliminar la foto.', buttons: ['OK'] });
        await alert.present();
      },
    });
  }

  private async showPhotoError(message: string): Promise<void> {
    const alert = await this.alertCtrl.create({
      header: 'Error',
      message,
      buttons: ['OK'],
    });
    await alert.present();
  }

  protected async saveProfile(): Promise<void> {
    this.saving.set(true);
    const sub = this.configDataService.updateMyProfile(this.editData).subscribe({
      next: async (p: any) => {
        this.profile.set(p);
        const currentUser = this.authService.currentUser();
        if (currentUser) {
          this.authService.saveUserInfo({ ...currentUser, nombre: p.nombre });
        }
        this.saving.set(false);
        const alert = await this.alertCtrl.create({
          header: 'Éxito',
          message: 'Perfil actualizado correctamente.',
          buttons: ['OK'],
        });
        await alert.present();
      },
      error: async (err: any) => {
        this.saving.set(false);
        const msg = err.error?.error || 'Error al actualizar el perfil';
        const alert = await this.alertCtrl.create({
          header: 'Error',
          message: typeof msg === 'string' ? msg : JSON.stringify(msg),
          buttons: ['OK'],
        });
        await alert.present();
      },
    });
    this.destroyRef.onDestroy(() => sub.unsubscribe());
  }
}
