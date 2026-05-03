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
    <div class="field-shell" [class.active]="isActive()">
      @if (isSelectType()) {
        <select class="col-filter select-filter" [value]="value" (change)="onSelect($event)">
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

      @if (showsTrailingIcon()) {
        <mat-icon class="field-icon">{{ trailingIcon() }}</mat-icon>
      }
    </div>

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
      justify-content: center;
      gap: 10px;
      width: 100%;
      margin: 0;
    }

    .field-shell {
      position: relative;
      display: flex;
      align-items: center;
      flex: 1 1 auto;
      width: 100%;
      min-width: 0;
      min-height: 40px;
      padding-inline: 12px;
      background: #fff;
      border: 1px solid color-mix(in srgb, var(--app-text) 18%, white);
      border-radius: 12px;
      box-shadow: inset 0 1px 2px rgba(15, 23, 42, 0.04);
      transition: border-color .18s ease, box-shadow .18s ease, background-color .18s ease;
    }

    .field-shell:hover {
      border-color: color-mix(in srgb, var(--app-primary) 28%, white);
      background: color-mix(in srgb, var(--app-primary-soft) 20%, white);
    }

    .field-shell:focus-within {
      border-color: var(--app-primary);
      box-shadow: 0 0 0 3px color-mix(in srgb, var(--app-primary) 14%, white);
    }

    .field-shell.active {
      border-color: color-mix(in srgb, var(--app-primary) 40%, white);
    }

    .col-filter {
      flex: 1 1 auto;
      height: 38px;
      width: 100%;
      min-width: 0;
      font-size: 13px;
      line-height: 38px;
      text-align: center;
      border: 0;
      padding: 0;
      background: transparent;
      outline: none;
      color: var(--app-text);
    }

    .col-filter::placeholder {
      text-align: center;
      color: var(--app-muted);
    }

    .select-filter {
      appearance: none;
      -webkit-appearance: none;
      -moz-appearance: none;
      cursor: pointer;
      text-align-last: center;
      padding-right: 24px;
    }

    .field-icon {
      position: absolute;
      right: 10px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 18px;
      height: 18px;
      font-size: 18px;
      color: var(--app-muted);
      pointer-events: none;
    }

    .clear-filter {
      width: 40px;
      height: 40px;
      min-width: 40px;
      padding: 0;
      border-radius: 999px;
      background: color-mix(in srgb, var(--app-primary-soft) 70%, white);
      border: 1px solid color-mix(in srgb, var(--app-primary-outline) 55%, #ffffff);
      color: var(--app-primary);
      transition: all .18s ease;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      align-self: center;
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
      margin: 0;
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

  showsTrailingIcon(): boolean {
    return this.isSelectType() || this.type === 'date';
  }

  trailingIcon(): string {
    if (this.isSelectType()) return 'expand_more';
    if (this.type === 'date') return 'calendar_month';
    return '';
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
    const input = event.target as HTMLInputElement;
    const value = input.value;

    // Only emit value if date is complete (for date inputs) or always for other inputs
    if (this.type === 'date') {
      // Check if it's in valid ISO date format (YYYY-MM-DD)
      if (value && /^\d{4}-\d{2}-\d{2}$/.test(value)) {
        this.valueChange.emit(value);
      } else if (!value) {
        // Always emit empty values (to clear filter)
        this.valueChange.emit('');
      }
      // Otherwise don't emit for partial dates - user is still typing
    } else {
      this.valueChange.emit(value);
    }
  }

  onSelect(event: Event): void {
    this.valueChange.emit((event.target as HTMLSelectElement).value);
  }
}


