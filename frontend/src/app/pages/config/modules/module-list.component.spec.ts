import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AlertController, ToastController } from '@ionic/angular/standalone';
import { of } from 'rxjs';
import { ModuleListComponent } from './module-list.component';
import { ConfigDataService } from '../services/config-data.service';
import { DashboardService } from '../../../services/dashboard.service';
import { ConfigModule } from '../models/config.models';

describe('ModuleListComponent', () => {
  let fixture: ComponentFixture<ModuleListComponent>;
  let component: ModuleListComponent;
  let dataService: jasmine.SpyObj<ConfigDataService>;

  const modules: ConfigModule[] = [
    {
      id: '1', nombre: 'Panel', codigo: 'panel', ruta: '/dashboard', icono: 'grid-outline',
      descripcion: 'Panel institucional', categoria: 'principal', color: '#2563eb',
      estado: true, orden: 1, visibleEnMenu: true, visibleEnDashboard: true,
    },
    {
      id: '2', nombre: 'Documentos', codigo: 'documentos', ruta: '/documentos', icono: 'folder-outline',
      categoria: 'documentos', color: '#2563eb', estado: false, orden: 2,
      visibleEnMenu: true, visibleEnDashboard: true,
    },
  ];

  beforeEach(async () => {
    dataService = jasmine.createSpyObj<ConfigDataService>('ConfigDataService', [
      'getModules', 'updateModule', 'saveModuleConfiguration',
    ]);
    dataService.getModules.and.returnValue(of(modules.map(item => ({ ...item }))));
    dataService.updateModule.and.callFake((id, data) => of({
      ...modules.find(item => item.id === id)!,
      ...data,
    }));
    dataService.saveModuleConfiguration.and.returnValue(of(modules.map(item => ({ ...item }))));

    const present = jasmine.createSpy('present');
    await TestBed.configureTestingModule({
      imports: [ModuleListComponent],
      providers: [
        { provide: ConfigDataService, useValue: dataService },
        { provide: DashboardService, useValue: { refreshMenuItems: jasmine.createSpy('refreshMenuItems') } },
        { provide: AlertController, useValue: { create: () => Promise.resolve({ present }) } },
        { provide: ToastController, useValue: { create: () => Promise.resolve({ present }) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ModuleListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('shows installed modules without create or delete controls', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Módulos instalados');
    expect(text).not.toContain('Crear Módulo');
    expect(fixture.nativeElement.querySelector('.delete')).toBeNull();
  });

  it('keeps code, route and active state out of presentation updates', () => {
    (component as any).editModule(modules[1]);
    (component as any).formNombre = 'Gestión documental';
    (component as any).submitForm();

    const [, payload] = dataService.updateModule.calls.mostRecent().args;
    expect(payload.nombre).toBe('Gestión documental');
    expect(payload.ruta).toBeUndefined();
    expect(payload.codigo).toBeUndefined();
    expect(payload.activo).toBeUndefined();
  });

  it('saves state and sequential order in one request', () => {
    (component as any).setLocalState(modules[1], true);
    (component as any).moveUp(modules[1]);
    (component as any).saveChanges();

    expect(dataService.saveModuleConfiguration).toHaveBeenCalledOnceWith([
      { id: '2', orden: 1, activo: true },
      { id: '1', orden: 2, activo: true },
    ]);
  });

  it('blocks reordering while a search is active', () => {
    (component as any).searchTerm.set('doc');
    (component as any).moveUp(modules[1]);

    expect((component as any).modules()[0].id).toBe('1');
  });
});
