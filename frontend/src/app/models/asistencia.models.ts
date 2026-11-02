export interface Sede {
  id: string;
  nombre: string;
  direccion: string;
  lat: number;
  lng: number;
  radio: number;
}

export interface RegistroAsistencia {
  id: number;
  usuarioId: number;
  usuarioNombre: string;
  fecha: string;
  horaEntrada: string;
  horaSalida?: string;
  latEntrada?: number;
  lngEntrada?: number;
  latSalida?: number;
  lngSalida?: number;
  sede?: Sede;
  metodo: string;
  estado: string;
  creadoEn?: string;
}
