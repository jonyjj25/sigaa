import { mkdir, rm, writeFile } from 'node:fs/promises';

const rawUrl = process.env.SIGAA_ANDROID_API_URL?.trim();
const allowHttp = process.env.SIGAA_ANDROID_ALLOW_HTTP === 'true';
const release = process.env.SIGAA_ANDROID_RELEASE === 'true';

if (!rawUrl) {
  throw new Error('Defina SIGAA_ANDROID_API_URL con la URL de la API antes de compilar Android.');
}

let apiUrl;
try {
  apiUrl = new URL(rawUrl);
} catch {
  throw new Error('SIGAA_ANDROID_API_URL no es una URL válida.');
}

if (!['https:', 'http:'].includes(apiUrl.protocol) || (apiUrl.protocol === 'http:' && !allowHttp)) {
  throw new Error('La APK de distribución exige HTTPS. Para una prueba local explícita use SIGAA_ANDROID_ALLOW_HTTP=true.');
}
if (apiUrl.username || apiUrl.password || apiUrl.search || apiUrl.hash || !['', '/'].includes(apiUrl.pathname)) {
  throw new Error('Indique solo el origen de la API, sin credenciales, /api, consultas ni fragmentos.');
}
if (release && (allowHttp || apiUrl.protocol !== 'https:' || /^(localhost|127\.|\[::1\])|\.(invalid|test|example)$|(^|\.)example\.(com|org|net)$/.test(apiUrl.hostname))) {
  throw new Error('Una release requiere el dominio HTTPS institucional real y ninguna excepción HTTP.');
}

const normalized = apiUrl.toString().replace(/\/$/, '');
const target = new URL('../src/environments/environment.android.generated.ts', import.meta.url);
const contents = `// Generado por scripts/configure-android-env.mjs. No editar manualmente.\nexport const environment = {\n  production: true,\n  apiUrl: ${JSON.stringify(normalized)},\n};\n`;
const debugResources = new URL('../android/app/src/debug/res/xml/', import.meta.url);
const debugNetworkConfig = new URL('network_security_config.xml', debugResources);
if (apiUrl.protocol === 'http:') {
  const host = apiUrl.hostname.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
  await mkdir(debugResources, { recursive: true });
  await writeFile(debugNetworkConfig, `<?xml version="1.0" encoding="utf-8"?>\n<network-security-config>\n    <base-config cleartextTrafficPermitted="false" />\n    <domain-config cleartextTrafficPermitted="true">\n        <domain>${host}</domain>\n    </domain-config>\n</network-security-config>\n`, 'utf8');
} else {
  await rm(debugNetworkConfig, { force: true });
}
await writeFile(target, contents, 'utf8');
console.log(`Android configurado para ${normalized}`);
