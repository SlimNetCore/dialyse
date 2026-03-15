import { Component, EventEmitter, Input, Output, signal, computed, OnChanges } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatIconModule } from '@angular/material/icon';

export interface DropdownItem {
  id: string;
  label: string;
  [key: string]: any;
}

@Component({
  selector: 'app-searchable-select',
  standalone: true,
  imports: [FormsModule, MatFormFieldModule, MatInputModule, MatAutocompleteModule, MatIconModule],
  template: `
    <mat-form-field [appearance]="appearance" [class]="cssClass" [style.width]="width">
      <mat-label>{{ label }}</mat-label>
      @if (prefixIcon) {
        <mat-icon matPrefix class="prefix-icon">{{ prefixIcon }}</mat-icon>
      }
      <input matInput
             [matAutocomplete]="auto"
             [value]="displayValue()"
             (input)="onSearch($event)"
             (focus)="onFocus()"
             [placeholder]="placeholder" />
      <mat-icon matSuffix class="dd-icon">arrow_drop_down</mat-icon>
      <mat-autocomplete #auto="matAutocomplete"
                        (optionSelected)="onSelect($event.option.value)"
                        [displayWith]="displayFn">
        @for (item of filtered(); track item.id) {
          <mat-option [value]="item">{{ item.label }}</mat-option>
        }
      </mat-autocomplete>
    </mat-form-field>
  `,
  styles: [`
    .dd-icon { font-size: 18px; color: #94a3b8; cursor: pointer; }
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
  @Output() selectionChanged = new EventEmitter<DropdownItem | null>();

  private searchText = signal('');
  private allItems = signal<DropdownItem[]>([]);

  filtered = computed(() => {
    const txt = this.searchText().toLowerCase();
    return this.allItems().filter(i => !txt || i.label.toLowerCase().includes(txt));
  });

  displayValue = computed(() => {
    if (this.selectedId) {
      const found = this.allItems().find(i => i.id === this.selectedId);
      return found?.label ?? '';
    }
    return '';
  });

  displayFn = (item: DropdownItem): string => item?.label ?? '';

  ngOnChanges(): void {
    this.allItems.set(this.items);
  }

  onSearch(event: Event): void {
    this.searchText.set((event.target as HTMLInputElement).value);
  }

  onFocus(): void {
    this.searchText.set('');
  }

  onSelect(item: DropdownItem): void {
    this.selectionChanged.emit(item);
  }
}
