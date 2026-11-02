import { Component, inject, OnInit, signal, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { IonIcon } from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { checkmarkCircleOutline, personOutline, helpCircleOutline } from 'ionicons/icons';
import { AsistenciaService } from '../../services/asistencia.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-asistencia-publica',
  standalone: true,
  imports: [CommonModule, FormsModule, IonIcon],
  templateUrl: './asistencia-publica.page.html',
  styleUrls: ['./asistencia-publica.page.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AsistenciaPublicaPage implements OnInit {
  private static readonly PENDING_ATTENDANCE_KEY = 'sigaa_pending_attendance_code';
  private readonly route = inject(ActivatedRoute);
  private readonly asistenciaService = inject(AsistenciaService);
  protected readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  protected codigo = signal('');
  protected sessionName = signal('');
  protected sessionLoading = signal(true);
  protected sessionNotFound = signal(false);

  protected modo = signal<'usuario' | 'invitado'>('usuario');
  protected registrado = signal(false);
  protected error = signal('');
  protected enviando = signal(false);
  protected successMessage = signal('');

  /* Invitado mode */
  protected formData = {
    nombre: '',
    correo: '',
    telefono: '',
    institucion: '',
    cargo: '',
    aceptaTerminos: false,
  };

  constructor() {
    addIcons({ checkmarkCircleOutline, personOutline, helpCircleOutline });
  }

  ngOnInit(): void {
    this.codigo.set(this.route.snapshot.paramMap.get('codigo') || '');
    this.loadSession();
  }

  private loadSession(): void {
    this.sessionLoading.set(true);
    this.asistenciaService.getSesionPorCodigo(this.codigo()).subscribe({
      next: (sesion) => {
        this.sessionLoading.set(false);
        if (sesion) {
          this.sessionName.set(sesion.nombre);
          this.sessionNotFound.set(false);
          this.completePendingRegistration();
        } else {
          this.sessionNotFound.set(true);
        }
      },
      error: (err) => {
        console.error('[asistencia-publica] loadSession error:', err);
        this.sessionLoading.set(false);
        this.sessionNotFound.set(true);
      },
    });
  }

  protected setModo(m: 'usuario' | 'invitado'): void {
    this.modo.set(m);
    this.error.set('');
  }

  protected registrarUsuario(): void {
    if (!this.authService.isAuthenticated()) {
      sessionStorage.setItem(AsistenciaPublicaPage.PENDING_ATTENDANCE_KEY, this.codigo());
      this.router.navigate(['/home'], { queryParams: { returnUrl: `/asistencia/publica/${this.codigo()}` } });
      return;
    }
    this.enviando.set(true);
    this.error.set('');
    const currentUser = this.authService.currentUser();
    if (!currentUser?.id) {
      this.enviando.set(false);
      this.error.set('No se pudo identificar tu sesión. Inicia sesión nuevamente.');
      return;
    }
    this.asistenciaService.escanearQr(currentUser.id, this.codigo()).subscribe({
      next: (res) => {
        this.enviando.set(false);
        if (res.success) {
          this.registrado.set(true);
          this.successMessage.set(res.message);
        } else {
          this.error.set(res.message);
        }
      },
      error: (e) => {
        this.enviando.set(false);
        console.error('[asistencia-publica] registrarUsuario HTTP error:', e);
        this.error.set(e?.error?.error || 'Error al registrar asistencia');
      },
    });
  }

  private completePendingRegistration(): void {
    if (!this.authService.isAuthenticated()) return;
    const pendingCode = sessionStorage.getItem(AsistenciaPublicaPage.PENDING_ATTENDANCE_KEY);
    if (pendingCode !== this.codigo()) return;
    sessionStorage.removeItem(AsistenciaPublicaPage.PENDING_ATTENDANCE_KEY);
    this.registrarUsuario();
  }

  protected registrarInvitado(): void {
    if (!this.formData.nombre || !this.formData.correo) {
      this.error.set('Completa los campos obligatorios (nombre y correo)');
      return;
    }
    if (!this.formData.aceptaTerminos) {
      this.error.set('Debes aceptar los términos');
      return;
    }
    this.enviando.set(true);
    this.error.set('');
    const payload = {
      codigoSesion: this.codigo(),
      nombre: this.formData.nombre,
      correo: this.formData.correo,
      telefono: this.formData.telefono,
      institucion: this.formData.institucion,
      cargo: this.formData.cargo,
    };
    this.asistenciaService.registrarInvitado(payload).subscribe({
      next: (res) => {
        this.enviando.set(false);
        if (res.success) {
          this.registrado.set(true);
          this.successMessage.set(res.message);
        } else {
          this.error.set(res.message);
        }
      },
      error: (e) => {
        this.enviando.set(false);
        console.error('[asistencia-publica] registrarInvitado HTTP error:', e);
        this.error.set(e?.error?.error || 'Error al registrar invitado');
      },
    });
  }
}
