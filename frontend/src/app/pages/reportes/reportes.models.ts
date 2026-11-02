export type ReporteModulo = 'Reuniones' | 'Actas' | 'Asistencia' | 'Actividades' | 'Inventario' | 'Tareas' | 'Documentos' | 'Usuarios';
export type ReporteTipo = 'analitico' | 'ejecutivo' | 'detallado';
export type ReporteFormato = 'PDF' | 'Excel' | 'Word' | 'CSV';

export interface ReporteItem {
  id: string;
  nombre: string;
  modulo: ReporteModulo;
  tipo: ReporteTipo;
  fecha: string;
  generadoPor: string;
  formato: ReporteFormato;
  descripcion: string;
  archivosRuta?: string;
  archivosNombres?: string[];
  generadoDesdePlantilla?: boolean;
}

export interface ReportePlantilla {
  modulo: Extract<ReporteModulo, 'Reuniones' | 'Actas' | 'Asistencia' | 'Tareas'>;
  nombre: string;
  descripcion: string;
}


