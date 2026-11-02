import { DestroyRef, computed, signal } from '@angular/core';
import { finalize, Subscription } from 'rxjs';
import { ApiService, mensajeErrorApi } from '../../services/api.service';
import { Nota, NotaDepartamento, NotaInstitucion } from './notas.models';

export interface NotaDepartamentoOpcion extends NotaDepartamento {
  director: string;
  directorCargo: string;
  activo: boolean;
  guardado?: boolean;
}

type DepartamentoRespuesta = Omit<NotaDepartamentoOpcion, 'id'> & { id: string | number };
type DestinatarioGuardado = Pick<Nota, 'institucion' | 'departamento' | 'destinatarioNombre' | 'destinatarioCargo' | 'destino'>;

/** Estado del destinatario para ambos formularios; no conoce usuarios ni seguimiento. */
export class NotaDestinatarioForm {
  readonly institucionId = signal('');
  readonly departamentoId = signal('');
  readonly nombre = signal('');
  readonly cargo = signal('');
  readonly destino = signal('');
  readonly institucionOriginal = signal<NotaInstitucion | null>(null);
  readonly departamentoOriginal = signal<NotaDepartamento | null>(null);
  readonly departamentos = signal<NotaDepartamentoOpcion[]>([]);
  readonly cargando = signal(false);
  readonly error = signal('');
  readonly opcionesDepartamentos = computed(() => {
    const original = this.departamentoOriginal();
    const actuales = this.departamentos();
    if (!original) return actuales;
    const existente = actuales.find(item => item.id === original.id);
    const conservado: NotaDepartamentoOpcion = {
      ...existente, ...original, director: existente?.director || '', directorCargo: existente?.directorCargo || '',
      activo: existente?.activo ?? false, guardado: true,
    };
    return [conservado, ...actuales.filter(item => item.id !== original.id)];
  });
  private readonly cache = new Map<string, NotaDepartamentoOpcion[]>();
  private request?: Subscription;

  constructor(private readonly api: ApiService, destroyRef: DestroyRef) {
    destroyRef.onDestroy(() => this.request?.unsubscribe());
  }

  inicializar(nota?: DestinatarioGuardado): void {
    this.request?.unsubscribe();
    this.cache.clear();
    this.institucionId.set(nota?.institucion?.id || '');
    this.institucionOriginal.set(nota?.institucion || null);
    this.departamentoId.set(nota?.departamento?.id || '');
    this.departamentoOriginal.set(nota?.departamento || null);
    this.nombre.set(nota?.destinatarioNombre || '');
    this.cargo.set(nota?.destinatarioCargo || '');
    this.destino.set(nota?.destino || '');
    this.departamentos.set([]);
    this.error.set('');
    this.cargarDepartamentos();
  }

  cambiarInstitucion(id: string): void {
    if (id === this.institucionId()) return;
    this.request?.unsubscribe();
    this.institucionId.set(id);
    this.institucionOriginal.set(null);
    this.departamentoOriginal.set(null);
    this.departamentoId.set('');
    this.nombre.set('');
    this.cargo.set('');
    this.destino.set('');
    this.departamentos.set([]);
    this.error.set('');
    this.cargarDepartamentos();
  }

  cambiarDepartamento(id: string): void {
    if (id === this.departamentoId()) return;
    const departamento = this.departamentos().find(item => item.id === id && item.activo);
    this.departamentoOriginal.set(null);
    this.departamentoId.set(departamento?.id || '');
    this.nombre.set(departamento?.director || '');
    this.cargo.set(departamento?.directorCargo || '');
  }

  reintentar(): void { this.cargarDepartamentos(); }

  private cargarDepartamentos(): void {
    this.request?.unsubscribe();
    const id = this.institucionId();
    this.cargando.set(false);
    if (!id) return;
    const guardados = this.cache.get(id);
    if (guardados) { this.departamentos.set(guardados); return; }
    this.cargando.set(true);
    this.error.set('');
    this.request = this.api.get<DepartamentoRespuesta[]>(`/api/notas/instituciones/${encodeURIComponent(id)}/departamentos`)
      .pipe(finalize(() => this.cargando.set(false))).subscribe({
        next: items => {
          const departamentos = items.filter(item => item.activo).map(item => ({
            id: String(item.id), nombre: item.nombre, director: item.director || '',
            directorCargo: item.directorCargo || '', activo: true,
          }));
          this.cache.set(id, departamentos);
          this.departamentos.set(departamentos);
          // Cargar opciones nunca reemplaza el destinatario ni el cargo de una nota guardada.
        },
        error: error => this.error.set(mensajeErrorApi(error, 'No se pudieron cargar los departamentos. Intenta nuevamente.')),
      });
  }
}
