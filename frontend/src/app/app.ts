import {ChangeDetectionStrategy, Component, computed, inject, OnDestroy, signal,} from '@angular/core';
import {NavigationEnd, Router, RouterOutlet} from '@angular/router';
import {AuthStore} from './core/state/auth.store';
import {ThemeStore} from './core/state/theme.store';
import {filter, Subscription} from 'rxjs';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  templateUrl: './app.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './app.component.css',
})
export class App implements OnDestroy {
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly currentUrl = signal(this.router.url);
  readonly showAuthLoader = computed(() => {
    const onLogin = this.currentUrl().startsWith('/login');
    return !onLogin && this.auth.isServerSyncing();
  });
  private readonly routerSub: Subscription;

  constructor() {
    inject(ThemeStore);
    this.routerSub = this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe((e) => this.currentUrl.set(e.urlAfterRedirects));
  }

  ngOnDestroy(): void {
    this.routerSub.unsubscribe();
  }
}
