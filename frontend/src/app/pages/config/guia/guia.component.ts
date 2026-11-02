import { Component, ChangeDetectionStrategy, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { IonIcon } from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import {
  chevronDownOutline, chevronForwardOutline, speedometerOutline, documentTextOutline,
  calendarOutline, checkmarkCircleOutline, folderOpenOutline, barChartOutline, cubeOutline,
  settingsOutline, searchOutline, rocketOutline, arrowForwardOutline, peopleOutline,
} from 'ionicons/icons';
import { AuthService } from '../../../services/auth.service';

interface SeccionGuia {
  id: string;
  titulo: string;
  icono: string;
  resumen: string;
  ruta: string;
  disponible: () => boolean;
  abierta: boolean;
  pasos: string[];
  consejo?: string;
}

@Component({
  selector: 'app-guia', standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, IonIcon],
  templateUrl: './guia.component.html', styleUrls: ['./guia.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GuiaComponent {
  private readonly auth = inject(AuthService);
  protected readonly busqueda = signal('');
  protected readonly secciones = signal<SeccionGuia[]>([
    {
      id: 'inicio', titulo: 'Primeros pasos', icono: 'rocket-outline', resumen: 'Conoce dónde encontrar lo importante al entrar al sistema.', ruta: '/dashboard', disponible: () => true, abierta: true,
      pasos: ['Revisa el panel para ver pendientes, actividades y accesos rápidos.', 'Usa el menú lateral para abrir un módulo.', 'Haz clic en tu perfil para cerrar sesión o cambiar tus preferencias.'], consejo: 'Si no ves una opción, probablemente tu rol no tiene acceso a ella.'
    },
    {
      id: 'actas', titulo: 'Actas', icono: 'document-text-outline', resumen: 'Crea, consulta y registra las firmas de las actas de reuniones.', ruta: '/actas', disponible: () => this.auth.hasPermission('Actas', 'Ver'), abierta: false,
      pasos: ['Selecciona “Nueva acta” y completa los datos obligatorios.', 'Agrega participantes, acuerdos o archivos adjuntos cuando corresponda.', 'Guarda como borrador y continúa el proceso de firma cuando esté lista.'], consejo: 'Antes de firmar, verifica que el título, la fecha y los acuerdos sean correctos.'
    },
    {
      id: 'reuniones', titulo: 'Reuniones', icono: 'calendar-outline', resumen: 'Programa reuniones y organiza a sus participantes.', ruta: '/reuniones', disponible: () => this.auth.hasPermission('Reuniones', 'Ver'), abierta: false,
      pasos: ['Pulsa “Nueva reunión” si tienes permiso de creación.', 'Indica título, fecha, hora de inicio y fin, modalidad y participantes.', 'Consulta el detalle para iniciar o actualizar la reunión cuando tus permisos lo permitan.'], consejo: 'El creador participa automáticamente, excepto Secretaría y Superadmin. Secretaría puede consultar reuniones sin ser participante. Al iniciar se notifica a los participantes.'
    },
    {
      id: 'tareas', titulo: 'Tareas', icono: 'checkmark-circle-outline', resumen: 'Asigna trabajo, define fechas límite y consulta el avance.', ruta: '/tareas', disponible: () => this.auth.hasPermission('Tareas', 'Ver'), abierta: false,
      pasos: ['Crea una tarea e indica a quién está asignada.', 'Define prioridad y fechas de inicio y finalización.', 'Usa el checklist para registrar subtareas y consulta el historial de responsable, estado y avance.'], consejo: 'La fecha límite vencida no completa la tarea. Mantén actualizado el estado y revisa sus adjuntos desde el detalle.'
    },
    {
      id: 'documentos', titulo: 'Documentos', icono: 'folder-open-outline', resumen: 'Guarda archivos institucionales y consulta sus versiones.', ruta: '/documentos', disponible: () => this.auth.hasPermission('Documentos', 'Ver'), abierta: false,
      pasos: ['Completa categoría, unidad responsable y clasificación; la carpeta organiza el documento sin ampliar sus permisos.', 'El primer archivo genera la versión 1.0. Reemplazarlo por contenido diferente genera una nueva versión automáticamente.', 'Consulta versiones anteriores, descargas autorizadas y aprobaciones desde el detalle. La aprobación corresponde a una versión concreta.'], consejo: 'Cambiar metadatos o adjuntar contenido idéntico no incrementa la versión. Los adjuntos de Tareas se gestionan desde su tarea de origen.'
    },
    {
      id: 'notas', titulo: 'Correspondencia · Notas', icono: 'document-text-outline', resumen: 'Redacta notas, utiliza numeración anual y registra su seguimiento.', ruta: '/correspondencia/notas', disponible: () => this.auth.hasPermission('Notas', 'Ver'), abierta: false,
      pasos: ['Crea un borrador y selecciona los destinatarios y el responsable de seguimiento.', 'Revisa la vista previa y oficializa la nota cuando esté completa y tengas permiso.', 'Registra el seguimiento y adjunta el PDF recibido cuando corresponda. Consulta los movimientos en el historial.'], consejo: 'Marcar firmada confirma una firma física; no equivale a una firma digital con certificado. El formato institucional del documento se conserva al visualizar, descargar e imprimir.'
    },
    {
      id: 'memos', titulo: 'Correspondencia · Memos', icono: 'document-text-outline', resumen: 'Registra memos con número, destinatario y responsable de seguimiento.', ruta: '/correspondencia/memos', disponible: () => this.auth.hasPermission('Memos', 'Ver'), abierta: false,
      pasos: ['Registra el asunto, destinatario y responsable y utiliza un número disponible.', 'Abre el detalle para consultar el seguimiento y descargar el documento.', 'Completa o anula el memo únicamente mediante las acciones autorizadas; la trazabilidad se conserva.'], consejo: 'La numeración de Memos y la de Notas son independientes.'
    },
    {
      id: 'grupos', titulo: 'Grupos', icono: 'people-outline', resumen: 'Consulta o administra grupos de trabajo según tu rol.', ruta: '/grupos', disponible: () => this.auth.hasPermission('Grupos', 'Ver'), abierta: false,
      pasos: ['Selecciona un grupo para ver su unidad, responsable y miembros.', 'Como Jefe de Unidad, crea y administra solo los grupos de tu unidad.', 'Como miembro, podrás consultar únicamente los grupos a los que perteneces.'], consejo: 'El responsable del grupo se asigna automáticamente al Jefe de la Unidad.'
    },
    {
      id: 'reportes', titulo: 'Reportes', icono: 'bar-chart-outline', resumen: 'Genera informes y exporta información cuando tengas acceso.', ruta: '/reporte', disponible: () => this.auth.hasPermission('Reporte', 'Ver'), abierta: false,
      pasos: ['Selecciona una plantilla o genera un reporte indicando el tipo, período y formato.', 'Descarga el archivo cuando el reporte esté listo y tengas acceso.', 'Configura informes programados cuando tengas permiso y consulta su próxima ejecución e historial.'], consejo: 'El correo requiere configuración del servidor. Un fallo de envío no equivale a una descarga disponible ni confirma por sí solo la generación del archivo.'
    },
    {
      id: 'inventario', titulo: 'Inventario', icono: 'cube-outline', resumen: 'Administra activos institucionales.', ruta: '/inventario', disponible: () => ['Superadmin', 'Director', 'Subdirector'].some(role => this.auth.hasRole(role)) && this.auth.hasPermission('Inventario', 'Ver'), abierta: false,
      pasos: ['Registra el activo con su código, ubicación y responsable.', 'Actualiza su estado cuando entre en mantenimiento o salga de servicio.', 'Consulta el historial antes de realizar cambios importantes.']
    },
    {
      id: 'configuracion', titulo: 'Configuración', icono: 'settings-outline', resumen: 'Administra el sistema; disponible solo para Superadmin.', ruta: '/config', disponible: () => this.auth.isSuperAdmin(), abierta: false,
      pasos: ['Gestiona usuarios, roles, permisos y el catálogo de Organización desde sus secciones.', 'Administra las políticas funcionales de seguridad y consulta la auditoría según tus permisos.', 'Usa Perfil, Contraseña y Preferencias para personalizar tu propia cuenta.'], consejo: 'CORS, duraciones técnicas de tokens y máximos absolutos de carga se definen en el backend por ambiente. La pantalla no sustituye las comprobaciones de permisos del servidor.'
    },
  ]);

  protected readonly filtradas = computed(() => {
    const term = this.busqueda().trim().toLowerCase();
    return this.secciones().filter(s => s.disponible() && (!term || `${s.titulo} ${s.resumen} ${s.pasos.join(' ')} ${s.consejo || ''}`.toLowerCase().includes(term)));
  });

  constructor() { addIcons({ chevronDownOutline, chevronForwardOutline, speedometerOutline, documentTextOutline, calendarOutline, checkmarkCircleOutline, folderOpenOutline, barChartOutline, cubeOutline, settingsOutline, searchOutline, rocketOutline, arrowForwardOutline, peopleOutline }); }
  protected toggle(id: string): void { this.secciones.update(list => list.map(s => s.id === id ? { ...s, abierta: !s.abierta } : s)); }
}
