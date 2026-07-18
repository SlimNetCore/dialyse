import {ChangeDetectionStrategy, Component} from '@angular/core';
import {MatIconModule} from '@angular/material/icon';
import {TranslateModule} from '@ngx-translate/core';

@Component({
  standalone: true,
  imports: [MatIconModule, TranslateModule],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './facturation-placeholder.component.html',
  styleUrl: './facturation-placeholder.component.css',
})
export class FacturationPlaceholderComponent {}
