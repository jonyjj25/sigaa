export type MemoEstado = 'EN_SEGUIMIENTO' | 'COMPLETADO' | 'ANULADO';

export interface MemoUsuario { id: string; nombre: string; }
export interface MemoHistorial {
  id: string; usuario: MemoUsuario | null; accion: string;
  estadoAnterior: MemoEstado | null; estadoNuevo: MemoEstado | null;
  detalle: string; createdAt: string;
}
export interface Memo {
  id: string; numeroId: string; numero: string; asunto: string; destino: string;
  descripcion: string; creadoPor: MemoUsuario; seguimientoUsuario: MemoUsuario;
  estado: MemoEstado; completadoPor: MemoUsuario | null; completadoAt: string | null;
  anuladoPor: MemoUsuario | null; anuladoAt: string | null; anulacionMotivo: string | null;
  createdAt: string; updatedAt: string; historial: MemoHistorial[];
}
export interface MemoNumero {
  id: string; numero: string; estado: 'DISPONIBLE' | 'RESERVADO' | 'OCUPADO';
  memoId: string | null; reservadoPor: MemoUsuario | null;
  reservadoDesde: string | null; reservadoHasta: string | null; reservaPropia: boolean;
}
export interface MemoUsuarioSeleccionable { id: string; nombre: string; }
