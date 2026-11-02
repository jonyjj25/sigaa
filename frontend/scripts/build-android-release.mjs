import { spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url));
for (const name of ['SIGAA_ANDROID_API_URL', 'SIGAA_ANDROID_KEYSTORE', 'SIGAA_ANDROID_STORE_PASSWORD', 'SIGAA_ANDROID_KEY_ALIAS', 'SIGAA_ANDROID_KEY_PASSWORD']) {
  if (!process.env[name]) throw new Error(`Falta ${name}. No introduzca secretos en argumentos ni en Git.`);
}
if (!existsSync(process.env.SIGAA_ANDROID_KEYSTORE)) throw new Error('No existe el keystore institucional');
if (process.env.SIGAA_ANDROID_ALLOW_HTTP === 'true') throw new Error('Retire SIGAA_ANDROID_ALLOW_HTTP antes de crear una release');
const env = { ...process.env, SIGAA_ANDROID_RELEASE: 'true' };
const windows = process.platform === 'win32';
function run(command, args, cwd) {
  const result = spawnSync(command, args, { cwd, env, stdio: 'inherit', shell: windows });
  if (result.error) throw result.error;
  if (result.status !== 0) process.exit(result.status ?? 1);
}
run(windows ? 'npm.cmd' : 'npm', ['run', 'sync:android'], root);
env.SIGAA_ANDROID_API_URL = new URL(env.SIGAA_ANDROID_API_URL).origin;
run(windows ? 'gradlew.bat' : './gradlew', ['assembleRelease', '--no-daemon'], `${root}/android`);
console.log('APK release firmada: android/app/build/outputs/apk/release/app-release.apk');
