import { Component, inject, signal, computed, ChangeDetectionStrategy, OnInit, DestroyRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, ActivatedRoute, Router } from '@angular/router';
import { FormGroup, FormControl, Validators, ReactiveFormsModule, FormArray, ValidatorFn, AbstractControl, ValidationErrors } from '@angular/forms';
import { IonIcon, IonSelect, IonSelectOption } from '@ionic/angular/standalone';
import { AlertController } from '@ionic/angular';
import { addIcons } from 'ionicons';
import { saveOutline, closeOutline, arrowBackOutline, personOutline, addOutline, trashOutline, checkmarkOutline, informationCircleOutline } from 'ionicons/icons';
import { ConfigDataService } from '../services/config-data.service';
import { ConfigRole, Unidad } from '../models/config.models';
import { forkJoin } from 'rxjs';
import { canonicalRole, isGlobalRole, isUnitRole, roleLabel } from '../../../shared/institutional-roles';

interface MembresiaForm {
  unidadId: FormControl<string | null>;
  rolId: FormControl<string | null>;
}

@Component({
  selector: 'app-user-form',
  standalone: true,
  imports: [CommonModule, RouterLink, ReactiveFormsModule, IonIcon, IonSelect, IonSelectOption],
  template: `
    <div class="page-container">
      <div class="page-header">
        <div class="header-left">
          <button type="button" class="btn-back" routerLink="/config/users" title="Volver" aria-label="Volver a usuarios">
            <ion-icon name="arrow-back-outline" aria-hidden="true" />
          </button>
          <div class="header-icon"><ion-icon name="person-outline" /></div>
          <div>
            <span class="eyebrow">Administración de usuarios</span>
            <h1>{{isEditMode() ? 'Editar Usuario' : 'Nuevo Usuario'}}</h1>
            <p class="subtitle">{{isEditMode() ? 'Actualiza la información y los accesos del usuario.' : 'Completa la información y define sus accesos iniciales.'}}</p>
          </div>
        </div>
      </div>

      <div class="form-card">
        <section class="form-section identity-section">
          <div class="section-heading">
            <span class="section-icon"><ion-icon name="person-outline" /></span>
            <div><h2>Información personal</h2><p>Datos principales para identificar e iniciar sesión.</p></div>
          </div>
          <div class="form-grid">
          <div class="form-field">
            <label class="field-label" for="user-first-name">Nombre <span>*</span></label>
            <input id="user-first-name" type="text" placeholder="Ingrese el nombre" autocomplete="given-name"
              [formControl]="userForm.controls.nombre" class="field-input"
              [class.invalid]="userForm.controls.nombre.invalid && userForm.controls.nombre.touched" />
            @if (userForm.controls.nombre.invalid && userForm.controls.nombre.touched) {
              <span class="field-error">El nombre es requerido</span>
            }
          </div>

          <div class="form-field">
            <label class="field-label" for="user-last-name">Apellido <span>*</span></label>
            <input id="user-last-name" type="text" placeholder="Ingrese el apellido" autocomplete="family-name"
              [formControl]="userForm.controls.apellido" class="field-input"
              [class.invalid]="userForm.controls.apellido.invalid && userForm.controls.apellido.touched" />
            @if (userForm.controls.apellido.invalid && userForm.controls.apellido.touched) {
              <span class="field-error">El apellido es requerido</span>
            }
          </div>

          <div class="form-field">
            <label class="field-label" for="user-username">Nombre de usuario <span>*</span></label>
            <input id="user-username" type="text" placeholder="Ej. maria.gomez" autocomplete="username"
              [formControl]="userForm.controls.username" class="field-input"
              [readonly]="!isEditMode()"
              [class.invalid]="userForm.controls.username.invalid && userForm.controls.username.touched" />
            @if (!isEditMode()) {
              <span class="field-helper">Se genera automáticamente a partir del nombre y apellido.</span>
            }
            @if (userForm.controls.username.invalid && userForm.controls.username.touched) {
              <span class="field-error">El nombre de usuario es requerido</span>
            }
          </div>

          <div class="form-field">
            <label class="field-label" for="user-email">Correo electrónico <span>*</span></label>
            <input id="user-email" type="email" placeholder="correo@institucion.gob.pa" autocomplete="email"
              [formControl]="userForm.controls.correo" class="field-input"
              [readonly]="!isEditMode()"
              [class.invalid]="userForm.controls.correo.invalid && userForm.controls.correo.touched" />
            @if (!isEditMode()) {
              <span class="field-helper">Se genera como usuario&#64;sigaa.com.</span>
            }
            @if (userForm.controls.correo.invalid && userForm.controls.correo.touched) {
              <span class="field-error">Ingrese un correo válido</span>
            }
          </div>

          <div class="form-field">
            <label class="field-label" for="user-password">Contraseña @if (!isEditMode()) { <span>*</span> }</label>
            <input id="user-password" type="password" autocomplete="new-password"
              [placeholder]="isEditMode() ? 'Dejar en blanco para mantener' : 'Ingrese la contraseña'"
              [formControl]="userForm.controls.password" class="field-input"
              [class.invalid]="userForm.controls.password.invalid && userForm.controls.password.touched" />
            @if (userForm.controls.password.invalid && userForm.controls.password.touched) {
              <span class="field-error">La contraseña es requerida</span>
            }
          </div>
        </div>
        </section>

        <section class="form-section professional-section">
          <div class="section-heading">
            <span class="section-icon"><ion-icon name="person-outline" /></span>
            <div><h2>Información profesional</h2><p>Datos que identifican el vínculo institucional del usuario.</p></div>
          </div>
          <div class="form-grid">
            <div class="form-field">
              <label class="field-label" for="user-institution">Institución <span>*</span></label>
              <input id="user-institution" type="text" placeholder="Ej. Alerta Amber Panamá" [formControl]="userForm.controls.institucion" class="field-input"
                [class.invalid]="userForm.controls.institucion.invalid && userForm.controls.institucion.touched" />
              @if (userForm.controls.institucion.invalid && userForm.controls.institucion.touched) { <span class="field-error">La institución es requerida</span> }
            </div>
            <div class="form-field">
              <label class="field-label" for="user-position">Cargo <span>*</span></label>
              <input id="user-position" type="text" list="institutional-position-options" placeholder="Selecciona o escribe un cargo" [formControl]="userForm.controls.cargo" class="field-input"
                [class.invalid]="userForm.controls.cargo.invalid && userForm.controls.cargo.touched" />
              <datalist id="institutional-position-options">
                <option value="Director"></option>
                <option value="Directora"></option>
                <option value="Director Ejecutivo"></option>
                <option value="Directora Ejecutiva"></option>
                <option value="Subdirector"></option>
                <option value="Subdirectora"></option>
              </datalist>
              <span class="field-helper">Opciones directivas disponibles; también puedes escribir otro cargo.</span>
              @if (userForm.controls.cargo.invalid && userForm.controls.cargo.touched) { <span class="field-error">El cargo es requerido</span> }
            </div>
          </div>
        </section>

        @if (isEditMode()) {
          <div class="form-field">
            <label class="field-label">Estado</label>
            <ion-select
              [formControl]="userForm.controls.estado"
              class="field-ion-select"
              interface="popover"
              aria-label="Estado del usuario"
              [interfaceOptions]="{ cssClass: 'institutional-select-popover', alignment: 'start', side: 'bottom' }">
              <ion-select-option value="activo">Activo</ion-select-option>
              <ion-select-option value="inactivo">Inactivo</ion-select-option>
              <ion-select-option value="bloqueado">Bloqueado</ion-select-option>
            </ion-select>
          </div>
        }

        <section class="form-section access-section">
          <div class="section-heading access-heading">
            <span class="section-icon"><ion-icon name="person-outline" /></span>
            <div><h2>Roles y unidades</h2><p>Define el nivel de acceso y las unidades a las que pertenece.</p></div>
          </div>

          <div class="field-group">
            <span class="field-label">Roles institucionales</span>
            @for (rol of rolesGlobales(); track rol.id) {
              <label class="checkbox-label">
                <input type="checkbox" class="field-checkbox"
                  [checked]="userForm.controls.rolGlobalIds.value.includes(rol.id)"
                  [disabled]="editingSuperadmin()" (change)="toggleGlobalRole(rol.id)" />
                <span class="custom-check" aria-hidden="true"><ion-icon name="checkmark-outline" /></span>
                <span>{{ etiquetaRol(rol.nombre) }}</span>
              </label>
            }
            <small>Dirección y Subdirección comparten funciones. Secretaría gestiona documentación.</small>
            <small>Para personal de unidad, asigna Jefe de Unidad o Miembro en las membresías.</small>
          </div>

        @if (tieneRolGlobal()) {
          <div class="admin-unit-note" role="status">
            <ion-icon name="information-circle-outline" aria-hidden="true" />
            <span>Este rol tiene alcance institucional. Las membresías de unidad se gestionan por separado.</span>
          </div>
        }
        <div class="membresias-section">
          <div class="membresias-header">
            <label class="field-label">Membresías de Unidad</label>
            <button type="button" class="btn-add" (click)="agregarMembresia()">
              <ion-icon name="add-outline" />
              Agregar Unidad
            </button>
          </div>

          @if (userForm.controls.membresias.length === 0) {
            <div class="empty-membresias">
              <ion-icon name="person-outline" />
              <strong>Sin unidades asignadas</strong>
              <span>Agrega al menos una unidad y selecciona el rol correspondiente.</span>
            </div>
          }

          @for (mem of userForm.controls.membresias.controls; track $index) {
            <div class="membresia-row">
              <div class="membresia-field">
                <label>Unidad</label>
                <select [formControl]="mem.controls.unidadId" class="field-select">
                  <option value="" disabled>Seleccione unidad</option>
                  @for (u of unidades(); track u.id) {
                    <option [value]="u.id">{{u.nombre}}</option>
                  }
                </select>
              </div>
              <div class="membresia-field">
                <label>Rol en la unidad</label>
                <select [formControl]="mem.controls.rolId" class="field-select">
                  <option value="" disabled>Seleccione rol</option>
                  @for (r of rolesUnidad(); track r.id) {
                    <option [value]="r.id">{{etiquetaRol(r.nombre)}}</option>
                  }
                </select>
              </div>
              <button type="button" class="btn-remove" (click)="eliminarMembresia($index)" title="Eliminar" aria-label="Eliminar asignación de unidad">
                <ion-icon name="trash-outline" aria-hidden="true" />
              </button>
            </div>
          }

          @if (userForm.controls.membresias.hasError('duplicateUnit')) {
            <span class="field-error field-error-block">No puede asignar la misma unidad más de una vez</span>
          }
        </div>
        </section>
      </div>

      <div class="form-actions">
        <span class="required-note"><strong>*</strong> Campos obligatorios</span>
        <a class="btn-ghost" routerLink="/config/users">
          <ion-icon name="close-outline" />
          Cancelar
        </a>
        <button class="btn-primary" [disabled]="userForm.invalid" (click)="onSave()">
          <ion-icon name="save-outline" />
          {{isEditMode() ? 'Actualizar' : 'Guardar'}}
        </button>
      </div>
    </div>
  `,
  styles: [`
    .page-container {
      font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
      max-width: 800px; margin: 0 auto;
    }
    .page-header {
      display: flex; justify-content: space-between; align-items: center;
      margin-bottom: 28px;
    }
    .header-left { display: flex; align-items: center; gap: 16px; }
    .btn-back {
      width: 40px; height: 40px; border: 1px solid #e2e8f0;
      border-radius: 10px; background: #fff; cursor: pointer;
      display: flex; align-items: center; justify-content: center;
      color: #64748b; font-size: 20px; transition: all 0.2s;
      flex-shrink: 0; text-decoration: none;
    }
    .btn-back:hover { background: #f8fafc; color: var(--institutional-blue); border-color: var(--institutional-blue); transform: translateY(-1px); }
    .header-icon {
      width: 48px; height: 48px; background: linear-gradient(135deg, var(--institutional-blue), var(--institutional-blue));
      border-radius: 12px; display: flex; align-items: center; justify-content: center;
      font-size: 24px; color: #fff; flex-shrink: 0;
      box-shadow: 0 4px 12px rgba(59,130,246,0.25);
    }
    .page-header h1 {
      font-size: 24px; font-weight: 700; color: #0f172a;
      margin: 0; letter-spacing: -0.3px;
    }
    .subtitle { color: #64748b; font-size: 14px; margin: 4px 0 0 0; }
    .form-card {
      background: #fff; border-radius: 16px;
      border: 1px solid #e2e8f0; padding: 32px;
      box-shadow: 0 12px 30px rgba(15,23,42,0.07); position: relative; overflow: hidden;
    }
    .form-card::before { content: ''; position: absolute; inset: 0 0 auto; height: 4px; background: linear-gradient(90deg, var(--institutional-blue), var(--institutional-blue)); }
    .form-grid {
      display: grid; grid-template-columns: 1fr 1fr; gap: 20px;
    }
    .form-field { display: flex; flex-direction: column; gap: 6px; }
    .field-label {
      font-size: 12px; font-weight: 600; color: var(--text-secondary);
      text-transform: uppercase; letter-spacing: 0.3px;
    }
    .field-input, .field-select {
      padding: 11px 14px; border: 1px solid var(--control-border); border-radius: 10px;
      font-size: 14px; color: var(--control-text); background: var(--control-bg);
      outline: none; transition: border-color 0.2s, box-shadow 0.2s; font-family: inherit;
    }
    .field-ion-select {
      min-height: 44px; padding: 0 14px;
      border: 1px solid #d7e0eb; border-radius: 10px;
      background: #fff; color: #0f172a; font-size: 14px;
      --padding-start: 0; --padding-end: 0;
      --placeholder-color: #94a3b8;
      transition: border-color .2s ease, background-color .2s ease;
    }
    .field-ion-select:hover { border-color: #b8c5d6; background: #fbfdff; }
    .field-ion-select.ion-focused { border-color: #94a3b8; box-shadow: none; }
    .field-input:focus, .field-select:focus {
      border-color: var(--institutional-blue); box-shadow: 0 0 0 3px rgba(59,130,246,0.1);
    }
    .field-input.invalid, .field-select.invalid {
      border-color: #ef4444;
    }
    .field-input::placeholder { color: #94a3b8; }
    .professional-section {
      margin: 26px 0 4px; padding: 20px; border: 1px solid var(--brand-soft);
      border-radius: 14px; background: linear-gradient(135deg, #f8fbff, var(--brand-soft));
    }
    .section-heading { display: flex; align-items: flex-start; gap: 10px; margin-bottom: 18px; }
    .section-heading ion-icon { color: var(--institutional-blue); font-size: 21px; margin-top: 1px; }
    .section-heading h2 { margin: 0; font-size: 15px; color: var(--text-strong); }
    .section-heading p { margin: 3px 0 0; color: var(--text-secondary); font-size: 12px; }
    .field-error {
      font-size: 12px; color: #ef4444; font-weight: 500;
    }
    .field-error-block {
      display: block; margin-top: 8px; padding: 8px 12px;
      background: #fef2f2; border-radius: 8px; font-weight: 500;
      border: 1px solid #fecaca;
    }
    .field-helper { font-size: 11px; color: var(--text-secondary); margin-top: 2px; }
    .field-select {
      cursor: pointer; appearance: none;
      background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='%2394a3b8' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpolyline points='6 9 12 15 18 9'%3E%3C/polyline%3E%3C/svg%3E");
      background-repeat: no-repeat; background-position: right 12px center;
      padding-right: 36px;
    }
    .field-checkbox {
      width: 18px; height: 18px; cursor: pointer; accent-color: var(--institutional-blue);
    }
    .checkbox-field { grid-column: 1 / -1; }
    .checkbox-label {
      display: flex; align-items: center; gap: 10px; cursor: pointer;
    }
    .checkbox-text { font-size: 14px; font-weight: 500; color: #0f172a; }
    .section-divider {
      margin: 28px 0 16px; padding-top: 24px;
      border-top: 1px solid #e2e8f0;
    }
    .section-title {
      display: flex; align-items: center; gap: 8px;
      font-size: 15px; font-weight: 600; color: #0f172a;
    }
    .section-title ion-icon { font-size: 18px; color: var(--institutional-blue); }
    .membresias-section {
      margin-top: 16px; padding: 18px; background: #f8fafc;
      border-radius: 12px; border: 1px solid #e2e8f0;
    }
    .membresias-header {
      display: flex; justify-content: space-between; align-items: center;
      margin-bottom: 14px;
    }
    .btn-add {
      display: inline-flex; align-items: center; gap: 6px;
      padding: 7px 14px; border: 1px dashed var(--institutional-blue); border-radius: 8px;
      background: var(--brand-soft); color: var(--institutional-blue); font-size: 13px; font-weight: 600;
      cursor: pointer; transition: all 0.2s; font-family: inherit;
    }
    .btn-add:hover { background: var(--brand-soft); transform: translateY(-1px); }
    .btn-add ion-icon { font-size: 16px; }
    .empty-membresias {
      text-align: center; color: #94a3b8; font-size: 13px; padding: 12px 0; margin: 0;
    }
    .membresia-row {
      display: flex; gap: 12px; align-items: center; margin-bottom: 10px;
    }
    .membresia-row:last-child { margin-bottom: 0; }
    .membresia-field { flex: 1; }
    .membresia-field .field-select { width: 100%; }
    .btn-remove {
      width: 38px; height: 38px; border: 1px solid #fecaca; border-radius: 10px;
      background: #fef2f2; color: #dc2626; cursor: pointer;
      display: flex; align-items: center; justify-content: center;
      transition: all 0.2s; font-size: 16px; flex-shrink: 0;
    }
    .btn-remove:hover { background: #fee2e2; border-color: #dc2626; transform: translateY(-1px); }
    .form-actions {
      display: flex; justify-content: flex-end; gap: 12px;
      margin-top: 28px; padding-top: 24px;
      border-top: 1px solid #e2e8f0;
    }
    button.btn-primary, a.btn-ghost {
      display: inline-flex; align-items: center; gap: 8px;
      padding: 11px 22px; border-radius: 10px;
      font-size: 14px; font-weight: 600; cursor: pointer;
      transition: all 0.25s; text-decoration: none; font-family: inherit;
    }
    button.btn-primary {
      background: linear-gradient(135deg, var(--institutional-blue), var(--institutional-blue)); color: #fff; border: none;
      box-shadow: 0 4px 14px rgba(59,130,246,0.3);
    }
    button.btn-primary:hover:not(:disabled) {
      transform: translateY(-1px);
      box-shadow: 0 6px 20px rgba(59,130,246,0.4);
    }
    button.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }
    a.btn-ghost {
      background: #fff; color: #64748b; border: 1px solid #e2e8f0;
    }
    a.btn-ghost:hover { background: #f8fafc; color: #334155; transform: translateY(-1px); }
    @media (max-width: 640px) {
      .form-grid { grid-template-columns: 1fr; }
      .membresia-row { flex-direction: column; }
      .btn-remove { width: 100%; }
    }
    .admin-unit-note {
      display: flex; align-items: flex-start; gap: 9px;
      margin-top: 16px; padding: 13px 15px;
      border: 1px solid var(--brand-soft); border-radius: 11px;
      background: var(--brand-soft); color: var(--institutional-blue);
      font-size: 13px; line-height: 1.45;
    }
    .admin-unit-note ion-icon { flex: 0 0 auto; margin-top: 1px; font-size: 18px; }

    /* Visual refresh */
    .page-container { max-width: 920px; padding-bottom: 24px; }
    .page-header { margin-bottom: 18px; }
    .header-left { gap: 12px; }
    .btn-back { width: 42px; height: 42px; border-color: #d7e3f2; color: var(--institutional-blue); }
    .btn-back:hover { transform: none; color: var(--institutional-blue); border-color: var(--institutional-blue); background: var(--brand-soft); }
    .header-icon {
      width: 52px;
      height: 52px;
      border-radius: 16px;
      background: linear-gradient(145deg, var(--institutional-blue), var(--institutional-blue));
      box-shadow: 0 10px 24px rgba(37, 99, 235, .22);
    }
    .eyebrow {
      display: block;
      margin-bottom: 3px;
      color: var(--institutional-blue);
      font-size: 10px;
      font-weight: 800;
      letter-spacing: .11em;
      text-transform: uppercase;
    }
    .page-header h1 { font-size: 25px; line-height: 1.1; }
    .subtitle { margin-top: 5px; font-size: 13px; }

    .form-card {
      padding: 18px;
      border-color: var(--border-default);
      border-radius: 16px;
      background: var(--surface-subtle);
      box-shadow: 0 10px 28px rgba(15, 23, 42, .065);
    }
    .form-card::before { display: none; }
    .form-section {
      margin: 0 0 14px;
      padding: 20px;
      border: 1px solid var(--border-default);
      border-radius: 14px;
      background: var(--surface-panel);
      box-shadow: 0 3px 12px rgba(15, 23, 42, .025);
    }
    .professional-section { background: var(--surface-panel); }
    .form-grid { gap: 17px; }
    .section-heading { align-items: center; margin-bottom: 17px; }
    .section-icon {
      display: grid;
      place-items: center;
      width: 38px;
      height: 38px;
      border-radius: 11px;
      color: var(--institutional-blue);
      background: var(--brand-soft);
      flex: 0 0 auto;
    }
    .section-heading .section-icon ion-icon { margin: 0; color: inherit; font-size: 19px; }
    .section-heading h2 { font-size: 16px; }
    .section-heading p { margin-top: 3px; font-size: 12px; }
    .access-heading { margin-bottom: 14px; }
    .field-label { font-size: 10px; font-weight: 800; letter-spacing: .06em; }
    .field-label span { color: #dc2626; }
    .field-input, .field-select { min-height: 44px; border-color: var(--control-border); background-color: var(--control-bg); }
    .field-input:hover, .field-select:hover { border-color: #bfcee0; }
    .field-input:focus, .field-select:focus { border-color: var(--institutional-blue); box-shadow: 0 0 0 3px rgba(37, 99, 235, .1); background-color: #fff; }
    .field-error { display: flex; align-items: center; gap: 5px; color: #dc2626; }

    .admin-option { margin-bottom: 14px; }
    .checkbox-label {
      align-items: center;
      padding: 14px;
      border: 1px solid var(--border-default);
      border-radius: 12px;
      background: var(--surface-subtle);
      transition: border-color .2s, background .2s;
    }
    .checkbox-label:hover { border-color: var(--brand-soft); background: var(--hover-bg); }
    .field-checkbox { position: absolute; width: 1px; height: 1px; opacity: 0; pointer-events: none; }
    .custom-check {
      display: grid;
      place-items: center;
      width: 38px;
      height: 22px;
      border-radius: 999px;
      background: #cbd5e1;
      color: transparent;
      position: relative;
      flex: 0 0 auto;
      transition: background .2s;
    }
    .custom-check::before {
      content: '';
      position: absolute;
      left: 2px;
      width: 18px;
      height: 18px;
      border-radius: 50%;
      background: #fff;
      box-shadow: 0 1px 3px rgba(15, 23, 42, .22);
      transition: transform .2s;
    }
    .field-checkbox:checked + .custom-check { color: transparent; background: var(--institutional-blue); }
    .field-checkbox:checked + .custom-check::before { transform: translateX(16px); }
    .field-checkbox:focus-visible + .custom-check { outline: 3px solid rgba(37, 99, 235, .24); outline-offset: 2px; }
    .custom-check ion-icon { display: none; }
    .admin-copy { display: grid; gap: 2px; }
    .admin-copy strong { color: #0f172a; font-size: 13px; }
    .admin-copy small { color: #64748b; font-size: 11px; line-height: 1.35; }

    .membresias-section { margin: 0; padding: 16px; border-color: var(--border-default); background: var(--surface-subtle); }
    .membresias-header { margin-bottom: 12px; }
    .btn-add { min-height: 38px; border-style: solid; border-color: var(--brand-soft); border-radius: 9px; color: var(--institutional-blue); background: var(--brand-soft); }
    .btn-add:hover { transform: none; border-color: var(--institutional-blue); background: var(--brand-soft); }
    .empty-membresias {
      display: grid;
      justify-items: center;
      gap: 4px;
      padding: 20px 12px;
      border: 1px dashed var(--border-default);
      border-radius: 11px;
      color: #64748b;
      background: var(--surface-panel);
    }
    .empty-membresias ion-icon { margin-bottom: 3px; color: #93a4ba; font-size: 25px; }
    .empty-membresias strong { color: #475569; font-size: 13px; }
    .empty-membresias span { font-size: 11px; text-align: center; }
    .membresia-row { padding: 12px; border: 1px solid var(--border-default); border-radius: 11px; background: var(--surface-panel); }
    .membresia-field { display: grid; gap: 4px; }
    .membresia-field label { color: #64748b; font-size: 9px; font-weight: 800; letter-spacing: .06em; text-transform: uppercase; }
    .btn-remove { width: 42px; height: 42px; margin-top: 13px; border-color: #fecaca; border-radius: 9px; background: var(--surface-panel); }
    .btn-remove:hover { transform: none; background: #fef2f2; }

    .form-actions {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto auto;
      align-items: center;
      gap: 10px;
      margin-top: 8px;
      padding: 4px 2px 0;
    }
    .required-note { color: var(--text-secondary); font-size: 11px; }
    .required-note strong { color: #dc2626; }
    button.btn-primary, a.btn-ghost { min-height: 42px; padding: 0 19px; }
    a.btn-ghost { color: var(--text-secondary); background: var(--surface-subtle); border-color: var(--control-border); }
    button.btn-primary { background: var(--institutional-blue); box-shadow: 0 5px 14px rgba(37, 99, 235, .22); }
    button.btn-primary:hover:not(:disabled) { transform: none; background: var(--institutional-blue); box-shadow: 0 7px 18px rgba(37, 99, 235, .27); }
    button.btn-primary:disabled { background: #cbd5e1; box-shadow: none; }
    a.btn-ghost:hover { transform: none; border-color: #cbd5e1; }
    button:focus-visible, a:focus-visible, input:focus-visible, select:focus-visible { outline: 3px solid rgba(37, 99, 235, .24); outline-offset: 2px; }

    @media (max-width: 640px) {
      .page-header .header-left { align-items: flex-start; }
      .header-icon { width: 46px; height: 46px; }
      .form-card { padding: 10px; }
      .form-section { padding: 16px; }
      .membresia-row { align-items: stretch; }
      .btn-remove { width: 100%; margin-top: 0; }
      .form-actions { grid-template-columns: repeat(2, minmax(0, 1fr)); padding: 8px 2px 0; }
      .required-note { grid-column: 1 / -1; }
      .form-actions a, .form-actions button { width: 100%; justify-content: center; }
    }

    @media (prefers-reduced-motion: reduce) {
      *, *::before, *::after { transition-duration: .01ms !important; animation-duration: .01ms !important; }
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserFormComponent implements OnInit {
  private configDataService = inject(ConfigDataService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private destroyRef = inject(DestroyRef);
  private alertCtrl = inject(AlertController);

  protected roles = signal<ConfigRole[]>([]);
  protected unidades = signal<Unidad[]>([]);

  protected rolesUnidad = computed(() => this.roles().filter(r => r.estado && isUnitRole(r.nombre)));
  protected rolesGlobales = computed(() => this.roles().filter(r => r.estado && isGlobalRole(r.nombre)
    && (canonicalRole(r.nombre) !== 'Superadmin' || this.editingSuperadmin())));
  protected tieneRolGlobal = signal(false);
  protected etiquetaRol = roleLabel;
  protected editingSuperadmin = signal(false);

  protected isEditMode = computed(() => !!this.userId());
  protected userId = signal<string | null>(null);

  protected userForm = new FormGroup({
    nombre: new FormControl('', [Validators.required, Validators.minLength(2)]),
    apellido: new FormControl('', [Validators.required, Validators.minLength(2)]),
    username: new FormControl('', [Validators.required, Validators.maxLength(100)]),
    correo: new FormControl('', [Validators.required, Validators.email]),
    institucion: new FormControl('', [Validators.required, Validators.maxLength(200)]),
    cargo: new FormControl('', [Validators.required, Validators.maxLength(150)]),
    password: new FormControl('', []),
    rolGlobalIds: new FormControl<string[]>([], { nonNullable: true }),
    estado: new FormControl('activo'),
    membresias: new FormArray<FormGroup<MembresiaForm>>([]),
  });

  constructor() {
    addIcons({ saveOutline, closeOutline, arrowBackOutline, personOutline, addOutline, trashOutline, checkmarkOutline, informationCircleOutline });
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.userId.set(id);
    }

    if (!this.isEditMode()) {
      this.userForm.controls.password.setValidators([Validators.required]);
      const credentialsSub = this.userForm.valueChanges.subscribe(({ nombre, apellido }) => {
        const username = this.buildUsername(nombre, apellido);
        this.userForm.patchValue({
          username,
          correo: username ? `${username}@sigaa.com` : '',
        }, { emitEvent: false });
      });
      this.destroyRef.onDestroy(() => credentialsSub.unsubscribe());
    }

    const adminSub = this.userForm.controls.rolGlobalIds.valueChanges.subscribe(roleIds => {
      this.tieneRolGlobal.set(roleIds.length > 0);
    });
    this.destroyRef.onDestroy(() => adminSub.unsubscribe());

    const rolesSub = this.configDataService.getRoles().subscribe(data => {
      this.roles.set(data);
      this.userForm.controls.membresias.addValidators(this.membresiaValidator());
      this.userForm.controls.membresias.updateValueAndValidity();
    });
    this.destroyRef.onDestroy(() => rolesSub.unsubscribe());

    const uniSub = this.configDataService.getUnidades().subscribe(data => this.unidades.set(data));
    this.destroyRef.onDestroy(() => uniSub.unsubscribe());

    if (this.userId()) {
      const userSub = forkJoin([this.configDataService.getRoles(), this.configDataService.getUsers()]).subscribe(([roles, users]) => {
        this.roles.set(roles);
        const user = users.find(u => u.id === this.userId());
        if (user) {
          this.userForm.patchValue({
            nombre: user.nombre,
            apellido: user.apellido,
            username: user.username,
            correo: user.correo,
            institucion: user.institucion || '',
            cargo: user.cargo || '',
            estado: user.estado,
          });

          const names = (user.roles?.length ? user.roles : [user.rol]).filter(isGlobalRole);
          const isSuperadmin = names.some(name => canonicalRole(name) === 'Superadmin');
          this.editingSuperadmin.set(isSuperadmin);
          this.userForm.controls.rolGlobalIds.setValue(this.roles().filter(r =>
            names.some(name => canonicalRole(name) === canonicalRole(r.nombre))).map(r => r.id));
          if (isSuperadmin) this.userForm.controls.rolGlobalIds.disable();

          {
            for (const m of user.unidades) {
              const memGroup = new FormGroup<MembresiaForm>({
                unidadId: new FormControl(m.unidadId ?? '', [Validators.required]),
                rolId: new FormControl(m.rolId, [Validators.required]),
              });
              this.userForm.controls.membresias.push(memGroup);
            }
          }
        }
      });
      this.destroyRef.onDestroy(() => userSub.unsubscribe());
    }
  }

  protected agregarMembresia(): void {
    const memGroup = new FormGroup<MembresiaForm>({
      unidadId: new FormControl('', [Validators.required]),
      rolId: new FormControl('', [Validators.required]),
    });
    this.userForm.controls.membresias.push(memGroup);
    this.userForm.controls.membresias.updateValueAndValidity();
  }

  protected toggleGlobalRole(id: string): void {
    if (this.editingSuperadmin()) return;
    const selected = this.userForm.controls.rolGlobalIds.value;
    this.userForm.controls.rolGlobalIds.setValue(selected.includes(id)
      ? selected.filter(roleId => roleId !== id) : [...selected, id]);
  }

  protected eliminarMembresia(index: number): void {
    this.userForm.controls.membresias.removeAt(index);
    this.userForm.controls.membresias.updateValueAndValidity();
  }

  private membresiaValidator(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const array = control as FormArray;
      const values = array.value || [];
      const errors: ValidationErrors = {};

      const unidadIds = values.map((v: any) => v.unidadId).filter((id: string) => id);
      if (unidadIds.length > 0 && new Set(unidadIds).size !== unidadIds.length) {
        errors['duplicateUnit'] = true;
      }


      return Object.keys(errors).length > 0 ? errors : null;
    };
  }

  private buildUsername(nombre: string | null | undefined, apellido: string | null | undefined): string {
    const normalize = (value: string | null | undefined) => (value || '')
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/(^-+|-+$)/g, '');
    const first = normalize(nombre);
    const last = normalize(apellido);
    return first && last ? `${first}.${last}`.slice(0, 100) : '';
  }

  protected async onSave(): Promise<void> {
    if (this.userForm.invalid) return;

    const f = this.userForm.getRawValue();
    const rolIds = f.rolGlobalIds.map(Number);

    const membresias = (f.membresias || [])
      .filter(m => m.unidadId && m.rolId)
      .map(m => ({
        unidadId: Number(m.unidadId),
        rolId: Number(m.rolId),
      }));

    const estado = f.estado || 'activo';
    const activo = estado !== 'inactivo';
    const bloqueado = estado === 'bloqueado';

    const updateData = {
      nombre: f.nombre!,
      apellido: f.apellido!,
      username: f.username!.trim(),
      correo: f.correo!,
      institucion: f.institucion!.trim(),
      cargo: f.cargo!.trim(),
      activo,
      bloqueado,
      rolIds,
      membresias,
    };
    const createData = {
      ...updateData,
      password: f.password!,
      membresias,
    };

    const obs = this.isEditMode()
      ? this.configDataService.updateUser(this.userId()!, updateData)
      : this.configDataService.createUser(createData);

    obs.subscribe({
      next: async () => {
        const msg = this.isEditMode() ? 'Usuario actualizado correctamente' : 'Usuario creado correctamente';
        const alert = await this.alertCtrl.create({
          header: 'Éxito',
          message: msg,
          buttons: ['OK'],
        });
        await alert.present();
        await alert.onDidDismiss();
        this.router.navigate(['/config/users']);
      },
      error: async (err) => {
        console.error('[UserForm] Error al guardar usuario:', err);
        let status = err.status;
        let body = '';
        if (err.error && typeof err.error === 'object') {
          body = JSON.stringify(err.error, null, 2);
        } else if (err.body) {
          body = err.body;
        } else if (typeof err.error === 'string') {
          body = err.error;
        }
        const msg = status === 403
          ? `No tienes permiso para ${this.isEditMode() ? 'actualizar' : 'crear'} usuarios. Solicita a un superadministrador que asigne el permiso correspondiente a tu rol.`
          : `Error HTTP ${status || 'desconocido'}\n\n${body}\n\n${err.message || err.statusText || ''}`;
        const alert = await this.alertCtrl.create({
          header: 'Error al guardar',
          message: msg.trim(),
          buttons: ['OK'],
        });
        await alert.present();
      },
    });
  }
}
