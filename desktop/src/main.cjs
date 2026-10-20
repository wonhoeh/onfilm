const { app, dialog } = require('electron');
const { serverOrigin } = require('./policy.cjs');
const { createWindow } = require('./window.cjs');
const path = require('node:path');
const fs = require('node:fs');
const { apiConfig } = require('./api-config.cjs');
const { ApiProcess } = require('./api-process.cjs');

app.setName('OnFilm');
let window;
let origin;
let backend;
let quitting = false;
const managed = process.argv.includes('--managed-api');
const openWindow = () => createWindow({ origin, backend });

if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (!window || window.isDestroyed()) return;
    if (window.isMinimized()) window.restore();
    window.show();
    window.focus();
  });

  app.whenReady().then(() => {
    if (managed) {
      const envFile = path.join(__dirname, '..', '.env');
      if (fs.existsSync(envFile)) process.loadEnvFile(envFile);
      const config = apiConfig(process.env, path.resolve(__dirname, '../..'));
      backend = new ApiProcess(config);
      origin = config.origin;
    } else {
      origin = serverOrigin(process.env.ONFILM_SERVER_URL);
    }
    window = openWindow();
    app.on('activate', () => {
      if (!window || window.isDestroyed()) window = openWindow();
    });
  }).catch((error) => {
    dialog.showErrorBox('OnFilm 시작 실패', error.message);
    app.quit();
  });

  app.on('window-all-closed', () => {
    if (managed || process.platform !== 'darwin') app.quit();
  });

  app.on('before-quit', (event) => {
    if (!backend || quitting) return;
    event.preventDefault();
    quitting = true;
    void backend.stop().finally(() => app.quit());
  });
  for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => app.quit());
}
