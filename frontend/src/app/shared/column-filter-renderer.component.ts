import {CommonModule} from '@angular/common';
import {Component, EventEmitter, inject, Input, OnChanges, Output, SimpleChanges} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatNativeDateModule} from '@angular/material/core';
import {MatDatepickerModule} from '@angular/material/datepicker';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {SelectFilterComponent} from './select-filter.component';

export type ColumnFilterType = 'text' | 'date' | 'number' | 'boolean' | 'enum';

@Component({
  selector: 'app-column-filter-renderer',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatNativeDateModule,
    MatSelectModule,
    TranslateModule,
    SelectFilterComponent
  ],
  template: `
    @if (type === 'date') {
      <mat-form-field appearance="outline" class="date-range-field">
        <mat-label>{{ 'COMMON.FILTER_BY' | translate:{ field: (labelKey | translate) } }}</mat-label>
        <mat-date-range-input [rangePicker]="picker" [separator]="'–'">
          <input
            matStartDate
            [value]="draftDateRange.from"
            [placeholder]="'COMMON.DATE_START' | translate"
            (dateInput)="onDraftDateRangeChange('from', $event.value)"
            (dateChange)="onDraftDateRangeChange('from', $event.value)"
            (keydown.enter)="applyDateRange()"
          />
          <input
            matEndDate
            [value]="draftDateRange.to"
            [placeholder]="'COMMON.DATE_END' | translate"
            (dateInput)="onDraftDateRangeChange('to', $event.value)"
            (dateChange)="onDraftDateRangeChange('to', $event.value)"
            (keydown.enter)="applyDateRange()"
          />
        </mat-date-range-input>
        <mat-datepicker-toggle matIconSuffix [for]="picker"></mat-datepicker-toggle>
        <mat-date-range-picker #picker (opened)="syncDraftDateRange()">
          <mat-date-range-picker-actions>
            <button mat-button type="button" matDateRangePickerCancel (click)="cancelDateRange()">{{ 'PATIENT_FORM.BTN_CANCEL' | translate }}</button>
            <button mat-flat-button color="primary" type="button" matDateRangePickerApply (click)="applyDateRange()">
              {{ 'COMMON.OK' | translate }}
            </button>
          </mat-date-range-picker-actions>
        </mat-date-range-picker>
      </mat-form-field>
    } @else {
      @if (isSelectType()) {
        <mat-form-field appearance="outline" class="select-filter-field" [class.active]="isActive()">
          <mat-select [value]="selectedValues()" [multiple]="isMultiSelect()" panelClass="column-filter-select-panel"
                      [panelWidth]="'360px'" (selectionChange)="onMatSelect($event.value)"
                      [placeholder]="placeholder || ('COMMON.FILTER_BY' | translate:{ field: (labelKey | translate) })">
            <mat-option class="panel-filter-option" disabled>
              <app-select-filter [placeholder]="'COMMON.SEARCH'" (valueChange)="onPanelFilterChange($event)"/>
            </mat-option>
            @if (!isMultiSelect()) {
              <mat-option value="">{{ 'COMMON.ALL' | translate }}</mat-option>
            }
            @if (type === 'boolean') {
              @for (o of booleanOptions(); track o.value) {
                @if (isPanelOptionVisible(o.label)) {
                  <mat-option [value]="o.value">{{ o.label }}</mat-option>
                }
              }
            } @else {
              @for (o of options; track o.value) {
                @if (isPanelOptionVisible(o.label)) {
                  <mat-option [value]="o.value">{{ o.label }}</mat-option>
                }
              }
            }
          </mat-select>
        </mat-form-field>
      } @else {
        <div class="field-shell" [class.active]="isActive()">
          <input
            class="col-filter"
            [attr.type]="inputType()"
            [value]="value"
            [placeholder]="placeholder || ('COMMON.FILTER_BY' | translate:{ field: (labelKey | translate) })"
            (input)="onInput($event)"
          />

          @if (showsTrailingIcon()) {
            <mat-icon class="field-icon">{{ trailingIcon() }}</mat-icon>
          }
        </div>
      }
    }

    @if (isActive()) {
      <button mat-icon-button class="clear-filter" (click)="clear.emit()" type="button" [attr.aria-label]="'COMMON.CLEAR_FILTER' | translate">
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
      text-transform: none;
      --filter-font-family: 'Manrope', 'Segoe UI', Tahoma, sans-serif;
      --filter-font-size: 13px;
      --filter-font-weight: 600;
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
      display: none;
    }

    :host ::ng-deep .date-range-field .mat-mdc-text-field-wrapper {
      background: var(--app-field-bg);
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
      font-family: var(--filter-font-family);
      font-size: var(--filter-font-size);
      font-weight: var(--filter-font-weight);
      text-align: center;
    }

    :host ::ng-deep .date-range-field input.mat-mdc-input-element::placeholder {
      color: var(--app-muted);
      text-transform: none;
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
      background: var(--app-field-bg);
      border: 1px solid var(--app-border-strong);
      border-radius: 12px;
      box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.04);
      transition: border-color .18s ease, box-shadow .18s ease, background-color .18s ease;
    }

    .select-filter-field {
      width: 100%;
      margin: 0;
      --mat-form-field-container-height: 40px;
      --mat-form-field-container-vertical-padding: 8px;
    }

    :host ::ng-deep .select-filter-field .mat-mdc-form-field-subscript-wrapper {
      display: none;
    }

    :host ::ng-deep .select-filter-field .mat-mdc-form-field-infix {
      min-height: 40px;
      display: flex;
      align-items: center;
    }

    .select-filter-field.active {
      --mdc-outlined-text-field-outline-color: var(--app-primary-outline);
    }

    :host ::ng-deep .panel-filter-option {
      height: auto !important;
      min-height: 0 !important;
      padding: 0 !important;
      opacity: 1 !important;
      cursor: default;
      pointer-events: auto;
    }

    :host ::ng-deep .panel-filter-option .mdc-list-item__primary-text {
      width: 100%;
    }

    .field-shell:hover {
      border-color: var(--app-primary-outline);
      background: var(--app-field-hover-bg);
    }

    .field-shell:focus-within {
      border-color: var(--app-primary);
      box-shadow: 0 0 0 3px color-mix(in srgb, var(--app-primary) 16%, transparent);
    }

    .field-shell.active {
      border-color: var(--app-primary-outline);
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
      font-family: var(--filter-font-family);
      font-size: var(--filter-font-size);
      font-weight: var(--filter-font-weight);
      text-transform: none;
    }

    .col-filter::placeholder {
      font-family: var(--filter-font-family);
      font-size: var(--filter-font-size);
      font-weight: var(--filter-font-weight);
      text-align: center;
      color: var(--app-muted);
      text-transform: none;
    }

    :host ::ng-deep .select-filter-field .mat-mdc-select-value,
    :host ::ng-deep .select-filter-field .mat-mdc-select-placeholder,
    :host ::ng-deep .select-filter-field .mat-mdc-select-value-text,
    :host ::ng-deep .select-filter-field .mat-mdc-select-min-line {
      font-family: var(--filter-font-family) !important;
      font-size: var(--filter-font-size) !important;
      font-weight: var(--filter-font-weight) !important;
      text-transform: none !important;
    }

    .select-filter {
      appearance: none;
      -webkit-appearance: none;
      -moz-appearance: none;
      cursor: pointer;
      text-align-last: center;
      padding-right: 24px;
    }

    .select-filter option {
      background: var(--app-surface-solid);
      color: var(--app-text);
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
      background: var(--app-frost);
      border: 1px solid var(--app-primary-outline);
      color: var(--app-primary);
      transition: all .18s ease;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      align-self: center;
      flex: 0 0 auto;
    }

    .clear-filter:hover {
      background: var(--app-hover-surface);
      transform: translateY(-1px);
      box-shadow: 0 10px 24px rgba(0, 0, 0, 0.22);
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
  protected panelFilter = '';

  @Input() type: ColumnFilterType = 'text';
  @Input() value = '';
  @Input() placeholder = '';
  @Input() labelKey = '';
  @Input() options: Array<{ value: string; label: string }> = [];

  @Output() valueChange = new EventEmitter<string>();
  @Output() clear = new EventEmitter<void>();

  protected draftDateRange: { from: Date | null; to: Date | null } = {from: null, to: null};
  private readonly translate = inject(TranslateService);

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['value'] || changes['type']) {
      this.syncDraftDateRange();
      if (changes['type']) this.panelFilter = '';
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

  onMatSelect(value: string | string[]): void {
    if (Array.isArray(value)) {
      this.valueChange.emit(value.filter(v => !!v).join(','));
      return;
    }
    this.valueChange.emit(value ?? '');
  }

  selectedValues(): string[] | string {
    if (!this.isMultiSelect()) return this.value ?? '';
    return (this.value ?? '')
      .split(',')
      .map(v => v.trim())
      .filter(v => !!v);
  }

  isMultiSelect(): boolean {
    return this.type === 'enum';
  }

  onPanelFilterChange(value: string): void {
    this.panelFilter = value ?? '';
  }

  booleanOptions(): Array<{ value: string; label: string }> {
    return [
      {value: 'true', label: this.translate.instant('COMMON.YES')},
      {value: 'false', label: this.translate.instant('COMMON.NO')}
    ];
  }

  isPanelOptionVisible(label: string): boolean {
    const query = (this.panelFilter ?? '').trim().toLowerCase();
    if (!query) return true;
    return (label ?? '').toLowerCase().includes(query);
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


