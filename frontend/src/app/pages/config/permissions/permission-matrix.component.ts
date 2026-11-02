import { Component, OnInit, signal, computed, inject, ChangeDetectionStrategy, DestroyRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { IonIcon } from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { forkJoin } from 'rxjs';
import {
  keyOutline, checkmarkCircleOutline, closeOutline, arrowBackOutline,
  searchOutline, shieldCheckmarkOutline, peopleOutline,
  chevronBackOutline, chevronForwardOutline,
} from 'ionicons/icons';
import { ConfigDataService } from '../services/config-data.service';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ConfigRole, ConfigPermission } from '../models/config.models';

interface MatrixCell {
  roleId: string;
  roleName: string;
  checked: boolean;
}

interface MatrixRow {
  permission: ConfigPermission;
  cells: MatrixCell[];
}

interface MatrixGroup {
  modulo: string;
  rows: MatrixRow[];
}

@Component({
  selector: 'app-permission-matrix',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, RouterLink, IonIcon],
  template: `
    <div class="page-container">
      <div class="page-header">
        <div class="header-left">
          <div class="header-icon">
            <ion-icon name="key-outline" />
          </div>
          <div>
            <span class="eyebrow">Gobierno de accesos</span>
            <h1>Matriz de acceso</h1>
            <p class="subtitle">Consulta qué permisos efectivos tiene cada rol institucional.</p>
          </div>
        </div>
        <a class="btn-back" routerLink="/config/roles">
          <ion-icon name="arrow-back-outline" />
          Volver a Roles
        </a>
      </div>

      <section class="governance-notice" role="note">
        <ion-icon name="shield-checkmark-outline" />
        <div>
          <strong>Vista consolidada de solo lectura</strong>
          <span>Para cambiar accesos utiliza la acción “Administrar permisos” del rol correspondiente. La matriz no realiza modificaciones masivas.</span>
        </div>
      </section>

      <div *ngIf="loading()" class="feedback-state">Cargando matriz de acceso...</div>
      <div *ngIf="loadError()" class="feedback-state feedback-error" role="alert">
        <span>{{ loadError() }}</span>
        <button type="button" (click)="loadMatrix()">Reintentar</button>
      </div>

      <section class="summary-grid" aria-label="Resumen de la matriz" *ngIf="!loading() && !loadError()">
        <article class="summary-card roles-summary">
          <span class="summary-icon"><ion-icon name="people-outline" /></span>
          <span class="summary-copy"><strong>{{ roles().length }}</strong><small>Roles configurados</small></span>
        </article>
        <article class="summary-card permissions-summary">
          <span class="summary-icon"><ion-icon name="key-outline" /></span>
          <span class="summary-copy"><strong>{{ permissions().length }}</strong><small>Permisos disponibles</small></span>
        </article>
        <article class="summary-card assignments-summary">
          <span class="summary-icon"><ion-icon name="shield-checkmark-outline" /></span>
          <span class="summary-copy"><strong>{{ totalAssignments() }}</strong><small>Asignaciones activas</small></span>
        </article>
      </section>

      <div class="toolbar-card" *ngIf="!loading() && !loadError()">
        <label class="search-field">
          <ion-icon name="search-outline" />
          <input
            type="search"
            aria-label="Buscar permisos en la matriz"
            placeholder="Buscar por acción, recurso o módulo..."
            [ngModel]="searchTerm()"
            (ngModelChange)="updateSearch($event)"
          />
        </label>
        <label class="filter-field">
          <span>Módulo</span>
          <select [ngModel]="moduleFilter()" (ngModelChange)="updateModuleFilter($event)">
            <option value="">Todos los módulos</option>
            <option *ngFor="let module of modules()" [value]="module">{{ module }}</option>
          </select>
        </label>
      </div>

      <div class="matrix-card" *ngIf="!loading() && !loadError()">
        <div class="matrix-scroll">
          <table class="matrix-table">
            <thead>
              <tr>
                <th class="col-perm">
                  <div class="th-perm-content">
                    <span class="th-icon"><ion-icon name="key-outline" /></span>
                    Permiso
                  </div>
                </th>
                <th *ngFor="let role of roles()" class="col-role">
                  <div class="role-header-content">
                    <div class="role-avatar">{{ role.nombre.charAt(0) }}</div>
                    <div class="role-info">
                      <span class="role-name">{{ role.nombre }}</span>
                      <span class="role-count">{{ roleAssignmentCount(role.id) }} permisos</span>
                    </div>
                  </div>
                </th>
              </tr>
            </thead>
            <tbody>
              <ng-container *ngFor="let group of groupedMatrixData()">
              <tr class="module-row">
                <td class="module-title" [attr.colspan]="roles().length + 1">
                  <div class="module-title-content">
                    <ion-icon name="key-outline" />
                    {{ group.modulo }}
                    <span>{{ group.rows.length }} permisos</span>
                  </div>
                </td>
              </tr>
              <tr *ngFor="let row of group.rows" class="matrix-row">
                <td class="col-perm">
                  <div class="perm-info">
                    <span class="perm-module">{{ row.permission.modulo }}</span>
                    <span class="perm-action">{{ row.permission.accion }}</span>
                    <span class="perm-resource">{{ row.permission.recurso }}</span>
                  </div>
                </td>
                <td *ngFor="let cell of row.cells" class="col-role">
                  <span class="matrix-check" [class.checked]="cell.checked" role="img"
                    [attr.aria-label]="row.permission.accion + ' para ' + cell.roleName + ': ' + (cell.checked ? 'concedido' : 'no concedido')">
                    <span class="check-visual">
                      <ion-icon
                        *ngIf="cell.checked"
                        name="checkmark-circle-outline"
                        class="check-icon"
                      />
                      <ion-icon
                        *ngIf="!cell.checked"
                        name="close-outline"
                        class="uncheck-icon"
                      />
                    </span>
                  </span>
                </td>
              </tr>
              </ng-container>
            </tbody>
          </table>
        </div>
        <div class="matrix-footer">
          <span *ngIf="filteredMatrixData().length">Mostrando {{ rangeStart() }}-{{ rangeEnd() }} de {{ filteredMatrixData().length }} permisos</span>
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
      max-width: 1200px;
      margin: 0 auto;
      font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
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
      gap: 16px;
    }

    .header-icon {
      width: 48px;
      height: 48px;
      background: linear-gradient(135deg, #f59e0b, #d97706);
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 24px;
      color: #fff;
      flex-shrink: 0;
    }

    .page-header h1 {
      font-size: 22px;
      font-weight: 700;
      color: #0f172a;
      margin: 0;
      letter-spacing: -0.3px;
    }

    .subtitle {
      font-size: 13px;
      color: #64748b;
      margin: 2px 0 0 0;
    }

    .btn-back {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      padding: 10px 18px;
      background: #fff;
      color: #475569;
      border: 1px solid #e2e8f0;
      border-radius: 10px;
      font-size: 14px;
      font-weight: 500;
      cursor: pointer;
      text-decoration: none;
      transition: all 0.2s;
    }

    .btn-back:hover {
      background: #f8fafc;
      border-color: #cbd5e1;
      color: #0f172a;
    }

    .btn-back ion-icon {
      font-size: 18px;
    }

    .governance-notice, .feedback-state {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-bottom: 16px;
      padding: 13px 16px;
      border: 1px solid var(--border-default, #dbe5f2);
      border-radius: 12px;
      color: var(--text-soft, #475569);
      background: var(--surface-panel, #fff);
    }
    .governance-notice > ion-icon { flex: 0 0 auto; color: var(--institutional-blue); font-size: 22px; }
    .governance-notice div { display: grid; gap: 2px; }
    .governance-notice strong { color: var(--text-strong, #0f172a); font-size: 13px; }
    .governance-notice span { font-size: 12px; line-height: 1.45; }
    .feedback-state { justify-content: space-between; }
    .feedback-state button { padding: 7px 12px; border: 1px solid var(--border-default, #dbe5f2); border-radius: 8px; color: var(--institutional-blue); background: var(--surface-panel, #fff); cursor: pointer; }
    .feedback-error { color: #b91c1c; border-color: #fecaca; background: #fef2f2; }

    .matrix-card {
      background: #fff;
      border: 1px solid #e2e8f0;
      border-radius: 12px;
      overflow: hidden;
      box-shadow: 0 1px 3px rgba(0,0,0,0.04);
    }

    .matrix-scroll {
      overflow-x: auto;
      overflow-y: auto;
      max-height: 65vh;
    }

    .matrix-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 14px;
      min-width: 600px;
    }

    .matrix-table thead {
      position: sticky;
      top: 0;
      z-index: 2;
    }

    .matrix-table thead tr {
      background: #1e293b;
    }

    .matrix-table th {
      padding: 14px 16px;
      text-align: left;
      font-weight: 600;
      color: #e2e8f0;
      border-bottom: 1px solid #334155;
      white-space: nowrap;
    }

    .col-perm {
      min-width: 240px;
      position: sticky;
      left: 0;
      z-index: 1;
      background: inherit;
    }

    thead .col-perm {
      z-index: 3;
    }

    .th-perm-content {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 12px;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      color: #94a3b8;
    }

    .th-icon ion-icon {
      font-size: 16px;
      color: #f59e0b;
    }

    .col-role {
      min-width: 140px;
      text-align: center;
    }

    .role-header-content {
      display: flex;
      align-items: center;
      gap: 10px;
      justify-content: center;
    }

    .role-avatar {
      width: 30px;
      height: 30px;
      border-radius: 8px;
      background: linear-gradient(135deg, #6366f1, #8b5cf6);
      color: #fff;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 13px;
      font-weight: 700;
      flex-shrink: 0;
    }

    .role-info {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      text-align: left;
    }

    .role-name {
      font-size: 13px;
      font-weight: 600;
      color: #f1f5f9;
    }

    .role-count {
      font-size: 11px;
      color: #64748b;
    }

    .matrix-row {
      transition: background 0.15s;
    }

    .matrix-row {
      background: var(--brand-soft);
    }

    .matrix-row:nth-child(even) {
      background: #e8f4fd;
    }

    .matrix-row:hover {
      background: var(--brand-soft);
    }

    .matrix-row td {
      padding: 12px 16px;
      border-bottom: 1px solid #f1f5f9;
      vertical-align: middle;
    }

    .matrix-row:last-child td {
      border-bottom: none;
    }

    .matrix-row .col-perm {
      background: var(--brand-soft);
    }

    .matrix-row:nth-child(even) .col-perm {
      background: #e8f4fd;
    }

    .matrix-row:hover .col-perm {
      background: var(--brand-soft);
    }

    .module-row td {
      padding: 10px 16px;
      background: #e2e8f0;
      border-top: 1px solid #cbd5e1;
      border-bottom: 1px solid #cbd5e1;
    }

    .module-title {
      color: #1e293b;
      font-size: 13px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: .4px;
    }

    .module-title-content {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .module-title-content ion-icon { color: #6366f1; font-size: 17px; }
    .module-title-content span {
      margin-left: 4px;
      padding: 2px 7px;
      border-radius: 999px;
      background: #cbd5e1;
      color: #475569;
      font-size: 11px;
      font-weight: 600;
      text-transform: none;
      letter-spacing: 0;
    }

    .matrix-row td.col-role {
      text-align: center;
    }

    .perm-info {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    .perm-module {
      display: inline-block;
      padding: 2px 8px;
      background: var(--brand-soft);
      color: var(--institutional-blue);
      border-radius: 4px;
      font-size: 11px;
      font-weight: 600;
      width: fit-content;
    }

    .perm-action {
      font-weight: 600;
      color: #0f172a;
      font-size: 13px;
    }

    .perm-resource {
      font-size: 12px;
      color: #94a3b8;
      font-family: 'SF Mono', 'Fira Code', monospace;
    }

    .matrix-check {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      cursor: default;
      user-select: none;
    }

    .matrix-check input {
      display: none;
    }

    .check-visual {
      width: 28px;
      height: 28px;
      border-radius: 6px;
      border: 2px solid #e2e8f0;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.2s;
    }

    .matrix-check.checked .check-visual {
      background: linear-gradient(135deg, #6366f1, #8b5cf6);
      border-color: #6366f1;
    }

    .check-icon {
      font-size: 16px;
      color: #fff;
    }

    .uncheck-icon {
      font-size: 16px;
      color: #cbd5e1;
    }

    .save-bar {
      display: flex;
      justify-content: center;
      margin-top: 20px;
    }

    .btn-save {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      padding: 12px 28px;
      background: linear-gradient(135deg, #6366f1, #8b5cf6);
      color: #fff;
      border: none;
      border-radius: 10px;
      font-size: 15px;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.2s;
      box-shadow: 0 2px 8px rgba(99, 102, 241, 0.3);
    }

    .btn-save:hover {
      transform: translateY(-1px);
      box-shadow: 0 4px 12px rgba(99, 102, 241, 0.4);
    }

    .btn-save:disabled {
      opacity: 0.6;
      cursor: not-allowed;
      transform: none;
    }

    .btn-save ion-icon {
      font-size: 18px;
    }

    .btn-save.loading {
      background: linear-gradient(135deg, #94a3b8, #64748b);
      box-shadow: none;
    }

    .summary-bar {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 24px;
      margin-top: 20px;
      padding: 16px 24px;
      background: #fff;
      border: 1px solid #e2e8f0;
      border-radius: 12px;
      box-shadow: 0 1px 3px rgba(0,0,0,0.04);
    }

    .summary-item {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 2px;
    }

    .summary-value {
      font-size: 22px;
      font-weight: 700;
      color: #0f172a;
      line-height: 1;
    }

    .summary-label {
      font-size: 12px;
      color: #64748b;
      font-weight: 500;
    }

    .summary-divider {
      width: 1px;
      height: 32px;
      background: #e2e8f0;
    }

    .btn-col-toggle, .btn-row-toggle {
      background: none;
      border: none;
      cursor: pointer;
      padding: 2px;
      display: inline-flex;
      align-items: center;
      opacity: 0.5;
      transition: opacity 0.2s;
    }

    .btn-col-toggle:hover, .btn-row-toggle:hover {
      opacity: 1;
    }

    .btn-col-toggle ion-icon, .btn-row-toggle ion-icon {
      font-size: 16px;
      color: #6366f1;
    }

    .btn-row-toggle {
      margin-left: 8px;
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
    .subtitle { margin-top: 5px; }
    .btn-back { min-height: 42px; padding: 0 16px; border-color: #d7e3f2; color: var(--institutional-blue); }
    .btn-back:hover { border-color: var(--institutional-blue); color: var(--institutional-blue); background: var(--brand-soft); }

    .summary-grid {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 12px;
      margin-bottom: 16px;
    }
    .summary-card {
      display: flex;
      align-items: center;
      gap: 12px;
      min-height: 76px;
      padding: 13px 16px;
      border: 1px solid #dbe5f2;
      border-radius: 14px;
      background: #fff;
      box-shadow: 0 5px 18px rgba(15, 23, 42, .045);
    }
    .summary-icon {
      display: grid;
      place-items: center;
      width: 42px;
      height: 42px;
      border-radius: 12px;
      color: var(--institutional-blue);
      background: var(--brand-soft);
      font-size: 20px;
      flex: 0 0 auto;
    }
    .roles-summary .summary-icon { color: #7c3aed; background: #f5f3ff; }
    .assignments-summary .summary-icon { color: #059669; background: #ecfdf5; }
    .summary-copy { display: grid; gap: 2px; }
    .summary-copy strong { color: #0f172a; font-size: 22px; line-height: 1; }
    .summary-copy small { color: #64748b; font-size: 12px; }

    .toolbar-card {
      display: grid;
      grid-template-columns: minmax(280px, 1fr) 240px;
      gap: 12px;
      align-items: end;
      margin-bottom: 14px;
      padding: 12px;
      border: 1px solid #dbe5f2;
      border-radius: 14px;
      background: #fff;
      box-shadow: 0 4px 14px rgba(15, 23, 42, .035);
    }
    .search-field {
      display: flex;
      align-items: center;
      gap: 10px;
      min-height: 44px;
      padding: 0 14px;
      border: 1px solid #dbe5f2;
      border-radius: 10px;
      background: #f8fafc;
    }
    .search-field:focus-within { border-color: var(--institutional-blue); box-shadow: 0 0 0 3px rgba(37, 99, 235, .1); }
    .search-field ion-icon { color: #94a3b8; font-size: 19px; }
    .search-field input { width: 100%; border: 0; outline: 0; color: #0f172a; background: transparent; font: 14px inherit; }
    .search-field input::placeholder { color: #94a3b8; }
    .filter-field { display: grid; gap: 4px; }
    .filter-field > span { padding-left: 2px; color: #64748b; font-size: 10px; font-weight: 800; letter-spacing: .06em; text-transform: uppercase; }
    .filter-field select {
      width: 100%;
      height: 44px;
      padding: 0 34px 0 12px;
      border: 1px solid #dbe5f2;
      border-radius: 10px;
      outline: 0;
      color: #334155;
      background: #fff;
      font: 500 13px inherit;
    }
    .filter-field select:focus { border-color: var(--institutional-blue); box-shadow: 0 0 0 3px rgba(37, 99, 235, .1); }

    .matrix-card { border-color: #dbe5f2; border-radius: 14px; box-shadow: 0 7px 22px rgba(15, 23, 42, .05); }
    .matrix-scroll { max-height: none; overflow-x: auto; overflow-y: visible; scrollbar-color: #9ab9e4 #eef4fb; scrollbar-width: thin; }
    .matrix-scroll::-webkit-scrollbar { height: 8px; }
    .matrix-scroll::-webkit-scrollbar-track { background: #eef4fb; }
    .matrix-scroll::-webkit-scrollbar-thumb { border: 2px solid #eef4fb; border-radius: 999px; background: #9ab9e4; }
    .matrix-table { min-width: 820px; }
    .matrix-table thead tr { background: #17345f; }
    .matrix-table th { height: 68px; padding: 10px 14px; color: #fff; border-bottom-color: #2b4c78; background: #17345f; }
    .matrix-table thead .col-perm { color: #fff; background: #17345f; }
    .th-perm-content { color: var(--brand-soft); }
    .th-icon ion-icon { color: var(--institutional-blue); }
    .col-perm { min-width: 260px; }
    .col-role { min-width: 175px; }
    .role-header-content { justify-content: flex-start; }
    .role-avatar { background: #2b5ca0; box-shadow: none; }
    .role-name { max-width: 100px; overflow: hidden; color: #fff; text-overflow: ellipsis; }
    .role-count { color: var(--brand-soft); }
    .btn-col-toggle { margin-left: auto; }
    .btn-col-toggle, .btn-row-toggle { opacity: 1; }
    .btn-col-toggle ion-icon { color: var(--brand-soft); font-size: 18px; }
    .btn-row-toggle ion-icon { color: var(--institutional-blue); font-size: 18px; }
    .matrix-row, .matrix-row:nth-child(even) { background: #fff; }
    .matrix-row:hover { background: #f8fbff; }
    .matrix-row .col-perm, .matrix-row:nth-child(even) .col-perm { background: #fff; }
    .matrix-row:hover .col-perm { background: #f8fbff; }
    .matrix-row td { height: 60px; padding: 10px 14px; border-bottom-color: #edf2f7; }
    .module-row td { padding: 10px 14px; border-color: var(--brand-soft); background: #eaf3ff; }
    .module-title { color: #1e3a5f; }
    .module-title-content ion-icon { color: var(--institutional-blue); }
    .module-title-content span { color: var(--institutional-blue); background: var(--brand-soft); }
    .perm-module { padding: 3px 8px; border-radius: 999px; color: var(--institutional-blue); background: var(--brand-soft); }
    .perm-resource { font-family: inherit; }
    .check-visual { width: 30px; height: 30px; border: 1px solid #dbe5f2; border-radius: 8px; background: #f8fafc; }
    .matrix-check.checked .check-visual { border-color: var(--institutional-blue); background: var(--institutional-blue); box-shadow: 0 4px 10px rgba(37, 99, 235, .2); }

    .matrix-footer {
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

    .save-bar {
      position: static;
      z-index: 8;
      justify-content: space-between;
      align-items: center;
      max-width: 560px;
      margin: 16px auto 0;
      padding: 10px 12px 10px 16px;
      border: 1px solid #dbe5f2;
      border-radius: 14px;
      background: rgba(255, 255, 255, .96);
      box-shadow: 0 12px 30px rgba(15, 23, 42, .13);
      backdrop-filter: blur(10px);
    }
    .change-status { display: flex; align-items: center; gap: 8px; color: #64748b; font-size: 12px; font-weight: 600; }
    .status-dot { width: 8px; height: 8px; border-radius: 50%; background: #10b981; box-shadow: 0 0 0 4px #d1fae5; }
    .change-status.pending { color: #b45309; }
    .change-status.pending .status-dot { background: #f59e0b; box-shadow: 0 0 0 4px #fef3c7; }
    .btn-save { padding: 11px 20px; border-radius: 10px; background: var(--institutional-blue); box-shadow: 0 5px 14px rgba(37, 99, 235, .24); }
    .btn-save:hover:not(:disabled) { background: var(--institutional-blue); box-shadow: 0 7px 18px rgba(37, 99, 235, .3); }
    .btn-save:disabled { background: #cbd5e1; box-shadow: none; }

    button:focus-visible, a:focus-visible, input:focus-visible, select:focus-visible { outline: 3px solid rgba(37, 99, 235, .24); outline-offset: 2px; }

    @media (max-width: 760px) {
      .page-header { display: grid; gap: 15px; }
      .btn-back { width: fit-content; }
      .summary-grid { grid-template-columns: 1fr; }
      .summary-card { min-height: 66px; }
      .toolbar-card { grid-template-columns: 1fr; }
      .col-perm { min-width: 190px; }
      .col-role { min-width: 125px; }
      .role-header-content { gap: 6px; }
      .role-avatar { width: 28px; height: 28px; }
      .role-name { max-width: 62px; font-size: 11px; }
      .role-count { font-size: 9px; }
      .save-bar { bottom: 8px; gap: 12px; }
      .matrix-footer { align-items: flex-start; gap: 10px; }
    }

    @media (max-width: 520px) {
      .save-bar { align-items: stretch; flex-direction: column; }
      .change-status { justify-content: center; }
      .btn-save { justify-content: center; }
      .matrix-footer { flex-direction: column; }
    }

    @media (prefers-reduced-motion: reduce) {
      *, *::before, *::after { transition-duration: .01ms !important; animation-duration: .01ms !important; scroll-behavior: auto !important; }
    }
  `],
})
export class PermissionMatrixComponent implements OnInit {
  protected roles = signal<ConfigRole[]>([]);
  protected permissions = signal<ConfigPermission[]>([]);
  protected selected = signal<Set<string>>(new Set());
  protected searchTerm = signal('');
  protected moduleFilter = signal('');
  protected currentPage = signal(1);
  protected readonly pageSize = 20;
  protected loading = signal(true);
  protected loadError = signal('');
  protected totalAssignments = computed(() => this.selected().size);
  protected modules = computed(() =>
    [...new Set(this.permissions().map(permission => permission.modulo?.trim()).filter((module): module is string => Boolean(module)))]
      .sort((a, b) => a.localeCompare(b, 'es'))
  );
  protected matrixData = computed<MatrixRow[]>(() => {
    const perms = this.permissions();
    const roles = this.roles();
    const selected = this.selected();
    return perms.map(perm => ({
      permission: perm,
      cells: roles.map(role => ({
        roleId: role.id,
        roleName: role.nombre,
        checked: selected.has(`${role.id}:${perm.id}`),
      })),
    }));
  });

