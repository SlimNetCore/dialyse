import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';

// Compatibility shim for libraries expecting Node's `global` in browser
(globalThis as any).global = globalThis;

bootstrapApplication(App, appConfig)
  .catch((err) => console.error(err));
