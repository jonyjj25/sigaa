import { Tarea, TareaRespuesta, TareaGuardarRequest, TareaFormularioAdjunto } from './tareas.models';

/** Normalización compartida por la carga inicial y las respuestas de las operaciones. */
export function mapTareaRespuesta(t: TareaRespuesta): Tarea {
  return {
    id: String(t.id), titulo: t.titulo, descripcion: t.descripcion || '',
    proyecto: t.proyecto || t.tipo || '', codigo: t.codigo || '', tipo: t.tipo || '',
    asignado: t.asignado || '',
    asignadoUsuarioId: t.asignadoUsuarioId == null ? null : Number(t.asignadoUsuarioId),
    participanteUsuarioIds: (t.participanteUsuarioIds || []).map(id => Number(id)),
    participantes: t.participantes || [], cargo: t.cargo || '', unidades: t.unidades || '',
    prioridad: t.prioridad || 'media', fechaLimite: t.fechaLimite ? t.fechaLimite.slice(0, 10) : '',
    horaLimite: t.horaLimite || '', fechaCreacion: t.fechaCreacion ? t.fechaCreacion.slice(0, 10) : '',
    estado: t.estado || 'por_hacer', creadoPor: t.creadoPor || 'Administrador', progreso: t.progreso ?? 0,
    checklist: t.checklist || [], adjuntos: t.adjuntos || [],
  };
}

/** Mantiene el contrato multipart y las referencias a archivos previamente guardados. */
export function prepararTareaMultipart(body: TareaGuardarRequest, adjuntos: TareaFormularioAdjunto[]): {
  data: FormData; totalBytes: number;
} {
  const data = new FormData();
  let fileIndex = 0;
  let totalBytes = 0;
  const referencias = adjuntos.map(adjunto => {
    if (adjunto.archivo) {
      data.append('archivos', adjunto.archivo, adjunto.archivo.name);
      totalBytes += adjunto.archivo.size;
      return { archivoIndice: fileIndex++ };
    }
    return { id: adjunto.id, nombre: adjunto.nombre, tamano: adjunto.tamano };
  });
  data.append('datos', new Blob([JSON.stringify({ ...body, adjuntos: referencias })], { type: 'application/json' }));
  return { data, totalBytes };
}
