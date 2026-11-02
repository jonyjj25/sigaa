import { readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const projectRoot = fileURLToPath(new URL('../', import.meta.url));
const transformations = [
  ['src/app/pages/tareas/tareas.page.scss', '.context-danger {\n  color: #ef4444 !important;\n\n  ion-icon {\n    color: #ef4444 !important;\n  }\n\n  &:hover {\n    background: #fef2f2 !important;\n  }\n}', '.context-menu .context-danger {\n  color: var(--state-danger-text);\n\n  ion-icon { color: var(--state-danger-text); }\n\n  &:hover { background: var(--state-danger-bg); }\n}'],
  ['src/app/pages/tareas/tareas.page.scss', '\n.context-danger {\n  color: #ef4444;\n}\n', '\n'],
  ['src/app/pages/tareas/tareas.page.scss', '\n.context-danger:hover {\n  background: #fef2f2;\n}\n\n.context-danger ion-icon {\n  color: #ef4444;\n}\n', '\n'],
  ['src/app/pages/tareas/tareas.page.scss', '    width: 100% !important;\n', '    width: 100%;\n'],
  ['src/app/pages/tareas/tareas.page.scss', '.kpi-card .kpi-icon {\n  background: var(--brand-soft) !important;\n  color: #1f4e85 !important;', '.tareas-content .kpi-card .kpi-icon {\n  background: var(--brand-soft);\n  color: var(--institutional-blue);'],
  ['src/app/pages/reportes/reportes.page.scss', '.trend-up {\n  color: #22c55e !important;', '.trend-up {\n  color: var(--state-success-text);'],
  ['src/app/pages/reportes/reportes.page.scss', '.schedule-error { color: #dc2626 !important; }', '.schedule-card .schedule-error { color: var(--state-danger-text); }'],
  ['src/app/pages/reuniones/reuniones.page.scss', '.table-container th:nth-child(7) { width: 8% !important; }', '.table-container th:nth-child(7) { width: 8%; }'],
  ['src/app/pages/asistencia-qr/asistencia-qr.page.scss', '.empty-cell { text-align: center; color: #94a3b8; padding: 32px !important; }', 'td.empty-cell { text-align: center; color: var(--text-muted-accessible); padding: 32px; }'],
  ['src/app/pages/asistencia-publica/asistencia-publica.page.scss', '.search-input {\n  padding-left: 42px !important;\n}', '.search-wrapper .search-input {\n  padding-left: 42px;\n}'],
  ['src/app/pages/actas/actas.page.scss', '    width: 100% !important;\n', '    width: 100%;\n'],
  ['src/app/pages/solicitudes/solicitudes.page.scss', '.modal-body label{grid-column:1!important}', '.modal-body label:nth-child(n){grid-column:1/-1}'],
  ['src/app/pages/grupos/grupos.page.scss', '.off{color:#b91c1c!important;background:#fee2e2!important}', '.group-card>span.off,.status.off{color:var(--state-danger-text);background:var(--state-danger-bg)}'],
  ['src/app/pages/grupos/grupos.page.scss', '.toggle{display:flex!important;align-items:center;gap:8px}', '.modal-body label.toggle{display:flex;align-items:center;gap:8px}'],
  ['src/app/pages/documentos/documentos.page.scss', '.folder-choice{position:relative;display:flex;align-items:flex-start;gap:10px;min-height:78px;padding:14px!important;border:1px solid #dbe5f1!important;border-radius:12px!important;', '.folder-type-choice .folder-choice{position:relative;display:flex;align-items:flex-start;gap:10px;min-height:78px;padding:14px;border:1px solid var(--border-default);border-radius:12px;'],
  ['src/app/pages/documentos/documentos.page.scss', '.folder-choice span{display:grid!important;', '.folder-choice span{display:grid;'],
  ['src/app/pages/documentos/documentos.page.scss', '.folder-choice:hover{border-color:var(--institutional-blue)!important;', '.folder-choice:hover{border-color:var(--institutional-blue);'],
  ['src/app/pages/documentos/documentos.page.scss', '.folder-choice.selected{border-color:var(--institutional-blue)!important;', '.folder-choice.selected{border-color:var(--institutional-blue);'],
  ['src/app/pages/documentos/documentos.page.scss', '.folder-choice.shared.selected{border-color:#10b981!important;', '.folder-choice.shared.selected{border-color:#10b981;'],
];

let updatedFiles = 0;
let updatedRules = 0;
const grouped = Map.groupBy(transformations, ([path]) => path);
for (const [relativePath, entries] of grouped) {
  const url = new URL(relativePath.replaceAll('\\', '/'), new URL('../', import.meta.url));
  const before = await readFile(url, 'utf8');
  let after = before;
  for (const [, search, replacement] of entries) {
    if (after.includes(search)) {
      after = after.replace(search, replacement);
      updatedRules += 1;
    }
  }
  if (after !== before) {
    await writeFile(url, after, 'utf8');
    updatedFiles += 1;
  }
}

console.log(`Limpieza CSS completada: ${updatedRules} reglas consolidadas en ${updatedFiles} archivos.`);
