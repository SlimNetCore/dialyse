import {ChangeDetectionStrategy, Component, computed, effect, inject, signal} from '@angular/core';
import {NavigationEnd, Router, RouterOutlet} from '@angular/router';
import {ThemeStore} from './core/state/theme.store';
import {HemodialysisLoaderComponent} from './shared/hemodialysis-loader.component';
import {BackendInitService} from './core/startup/backend-init.service';
import {WebSocketService} from './core/ws/websocket.service';
import {AuthStore} from './core/state/auth.store';
import {filter} from 'rxjs/operators';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, HemodialysisLoaderComponent],
  templateUrl: './app.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './app.component.css',
})
export class App {
  private readonly backendInit = inject(BackendInitService);
  readonly showStartupLoader = computed(() => this.backendInit.state() !== 'ready-for-auth');
  readonly startupLoaderLabel = computed(() => {
    if (this.backendInit.state() === 'server-unavailable') {
      return 'COMMON.SERVER_UNAVAILABLE';
    }
    if (this.showConnectionLoader()) {
      return 'COMMON.SERVER_CONNECTION_INTERRUPTED';
    }
    return 'COMMON.SERVER_CONTACT_IN_PROGRESS';
  });
  private readonly websocket = inject(WebSocketService);
  private readonly auth = inject(AuthStore);
  readonly showConnectionLoader = computed(() =>
    this.backendInit.state() === 'ready-for-auth'
    && this.auth.isAuthenticated()
    && !this.isLoginRoute()
    && this.websocket.healthStatus() === 'down'
  );
  private readonly router = inject(Router);
  private readonly currentUrl = signal('/');
  readonly startupServerUnavailable = computed(() => this.backendInit.state() === 'server-unavailable');
  readonly isLoginRoute = computed(() => this.currentUrl().startsWith('/login'));

  constructor() {
    inject(ThemeStore);
    this.currentUrl.set(this.router.url);
    this.router.events
      .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
      .subscribe((event) => {
        this.currentUrl.set(event.urlAfterRedirects);
      });

    effect(() => {
      if (this.backendInit.state() !== 'ready-for-auth') {
        return;
      }
      if (this.isLoginRoute()) {
        return;
      }
      if (!this.auth.isAuthenticated()) {
        void this.router.navigateByUrl('/login');
      }
    });

    this.backendInit.start();
  }
}
