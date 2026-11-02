import { CommonModule, DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, interval, map, startWith, switchMap, tap } from 'rxjs';
import { IonButton, IonButtons, IonHeader, IonIcon, IonModal, IonTitle, IonToolbar } from '@ionic/angular/standalone';
import { addIcons } from 'ionicons'; import { arrowBackOutline, businessOutline, calendarOutline, chatbubblesOutline, checkmarkCircleOutline, documentOutline, megaphoneOutline, peopleOutline, sendOutline, settingsOutline } from 'ionicons/icons';
import { ApiService } from '../../services/api.service'; import { AuthService } from '../../services/auth.service'; import { DashboardService } from '../../services/dashboard.service'; import { DashboardSidebarComponent } from '../../components/dashboard-sidebar/dashboard-sidebar.component'; import { DashboardTopbarComponent } from '../../components/dashboard-topbar/dashboard-topbar.component'; import { Notification, SidebarItem } from '../../models/dashboard.models';

interface Grupo { id: number; nombre: string; descripcion: string; unidadNombre: string; activo: boolean; creadoEn: string; responsableNombre?: string; miembros: number; }
interface Miembro { id: number; usuarioId: number; nombre: string; correo: string; responsable: boolean; activo: boolean; fechaAsignacion: string; }
interface Mensaje { id: number; autorId: number; autorNombre: string; contenido: string; creadoEn: string; }
interface Resumen { miembros: number; miembrosActivos: number; archivos: number; tareasActivas: number; tareasPendientes: number; reuniones: number; proximaReunion: string; } interface TareaUnidad { id: number; titulo: string; asignado: string; fechaLimite: string; prioridad: string; estado: string; progreso: number } interface ArchivoUnidad { id: number; nombre: string; tipo: string; tamano: string; propietario: string; fecha: string }
@Component({ selector: 'app-grupo-detalle', standalone: true, imports: [CommonModule, FormsModule, RouterLink, IonIcon, IonModal, IonHeader, IonToolbar, IonTitle, IonButtons, IonButton, DashboardSidebarComponent, DashboardTopbarComponent, DatePipe], templateUrl: './grupo-detalle.page.html', styleUrls: ['./grupo-detalle.page.scss'] })
export class GrupoDetallePage implements OnInit {
  private api = inject(ApiService); protected auth = inject(AuthService); private route = inject(ActivatedRoute); private router = inject(Router); private dashboard = inject(DashboardService); private destroy = inject(DestroyRef);
  protected menuItems = computed<SidebarItem[]>(() => this.dashboard.getMenuItems()); protected notifications = signal<Notification[]>([]); protected grupo = signal<Grupo | null>(null); protected miembros = signal<Miembro[]>([]); protected mensajes = signal<Mensaje[]>([]); protected tareas = signal<TareaUnidad[]>([]); protected archivos = signal<ArchivoUnidad[]>([]); protected resumen = signal<Resumen>({ miembros: 0, miembrosActivos: 0, archivos: 0, tareasActivas: 0, tareasPendientes: 0, reuniones: 0, proximaReunion: '' });
  protected active = signal('resumen'); protected loading = signal(true); protected error = signal(''); protected chatError = signal(''); protected texto = signal(''); protected enviando = signal(false); protected search = signal(''); protected menciones = signal<Miembro[]>([]); private id = 0;
  protected gestionarMiembrosOpen = signal(false); protected guardandoMiembros = signal(false); protected usuariosDisponibles = signal<{ id: number; nombre: string; apellido?: string; correo: string }[]>([]); protected miembroIds = signal<number[]>([]);
  protected canManage = () => this.auth.isInstitutionalManager() || this.auth.hasRole('Jefe de Unidad'); protected own = (m: Mensaje) => m.autorId === this.auth.currentUser()?.id;
  protected filteredMembers = computed(() => this.miembros().filter(m => m.nombre.toLowerCase().includes(this.search().toLowerCase())));
  protected mentionSuggestions = computed(() => { const match = this.texto().match(/@([^@\s]*)$/); if (!match) return []; const term = match[1].toLowerCase(); return this.miembros().filter(m => m.usuarioId !== this.auth.currentUser()?.id && m.activo && m.nombre.toLowerCase().includes(term)).slice(0, 5); });
  constructor() { addIcons({ arrowBackOutline, businessOutline, calendarOutline, chatbubblesOutline, checkmarkCircleOutline, documentOutline, megaphoneOutline, peopleOutline, sendOutline, settingsOutline }); }
  ngOnInit() {
    this.dashboard.getNotificaciones().pipe(takeUntilDestroyed(this.destroy)).subscribe(x => this.notifications.set(x));
    this.route.paramMap.pipe(
      map(params => Number(params.get('id'))),
      switchMap(id => {
        if (!Number.isInteger(id) || id < 1) { this.notFound(); return EMPTY; }
        this.chatLoadError.set(''); this.prepareGroup(id); this.load();
        return interval(10000).pipe(startWith(0), switchMap(() =>
          this.api.get<{ content?: Mensaje[] }>(`/api/grupos/${id}/mensajes?tamano=50`).pipe(
            tap(() => this.chatLoadError.set('')),
            catchError(() => { this.chatLoadError.set('No se pudieron actualizar los mensajes. Se intentará nuevamente.'); return EMPTY; }),
          )));
      }), takeUntilDestroyed(this.destroy),
    ).subscribe(r => { this.mensajes.set([...(r.content || [])].reverse()); this.scrollChatToBottom(); });
  }
  protected readonly chatLoadError = signal('');
  private prepareGroup(id: number) { this.id = id; this.loading.set(true); this.error.set(''); this.chatError.set(''); this.grupo.set(null); this.miembros.set([]); this.mensajes.set([]); this.tareas.set([]); this.archivos.set([]); this.resumen.set({ miembros: 0, miembrosActivos: 0, archivos: 0, tareasActivas: 0, tareasPendientes: 0, reuniones: 0, proximaReunion: '' }); this.texto.set(''); this.menciones.set([]); }
  protected load() { const groupId = this.id; this.loading.set(true); this.api.get<Grupo>(`/api/grupos/${groupId}`).pipe(takeUntilDestroyed(this.destroy)).subscribe({ next: g => { if (groupId !== this.id) return; this.grupo.set(g); this.loading.set(false); }, error: () => { if (groupId === this.id) this.notFound(); } }); this.api.get<Resumen>(`/api/grupos/${groupId}/resumen`).subscribe({ next: x => { if (groupId === this.id) this.resumen.set(x) } }); this.api.get<Miembro[]>(`/api/grupos/${groupId}/miembros`).subscribe({ next: x => { if (groupId === this.id) this.miembros.set(x) } }); this.api.get<TareaUnidad[]>(`/api/grupos/${groupId}/tareas`).subscribe({ next: x => { if (groupId === this.id) this.tareas.set(x) } }); this.api.get<ArchivoUnidad[]>(`/api/grupos/${groupId}/archivos`).subscribe({ next: x => { if (groupId === this.id) this.archivos.set(x) } }); }
  protected selectMention(member: Miembro) { this.texto.update(text => text.replace(/@([^@\s]*)$/, `@${member.nombre} `)); this.menciones.update(list => list.some(m => m.usuarioId === member.usuarioId) ? list : [...list, member]); }
  protected abrirGestionMiembros(): void { this.miembroIds.set(this.miembros().map(m => m.usuarioId)); this.api.get<{ id: number; nombre: string; apellido?: string; correo: string }[]>('/api/usuarios/seleccionables').subscribe({ next: usuarios => { this.usuariosDisponibles.set(usuarios); this.gestionarMiembrosOpen.set(true); }, error: () => { this.usuariosDisponibles.set([]); this.gestionarMiembrosOpen.set(true); } }); }
  protected cambiarMiembro(usuarioId: number, agregar: boolean): void { this.miembroIds.update(ids => agregar ? [...new Set([...ids, usuarioId])] : ids.filter(id => id !== usuarioId)); }
  protected esResponsable(usuarioId: number): boolean { return this.miembros().some(m => m.usuarioId === usuarioId && m.responsable); }
  protected guardarMiembros(): void { if (this.guardandoMiembros()) return; this.guardandoMiembros.set(true); this.api.put<Miembro[]>(`/api/grupos/${this.id}/miembros`, { miembroIds: this.miembroIds() }).subscribe({ next: miembros => { this.miembros.set(miembros); this.gestionarMiembrosOpen.set(false); this.guardandoMiembros.set(false); this.load(); }, error: () => { this.guardandoMiembros.set(false); this.chatError.set('No fue posible actualizar los miembros.'); } }); }
  protected abrirArchivo(archivo: ArchivoUnidad): void {
    this.router.navigate(['/documentos'], { queryParams: { documento: archivo.id } });
  }
  protected abrirTarea(tarea: TareaUnidad): void {
    this.router.navigate(['/tareas'], { queryParams: { tarea: tarea.id } });
  }
  protected abrirChat() { this.active.set('chat'); this.scrollChatToBottom(); }
  protected onChatKeydown(event: KeyboardEvent) { if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); this.send(); } }
  protected send() { const contenido = this.texto().trim(); if (!contenido || contenido.length > 2000 || this.enviando()) return; const groupId = this.id; const mencionadosIds = this.menciones().filter(m => contenido.includes(`@${m.nombre}`)).map(m => m.usuarioId); this.chatError.set(''); this.enviando.set(true); this.api.post<Mensaje>(`/api/grupos/${groupId}/mensajes`, { contenido, mencionadosIds }).subscribe({ next: m => { if (groupId === this.id && !this.mensajes().some(x => x.id === m.id)) { this.mensajes.update(x => [...x, m]); this.scrollChatToBottom(); } this.texto.set(''); this.menciones.set([]); this.enviando.set(false); }, error: (response: any) => { this.chatError.set(response?.error?.message || response?.error?.error || 'No fue posible enviar el mensaje. Inténtalo de nuevo.'); this.enviando.set(false); } }); }
  private scrollChatToBottom() { setTimeout(() => { const panel = document.querySelector<HTMLElement>('.chat .messages'); if (panel) panel.scrollTop = panel.scrollHeight; }); }
  protected initials(nombre: string) { return nombre.split(' ').filter(Boolean).slice(0, 2).map(x => x[0]).join('').toUpperCase(); } protected onMenuToggle() { this.auth.sidebarCollapsed.update(v => !v) } protected onLogout() { this.auth.logout() } private notFound() { this.loading.set(false); this.error.set('El grupo no existe o no tienes permiso para consultarlo.'); } protected volver() { this.router.navigate(['/grupos']); }
}
