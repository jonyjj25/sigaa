import { readdir, readFile } from 'node:fs/promises';
import { extname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url));
const appDirectory = fileURLToPath(new URL('../src/app/', import.meta.url));
const themeFile = new URL('../src/theme/variables.scss', import.meta.url);
const failures = [];

async function filesBelow(directory, extension) {
  const entries = await readdir(directory, { withFileTypes: true });
  const nested = await Promise.all(entries.map(entry => {
    const path = join(directory, entry.name);
    return entry.isDirectory() ? filesBelow(path, extension) : [path];
  }));
  return nested.flat().filter(path => extname(path) === extension);
}

function lineAt(source, offset) {
  return source.slice(0, offset).split(/\r?\n/).length;
}

function hasAccessibleName(attributes) {
  return /(?:^|\s)(?:aria-label|aria-labelledby|\[attr\.aria-label\])\s*=/.test(attributes);
}

function hasDescriptiveTitle(attributes) {
  return /(?:^|\s)(?:title|\[title\]|\[attr\.title\])\s*=/.test(attributes);
}

const templateFiles = [
  ...await filesBelow(appDirectory, '.html'),
  ...(await filesBelow(appDirectory, '.ts')).filter(path => !path.endsWith('.spec.ts')),
];

for (const path of templateFiles) {
  const html = await readFile(path, 'utf8');
  const displayPath = relative(root, path);

  for (const match of html.matchAll(/<(button|ion-button)\b([^>]*)>([\s\S]*?)<\/\1>/gi)) {
    const [, , attributes, contents] = match;
    const containsIcon = /<ion-icon\b/i.test(contents);
    const visibleText = contents
      .replace(/<ion-icon\b[^>]*(?:\/>|>[\s\S]*?<\/ion-icon>)/gi, '')
      .replace(/<[^>]+>/g, '')
      .replace(/{{[\s\S]*?}}/g, 'contenido dinámico')
      .replace(/&(?:nbsp|#160);/gi, ' ')
      .trim();

    if (containsIcon && !visibleText && !hasAccessibleName(attributes)) {
      failures.push(`${displayPath}:${lineAt(html, match.index)} botón de icono sin nombre accesible`);
    }

    if (containsIcon && !visibleText && !hasDescriptiveTitle(attributes)) {
      failures.push(`${displayPath}:${lineAt(html, match.index)} botón de icono sin título descriptivo`);
    }

    if (/\brole\s*=\s*["']tab["']/i.test(attributes) && !/(?:aria-selected|\[attr\.aria-selected\])\s*=/.test(attributes)) {
      failures.push(`${displayPath}:${lineAt(html, match.index)} pestaña sin aria-selected`);
    }
  }

  for (const tablist of html.matchAll(/<(div|nav)\b[^>]*role\s*=\s*["']tablist["'][^>]*>([\s\S]*?)<\/\1>/gi)) {
    for (const button of tablist[2].matchAll(/<button\b([^>]*)>/gi)) {
      if (!/\brole\s*=\s*["']tab["']/i.test(button[1])) {
        failures.push(`${displayPath}:${lineAt(html, tablist.index + button.index)} control dentro de tablist sin role="tab"`);
      }
    }
  }

  for (const match of html.matchAll(/\btabindex\s*=\s*["']([1-9]\d*)["']/gi)) {
    failures.push(`${displayPath}:${lineAt(html, match.index)} tabindex positivo (${match[1]}) altera el orden de teclado`);
  }
}

function blockFor(source, selector) {
  const start = source.indexOf(selector);
  const opening = source.indexOf('{', start);
  let depth = 0;
  for (let index = opening; index < source.length; index += 1) {
    if (source[index] === '{') depth += 1;
    if (source[index] === '}') depth -= 1;
    if (depth === 0) return source.slice(opening + 1, index);
  }
  throw new Error(`No se pudo leer el bloque ${selector}`);
}

function declarationsFrom(block) {
  return Object.fromEntries([...block.matchAll(/--([\w-]+)\s*:\s*([^;]+)\s*;/gi)].map(match => [match[1], match[2].trim()]));
}

function resolvedColors(declarations) {
  const resolved = {};
  const resolve = (name, visited = new Set()) => {
    if (resolved[name]) return resolved[name];
    if (visited.has(name)) return undefined;
    visited.add(name);
    const value = declarations[name];
    if (/^#[\da-f]{6}$/i.test(value || '')) return (resolved[name] = value);
    const reference = value?.match(/^var\(--([\w-]+)\)$/);
    if (reference) return (resolved[name] = resolve(reference[1], visited));
    return undefined;
  };
  for (const name of Object.keys(declarations)) resolve(name);
  return resolved;
}

function luminance(hex) {
  const channels = hex.slice(1).match(/.{2}/g).map(value => parseInt(value, 16) / 255);
  const linear = channels.map(value => value <= .04045 ? value / 12.92 : ((value + .055) / 1.055) ** 2.4);
  return .2126 * linear[0] + .7152 * linear[1] + .0722 * linear[2];
}

function contrast(foreground, background) {
  const values = [luminance(foreground), luminance(background)].sort((a, b) => b - a);
  return (values[0] + .05) / (values[1] + .05);
}

const rawThemeSource = await readFile(themeFile, 'utf8');
// Resolver los literales Sass que usa el tema: la interpolación no es un token ausente.
const sassColors = Object.fromEntries([...rawThemeSource.matchAll(/\$([\w-]+)\s*:\s*(#[\da-f]{6})\s*;/gi)]
  .map(match => [match[1], match[2]]));
const themeSource = rawThemeSource.replace(/#\{\$([\w-]+)\}/g, (literal, name) => sassColors[name] ?? literal);
const lightDeclarations = declarationsFrom(blockFor(themeSource, ':root'));
const themes = {
  claro: resolvedColors(lightDeclarations),
  oscuro: resolvedColors({ ...lightDeclarations, ...declarationsFrom(blockFor(themeSource, '[data-theme="dark"]')) }),
};
const contrastPairs = [
  ['text-strong', 'surface-panel'],
  ['text-default', 'surface-panel'],
  ['text-soft', 'surface-panel'],
  ['text-muted-accessible', 'surface-panel'],
  ['text-link', 'surface-panel'],
  ['control-text', 'control-bg'],
  ['control-placeholder', 'control-bg'],
  ['table-header-text', 'table-header-bg'],
  ['state-success-text', 'state-success-bg'],
  ['state-warning-text', 'state-warning-bg'],
  ['state-danger-text', 'state-danger-bg'],
  ['state-info-text', 'state-info-bg'],
];

for (const [themeName, tokens] of Object.entries(themes)) {
  for (const [foreground, background] of contrastPairs) {
    if (!tokens[foreground] || !tokens[background]) {
      failures.push(`tema ${themeName}: falta --${!tokens[foreground] ? foreground : background}`);
      continue;
    }
    const ratio = contrast(tokens[foreground], tokens[background]);
    if (ratio < 4.5) failures.push(`tema ${themeName}: --${foreground} sobre --${background} = ${ratio.toFixed(2)}:1 (mínimo AA 4.5:1)`);
  }
}

const deprecatedBlueLiterals = /#(?:0284c7|0369a1|0ea5e9|1d4ed8|1e40af|3b82f6|38bdf8|60a5fa|7dd3fc|93c5fd|bae6fd|bfdbfe|dbeafe|e0f2fe|e4eeff|e8f0ff|edf3f9|edf4ff|eef4ff|eff6ff|f1f6ff|cfe0f7)(?![\da-f])/gi;
const styleFiles = [
  ...await filesBelow(fileURLToPath(new URL('../src/', import.meta.url)), '.scss'),
  ...await filesBelow(fileURLToPath(new URL('../src/', import.meta.url)), '.css'),
].filter(path => path !== fileURLToPath(themeFile));

for (const path of styleFiles) {
  const source = await readFile(path, 'utf8');
  for (const match of source.matchAll(deprecatedBlueLiterals)) {
    failures.push(`${relative(root, path)}:${lineAt(source, match.index)} usa ${match[0]} en vez de un token institucional`);
  }
}

if (failures.length) {
  console.error(`Auditoría UX fallida (${failures.length}):\n${failures.map(item => `- ${item}`).join('\n')}`);
  process.exitCode = 1;
} else {
  console.log('Auditoría UX superada: nombres accesibles, navegación por teclado y contraste de tokens AA.');
}
