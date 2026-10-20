const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { BrowserWindow, ipcMain } = require('electron');
const { isAppUrl } = require('./policy.cjs');

const statusFile = path.join(__dirname, 'status.html');
const statusUrl = pathToFileURL(statusFile).href;

function createWindow({ origin, backend, show = true, timeoutMs = 15000, partition = 'persist:onfilm-desktop' }) {
  const win = new BrowserWindow({
    width: 1280, height: 900, minWidth: 900, minHeight: 640,
    title: 'OnFilm', backgroundColor: '#15161b', show,
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      nodeIntegration: false, contextIsolation: true, sandbox: true,
      webSecurity: true, webviewTag: false, partition,
    },
  });
  const contents = win.webContents;
  contents.session.setPermissionRequestHandler((_wc, _permission, callback) => callback(false));
  contents.session.setPermissionCheckHandler(() => false);
  // This prototype never launches OS handlers or navigates to external sites.
  contents.setWindowOpenHandler(({ url }) => {
    if (isAppUrl(url, origin)) void contents.loadURL(url).catch(() => {});
    return { action: 'deny' };
  });
  contents.on('will-attach-webview', (event) => event.preventDefault());
  for (const eventName of ['will-navigate', 'will-redirect']) {
    contents.on(eventName, (event, url) => {
      if (!isAppUrl(url, origin)) event.preventDefault();
    });
  }
  contents.on('will-frame-navigate', (event) => {
    if (!isAppUrl(event.url, origin)) event.preventDefault();
  });

  let timer;
  let attempting = false;
  let errorVisible = false;
  const clearTimer = () => { clearTimeout(timer); timer = undefined; };

  async function status(state, message = '') {
    if (win.isDestroyed()) return;
    try {
      await win.loadFile(statusFile, { query: { state, server: origin, mode: backend ? 'managed' : 'external', message } });
    } catch (error) {
      if (!win.isDestroyed() && error.code !== 'ERR_ABORTED' && error.errno !== -3) {
        console.error('Status page failed:', error.message);
      }
    }
  }

  async function fail(error) {
    if (win.isDestroyed() || errorVisible) return;
    errorVisible = true;
    attempting = false;
    clearTimer();
    contents.stop();
    await status('error', error?.message || '');
  }

  async function connect() {
    if (win.isDestroyed() || attempting) return;
    attempting = true;
    errorVisible = false;
    await status('loading');
    if (win.isDestroyed()) return;
    try {
      // The backend enforces its own startup timeout and shares concurrent starts.
      if (backend) await backend.start();
      if (win.isDestroyed()) return;
      timer = setTimeout(() => void fail(), timeoutMs);
      await win.loadURL(`${origin}/`);
      clearTimer();
      attempting = false;
    } catch (error) {
      await fail(backend ? error : undefined);
    }
  }

  contents.on('did-fail-load', (_event, code, _description, url, isMainFrame) => {
    if (isMainFrame && code !== -3 && isAppUrl(url, origin)) void fail();
  });
  contents.on('did-navigate', (_event, url, responseCode) => {
    if (isAppUrl(url, origin) && responseCode >= 400) void fail();
  });
  contents.on('render-process-gone', () => void fail());

  const retry = (event) => {
    if (win.isDestroyed() || event.sender !== contents
        || event.senderFrame !== contents.mainFrame) return;
    const frameUrl = event.senderFrame?.url;
    if (frameUrl && frameUrl.split('?')[0] === statusUrl) void connect();
  };
  ipcMain.on('onfilm:retry', retry);
  const backendFailed = (error) => { void fail(error); };
  backend?.on('failed', backendFailed);
  win.on('closed', () => {
    clearTimer();
    ipcMain.removeListener('onfilm:retry', retry);
    backend?.removeListener('failed', backendFailed);
  });
  void connect();
  return win;
}

module.exports = { createWindow };
