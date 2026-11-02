import { Injectable, OnDestroy, inject, signal } from '@angular/core';
import { Observable, Subject, catchError, defer, finalize, map, of, shareReplay, throwError, timeout } from 'rxjs';
import { Router } from '@angular/router';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { UserInfo, UserPreferences, DEFAULT_PREFERENCES } from '../models/dashboard.models';
import { canonicalRole } from '../shared/institutional-roles';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class AuthService implements OnDestroy {
  readonly currentUser = signal<UserInfo | null>(null);
  readonly preferences = signal<UserPreferences>({ ...DEFAULT_PREFERENCES });
  readonly effectiveTheme = signal<'light' | 'dark'>('light');
  readonly sidebarCollapsed = signal(this.isCompactViewport());
  private readonly sessionChangedSubject = new Subject<void>();
  readonly sessionChanged$ = this.sessionChangedSubject.asObservable();
  private readonly router = inject(Router);
  private readonly http = inject(HttpClient);
  private systemThemeMql: MediaQueryList | null = null;
  private sessionSyncTimer: ReturnType<typeof setInterval> | null = null;
  private sessionRefresh?: { session: string; request: Observable<UserInfo | null> };
  private readonly handleWindowFocus = () => this.syncSessionIfVisible();
  private readonly handleVisibilityChange = () => this.syncSessionIfVisible();

  constructor() {
    this.loadUserFromStorage();
    this.loadPreferencesFromStorage();
    this.applyPreferences();
    this.startSessionSync();
  }

  saveToken(token: string) {
    sessionStorage.setItem('sigaa_token', token);
    localStorage.removeItem('sigaa_token');
  }

  getToken(): string | null {
    return sessionStorage.getItem('sigaa_token');
  }

  removeToken() {
    this.stopSessionSync();
    this.sessionRefresh = undefined;
    sessionStorage.removeItem('sigaa_token');
    localStorage.removeItem('sigaa_token');
    localStorage.removeItem('sigaa_user');
    localStorage.removeItem('sigaa_preferences');
    this.currentUser.set(null);
    this.preferences.set({ ...DEFAULT_PREFERENCES });
    this.sidebarCollapsed.set(this.isCompactViewport());
    this.sessionChangedSubject.next();
  }

  isAuthenticated(): boolean {
    const token = this.getToken();
    return !!token && !this.isTokenExpired(token);
  }

  logout() {
    this.http.post(`${environment.apiUrl}/api/auth/logout`, {}, {
      headers: this.getAuthHeaders(), withCredentials: true,
    }).subscribe({
      error: () => undefined,
    });
    this.removeToken();
    this.router.navigate(['/home']);
  }

  saveUserInfo(user: UserInfo): void {
    if (user.cambioContrasenaRequerido) {
      user = { nombre: user.nombre, correo: user.correo, roles: '', permisos: [], cambioContrasenaRequerido: true };
    }
    localStorage.setItem('sigaa_user', JSON.stringify(user));
    this.currentUser.set(user);
    this.sessionChangedSubject.next();
    this.startSessionSync();
  }

  requirePasswordChange(): void {
    const user = this.currentUser();
    if (user && !user.cambioContrasenaRequerido) this.saveUserInfo({ ...user, cambioContrasenaRequerido: true });
    if (!this.router.url?.startsWith('/config/password')) {
      this.router.navigate(['/config/password'], { queryParams: { required: 'true' } });
    }
  }

  refreshCurrentUser(): Observable<UserInfo | null> {
    return defer(() => {
      if (!this.isAuthenticated()) return of(null);
      const sessionToken = this.getToken()!;
      if (this.sessionRefresh?.session === sessionToken) return this.sessionRefresh.request;
      // Compartir solo la consulta en curso. Al terminar, la siguiente vuelve
      // al servidor para obtener los permisos vigentes, sin caché de sesión.
      const request = this.http.get<UserInfo>(`${environment.apiUrl}/api/auth/me`, {
        headers: this.getAuthHeaders(),
        withCredentials: true,
      }).pipe(
        timeout(15_000),
        map(remote => {
          // Una respuesta tardía no debe restaurar un usuario tras cerrar sesión
          // ni reemplazar al usuario de una sesión nueva.
          if (sessionToken !== this.getToken()) return null;
          const current = this.currentUser();
          const updated: UserInfo = { ...current, ...remote } as UserInfo;
          if (this.userSignature(current) !== this.userSignature(updated)) this.saveUserInfo(updated);
          if (remote.cambioContrasenaRequerido) this.requirePasswordChange();
          return this.currentUser();
        }),
        // El interceptor invalida la sesión. Los demás errores se propagan para
        // que el consumidor distinga un fallo temporal de una sesión rechazada.
        catchError((error: unknown) => error instanceof HttpErrorResponse && error.status === 401
          ? of(null) : throwError(() => error)),
        finalize(() => { if (this.sessionRefresh?.request === request) this.sessionRefresh = undefined; }),
        shareReplay({ bufferSize: 1, refCount: true }),
      );
      this.sessionRefresh = { session: sessionToken, request };
      return request;
    });
  }

  ngOnDestroy(): void {
    this.stopSessionSync();
  }

  getAuthHeaders(): Record<string, string> {
    const token = this.getToken();
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (token) headers['Authorization'] = `Bearer ${token}`;
    return headers;
  }

  hasRole(role: string): boolean {
    const user = this.currentUser();
    if (!user?.roles) return false;
    const normalized = canonicalRole(role).toLowerCase();
    const rolesList = Array.isArray(user.roles) ? user.roles : user.roles.split(',');
    return rolesList.some((r: string) => canonicalRole(r).toLowerCase() === normalized);
  }

  hasPermission(modulo: string, accion: string): boolean {
    const user = this.currentUser();
    if (this.isSuperAdmin()) return true;
    if (!user?.permisos) return false;
    return user.permisos.includes(`${modulo}:${accion}`);
  }

  isAdmin(): boolean {
    return this.isSuperAdmin() || this.hasRole('Director');
  }

  isInstitutionalManager(): boolean {
    return this.isAdmin() || this.hasRole('Director') || this.hasRole('Subdirector');
  }

  isSuperAdmin(): boolean {
    const roles = this.currentUser()?.roles;
    const values = Array.isArray(roles) ? roles : (roles || '').split(',');
    return values.some(role => {
      const value = String(role).trim();
      return value.toLowerCase() === 'superadmin';
    });
  }

  isUnitAdmin(): boolean {
    return this.hasRole('Jefe de Unidad');
  }

  updatePhoto(fotografiaRuta: string): void {
    const user = this.currentUser();
    if (user) {
      const updated = { ...user, fotografiaRuta };
      this.saveUserInfo(updated);
    }
  }

  has2fa(): boolean {
    return this.currentUser()?.has2fa ?? false;
  }

  updatePreferences(prefs: UserPreferences): void {
    const normalized = { ...DEFAULT_PREFERENCES, ...prefs, homePage: this.allowedHomePage(prefs.homePage) };
    this.preferences.set(normalized);
    localStorage.setItem('sigaa_preferences', JSON.stringify(normalized));
    this.applyPreferences();
  }

  allowedHomePage(homePage: string | null | undefined): string {
    const requested = (homePage || 'dashboard').replace(/^\//, '');
    const common = new Set(['dashboard', 'actas', 'tareas', 'reuniones', 'documentos', 'grupos']);
    if (this.hasPermission('Notas', 'Ver') || this.hasPermission('Memos', 'Ver')) common.add('correspondencia');
    if (this.hasPermission('Notas', 'Ver')) common.add('notas');
    if (this.hasPermission('Memos', 'Ver')) common.add('memos');
    if (this.hasPermission('Inventario', 'Ver')) common.add('inventario');
    if (this.hasPermission('Reporte', 'Ver')) common.add('reporte');
    return common.has(requested) ? requested : 'dashboard';
  }

  formatDate(value: string | number | Date, options?: Intl.DateTimeFormatOptions): string {
    const prefs = this.preferences();
    const locale = prefs.language === 'en' ? 'en-US' : prefs.language === 'pt' ? 'pt-BR' : 'es-PA';
    const ordering: Record<string, Intl.DateTimeFormatOptions> = {
      'DD/MM/YYYY': { day: '2-digit', month: '2-digit', year: 'numeric' },
      'MM/DD/YYYY': { month: '2-digit', day: '2-digit', year: 'numeric' },
      'YYYY-MM-DD': { year: 'numeric', month: '2-digit', day: '2-digit' },
    };
    return new Intl.DateTimeFormat(locale, { timeZone: prefs.timezone, ...(ordering[prefs.dateFormat] || ordering['DD/MM/YYYY']), ...options }).format(new Date(value));
  }

  formatTime(value: string | number | Date): string {
    const prefs = this.preferences();
    const locale = prefs.language === 'en' ? 'en-US' : prefs.language === 'pt' ? 'pt-BR' : 'es-PA';
    return new Intl.DateTimeFormat(locale, { timeZone: prefs.timezone, hour: '2-digit', minute: '2-digit', hour12: prefs.timeFormat === 'hh:mm A' }).format(new Date(value));
  }

  private loadPreferencesFromStorage(): void {
    const stored = localStorage.getItem('sigaa_preferences');
    if (stored) {
      try {
        const parsed = JSON.parse(stored) as Partial<UserPreferences>;
        this.preferences.set({ ...DEFAULT_PREFERENCES, ...parsed });
      } catch {
        localStorage.removeItem('sigaa_preferences');
      }
    }
  }

  private loadUserFromStorage(): void {
    const stored = localStorage.getItem('sigaa_user');
    if (stored) {
      try {
        this.currentUser.set(JSON.parse(stored));
      } catch {
        localStorage.removeItem('sigaa_user');
      }
    }
  }

  private startSessionSync(): void {
    if (this.sessionSyncTimer || !this.isAuthenticated() || typeof window === 'undefined') return;
    this.sessionSyncTimer = setInterval(() => this.syncSessionIfVisible(), 10000);
    window.addEventListener('focus', this.handleWindowFocus);
    document.addEventListener('visibilitychange', this.handleVisibilityChange);
  }

  private stopSessionSync(): void {
    if (this.sessionSyncTimer) clearInterval(this.sessionSyncTimer);
    this.sessionSyncTimer = null;
    if (typeof window !== 'undefined') {
      window.removeEventListener('focus', this.handleWindowFocus);
      document.removeEventListener('visibilitychange', this.handleVisibilityChange);
    }
  }

  private syncSessionIfVisible(): void {
    if (!this.isAuthenticated() || (typeof document !== 'undefined' && document.hidden)) return;
    this.refreshCurrentUser().subscribe({
      error: (error: unknown) => console.warn('No se pudo sincronizar la sesión; se reintentará en la próxima consulta.', error),
    });
  }

  private userSignature(user: UserInfo | null): string {
    if (!user) return '';
    const roles = Array.isArray(user.roles) ? [...user.roles].sort() : user.roles;
    const permisos = [...(user.permisos || [])].sort();
    return JSON.stringify({ ...user, roles, permisos });
  }

  private applyPreferences(): void {
    const prefs = this.preferences();
    this.sidebarCollapsed.set(this.isCompactViewport() || !prefs.sidebarExpanded);
    this.applyTheme(prefs.theme);
    this.applyFontSize(prefs.fontSize);
    this.applyHighContrast(prefs.highContrast);
    this.applyAnimations(prefs.animationsEnabled);
    this.applyRegionalPreferences(prefs);
  }

  private isCompactViewport(): boolean {
    return typeof window !== 'undefined' && window.innerWidth <= 768;
  }

  private applyTheme(theme: string): void {
    const root = document.documentElement;
    const setEffectiveTheme = (value: 'light' | 'dark') => {
      root.setAttribute('data-theme', value);
      this.effectiveTheme.set(value);
    };
    if (this.systemThemeMql) {
      this.systemThemeMql.onchange = null;
      this.systemThemeMql = null;
    }
    if (theme === 'system') {
      this.systemThemeMql = window.matchMedia('(prefers-color-scheme: dark)');
      const applySystem = (e: MediaQueryListEvent | MediaQueryList) => {
        setEffectiveTheme(e.matches ? 'dark' : 'light');
      };
      applySystem(this.systemThemeMql);
      this.systemThemeMql.onchange = applySystem;
    } else {
      setEffectiveTheme(theme === 'dark' ? 'dark' : 'light');
    }
  }

  private applyFontSize(fontSize: string): void {
    const sizes: Record<string, string> = {
      small: '13px',
      medium: '14px',
      large: '16px',
      'x-large': '18px',
    };
    document.documentElement.style.fontSize = sizes[fontSize] || '14px';
  }

  private applyHighContrast(highContrast: boolean): void {
    document.documentElement.setAttribute('data-high-contrast', String(highContrast));
  }

  private applyAnimations(enabled: boolean): void {
    document.documentElement.setAttribute('data-no-animations', String(!enabled));
  }

  private applyRegionalPreferences(prefs: UserPreferences): void {
    const root = document.documentElement;
    root.lang = prefs.language || 'es';
    root.setAttribute('data-timezone', prefs.timezone || 'America/Panama');
    root.setAttribute('data-date-format', prefs.dateFormat || 'DD/MM/YYYY');
    root.setAttribute('data-time-format', prefs.timeFormat || 'HH:mm');
    root.style.setProperty('--records-per-page', String(prefs.recordsPerPage || 20));
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
    if (!payload || !payload.exp) return true;
    return Date.now() / 1000 > payload.exp;
  }
}
