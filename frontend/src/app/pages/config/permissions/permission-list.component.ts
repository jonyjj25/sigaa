import { Component, OnInit, signal, computed, inject, ChangeDetectionStrategy, DestroyRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { IonIcon } from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import {
  keyOutline, searchOutline, gridOutline, checkmarkCircle, closeCircle,
  shieldCheckmarkOutline, chevronBackOutline, chevronForwardOutline,
} from 'ionicons/icons';
import { ConfigDataService } from '../services/config-data.service';
import { ConfigPermission } from '../models/config.models';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

@Component({
  selector: 'app-permission-list',
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
            <h1>Catálogo técnico de permisos</h1>
            <p class="subtitle">Consulta las acciones técnicas registradas para los módulos del sistema.</p>
          </div>
        </div>
        <div class="header-actions">
          <a class="btn-primary" routerLink="/config/permissions/matrix">
            <ion-icon name="grid-outline" />
            Consultar matriz
          </a>
        </div>
      </div>

      <section class="governance-notice" role="note">
        <ion-icon name="shield-checkmark-outline" />
        <div>
          <strong>Catálogo de solo lectura</strong>
          <span>Los permisos se incorporan mediante migraciones versionadas y revisión técnica. Desde aquí no se crean, editan ni eliminan.</span>
        </div>
      </section>

      <div *ngIf="loading()" class="feedback-state">Cargando catálogo de permisos...</div>
      <div *ngIf="loadError()" class="feedback-state feedback-error" role="alert">
        <span>{{ loadError() }}</span>
        <button type="button" (click)="loadPermissions()">Reintentar</button>
      </div>

      <section class="summary-grid" aria-label="Resumen de permisos" *ngIf="!loading() && !loadError()">
        <article class="summary-card total">
          <span class="summary-icon"><ion-icon name="key-outline" /></span>
          <span class="summary-copy"><strong>{{ permissions().length }}</strong><small>Permisos registrados</small></span>
        </article>
        <article class="summary-card active">
          <span class="summary-icon"><ion-icon name="checkmark-circle" /></span>
          <span class="summary-copy"><strong>{{ activeCount() }}</strong><small>Permisos activos</small></span>
        </article>
        <article class="summary-card modules">
          <span class="summary-icon"><ion-icon name="shield-checkmark-outline" /></span>
          <span class="summary-copy"><strong>{{ modules().length }}</strong><small>Módulos configurados</small></span>
        </article>
      </section>

      <div class="toolbar-card" *ngIf="!loading() && !loadError()">
        <label class="search-bar">
          <ion-icon name="search-outline" class="search-icon" />
          <input
            type="search"
            aria-label="Buscar permisos"
            placeholder="Buscar por módulo, acción, recurso o descripción..."
            [ngModel]="searchTerm()"
            (ngModelChange)="updateSearch($event)"
          />
        </label>
        <div class="filters">
          <label class="filter-field">
            <span>Módulo</span>
            <select [ngModel]="moduleFilter()" (ngModelChange)="updateModuleFilter($event)">
              <option value="">Todos los módulos</option>
              <option *ngFor="let module of modules()" [value]="module">{{ module }}</option>
            </select>
          </label>
          <label class="filter-field status-filter">
            <span>Estado</span>
            <select [ngModel]="statusFilter()" (ngModelChange)="updateStatusFilter($event)">
              <option value="">Todos</option>
              <option value="active">Activos</option>
              <option value="inactive">Inactivos</option>
            </select>
          </label>
        </div>
      </div>

      <div class="table-card" *ngIf="!loading() && !loadError()">
        <div class="table-wrapper">
          <table class="perm-table">
            <thead>
              <tr>
                <th>Módulo</th>
                <th>Acción</th>
                <th>Recurso</th>
                <th>Descripción</th>
                <th class="col-status">Estado</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let perm of pagedPermissions()" class="perm-row">
                <td data-label="Módulo">
                  <span class="module-tag">{{ perm.modulo }}</span>
                </td>
                <td data-label="Acción" class="cell-action">{{ perm.accion }}</td>
                <td data-label="Recurso" class="cell-resource">{{ perm.recurso }}</td>
                <td data-label="Descripción" class="cell-desc">{{ perm.descripcion }}</td>
                <td data-label="Estado" class="col-status">
                  <span class="status-pill" [class.active]="perm.estado" [class.inactive]="!perm.estado">
                    <ion-icon [name]="perm.estado ? 'checkmark-circle' : 'close-circle'" />
                    {{ perm.estado ? 'Activo' : 'Inactivo' }}
                  </span>
                </td>
              </tr>
              <tr *ngIf="filteredPermissions().length === 0">
                <td colspan="5" class="empty-state">
                  <div class="empty-content">
                    <ion-icon name="search-outline" />
                    <p>No se encontraron permisos</p>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="table-footer">
          <span class="footer-count" *ngIf="filteredPermissions().length">
            Mostrando {{ rangeStart() }}-{{ rangeEnd() }} de {{ filteredPermissions().length }}
          </span>
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

    .btn-primary {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      padding: 10px 20px;
      background: linear-gradient(135deg, #6366f1, #8b5cf6);
      color: #fff;
      border: none;
      border-radius: 10px;
      font-size: 14px;
      font-weight: 600;
      cursor: pointer;
      text-decoration: none;
      transition: all 0.25s;
      box-shadow: 0 4px 14px rgba(99, 102, 241, 0.3);
    }

    .btn-primary:hover {
      transform: translateY(-1px);
      box-shadow: 0 6px 20px rgba(99, 102, 241, 0.4);
    }

    .btn-primary ion-icon {
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

    .search-bar {
      display: flex;
      align-items: center;
      gap: 12px;
      background: #fff;
      border: 1px solid #e2e8f0;
      border-radius: 10px;
      padding: 0 16px;
      margin-bottom: 20px;
      transition: border-color 0.2s, box-shadow 0.2s;
    }

    .search-bar:focus-within {
      border-color: #6366f1;
      box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
    }

    .search-icon {
      font-size: 20px;
      color: #94a3b8;
      flex-shrink: 0;
    }

    .search-bar input {
      flex: 1;
      border: none;
      outline: none;
      padding: 14px 0;
      font-size: 14px;
      color: #0f172a;
      background: transparent;
      font-family: inherit;
    }

    .search-bar input::placeholder {
      color: #94a3b8;
    }

    .table-card {
      background: #fff;
      border: 1px solid #e2e8f0;
      border-radius: 12px;
      overflow: hidden;
      box-shadow: 0 1px 3px rgba(0,0,0,0.04);
    }

    .table-wrapper {
      overflow-x: auto;
    }

    .perm-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 14px;
    }

    .perm-table thead {
      background: #f8fafc;
      border-bottom: 1px solid #e2e8f0;
    }

    .perm-table th {
      padding: 12px 16px;
      text-align: left;
      font-weight: 600;
      font-size: 12px;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      color: #64748b;
      white-space: nowrap;
    }

    .perm-table td {
      padding: 14px 16px;
      color: #334155;
      border-bottom: 1px solid #f1f5f9;
      vertical-align: middle;
    }

    .perm-row:last-child td {
      border-bottom: none;
    }

    .perm-row:hover {
      background: #f8fafc;
    }

    .col-status {
      width: 140px;
      text-align: center;
    }

    .module-tag {
      display: inline-block;
      padding: 3px 10px;
      background: var(--brand-soft);
      color: var(--institutional-blue);
      border-radius: 6px;
      font-size: 12px;
      font-weight: 600;
    }

    .cell-action {
      font-weight: 600;
      color: #0f172a;
    }

    .cell-resource {
      color: #64748b;
      font-family: 'SF Mono', 'Fira Code', monospace;
      font-size: 13px;
    }

    .cell-desc {
      color: #475569;
      max-width: 280px;
    }

    .toggle-switch {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      cursor: pointer;
      user-select: none;
    }

    .toggle-switch input {
      display: none;
    }

    .toggle-track {
      position: relative;
      width: 40px;
      height: 22px;
      background: #cbd5e1;
      border-radius: 11px;
      transition: background 0.25s;
      flex-shrink: 0;
    }

    .toggle-switch.active .toggle-track {
      background: linear-gradient(135deg, #22c55e, #16a34a);
    }

    .toggle-thumb {
      position: absolute;
      top: 2px;
      left: 2px;
      width: 18px;
      height: 18px;
      background: #fff;
      border-radius: 50%;
      transition: transform 0.25s;
      box-shadow: 0 1px 3px rgba(0,0,0,0.2);
    }

    .toggle-switch.active .toggle-thumb {
      transform: translateX(18px);
    }

    .toggle-label {
      font-size: 12px;
      font-weight: 500;
      color: #94a3b8;
      transition: color 0.25s;
      min-width: 48px;
    }

    .toggle-switch.active .toggle-label {
      color: #16a34a;
    }

    .empty-state {
      text-align: center;
      padding: 48px 16px !important;
    }

    .empty-content {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 8px;
      color: #94a3b8;
    }

    .empty-content ion-icon {
      font-size: 36px;
    }

    .empty-content p {
      margin: 0;
      font-size: 14px;
    }

    .table-footer {
      display: flex;
      justify-content: flex-end;
      padding: 10px 16px;
      border-top: 1px solid #f1f5f9;
      background: #fafafa;
    }

    .footer-count {
      font-size: 12px;
      color: #94a3b8;
      font-weight: 500;
    }

    .header-actions {
      display: flex;
      gap: 10px;
      align-items: center;
    }

    .btn-create {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 10px 18px;
      background: #fff;
      color: #059669;
      border: 1px solid #059669;
      border-radius: 10px;
      font-size: 14px;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.2s;
    }

    .btn-create:hover {
      background: #059669;
      color: #fff;
    }

    .btn-create ion-icon { font-size: 18px; }

    .col-actions {
      width: 112px;
      min-width: 112px;
      text-align: center;
      white-space: nowrap;
    }

    .btn-icon {
      background: none;
      border: none;
      cursor: pointer;
      padding: 4px 6px;
      border-radius: 6px;
      transition: background 0.2s;
    }

    .btn-icon:hover { background: #f1f5f9; }

    .btn-edit ion-icon { font-size: 18px; color: var(--institutional-blue); }
    .btn-delete ion-icon { font-size: 18px; color: #ef4444; }

    .form-card {
      background: #fff;
      border: 1px solid #e2e8f0;
      border-radius: 12px;
      padding: 20px;
      margin-bottom: 20px;
    }

    .form-card h3 {
      font-size: 16px;
      font-weight: 600;
      color: #0f172a;
      margin: 0 0 16px 0;
    }

    .form-grid {
      display: grid;
      grid-template-columns: 1fr 1fr 1fr;
      gap: 14px;
    }

    .form-field {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .form-field.full-width {
      grid-column: 1 / -1;
    }

    .form-field label {
      font-size: 12px;
      font-weight: 600;
      color: #64748b;
      text-transform: uppercase;
      letter-spacing: 0.3px;
    }

    .form-field input {
      padding: 10px 12px;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      font-size: 14px;
      color: #0f172a;
      outline: none;
      transition: border-color 0.2s;
    }

    .form-field input:focus {
      border-color: #6366f1;
      box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
    }

    .form-actions {
      display: flex;
      gap: 10px;
      margin-top: 16px;
    }

    .btn-save {
      padding: 10px 20px;
      background: linear-gradient(135deg, #6366f1, #8b5cf6);
      color: #fff;
      border: none;
      border-radius: 8px;
      font-size: 14px;
      font-weight: 600;
      cursor: pointer;
    }

    .btn-save:hover { opacity: 0.9; }
    .btn-save:disabled { cursor: not-allowed; opacity: .45; box-shadow: none; }

    .btn-cancel {
      padding: 10px 20px;
      background: #f1f5f9;
      color: #475569;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      font-size: 14px;
      font-weight: 500;
      cursor: pointer;
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
    .eyebrow, .form-kicker {
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
    .btn-primary, .btn-create { min-height: 42px; padding: 0 17px; border-radius: 11px; }
    .btn-primary {
      background: var(--institutional-blue);
      box-shadow: 0 7px 16px rgba(29, 78, 216, .22);
    }
    .btn-primary:hover { background: var(--institutional-blue); box-shadow: 0 9px 20px rgba(29, 78, 216, .28); }
    .btn-create { color: var(--institutional-blue); border-color: var(--brand-soft); background: var(--brand-soft); }
    .btn-create:hover { color: #fff; border-color: var(--institutional-blue); background: var(--institutional-blue); }

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
    .summary-card.active .summary-icon { color: #059669; background: #ecfdf5; }
    .summary-card.modules .summary-icon { color: #7c3aed; background: #f5f3ff; }
    .summary-copy { display: grid; gap: 2px; }
    .summary-copy strong { color: #0f172a; font-size: 22px; line-height: 1; }
    .summary-copy small { color: #64748b; font-size: 12px; }

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
      min-width: 180px;
      height: 44px;
      padding: 0 34px 0 12px;
      border: 1px solid #dbe5f2;
      border-radius: 10px;
      outline: none;
      color: #334155;
      background: #fff;
      font: 500 13px inherit;
    }
    .filter-field select:focus { border-color: var(--institutional-blue); box-shadow: 0 0 0 3px rgba(37, 99, 235, .11); }
    .status-filter select { min-width: 130px; }

    .form-card {
      padding: 0;
      overflow: hidden;
      border-color: #cfe0f5;
      border-radius: 15px;
      box-shadow: 0 10px 28px rgba(15, 23, 42, .07);
    }
    .form-heading {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 16px 20px;
      border-bottom: 1px solid #e5edf7;
      background: linear-gradient(90deg, #f8fbff, #fff);
    }
    .form-card h3 { margin: 0; font-size: 17px; }
    .btn-close {
      display: grid;
      place-items: center;
      width: 34px;
      height: 34px;
      border: 0;
      border-radius: 9px;
      color: #64748b;
      background: transparent;
      cursor: pointer;
      font-size: 20px;
    }
    .btn-close:hover { color: #0f172a; background: #eaf1fa; }
    .form-grid { padding: 18px 20px 0; }
    .form-actions { padding: 0 20px 18px; }
    .form-field input { min-height: 42px; background: #fbfdff; }
    .btn-save { background: var(--institutional-blue); box-shadow: 0 5px 13px rgba(37, 99, 235, .2); }

    .table-card { border-color: #dbe5f2; border-radius: 14px; box-shadow: 0 7px 22px rgba(15, 23, 42, .05); }
    .perm-table thead { background: var(--brand-soft); border-bottom-color: var(--brand-soft); }
    .perm-table th { padding: 13px 16px; color: #52657e; font-size: 11px; }
    .perm-table td { height: 58px; padding: 11px 16px; }
    .perm-row:hover { background: #f8fbff; }
    .module-tag { padding: 5px 10px; border-radius: 999px; color: var(--institutional-blue); background: var(--brand-soft); }
    .cell-resource { color: #64748b; font-family: inherit; font-size: 13px; }
    .status-pill { display: inline-flex; align-items: center; gap: 6px; padding: 5px 9px; border-radius: 999px; font-size: 12px; font-weight: 700; }
    .status-pill.active { color: #047857; background: #ecfdf5; }
    .status-pill.inactive { color: #b91c1c; background: #fef2f2; }
    .toggle-track { width: 38px; height: 21px; background: #cbd5e1; }
    .toggle-thumb { width: 17px; height: 17px; }
    .toggle-switch.active .toggle-track { background: #10b981; }
    .toggle-switch.active .toggle-thumb { transform: translateX(17px); }
    .toggle-switch input:focus-visible + .toggle-track { outline: 3px solid rgba(37, 99, 235, .25); outline-offset: 2px; }
    .btn-icon {
      display: inline-grid;
      place-items: center;
      width: 34px;
      height: 34px;
      margin: 0 2px;
      padding: 0;
      border: 1px solid #dbe5f2;
      background: #fff;
    }
    .btn-edit:hover { border-color: var(--brand-soft); background: var(--brand-soft); }
    .btn-delete:hover { border-color: #fecaca; background: #fef2f2; }
    .table-footer { align-items: center; justify-content: space-between; min-height: 54px; padding: 9px 14px; background: #f8fafc; }
    .footer-count { color: #64748b; }
    .pagination { display: flex; align-items: center; gap: 10px; color: #64748b; font-size: 12px; }
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

    button:focus-visible, a:focus-visible, input:focus-visible, select:focus-visible {
      outline: 3px solid rgba(37, 99, 235, .25);
      outline-offset: 2px;
    }

    @media (max-width: 900px) {
      .page-header { align-items: flex-start; }
      .summary-grid { grid-template-columns: repeat(3, 1fr); }
      .toolbar-card { grid-template-columns: 1fr; }
      .filters { display: grid; grid-template-columns: 1fr 1fr; }
      .filter-field select { width: 100%; min-width: 0; }
      .form-grid { grid-template-columns: 1fr 1fr; }
      .form-field.full-width { grid-column: 1 / -1; }
    }

    @media (max-width: 680px) {
      .page-header { display: grid; gap: 16px; }
      .header-actions { width: 100%; }
      .header-actions > * { flex: 1; justify-content: center; }
      .summary-grid { grid-template-columns: 1fr; }
      .summary-card { min-height: 66px; }
      .filters, .form-grid { grid-template-columns: 1fr; }
      .form-field.full-width { grid-column: auto; }
      .table-card { border: 0; box-shadow: none; background: transparent; overflow: visible; }
      .table-wrapper { overflow: visible; }
      .perm-table, .perm-table tbody { display: block; }
      .perm-table thead { display: none; }
      .perm-table tr.perm-row {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 10px 14px;
        margin-bottom: 10px;
        padding: 14px;
        border: 1px solid #dbe5f2;
        border-radius: 13px;
        background: #fff;
        box-shadow: 0 4px 14px rgba(15, 23, 42, .045);
      }
      .perm-table td { display: grid; gap: 4px; height: auto; padding: 0; border: 0; }
      .perm-table td::before { content: attr(data-label); color: #94a3b8; font-size: 9px; font-weight: 800; letter-spacing: .08em; text-transform: uppercase; }
      .perm-table .cell-desc { grid-column: 1 / -1; max-width: none; }
      .perm-table .col-status, .perm-table .col-actions { width: auto; text-align: left; }
      .perm-table .col-actions { min-width: 0; white-space: normal; }
      .toggle-switch { justify-content: flex-start; }
      .empty-state { display: block !important; }
      .empty-state::before { display: none; }
      .table-footer { border: 1px solid #dbe5f2; border-radius: 12px; }
    }

    @media (prefers-reduced-motion: reduce) {
      *, *::before, *::after { scroll-behavior: auto !important; transition-duration: .01ms !important; animation-duration: .01ms !important; }
    }
  `],
})
export class PermissionListComponent implements OnInit {
  protected permissions = signal<ConfigPermission[]>([]);
  protected searchTerm = signal('');
  protected moduleFilter = signal('');
  protected statusFilter = signal('');
  protected currentPage = signal(1);
  protected readonly pageSize = 15;
  protected loading = signal(true);
  protected loadError = signal('');
  protected activeCount = computed(() => this.permissions().filter(permission => permission.estado).length);
  protected modules = computed(() =>
    [...new Set(this.permissions().map(permission => permission.modulo).filter(Boolean))]
      .sort((a, b) => a.localeCompare(b, 'es'))
  );
  protected filteredPermissions = computed(() => {
    const term = this.searchTerm().toLowerCase().trim();
    const module = this.moduleFilter();
    const status = this.statusFilter();
    return this.permissions().filter(permission => {
      const matchesTerm = !term ||
        permission.modulo.toLowerCase().includes(term) ||
        permission.accion.toLowerCase().includes(term) ||
        permission.recurso.toLowerCase().includes(term) ||
        permission.descripcion.toLowerCase().includes(term);
      const matchesModule = !module || permission.modulo === module;
      const matchesStatus = !status || (status === 'active' ? permission.estado : !permission.estado);
      return matchesTerm && matchesModule && matchesStatus;
    });
  });
  protected totalPages = computed(() => Math.max(1, Math.ceil(this.filteredPermissions().length / this.pageSize)));
  protected pagedPermissions = computed(() => {
    const start = (this.currentPage() - 1) * this.pageSize;
    return this.filteredPermissions().slice(start, start + this.pageSize);
  });
  protected rangeStart = computed(() => this.filteredPermissions().length
    ? (this.currentPage() - 1) * this.pageSize + 1
    : 0
  );
  protected rangeEnd = computed(() => Math.min(this.currentPage() * this.pageSize, this.filteredPermissions().length));

  private dataService = inject(ConfigDataService);
  private destroyRef = inject(DestroyRef);

  constructor() {
    addIcons({
      keyOutline, searchOutline, gridOutline, checkmarkCircle, closeCircle,
      shieldCheckmarkOutline, chevronBackOutline, chevronForwardOutline,
    });
  }

  ngOnInit() {
    this.loadPermissions();
  }

  protected loadPermissions(): void {
    this.loading.set(true);
    this.loadError.set('');
    this.dataService.getPermissionsStrict().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: permissions => {
        this.permissions.set(permissions);
        this.loading.set(false);
      },
      error: () => {
        this.loadError.set('No fue posible cargar el catálogo de permisos.');
        this.loading.set(false);
      },
    });
  }

  updateSearch(value: string): void {
    this.searchTerm.set(value);
    this.currentPage.set(1);
  }

  updateModuleFilter(value: string): void {
    this.moduleFilter.set(value);
    this.currentPage.set(1);
  }

  updateStatusFilter(value: string): void {
    this.statusFilter.set(value);
    this.currentPage.set(1);
  }

  previousPage(): void {
    this.currentPage.update(page => Math.max(1, page - 1));
  }

  nextPage(): void {
    this.currentPage.update(page => Math.min(this.totalPages(), page + 1));
  }

}
