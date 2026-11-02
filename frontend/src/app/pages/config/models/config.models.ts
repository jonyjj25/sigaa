export interface ConfigUser {
  id: string;
  nombre: string;
  apellido: string;
  username: string;
  correo: string;
  institucion?: string;
  cargo?: string;
  estado: 'activo' | 'inactivo' | 'bloqueado';
  rol: string;
  roles?: string[];
  unidades: UnidadMembership[];
  ultimoAcceso?: Date;
  creado: Date;
}

export interface UnidadMembership {
  unidadId: string | null;
  unidadNombre: string | null;
  rolId: string;
  rolNombre: string;
}

export interface Unidad {
  id: string;
  nombre: string;
  descripcion: string;
  activo: boolean;
}

export interface ConfigRole {
  id: string;
  nombre: string;
  descripcion: string;
  permisos: number;
  permisoIds?: number[];
  usuarios: number;
  estado: boolean;
  sistema: boolean;
  fechaCreacion: Date;
}

export interface ConfigPermission {
  id: string;
  modulo: string;
  accion: string;
  recurso: string;
  descripcion: string;
  estado: boolean;
}

export interface ConfigModule {
  id: string;
  nombre: string;
  ruta: string;
  icono: string;
  estado: boolean;
  orden: number;
  codigo?: string;
  descripcion?: string;
  categoria?: string;
  color?: string;
  padreId?: string;
  tipoModulo?: string;
  etiqueta?: string;
  visibleEnMenu?: boolean;
  visibleEnDashboard?: boolean;
  createdAt?: string;
}

export interface SecurityPolicy {
  id: string;
  nombre: string;
  valor: string | number | boolean;
  descripcion: string;
  categoria: string;
  administrable?: boolean;
  maximoServidorMb?: number | null;
}

export interface AuditLog {
  id: string;
  usuario: string;
  accion: string;
  entidad: string;
  detalle: string;
  direccionIp: string;
  fecha: Date;
}

export interface NotificationTemplate {
  id: string;
  nombre: string;
  tipo: 'email' | 'sms' | 'push';
  asunto: string;
  cuerpo: string;
  estado: boolean;
}

export interface BackupRecord {
  id: string;
  nombre: string;
  tamanio: string;
  estado: 'completado' | 'en_progreso' | 'fallido';
  fecha: Date;
  tipo: 'manual' | 'automatico';
  copiaExterna: boolean;
  checksumSha256?: string;
  verificacionEstado: 'pendiente' | 'verificado' | 'fallido';
  verificadoAt?: Date;
}

export interface BackupOperationalStatus {
  listoParaRespaldoManual: boolean;
  cifradoConfigurado: boolean;
  pgDumpDisponible: boolean;
  pgRestoreDisponible: boolean;
  copiaExternaConfigurada: boolean;
  respaldoAutomaticoActivo: boolean;
  pruebaRestauracionActiva: boolean;
  respaldoEnProgreso: boolean;
  retencionDias: number;
  retencionCopias: number;
  horarioRespaldo: string;
  horarioPruebaRestauracion: string;
  ultimoRespaldoExitoso?: Date;
  ultimaRestauracionVerificada?: Date;
  totalRespaldos: number;
}

export interface OrgUnit {
  id: string;
  nombre: string;
  siglas: string;
  nivel: number;
  padreId?: string;
  responsable?: string;
  children?: OrgUnit[];
}

export interface Ubicacion {
  id: string;
  nombre: string;
  direccion: string;
  latitud: number;
  longitud: number;
  radio: number;
}

export interface QuickAccessCard {
  titulo: string;
  descripcion: string;
  icono: string;
  ruta: string;
  color: string;
  stats?: { label: string; valor: string | number }[];
}

export interface IpRestriction {
  id: string;
  tipo: 'WHITELIST' | 'BLACKLIST';
  direccionIp: string;
  rangoInicio?: string;
  rangoFin?: string;
  descripcion?: string;
  activo: boolean;
}

export interface AllowedOrigin {
  id: string;
  origen: string;
  descripcion?: string;
  activo: boolean;
}
