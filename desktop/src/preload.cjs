const { contextBridge, ipcRenderer } = require('electron');

// The only bridge is a no-argument retry; the main process also checks the sender.
if (window.location.protocol === 'file:') {
  contextBridge.exposeInMainWorld('onfilmDesktop', {
    retry: () => ipcRenderer.send('onfilm:retry'),
  });
}
