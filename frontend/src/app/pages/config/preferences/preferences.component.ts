import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { IonIcon } from '@ionic/angular/standalone';
import { AlertController } from '@ionic/angular';
import { addIcons } from 'ionicons';
import {
  arrowBackOutline, checkmarkCircleOutline, gridOutline, moonOutline,
  notificationsOutline, optionsOutline, refreshOutline, saveOutline,
} from 'ionicons/icons';
import { ConfigDataService } from '../services/config-data.service';
import { AuthService } from '../../../services/auth.service';

interface PrefsModel {
  theme: string;
  language: string;
  timezone: string;
  dateFormat: string;
  timeFormat: string;
  homePage: string;
  recordsPerPage: number;
  sidebarExpanded: boolean;
  animationsEnabled: boolean;
  emailNotifications: boolean;
  pushNotifications: boolean;
  soundEnabled: boolean;
  confirmationsEnabled: boolean;
  fontSize: string;
  highContrast: boolean;
}

@Component({
  selector: 'app-preferences',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, IonIcon],
  template: `
    <div class="page-container">
      <header class="page-header">
        <div class="header-left">
          <div class="header-icon"><ion-icon name="options-outline" /></div>
          <div>
            <div class="title-row">
              <h1>Preferencias</h1>
              <span class="effective-badge"><ion-icon name="checkmark-circle-outline" /> Configuración activa</span>
            </div>
            <p>Personaliza cómo se ve y se comporta tu espacio de trabajo.</p>
          </div>
        </div>
        <a class="back-link" routerLink="/dashboard"><ion-icon name="arrow-back-outline" /> Volver al inicio</a>
      </header>

      @if (loading()) {
        <div class="preferences-grid" aria-label="Cargando preferencias">
          <div class="skeleton-card"></div><div class="skeleton-card"></div><div class="skeleton-card"></div>
        </div>
      } @else {
        <main class="preferences-grid">
          <section class="preference-card appearance-card">
            <div class="card-heading">
              <span class="section-icon"><ion-icon name="moon-outline" /></span>
              <div><h2>Apariencia y accesibilidad</h2><p>Los cambios se aplican a toda la interfaz al guardar.</p></div>
            </div>

            <div class="card-content">
              <fieldset class="theme-fieldset">
                <legend>Tema visual</legend>
                <div class="theme-options">
                  @for (theme of themes; track theme.value) {
                    <label class="theme-option" [class.selected]="prefsMutable.theme === theme.value">
                      <input type="radio" name="theme" [value]="theme.value" [(ngModel)]="prefsMutable.theme" />
                      <span class="theme-preview" [class]="'theme-preview preview-' + theme.value">
                        <span class="preview-sidebar"></span>
                        <span class="preview-main"><i></i><i></i><i></i></span>
                      </span>
                      <span>{{ theme.label }}</span>
                      <ion-icon name="checkmark-circle-outline" />
                    </label>
                  }
                </div>
              </fieldset>

              <div class="setting-block">
                <label for="fontSize">Tamaño del texto</label>
                <p>Ajusta la escala de lectura de todo el sistema.</p>
                <select id="fontSize" [(ngModel)]="prefsMutable.fontSize">
                  <option value="small">Pequeño</option>
                  <option value="medium">Mediano</option>
                  <option value="large">Grande</option>
                  <option value="x-large">Muy grande</option>
                </select>
              </div>

              <div class="toggle-list compact">
                <label class="toggle-row">
                  <span><strong>Alto contraste</strong><small>Refuerza bordes y colores para facilitar la lectura.</small></span>
                  <span class="toggle"><input type="checkbox" [(ngModel)]="prefsMutable.highContrast" /><span></span></span>
                </label>
                <label class="toggle-row">
                  <span><strong>Animaciones de interfaz</strong><small>Desactívalas para reducir el movimiento visual.</small></span>
                  <span class="toggle"><input type="checkbox" [(ngModel)]="prefsMutable.animationsEnabled" /><span></span></span>
                </label>
              </div>
            </div>
          </section>

          <section class="preference-card">
            <div class="card-heading">
              <span class="section-icon"><ion-icon name="grid-outline" /></span>
              <div><h2>Navegación y contenido</h2><p>Define tu entrada y la cantidad de información visible.</p></div>
            </div>
            <div class="card-content stack">
              <div class="setting-block">
                <label for="homePage">Página inicial</label>
                <p>Se abrirá automáticamente después de iniciar sesión.</p>
                <select id="homePage" [(ngModel)]="prefsMutable.homePage">
                  <option value="dashboard">Panel</option>
                  <option value="actas">Actas</option>
                  <option value="tareas">Tareas</option>
                  <option value="reuniones">Reuniones</option>
                  <option value="documentos">Documentos</option>
                  <option value="grupos">Grupos</option>
                  @if (authService.isAdmin()) { <option value="inventario">Inventario</option> }
                  @if (authService.isAdmin() || authService.hasRole('Jefe de Unidad')) { <option value="reporte">Reportes</option> }
                </select>
              </div>
              <div class="setting-block">
                <label for="recordsPerPage">Registros por página</label>
                <p>Se aplica en actas, tareas, reuniones, inventario y reportes.</p>
                <select id="recordsPerPage" [(ngModel)]="prefsMutable.recordsPerPage">
                  <option [ngValue]="10">10 registros</option>
                  <option [ngValue]="20">20 registros</option>
                  <option [ngValue]="50">50 registros</option>
                  <option [ngValue]="100">100 registros</option>
                </select>
              </div>
              <label class="toggle-row standalone">
                <span><strong>Barra lateral expandida</strong><small>Inicia la navegación mostrando nombres e iconos en pantallas amplias.</small></span>
                <span class="toggle"><input type="checkbox" [(ngModel)]="prefsMutable.sidebarExpanded" /><span></span></span>
              </label>
            </div>
          </section>

          <section class="preference-card notification-card">
            <div class="card-heading">
              <span class="section-icon"><ion-icon name="notifications-outline" /></span>
              <div><h2>Notificaciones</h2><p>Controla los canales que sí están conectados al sistema.</p></div>
            </div>
            <div class="card-content toggle-list">
              <label class="toggle-row">
                <span><strong>Correo electrónico</strong><small>Recibe por correo avisos relacionados con tus tareas.</small></span>
                <span class="toggle"><input type="checkbox" [(ngModel)]="prefsMutable.emailNotifications" /><span></span></span>
              </label>
              <label class="toggle-row">
                <span><strong>Notificaciones del navegador</strong><small>Muestra avisos nuevos mientras estás usando el sistema.</small></span>
                <span class="toggle"><input type="checkbox" [(ngModel)]="prefsMutable.pushNotifications" /><span></span></span>
              </label>
            </div>
          </section>
        </main>

        <footer class="action-bar">
          <div><strong>Preferencias personales</strong><span>Los cambios solo afectan a tu cuenta.</span></div>
          <div class="actions">
            <button class="btn-secondary" type="button" (click)="resetPreferences()" [disabled]="saving()">
              <ion-icon name="refresh-outline" /> Restablecer
            </button>
            <button class="btn-primary" type="button" (click)="savePreferences()" [disabled]="saving()">
              <ion-icon name="save-outline" /> {{ saving() ? 'Guardando…' : 'Guardar cambios' }}
            </button>
          </div>
        </footer>
      }
    </div>
  `,
  styles: [`
    :host { display: block; width: 100%; }
    .page-container { max-width: 1180px; margin: 0 auto; padding: 4px 0 28px; font-family: Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; color: #0f172a; }
    .page-header { display: flex; align-items: center; justify-content: space-between; gap: 24px; margin-bottom: 24px; }
    .header-left { display: flex; align-items: center; gap: 16px; min-width: 0; }
    .header-icon { width: 52px; height: 52px; display: grid; place-items: center; flex: 0 0 auto; border-radius: 15px; color: #fff; font-size: 25px; background: linear-gradient(145deg, var(--institutional-blue), var(--institutional-blue)); box-shadow: 0 10px 24px rgba(37, 99, 235, .22); }
    .title-row { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
    h1 { margin: 0; font-size: 28px; line-height: 1.15; letter-spacing: -.5px; }
    .page-header p { margin: 5px 0 0; color: #64748b; font-size: 14px; }
    .effective-badge { display: inline-flex; align-items: center; gap: 5px; padding: 5px 9px; border-radius: 999px; color: #166534; background: #ecfdf3; border: 1px solid #bbf7d0; font-size: 11px; font-weight: 700; }
    .effective-badge ion-icon { font-size: 14px; }
    .back-link { display: inline-flex; align-items: center; gap: 7px; flex: 0 0 auto; padding: 10px 14px; border: 1px solid #dbe4f0; border-radius: 10px; color: #475569; background: #fff; text-decoration: none; font-size: 13px; font-weight: 650; }
    .back-link:hover { border-color: #93b4f8; color: var(--institutional-blue); background: #f8fbff; }
    .preferences-grid { display: grid; grid-template-columns: minmax(0, 1.3fr) minmax(320px, .7fr); gap: 18px; align-items: start; }
    .preference-card { background: #fff; border: 1px solid #dce5f0; border-radius: 16px; overflow: hidden; box-shadow: 0 4px 16px rgba(15, 23, 42, .045); }
    .appearance-card { grid-row: span 2; }
    .card-heading { display: flex; align-items: center; gap: 12px; padding: 18px 20px; border-bottom: 1px solid #e8eef6; background: linear-gradient(180deg, #fbfdff, #f7faff); }
    .section-icon { width: 38px; height: 38px; flex: 0 0 auto; display: grid; place-items: center; border-radius: 11px; color: var(--institutional-blue); background: #eaf2ff; font-size: 19px; }
    .card-heading h2 { margin: 0; font-size: 15px; font-weight: 750; }
    .card-heading p { margin: 3px 0 0; color: #7c8da5; font-size: 12px; line-height: 1.35; }
    .card-content { padding: 20px; }
    .card-content.stack { display: grid; gap: 18px; }
    .theme-fieldset { margin: 0 0 22px; padding: 0; border: 0; }
    .theme-fieldset legend, .setting-block > label { display: block; margin-bottom: 8px; color: #334155; font-size: 12px; font-weight: 750; text-transform: uppercase; letter-spacing: .35px; }
    .theme-options { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
    .theme-option { position: relative; display: grid; gap: 8px; padding: 10px; border: 1px solid #dce5f0; border-radius: 12px; cursor: pointer; color: #475569; font-size: 12px; font-weight: 700; transition: border-color .18s, box-shadow .18s, background .18s; }
    .theme-option input { position: absolute; opacity: 0; pointer-events: none; }
    .theme-option > ion-icon { position: absolute; right: 7px; bottom: 7px; color: var(--institutional-blue); font-size: 17px; opacity: 0; }
    .theme-option.selected { color: var(--institutional-blue); border-color: var(--institutional-blue); background: #f3f7ff; box-shadow: 0 0 0 2px rgba(37, 99, 235, .1); }
    .theme-option.selected > ion-icon { opacity: 1; }
    .theme-preview { height: 66px; display: grid; grid-template-columns: 24% 1fr; overflow: hidden; border: 1px solid #dbe4f0; border-radius: 7px; background: #fff; }
    .preview-sidebar { background: #183b70; }
    .preview-main { display: flex; flex-direction: column; gap: 5px; padding: 10px 8px; background: #f8fafc; }
    .preview-main i { display: block; height: 6px; border-radius: 3px; background: #dbe5f1; }
    .preview-main i:nth-child(2) { width: 65%; }
    .preview-dark .preview-main { background: #1e293b; }
    .preview-dark .preview-main i { background: #475569; }
    .preview-dark .preview-sidebar { background: #0f172a; }
    .preview-system .preview-main { background: linear-gradient(135deg, #f8fafc 49%, #1e293b 51%); }
    .setting-block p { margin: -3px 0 10px; color: #8190a5; font-size: 12px; line-height: 1.35; }
    select { width: 100%; height: 42px; padding: 0 38px 0 12px; border: 1px solid #d6e0ed; border-radius: 9px; color: #1e293b; background: #fff url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='14' height='14' viewBox='0 0 24 24' fill='none' stroke='%2364788b' stroke-width='2'%3E%3Cpath d='m6 9 6 6 6-6'/%3E%3C/svg%3E") no-repeat right 12px center; appearance: none; font: inherit; font-size: 13px; cursor: pointer; outline: none; }
    select:focus { border-color: var(--institutional-blue); box-shadow: 0 0 0 3px rgba(37, 99, 235, .12); }
    .toggle-list { display: grid; gap: 0; }
    .toggle-list.compact { margin-top: 22px; }
    .toggle-row { display: flex; align-items: center; justify-content: space-between; gap: 18px; padding: 15px 0; border-bottom: 1px solid #edf2f7; cursor: pointer; }
    .toggle-row:last-child { border-bottom: 0; }
    .toggle-row.standalone { padding: 16px; border: 1px solid #e2e8f0; border-radius: 11px; background: #f8fafc; }
    .toggle-row > span:first-child { display: grid; gap: 3px; }
    .toggle-row strong { color: #26364d; font-size: 13px; font-weight: 700; }
    .toggle-row small { color: #8190a5; font-size: 11px; line-height: 1.4; }
    .toggle { position: relative; width: 42px; height: 24px; flex: 0 0 42px; }
    .toggle input { position: absolute; opacity: 0; }
    .toggle > span { position: absolute; inset: 0; border-radius: 999px; background: #cbd5e1; transition: background .2s; }
    .toggle > span::before { content: ''; position: absolute; width: 18px; height: 18px; left: 3px; top: 3px; border-radius: 50%; background: #fff; box-shadow: 0 1px 3px rgba(15, 23, 42, .25); transition: transform .2s; }
    .toggle input:checked + span { background: var(--institutional-blue); }
    .toggle input:checked + span::before { transform: translateX(18px); }
    .toggle input:focus-visible + span { outline: 3px solid rgba(37, 99, 235, .25); outline-offset: 2px; }
    .action-bar { display: flex; align-items: center; justify-content: space-between; gap: 20px; margin-top: 18px; padding: 16px 18px; border: 1px solid #dce5f0; border-radius: 14px; background: #fff; box-shadow: 0 6px 20px rgba(15, 23, 42, .05); }
    .action-bar > div:first-child { display: grid; gap: 2px; }
    .action-bar strong { font-size: 13px; }
    .action-bar span { color: #8190a5; font-size: 11px; }
    .actions { display: flex; gap: 10px; }
    button { height: 40px; display: inline-flex; align-items: center; justify-content: center; gap: 7px; padding: 0 16px; border-radius: 9px; font: inherit; font-size: 12px; font-weight: 700; cursor: pointer; transition: background .18s, border-color .18s, transform .18s; }
    button:disabled { opacity: .55; cursor: wait; }
    .btn-primary { border: 1px solid var(--institutional-blue); color: #fff; background: var(--institutional-blue); box-shadow: 0 5px 12px rgba(37, 99, 235, .2); }
    .btn-primary:hover:not(:disabled) { background: var(--institutional-blue); transform: translateY(-1px); }
    .btn-secondary { border: 1px solid #d6e0ed; color: #475569; background: #fff; }
    .btn-secondary:hover:not(:disabled) { border-color: #9db7db; color: var(--institutional-blue); background: #f8fbff; }
    .skeleton-card { height: 310px; border-radius: 16px; background: linear-gradient(90deg, #edf2f7 25%, #f8fafc 50%, #edf2f7 75%); background-size: 200% 100%; animation: shimmer 1.2s infinite; }
    .skeleton-card:first-child { grid-row: span 2; height: 638px; }
    @keyframes shimmer { to { background-position: -200% 0; } }
    @media (max-width: 900px) { .preferences-grid { grid-template-columns: 1fr; } .appearance-card { grid-row: auto; } .skeleton-card:first-child { height: 310px; } }
    @media (max-width: 640px) { .page-header { align-items: flex-start; } .back-link { width: 40px; height: 40px; padding: 0; justify-content: center; font-size: 0; } .back-link ion-icon { font-size: 20px; } .effective-badge { display: none; } .theme-options { grid-template-columns: 1fr; } .theme-option { grid-template-columns: 72px 1fr; align-items: center; } .theme-preview { height: 48px; } .action-bar { align-items: stretch; flex-direction: column; } .actions { display: grid; grid-template-columns: 1fr 1fr; } }
    @media (prefers-reduced-motion: reduce) { *, *::before, *::after { transition-duration: .01ms !important; animation-duration: .01ms !important; } }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PreferencesComponent implements OnInit {
  private readonly configDataService = inject(ConfigDataService);
  protected readonly authService = inject(AuthService);
  private readonly alertCtrl = inject(AlertController);

  protected readonly themes = [
    { value: 'light', label: 'Claro' },
    { value: 'dark', label: 'Oscuro' },
    { value: 'system', label: 'Sistema' },
  ];

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected prefsMutable: PrefsModel = this.defaultPreferences();

  constructor() {
    addIcons({
      arrowBackOutline, checkmarkCircleOutline, gridOutline, moonOutline,
      notificationsOutline, optionsOutline, refreshOutline, saveOutline,
    });
  }

  ngOnInit(): void {
    this.configDataService.getMyPreferences().subscribe({
      next: (data: PrefsModel) => {
        this.prefsMutable = { ...this.defaultPreferences(), ...data };
        this.loading.set(false);
      },
      error: () => {
        this.prefsMutable = this.defaultPreferences();
        this.loading.set(false);
      },
    });
  }

  protected async savePreferences(): Promise<void> {
    this.prefsMutable.homePage = this.authService.allowedHomePage(this.prefsMutable.homePage);
    if (this.prefsMutable.pushNotifications && 'Notification' in window && Notification.permission === 'default') {
      try { await Notification.requestPermission(); } catch { /* El navegador puede bloquear la solicitud. */ }
    }

    this.saving.set(true);
    this.configDataService.saveMyPreferences({ ...this.prefsMutable }).subscribe({
      next: async (data: PrefsModel) => {
        this.saving.set(false);
        this.prefsMutable = { ...this.defaultPreferences(), ...data };
        this.authService.updatePreferences(data);
        await this.showAlert('Preferencias guardadas', 'Los cambios ya están aplicados en tu cuenta.');
      },
      error: async (err: any) => {
        this.saving.set(false);
        let message = err.error?.error || err.error?.message || 'No fue posible guardar las preferencias.';
        if (err.error?.fields) {
          message += '\n' + Object.entries(err.error.fields).map(([key, value]) => `${key}: ${value}`).join('\n');
        }
        await this.showAlert('No se pudieron guardar', typeof message === 'string' ? message : JSON.stringify(message));
      },
    });
  }

  protected resetPreferences(): void {
    if (this.saving()) return;
    this.saving.set(true);
    this.configDataService.resetMyPreferences().subscribe({
      next: async (data: PrefsModel) => {
        this.saving.set(false);
        this.prefsMutable = { ...this.defaultPreferences(), ...data };
        this.authService.updatePreferences(data);
        await this.showAlert('Preferencias restablecidas', 'Se aplicaron los valores predeterminados.');
      },
      error: async () => {
        this.saving.set(false);
        await this.showAlert('No se pudieron restablecer', 'Intenta nuevamente en unos momentos.');
      },
    });
  }

  private async showAlert(header: string, message: string): Promise<void> {
    const alert = await this.alertCtrl.create({ header, message, buttons: ['Aceptar'] });
    await alert.present();
  }

  private defaultPreferences(): PrefsModel {
    return {
      theme: 'light', language: 'es', timezone: 'America/Panama', dateFormat: 'DD/MM/YYYY', timeFormat: 'HH:mm',
      homePage: 'dashboard', recordsPerPage: 20, sidebarExpanded: true, animationsEnabled: true,
      emailNotifications: true, pushNotifications: true, soundEnabled: true, confirmationsEnabled: true,
      fontSize: 'medium', highContrast: false,
    };
  }
}