  protected filteredMatrixData = computed<MatrixRow[]>(() => {
    const term = this.searchTerm().trim().toLowerCase();
    const module = this.moduleFilter();
    return this.matrixData().filter(row => {
      const permission = row.permission;
      const matchesModule = !module || permission.modulo === module;
      const matchesTerm = !term ||
        permission.modulo?.toLowerCase().includes(term) ||
        permission.accion.toLowerCase().includes(term) ||
        permission.recurso.toLowerCase().includes(term) ||
        permission.descripcion?.toLowerCase().includes(term);
      return matchesModule && matchesTerm;
    });
  });

  protected totalPages = computed(() => Math.max(1, Math.ceil(this.filteredMatrixData().length / this.pageSize)));
  protected pagedMatrixData = computed(() => {
    const start = (this.currentPage() - 1) * this.pageSize;
    return this.filteredMatrixData().slice(start, start + this.pageSize);
  });
  protected rangeStart = computed(() => this.filteredMatrixData().length
    ? (this.currentPage() - 1) * this.pageSize + 1
    : 0
  );
  protected rangeEnd = computed(() => Math.min(this.currentPage() * this.pageSize, this.filteredMatrixData().length));

  protected groupedMatrixData = computed<MatrixGroup[]>(() => {
    const groups = new Map<string, MatrixRow[]>();
    for (const row of this.pagedMatrixData()) {
      const modulo = row.permission.modulo?.trim() || 'Sin módulo';
      groups.set(modulo, [...(groups.get(modulo) ?? []), row]);
    }
    return Array.from(groups.entries())
      .sort(([a], [b]) => a.localeCompare(b, 'es'))
      .map(([modulo, rows]) => ({ modulo, rows }));
  });

