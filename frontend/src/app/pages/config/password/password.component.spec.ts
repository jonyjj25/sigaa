import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { AlertController } from '@ionic/angular';
import { of, throwError } from 'rxjs';
import { UserInfo } from '../../../models/dashboard.models';
import { AuthService } from '../../../services/auth.service';
import { ConfigDataService } from '../services/config-data.service';
import { PasswordComponent } from './password.component';

describe('PasswordComponent required change', () => {
  const user = signal<UserInfo | null>(null);
  let data: jasmine.SpyObj<ConfigDataService>;
  let logout: jasmine.Spy;
  let component: PasswordComponent;
  let state: { requiredChange: () => boolean; newPassword: string; confirmPassword: string; changePassword: () => Promise<void> };

  beforeEach(async () => {
    user.set({ nombre: 'Usuario', correo: 'usuario@example.test', roles: '' });
    logout = jasmine.createSpy('logout');
    data = jasmine.createSpyObj('ConfigDataService', ['getMyPasswordPolicy', 'completeRequiredPassword', 'changeMyPassword']);
    data.getMyPasswordPolicy.and.returnValue(of({ minLength: 12, maxLength: 128, requiresUppercase: false,
      requiresLowercase: false, requiresDigits: false, requiresSpecialChars: false, historySize: 5 }));
    data.completeRequiredPassword.and.returnValue(of({ message: 'Actualizada' }));
    await TestBed.configureTestingModule({
      imports: [PasswordComponent],
      providers: [
        { provide: ConfigDataService, useValue: data },
        { provide: AuthService, useValue: { currentUser: user, logout } },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: { get: () => null } } } },
        { provide: AlertController, useValue: { create: jasmine.createSpy('create').and.resolveTo({ present: async () => undefined }) } },
      ],
    }).overrideComponent(PasswordComponent, { set: { template: '' } }).compileComponents();
    const fixture = TestBed.createComponent(PasswordComponent);
    component = fixture.componentInstance;
    state = component as unknown as typeof state;
    fixture.detectChanges();
    state.newPassword = 'TEST_ONLY_NEW_PASSWORD';
    state.confirmPassword = state.newPassword;
  });

  it('updates an already open password form when the backend restricts the session', () => {
    expect(state.requiredChange()).toBeFalse();
    user.set({ nombre: 'Usuario', correo: 'usuario@example.test', roles: '', cambioContrasenaRequerido: true });
    expect(state.requiredChange()).toBeTrue();
  });

  it('uses the existing required-change endpoint and logs out only after success', async () => {
    user.set({ nombre: 'Usuario', correo: 'usuario@example.test', roles: '', cambioContrasenaRequerido: true });
    await state.changePassword();
    // El callback espera a que se presente la confirmación antes de cerrar sesión.
    await Promise.resolve();
    await Promise.resolve();
    expect(data.completeRequiredPassword).toHaveBeenCalledWith('TEST_ONLY_NEW_PASSWORD');
    expect(data.changeMyPassword).not.toHaveBeenCalled();
    expect(logout).toHaveBeenCalledTimes(1);
  });

  it('keeps the restricted state when the required change fails', async () => {
    user.set({ nombre: 'Usuario', correo: 'usuario@example.test', roles: '', cambioContrasenaRequerido: true });
    data.completeRequiredPassword.and.returnValue(throwError(() => ({ status: 400, error: { error: 'Clave rechazada' } })));
    await state.changePassword();
    expect(state.requiredChange()).toBeTrue();
    expect(logout).not.toHaveBeenCalled();
  });
});
