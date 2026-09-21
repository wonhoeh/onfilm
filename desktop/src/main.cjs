const { app, dialog } = require('electron');
const { serverOrigin } = require('./policy.cjs');
const { createWindow } = require('./window.cjs');

app.setName('OnFilm');
let window;
let origin;

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
    origin = serverOrigin(process.env.ONFILM_SERVER_URL);
    window = createWindow({ origin });
    app.on('activate', () => {
      if (!window || window.isDestroyed()) window = createWindow({ origin });
    });
  }).catch((error) => {
    dialog.showErrorBox('OnFilm 시작 실패', error.message);
    app.quit();
  });

  app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') app.quit();
  });
}
