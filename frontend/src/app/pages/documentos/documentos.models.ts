export type EstadoDocumento = 'aprobado' | 'en_revision' | 'borrador' | 'rechazado';
export type DocumentoClasificacion = 'PUBLICO' | 'USO_INTERNO' | 'RESTRINGIDO' | 'CONFIDENCIAL' | 'DATOS_SENSIBLES';
export type DocumentoOrigen = 'PROPIO' | 'COMPARTIDO' | 'TAREA' | 'UNIDAD';

export interface DocumentoItem {
  id: string;
  nombre: string;
  categoria: string;
  area: string;
  version: string;
  versionActualId?: number | null;
  fecha: string;
  propietario: string;
  propietarioId?: number | null;
  origen?: DocumentoOrigen;
  clasificacion: DocumentoClasificacion;
  politicaAcceso?: 'PUBLICO' | 'RESTRINGIDO' | 'CONFIDENCIAL';
  estado: EstadoDocumento;
  descripcion: string;
  paginas: number;
  tamano: string;
  compartido: boolean;
  archivoRuta?: string | null;
  descargaUrl?: string | null;
  vistaPreviaUrl?: string | null;
  permiso?: string;
  carpetaId?: string | number | null;
  solicitudAprobacion?: SolicitudAprobacionDocumento | null;
  puedeEditar?: boolean;
  puedeDescargar?: boolean;
  puedeAdministrarAcceso?: boolean;
  puedeEliminar?: boolean;
}

export interface DocumentoCompartido {
  usuarioId: number;
  nombre: string;
  correo: string;
  permiso: 'LECTURA' | 'EDICION';
  expiraEn?: string | null;
}

export interface SolicitudAprobacionDocumento {
  id: number;
  estado: 'PENDIENTE' | 'APROBADA' | 'RECHAZADA' | 'SUSTITUIDA';
  solicitanteId: number;
  solicitante: string;
  aprobadorId: number;
  aprobador: string;
  fecha: string;
  comentario: string;
  versionId?: number | null;
  version?: string | null;
  respondidoPor?: string | null;
  fechaRespuesta?: string | null;
  comentarioRespuesta?: string;
}

export interface DocumentoVersionItem {
  id: number;
  version: string;
  autor: string;
  autorId?: number | null;
  fecha: string | null;
  lineaBase: boolean;
  cambios: string;
  archivoNombre?: string | null;
  tamanoBytes?: number | null;
  actual: boolean;
  disponible: boolean;
  descargaUrl?: string | null;
  aprobaciones: SolicitudAprobacionDocumento[];
}

export interface DocumentoHistorial {
  documento: DocumentoItem;
  versiones: DocumentoVersionItem[];
  registrosAnteriores: DocumentoVersionItem[];
  aprobacionesSinVersion: SolicitudAprobacionDocumento[];
  archivoActualDisponible: boolean;
}

export interface CarpetaItem {
  id: string;
  nombre: string;
  tipo?: 'PERSONAL' | 'COMPARTIDA';
  propietario?: string;
  puedeEliminar?: boolean;
  count: number;
  documentos: DocumentoItem[];
}

export interface DocumentosFiltros {
  search: string;
  tipo: string;
  area: string;
  fecha: string;
  estado: string;
}
