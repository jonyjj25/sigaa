import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject } from 'rxjs';
import { signal } from '@angular/core';
import { DEFAULT_PREFERENCES, UserInfo } from '../models/dashboard.models';

import { HomePage } from './home.page';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { PasswordResetService } from '../services/password-reset.service';
import { ConfigDataService } from '../pages/config/services/config-data.service';

describe('HomePage', () => {
  let component: HomePage;
  let fixture: ComponentFixture<HomePage>;
  const currentUser = signal<UserInfo | null>(null);
  const preferences = signal({ ...DEFAULT_PREFERENCES });
  const auth = { isAuthenticated: jasmine.createSpy('isAuthenticated'), currentUser, preferences,
    updatePreferences: (value: typeof DEFAULT_PREFERENCES) => preferences.set(value),
    allowedHomePage: jasmine.createSpy('allowedHomePage') };
  const router = { navigate: jasmine.createSpy('navigate') };
  const config = { getMyPreferences: jasmine.createSpy('getMyPreferences') };
  const passwordResetService = {
    requestPasswordReset: jasmine.createSpy('requestPasswordReset').and.resolveTo({ ok: true, status: 200, message: 'Código enviado.' }),
    resendPasswordResetCode: jasmine.createSpy('resendPasswordResetCode').and.resolveTo({ ok: true, status: 200 }),
    verifyPasswordResetCode: jasmine.createSpy('verifyPasswordResetCode').and.resolveTo({ ok: true, status: 200, resetToken: 'token' }),
    resetPassword: jasmine.createSpy('resetPassword').and.resolveTo({ ok: true, status: 200 }),
  };

  beforeEach(async () => {
    localStorage.removeItem('sigaa_remembered_username');
    auth.isAuthenticated.and.returnValue(false); auth.allowedHomePage.and.callFake((home: string) => home);
    preferences.set({ ...DEFAULT_PREFERENCES }); currentUser.set(null); router.navigate.calls.reset();
    config.getMyPreferences.calls.reset(); config.getMyPreferences.and.returnValue(of(DEFAULT_PREFERENCES));
    passwordResetService.requestPasswordReset.calls.reset();
    passwordResetService.resendPasswordResetCode.calls.reset();
    passwordResetService.verifyPasswordResetCode.calls.reset();
    passwordResetService.resetPassword.calls.reset();
    await TestBed.configureTestingModule({
      imports: [HomePage],
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: PasswordResetService, useValue: passwordResetService },
        { provide: ConfigDataService, useValue: config },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: { get: () => null } } } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(HomePage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('associates login labels with unique native form fields', async () => {
    await fixture.whenStable();
    const root: HTMLElement = fixture.nativeElement;
    const nativeInputs = Array.from(root.querySelectorAll<HTMLInputElement>('input.auth-input'));
    const ids = Array.from(root.querySelectorAll('[id]'), element => element.id);

    expect(nativeInputs.length).toBe(2);
    expect(new Set(ids).size).withContext(`Rendered IDs: ${ids.join(', ')}`).toBe(ids.length);
    for (const input of nativeInputs) {
      const label = root.querySelector<HTMLLabelElement>(`label.input-label[for="${input.id}"]`);
      expect(label?.control).withContext(`Label for native field ${input.id}`).toBe(input);
    }
  });

  it('keeps field identifiers unique when two login views coexist', async () => {
    const second = TestBed.createComponent(HomePage);
    try {
      // TestBed replaces the previous root; attach both to reproduce cached views.
      document.body.appendChild(fixture.nativeElement);
      second.detectChanges();
      await second.whenStable();
      const roots: HTMLElement[] = [fixture.nativeElement, second.nativeElement];
      const ids: string[] = [];
      for (const root of roots) {
        ids.push(...Array.from(root.querySelectorAll('[id]'), element => element.id));
      }

      expect(new Set(ids).size).toBe(ids.length);
      for (const root of roots) {
        for (const label of Array.from(root.querySelectorAll<HTMLLabelElement>('label[for]'))) {
          expect(label.control).not.toBeNull();
          expect(root.contains(label.control)).toBeTrue();
        }
      }
    } finally {
      second.destroy();
    }
  });

  it('associates verification and recovery labels with their native controls', async () => {
    component.requires2fa.set(true);
    for (const step of ['2fa', 'request', 'verify', 'password'] as const) {
      if (step !== '2fa') {
        component.forgotPasswordMode.set(true);
        component.resetStep.set(step);
      }
      fixture.detectChanges();
      await fixture.whenStable();
      const root: HTMLElement = fixture.nativeElement;
      const fields = Array.from(root.querySelectorAll<HTMLInputElement>('input.auth-input'));

      expect(fields.length).withContext(step).toBe(step === 'password' ? 2 : 1);
      for (const field of fields) {
        const label = root.querySelector<HTMLLabelElement>(`label[for="${field.id}"]`);
        expect(label?.control).withContext(`${step}: ${field.name}`).toBe(field);
        expect(field.autocomplete).not.toBe('');
      }
    }
  });

  it('reads autofilled native values before submitting the login', async () => {
    const root: HTMLElement = fixture.nativeElement;
    root.querySelector<HTMLInputElement>('input[name="username"]')!.value = ' usuario_prueba ';
    root.querySelector<HTMLInputElement>('input[name="password"]')!.value = 'TEST_ONLY_NOT_REAL';
    const login = spyOn(component, 'login').and.resolveTo();

    await component.loginFromForm();

    expect(component.username).toBe('usuario_prueba');
    expect(component.password).toBe('TEST_ONLY_NOT_REAL');
    expect(login).toHaveBeenCalledTimes(1);
  });

  it('reuses preferences from the existing session without requesting them again', () => {
    fixture.destroy(); auth.isAuthenticated.and.returnValue(true);
    preferences.update(value => ({ ...value, homePage: 'documentos' }));
    fixture = TestBed.createComponent(HomePage);
    expect(router.navigate).toHaveBeenCalledWith(['/documentos']);
    expect(config.getMyPreferences).not.toHaveBeenCalled();
  });

  it('keeps the required-password redirect ahead of cached preferences', () => {
    fixture.destroy(); auth.isAuthenticated.and.returnValue(true);
    currentUser.set({ nombre: 'Usuario', correo: 'usuario@example.test', roles: '', cambioContrasenaRequerido: true });
    fixture = TestBed.createComponent(HomePage);
    expect(router.navigate).toHaveBeenCalledWith(['/config/password'], { queryParams: { required: 'true' } });
    expect(config.getMyPreferences).not.toHaveBeenCalled();
  });

  it('performs one authoritative preference read after a fresh login and respects the chosen home page', () => {
    const response = new Subject<typeof DEFAULT_PREFERENCES>(); config.getMyPreferences.and.returnValue(response);
    (component as unknown as { loadPrefsAndNavigate: () => void }).loadPrefsAndNavigate();
    expect(config.getMyPreferences).toHaveBeenCalledTimes(1); expect(router.navigate).not.toHaveBeenCalled();
    response.next({ ...DEFAULT_PREFERENCES, homePage: 'reuniones' }); response.complete();
    expect(router.navigate).toHaveBeenCalledWith(['/reuniones']);
  });

  it('uses the allowed page when a cached home page is no longer permitted', () => {
    fixture.destroy(); auth.isAuthenticated.and.returnValue(true);
    preferences.update(value => ({ ...value, homePage: 'reporte' }));
    auth.allowedHomePage.and.returnValue('dashboard'); fixture = TestBed.createComponent(HomePage);
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
  });

  it('continues to code verification after a reset code is requested', async () => {
    component.forgotPasswordEmail = 'usuario@institucion.pa';

    await component.sendResetCode();

    expect(passwordResetService.requestPasswordReset).toHaveBeenCalledWith('usuario@institucion.pa');
    expect(component.resetStep()).toBe('verify');
    expect(component.messageType).toBe('success');
  });
});
