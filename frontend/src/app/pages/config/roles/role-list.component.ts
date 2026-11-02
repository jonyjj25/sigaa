import { Component, OnInit, signal, ChangeDetectionStrategy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { IonIcon, AlertController } from '@ionic/angular/standalone';
import { Router } from '@angular/router';
import { addIcons } from 'ionicons';
import { searchOutline, addOutline, createOutline, archiveOutline, keyOutline, checkmarkOutline, closeOutline, shieldOutline, peopleOutline, gridOutline } from 'ionicons/icons';
import { ConfigDataService } from '../services/config-data.service';
import { ConfigRole, ConfigUser } from '../models/config.models';

@Component({
  selector: 'app-role-list',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, IonIcon],
  template: `
    <div class="page-container">
      <div class="page-header">
        <div class="header-left">
          <div class="header-icon">
            <ion-icon name="shield-outline" />
          </div>
          <div>
            <h1>Roles</h1>
            <p class="subtitle">Gestión de roles y permisos institucionales</p>
          </div>
        </div>
        <div class="header-actions">
          <button class="btn-secondary" type="button" (click)="openAccessMatrix()">
            <ion-icon name="grid-outline" />
            Consultar matriz
          </button>
          <button class="btn-primary" (click)="openCreateModal()">
            <ion-icon name="add-outline" />
            Nuevo Rol
          </button>
        </div>
      </div>

      <div class="search-bar">
        <ion-icon name="search-outline" class="search-icon" />
        <input
          type="text"
          placeholder="Buscar roles..."
          aria-label="Buscar roles"
          [ngModel]="searchTerm()"
          (ngModelChange)="searchTerm.set($event)"
          class="search-input"
        />
      </div>

      <div *ngIf="loadingRoles()" class="feedback-state">Cargando roles...</div>
      <div *ngIf="loadError()" class="feedback-state feedback-error" role="alert">
        <span>{{ loadError() }}</span>
        <button type="button" class="btn-retry" (click)="loadRoles()">Reintentar</button>
      </div>

      <div class="table-container" *ngIf="!loadingRoles() && !loadError()">
        <table class="data-table">
          <thead>
            <tr>
              <th>Rol</th>
              <th>Descripción</th>
              <th>Permisos</th>
              <th>Usuarios</th>
              <th>Estado</th>
              <th>Acciones</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let role of filteredRoles()">
              <td class="role-name">
                {{ role.nombre }}
                <span *ngIf="role.sistema" class="system-badge">Institucional</span>
              </td>
              <td class="role-desc">{{ role.descripcion }}</td>
              <td>
                <button type="button" class="badge badge-info badge-clickable"
                        [attr.aria-label]="'Administrar ' + role.permisos + ' permisos de ' + role.nombre"
                        title="Administrar permisos" (click)="managePermissions(role)">
                  {{ role.permisos }}
                  <ion-icon name="key-outline" class="badge-icon" aria-hidden="true" />
                </button>
              </td>
              <td>
                <button class="badge badge-secondary badge-clickable" (click)="openUsersModal(role)" [title]="'Ver usuarios asignados a ' + role.nombre">
                  {{ role.usuarios }}
                  <ion-icon name="people-outline" class="badge-icon" />
                </button>
              </td>
              <td>
                <span class="status-badge" [class.active]="role.estado" [class.inactive]="!role.estado">
                  {{ role.estado ? 'Activo' : 'Inactivo' }}
                </span>
              </td>
              <td class="actions-cell">
                <button type="button" class="btn-icon" [attr.aria-label]="'Administrar permisos de ' + role.nombre" title="Administrar permisos" (click)="managePermissions(role)">
                  <ion-icon name="key-outline" aria-hidden="true" />
                </button>
                <button type="button" class="btn-icon" [attr.aria-label]="'Editar rol ' + role.nombre" title="Editar" (click)="openEditModal(role)">
                  <ion-icon name="create-outline" aria-hidden="true" />
                </button>
                <button type="button" class="btn-icon btn-icon-danger" [attr.aria-label]="'Archivar rol ' + role.nombre"
                        [disabled]="role.sistema || !role.estado"
                        [title]="role.sistema ? 'Los roles institucionales están protegidos' : (!role.estado ? 'El rol ya está archivado' : 'Archivar')"
                        (click)="archiveRole(role)">
                  <ion-icon name="archive-outline" aria-hidden="true" />
                </button>
              </td>
            </tr>
            <tr *ngIf="filteredRoles().length === 0">
              <td colspan="6" class="empty-state">
                <div class="empty-content">
                  <ion-icon name="shield-outline" class="empty-icon" />
                  <p>No se encontraron roles</p>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Create/Edit Modal -->
    <div class="modal-overlay" *ngIf="showModal()" (click)="closeModal()">
      <div class="modal-content" (click)="$event.stopPropagation()">
        <div class="modal-header">
          <h2>{{ editingRole() ? 'Editar Rol' : 'Nuevo Rol' }}</h2>
          <button type="button" class="btn-close" aria-label="Cerrar formulario de rol" (click)="closeModal()" title="Cerrar formulario de rol">
            <ion-icon name="close-outline" aria-hidden="true" />
          </button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label for="nombre">Nombre</label>
            <input
              id="nombre"
              type="text"
              [formControl]="roleForm.controls.nombre"
              placeholder="Nombre del rol"
              maxlength="100"
              [attr.aria-describedby]="roleForm.controls.nombre.invalid ? 'nombre-error' : null"
              class="form-input"
            />
            <small id="nombre-error" class="field-error" *ngIf="roleForm.controls.nombre.touched && roleForm.controls.nombre.invalid">Ingrese un nombre de hasta 100 caracteres.</small>
          </div>
          <div class="form-group">
            <label for="descripcion">Descripción</label>
            <textarea
              id="descripcion"
              [formControl]="roleForm.controls.descripcion"
              placeholder="Descripción del rol"
              class="form-input form-textarea"
              rows="3"
              maxlength="255"
            ></textarea>
            <small class="field-help">{{ roleForm.controls.descripcion.value?.length || 0 }}/255</small>
          </div>
          <div class="form-group toggle-group">
            <label>Estado</label>
            <div class="toggle-wrapper">
              <span class="toggle-label">{{ roleForm.controls.estado.value ? 'Activo' : 'Inactivo' }}</span>
              <button
                class="toggle-btn"
                [class.toggled]="roleForm.controls.estado.value"
                [disabled]="editingRole()?.sistema"
                role="switch"
                [attr.aria-checked]="roleForm.controls.estado.value"
                aria-label="Estado del rol"
                (click)="roleForm.controls.estado.setValue(!roleForm.controls.estado.value)"
                type="button"
              >
                <div class="toggle-thumb"></div>
              </button>
            </div>
            <small class="field-help" *ngIf="editingRole()?.sistema">Los roles institucionales deben permanecer activos.</small>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-cancel" (click)="closeModal()">Cancelar</button>
          <button class="btn-primary" [disabled]="savingRole() || roleForm.invalid" (click)="saveRole()">
            {{ savingRole() ? 'Guardando...' : (editingRole() ? 'Actualizar' : 'Crear') }}
          </button>
        </div>
      </div>
    </div>

    <!-- Users Modal -->
    <div class="modal-overlay" *ngIf="showUsersModal()" (click)="closeUsersModal()">
      <div class="modal-content modal-wide" (click)="$event.stopPropagation()">
        <div class="modal-header">
          <div>
            <h2>Usuarios asignados: {{ selectedRole()?.nombre }}</h2>
            <p class="modal-subtitle">{{ roleUsers().length }} usuario(s) con este rol</p>
          </div>
          <button type="button" class="btn-close" aria-label="Cerrar usuarios asignados" (click)="closeUsersModal()" title="Cerrar usuarios asignados">
            <ion-icon name="close-outline" aria-hidden="true" />
          </button>
        </div>
        <div class="modal-body">
          <div *ngIf="loadingUsers()" class="loading-state">
            <p>Cargando usuarios...</p>
          </div>
          <div *ngIf="!loadingUsers() && !usersError() && roleUsers().length === 0" class="empty-users">
            <ion-icon name="people-outline" class="empty-icon" />
            <p>No hay usuarios asignados a este rol</p>
          </div>
          <div *ngIf="!loadingUsers() && usersError()" class="feedback-state feedback-error" role="alert">
            {{ usersError() }}
          </div>
          <div *ngIf="!loadingUsers() && roleUsers().length > 0" class="users-table-container">
            <table class="users-table">
              <thead>
                <tr>
                  <th>Nombre</th>
                  <th>Correo</th>
                  <th>Estado</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let user of roleUsers()">
                  <td class="user-name-cell">
                    <div class="user-avatar">{{ user.nombre.charAt(0) }}{{ user.apellido.charAt(0) }}</div>
                    <span>{{ user.nombre }} {{ user.apellido }}</span>
                  </td>
                  <td>{{ user.correo }}</td>
                  <td>
                    <span class="status-badge" [class.active]="user.estado === 'activo'" [class.inactive]="user.estado === 'inactivo'" [class.blocked]="user.estado === 'bloqueado'">
                      {{ user.estado === 'activo' ? 'Activo' : user.estado === 'inactivo' ? 'Inactivo' : 'Bloqueado' }}
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-cancel" (click)="closeUsersModal()">Cerrar</button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .page-container { font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; max-width: 1200px; margin: 0 auto; }
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
    .header-left { display: flex; align-items: center; gap: 16px; }
    .header-icon { width: 48px; height: 48px; background: #f5f3ff; border-radius: 12px; display: flex; align-items: center; justify-content: center; font-size: 24px; color: #8b5cf6; }
    .page-header h1 { font-size: 24px; font-weight: 700; color: var(--text-strong); margin: 0; }
    .subtitle { color: var(--text-soft); font-size: 14px; margin: 4px 0 0 0; }
    .header-actions { display: flex; align-items: center; gap: 10px; }
    .btn-primary, .btn-secondary { display: flex; align-items: center; gap: 8px; padding: 10px 20px; border-radius: 8px; font-size: 14px; font-weight: 600; cursor: pointer; transition: all 0.2s; font-family: inherit; }
    .btn-primary { background: #8b5cf6; color: #fff; border: none; }
    .btn-primary:hover { background: #7c3aed; transform: translateY(-1px); box-shadow: 0 4px 12px rgba(139, 92, 246, 0.35); }
    .btn-primary ion-icon, .btn-secondary ion-icon { font-size: 18px; }
    .btn-secondary { background: var(--surface-panel); color: var(--text-strong); border: 1px solid var(--border-default); }
    .btn-secondary:hover { color: #7c3aed; border-color: #8b5cf6; background: var(--surface-subtle); }
    .search-bar { display: flex; align-items: center; background: var(--surface-panel); border: 1px solid var(--border-default); border-radius: 10px; padding: 0 16px; margin-bottom: 20px; box-shadow: var(--shadow-sm); }
    .search-bar:focus-within { border-color: #b7c3d2; box-shadow: none; }
    .search-icon { font-size: 20px; color: #94a3b8; flex-shrink: 0; }
    .search-input { flex: 1; border: none; padding: 12px 12px; font-size: 14px; color: var(--text-strong); background: transparent; outline: none; box-shadow: none !important; font-family: inherit; }
    .search-input:focus, .search-input:focus-visible { border: 0 !important; outline: 0 !important; box-shadow: none !important; }
    .search-input::placeholder { color: #94a3b8; }
    .table-container { background: var(--surface-panel); border-radius: 12px; border: 1px solid var(--border-default); overflow-x: auto; box-shadow: var(--shadow-sm); }
    .data-table { width: 100%; border-collapse: collapse; }
    .data-table th { text-align: left; padding: 14px 16px; font-size: 12px; font-weight: 600; color: var(--text-soft); text-transform: uppercase; letter-spacing: 0.5px; background: var(--surface-subtle); border-bottom: 1px solid var(--border-default); }
    .data-table td { padding: 14px 16px; font-size: 14px; color: var(--text-default); border-bottom: 1px solid var(--border-subtle); }
    .data-table tbody tr:hover { background: var(--surface-hover); }
    .data-table tbody tr:last-child td { border-bottom: none; }
    .role-name { font-weight: 600; color: var(--text-strong); white-space: nowrap; }
    .role-desc { color: var(--text-soft); min-width: 220px; }
    .system-badge { display: inline-flex; margin-left: 6px; padding: 2px 7px; border-radius: 999px; background: var(--brand-soft); color: var(--text-link); font-size: 10px; font-weight: 700; text-transform: uppercase; }
    .badge { display: inline-flex; align-items: center; gap: 4px; padding: 3px 10px; border-radius: 6px; font-size: 12px; font-weight: 600; }
    .badge-info { background: var(--brand-soft); color: var(--institutional-blue); }
    .badge-secondary { background: #f1f5f9; color: #64748b; }
    .badge-clickable { cursor: pointer; border: 1px solid transparent; transition: all 0.2s; }
    .badge-clickable:hover { border-color: #8b5cf6; background: #f5f3ff; color: #7c3aed; }
    .badge-icon { font-size: 14px; }
    .status-badge { display: inline-flex; align-items: center; padding: 4px 12px; border-radius: 20px; font-size: 12px; font-weight: 600; }
    .status-badge.active { background: #f0fdf4; color: #16a34a; }
    .status-badge.inactive { background: #fef2f2; color: #dc2626; }
    .status-badge.blocked { background: #fef3c7; color: #d97706; }
    .actions-cell { display: flex; gap: 8px; }
    .btn-icon { width: 34px; height: 34px; border: 1px solid var(--border-default); border-radius: 8px; background: var(--surface-panel); color: var(--text-soft); cursor: pointer; display: flex; align-items: center; justify-content: center; transition: all 0.2s; font-size: 16px; }
    .btn-icon:disabled, .btn-primary:disabled, .toggle-btn:disabled { opacity: .5; cursor: not-allowed; transform: none; box-shadow: none; }
    .btn-icon:hover { background: #f8fafc; color: var(--institutional-blue); border-color: var(--institutional-blue); }
    .btn-icon-danger:hover { color: #dc2626; border-color: #dc2626; background: #fef2f2; }
    .empty-state { text-align: center; padding: 48px 16px !important; }
    .empty-content { display: flex; flex-direction: column; align-items: center; gap: 12px; }
    .empty-icon { font-size: 48px; color: #cbd5e1; }
    .empty-content p { color: #94a3b8; font-size: 15px; margin: 0; }
    .modal-overlay { position: fixed; inset: 0; background: rgba(15, 23, 42, 0.5); display: flex; align-items: center; justify-content: center; z-index: 1000; backdrop-filter: blur(4px); }
    .modal-content { background: var(--surface-overlay); color: var(--text-default); border: 1px solid var(--border-default); border-radius: 16px; width: 480px; max-width: 90vw; max-height: 90vh; overflow-y: auto; box-shadow: var(--shadow-xl); }
    .modal-wide { width: 640px; }
    .modal-header { display: flex; justify-content: space-between; align-items: center; padding: 20px 24px; border-bottom: 1px solid var(--border-default); }
    .modal-header h2 { margin: 0; font-size: 18px; font-weight: 600; color: var(--text-strong); }
    .modal-subtitle { margin: 4px 0 0 0; font-size: 13px; color: #64748b; }
    .btn-close { width: 32px; height: 32px; border: 1px solid #e2e8f0; border-radius: 8px; background: #fff; color: #94a3b8; cursor: pointer; display: flex; align-items: center; justify-content: center; transition: all 0.2s; font-size: 18px; }
    .btn-close:hover { background: #f1f5f9; color: #475569; }
    .modal-body { padding: 20px 24px; display: flex; flex-direction: column; gap: 20px; }
    .form-group { display: flex; flex-direction: column; gap: 6px; }
    .form-group label { font-size: 13px; font-weight: 600; color: var(--text-default); }
    .form-input { border: 1px solid var(--border-default); border-radius: 8px; padding: 10px 14px; font-size: 14px; color: var(--text-strong); background: var(--control-bg); outline: none; transition: all 0.2s; font-family: inherit; }
    .form-input:disabled { opacity: .7; cursor: not-allowed; }
    .form-input:focus { border-color: #8b5cf6; background: #fff; box-shadow: 0 0 0 3px rgba(139, 92, 246, 0.1); }
    .form-textarea { resize: vertical; }
    .toggle-group { gap: 10px; }
    .toggle-wrapper { display: flex; align-items: center; gap: 12px; }
    .toggle-label { font-size: 14px; color: #475569; font-weight: 500; min-width: 60px; }
    .toggle-btn { width: 44px; height: 24px; border-radius: 12px; border: none; background: #cbd5e1; cursor: pointer; position: relative; transition: all 0.3s; padding: 0; }
    .toggle-btn.toggled { background: #8b5cf6; }
    .toggle-thumb { width: 20px; height: 20px; border-radius: 50%; background: #fff; position: absolute; top: 2px; left: 2px; transition: all 0.3s; box-shadow: 0 1px 3px rgba(0,0,0,0.15); }
    .toggle-btn.toggled .toggle-thumb { left: 22px; }
    .modal-footer { display: flex; justify-content: flex-end; gap: 12px; padding: 16px 24px; border-top: 1px solid var(--border-default); }
    .field-error { color: var(--app-danger); font-size: 12px; }
    .field-help { color: var(--text-muted-accessible); font-size: 12px; }
    .feedback-state { display: flex; align-items: center; justify-content: center; gap: 12px; min-height: 96px; margin-bottom: 20px; padding: 20px; border: 1px solid var(--border-default); border-radius: 12px; background: var(--surface-panel); color: var(--text-soft); }
    .feedback-error { border-color: color-mix(in srgb, var(--app-danger) 40%, var(--border-default)); color: var(--app-danger); }
    .btn-retry { border: 1px solid currentColor; border-radius: 8px; padding: 7px 12px; background: transparent; color: inherit; cursor: pointer; font-weight: 600; }
    .btn-cancel { background: #fff; color: #64748b; border: 1px solid #e2e8f0; padding: 10px 20px; border-radius: 8px; font-size: 14px; font-weight: 600; cursor: pointer; transition: all 0.2s; font-family: inherit; }
    .btn-cancel:hover { background: #f8fafc; border-color: #94a3b8; }
    .loading-state { text-align: center; padding: 32px; color: #64748b; }
    .empty-users { display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 32px; }
    .empty-users .empty-icon { font-size: 48px; color: #cbd5e1; }
    .empty-users p { color: #94a3b8; font-size: 15px; margin: 0; }
    .users-table-container { max-height: 400px; overflow-y: auto; }
    .users-table { width: 100%; border-collapse: collapse; }
    .users-table th { text-align: left; padding: 10px 12px; font-size: 12px; font-weight: 600; color: #64748b; text-transform: uppercase; letter-spacing: 0.5px; background: #f8fafc; border-bottom: 1px solid #e2e8f0; }
    .users-table td { padding: 10px 12px; font-size: 14px; color: #334155; border-bottom: 1px solid #f1f5f9; }
    .users-table tbody tr:hover { background: #f8fafc; }
    .user-name-cell { display: flex; align-items: center; gap: 10px; }
    .user-avatar { width: 30px; height: 30px; border-radius: 50%; background: #ede9fe; color: #7c3aed; display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 600; flex-shrink: 0; }
    @media (max-width: 720px) {
      .page-header { align-items: flex-start; gap: 16px; flex-direction: column; }
      .header-actions { width: 100%; flex-wrap: wrap; }
      .page-header .btn-primary { padding-inline: 14px; }
      .data-table { min-width: 880px; }
      .modal-content { max-width: calc(100vw - 24px); }
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RoleListComponent implements OnInit {
  private dataService = inject(ConfigDataService);
  private fb = inject(FormBuilder);
  private alertCtrl = inject(AlertController);
  private router = inject(Router);

  protected roles = signal<ConfigRole[]>([]);
  protected searchTerm = signal('');
  protected showModal = signal(false);
  protected editingRole = signal<ConfigRole | null>(null);
  protected showUsersModal = signal(false);
  protected selectedRole = signal<ConfigRole | null>(null);
  protected roleUsers = signal<ConfigUser[]>([]);
  protected loadingUsers = signal(false);
  protected loadingRoles = signal(true);
  protected loadError = signal('');
  protected usersError = signal('');
  protected savingRole = signal(false);

  protected roleForm = this.fb.group({
    nombre: ['', [Validators.required, Validators.maxLength(100)]],
    descripcion: ['', Validators.maxLength(255)],
    estado: [true],
  });

  protected filteredRoles = () => {
    const term = this.searchTerm().toLowerCase();
    return this.roles().filter(r =>
      r.nombre.toLowerCase().includes(term) ||
      r.descripcion.toLowerCase().includes(term)
    );
  };

  constructor() {
    addIcons({ searchOutline, addOutline, createOutline, archiveOutline, keyOutline, checkmarkOutline, closeOutline, shieldOutline, peopleOutline, gridOutline });
  }

  ngOnInit() {
    this.loadRoles();
  }

  protected loadRoles() {
    this.loadingRoles.set(true);
    this.loadError.set('');
    this.dataService.getRolesStrict().subscribe({
      next: roles => {
        this.roles.set(roles);
        this.loadingRoles.set(false);
      },
      error: () => {
        this.loadError.set('No fue posible cargar los roles.');
        this.loadingRoles.set(false);
      },
    });
  }

  protected openUsersModal(role: ConfigRole) {
    this.selectedRole.set(role);
    this.showUsersModal.set(true);
    this.loadingUsers.set(true);
    this.usersError.set('');
    this.dataService.getRoleUsers(role.id).subscribe({
      next: users => {
        this.roleUsers.set(users);
        this.loadingUsers.set(false);
      },
      error: () => {
        this.roleUsers.set([]);
        this.usersError.set('No fue posible consultar los usuarios asignados.');
        this.loadingUsers.set(false);
      },
    });
  }

  protected closeUsersModal() {
    this.showUsersModal.set(false);
    this.selectedRole.set(null);
    this.roleUsers.set([]);
    this.usersError.set('');
  }

  protected openCreateModal() {
    this.editingRole.set(null);
    this.roleForm.controls.nombre.enable({ emitEvent: false });
    this.roleForm.controls.estado.enable({ emitEvent: false });
    this.roleForm.reset({ nombre: '', descripcion: '', estado: true });
    this.showModal.set(true);
  }

  protected openEditModal(role: ConfigRole) {
    this.editingRole.set(role);
    this.roleForm.setValue({ nombre: role.nombre, descripcion: role.descripcion, estado: role.estado });
    if (role.sistema) {
      this.roleForm.controls.nombre.disable({ emitEvent: false });
      this.roleForm.controls.estado.disable({ emitEvent: false });
    } else {
      this.roleForm.controls.nombre.enable({ emitEvent: false });
      this.roleForm.controls.estado.enable({ emitEvent: false });
    }
    this.showModal.set(true);
  }

  protected closeModal() {
    if (this.savingRole()) return;
    this.showModal.set(false);
    this.editingRole.set(null);
    this.roleForm.controls.nombre.enable({ emitEvent: false });
    this.roleForm.controls.estado.enable({ emitEvent: false });
  }

  protected saveRole() {
    this.roleForm.markAllAsTouched();
    if (this.roleForm.invalid || this.savingRole()) return;
    const formValue = this.roleForm.getRawValue();
    const editing = this.editingRole();
    const data = {
      nombre: formValue.nombre!.trim(),
      descripcion: (formValue.descripcion || '').trim(),
      activo: Boolean(formValue.estado),
    };
    this.savingRole.set(true);

    if (editing) {
      this.dataService.updateRole(editing.id, data).subscribe({
        next: updated => {
          this.roles.update(roles => roles.map(r => r.id === updated.id ? updated : r));
          this.savingRole.set(false);
          this.closeModal();
        },
        error: err => {
          this.savingRole.set(false);
          const msg = err.error?.message || err.message || 'Error al actualizar rol';
          this.alertCtrl.create({ header: 'Error', message: msg, buttons: ['OK'] }).then(a => a.present());
        },
      });
    } else {
      this.dataService.createRole(data).subscribe({
        next: created => {
          this.roles.update(roles => [...roles, created]);
          this.savingRole.set(false);
          this.closeModal();
        },
        error: err => {
          this.savingRole.set(false);
          const msg = err.error?.message || err.message || 'Error al crear rol';
          this.alertCtrl.create({ header: 'Error', message: msg, buttons: ['OK'] }).then(a => a.present());
        },
      });
    }
  }

  protected managePermissions(role: ConfigRole) {
    this.router.navigate(['/config/roles', role.id, 'permissions']);
  }

  protected openAccessMatrix(): void {
    this.router.navigate(['/config/permissions/matrix']);
  }

  protected archiveRole(role: ConfigRole) {
    if (role.sistema || !role.estado) return;
    this.alertCtrl.create({
      header: 'Archivar rol',
      message: `El rol "${role.nombre}" dejará de otorgar acceso, pero se conservarán sus asignaciones y su historial.`,
      buttons: [
        { text: 'Cancelar', role: 'cancel' },
        {
          text: 'Archivar',
          role: 'destructive',
          handler: () => {
            this.dataService.deleteRole(role.id).subscribe({
              next: () => {
                this.roles.update(roles => roles.map(current =>
                  current.id === role.id ? { ...current, estado: false } : current));
              },
              error: err => {
                const msg = err.error?.message || err.message || 'Error al archivar rol';
                this.alertCtrl.create({ header: 'Error', message: msg, buttons: ['OK'] }).then(a => a.present());
              },
            });
          },
        },
      ],
    }).then(a => a.present());
  }
}
