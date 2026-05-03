import {CommonModule} from '@angular/common';
import {Component, EventEmitter, Input, OnChanges, Output, SimpleChanges} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatNativeDateModule} from '@angular/material/core';
import {MatDatepickerModule} from '@angular/material/datepicker';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';

export type ColumnFilterType = 'text' | 'date' | 'number' | 'boolean' | 'enum';

@Component({
  selector: 'app-column-filter-renderer',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatDatepickerModule, MatFormFieldModule, MatIconModule, MatInputModule, MatNativeDateModule],
  template: `
    @if (type === 'date') {
      <mat-form-field appearance="outline" class="date-range-field">
        <mat-label>Entrer une période</mat-label>
        <mat-date-range-input [rangePicker]="picker" [separator]="'–'">
          <input
            matStartDate
            [value]="draftDateRange.from"
            placeholder="Date début"
            (dateInput)="onDraftDateRangeChange('from', $event.value)"
            (dateChange)="onDraftDateRangeChange('from', $event.value)"
            (keydown.enter)="applyDateRange()"
          />
          <input
            matEndDate
            [value]="draftDateRange.to"
            placeholder="Date fin"
            (dateInput)="onDraftDateRangeChange('to', $event.value)"
            (dateChange)="onDraftDateRangeChange('to', $event.value)"
            (keydown.enter)="applyDateRange()"
          />
        </mat-date-range-input>
        <mat-hint>JJ/MM/AAAA – JJ/MM/AAAA</mat-hint>
        <mat-datepicker-toggle matIconSuffix [for]="picker"></mat-datepicker-toggle>
        <mat-date-range-picker #picker (opened)="syncDraftDateRange()">
          <mat-date-range-picker-actions>
            <button mat-button type="button" matDateRangePickerCancel (click)="cancelDateRange()">Annuler</button>
            <button mat-flat-button color="primary" type="button" matDateRangePickerApply (click)="applyDateRange()">
              OK
            </button>
          </mat-date-range-picker-actions>
        </mat-date-range-picker>
      </mat-form-field>
    } @else {
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
      justify-content: center;
      gap: 10px;
      width: 100%;
      margin: 0;
    }

    .date-range-field {
      flex: 1 1 auto;
      width: 100%;
      min-width: 0;
      margin: 0;
      align-self: center;
      --mat-form-field-container-height: 40px;
      --mat-form-field-container-vertical-padding: 8px;
    }

    :host ::ng-deep .date-range-field .mat-mdc-form-field-subscript-wrapper {
      height: auto;
      min-height: 18px;
      padding-top: 2px;
      text-align: center;
    }

    :host ::ng-deep .date-range-field .mat-mdc-text-field-wrapper {
      background: #fff;
      border-radius: 12px;
    }

    :host ::ng-deep .date-range-field .mat-mdc-form-field-flex,
    :host ::ng-deep .date-range-field .mat-date-range-input-container,
    :host ::ng-deep .date-range-field .mat-date-range-input-wrapper {
      align-items: center;
      justify-content: center;
    }

    :host ::ng-deep .date-range-field .mat-mdc-form-field-infix {
      min-height: 40px;
      padding-top: 6px !important;
      padding-bottom: 6px !important;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    :host ::ng-deep .date-range-field input.mat-start-date,
    :host ::ng-deep .date-range-field input.mat-end-date,
    :host ::ng-deep .date-range-field .mat-date-range-input-separator,
    :host ::ng-deep .date-range-field input.mat-mdc-input-element,
    :host ::ng-deep .date-range-field input.mat-mdc-input-element::placeholder {
      text-align: center;
    }

    :host ::ng-deep .date-range-field .mat-date-range-input-separator {
      color: var(--app-muted);
      min-width: 16px;
    }

    :host ::ng-deep .date-range-field .mat-mdc-form-field-hint-wrapper,
    :host ::ng-deep .date-range-field .mat-mdc-form-field-hint,
    :host ::ng-deep .date-range-field .mat-mdc-form-field-bottom-align::before {
      font-size: 11px;
      color: var(--app-muted);
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
      flex: 0 0 auto;
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
export class ColumnFilterRendererComponent implements OnChanges {
  @Input() type: ColumnFilterType = 'text';
  @Input() value = '';
  @Input() placeholder = '';
  @Input() options: Array<{ value: string; label: string }> = [];

  @Output() valueChange = new EventEmitter<string>();
  @Output() clear = new EventEmitter<void>();

  protected draftDateRange: { from: Date | null; to: Date | null } = {from: null, to: null};

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['value'] || changes['type']) {
      this.syncDraftDateRange();
    }
  }

  isSelectType(): boolean {
    return this.type === 'boolean' || this.type === 'enum';
  }

  showsTrailingIcon(): boolean {
    return this.isSelectType();
  }

  trailingIcon(): string {
    if (this.isSelectType()) return 'expand_more';
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

    this.valueChange.emit(value);
  }

  onSelect(event: Event): void {
    this.valueChange.emit((event.target as HTMLSelectElement).value);
  }

  onDraftDateRangeChange(bound: 'from' | 'to', value: Date | null): void {
    this.draftDateRange = {
      ...this.draftDateRange,
      [bound]: this.normalizeDate(value)
    };
  }

  applyDateRange(): void {
    const ordered = this.getOrderedDraftDateRange();
    this.draftDateRange = ordered;
    this.valueChange.emit(this.serializeDateRange(this.toIsoDate(ordered.from), this.toIsoDate(ordered.to)));
  }

  cancelDateRange(): void {
    this.syncDraftDateRange();
  }

  syncDraftDateRange(): void {
    if (this.type !== 'date') {
      this.draftDateRange = {from: null, to: null};
      return;
    }

    const parsed = this.parseDateRange(this.value);
    this.draftDateRange = {
      from: this.isoToDate(parsed.from),
      to: this.isoToDate(parsed.to)
    };
  }

  private parseDateRange(value: string): { from: string; to: string } {
    const raw = (value ?? '').trim();
    if (!raw) return {from: '', to: ''};

    if (raw.includes('..')) {
      const [fromRaw = '', toRaw = ''] = raw.split('..', 2);
      return {
        from: this.normalizeIsoDate(fromRaw),
        to: this.normalizeIsoDate(toRaw)
      };
    }

    const normalized = this.normalizeIsoDate(raw);
    return {from: normalized, to: normalized};
  }

  private serializeDateRange(from: string, to: string): string {
    if (!from && !to) return '';
    return `${from}..${to}`;
  }

  private getOrderedDraftDateRange(): { from: Date | null; to: Date | null } {
    const from = this.normalizeDate(this.draftDateRange.from);
    const to = this.normalizeDate(this.draftDateRange.to);

    if (from && to && from.getTime() > to.getTime()) {
      return {from: to, to: from};
    }

    return {from, to};
  }

  private isoToDate(value: string): Date | null {
    const raw = this.normalizeIsoDate(value);
    if (!raw) return null;

    const [year, month, day] = raw.split('-').map(Number);
    if (!year || !month || !day) return null;

    return new Date(year, month - 1, day);
  }

  private normalizeIsoDate(value: string): string {
    const raw = (value ?? '').trim();
    return /^\d{4}-\d{2}-\d{2}$/.test(raw) ? raw : '';
  }

  private normalizeDate(value: Date | null): Date | null {
    if (!(value instanceof Date) || Number.isNaN(value.getTime())) {
      return null;
    }

    return new Date(value.getFullYear(), value.getMonth(), value.getDate());
  }

  private toIsoDate(value: Date | null): string {
    const normalized = this.normalizeDate(value);
    if (!normalized) {
      return '';
    }

    const year = normalized.getFullYear();
    const month = `${normalized.getMonth() + 1}`.padStart(2, '0');
    const day = `${normalized.getDate()}`.padStart(2, '0');
    return `${year}-${month}-${day}`;
  }
}


