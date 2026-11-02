import { Component, ElementRef, inject, signal, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { IonContent, IonItem, IonIcon, IonSpinner } from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { alertCircleOutline, eyeOutline, eyeOffOutline, keyOutline, lockClosedOutline, mailOutline, personOutline, shieldCheckmarkOutline } from 'ionicons/icons';
import { AuthService } from '../services/auth.service';
import { PasswordResetService } from '../services/password-reset.service';
import { ConfigDataService } from '../pages/config/services/config-data.service';
import { environment } from '../../environments/environment';
import { UserPreferences, DEFAULT_PREFERENCES } from '../models/dashboard.models';

@Component({ selector: 'app-home', templateUrl: 'home.page.html', styleUrls: ['home.page.scss'], imports: [CommonModule, FormsModule, IonContent, IonItem, IonIcon, IonSpinner] })
export class HomePage {
  private static nextFormId = 0;
  readonly formId = `sigaa-login-${HomePage.nextFormId++}`;

  @ViewChild('usernameInput') private usernameInput?: ElementRef<HTMLInputElement>;
  @ViewChild('passwordInput') private passwordInput?: ElementRef<HTMLInputElement>;

  private readonly authService = inject(AuthService);
  private readonly passwordResetService = inject(PasswordResetService);
  private readonly configDataService = inject(ConfigDataService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  username = ''; password = ''; code = ''; tempToken = ''; message = ''; isLoading = false;
  messageType: 'error' | 'success' | 'info' = 'info';
  rememberUsername = false; loginSubmitted = false; usernameTouched = false; passwordTouched = false;
  showPassword = signal(false); requires2fa = signal(false);
  forgotPasswordMode = signal(false); resetStep = signal<'request' | 'verify' | 'password' | 'success'>('request');
  forgotPasswordEmail = ''; resetCode = ''; newPassword = ''; confirmPassword = ''; resetToken = '';
  resendSeconds = signal(0); showResetPassword = signal(false); showResetConfirmation = signal(false);

  constructor() {
    addIcons({ alertCircleOutline, eyeOutline, eyeOffOutline, keyOutline, lockClosedOutline, mailOutline, personOutline, shieldCheckmarkOutline });
    this.restoreRememberedUsername();
    if (this.authService.isAuthenticated()) {
      if (this.authService.currentUser()?.cambioContrasenaRequerido) {
        this.router.navigate(['/config/password'], { queryParams: { required: 'true' } });
      } else this.navigateToPreferredHome();
    }
  }
  togglePassword(): void { this.showPassword.update(v => !v); }
  toggleResetPassword(): void { this.showResetPassword.update(v => !v); }
  toggleResetConfirmation(): void { this.showResetConfirmation.update(v => !v); }
  setRememberUsername(checked: boolean): void { this.rememberUsername = checked; if (!checked) localStorage.removeItem('sigaa_remembered_username'); }

  private loadPrefsAndNavigate(): void {
    this.configDataService.getMyPreferences().subscribe({
      next: (prefs: UserPreferences) => { this.authService.updatePreferences(prefs); this.navigateToPreferredHome(); },
      error: () => { this.authService.updatePreferences({ ...DEFAULT_PREFERENCES }); this.navigateToPreferredHome(); },
    });
  }
  private navigateToPreferredHome(): void {
    const home = this.authService.allowedHomePage(this.authService.preferences().homePage);
    this.router.navigate([this.returnUrl() || (home === 'dashboard' ? '/dashboard' : `/${home}`)]);
  }
  private returnUrl(): string | null { const value = this.route.snapshot.queryParamMap.get('returnUrl'); return value?.startsWith('/') && !value.startsWith('//') ? value : null; }

  async login(): Promise<void> {
    this.username = this.username.trim();
    this.loginSubmitted = true;
    if (!this.username || !this.password) {
      this.setMessage('Ingresa tu nombre de usuario y contraseña.', 'error');
      return;
    }
    this.persistRememberedUsername();
    this.clearMessage(); this.isLoading = true;
    try {
      const response = await fetch(`${environment.apiUrl}/api/auth/login`, { method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: this.username, password: this.password }) });
      if (response.ok) {
        const body = await response.json();
        if (body.requires2fa) { this.tempToken = body.tempToken; this.requires2fa.set(true); this.setMessage('Se ha enviado un código de verificación a tu correo.', 'info'); return; }
        if (body?.token) { this.saveAuthenticatedUser(body); if (!body.cambioContrasenaRequerido) this.loadPrefsAndNavigate(); return; }
      }
      const errorBody = await response.json().catch(() => null); this.setMessage(errorBody?.message || 'Nombre de usuario o contraseña incorrectos', 'error');
    } catch { this.setMessage('No se pudo conectar con el servidor.', 'error'); }
    finally { this.isLoading = false; }
  }

  async loginFromForm(): Promise<void> {
    const emailField = this.usernameInput?.nativeElement;
    const passwordField = this.passwordInput?.nativeElement;
    this.username = (emailField?.value ?? this.username).trim();
    this.password = passwordField?.value ?? this.password;
    await this.login();
  }

  async verify2fa(): Promise<void> {
    this.clearMessage(); this.isLoading = true;
    try {
      const response = await fetch(`${environment.apiUrl}/api/auth/2fa/authenticate`, { method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ tempToken: this.tempToken, code: this.code }) });
      const body = await response.json().catch(() => null);
      if (response.ok && body?.token) { this.saveAuthenticatedUser(body, true); if (!body.cambioContrasenaRequerido) this.loadPrefsAndNavigate(); return; }
      this.setMessage(body?.message || 'Código inválido o expirado', 'error');
    } catch { this.setMessage('No se pudo conectar con el servidor.', 'error'); }
    finally { this.isLoading = false; }
  }

  toggleForgotPassword(): void {
    this.forgotPasswordMode.update(v => !v); this.resetStep.set('request'); this.clearMessage(); this.forgotPasswordEmail = ''; this.resetCode = ''; this.newPassword = ''; this.confirmPassword = ''; this.resetToken = ''; this.resendSeconds.set(0);
  }

  async sendResetCode(): Promise<void> {
    const email = this.forgotPasswordEmail.trim();
    if (!this.isValidEmail(email)) { this.setMessage('Ingresa un correo electrónico válido.', 'error'); return; }
    this.clearMessage(); this.isLoading = true;
    const result = await this.passwordResetService.requestPasswordReset(email); this.isLoading = false;
    if (!result.ok) { this.setMessage(result.message || 'No fue posible enviar el código.', 'error'); return; }
    this.forgotPasswordEmail = email; this.resetStep.set('verify'); this.startResendTimer();
    this.setMessage(result.message || 'Revisa tu correo e ingresa el código de verificación.', 'success');
  }

  async verifyResetCode(): Promise<void> {
    this.clearMessage();
    if (!/^\d{6}$/.test(this.resetCode)) { this.setMessage('Ingresa los 6 dígitos del código.', 'error'); return; }
    this.isLoading = true; const result = await this.passwordResetService.verifyPasswordResetCode(this.forgotPasswordEmail, this.resetCode); this.isLoading = false;
    if (!result.ok || !result.resetToken) { this.setMessage(result.message || 'El código no es válido.', 'error'); return; }
    this.resetToken = result.resetToken; this.resetCode = ''; this.resetStep.set('password');
  }

  async resendResetCode(): Promise<void> {
    if (this.resendSeconds() > 0 || this.isLoading) return;
    this.isLoading = true; const result = await this.passwordResetService.resendPasswordResetCode(this.forgotPasswordEmail); this.isLoading = false;
    this.setMessage(result.ok ? (result.message || 'Si el correo está registrado, recibirás un código de verificación.') : (result.message || 'No fue posible reenviar el código.'), result.ok ? 'success' : 'error');
    if (result.ok) this.startResendTimer();
  }

  async resetPassword(): Promise<void> {
    this.clearMessage();
    if (this.newPassword !== this.confirmPassword) { this.setMessage('Las contraseñas no coinciden.', 'error'); return; }
    if (!this.isPasswordReady()) { this.setMessage('La contraseña no cumple los requisitos indicados.', 'error'); return; }
    this.isLoading = true; const result = await this.passwordResetService.resetPassword(this.resetToken, this.newPassword); this.isLoading = false;
    if (!result.ok) { this.setMessage(result.message || 'No fue posible actualizar la contraseña.', 'error'); return; }
    this.resetToken = ''; this.newPassword = ''; this.confirmPassword = ''; this.resetStep.set('success'); this.clearMessage();
  }

  sanitizeResetCode(): void { this.resetCode = this.resetCode.replace(/\D/g, '').slice(0, 6); }
  changeResetEmail(): void { this.resetCode = ''; this.resetStep.set('request'); this.clearMessage(); }
  usernameInvalid(): boolean { return (this.loginSubmitted || this.usernameTouched) && !this.username.trim(); }
  passwordInvalid(): boolean { return (this.loginSubmitted || this.passwordTouched) && !this.password; }
  isPasswordReady(): boolean { const value = this.newPassword; return value === value.trim() && value.length >= 8 && /[A-Z]/.test(value) && /[a-z]/.test(value) && /\d/.test(value) && /[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>/?]/.test(value); }
  private isValidEmail(email: string): boolean { return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email); }
  private restoreRememberedUsername(): void { const remembered = localStorage.getItem('sigaa_remembered_username'); if (remembered) { this.username = remembered; this.rememberUsername = true; } }
  private persistRememberedUsername(): void { if (this.rememberUsername) localStorage.setItem('sigaa_remembered_username', this.username); else localStorage.removeItem('sigaa_remembered_username'); }
  private clearMessage(): void { this.message = ''; this.messageType = 'info'; }
  private setMessage(message: string, type: 'error' | 'success' | 'info'): void { this.message = message; this.messageType = type; }
  private startResendTimer(): void { this.resendSeconds.set(60); const timer = window.setInterval(() => { const next = this.resendSeconds() - 1; this.resendSeconds.set(Math.max(0, next)); if (next <= 0) window.clearInterval(timer); }, 1000); }
  private saveAuthenticatedUser(body: any, has2fa = false): void {
    this.authService.saveToken(body.token);
    this.authService.saveUserInfo({ id: body.id, nombre: body.nombre, correo: body.correo, roles: body.roles || '', permisos: body.permisos || [], has2fa, fotografiaRuta: body.fotografiaRuta || '', cambioContrasenaRequerido: !!body.cambioContrasenaRequerido });
    if (body.cambioContrasenaRequerido) this.router.navigate(['/config/password'], { queryParams: { required: 'true' } });
  }
}
