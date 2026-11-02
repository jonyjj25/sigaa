export interface DonutItem {
  label: string;
  value: number;
  color: string;
}

export type DashboardSection = 'kpis' | 'chartData' | 'events' | 'activities' | 'inventory' | 'tasks' | 'meetings';
export interface DashboardSectionState { loading: boolean; error: string; }
export type DashboardSectionStates = Record<DashboardSection, DashboardSectionState>;

export interface DonutData {
  items: DonutItem[];
}

export interface CalendarEvent {
  id: string;
  title: string;
  date: string;
  endDate?: string;
  type: 'reunion' | 'actividad' | 'vencimiento' | 'evento' | 'tarea';
  description?: string;
  allDay?: boolean;
  estado?: string;
}

export interface Notification {
  id: string;
  title: string;
  message: string;
  type: 'info' | 'success' | 'warning' | 'error';
  timestamp: Date;
  read: boolean;
}

export interface Activity {
  id: string;
  user: string;
  action: string;
  target: string;
  timestamp: Date;
  avatar?: string;
}

export interface KpiCard {
  label: string;
  value: string | number;
  subtitle: string;
  icon: string;
  trend: 'up' | 'down' | 'neutral';
  trendValue?: string;
  trendDescription?: string;
  color: string;
  route?: string;
}

export interface SidebarItem {
  label: string;
  icon: string;
  route: string;
  badge?: string | number;
  divider?: boolean;
}

export interface UserInfo {
  id?: number;
  nombre: string;
  correo: string;
  cambioContrasenaRequerido?: boolean;
  roles: string;
  permisos?: string[];
  has2fa?: boolean;
  fotografiaRuta?: string;
}

export interface UserPreferences {
  theme: string;
  language: string;
  timezone: string;
  dateFormat: string;
  timeFormat: string;
  homePage: string;
  recordsPerPage: number;
  sidebarExpanded: boolean;
  animationsEnabled: boolean;
  emailNotifications: boolean;
  pushNotifications: boolean;
  soundEnabled: boolean;
  confirmationsEnabled: boolean;
  fontSize: string;
  highContrast: boolean;
}

export const DEFAULT_PREFERENCES: UserPreferences = {
  theme: 'light',
  language: 'es',
  timezone: 'America/Panama',
  dateFormat: 'DD/MM/YYYY',
  timeFormat: 'HH:mm',
  homePage: 'dashboard',
  recordsPerPage: 20,
  sidebarExpanded: true,
  animationsEnabled: true,
  emailNotifications: true,
  pushNotifications: true,
  soundEnabled: true,
  confirmationsEnabled: true,
  fontSize: 'medium',
  highContrast: false,
};

export interface ChartData {
  categories: string[];
  series: ChartSerie[];
}

export interface ChartSerie {
  name: string;
  data: number[];
}
