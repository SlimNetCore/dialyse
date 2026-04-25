import {CommonModule} from '@angular/common';
import {Component, EventEmitter, Input, Output} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';

export type ColumnFilterType = 'text' | 'date' | 'number' | 'boolean' | 'enum';

@Component({
  selector: 'app-column-filter-renderer',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatIconModule],
  template: `
    @if (isSelectType()) {
      <select class="col-filter" [value]="value" (change)="onSelect($event)">
        @if (type === 'boolean') {
          <option value="">Tous</option>
          <option value="true">Oui</option>
          <option value="false">Non</option>
        } @else {
          <option value="">Tous</option>
          @for (o of options; track o.value) {
            <option [value]="o.value">{{ o.label }}</option>
          }
        }
      </select>
    } @else {
      <input
        class="col-filter"
        [attr.type]="inputType()"
        [value]="value"
        [placeholder]="placeholder"
        (input)="onInput($event)"
      />
    }

    @if (isActive()) {
      <button mat-icon-button class="clear-filter" (click)="clear.emit()" type="button" aria-label="Effacer filtre">
        <mat-icon>close</mat-icon>
      </button>
    }
  `,
  styles: [`
    :host {
      display: flex;
      align-items: center;
      gap: 6px;
      width: 100%;
      margin-bottom: 4px;
    }

    .col-filter {
      height: 30px;
      width: 100%;
      min-width: 96px;
      font-size: 12px;
      border: 1px solid transparent;
      border-radius: 8px;
      padding: 4px 8px;
      background: #fff;
      outline: none;
    }

    .col-filter:focus {
      border-color: var(--app-primary-outline);
      box-shadow: 0 0 0 3px color-mix(in srgb, var(--app-primary) 14%, white);
    }

    .clear-filter {
      width: 28px;
      height: 28px;
      border-radius: 999px;
      background: color-mix(in srgb, var(--app-primary-soft) 70%, white);
      border: 1px solid color-mix(in srgb, var(--app-primary-outline) 55%, #ffffff);
      color: var(--app-primary);
      transition: all .18s ease;
    }

    .clear-filter:hover {
      background: color-mix(in srgb, var(--app-primary-soft) 35%, white);
      transform: translateY(-1px);
      box-shadow: 0 3px 8px rgba(2, 6, 23, 0.12);
    }

    .clear-filter mat-icon {
      font-size: 15px;
      width: 15px;
      height: 15px;
    }
  `]
})
export class ColumnFilterRendererComponent {
  @Input() type: ColumnFilterType = 'text';
  @Input() value = '';
  @Input() placeholder = '';
  @Input() options: Array<{ value: string; label: string }> = [];

  @Output() valueChange = new EventEmitter<string>();
  @Output() clear = new EventEmitter<void>();

  isSelectType(): boolean {
    return this.type === 'boolean' || this.type === 'enum';
  }

  inputType(): string {
    if (this.type === 'date') return 'date';
    if (this.type === 'number') return 'number';
    return 'text';
  }

  isActive(): boolean {
    return !!this.value?.toString().trim();
  }

  onInput(event: Event): void {
    this.valueChange.emit((event.target as HTMLInputElement).value);
  }

  onSelect(event: Event): void {
    this.valueChange.emit((event.target as HTMLSelectElement).value);
  }
}
