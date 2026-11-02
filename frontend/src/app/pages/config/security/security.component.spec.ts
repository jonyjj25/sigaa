import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { SecurityComponent } from './security.component';
import { ConfigDataService } from '../services/config-data.service';
import { SecurityPolicy } from '../models/config.models';

describe('SecurityComponent functional configuration', () => {
  let fixture: ComponentFixture<SecurityComponent>;
  let service: jasmine.SpyObj<ConfigDataService>;
  const policy = (id: string, nombre: string, categoria: string, valor = 'true'): SecurityPolicy =>
    ({ id, nombre, categoria, valor, administrable: true, descripcion: 'Configuración ficticia de prueba' });

  beforeEach(async () => {
    service = jasmine.createSpyObj<ConfigDataService>('ConfigDataService', ['getSecurityPolicies', 'updateSecurityPolicy']);
    service.getSecurityPolicies.and.returnValue(of([
      policy('1', 'Longitud mínima de contraseña', 'Contrasena', '12'),
      policy('2', 'Autenticación de dos factores', 'Autenticación'),
      policy('3', 'Restricción por horario', 'IP'),
      policy('4', 'CORS habilitado', 'CORS'),
      policy('5', 'Orígenes permitidos', 'CORS', 'https://historical.example.test'),
      policy('6', 'Credenciales permitidos', 'Legado'),
      policy('7', 'CORS facilita', 'Legado'),
      policy('8', 'Encabezados permitidos', 'CORS', '*'),
      policy('9', 'Métodos HTTP permitidos', 'CORS', 'GET'),
      policy('10', 'Credenciales permitidas', 'CORS'),
      policy('12', 'Tiempo de expiración JWT (min)', 'JWT', '60'),
      policy('13', 'Tiempo de expiración del token temporal 2FA (seg)', 'Autenticación', '300'),
      policy('14', 'Restricción por IP', 'IP'),
      policy('15', 'Lista blanca de IP activa', 'IP'),
      policy('16', 'Límite de carga por petición (MB)', 'Carga', '50'),
    ]));
    await TestBed.configureTestingModule({
      imports: [SecurityComponent], providers: [{ provide: ConfigDataService, useValue: service }],
    }).compileComponents();
    fixture = TestBed.createComponent(SecurityComponent);
    fixture.detectChanges();
  });

  it('does not render CORS controls or historical values, including legacy aliases', () => {
    const text = fixture.nativeElement.textContent as string;
    expect(text).not.toContain('CORS');
    expect(text).not.toContain('Credenciales permitid');
    expect(text).not.toContain('Orígenes permitidos');
    expect(text).not.toContain('historical.example.test');
    expect(text).not.toContain('Tiempo de expiración');
    expect(text).not.toContain('Restricción por IP');
    expect(text).not.toContain('Lista blanca');
    expect(text).not.toContain('Límite de carga por petición');
    expect(fixture.nativeElement.querySelectorAll('.policy-item').length).toBe(3);
    expect(service.updateSecurityPolicy).not.toHaveBeenCalled();
  });

  it('preserves other security policies and their existing update flow', () => {
    const input = fixture.nativeElement.querySelector('#policy-1') as HTMLInputElement;
    service.updateSecurityPolicy.and.returnValue(of(policy('1', 'Longitud mínima de contraseña', 'Contrasena', '14')));
    input.value = '14';
    input.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(service.updateSecurityPolicy).toHaveBeenCalledOnceWith('1', '14');
    expect(fixture.nativeElement.textContent).toContain('se guardó correctamente');
    expect(fixture.nativeElement.textContent).toContain('Autenticación de dos factores');
    expect(fixture.nativeElement.textContent).toContain('Restricción por horario');
    expect(fixture.nativeElement.textContent).toContain('Horarios institucionales');
  });

  it('also hides a historical CORS category with a normally visible policy name', () => {
    fixture.destroy();
    service.getSecurityPolicies.and.returnValue(of([policy('11', 'Restricción por horario', ' cors ')]));
    fixture = TestBed.createComponent(SecurityComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.policy-item').length).toBe(0);
    expect(service.updateSecurityPolicy).not.toHaveBeenCalled();
  });

  it('hides policies that the server marks as non-administrable even with a functional name', () => {
    fixture.destroy();
    service.getSecurityPolicies.and.returnValue(of([{ ...policy('17', 'Restricción por horario', 'IP'), administrable: false }]));
    fixture = TestBed.createComponent(SecurityComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.policy-item').length).toBe(0);
  });

  it('shows the functional file limit with the technical maximum supplied by the server', () => {
    fixture.destroy();
    service.getSecurityPolicies.and.returnValue(of([{ ...policy('18', 'Tamaño máximo de archivos (MB)', 'Carga', '4'), maximoServidorMb: 6 }]));
    fixture = TestBed.createComponent(SecurityComponent);
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('#policy-18') as HTMLInputElement;
    expect(input.max).toBe('6');
    expect(input.value).toBe('4');
    expect(fixture.nativeElement.textContent).toContain('Límite funcional por archivo');
    expect(fixture.nativeElement.textContent).toContain('Máximo del servidor: 6 MB');
  });
});
