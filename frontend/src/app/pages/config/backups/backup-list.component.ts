import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AlertController, IonIcon } from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import {
  addOutline, checkmarkCircle, cloudDoneOutline, flaskOutline, informationCircleOutline,
  refreshOutline, serverOutline, shieldCheckmarkOutline, timeOutline, warningOutline,
} from 'ionicons/icons';
import { forkJoin } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { BackupOperationalStatus, BackupRecord } from '../models/config.models';
import { ConfigDataService } from '../services/config-data.service';

@Component({
  selector: 'app-backup-list',
  standalone: true,
  imports: [CommonModule, IonIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-container">
      <header class="page-header">
        <div class="title-group">
          <span class="header-icon"><ion-icon name="server-outline" /></span>
          <div><span class="eyebrow">Continuidad operativa</span><h1>Respaldos</h1><p>Supervisa copias cifradas y verifica que puedan restaurarse.</p></div>
        </div>
        <div class="header-actions">
          <button type="button" class="btn-secondary" (click)="loadData()" [disabled]="loading()" aria-label="Actualizar estado de respaldos"><ion-icon name="refresh-outline" /> Actualizar</button>
          <button type="button" class="btn-primary" (click)="confirmCreateBackup()"
                  [disabled]="creating() || loading() || !status()?.listoParaRespaldoManual"
                  [title]="status()?.listoParaRespaldoManual ? 'Crear una copia cifrada y externa' : 'Complete la configuración operativa antes de crear respaldos'">
            <ion-icon name="add-outline" /> {{ creating() ? 'Creando...' : 'Crear respaldo' }}
          </button>
        </div>
      </header>

      <div *ngIf="loading()" class="feedback-state">Cargando estado de continuidad...</div>
      <div *ngIf="loadError()" class="feedback-state error-state" role="alert"><span>{{ loadError() }}</span><button type="button" (click)="loadData()">Reintentar</button></div>

      <ng-container *ngIf="status() as operationalStatus">
        <section class="summary-grid" aria-label="Indicadores de respaldos">
          <article class="summary-card"><ion-icon name="time-outline" /><div><small>Último respaldo</small><strong>{{ operationalStatus.ultimoRespaldoExitoso ? (operationalStatus.ultimoRespaldoExitoso | date:'dd/MM/yyyy HH:mm') : 'Sin evidencia' }}</strong></div></article>
          <article class="summary-card"><ion-icon name="flask-outline" /><div><small>Última restauración</small><strong>{{ operationalStatus.ultimaRestauracionVerificada ? (operationalStatus.ultimaRestauracionVerificada | date:'dd/MM/yyyy HH:mm') : 'No verificada' }}</strong></div></article>
          <article class="summary-card"><ion-icon name="shield-checkmark-outline" /><div><small>Retención</small><strong>{{ operationalStatus.retencionDias }} días / {{ operationalStatus.retencionCopias }} copias</strong></div></article>
          <article class="summary-card"><ion-icon name="cloud-done-outline" /><div><small>Copia externa</small><strong>{{ operationalStatus.copiaExternaConfigurada ? 'Configurada' : 'Pendiente' }}</strong></div></article>
        </section>

        <section class="operations-card" [class.ready]="operationalStatus.listoParaRespaldoManual">
          <div class="operations-heading">
            <div><span class="eyebrow">Diagnóstico sin secretos</span><h2>Estado operativo</h2></div>
            <span class="readiness-badge" [class.ready]="operationalStatus.listoParaRespaldoManual"><ion-icon [name]="operationalStatus.listoParaRespaldoManual ? 'checkmark-circle' : 'warning-outline'" />{{ operationalStatus.listoParaRespaldoManual ? 'Listo para respaldar' : 'Configuración incompleta' }}</span>
          </div>
          <div class="checks-grid">
            <span [class.ok]="operationalStatus.cifradoConfigurado">Cifrado AES-256-GCM</span>
            <span [class.ok]="operationalStatus.pgDumpDisponible">Herramienta de respaldo</span>
            <span [class.ok]="operationalStatus.pgRestoreDisponible">Herramienta de restauración</span>
            <span [class.ok]="operationalStatus.copiaExternaConfigurada">Destino externo</span>
            <span [class.ok]="operationalStatus.respaldoAutomaticoActivo">Ejecución automática</span>
            <span [class.ok]="operationalStatus.pruebaRestauracionActiva">Prueba periódica</span>
          </div>
          <p class="operations-help" *ngIf="missingRequirements()"><ion-icon name="information-circle-outline" /> Pendiente: {{ missingRequirements() }}.</p>
        </section>
      </ng-container>

      <section class="table-card" *ngIf="!loading() && !loadError()">
        <div class="section-heading"><div><h2>Evidencia de respaldos</h2><p>La descarga y eliminación no están disponibles desde la operación cotidiana.</p></div><span>{{ backups().length }} registros</span></div>
        <div class="table-scroll">
          <table class="data-table">
            <thead><tr><th>Archivo</th><th>Tipo</th><th>Fecha</th><th>Tamaño</th><th>Copia externa</th><th>Verificación</th><th>Acción segura</th></tr></thead>
            <tbody>
              <tr *ngFor="let backup of backups()">
                <td><div class="file-cell"><ion-icon name="server-outline" /><span>{{ backup.nombre }}</span></div></td>
                <td><span class="type-badge">{{ backup.tipo === 'automatico' ? 'Automático' : 'Manual' }}</span></td>
                <td class="date-cell">{{ backup.fecha | date:'dd/MM/yyyy HH:mm' }}</td><td>{{ backup.tamanio }}</td>
                <td><span class="evidence-badge" [class.ok]="backup.copiaExterna">{{ backup.copiaExterna ? 'Confirmada' : 'Sin evidencia' }}</span></td>
                <td><span class="verification-badge" [class.verified]="backup.verificacionEstado === 'verificado'" [class.failed]="backup.verificacionEstado === 'fallido'">{{ verificationLabel(backup) }}</span><small class="verified-at" *ngIf="backup.verificadoAt">{{ backup.verificadoAt | date:'dd/MM/yyyy HH:mm' }}</small></td>
                <td><button type="button" class="btn-test" (click)="confirmRestoreTest(backup)" [disabled]="verifyingId() !== null || backup.estado !== 'completado' || !status()?.cifradoConfigurado || !status()?.pgRestoreDisponible"><ion-icon name="flask-outline" /> {{ verifyingId() === backup.id ? 'Verificando...' : 'Probar restauración' }}</button></td>
              </tr>
              <tr *ngIf="backups().length === 0"><td colspan="7" class="empty-cell"><ion-icon name="server-outline" /><strong>No existen respaldos registrados</strong><span>Complete la configuración operativa para generar la primera copia cifrada.</span></td></tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>
  `,
  styles: [`
    :host{display:block;color:var(--text-strong,#0f172a)}.page-container{max-width:1280px;margin:0 auto;padding:8px 0 28px}.page-header,.title-group,.header-actions,.operations-heading,.section-heading{display:flex;align-items:center}.page-header,.operations-heading,.section-heading{justify-content:space-between;gap:18px}.page-header{margin-bottom:18px}.title-group{gap:14px}.header-icon{display:grid;place-items:center;width:52px;height:52px;border-radius:15px;color:#fff;background:linear-gradient(145deg,var(--institutional-blue),var(--institutional-blue));font-size:24px}.eyebrow{display:block;margin-bottom:3px;color:var(--institutional-blue);font-size:10px;font-weight:800;letter-spacing:.1em;text-transform:uppercase}h1,h2,p{margin:0}h1{font-size:26px}h2{font-size:16px}.title-group p,.section-heading p{margin-top:4px;color:var(--text-soft,#64748b);font-size:13px}.header-actions{gap:10px}button{font:600 13px inherit}.btn-primary,.btn-secondary,.btn-test{display:inline-flex;align-items:center;justify-content:center;gap:7px;min-height:40px;padding:0 15px;border-radius:10px;cursor:pointer;transition:.2s}.btn-primary{color:#fff;border:0;background:var(--institutional-blue)}.btn-secondary,.btn-test{color:var(--text-strong,#334155);border:1px solid var(--border-default,#dbe5f2);background:var(--surface-panel,#fff)}button:disabled{cursor:not-allowed;opacity:.5}button:focus-visible{outline:3px solid rgba(37,99,235,.25);outline-offset:2px}.feedback-state,.operations-card,.table-card,.summary-card{border:1px solid var(--border-default,#dbe5f2);background:var(--surface-panel,#fff)}.feedback-state{display:flex;justify-content:space-between;align-items:center;padding:14px 16px;border-radius:12px;color:var(--text-soft,#64748b)}.feedback-state button{padding:7px 12px;border:1px solid currentColor;border-radius:8px;color:var(--institutional-blue);background:transparent}.error-state{color:#b91c1c;border-color:#fecaca;background:#fef2f2}.summary-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px;margin-bottom:14px}.summary-card{display:flex;align-items:center;gap:12px;min-height:82px;padding:14px 16px;border-radius:14px}.summary-card>ion-icon{flex:0 0 auto;padding:10px;border-radius:11px;color:var(--institutional-blue);background:var(--brand-soft);font-size:21px}.summary-card div{display:grid;gap:4px;min-width:0}.summary-card small{color:var(--text-soft,#64748b);font-size:11px}.summary-card strong{font-size:14px}.operations-card{margin-bottom:14px;padding:16px;border-radius:14px;border-left:4px solid #f59e0b}.operations-card.ready{border-left-color:#10b981}.readiness-badge,.checks-grid span,.evidence-badge,.verification-badge,.type-badge{display:inline-flex;align-items:center;gap:5px;width:fit-content;padding:5px 9px;border-radius:999px;font-size:11px;font-weight:700}.readiness-badge{color:#b45309;background:#fffbeb}.readiness-badge.ready{color:#047857;background:#ecfdf5}.checks-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px;margin-top:14px}.checks-grid span{width:auto;color:#b45309;background:#fffbeb}.checks-grid span.ok{color:#047857;background:#ecfdf5}.checks-grid span::before{content:'•';font-size:18px;line-height:0}.operations-help{display:flex;align-items:flex-start;gap:7px;margin-top:12px;color:var(--text-soft,#64748b);font-size:12px}.table-card{overflow:hidden;border-radius:14px}.section-heading{padding:16px 18px;border-bottom:1px solid var(--border-default,#dbe5f2)}.section-heading>span{color:var(--text-soft,#64748b);font-size:12px}.table-scroll{overflow-x:auto}.data-table{width:100%;min-width:1050px;border-collapse:collapse}th{padding:12px 16px;text-align:left;color:var(--text-soft,#64748b);background:var(--surface-subtle,#f8fafc);font-size:10px;letter-spacing:.06em;text-transform:uppercase}td{padding:13px 16px;border-top:1px solid var(--border-default,#e2e8f0);color:var(--text-strong,#334155);font-size:13px}.file-cell{display:flex;align-items:center;gap:9px;font-weight:600}.file-cell ion-icon{color:var(--institutional-blue)}.date-cell{white-space:nowrap;color:var(--text-soft,#64748b)}.type-badge{color:var(--institutional-blue);background:var(--brand-soft)}.evidence-badge,.verification-badge{color:#b45309;background:#fffbeb}.evidence-badge.ok,.verification-badge.verified{color:#047857;background:#ecfdf5}.verification-badge.failed{color:#b91c1c;background:#fef2f2}.verified-at{display:block;margin-top:4px;color:var(--text-soft,#64748b);font-size:10px}.btn-test{min-height:34px;padding:0 10px;color:var(--institutional-blue);white-space:nowrap}.empty-cell{padding:46px 20px;text-align:center}.empty-cell ion-icon,.empty-cell strong,.empty-cell span{display:block;margin:0 auto 7px}.empty-cell ion-icon{color:#94a3b8;font-size:28px}.empty-cell span{color:var(--text-soft,#64748b);font-size:12px}@media(max-width:900px){.summary-grid{grid-template-columns:repeat(2,1fr)}.checks-grid{grid-template-columns:repeat(2,1fr)}}@media(max-width:640px){.page-header{align-items:flex-start;flex-direction:column}.header-actions{width:100%}.header-actions button{flex:1}.summary-grid,.checks-grid{grid-template-columns:1fr}.operations-heading,.section-heading{align-items:flex-start;flex-direction:column}}
  `],
})
export class BackupListComponent implements OnInit {
  private readonly dataService = inject(ConfigDataService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly alertCtrl = inject(AlertController);
  protected readonly backups = signal<BackupRecord[]>([]);
  protected readonly status = signal<BackupOperationalStatus | null>(null);
  protected readonly loading = signal(true);
  protected readonly loadError = signal('');
  protected readonly creating = signal(false);
  protected readonly verifyingId = signal<string | null>(null);
  protected readonly missingRequirements = computed(() => {
    const state = this.status(); if (!state) return '';
    const missing: string[] = [];
    if (!state.cifradoConfigurado) missing.push('clave de cifrado');
    if (!state.pgDumpDisponible) missing.push('herramienta pg_dump');
    if (!state.pgRestoreDisponible) missing.push('herramienta pg_restore');
    if (!state.copiaExternaConfigurada) missing.push('destino de copia externa');
    if (!state.respaldoAutomaticoActivo) missing.push('programación automática');
    if (!state.pruebaRestauracionActiva) missing.push('prueba periódica de restauración');
    return missing.join(', ');
  });

  constructor() { addIcons({ addOutline, checkmarkCircle, cloudDoneOutline, flaskOutline, informationCircleOutline, refreshOutline, serverOutline, shieldCheckmarkOutline, timeOutline, warningOutline }); }
  ngOnInit(): void { this.loadData(); }

  protected loadData(): void {
    this.loading.set(true); this.loadError.set('');
    forkJoin({ backups: this.dataService.getBackups(), status: this.dataService.getBackupOperationalStatus() })
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false)))
      .subscribe({ next: result => { this.backups.set(result.backups); this.status.set(result.status); }, error: () => this.loadError.set('No fue posible consultar el estado real de los respaldos.') });
  }

  protected async confirmCreateBackup(): Promise<void> {
    if (this.creating() || !this.status()?.listoParaRespaldoManual) return;
    const alert = await this.alertCtrl.create({ header: 'Crear respaldo manual', message: 'Se generará una copia cifrada de la base de datos y se verificará su copia externa.', buttons: [{ text: 'Cancelar', role: 'cancel' }, { text: 'Crear respaldo', handler: () => this.createBackup() }] });
    await alert.present();
  }

  private createBackup(): void {
    this.creating.set(true);
    this.dataService.createBackup().pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.creating.set(false))).subscribe({
      next: backup => { this.backups.update(items => [backup, ...items]); this.refreshStatus(); },
      error: error => this.presentError(error?.error?.error || error?.error?.message || 'No fue posible generar el respaldo cifrado.'),
    });
  }

  protected async confirmRestoreTest(backup: BackupRecord): Promise<void> {
    if (this.verifyingId() !== null) return;
    const alert = await this.alertCtrl.create({ header: 'Probar restauración', message: 'Se restaurará esta copia en una base temporal aislada. La base operativa no será reemplazada.', buttons: [{ text: 'Cancelar', role: 'cancel' }, { text: 'Iniciar prueba', handler: () => this.testRestore(backup) }] });
    await alert.present();
  }

  private testRestore(backup: BackupRecord): void {
    this.verifyingId.set(backup.id);
    this.dataService.testBackupRestore(backup.id).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.verifyingId.set(null))).subscribe({
      next: result => { const verifiedAt = new Date(result.verifiedAt); this.backups.update(items => items.map(item => item.id === backup.id ? { ...item, verificacionEstado: 'verificado', verificadoAt: verifiedAt } : item)); this.refreshStatus(); this.presentMessage('Restauración verificada', 'La copia superó la prueba en una base temporal aislada.'); },
      error: error => { this.backups.update(items => items.map(item => item.id === backup.id ? { ...item, verificacionEstado: 'fallido', verificadoAt: new Date() } : item)); this.presentError(error?.error?.message || 'El respaldo no superó la prueba de restauración.'); },
    });
  }

  protected verificationLabel(backup: BackupRecord): string {
    if (backup.estado === 'en_progreso') return 'Respaldo en progreso';
    if (backup.estado === 'fallido') return 'Respaldo no disponible';
    return backup.verificacionEstado === 'verificado' ? 'Verificado' : backup.verificacionEstado === 'fallido' ? 'Prueba fallida' : 'Pendiente';
  }
  private refreshStatus(): void { this.dataService.getBackupOperationalStatus().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: status => this.status.set(status) }); }
  private async presentError(message: string): Promise<void> { await this.presentMessage('Operación no disponible', message); }
  private async presentMessage(header: string, message: string): Promise<void> { const alert = await this.alertCtrl.create({ header, message, buttons: ['Aceptar'] }); await alert.present(); }
}
