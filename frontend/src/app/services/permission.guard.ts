import { Injectable, inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Router } from '@angular/router';
import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class PermissionGuard implements CanActivate {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  canActivate(route: ActivatedRouteSnapshot): boolean {
    if (!this.authService.isAuthenticated()) {
      this.router.navigate(['/home']);
      return false;
    }

    const modulo = route.data?.['permissionModule'] as string | undefined;
    const accion = (route.data?.['permissionAction'] as string | undefined) || 'Ver';
    if (!modulo || this.authService.hasPermission(modulo, accion)) return true;

    this.router.navigate(['/dashboard'], { queryParams: { acceso: 'denegado' } });
    return false;
  }
}
