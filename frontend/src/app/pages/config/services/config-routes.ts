import { Routes } from '@angular/router';
import { AuthGuard } from '../../../services/auth.guard';
import { AdminGuard } from '../../../services/admin.guard';
import { PermissionGuard } from '../../../services/permission.guard';

const adminOnly = { canActivate: [AdminGuard] };
const usersView = { canActivate: [PermissionGuard], data: { permissionModule: 'Usuarios', permissionAction: 'Ver' } };
const usersCreate = { canActivate: [PermissionGuard], data: { permissionModule: 'Usuarios', permissionAction: 'Crear' } };
const usersEdit = { canActivate: [PermissionGuard], data: { permissionModule: 'Usuarios', permissionAction: 'Editar' } };

export const configRoutes: Routes = [
  {
    path: 'config',
    loadComponent: () => import('../layout/config-layout.component').then(m => m.ConfigLayoutComponent),
    canActivate: [AuthGuard],
    children: [
      { path: '', loadComponent: () => import('../dashboard/config-dashboard.component').then(m => m.ConfigDashboardComponent), ...adminOnly },
      { path: 'users', loadComponent: () => import('../users/user-list.component').then(m => m.UserListComponent), ...usersView },
      { path: 'users/new', loadComponent: () => import('../users/user-form.component').then(m => m.UserFormComponent), ...usersCreate },
      { path: 'users/:id', loadComponent: () => import('../users/user-detail.component').then(m => m.UserDetailComponent), ...usersView },
      { path: 'users/:id/edit', loadComponent: () => import('../users/user-form.component').then(m => m.UserFormComponent), ...usersEdit },
      { path: 'users/:id/history', loadComponent: () => import('../users/user-history.component').then(m => m.UserHistoryComponent), ...usersView },
      { path: 'roles', loadComponent: () => import('../roles/role-list.component').then(m => m.RoleListComponent), ...adminOnly },
      { path: 'roles/new', loadComponent: () => import('../roles/role-form.component').then(m => m.RoleFormComponent), ...adminOnly },
      { path: 'roles/:id/edit', loadComponent: () => import('../roles/role-form.component').then(m => m.RoleFormComponent), ...adminOnly },
      { path: 'roles/:id/permissions', loadComponent: () => import('../roles/role-permissions.component').then(m => m.RolePermissionsComponent), ...adminOnly },
      { path: 'permissions', loadComponent: () => import('../permissions/permission-list.component').then(m => m.PermissionListComponent), ...adminOnly },
      { path: 'permissions/matrix', loadComponent: () => import('../permissions/permission-matrix.component').then(m => m.PermissionMatrixComponent), ...adminOnly },
      { path: 'modules', loadComponent: () => import('../modules/module-list.component').then(m => m.ModuleListComponent), ...adminOnly },
      // Los parámetros genéricos no están conectados a funciones operativas.
      // Conservamos la URL como redirección para no romper marcadores existentes.
      { path: 'parameters', redirectTo: '', pathMatch: 'full' },
      { path: 'security', loadComponent: () => import('../security/security.component').then(m => m.SecurityComponent), ...adminOnly },
      { path: 'audit', loadComponent: () => import('../audit/audit.component').then(m => m.AuditComponent), ...adminOnly },
      { path: 'logs', redirectTo: 'audit', pathMatch: 'full' },
      { path: 'notifications', loadComponent: () => import('../notifications/notification-list.component').then(m => m.NotificationListComponent), ...adminOnly },
      { path: 'backups', loadComponent: () => import('../backups/backup-list.component').then(m => m.BackupListComponent), ...adminOnly },
      { path: 'backup', redirectTo: 'backups', pathMatch: 'full' },
      { path: 'guia', loadComponent: () => import('../guia/guia.component').then(m => m.GuiaComponent) },
      { path: 'catalogs', redirectTo: '', pathMatch: 'full' },
      { path: 'organization', loadComponent: () => import('../organization/organization.component').then(m => m.OrganizationComponent), ...adminOnly },
      { path: 'ubicaciones', loadComponent: () => import('../ubicaciones/ubicacion-list.component').then(m => m.UbicacionListComponent), ...adminOnly },
      { path: 'profile', loadComponent: () => import('../profile/profile.component').then(m => m.ProfileComponent) },
      { path: 'password', loadComponent: () => import('../password/password.component').then(m => m.PasswordComponent) },
      { path: 'preferences', loadComponent: () => import('../preferences/preferences.component').then(m => m.PreferencesComponent) },
    ],
  },
];
