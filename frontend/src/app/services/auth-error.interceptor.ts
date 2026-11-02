import { Injectable, inject } from '@angular/core';
import { HttpErrorResponse, HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

@Injectable()
export class AuthErrorInterceptor implements HttpInterceptor {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  intercept(request: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    const authorization = request.headers.get('Authorization');
    const bearerToken = authorization?.startsWith('Bearer ') ? authorization.slice('Bearer '.length) : null;
    // Las cookies no son legibles desde JS: asociar la respuesta a la sesión
    // local que existía al enviar la solicitud, también cuando no usa Bearer.
    const sessionToken = bearerToken ?? this.auth.getToken();
    const sessionUser = this.auth.currentUser();
    const usesSession = !!bearerToken || (request.withCredentials && (!!sessionToken || !!sessionUser));
    const publicAuthRequest = /\/api\/auth\/(login|2fa\/authenticate|forgot-password|resend-reset-code|verify-reset-code|reset-password|logout)(?:[/?#]|$)/.test(request.url);
    return next.handle(request).pipe(catchError((error: HttpErrorResponse) => {
      if (usesSession && !publicAuthRequest) {
        const sameSession = sessionToken === this.auth.getToken()
          && (sessionToken !== null || sessionUser === this.auth.currentUser());
        if (sameSession && error.status === 401) {
          this.auth.removeToken();
          this.router.navigate(['/home'], { queryParams: { sessionExpired: 'true' } });
        } else if (sameSession && error.status === 403 && error.error?.code === 'PASSWORD_CHANGE_REQUIRED') {
          this.auth.requirePasswordChange();
        }
      }
      return throwError(() => error);
    }));
  }
}
