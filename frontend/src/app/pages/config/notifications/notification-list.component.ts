import { Component, inject, OnInit, ChangeDetectionStrategy, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { IonIcon } from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import {
  notificationsOutline, mailOutline, chatboxEllipsesOutline, alertCircleOutline,
  addOutline, createOutline, trashOutline, eyeOutline,
  timeOutline, globeOutline, settingsOutline, closeOutline,
  checkmarkCircleOutline, peopleOutline, statsChartOutline,
  sendOutline, calendarOutline, documentTextOutline, shieldCheckmarkOutline,
  toggleOutline, refreshOutline, saveOutline,
} from 'ionicons/icons';
import {
  ConfigDataService, NotificationChannel,
  NotificationHistoryItem, NotificationStats,
  NotificationSettingItem, NotificationSettingPayload,
} from '../services/config-data.service';

type NotifTab = 'canales' | 'tipos' | 'plantillas' | 'preferencias' | 'programacion' | 'historial' | 'resumen';

@Component({
  selector: 'app-notification-list',
  standalone: true,
  imports: [CommonModule, FormsModule, IonIcon],
  template: `
    <div class="notif-container">
      <div class="page-header">
        <div>
          <span class="page-eyebrow">Configuración</span>
          <h1 class="page-title">Notificaciones</h1>
          <p class="page-desc">Administra los avisos internos del sistema.</p>
        </div>
      </div>

      <div class="kpi-grid">
        <div class="kpi-card">
          <div class="kpi-icon" style="background:var(--brand-soft);color:var(--institutional-blue);">
            <ion-icon name="notifications-outline" />
          </div>
          <div class="kpi-info">
            <span class="kpi-value">{{canalesActivos()}} / {{canales().length}}</span>
            <span class="kpi-label">Canal interno</span>
            <span class="kpi-sub">{{canalesActivos() ? 'Habilitado' : 'Deshabilitado'}}</span>
          </div>
        </div>
        <div class="kpi-card">
          <div class="kpi-icon" style="background:#f0fdf4;color:#22c55e;">
            <ion-icon name="checkmark-circle-outline" />
          </div>
          <div class="kpi-info">
            <span class="kpi-value">{{eventosActivos()}} / {{tiposNotif().length}}</span>
            <span class="kpi-label">Eventos activos</span>
            <span class="kpi-sub">Notificaciones internas configuradas</span>
          </div>
        </div>
      </div>

      <div class="tab-bar">
        @for (t of tabs; track t.key) {
          <button class="tab-item" [class.active]="activeTab() === t.key" (click)="activeTab.set(t.key)">
            {{t.label}}
          </button>
        }
      </div>

      <!-- ======================== TAB: CANALES ======================== -->
      @if (activeTab() === 'canales') {
        <div class="channel-layout">
        <div class="full-panel channel-control-panel">
          <div class="canales-panel">
            <div class="panel-header-row">
              <div><h3 class="panel-title">Canal interno</h3><p class="panel-desc">Este es el único canal habilitado para las notificaciones del sistema.</p></div>
            </div>
            @for (c of canales(); track c.id) {
              <div class="canal-card">
                <div class="canal-info">
                  <div class="canal-icon" [style.background]="c.activo ? 'var(--brand-soft)' : 'var(--surface-subtle)'" [style.color]="c.activo ? 'var(--institutional-blue)' : 'var(--text-muted-accessible)'">
                    <ion-icon [name]="c.icono" />
                  </div>
                  <div class="canal-text">
                    <span class="canal-nombre">{{c.nombre}}</span>
              <span class="canal-desc">{{c.descripcion || 'Avisos mostrados dentro de la aplicación'}}</span>
                  </div>
                </div>
                <div class="canal-actions">
                  <label class="switch">
                    <input type="checkbox" [checked]="c.activo" (click)="toggleCanal(c.id)" />
                    <span class="switch-slider"></span>
                  </label>
                </div>
              </div>
            }
            <p class="panel-desc">Al desactivarlo se detienen las notificaciones que aparecen dentro de la aplicación.</p>
          </div>
        </div>
        <aside class="channel-help-card"><ion-icon name="shield-checkmark-outline" /><div><strong>{{eventosActivos()}} eventos activos</strong><p>Revisa y controla qué eventos generan avisos internos.</p><button type="button" (click)="activeTab.set('tipos')">Administrar eventos</button></div></aside>
        </div>
      }

      <!-- ======================== TAB: TIPOS ======================== -->
      @if (activeTab() === 'tipos') {
        <div class="full-panel">
          <div class="panel-header-row">
            <div><h3 class="panel-title">Eventos del sistema</h3><span class="panel-desc">Activa solamente los eventos que deben generar avisos dentro de la aplicación.</span></div>
            <span class="event-count">{{ filteredTiposNotif().length }} de {{ tiposNotif().length }} eventos</span>
          </div>

          <div class="event-toolbar">
            <label><ion-icon name="search-outline" /><input type="search" placeholder="Buscar evento, código o descripción..." [value]="eventSearch()" (input)="eventSearch.set($any($event.target).value)" /></label>
            <select [value]="eventCategoryFilter()" (change)="eventCategoryFilter.set($any($event.target).value)"><option value="">Todas las categorías</option>@for (category of eventCategories(); track category) {<option [value]="category">{{ category }}</option>}</select>
            <select [value]="eventStatusFilter()" (change)="eventStatusFilter.set($any($event.target).value)"><option value="">Todos los estados</option><option value="active">Activos</option><option value="inactive">Inactivos</option></select>
          </div>

          <div class="table-container">
            <table class="data-table">
              <thead>
                <tr>
                  <th>Código</th>
                  <th>Nombre</th>
                  <th>Descripción</th>
                  <th>Categoría</th>
                  <th>Notificación interna</th>
                  <th>Estado</th>
                </tr>
              </thead>
              <tbody>
                @for (t of filteredTiposNotif(); track t.id) {
                  <tr>
                    <td class="cell-code">{{t.codigo}}</td>
                    <td class="cell-name">{{t.nombre}}</td>
                    <td class="cell-desc">{{t.descripcion}}</td>
                    <td><span class="badge-categoria">{{t.categoria}}</span></td>
                    <td>
                      <div class="canal-icons">
                        <button class="event-channel-toggle" [class.active]="t.sistema" (click)="toggleCanalEvento(t, 'SISTEMA')">
                          {{t.sistema ? 'Habilitada' : 'Deshabilitada'}}
                        </button>
                      </div>
                    </td>
                    <td>
                      <label class="switch-sm">
                        <input type="checkbox" [checked]="t.activo" (click)="toggleEvento(t.id)" />
                        <span class="switch-slider"></span>
                      </label>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          @if (filteredTiposNotif().length === 0) {<div class="events-empty">No hay eventos que coincidan con los filtros.</div>}
        </div>
      }

      <!-- ======================== TAB: PLANTILLAS ======================== -->
      @if (activeTab() === 'plantillas') {
        <div class="full-panel">
          <div class="panel-header-row">
            <h3 class="panel-title">Plantillas de Notificación</h3>
            @if (!mostrarFormPlantilla()) {
              <button class="btn-add-inline" (click)="iniciarCrearPlantilla()">
                <ion-icon name="add-outline" /> Nueva Plantilla
              </button>
            }
          </div>

          @if (mostrarFormPlantilla()) {
            <div class="inline-form-wide">
              <div class="form-grid-2">
                <div class="form-group">
                  <label class="form-label">Nombre</label>
                  <input class="form-input" placeholder="Nombre de la plantilla" [ngModel]="formPlantillaNombre()" (ngModelChange)="formPlantillaNombre.set($event)" />
                </div>
                <div class="form-group">
                  <label class="form-label">Tipo</label>
                  <select class="form-input" [ngModel]="formPlantillaTipo()" (ngModelChange)="formPlantillaTipo.set($event)">
                    <option value="email">Correo Electrónico</option>
                    <option value="push">Push</option>
                    <option value="sms">SMS</option>
                    <option value="sistema">Sistema</option>
                  </select>
                </div>
              </div>
              <div class="form-group">
                <label class="form-label">Asunto</label>
                <input class="form-input" placeholder="Asunto del mensaje" [ngModel]="formPlantillaAsunto()" (ngModelChange)="formPlantillaAsunto.set($event)" />
              </div>
              <div class="form-group">
                <label class="form-label">Cuerpo</label>
                <textarea class="form-textarea" rows="6" placeholder="Cuerpo del mensaje. Usa plantilla variables para campos dinámicos." [ngModel]="formPlantillaCuerpo()" (ngModelChange)="formPlantillaCuerpo.set($event)"></textarea>
              </div>
              <div class="form-actions">
                <button class="btn-save" (click)="guardarPlantilla()">
                  <ion-icon name="save-outline" /> {{formPlantillaId() ? 'Actualizar' : 'Crear'}}
                </button>
                <button class="btn-cancel" (click)="cerrarFormPlantilla()">
                  <ion-icon name="close-outline" /> Cancelar
                </button>
              </div>
            </div>
          }

          <div class="table-container">
            <table class="data-table">
              <thead>
                <tr>
                  <th>Nombre</th>
                  <th>Tipo</th>
                  <th>Asunto</th>
                  <th>Estado</th>
                  <th>Acciones</th>
                </tr>
              </thead>
              <tbody>
                @for (p of plantillas(); track p.id) {
                  <tr>
                    <td class="cell-name">{{p.nombre}}</td>
                    <td><span class="badge-tipo" [attr.data-tipo]="p.tipo">{{p.tipo}}</span></td>
                    <td class="cell-desc">{{p.asunto}}</td>
                    <td>
                      <span class="status-dot" [class.active]="p.estado" [class.inactive]="!p.estado">
                        {{p.estado ? 'Activa' : 'Inactiva'}}
                      </span>
                    </td>
                    <td>
                      <div class="actions">
                        <button type="button" class="btn-icon" [attr.aria-label]="'Ver plantilla ' + p.nombre" title="Ver" (click)="verPlantilla(p)"><ion-icon name="eye-outline" aria-hidden="true" /></button>
                        <button type="button" class="btn-icon" [attr.aria-label]="'Editar plantilla ' + p.nombre" title="Editar" (click)="editarPlantilla(p)"><ion-icon name="create-outline" aria-hidden="true" /></button>
                        <button type="button" class="btn-icon btn-danger-icon" [attr.aria-label]="'Eliminar plantilla ' + p.nombre" title="Eliminar" (click)="eliminarPlantilla(p.id, p.nombre)"><ion-icon name="trash-outline" aria-hidden="true" /></button>
                      </div>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>

          @if (plantillaPreview()) {
            <div class="preview-wide">
              <h4 class="panel-title">Vista Previa: {{plantillaPreview()!.nombre}}</h4>
              <div class="preview-card">
                <div class="preview-header"><span class="preview-subject">{{plantillaPreview()!.asunto}}</span></div>
                <div class="preview-body" [innerHTML]="plantillaPreview()!.cuerpo"></div>
              </div>
              <button class="btn-cancel" style="margin-top:12px" (click)="plantillaPreview.set(null)">
                <ion-icon name="close-outline" /> Cerrar vista previa
              </button>
            </div>
          }
        </div>
      }

      <!-- ======================== TAB: PREFERENCIAS ======================== -->
      @if (activeTab() === 'preferencias') {
        <div class="full-panel">
          <div class="panel-header-row">
            <h3 class="panel-title">Preferencias por Evento</h3>
          </div>
          <p class="panel-desc">Configura los canales activos, prioridad y frecuencia para cada tipo de notificación.</p>

          @for (evt of tiposNotif(); track evt.id) {
            <div class="preferencia-card">
              <div class="preferencia-header" (click)="togglePreferenciaExpand(evt.id)">
                <div>
                  <span class="preferencia-nombre">{{evt.nombre}}</span>
                  <span class="preferencia-codigo">{{evt.codigo}}</span>
                </div>
                <div class="preferencia-badges">
                  @if (evt.correo) { <span class="badge-sm badge-correo">@ Correo</span> }
                  @if (evt.push) { <span class="badge-sm badge-push">P Push</span> }
                  @if (evt.sms) { <span class="badge-sm badge-sms">S SMS</span> }
                  @if (evt.sistema) { <span class="badge-sm badge-sistema">! Sistema</span> }
                </div>
              </div>
              @if (expandedEventoId() === evt.id) {
                <div class="preferencia-body">
                  @if (cargandoPreferencias()) {
                    <div class="loading-inline">Cargando configuración...</div>
                  } @else {
                    @for (s of preferenciasEvento(); track s.id) {
                      <div class="setting-row">
                        <span class="setting-canal">{{s.canal}}</span>
                        <label class="switch-sm">
                          <input type="checkbox" [checked]="s.activo" (click)="toggleSettingActivo(s)" />
                          <span class="switch-slider"></span>
                        </label>
                        <select class="form-input-sm" [ngModel]="s.prioridad" (ngModelChange)="actualizarSettingField(s.id, 'prioridad', $event)">
                          <option value="BAJA">Baja</option>
                          <option value="NORMAL">Normal</option>
                          <option value="ALTA">Alta</option>
                          <option value="URGENTE">Urgente</option>
                        </select>
                        <select class="form-input-sm" [ngModel]="s.frecuencia" (ngModelChange)="actualizarSettingField(s.id, 'frecuencia', $event)">
                          <option value="INMEDIATA">Inmediata</option>
                          <option value="DIARIA">Diaria</option>
                          <option value="SEMANAL">Semanal</option>
                          <option value="MENSUAL">Mensual</option>
                        </select>
                        <span class="setting-status" [class.active]="s.activo">{{s.activo ? 'Activo' : 'Inactivo'}}</span>
                      </div>
                    }
                    @if (preferenciasEvento().length === 0) {
                      <p class="empty-text">No hay configuración de canales para este evento. Crea una en la pestaña "Programación".</p>
                    }
                  }
                </div>
              }
            </div>
          }
        </div>
      }

      <!-- ======================== TAB: PROGRAMACIÓN ======================== -->
      @if (activeTab() === 'programacion') {
        <div class="full-panel">
          <div class="panel-header-row">
            <h3 class="panel-title">Programación de Notificaciones</h3>
          </div>
          <p class="panel-desc">Define horarios, recordatorios y vencimientos para cada canal de cada evento.</p>

          @for (evt of tiposNotif(); track evt.id) {
            <div class="programacion-card">
              <h4 class="prog-title">{{evt.nombre}}</h4>
              @for (canal of ['CORREO','PUSH','SMS','SISTEMA']; track canal) {
                <div class="prog-row">
                  <span class="prog-canal" [class.active]="isCanalActivo(evt, canal)">{{canal}}</span>
                  <div class="prog-fields">
                    <div class="prog-field">
                      <label>Horario</label>
                      <div class="time-range">
                        <input type="time" class="form-input-sm" value="08:00" [id]="'hi-'+evt.id+'-'+canal" />
                        <span>-</span>
                        <input type="time" class="form-input-sm" value="17:00" [id]="'hf-'+evt.id+'-'+canal" />
                      </div>
                    </div>
                    <div class="prog-field">
                      <label>Prioridad</label>
                      <select class="form-input-sm" [id]="'pr-'+evt.id+'-'+canal">
                        <option value="NORMAL">Normal</option>
                        <option value="ALTA">Alta</option>
                        <option value="URGENTE">Urgente</option>
                      </select>
                    </div>
                    <div class="prog-field">
                      <label>Frecuencia</label>
                      <select class="form-input-sm" [id]="'fr-'+evt.id+'-'+canal">
                        <option value="INMEDIATA">Inmediata</option>
                        <option value="DIARIA">Diaria</option>
                        <option value="SEMANAL">Semanal</option>
                      </select>
                    </div>
                    <div class="prog-field">
                      <label>Recordatorio</label>
                      <input type="number" class="form-input-sm" value="15" min="0" [id]="'rec-'+evt.id+'-'+canal" placeholder="min" />
                    </div>
                    <button type="button" class="btn-save-sm" [attr.aria-label]="'Guardar programación de ' + canal" (click)="guardarProgramacion(evt.id, canal)" [title]="'Guardar programación de ' + canal">
                      <ion-icon name="save-outline" aria-hidden="true" />
                    </button>
                  </div>
                </div>
              }
            </div>
          }
        </div>
      }

      <!-- ======================== TAB: HISTORIAL ======================== -->
      @if (activeTab() === 'historial') {
        <div class="full-panel">
          <div class="panel-header-row">
            <h3 class="panel-title">Historial de Envíos</h3>
            <button class="btn-refresh" (click)="loadHistorial()">
              <ion-icon name="refresh-outline" /> Actualizar
            </button>
          </div>

          @if (historial().length === 0) {
            <div class="empty-state">
              <ion-icon name="send-outline" class="empty-icon" />
              <p>No hay notificaciones enviadas aún.</p>
            </div>
          } @else {
            <div class="table-container">
              <table class="data-table">
                <thead>
                  <tr>
                    <th>Fecha/Hora</th>
                    <th>Evento</th>
                    <th>Canal</th>
                    <th>Destinatario</th>
                    <th>Asunto</th>
                    <th>Estado</th>
                  </tr>
                </thead>
                <tbody>
                  @for (h of historial(); track h.id) {
                    <tr>
                      <td class="cell-date">{{h.enviadoEn || h.createdAt}}</td>
                      <td>{{h.eventoCodigo}}</td>
                      <td><span class="badge-canal">{{h.canal}}</span></td>
                      <td>{{h.destinatario}}</td>
                      <td class="cell-desc">{{h.asunto}}</td>
                      <td>
                        <span class="status-dot" [class.active]="h.estado === 'enviado'" [class.inactive]="h.estado === 'error'" [class.warning]="h.estado === 'pendiente'">
                          {{h.estado}}
                        </span>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </div>
      }

      <!-- ======================== TAB: RESUMEN ======================== -->
      @if (activeTab() === 'resumen') {
        <div class="full-panel">
          <div class="panel-header-row">
            <h3 class="panel-title">Resumen de Notificaciones</h3>
            <button class="btn-refresh" (click)="loadStats()">
              <ion-icon name="refresh-outline" /> Actualizar
            </button>
          </div>

          <div class="resumen-grid">
            <div class="resumen-card">
              <div class="resumen-icon" style="background:#f0fdf4;color:#22c55e;"><ion-icon name="checkmark-circle-outline" /></div>
              <span class="resumen-value">{{stats()?.totalEnviados ?? 0}}</span>
              <span class="resumen-label">Enviados</span>
            </div>
            <div class="resumen-card">
              <div class="resumen-icon" style="background:#fef3c7;color:#f59e0b;"><ion-icon name="time-outline" /></div>
              <span class="resumen-value">{{stats()?.totalPendientes ?? 0}}</span>
              <span class="resumen-label">Pendientes</span>
            </div>
            <div class="resumen-card">
              <div class="resumen-icon" style="background:#fef2f2;color:#ef4444;"><ion-icon name="alertCircleOutline" /></div>
              <span class="resumen-value">{{stats()?.totalErrores ?? 0}}</span>
              <span class="resumen-label">Errores</span>
            </div>
            <div class="resumen-card">
              <div class="resumen-icon" style="background:var(--brand-soft);color:var(--institutional-blue);"><ion-icon name="send-outline" /></div>
              <span class="resumen-value">{{stats()?.enviadosSemana ?? 0}}</span>
              <span class="resumen-label">Últimos 7 días</span>
            </div>
          </div>

          <div class="resumen-channels">
            <h4 class="panel-title" style="margin-top:24px">Por Canal</h4>
            <div class="channel-bars">
              <div class="channel-bar-row">
                <span class="bar-label">Correo</span>
                <div class="bar-track"><div class="bar-fill bar-correo" [style.width.%]="getBarWidth('porCorreo')"></div></div>
                <span class="bar-value">{{stats()?.porCorreo ?? 0}}</span>
              </div>
              <div class="channel-bar-row">
                <span class="bar-label">Push</span>
                <div class="bar-track"><div class="bar-fill bar-push" [style.width.%]="getBarWidth('porPush')"></div></div>
                <span class="bar-value">{{stats()?.porPush ?? 0}}</span>
              </div>
              <div class="channel-bar-row">
                <span class="bar-label">SMS</span>
                <div class="bar-track"><div class="bar-fill bar-sms" [style.width.%]="getBarWidth('porSMS')"></div></div>
                <span class="bar-value">{{stats()?.porSMS ?? 0}}</span>
              </div>
              <div class="channel-bar-row">
                <span class="bar-label">Sistema</span>
                <div class="bar-track"><div class="bar-fill bar-sistema" [style.width.%]="getBarWidth('porSistema')"></div></div>
                <span class="bar-value">{{stats()?.porSistema ?? 0}}</span>
              </div>
            </div>
          </div>
        </div>
      }

    </div>
  `,
  styles: [`
    .notif-container { max-width: 1400px; margin: 0; }
    .page-header { margin-bottom: 24px; }
    .page-title { font-size: 24px; font-weight: 700; color: #0f172a; margin: 0 0 4px; letter-spacing: -0.3px; }
    .page-desc { font-size: 14px; color: #64748b; margin: 0; }
    .page-eyebrow { display: block; margin-bottom: 4px; color: var(--institutional-blue); font-size: 10px; font-weight: 800; letter-spacing: .1em; text-transform: uppercase; }

    .kpi-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 16px; margin-bottom: 20px; }
    .kpi-card { display: flex; align-items: center; gap: 16px; background: #fff; border-radius: 16px; padding: 22px; border: 1px solid #e2e8f0; box-shadow: 0 1px 3px rgba(0,0,0,0.04); }
    .kpi-icon { width: 48px; height: 48px; border-radius: 12px; display: flex; align-items: center; justify-content: center; font-size: 24px; flex-shrink: 0; }
    .kpi-info { display: flex; flex-direction: column; gap: 2px; }
    .kpi-value { font-size: 28px; font-weight: 700; color: #0f172a; line-height: 1.2; }
    .kpi-label { font-size: 13px; color: #475569; font-weight: 500; }
    .kpi-sub { font-size: 11px; color: #94a3b8; }

    .tab-bar { display: flex; gap: 4px; margin-bottom: 20px; background: #fff; border-radius: 12px; padding: 4px; border: 1px solid #e2e8f0; overflow-x: auto; }
    .tab-item { flex: 1; padding: 10px 14px; border: none; background: transparent; border-radius: 8px; font-size: 13px; font-weight: 500; color: #64748b; cursor: pointer; transition: all 0.2s; font-family: inherit; white-space: nowrap; text-align: center; }
    .tab-item:hover { background: #f8fafc; color: #334155; }
    .tab-item.active { background: var(--institutional-blue); color: #fff; font-weight: 600; }

    .content-grid { display: grid; grid-template-columns: 1fr 380px; gap: 20px; align-items: start; }

    .panel-title { font-size: 16px; font-weight: 600; color: #0f172a; margin: 0 0 16px; }
    .panel-desc { font-size: 13px; color: #64748b; margin: 0 0 16px; }
    .panel-header-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    .panel-header-row .panel-title { margin-bottom: 0; }

    .full-panel { background: #fff; border-radius: 16px; border: 1px solid #e2e8f0; padding: 20px; box-shadow: 0 1px 3px rgba(0,0,0,0.04); }
    .channel-layout { display:grid; grid-template-columns:minmax(0,1.2fr) minmax(280px,.8fr); gap:18px; align-items:stretch; }
    .channel-control-panel { max-width:none; padding:0; border:0; background:transparent; box-shadow:none; }
    .channel-control-panel .canales-panel { box-sizing: border-box; width: 100%; }
    .channel-help-card { display:flex; align-items:flex-start; gap:14px; padding:22px; border:1px solid var(--brand-soft); border-radius:16px; background:linear-gradient(145deg,var(--brand-soft),#fff); }.channel-help-card > ion-icon { flex:0 0 auto; padding:10px; border-radius:11px; color:var(--institutional-blue); background:var(--brand-soft); font-size:24px; }.channel-help-card strong { display:block; color:#0f172a; font-size:16px; }.channel-help-card p { margin:6px 0 15px; color:#64748b; font-size:13px; line-height:1.45; }.channel-help-card button { padding:8px 11px; border:0; border-radius:8px; color:#fff; background:var(--institutional-blue); font:600 12px inherit; cursor:pointer; }

    /* CANALES */
    .canales-panel { background: #fff; border-radius: 16px; border: 1px solid #e2e8f0; padding: 20px; box-shadow: 0 1px 3px rgba(0,0,0,0.04); }
    .canal-card { display: flex; align-items: center; justify-content: space-between; padding: 14px 0; border-bottom: 1px solid #f1f5f9; }
    .canal-card:last-of-type { border-bottom: none; }
    .canal-info { display: flex; align-items: center; gap: 12px; }
    .canal-icon { width: 40px; height: 40px; border-radius: 10px; display: flex; align-items: center; justify-content: center; font-size: 20px; }
    .canal-text { display: flex; flex-direction: column; gap: 2px; }
    .canal-nombre { font-size: 14px; font-weight: 600; color: #0f172a; }
    .canal-desc { font-size: 12px; color: #94a3b8; }
    .canal-actions { display: flex; align-items: center; gap: 8px; }

    .config-panel { background: #fff; border-radius: 16px; border: 1px solid #e2e8f0; padding: 20px; box-shadow: 0 1px 3px rgba(0,0,0,0.04); }
    .config-list { display: flex; flex-direction: column; gap: 6px; }
    .config-row { display: flex; align-items: center; justify-content: space-between; padding: 10px 0; border-bottom: 1px solid #f8fafc; gap: 12px; }
    .config-row:last-child { border-bottom: none; }
    .config-label { font-size: 14px; color: #334155; font-weight: 500; }
    .config-input, .config-select { padding: 8px 12px; border: 1px solid #e2e8f0; border-radius: 8px; font-size: 13px; color: #0f172a; background: #f8fafc; outline: none; font-family: inherit; max-width: 200px; }
    .config-input:focus, .config-select:focus { border-color: var(--institutional-blue); box-shadow: 0 0 0 3px rgba(37,99,235,0.08); }

    /* FORMS */
    .inline-form { display: flex; flex-direction: column; gap: 10px; margin-top: 16px; padding: 16px; background: #f8fafc; border-radius: 12px; border: 1px dashed #cbd5e1; }
    .inline-form-wide { margin-bottom: 20px; padding: 20px; background: #f8fafc; border-radius: 12px; border: 1px solid #e2e8f0; }
    .form-grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
    .form-group { display: flex; flex-direction: column; gap: 4px; margin-bottom: 12px; }
    .form-label { font-size: 12px; font-weight: 600; color: #475569; text-transform: uppercase; letter-spacing: 0.3px; }
    .form-input { padding: 8px 12px; border: 1px solid #e2e8f0; border-radius: 8px; font-size: 13px; color: #0f172a; background: #fff; outline: none; font-family: inherit; width: 100%; box-sizing: border-box; }
    .form-input:focus { border-color: var(--institutional-blue); box-shadow: 0 0 0 3px rgba(37,99,235,0.08); }
    .form-input-sm { padding: 6px 10px; border: 1px solid #e2e8f0; border-radius: 6px; font-size: 12px; color: #0f172a; background: #fff; outline: none; font-family: inherit; }
    .form-textarea { padding: 10px 12px; border: 1px solid #e2e8f0; border-radius: 8px; font-size: 13px; color: #0f172a; background: #fff; outline: none; font-family: inherit; width: 100%; resize: vertical; box-sizing: border-box; }
    .form-textarea:focus { border-color: var(--institutional-blue); box-shadow: 0 0 0 3px rgba(37,99,235,0.08); }
    .form-actions { display: flex; gap: 8px; margin-top: 12px; }

    .btn-add, .btn-add-inline { display: flex; align-items: center; justify-content: center; gap: 8px; width: 100%; padding: 10px; background: #f8fafc; border: 1px dashed #cbd5e1; border-radius: 10px; font-size: 14px; font-weight: 500; color: #64748b; cursor: pointer; font-family: inherit; }
    .btn-add:hover, .btn-add-inline:hover { background: var(--brand-soft); border-color: var(--institutional-blue); color: var(--institutional-blue); }
    .btn-add-inline { width: auto; padding: 8px 16px; font-size: 13px; }

    .btn-save, .btn-save-sm { display: inline-flex; align-items: center; gap: 6px; padding: 8px 16px; background: var(--institutional-blue); color: #fff; border: none; border-radius: 8px; font-size: 13px; font-weight: 500; cursor: pointer; font-family: inherit; }
    .btn-save:hover { background: var(--institutional-blue); }
    .btn-save-sm { padding: 6px 10px; font-size: 12px; }
    .btn-cancel { display: inline-flex; align-items: center; gap: 6px; padding: 8px 16px; background: #fff; color: #64748b; border: 1px solid #e2e8f0; border-radius: 8px; font-size: 13px; font-weight: 500; cursor: pointer; font-family: inherit; }
    .btn-cancel:hover { background: #f8fafc; }
    .btn-refresh { display: inline-flex; align-items: center; gap: 6px; padding: 8px 16px; background: #f8fafc; color: #475569; border: 1px solid #e2e8f0; border-radius: 8px; font-size: 13px; font-weight: 500; cursor: pointer; font-family: inherit; }
    .btn-refresh:hover { background: var(--brand-soft); border-color: var(--institutional-blue); }

    /* SWITCH */
    .switch { position: relative; display: inline-block; width: 44px; height: 24px; flex-shrink: 0; cursor: pointer; }
    .switch input { opacity: 0; width: 0; height: 0; }
    .switch-slider { position: absolute; inset: 0; background: #cbd5e1; border-radius: 24px; transition: all 0.25s; }
    .switch-slider::before { content: ''; position: absolute; left: 3px; top: 3px; width: 18px; height: 18px; background: #fff; border-radius: 50%; transition: transform 0.25s; box-shadow: 0 1px 3px rgba(0,0,0,0.15); }
    .switch input:checked + .switch-slider { background: var(--institutional-blue); }
    .switch input:checked + .switch-slider::before { transform: translateX(20px); }
    .switch-sm { position: relative; display: inline-block; width: 36px; height: 20px; flex-shrink: 0; cursor: pointer; }
    .switch-sm input { opacity: 0; width: 0; height: 0; }
    .switch-sm .switch-slider { position: absolute; inset: 0; background: #cbd5e1; border-radius: 20px; transition: all 0.25s; }
    .switch-sm .switch-slider::before { content: ''; position: absolute; left: 2px; top: 2px; width: 16px; height: 16px; background: #fff; border-radius: 50%; transition: transform 0.25s; box-shadow: 0 1px 3px rgba(0,0,0,0.15); }
    .switch-sm input:checked + .switch-slider { background: var(--institutional-blue); }
    .switch-sm input:checked + .switch-slider::before { transform: translateX(16px); }

    /* TABLE */
    .table-container { overflow-x: auto; }
    .data-table { width: 100%; border-collapse: collapse; }
    .data-table th { text-align: left; padding: 14px 16px; font-size: 12px; font-weight: 600; color: #64748b; text-transform: uppercase; letter-spacing: 0.5px; background: #f8fafc; border-bottom: 1px solid #e2e8f0; white-space: nowrap; }
    .data-table td { padding: 12px 16px; font-size: 14px; color: #334155; border-bottom: 1px solid #f1f5f9; }
    .data-table tbody tr:last-child td { border-bottom: none; }
    .data-table tbody tr:hover { background: #f8fafc; }
    .cell-name { font-weight: 600; color: #0f172a; }
    .cell-code { font-family: monospace; font-size: 12px; color: #64748b; }
    .cell-desc { color: #64748b; font-size: 13px; max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .cell-date { font-size: 12px; color: #64748b; white-space: nowrap; }

    .canal-icons { display: flex; gap: 6px; }
    .canal-dot { width: 26px; height: 26px; display: flex; align-items: center; justify-content: center; border-radius: 6px; font-size: 12px; font-weight: 700; background: #f1f5f9; color: #94a3b8; cursor: pointer; transition: all 0.2s; }
    .canal-dot.active { background: var(--brand-soft); color: var(--institutional-blue); }
    .canal-dot:hover { transform: scale(1.1); }
    .event-channel-toggle { min-width: 112px; padding: 6px 10px; border: 1px solid #fecaca; border-radius: 999px; background: #fff7f7; color: #b91c1c; font: 600 12px inherit; cursor: pointer; transition: background .2s,border-color .2s,color .2s; }
    .event-channel-toggle.active { border-color: #bbf7d0; background: #f0fdf4; color: #15803d; }
    .event-channel-toggle:focus-visible { outline: 3px solid rgba(37,99,235,.2); outline-offset: 2px; }
    .event-count { display:inline-flex; padding:5px 9px; border-radius:999px; color:var(--institutional-blue); background:var(--brand-soft); font-size:12px; font-weight:700; white-space:nowrap; }
    .event-toolbar { display:flex; align-items:center; gap:10px; margin:0 0 16px; padding:10px; border:1px solid #e2e8f0; border-radius:10px; background:#f8fafc; }.event-toolbar label { display:flex; align-items:center; flex:1; min-width:200px; gap:8px; padding:0 10px; border:1px solid #dbe5f2; border-radius:8px; color:#94a3b8; background:#fff; }.event-toolbar input { width:100%; padding:9px 0; border:0; outline:0; color:#334155; background:transparent; font:13px inherit; }.event-toolbar select { padding:9px 29px 9px 10px; border:1px solid #dbe5f2; border-radius:8px; color:#475569; background:#fff; font:13px inherit; }.events-empty { padding:28px; text-align:center; color:#94a3b8; font-size:13px; }

    .badge-categoria { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: 600; background: #f1f5f9; color: #475569; text-transform: capitalize; }
    .badge-canal { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: 600; background: var(--brand-soft); color: var(--institutional-blue); }
    .badge-tipo { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: 600; text-transform: uppercase; }
    .badge-tipo[data-tipo="email"] { background: var(--brand-soft); color: var(--institutional-blue); }
    .badge-tipo[data-tipo="push"] { background: #f0fdf4; color: #22c55e; }
    .badge-tipo[data-tipo="sms"] { background: #fef3c7; color: #f59e0b; }
    .badge-tipo[data-tipo="sistema"] { background: #f3e8ff; color: #a855f7; }

    .badge-sm { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: 600; margin-left: 4px; }
    .badge-correo { background: var(--brand-soft); color: var(--institutional-blue); }
    .badge-push { background: #f0fdf4; color: #22c55e; }
    .badge-sms { background: #fef3c7; color: #f59e0b; }
    .badge-sistema { background: #f3e8ff; color: #a855f7; }

    .status-dot { display: inline-flex; align-items: center; padding: 3px 10px; border-radius: 6px; font-size: 12px; font-weight: 600; text-transform: capitalize; }
    .status-dot.active { background: #f0fdf4; color: #22c55e; }
    .status-dot.inactive { background: #fef2f2; color: #ef4444; }
    .status-dot.warning { background: #fef3c7; color: #f59e0b; }

    .actions { display: flex; gap: 6px; }
    .btn-icon { width: 32px; height: 32px; border: 1px solid #e2e8f0; border-radius: 8px; background: #fff; color: #64748b; cursor: pointer; display: flex; align-items: center; justify-content: center; transition: all 0.2s; font-size: 16px; }
    .btn-icon:hover { background: #f8fafc; color: var(--institutional-blue); border-color: var(--institutional-blue); }
    .btn-danger-icon:hover { color: #ef4444; border-color: #ef4444; }

    /* PREFERENCIAS */
    .preferencia-card { border: 1px solid #e2e8f0; border-radius: 12px; margin-bottom: 12px; overflow: hidden; }
    .preferencia-header { display: flex; justify-content: space-between; align-items: center; padding: 14px 16px; cursor: pointer; background: #fafbfc; }
    .preferencia-header:hover { background: #f1f5f9; }
    .preferencia-nombre { font-weight: 600; color: #0f172a; margin-right: 8px; }
    .preferencia-codigo { font-family: monospace; font-size: 11px; color: #94a3b8; }
    .preferencia-badges { display: flex; gap: 4px; }
    .preferencia-body { padding: 16px; border-top: 1px solid #e2e8f0; }
    .setting-row { display: flex; align-items: center; gap: 12px; padding: 8px 0; border-bottom: 1px solid #f8fafc; }
    .setting-row:last-child { border-bottom: none; }
    .setting-canal { font-weight: 600; font-size: 13px; color: #334155; min-width: 80px; }
    .setting-status { font-size: 12px; font-weight: 500; }
    .setting-status.active { color: #22c55e; }
    .empty-text { font-size: 13px; color: #94a3b8; font-style: italic; margin: 0; }
    .loading-inline { font-size: 13px; color: #64748b; padding: 12px; text-align: center; }

    /* PROGRAMACIÓN */
    .programacion-card { border: 1px solid #e2e8f0; border-radius: 12px; margin-bottom: 16px; overflow: hidden; }
    .prog-title { font-size: 14px; font-weight: 600; color: #0f172a; margin: 0; padding: 14px 16px; background: #fafbfc; border-bottom: 1px solid #e2e8f0; }
    .prog-row { display: flex; align-items: center; gap: 12px; padding: 12px 16px; border-bottom: 1px solid #f8fafc; }
    .prog-row:last-child { border-bottom: none; }
    .prog-canal { font-weight: 600; font-size: 13px; min-width: 80px; color: #64748b; }
    .prog-canal.active { color: var(--institutional-blue); }
    .prog-fields { display: flex; gap: 12px; align-items: end; flex: 1; flex-wrap: wrap; }
    .prog-field { display: flex; flex-direction: column; gap: 4px; }
    .prog-field label { font-size: 11px; color: #94a3b8; font-weight: 500; }
    .time-range { display: flex; align-items: center; gap: 4px; }
    .time-range span { color: #94a3b8; }

    /* PREVIEW */
    .preview-wide { margin-top: 20px; }
    .preview-card { background: #fff; border-radius: 12px; border: 1px solid #e2e8f0; overflow: hidden; }
    .preview-header { padding: 14px 16px; background: #f8fafc; border-bottom: 1px solid #e2e8f0; }
    .preview-subject { font-weight: 600; color: #0f172a; }
    .preview-body { padding: 16px; font-size: 14px; color: #475569; line-height: 1.6; }

    /* HISTORIAL */
    .empty-state { display: flex; flex-direction: column; align-items: center; padding: 60px 20px; color: #94a3b8; text-align: center; }
    .empty-icon { font-size: 48px; color: #cbd5e1; margin-bottom: 12px; }

    /* RESUMEN */
    .resumen-grid { display: grid; grid-template-columns: repeat(4,1fr); gap: 16px; }
    .resumen-card { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 24px; background: #fff; border: 1px solid #e2e8f0; border-radius: 12px; text-align: center; }
    .resumen-icon { width: 48px; height: 48px; border-radius: 12px; display: flex; align-items: center; justify-content: center; font-size: 24px; }
    .resumen-value { font-size: 32px; font-weight: 700; color: #0f172a; }
    .resumen-label { font-size: 13px; color: #64748b; font-weight: 500; }

    .resumen-channels { margin-top: 24px; }
    .channel-bars { display: flex; flex-direction: column; gap: 12px; }
    .channel-bar-row { display: flex; align-items: center; gap: 12px; }
    .bar-label { font-size: 13px; font-weight: 500; color: #334155; min-width: 80px; }
    .bar-track { flex: 1; height: 24px; background: #f1f5f9; border-radius: 6px; overflow: hidden; }
    .bar-fill { height: 100%; border-radius: 6px; transition: width 0.5s ease; min-width: 2px; }
    .bar-correo { background: var(--institutional-blue); }
    .bar-push { background: #22c55e; }
    .bar-sms { background: #f59e0b; }
    .bar-sistema { background: #a855f7; }
    .bar-value { font-size: 13px; font-weight: 600; color: #0f172a; min-width: 40px; text-align: right; }

    @media (max-width: 1200px) { .kpi-grid { grid-template-columns: repeat(2,1fr); } .resumen-grid { grid-template-columns: repeat(2,1fr); } }
    @media (max-width: 1024px) { .content-grid, .channel-layout { grid-template-columns: 1fr; } }
    @media (max-width: 768px) { .kpi-grid, .resumen-grid { grid-template-columns: repeat(2,1fr); } .form-grid-2 { grid-template-columns: 1fr; } .event-toolbar { align-items:stretch; flex-direction:column; } }
    @media (max-width: 480px) { .kpi-grid, .resumen-grid { grid-template-columns: 1fr; } }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotificationListComponent implements OnInit {
  private readonly configData = inject(ConfigDataService);

  protected readonly activeTab = signal<NotifTab>('canales');

  protected readonly tabs: { key: NotifTab; label: string }[] = [
    { key: 'canales', label: 'Canal interno' },
    { key: 'tipos', label: 'Eventos del sistema' },
  ];

  protected readonly canales = signal<NotificationChannel[]>([]);
  protected readonly canalesActivos = computed(() => this.canales().filter(c => c.activo).length);

  protected readonly tiposNotif = signal<TipoNotifRow[]>([]);
  protected readonly eventosActivos = computed(() => this.tiposNotif().filter(t => t.activo && t.sistema).length);
  protected readonly eventSearch = signal('');
  protected readonly eventCategoryFilter = signal('');
  protected readonly eventStatusFilter = signal('');
  protected readonly eventCategories = computed(() => [...new Set(this.tiposNotif().map(event => event.categoria).filter(Boolean))].sort((a, b) => a.localeCompare(b, 'es')));
  protected readonly filteredTiposNotif = computed(() => {
    const term = this.eventSearch().trim().toLowerCase();
    const category = this.eventCategoryFilter();
    const status = this.eventStatusFilter();
    return this.tiposNotif().filter(event => {
      const matchesTerm = !term || [event.codigo, event.nombre, event.descripcion, event.categoria].some(value => value.toLowerCase().includes(term));
      const matchesCategory = !category || event.categoria === category;
      const matchesStatus = !status || (status === 'active' ? event.activo : !event.activo);
      return matchesTerm && matchesCategory && matchesStatus;
    });
  });

  protected readonly stats = signal<NotificationStats | null>(null);
  protected readonly historial = signal<NotificationHistoryItem[]>([]);
  protected readonly plantillas = signal<any[]>([]);

  protected readonly preferenciasEvento = signal<NotificationSettingItem[]>([]);
  protected readonly expandedEventoId = signal<string | null>(null);
  protected readonly cargandoPreferencias = signal(false);

  protected readonly mostrarFormPlantilla = signal(false);
  protected readonly formPlantillaId = signal<string | null>(null);
  protected readonly formPlantillaNombre = signal('');
  protected readonly formPlantillaTipo = signal('email');
  protected readonly formPlantillaAsunto = signal('');
  protected readonly formPlantillaCuerpo = signal('');
  protected readonly plantillaPreview = signal<any | null>(null);

  ngOnInit(): void {
    this.loadCanales();
    this.loadEventos();
  }

  private loadCanales(): void {
    this.configData.getNotificationChannels().subscribe({
      next: (list) => this.canales.set(list.filter(c => c.nombre.trim().toLowerCase() === 'sistema')),
    });
  }

  private loadEventos(): void {
    this.configData.getNotificationEvents().subscribe({
      next: (events) => {
        const rows: TipoNotifRow[] = events.map(evt => {
          const sistema = evt.settings?.find(s => s.canal === 'SISTEMA');
          return {
            id: String(evt.id), codigo: evt.codigo, nombre: evt.nombre,
            descripcion: evt.descripcion || '', categoria: evt.categoria || 'general',
            correo: false, push: false, sms: false,
            sistema: sistema?.activo ?? true,
            activo: evt.activo,
          };
        });
        this.tiposNotif.set(rows);
      },
    });
  }

  protected loadStats(): void {
    this.configData.getNotificationStats().subscribe({
      next: (s) => this.stats.set(s),
    });
  }

  private loadPlantillas(): void {
    this.configData.getNotificationTemplates().subscribe({
      next: (list) => this.plantillas.set(list),
    });
  }

  loadHistorial(): void {
    this.configData.getNotificationHistory().subscribe({
      next: (list) => this.historial.set(list),
    });
  }

  constructor() {
    addIcons({
      notificationsOutline, mailOutline, chatboxEllipsesOutline, alertCircleOutline,
      addOutline, createOutline, trashOutline, eyeOutline,
      timeOutline, globeOutline, settingsOutline, closeOutline,
      checkmarkCircleOutline, peopleOutline, statsChartOutline,
      sendOutline, calendarOutline, documentTextOutline, shieldCheckmarkOutline,
      toggleOutline, refreshOutline, saveOutline,
    });
  }

  protected toggleCanal(id: string): void {
    this.configData.toggleNotificationChannel(id).subscribe({
      next: (updated) => {
        this.canales.update(list => list.map(c => c.id === id ? { ...c, activo: updated.activo } : c));
      },
    });
  }

  protected toggleEvento(id: string): void {
    this.configData.toggleNotificationEvent(id).subscribe({
      next: () => this.loadEventos(),
    });
  }

  protected toggleCanalEvento(t: TipoNotifRow, canal: string): void {
    const evtId = Number(t.id);
    const canalKey = canal.toLowerCase() as 'correo' | 'push' | 'sms' | 'sistema';
    const isActive = t[canalKey];

    if (isActive) {
      this.configData.getNotificationSettings(String(evtId)).subscribe({
        next: (settings) => {
          const existing = settings.find(s => s.canal === canal && s.activo);
          if (existing) {
            this.configData.updateNotificationSetting(existing.id, this.settingPayload(existing, { activo: false })).subscribe({
              next: () => this.loadEventos(),
            });
          }
        },
      });
    } else {
      const payload: NotificationSettingPayload = {
        eventoId: evtId, canal, activo: true,
        prioridad: 'NORMAL', frecuencia: 'INMEDIATA',
        horarioInicio: '08:00', horarioFin: '17:00',
      };
      this.configData.createNotificationSetting(String(evtId), payload).subscribe({
        next: () => this.loadEventos(),
      });
    }
  }

  protected togglePreferenciaExpand(eventoId: string): void {
    if (this.expandedEventoId() === eventoId) {
      this.expandedEventoId.set(null);
    } else {
      this.expandedEventoId.set(eventoId);
      this.cargandoPreferencias.set(true);
      this.configData.getNotificationSettings(eventoId).subscribe({
        next: (settings) => {
          this.preferenciasEvento.set(settings);
          this.cargandoPreferencias.set(false);
        },
        error: () => this.cargandoPreferencias.set(false),
      });
    }
  }

  protected toggleSettingActivo(s: NotificationSettingItem): void {
    this.configData.updateNotificationSetting(s.id, this.settingPayload(s, { activo: !s.activo })).subscribe({
      next: (updated) => {
        this.preferenciasEvento.update(list => list.map(x => x.id === updated.id ? updated : x));
        this.loadEventos();
      },
    });
  }

  protected actualizarSettingField(settingId: string, field: string, value: string): void {
    const current = this.preferenciasEvento().find(s => s.id === settingId);
    if (!current) return;
    this.configData.updateNotificationSetting(settingId, this.settingPayload(current, { [field]: value })).subscribe({
      next: (updated) => {
        this.preferenciasEvento.update(list => list.map(x => x.id === updated.id ? updated : x));
      },
    });
  }

  protected guardarProgramacion(eventoId: string, canal: string): void {
    const hiEl = document.getElementById(`hi-${eventoId}-${canal}`) as HTMLInputElement;
    const hfEl = document.getElementById(`hf-${eventoId}-${canal}`) as HTMLInputElement;
    const prEl = document.getElementById(`pr-${eventoId}-${canal}`) as HTMLSelectElement;
    const frEl = document.getElementById(`fr-${eventoId}-${canal}`) as HTMLSelectElement;
    const recEl = document.getElementById(`rec-${eventoId}-${canal}`) as HTMLInputElement;

    const payload: NotificationSettingPayload = {
      eventoId: Number(eventoId), canal, activo: true,
      prioridad: prEl?.value || 'NORMAL',
      frecuencia: frEl?.value || 'INMEDIATA',
      horarioInicio: hiEl?.value || '08:00',
      horarioFin: hfEl?.value || '17:00',
      recordatorioHoras: Number(recEl?.value) || 0,
    };

    this.configData.getNotificationSettings(eventoId).subscribe({
      next: (existing) => {
        const found = existing.find(s => s.canal === canal);
        if (found) {
          this.configData.updateNotificationSetting(found.id, payload).subscribe({
            next: () => this.loadEventos(),
          });
        } else {
          this.configData.createNotificationSetting(eventoId, payload).subscribe({
            next: () => this.loadEventos(),
          });
        }
      },
    });
  }

  protected iniciarCrearPlantilla(): void {
    this.formPlantillaId.set(null);
    this.formPlantillaNombre.set('');
    this.formPlantillaTipo.set('email');
    this.formPlantillaAsunto.set('');
    this.formPlantillaCuerpo.set('');
    this.mostrarFormPlantilla.set(true);
  }

  protected editarPlantilla(p: any): void {
    this.formPlantillaId.set(p.id);
    this.formPlantillaNombre.set(p.nombre);
    this.formPlantillaTipo.set(p.tipo);
    this.formPlantillaAsunto.set(p.asunto);
    this.formPlantillaCuerpo.set(p.cuerpo);
    this.mostrarFormPlantilla.set(true);
  }

  protected verPlantilla(p: any): void {
    this.plantillaPreview.set(p);
  }

  protected guardarPlantilla(): void {
    const data = {
      nombre: this.formPlantillaNombre(),
      tipo: this.formPlantillaTipo(),
      asunto: this.formPlantillaAsunto(),
      cuerpo: this.formPlantillaCuerpo(),
    };
    if (!data.nombre) return;
    if (this.formPlantillaId()) {
      this.configData.updateNotificationTemplate(this.formPlantillaId()!, data).subscribe({
        next: () => { this.loadPlantillas(); this.cerrarFormPlantilla(); },
      });
    } else {
      this.configData.createNotificationTemplate(data).subscribe({
        next: () => { this.loadPlantillas(); this.cerrarFormPlantilla(); },
      });
    }
  }

  protected cerrarFormPlantilla(): void {
    this.mostrarFormPlantilla.set(false);
    this.formPlantillaId.set(null);
  }

  protected eliminarPlantilla(id: string, nombre: string): void {
    if (!confirm(`¿Eliminar la plantilla "${nombre}"?`)) return;
    this.configData.deleteNotificationTemplate(id).subscribe({
      next: () => this.loadPlantillas(),
    });
  }

  protected getBarWidth(key: string): number {
    const s = this.stats();
    if (!s) return 0;
    const val = (s as any)[key] ?? 0;
    const max = Math.max(s.totalEnviados ?? 1, 1);
    return Math.min((val / max) * 100, 100);
  }

  protected isCanalActivo(evt: TipoNotifRow, canal: string): boolean {
    switch (canal) {
      case 'CORREO': return evt.correo;
      case 'PUSH': return evt.push;
      case 'SMS': return evt.sms;
      case 'SISTEMA': return evt.sistema;
      default: return false;
    }
  }

  private settingPayload(setting: NotificationSettingItem, changes: Partial<NotificationSettingPayload>): NotificationSettingPayload {
    return {
      eventoId: Number(setting.eventoId),
      canal: setting.canal,
      activo: setting.activo,
      prioridad: setting.prioridad,
      frecuencia: setting.frecuencia,
      horarioInicio: setting.horarioInicio || '',
      horarioFin: setting.horarioFin || '',
      recordatorioActivo: setting.recordatorioActivo,
      recordatorioHoras: setting.recordatorioHoras || 0,
      vencimientoHoras: setting.vencimientoHoras || 0,
      mensajePersonalizado: setting.mensajePersonalizado || '',
      ...changes,
    };
  }
}

interface TipoNotifRow {
  id: string; codigo: string; nombre: string; descripcion: string; categoria: string;
  correo: boolean; push: boolean; sms: boolean; sistema: boolean; activo: boolean;
}
