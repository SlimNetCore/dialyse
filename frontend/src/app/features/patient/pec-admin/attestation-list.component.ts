import {ChangeDetectionStrategy, Component, computed, HostListener, inject, OnInit, signal,} from '@angular/core';
import {CommonModule} from '@angular/common';
import {MatTableModule} from '@angular/material/table';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatCardModule} from '@angular/material/card';
import {MatTooltipModule} from '@angular/material/tooltip';
import {MatSnackBar, MatSnackBarModule} from '@angular/material/snack-bar';
import {MatInputModule} from '@angular/material/input';
import {MatMenuModule} from '@angular/material/menu';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {BackendApiService} from '../../../core/api/backend-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {TranslateModule} from '@ngx-translate/core';
import {ColumnFilterRendererComponent} from '../../../shared/column-filter-renderer.component';
import {AttestationListStore} from './state/attestation-list.store';

@Component({
  selector: 'app-attestation-list',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatTooltipModule,
    MatSnackBarModule,
    MatInputModule,
    MatMenuModule,
    MatCheckboxModule,
    MatPaginatorModule,
    TranslateModule,
    ColumnFilterRendererComponent,
  ],
  templateUrl: './attestation-list.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './attestation-list.component.css',
})
export class AttestationListComponent implements OnInit {
  private readonly api = inject(BackendApiService);
  private readonly store = inject(AppShellStore);
  private readonly attestationListStore = inject(AttestationListStore);
  private readonly snack = inject(MatSnackBar);
  readonly hasActiveFilters = this.attestationListStore.hasActiveFilters;

  readonly rows = this.attestationListStore.rows;
  readonly total = this.attestationListStore.total;
  readonly pageIndex = this.attestationListStore.pageIndex;
  readonly pageSize = this.attestationListStore.pageSize;

  readonly allColumnsConfig = [
    {key: 'code', labelKey: 'ATTEST_LIST.COL_CODE'},
    {key: 'nom', labelKey: 'ATTEST_LIST.COL_PATIENT'},
    {key: 'assurance', labelKey: 'ATTEST_LIST.COL_ASSURANCE'},
    {key: 'debut', labelKey: 'ATTEST_LIST.COL_DEBUT'},
    {key: 'fin', labelKey: 'ATTEST_LIST.COL_FIN'},
    {key: 'actions', labelKey: 'ATTEST_LIST.COL_ACTIONS'},
  ] as const;
  readonly visibleColumns = this.attestationListStore.visibleColumns;
  private readonly isCompactViewport = signal(
    typeof window !== 'undefined' ? window.innerWidth <= 900 : false,
  );
  private readonly mobilePriorityColumns = new Set<string>([
    'code',
    'nom',
    'debut',
    'fin',
    'actions',
  ]);
  readonly displayedColumns = computed(() => {
    const visible = this.allColumnsConfig
      .filter((c) => this.visibleColumns()[c.key])
      .map((c) => c.key);
    if (!this.isCompactViewport()) return visible;

    const prioritized = visible.filter((key) => this.mobilePriorityColumns.has(key));
    if (visible.includes('actions') && !prioritized.includes('actions'))
      prioritized.push('actions');
    return prioritized.length > 0 ? prioritized : visible.slice(0, 4);
  });
  readonly columnFilters = this.attestationListStore.columnFilters;

  ngOnInit(): void {
    this.fetchPage(0, this.pageSize());
  }

  toggleColumn(column: string, checked: boolean): void {
    this.attestationListStore.setVisibleColumn(column, checked);
  }

  isColumnVisible(column: string): boolean {
    return this.visibleColumns()[column] ?? false;
  }

  onColumnFilterValue(column: string, value: string): void {
    this.attestationListStore.setFilter(column, value);
    this.fetchPage(0, this.pageSize());
  }

  columnFilterValue(column: string): string {
    return this.columnFilters()[column] ?? '';
  }

  isColumnFiltered(column: string): boolean {
    return !!(this.columnFilters()[column] ?? '').trim();
  }

  clearColumnFilter(column: string): void {
    this.attestationListStore.clearFilter(column);
    this.fetchPage(0, this.pageSize());
  }

  toggleFilterPanel(column: string, event: MouseEvent): void {
    event.stopPropagation();
    if (typeof window !== 'undefined' && window.innerWidth <= 760) {
      const wrap = (event.target as HTMLElement).closest('.th-wrap');
      if (wrap) {
        const rect = wrap.getBoundingClientRect();
        document.documentElement.style.setProperty(
          '--filter-row-bottom',
          `${Math.round(rect.bottom + 6)}px`,
        );
      }
    }
    this.attestationListStore.toggleFilterPanel(column);
  }

  isFilterOpen(column: string): boolean {
    return this.attestationListStore.openFilterColumn() === column;
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    const target = event.target as HTMLElement | null;
    if (!target) {
      this.attestationListStore.closeFilterPanel();
      return;
    }
    if (target.closest('.th-wrap')) {
      return;
    }
    this.attestationListStore.closeFilterPanel();
  }

  @HostListener('window:resize')
  onWindowResize(): void {
    this.isCompactViewport.set(window.innerWidth <= 900);
  }

  clearAllColumnFilters(): void {
    this.attestationListStore.closeFilterPanel();
    this.attestationListStore.clearAllFilters();
    this.fetchPage(0, this.pageSize());
  }

  onPageChange(event: PageEvent): void {
    this.attestationListStore.setPagination(event.pageIndex, event.pageSize);
    this.fetchPage(event.pageIndex, event.pageSize);
  }

  printList(): void {
    const cid = this.store.currentCenterId();
    if (!cid) return;
    this.api.printDocument(cid, 'LISTE_ATTESTATIONS', {}).subscribe({
      next: (blob) => window.open(URL.createObjectURL(blob), '_blank'),
      error: (e) =>
        this.snack.open('Erreur impression: ' + (e?.error?.text || e.message), 'OK', {
          duration: 5000,
        }),
    });
  }

  exportExcel(): void {
    const cid = this.store.currentCenterId();
    if (!cid) return;
    this.api.printDocument(cid, 'LISTE_ATTESTATIONS', {}, 'EXCEL').subscribe({
      next: (blob) => {
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = 'liste-attestations.xls';
        a.click();
      },
      error: (e) =>
        this.snack.open('Erreur export: ' + (e?.error?.text || e.message), 'OK', {
          duration: 5000,
        }),
    });
  }

  printRow(r: any): void {
    const cid = this.store.currentCenterId();
    const pid = (r.PATIENT_ID || r.patient_id || '').toString();
    if (!cid || !pid) return;
    this.api.printDocument(cid, 'ATTESTATION', { patientId: pid }).subscribe({
      next: (blob) => window.open(URL.createObjectURL(blob), '_blank'),
      error: (e) =>
        this.snack.open('Erreur impression: ' + (e?.error?.text || e.message), 'OK', {
          duration: 5000,
        }),
    });
  }

  private fetchPage(page: number, size: number): void {
    const cid = this.store.currentCenterId();
    if (!cid) {
      this.attestationListStore.setPageData([], 0, 0);
      return;
    }

    this.attestationListStore.setLoading(true);
    this.api
      .listAttestationsByCenter(cid, {page, size, filters: this.columnFilters()})
      .subscribe({
        next: (res) => {
          this.attestationListStore.setPageData(res.items ?? [], res.total ?? 0, res.page ?? page);
          this.attestationListStore.setLoading(false);
        },
        error: () => {
          this.attestationListStore.setPageData([], 0, page);
          this.attestationListStore.setLoading(false);
        },
      });
  }
}
