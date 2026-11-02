import { Injectable, inject } from '@angular/core';
import { CanActivate, Router } from '@angular/router';
import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class AdminGuard implements CanActivate {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  canActivate(): boolean {
    if (this.authService.isAuthenticated() && this.authService.isSuperAdmin()) {
      return true;
    }
    if (this.authService.isAuthenticated()) {
      this.router.navigate(['/config/profile']);
      return false;
    }
    this.router.navigate(['/home']);
    return false;
  }
}
