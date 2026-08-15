import {ChangeDetectionStrategy, Component, Input, OnChanges, OnDestroy, signal, SimpleChanges} from '@angular/core';
import {TranslateModule} from '@ngx-translate/core';

@Component({
  selector: 'app-hemodialysis-loader, hemo-circuit-loader',
  standalone: true,
  imports: [TranslateModule],
  templateUrl: './hemodialysis-loader.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './hemodialysis-loader.component.css',
})
export class HemodialysisLoaderComponent implements OnChanges, OnDestroy {
  @Input() label = 'COMMON.LOADING_DATA';
  @Input() mode: 'inline' | 'overlay' = 'inline';
  @Input() showServerUnavailableIcon = false;
  @Input() showReconnectingKidney = false;
  @Input() showElapsedSeconds = false;
  @Input() size = 120;
  @Input() speed = 3;

  readonly reconnectSeconds = signal(0);
  readonly reducedMotion = signal(false);

  readonly circuitGradientId = `hemoCircuitGradient-${Math.random().toString(36).slice(2, 10)}`;
  readonly circuitGlowId = `hemoCircuitGlow-${Math.random().toString(36).slice(2, 10)}`;
  readonly alertGradientId = `hemoAlertGradient-${Math.random().toString(36).slice(2, 10)}`;
  readonly tearGradientId = `hemoTearGradient-${Math.random().toString(36).slice(2, 10)}`;

  private readonly motionQuery = typeof window !== 'undefined' && typeof window.matchMedia === 'function'
    ? window.matchMedia('(prefers-reduced-motion: reduce)')
    : null;
  private motionQueryListener: ((event: MediaQueryListEvent) => void) | null = null;
  private reconnectTimerId: ReturnType<typeof setInterval> | null = null;

  constructor() {
    this.reducedMotion.set(this.motionQuery?.matches ?? false);

    if (this.motionQuery) {
      this.motionQueryListener = (event: MediaQueryListEvent) => {
        this.reducedMotion.set(event.matches);
      };
      this.motionQuery.addEventListener('change', this.motionQueryListener);
    }
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['showReconnectingKidney'] || changes['showElapsedSeconds']) {
      if (this.shouldRunTimer()) {
        this.startReconnectTimer();
      } else {
        this.stopReconnectTimer(true);
      }
    }
  }

  ngOnDestroy(): void {
    this.stopReconnectTimer(false);

    if (this.motionQuery && this.motionQueryListener) {
      this.motionQuery.removeEventListener('change', this.motionQueryListener);
    }
  }

  protected visualSize(): number {
    return this.showReconnectingKidney ? Math.max(this.size, 148) : this.size;
  }

  protected shouldShowCounter(): boolean {
    return this.showReconnectingKidney || this.showElapsedSeconds;
  }

  protected formattedElapsedTime(): string {
    const totalSeconds = Math.max(0, this.reconnectSeconds());

    if (totalSeconds < 60) {
      return `${totalSeconds}s`;
    }

    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;

    return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
  }

  protected dots(): Array<{ id: string; color: string; angle: number; kind: 'arterial' | 'venous'; radius: number }> {
    return [
      {id: 'a1', color: 'var(--hemo-loader-arteriel)', angle: 0, kind: 'arterial', radius: 5.6},
      {id: 'a2', color: 'var(--hemo-loader-arteriel)', angle: 120, kind: 'arterial', radius: 5.6},
      {id: 'v1', color: 'var(--hemo-loader-veineux)', angle: 240, kind: 'venous', radius: 5.4},
    ];
  }

  private startReconnectTimer(): void {
    if (this.reconnectTimerId) {
      return;
    }
    this.reconnectSeconds.set(0);
    this.reconnectTimerId = setInterval(() => {
      this.reconnectSeconds.update((seconds) => seconds + 1);
    }, 1000);
  }

  private stopReconnectTimer(reset: boolean): void {
    if (this.reconnectTimerId) {
      clearInterval(this.reconnectTimerId);
      this.reconnectTimerId = null;
    }
    if (reset) {
      this.reconnectSeconds.set(0);
    }
  }

  private shouldRunTimer(): boolean {
    return this.showReconnectingKidney || this.showElapsedSeconds;
  }
}

