import { readdir, readFile, writeFile } from 'node:fs/promises';
import { extname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const projectRoot = fileURLToPath(new URL('../', import.meta.url));
const appRoot = join(projectRoot, 'src', 'app');

async function filesBelow(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const nested = await Promise.all(entries.map(entry => {
    const path = join(directory, entry.name);
    return entry.isDirectory() ? filesBelow(path) : [path];
  }));
  return nested.flat().filter(path => ['.html', '.ts'].includes(extname(path)) && !path.endsWith('.spec.ts'));
}

function visibleText(contents) {
  return contents
    .replace(/<ion-icon\b[^>]*(?:\/>|>[\s\S]*?<\/ion-icon>)/gi, '')
    .replace(/<[^>]+>/g, '')
    .replace(/{{[\s\S]*?}}/g, 'contenido dinámico')
    .replace(/&(?:nbsp|#160);/gi, ' ')
    .trim();
}

let changedFiles = 0;
let changedButtons = 0;
for (const path of await filesBelow(appRoot)) {
  const before = await readFile(path, 'utf8');
  const after = before.replace(/<(button|ion-button)\b([^>]*)>([\s\S]*?)<\/\1>/gi, (full, tag, attributes, contents) => {
    if (!/<ion-icon\b/i.test(contents) || visibleText(contents)) return full;
    const existingTitle = attributes.match(/(?:^|\s)title\s*=\s*(["'])([\s\S]*?)\1/i);
    if (existingTitle && !/(?:aria-label|aria-labelledby|\[attr\.aria-label\])\s*=/.test(attributes)) {
      changedButtons += 1;
      return `<${tag}${attributes} aria-label=${existingTitle[1]}${existingTitle[2]}${existingTitle[1]}>${contents}</${tag}>`;
    }
    if (/(?:^|\s)(?:title|\[title\]|\[attr\.title\])\s*=/.test(attributes)) return full;

    const dynamic = attributes.match(/\[attr\.aria-label\]\s*=\s*(["'])([\s\S]*?)\1/i);
    const literal = attributes.match(/(?:^|\s)aria-label\s*=\s*(["'])([\s\S]*?)\1/i);
    let titleAttribute;
    if (dynamic) titleAttribute = `[title]=${dynamic[1]}${dynamic[2]}${dynamic[1]}`;
    else if (literal) titleAttribute = `title=${literal[1]}${literal[2]}${literal[1]}`;
    else return full;

    changedButtons += 1;
    return `<${tag}${attributes} ${titleAttribute}>${contents}</${tag}>`;
  });

  if (after !== before) {
    await writeFile(path, after, 'utf8');
    changedFiles += 1;
  }
}

console.log(`Accesibilidad actualizada: ${changedButtons} títulos añadidos en ${changedFiles} archivos.`);
