export type ReunionEstado = 'programada' | 'en_curso' | 'realizada' | 'cancelada';
export type ReunionModalidad = 'Presencial' | 'Virtual' | 'Mixta';

export interface Reunion {
  id: string;
  codigo?: string;
  fecha: string;
  fechaISO: string;
  horaInicio: string;
  horaFin: string;
  titulo: string;
  subtitulo?: string;
  descripcion: string;
  objetivo?: string;
  tipoReunion?: string;
  area: string;
  organizador: string;
  organizadorUsuarioId?: number;
  lugar: string;
  modalidad: ReunionModalidad;
  enlaceVirtual?: string;
  sala?: string;
  unidadIds?: number[];
  unidades?: { id: number; nombre: string }[];
  unidadResponsable?: string;
  organizacion?: string;
  estado: ReunionEstado;
  participantes: number;
  participantesNombres?: string[];
  participanteUsuarioIds?: number[];
  puedeIniciar?: boolean;
  puedeFinalizar?: boolean;
  puedeCancelar?: boolean;
  puedeEditar?: boolean;
  puedeEliminar?: boolean;
  observaciones?: string;
  createdAt?: string;
  updatedAt?: string;
}
