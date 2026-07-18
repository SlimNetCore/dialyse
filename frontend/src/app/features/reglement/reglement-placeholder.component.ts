import {ChangeDetectionStrategy, Component} from '@angular/core';
import {MatIconModule} from '@angular/material/icon';
import {TranslateModule} from '@ngx-translate/core';

@Component({
  standalone: true,
  imports: [MatIconModule, TranslateModule],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './reglement-placeholder.component.html',
  styleUrl: './reglement-placeholder.component.css',
})
export class ReglementPlaceholderComponent {}
