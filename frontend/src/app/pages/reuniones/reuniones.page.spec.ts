import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { ActionSheetController, AlertController, ToastController } from '@ionic/angular/standalone';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, of, throwError } from 'rxjs';
import { ReunionesPage } from './reuniones.page';
import { Reunion } from './reuniones.models';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { DashboardService } from '../../services/dashboard.service';

class ReunionesTestPage extends ReunionesPage {
  access() {
    return {
      iniciar: (id: string) => this.iniciarReunion(id),
      finalizar: (id: string) => this.finalizarReunion(id),
      nueva: () => this.openNewModal(),
      editar: (reunion: Reunion) => this.openEditModal(reunion),
      participantes: this.selectedParticipantes,
      reuniones: this.reuniones,
      agregar: (event: Event) => this.agregarParticipante(event),
      quitar: (value: string) => this.quitarParticipante(value),
      titulo: this.nuevoTitulo, fecha: this.nuevoFecha, modalidad: this.nuevoModalidad,
      guardar: () => this.guardarReunion(),
      cargar: () => this.ngOnInit(),
      inicio: (value: string) => this.setHoraInicio(value),
      fin: (value: string) => this.setHoraFin(value),
      horarioValido: this.horarioValido,
      unidadesManuales: this.unidadesManuales,
      unidadesSeleccionadas: this.unidadesSeleccionadas,
      usuarios: this.usuarios,
      organizador: this.nuevoOrganizador,
      filtradas: this.reunionesFiltradas,
      filtroEstado: this.filtroEstado,
      limpiar: () => this.limpiarFiltros(),
    };
  }
}

