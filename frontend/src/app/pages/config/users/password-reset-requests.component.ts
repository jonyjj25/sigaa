import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AlertController } from '@ionic/angular';
import { ConfigDataService } from '../services/config-data.service';

interface ResetRequest {
  id: number; userId: number; userName: string; email: string;
  requestIp: string; requestedAt: string; status: string;
}

interface TemporaryCredential {
  temporaryPassword: string;
  userName: string;
  email: string;
  expiresAt: string;
}

@Component({
  selector: 'app-password-reset-requests',
  standalone: true,
  imports: [CommonModule],
  template: `
    @if (requests().length) {
      <section class="request-card" aria-labelledby="reset-title">
        <div class="request-heading">
          <div><span class="eyebrow">Acción requerida</span><h2 id="reset-title">Solicitudes de contraseña</h2><p>Verifica la identidad del usuario antes de aprobar.</p></div>
          <span class="counter">{{ requests().length }} pendiente{{ requests().length === 1 ? '' : 's' }}</span>
        </div>
        <div class="request-list">
          @for (request of requests(); track request.id) {
            <article class="request-row">
              <span class="avatar">{{ initials(request.userName) }}</span>
              <div class="identity"><strong>{{ request.userName }}</strong><span>{{ request.email }}</span></div>
              <div class="metadata"><span>Solicitada</span><strong>{{ request.requestedAt | date:'dd/MM/yyyy, HH:mm' }}</strong></div>
              <div class="metadata ip"><span>Origen</span><strong>{{ request.requestIp || 'No disponible' }}</strong></div>
              <div class="actions">
                <button class="reject" type="button" [disabled]="processing() === request.id" (click)="reject(request)">Rechazar</button>
                <button class="approve" type="button" [disabled]="processing() === request.id" (click)="approve(request)">{{ processing() === request.id ? 'Procesando…' : 'Aprobar' }}</button>
              </div>
            </article>
          }
        </div>
      </section>
    }

    @if (temporaryCredential(); as credential) {
      <div class="credential-backdrop">
        <section class="credential-modal" role="dialog" aria-modal="true" aria-labelledby="credential-title">
          <div class="credential-header">
            <span class="credential-icon" aria-hidden="true">✓</span>
            <div>
              <span class="credential-eyebrow">Restablecimiento aprobado</span>
              <h2 id="credential-title">Contraseña temporal generada</h2>
            </div>
          </div>

          <div class="credential-body">
            <div class="credential-user">
              <span class="avatar">{{ initials(credential.userName) }}</span>
              <div><strong>{{ credential.userName }}</strong><span>{{ credential.email }}</span></div>
              <span class="expiry">Vence en 30 minutos</span>
            </div>

            <p class="credential-help">Comunícala únicamente por un canal verificado. Por seguridad, no podrá consultarse nuevamente después de cerrar esta ventana.</p>

            <div class="password-box">
              <span>Contraseña temporal</span>
              <div class="password-value">
                <code>{{ credential.temporaryPassword }}</code>
                <button type="button" class="copy-button" [class.copied]="passwordCopied()" (click)="copyTemporaryPassword()">
                  <span aria-hidden="true">{{ passwordCopied() ? '✓' : '⧉' }}</span>
                  {{ passwordCopied() ? 'Copiada' : 'Copiar' }}
                </button>
              </div>
            </div>

            <div class="security-note"><span aria-hidden="true">!</span><p>El usuario deberá iniciar sesión con esta contraseña y crear una nueva inmediatamente.</p></div>
          </div>

          <div class="credential-footer">
            <span>Confirma que guardaste o comunicaste la contraseña.</span>
            <button type="button" class="close-credential" (click)="closeTemporaryCredential()">Entendido, cerrar</button>
          </div>
        </section>
      </div>
    }
  `,
  styles: [`
    :host{display:block}.request-card{overflow:hidden;margin:0 0 18px;border:1px solid var(--brand-soft);border-radius:14px;background:#fff;box-shadow:0 7px 22px rgba(15,23,42,.05);font-family:Inter,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif}.request-heading{display:flex;align-items:center;justify-content:space-between;gap:18px;padding:16px 18px;border-bottom:1px solid var(--brand-soft);background:#f5f9ff}.eyebrow{color:var(--institutional-blue);font-size:10px;font-weight:800;letter-spacing:.09em;text-transform:uppercase}.request-heading h2{margin:3px 0 0;color:#0f172a;font-size:16px}.request-heading p{margin:4px 0 0;color:#64748b;font-size:12px}.counter{padding:6px 10px;border-radius:999px;color:var(--institutional-blue);background:var(--brand-soft);font-size:11px;font-weight:750;white-space:nowrap}.request-list{display:grid}.request-row{display:grid;grid-template-columns:40px minmax(180px,1.4fr) minmax(145px,.8fr) minmax(110px,.6fr) auto;align-items:center;gap:12px;padding:13px 18px;border-bottom:1px solid #edf2f7}.request-row:last-child{border-bottom:0}.avatar{display:grid;place-items:center;width:38px;height:38px;border-radius:11px;color:var(--institutional-blue);background:#eaf2ff;font-size:12px;font-weight:800}.identity,.metadata{display:grid;gap:3px;min-width:0}.identity strong{overflow:hidden;color:#172033;font-size:13px;text-overflow:ellipsis;white-space:nowrap}.identity span,.metadata span{color:#8392a7;font-size:10px}.metadata strong{color:#475569;font-size:11px;font-weight:650}.actions{display:flex;gap:7px}.actions button{height:34px;padding:0 13px;border-radius:8px;font:inherit;font-size:11px;font-weight:750;cursor:pointer}.actions button:disabled{opacity:.55;cursor:wait}.reject{border:1px solid #fecaca;color:#b91c1c;background:#fff}.approve{border:1px solid var(--institutional-blue);color:#fff;background:var(--institutional-blue)}.actions button:focus-visible{outline:3px solid rgba(37,99,235,.22);outline-offset:2px}
    .credential-backdrop{position:fixed;inset:0;z-index:10000;display:grid;place-items:center;padding:20px;background:rgba(15,23,42,.58);backdrop-filter:blur(3px);font-family:Inter,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif}.credential-modal{width:min(560px,100%);overflow:hidden;border:1px solid rgba(255,255,255,.65);border-radius:18px;background:#fff;box-shadow:0 28px 70px rgba(15,23,42,.3)}.credential-header{display:flex;align-items:center;gap:13px;padding:20px 22px;border-bottom:1px solid #e5edf7;background:linear-gradient(180deg,#f8fbff,#f3f7fd)}.credential-icon{display:grid;place-items:center;width:42px;height:42px;flex:0 0 auto;border-radius:12px;color:#047857;background:#d1fae5;font-size:20px;font-weight:900}.credential-eyebrow{color:var(--institutional-blue);font-size:9px;font-weight:850;letter-spacing:.1em;text-transform:uppercase}.credential-header h2{margin:4px 0 0;color:#0f172a;font-size:19px;letter-spacing:-.2px}.credential-body{display:grid;gap:17px;padding:20px 22px}.credential-user{display:grid;grid-template-columns:38px 1fr auto;align-items:center;gap:10px}.credential-user>div{display:grid;gap:3px;min-width:0}.credential-user strong{overflow:hidden;color:#172033;font-size:13px;text-overflow:ellipsis;white-space:nowrap}.credential-user div span{color:#64748b;font-size:11px}.expiry{padding:5px 9px;border-radius:999px;color:#a16207;background:#fef3c7;font-size:10px;font-weight:750;white-space:nowrap}.credential-help{margin:0;color:#5f6f85;font-size:12px;line-height:1.55}.password-box{display:grid;gap:8px;padding:13px;border:1px solid var(--brand-soft);border-radius:12px;background:#f5f9ff}.password-box>span{color:#64748b;font-size:9px;font-weight:800;letter-spacing:.08em;text-transform:uppercase}.password-value{display:flex;align-items:center;gap:12px}.password-value code{min-width:0;flex:1;overflow-wrap:anywhere;color:var(--institutional-blue);font-family:"Cascadia Code",Consolas,monospace;font-size:17px;font-weight:750;letter-spacing:.55px;user-select:all}.copy-button{display:inline-flex;align-items:center;justify-content:center;gap:6px;height:36px;padding:0 12px;border:1px solid var(--institutional-blue);border-radius:8px;color:#fff;background:var(--institutional-blue);font:inherit;font-size:11px;font-weight:750;cursor:pointer}.copy-button:hover{background:var(--institutional-blue)}.copy-button.copied{border-color:#059669;background:#059669}.security-note{display:flex;align-items:flex-start;gap:9px;padding:10px 12px;border-radius:10px;color:#854d0e;background:#fffbeb}.security-note>span{display:grid;place-items:center;width:18px;height:18px;flex:0 0 auto;border-radius:50%;color:#fff;background:#f59e0b;font-size:11px;font-weight:900}.security-note p{margin:0;font-size:10px;line-height:1.45}.credential-footer{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:14px 22px;border-top:1px solid #e5edf7;background:#f8fafc}.credential-footer>span{color:#7c8a9e;font-size:10px}.close-credential{height:38px;padding:0 15px;border:1px solid #d5dfeb;border-radius:9px;color:#334155;background:#fff;font:inherit;font-size:11px;font-weight:750;cursor:pointer}.close-credential:hover{border-color:#93b4f8;color:var(--institutional-blue);background:#f8fbff}.copy-button:focus-visible,.close-credential:focus-visible{outline:3px solid rgba(37,99,235,.24);outline-offset:2px}
    @media(max-width:850px){.request-row{grid-template-columns:40px 1fr auto}.metadata{display:none}.actions{grid-column:2/4}}@media(max-width:560px){.request-heading{align-items:flex-start}.request-row{grid-template-columns:38px 1fr}.actions{grid-column:1/3;display:grid;grid-template-columns:1fr 1fr}.actions button{width:100%}.credential-backdrop{padding:12px}.credential-header,.credential-body,.credential-footer{padding-left:16px;padding-right:16px}.credential-user{grid-template-columns:38px 1fr}.expiry{grid-column:2}.password-value{align-items:stretch;flex-direction:column}.copy-button{width:100%}.credential-footer{align-items:stretch;flex-direction:column}.close-credential{width:100%}}
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PasswordResetRequestsComponent implements OnInit {
  private readonly data = inject(ConfigDataService);
  private readonly alerts = inject(AlertController);
  protected readonly requests = signal<ResetRequest[]>([]);
  protected readonly processing = signal<number | null>(null);
  protected readonly temporaryCredential = signal<TemporaryCredential | null>(null);
  protected readonly passwordCopied = signal(false);

  ngOnInit(): void { this.load(); }
  private load(): void { this.data.getPasswordResetRequests().subscribe({ next: value => this.requests.set(value || []), error: () => this.requests.set([]) }); }
  protected initials(name: string): string { return name.split(/\s+/).filter(Boolean).slice(0, 2).map(v => v[0]).join('').toUpperCase(); }

  protected approve(request: ResetRequest): void {
    this.processing.set(request.id);
    this.data.approvePasswordResetRequest(request.id).subscribe({
      next: result => {
        this.requests.update(items => items.filter(item => item.id !== request.id)); this.processing.set(null);
        this.passwordCopied.set(false);
        this.temporaryCredential.set(result as TemporaryCredential);
      },
      error: async err => { this.processing.set(null); await this.showError(err.error?.error || 'No se pudo aprobar la solicitud.'); },
    });
  }

  protected async copyTemporaryPassword(): Promise<void> {
    const password = this.temporaryCredential()?.temporaryPassword;
    if (!password) return;
    try {
      if (navigator.clipboard && window.isSecureContext) await navigator.clipboard.writeText(password);
      else this.copyWithFallback(password);
      this.passwordCopied.set(true);
    } catch {
      this.copyWithFallback(password);
      this.passwordCopied.set(true);
    }
  }

  protected closeTemporaryCredential(): void {
    this.temporaryCredential.set(null);
    this.passwordCopied.set(false);
  }

  private copyWithFallback(value: string): void {
    const field = document.createElement('textarea');
    field.value = value;
    field.setAttribute('readonly', '');
    field.style.position = 'fixed';
    field.style.opacity = '0';
    document.body.appendChild(field);
    field.select();
    document.execCommand('copy');
    document.body.removeChild(field);
  }

  protected async reject(request: ResetRequest): Promise<void> {
    const alert = await this.alerts.create({ header: 'Rechazar solicitud', message: `Indica el motivo para ${request.userName}.`, inputs: [{ name: 'reason', type: 'textarea', placeholder: 'Motivo de rechazo' }], buttons: [{ text: 'Cancelar', role: 'cancel' }, { text: 'Rechazar', role: 'destructive' }] });
    await alert.present(); const result = await alert.onDidDismiss(); if (result.role !== 'destructive') return;
    this.processing.set(request.id);
    this.data.rejectPasswordResetRequest(request.id, result.data?.values?.reason || '').subscribe({ next: () => { this.requests.update(items => items.filter(item => item.id !== request.id)); this.processing.set(null); }, error: async err => { this.processing.set(null); await this.showError(err.error?.error || 'No se pudo rechazar la solicitud.'); } });
  }
  private async showError(message: string): Promise<void> { const alert = await this.alerts.create({ header: 'No se pudo completar', message, buttons: ['Aceptar'] }); await alert.present(); }
}
