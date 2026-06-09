import { Injectable } from '@angular/core';
import { Router } from '@angular/router';

export interface UserInfo {
  nombre: string;
  correo: string;
  roles: string;
}

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  constructor(private router: Router) {}

  saveToken(token: string) {
    localStorage.setItem('sigaa_token', token);
  }

  getToken(): string | null {
    return localStorage.getItem('sigaa_token');
  }

  removeToken() {
    localStorage.removeItem('sigaa_token');
  }

  isAuthenticated(): boolean {
    const token = this.getToken();
    return token !== null && !this.isTokenExpired(token);
  }

  logout() {
    this.removeToken();
    this.router.navigate(['/home']);
  }

  getAuthHeaders(): Record<string, string> {
    const token = this.getToken();
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
    };
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
    return headers;
  }

  private decodePayload(token: string): any {
    try {
      const payload = token.split('.')[1];
      return JSON.parse(atob(payload));
    } catch {
      return null;
    }
  }

  private isTokenExpired(token: string): boolean {
    const payload = this.decodePayload(token);
    if (!payload || !payload.exp) {
      return true;
    }
    return Date.now() / 1000 > payload.exp;
  }
}
