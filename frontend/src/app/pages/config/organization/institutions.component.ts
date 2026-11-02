import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ApiService } from '../../../services/api.service';

interface Institution {
  id: number;
  nombre: string;
  siglas: string | null;
  director: string | null;
  directorCargo: string | null;
  activo: boolean;
}
interface Department {
  id: number;
  nombre: string;
  director: string;
  directorCargo: string;
  activo: boolean;
}

@Component({
  selector: 'app-institutions',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './institutions.component.html',
  styleUrls: ['./institutions.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InstitutionsComponent {
  private readonly api = inject(ApiService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly institutions = signal<Institution[]>([]);
  protected readonly selected = signal<Institution | null>(null);
  protected readonly departments = signal<Department[]>([]);
  protected readonly search = signal('');
  protected readonly loading = signal(false);
  protected readonly loadingDepartments = signal(false);
  protected readonly saving = signal(false);
  protected readonly message = signal('');
  protected readonly error = signal('');
  protected readonly departmentError = signal('');
  protected readonly modal = signal<'institution' | 'department' | null>(null);
  protected readonly editingId = signal<number | null>(null);
  protected readonly formError = signal('');
  protected form = { nombre: '', siglas: '', director: '', directorCargo: '', activo: true };
  protected readonly filtered = computed(() => {
    const query = this.search().trim().toLocaleLowerCase('es');
    return this.institutions().filter(item => !query ||
      [item.nombre, item.siglas, item.director].some(value => value?.toLocaleLowerCase('es').includes(query)));
  });

  constructor() { this.load(); }

  protected load(): void {
    this.loading.set(true);
    this.error.set('');
    this.api.get<Institution[]>('/api/organizations').pipe(
      finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: items => {
        this.institutions.set([...items].sort((a, b) => a.nombre.localeCompare(b.nombre, 'es')));
        const selectedId = this.selected()?.id;
        this.selected.set(items.find(item => item.id === selectedId) || null);
      },
      error: () => this.error.set('No se pudieron cargar las instituciones. Verifica tu acceso e inténtalo nuevamente.'),
    });
  }

  protected select(institution: Institution): void {
    this.selected.set(institution);
    this.departments.set([]);
    this.loadDepartments(institution.id);
  }

  protected loadDepartments(id: number): void {
    this.loadingDepartments.set(true);
    this.departmentError.set('');
    this.api.get<Department[]>(`/api/organizations/${id}/departamentos`)
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: items => {
          if (this.selected()?.id !== id) return;
          this.departments.set(items);
          this.loadingDepartments.set(false);
        },
        error: () => {
          if (this.selected()?.id !== id) return;
          this.loadingDepartments.set(false);
          this.departmentError.set('No se pudieron cargar los departamentos. Inténtalo nuevamente.');
        },
      });
  }

  protected openInstitution(item?: Institution): void {
    this.editingId.set(item?.id ?? null);
    this.form = {
      nombre: item?.nombre || '', siglas: item?.siglas || '', director: item?.director || '',
      directorCargo: item?.directorCargo || '', activo: item?.activo ?? true
    };
    this.formError.set('');
    this.modal.set('institution');
  }

  protected openDepartment(item?: Department): void {
    if (!this.selected()) return;
    this.editingId.set(item?.id ?? null);
    this.form = {
      nombre: item?.nombre || '', siglas: '', director: item?.director || '',
      directorCargo: item?.directorCargo || '', activo: item?.activo ?? true
    };
    this.formError.set('');
    this.modal.set('department');
  }

  protected close(): void { if (!this.saving()) this.modal.set(null); }

  protected save(): void {
    if (this.saving() || !this.form.nombre.trim()) return;
    const type = this.modal();
    const id = this.editingId();
    const institutionId = this.selected()?.id;
    if (!type || (type === 'department' && !institutionId)) return;
    const body = {
      nombre: this.form.nombre.trim(), director: this.form.director.trim(),
      directorCargo: this.form.directorCargo.trim(), activo: this.form.activo,
      ...(type === 'institution' ? { siglas: this.form.siglas.trim(), estado: this.form.activo ? 'ACTIVO' : 'INACTIVO' } : {})
    };
    const path = type === 'institution' ? '/api/organizations' : `/api/organizations/${institutionId}/departamentos`;
    this.saving.set(true);
    this.formError.set('');
    const request = id ? this.api.put<Institution | Department>(`${path}/${id}`, body)
      : this.api.post<Institution | Department>(path, body);
    request.pipe(finalize(() => this.saving.set(false)), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: saved => {
        this.modal.set(null);
        this.message.set(type === 'institution' ? 'Institución guardada correctamente.' : 'Departamento y responsable guardados correctamente.');
        if (type === 'institution') {
          this.selected.set(saved as Institution);
          this.load();
          this.departments.set([]);
          this.loadDepartments(saved.id);
        } else if (institutionId) this.loadDepartments(institutionId);
      },
      error: response => this.formError.set(response?.error?.message || response?.error?.error || 'No se pudo guardar. Revisa los datos e inténtalo nuevamente.'),
    });
  }
}
