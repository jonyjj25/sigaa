import { Injectable, inject } from '@angular/core';
import { CanActivate, Router, ActivatedRouteSnapshot } from '@angular/router';
import { AuthService } from './auth.service';

const ROLE_MAP: Record<string, string[]> = {
  'inventario': ['Superadmin', 'Director', 'Subdirector'],
  'reporte': ['Superadmin', 'Director', 'Subdirector', 'Jefe de Unidad'],
};

@Injectable({ providedIn: 'root' })
export class RoleGuard implements CanActivate {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  canActivate(route: ActivatedRouteSnapshot): boolean {
    if (!this.authService.isAuthenticated()) {
      this.router.navigate(['/home']);
      return false;
    }

    const path = route.routeConfig?.path ?? '';
    const requiredRoles = ROLE_MAP[path];

    if (!requiredRoles) return true;

    const hasAccess = requiredRoles.some(role => this.authService.hasRole(role));
    if (hasAccess) return true;

    this.router.navigate(['/dashboard']);
    return false;
  }
}
