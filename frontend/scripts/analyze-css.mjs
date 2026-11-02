import { readdir, readFile } from 'node:fs/promises';
import { join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import postcss from 'postcss';
import * as sass from 'sass';

const projectRoot = fileURLToPath(new URL('../', import.meta.url));
const targets = [
  'src/app/shared/styles/_dashboard-common.scss',
  'src/app/pages/actas/actas.page.scss',
  'src/app/pages/reuniones/reuniones.page.scss',
  'src/app/pages/documentos/documentos.page.scss',
  'src/app/pages/tareas/tareas.page.scss',
  'src/app/pages/reportes/reportes.page.scss',
];

function contextFor(rule) {
  const parents = [];
  let parent = rule.parent;
  while (parent && parent.type !== 'root') {
    if (parent.type === 'atrule') parents.unshift(`@${parent.name} ${parent.params}`);
    parent = parent.parent;
  }
  return parents.join(' > ');
}

function declarationKey(rule) {
  return rule.nodes
    .filter(node => node.type === 'decl')
    .map(node => `${node.prop}:${node.value}${node.important ? '!important' : ''}`)
    .sort()
    .join(';');
}

let duplicateBlocks = 0;
for (const target of targets) {
  const path = join(projectRoot, target);
  const result = sass.compile(path, { style: 'expanded', loadPaths: [join(projectRoot, 'src')] });
  const root = postcss.parse(result.css);
  const seen = new Map();
  const duplicates = [];
  root.walkRules(rule => {
    const declarations = declarationKey(rule);
    if (!declarations) return;
    const key = `${contextFor(rule)}|${rule.selector}|${declarations}`;
    if (seen.has(key)) duplicates.push({ selector: rule.selector, line: rule.source?.start?.line, firstLine: seen.get(key) });
    else seen.set(key, rule.source?.start?.line);
  });
  duplicateBlocks += duplicates.length;
  console.log(`${relative(projectRoot, path)}: ${duplicates.length} bloques exactamente duplicados`);
  for (const duplicate of duplicates.slice(0, 12)) {
    console.log(`  - ${duplicate.selector} (líneas compiladas ${duplicate.firstLine} y ${duplicate.line})`);
  }
}

console.log(`Total: ${duplicateBlocks} bloques exactamente duplicados.`);

async function filesBelow(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const nested = await Promise.all(entries.map(entry => {
    const path = join(directory, entry.name);
    return entry.isDirectory() ? filesBelow(path) : [path];
  }));
  return nested.flat().filter(path => /\.(?:css|scss)$/.test(path));
}

async function importantCount(paths) {
  let count = 0;
  for (const path of paths) count += ((await readFile(path, 'utf8')).match(/!important/g) || []).length;
  return count;
}

const importantLimits = {
  global: { count: await importantCount([join(projectRoot, 'src/global.scss')]), maximum: 207 },
  components: { count: await importantCount(await filesBelow(join(projectRoot, 'src/app'))), maximum: 14 },
  theme: { count: await importantCount(await filesBelow(join(projectRoot, 'src/theme'))), maximum: 4 },
};

for (const [area, result] of Object.entries(importantLimits)) {
  console.log(`!important ${area}: ${result.count}/${result.maximum}`);
}

if (duplicateBlocks > 0 || Object.values(importantLimits).some(result => result.count > result.maximum)) {
  console.error('Auditoría CSS fallida: se introdujeron duplicados o aumentó la deuda de especificidad.');
  process.exitCode = 1;
}
