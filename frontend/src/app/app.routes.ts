import { inject } from '@angular/core';
import { Router, Routes } from '@angular/router';
import { AuthGuard } from './services/auth.guard';
import { RoleGuard } from './services/role.guard';
import { PermissionGuard } from './services/permission.guard';
import { configRoutes } from './pages/config/services/config-routes';
import { AuthService } from './services/auth.service';

export const routes: Routes = [
  {
    path: 'home',
    loadComponent: () => import('./home/home.page').then((m) => m.HomePage),
  },
  {
    path: 'dashboard',
    loadComponent: () => import('./dashboard/dashboard.page').then((m) => m.DashboardPage),
    canActivate: [AuthGuard],
  },
  {
    path: 'asistencia-qr',
    loadComponent: () => import('./pages/asistencia-qr/asistencia-qr.page').then((m) => m.AsistenciaQrPage),
    canActivate: [AuthGuard, PermissionGuard],
    data: { permissionModule: 'Configuracion QR', permissionAction: 'Ver' },
  },
  {
    path: 'asistencia/publica/:codigo',
    loadComponent: () => import('./pages/asistencia-publica/asistencia-publica.page').then((m) => m.AsistenciaPublicaPage),
  },
  {
    path: 'actas',
    loadComponent: () => import('./pages/actas/actas.page').then((m) => m.ActasPage),
    canActivate: [AuthGuard],
  },
  {
    path: 'actas/nueva',
    loadComponent: () => import('./pages/actas/acta-form.page').then((m) => m.ActaFormPage),
    canActivate: [AuthGuard],
  },
  {
    path: 'actas/:id',
    loadComponent: () => import('./pages/actas/actas.page').then((m) => m.ActasPage),
    canActivate: [AuthGuard],
  },
  {
    path: 'inventario',
    loadComponent: () => import('./pages/inventario/inventario.page').then((m) => m.InventarioPage),
    canActivate: [AuthGuard, RoleGuard],
  },
  {
    path: 'reuniones/:id',
    loadComponent: () => import('./pages/reuniones/reuniones.page').then((m) => m.ReunionesPage),
    canActivate: [AuthGuard],
  },
  {
    path: 'reuniones',
    loadComponent: () => import('./pages/reuniones/reuniones.page').then((m) => m.ReunionesPage),
    canActivate: [AuthGuard],
  },
  {
    path: 'grupos',
    loadComponent: () => import('./pages/grupos/grupos.page').then((m) => m.GruposPage),
    canActivate: [AuthGuard],
  },
  {
    path: 'grupos/:id',
    loadComponent: () => import('./pages/grupos/grupo-detalle.page').then((m) => m.GrupoDetallePage),
    canActivate: [AuthGuard],
  },
  {
    path: 'tareas',
    loadComponent: () => import('./pages/tareas/tareas.page').then((m) => m.TareasPage),
    canActivate: [AuthGuard],
  },
  {
    path: 'notas/:id',
    redirectTo: 'correspondencia/notas/:id',
    pathMatch: 'full',
  },
  {
    path: 'correspondencia/notas/:id',
    loadComponent: () => import('./pages/notas/nota-detalle.page').then((m) => m.NotaDetallePage),
    canActivate: [AuthGuard, PermissionGuard],
    data: { permissionModule: 'Notas', permissionAction: 'Ver' },
  },
  {
    path: 'notas',
    redirectTo: '/correspondencia/notas',
    pathMatch: 'full',
  },
  {
    path: 'memos',
    redirectTo: '/correspondencia/memos',
    pathMatch: 'full',
  },
  {
    path: 'correspondencia',
    redirectTo: () => {
      const auth = inject(AuthService);
      const router = inject(Router);
      return router.parseUrl(auth.hasPermission('Notas', 'Ver') ? '/correspondencia/notas' : '/correspondencia/memos');
    },
    pathMatch: 'full',
  },
  {
    path: 'correspondencia/notas',
    loadComponent: () => import('./pages/notas/notas.page').then((m) => m.NotasPage),
    canActivate: [AuthGuard, PermissionGuard],
    data: { permissionModule: 'Notas', permissionAction: 'Ver' },
  },
  {
    path: 'correspondencia/memos',
    loadComponent: () => import('./pages/memos/memos.page').then((m) => m.MemosPage),
    canActivate: [AuthGuard, PermissionGuard],
    data: { permissionModule: 'Memos', permissionAction: 'Ver' },
  },
  {
    path: 'reportes',
    redirectTo: 'reporte',
    pathMatch: 'full',
  },
  {
    path: 'reporte',
    loadComponent: () => import('./pages/reportes/reportes.page').then((m) => m.ReportesPage),
    canActivate: [AuthGuard, RoleGuard],
  },
  {
    path: 'documentos',
    loadComponent: () => import('./pages/documentos/documentos.page').then((m) => m.DocumentosPage),
    canActivate: [AuthGuard],
  },
  ...configRoutes,
  {
    path: '',
    redirectTo: 'home',
    pathMatch: 'full',
  },
  {
    path: '**',
    redirectTo: 'home',
  },
];
