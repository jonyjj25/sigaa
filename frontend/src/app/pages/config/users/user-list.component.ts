import { Component, inject, signal, computed, ChangeDetectionStrategy, OnInit, DestroyRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { IonIcon } from '@ionic/angular/standalone';
import { AlertController } from '@ionic/angular';
import { addIcons } from 'ionicons';
import {
  personAddOutline, searchOutline, eyeOutline, createOutline,
  lockClosedOutline, lockOpenOutline, peopleOutline,
  checkmarkOutline, removeOutline, trashOutline, trashBinOutline,
  chevronBackOutline, chevronForwardOutline,
} from 'ionicons/icons';
import { ConfigDataService } from '../services/config-data.service';
import { AuthService } from '../../../services/auth.service';
import { ConfigUser } from '../models/config.models';
import { PasswordResetRequestsComponent } from './password-reset-requests.component';

@Component({
  selector: 'app-user-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, IonIcon, PasswordResetRequestsComponent],
  template: `
    <div class="page-container">
      <div class="page-header">
        <div class="header-left">
          <div class="header-icon"><ion-icon name="people-outline" /></div>
          <div>
            <span class="eyebrow">Administración</span>
            <h1>Usuarios</h1>
            <p class="subtitle">Gestiona cuentas, roles, unidades y estados de acceso.</p>
          </div>
        </div>
        <button class="btn-primary" routerLink="/config/users/new">
          <ion-icon name="person-add-outline" />
          Nuevo Usuario
        </button>
      </div>

      <div class="stats-grid">
        <div class="stat-card total">
          <div class="stat-icon-wrapper"><ion-icon name="people-outline" /></div>
          <div class="stat-info">
            <span class="stat-value">{{totalCount()}}</span>
            <span class="stat-label">Total</span>
          </div>
        </div>
        <div class="stat-card active">
          <div class="stat-icon-wrapper"><ion-icon name="checkmark-outline" /></div>
          <div class="stat-info">
            <span class="stat-value">{{activeCount()}}</span>
            <span class="stat-label">Activos</span>
          </div>
        </div>
        <div class="stat-card inactive">
          <div class="stat-icon-wrapper"><ion-icon name="remove-outline" /></div>
          <div class="stat-info">
            <span class="stat-value">{{inactiveCount()}}</span>
            <span class="stat-label">Inactivos</span>
          </div>
        </div>
        <div class="stat-card blocked">
          <div class="stat-icon-wrapper"><ion-icon name="lock-closed-outline" /></div>
          <div class="stat-info">
            <span class="stat-value">{{bloqueadoCount()}}</span>
            <span class="stat-label">Bloqueados</span>
          </div>
        </div>
      </div>

      <app-password-reset-requests />

      <div class="toolbar-card">
        <label class="search-bar">
          <ion-icon name="search-outline" class="search-icon" />
          <input
            type="search"
            aria-label="Buscar usuarios"
            placeholder="Buscar por nombre, correo, rol o unidad..."
            [ngModel]="searchTerm()"
            (ngModelChange)="updateSearch($event)"
            class="search-input"
          />
        </label>
        <div class="filters">
          <label class="filter-field">
            <span>Rol</span>
            <select [ngModel]="roleFilter()" (ngModelChange)="updateRoleFilter($event)">
              <option value="">Todos los roles</option>
              @for (role of roles(); track role) {
                <option [value]="role">{{ capitalize(role) }}</option>
              }
            </select>
          </label>
          <label class="filter-field status-filter">
            <span>Estado</span>
            <select [ngModel]="statusFilter()" (ngModelChange)="updateStatusFilter($event)">
              <option value="">Todos</option>
              <option value="activo">Activos</option>
              <option value="inactivo">Inactivos</option>
              <option value="bloqueado">Bloqueados</option>
            </select>
          </label>
        </div>
      </div>

      <div class="table-container">
        <table class="data-table">
          <thead>
            <tr>
              <th>Nombre</th>
              <th>Correo</th>
              <th>Rol</th>
              <th>Unidades</th>
              <th>Estado</th>
              <th>Acciones</th>
            </tr>
          </thead>
          <tbody>
            @for (user of pagedUsers(); track user.id) {
              <tr>
                <td data-label="Nombre">
                  <div class="user-cell">
                    <div class="user-avatar">{{user.nombre.charAt(0)}}{{user.apellido.charAt(0)}}</div>
                    <div class="user-name-cell">
                      <span class="user-fullname">{{user.nombre}} {{user.apellido}}</span>
                    </div>
                  </div>
                </td>
                <td data-label="Correo" class="cell-email">{{user.correo}}</td>
                <td data-label="Rol">
                  @if (user.rol) {
                    <span class="badge" [class.badge-admin]="user.rol.toLowerCase().includes('admin')" [class.badge-secondary]="!user.rol.toLowerCase().includes('admin')">
                      {{capitalize(user.rol)}}
                    </span>
                  } @else {
                    <span class="badge badge-muted">—</span>
                  }
                </td>
                <td data-label="Unidades">
                  @for (u of user.unidades; track u.unidadId) {
                    <span class="badge badge-outline">{{u.unidadNombre}}</span>
                  }
                  @if (user.unidades.length === 0) {
                    <span class="badge badge-muted">—</span>
                  }
                </td>
                <td data-label="Estado">
                  <span class="status-badge" [class.active]="user.estado === 'activo'" [class.inactive]="user.estado === 'inactivo'" [class.blocked]="user.estado === 'bloqueado'">
                    {{user.estado === 'activo' ? 'Activo' : user.estado === 'inactivo' ? 'Inactivo' : 'Bloqueado'}}
                  </span>
                </td>
                <td data-label="Acciones" class="actions-cell">
                  <button class="btn-icon btn-view" [routerLink]="['/config/users', user.id]" title="Ver" [attr.aria-label]="'Ver usuario ' + user.nombre">
                    <ion-icon name="eye-outline" />
                  </button>
                  <button class="btn-icon btn-edit" [routerLink]="['/config/users', user.id, 'edit']" title="Editar" [attr.aria-label]="'Editar usuario ' + user.nombre">
                    <ion-icon name="create-outline" />
                  </button>
                  <button class="btn-icon btn-lock" (click)="toggleBlock(user)" title="Bloquear/Desbloquear" [attr.aria-label]="'Bloquear o desbloquear usuario ' + user.nombre"
                    [disabled]="user.rol.toLowerCase() === 'superadmin' || user.correo === currentUser()?.correo">
                    <ion-icon [name]="user.estado === 'bloqueado' ? 'lock-open-outline' : 'lock-closed-outline'" />
                  </button>
                   <button class="btn-icon btn-deactivate" (click)="deleteUser(user)" title="Desactivar" [attr.aria-label]="'Desactivar usuario ' + user.nombre"
                    [disabled]="user.rol.toLowerCase() === 'superadmin' || user.correo === currentUser()?.correo">
                    <ion-icon name="trash-outline" />
                  </button>
                  <button class="btn-icon btn-icon-danger" (click)="deleteUserPermanently(user)" title="Eliminar permanentemente" [attr.aria-label]="'Eliminar permanentemente usuario ' + user.nombre"
                    [disabled]="user.rol.toLowerCase() === 'superadmin' || user.correo === currentUser()?.correo">
                    <ion-icon name="trash-bin-outline" />
                  </button>
                </td>
              </tr>
            } @empty {
              <tr>
                <td colspan="6" class="empty-state">
                  <div class="empty-content">
                    <ion-icon name="people-outline" class="empty-icon" />
                    <p>No se encontraron usuarios</p>
                  </div>
                </td>
              </tr>
            }
          </tbody>
        </table>
        <div class="table-footer">
          <span *ngIf="filteredUsers().length">Mostrando {{ rangeStart() }}-{{ rangeEnd() }} de {{ filteredUsers().length }} usuarios</span>
          <div class="pagination" *ngIf="totalPages() > 1">
            <button type="button" (click)="previousPage()" [disabled]="currentPage() === 1" aria-label="Página anterior" title="Página anterior">
              <ion-icon name="chevron-back-outline" />
            </button>
            <span>Página <strong>{{ currentPage() }}</strong> de {{ totalPages() }}</span>
            <button type="button" (click)="nextPage()" [disabled]="currentPage() === totalPages()" aria-label="Página siguiente" title="Página siguiente">
              <ion-icon name="chevron-forward-outline" />
            </button>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .page-container {
      font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
      max-width: 1400px; margin: 0 auto;
    }
    .page-header {
      display: flex; justify-content: space-between; align-items: center; margin-bottom: 28px;
    }
    .header-left { display: flex; align-items: center; gap: 16px; }
    .header-icon {
      width: 48px; height: 48px; background: linear-gradient(135deg, var(--institutional-blue), var(--institutional-blue));
      border-radius: 12px; display: flex; align-items: center; justify-content: center;
      font-size: 24px; color: #fff; flex-shrink: 0;
      box-shadow: 0 4px 12px rgba(59,130,246,0.25);
    }
    .page-header h1 {
      font-size: 24px; font-weight: 700; color: #0f172a;
      margin: 0; letter-spacing: -0.3px;
    }
    .subtitle { color: #64748b; font-size: 14px; margin: 4px 0 0 0; }
    .btn-primary {
      display: flex; align-items: center; gap: 8px;
      background: linear-gradient(135deg, var(--institutional-blue), var(--institutional-blue)); color: #fff; border: none;
      padding: 10px 20px; border-radius: 10px; font-size: 14px;
      font-weight: 600; cursor: pointer; transition: all 0.25s;
      font-family: inherit; text-decoration: none;
      box-shadow: 0 4px 14px rgba(59,130,246,0.3);
    }
    .btn-primary:hover { transform: translateY(-1px); box-shadow: 0 6px 20px rgba(59,130,246,0.4); }
    .btn-primary ion-icon { font-size: 18px; }
    .stats-grid {
      display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 24px;
    }
    .stat-card {
      display: flex; align-items: center; gap: 14px;
      background: #fff; border-radius: 14px; padding: 20px 22px;
      border: 1px solid #e2e8f0; transition: all 0.25s;
      box-shadow: 0 1px 3px rgba(0,0,0,0.04);
    }
    .stat-card:hover { transform: translateY(-2px); box-shadow: 0 4px 16px rgba(0,0,0,0.08); }
    .stat-icon-wrapper {
      width: 46px; height: 46px; border-radius: 12px;
      display: flex; align-items: center; justify-content: center;
      font-size: 22px; flex-shrink: 0;
    }
    .stat-card.total .stat-icon-wrapper { background: var(--brand-soft); color: var(--institutional-blue); }
    .stat-card.active .stat-icon-wrapper { background: #f0fdf4; color: #16a34a; }
    .stat-card.inactive .stat-icon-wrapper { background: #fffbeb; color: #d97706; }
    .stat-card.blocked .stat-icon-wrapper { background: #fef2f2; color: #dc2626; }
    .stat-info { display: flex; flex-direction: column; gap: 2px; }
    .stat-value { font-size: 28px; font-weight: 700; color: #0f172a; line-height: 1.2; }
    .stat-label { font-size: 12px; color: #64748b; text-transform: uppercase; letter-spacing: 0.5px; font-weight: 600; }
    .search-bar {
      display: flex; align-items: center;
      background: #fff; border: 1px solid #e2e8f0;
      border-radius: 10px; padding: 0 16px; margin-bottom: 20px;
      transition: border-color 0.2s, box-shadow 0.2s;
    }
    .search-bar:focus-within {
      border-color: var(--institutional-blue); box-shadow: 0 0 0 3px rgba(59,130,246,0.1);
    }
    .search-icon { font-size: 20px; color: #94a3b8; flex-shrink: 0; }
    .search-input {
      flex: 1; border: none; padding: 14px 12px; font-size: 14px;
      color: #0f172a; background: transparent; outline: none; font-family: inherit;
    }
    .search-input::placeholder { color: #94a3b8; }
    .table-container {
      background: #fff; border-radius: 14px;
      border: 1px solid #e2e8f0; overflow: hidden;
      box-shadow: 0 1px 3px rgba(0,0,0,0.04);
    }
    .data-table { width: 100%; border-collapse: collapse; }
    .data-table th {
      text-align: left; padding: 14px 16px; font-size: 12px;
      font-weight: 600; color: #64748b; text-transform: uppercase;
      letter-spacing: 0.5px; background: #f8fafc; border-bottom: 1px solid #e2e8f0;
    }
    .data-table td {
      padding: 14px 16px; font-size: 14px; color: #334155;
      border-bottom: 1px solid #f1f5f9; transition: background 0.15s;
    }
    .data-table tbody tr { transition: background 0.15s; }
    .data-table tbody tr:hover { background: #f8fafc; }
    .data-table tbody tr:last-child td { border-bottom: none; }
    .user-cell { display: flex; align-items: center; gap: 12px; }
    .user-avatar {
      width: 36px; height: 36px; border-radius: 50%;
      background: linear-gradient(135deg, var(--brand-soft), var(--brand-soft)); color: var(--institutional-blue);
      display: flex; align-items: center; justify-content: center;
      font-size: 13px; font-weight: 700; flex-shrink: 0;
    }
    .user-name-cell { display: flex; flex-direction: column; }
    .user-fullname { font-size: 14px; font-weight: 600; color: #0f172a; }
    .cell-email { color: #64748b; font-size: 13px; }
    .text-muted { color: #94a3b8; font-size: 13px; }
    .badge {
      display: inline-flex; align-items: center;
      padding: 3px 10px; border-radius: 6px;
      font-size: 12px; font-weight: 600; white-space: nowrap;
    }
    .badge-secondary { background: #f1f5f9; color: #64748b; }
    .badge-admin { background: #fef2f2; color: #dc2626; }
    .badge-muted { background: transparent; color: #cbd5e1; padding: 3px 0; }
    .badge-outline { background: #fff; color: var(--institutional-blue); border: 1px solid var(--brand-soft); }
    .status-badge {
      display: inline-flex; align-items: center;
      padding: 4px 12px; border-radius: 20px;
      font-size: 12px; font-weight: 600;
    }
    .status-badge.active { background: #f0fdf4; color: #16a34a; }
    .status-badge.inactive { background: #fef3c7; color: #d97706; }
    .status-badge.blocked { background: #fef2f2; color: #dc2626; }
    .actions-cell { display: flex; gap: 6px; }
    .btn-icon {
      width: 34px; height: 34px;
      border: 1px solid #e2e8f0; border-radius: 8px;
      background: #fff; color: #64748b; cursor: pointer;
      display: flex; align-items: center; justify-content: center;
      transition: all 0.2s; font-size: 16px;
    }
    .btn-icon:hover { background: #f8fafc; color: var(--institutional-blue); border-color: var(--institutional-blue); transform: translateY(-1px); }
    .btn-icon:disabled { opacity: 0.3; cursor: not-allowed; }
    .btn-icon:disabled:hover { background: #fff; color: #64748b; border-color: #e2e8f0; transform: none; }
    .btn-icon-danger { color: #dc2626; border-color: #fecaca; }
    .btn-icon-danger:hover { background: #fef2f2; color: #dc2626; border-color: #dc2626; }
    .empty-state { text-align: center; padding: 60px 16px !important; }
    .empty-content {
      display: flex; flex-direction: column; align-items: center; gap: 12px;
    }
    .empty-icon { font-size: 48px; color: #cbd5e1; }
    .empty-content p { color: #94a3b8; font-size: 15px; margin: 0; }
    @media (max-width: 900px) {
      .stats-grid { grid-template-columns: repeat(2, 1fr); }
      .page-header { flex-direction: column; align-items: flex-start; gap: 12px; }
    }

    /* Visual refresh */
    .page-container { max-width: 1280px; padding-bottom: 24px; }
    .page-header { margin-bottom: 18px; }
    .header-icon {
      width: 52px;
      height: 52px;
      border-radius: 16px;
      background: linear-gradient(145deg, var(--institutional-blue), var(--institutional-blue));
      box-shadow: 0 10px 24px rgba(37, 99, 235, .22);
    }
    .eyebrow {
      display: block;
      margin-bottom: 3px;
      color: var(--institutional-blue);
      font-size: 10px;
      font-weight: 800;
      letter-spacing: .11em;
      text-transform: uppercase;
    }
    .page-header h1 { font-size: 25px; line-height: 1.1; }
    .subtitle { margin-top: 5px; font-size: 13px; }
    .btn-primary { min-height: 42px; padding: 0 17px; border-radius: 11px; background: var(--institutional-blue); box-shadow: 0 7px 16px rgba(37, 99, 235, .22); }
    .btn-primary:hover { background: var(--institutional-blue); box-shadow: 0 9px 20px rgba(37, 99, 235, .28); }

    .stats-grid { gap: 12px; margin-bottom: 16px; }
    .stat-card {
      min-height: 78px;
      padding: 13px 16px;
      border-color: #dbe5f2;
      border-radius: 14px;
      box-shadow: 0 5px 18px rgba(15, 23, 42, .045);
    }
    .stat-card:hover { transform: none; border-color: var(--brand-soft); box-shadow: 0 7px 20px rgba(15, 23, 42, .065); }
    .stat-icon-wrapper { width: 42px; height: 42px; }
    .stat-value { font-size: 23px; line-height: 1; }
    .stat-label { margin-top: 2px; font-size: 10px; letter-spacing: .08em; }

    .toolbar-card {
      display: grid;
      grid-template-columns: minmax(280px, 1fr) auto;
      gap: 12px;
      align-items: end;
      margin-bottom: 14px;
      padding: 12px;
      border: 1px solid #dbe5f2;
      border-radius: 14px;
      background: #fff;
      box-shadow: 0 4px 14px rgba(15, 23, 42, .035);
    }
    .search-bar { min-height: 44px; margin: 0; border-radius: 10px; background: #f8fafc; }
    .search-input { padding: 11px 10px; }
    .filters { display: flex; gap: 10px; }
    .filter-field { display: grid; gap: 4px; }
    .filter-field > span {
      padding-left: 2px;
      color: #64748b;
      font-size: 10px;
      font-weight: 800;
      letter-spacing: .06em;
      text-transform: uppercase;
    }
    .filter-field select {
      min-width: 170px;
      height: 44px;
      padding: 0 34px 0 12px;
      border: 1px solid #dbe5f2;
      border-radius: 10px;
      outline: none;
      color: #334155;
      background: #fff;
      font: 500 13px inherit;
    }
    .status-filter select { min-width: 130px; }
    .filter-field select:focus { border-color: var(--institutional-blue); box-shadow: 0 0 0 3px rgba(37, 99, 235, .1); }

    .table-container { border-color: #dbe5f2; border-radius: 14px; box-shadow: 0 7px 22px rgba(15, 23, 42, .05); }
    .data-table th { padding: 13px 14px; color: #52657e; background: var(--brand-soft); border-bottom-color: var(--brand-soft); font-size: 11px; }
    .data-table td { height: 62px; padding: 11px 14px; border-bottom-color: #edf2f7; }
    .data-table tbody tr:hover { background: #f8fbff; }
    .user-avatar { width: 38px; height: 38px; color: var(--institutional-blue); background: #eaf2ff; box-shadow: inset 0 0 0 1px var(--brand-soft); }
    .badge { padding: 4px 10px; border-radius: 999px; }
    .badge-secondary { color: #475569; background: #f1f5f9; }
    .badge-admin { color: #b91c1c; background: #fef2f2; }
    .badge-outline { color: var(--institutional-blue); border-color: var(--brand-soft); background: #f8fbff; }
    .status-badge { gap: 6px; padding: 5px 10px; }
    .status-badge::before { content: ''; width: 6px; height: 6px; border-radius: 50%; background: currentColor; }
    .status-badge.active { color: #047857; background: #ecfdf5; }
    .status-badge.inactive { color: #b45309; background: #fffbeb; }
    .status-badge.blocked { color: #b91c1c; background: #fef2f2; }

    .actions-cell { display: table-cell; min-width: 214px; white-space: nowrap; }
    .btn-icon { display: inline-flex; width: 34px; height: 34px; margin-right: 4px; border-color: #dbe5f2; border-radius: 9px; vertical-align: middle; }
    .btn-icon:hover { transform: none; }
    .btn-view:hover { color: var(--institutional-blue); border-color: var(--brand-soft); background: var(--brand-soft); }
    .btn-edit:hover { color: #4f46e5; border-color: #c7d2fe; background: #eef2ff; }
    .btn-lock:hover { color: #b45309; border-color: #fde68a; background: #fffbeb; }
    .btn-deactivate { color: #ea580c; border-color: #fed7aa; }
    .btn-deactivate:hover { color: #c2410c; border-color: #fb923c; background: #fff7ed; }
    .btn-icon-danger { color: #dc2626; border-color: #fecaca; }

    .table-footer {
      display: flex;
      align-items: center;
      justify-content: space-between;
      min-height: 54px;
      padding: 9px 14px;
      border-top: 1px solid #e5edf7;
      color: #64748b;
      background: #f8fafc;
      font-size: 12px;
    }
    .pagination { display: flex; align-items: center; gap: 10px; }
    .pagination strong { color: var(--institutional-blue); }
    .pagination button {
      display: grid;
      place-items: center;
      width: 34px;
      height: 34px;
      border: 1px solid #dbe5f2;
      border-radius: 9px;
      color: var(--institutional-blue);
      background: #fff;
      cursor: pointer;
    }
    .pagination button:hover:not(:disabled) { border-color: var(--institutional-blue); background: var(--brand-soft); }
    .pagination button:disabled { cursor: not-allowed; opacity: .42; }
    button:focus-visible, input:focus-visible, select:focus-visible { outline: 3px solid rgba(37, 99, 235, .24); outline-offset: 2px; }

    @media (max-width: 1050px) {
      .toolbar-card { grid-template-columns: 1fr; }
      .filters { display: grid; grid-template-columns: 1fr 1fr; }
      .filter-field select { width: 100%; min-width: 0; }
      .table-container { overflow-x: auto; }
      .data-table { min-width: 980px; }
    }

    @media (max-width: 680px) {
      .stats-grid { grid-template-columns: 1fr 1fr; }
      .stat-card { min-height: 70px; }
      .filters { grid-template-columns: 1fr; }
      .table-container { overflow: visible; border: 0; box-shadow: none; background: transparent; }
      .data-table { display: block; min-width: 0; }
      .data-table thead { display: none; }
      .data-table tbody { display: grid; gap: 10px; }
      .data-table tbody tr {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 11px 14px;
        padding: 14px;
        border: 1px solid #dbe5f2;
        border-radius: 13px;
        background: #fff;
        box-shadow: 0 4px 14px rgba(15, 23, 42, .045);
      }
      .data-table td { display: grid; gap: 4px; height: auto; padding: 0; border: 0; }
      .data-table td::before { content: attr(data-label); color: #94a3b8; font-size: 9px; font-weight: 800; letter-spacing: .08em; text-transform: uppercase; }
      .data-table td:first-child, .data-table .cell-email, .data-table .actions-cell { grid-column: 1 / -1; }
      .actions-cell { min-width: 0; white-space: normal; }
      .empty-state { display: block !important; }
      .empty-state::before { display: none; }
      .table-footer { border: 1px solid #dbe5f2; border-radius: 12px; }
    }

    @media (prefers-reduced-motion: reduce) {
      *, *::before, *::after { transition-duration: .01ms !important; animation-duration: .01ms !important; scroll-behavior: auto !important; }
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserListComponent implements OnInit {
  private configDataService = inject(ConfigDataService);
  private authService = inject(AuthService);
  private alertCtrl = inject(AlertController);
  private destroyRef = inject(DestroyRef);

  protected currentUser = this.authService.currentUser;
  protected users = signal<ConfigUser[]>([]);
  protected searchTerm = signal('');
  protected roleFilter = signal('');
  protected statusFilter = signal('');
  protected currentPage = signal(1);
  protected readonly pageSize = 10;
  protected roles = computed(() =>
    [...new Set(this.users().map(user => user.rol).filter((role): role is string => Boolean(role)))]
      .sort((a, b) => a.localeCompare(b, 'es'))
  );

  protected filteredUsers = computed(() => {
    const term = this.searchTerm().toLowerCase().trim();
    const role = this.roleFilter();
    const status = this.statusFilter();
    return this.users().filter(user => {
      const matchesTerm = !term ||
        user.nombre.toLowerCase().includes(term) ||
        user.apellido.toLowerCase().includes(term) ||
        user.correo.toLowerCase().includes(term) ||
        (user.rol ?? '').toLowerCase().includes(term) ||
        user.unidades.some(unit => (unit.unidadNombre ?? '').toLowerCase().includes(term)) ||
        user.unidades.some(unit => (unit.rolNombre ?? '').toLowerCase().includes(term));
      const matchesRole = !role || user.rol === role;
      const matchesStatus = !status || user.estado === status;
      return matchesTerm && matchesRole && matchesStatus;
    });
  });
  protected totalPages = computed(() => Math.max(1, Math.ceil(this.filteredUsers().length / this.pageSize)));
  protected pagedUsers = computed(() => {
    const start = (this.currentPage() - 1) * this.pageSize;
    return this.filteredUsers().slice(start, start + this.pageSize);
  });
  protected rangeStart = computed(() => this.filteredUsers().length
    ? (this.currentPage() - 1) * this.pageSize + 1
    : 0
  );
  protected rangeEnd = computed(() => Math.min(this.currentPage() * this.pageSize, this.filteredUsers().length));

  protected totalCount = computed(() => this.users().length);
  protected activeCount = computed(() => this.users().filter(u => u.estado === 'activo').length);
  protected inactiveCount = computed(() => this.users().filter(u => u.estado === 'inactivo').length);
  protected bloqueadoCount = computed(() => this.users().filter(u => u.estado === 'bloqueado').length);

  constructor() {
    addIcons({
      personAddOutline, searchOutline, eyeOutline, createOutline,
      lockClosedOutline, lockOpenOutline, peopleOutline, checkmarkOutline, removeOutline, trashOutline, trashBinOutline,
      chevronBackOutline, chevronForwardOutline,
    });
  }

  ngOnInit(): void {
    const sub = this.configDataService.getUsers().subscribe(data => this.users.set(data));
    this.destroyRef.onDestroy(() => sub.unsubscribe());
  }

  protected updateSearch(value: string): void {
    this.searchTerm.set(value);
    this.currentPage.set(1);
  }

  protected updateRoleFilter(value: string): void {
    this.roleFilter.set(value);
    this.currentPage.set(1);
  }

  protected updateStatusFilter(value: string): void {
    this.statusFilter.set(value);
    this.currentPage.set(1);
  }

  protected previousPage(): void {
    this.currentPage.update(page => Math.max(1, page - 1));
  }

  protected nextPage(): void {
    this.currentPage.update(page => Math.min(this.totalPages(), page + 1));
  }

  protected capitalize(val: string): string {
    return val.charAt(0).toUpperCase() + val.slice(1);
  }

  protected async toggleBlock(user: ConfigUser): Promise<void> {
    const action = user.estado === 'bloqueado' ? 'desbloquear' : 'bloquear';
    const confirm = await this.alertCtrl.create({
      header: `${action === 'bloquear' ? 'Bloquear' : 'Desbloquear'} usuario`,
      message: `¿Estás seguro de ${action} a ${user.nombre ?? ''} ${user.apellido ?? ''}?`,
      buttons: [
        { text: 'Cancelar', role: 'cancel' },
        {
          text: action === 'bloquear' ? 'Bloquear' : 'Desbloquear', role: 'destructive', handler: () => {
            this.configDataService.toggleBlockUser(user.id).subscribe({
              next: updated => {
                this.users.update(users => users.map(u => u.id === updated.id ? updated : u));
              },
              error: async err => {
                const msg = err.status === 0 ? 'Error de conexión con el servidor (CORS o red)' : (err.error?.message || err.error || err.message || 'Error al bloquear/desbloquear');
                const alert = await this.alertCtrl.create({
                  header: 'Error',
                  message: typeof msg === 'string' ? msg : JSON.stringify(msg),
                  buttons: ['OK'],
                });
                await alert.present();
              },
            });
          }
        },
      ],
    });
    await confirm.present();
  }

  protected async deleteUser(user: ConfigUser): Promise<void> {
    const alert = await this.alertCtrl.create({
      header: 'Desactivar usuario',
      message: `¿Estás seguro de desactivar a ${user.nombre ?? ''} ${user.apellido ?? ''}? El usuario no podrá iniciar sesión, pero sus datos se conservarán.`,
      buttons: [
        { text: 'Cancelar', role: 'cancel' },
        {
          text: 'Desactivar', role: 'destructive', handler: () => {
            this.configDataService.deleteUser(user.id).subscribe({
              next: () => this.users.update(users => users.map(u => u.id === user.id ? { ...u, estado: 'inactivo' as const } : u)),
              error: (err: any) => console.error('Error al desactivar usuario', err),
            });
          }
        },
      ],
    });
    await alert.present();
  }

  protected async deleteUserPermanently(user: ConfigUser): Promise<void> {
    const alert = await this.alertCtrl.create({
      header: 'Eliminar permanentemente',
      message: `¿Estás seguro de eliminar permanentemente a ${user.nombre ?? ''} ${user.apellido ?? ''}? Esta acción es irreversible: se eliminarán todos los datos del usuario, incluidas sus membresías y roles.`,
      buttons: [
        { text: 'Cancelar', role: 'cancel' },
        {
          text: 'Eliminar permanentemente', role: 'destructive', handler: () => {
            this.configDataService.deleteUserPermanently(user.id).subscribe({
              next: () => this.users.update(users => users.filter(u => u.id !== user.id)),
              error: async (err: any) => {
                const msg = err.error?.error || err.error || err.message || 'Error al eliminar el usuario';
                const errorAlert = await this.alertCtrl.create({
                  header: 'Error',
                  message: typeof msg === 'string' ? msg : JSON.stringify(msg),
                  buttons: ['OK'],
                });
                await errorAlert.present();
              },
            });
          }
        },
      ],
    });
    await alert.present();
  }
}
