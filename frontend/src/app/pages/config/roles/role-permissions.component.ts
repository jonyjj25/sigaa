import { Component, OnInit, ChangeDetectionStrategy, inject, signal, computed, DestroyRef, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { IonIcon, AlertController } from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { saveOutline, arrowBackOutline, checkmarkOutline, closeOutline, shieldOutline } from 'ionicons/icons';
import { ConfigDataService } from '../services/config-data.service';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ConfigRole, ConfigPermission } from '../models/config.models';
import { canonicalRole } from '../../../shared/institutional-roles';
import { forkJoin } from 'rxjs';

@Component({
  selector: 'app-role-permissions',
  standalone: true,
  imports: [CommonModule, IonIcon],
  template: `
    <div class="page-container">
      <div class="page-header">
        <div class="header-left">
          <button type="button" class="btn-back" aria-label="Volver a roles" (click)="goBack()" title="Volver a roles">
            <ion-icon name="arrow-back-outline" aria-hidden="true" />
          </button>
          <div class="header-icon">
            <ion-icon name="shield-outline" />
          </div>
          <div>
            <h1>Permisos: {{ roleName() }}</h1>
            <p class="subtitle">Gestión de permisos asignados al rol</p>
          </div>
        </div>
        <button class="btn-primary" [disabled]="loading() || saving() || !dirty()" (click)="savePermissions()">
          <ion-icon name="save-outline" />
          {{ saving() ? 'Guardando...' : 'Guardar cambios' }}
        </button>
      </div>

      <div *ngIf="role()?.sistema" class="system-notice" role="note">
        <ion-icon name="shield-outline" aria-hidden="true" />
        <span>Rol institucional protegido. Debe conservar permisos operativos y no puede quedar vacío.</span>
      </div>

      <div *ngIf="isLeadershipRole()" class="system-notice" role="note">
        <ion-icon name="shield-outline" aria-hidden="true" />
        <span>Dirección tiene alcance operativo institucional. Subdirección requiere además acceso al recurso. Los permisos de cada cargo se guardan por separado.</span>
      </div>

      <div class="permission-toolbar" *ngIf="!loading() && !loadError()">
        <label for="permission-search">Buscar permisos</label>
        <input id="permission-search" type="search" [value]="searchTerm()"
               (input)="searchTerm.set($any($event.target).value)"
               placeholder="Módulo, acción, recurso o descripción" />
        <span>{{ selectedIds().size }} permisos seleccionados</span>
      </div>

      <div *ngIf="loading()" class="empty-state"><div class="empty-content"><p>Cargando permisos...</p></div></div>
      <div *ngIf="loadError()" class="empty-state error-state" role="alert">
        <div class="empty-content">
          <p>{{ loadError() }}</p>
          <button type="button" class="btn-toggle-all" (click)="loadData()">Reintentar</button>
        </div>
      </div>

      <div class="modules-list" *ngIf="!loading() && !loadError()">
        <div *ngFor="let group of filteredGroups()" class="module-card">
          <div class="module-header">
            <h2>{{ group.module }}</h2>
            <button
              class="btn-toggle-all"
              type="button"
              (click)="toggleAll(group)"
            >
              {{ isAllSelected(group) ? 'Deseleccionar todo' : 'Seleccionar todo' }}
            </button>
          </div>
          <div class="permissions-grid">
            <label
              *ngFor="let perm of group.permissions"
              class="permission-item"
              [class.selected]="selectedIds().has(perm.id)"
            >
              <div class="checkbox-wrapper">
                <input
                  type="checkbox"
                  [checked]="selectedIds().has(perm.id)"
                  (change)="togglePermission(perm.id)"
                  class="checkbox-input"
                />
                <div class="checkbox-custom">
                  <ion-icon *ngIf="selectedIds().has(perm.id)" name="checkmark-outline" />
                </div>
              </div>
              <div class="permission-info">
                <span class="permission-action">{{ perm.accion }}</span>
                <span class="permission-desc">{{ perm.descripcion }}</span>
              </div>
            </label>
          </div>
        </div>
      </div>

      <div *ngIf="!loading() && !loadError() && filteredGroups().length === 0" class="empty-state">
        <div class="empty-content">
          <ion-icon name="shield-outline" class="empty-icon" />
          <p>No hay permisos activos que coincidan con la búsqueda.</p>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .page-container {
      font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
      max-width: 960px;
      margin: 0 auto;
    }

    .page-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 24px;
    }

    .header-left {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .btn-back {
      width: 40px;
      height: 40px;
      border: 1px solid #e2e8f0;
      border-radius: 10px;
      background: #fff;
      color: #64748b;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.2s;
      font-size: 20px;
      flex-shrink: 0;
    }

    .btn-back:hover {
      background: #f8fafc;
      color: #0f172a;
      border-color: #94a3b8;
    }

    .header-icon {
      width: 48px;
      height: 48px;
      background: #f5f3ff;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 24px;
      color: #8b5cf6;
      flex-shrink: 0;
    }

    .page-header h1 {
      font-size: 24px;
      font-weight: 700;
      color: #0f172a;
      margin: 0;
      letter-spacing: -0.3px;
    }

    .subtitle {
      color: #64748b;
      font-size: 14px;
      margin: 4px 0 0 0;
    }

    .btn-primary {
      display: flex;
      align-items: center;
      gap: 8px;
      background: #8b5cf6;
      color: #fff;
      border: none;
      padding: 10px 20px;
      border-radius: 8px;
      font-size: 14px;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.2s;
      font-family: inherit;
    }

    .btn-primary:hover {
      background: #7c3aed;
      transform: translateY(-1px);
      box-shadow: 0 4px 12px rgba(139, 92, 246, 0.35);
    }

    .btn-primary:disabled {
      opacity: .55;
      cursor: not-allowed;
      transform: none;
      box-shadow: none;
    }

    .system-notice {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 18px;
      padding: 12px 14px;
      border: 1px solid var(--border-default);
      border-radius: 10px;
      background: var(--brand-soft);
      color: var(--text-default);
      font-size: 13px;
    }

    .permission-toolbar {
      display: grid;
      grid-template-columns: auto minmax(220px, 1fr) auto;
      align-items: center;
      gap: 12px;
      margin-bottom: 20px;
      padding: 14px 16px;
      border: 1px solid var(--border-default);
      border-radius: 12px;
      background: var(--surface-panel);
      color: var(--text-soft);
      font-size: 13px;
    }

    .permission-toolbar label { color: var(--text-strong); font-weight: 600; }
    .permission-toolbar input {
      min-width: 0;
      padding: 10px 12px;
      border: 1px solid var(--border-default);
      border-radius: 8px;
      background: var(--control-bg);
      color: var(--text-strong);
      font: inherit;
    }

    .permission-toolbar input:focus-visible { outline: 3px solid var(--focus-ring); outline-offset: 1px; }

    .btn-primary ion-icon {
      font-size: 18px;
    }

    .modules-list {
      display: flex;
      flex-direction: column;
      gap: 20px;
    }

    .module-card {
      background: var(--surface-panel);
      border-radius: 12px;
      border: 1px solid var(--border-default);
      overflow: hidden;
      box-shadow: 0 1px 3px rgba(0,0,0,0.04);
    }

    .module-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 16px 20px;
      background: var(--surface-subtle);
      border-bottom: 1px solid var(--border-default);
    }

    .module-header h2 {
      margin: 0;
      font-size: 15px;
      font-weight: 600;
      color: var(--text-strong);
    }

    .btn-toggle-all {
      background: none;
      border: 1px solid #e2e8f0;
      border-radius: 6px;
      padding: 4px 12px;
      font-size: 12px;
      font-weight: 500;
      color: var(--text-soft);
      cursor: pointer;
      transition: all 0.2s;
      font-family: inherit;
    }

    .btn-toggle-all:hover {
      background: #f1f5f9;
      color: #0f172a;
      border-color: #94a3b8;
    }

    .permissions-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
      gap: 8px;
      padding: 16px 20px;
    }

    .permission-item {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 10px 12px;
      border-radius: 8px;
      border: 1px solid var(--border-default);
      cursor: pointer;
      transition: all 0.2s;
      background: var(--surface-subtle);
    }

    .permission-item:hover {
      border-color: #8b5cf6;
      background: #f5f3ff;
    }

    .permission-item.selected {
      border-color: #8b5cf6;
      background: #f5f3ff;
    }

    .checkbox-wrapper {
      position: relative;
      width: 20px;
      height: 20px;
      flex-shrink: 0;
    }

    .checkbox-input {
      position: absolute;
      opacity: 0;
      width: 100%;
      height: 100%;
      cursor: pointer;
      margin: 0;
    }

    .checkbox-custom {
      width: 20px;
      height: 20px;
      border: 2px solid #cbd5e1;
      border-radius: 5px;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.2s;
      pointer-events: none;
    }

    .checkbox-custom ion-icon {
      font-size: 14px;
      color: #fff;
    }

    .checkbox-input:checked + .checkbox-custom {
      background: #8b5cf6;
      border-color: #8b5cf6;
    }

    .checkbox-input:focus + .checkbox-custom {
      box-shadow: 0 0 0 3px rgba(139, 92, 246, 0.2);
    }

    .permission-info {
      display: flex;
      flex-direction: column;
      gap: 2px;
      min-width: 0;
    }

    .permission-action {
      font-size: 13px;
      font-weight: 600;
      color: var(--text-strong);
    }

    .permission-desc {
      font-size: 12px;
      color: var(--text-soft);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .empty-state {
      text-align: center;
      padding: 64px 16px;
    }

    .empty-content {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 12px;
    }

    .empty-icon {
      font-size: 48px;
      color: #cbd5e1;
    }

    .empty-content p {
      color: var(--text-muted-accessible);
      font-size: 15px;
      margin: 0;
    }

    .error-state p { color: var(--app-danger); }

    @media (max-width: 720px) {
      .page-header { align-items: flex-start; gap: 14px; }
      .permission-toolbar { grid-template-columns: 1fr; }
      .permissions-grid { grid-template-columns: 1fr; }
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RolePermissionsComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private dataService = inject(ConfigDataService);
  private destroyRef = inject(DestroyRef);
  private alertCtrl = inject(AlertController);

  protected roleName = signal('');
  protected isLeadershipRole = computed(() => ['Director', 'Subdirector'].includes(canonicalRole(this.roleName())));
  protected role = signal<ConfigRole | null>(null);
  protected selectedIds = signal<Set<string>>(new Set());
  protected groupedPermissions = signal<GroupedPermission[]>([]);
  protected searchTerm = signal('');
  protected loading = signal(true);
  protected saving = signal(false);
  protected loadError = signal('');
  protected dirty = signal(false);
  protected filteredGroups = computed(() => {
    const term = this.searchTerm().trim().toLocaleLowerCase('es');
    if (!term) return this.groupedPermissions();
    return this.groupedPermissions()
      .map(group => ({
        ...group,
        permissions: group.permissions.filter(permission =>
          [group.module, permission.accion, permission.recurso, permission.descripcion]
            .some(value => value.toLocaleLowerCase('es').includes(term))),
      }))
      .filter(group => group.permissions.length > 0);
  });
  private roleId: string | null = null;

  constructor() {
    addIcons({ saveOutline, arrowBackOutline, checkmarkOutline, closeOutline, shieldOutline });
  }

  ngOnInit() {
    this.roleId = this.route.snapshot.paramMap.get('id');
    if (!this.roleId) {
      this.loading.set(false);
      this.loadError.set('No se indicó el rol que se desea administrar.');
      return;
    }
    this.loadData();
  }

  protected loadData() {
    if (!this.roleId) return;
    this.loading.set(true);
    this.loadError.set('');
    forkJoin({
      permissions: this.dataService.getPermissionsStrict(),
      role: this.dataService.getRole(this.roleId),
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: ({ permissions, role }) => {
        const activePermissions = permissions.filter(permission => permission.estado);
        const activeIds = new Set(activePermissions.map(permission => permission.id));
        this.role.set(role);
        this.roleName.set(role.nombre);
        this.groupedPermissions.set(this.groupByModule(activePermissions));
        this.selectedIds.set(new Set((role.permisoIds || []).map(String).filter(id => activeIds.has(id))));
        this.dirty.set(false);
        this.loading.set(false);
      },
      error: () => {
        this.loadError.set('No fue posible cargar el rol y sus permisos.');
        this.loading.set(false);
      },
    });
  }

  private groupByModule(permissions: ConfigPermission[]): GroupedPermission[] {
    const map = new Map<string, ConfigPermission[]>();
    for (const perm of permissions) {
      const list = map.get(perm.modulo) || [];
      list.push(perm);
      map.set(perm.modulo, list);
    }
    return Array.from(map.entries()).map(([module, perms]) => ({ module, permissions: perms }));
  }

  protected togglePermission(id: string) {
    this.selectedIds.update(set => {
      const newSet = new Set(set);
      if (newSet.has(id)) {
        newSet.delete(id);
      } else {
        newSet.add(id);
      }
      this.dirty.set(true);
      return newSet;
    });
  }

  protected toggleAll(group: GroupedPermission) {
    const allSelected = this.isAllSelected(group);
    this.selectedIds.update(set => {
      const newSet = new Set(set);
      for (const perm of group.permissions) {
        if (allSelected) {
          newSet.delete(perm.id);
        } else {
          newSet.add(perm.id);
        }
      }
      this.dirty.set(true);
      return newSet;
    });
  }

  protected isAllSelected(group: GroupedPermission): boolean {
    return group.permissions.every(p => this.selectedIds().has(p.id));
  }

  protected savePermissions() {
    if (!this.roleId || this.saving() || !this.dirty()) return;
    const permisoIds = Array.from(this.selectedIds()).map(Number);
    this.saving.set(true);
    this.dataService.updateRolePermissions(this.roleId, permisoIds).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.saving.set(false);
        this.dirty.set(false);
        this.alertCtrl.create({ header: 'Éxito', message: 'Permisos guardados correctamente', buttons: ['OK'] }).then(a => a.present());
        this.router.navigate(['/config/roles']);
      },
      error: err => {
        this.saving.set(false);
        const msg = err.error?.message || err.message || 'Error al guardar permisos';
        this.alertCtrl.create({ header: 'Error', message: msg, buttons: ['OK'] }).then(a => a.present());
      },
    });
  }

  protected goBack() {
    if (!this.dirty()) {
      this.router.navigate(['/config/roles']);
      return;
    }
    this.alertCtrl.create({
      header: 'Cambios sin guardar',
      message: 'Los cambios de permisos se perderán si regresa al listado.',
      buttons: [
        { text: 'Continuar editando', role: 'cancel' },
        { text: 'Descartar cambios', role: 'destructive', handler: () => this.router.navigate(['/config/roles']) },
      ],
    }).then(alert => alert.present());
  }

  @HostListener('window:beforeunload', ['$event'])
  protected warnBeforeUnload(event: BeforeUnloadEvent) {
    if (this.dirty()) event.preventDefault();
  }
}

interface GroupedPermission {
  module: string;
  permissions: ConfigPermission[];
}
