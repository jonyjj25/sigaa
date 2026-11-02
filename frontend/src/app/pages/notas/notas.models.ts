export type NotaEstado = 'BORRADOR' | 'EN_SEGUIMIENTO' | 'FIRMADA' | 'CERRADA' | 'ANULADA';

export interface NotaUsuario {
  id: string;
  nombre: string;
}

export interface NotaInstitucion {
  id: string;
  nombre: string;
  siglas: string;
}

export interface NotaDepartamento {
  id: string;
  nombre: string;
}

export interface NotaFirmante extends NotaUsuario {
  cargo: string;
  institucion: string;
}

export interface NotaHistorial {
  id: string;
  usuario: NotaUsuario | null;
  accion: string;
  accionTexto?: string;
  estadoAnterior: NotaEstado | null;
  estadoNuevo: NotaEstado | null;
  detalle: string;
  createdAt: string;
}

export interface NotaSeguimiento {
  estado: string | null;
  estadoTexto: string;
  usuario: NotaUsuario | null;
  createdAt: string | null;
  puedeComentar: boolean;
  opciones: { estado: string; texto: string }[];
}

export interface NotaPdf {
  id: string;
  numeroVersion: number;
  nombreOriginal: string;
  tipoMime: string;
  tamanoBytes: number;
  usuario: NotaUsuario | null;
  createdAt: string;
  vigente: boolean;
}

export type NotaRelacionTipo = 'RELACIONADA_CON' | 'RESPONDE_A' | 'ANTECEDENTE_DE';

export interface NotaBusqueda {
  id: string;
  numero: string;
  asunto: string;
  estado: NotaEstado;
  sensitiva: boolean;
  creadoPor: NotaUsuario | null;
  seguimientoUsuario: NotaUsuario | null;
}

export interface NotaRelacion {
  id: string;
  tipo: NotaRelacionTipo;
  direccion: 'SALIENTE' | 'ENTRANTE';
  nota: NotaBusqueda;
  creadoPor: NotaUsuario | null;
  createdAt: string;
}

export interface Nota {
  id: string;
  numeroId: string;
  numero: string;
  documentoNombre?: string;
  asunto: string;
  destino: string;
  fechaOficial: string;
  fechaOficialTexto: string;
  destinatarioNombre: string;
  destinatarioCargo: string;
  institucion: NotaInstitucion | null;
  departamento?: NotaDepartamento | null;
  contenido: string;
  descripcion: string;
  sensitiva: boolean;
  creadoPor: NotaUsuario;
  seguimientoUsuario: NotaUsuario;
  estado: NotaEstado;
  tienePdf: boolean;
  puedeAdjuntarPdf?: boolean;
  pdfNombre: string | null;
  pdfTamanoBytes: number | null;
  firmadaPor: NotaUsuario | null;
  firmadaAt: string | null;
  cerradaPor: NotaUsuario | null;
  cerradaAt: string | null;
  oficializadaPor: NotaUsuario | null;
  oficializadaAt: string | null;
  firmante: NotaFirmante | null;
  firmanteNombre: string | null;
  firmanteCargo: string | null;
  firmanteInstitucion: string | null;
  anuladaPor: NotaUsuario | null;
  anuladaAt: string | null;
  anulacionMotivo: string | null;
  createdAt: string;
  updatedAt: string;
  historial: NotaHistorial[];
  seguimiento?: NotaSeguimiento;
  pdfs: NotaPdf[];
  relaciones: NotaRelacion[];
}

export interface NotaNumero {
  id: string;
  numero: string;
  estado: 'DISPONIBLE' | 'RESERVADO' | 'OCUPADO';
  notaId: string | null;
  tomadoPor: NotaUsuario | null;
  tomadoAt: string | null;
  reservadoPor: NotaUsuario | null;
  reservadoDesde: string | null;
  reservadoHasta: string | null;
  reservaPropia: boolean;
}

export interface UsuarioSeleccionable {
  id: string;
  nombre: string;
}

export type NotaUsuarioRespuesta = Omit<NotaUsuario, 'id'> & { id: string | number };
export type NotaInstitucionRespuesta = Omit<NotaInstitucion, 'id'> & { id: string | number };
export type NotaDepartamentoRespuesta = Omit<NotaDepartamento, 'id'> & { id: string | number };
export type NotaFirmanteRespuesta = Omit<NotaFirmante, 'id'> & { id: string | number };
export type NotaBusquedaRespuesta = Omit<Partial<NotaBusqueda>, 'id' | 'creadoPor' | 'seguimientoUsuario'> & {
  id: string | number;
  creadoPor?: NotaUsuarioRespuesta | null;
  seguimientoUsuario?: NotaUsuarioRespuesta | null;
};
export type NotaRespuesta = Omit<Partial<Nota>,
  'id' | 'numeroId' | 'institucion' | 'departamento' | 'creadoPor' | 'seguimientoUsuario' | 'firmadaPor' | 'cerradaPor' |
  'oficializadaPor' | 'firmante' | 'anuladaPor' | 'historial' | 'seguimiento' | 'pdfs' | 'relaciones'> & {
  id: string | number;
  numeroId: string | number;
  institucion?: NotaInstitucionRespuesta | null;
  departamento?: NotaDepartamentoRespuesta | null;
  creadoPor: NotaUsuarioRespuesta;
  seguimientoUsuario: NotaUsuarioRespuesta;
  firmadaPor?: NotaUsuarioRespuesta | null;
  cerradaPor?: NotaUsuarioRespuesta | null;
  oficializadaPor?: NotaUsuarioRespuesta | null;
  anuladaPor?: NotaUsuarioRespuesta | null;
  firmante?: NotaFirmanteRespuesta | null;
  historial?: (Omit<NotaHistorial, 'id' | 'usuario'> & { id: string | number; usuario: NotaUsuarioRespuesta | null })[];
  seguimiento?: Omit<NotaSeguimiento, 'usuario'> & { usuario: NotaUsuarioRespuesta | null };
  pdfs?: (Omit<NotaPdf, 'id' | 'usuario'> & { id: string | number; usuario: NotaUsuarioRespuesta | null })[];
  relaciones?: (Omit<NotaRelacion, 'id' | 'nota' | 'creadoPor'> & {
    id: string | number; nota: NotaBusquedaRespuesta; creadoPor: NotaUsuarioRespuesta | null;
  })[];
};
export type NotaNumeroRespuesta = Omit<Partial<NotaNumero>, 'id' | 'notaId' | 'tomadoPor' | 'reservadoPor'> & {
  id: string | number;
  notaId?: string | number | null;
  tomadoPor?: NotaUsuarioRespuesta | null;
  reservadoPor?: NotaUsuarioRespuesta | null;
};
export interface NotaContextoBorrador {
  fechaOficialTexto?: string;
  directora?: NotaFirmanteRespuesta | null;
  advertenciaDirectora?: string;
}

export interface NotaListadoRespuesta {
  content: NotaRespuesta[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  resumen: Partial<Record<NotaEstado, number>>;
}

/** El mismo salto explícito que la plantilla convierte en un salto de página Word/PDF. */
export function insertarSaltoPagina(editor: HTMLTextAreaElement): string {
  return editor.value.slice(0, editor.selectionStart) + '\n\f\n' + editor.value.slice(editor.selectionEnd);
}
