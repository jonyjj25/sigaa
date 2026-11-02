import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { IonIcon, IonToggle } from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import {
  alertCircleOutline, checkmarkCircleOutline, cloudUploadOutline,
  keyOutline, lockClosedOutline, shieldCheckmarkOutline, timeOutline,
} from 'ionicons/icons';
import { ConfigDataService } from '../services/config-data.service';
import { SecurityPolicy } from '../models/config.models';

interface PolicyCategory {
  categoria: string;
  items: SecurityPolicy[];
}

@Component({
  selector: 'app-security',
  standalone: true,
  imports: [CommonModule, IonIcon, IonToggle],
  template: `
    <div class="page-container">
      <header class="page-header">
        <div class="title-icon"><ion-icon name="shield-checkmark-outline" /></div>
        <div>
          <p class="eyebrow">Configuración del sistema</p>
          <h1>Seguridad</h1>
          <p class="page-description">Estas políticas tienen efecto directo sobre el acceso, las contraseñas y los archivos del sistema.</p>
        </div>
        <span class="active-count"><b>{{ policyCount() }}</b> políticas funcionales</span>
      </header>

      <div *ngIf="errorMessage()" class="feedback error" role="alert">
        <ion-icon name="alert-circle-outline" />
        <span>{{ errorMessage() }}</span>
      </div>
      <div *ngIf="successMessage() && !errorMessage()" class="feedback success" role="status">
        <ion-icon name="checkmark-circle-outline" />
        <span>{{ successMessage() }}</span>
      </div>

      <section class="notice">
        <ion-icon name="alert-circle-outline" />
        <div><b>Cambios inmediatos</b><span>Los ajustes funcionales se aplican al guardarse.</span></div>
      </section>

      <div class="cards-grid">
        <section class="category-card" *ngFor="let cat of categories()">
          <header class="card-header">
            <span class="card-icon"><ion-icon [name]="categoryIcon(cat.categoria)" /></span>
            <div><h2>{{ categoryTitle(cat.categoria) }}</h2><p>{{ categoryDescription(cat.categoria) }}</p></div>
            <span class="policy-count">{{ cat.items.length }}</span>
          </header>

          <div class="card-body">
            <div class="policy-item" *ngFor="let policy of cat.items">
              <div class="policy-info">
                <label class="policy-name" [attr.for]="'policy-' + policy.id">{{ displayName(policy.nombre) }}</label>
                <span class="policy-desc">{{ policy.descripcion }}</span>
                <span class="policy-desc" *ngIf="policy.maximoServidorMb !== null && policy.maximoServidorMb !== undefined">Máximo del servidor: {{ policy.maximoServidorMb }} MB</span>
              </div>
              <div class="policy-control" [class.saving]="savingIds().has(policy.id)">
                <span class="saving-label" *ngIf="savingIds().has(policy.id)">Guardando…</span>
                <ion-toggle *ngIf="policyType(policy) === 'boolean'" [id]="'policy-' + policy.id"
                  [checked]="asBoolean(policy.valor)" [disabled]="savingIds().has(policy.id)"
                  (ionChange)="togglePolicy(policy, $event)" color="primary" />
                <input *ngIf="policyType(policy) === 'number'" [id]="'policy-' + policy.id" type="number" min="0"
                  [attr.max]="policy.maximoServidorMb ?? null"
                  [value]="policy.valor" [disabled]="savingIds().has(policy.id)"
                  (change)="updateNativePolicy(policy, $event)" class="value-input number-input" />
                <input *ngIf="policyType(policy) === 'time'" [id]="'policy-' + policy.id" type="time"
                  [value]="policy.valor" [disabled]="savingIds().has(policy.id)"
                  (change)="updateNativePolicy(policy, $event)" class="value-input time-input" />
                <input *ngIf="policyType(policy) === 'text'" [id]="'policy-' + policy.id" type="text"
                  [value]="policy.valor" [disabled]="savingIds().has(policy.id)"
                  (change)="updateNativePolicy(policy, $event)" class="value-input text-input" />
              </div>
            </div>
          </div>
        </section>
      </div>
    </div>
  `,
  styles: [`
    :host { display:block; min-height:100%; background:var(--surface-page, #f5f8fc); }
    .page-container { box-sizing:border-box; width:100%; max-width:1320px; margin:0 auto; padding:28px; }
    .page-header { display:flex; align-items:center; gap:16px; margin-bottom:20px; padding:22px 24px; border:1px solid #dfe8f4; border-radius:18px; background:linear-gradient(135deg,#fff 0%,#f4f8ff 100%); box-shadow:0 8px 26px rgba(15,42,91,.06); }
    .title-icon { display:grid; flex:0 0 52px; width:52px; height:52px; place-items:center; border-radius:15px; background:#eaf2ff; color:var(--institutional-blue); font-size:27px; }
    .eyebrow { margin:0 0 3px; color:var(--institutional-blue); font-size:11px; font-weight:800; letter-spacing:.08em; text-transform:uppercase; }
    .page-header h1 { margin:0; color:#102a56; font-size:27px; line-height:1.15; }
    .page-description { margin:5px 0 0; color:#64748b; font-size:13px; }
    .active-count { margin-left:auto; padding:9px 13px; border:1px solid var(--brand-border); border-radius:11px; background:#fff; color:#64748b; font-size:12px; white-space:nowrap; }
    .active-count b { color:var(--institutional-blue); font-size:17px; margin-right:4px; }
    .feedback,.notice { display:flex; align-items:flex-start; gap:10px; margin-bottom:16px; padding:12px 14px; border-radius:11px; font-size:13px; }
    .feedback ion-icon,.notice ion-icon { flex:0 0 auto; margin-top:1px; font-size:18px; }
    .feedback.error { border:1px solid #fecaca; background:#fff1f2; color:#b91c1c; }
    .feedback.success { border:1px solid #bbf7d0; background:#f0fdf4; color:#15803d; }
    .notice { border:1px solid #fde68a; background:#fffbeb; color:#92400e; }
    .notice div { display:grid; gap:2px; }.notice span { color:#a16207; }
    .cards-grid { columns:2; column-gap:18px; }
    .category-card { display:inline-block; overflow:hidden; box-sizing:border-box; width:100%; margin:0 0 18px; break-inside:avoid; border:1px solid #dfe7f2; border-radius:16px; background:#fff; box-shadow:0 5px 18px rgba(15,42,91,.045); vertical-align:top; }
    .card-header { display:flex; align-items:center; gap:12px; padding:18px 20px; border-bottom:1px solid #edf2f7; background:#fbfdff; }
    .card-icon { display:grid; flex:0 0 38px; width:38px; height:38px; place-items:center; border-radius:11px; background:#eaf2ff; color:var(--institutional-blue); font-size:19px; }
    .card-header div { min-width:0; }.card-header h2 { margin:0; color:#173b75; font-size:16px; }.card-header p { margin:3px 0 0; color:#8492a6; font-size:11px; }
    .policy-count { display:grid; flex:0 0 28px; width:28px; height:28px; margin-left:auto; place-items:center; border-radius:9px; background:#eef4fc; color:#52709b; font-size:12px; font-weight:800; }
    .category-warning { display:flex; align-items:center; gap:7px; margin:12px 16px 0; padding:9px 10px; border-radius:9px; background:#fff7ed; color:#9a5912; font-size:11px; line-height:1.35; }
    .category-warning ion-icon { flex:0 0 auto; font-size:15px; }
    .card-body { padding:5px 0; }
    .policy-item { display:flex; align-items:center; justify-content:space-between; min-height:68px; gap:18px; padding:12px 20px; border-bottom:1px solid #f1f5f9; transition:background .15s; }
    .policy-item:last-child { border-bottom:0; }.policy-item:hover { background:#fafcff; }
    .policy-info { display:flex; flex:1; min-width:0; flex-direction:column; gap:4px; }
    .policy-name { color:#27364c; font-size:13px; font-weight:700; line-height:1.3; }
    .policy-desc { color:#8a98ab; font-size:11px; line-height:1.35; }
    .policy-control { position:relative; display:flex; flex:0 0 auto; align-items:center; gap:7px; }
    .policy-control.saving { opacity:.7; }.saving-label { color:#64748b; font-size:10px; }
    .value-input { box-sizing:border-box; height:40px; padding:0 11px; border:1px solid #ccd9e9; border-radius:9px; outline:0; background:#f8fbff; color:#27364c; font:13px inherit; transition:border-color .18s,box-shadow .18s,background .18s; }
    .value-input:hover { border-color:#9db6d5; background:#fff; }
    .value-input:focus { border-color:var(--institutional-blue); box-shadow:0 0 0 3px rgba(37,99,235,.12); background:#fff; }
    .value-input:disabled { cursor:not-allowed; opacity:.65; }
    .number-input { appearance:textfield; -moz-appearance:textfield; }
    .number-input::-webkit-outer-spin-button,.number-input::-webkit-inner-spin-button { margin:0; appearance:none; -webkit-appearance:none; }
    .number-input { width:105px; }.time-input { width:125px; }.text-input { width:235px; }
    ion-toggle { --track-background:#cbd5e1; --track-background-checked:var(--institutional-blue); --handle-background:#fff; --handle-background-checked:#fff; }
    @media(max-width:980px){.cards-grid{columns:1}.active-count{display:none}}
    @media(max-width:620px){.page-container{padding:16px}.page-header{align-items:flex-start;padding:18px}.page-description{line-height:1.45}.policy-item{align-items:flex-start;flex-direction:column;gap:10px}.policy-control{width:100%;justify-content:flex-end}.text-input{width:100%}.number-input,.time-input{width:140px}.category-card{margin-bottom:14px}}
    @media(prefers-reduced-motion:reduce){*{transition:none!important}}
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SecurityComponent {
  private readonly dataService = inject(ConfigDataService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly categories = signal<PolicyCategory[]>([]);
  protected readonly savingIds = signal<Set<string>>(new Set());
  protected readonly errorMessage = signal('');
  protected readonly successMessage = signal('');

  private readonly visiblePolicies = new Set([
    'Intentos fallidos máximos', 'Tiempo de bloqueo (min)', 'Tiempo bloqueo (min)',
    'Autenticación de dos factores',
    'Tamaño máximo de archivos (MB)', 'Tipos de archivos permitidos',
    'Longitud mínima de contraseña', 'Longitud de contraseña mínima', 'Longitud máxima de contraseña', 'Historial de contraseñas',
    'Requiere mayúsculas', 'Requiere minúsculas', 'Requiere números', 'Requiere caracteres especiales',
    'Restricción por horario', 'Horario inicio acceso', 'Horario fin acceso',
  ]);

  constructor() {
    addIcons({ alertCircleOutline, checkmarkCircleOutline, cloudUploadOutline, keyOutline, lockClosedOutline, shieldCheckmarkOutline, timeOutline });
    this.dataService.getSecurityPolicies().pipe(takeUntilDestroyed(this.destroyRef)).subscribe(data => {
      this.categories.set(this.groupBy(data.filter(policy => policy.administrable === true && policy.categoria.trim().toUpperCase() !== 'CORS' && this.visiblePolicies.has(policy.nombre))));
    });
  }

  protected policyCount(): number { return this.categories().reduce((total, category) => total + category.items.length, 0); }
  protected displayName(name: string): string {
    const aliases: Record<string, string> = {
      'Tiempo bloqueo (min)': 'Tiempo de bloqueo (min)',
      'Longitud de contraseña mínima': 'Longitud mínima de contraseña',
      'Tamaño máximo de archivos (MB)': 'Límite funcional por archivo (MB)',
    };
    return aliases[name] ?? name;
  }
  protected categoryTitle(category: string): string { return category === 'Contrasena' ? 'Contraseñas' : category === 'IP' ? 'Horarios institucionales' : category; }
  protected categoryDescription(category: string): string {
    const descriptions: Record<string, string> = {
      Acceso: 'Bloqueo de cuentas por intentos fallidos', Autenticación: 'Verificación adicional al iniciar sesión',
      Carga: 'Límites y formatos de archivos', Contrasena: 'Requisitos de contraseñas nuevas',
      IP: 'Horario institucional permitido para el acceso',
    };
    return descriptions[category] ?? 'Políticas aplicadas por el sistema';
  }
  protected categoryIcon(category: string): string {
    if (category === 'Carga') return 'cloud-upload-outline';
    if (category === 'Autenticación' || category === 'Contrasena') return 'key-outline';
    if (category === 'Acceso' || category === 'IP') return 'time-outline';
    return 'lock-closed-outline';
  }
  protected policyType(policy: SecurityPolicy): 'boolean' | 'number' | 'time' | 'text' {
    const name = this.displayName(policy.nombre);
    if (['Autenticación de dos factores', 'Restricción por horario', 'Requiere mayúsculas', 'Requiere minúsculas', 'Requiere números', 'Requiere caracteres especiales'].includes(name)) return 'boolean';
    if (name === 'Horario inicio acceso' || name === 'Horario fin acceso') return 'time';
    if (name.includes('(min)') || name.includes('(seg)') || name.includes('Longitud') || name.includes('Intentos') || name.includes('Historial') || name.includes('Tamaño') || name.includes('Límite')) return 'number';
    return 'text';
  }

  protected asBoolean(value: string | number | boolean): boolean { return String(value).toLowerCase() === 'true'; }
  protected togglePolicy(policy: SecurityPolicy, event: CustomEvent<{ checked: boolean }>): void { this.persistPolicy(policy, String(Boolean(event.detail.checked))); }
  protected updateNativePolicy(policy: SecurityPolicy, event: Event): void {
    const value = (event.target as HTMLInputElement).value.trim();
    if (value && value !== String(policy.valor)) this.persistPolicy(policy, value);
  }

  private persistPolicy(policy: SecurityPolicy, newValue: string): void {
    const previousValue = policy.valor;
    this.errorMessage.set(''); this.successMessage.set(''); this.setSaving(policy.id, true);
    this.replacePolicy(policy.id, { ...policy, valor: newValue });
    this.dataService.updateSecurityPolicy(policy.id, newValue).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: saved => { this.setSaving(policy.id, false); this.replacePolicy(policy.id, saved); this.successMessage.set(`${this.displayName(policy.nombre)} se guardó correctamente.`); },
      error: error => { this.setSaving(policy.id, false); this.replacePolicy(policy.id, { ...policy, valor: previousValue }); this.errorMessage.set(this.getSaveErrorMessage(error)); },
    });
  }
  private replacePolicy(id: string, policy: SecurityPolicy): void {
    this.categories.update(categories => categories.map(category => ({ ...category, items: category.items.map(item => item.id === id ? policy : item) })));
  }
  private setSaving(id: string, saving: boolean): void {
    this.savingIds.update(ids => { const next = new Set(ids); saving ? next.add(id) : next.delete(id); return next; });
  }
  private getSaveErrorMessage(error: { status?: number; error?: { error?: string; message?: string } }): string {
    const message = error?.error?.error ?? error?.error?.message;
    if (message) return message;
    if (error?.status === 401) return 'Tu sesión expiró. Inicia sesión nuevamente para guardar cambios.';
    if (error?.status === 403) return 'No tienes permiso para modificar las políticas de seguridad.';
    if (error?.status === 0) return 'No fue posible conectar con el servidor.';
    return 'No se pudo guardar la política. Intenta nuevamente.';
  }
  private groupBy(data: SecurityPolicy[]): PolicyCategory[] {
    const order = ['Acceso', 'Autenticación', 'Contrasena', 'Carga', 'IP'];
    const map = new Map<string, SecurityPolicy[]>();
    for (const policy of data) map.set(policy.categoria, [...(map.get(policy.categoria) ?? []), policy]);
    return Array.from(map.entries()).map(([categoria, items]) => ({ categoria, items }))
      .sort((a, b) => order.indexOf(a.categoria) - order.indexOf(b.categoria));
  }
}
