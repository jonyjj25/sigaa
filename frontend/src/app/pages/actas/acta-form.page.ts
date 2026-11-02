import { Component, inject, OnInit, signal, computed, ChangeDetectionStrategy, DestroyRef, ViewChild, ElementRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { lastValueFrom } from 'rxjs';
import { Router, ActivatedRoute } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { IonIcon } from '@ionic/angular/standalone';
import { ViewWillEnter } from '@ionic/angular';
import { AlertController } from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import {
  gridOutline, peopleOutline, clipboardOutline,
  cubeOutline, barChartOutline, settingsOutline, logOutOutline,
  documentTextOutline, qrCodeOutline, folderOutline, calendarOutline,
  notificationsOutline, personCircleOutline, menuOutline,
  arrowBackOutline, addOutline, closeOutline, saveOutline,
  eyeOutline, downloadOutline, printOutline, checkmarkOutline,
  createOutline, ellipsisVerticalOutline, timeOutline, locationOutline,
  searchOutline, filterOutline, chevronBackOutline, chevronForwardOutline,
  chevronUpOutline, chevronDownOutline,
  attachOutline, linkOutline, pencilOutline, flagOutline,
  checkmarkCircleOutline, alertCircleOutline, bulbOutline,
  moveOutline, trashOutline, download as downloadIcon,
  reorderTwoOutline, informationCircleOutline, listOutline, cloudUploadOutline,
  imageOutline, documentOutline,
} from 'ionicons/icons';
import { DashboardSidebarComponent } from '../../components/dashboard-sidebar/dashboard-sidebar.component';
import { DashboardTopbarComponent } from '../../components/dashboard-topbar/dashboard-topbar.component';
import { DashboardService } from '../../services/dashboard.service';
import { AuthService } from '../../services/auth.service';
import { ApiService } from '../../services/api.service';
import { Notification } from '../../models/dashboard.models';

@Component({
  selector: 'app-acta-form',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    DashboardSidebarComponent, DashboardTopbarComponent,
    IonIcon,
  ],
  templateUrl: './acta-form.page.html',
  styleUrls: ['./acta-form.page.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ActaFormPage implements OnInit, ViewWillEnter {
  private readonly dashboardService = inject(DashboardService);
  protected readonly authService = inject(AuthService);
  private readonly apiService = inject(ApiService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly alertCtrl = inject(AlertController);
  private readonly destroyRef = inject(DestroyRef);

  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;

  protected readonly menuItems = computed(() => this.dashboardService.getMenuItems());
  protected readonly notifications = signal<Notification[]>([]);
  protected readonly currentUser = this.authService.currentUser;
  protected readonly responsables = signal<Array<{nombre: string; cargo: string; departamento: string}>>([]);

  /* ── Edit mode ── */
  protected readonly editandoId = signal<string | null>(null);
  protected readonly esEdicion = computed(() => !!this.editandoId());

  protected puedeGuardar(): boolean {
    if (this.route.snapshot.queryParams['id'] && !this.esEdicion()) return false;
    return this.esEdicion()
      ? this.authService.hasPermission('Actas', 'Editar')
      : this.authService.hasPermission('Actas', 'Crear') || this.authService.hasPermission('Reuniones', 'Generar acta');
  }

  /* ── Form signals ── */
  protected readonly formTitulo = signal('');
  protected readonly formTipo = signal('');
  protected readonly formLugar = signal('');
  protected readonly formModalidad = signal('Presencial');
  protected readonly formResponsable = signal('');
  protected readonly formObjetivos = signal('');
  protected readonly formInstitucionParticipantes = signal('');
  protected readonly formEstado = signal('Borrador');
  protected readonly formFecha = signal('');
  protected readonly formHora = signal('');

  protected readonly formValido = computed(() =>
    !!this.formTitulo() && !!this.formTipo() && !!this.formFecha()
  );

  /* ── Participantes ── */
  protected readonly participantes = signal<Array<{nombre: string; apellido: string; area: string; asistio: boolean; correo: string; institucion: string; cargo: string}>>([]);

  /* ── Orden del día ── */
  protected readonly ordenDia = signal<string[]>([]);

  /* ── Acuerdos ── */
  protected readonly acuerdos = signal<Array<{descripcion: string; responsable: string; estado: string; fechaLimite?: string}>>([]);

  /* ── Compromisos ── */
  protected readonly compromisos = signal<Array<{actividad: string; responsable: string; estado: string; fechaLimite?: string}>>([]);

  /* ── Adjuntos ── */
  protected readonly adjuntos = signal<Array<{nombre: string; tipo: string; tamano: string; archivo?: File}>>([]);

  /* ── Nuevo acuerdo form ── */
  protected readonly nuevoAcuerdoDesc = signal('');
  protected readonly nuevoAcuerdoResp = signal('');

  /* ── Nuevo compromiso form ── */
  protected readonly nuevoCompActividad = signal('');
  protected readonly nuevoCompResp = signal('');

  /* ── Sesión de asistencia QR ── */
  protected readonly formVincularSesion = signal(false);
  protected readonly formSesionExistente = signal(false);
  protected readonly formSesionId = signal<string>('');
  protected readonly sesionesDisponibles = signal<Array<{id: string; codigo: string; nombre: string; fecha: string}>>([]);

  constructor() {
    addIcons({
      gridOutline, peopleOutline, clipboardOutline,
      cubeOutline, barChartOutline, settingsOutline, logOutOutline,
      documentTextOutline, qrCodeOutline, folderOutline, calendarOutline,
      notificationsOutline, personCircleOutline, menuOutline,
      arrowBackOutline, addOutline, closeOutline, saveOutline,
      eyeOutline, downloadOutline, printOutline, checkmarkOutline,
      createOutline, ellipsisVerticalOutline, timeOutline, locationOutline,
      searchOutline, filterOutline, chevronBackOutline, chevronForwardOutline,
      chevronUpOutline, chevronDownOutline,
      attachOutline, linkOutline, pencilOutline, flagOutline,
      checkmarkCircleOutline, alertCircleOutline, bulbOutline,
      moveOutline, trashOutline, downloadIcon,
      reorderTwoOutline, informationCircleOutline, listOutline, cloudUploadOutline,
      imageOutline, documentOutline,
    });
  }

  ngOnInit(): void {
    if (!this.authService.isAuthenticated()) {
      this.router.navigate(['/home']);
    }
    this.dashboardService.getNotificaciones().pipe(takeUntilDestroyed(this.destroyRef)).subscribe(d => this.notifications.set(d));
    this.cargarResponsables();

    this.apiService.get<any[]>('/api/asistencia/sesiones').pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (list) => this.sesionesDisponibles.set(list.map(s => ({
        id: String(s.id),
        codigo: s.codigo,
        nombre: s.nombre,
        fecha: s.fecha,
      }))),
      error: () => {},
    });

    const actaId = this.route.snapshot.queryParams['id'];
    if (actaId) {
      this.loadActa(actaId);
    } else {
      this.loadReunion(this.route.snapshot.queryParams['reunionId']);
    }
  }

  ionViewWillEnter(): void {
    this.editandoId.set(null);
    this.formTitulo.set('');
    this.formTipo.set('');
    this.formLugar.set('');
    this.formResponsable.set('');
    this.formObjetivos.set('');
    this.formInstitucionParticipantes.set('');
    this.formEstado.set('Borrador');
    this.formFecha.set('');
    this.formHora.set('');
    this.ordenDia.set([]);
    this.participantes.set([]);
    this.acuerdos.set([]);
    this.compromisos.set([]);
    this.adjuntos.set([]);
    this.formVincularSesion.set(false);
    this.formSesionExistente.set(false);
    this.formSesionId.set('');

    this.apiService.get<any[]>('/api/asistencia/sesiones')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (list) => this.sesionesDisponibles.set(list.map(s => ({
          id: String(s.id), codigo: s.codigo,
          nombre: s.nombre, fecha: s.fecha,
        }))),
        error: () => {},
      });

    const actaId = this.route.snapshot.queryParams['id'];
    if (actaId) {
      this.loadActa(actaId);
    } else {
      this.loadReunion(this.route.snapshot.queryParams['reunionId']);
    }
  }

  private loadReunion(reunionId: string | undefined): void {
    if (!reunionId) return;
    this.apiService.get<any>(`/api/reuniones/${reunionId}`).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (reunion) => {
        const tipo = String(reunion.tipoReunion || '').toLowerCase();
        const tipoActa = ({
          ordinaria: 'Ordinaria',
          extraordinaria: 'Extraordinaria',
          comite: 'Comité',
          comité: 'Comité',
          consejo: 'Consejo',
          directiva: 'Consejo',
        } as Record<string, string>)[tipo] || '';

        this.formTitulo.set(reunion.titulo || '');
        this.formTipo.set(tipoActa);
        this.formFecha.set((reunion.fechaISO || reunion.fecha || '').slice(0, 10));
        this.formHora.set(reunion.horaInicio || '');
        this.formLugar.set(reunion.lugar || reunion.sala || '');
        this.formModalidad.set(reunion.modalidad === 'Mixta' ? 'Híbrida' : (reunion.modalidad || 'Presencial'));
        this.formResponsable.set(reunion.organizador || '');
        this.formObjetivos.set(reunion.objetivo || reunion.descripcion || '');

        if (reunion.organizador) {
          this.responsables.update(list => list.some(persona => persona.nombre === reunion.organizador)
            ? list
            : [...list, { nombre: reunion.organizador, cargo: '', departamento: reunion.area || '' }]);
        }

      },
      error: () => {},
    });
  }

  private loadActa(id: string): void {
    this.apiService.get<any>(`/api/actas/${id}`).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (data) => {
        this.editandoId.set(String(data.id));
        this.formTitulo.set(data.titulo || '');
        this.formTipo.set(data.tipo || '');
        this.formLugar.set(data.lugar || '');
        this.formResponsable.set(data.responsable || '');
        this.formObjetivos.set(data.objetivos || '');
        this.formInstitucionParticipantes.set(data.institucionParticipantes || '');
        this.formEstado.set(data.estado || 'Borrador');
        this.formFecha.set(data.fecha ? data.fecha.slice(0, 10) : '');
        this.formHora.set(data.hora || '');

        this.ordenDia.set(data.temas || []);

        this.participantes.set((data.participantes || []).map((p: any) => ({
          nombre: p.nombre || '',
          apellido: p.apellido || '',
          area: p.area || '',
          asistio: p.asistio === true,
          correo: p.correo || '',
          institucion: p.institucion || '',
          cargo: p.cargo || '',
        })));

        const acuerdosRaw = (data.acuerdosRel || []).map((a: any) => ({
          descripcion: a.descripcion || '',
          responsable: a.responsable || '',
          estado: a.estado || 'Pendiente',
          fechaLimite: a.fechaLimite || '',
        }));

        const acuerdosNormales = acuerdosRaw.filter((a: any) => !a.descripcion.startsWith('[Compromiso] '));
        const compromisosRaw = acuerdosRaw
          .filter((a: any) => a.descripcion.startsWith('[Compromiso] '))
          .map((a: any) => ({
            actividad: a.descripcion.replace('[Compromiso] ', ''),
            responsable: a.responsable,
            estado: a.estado,
            fechaLimite: a.fechaLimite,
          }));

        this.acuerdos.set(acuerdosNormales);
        this.compromisos.set(compromisosRaw);

        this.adjuntos.set((data.adjuntos || []).map((adj: any) => ({
          nombre: adj.nombreOriginal || adj.nombre || '',
          tipo: adj.tipoMime ? adj.tipoMime.split('/').pop() || '' : '',
          tamano: adj.tamano ? `${(adj.tamano / (1024 * 1024)).toFixed(1)} MB` : '',
        })));

        if (data.sesion) {
          this.formVincularSesion.set(true);
          this.formSesionExistente.set(true);
          this.formSesionId.set(String(data.sesion.id));
        }
      },
      error: async () => {
        const alert = await this.alertCtrl.create({
          header: 'Error',
          message: 'No se pudo cargar el acta para editar',
          buttons: ['OK'],
        });
        await alert.present();
        this.router.navigate(['/actas']);
      },
    });
  }

  private cargarResponsables(): void {
    this.apiService.get<any[]>('/api/usuarios/seleccionables').pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (usuarios) => this.responsables.set(usuarios
        .filter(u => u.activo !== false && u.bloqueado !== true)
        .map(u => ({
          nombre: [u.nombre, u.segundoNombre, u.apellido].filter(Boolean).join(' ').trim(),
          cargo: u.cargo || '',
          departamento: u.departamento || u.unidadAdministrativa || '',
        }))
        .filter(u => !!u.nombre)
        .sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'))),
      error: () => this.responsables.set([]),
    });
  }

  protected volver(): void {
    this.router.navigate(['/actas']);
  }

  protected onMenuToggle(): void {
    this.authService.sidebarCollapsed.update(v => !v);
  }

  protected onLogout(): void {
    this.authService.logout();
  }

  /* ── Orden del día ── */

  protected agregarPunto(): void {
    this.ordenDia.update(list => [...list, '']);
  }

  protected actualizarPunto(index: number, value: string): void {
    this.ordenDia.update(list => list.map((item, i) => i === index ? value : item));
  }

  protected moverPunto(index: number, direccion: -1 | 1): void {
    const items = [...this.ordenDia()];
    const newIndex = index + direccion;
    if (newIndex < 0 || newIndex >= items.length) return;
    [items[index], items[newIndex]] = [items[newIndex], items[index]];
    this.ordenDia.set(items);
  }

  protected eliminarPunto(index: number): void {
    this.ordenDia.update(list => list.filter((_, i) => i !== index));
  }

  /* ── Acuerdos ── */

  protected agregarAcuerdo(): void {
    const desc = this.nuevoAcuerdoDesc();
    if (!desc.trim()) return;
    this.acuerdos.update(list => [...list, {
      descripcion: desc,
      responsable: this.nuevoAcuerdoResp(),
      estado: 'Pendiente',
    }]);
    this.nuevoAcuerdoDesc.set('');
    this.nuevoAcuerdoResp.set('');
  }

  protected eliminarAcuerdo(index: number): void {
    this.acuerdos.update(list => list.filter((_, i) => i !== index));
  }

  /* ── Compromisos ── */

  protected agregarCompromiso(): void {
    const act = this.nuevoCompActividad();
    if (!act.trim()) return;
    this.compromisos.update(list => [...list, {
      actividad: act,
      responsable: this.nuevoCompResp(),
      estado: 'Pendiente',
    }]);
    this.nuevoCompActividad.set('');
    this.nuevoCompResp.set('');
  }

  protected eliminarCompromiso(index: number): void {
    this.compromisos.update(list => list.filter((_, i) => i !== index));
  }

  /* ── Adjuntos ── */

  protected onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files) return;
    const rejected: string[] = [];
    Array.from(input.files).forEach(file => {
      const extension = file.name.split('.').pop() || '';
      if (!file.size || file.size > 10 * 1024 * 1024
          || !/^(pdf|docx?|xlsx?|pptx?|png|jpe?g|gif|webp|txt|csv|zip|rar)$/i.test(extension)) {
        rejected.push(file.name);
        return;
      }
      const sizeMB = (file.size / (1024 * 1024)).toFixed(1);
      this.adjuntos.update(list => [...list, {
        nombre: file.name,
        tipo: extension.toLowerCase(),
        tamano: `${sizeMB} MB`,
        archivo: file,
      }]);
    });
    input.value = '';
    if (rejected.length > 0) {
      void this.alertCtrl.create({
        header: 'Archivo no admitido',
        message: `No se agregaron: ${rejected.join(', ')}. Usa los formatos indicados y un máximo de 10 MB por archivo.`,
        buttons: ['OK'],
      }).then(alert => alert.present());
    }
  }

  protected triggerFileInput(): void {
    this.fileInput?.nativeElement.click();
  }

  protected eliminarAdjunto(index: number): void {
    this.adjuntos.update(list => list.filter((_, i) => i !== index));
  }

  protected getFileIcon(tipo: string): string {
    const map: Record<string, string> = {
      pdf: 'document-text-outline',
      pptx: 'bar-chart-outline',
      jpg: 'image-outline',
      png: 'image-outline',
      doc: 'clipboard-outline',
      docx: 'clipboard-outline',
      xls: 'grid-outline',
      xlsx: 'grid-outline',
    };
    return map[tipo] || 'document-outline';
  }

  /* ── Guardar ── */

  protected guardarBorrador(): void {
    this.guardar('Borrador');
  }

  protected guardar(estado: 'Borrador' | 'Terminada' = 'Terminada'): void {
    if (!this.puedeGuardar()) return;
    this.formEstado.set(estado);
    if (!this.formValido()) return;

    const acuerdosList = this.acuerdos();
    const compromisosList = this.compromisos();

    const allAcuerdosRel = [
      ...acuerdosList.map(a => ({
        descripcion: a.descripcion,
        responsable: a.responsable,
        estado: a.estado,
        fechaLimite: a.fechaLimite || '',
      })),
      ...compromisosList.map(c => ({
        descripcion: '[Compromiso] ' + c.actividad,
        responsable: c.responsable,
        estado: c.estado,
        fechaLimite: c.fechaLimite || '',
      })),
    ];

    const body: Record<string, any> = {
      titulo: this.formTitulo(),
      tipo: this.formTipo(),
      fecha: this.formFecha(),
      hora: this.formHora(),
      lugar: this.formLugar(),
      responsable: this.formResponsable(),
      objetivos: this.formObjetivos(),
      institucionParticipantes: this.formInstitucionParticipantes().trim(),
      estado: this.formEstado(),
      temas: this.ordenDia().filter(t => t.trim()),
      acuerdos: allAcuerdosRel.map(a => a.descripcion),
      acuerdosRel: allAcuerdosRel,
      participantes: this.participantes().map(p => ({
        nombre: p.nombre,
        apellido: p.apellido,
        area: p.area,
        asistio: p.asistio,
        correo: p.correo,
        institucion: p.institucion,
        cargo: p.cargo,
      })),
    };

    if (this.formVincularSesion()) {
      if (this.formSesionExistente() && this.formSesionId()) {
        body['sesionId'] = this.formSesionId();
      } else if (!this.formSesionExistente()) {
        body['crearSesion'] = true;
      }
    }

    const editId = this.editandoId();
    const request$ = editId
      ? this.apiService.put<any>(`/api/actas/${editId}`, body)
      : this.apiService.post<any>('/api/actas', body);

    request$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: async (result: any) => {
        const actaId = result.id;
        const archivos = this.adjuntos().filter(a => a.archivo);
        const fallidos: string[] = [];

        for (const adj of archivos) {
          const formData = new FormData();
          formData.append('file', adj.archivo!);
          try {
            await lastValueFrom(
              this.apiService.post<any>(`/api/actas/${actaId}/adjuntos`, formData)
            );
          } catch {
            fallidos.push(adj.nombre);
          }
        }

        let mensaje = editId ? 'Acta actualizada correctamente' : 'Acta creada correctamente';
        if (fallidos.length > 0) {
          mensaje += `\n\nNo se pudieron subir: ${fallidos.join(', ')}`;
        }

        const alert = await this.alertCtrl.create({
          header: fallidos.length > 0 ? 'Advertencia' : 'Éxito',
          message: mensaje,
          buttons: ['OK'],
        });
        await alert.present();
        await alert.onDidDismiss();
        this.router.navigate(['/actas']);
      },
      error: async () => {
        const alert = await this.alertCtrl.create({
          header: 'Error',
          message: editId ? 'Ocurrió un error al actualizar el acta' : 'Ocurrió un error al crear el acta',
          buttons: ['OK'],
        });
        await alert.present();
      },
    });
  }
}
