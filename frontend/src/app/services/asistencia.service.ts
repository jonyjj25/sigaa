import { Injectable, inject } from '@angular/core';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { ApiService } from './api.service';
import { RegistroAsistencia } from '../models/asistencia.models';

@Injectable({ providedIn: 'root' })
export class AsistenciaService {
  private readonly api = inject(ApiService);

  getHistorial(): Observable<RegistroAsistencia[]> {
    return this.api.get<any[]>('/api/asistencia/historial').pipe(
      map(list => list.map(r => this.mapRegistro(r))),
      catchError(() => of([])),
    );
  }

  registrarInvitado(data: {
    codigoSesion: string; nombre: string; correo: string;
    telefono: string; institucion: string; cargo: string;
  }): Observable<{ success: boolean; message: string }> {
    return this.api.post<any>('/api/asistencia/invitado', data).pipe(
      map(r => ({ success: r.success, message: r.message })),
      catchError((e) => {
        console.error('[asistencia.service] registrarInvitado error:', e?.status, e?.error, e);
        return of({ success: false, message: e?.error?.error || 'Error al registrar asistencia' });
      }),
    );
  }

  escanearQr(uid: number, codigo: string): Observable<{ success: boolean; message: string }> {
    return this.api.post<any>('/api/asistencia/escanear', { uid: uid.toString(), codigo }).pipe(
      map(r => ({ success: r.success, message: r.message })),
      catchError((e) => {
        console.error('[asistencia.service] escanearQr error:', e?.status, e?.error, e);
        return of({ success: false, message: e?.error?.error || 'Error al registrar asistencia' });
      }),
    );
  }

  getSesionPorCodigo(codigo: string): Observable<any | null> {
    return this.api.get<any>(`/api/asistencia/sesiones/${codigo}`).pipe(
      catchError((err) => {
        console.error('[getSesionPorCodigo] Error:', err.status, err.message, codigo);
        return of(null);
      }),
    );
  }

  getAsistentesPorSesion(codigo: string): Observable<any> {
    return this.api.get<any>(`/api/asistencia/sesiones/${codigo}/asistentes`);
  }

  private mapRegistro(r: any): RegistroAsistencia {
    return {
      id: r.id,
      usuarioId: r.usuarioId,
      usuarioNombre: r.usuarioNombre,
      fecha: r.fecha,
      horaEntrada: r.horaEntrada,
      horaSalida: r.horaSalida,
      latEntrada: r.latEntrada,
      lngEntrada: r.lngEntrada,
      latSalida: r.latSalida,
      lngSalida: r.lngSalida,
      sede: r.sede ? { id: r.sede.id?.toString() || '', nombre: r.sede.nombre, direccion: '', lat: r.sede.lat, lng: r.sede.lng, radio: r.sede.radio } : undefined,
      metodo: r.metodo,
      estado: r.estado,
    };
  }
}
