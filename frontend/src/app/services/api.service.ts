import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, Subject, catchError, of, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthService } from './auth.service';

/** Campos mínimos que utilizan los selectores de usuarios, sin datos de perfil. */
export interface UsuarioSeleccionableRespuesta {
  membresias?: { unidadId: string | number; unidadNombre: string }[];
  id: string | number;
  nombre: string;
  segundoNombre?: string | null;
  apellido?: string | null;
  correo?: string;
  cargo?: string | null;
  activo?: boolean;
  bloqueado?: boolean;
}

export function mensajeErrorApi(error: unknown, fallback: string): string {
  if (!error || typeof error !== 'object' || !('error' in error)) return fallback;
  const payload: unknown = error.error;
  if (!payload || typeof payload !== 'object') return fallback;
  if ('message' in payload && typeof payload.message === 'string' && payload.message) return payload.message;
  if ('error' in payload && typeof payload.error === 'string' && payload.error) return payload.error;
  return fallback;
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly apiUrl = environment.apiUrl;

  readonly qrBaseUrl = signal(window.location.origin);
  private readonly changesSubject = new Subject<void>();
  readonly dataChanged$ = this.changesSubject.asObservable();

  constructor() {
    this.loadConfig();
  }

  private loadConfig(): void {
    this.http.get<{ qrBaseUrl: string }>(`${this.apiUrl}/api/config`).pipe(
      catchError(() => of({ qrBaseUrl: window.location.origin })),
    ).subscribe(config => {
      if (config?.qrBaseUrl) {
        this.qrBaseUrl.set(config.qrBaseUrl);
      }
    });
  }

  private headers(contentType?: string): HttpHeaders {
    const token = this.auth.getToken();
    let headers = new HttpHeaders();
    if (contentType) {
      headers = headers.set('Content-Type', contentType);
    }
    if (token) {
      headers = headers.set('Authorization', `Bearer ${token}`);
    }
    return headers;
  }

  get<T>(path: string): Observable<T> {
    return this.http.get<T>(`${this.apiUrl}${path}`, { headers: this.headers('application/json'), withCredentials: true });
  }

  post<T>(path: string, body: unknown, options?: { notifyChanges?: boolean }): Observable<T> {
    const isMultipart = typeof FormData !== 'undefined' && body instanceof FormData;
    return this.http.post<T>(`${this.apiUrl}${path}`, body, {
      headers: this.headers(isMultipart ? undefined : 'application/json'),
      withCredentials: true,
    }).pipe(tap(() => { if (options?.notifyChanges !== false) this.changesSubject.next(); }));
  }

  put<T>(path: string, body: unknown, options?: { notifyChanges?: boolean }): Observable<T> {
    const isMultipart = typeof FormData !== 'undefined' && body instanceof FormData;
    return this.http.put<T>(`${this.apiUrl}${path}`, body, {
      headers: this.headers(isMultipart ? undefined : 'application/json'),
      withCredentials: true,
    }).pipe(tap(() => { if (options?.notifyChanges !== false) this.changesSubject.next(); }));
  }

  patch<T>(path: string, body: unknown): Observable<T> {
    return this.http.patch<T>(`${this.apiUrl}${path}`, body, { headers: this.headers('application/json'), withCredentials: true }).pipe(tap(() => this.changesSubject.next()));
  }

  delete<T>(path: string): Observable<T> {
    return this.http.delete<T>(`${this.apiUrl}${path}`, { headers: this.headers('application/json'), withCredentials: true }).pipe(tap(() => this.changesSubject.next()));
  }

  getBlob(path: string): Observable<Blob> {
    return this.http.get(`${this.apiUrl}${path}`, {
      headers: this.headers(),
      responseType: 'blob',
      withCredentials: true,
    });
  }
}
