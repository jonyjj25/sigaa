import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.sigaa.app',
  appName: 'SIGAA',
  webDir: 'www',
  android: {
    allowMixedContent: process.env['SIGAA_ANDROID_ALLOW_HTTP'] === 'true'
      && process.env['SIGAA_ANDROID_API_URL']?.startsWith('http:') === true,
  },
};

export default config;