describe('Reuniones: creación, participantes e inicio', () => {
  let page: ReturnType<ReunionesTestPage['access']>;
  let api: jasmine.SpyObj<ApiService>;
  let toast: jasmine.Spy;
  let role: string;
  let permissions: boolean;
  const current = signal({ id: 1, nombre: 'Creadora', correo: 'creadora@example.test', roles: 'Miembro' });
  const meeting: Reunion = {
    id: '7', titulo: 'Reunión ficticia', fecha: '2099-10-27', fechaISO: '2099-10-27',
    horaInicio: '09:00', horaFin: '10:00', descripcion: '', area: '', organizador: 'Creadora',
    lugar: '', modalidad: 'Presencial', estado: 'programada', participantes: 1,
    participantesNombres: ['Invitado Reuniones'],
  };

  beforeEach(() => {
    role = 'Miembro'; permissions = true;
    current.set({ id: 1, nombre: 'Creadora', correo: 'creadora@example.test', roles: role });
    api = jasmine.createSpyObj('ApiService', ['get', 'post', 'put', 'patch', 'delete']);
    api.get.and.callFake(<T>(url: string) => of((url === '/api/usuarios/seleccionables' ? [
      { id: 1, nombre: 'Creadora', apellido: 'Reuniones', correo: current().correo },
      { id: 2, nombre: 'Invitado', apellido: 'Reuniones', correo: 'invitado@example.test' },
    ] : []) as T));
    api.post.and.returnValue(of(meeting)); api.put.and.returnValue(of(meeting));
    api.patch.and.returnValue(of({ ...meeting, estado: 'en_curso' }));
    toast = jasmine.createSpy('create').and.resolveTo({ present: () => Promise.resolve() });
    TestBed.configureTestingModule({ providers: [
      { provide: ApiService, useValue: api },
      { provide: AuthService, useValue: {
        currentUser: current, hasRole: (value: string) => value === role,
        isSuperAdmin: () => role === 'Superadmin', hasPermission: () => permissions,
      } },
      { provide: DashboardService, useValue: { getNotificaciones: () => of([]), getMenuItems: () => [] } },
      { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({})), snapshot: { paramMap: convertToParamMap({}) } } },
      { provide: Router, useValue: {} },
      { provide: AlertController, useValue: { create: jasmine.createSpy('alert') } },
      { provide: ActionSheetController, useValue: { create: jasmine.createSpy('sheet') } },
      { provide: ToastController, useValue: { create: toast } },
    ] });
    page = TestBed.runInInjectionContext(() => new ReunionesTestPage().access());
  });

  it('preselecciona al creador por su ID y evita duplicarlo', () => {
    page.cargar(); page.nueva();
    expect(page.participantes()).toEqual(['1']);
    const select = document.createElement('select');
    select.add(new Option('Creadora Reuniones', '1')); select.value = '1';
    page.agregar({ target: select } as unknown as Event);
    expect(page.participantes()).toEqual(['1']);
  });

  it('no convierte una hora vacía en medianoche y exige fin posterior', () => {
    page.nueva(); page.inicio('09:00'); page.fin('');
    expect(page.horarioValido()).toBeFalse();
    page.fin('08:59'); expect(page.horarioValido()).toBeFalse();
    page.fin('09:00'); expect(page.horarioValido()).toBeFalse();
    page.fin('09:01'); expect(page.horarioValido()).toBeTrue();
  });

  it('incluye unidades de participantes y manuales una sola vez', () => {
    page.nueva();
    page.usuarios.set([{ id: 1, nombre: 'Creadora', membresias: [{ unidadId: 4, unidadNombre: 'Unidad ficticia' }] }]);
    page.unidadesManuales.set([4, 5, 5]);
    expect(page.unidadesSeleccionadas()).toEqual([4, 5]);
  });

  it('convoca con el nombre completo del usuario seleccionado y limpia todos los filtros', () => {
    page.cargar(); page.nueva();
    expect(page.organizador()).toBe('Creadora Reuniones');
    page.reuniones.set([meeting]); page.filtroEstado.set('Cancelada');
    expect(page.filtradas()).toEqual([]);
    page.limpiar(); expect(page.filtradas()).toEqual([meeting]);
  });

  for (const exception of ['Secretaria', 'Superadmin']) {
    it(`no incorpora automáticamente a ${exception}`, () => {
      role = exception; page.nueva();
      expect(page.participantes()).toEqual([]);
    });
  }

  it('conserva la participación automática durante la creación', () => {
    page.nueva(); page.quitar('1');
    expect(page.participantes()).toEqual(['1']);
  });

  it('no agrega al editor ni cambia la selección al editar una reunión anterior', () => {
    page.editar(meeting);
    expect(page.participantes()).toEqual(['Invitado Reuniones']);
    page.quitar('Invitado Reuniones');
    expect(page.participantes()).toEqual([]);
  });

  it('envía IDs únicos al crear y conserva el contrato anterior de edición', () => {
    page.cargar(); page.nueva(); page.participantes.set(['1', '2', '1']);
    page.titulo.set('Reunión ficticia'); page.fecha.set('2099-10-27'); page.modalidad.set('Presencial');
    page.guardar();
    expect(api.post.calls.mostRecent().args[1]).toEqual(jasmine.objectContaining({ participanteUsuarioIds: [1, 2] }));
    expect(api.post.calls.mostRecent().args[1]).not.toEqual(jasmine.objectContaining({ participantesNombres: jasmine.anything() }));
    page.editar(meeting); page.guardar();
    expect(api.put.calls.mostRecent().args[1]).toEqual(jasmine.objectContaining({ participantesNombres: ['Invitado Reuniones'] }));
  });

  it('evita dos solicitudes de inicio mientras la primera sigue pendiente', () => {
    const pending = new Subject<Reunion>(); api.patch.and.returnValue(pending);
    void page.iniciar('7'); void page.iniciar('7');
    expect(api.patch).toHaveBeenCalledTimes(1);
    pending.complete();
  });

  for (const [status, fragment] of [[0, 'conexión'], [401, 'sesión'], [403, 'permisos'],
    [404, 'reunión'], [409, 'estado'], [500, 'servidor']] as const) {
    it(`explica el error ${status} al iniciar sin exponer detalles técnicos`, async () => {
      api.patch.and.returnValue(throwError(() => new HttpErrorResponse({
        status, error: { message: 'TEST_ONLY_INTERNAL_DETAIL' },
      })));
      await page.iniciar('7');
      const options = toast.calls.mostRecent().args[0] as { message: string };
      expect(options.message.toLowerCase()).toContain(fragment);
      expect(options.message).not.toContain('TEST_ONLY_INTERNAL_DETAIL');
    });
  }

  it('no intenta iniciar si falta el permiso funcional', () => {
    permissions = false; void page.iniciar('7');
    expect(api.patch).not.toHaveBeenCalled();
  });

  it('Secretaría puede ser invitada expresamente sin participación automática', () => {
    role = 'Secretaria'; page.cargar(); page.nueva();
    expect(page.participantes()).toEqual([]);
    const select = document.createElement('select');
    select.add(new Option('Secretaría invitada', '1')); select.value = '1';
    page.agregar({ target: select } as unknown as Event);
    page.titulo.set('Reunión ficticia'); page.fecha.set('2099-10-27'); page.modalidad.set('Presencial');
    page.guardar();
    expect(api.post.calls.mostRecent().args[1]).toEqual(jasmine.objectContaining({ participanteUsuarioIds: [1] }));
  });

  it('respeta el rechazo de inicio por recurso aunque tenga permiso funcional', () => {
    page.reuniones.set([{ ...meeting, puedeIniciar: false }]);
    void page.iniciar('7');
    expect(api.patch).not.toHaveBeenCalled();
  });

  it('permite finalizar una reunión propia cuando el backend autoriza la acción', () => {
    page.reuniones.set([{ ...meeting, estado: 'en_curso', puedeFinalizar: true }]);
    void page.finalizar('7');
    expect(api.patch).toHaveBeenCalledOnceWith('/api/reuniones/7/estado', { estado: 'realizada' });
  });

  it('permite reintentar el inicio después de un fallo temporal', () => {
    api.patch.and.returnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
    void page.iniciar('7');
    api.patch.and.returnValue(of({ ...meeting, estado: 'en_curso' }));
    void page.iniciar('7');
    expect(api.patch).toHaveBeenCalledTimes(2);
  });
});
