import { Injectable, inject } from '@angular/core';
import { CanActivate, Router, RouterStateSnapshot } from '@angular/router';
import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class AuthGuard implements CanActivate {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  canActivate(_route: unknown, state: RouterStateSnapshot): boolean {
    if (this.authService.isAuthenticated()) {
      if (this.authService.currentUser()?.cambioContrasenaRequerido && !state.url.startsWith('/config/password')) {
        this.router.navigate(['/config/password'], { queryParams: { required: 'true' } });
        return false;
      }
      return true;
    }
    this.router.navigate(['/home']);
    return false;
  }
}
