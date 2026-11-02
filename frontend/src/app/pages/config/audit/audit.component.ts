import { FileActionsService } from '../../../services/file-actions.service';
import { Component, ChangeDetectionStrategy, signal, computed, inject, effect, DestroyRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IonIcon } from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { documentTextOutline, searchOutline, downloadOutline, copyOutline, chevronBackOutline, chevronForwardOutline } from 'ionicons/icons';
import { ConfigDataService } from '../services/config-data.service';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuditLog } from '../models/config.models';

@Component({
  selector: 'app-audit',
  standalone: true,
  imports: [CommonModule, IonIcon],
  template: `
    <div class="page-container">
      <div class="page-header">
        <div><span class="eyebrow">Seguridad y trazabilidad</span><h1>Auditoría</h1><p>Consulta la actividad registrada en el sistema.</p></div>
        <button class="export-button" type="button" (click)="exportCsv()"><ion-icon name="download-outline" />Exportar página</button>
      </div>

      <section class="summary-grid" aria-label="Resumen de auditoría">
        <div class="summary-card"><span>Eventos registrados</span><strong>{{ summary().total }}</strong></div>
        <div class="summary-card"><span>Eventos de hoy</span><strong>{{ summary().hoy }}</strong></div>
        <div class="summary-card critical"><span>Eventos críticos</span><strong>{{ summary().criticos }}</strong></div>
        <div class="summary-card"><span>Usuarios involucrados</span><strong>{{ summary().usuarios }}</strong></div>
      </section>

      <div class="filters-bar">
        <div class="search-wrapper">
          <ion-icon name="search-outline" class="search-icon" />
          <input class="search-input" type="text" aria-label="Buscar en todos los registros de auditoría" placeholder="Buscar en todos los registros..." [value]="searchTerm()" (input)="searchTerm.set($any($event.target).value)" />
        </div>
        <select class="select-input" aria-label="Filtrar por acción" [value]="actionFilter()" (change)="actionFilter.set($any($event.target).value)"><option value="">Todas las acciones</option><option *ngFor="let action of actions()" [value]="action">{{ formatAction(action) }}</option></select>
        <select class="select-input" aria-label="Filtrar por entidad" [value]="entityFilter()" (change)="entityFilter.set($any($event.target).value)"><option value="">Todas las entidades</option><option *ngFor="let entity of entities()" [value]="entity">{{ entity }}</option></select>
        <div class="date-filters">
          <div class="date-field">
            <span class="date-label">Desde</span>
            <input type="date" class="date-input" aria-label="Fecha inicial" [value]="dateFrom()" (change)="dateFrom.set($any($event.target).value)" />
          </div>
          <div class="date-field">
            <span class="date-label">Hasta</span>
            <input type="date" class="date-input" aria-label="Fecha final" [value]="dateTo()" (change)="dateTo.set($any($event.target).value)" />
          </div>
        </div>
      </div>

      <div class="audit-list">
        <div class="audit-columns" aria-hidden="true">
          <span>Fecha</span><span>Usuario</span><span>Acción</span><span>Entidad</span><span>Detalle</span><span>Dirección IP</span>
        </div>
        <article class="audit-record" *ngFor="let log of logs()" [class.is-critical]="isCritical(log)">
          <div class="audit-field date-cell" data-label="Fecha">{{ log.fecha | date:'dd/MM/yyyy HH:mm' }}</div>
          <div class="audit-field user-cell" data-label="Usuario">{{ log.usuario }}</div>
          <div class="audit-field" data-label="Acción"><span class="action-badge">{{ formatAction(log.accion) }}</span></div>
          <div class="audit-field entity-cell" data-label="Entidad">{{ log.entidad }}</div>
          <div class="audit-field detail-cell" data-label="Detalle">
            <button type="button" class="detail-button" (click)="toggleDetails(log.id)" [attr.aria-expanded]="expandedLog() === log.id">
              {{ expandedLog() === log.id ? 'Ocultar detalle' : 'Ver detalle' }}
            </button>
          </div>
          <div class="audit-field ip-cell" data-label="Dirección IP"><code>{{ log.direccionIp }}</code><button type="button" class="copy-button" (click)="copyIp(log.direccionIp)" aria-label="Copiar dirección IP" title="Copiar dirección IP"><ion-icon name="copy-outline" /></button></div>
          <div class="record-detail" *ngIf="expandedLog() === log.id">
            <strong>Detalle del registro</strong>
            <p>{{ log.detalle || 'Sin detalle adicional' }}</p>
          </div>
        </article>
        <div *ngIf="loadError()" class="empty-cell error-cell">No se pudo cargar la auditoría. <button type="button" (click)="retryLoad()">Reintentar</button></div>
        <div *ngIf="!loadError() && logs().length === 0" class="empty-cell">No se encontraron registros de auditoría</div>
      </div>
      <div class="pagination" *ngIf="totalElements() > 0"><span>Mostrando {{ pageStart() }}–{{ pageEnd() }} de {{ totalElements() }}</span><div><button type="button" aria-label="Página anterior de auditoría" (click)="previousPage()" [disabled]="currentPage() === 1" title="Página anterior de auditoría"><ion-icon name="chevron-back-outline" aria-hidden="true" /></button><span>{{ currentPage() }} / {{ pageCount() }}</span><button type="button" aria-label="Página siguiente de auditoría" (click)="nextPage()" [disabled]="currentPage() === pageCount()" title="Página siguiente de auditoría"><ion-icon name="chevron-forward-outline" aria-hidden="true" /></button></div></div>
    </div>
  `,
  styles: [`
    .page-container { padding: 24px; max-width: 1200px; margin: 0 auto; }
    .page-header { display:flex; justify-content:space-between; align-items:flex-start; gap:16px; margin-bottom:20px; }
    .page-header h1 { font-size: 24px; font-weight: 700; color: #0f172a; margin: 0; }
    .page-header p { margin:5px 0 0; color:#64748b; font-size:14px; }
    .eyebrow { display:block; margin-bottom:4px; color:var(--institutional-blue); font-size:10px; font-weight:800; letter-spacing:.1em; text-transform:uppercase; }
    .export-button { display:inline-flex; align-items:center; gap:7px; padding:10px 13px; border:1px solid var(--brand-soft); border-radius:9px; color:var(--institutional-blue); background:var(--brand-soft); font:600 13px inherit; cursor:pointer; }
    .summary-grid { display:grid; grid-template-columns:repeat(4,1fr); gap:12px; margin-bottom:18px; }
    .summary-card { padding:13px 15px; border:1px solid var(--brand-soft); border-radius:12px; background:#fff; }
    .summary-card span { display:block; color:#64748b; font-size:11px; font-weight:700; }
    .summary-card strong { display:block; margin-top:5px; color:#0f172a; font-size:22px; }
    .summary-card.critical { border-color:#fecaca; background:#fffafa; }.summary-card.critical strong { color:#dc2626; }
    .filters-bar { display: flex; align-items: flex-end; gap: 16px; margin-bottom: 20px; flex-wrap: wrap; }
    .search-wrapper { position: relative; flex: 1; min-width: 200px; }
    .search-icon { position: absolute; left: 14px; top: 50%; transform: translateY(-50%); font-size: 18px; color: #94a3b8; pointer-events: none; }
    .search-input { width: 100%; padding: 10px 14px 10px 42px; border: 1px solid #e2e8f0; border-radius: 8px; font-size: 14px; color: #334155; background: #fff; outline: none; transition: border-color 0.2s; }
    .search-input:focus { border-color: var(--institutional-blue); box-shadow: 0 0 0 3px rgba(59,130,246,0.1); }
    .date-filters { display: flex; gap: 12px; }
    .date-field { display: flex; flex-direction: column; gap: 4px; }
    .date-label { font-size: 11px; font-weight: 600; color: #64748b; text-transform: uppercase; letter-spacing: 0.5px; }
    .date-input { padding: 8px 12px; border: 1px solid #e2e8f0; border-radius: 8px; font-size: 13px; color: #334155; background: #fff; outline: none; }
    .date-input:focus { border-color: var(--institutional-blue); }
    .select-input { min-height:39px; padding:8px 30px 8px 10px; border:1px solid #e2e8f0; border-radius:8px; color:#334155; background:#fff; font-size:13px; }
    .audit-list { container-type: inline-size; overflow: hidden; border: 1px solid #e2e8f0; border-radius: 14px; background: #fff; box-shadow: 0 8px 24px rgba(15,23,42,.06); }
    .audit-columns, .audit-record { display: grid; grid-template-columns: 1.2fr 1.2fr minmax(130px, 1.5fr) .8fr minmax(150px, 1.45fr) minmax(130px, 1fr); gap: 14px; align-items: center; }
    .audit-columns { padding: 13px 18px; color: #64748b; font-size: 10px; font-weight: 800; letter-spacing: .08em; text-transform: uppercase; background: #f8fafc; border-bottom: 1px solid #e2e8f0; }
    .audit-record { min-width: 0; padding: 14px 18px; border-bottom: 1px solid #eef2f7; transition: background .15s ease; }
    .audit-record:last-of-type { border-bottom: 0; }
    .audit-record:hover { background: #f8fbff; }
    .audit-field { min-width: 0; color: #334155; font-size: 14px; overflow-wrap: anywhere; }
    .date-cell { color: #475569; font-size: 13px; font-variant-numeric: tabular-nums; }
    .user-cell { color: #1e3a5f; font-weight: 600; }
    .entity-cell { color: #475569; }
    .action-badge { display: inline-block; max-width: 100%; padding: 4px 9px; border-radius: 999px; font-size: 11px; font-weight: 700; line-height: 1.3; overflow-wrap: anywhere; background: var(--brand-soft); color: var(--institutional-blue); }
    .detail-cell { color: #64748b; }
    .ip-cell code { display: inline-block; max-width: 100%; padding: 4px 7px; overflow-wrap: anywhere; vertical-align: middle; }
    .copy-button { display:inline-grid; place-items:center; width:27px; height:27px; margin-left:5px; border:0; border-radius:6px; color:#64748b; background:#f1f5f9; cursor:pointer; vertical-align:middle; }
    .is-critical { border-left:3px solid #ef4444; }
    .record-detail { grid-column:1 / -1; padding:12px 14px; border:1px solid var(--brand-soft); border-radius:9px; color:#475569; background:#f8fbff; font-size:13px; }
    .record-detail strong { display:block; margin-bottom:5px; color:#1e3a5f; font-size:12px; }
    .record-detail p { margin:0; white-space:pre-wrap; overflow-wrap:anywhere; line-height:1.5; }
    .detail-button { padding:5px 9px; border:1px solid var(--brand-soft); border-radius:7px; color:var(--institutional-blue); background:var(--brand-soft); font:600 12px inherit; cursor:pointer; white-space:nowrap; }
    code { background: #f1f5f9; padding: 3px 6px; border-radius: 4px; font-size: 12px; color: #475569; font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; }
    .empty-cell { text-align: center; color: #94a3b8; padding: 40px 20px; font-size: 14px; }
    .error-cell { color:#b91c1c; background:#fff7f7; }.error-cell button { margin-left:8px; padding:6px 10px; border:1px solid #fecaca; border-radius:7px; color:#b91c1c; background:#fff; cursor:pointer; }
    .pagination { display:flex; align-items:center; justify-content:space-between; padding:14px 4px; color:var(--text-soft); font-size:12px; }.pagination div { display:flex; align-items:center; gap:9px; }.pagination button { display:grid; place-items:center; width:31px; height:31px; border:1px solid var(--control-border); border-radius:7px; color:var(--text-link); background:var(--control-bg); cursor:pointer; }.pagination button:disabled { opacity:.4; cursor:not-allowed; }
    @container (max-width: 1000px) {
      .audit-columns { display: none; }
      .audit-record { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px 24px; margin: 10px; padding: 16px; border: 1px solid #e2e8f0; border-radius: 12px; }
      .audit-record:last-of-type { border-bottom: 1px solid #e2e8f0; }
      .audit-field::before { content: attr(data-label); display: block; margin-bottom: 4px; color: #64748b; font-size: 10px; font-weight: 800; letter-spacing: .07em; text-transform: uppercase; }
      .detail-cell, .ip-cell { grid-column: 1 / -1; }
    }
    @media (max-width: 700px) {
      .page-container { padding: 16px; }
      .page-header, .filters-bar, .date-filters { align-items: stretch; flex-direction: column; }
      .summary-grid { grid-template-columns:repeat(2,1fr); }
    }
    @media (max-width: 520px) {
      .audit-record { grid-template-columns: 1fr; }
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AuditComponent {
  private readonly fileActions = inject(FileActionsService);
  private dataService = inject(ConfigDataService);
  private destroyRef = inject(DestroyRef);
  protected logs = signal<AuditLog[]>([]);
  protected searchTerm = signal('');
  protected actionFilter = signal('');
  protected entityFilter = signal('');
  protected dateFrom = signal('');
  protected dateTo = signal('');
  protected currentPage = signal(1);
  protected totalElements = signal(0);
  protected totalPages = signal(0);
  protected loadError = signal(false);
  protected summary = signal({ total: 0, hoy: 0, criticos: 0, usuarios: 0 });
  protected expandedLog = signal<string | null>(null);
  protected readonly pageSize = 12;
  protected actions = signal<string[]>([]);
  protected entities = signal<string[]>([]);
  protected pageCount = computed(() => Math.max(1, this.totalPages()));
  protected pageStart = computed(() => this.totalElements() ? (this.currentPage() - 1) * this.pageSize + 1 : 0);
  protected pageEnd = computed(() => Math.min(this.currentPage() * this.pageSize, this.totalElements()));
  private reloadTimer: ReturnType<typeof setTimeout> | undefined;
  private lastFilterKey = '';
  private requestSequence = 0;

  constructor() {
    addIcons({ documentTextOutline, searchOutline, downloadOutline, copyOutline, chevronBackOutline, chevronForwardOutline });
    this.dataService.getAuditFacets().pipe(takeUntilDestroyed(this.destroyRef)).subscribe(data => {
      this.actions.set(data.acciones);
      this.entities.set(data.entidades);
    });
    this.dataService.getAuditSummary().pipe(takeUntilDestroyed(this.destroyRef)).subscribe(data => {
      this.summary.set(data);
    });
    effect(() => {
      const filterKey = [this.searchTerm(), this.actionFilter(), this.entityFilter(), this.dateFrom(), this.dateTo()].join('|');
      const page = this.currentPage();
      if (filterKey !== this.lastFilterKey) {
        this.lastFilterKey = filterKey;
        if (page !== 1) {
          this.currentPage.set(1);
          return;
        }
      }
      this.scheduleLoad();
    });
    this.destroyRef.onDestroy(() => { if (this.reloadTimer) clearTimeout(this.reloadTimer); });
  }

  private scheduleLoad(): void {
    if (this.reloadTimer) clearTimeout(this.reloadTimer);
    this.reloadTimer = setTimeout(() => this.loadPage(), 250);
  }

  private loadPage(): void {
    const sequence = ++this.requestSequence;
    this.loadError.set(false);
    this.dataService.getAuditPage({
      page: this.currentPage() - 1,
      size: this.pageSize,
      q: this.searchTerm().trim() || undefined,
      accion: this.actionFilter() || undefined,
      entidad: this.entityFilter() || undefined,
      desde: this.dateFrom() || undefined,
      hasta: this.dateTo() || undefined,
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: result => {
        if (sequence !== this.requestSequence) return;
        this.logs.set(result.content);
        this.totalElements.set(result.totalElements);
        this.totalPages.set(result.totalPages);
      },
      error: () => {
        if (sequence !== this.requestSequence) return;
        this.logs.set([]);
        this.totalElements.set(0);
        this.totalPages.set(0);
        this.loadError.set(true);
      },
    });
  }

  protected formatAction(action: string): string {
    return action.replace(/_/g, ' ');
  }

  protected isCritical(log: AuditLog): boolean { return /ELIMINAR|BLOQUEAR|RESTANTABLECER|RESTABLECER|CAMBIO.*OBLIGATORIO/i.test(log.accion); }
  protected toggleDetails(id: string): void { this.expandedLog.update(current => current === id ? null : id); }
  protected previousPage(): void { this.currentPage.update(page => Math.max(1, page - 1)); }
  protected nextPage(): void { this.currentPage.update(page => Math.min(this.pageCount(), page + 1)); }
  protected retryLoad(): void { this.loadPage(); }
  protected async copyIp(ip: string): Promise<void> { await navigator.clipboard?.writeText(ip); }
  protected exportCsv(): void {
    const columns = ['Fecha', 'Usuario', 'Acción', 'Entidad', 'Detalle', 'IP'];
    const rows = this.logs().map(log => [new Date(log.fecha).toLocaleString('es-PA'), log.usuario, this.formatAction(log.accion), log.entidad, log.detalle, log.direccionIp]);
    const csv = [columns, ...rows].map(row => row.map(value => `"${String(value ?? '').replace(/"/g, '""')}"`).join(',')).join('\n');
    void this.fileActions.save(new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8;' }), 'auditoria.csv');
  }
}
