import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdir, mkdtemp, writeFile, readFile, rm } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const repo = fileURLToPath(new URL('../../', import.meta.url));
const slash = value => value.replaceAll('\\', '/');
await mkdir(path.join(repo, '.runtime'), { recursive: true });
const root = await mkdtemp(path.join(repo, '.runtime/full-backup-test-'));
try {
  const bin = path.join(root, 'bin'); await mkdir(bin);
  const executable = async (name, contents) => writeFile(path.join(bin, name), `#!/usr/bin/env bash\nset -eu\n${contents}\n`, { mode: 0o755 });
  await executable('flock', 'exit 0');
  await executable('findmnt', 'if [[ "$*" == *external* ]]; then if [[ "$*" == *TARGET* ]]; then echo /mnt/external; elif [[ "$FAKE_MOUNT" == ok ]]; then echo /dev/backup; else echo /dev/root; fi; else echo /dev/root; fi');
  if (process.platform === 'win32') {
    if (!process.env.SIGAA_TEST_PYTHON) throw new Error('Configure SIGAA_TEST_PYTHON con Python real para probar en Git Bash.');
    await executable('python3', `exec "${slash(process.env.SIGAA_TEST_PYTHON)}" "$@"`);
  }
  await executable('docker', await readFile(new URL('./fixtures/fake-docker-backup.sh', import.meta.url), 'utf8'));
  for (const scenario of ['success', 'failure', 'interrupted', 'mount-missing', 'already-stopped']) {
    const work = path.join(root, scenario), external = path.join(work, 'external');
    await mkdir(external, { recursive: true });
    const log = path.join(work, 'calls.log'); await writeFile(log, '');
    const result = spawnSync(process.env.SIGAA_TEST_BASH || 'bash', [slash(path.join(repo, 'ops/scripts/full-backup.sh'))], {
      cwd: repo, encoding: 'utf8', env: { ...process.env,
        PATH: `${bin}${path.delimiter}${process.env.PATH || process.env.Path}`,
        SIGAA_BACKUP_LOCK_DIR: slash(work), SIGAA_ENV_FILE: slash(path.join(work, '.env.fake')),
        FAKE_EXTERNAL: slash(external), FAKE_LOG: slash(log),
        FAKE_RUNNING: scenario === 'already-stopped' ? 'db' : 'db\napi\nweb',
        FAKE_MOUNT: scenario === 'mount-missing' ? 'missing' : 'ok', FAKE_FAILURE: scenario === 'failure' ? 'yes' : scenario === 'interrupted' ? 'signal' : 'no',
      },
    });
    const calls = (await readFile(log, 'utf8')).trim().split(/\r?\n/).filter(Boolean);
    assert.equal(result.status, ['success', 'already-stopped'].includes(scenario) ? 0 : scenario === 'failure' ? 12 : scenario === 'interrupted' ? 143 : 1, `${scenario}: ${result.stderr}`);
    assert.deepEqual(calls, scenario === 'mount-missing' ? [] : scenario === 'already-stopped' ? ['create'] : ['stop', 'create', 'start']);
    console.log(`Copia completa: ${scenario} correcto.`);
  }
} finally { await rm(root, { recursive: true, force: true }); }
