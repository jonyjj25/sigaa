import { Injectable } from '@angular/core';
import { environment } from '../../environments/environment';

export interface PasswordResetResponse { ok: boolean; status: number; message?: string; resetToken?: string; }

@Injectable({ providedIn: 'root' })
export class PasswordResetService {
  requestPasswordReset(correo: string) { return this.post('/api/auth/forgot-password', { correo }); }
  resendPasswordResetCode(correo: string) { return this.post('/api/auth/resend-reset-code', { correo }); }
  verifyPasswordResetCode(correo: string, code: string) { return this.post('/api/auth/verify-reset-code', { correo, code }); }
  resetPassword(resetToken: string, newPassword: string) { return this.post('/api/auth/reset-password', { resetToken, newPassword }); }

  private async post(path: string, body: object): Promise<PasswordResetResponse> {
    try {
      const response = await fetch(`${environment.apiUrl}${path}`, { method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
      const data = await response.json().catch(() => ({}));
      return { ok: response.ok, status: response.status, message: data.message || data.error, resetToken: data.resetToken };
    } catch {
      return { ok: false, status: 0, message: 'No se pudo conectar con el servidor.' };
    }
  }
}
