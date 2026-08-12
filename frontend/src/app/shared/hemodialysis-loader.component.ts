import {CommonModule} from '@angular/common';
import {ChangeDetectionStrategy, Component, Input} from '@angular/core';
import {MatIconModule} from '@angular/material/icon';
import {TranslateModule} from '@ngx-translate/core';

@Component({
  selector: 'app-hemodialysis-loader',
  standalone: true,
  imports: [CommonModule, MatIconModule, TranslateModule],
  templateUrl: './hemodialysis-loader.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './hemodialysis-loader.component.css',
})
export class HemodialysisLoaderComponent {
  @Input() label = 'COMMON.LOADING_DATA';
  @Input() mode: 'inline' | 'overlay' = 'inline';
  @Input() showServerUnavailableIcon = false;
}

