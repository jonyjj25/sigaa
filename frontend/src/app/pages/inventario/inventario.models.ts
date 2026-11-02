export type InventarioEstado = 'en buen estado' | 'mantenimiento' | 'fuera de servicio' | 'dañado';
export type InventarioAsignacion = 'SIN_ASIGNAR' | 'PERSONA' | 'UNIDAD';
export interface InventarioAsignables {
  personas: { id: number; nombre: string }[];
  unidades: { id: number; nombre: string }[];
}

export interface InventarioItem {
  id: string;
  codigo: string;
  nombre: string;
  categoria: string;
  ubicacion: string;
  unidad?: string;
  lockVersion: number;
  tipoAsignacion: InventarioAsignacion | 'PENDIENTE_REVISION';
  asignadoUsuarioId: number | null;
  asignadoUnidadId: number | null;
  estado: InventarioEstado;
  fechaAdquisicion: string;
  marcaModelo?: string;
  serie?: string;
  serieInstitucional?: string;
  responsable?: string;
  proveedor?: string;
  descripcion?: string;
}

export interface AuditLogEntry {
  id: string;
  usuario: string;
  accion: string;
  entidad: string;
  detalle: string;
  entidadId: string;
  direccionIp: string;
  fecha: Date;
}

export interface InventarioActivoRequest {
  nombre: string;
  categoria: string;
  ubicacion: string;
  tipoAsignacion: InventarioAsignacion;
  asignadoUsuarioId: number | null;
  asignadoUnidadId: number | null;
  estado: InventarioEstado;
  fechaAdquisicion: string | null;
  marcaModelo: string;
  serie: string;
  serieInstitucional: string;
  proveedor: string;
  descripcion: string;
}
