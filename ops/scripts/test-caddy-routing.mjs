import assert from 'node:assert/strict';
import { execFileSync, spawn } from 'node:child_process';
import { createServer } from 'node:http';
import { mkdir, mkdtemp, readFile, writeFile, rm } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const repo = fileURLToPath(new URL('../../', import.meta.url));
const executable = process.env.CADDY_BINARY;
if (!executable) throw new Error('Configure CADDY_BINARY con el ejecutable Caddy 2.10 verificado.');
const webRoot = path.resolve(process.env.SIGAA_WEB_BUILD_DIR || path.join(repo, 'frontend/www'));
const html = await readFile(path.join(webRoot, 'index.html'), 'utf8');
assert.ok(!/onload=|fonts\.googleapis|fonts\.gstatic/.test(html), 'El HTML optimizado debe ser compatible con la CSP local');
await mkdir(path.join(repo, '.runtime'), { recursive: true });
const temporary = await mkdtemp(path.join(repo, '.runtime/caddy-test-'));
const mock = createServer((request, response) => {
  response.setHeader('Content-Type', 'application/json');
  response.statusCode = request.url.startsWith('/uploads/') ? 403 : request.url === '/api/private' ? 401 : 200;
  response.end(JSON.stringify({ path: request.url, method: request.method, ip: request.headers['x-forwarded-for'], proto: request.headers['x-forwarded-proto'], forwarded: request.headers.forwarded }));
});
await new Promise(resolve => mock.listen(0, '127.0.0.1', resolve));
const backendPort = mock.address().port;
try {
  for (const trusted of [false, true]) {
    const reservation = createServer();
    await new Promise(resolve => reservation.listen(0, '127.0.0.1', resolve));
    const port = reservation.address().port; await new Promise(resolve => reservation.close(resolve));
    const config = JSON.parse(execFileSync(executable, ['adapt', '--config', path.join(repo, 'frontend/Caddyfile'), '--adapter', 'caddyfile'], {
      env: { ...process.env, SIGAA_TRUSTED_PROXIES: trusted ? '127.0.0.1/32' : '192.0.2.1/32' }, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'],
    }));
    for (const server of Object.values(config.apps.http.servers)) server.listen = [`127.0.0.1:${port}`];
    const rewrite = value => {
      if (!value || typeof value !== 'object') return;
      if (value.root === '/srv') value.root = webRoot;
      if (value.dial === 'api:8080') value.dial = `127.0.0.1:${backendPort}`;
      for (const child of Object.values(value)) rewrite(child);
    };
    rewrite(config);
    const configFile = path.join(temporary, `caddy-${trusted}.json`); await writeFile(configFile, JSON.stringify(config));
    const processHandle = spawn(executable, ['run', '--config', configFile], { stdio: 'ignore' });
    try {
      const base = `http://127.0.0.1:${port}`;
      let ready = false;
      for (let attempt = 0; attempt < 100; attempt++) {
        try { if ((await fetch(`${base}/api/config`)).ok) { ready = true; break; } } catch { /* Arranque del proceso. */ }
        if (processHandle.exitCode !== null) throw new Error('Caddy terminó durante el arranque');
        await new Promise(resolve => setTimeout(resolve, 50));
      }
      assert.ok(ready, 'Caddy debe iniciar');
      const result = await fetch(`${base}/api/config`, { headers: { 'X-Forwarded-For': '198.51.100.20', 'X-Forwarded-Proto': 'https', Forwarded: 'for=198.51.100.99' } });
      assert.match(result.headers.get('content-type'), /application\/json/);
      const body = await result.json(); assert.equal(body.path, '/api/config');
      assert.equal(body.ip, trusted ? '198.51.100.20' : '127.0.0.1');
      assert.equal(body.proto, trusted ? 'https' : 'http'); assert.equal(body.forwarded, undefined);
      const login = await (await fetch(`${base}/api/auth/login`, { method: 'POST', body: '{}' })).json();
      assert.equal(login.path, '/api/auth/login'); assert.equal(login.method, 'POST');
      assert.equal((await fetch(`${base}/api/private`)).status, 401);
      const privateFile = await fetch(`${base}/uploads/documentos/private.png`);
      assert.equal(privateFile.status, 403); assert.ok(!/public/.test(privateFile.headers.get('cache-control') || ''));
      const angular = await fetch(`${base}/dashboard`); assert.equal(angular.status, 200);
      assert.match(angular.headers.get('content-type'), /text\/html/);
      assert.match(angular.headers.get('content-security-policy'), /script-src 'self'/);
      const stylesheet = html.match(/href="([^"]+\.css)"/)[1];
      assert.match((await fetch(`${base}/${stylesheet}`)).headers.get('content-type'), /text\/css/);
      console.log(`Caddy: API, login, archivos privados, SPA, CSS y confianza ${trusted ? 'autorizada' : 'no autorizada'} correctos.`);
    } finally {
      if (processHandle.exitCode === null) {
        processHandle.kill(); await new Promise(resolve => processHandle.once('exit', resolve));
      }
    }
  }
} finally {
  await new Promise(resolve => mock.close(resolve));
  await rm(temporary, { recursive: true, force: true });
}
