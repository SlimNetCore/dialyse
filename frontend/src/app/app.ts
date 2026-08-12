import {ChangeDetectionStrategy, Component, computed, inject} from '@angular/core';
import {RouterOutlet} from '@angular/router';
import {ThemeStore} from './core/state/theme.store';
import {HemodialysisLoaderComponent} from './shared/hemodialysis-loader.component';
import {BackendInitService} from './core/startup/backend-init.service';

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
  readonly startupLoaderLabel = computed(() =>
    this.backendInit.state() === 'server-unavailable'
      ? 'COMMON.SERVER_UNAVAILABLE'
      : 'COMMON.SERVER_CONTACT_IN_PROGRESS');

  constructor() {
    inject(ThemeStore);
    this.backendInit.start();
  }
}
