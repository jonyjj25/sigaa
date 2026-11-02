import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AlertController, IonIcon, IonToggle, ToastController } from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import {
  barChartOutline,
  calendarOutline,
  checkboxOutline,
  chevronDownOutline,
  chevronUpOutline,
  closeOutline,
  createOutline,
  cubeOutline,
  documentTextOutline,
  folderOutline,
  gitPullRequestOutline,
  gridOutline,
  informationCircleOutline,
  peopleOutline,
  qrCodeOutline,
  refreshOutline,
  saveOutline,
  searchOutline,
  settingsOutline,
} from 'ionicons/icons';
import { ConfigDataService } from '../services/config-data.service';
import { DashboardService } from '../../../services/dashboard.service';
import { ConfigModule } from '../models/config.models';
import { INSTITUTIONAL_BLUE } from '../../../shared/ui-colors';

@Component({
  selector: 'app-module-list',
  standalone: true,
  imports: [CommonModule, FormsModule, IonIcon, IonToggle],
  templateUrl: './module-list.component.html',
  styleUrl: './module-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ModuleListComponent {
  private readonly dataService = inject(ConfigDataService);
  private readonly dashboardService = inject(DashboardService);
  private readonly alertCtrl = inject(AlertController);
  private readonly toastCtrl = inject(ToastController);

  protected readonly modules = signal<ConfigModule[]>([]);
  protected readonly loading = signal(true);
  protected readonly loadError = signal('');
  protected readonly dirty = signal(false);
  protected readonly saving = signal(false);
  protected readonly savingForm = signal(false);
  protected readonly showForm = signal(false);
  protected readonly editingModule = signal<ConfigModule | null>(null);
  protected readonly searchTerm = signal('');

  protected readonly filteredModules = computed(() => {
    const term = this.searchTerm().trim().toLocaleLowerCase('es');
    if (!term) return this.modules();
    return this.modules().filter(module =>
      [module.nombre, module.codigo, module.ruta, module.categoria]
        .filter(Boolean)
        .some(value => value!.toLocaleLowerCase('es').includes(term)),
    );
  });

  protected readonly reorderBlocked = computed(() => this.searchTerm().trim().length > 0);
  protected readonly iconOptions = [
    'grid-outline',
    'calendar-outline',
    'document-text-outline',
    'qr-code-outline',
    'checkbox-outline',
    'people-outline',
    'cube-outline',
    'folder-outline',
    'bar-chart-outline',
    'settings-outline',
    'git-pull-request-outline',
  ];

  protected formNombre = '';
  protected formIcono = '';
  protected formDescripcion = '';
  protected formCategoria = 'principal';
  protected formColor = INSTITUTIONAL_BLUE;
  protected formEtiqueta = '';
  protected formVisibleEnMenu = true;
  protected formVisibleEnDashboard = true;

  private snapshot: ConfigModule[] = [];

  constructor() {
    addIcons({
      barChartOutline,
      calendarOutline,
      checkboxOutline,
      chevronDownOutline,
      chevronUpOutline,
      closeOutline,
      createOutline,
      cubeOutline,
      documentTextOutline,
      folderOutline,
      gitPullRequestOutline,
      gridOutline,
      informationCircleOutline,
      peopleOutline,
      qrCodeOutline,
      refreshOutline,
      saveOutline,
      searchOutline,
      settingsOutline,
    });
    this.loadModules();
  }

  protected loadModules(): void {
    this.loading.set(true);
    this.loadError.set('');
    this.dataService.getModules().subscribe({
      next: data => {
        const ordered = [...data].sort((a, b) => a.orden - b.orden || Number(a.id) - Number(b.id));
        this.modules.set(ordered);
        this.snapshot = ordered.map(module => ({ ...module }));
        this.dirty.set(false);
        this.loading.set(false);
      },
      error: () => {
        this.modules.set([]);
        this.loadError.set('No fue posible cargar los módulos. Verifique la conexión e inténtelo nuevamente.');
        this.loading.set(false);
      },
    });
  }

  protected editModule(module: ConfigModule): void {
    this.editingModule.set(module);
    this.formNombre = module.nombre;
    this.formIcono = module.icono || 'grid-outline';
    this.formDescripcion = module.descripcion || '';
    this.formCategoria = module.categoria || 'principal';
    this.formColor = module.color || INSTITUTIONAL_BLUE;
    this.formEtiqueta = module.etiqueta || '';
    this.formVisibleEnMenu = module.visibleEnMenu ?? true;
    this.formVisibleEnDashboard = module.visibleEnDashboard ?? true;
    this.showForm.set(true);
  }

  protected cancelForm(): void {
    this.showForm.set(false);
    this.editingModule.set(null);
  }

  protected submitForm(): void {
    const module = this.editingModule();
    const nombre = this.formNombre.trim();
    if (!module || !nombre || this.savingForm()) return;

    this.savingForm.set(true);
    this.dataService.updateModule(module.id, {
      nombre,
      icono: this.formIcono,
      descripcion: this.formDescripcion.trim(),
      categoria: this.formCategoria,
      color: this.formColor,
      etiqueta: this.formEtiqueta.trim(),
      visibleEnMenu: this.formVisibleEnMenu,
      visibleEnDashboard: this.formVisibleEnDashboard,
    }).subscribe({
      next: updated => {
        this.modules.update(items => items.map(item => item.id === updated.id ? updated : item));
        this.snapshot = this.modules().map(item => ({ ...item }));
        this.savingForm.set(false);
        this.cancelForm();
        this.dashboardService.refreshMenuItems();
        this.showToast('Configuración del módulo actualizada.');
      },
      error: error => {
        this.savingForm.set(false);
        this.showError(error?.error?.message || error?.error?.error || 'No fue posible actualizar el módulo.');
      },
    });
  }

  protected setLocalState(module: ConfigModule, activo: boolean): void {
    this.modules.update(items => items.map(item => item.id === module.id ? { ...item, estado: activo } : item));
    this.updateDirtyState();
  }

  protected moveUp(module: ConfigModule): void {
    this.move(module, -1);
  }

  protected moveDown(module: ConfigModule): void {
    this.move(module, 1);
  }

  protected isFirst(module: ConfigModule): boolean {
    return this.modules().findIndex(item => item.id === module.id) === 0;
  }

  protected isLast(module: ConfigModule): boolean {
    return this.modules().findIndex(item => item.id === module.id) === this.modules().length - 1;
  }

  protected saveChanges(): void {
    if (this.saving() || !this.dirty()) return;
    this.saving.set(true);
    const body = this.modules().map((module, index) => ({
      id: module.id,
      orden: index + 1,
      activo: module.estado,
    }));

    this.dataService.saveModuleConfiguration(body).subscribe({
      next: data => {
        const ordered = [...data].sort((a, b) => a.orden - b.orden);
        this.modules.set(ordered);
        this.snapshot = ordered.map(module => ({ ...module }));
        this.dirty.set(false);
        this.saving.set(false);
        this.dashboardService.refreshMenuItems();
        this.showToast('Orden y estado guardados correctamente.');
      },
      error: error => {
        this.saving.set(false);
        this.showError(error?.error?.message || 'No fue posible guardar la configuración completa.');
      },
    });
  }

  private move(module: ConfigModule, offset: -1 | 1): void {
    if (this.reorderBlocked()) return;
    const items = [...this.modules()];
    const currentIndex = items.findIndex(item => item.id === module.id);
    const targetIndex = currentIndex + offset;
    if (currentIndex < 0 || targetIndex < 0 || targetIndex >= items.length) return;
    [items[currentIndex], items[targetIndex]] = [items[targetIndex], items[currentIndex]];
    this.modules.set(items.map((item, index) => ({ ...item, orden: index + 1 })));
    this.updateDirtyState();
  }

  private updateDirtyState(): void {
    const changed = this.modules().some(module => {
      const original = this.snapshot.find(item => item.id === module.id);
      return !original || original.estado !== module.estado || original.orden !== module.orden;
    });
    this.dirty.set(changed);
  }

  private showError(message: string): void {
    this.alertCtrl.create({ header: 'No se guardaron los cambios', message, buttons: ['Entendido'] })
      .then(alert => alert.present());
  }

  private showToast(message: string): void {
    this.toastCtrl.create({ message, duration: 2400, position: 'bottom', color: 'success' })
      .then(toast => toast.present());
  }
}
