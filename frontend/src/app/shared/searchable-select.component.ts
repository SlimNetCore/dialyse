import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  inject,
  Input,
  OnChanges,
  Output,
  signal,
} from '@angular/core';
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
  templateUrl: './searchable-select.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './searchable-select.component.css',
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
    const item = this.allItems().find((x) => x.id === id) ?? null;
    this.selectionChanged.emit(item);
  }
}
