import { Component } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  standalone: true,
  imports: [MatIconModule, TranslateModule],
  template: `
    <div style="text-align:center; padding:80px 0; color:#666;">
      <mat-icon style="font-size:64px; width:64px; height:64px; color:#1b5e20;">event_note</mat-icon>
      <h2>{{ 'NAV.SEANCES' | translate }}</h2>
      <p>Module en cours de développement</p>
    </div>
  `
})
export class SeancesPlaceholderComponent {}

