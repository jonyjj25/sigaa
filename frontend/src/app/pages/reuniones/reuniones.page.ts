import { Component, inject, OnInit, signal, computed, ChangeDetectionStrategy, DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActionSheetController, AlertController, ToastController } from '@ionic/angular/standalone';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import {
  IonIcon, IonButton,
  IonModal, IonHeader, IonToolbar, IonTitle, IonButtons,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import {
  calendarOutline, checkmarkCircleOutline, timeOutline, peopleOutline,
  addOutline, searchOutline, filterOutline,
  eyeOutline, createOutline, closeOutline,
  ellipsisVerticalOutline, menuOutline, notificationsOutline, logOutOutline,
  locationOutline, videocamOutline, businessOutline,
  chevronBackOutline, chevronForwardOutline, documentTextOutline,
  gridOutline, trashOutline, copyOutline, refreshOutline, playOutline,
} from 'ionicons/icons';
import { DashboardSidebarComponent } from '../../components/dashboard-sidebar/dashboard-sidebar.component';
import { DashboardTopbarComponent } from '../../components/dashboard-topbar/dashboard-topbar.component';
import { DashboardService } from '../../services/dashboard.service';
import { AuthService } from '../../services/auth.service';
import { ApiService, UsuarioSeleccionableRespuesta } from '../../services/api.service';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize, forkJoin } from 'rxjs';
import { Notification } from '../../models/dashboard.models';
import { Reunion, ReunionEstado } from './reuniones.models';