  private dataService = inject(ConfigDataService);
  private destroyRef = inject(DestroyRef);

  constructor() {
    addIcons({
      keyOutline, checkmarkCircleOutline, closeOutline, arrowBackOutline,
      searchOutline, shieldCheckmarkOutline, peopleOutline,
      chevronBackOutline, chevronForwardOutline,
    });
  }

  ngOnInit() {
    this.loadMatrix();
  }

  protected loadMatrix(): void {
    this.loading.set(true);
    this.loadError.set('');
    forkJoin({
      roles: this.dataService.getRolesStrict(),
      permissions: this.dataService.getPermissionsStrict(),
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: ({ roles, permissions }) => {
        this.roles.set(roles);
        this.permissions.set(permissions);
        this.initSelected(roles);
        this.loading.set(false);
      },
      error: () => {
        this.loadError.set('No fue posible cargar la matriz de acceso.');
        this.loading.set(false);
      },
    });
  }

  private initSelected(roles: ConfigRole[]): void {
    const selected = new Set<string>();
    for (const role of roles) {
      if (role.permisoIds) {
        for (const permId of role.permisoIds) {
          selected.add(`${role.id}:${permId}`);
        }
      }
    }
    this.selected.set(new Set(selected));
  }

  updateSearch(value: string): void {
    this.searchTerm.set(value);
    this.currentPage.set(1);
  }

  updateModuleFilter(value: string): void {
    this.moduleFilter.set(value);
    this.currentPage.set(1);
  }

  previousPage(): void {
    this.currentPage.update(page => Math.max(1, page - 1));
  }

  nextPage(): void {
    this.currentPage.update(page => Math.min(this.totalPages(), page + 1));
  }

  roleAssignmentCount(roleId: string): number {
    const prefix = `${roleId}:`;
    let count = 0;
    for (const key of this.selected()) if (key.startsWith(prefix)) count++;
    return count;
  }

}
