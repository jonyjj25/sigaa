import { readdir, readFile, writeFile } from 'node:fs/promises';
import { extname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const projectRoot = fileURLToPath(new URL('../', import.meta.url));
const sourceRoot = join(projectRoot, 'src');
const excluded = new Set([join(sourceRoot, 'theme', 'variables.scss')]);
const extensions = new Set(['.css', '.html', '.scss', '.ts']);

const replacements = new Map([
  ['#0284c7', 'var(--institutional-blue)'],
  ['#0369a1', 'var(--institutional-blue)'],
  ['#0ea5e9', 'var(--institutional-blue)'],
  ['#1d4ed8', 'var(--institutional-blue)'],
  ['#1e40af', 'var(--institutional-blue)'],
  ['#2563eb', 'var(--institutional-blue)'],
  ['#3b82f6', 'var(--institutional-blue)'],
  ['#38bdf8', 'var(--institutional-blue)'],
  ['#60a5fa', 'var(--institutional-blue)'],
  ['#7dd3fc', 'var(--institutional-blue)'],
  ['#93c5fd', 'var(--institutional-blue)'],
  ['#bae6fd', 'var(--brand-soft)'],
  ['#bfdbfe', 'var(--brand-soft)'],
  ['#dbeafe', 'var(--brand-soft)'],
  ['#e0f2fe', 'var(--brand-soft)'],
  ['#e4eeff', 'var(--brand-hover)'],
  ['#e8f0ff', 'var(--brand-soft)'],
  ['#edf3f9', 'var(--brand-soft)'],
  ['#edf4ff', 'var(--brand-soft)'],
  ['#eef4ff', 'var(--brand-soft)'],
  ['#eff6ff', 'var(--brand-soft)'],
  ['#f1f6ff', 'var(--brand-soft)'],
  ['#cfe0f7', 'var(--brand-border)'],
  ['#1d4ed81f', 'color-mix(in srgb, var(--institutional-blue) 12%, transparent)'],
  ['#1d4ed82e', 'color-mix(in srgb, var(--institutional-blue) 18%, transparent)'],
  ['#1d4ed833', 'color-mix(in srgb, var(--institutional-blue) 20%, transparent)'],
  ['#2563eb18', 'color-mix(in srgb, var(--institutional-blue) 9%, transparent)'],
  ['#2563eb1a', 'color-mix(in srgb, var(--institutional-blue) 10%, transparent)'],
]);

async function filesBelow(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const nested = await Promise.all(entries.map(entry => {
    const path = join(directory, entry.name);
    return entry.isDirectory() ? filesBelow(path) : [path];
  }));
  return nested.flat().filter(path => extensions.has(extname(path)) && !excluded.has(path));
}

let changedFiles = 0;
let changedTokens = 0;

function migrateStyles(source) {
  let migrated = source;
  for (const [literal, token] of replacements) {
    const pattern = new RegExp(`${literal}(?![\\da-f])`, 'gi');
    migrated = migrated.replace(pattern, () => {
      changedTokens += 1;
      return token;
    });
  }
  return migrated;
}

for (const path of await filesBelow(sourceRoot)) {
  const before = await readFile(path, 'utf8');
  const after = extname(path) === '.ts'
    ? before.replace(/(styles\s*:\s*\[\s*`)([\s\S]*?)(`\s*\])/g, (_, start, styles, end) => `${start}${migrateStyles(styles)}${end}`)
    : migrateStyles(before);
  if (after !== before) {
    await writeFile(path, after, 'utf8');
    changedFiles += 1;
  }
}

console.log(`Migración visual completada: ${changedTokens} colores directos reemplazados en ${changedFiles} archivos.`);
