import { TestBed } from '@angular/core/testing';
import { of, Subject } from 'rxjs';
import { ApiService } from './api.service';
import { AuthService } from './auth.service';
import { DashboardService } from './dashboard.service';
import { Notification } from '../models/dashboard.models';

describe('DashboardService after removing Solicitudes', () => {
  let service: DashboardService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        DashboardService,
        { provide: AuthService, useValue: {
          isAuthenticated: () => true,
          isAdmin: () => true,
          isSuperAdmin: () => true,
          hasRole: () => false,
          hasPermission: () => true,
          sessionChanged$: new Subject<void>(),
        } },
        { provide: ApiService, useValue: { get: () => of([
          { id: 1, nombre: 'Solicitudes', ruta: '/solicitudes', icono: '', activo: true, orden: 1 },
          { id: 2, nombre: 'Documentos', ruta: '/documentos', icono: '', activo: true, orden: 2 },
          { id: 3, nombre: 'Configuración', ruta: '/config', icono: '', activo: true, orden: 3 },
          { id: 4, nombre: 'Solicitudes', ruta: '/solicitudes/', icono: '', activo: true, orden: 4 },
        ]) } },
      ],
    });
    service = TestBed.inject(DashboardService);
  });

  it('hides stored Solicitudes menu entries for administrators and preserves other modules', () => {
    const routes = service.getMenuItems().map(item => item.route);
    expect(routes).not.toContain('/solicitudes');
    expect(routes).not.toContain('/solicitudes/');
    expect(routes).toContain('/documentos');
    expect(routes).toContain('/config');
  });

  function notification(title: string, message: string): Notification {
    return { id: 'test', title, message, type: 'info', timestamp: new Date(0), read: false };
  }

  it('sends historical Solicitudes notifications to the dashboard', () => {
    expect(service.notificationUrl(notification('Solicitud aprobada', 'SOL-2026-000001: documento de prueba')))
      .toBe('/dashboard');
  });

  it('preserves document approval notification navigation', () => {
    expect(service.notificationUrl(notification('Solicitud de aprobación', 'Documento de prueba')))
      .toBe('/documentos');
  });

  function operationalMenu(): void {
    spyOn(TestBed.inject(ApiService), 'get').and.returnValue(of([
      { id: 1, nombre: 'Actas', ruta: '/actas', icono: '', activo: true, orden: 1 },
      { id: 2, nombre: 'Documentos', ruta: '/documentos', icono: '', activo: true, orden: 2 },
      { id: 3, nombre: 'Tareas', ruta: '/tareas', icono: '', activo: true, orden: 3 },
      { id: 4, nombre: 'Inventario', ruta: '/inventario', icono: '', activo: true, orden: 4 },
      { id: 5, nombre: 'Reportes', ruta: '/reporte', icono: '', activo: true, orden: 5 },
    ]));
    spyOn(TestBed.inject(AuthService), 'isAdmin').and.returnValue(false);
    spyOn(TestBed.inject(AuthService), 'isSuperAdmin').and.returnValue(false);
  }

  it('shows Secretariat its documentary modules without unrelated operational menus', () => {
    operationalMenu();
    spyOn(TestBed.inject(AuthService), 'hasRole').and.callFake(name => name === 'Secretaria');
    spyOn(TestBed.inject(AuthService), 'hasPermission').and.callFake(module => ['Actas', 'Documentos'].includes(module));
    service.refreshMenuItems();
    const routes = service.getMenuItems().map(item => item.route);
    expect(routes).toContain('/actas');
    expect(routes).toContain('/documentos');
    expect(routes).not.toContain('/tareas');
    expect(routes).not.toContain('/inventario');
    expect(routes).not.toContain('/reporte');
  });

  it('shows Direction and Subdirection equivalent permitted operational menus', () => {
    operationalMenu();
    const roleCheck = spyOn(TestBed.inject(AuthService), 'hasRole');
    roleCheck.and.callFake(name => name === 'Director');
    service.refreshMenuItems();
    const direction = service.getMenuItems().map(item => item.route);
    roleCheck.and.callFake(name => name === 'Subdirector');
    service.refreshMenuItems();
    expect(service.getMenuItems().map(item => item.route)).toEqual(direction);
    expect(direction).toContain('/actas');
    expect(direction).toContain('/tareas');
    expect(direction).toContain('/inventario');
    expect(direction).toContain('/reporte');
  });

  it('uses the task icon fallback when the configured module icon is empty', () => {
    operationalMenu();
    spyOn(TestBed.inject(AuthService), 'hasRole').and.callFake(name => name === 'Jefe de Unidad');
    service.refreshMenuItems();
    expect(service.getMenuItems().find(item => item.route === '/tareas')?.icon).toBe('clipboard-outline');
  });
});
