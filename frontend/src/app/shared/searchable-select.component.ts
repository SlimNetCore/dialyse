import {Component, EventEmitter, inject, Input, OnChanges, Output, signal} from '@angular/core';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatSelectModule} from '@angular/material/select';
import {TranslateService} from '@ngx-translate/core';

export interface DropdownItem {
  id: string;
  label: string;
  [key: string]: any;
}

@Component({
  selector: 'app-searchable-select',
  standalone: true,
  imports: [MatFormFieldModule, MatSelectModule, MatIconModule],
  template: `
    <mat-form-field [appearance]="appearance" [class]="cssClass" [style.width]="width">
      <mat-label>{{ label }}</mat-label>
      @if (prefixIcon) {
        <mat-icon matPrefix class="prefix-icon">{{ prefixIcon }}</mat-icon>
      }
      <mat-select
        [value]="selectedIdSignal()"
        [disabled]="disabled"
        [attr.data-autofocus-first]="autofocusFirst ? '' : null"
        (selectionChange)="onSelectById($event.value)">
        @for (item of allItems(); track item.id) {
          <mat-option [value]="item.id">{{ itemLabel(item) }}</mat-option>
        }
      </mat-select>
    </mat-form-field>
  `,
  styles: [`
    .prefix-icon { margin-right: 6px; color: #607d8b; }
  `]
})
export class SearchableSelectComponent implements OnChanges {
  @Input() items: DropdownItem[] = [];
  @Input() label = '';
  @Input() placeholder = '';
  @Input() selectedId: string | null = null;
  @Input() appearance: 'outline' | 'fill' = 'outline';
  @Input() cssClass = 'full';
  @Input() width = '100%';
  @Input() prefixIcon = '';
  @Input() disabled = false;
  @Input() translateLabels = false;
  @Input() autofocusFirst = false;
  @Output() selectionChanged = new EventEmitter<DropdownItem | null>();
  readonly allItems = signal<DropdownItem[]>([]);
  readonly selectedIdSignal = signal<string | null>(null);
  private readonly translate = inject(TranslateService);

  itemLabel(item: DropdownItem | null | undefined): string {
    const raw = item?.label ?? '';
    return this.translateLabels ? this.translate.instant(raw) : raw;
  }

  ngOnChanges(): void {
    this.allItems.set([...this.items]);
    this.selectedIdSignal.set(this.selectedId);
  }

  onSelectById(id: string | null): void {
    if (this.disabled) return;
    this.selectedIdSignal.set(id);
    const item = this.allItems().find(x => x.id === id) ?? null;
    this.selectionChanged.emit(item);
  }
}
