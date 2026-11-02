export interface Acta {
  id: string;
  codigo: string;
  fecha: string;
  hora: string;
  lugar: string;
  titulo: string;
  tipo: 'Ordinaria' | 'Extraordinaria' | 'Comité' | 'Consejo';
  estado: 'Firmada' | 'Pendiente Firma' | 'Borrador' | 'Terminada';
  responsable: string;
  objetivos: string;
  institucionParticipantes: string;
  temas: string[];
  acuerdos: string[];
  participantes: ActaParticipante[];
  acuerdosRel: ActaAcuerdo[];
  adjuntos: ActaAdjunto[];
  firmas: ActaFirma[];
  historial: HistorialEntry[];
  sesion?: ActaSesion | null;
}

export interface ActaSesion {
  id: string;
  codigo: string;
  nombre: string;
  fecha: string;
  tipo: string;
  activo: boolean;
}

export interface HistorialEntry {
  id: string;
  usuario: string;
  accion: string;
  detalle: string;
  fecha: string;
}

export interface ActaParticipante {
  id: string;
  nombre: string;
  apellido: string;
  area: string;
  asistio: boolean;
  tipo?: string;
  correo?: string;
  institucion?: string;
  cargo?: string;
}

export interface ActaAcuerdo {
  id: string;
  descripcion: string;
  responsable: string;
  fechaLimite: string;
  estado: 'Pendiente' | 'En Progreso' | 'Completado';
}

export interface ActaAdjunto {
  id: string;
  nombreOriginal: string;
  nombreArchivo: string;
  tipoMime: string;
  tamano: number;
}

export interface ActaFirma {
  id: string;
  nombre: string;
  rol: string;
  fecha: string;
}
