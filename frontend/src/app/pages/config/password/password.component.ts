import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { IonIcon } from '@ionic/angular/standalone';
import { AlertController } from '@ionic/angular';
import { addIcons } from 'ionicons';
import {
  arrowBackOutline, checkmarkCircleOutline, closeCircleOutline, eyeOffOutline,
  eyeOutline, lockClosedOutline, saveOutline,
  shieldCheckmarkOutline,
} from 'ionicons/icons';
import { ConfigDataService } from '../services/config-data.service';
import { AuthService } from '../../../services/auth.service';

interface PasswordPolicy {
  minLength: number;
  maxLength: number;
  requiresUppercase: boolean;
  requiresLowercase: boolean;
  requiresDigits: boolean;
  requiresSpecialChars: boolean;
  historySize: number;
}

const DEFAULT_POLICY: PasswordPolicy = {
  minLength: 8,
  maxLength: 128,
  requiresUppercase: true,
  requiresLowercase: true,
  requiresDigits: true,
  requiresSpecialChars: true,
  historySize: 5,
};

@Component({
  selector: 'app-password',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, IonIcon],
  template: `
    <div class="page-container">
      <header class="page-header">
        <div class="header-left">
          <span class="header-icon"><ion-icon name="lock-closed-outline" /></span>
          <div>
            <div class="title-row">
              <h1>Cambiar contraseña</h1>
              <span class="secure-badge"><ion-icon name="shield-checkmark-outline" /> Cambio protegido</span>
            </div>
            <p>Actualiza la credencial con la que accedes al sistema.</p>
          </div>
        </div>
        <a class="back-link" routerLink="/config/profile"><ion-icon name="arrow-back-outline" /> Volver al perfil</a>
      </header>

      <div class="content-grid">
        <form class="password-card" (ngSubmit)="changePassword()" novalidate>
          <div class="card-heading">
            <span class="section-icon"><ion-icon name="lock-closed-outline" /></span>
            <div><h2>Nueva credencial</h2><p>Define y confirma la contraseña que utilizarás para acceder.</p></div>
          </div>

          <div class="card-content">
            <div class="field-grid">
              <div class="form-field">
                <label for="newPassword">Nueva contraseña</label>
                <div class="input-wrap" [class.invalid]="newPasswordTouched && !meetsPolicy()">
                  <input id="newPassword" [type]="showNew ? 'text' : 'password'"
                    [(ngModel)]="newPassword" name="newPassword" (ngModelChange)="newPasswordTouched = true"
                    [maxlength]="policy().maxLength" [placeholder]="'Entre ' + policy().minLength + ' y ' + policy().maxLength + ' caracteres'"
                    autocomplete="new-password" />
                  <button type="button" class="visibility-button" (click)="showNew = !showNew"
                    [attr.aria-label]="showNew ? 'Ocultar nueva contraseña' : 'Mostrar nueva contraseña'" [title]="showNew ? 'Ocultar nueva contraseña' : 'Mostrar nueva contraseña'">
                    <ion-icon [name]="showNew ? 'eye-off-outline' : 'eye-outline'" />
                  </button>
                </div>
              </div>

              <div class="form-field">
                <label for="confirmPassword">Confirmar contraseña</label>
                <div class="input-wrap" [class.invalid]="confirmPassword && !passwordsMatch()">
                  <input id="confirmPassword" [type]="showConfirm ? 'text' : 'password'"
                    [(ngModel)]="confirmPassword" name="confirmPassword"
                    placeholder="Repite la nueva contraseña" autocomplete="new-password" />
                  <button type="button" class="visibility-button" (click)="showConfirm = !showConfirm"
                    [attr.aria-label]="showConfirm ? 'Ocultar confirmación' : 'Mostrar confirmación'" [title]="showConfirm ? 'Ocultar confirmación' : 'Mostrar confirmación'">
                    <ion-icon [name]="showConfirm ? 'eye-off-outline' : 'eye-outline'" />
                  </button>
                </div>
                @if (confirmPassword && !passwordsMatch()) { <small class="field-error">Las contraseñas no coinciden.</small> }
              </div>
            </div>

            <div class="strength" aria-live="polite">
              <div class="strength-heading"><span>Seguridad de la contraseña</span><strong [class]="strengthClass()">{{ strengthLabel() }}</strong></div>
              <div class="strength-track"><span [style.width.%]="strengthPercent()" [class]="strengthClass()"></span></div>
            </div>

          </div>

          <div class="form-actions">
            <a class="btn-secondary" routerLink="/config/profile">Cancelar</a>
            <button class="btn-primary" type="submit" [disabled]="!canSubmit() || saving()">
              <ion-icon name="save-outline" /> {{ saving() ? 'Actualizando…' : 'Actualizar contraseña' }}
            </button>
          </div>
        </form>

        <aside class="policy-card">
          <div class="policy-heading">
            <span class="section-icon"><ion-icon name="shield-checkmark-outline" /></span>
            <div><h2>Requisitos vigentes</h2><p>Configurados por la política de seguridad del sistema.</p></div>
          </div>
          <div class="requirements">
            <div class="requirement" [class.valid]="hasRequiredLength()">
              <ion-icon [name]="hasRequiredLength() ? 'checkmark-circle-outline' : 'close-circle-outline'" />
              <span>Entre {{ policy().minLength }} y {{ policy().maxLength }} caracteres</span>
            </div>
            @if (policy().requiresUppercase) {
              <div class="requirement" [class.valid]="hasUppercase()"><ion-icon [name]="hasUppercase() ? 'checkmark-circle-outline' : 'close-circle-outline'" /><span>Al menos una letra mayúscula</span></div>
            }
            @if (policy().requiresLowercase) {
              <div class="requirement" [class.valid]="hasLowercase()"><ion-icon [name]="hasLowercase() ? 'checkmark-circle-outline' : 'close-circle-outline'" /><span>Al menos una letra minúscula</span></div>
            }
            @if (policy().requiresDigits) {
              <div class="requirement" [class.valid]="hasDigit()"><ion-icon [name]="hasDigit() ? 'checkmark-circle-outline' : 'close-circle-outline'" /><span>Al menos un número</span></div>
            }
            @if (policy().requiresSpecialChars) {
              <div class="requirement" [class.valid]="hasSpecialChar()"><ion-icon [name]="hasSpecialChar() ? 'checkmark-circle-outline' : 'close-circle-outline'" /><span>Al menos un carácter especial</span></div>
            }
            <div class="requirement" [class.valid]="passwordsMatch() && !!confirmPassword">
              <ion-icon [name]="passwordsMatch() && confirmPassword ? 'checkmark-circle-outline' : 'close-circle-outline'" />
              <span>La confirmación debe coincidir</span>
            </div>
          </div>
        </aside>
      </div>
    </div>
  `,
  styles: [`
    :host { display: block; width: 100%; }
    .page-container { max-width: 1080px; margin: 0 auto; padding: 4px 0 28px; color: var(--text-strong); font-family: Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; }
    .page-header { display: flex; align-items: center; justify-content: space-between; gap: 24px; margin-bottom: 24px; }
    .header-left { display: flex; align-items: center; gap: 16px; min-width: 0; }
    .header-icon { width: 52px; height: 52px; display: grid; place-items: center; flex: 0 0 auto; border-radius: 15px; color: #fff; font-size: 24px; background: linear-gradient(145deg, var(--institutional-blue), var(--institutional-blue)); box-shadow: 0 10px 24px rgba(37, 99, 235, .22); }
    .title-row { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
    h1 { margin: 0; font-size: 28px; line-height: 1.15; letter-spacing: -.5px; }
    .page-header p { margin: 5px 0 0; color: var(--text-soft); font-size: 14px; }
    .secure-badge { display: inline-flex; align-items: center; gap: 5px; padding: 5px 9px; border: 1px solid var(--state-success-border); border-radius: 999px; color: var(--state-success-text); background: var(--state-success-bg); font-size: 11px; font-weight: 700; }
    .back-link { display: inline-flex; align-items: center; gap: 7px; padding: 10px 14px; flex: 0 0 auto; border: 1px solid var(--control-border); border-radius: 10px; color: var(--control-text); background: var(--control-bg); text-decoration: none; font-size: 13px; font-weight: 650; }
    .back-link:hover { border-color: var(--control-focus-border); color: var(--text-link); background: var(--control-bg-hover); }
    .content-grid { display: grid; grid-template-columns: minmax(0, 1fr) 330px; gap: 18px; align-items: start; }
    .password-card, .policy-card { overflow: hidden; border: 1px solid var(--border-default); border-radius: 16px; background: var(--surface-panel); box-shadow: var(--card-shadow); }
    .card-heading, .policy-heading { display: flex; align-items: center; gap: 12px; padding: 18px 20px; border-bottom: 1px solid var(--border-subtle); background: var(--surface-subtle); }
    .section-icon { width: 38px; height: 38px; display: grid; place-items: center; flex: 0 0 auto; border-radius: 11px; color: var(--state-info-text); background: var(--state-info-bg); font-size: 19px; }
    h2 { margin: 0; font-size: 15px; font-weight: 750; }
    .card-heading p, .policy-heading p { margin: 3px 0 0; color: var(--text-soft); font-size: 12px; line-height: 1.35; }
    .card-content { display: grid; gap: 22px; padding: 24px; }
    .field-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
    .form-field { display: grid; align-content: start; gap: 7px; }
    .form-field > label { color: var(--text-default); font-size: 12px; font-weight: 750; text-transform: uppercase; letter-spacing: .35px; }
    .input-wrap { display: flex; align-items: center; overflow: hidden; height: 44px; border: 1px solid var(--control-border); border-radius: 10px; background: var(--control-bg); transition: border-color .18s, box-shadow .18s; }
    .input-wrap:focus-within { border-color: var(--control-focus-border); box-shadow: var(--focus-shadow); }
    .input-wrap.invalid { border-color: var(--state-danger-border); }
    input { min-width: 0; flex: 1; height: 100%; padding: 0 4px 0 13px; border: 0; outline: 0; color: var(--control-text); background: transparent; font: inherit; font-size: 13px; }
    input::placeholder { color: var(--control-placeholder); }
    .visibility-button { width: 42px; height: 42px; display: grid; place-items: center; flex: 0 0 auto; border: 0; color: var(--text-soft); background: transparent; font-size: 19px; cursor: pointer; }
    .visibility-button:hover { color: var(--text-link); background: var(--surface-hover); }
    .visibility-button:focus-visible { outline: 2px solid var(--institutional-blue); outline-offset: -3px; border-radius: 8px; }
    .field-error { color: var(--state-danger-text); font-size: 11px; font-weight: 600; }
    .strength { display: grid; gap: 8px; }
    .strength-heading { display: flex; align-items: center; justify-content: space-between; color: var(--text-soft); font-size: 11px; }
    .strength-heading strong { padding: 3px 8px; border: 1px solid transparent; border-radius: 999px; font-size: 10px; font-weight: 750; line-height: 1.2; }
    .strength-track { height: 6px; overflow: hidden; border-radius: 999px; background: #e8eef5; }
    .strength-track span { display: block; height: 100%; border-radius: inherit; transition: width .25s, background .25s; }
    .strength-heading strong.weak { color: #b91c1c; border-color: #fecaca; background: #fef2f2; }
    .strength-heading strong.medium { color: #a16207; border-color: #fde68a; background: #fffbeb; }
    .strength-heading strong.strong { color: #15803d; border-color: #bbf7d0; background: #f0fdf4; }
    .strength-heading strong.empty { color: var(--text-soft); border-color: var(--border-default); background: var(--empty-bg); }
    .strength-track span.weak { background: #ef4444; }
    .strength-track span.medium { background: #f59e0b; }
    .strength-track span.strong { background: #22c55e; }
    .strength-track span.empty { background: #cbd5e1; }
    .form-actions { display: flex; justify-content: flex-end; gap: 10px; padding: 16px 24px; border-top: 1px solid var(--border-subtle); background: var(--surface-subtle); }
    .btn-primary, .btn-secondary { height: 40px; display: inline-flex; align-items: center; justify-content: center; gap: 7px; padding: 0 17px; border-radius: 9px; font: inherit; font-size: 12px; font-weight: 700; text-decoration: none; cursor: pointer; }
    .btn-primary { border: 1px solid var(--institutional-blue); color: #fff; background: var(--institutional-blue); box-shadow: 0 5px 12px rgba(37, 99, 235, .2); }
    .btn-primary:hover:not(:disabled) { background: var(--institutional-blue); }
    .btn-primary:disabled { opacity: .5; cursor: not-allowed; }
    .btn-secondary { border: 1px solid var(--control-border); color: var(--control-text); background: var(--control-bg); }
    .btn-secondary:hover { border-color: var(--control-focus-border); color: var(--text-link); background: var(--control-bg-hover); }
    .requirements { padding: 10px 20px; }
    .requirement { display: flex; align-items: center; gap: 9px; padding: 11px 0; border-bottom: 1px solid var(--border-subtle); color: var(--text-soft); font-size: 12px; line-height: 1.35; }
    .requirement:last-child { border-bottom: 0; }
    .requirement ion-icon { flex: 0 0 auto; color: #c3ceda; font-size: 18px; }
    .requirement.valid { color: #166534; }
    .requirement.valid ion-icon { color: #22c55e; }
    @media (max-width: 900px) { .content-grid { grid-template-columns: 1fr; } .policy-card { grid-row: 1; } }
    @media (max-width: 640px) { .page-header { align-items: flex-start; } .back-link { width: 40px; height: 40px; padding: 0; justify-content: center; font-size: 0; } .back-link ion-icon { font-size: 20px; } .secure-badge { display: none; } .field-grid { grid-template-columns: 1fr; } .card-content { padding: 20px; } .form-actions { display: grid; grid-template-columns: 1fr 1fr; padding: 14px 20px; } }
    @media (prefers-reduced-motion: reduce) { *, *::before, *::after { transition-duration: .01ms !important; } }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PasswordComponent implements OnInit {
  private readonly configDataService = inject(ConfigDataService);
  private readonly alertCtrl = inject(AlertController);
  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);

  protected readonly policy = signal<PasswordPolicy>(DEFAULT_POLICY);
  protected readonly saving = signal(false);
  protected readonly requiredChange = computed(() => this.route.snapshot.queryParamMap.get('required') === 'true'
    || !!this.authService.currentUser()?.cambioContrasenaRequerido);
  protected newPassword = '';
  protected confirmPassword = '';
  protected showNew = false;
  protected showConfirm = false;
  protected newPasswordTouched = false;
  protected submitted = false;

  constructor() {
    addIcons({
      arrowBackOutline, checkmarkCircleOutline, closeCircleOutline, eyeOffOutline,
      eyeOutline, lockClosedOutline, saveOutline,
      shieldCheckmarkOutline,
    });
  }

  ngOnInit(): void {
    this.configDataService.getMyPasswordPolicy().subscribe({
      next: policy => this.policy.set({ ...DEFAULT_POLICY, ...policy }),
      error: () => this.policy.set(DEFAULT_POLICY),
    });
  }

  protected hasRequiredLength(): boolean {
    return this.newPassword.length >= this.policy().minLength && this.newPassword.length <= this.policy().maxLength;
  }
  protected hasUppercase(): boolean { return /[A-Z]/.test(this.newPassword); }
  protected hasLowercase(): boolean { return /[a-z]/.test(this.newPassword); }
  protected hasDigit(): boolean { return /[0-9]/.test(this.newPassword); }
  protected hasSpecialChar(): boolean { return /[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>/?]/.test(this.newPassword); }
  protected passwordsMatch(): boolean { return this.newPassword === this.confirmPassword; }

  protected meetsPolicy(): boolean {
    const policy = this.policy();
    return this.hasRequiredLength()
      && (!policy.requiresUppercase || this.hasUppercase())
      && (!policy.requiresLowercase || this.hasLowercase())
      && (!policy.requiresDigits || this.hasDigit())
      && (!policy.requiresSpecialChars || this.hasSpecialChar());
  }

  protected canSubmit(): boolean {
    return this.meetsPolicy()
      && !!this.confirmPassword
      && this.passwordsMatch();
  }

  protected strengthPercent(): number {
    if (!this.newPassword) return 0;
    const policy = this.policy();
    const checks = [
      this.hasRequiredLength(),
      !policy.requiresUppercase || this.hasUppercase(),
      !policy.requiresLowercase || this.hasLowercase(),
      !policy.requiresDigits || this.hasDigit(),
      !policy.requiresSpecialChars || this.hasSpecialChar(),
    ];
    return Math.round((checks.filter(Boolean).length / checks.length) * 100);
  }

  protected strengthLabel(): string {
    const value = this.strengthPercent();
    return value === 0 ? 'Aún sin evaluar' : value < 60 ? 'Débil' : value < 100 ? 'Media' : 'Fuerte';
  }

  protected strengthClass(): string {
    const value = this.strengthPercent();
    return value === 0 ? 'empty' : value < 60 ? 'weak' : value < 100 ? 'medium' : 'strong';
  }

  protected async changePassword(): Promise<void> {
    this.submitted = true;
    this.newPasswordTouched = true;
    if (!this.canSubmit() || this.saving()) return;

    const currentPassword = this.requiredChange() ? '' : await this.requestCurrentPassword();
    if (!this.requiredChange() && !currentPassword) return;

    this.saving.set(true);
    const operation = this.requiredChange()
      ? this.configDataService.completeRequiredPassword(this.newPassword)
      : this.configDataService.changeMyPassword(currentPassword!, this.newPassword);
    operation.subscribe({
      next: async () => {
        this.saving.set(false);
        this.newPassword = '';
        this.confirmPassword = '';
        this.submitted = false;
        this.newPasswordTouched = false;
        await this.showAlert('Contraseña actualizada', this.requiredChange() ? 'La contraseña temporal fue reemplazada. Inicia sesión con tu nueva contraseña.' : 'Tu nueva contraseña ya está activa.');
        if (this.requiredChange()) this.authService.logout();
      },
      error: async (err: any) => {
        this.saving.set(false);
        const message = err.error?.error || 'No fue posible cambiar la contraseña.';
        await this.showAlert('No se pudo actualizar', typeof message === 'string' ? message : JSON.stringify(message));
      },
    });
  }

  private async showAlert(header: string, message: string): Promise<void> {
    const alert = await this.alertCtrl.create({ header, message, buttons: ['Aceptar'] });
    await alert.present();
  }

  private async requestCurrentPassword(): Promise<string | null> {
    const alert = await this.alertCtrl.create({
      header: 'Confirma tu identidad',
      message: 'Ingresa tu contraseña actual para autorizar el cambio.',
      inputs: [{ name: 'currentPassword', type: 'password', placeholder: 'Contraseña actual', attributes: { autocomplete: 'current-password' } }],
      buttons: [
        { text: 'Cancelar', role: 'cancel' },
        { text: 'Continuar', role: 'confirm' },
      ],
    });
    await alert.present();
    const result = await alert.onDidDismiss();
    if (result.role !== 'confirm') return null;
    const value = result.data?.values?.currentPassword;
    if (typeof value !== 'string' || !value) {
      await this.showAlert('Contraseña requerida', 'Debes ingresar tu contraseña actual para continuar.');
      return null;
    }
    return value;
  }
}
