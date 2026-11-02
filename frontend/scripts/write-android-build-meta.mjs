import { writeFile } from 'node:fs/promises';
const apiUrl = new URL(process.env.SIGAA_ANDROID_API_URL).origin;
await writeFile(new URL('../www/sigaa-build.json', import.meta.url), JSON.stringify({
  apiUrl, release: process.env.SIGAA_ANDROID_RELEASE === 'true', builtAt: new Date().toISOString(),
}, null, 2));
