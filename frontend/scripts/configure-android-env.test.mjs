import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { copyFile, mkdir, mkdtemp, readFile, rm } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const frontend = fileURLToPath(new URL('../', import.meta.url));

async function fixture(run) {
  const cache = path.join(frontend, 'node_modules/.cache');
  await mkdir(cache, { recursive: true });
  const root = await mkdtemp(path.join(cache, 'android-config-test-'));
  await mkdir(path.join(root, 'scripts'));
  await mkdir(path.join(root, 'src/environments'), { recursive: true });
  const script = path.join(root, 'scripts/configure-android-env.mjs');
  await copyFile(new URL('./configure-android-env.mjs', import.meta.url), script);
  const configure = (url, allowHttp = false, release = false) => execFileSync(process.execPath, [script], {
    env: { ...process.env, SIGAA_ANDROID_API_URL: url, SIGAA_ANDROID_ALLOW_HTTP: String(allowHttp), SIGAA_ANDROID_RELEASE: String(release) },
    stdio: 'pipe',
  });

  try { await run(root, configure); }
  finally { await rm(root, { recursive: true, force: true }); }
}

test('release rejects local/test endpoints, HTTP exceptions and API paths', () => fixture(async (_root, configure) => {
  for (const url of ['https://localhost', 'https://api.invalid', 'https://api.example.test', 'https://sigaa.institucion.pa/api', 'https://user:secret@sigaa.institucion.pa']) {
    assert.throws(() => configure(url, false, true));
  }
  assert.throws(() => configure('http://192.168.0.52:8080', true, true));
  configure('https://sigaa.institucion.pa', false, true);
}));

test('requires an API URL and rejects HTTP without explicit local opt-in', () => fixture(async (_root, configure) => {
  assert.throws(() => configure(''), /SIGAA_ANDROID_API_URL/);
  assert.throws(() => configure('http://192.168.0.52:8080'), /HTTPS/);
  assert.throws(() => configure('ftp://example.test', true), /HTTPS/);
}));

test('local debug configuration permits HTTP only for the selected API host', () => fixture(async (root, configure) => {
  configure('http://192.168.0.52:8080/', true);
  const environment = await readFile(path.join(root, 'src/environments/environment.android.generated.ts'), 'utf8');
  assert.ok(environment.includes('apiUrl: "http://192.168.0.52:8080"'));
  const network = await readFile(path.join(root, 'android/app/src/debug/res/xml/network_security_config.xml'), 'utf8');
  assert.ok(network.includes('<domain>192.168.0.52</domain>'));
  assert.ok(network.includes('<base-config cleartextTrafficPermitted="false"'));
  assert.ok(!network.includes('192.168.41.26'));
}));

test('switching to the HTTPS server removes the previous debug HTTP exception', () => fixture(async (root, configure) => {
  configure('http://192.168.0.52:8080', true);
  configure('https://api.example.test/');
  const environment = await readFile(path.join(root, 'src/environments/environment.android.generated.ts'), 'utf8');
  assert.ok(environment.includes('apiUrl: "https://api.example.test"'));
  await assert.rejects(readFile(path.join(root, 'android/app/src/debug/res/xml/network_security_config.xml')), { code: 'ENOENT' });
}));
