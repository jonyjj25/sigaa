import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import {
  IonButton,
  IonContent,
  IonHeader,
  IonInput,
  IonItem,
  IonLabel,
  IonTitle,
  IonToolbar,
} from '@ionic/angular/standalone';
import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-home',
  templateUrl: 'home.page.html',
  styleUrls: ['home.page.scss'],
  imports: [CommonModule, FormsModule, IonHeader, IonToolbar, IonTitle, IonContent, IonItem, IonLabel, IonInput, IonButton],
})
export class HomePage {
  correo = '';
  password = '';
  message = '';
  isLoading = false;

  constructor(private authService: AuthService, private router: Router) {
    if (this.authService.isAuthenticated()) {
      this.router.navigate(['/dashboard']);
    }
  }

  async login() {
    this.message = '';
    this.isLoading = true;

    try {
      const response = await fetch('http://localhost:8080/api/auth/login', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ correo: this.correo, password: this.password }),
      });

      if (response.ok) {
        const body = await response.json();
        if (body && body.token) {
          this.authService.saveToken(body.token);
          this.router.navigate(['/dashboard']);
          return;
        }
      } else if (response.status === 401) {
        this.message = 'Correo o contraseña incorrectos';
      } else {
        this.message = 'Error de conexión con el servidor';
      }
    } catch (error) {
      this.message = 'No se pudo conectar con el servidor';
    } finally {
      this.isLoading = false;
    }
  }
}
