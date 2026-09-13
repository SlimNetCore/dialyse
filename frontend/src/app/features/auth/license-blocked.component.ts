import {ChangeDetectionStrategy, Component, inject} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {AuthStore} from '../../core/state/auth.store';
import {LicenseBlockService} from '../../core/state/license-block.service';

@Component({
  selector: 'app-license-blocked',
  standalone: true,
  imports: [MatButtonModule, MatIconModule],
  templateUrl: './license-blocked.component.html',
  styleUrl: './license-blocked.component.css',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class LicenseBlockedComponent {
  protected readonly licenseBlock = inject(LicenseBlockService);
  private readonly auth = inject(AuthStore);

  logout(): void {
    this.auth.clearSession();
    window.location.assign('/login');
  }
}
