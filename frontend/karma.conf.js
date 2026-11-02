// Karma configuration file, see link for more information
// https://karma-runner.github.io/1.0/config/configuration-file.html

const path = require('path');
const fs = require('fs');
const runCache = path.join(__dirname, 'node_modules', '.cache', `karma-${process.pid}`);
const chromeProfile = path.join(runCache, 'chrome-profile');
const karmaTemp = path.join(runCache, 'temp');

// karma-chrome-launcher creates its profile from TEMP before applying launcher
// flags.  AppData\Local\Temp is locked in some Windows/CI sessions, so use a
// project-local cache that is already ignored by Git instead.
fs.mkdirSync(karmaTemp, { recursive: true });
process.env.TEMP = karmaTemp;
process.env.TMP = karmaTemp;

module.exports = function (config) {
  config.set({
    basePath: '',
    frameworks: ['jasmine', '@angular-devkit/build-angular'],
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-jasmine-html-reporter'),
      require('karma-coverage'),
      require('@angular-devkit/build-angular/plugins/karma')
    ],
    client: {
      jasmine: {
        // you can add configuration options for Jasmine here
        // the possible options are listed at https://jasmine.github.io/api/edge/Configuration.html
        // for example, you can disable the random execution with `random: false`
        // or set a specific seed with `seed: 4321`
      },
      clearContext: false // leave Jasmine Spec Runner output visible in browser
    },
    jasmineHtmlReporter: {
      suppressAll: true // removes the duplicated traces
    },
    coverageReporter: {
      dir: require('path').join(__dirname, './coverage/app'),
      subdir: '.',
      check: {
        global: {
          lines: 40,
          statements: 35,
          functions: 35,
          branches: 30,
        },
      },
      reporters: [
        { type: 'html' },
        { type: 'text-summary' }
      ]
    },
    reporters: ['progress', 'kjhtml', 'coverage'],
    port: 9876,
    colors: true,
    logLevel: config.LOG_INFO,
    autoWatch: true,
    // Chrome can fail in headless/CI environments when it tries to initialise
    // the GPU process.  Keep the browser profile isolated and disable that
    // optional path so the same command works locally and in CI.
    customLaunchers: {
      ChromeHeadlessCI: {
        base: 'ChromeHeadless',
        flags: [
          '--headless=new',
          '--disable-gpu',
          '--disable-gpu-compositing',
          '--disable-gpu-shader-disk-cache',
          '--disable-software-rasterizer',
          '--disable-features=Vulkan,UseSkiaRenderer,SkiaGraphite,Dawn',
          '--disable-dev-shm-usage',
          // The launcher only opens the local Karma test page. Disabling the
          // browser sandbox avoids Windows/runner profile-lock failures.
          '--no-sandbox',
          '--disable-setuid-sandbox',
          '--no-first-run',
          '--no-default-browser-check',
          '--remote-debugging-port=0',
          // karma-chrome-launcher otherwise creates a shared OS temporary
          // profile; that is the source of intermittent Windows cache locks.
          `--user-data-dir=${chromeProfile}`,
        ],
      },
    },
    browsers: ['ChromeHeadlessCI'],
    singleRun: true,
    restartOnFileChange: true
  });
};
