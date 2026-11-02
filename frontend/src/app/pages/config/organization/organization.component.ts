import { Component, ChangeDetectionStrategy, signal, inject, DestroyRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IonIcon } from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { layersOutline, chevronForwardOutline, chevronDownOutline, peopleOutline, searchOutline, expandOutline, contractOutline } from 'ionicons/icons';
import { ConfigDataService } from '../services/config-data.service';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { OrgUnit } from '../models/config.models';
import { InstitutionsComponent } from './institutions.component';

@Component({
  selector: 'app-organization',
  standalone: true,
  imports: [CommonModule, IonIcon, InstitutionsComponent],
  template: `
    <div class="page-container organization-page">
      <div class="page-header header-row">
        <div><span class="eyebrow">Estructura institucional</span><h1>Organización</h1><p>Gestiona instituciones, sus directores y la estructura interna.</p></div>
        <div class="header-stats"><span><strong>{{ allUnits().length }}</strong> unidades</span><span><strong>{{ rootUnitsCount() }}</strong> direcciones</span></div>
      </div>

      <nav class="organization-tabs" aria-label="Secciones de organización">
        <button type="button" [class.active]="section() === 'institutions'" [attr.aria-pressed]="section() === 'institutions'" (click)="section.set('institutions')">Instituciones</button>
        <button type="button" [class.active]="section() === 'units'" [attr.aria-pressed]="section() === 'units'" (click)="section.set('units')">Estructura interna</button>
      </nav>
      @if (section() === 'institutions') {
        <app-institutions />
      } @else {
      <div class="tree-toolbar">
        <label class="search-box"><ion-icon name="search-outline" /><input type="search" placeholder="Buscar por nombre, siglas o responsable..." [value]="searchTerm()" (input)="searchTerm.set($any($event.target).value); updateVisible()" /></label>
        <div class="tree-controls"><button type="button" (click)="expandAll()"><ion-icon name="expand-outline" />Expandir todo</button><button type="button" (click)="collapseAll()"><ion-icon name="contract-outline" />Contraer todo</button></div>
      </div>

      <div class="org-tree">
        <div *ngFor="let unit of visibleUnits()" class="org-tree-node">
          <div class="node-row" [class.level-root]="unit.nivel === 0" [style.--node-depth]="unit.nivel" (click)="toggleNode(unit.id)">
            <button type="button" class="expand-btn" [attr.aria-label]="expandedIds().has(unit.id) ? 'Contraer ' + unit.nombre : 'Expandir ' + unit.nombre" [attr.aria-expanded]="expandedIds().has(unit.id)" [class.expanded]="expandedIds().has(unit.id)" [class.invisible]="!hasChildren(unit.id)" [title]="expandedIds().has(unit.id) ? 'Contraer ' + unit.nombre : 'Expandir ' + unit.nombre">
              <ion-icon [name]="expandedIds().has(unit.id) ? 'chevron-down-outline' : 'chevron-forward-outline'" aria-hidden="true" />
            </button>
            <div class="node-content">
              <div class="node-icon-wrapper">
                <ion-icon name="layers-outline" class="node-icon" />
              </div>
              <div class="node-info">
                <span class="node-name">{{ unit.nombre }}</span>
              <span class="node-siglas">{{ unit.siglas }}</span>
              </div>
              <span class="node-responsable" *ngIf="unit.responsable">
                <ion-icon name="people-outline" class="people-icon" />
                {{ unit.responsable }}
              </span>
              <span class="children-count" *ngIf="hasChildren(unit.id)">{{ childCount(unit.id) }} {{ childCount(unit.id) === 1 ? 'subunidad' : 'subunidades' }}</span>
            </div>
          </div>
        </div>
      </div>
      }
    </div>
  `,
  styles: [`
    :host { display:block; min-width:0; font-family:var(--font-family); }
    .organization-page { width:100%; max-width:1280px; margin:0 auto; padding:0; }
    .page-header { align-items:center; gap:20px; margin-bottom:18px; }
    .page-header h1 { font-size:var(--font-size-page); font-weight:var(--font-weight-bold); line-height:var(--line-height-heading); color:var(--text-strong); margin:0; }
    .page-header p { margin:7px 0 0; color:var(--text-soft); font-size:var(--font-size-body); line-height:var(--line-height-body); }
    .eyebrow { display:block; margin-bottom:6px; color:var(--text-link); font-size:var(--font-size-label); font-weight:var(--font-weight-bold); letter-spacing:.08em; text-transform:uppercase; }
    .header-stats { display:flex; flex-wrap:wrap; justify-content:flex-end; gap:8px; flex-shrink:0; }
    .header-stats span { display:inline-flex; align-items:baseline; gap:5px; padding:9px 12px; border:1px solid var(--border-default); border-radius:8px; color:var(--text-soft); background:var(--surface-subtle); font-size:var(--font-size-label); }
    .header-stats strong { color:var(--text-link); font-size:var(--font-size-section-min); font-weight:var(--font-weight-bold); }
    .organization-tabs { display:flex; gap:6px; width:fit-content; max-width:100%; margin-bottom:20px; padding:5px; border:1px solid var(--border-default); border-radius:10px; background:var(--surface-panel); }
    .organization-tabs button { min-height:42px; padding:9px 16px; border:1px solid transparent; border-radius:7px; color:var(--text-soft); background:transparent; font-family:inherit; font-size:var(--font-size-compact); font-weight:var(--font-weight-semibold); line-height:var(--line-height-control); cursor:pointer; transition:background-color .16s, color .16s; }
    .organization-tabs button:hover { background:var(--surface-hover); color:var(--text-strong); }
    .organization-tabs button.active { color:var(--text-on-brand); border-color:var(--institutional-blue); background:var(--institutional-blue); }
    .tree-toolbar { display:flex; flex-wrap:wrap; align-items:center; gap:12px; margin-bottom:16px; padding:14px; border:1px solid var(--border-default); border-radius:var(--card-radius); background:var(--surface-panel); }
    .search-box { display:flex; align-items:center; flex:1 1 360px; min-width:0; gap:9px; padding:0 12px; border:1px solid var(--control-border); border-radius:8px; background:var(--control-bg); color:var(--control-placeholder); }
    .search-box > ion-icon { flex-shrink:0; font-size:18px; }
    .search-box input { min-width:0; width:100%; min-height:44px; padding:10px 0; border:0; outline:0; color:var(--control-text); background:transparent; font-family:inherit; font-size:var(--font-size-body); font-weight:var(--font-weight-regular); }
    .search-box input::placeholder { color:var(--control-placeholder); opacity:1; }
    .tree-controls { display:flex; flex-wrap:wrap; gap:8px; }
    .tree-controls button { display:inline-flex; align-items:center; justify-content:center; gap:6px; min-height:42px; padding:9px 12px; border:1px solid var(--border-default); border-radius:8px; color:var(--text-soft); background:var(--surface-panel); font-family:inherit; font-size:var(--font-size-compact); font-weight:var(--font-weight-semibold); cursor:pointer; }
    .tree-controls button:hover { background:var(--surface-hover); color:var(--text-link); border-color:var(--brand-border); }
    .org-tree { background:var(--surface-panel); border:1px solid var(--border-default); border-radius:var(--card-radius); box-shadow:var(--shadow-sm); overflow:hidden; }
    .org-tree-node { border-bottom:1px solid var(--border-subtle); }
    .org-tree-node:last-child { border-bottom: none; }
    .node-row { display:flex; align-items:center; min-height:64px; padding:12px 16px; padding-left:clamp(16px, calc(16px + var(--node-depth, 0) * 24px), 160px); cursor:pointer; transition:background-color .15s; gap:8px; }
    .node-row.level-root { background:var(--surface-subtle); }
    .node-row:hover { background:var(--surface-hover); }
    .expand-btn { display:inline-flex; align-items:center; justify-content:center; width:32px; height:32px; border:none; background:transparent; cursor:pointer; color:var(--text-muted-accessible); padding:0; flex-shrink:0; border-radius:6px; }
    .expand-btn:hover { background:var(--surface-hover); color:var(--text-link); }
    .expand-btn.expanded { color:var(--text-link); }
    .expand-btn.invisible { visibility: hidden; }
    .expand-btn ion-icon { font-size: 16px; }
    .node-content { display:grid; grid-template-columns:36px minmax(0, 1fr) minmax(0, 220px) auto; align-items:center; gap:12px; flex:1; min-width:0; }
    .node-icon-wrapper { grid-column:1; width:36px; height:36px; border-radius:8px; background:var(--brand-soft); display:flex; align-items:center; justify-content:center; }
    .node-icon { font-size:18px; color:var(--text-link); }
    .node-info { display: flex; flex-direction: column; gap: 2px; flex: 1; min-width: 0; }
    .node-info { grid-column:2; }
    .node-name { font-size:var(--font-size-body); font-weight:var(--font-weight-semibold); color:var(--text-strong); overflow-wrap:anywhere; }
    .node-siglas { font-size:var(--font-size-label); color:var(--text-muted-accessible); text-transform:uppercase; letter-spacing:.04em; font-weight:var(--font-weight-medium); overflow-wrap:anywhere; }
    .node-responsable { grid-column:3; display:inline-flex; align-items:center; gap:6px; font-size:var(--font-size-label); color:var(--text-soft); background:var(--surface-subtle); padding:6px 9px; border-radius:6px; overflow-wrap:anywhere; }
    .children-count { grid-column:4; padding:5px 8px; border-radius:999px; color:var(--text-soft); background:var(--surface-subtle); font-size:var(--font-size-label); font-weight:var(--font-weight-medium); white-space:nowrap; }
    .people-icon { flex-shrink:0; font-size:16px; color:var(--text-muted-accessible); }
    @media(max-width:900px) {
      .page-header { align-items:stretch; flex-direction:column; }
      .header-stats { justify-content:flex-start; }
      .node-content { grid-template-columns:36px minmax(0, 1fr); gap:6px 10px; }
      .node-responsable,.children-count { grid-column:2; justify-self:start; max-width:100%; }
    }
    @media(max-width:600px) {
      .page-header { padding:18px 16px; }
      .organization-tabs { width:100%; }
      .organization-tabs button { flex:1; min-width:0; padding:9px 10px; }
      .tree-toolbar { padding:12px; }
      .search-box { flex-basis:100%; }
      .search-box input { font-size:var(--font-size-control-mobile); }
      .tree-controls { width:100%; }
      .tree-controls button { flex:1; min-width:0; }
      .node-row { align-items:flex-start; padding:12px 10px; padding-left:clamp(10px, calc(10px + var(--node-depth, 0) * 12px), 58px); gap:4px; }
      .expand-btn { width:40px; height:40px; }
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrganizationComponent {
  protected section = signal<'institutions' | 'units'>('institutions');
  private dataService = inject(ConfigDataService);
  private destroyRef = inject(DestroyRef);
  protected allUnits = signal<OrgUnit[]>([]);
  protected expandedIds = signal<Set<string>>(new Set(['1']));
  protected visibleUnits = signal<OrgUnit[]>([]);
  protected searchTerm = signal('');

  constructor() {
    addIcons({ layersOutline, chevronForwardOutline, chevronDownOutline, peopleOutline, searchOutline, expandOutline, contractOutline });
    this.dataService.getOrganization().pipe(takeUntilDestroyed(this.destroyRef)).subscribe(data => {
      const flat = this.flattenTree(data);
      this.allUnits.set(flat);
      this.updateVisible();
    });
  }

  protected hasChildren(id: string): boolean {
    return this.allUnits().some(u => u.padreId === id);
  }

  protected toggleNode(id: string): void {
    this.expandedIds.update(set => {
      const next = new Set(set);
      if (next.has(id)) { next.delete(id); } else { next.add(id); }
      return next;
    });
    this.updateVisible();
  }

  protected expandAll(): void {
    this.expandedIds.set(new Set(this.allUnits().filter(unit => this.hasChildren(unit.id)).map(unit => unit.id)));
    this.updateVisible();
  }

  protected collapseAll(): void {
    this.expandedIds.set(new Set());
    this.updateVisible();
  }

  protected childCount(id: string): number { return this.allUnits().filter(unit => unit.padreId === id).length; }
  protected rootUnitsCount(): number { return this.allUnits().filter(unit => unit.nivel === 0).length; }

  protected updateVisible(): void {
    const expanded = this.expandedIds();
    const term = this.searchTerm().trim().toLowerCase();
    this.visibleUnits.set(this.allUnits().filter(u => {
      if (term) return [u.nombre, u.siglas, u.responsable].some(value => value?.toLowerCase().includes(term));
      if (u.nivel === 0) return true;
      let parent = this.allUnits().find(p => p.id === u.padreId);
      while (parent) {
        if (!expanded.has(parent.id)) return false;
        parent = this.allUnits().find(p => p.id === parent!.padreId);
      }
      return true;
    }));
  }

  private flattenTree(units: OrgUnit[], level: number = 0, parentId?: string): OrgUnit[] {
    const result: OrgUnit[] = [];
    for (const unit of units) {
      result.push({ ...unit, nivel: level, padreId: parentId, children: undefined });
      if (unit.children) {
        result.push(...this.flattenTree(unit.children, level + 1, unit.id));
      }
    }
    return result;
  }
}
