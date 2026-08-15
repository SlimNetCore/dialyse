import {ChangeDetectionStrategy, Component, computed, effect, inject} from '@angular/core';
import {RouterLink} from '@angular/router';
import {MatCardModule} from '@angular/material/card';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSlideToggleModule} from '@angular/material/slide-toggle';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {TranslateModule} from '@ngx-translate/core';
import {FacturationStore} from '../facturation/state/facturation.store';
import {AppShellStore} from '../../core/state/app-shell.store';
import {AuthStore} from '../../core/state/auth.store';

@Component({
  selector: 'app-facturation-settings',
  standalone: true,
  imports: [
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSlideToggleModule,
    MatProgressBarModule,
    TranslateModule,
  ],
  templateUrl: './facturation-settings.component.html',
  styleUrl: './facturation-settings.component.css',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class FacturationSettingsComponent {
  protected readonly store = inject(FacturationStore);
  private readonly appShell = inject(AppShellStore);
  private readonly auth = inject(AuthStore);

  protected readonly currentCenterId = computed(() => this.appShell.currentCenterId());
  protected readonly currentUsername = computed(() => this.auth.username() ?? 'system');

  constructor() {
    effect(() => {
      const centerId = this.currentCenterId();
      if (!centerId) {
        return;
      }
      this.store.setActiveCenterId(centerId);
      this.store.loadSettings({centerId});
    });
  }


  protected onCodeFormatChange(value: string): void {
    this.store.patchSettingsDraft({codeFormat: value});
  }

  protected onRegroupementChange(checked: boolean): void {
    this.store.patchSettingsDraft({regroupementMultiForfait: checked});
  }

  protected onSaveSettings(): void {
    const centerId = this.currentCenterId();
    if (!centerId) {
      return;
    }
    this.store.saveSettings({
      centerId,
      userId: this.currentUsername(),
    });
  }
}

