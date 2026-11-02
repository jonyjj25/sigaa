import { Component, ChangeDetectionStrategy, signal, inject, OnInit, DestroyRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IonIcon, IonModal, AlertController } from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import {
  createOutline, trashOutline, addOutline, businessOutline,
  locationOutline, locateOutline, closeOutline,
} from 'ionicons/icons';
import { ConfigDataService } from '../services/config-data.service';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Ubicacion } from '../models/config.models';

@Component({
  selector: 'app-ubicacion-list',
  standalone: true,
  imports: [CommonModule, IonIcon, IonModal],
  templateUrl: './ubicacion-list.component.html',
  styleUrls: ['./ubicacion-list.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UbicacionListComponent implements OnInit {
  private dataService = inject(ConfigDataService);
  private destroyRef = inject(DestroyRef);
  private alertCtrl = inject(AlertController);

  protected ubicaciones = signal<Ubicacion[]>([]);
  protected showModal = signal(false);
  protected editingUbicacion = signal<Ubicacion | null>(null);
  protected obteniendoUbicacion = signal(false);
  protected errorUbicacion = signal('');

  protected formNombre = signal('');
  protected formDireccion = signal('');
  protected formLatitud = signal(0);
  protected formLongitud = signal(0);
  protected formRadio = signal(200);

  constructor() {
    addIcons({
      createOutline, trashOutline, addOutline, businessOutline,
      locationOutline, locateOutline, closeOutline,
    });
  }

  ngOnInit(): void {
    this.dataService.getUbicaciones().pipe(takeUntilDestroyed(this.destroyRef)).subscribe(data => this.ubicaciones.set(data));
  }

  /* ---- Geolocation ---- */
  protected obtenerUbicacionActual(): void {
    if (!navigator.geolocation) {
      this.errorUbicacion.set('Geolocalización no soportada por el navegador');
      return;
    }
    this.obteniendoUbicacion.set(true);
    this.errorUbicacion.set('');
    navigator.geolocation.getCurrentPosition(
      pos => {
        this.formLatitud.set(Math.round(pos.coords.latitude * 10000) / 10000);
        this.formLongitud.set(Math.round(pos.coords.longitude * 10000) / 10000);
        this.obteniendoUbicacion.set(false);
      },
      err => {
        this.obteniendoUbicacion.set(false);
        switch (err.code) {
          case err.PERMISSION_DENIED:
            this.errorUbicacion.set('Permiso denegado para acceder a la ubicación');
            break;
          case err.POSITION_UNAVAILABLE:
            this.errorUbicacion.set('Ubicación no disponible');
            break;
          case err.TIMEOUT:
            this.errorUbicacion.set('Tiempo de espera agotado');
            break;
          default:
            this.errorUbicacion.set('Error al obtener la ubicación');
        }
      },
      { enableHighAccuracy: true, timeout: 10000 },
    );
  }

  /* ---- Formato ---- */
  protected formatearCoordenadas(lat: number, lng: number): string {
    const latDir = lat >= 0 ? 'N' : 'S';
    const lngDir = lng >= 0 ? 'E' : 'O';
    return `${Math.abs(lat).toFixed(4)}° ${latDir}, ${Math.abs(lng).toFixed(4)}° ${lngDir}`;
  }

  /* ---- Modal ---- */
  protected abrirModalNuevo(): void {
    this.editingUbicacion.set(null);
    this.formNombre.set('');
    this.formDireccion.set('');
    this.formLatitud.set(0);
    this.formLongitud.set(0);
    this.formRadio.set(200);
    this.errorUbicacion.set('');
    this.showModal.set(true);
  }

  protected abrirModalEditar(u: Ubicacion): void {
    this.editingUbicacion.set(u);
    this.formNombre.set(u.nombre);
    this.formDireccion.set(u.direccion);
    this.formLatitud.set(u.latitud);
    this.formLongitud.set(u.longitud);
    this.formRadio.set(u.radio);
    this.errorUbicacion.set('');
    this.showModal.set(true);
  }

  protected cerrarModal(): void {
    this.showModal.set(false);
    this.editingUbicacion.set(null);
  }

  protected guardarUbicacion(): void {
    const data = {
      nombre: this.formNombre(),
      direccion: this.formDireccion(),
      latitud: this.formLatitud(),
      longitud: this.formLongitud(),
      radio: this.formRadio(),
    };

    const edit = this.editingUbicacion();
    if (edit) {
      this.dataService.updateUbicacion(edit.id, data).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(u => {
        this.ubicaciones.update(list => list.map(item => item.id === u.id ? u : item));
        this.cerrarModal();
      });
    } else {
      this.dataService.createUbicacion(data).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(u => {
        this.ubicaciones.update(list => [...list, u]);
        this.cerrarModal();
      });
    }
  }

  protected confirmarEliminar(id: string): void {
    this.alertCtrl.create({
      header: 'Confirmar',
      message: '¿Eliminar esta sede?',
      buttons: [
        { text: 'Cancelar', role: 'cancel' },
        {
          text: 'Eliminar',
          role: 'destructive',
          handler: () => {
            this.dataService.deleteUbicacion(id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
              next: () => this.ubicaciones.update(list => list.filter(u => u.id !== id)),
            });
          },
        },
      ],
    }).then(a => a.present());
  }

  protected toNumber(v: string): number {
    return Number(v);
  }
}
