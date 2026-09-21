// Runs the real Electron runtime against an isolated HTTP fixture, not user data.
const { app } = require('electron');
const http = require('node:http');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const assert = require('node:assert/strict');
const { setTimeout: delay } = require('node:timers/promises');
const { createWindow } = require('../src/window.cjs');

const dataDir = fs.mkdtempSync(path.join(os.tmpdir(), 'onfilm-electron-smoke-'));
app.setPath('userData', dataDir);
let win;
let second;
const server = http.createServer((req, res) => {
  if (req.url === '/hang') return;
  if (req.url === '/error') { res.writeHead(503); res.end('unavailable'); return; }
  res.setHeader('Content-Type', 'text/html; charset=utf-8');
  res.end('<!doctype html><title>OnFilm fixture</title><h1 id="fixture">Connected</h1>');
});

async function waitFor(predicate, label) {
  const deadline = Date.now() + 10000;
  while (Date.now() < deadline) {
    try { if (await predicate()) return; } catch { /* navigation may replace the context */ }
    await delay(50);
  }
  throw new Error(`Timed out: ${label}`);
}
const hasError = () => win.webContents.getURL().includes('state=error');

app.whenReady().then(async () => {
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const port = server.address().port;
  await new Promise((resolve) => server.close(resolve));
  const origin = `http://127.0.0.1:${port}`;
  win = createWindow({ origin, show: false, timeoutMs: 2000, partition: 'smoke' });
  await waitFor(hasError, 'offline error page');
  await waitFor(() => win.webContents.executeJavaScript(
    "document.getElementById('help')?.hidden === false"), 'error UI');
  console.log('PASS: unavailable server shows local error UI');

  await new Promise((resolve) => server.listen(port, '127.0.0.1', resolve));
  await win.webContents.executeJavaScript("document.getElementById('retry').click()");
  await waitFor(() => win.webContents.executeJavaScript(
    "document.getElementById('fixture')?.textContent === 'Connected'"), 'retry connects');
  assert.equal(win.webContents.getURL(), `${origin}/`);
  const globals = await win.webContents.executeJavaScript(
    "({ require: typeof require, process: typeof process, bridge: typeof onfilmDesktop })");
  assert.deepEqual(globals, { require: 'undefined', process: 'undefined', bridge: 'undefined' });
  const prefs = win.webContents.getLastWebPreferences();
  assert.equal(prefs.sandbox, true);
  assert.equal(prefs.contextIsolation, true);
  assert.equal(prefs.nodeIntegration, false);
  console.log('PASS: retry connects, remote renderer has no Node or retry bridge');

  await win.webContents.executeJavaScript("location.href = 'https://example.com/'");
  await delay(250);
  assert.equal(win.webContents.getURL(), `${origin}/`);
  await win.webContents.executeJavaScript("window.open('https://example.com/')");
  await delay(100);
  const { BrowserWindow } = require('electron');
  assert.equal(BrowserWindow.getAllWindows().length, 1);
  console.log('PASS: external navigation and popups blocked');

  await win.webContents.executeJavaScript("location.href = '/error'");
  await waitFor(hasError, 'HTTP 503 error page');
  await waitFor(() => win.webContents.executeJavaScript(
    "document.getElementById('help')?.hidden === false"), 'HTTP error UI ready');
  console.log('PASS: HTTP 503 shows retry UI');
  await win.webContents.executeJavaScript("document.getElementById('retry').click()");
  await waitFor(() => win.webContents.executeJavaScript(
    "document.getElementById('fixture')?.textContent === 'Connected'"), 'retry after HTTP error');
  console.log('PASS: HTTP error retry recovers');

  // A hanging root request is timed out during initial connection.
  const hanging = http.createServer(() => {});
  await new Promise((resolve) => hanging.listen(0, '127.0.0.1', resolve));
  try {
    second = createWindow({ origin: `http://127.0.0.1:${hanging.address().port}`,
      show: false, timeoutMs: 300, partition: 'smoke-timeout' });
    await waitFor(() => second.webContents.getURL().includes('state=error'), 'timeout error');
    await waitFor(() => second.webContents.executeJavaScript(
      "document.getElementById('help')?.hidden === false"), 'timeout UI ready');
    console.log('PASS: startup timeout shows retry UI');
  } finally {
    second?.destroy();
    hanging.closeAllConnections();
    await new Promise((resolve) => hanging.close(resolve));
  }
}).then(() => finish(0), (error) => {
  console.error(error);
  finish(1);
});

function finish(code) {
  win?.destroy();
  server.closeAllConnections();
  server.close();
  console.log(`Smoke test profile (temporary): ${dataDir}`);
  app.exit(code);
}
