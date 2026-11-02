import { Injectable, inject, signal } from '@angular/core';
import { Observable, defer, of, throwError } from 'rxjs';
import { map, catchError, tap } from 'rxjs/operators';
import { ApiService } from '../../../services/api.service';
import {
  ConfigUser, ConfigRole, ConfigPermission, ConfigModule,
  SecurityPolicy, AuditLog, NotificationTemplate,
  BackupRecord, BackupOperationalStatus, OrgUnit, QuickAccessCard, Ubicacion,
  Unidad, UnidadMembership,
  IpRestriction, AllowedOrigin,
} from '../models/config.models';
import { INSTITUTIONAL_BLUE } from '../../../shared/ui-colors';

interface BackendUser {
  id: number; nombre: string; apellido: string; username: string; correo: string;
  activo: boolean; bloqueado: boolean; institucion?: string; cargo?: string;
  creadoEn: string; ultimoAcceso: string | null; roles: string[];
  membresias: {
    unidadId: number | null; unidadNombre: string | null;
    rolId: number; rolNombre: string;
  }[];
}
interface BackendRole {
  id: number; nombre: string; descripcion: string; activo: boolean; sistema?: boolean; permisos: { id: number; modulo: string; accion: string; recurso: string; descripcion: string; activo: boolean }[]; usuarios: number;
}
interface BackendPermission {
  id: number; modulo: string; accion: string; recurso: string; descripcion: string; activo: boolean;
}
interface BackendUnidad {
  id: number; nombre: string; descripcion: string; activo: boolean;
}

