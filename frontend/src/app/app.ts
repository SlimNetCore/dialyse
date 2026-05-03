import {Component, computed, inject, OnDestroy, signal} from '@angular/core';
import {NavigationEnd, Router, RouterOutlet} from '@angular/router';
import {ThemeService} from './core/theme/theme.service';
import {AuthSessionService} from './core/auth/auth-session.service';
import {filter, Subscription} from 'rxjs';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  template: `
    @if (showAuthLoader()) {
      <div class="auth-loader-backdrop">
        <div class="auth-loader-spinner" aria-label="Authentification en cours"></div>
      </div>
    } @else {
      <router-outlet/>
    }
  `,
  styles: [
    `
      :host {
        display: block;
      }

      .auth-loader-backdrop {
        position: fixed;
        inset: 0;
        display: grid;
        place-items: center;
        background: var(--app-bg);
        z-index: 2000;
      }

      .auth-loader-spinner {
        width: 42px;
        height: 42px;
        border-radius: 50%;
        border: 4px solid color-mix(in srgb, var(--app-primary) 20%, transparent);
        border-top-color: var(--app-primary);
        animation: spin 0.8s linear infinite;
      }

      @keyframes spin {
        to {
          transform: rotate(360deg);
        }
      }
    `
  ]
})
export class App implements OnDestroy {
  private readonly auth = inject(AuthSessionService);
  private readonly router = inject(Router);
  private readonly currentUrl = signal(this.router.url);
  readonly showAuthLoader = computed(() => {
    const onLogin = this.currentUrl().startsWith('/login');
    return !onLogin && this.auth.isServerSyncing();
  });
  private readonly routerSub: Subscription;

  constructor() {
    inject(ThemeService);
    this.routerSub = this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe((e) => this.currentUrl.set(e.urlAfterRedirects));
  }

  ngOnDestroy(): void {
    this.routerSub.unsubscribe();
  }
}


