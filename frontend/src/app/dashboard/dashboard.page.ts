import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService, UserInfo } from '../services/auth.service';
import { CommonModule } from '@angular/common';
import {
  IonButton,
  IonContent,
  IonHeader,
  IonItem,
  IonLabel,
  IonTitle,
  IonToolbar,
} from '@ionic/angular/standalone';

@Component({
  selector: 'app-dashboard',
  templateUrl: 'dashboard.page.html',
  styleUrls: ['dashboard.page.scss'],
  imports: [CommonModule, IonHeader, IonToolbar, IonTitle, IonContent, IonItem, IonLabel, IonButton],
})
export class DashboardPage {
  usuario: UserInfo | null = null;
  error = '';
  isLoading = false;

  constructor(private authService: AuthService, private router: Router) {
    if (!this.authService.isAuthenticated()) {
      this.authService.logout();
    } else {
      this.loadUserInfo();
    }
  }

  async loadUserInfo() {
    this.isLoading = true;
    try {
      const response = await fetch('http://localhost:8080/api/auth/me', {
        method: 'GET',
        headers: this.authService.getAuthHeaders(),
      });

      if (response.ok) {
        this.usuario = await response.json();
      } else {
        this.authService.logout();
      }
    } catch (error) {
      this.error = 'No se pudo cargar la información del usuario.';
    } finally {
      this.isLoading = false;
    }
  }

  logout() {
    this.authService.logout();
  }
}
