import {CommonModule} from '@angular/common';
import {ChangeDetectionStrategy, Component, EventEmitter, Input, Output} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {TranslateModule} from '@ngx-translate/core';

@Component({
  selector: 'app-select-filter',
  standalone: true,
  imports: [
    CommonModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatButtonModule,
    TranslateModule,
  ],
  templateUrl: './select-filter.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './select-filter.component.css',
})
export class SelectFilterComponent {
  @Input() placeholder = 'COMMON.SEARCH';
  @Output() valueChange = new EventEmitter<string>();

  protected onClear(input: HTMLInputElement): void {
    input.value = '';
    this.valueChange.emit('');
    input.focus();
  }
}
