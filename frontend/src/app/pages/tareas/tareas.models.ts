export type TareaEstado = 'por_hacer' | 'en_progreso' | 'completada' | 'vencida';
export type TareaPrioridad = 'alta' | 'media' | 'baja';
export type TareaTipo = string;

export interface TareaMovimiento {
  id: string;
  usuario: string;
  fecha: string;
  accion: string;
  detalle: string;
}

export interface TareaChecklistItem {
  id: string;
  texto: string;
  completado: boolean;
}

export interface TareaAdjunto {
  id?: string;
  nombre: string;
  tamano: string;
  disponible?: boolean;
  tipoContenido?: string;
}

export interface Tarea {
  id: string;
  titulo: string;
  descripcion: string;
  proyecto: string;
  codigo: string;
  tipo: TareaTipo;
  asignado: string;
  asignadoUsuarioId: number | null;
  participanteUsuarioIds: number[];
  participantes: string[];
  cargo: string;
  unidades: string;
  prioridad: TareaPrioridad;
  fechaLimite: string;
  horaLimite: string;
  fechaCreacion: string;
  estado: TareaEstado;
  creadoPor: string;
  progreso: number;
  checklist: TareaChecklistItem[];
  adjuntos: TareaAdjunto[];
}

/** Respuesta del backend, antes de normalizar identificadores y valores opcionales. */
export type TareaRespuesta = Omit<Partial<Tarea>, 'id' | 'asignadoUsuarioId' | 'participanteUsuarioIds'> & {
  id: string | number;
  titulo: string;
  asignadoUsuarioId?: string | number | null;
  participanteUsuarioIds?: (string | number)[];
};

export type TareaFormularioAdjunto = TareaAdjunto & { archivo?: File };
export interface TareaGuardarRequest {
  titulo: string;
  descripcion: string;
  tipo: TareaTipo;
  proyecto: string;
  asignado: string;
  asignadoUsuarioId: number | null;
  participanteUsuarioIds: number[];
  prioridad: TareaPrioridad;
  fechaCreacion: string;
  fechaLimite: string;
  checklist: Pick<TareaChecklistItem, 'texto' | 'completado'>[];
  unidades: string;
}