@Component({
  selector: 'app-reuniones',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    DashboardSidebarComponent, DashboardTopbarComponent,
    IonIcon, IonButton,
    IonModal, IonHeader, IonToolbar, IonTitle, IonButtons,
  ],
  templateUrl: './reuniones.page.html',
  styleUrls: ['./reuniones.page.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReunionesPage implements OnInit {
  private readonly dashboardService = inject(DashboardService);
  protected readonly authService = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly alertCtrl = inject(AlertController);
  private readonly actionSheetCtrl = inject(ActionSheetController);
  private readonly toastCtrl = inject(ToastController);
  private readonly destroyRef = inject(DestroyRef);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly menuItems = computed(() => this.dashboardService.getMenuItems());
  protected readonly notifications = signal<Notification[]>([]);

  protected readonly reuniones = signal<Reunion[]>([]);
  protected readonly selectedReunion = signal<Reunion | null>(null);
  protected readonly detailMode = signal(false);
  protected readonly filtroSearch = signal('');
  protected readonly filtroTipo = signal('Todas las áreas');
  protected readonly filtroEstado = signal('Todos');
  protected readonly filtroFecha = signal('');
  protected readonly tabActivo = signal<ReunionEstado | 'todas'>('todas');

  protected readonly openMenuId = signal<string | null>(null);
  protected readonly menuPos = signal<{ top: number; left: number } | null>(null);
  protected readonly selectedMenuItem = signal<Reunion | null>(null);
  protected readonly showModal = signal(false);
  protected readonly editandoId = signal<string | null>(null);
  protected readonly showParticipantesModal = signal(false);
  protected readonly participantesActuales = signal<string[]>([]);

  protected readonly loading = signal(false);
  protected readonly loadError = signal(false);
  protected readonly saving = signal(false);
  protected readonly cambiandoEstado = signal<ReadonlySet<string>>(new Set());

  /* Form signals */
  protected readonly nuevoTitulo = signal('');
  protected readonly nuevoSubtitulo = signal('');
  protected readonly nuevoDescripcion = signal('');
  protected readonly nuevoObjetivo = signal('');
  protected readonly nuevoFecha = signal('');
  protected readonly nuevoHoraInicioHora = signal('09');
  protected readonly nuevoHoraInicioMin = signal('00');
  protected readonly nuevoHoraInicioAmPm = signal('AM');
  protected readonly nuevoHoraFinHora = signal('10');
  protected readonly nuevoHoraFinMin = signal('00');
  protected readonly nuevoHoraFinAmPm = signal('AM');
  protected readonly nuevoModalidad = signal('');
  protected readonly nuevoEstado = signal('programada');
  protected readonly nuevoArea = signal('');
  protected readonly nuevoOrganizador = signal('');
  protected readonly nuevoLugar = signal('');
  protected readonly nuevoEnlaceVirtual = signal('');
  protected readonly nuevoSala = signal('');
  protected readonly nuevoUnidadResponsable = signal('');
  protected readonly nuevoOrganizacion = signal('');
  protected readonly instituciones = signal<{ id: number; nombre: string }[]>([]);
  protected readonly institucionLegada = computed(() => this.nuevoOrganizacion()
    && !this.instituciones().some(item => item.nombre === this.nuevoOrganizacion()));
  protected readonly unidades = signal<{ id: number; nombre: string }[]>([]);
  protected readonly unidadesManuales = signal<number[]>([]);
  protected readonly catalogoError = signal(false);
  private catalogoCargado = false;
  private catalogoCargando = false;
  protected readonly unidadesAutomaticas = computed(() => {
    const seleccion = this.selectedParticipantes();
    return [...new Set(this.usuarios().filter(u => seleccion.includes(this.editandoId() ? this.nombreUsuario(u) : String(u.id)))
      .reduce<number[]>((ids, u) => ids.concat((u.membresias || []).map(m => Number(m.unidadId))), []))];
  });
  protected readonly unidadesSeleccionadas = computed(() => [...new Set([...this.unidadesManuales(), ...this.unidadesAutomaticas()])]);

  protected cargarCatalogos(): void {
    if (this.catalogoCargado || this.catalogoCargando) return;
    this.catalogoCargando = true;
    this.catalogoError.set(false);
    forkJoin({ instituciones: this.api.get<{ id: number; nombre: string }[]>('/api/organizations/seleccionables'),
      unidades: this.api.get<{ id: number; nombre: string }[]>('/api/unidades') })
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.catalogoCargando = false)).subscribe({
        next: data => { this.instituciones.set(data.instituciones); this.unidades.set(data.unidades); this.catalogoCargado = true; },
        error: () => this.catalogoError.set(true),
      });
  }

  protected agregarUnidad(event: Event): void {
    const select = event.target as HTMLSelectElement;
    if (select.value) this.unidadesManuales.update(ids => [...new Set([...ids, Number(select.value)])]);
    select.value = '';
  }
  protected quitarUnidad(id: number): void { this.unidadesManuales.update(ids => ids.filter(value => value !== id)); }
  protected nombreUnidad(id: number): string {
    return this.unidades().find(u => Number(u.id) === id)?.nombre
      || this.reuniones().find(r => r.id === this.editandoId())?.unidades?.find(u => u.id === id)?.nombre || String(id);
  }
  protected readonly nuevoTipoReunion = signal('');
  protected readonly nuevoObservaciones = signal('');
  protected readonly selectedParticipantes = signal<string[]>([]);

  /* Form validation */
  protected readonly camposObligatoriosCompletos = computed(() =>
    !!this.nuevoTitulo().trim() &&
    !!this.nuevoFecha() &&
    !!this.nuevoModalidad()
  );
  protected readonly formValido = computed(() => this.camposObligatoriosCompletos() && this.horarioValido());
  protected readonly muestraLugar = computed(() => ['Presencial', 'Mixta'].includes(this.nuevoModalidad()));
  protected readonly muestraEnlaceVirtual = computed(() => ['Virtual', 'Mixta'].includes(this.nuevoModalidad()));
  protected readonly horarioValido = computed(() =>
    this.minutosDesdeMedianoche(this.nuevoHoraFinHora(), this.nuevoHoraFinMin(), this.nuevoHoraFinAmPm())
    > this.minutosDesdeMedianoche(this.nuevoHoraInicioHora(), this.nuevoHoraInicioMin(), this.nuevoHoraInicioAmPm())
  );
  protected readonly formSubmitted = signal(false);
  protected readonly formDirty = signal(false);
  protected readonly canDismissModal = () => this.confirmDiscardChanges();

  /* Static data */
  protected readonly horas = ['01', '02', '03', '04', '05', '06', '07', '08', '09', '10', '11', '12'];
  protected readonly minutos = ['00', '15', '30', '45'];
  protected readonly usuarios = signal<UsuarioSeleccionableRespuesta[]>([]);
  protected horaInput(hora: string, minuto: string, ampm: string): string { if (!hora || !minuto || !ampm) return ''; let h = Number(hora); if (ampm === 'PM' && h !== 12) h += 12; if (ampm === 'AM' && h === 12) h = 0; return `${String(h).padStart(2, '0')}:${minuto}`; }
  protected setHoraInicio(value: string): void { this.setHora(value, this.nuevoHoraInicioHora, this.nuevoHoraInicioMin, this.nuevoHoraInicioAmPm); }
  protected setHoraFin(value: string): void { this.setHora(value, this.nuevoHoraFinHora, this.nuevoHoraFinMin, this.nuevoHoraFinAmPm); }
  private setHora(value: string, hora: any, minuto: any, ampm: any): void { if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(value)) { hora.set(''); minuto.set(''); ampm.set(''); return; } const [rawHora, rawMinuto] = value.split(':'); let h = Number(rawHora); ampm.set(h >= 12 ? 'PM' : 'AM'); h = h % 12 || 12; hora.set(String(h).padStart(2, '0')); minuto.set(rawMinuto || '00'); }
  private minutosDesdeMedianoche(hora: string, minuto: string, ampm: string): number {
    if (!/^(0?[1-9]|1[0-2])$/.test(hora) || !/^[0-5]\d$/.test(minuto) || !['AM', 'PM'].includes(ampm)) return NaN;
    let horas = Number(hora);
    if (ampm === 'PM' && horas !== 12) horas += 12;
    if (ampm === 'AM' && horas === 12) horas = 0;
    return horas * 60 + Number(minuto);
  }

  protected readonly currentPage = signal(1);
  protected get pageSize(): number { return this.authService.preferences().recordsPerPage; }

  protected readonly reunionesFiltradas = computed(() => {
    let items = this.reuniones();
    const tab = this.tabActivo();
    const search = this.filtroSearch().toLowerCase().trim();
    const tipo = this.filtroTipo();
    const est = this.filtroEstado();
    const fecha = this.filtroFecha();

    if (tab !== 'todas') {
      items = items.filter(r => r.estado === tab);
    }
    if (tipo !== 'Todas las áreas') {
      items = items.filter(r => r.area === tipo);
    }
    if (est !== 'Todos') {
      items = items.filter(r => this.estadoLabel(r.estado) === est);
    }
    if (fecha) {
      items = items.filter(r => r.fechaISO === fecha);
    }
    if (search) {
      items = items.filter(r =>
        (r.codigo || '').toLowerCase().includes(search) ||
        (r.organizacion || '').toLowerCase().includes(search) ||
        r.titulo.toLowerCase().includes(search) ||
        r.organizador.toLowerCase().includes(search) ||
        r.area.toLowerCase().includes(search)
      );
    }
    return items;
  });

  protected readonly programadasCount = computed(() => this.reuniones().filter(r => r.estado === 'programada').length);
  protected readonly realizadasCount = computed(() => this.reuniones().filter(r => r.estado === 'realizada').length);
  protected readonly enCursoCount = computed(() => this.reuniones().filter(r => r.estado === 'en_curso').length);
  protected readonly totalParticipantes = computed(() => this.reuniones().reduce((sum, r) => sum + r.participantes, 0));

  protected readonly paginatedReuniones = computed(() => {
    const start = (this.currentPage() - 1) * this.pageSize;
    return this.reunionesFiltradas().slice(start, start + this.pageSize);
  });

  protected readonly totalPages = computed(() =>
    Array.from({ length: Math.ceil(this.reunionesFiltradas().length / this.pageSize) }, (_, i) => i + 1)
  );

  protected readonly Math = Math;

  constructor() {
    addIcons({
      calendarOutline, checkmarkCircleOutline, timeOutline, peopleOutline,
      addOutline, searchOutline, filterOutline,
      eyeOutline, createOutline, closeOutline,
      ellipsisVerticalOutline, menuOutline, notificationsOutline, logOutOutline,
      locationOutline, videocamOutline, businessOutline,
      chevronBackOutline, chevronForwardOutline, documentTextOutline,
      gridOutline, trashOutline, copyOutline, refreshOutline, playOutline,
    });
  }

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.detailMode.set(params.has('id'));
      this.setSelectedReunionFromRoute();
    });
    this.loadReuniones();
    this.loadUsuarios();
    this.dashboardService.getNotificaciones().pipe(takeUntilDestroyed(this.destroyRef)).subscribe(d => this.notifications.set(d));
  }

  private loadUsuarios(): void {
    if (!this.can('Gestionar participantes')) return;
    this.api.get<UsuarioSeleccionableRespuesta[]>('/api/usuarios/seleccionables').pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (data) => this.usuarios.set(data),
      error: () => this.usuarios.set([]),
    });
  }

  protected can(accion: string): boolean {
    return this.authService.hasPermission('Reuniones', accion);
  }

  private loadReuniones(): void {
    this.loading.set(true);
    this.loadError.set(false);
    this.api.get<Reunion[]>('/api/reuniones').pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (data) => {
        this.reuniones.set(data);
        this.setSelectedReunionFromRoute(data);
        this.loading.set(false);
      },
      error: async (error) => {
        this.loading.set(false);
        this.loadError.set(true);
        const t = await this.toastCtrl.create({ message: this.mensajeErrorReunion(error), duration: 3000, color: 'danger' });
        t.present();
      },
    });
  }

  protected retryLoad(): void { this.loadReuniones(); }

  protected readonly tipoOptions = computed(() => [
    'Todas las áreas',
    ...Array.from(new Set(this.reuniones().map(r => r.area).filter(Boolean))).sort((a, b) => a.localeCompare(b, 'es')),
  ]);
  readonly estadoOptions = ['Todos', 'Programada', 'En Curso', 'Realizada', 'Cancelada'];

  protected formatFecha(fecha: string): string {
    if (!fecha) return 'Sin fecha';
    const [year, month, day] = fecha.slice(0, 10).split('-').map(Number);
    if (!year || !month || !day) return fecha;
    return new Intl.DateTimeFormat('es-PA', { day: '2-digit', month: 'short', year: 'numeric' })
      .format(new Date(year, month - 1, day));
  }

  protected formatFechaLarga(fecha: string): string {
    if (!fecha) return 'Sin fecha';
    const [year, month, day] = fecha.slice(0, 10).split('-').map(Number);
    if (!year || !month || !day) return fecha;
    return new Intl.DateTimeFormat('es-PA', { day: 'numeric', month: 'long', year: 'numeric' })
      .format(new Date(year, month - 1, day));
  }

  protected formatTipo(tipo?: string): string {
    if (!tipo) return 'Sin especificar';
    return tipo.charAt(0).toLocaleUpperCase('es-PA') + tipo.slice(1);
  }

  protected iniciales(nombre: string): string {
    return nombre.trim().split(/\s+/).slice(0, 2).map(parte => parte.charAt(0).toUpperCase()).join('');
  }

  protected estadoLabel(estado: ReunionEstado): string {
    switch (estado) {
      case 'programada': return 'Programada';
      case 'en_curso': return 'En Curso';
      case 'realizada': return 'Realizada';
      case 'cancelada': return 'Cancelada';
    }
  }

  protected estadoColor(estado: ReunionEstado): string {
    switch (estado) {
      case 'programada': return 'var(--state-info-text)';
      case 'en_curso': return 'var(--state-warning-text)';
      case 'realizada': return 'var(--state-success-text)';
      case 'cancelada': return 'var(--state-danger-text)';
    }
  }

  protected estadoBg(estado: ReunionEstado): string {
    switch (estado) {
      case 'programada': return 'var(--state-info-bg)';
      case 'en_curso': return 'var(--state-warning-bg)';
      case 'realizada': return 'var(--state-success-bg)';
      case 'cancelada': return 'var(--state-danger-bg)';
    }
  }

  protected selectReunion(item: Reunion): void {
    this.closeMenu();
    this.router.navigate(['/reuniones', item.id]);
  }

  protected closeDetail(): void {
    this.router.navigate(['/reuniones']);
  }

  protected setTab(tab: ReunionEstado | 'todas'): void {
    this.tabActivo.set(tab);
    this.currentPage.set(1);
  }

  protected onFilterChange(): void {
    this.currentPage.set(1);
  }

  protected limpiarFiltros(): void {
    this.filtroSearch.set('');
    this.filtroTipo.set('Todas las áreas');
    this.filtroEstado.set('Todos');
    this.filtroFecha.set('');
    this.tabActivo.set('todas');
    this.currentPage.set(1);
  }

  private setSelectedReunionFromRoute(reuniones = this.reuniones()): void {
    const reunionId = this.route.snapshot.paramMap.get('id');
    this.selectedReunion.set(reunionId ? reuniones.find((item) => String(item.id) === reunionId) ?? null : null);
  }

  private updateSelectedReunion(reunion: Reunion): void {
    if (this.detailMode() && this.route.snapshot.paramMap.get('id') === String(reunion.id)) {
      this.selectedReunion.set(reunion);
    }
  }

  protected toggleMenu(id: string, event: MouseEvent): void {
    event.stopPropagation();
    if (this.openMenuId() === id) {
      this.closeMenu();
      return;
    }
    this.openMenuId.set(id);
    const item = this.reuniones().find(r => r.id === id);
    this.selectedMenuItem.set(item ?? null);
    const btn = event.currentTarget as HTMLElement;
    const rect = btn.getBoundingClientRect();
    const menuWidth = 180;
    const menuHeight = 430;
    const gap = 4;
    let top = rect.bottom + gap;
    let left = rect.right - menuWidth;
    if (top + menuHeight > window.innerHeight - 12) {
      top = Math.max(12, window.innerHeight - menuHeight - 12);
    }
    if (left < gap) {
      left = gap;
    }
    if (left + menuWidth > window.innerWidth - gap) {
      left = window.innerWidth - menuWidth - gap;
    }
    this.menuPos.set({ top, left });

    window.requestAnimationFrame(() => {
      const menu = document.querySelector<HTMLElement>('.context-menu');
      if (!menu || this.openMenuId() !== id) return;
      const menuRect = menu.getBoundingClientRect();
      let adjustedTop = top;
      let adjustedLeft = left;
      if (menuRect.bottom > window.innerHeight - 12) {
        adjustedTop = Math.max(12, window.innerHeight - menuRect.height - 12);
      }
      if (menuRect.right > window.innerWidth - 12) {
        adjustedLeft = Math.max(12, window.innerWidth - menuRect.width - 12);
      }
      this.menuPos.set({ top: adjustedTop, left: adjustedLeft });
    });
  }

  protected closeMenu(): void {
    this.openMenuId.set(null);
    this.menuPos.set(null);
    this.selectedMenuItem.set(null);
  }

  protected goToPage(page: number): void {
    if (page >= 1 && page <= this.totalPages().length) {
      this.currentPage.set(page);
    }
  }

  protected onMenuToggle(): void {
    this.authService.sidebarCollapsed.update(v => !v);
  }

  protected onLogout(): void {
    this.authService.logout();
  }

  protected openNewModal(): void {
    this.editandoId.set(null);
    this.resetForm();
    this.cargarCatalogos();
    this.nuevoOrganizador.set(
      this.usuarios().find(u => Number(u.id) === this.authService.currentUser()?.id)
        ? this.nombreUsuario(this.usuarios().find(u => Number(u.id) === this.authService.currentUser()?.id)!)
        : this.authService.currentUser()?.nombre
      || this.authService.currentUser()?.correo
      || 'Usuario actual'
    );
    const creador = this.authService.currentUser();
    if (creador?.id != null && !this.authService.hasRole('Secretaria') && !this.authService.isSuperAdmin()) {
      this.selectedParticipantes.set([String(creador.id)]);
    }
    this.formSubmitted.set(false);
    this.formDirty.set(false);
    this.showModal.set(true);
  }

  protected closeModal(): void {
    this.showModal.set(false);
    this.formDirty.set(false);
  }

  protected async requestCloseModal(): Promise<void> {
    if (await this.confirmDiscardChanges()) this.closeModal();
  }

  private async confirmDiscardChanges(): Promise<boolean> {
    if (!this.formDirty() || this.saving()) return true;
    const alert = await this.alertCtrl.create({
      header: 'Descartar cambios',
      message: 'Tienes cambios sin guardar. ¿Deseas descartarlos?',
      buttons: [
        { text: 'Seguir editando', role: 'cancel' },
        { text: 'Descartar', role: 'confirm' },
      ],
    });
    await alert.present();
    const { role } = await alert.onDidDismiss();
    return role === 'confirm';
  }

  protected openEditModal(item: Reunion): void {
    this.closeMenu();
    this.editandoId.set(item.id);
    this.nuevoTitulo.set(item.titulo);
    this.nuevoSubtitulo.set(item.subtitulo ?? '');
    this.nuevoDescripcion.set(item.descripcion);
    this.nuevoObjetivo.set(item.objetivo ?? '');
    this.nuevoFecha.set(item.fechaISO);
    const [hI, mI, ampmI] = this.parseHora(item.horaInicio);
    this.nuevoHoraInicioHora.set(hI);
    this.nuevoHoraInicioMin.set(mI);
    this.nuevoHoraInicioAmPm.set(ampmI);
    const [hF, mF, ampmF] = this.parseHora(item.horaFin);
    this.nuevoHoraFinHora.set(hF);
    this.nuevoHoraFinMin.set(mF);
    this.nuevoHoraFinAmPm.set(ampmF);
    this.nuevoModalidad.set(item.modalidad);
    this.nuevoEstado.set(item.estado);
    this.nuevoArea.set(item.area);
    this.nuevoOrganizador.set(item.organizador);
    this.nuevoLugar.set(item.lugar);
    this.nuevoEnlaceVirtual.set(item.enlaceVirtual ?? '');
    this.nuevoSala.set(item.sala ?? '');
    this.nuevoUnidadResponsable.set(item.unidadResponsable ?? '');
    this.nuevoOrganizacion.set(item.organizacion ?? '');
    this.unidadesManuales.set(item.unidadIds || []);
    this.cargarCatalogos();
    this.nuevoTipoReunion.set(item.tipoReunion ?? '');
    this.nuevoObservaciones.set(item.observaciones ?? '');
    this.selectedParticipantes.set(item.participantesNombres ?? []);
    this.formSubmitted.set(false);
    this.formDirty.set(false);
    this.showModal.set(true);
  }

  private resetForm(): void {
    this.nuevoTitulo.set('');
    this.nuevoSubtitulo.set('');
    this.nuevoDescripcion.set('');
    this.nuevoObjetivo.set('');
    this.nuevoFecha.set('');
    this.nuevoHoraInicioHora.set('09');
    this.nuevoHoraInicioMin.set('00');
    this.nuevoHoraInicioAmPm.set('AM');
    this.nuevoHoraFinHora.set('10');
    this.nuevoHoraFinMin.set('00');
    this.nuevoHoraFinAmPm.set('AM');
    this.nuevoModalidad.set('');
    this.nuevoEstado.set('programada');
    this.nuevoArea.set('');
    this.nuevoOrganizador.set('');
    this.nuevoLugar.set('');
    this.nuevoEnlaceVirtual.set('');
    this.nuevoSala.set('');
    this.nuevoUnidadResponsable.set('');
    this.nuevoOrganizacion.set('');
    this.unidadesManuales.set([]);
    this.nuevoTipoReunion.set('');
    this.nuevoObservaciones.set('');
    this.selectedParticipantes.set([]);
  }

  private parseHora(hora: string): [string, string, string] {
    const parts = hora.split(':');
    const h24 = parseInt(parts[0], 10);
    const min = parts[1]?.padStart(2, '0') || '00';
    const ampm = h24 >= 12 ? 'PM' : 'AM';
    const h12 = h24 > 12 ? h24 - 12 : (h24 === 0 ? 12 : h24);
    return [String(h12).padStart(2, '0'), min, ampm];
  }

  /* ── Tag participantes ── */
  protected nombreUsuario(usuario: UsuarioSeleccionableRespuesta): string {
    return [usuario.nombre, usuario.segundoNombre, usuario.apellido].filter(Boolean).join(' ').trim();
  }

  protected valorParticipante(usuario: UsuarioSeleccionableRespuesta): string {
    return this.editandoId() ? this.nombreUsuario(usuario) : String(usuario.id);
  }

  protected nombreParticipante(valor: string): string {
    if (this.editandoId()) return valor;
    const usuario = this.usuarios().find(item => String(item.id) === valor);
    if (usuario) return this.nombreUsuario(usuario);
    const actual = this.authService.currentUser();
    return String(actual?.id) === valor ? actual?.nombre || actual?.correo || valor : valor;
  }

  protected esCreadorAutomatico(valor: string): boolean {
    return !this.editandoId() && !this.authService.hasRole('Secretaria') && !this.authService.isSuperAdmin()
      && valor === String(this.authService.currentUser()?.id);
  }

  protected agregarParticipante(event: Event): void {
    const select = event.target as HTMLSelectElement;
    const value = select.value;
    if (!value) return;
    this.selectedParticipantes.update(list => list.includes(value) ? list : [...list, value]);
    select.value = '';
  }

  protected quitarParticipante(nombre: string): void {
    if (this.esCreadorAutomatico(nombre)) return;
    this.selectedParticipantes.update(list => list.filter(n => n !== nombre));
  }

  private to24h(hora: string, min: string, ampm: string): string {
    let h = parseInt(hora, 10);
    if (ampm === 'PM' && h < 12) h += 12;
    if (ampm === 'AM' && h === 12) h = 0;
    return `${String(h).padStart(2, '0')}:${min}`;
  }

  protected guardarReunion(): void {
    if (this.saving()) return;
    this.formSubmitted.set(true);
    if (!this.formValido()) return;
    const hI = this.to24h(this.nuevoHoraInicioHora(), this.nuevoHoraInicioMin(), this.nuevoHoraInicioAmPm());
    const hF = this.to24h(this.nuevoHoraFinHora(), this.nuevoHoraFinMin(), this.nuevoHoraFinAmPm());
    const fechaISO = this.nuevoFecha();
    const participantes = [...new Set(this.selectedParticipantes())];

    const body: any = {
      titulo: this.nuevoTitulo(),
      subtitulo: this.nuevoSubtitulo(),
      descripcion: this.nuevoDescripcion(),
      objetivo: this.nuevoObjetivo(),
      fecha: fechaISO,
      fechaIso: fechaISO,
      horaInicio: hI,
      horaFin: hF,
      tipoReunion: this.nuevoTipoReunion(),
      area: this.nuevoArea(),
      organizador: this.nuevoOrganizador(),
      lugar: this.muestraLugar() ? this.nuevoLugar() : '',
      modalidad: this.nuevoModalidad(),
      estado: this.nuevoEstado(),
      enlaceVirtual: this.muestraEnlaceVirtual() ? this.nuevoEnlaceVirtual() : '',
      sala: this.muestraLugar() ? this.nuevoSala() : '',
      unidadResponsable: this.nuevoUnidadResponsable(),
      organizacion: this.nuevoOrganizacion(),
      unidadIds: this.unidadesSeleccionadas(),
      observaciones: this.nuevoObservaciones(),
    };

    const isEdit = this.editandoId();
    if (isEdit) body.participantesNombres = participantes;
    else body.participanteUsuarioIds = participantes.map(Number);
    const request$ = isEdit
      ? this.api.put<Reunion>(`/api/reuniones/${isEdit}`, body)
      : this.api.post<Reunion>('/api/reuniones', body);

    this.saving.set(true);
    request$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: async (result) => {
        if (isEdit) {
          this.reuniones.update(list => list.map(r => r.id === isEdit ? result : r));
        } else {
          this.reuniones.update(list => [result, ...list]);
        }
        this.updateSelectedReunion(result);
        this.closeModal();
        this.saving.set(false);
        const t = await this.toastCtrl.create({
          message: isEdit ? 'Reunión actualizada' : 'Reunión creada',
          duration: 2000,
          color: 'success',
        });
        t.present();
      },
      error: async (err) => {
        this.saving.set(false);
        const msg = this.mensajeErrorReunion(err);
        const t = await this.toastCtrl.create({ message: msg, duration: 3000, color: 'danger' });
        t.present();
      },
    });
  }

  protected async cancelarReunion(id: string): Promise<void> {
    this.closeMenu();
    const alert = await this.alertCtrl.create({
      header: 'Cancelar reunión',
      message: '¿Está seguro de cancelar esta reunión?',
      cssClass: 'reunion-cancel-alert',
      buttons: [
        { text: 'Volver', role: 'cancel', cssClass: 'reunion-cancel-dismiss' },
        {
          text: 'Sí, cancelar',
          cssClass: 'reunion-cancel-confirm',
          handler: () => this.cambiarEstadoReunion(id, 'cancelada'),
        },
      ],
    });
    alert.present();
  }

  protected async abrirMasAcciones(item: Reunion): Promise<void> {
    const buttons: any[] = [];

    if (this.can('Duplicar')) buttons.push({ text: 'Duplicar', icon: 'copy-outline', handler: () => { void this.duplicarReunion(item.id); } });
    if (this.authService.isSuperAdmin() || (this.authService.isInstitutionalManager() && this.can('Editar'))) buttons.push({ text: 'Reprogramar', icon: 'refresh-outline', handler: () => { void this.reprogramarReunion(item); } });
    if (this.can('Generar acta')) buttons.push({ text: 'Generar acta', icon: 'document-text-outline', handler: () => this.irActa(item.id) });
    if (this.can('Ver tareas')) buttons.push({ text: 'Tareas', icon: 'grid-outline', handler: () => this.irTareas(item.id) });
    if (this.authService.isSuperAdmin() || (this.authService.isInstitutionalManager() && this.authService.hasPermission('Configuracion QR', 'Ver'))) buttons.push({ text: 'Asistencia', icon: 'checkmark-circle-outline', handler: () => this.irAsistencia(item.id) });
    if (this.can('Cancelar') && item.puedeCancelar !== false && ['programada', 'en_curso'].includes(item.estado)) buttons.push({ text: 'Cancelar reunión', icon: 'close-outline', role: 'destructive', handler: () => { void this.cancelarReunion(item.id); } });
    if (this.can('Eliminar') && item.puedeEliminar !== false) buttons.push({ text: 'Eliminar', icon: 'trash-outline', role: 'destructive', handler: () => { void this.eliminarReunion(item.id); } });
    buttons.push({ text: 'Cerrar', role: 'cancel' });

    const sheet = await this.actionSheetCtrl.create({
      header: 'Acciones de la reunión',
      cssClass: ['reunion-actions-sheet', 'meeting-actions-sheet'],
      buttons,
    });
    await sheet.present();
  }

  protected async iniciarReunion(id: string): Promise<void> {
    this.closeMenu();
    this.cambiarEstadoReunion(id, 'en_curso');
  }

  protected async finalizarReunion(id: string): Promise<void> {
    this.closeMenu();
    this.cambiarEstadoReunion(id, 'realizada');
  }

  private cambiarEstadoReunion(id: string, estado: ReunionEstado): void {
    if (!this.can(estado === 'cancelada' ? 'Cancelar' : 'Iniciar') || this.cambiandoEstado().has(id)) return;
    const seleccionada = this.selectedReunion();
    const reunion = this.reuniones().find(item => String(item.id) === String(id))
      || (String(seleccionada?.id) === String(id) ? seleccionada : null);
    const permitido = estado === 'en_curso' ? reunion?.puedeIniciar
      : estado === 'realizada' ? reunion?.puedeFinalizar : reunion?.puedeCancelar;
    if (permitido === false) return;
    this.cambiandoEstado.update(ids => new Set([...ids, id]));
    this.api.patch<Reunion>(`/api/reuniones/${id}/estado`, { estado }).pipe(
      finalize(() => this.cambiandoEstado.update(ids => { const pendientes = new Set(ids); pendientes.delete(id); return pendientes; })),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: async (updated) => {
        this.reuniones.update(list => list.map(r => r.id === id ? updated : r));
        this.updateSelectedReunion(updated);
        const t = await this.toastCtrl.create({
          header: estado === 'en_curso' ? 'Reunión iniciada' : estado === 'realizada' ? 'Reunión finalizada' : 'Reunión cancelada',
          message: estado === 'en_curso' ? 'La reunión está en curso.' : estado === 'realizada' ? 'La reunión se marcó como realizada.' : 'La reunión fue cancelada.',
          icon: estado === 'en_curso' ? 'play-outline' : 'checkmark-circle-outline',
          cssClass: estado === 'en_curso' ? ['reunion-state-toast', 'reunion-started-toast'] : 'reunion-state-toast',
          position: 'top',
          duration: 3200,
          buttons: [{ text: 'Cerrar', role: 'cancel' }],
        });
        t.present();
      },
      error: async (error) => {
        const t = await this.toastCtrl.create({ message: this.mensajeErrorReunion(error), duration: 3500, color: 'danger' });
        t.present();
      },
    });
  }

  private mensajeErrorReunion(error: unknown): string {
    const status = error instanceof HttpErrorResponse ? error.status : 500;
    switch (status) {
      case 0: return 'No se pudo conectar con el servidor. Comprueba tu conexión e inténtalo nuevamente.';
      case 401: return 'Tu sesión ha vencido. Inicia sesión nuevamente.';
      case 403: return 'No tienes permisos para realizar esta acción en la reunión.';
      case 404: return 'La reunión ya no existe o no está disponible.';
      case 409: return 'El estado de la reunión cambió o no permite esta acción. Actualiza la información.';
      case 400: return 'Los datos de la reunión no son válidos. Revisa el formulario.';
      default: return 'No se pudo completar la operación por un error del servidor. Inténtalo nuevamente.';
    }
  }

  protected estadoSeleccionable(estado: ReunionEstado): boolean {
    const reunion = this.reuniones().find(item => item.id === this.editandoId()) || this.selectedReunion();
    if (!reunion || estado === reunion.estado) return true;
    if (estado === 'programada') return this.can('Editar');
    if (estado === 'en_curso') return this.can('Iniciar') && reunion.puedeIniciar !== false && reunion.estado === 'programada';
    if (estado === 'realizada') return this.can('Iniciar') && reunion.puedeFinalizar !== false && reunion.estado === 'en_curso';
    return this.can('Cancelar') && reunion.puedeCancelar !== false && ['programada', 'en_curso'].includes(reunion.estado);
  }

  protected async duplicarReunion(id: string): Promise<void> {
    this.closeMenu();
    this.api.post<Reunion>(`/api/reuniones/${id}/duplicar`, {}).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: async (duplicated) => {
        this.reuniones.update(list => [duplicated, ...list]);
        this.updateSelectedReunion(duplicated);
        const t = await this.toastCtrl.create({ message: 'Reunión duplicada', duration: 2000, color: 'success' });
        t.present();
      },
      error: async (error) => {
        const t = await this.toastCtrl.create({ message: this.mensajeErrorReunion(error), duration: 3000, color: 'danger' });
        t.present();
      },
    });
  }

  protected async reprogramarReunion(item: Reunion): Promise<void> {
    this.closeMenu();
    const alert = await this.alertCtrl.create({
      header: 'Reprogramar Reunión',
      message: `Reprogramar "${item.titulo}". Ingrese nueva fecha:`,
      inputs: [
        { name: 'fecha', type: 'date', label: 'Nueva fecha', value: item.fechaISO.split('T')[0] },
        { name: 'horaInicio', type: 'time', label: 'Nueva hora inicio', value: item.horaInicio },
        { name: 'horaFin', type: 'time', label: 'Nueva hora fin', value: item.horaFin },
      ],
      buttons: [
        { text: 'Cancelar', role: 'cancel' },
        {
          text: 'Reprogramar',
          handler: (data) => {
            if (!data.fecha || !data.horaInicio || !data.horaFin) return;
            this.api.put<Reunion>(`/api/reuniones/${item.id}/reprogramar`, {
              fecha: data.fecha,
              fechaIso: data.fecha,
              horaInicio: data.horaInicio,
              horaFin: data.horaFin,
            }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
              next: async (updated) => {
                this.reuniones.update(list => list.map(r => r.id === item.id ? updated : r));
                this.updateSelectedReunion(updated);
                const t = await this.toastCtrl.create({ message: 'Reunión reprogramada', duration: 2000, color: 'success' });
                t.present();
              },
              error: async (error) => {
                const t = await this.toastCtrl.create({ message: this.mensajeErrorReunion(error), duration: 3000, color: 'danger' });
                t.present();
              },
            });
          },
        },
      ],
    });
    alert.present();
  }

  protected openParticipantesModal(nombres: string[]): void {
    this.closeMenu();
    this.participantesActuales.set(nombres.map(nombre =>
      nombre.trim().replace(/^["“”]+|["“”]+$/g, '').trim(),
    ));
    this.showParticipantesModal.set(true);
  }

  protected closeParticipantesModal(): void {
    this.showParticipantesModal.set(false);
  }

  protected async eliminarReunion(id: string): Promise<void> {
    this.closeMenu();
    const alert = await this.alertCtrl.create({
      header: 'Eliminar reunión',
      message: '¿Está seguro de eliminar esta reunión? Esta acción no se puede deshacer.',
      cssClass: 'reunion-delete-alert',
      buttons: [
        { text: 'Volver', role: 'cancel', cssClass: 'reunion-delete-dismiss' },
        {
          text: 'Sí, eliminar',
          cssClass: 'reunion-delete-confirm',
          role: 'destructive',
          handler: () => {
            this.api.delete(`/api/reuniones/${id}`).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
              next: async () => {
                this.reuniones.update(list => list.filter(r => r.id !== id));
                if (this.selectedReunion()?.id === id) {
                  this.closeDetail();
                }
                const t = await this.toastCtrl.create({ message: 'Reunión eliminada', duration: 2000, color: 'success' });
                t.present();
              },
              error: async (error) => {
                const t = await this.toastCtrl.create({ message: this.mensajeErrorReunion(error), duration: 3000, color: 'danger' });
                t.present();
              },
            });
          },
        },
      ],
    });
    alert.present();
  }

  protected irActa(reunionId: string): void {
    this.closeMenu();
    this.router.navigate(['/actas/nueva'], { queryParams: { reunionId } });
  }

  protected irAsistencia(reunionId: string): void {
    this.closeMenu();
    const reunion = this.reuniones().find(item => item.id === reunionId);
    this.router.navigate(['/asistencia-qr'], {
      queryParams: { reunionId, reunionTitulo: reunion?.titulo, reunionFecha: reunion?.fechaISO || reunion?.fecha },
    });
  }

  protected irTareas(reunionId: string): void {
    this.closeMenu();
    const reunion = this.reuniones().find(item => item.id === reunionId);
    this.router.navigate(['/tareas'], {
      queryParams: { reunionId, tipo: 'Reuniones', reunionTitulo: reunion?.titulo },
    });
  }
}
