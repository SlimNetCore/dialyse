import {CommonModule} from '@angular/common';
import {Component, EventEmitter, Input, Output} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {TranslateModule} from '@ngx-translate/core';

@Component({
  selector: 'app-select-filter',
  standalone: true,
  imports: [CommonModule, MatFormFieldModule, MatInputModule, MatIconModule, MatButtonModule, TranslateModule],
  template: `
    <mat-form-field class="select-filter-input" appearance="outline">
      <mat-icon matPrefix>search</mat-icon>
      <input
        #searchInput
        matInput
        [placeholder]="placeholder | translate"
        autocomplete="off"
        (input)="valueChange.emit(searchInput.value)"
      />
      @if (searchInput.value) {
        <button mat-icon-button matSuffix type="button" [attr.aria-label]="'COMMON.CLEAR_FILTER' | translate"
                (click)="onClear(searchInput)">
          <mat-icon>close</mat-icon>
        </button>
      }
    </mat-form-field>
  `,
  styles: [`
    :host {
      display: block;
      position: sticky;
      top: 0;
      z-index: 2;
      background: var(--app-surface-solid);
      padding: 8px 8px 4px;
    }

    .select-filter-input {
      width: 100%;
      --mat-form-field-container-height: 36px;
      --mat-form-field-container-vertical-padding: 6px;
      --mat-form-field-container-text-font: 'Manrope', 'Segoe UI', Tahoma, sans-serif;
      --mat-form-field-container-text-size: 13px;
    }

    :host ::ng-deep .select-filter-input .mat-mdc-input-element,
    :host ::ng-deep .select-filter-input .mat-mdc-input-element::placeholder,
    :host ::ng-deep .select-filter-input .mat-icon {
      font-family: 'Manrope', 'Segoe UI', Tahoma, sans-serif;
      font-size: 13px;
      font-weight: 600;
    }

    :host ::ng-deep .select-filter-input .mat-mdc-input-element::placeholder {
      color: var(--app-muted);
      text-transform: none;
    }
  `]
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