export interface AuditPage {
  content: AuditLog[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface AuditQuery {
  page: number;
  size: number;
  q?: string;
  accion?: string;
  entidad?: string;
  desde?: string;
  hasta?: string;
}
interface BackendModule {
  id: number; nombre: string; ruta: string; icono: string; activo: boolean; orden: number;
  codigo?: string; descripcion?: string; categoria?: string; color?: string;
  padreId?: number; tipoModulo?: string; etiqueta?: string;
  visibleEnMenu?: boolean; visibleEnDashboard?: boolean;
  createdAt?: string; updatedAt?: string;
}
export interface NotificationChannel {
  id: string; nombre: string; descripcion: string; icono: string; activo: boolean;
  createdAt?: string; updatedAt?: string;
}

export interface NotificationEventItem {
  id: string; codigo: string; nombre: string; descripcion: string; categoria: string; activo: boolean;
  settings?: any[]; recipients?: any[];
  createdAt?: string; updatedAt?: string;
}

export interface NotificationSettingItem {
  id: string; eventoId?: string; canal: string; activo: boolean;
  prioridad: string; frecuencia: string;
  horarioInicio: string | null; horarioFin: string | null;
  recordatorioActivo: boolean; recordatorioHoras: number;
  vencimientoHoras: number;
  mensajePersonalizado: string;
  asuntoPlantilla: string; cuerpoPlantilla: string;
  creadoPor?: string; modificadoPor?: string;
  createdAt?: string; updatedAt?: string;
}

export interface NotificationSettingPayload {
  eventoId: number; canal: string; activo?: boolean;
  prioridad?: string; frecuencia?: string;
  horarioInicio?: string; horarioFin?: string;
  recordatorioActivo?: boolean; recordatorioHoras?: number;
  vencimientoHoras?: number;
  mensajePersonalizado?: string;
  plantillaId?: number;
}

export interface NotificationRecipientItem {
  id: string; eventoId?: string;
  eventoCodigo?: string; eventoNombre?: string;
  rolId?: number; rolNombre?: string;
  activo: boolean; createdAt?: string;
}

export interface GlobalSettingItem {
  clave: string; valor: string; descripcion: string; updatedAt?: string;
}

export interface NotificationHistoryItem {
  id: string; eventoCodigo: string; canal: string; destinatario: string;
  asunto: string; mensaje: string; estado: string; enviadoEn: string;
  errorDetalle?: string; createdAt?: string;
}

export interface NotificationStats {
  totalEnviados: number; totalPendientes: number; totalErrores: number;
  enviadosHoy: number; enviadosSemana: number;
  porCorreo: number; porPush: number; porSMS: number; porSistema: number;
}

@Injectable({ providedIn: 'root' })
export class ConfigDataService {
  private readonly api = inject(ApiService);
  readonly consultasConError = signal<string[]>([]);

  limpiarErroresConsulta(): void { this.consultasConError.set([]); }

  /** Los valores de respaldo de las lecturas no deben ocultar fallos de consulta. */
  private consultar<T>(url: string): Observable<T> {
    return defer(() => {
      // Conservar el aviso durante un reintento; solo una respuesta correcta lo limpia.
      return this.api.get<T>(url).pipe(tap({
        next: () => this.consultasConError.update(errores => errores.filter(item => item !== url)),
        error: () => this.consultasConError.update(errores => [...new Set([...errores, url])]),
      }));
    });
  }

  private toConfigUser(b: BackendUser): ConfigUser {
    const estado = b.bloqueado ? 'bloqueado' : (b.activo ? 'activo' : 'inactivo');
    const unidades: UnidadMembership[] = (b.membresias || []).map(m => ({
      unidadId: m.unidadId != null ? String(m.unidadId) : null,
      unidadNombre: m.unidadNombre,
      rolId: String(m.rolId),
      rolNombre: m.rolNombre,
    }));
    let rol = b.roles?.[0] ?? '';
    if (!rol && unidades.length > 0) {
      const tieneJefe = unidades.some(m => m.rolNombre === 'Jefe de Unidad');
      rol = tieneJefe ? 'Jefe de Unidad' : 'Miembro';
    }
    return {
      id: String(b.id), nombre: b.nombre, apellido: b.apellido, username: b.username, correo: b.correo, institucion: b.institucion || '', cargo: b.cargo || '',
      estado,
      rol,
      roles: b.roles || [],
      unidades,
      ultimoAcceso: b.ultimoAcceso ? new Date(b.ultimoAcceso) : undefined,
      creado: new Date(b.creadoEn),
    };
  }

  private toBackendUnidad(b: BackendUnidad): Unidad {
    return { id: String(b.id), nombre: b.nombre, descripcion: b.descripcion, activo: b.activo };
  }

  private toConfigRole(b: BackendRole): ConfigRole {
    return {
      id: String(b.id), nombre: b.nombre, descripcion: b.descripcion || '',
      permisos: b.permisos?.length || 0,
      permisoIds: (b.permisos || []).map(p => p.id),
      usuarios: b.usuarios || 0, estado: b.activo, sistema: b.sistema ?? false,
      fechaCreacion: new Date(),
    };
  }

  private toConfigPermission(b: BackendPermission): ConfigPermission {
    return {
      id: String(b.id), modulo: b.modulo, accion: b.accion,
      recurso: b.recurso, descripcion: b.descripcion || '', estado: b.activo,
    };
  }

  private toConfigModule(b: BackendModule): ConfigModule {
    return {
      id: String(b.id), nombre: b.nombre, ruta: b.ruta,
      icono: b.icono || '', estado: b.activo, orden: b.orden,
      codigo: b.codigo || '', descripcion: b.descripcion || '',
      categoria: b.categoria || 'principal', color: b.color || '',
      padreId: b.padreId != null ? String(b.padreId) : undefined,
      tipoModulo: b.tipoModulo || 'MODULO',
      etiqueta: b.etiqueta || '',
      visibleEnMenu: b.visibleEnMenu ?? true,
      visibleEnDashboard: b.visibleEnDashboard ?? true,
      createdAt: b.createdAt || undefined,
    };
  }

  /* ---- Dashboard ---- */
  getQuickAccessCards(): QuickAccessCard[] {
    return [
      { titulo: 'Usuarios', descripcion: 'Gestiona cuentas y asignaciones.', icono: 'people-outline', ruta: '/config/users', color: INSTITUTIONAL_BLUE },
      { titulo: 'Roles', descripcion: 'Define los roles institucionales.', icono: 'shield-outline', ruta: '/config/roles', color: '#7c3aed' },
      { titulo: 'Módulos', descripcion: 'Administra módulos y visibilidad.', icono: 'grid-outline', ruta: '/config/modules', color: '#0f766e' },
      { titulo: 'Seguridad', descripcion: 'Configura políticas y restricciones.', icono: 'lock-closed-outline', ruta: '/config/security', color: '#dc2626' },
      { titulo: 'Auditoría', descripcion: 'Consulta la actividad registrada.', icono: 'document-text-outline', ruta: '/config/audit', color: '#4f46e5' },
      { titulo: 'Notificaciones', descripcion: 'Configura canales y eventos.', icono: 'notifications-outline', ruta: '/config/notifications', color: INSTITUTIONAL_BLUE },
      { titulo: 'Respaldos', descripcion: 'Revisa y administra respaldos.', icono: 'server-outline', ruta: '/config/backups', color: '#059669' },
      { titulo: 'Organización', descripcion: 'Administra unidades y estructura.', icono: 'git-merge-outline', ruta: '/config/organization', color: INSTITUTIONAL_BLUE },
    ];
  }

  deleteUser(id: string): Observable<void> {
    return this.api.delete<void>(`/api/usuarios/${id}`);
  }

  deleteUserPermanently(id: string): Observable<void> {
    return this.api.delete<void>(`/api/usuarios/${id}/permanente`);
  }

  /* ---- Users ---- */
  getUsers(): Observable<ConfigUser[]> {
    return this.consultar<BackendUser[]>('/api/usuarios').pipe(
      map(list => list.map(b => this.toConfigUser(b))),
      catchError(() => of([])),
    );
  }

  getUserHistory(userId: string): Observable<{ id: number; usuario: string; accion: string; entidad: string; detalle: string; entidadId: number | null; direccionIp: string | null; fecha: string }[]> {
    return this.consultar<any[]>(`/api/usuarios/${userId}/historial`).pipe(
      catchError(() => of([])),
    );
  }

  createUser(data: {
    nombre: string; apellido: string; username: string; correo: string; password: string;
    activo?: boolean; institucion: string; cargo: string;
    rolIds: number[]; membresias: { unidadId: number | null; rolId: number }[];
  }): Observable<ConfigUser> {
    return this.api.post<BackendUser>('/api/usuarios', data).pipe(
      map(b => this.toConfigUser(b)),
      catchError(err => {
        console.error('[ConfigDataService] createUser error:', err);
        return throwError(() => err);
      }),
    );
  }

  updateUser(id: string, data: {
    nombre: string; apellido: string; username: string; correo: string;
    activo?: boolean; bloqueado?: boolean; institucion: string; cargo: string;
    rolIds: number[]; membresias?: { unidadId: number | null; rolId: number }[];
  }): Observable<ConfigUser> {
    return this.api.put<BackendUser>(`/api/usuarios/${id}`, data).pipe(
      map(b => this.toConfigUser(b)),
      catchError(err => {
        console.error('[ConfigDataService] updateUser via HttpClient error:', err);
        return throwError(() => err);
      }),
    );
  }

  toggleBlockUser(id: string): Observable<ConfigUser> {
    return this.api.patch<BackendUser>(`/api/usuarios/${id}/toggle-block`, {}).pipe(
      map(b => this.toConfigUser(b)),
    );
  }

  /* ---- Unidades ---- */
  createUnidad(data: { nombre: string; descripcion?: string }): Observable<Unidad> {
    return this.api.post<BackendUnidad>('/api/unidades', data).pipe(
      map(b => this.toBackendUnidad(b)),
    );
  }

  updateUnidad(id: string, data: { nombre?: string; descripcion?: string; activo?: boolean }): Observable<Unidad> {
    return this.api.put<BackendUnidad>(`/api/unidades/${id}`, data).pipe(
      map(b => this.toBackendUnidad(b)),
    );
  }

  deleteUnidad(id: string): Observable<void> {
    return this.api.delete<void>(`/api/unidades/${id}`);
  }

  getUnidades(): Observable<Unidad[]> {
    return this.consultar<BackendUnidad[]>('/api/unidades').pipe(
      map(list => list.map(b => this.toBackendUnidad(b))),
      catchError(() => of([])),
    );
  }

  /* ---- Roles ---- */
  getRoles(): Observable<ConfigRole[]> {
    return this.consultar<BackendRole[]>('/api/roles').pipe(
      map(list => list.map(b => this.toConfigRole(b))),
      catchError(() => of([])),
    );
  }

  getRolesStrict(): Observable<ConfigRole[]> {
    return this.consultar<BackendRole[]>('/api/roles').pipe(
      map(list => list.map(role => this.toConfigRole(role))),
    );
  }

  getRole(id: string): Observable<ConfigRole> {
    return this.consultar<BackendRole>(`/api/roles/${id}`).pipe(
      map(role => this.toConfigRole(role)),
    );
  }

  createRole(data: { nombre: string; descripcion: string; activo?: boolean }): Observable<ConfigRole> {
    return this.api.post<BackendRole>('/api/roles', data).pipe(
      map(b => this.toConfigRole(b)),
      catchError(err => {
        console.error('[ConfigDataService] createRole error:', err);
        return throwError(() => err);
      }),
    );
  }

  updateRole(id: string, data: { nombre: string; descripcion: string; activo?: boolean; permisoIds?: number[] }): Observable<ConfigRole> {
    return this.api.put<BackendRole>(`/api/roles/${id}`, data).pipe(
      map(b => this.toConfigRole(b)),
      catchError(err => {
        console.error('[ConfigDataService] updateRole error:', err);
        return throwError(() => err);
      }),
    );
  }

  deleteRole(id: string): Observable<void> {
    return this.api.delete<void>(`/api/roles/${id}`).pipe(
      catchError(err => {
        console.error('[ConfigDataService] deleteRole error:', err);
        return throwError(() => err);
      }),
    );
  }

  copyRolePermissions(fromRoleId: string, toRoleId: string): Observable<void> {
    return this.api.post<void>(`/api/roles/${fromRoleId}/copy-permissions/${toRoleId}`, {});
  }

  resetRolePermissions(roleId: string): Observable<void> {
    return this.api.post<void>(`/api/roles/${roleId}/reset-permissions`, {});
  }

  getRoleUsers(roleId: string): Observable<ConfigUser[]> {
    return this.consultar<BackendUser[]>(`/api/roles/${roleId}/usuarios`).pipe(
      map(list => list.map(b => this.toConfigUser(b))),
    );
  }

  updateRolePermissions(id: string, permisoIds: number[]): Observable<ConfigRole> {
    return this.api.put<BackendRole>(`/api/roles/${id}/permissions`, { permisoIds }).pipe(
      map(role => this.toConfigRole(role)),
    );
  }

  /* ---- Permissions ---- */
  getPermissions(): Observable<ConfigPermission[]> {
    return this.consultar<BackendPermission[]>('/api/permisos').pipe(
      map(list => list.map(b => this.toConfigPermission(b))),
      catchError(() => of([])),
    );
  }

  getPermissionModules(): Observable<string[]> {
    return this.consultar<string[]>('/api/permisos/modules').pipe(
      catchError(() => of([])),
    );
  }

  getPermissionActions(): Observable<string[]> {
    return this.consultar<string[]>('/api/permisos/actions').pipe(
      catchError(() => of([])),
    );
  }

  /* ---- Modules ---- */
  getModules(): Observable<ConfigModule[]> {
    return this.consultar<BackendModule[]>('/api/modulos').pipe(
      map(list => list.map(b => this.toConfigModule(b))),
    );
  }

  getPermissionsStrict(): Observable<ConfigPermission[]> {
    return this.consultar<BackendPermission[]>('/api/permisos').pipe(
      map(list => list.map(permission => this.toConfigPermission(permission))),
    );
  }

  createModule(data: { nombre: string; ruta: string; icono?: string; codigo?: string; descripcion?: string; categoria?: string; color?: string; activo?: boolean; orden?: number }): Observable<ConfigModule> {
    return this.api.post<BackendModule>('/api/modulos', data).pipe(
      map(b => this.toConfigModule(b)),
      catchError(err => {
        console.error('[ConfigDataService] createModule error:', err);
        return throwError(() => err);
      }),
    );
  }

  updateModule(id: string, data: { nombre?: string; ruta?: string; icono?: string; activo?: boolean; orden?: number; codigo?: string; descripcion?: string; categoria?: string; color?: string; etiqueta?: string; visibleEnMenu?: boolean; visibleEnDashboard?: boolean }): Observable<ConfigModule> {
    return this.api.put<BackendModule>(`/api/modulos/${id}`, data).pipe(
      map(b => this.toConfigModule(b)),
      catchError(err => {
        console.error('[ConfigDataService] updateModule error:', err);
        return throwError(() => err);
      }),
    );
  }

  deleteModule(id: string): Observable<void> {
    return this.api.delete<void>(`/api/modulos/${id}`);
  }

  toggleModule(id: string): Observable<ConfigModule> {
    return this.api.patch<BackendModule>(`/api/modulos/${id}/toggle`, {}).pipe(
      map(b => this.toConfigModule(b)),
    );
  }

  reorderModules(items: { id: string; orden: number }[]): Observable<ConfigModule[]> {
    const body = items.map(i => ({ id: Number(i.id), orden: i.orden }));
    return this.api.put<BackendModule[]>('/api/modulos/reorder', body).pipe(
      map(list => list.map(b => this.toConfigModule(b))),
      catchError(err => {
        console.error('[ConfigDataService] reorderModules error:', err);
        return throwError(() => err);
      }),
    );
  }

  /* ---- Security ---- */
  updateSecurityPolicy(id: string, valor: string): Observable<SecurityPolicy> {
    return this.api.put<{ id: number; nombre: string; valor: string; descripcion: string; categoria: string; administrable: boolean; maximoServidorMb?: number | null }>(`/api/security/policies/${id}`, { valor }).pipe(
      map(b => ({
        id: String(b.id), nombre: b.nombre, descripcion: b.descripcion, categoria: b.categoria,
        administrable: b.administrable === true, maximoServidorMb: b.maximoServidorMb,
        valor: !isNaN(Number(b.valor)) ? Number(b.valor) : (b.valor === 'true' ? true : (b.valor === 'false' ? false : b.valor)),
      } as SecurityPolicy)),
    );
  }

  getSecurityPolicies(): Observable<SecurityPolicy[]> {
    interface BackendPolicy {
      id: number; nombre: string; valor: string; descripcion: string; categoria: string;
      administrable: boolean; maximoServidorMb?: number | null;
    }
    return this.consultar<BackendPolicy[]>('/api/security/policies').pipe(
      map(list => list.map(b => ({
        id: String(b.id), nombre: b.nombre, descripcion: b.descripcion, categoria: b.categoria,
        administrable: b.administrable === true, maximoServidorMb: b.maximoServidorMb,
        valor: !isNaN(Number(b.valor)) ? Number(b.valor) : (b.valor === 'true' ? true : (b.valor === 'false' ? false : b.valor)),
      } as SecurityPolicy))),
      catchError(() => of([])),
    );
  }

  /* ---- Audit ---- */
  getAuditPage(query: AuditQuery): Observable<AuditPage> {
    interface BackendAuditLog {
      id: number; usuario: string; accion: string; entidad: string;
      detalle: string; direccionIp: string; fecha: string;
    }
    interface BackendPage {
      content: BackendAuditLog[]; page: number; size: number; totalElements: number;
      totalPages: number; first: boolean; last: boolean;
    }
    const params = new URLSearchParams({ page: String(query.page), size: String(query.size) });
    if (query.q) params.set('q', query.q);
    if (query.accion) params.set('accion', query.accion);
    if (query.entidad) params.set('entidad', query.entidad);
    if (query.desde) params.set('desde', query.desde);
    if (query.hasta) params.set('hasta', query.hasta);
    return this.consultar<BackendPage>(`/api/audit-logs/page?${params.toString()}`).pipe(
      map(result => ({
        ...result,
        content: result.content.map(b => ({
          id: String(b.id), usuario: b.usuario, accion: b.accion, entidad: b.entidad,
          detalle: b.detalle, direccionIp: b.direccionIp, fecha: new Date(b.fecha),
        })),
      })),
    );
  }

  saveModuleConfiguration(items: { id: string; orden: number; activo: boolean }[]): Observable<ConfigModule[]> {
    const body = items.map(item => ({ id: Number(item.id), orden: item.orden, activo: item.activo }));
    return this.api.put<BackendModule[]>('/api/modulos/configuracion', body).pipe(
      map(list => list.map(module => this.toConfigModule(module))),
    );
  }

  getAuditFacets(): Observable<{ acciones: string[]; entidades: string[] }> {
    return this.consultar<{ acciones: string[]; entidades: string[] }>('/api/audit-logs/facets').pipe(
      catchError(() => of({ acciones: [], entidades: [] })),
    );
  }

  getAuditSummary(): Observable<{ total: number; hoy: number; criticos: number; usuarios: number }> {
    return this.consultar<{ total: number; hoy: number; criticos: number; usuarios: number }>('/api/audit-logs/summary').pipe(
      catchError(() => of({ total: 0, hoy: 0, criticos: 0, usuarios: 0 })),
    );
  }

  /** @deprecated Use getAuditPage so large histories remain bounded. */
  getAuditLogs(): Observable<AuditLog[]> {
    interface BackendAuditLog {
      id: number; usuario: string; accion: string; entidad: string;
      detalle: string; direccionIp: string; fecha: string;
    }
    return this.consultar<BackendAuditLog[]>('/api/audit-logs').pipe(
      map(list => list.map(b => ({
        id: String(b.id), usuario: b.usuario, accion: b.accion, entidad: b.entidad,
        detalle: b.detalle, direccionIp: b.direccionIp, fecha: new Date(b.fecha),
      }))),
      catchError(() => of([])),
    );
  }

  /* ---- Notifications — Channels ---- */
  getNotificationChannels(): Observable<NotificationChannel[]> {
    return this.consultar<NotificationChannel[]>('/api/notificaciones/canales').pipe(
      catchError(() => of([])),
    );
  }

  createNotificationChannel(data: { nombre: string; descripcion?: string; icono?: string }): Observable<NotificationChannel> {
    return this.api.post<NotificationChannel>('/api/notificaciones/canales', data);
  }

  updateNotificationChannel(id: string, data: { nombre?: string; descripcion?: string; icono?: string; activo?: boolean }): Observable<NotificationChannel> {
    return this.api.put<NotificationChannel>(`/api/notificaciones/canales/${id}`, data);
  }

  toggleNotificationChannel(id: string): Observable<NotificationChannel> {
    return this.api.patch<NotificationChannel>(`/api/notificaciones/canales/${id}/toggle`, {});
  }

  deleteNotificationChannel(id: string): Observable<void> {
    return this.api.delete<void>(`/api/notificaciones/canales/${id}`);
  }

  /* ---- Notifications — Events ---- */
  getNotificationEvents(): Observable<NotificationEventItem[]> {
    return this.consultar<NotificationEventItem[]>('/api/notificaciones/eventos').pipe(
      catchError(() => of([])),
    );
  }

  createNotificationEvent(data: { codigo: string; nombre: string; descripcion?: string; categoria?: string }): Observable<NotificationEventItem> {
    return this.api.post<NotificationEventItem>('/api/notificaciones/eventos', data);
  }

  updateNotificationEvent(id: string, data: { codigo?: string; nombre?: string; descripcion?: string; categoria?: string; activo?: boolean }): Observable<NotificationEventItem> {
    return this.api.put<NotificationEventItem>(`/api/notificaciones/eventos/${id}`, data);
  }

  toggleNotificationEvent(id: string): Observable<NotificationEventItem> {
    return this.api.patch<NotificationEventItem>(`/api/notificaciones/eventos/${id}/toggle`, {});
  }

  deleteNotificationEvent(id: string): Observable<void> {
    return this.api.delete<void>(`/api/notificaciones/eventos/${id}`);
  }

  /* ---- Notifications — Settings (per event) ---- */
  getNotificationSettings(eventoId: string): Observable<NotificationSettingItem[]> {
    return this.consultar<NotificationSettingItem[]>(`/api/notificaciones/eventos/${eventoId}/settings`).pipe(
      catchError(() => of([])),
    );
  }

  createNotificationSetting(eventoId: string, data: NotificationSettingPayload): Observable<NotificationSettingItem> {
    return this.api.post<NotificationSettingItem>(`/api/notificaciones/eventos/${eventoId}/settings`, data);
  }

  updateNotificationSetting(settingId: string, data: Partial<NotificationSettingPayload>): Observable<NotificationSettingItem> {
    return this.api.put<NotificationSettingItem>(`/api/notificaciones/eventos/settings/${settingId}`, data);
  }

  deleteNotificationSetting(settingId: string): Observable<void> {
    return this.api.delete<void>(`/api/notificaciones/eventos/settings/${settingId}`);
  }

  /* ---- Notifications — Recipients (per event) ---- */
  getNotificationRecipients(eventoId: string): Observable<NotificationRecipientItem[]> {
    return this.consultar<NotificationRecipientItem[]>(`/api/notificaciones/eventos/${eventoId}/recipients`).pipe(
      catchError(() => of([])),
    );
  }

  createNotificationRecipient(eventoId: string, data: { rolId: number }): Observable<NotificationRecipientItem> {
    return this.api.post<NotificationRecipientItem>(`/api/notificaciones/eventos/${eventoId}/recipients`, data);
  }

  toggleNotificationRecipient(recipientId: string): Observable<NotificationRecipientItem> {
    return this.api.patch<NotificationRecipientItem>(`/api/notificaciones/eventos/recipients/${recipientId}/toggle`, {});
  }

  deleteNotificationRecipient(recipientId: string): Observable<void> {
    return this.api.delete<void>(`/api/notificaciones/eventos/recipients/${recipientId}`);
  }

  /* ---- Notifications — Global Settings ---- */
  getNotificationGlobalSettings(): Observable<GlobalSettingItem[]> {
    return this.consultar<GlobalSettingItem[]>('/api/notificaciones/configuracion/global').pipe(
      catchError(() => of([])),
    );
  }

  updateNotificationGlobalSettings(settings: { [clave: string]: string }): Observable<GlobalSettingItem[]> {
    return this.api.put<GlobalSettingItem[]>('/api/notificaciones/configuracion/global', settings);
  }

  /* ---- Notifications — History ---- */
  getNotificationHistory(): Observable<NotificationHistoryItem[]> {
    return this.consultar<NotificationHistoryItem[]>('/api/notificaciones/configuracion/historial').pipe(
      catchError(() => of([])),
    );
  }

  /* ---- Notifications — Stats ---- */
  getNotificationStats(): Observable<NotificationStats> {
    return this.consultar<NotificationStats>('/api/notificaciones/configuracion/estadisticas').pipe(
      catchError(() => of({ totalEnviados: 0, totalPendientes: 0, totalErrores: 0, enviadosHoy: 0, enviadosSemana: 0, porCorreo: 0, porPush: 0, porSMS: 0, porSistema: 0 })),
    );
  }

  /* ---- Notifications — Templates (full CRUD) ---- */
  getNotificationTemplates(): Observable<NotificationTemplate[]> {
    interface BackendTemplate {
      id: number; nombre: string; tipo: string; asunto: string; cuerpo: string; activo: boolean;
    }
    return this.consultar<BackendTemplate[]>('/api/notificaciones').pipe(
      map(list => list.map(b => ({
        id: String(b.id), nombre: b.nombre, tipo: b.tipo as NotificationTemplate['tipo'],
        asunto: b.asunto || '', cuerpo: b.cuerpo || '', estado: b.activo,
      }))),
      catchError(() => of([])),
    );
  }

  getNotificationTemplate(id: string): Observable<NotificationTemplate> {
    return this.consultar<{ id: number; nombre: string; tipo: string; asunto: string; cuerpo: string; activo: boolean }>(`/api/notificaciones/${id}`).pipe(
      map(b => ({
        id: String(b.id), nombre: b.nombre, tipo: b.tipo as NotificationTemplate['tipo'],
        asunto: b.asunto || '', cuerpo: b.cuerpo || '', estado: b.activo,
      })),
    );
  }

  createNotificationTemplate(data: { nombre: string; tipo: string; asunto: string; cuerpo: string; activo?: boolean }): Observable<NotificationTemplate> {
    return this.api.post<{ id: number; nombre: string; tipo: string; asunto: string; cuerpo: string; activo: boolean }>('/api/notificaciones', data).pipe(
      map(b => ({
        id: String(b.id), nombre: b.nombre, tipo: b.tipo as NotificationTemplate['tipo'],
        asunto: b.asunto || '', cuerpo: b.cuerpo || '', estado: b.activo,
      })),
    );
  }

  updateNotificationTemplate(id: string, data: { nombre?: string; tipo?: string; asunto?: string; cuerpo?: string; activo?: boolean }): Observable<NotificationTemplate> {
    return this.api.put<{ id: number; nombre: string; tipo: string; asunto: string; cuerpo: string; activo: boolean }>(`/api/notificaciones/${id}`, data).pipe(
      map(b => ({
        id: String(b.id), nombre: b.nombre, tipo: b.tipo as NotificationTemplate['tipo'],
        asunto: b.asunto || '', cuerpo: b.cuerpo || '', estado: b.activo,
      })),
    );
  }

  deleteNotificationTemplate(id: string): Observable<void> {
    return this.api.delete<void>(`/api/notificaciones/${id}`);
  }

  /* ---- Backups ---- */
  getBackups(): Observable<BackupRecord[]> {
    interface BackendBackup {
      id: number; nombre: string; tamanio: string;
      estado: string; tipo: string; fecha: string;
      copiaExterna: boolean; checksumSha256?: string;
      verificacionEstado: string; verificadoAt?: string;
    }
    return this.consultar<BackendBackup[]>('/api/backups').pipe(
      map(list => list.map(b => ({
        id: String(b.id), nombre: b.nombre, tamanio: b.tamanio,
        estado: b.estado as BackupRecord['estado'], fecha: new Date(b.fecha),
        tipo: b.tipo as BackupRecord['tipo'],
        copiaExterna: Boolean(b.copiaExterna), checksumSha256: b.checksumSha256 || undefined,
        verificacionEstado: (b.verificacionEstado || 'pendiente') as BackupRecord['verificacionEstado'],
        verificadoAt: b.verificadoAt ? new Date(b.verificadoAt) : undefined,
      }))),
    );
  }

  createBackup(): Observable<BackupRecord> {
    return this.api.post<{
      id: number; nombre: string; tamanio: string; estado: string; tipo: string; fecha: string;
      copiaExterna: boolean; checksumSha256?: string; verificacionEstado: string; verificadoAt?: string;
    }>(
      '/api/backups', {}
    ).pipe(map(b => ({
      id: String(b.id), nombre: b.nombre, tamanio: b.tamanio,
      estado: b.estado as BackupRecord['estado'], fecha: new Date(b.fecha), tipo: b.tipo as BackupRecord['tipo'],
      copiaExterna: Boolean(b.copiaExterna), checksumSha256: b.checksumSha256 || undefined,
      verificacionEstado: (b.verificacionEstado || 'pendiente') as BackupRecord['verificacionEstado'],
      verificadoAt: b.verificadoAt ? new Date(b.verificadoAt) : undefined,
    })));
  }

  getBackupOperationalStatus(): Observable<BackupOperationalStatus> {
    return this.consultar<any>('/api/backups/status').pipe(map(status => ({
      ...status,
      ultimoRespaldoExitoso: status.ultimoRespaldoExitoso ? new Date(status.ultimoRespaldoExitoso) : undefined,
      ultimaRestauracionVerificada: status.ultimaRestauracionVerificada ? new Date(status.ultimaRestauracionVerificada) : undefined,
    })));
  }

  testBackupRestore(id: string): Observable<{ ok: boolean; backupId: number; verifiedAt: string }> {
    return this.api.post<{ ok: boolean; backupId: number; verifiedAt: string }>(`/api/backups/${id}/restore-test`, {});
  }

  /* ---- Organization ---- */
  getOrganization(): Observable<OrgUnit[]> {
    interface BackendOrgUnit {
      id: number; nombre: string; siglas: string; nivel: number;
      padreId?: number; responsable: string; activo: boolean;
    }
    return this.consultar<BackendOrgUnit[]>('/api/organizacion').pipe(
      map(list => this.buildOrgTree(list.filter(unit => unit.activo))),
      catchError(() => of([])),
    );
  }

  private buildOrgTree(flat: { id: number; nombre: string; siglas: string; nivel: number; padreId?: number; responsable: string }[]): OrgUnit[] {
    const map = new Map<number, OrgUnit>();
    const roots: OrgUnit[] = [];
    for (const item of flat) {
      map.set(item.id, { id: String(item.id), nombre: item.nombre, siglas: item.siglas || '', nivel: item.nivel, responsable: item.responsable || '', children: [] });
    }
    for (const item of flat) {
      const node = map.get(item.id)!;
      if (item.padreId && map.has(item.padreId)) {
        const parent = map.get(item.padreId)!;
        if (!parent.children) parent.children = [];
        parent.children.push(node);
      } else {
        roots.push(node);
      }
    }
    return roots;
  }

  /* ---- Ubicaciones ---- */
  getUbicaciones(): Observable<Ubicacion[]> {
    interface BackendUbicacion {
      id: number; nombre: string; direccion: string; latitud: number; longitud: number; radio: number;
    }
    return this.consultar<BackendUbicacion[]>('/api/ubicaciones').pipe(
      map(list => list.map(b => ({
        id: String(b.id), nombre: b.nombre, direccion: b.direccion || '',
        latitud: b.latitud, longitud: b.longitud, radio: b.radio,
      }))),
      catchError(() => of([])),
    );
  }

  createUbicacion(data: { nombre: string; direccion: string; latitud: number; longitud: number; radio: number }): Observable<Ubicacion> {
    return this.api.post<{ id: number; nombre: string; direccion: string; latitud: number; longitud: number; radio: number }>('/api/ubicaciones', data).pipe(
      map(b => ({ id: String(b.id), nombre: b.nombre, direccion: b.direccion || '', latitud: b.latitud, longitud: b.longitud, radio: b.radio })),
    );
  }

  updateUbicacion(id: string, data: { nombre?: string; direccion?: string; latitud?: number; longitud?: number; radio?: number }): Observable<Ubicacion> {
    return this.api.put<{ id: number; nombre: string; direccion: string; latitud: number; longitud: number; radio: number }>(`/api/ubicaciones/${id}`, data).pipe(
      map(b => ({ id: String(b.id), nombre: b.nombre, direccion: b.direccion || '', latitud: b.latitud, longitud: b.longitud, radio: b.radio })),
    );
  }

  deleteUbicacion(id: string): Observable<void> {
    return this.api.delete<void>(`/api/ubicaciones/${id}`);
  }

  /* ---- Dashboard Stats ---- */
  getDashboardStats(): Observable<{ usuarios: number; roles: number; ultimoRespaldo: string; alertas: number; estado: string }> {
    return this.consultar<{ usuarios: number; roles: number; ultimoRespaldo: string; alertas: number; estado: string }>('/api/dashboard/stats');
  }

  /* ---- Security — IP Restrictions ---- */
  getIpRestrictions(tipo?: string): Observable<IpRestriction[]> {
    const params = tipo ? `?tipo=${tipo}` : '';
    return this.consultar<IpRestriction[]>(`/api/security/ip-restrictions${params}`).pipe(
      catchError(() => of([])),
    );
  }

  /* ---- Security — Allowed Origins ---- */
  getAllowedOrigins(): Observable<AllowedOrigin[]> {
    return this.consultar<AllowedOrigin[]>('/api/security/allowed-origins').pipe(
      catchError(() => of([])),
    );
  }

  /* ---- Security — Cache ---- */
  refreshSecurityCache(): Observable<{ message: string }> {
    return this.api.post<{ message: string }>('/api/security/policies/refresh-cache', {});
  }

  /* ---- Profile (self-service) ---- */
  getMyProfile(): Observable<any> {
    return this.consultar<any>('/api/auth/me/profile');
  }

  updateMyProfile(data: {
    nombre?: string;
    segundoNombre?: string;
    apellido?: string;
    telefono?: string;
    celular?: string;
    correoAlterno?: string;
    documentoIdentidad?: string;
    tipoDocumento?: string;
    cargo?: string;
    institucion?: string;
    departamento?: string;
    direccion?: string;
    unidadAdministrativa?: string;
    codigoInterno?: string;
    observaciones?: string;
    fotografiaRuta?: string;
  }): Observable<any> {
    return this.api.put<any>('/api/auth/me/profile', data);
  }

  changeMyPassword(currentPassword: string, newPassword: string): Observable<any> {
    return this.api.patch<any>('/api/auth/me/password', { currentPassword, newPassword });
  }

  completeRequiredPassword(newPassword: string): Observable<any> {
    return this.api.patch<any>('/api/auth/me/required-password', { newPassword });
  }

  getPasswordResetRequests(): Observable<any[]> { return this.consultar<any[]>('/api/admin/password-reset-requests'); }
  approvePasswordResetRequest(id: number): Observable<any> { return this.api.post<any>(`/api/admin/password-reset-requests/${id}/approve`, {}); }
  rejectPasswordResetRequest(id: number, reason: string): Observable<any> { return this.api.post<any>(`/api/admin/password-reset-requests/${id}/reject`, { reason }); }

  getMyPasswordPolicy(): Observable<{
    minLength: number;
    maxLength: number;
    requiresUppercase: boolean;
    requiresLowercase: boolean;
    requiresDigits: boolean;
    requiresSpecialChars: boolean;
    historySize: number;
  }> {
    return this.api.get('/api/auth/me/password-policy');
  }

  /* ---- Preferences (self-service) ---- */
  getMyPreferences(): Observable<any> {
    return this.consultar<any>('/api/config/preferences/me');
  }

  saveMyPreferences(data: any): Observable<any> {
    return this.api.put<any>('/api/config/preferences/me', data);
  }

  resetMyPreferences(): Observable<any> {
    return this.api.post<any>('/api/config/preferences/me/reset', {});
  }
}
