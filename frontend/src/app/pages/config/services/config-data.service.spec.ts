import { TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';
import { ApiService } from '../../../services/api.service';
import { ConfigDataService } from './config-data.service';

describe('ConfigDataService API contracts', () => {
  let service: ConfigDataService;
  let api: jasmine.SpyObj<ApiService>;

  beforeEach(() => {
    api = jasmine.createSpyObj<ApiService>('ApiService', ['get', 'post', 'put', 'patch', 'delete', 'getBlob']);
    api.get.and.returnValue(of([]));
    api.post.and.returnValue(of({}));
    api.put.and.returnValue(of([]));
    api.patch.and.returnValue(of({}));
    api.delete.and.returnValue(of(undefined));
    api.getBlob.and.returnValue(of(new Blob(['backup'])));
    TestBed.configureTestingModule({ providers: [
      ConfigDataService,
      { provide: ApiService, useValue: api },
    ] });
    service = TestBed.inject(ConfigDataService);
  });

  const consume = (streams: Observable<unknown>[]) => streams.forEach(stream => stream.subscribe({ error: fail }));

  it('does not offer administrative helpers that write CORS origins', () => {
    expect('createAllowedOrigin' in service).toBeFalse();
    expect('updateAllowedOrigin' in service).toBeFalse();
    expect('deleteAllowedOrigin' in service).toBeFalse();
    expect('createIpRestriction' in service).toBeFalse();
    expect('updateIpRestriction' in service).toBeFalse();
    expect('deleteIpRestriction' in service).toBeFalse();
    expect('toggleIpRestriction' in service).toBeFalse();
  });

  it('preserves server editability and upload maximums when reading and updating policies', () => {
    const response = { id: 20, nombre: 'Tamaño máximo de archivos (MB)', valor: '4', descripcion: 'Fixture',
      categoria: 'Carga', administrable: true, maximoServidorMb: 7 };
    api.get.and.returnValue(of([response, { ...response, id: 21, administrable: false }]));
    service.getSecurityPolicies().subscribe(policies => {
      expect(policies[0].administrable).toBeTrue();
      expect(policies[0].maximoServidorMb).toBe(7);
      expect(policies[1].administrable).toBeFalse();
    });
    api.put.and.returnValue(of(response));
    service.updateSecurityPolicy('20', '4').subscribe(policy => {
      expect(policy.valor).toBe(4);
      expect(policy.administrable).toBeTrue();
      expect(policy.maximoServidorMb).toBe(7);
    });
    expect(api.put).toHaveBeenCalledWith('/api/security/policies/20', { valor: '4' });
  });

  it('distinguishes a failed query from a successful empty result and clears the error on recovery', () => {
    api.get.and.returnValue(throwError(() => ({ status: 503 })));
    service.getUsers().subscribe(users => expect(users).toEqual([]));
    expect(service.consultasConError()).toEqual(['/api/usuarios']);
    api.get.and.returnValue(of([]));
    service.getUsers().subscribe(users => expect(users).toEqual([]));
    expect(service.consultasConError()).toEqual([]);
  });

  it('does not clear another query failure when an independent query succeeds', () => {
    api.get.and.returnValue(throwError(() => ({ status: 503 })));
    service.getUsers().subscribe();
    api.get.and.returnValue(of([]));
    service.getRoles().subscribe();
    expect(service.consultasConError()).toEqual(['/api/usuarios']);
    service.limpiarErroresConsulta();
    expect(service.consultasConError()).toEqual([]);
  });

  it('maps users, roles, permissions, modules and organizational data', () => {
    api.get.and.returnValue(of([{
      id: 1, nombre: 'Ana', apellido: 'Pérez', username: 'ana.perez', correo: 'ana@example.test', activo: true,
      bloqueado: false, creadoEn: '2026-08-24T10:00:00', ultimoAcceso: null,
      roles: ['Miembro'], membresias: [],
    }]));
    service.getUsers().subscribe(users => expect(users[0].estado).toBe('activo'));

    api.get.and.returnValue(of([{
      id: 2, nombre: 'Administrador', descripcion: 'Control total', activo: true, sistema: true,
      permisos: [{ id: 3, modulo: 'Documentos', accion: 'Ver', recurso: '*', descripcion: '', activo: true }], usuarios: 1,
    }]));
    service.getRoles().subscribe(roles => {
      expect(roles[0].permisos).toBe(1);
      expect(roles[0].sistema).toBeTrue();
    });

    api.get.and.returnValue(of([{
      id: 3, modulo: 'Documentos', accion: 'Ver', recurso: '*', descripcion: '', activo: true,
    }]));
    service.getPermissions().subscribe(permissions => expect(permissions[0].modulo).toBe('Documentos'));

    api.get.and.returnValue(of([{
      id: 4, nombre: 'Documentos', ruta: '/documentos', icono: 'folder', activo: true,
      orden: 2, codigo: 'documentos', categoria: 'gestión',
    }]));
    service.getModules().subscribe(modules => expect(modules[0].ruta).toBe('/documentos'));

    api.get.and.returnValue(of([
      { id: 1, nombre: 'Dirección', siglas: 'DIR', nivel: 1, responsable: 'Ana', activo: true },
      { id: 2, nombre: 'Tecnología', siglas: 'TI', nivel: 2, padreId: 1, responsable: 'Luis', activo: true },
    ]));
    service.getOrganization().subscribe(tree => expect(tree[0].children?.[0].siglas).toBe('TI'));
  });

  it('updates permissions through the dedicated endpoint without role metadata', () => {
    api.put.and.returnValue(of({
      id: 2, nombre: 'Administrador', descripcion: 'Control total', activo: true, sistema: true,
      permisos: [{ id: 3, modulo: 'Documentos', accion: 'Ver', recurso: '*', descripcion: '', activo: true }],
      usuarios: 1,
    }));

    service.updateRolePermissions('2', [3]).subscribe(role => {
      expect(role.descripcion).toBe('Control total');
      expect(role.estado).toBeTrue();
    });

    expect(api.put).toHaveBeenCalledWith('/api/roles/2/permissions', { permisoIds: [3] });
  });

  it('maps backup operational evidence without exposing destructive UI helpers', () => {
    api.get.and.returnValue(of([{
      id: 9, nombre: 'sigaa_20260826.dump.enc', tamanio: '2.0 MB', estado: 'completado',
      tipo: 'automatico', fecha: '2026-08-26T02:00:00', copiaExterna: true,
      checksumSha256: 'a'.repeat(64), verificacionEstado: 'verificado', verificadoAt: '2026-08-26T04:00:00',
    }]));

    service.getBackups().subscribe(backups => {
      expect(backups[0].copiaExterna).toBeTrue();
      expect(backups[0].verificacionEstado).toBe('verificado');
      expect(backups[0].verificadoAt instanceof Date).toBeTrue();
    });

    service.createBackup().subscribe();
    expect(api.post).toHaveBeenCalledWith('/api/backups', {});
    expect((service as any).downloadBackup).toBeUndefined();
    expect((service as any).deleteBackupRecord).toBeUndefined();
  });

  it('keeps administrative CRUD endpoint wiring executable', () => {
    consume([
      service.deleteUser('1'), service.deleteUserPermanently('1'), service.toggleBlockUser('1'),
      service.createUnidad({ nombre: 'Tecnología' }), service.updateUnidad('1', { activo: true }),
      service.deleteUnidad('1'), service.getUnidades(),
      service.createRole({ nombre: 'Auditor', descripcion: 'Consulta' }),
      service.updateRole('1', { nombre: 'Auditor', descripcion: 'Consulta' }), service.deleteRole('1'),
      service.copyRolePermissions('1', '2'), service.resetRolePermissions('2'), service.getRoleUsers('1'),
      service.getPermissionModules(), service.getPermissionActions(),
      service.createModule({ nombre: 'Actas', ruta: '/actas' }), service.updateModule('1', { activo: true }),
      service.deleteModule('1'), service.toggleModule('1'), service.reorderModules([{ id: '1', orden: 1 }]),
      service.updateSecurityPolicy('1', 'true'), service.getSecurityPolicies(),
    ]);

    expect(api.delete).toHaveBeenCalled();
    expect(api.patch).toHaveBeenCalled();
    expect(api.put).toHaveBeenCalled();
    expect(api.post).toHaveBeenCalled();
  });

  it('wires audit, notifications, backups, security and self-service endpoints', () => {
    api.get.and.returnValue(of({
      content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true,
    }));
    service.getAuditPage({ page: 0, size: 20, q: 'login', accion: 'CREAR', entidad: 'usuario' })
      .subscribe(page => expect(page.totalElements).toBe(0));

    api.get.and.returnValue(of([]));
    consume([
      service.getAuditFacets(), service.getAuditSummary(), service.getAuditLogs(),
      service.getNotificationChannels(), service.createNotificationChannel({ nombre: 'Correo' }),
      service.updateNotificationChannel('1', { activo: true }), service.toggleNotificationChannel('1'),
      service.deleteNotificationChannel('1'), service.getNotificationEvents(),
      service.createNotificationEvent({ codigo: 'ACTA', nombre: 'Acta' }),
      service.updateNotificationEvent('1', { activo: true }), service.toggleNotificationEvent('1'),
      service.deleteNotificationEvent('1'), service.getNotificationSettings('1'),
      service.createNotificationSetting('1', { eventoId: 1, canal: 'EMAIL' }),
      service.updateNotificationSetting('1', { prioridad: 'ALTA' }), service.deleteNotificationSetting('1'),
      service.getNotificationRecipients('1'), service.createNotificationRecipient('1', { rolId: 2 }),
      service.toggleNotificationRecipient('1'), service.deleteNotificationRecipient('1'),
      service.getNotificationGlobalSettings(), service.updateNotificationGlobalSettings({ activo: 'true' }),
      service.getNotificationHistory(), service.getNotificationStats(), service.getNotificationTemplates(),
      service.getNotificationTemplate('1'),
      service.createNotificationTemplate({ nombre: 'Acta', tipo: 'EMAIL', asunto: 'Acta', cuerpo: 'Lista' }),
      service.updateNotificationTemplate('1', { activo: true }), service.deleteNotificationTemplate('1'),
      service.getBackups(), service.createBackup(), service.getBackupOperationalStatus(), service.testBackupRestore('1'),
      service.getUbicaciones(), service.createUbicacion({ nombre: 'Sede', direccion: '', latitud: 8, longitud: -79, radio: 100 }),
      service.updateUbicacion('1', { radio: 50 }), service.deleteUbicacion('1'),
      service.getIpRestrictions('WHITELIST'),
      service.getAllowedOrigins(),
      service.refreshSecurityCache(), service.getMyProfile(), service.updateMyProfile({ telefono: '6000-0000' }),
      service.changeMyPassword('Anterior1!', 'NuevaClave1!'), service.completeRequiredPassword('NuevaClave1!'),
      service.getPasswordResetRequests(), service.approvePasswordResetRequest(1), service.rejectPasswordResetRequest(1, 'Inválida'),
      service.getMyPasswordPolicy(), service.getMyPreferences(), service.saveMyPreferences({ theme: 'dark' }),
      service.resetMyPreferences(),
    ]);

    expect(service.getQuickAccessCards().length).toBeGreaterThan(5);
    expect(api.post).toHaveBeenCalledWith('/api/backups/1/restore-test', {});
  });
});
