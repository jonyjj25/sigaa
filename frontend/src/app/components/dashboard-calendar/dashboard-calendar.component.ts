import { Component, input, signal, ChangeDetectionStrategy, effect, ElementRef, viewChild, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { IonCard, IonCardHeader, IonCardTitle, IonCardContent, IonChip } from '@ionic/angular/standalone';
import { CalendarEvent } from '../../models/dashboard.models';
import { INSTITUTIONAL_BLUE } from '../../shared/ui-colors';
import { AuthService } from '../../services/auth.service';
import { Calendar } from '@fullcalendar/core';
import dayGridPlugin from '@fullcalendar/daygrid';
import interactionPlugin from '@fullcalendar/interaction';
import esLocale from '@fullcalendar/core/locales/es';

type PersonalCalendarEvent = CalendarEvent & { personal: true };
type CalendarDisplayEvent = CalendarEvent & { personal?: boolean };

@Component({
  selector: 'app-dashboard-calendar',
  standalone: true,
  imports: [CommonModule, IonCard, IonCardHeader, IonCardTitle, IonCardContent, IonChip],
  templateUrl: './dashboard-calendar.component.html',
  styleUrls: ['./dashboard-calendar.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardCalendarComponent {
  readonly events = input<CalendarEvent[]>([]);
  readonly calendarEl = viewChild<ElementRef<HTMLElement>>('calendar');

  protected selectedEvent = signal<CalendarDisplayEvent | null>(null);
  protected showModal = signal(false);
  protected showEditor = signal(false);
  protected readonly personalEvents = signal<PersonalCalendarEvent[]>([]);
  protected readonly editingPersonalEventId = signal<string | null>(null);
  protected readonly editorTitle = signal('');
  protected readonly editorDate = signal('');
  protected readonly editorTime = signal('');
  protected readonly editorDescription = signal('');
  protected readonly editorAllDay = signal(true);
  protected readonly editorError = signal('');

  protected readonly typeColors: Record<string, string> = {
    reunion: INSTITUTIONAL_BLUE,
    tarea: '#f59e0b',
    actividad: '#10b981',
    vencimiento: '#f59e0b',
    evento: '#8b5cf6',
  };

  private calendar: Calendar | null = null;
  private readonly destroyRef = inject(DestroyRef);
  private readonly authService = inject(AuthService);
  private readonly compactViewport = signal(false);
  private resizeObserver: ResizeObserver | null = null;
  private resizeFrame: number | null = null;
  private resizeTimer: number | null = null;
  private observedWidth = -1;

  constructor() {
    this.loadPersonalEvents();
    this.authService.sessionChanged$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.loadPersonalEvents());
    this.updateViewportMode();
    if (typeof window !== 'undefined') {
      const onResize = () => {
        this.updateViewportMode();
        this.scheduleSizeUpdate();
      };
      window.addEventListener('resize', onResize, { passive: true });
      this.destroyRef.onDestroy(() => {
        window.removeEventListener('resize', onResize);
        this.resizeObserver?.disconnect();
        if (this.resizeFrame !== null) window.cancelAnimationFrame(this.resizeFrame);
        if (this.resizeTimer !== null) window.clearTimeout(this.resizeTimer);
        this.calendar?.destroy();
      });
    }

    effect(() => {
      if (this.calendarEl()) {
        this.initCalendar();
      }
    });
  }

  private initCalendar(): void {
    const el = this.calendarEl()?.nativeElement;
    if (!el) return;

    if (this.calendar) {
      this.calendar.destroy();
    }

    const compact = this.compactViewport();

    this.calendar = new Calendar(el, {
      plugins: [dayGridPlugin, interactionPlugin],
      locale: esLocale,
      initialView: 'dayGridMonth',
      height: 'auto',
      fixedWeekCount: false,
      expandRows: false,
      dayMaxEvents: compact ? 2 : true,
      moreLinkClick: 'popover',
      headerToolbar: {
        left: compact ? 'prev,next' : 'prev,next today',
        center: 'title',
        right: compact ? 'today' : 'dayGridMonth,dayGridWeek',
      },
      buttonText: {
        today: 'Hoy',
        month: 'Mes',
        week: 'Semana',
      },
      eventTimeFormat: {
        hour: '2-digit',
        minute: '2-digit',
        hour12: false,
      },
      events: [...this.events(), ...this.personalEvents()].map(e => ({
        id: e.id,
        title: e.title,
        start: e.date,
        end: e.endDate,
        allDay: e.allDay ?? false,
        backgroundColor: this.typeColors[e.type] || '#94a3b8',
        borderColor: this.typeColors[e.type] || '#94a3b8',
        textColor: '#fff',
        extendedProps: { type: e.type, description: e.description, personal: this.isPersonalEvent(e) },
      })),
      dateClick: (info) => this.openNewPersonalEvent(info.dateStr),
      eventClick: (info) => {
        const ext = info.event.extendedProps;
        this.selectedEvent.set({
          id: info.event.id,
          title: info.event.title,
          date: info.event.startStr,
          endDate: info.event.endStr || undefined,
          type: (ext as any).type || 'evento',
          description: (ext as any).description,
          allDay: info.event.allDay,
          personal: Boolean((ext as any).personal),
        });
        this.showModal.set(true);
      },
    });

    this.calendar.render();
    this.observeCalendarSize(el);
    this.scheduleSizeUpdate();
  }

  private observeCalendarSize(el: HTMLElement): void {
    if (typeof ResizeObserver === 'undefined') return;

    this.resizeObserver?.disconnect();
    this.observedWidth = -1;
    this.resizeObserver = new ResizeObserver(entries => {
      const width = Math.round(entries[0]?.contentRect.width ?? 0);
      if (width > 0 && width !== this.observedWidth) {
        this.observedWidth = width;
        this.updateViewportMode(width);
        this.scheduleSizeUpdate();
      }
    });
    this.resizeObserver.observe(el);
  }

  private scheduleSizeUpdate(): void {
    if (typeof window === 'undefined' || !this.calendar) return;

    if (this.resizeFrame !== null) window.cancelAnimationFrame(this.resizeFrame);
    if (this.resizeTimer !== null) window.clearTimeout(this.resizeTimer);

    this.resizeFrame = window.requestAnimationFrame(() => {
      this.resizeFrame = window.requestAnimationFrame(() => {
        this.resizeFrame = null;
        this.calendar?.updateSize();
      });
    });
    this.resizeTimer = window.setTimeout(() => {
      this.resizeTimer = null;
      this.calendar?.updateSize();
    }, 180);
  }

  private updateViewportMode(availableWidth?: number): void {
    if (typeof window === 'undefined') return;

    const viewportWidth = window.visualViewport?.width ?? window.innerWidth;
    const width = availableWidth && availableWidth > 0
      ? Math.min(viewportWidth, availableWidth)
      : viewportWidth;
    this.compactViewport.set(width <= 600);
  }

  protected closeModal(): void {
    this.showModal.set(false);
    this.selectedEvent.set(null);
  }

  protected openNewPersonalEvent(date = this.today()): void {
    this.closeModal();
    this.editingPersonalEventId.set(null);
    this.editorTitle.set('');
    this.editorDate.set(date.slice(0, 10));
    this.editorTime.set('');
    this.editorDescription.set('');
    this.editorAllDay.set(true);
    this.editorError.set('');
    this.showEditor.set(true);
  }

  protected editSelectedPersonalEvent(): void {
    const event = this.selectedEvent();
    if (!event?.personal) return;

    this.closeModal();
    this.editingPersonalEventId.set(event.id);
    this.editorTitle.set(event.title);
    this.editorDate.set(event.date.slice(0, 10));
    this.editorTime.set(event.allDay ? '' : this.timeFromDate(event.date));
    this.editorDescription.set(event.description || '');
    this.editorAllDay.set(Boolean(event.allDay));
    this.editorError.set('');
    this.showEditor.set(true);
  }

  protected closeEditor(): void {
    this.showEditor.set(false);
    this.editingPersonalEventId.set(null);
    this.editorError.set('');
  }

  protected savePersonalEvent(): void {
    const title = this.editorTitle().trim();
    const date = this.editorDate();
    if (!title || !date) {
      this.editorError.set('Indica un título y una fecha para el evento.');
      return;
    }

    const allDay = this.editorAllDay();
    const event: PersonalCalendarEvent = {
      id: this.editingPersonalEventId() || this.newPersonalEventId(),
      title,
      date: allDay ? date : `${date}T${this.editorTime() || '09:00'}`,
      type: 'evento',
      description: this.editorDescription().trim() || undefined,
      allDay,
      personal: true,
    };
    const editingId = this.editingPersonalEventId();
    this.personalEvents.update(events => editingId
      ? events.map(item => item.id === editingId ? event : item)
      : [...events, event]);
    this.persistPersonalEvents();
    this.closeEditor();
  }

  protected deleteSelectedPersonalEvent(): void {
    const event = this.selectedEvent();
    if (!event?.personal) return;
    this.personalEvents.update(events => events.filter(item => item.id !== event.id));
    this.persistPersonalEvents();
    this.closeModal();
  }

  protected eventTypeLabel(event: CalendarDisplayEvent): string {
    return event.personal ? 'Evento personal' : event.type;
  }

  protected formatDate(dateStr: string): string {
    const d = new Date(dateStr);
    return d.toLocaleDateString('es-ES', {
      weekday: 'long', year: 'numeric', month: 'long', day: 'numeric',
    });
  }

  private loadPersonalEvents(): void {
    if (typeof localStorage === 'undefined') {
      this.personalEvents.set([]);
      return;
    }
    try {
      const raw = localStorage.getItem(this.personalEventsStorageKey());
      const parsed: unknown = raw ? JSON.parse(raw) : [];
      if (!Array.isArray(parsed)) throw new Error('Formato inválido');
      const events: PersonalCalendarEvent[] = [];
      for (const item of parsed) {
        if (!item || typeof item !== 'object') continue;
        const event = item as Partial<PersonalCalendarEvent>;
        if (typeof event.id !== 'string' || typeof event.title !== 'string' || typeof event.date !== 'string') continue;
        events.push({
          id: event.id,
          title: event.title,
          date: event.date,
          endDate: typeof event.endDate === 'string' ? event.endDate : undefined,
          type: 'evento' as const,
          description: typeof event.description === 'string' ? event.description : undefined,
          allDay: Boolean(event.allDay),
          personal: true as const,
        });
      }
      this.personalEvents.set(events);
    } catch {
      this.personalEvents.set([]);
    }
  }

  private persistPersonalEvents(): void {
    if (typeof localStorage === 'undefined') return;
    localStorage.setItem(this.personalEventsStorageKey(), JSON.stringify(this.personalEvents()));
  }

  private personalEventsStorageKey(): string {
    return `sigaa_personal_calendar_${this.authService.currentUser()?.id ?? 'anonymous'}`;
  }

  private isPersonalEvent(event: CalendarEvent): event is PersonalCalendarEvent {
    return (event as PersonalCalendarEvent).personal === true;
  }

  private today(): string {
    const now = new Date();
    const offset = now.getTimezoneOffset() * 60_000;
    return new Date(now.getTime() - offset).toISOString().slice(0, 10);
  }

  private timeFromDate(date: string): string {
    const time = date.match(/T(\d{2}:\d{2})/);
    return time?.[1] || '';
  }

  private newPersonalEventId(): string {
    return typeof globalThis.crypto?.randomUUID === 'function'
      ? `personal-${globalThis.crypto.randomUUID()}`
      : `personal-${Date.now()}-${Math.random().toString(36).slice(2)}`;
  }
}
