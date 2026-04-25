import {Component, computed, effect, EventEmitter, inject, OnInit, Output, signal} from '@angular/core';
import {MatCardModule} from '@angular/material/card';
import {MatTableModule} from '@angular/material/table';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatChipsModule} from '@angular/material/chips';
import {MatTooltipModule} from '@angular/material/tooltip';
import {MatSnackBar, MatSnackBarModule} from '@angular/material/snack-bar';
import {TranslateModule} from '@ngx-translate/core';
import {PatientQrCardComponent} from './patient-qr-card.component';
import {BackendApiService} from '../../core/api/backend-api.service';
import {AuthSessionService} from '../../core/auth/auth-session.service';
import {MatMenuModule} from '@angular/material/menu';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {AppShellStore} from '../../core/state/app-shell.store';
import {WebSocketService} from '../../core/ws/websocket.service';
import {ColumnFilterRendererComponent} from '../../shared/column-filter-renderer.component';

export interface PatientRow {
  id: string;
  code: string;
  nom: string;
  prenom: string;
  sexe: string;
  dateAdmission: string;
  numeroAssurance: string;
  etatPatient: string;
  nonFacturable?: boolean;
}

type FilterType = 'text' | 'date';

@Component({
  selector: 'app-patient-list',
  standalone: true,
  imports: [
    MatCardModule, MatTableModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatChipsModule, MatTooltipModule, MatSnackBarModule,
    MatMenuModule, MatCheckboxModule, MatPaginatorModule, TranslateModule,
    PatientQrCardComponent, ColumnFilterRendererComponent
  ],
  template: `
    <mat-card class="list-card">
      <mat-card-header>
        <mat-icon mat-card-avatar class="header-icon">people</mat-icon>
        <mat-card-title>{{ 'PATIENT_LIST.TITLE' | translate }}</mat-card-title>
        <mat-card-subtitle>{{ 'PATIENT_LIST.TOTAL' | translate:{count: total()} }}</mat-card-subtitle>
      </mat-card-header>

      <mat-card-content>
        <div class="list-toolbar">
          <mat-form-field appearance="outline" class="search-field">
            <mat-icon matPrefix>search</mat-icon>
            <mat-label>{{ 'PATIENT_LIST.SEARCH' | translate }}</mat-label>
            <input matInput [value]="searchTerm()" (input)="onSearch($event)"/>
          </mat-form-field>

          <button mat-flat-button color="primary" (click)="newPatient.emit()" class="btn-new">
            <mat-icon>person_add</mat-icon>
            {{ 'PATIENT_LIST.BTN_NEW' | translate }}
          </button>

          <button mat-stroked-button color="primary" [matMenuTriggerFor]="colsMenu">
            <mat-icon>view_column</mat-icon>
            Colonnes
          </button>
          <mat-menu #colsMenu="matMenu">
            @for (c of allColumnsConfig; track c.key) {
              @if (c.key !== 'actions') {
                <button mat-menu-item (click)="$event.stopPropagation()">
                  <mat-checkbox [checked]="isColumnVisible(c.key)" (change)="toggleColumn(c.key, $event.checked)">
                    {{ c.labelKey | translate }}
                  </mat-checkbox>
                </button>
              }
            }
          </mat-menu>

          <button mat-stroked-button color="primary" (click)="printList()" [matTooltip]="'PATIENT_LIST.BTN_PRINT_LIST' | translate">
            <mat-icon>print</mat-icon> {{ 'PATIENT_LIST.BTN_PRINT' | translate }}
          </button>
          <button mat-stroked-button color="primary" (click)="exportListExcel()" [matTooltip]="'PATIENT_LIST.BTN_EXPORT_EXCEL' | translate">
            <mat-icon>table_view</mat-icon> {{ 'PATIENT_LIST.BTN_EXPORT_EXCEL' | translate }}
          </button>
        </div>

        @if (rows().length === 0) {
          <div class="empty-state">
            <mat-icon>person_off</mat-icon>
            <p>{{ 'PATIENT_LIST.EMPTY' | translate }}</p>
          </div>
        } @else {
          <div class="table-container">
            <table mat-table [dataSource]="rows()" class="patient-table">
              <ng-container matColumnDef="code">
                <th mat-header-cell *matHeaderCellDef>
                  <div class="th-wrap">
                    <div class="th-top"><span>{{ 'PATIENT_LIST.COL_CODE' | translate }}</span>
                      <mat-icon class="filter-ind"
                                [class.active]="isColumnFiltered('code')">{{ isColumnFiltered('code') ? 'filter_alt' : 'filter_alt_off' }}
                      </mat-icon>
                    </div>
                    <div class="th-filter">
                      <app-column-filter-renderer type="text" [value]="columnFilterValue('code')"
                                                  (valueChange)="onColumnFilterValue('code', $event)"
                                                  (clear)="clearColumnFilter('code')"/>
                    </div>
                  </div>
                </th>
                <td mat-cell *matCellDef="let row"><span class="code-chip">{{ row.code }}</span></td>
              </ng-container>

              <ng-container matColumnDef="nom">
                <th mat-header-cell *matHeaderCellDef>
                  <div class="th-wrap">
                    <div class="th-top"><span>{{ 'PATIENT_LIST.COL_NOM' | translate }}</span>
                      <mat-icon class="filter-ind"
                                [class.active]="isColumnFiltered('nom')">{{ isColumnFiltered('nom') ? 'filter_alt' : 'filter_alt_off' }}
                      </mat-icon>
                    </div>
                    <div class="th-filter">
                      <app-column-filter-renderer type="text" [value]="columnFilterValue('nom')"
                                                  (valueChange)="onColumnFilterValue('nom', $event)"
                                                  (clear)="clearColumnFilter('nom')"/>
                    </div>
                  </div>
                </th>
                <td mat-cell *matCellDef="let row">{{ row.nom }} @if (row.nonFacturable) {
                  <mat-icon color="warn" [matTooltip]="'PATIENT_LIST.NON_FACTURABLE_TOOLTIP' | translate"
                            style="font-size:16px;width:16px;height:16px;vertical-align:middle;margin-left:4px;">warning
                  </mat-icon>
                }</td>
              </ng-container>

              <ng-container matColumnDef="prenom">
                <th mat-header-cell *matHeaderCellDef>
                  <div class="th-wrap">
                    <div class="th-top"><span>{{ 'PATIENT_LIST.COL_PRENOM' | translate }}</span>
                      <mat-icon class="filter-ind"
                                [class.active]="isColumnFiltered('prenom')">{{ isColumnFiltered('prenom') ? 'filter_alt' : 'filter_alt_off' }}
                      </mat-icon>
                    </div>
                    <div class="th-filter">
                      <app-column-filter-renderer type="text" [value]="columnFilterValue('prenom')"
                                                  (valueChange)="onColumnFilterValue('prenom', $event)"
                                                  (clear)="clearColumnFilter('prenom')"/>
                    </div>
                  </div>
                </th>
                <td mat-cell *matCellDef="let row">{{ row.prenom }}</td>
              </ng-container>

              <ng-container matColumnDef="sexe">
                <th mat-header-cell *matHeaderCellDef>
                  <div class="th-wrap">
                    <div class="th-top"><span>{{ 'PATIENT_LIST.COL_SEXE' | translate }}</span>
                      <mat-icon class="filter-ind"
                                [class.active]="isColumnFiltered('sexe')">{{ isColumnFiltered('sexe') ? 'filter_alt' : 'filter_alt_off' }}
                      </mat-icon>
                    </div>
                    <div class="th-filter">
                      <app-column-filter-renderer type="enum" [options]="sexeFilterOptions"
                                                  [value]="columnFilterValue('sexe')"
                                                  (valueChange)="onColumnFilterValue('sexe', $event)"
                                                  (clear)="clearColumnFilter('sexe')"/>
                    </div>
                  </div>
                </th>
                <td mat-cell *matCellDef="let row">
                  <mat-icon class="sexe-icon" [class.male]="row.sexe === 'M'"
                            [class.female]="row.sexe === 'F'">{{ row.sexe === 'M' ? 'male' : 'female' }}
                  </mat-icon>
                </td>
              </ng-container>

              <ng-container matColumnDef="dateAdmission">
                <th mat-header-cell *matHeaderCellDef>
                  <div class="th-wrap">
                    <div class="th-top"><span>{{ 'PATIENT_LIST.COL_DATE_ADMISSION' | translate }}</span>
                      <mat-icon class="filter-ind"
                                [class.active]="isColumnFiltered('dateAdmission')">{{ isColumnFiltered('dateAdmission') ? 'filter_alt' : 'filter_alt_off' }}
                      </mat-icon>
                    </div>
                    <div class="th-filter">
                      <app-column-filter-renderer type="date" [value]="columnFilterValue('dateAdmission')"
                                                  (valueChange)="onColumnFilterValue('dateAdmission', $event)"
                                                  (clear)="clearColumnFilter('dateAdmission')"/>
                    </div>
                  </div>
                </th>
                <td mat-cell *matCellDef="let row">{{ row.dateAdmission }}</td>
              </ng-container>

              <ng-container matColumnDef="numeroAssurance">
                <th mat-header-cell *matHeaderCellDef>
                  <div class="th-wrap">
                    <div class="th-top"><span>{{ 'PATIENT_LIST.COL_ASSURANCE' | translate }}</span>
                      <mat-icon class="filter-ind"
                                [class.active]="isColumnFiltered('numeroAssurance')">{{ isColumnFiltered('numeroAssurance') ? 'filter_alt' : 'filter_alt_off' }}
                      </mat-icon>
                    </div>
                    <div class="th-filter">
                      <app-column-filter-renderer type="text" [value]="columnFilterValue('numeroAssurance')"
                                                  (valueChange)="onColumnFilterValue('numeroAssurance', $event)"
                                                  (clear)="clearColumnFilter('numeroAssurance')"/>
                    </div>
                  </div>
                </th>
                <td mat-cell *matCellDef="let row"><span class="mono">{{ row.numeroAssurance }}</span></td>
              </ng-container>

              <ng-container matColumnDef="etatPatient">
                <th mat-header-cell *matHeaderCellDef>
                  <div class="th-wrap">
                    <div class="th-top"><span>{{ 'PATIENT_LIST.COL_ETAT' | translate }}</span>
                      <mat-icon class="filter-ind"
                                [class.active]="isColumnFiltered('etatPatient')">{{ isColumnFiltered('etatPatient') ? 'filter_alt' : 'filter_alt_off' }}
                      </mat-icon>
                    </div>
                    <div class="th-filter">
                      <app-column-filter-renderer type="enum" [options]="etatFilterOptions"
                                                  [value]="columnFilterValue('etatPatient')"
                                                  (valueChange)="onColumnFilterValue('etatPatient', $event)"
                                                  (clear)="clearColumnFilter('etatPatient')"/>
                    </div>
                  </div>
                </th>
                <td mat-cell *matCellDef="let row"><span class="etat-badge"
                                                         [attr.data-etat]="row.etatPatient">{{ row.etatPatient }}</span>
                </td>
              </ng-container>

              <ng-container matColumnDef="nonFacturable">
                <th mat-header-cell *matHeaderCellDef>
                  <div class="th-wrap">
                    <div class="th-top"><span>Facturation</span>
                      <mat-icon class="filter-ind"
                                [class.active]="isColumnFiltered('nonFacturable')">{{ isColumnFiltered('nonFacturable') ? 'filter_alt' : 'filter_alt_off' }}
                      </mat-icon>
                    </div>
                    <div class="th-filter">
                      <app-column-filter-renderer type="boolean" [value]="columnFilterValue('nonFacturable')"
                                                  (valueChange)="onColumnFilterValue('nonFacturable', $event)"
                                                  (clear)="clearColumnFilter('nonFacturable')"/>
                    </div>
                  </div>
                </th>
                <td mat-cell *matCellDef="let row">
                  <span class="etat-badge" [style.background]="row.nonFacturable ? '#fee2e2' : '#dcfce7'"
                        [style.color]="row.nonFacturable ? '#991b1b' : '#166534'">
                    {{ row.nonFacturable ? 'Non facturable' : 'Facturable' }}
                  </span>
                </td>
              </ng-container>

              <ng-container matColumnDef="actions">
                <th mat-header-cell *matHeaderCellDef>{{ 'PATIENT_LIST.COL_ACTIONS' | translate }}</th>
                <td mat-cell *matCellDef="let row">
                  <button mat-icon-button [matTooltip]="'PATIENT_LIST.BTN_VIEW' | translate"
                          (click)="selectPatient.emit(row)">
                    <mat-icon>visibility</mat-icon>
                  </button>
                  <button mat-icon-button [matTooltip]="'PATIENT_LIST.BTN_PRINT' | translate" (click)="printFiche(row)"
                          color="primary">
                    <mat-icon>print</mat-icon>
                  </button>
                  <app-patient-qr-card [patientId]="row.id" [nom]="row.nom" [prenom]="row.prenom"
                                       [numeroAssurance]="row.numeroAssurance" [dateAdmission]="row.dateAdmission"/>
                </td>
              </ng-container>

              <tr mat-header-row *matHeaderRowDef="displayedColumns()"></tr>
              <tr mat-row *matRowDef="let row; columns: displayedColumns();" class="patient-row"></tr>
            </table>
          </div>
          <mat-paginator [length]="total()" [pageIndex]="pageIndex()" [pageSize]="pageSize()"
                         [pageSizeOptions]="[5,10,20,50]" (page)="onPageChange($event)"></mat-paginator>
        }
      </mat-card-content>
    </mat-card>
  `,
  styles: [`
    .list-card {
      padding: 10px;
      background: var(--app-surface);
      border: 1px solid var(--app-border);
      box-shadow: var(--app-shadow);
    }

    .header-icon {
      background: var(--app-primary-soft);
      color: var(--app-primary);
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      width: 40px;
      height: 40px;
    }

    .list-toolbar {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-bottom: 14px;
      flex-wrap: wrap;
    }
    .btn-new { margin-left: auto; }
    .search-field { flex: 1; max-width: 400px; }

    .table-container {
      overflow-x: auto;
      border-radius: 12px;
      border: 1px solid var(--app-border);
      background: var(--app-surface);
    }

    .patient-table {
      width: 100%;
    }

    .patient-row:hover {
      background: color-mix(in srgb, var(--app-primary-soft) 70%, white) !important;
    }

    th.mat-mdc-header-cell {
      font-weight: 700;
      color: var(--app-primary);
      font-size: 12px;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    .code-chip {
      font-family: 'Courier New', monospace;
      font-size: 12px;
      padding: 2px 8px;
      background: var(--app-primary-soft);
      border: 1px solid var(--app-primary-outline);
      border-radius: 6px;
      color: var(--app-primary);
    }

    .mono {
      font-family: 'Courier New', monospace;
      font-size: 12px;
    }

    .sexe-icon {
      font-size: 20px;
      width: 20px;
      height: 20px;
    }
    .sexe-icon.male { color: #1565c0; }
    .sexe-icon.female { color: #c62828; }

    .etat-badge {
      display: inline-block;
      padding: 2px 10px;
      border-radius: 20px;
      font-size: 11px;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.3px;
      background: var(--app-primary-soft);
      color: var(--app-primary);
    }
    .etat-badge[data-etat="DECEDE"] { background: #fee2e2; color: #991b1b; }
    .etat-badge[data-etat="TRANSFERE"] { background: #fef3c7; color: #92400e; }
    .etat-badge[data-etat="GREFFE"] { background: #dbeafe; color: #1e40af; }

    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 48px 24px;
      color: var(--app-muted);
    }

    .empty-state mat-icon {
      font-size: 48px;
      width: 48px;
      height: 48px;
      margin-bottom: 12px;
    }

    .empty-state p {
      font-style: italic;
      font-size: 15px;
    }

    .th-wrap {
      display: grid;
      gap: 6px;
    }

    .th-top {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 6px;
    }

    .th-filter {
      display: flex;
      align-items: center;
      gap: 6px;
      padding: 3px;
      border-radius: 10px;
      background: color-mix(in srgb, var(--app-primary-soft) 60%, white);
      border: 1px solid var(--app-border);
    }

    .filter-ind {
      font-size: 17px;
      width: 17px;
      height: 17px;
      color: #94a3b8;
    }

    .filter-ind.active {
      color: var(--app-primary);
    }

    .th-filter :where(app-column-filter-renderer) { width: 100%; }
  `]
})
export class PatientListComponent implements OnInit {
  @Output() newPatient = new EventEmitter<void>();
  @Output() selectPatient = new EventEmitter<PatientRow>();

  private readonly api = inject(BackendApiService);
  private readonly auth = inject(AuthSessionService);
  readonly allColumnsConfig = [
    {key: 'code', labelKey: 'PATIENT_LIST.COL_CODE', type: 'text' as FilterType},
    {key: 'nom', labelKey: 'PATIENT_LIST.COL_NOM', type: 'text' as FilterType},
    {key: 'prenom', labelKey: 'PATIENT_LIST.COL_PRENOM', type: 'text' as FilterType},
    {key: 'sexe', labelKey: 'PATIENT_LIST.COL_SEXE', type: 'text' as FilterType},
    {key: 'dateAdmission', labelKey: 'PATIENT_LIST.COL_DATE_ADMISSION', type: 'date' as FilterType},
    {key: 'numeroAssurance', labelKey: 'PATIENT_LIST.COL_ASSURANCE', type: 'text' as FilterType},
    {key: 'etatPatient', labelKey: 'PATIENT_LIST.COL_ETAT', type: 'text' as FilterType},
    {key: 'nonFacturable', labelKey: 'PATIENT_LIST.NON_FACTURABLE_TOOLTIP', type: 'text' as FilterType},
    {key: 'actions', labelKey: 'PATIENT_LIST.COL_ACTIONS', type: 'text' as FilterType}
  ] as const;
  readonly visibleColumns = signal<Record<string, boolean>>({
    code: true,
    nom: true,
    prenom: true,
    sexe: true,
    dateAdmission: true,
    numeroAssurance: true,
    etatPatient: true,
    nonFacturable: true,
    actions: true
  });
  private readonly snack = inject(MatSnackBar);
  readonly displayedColumns = computed(() => this.allColumnsConfig.filter(c => this.visibleColumns()[c.key]).map(c => c.key));
  readonly rows = signal<PatientRow[]>([]);
  readonly total = signal(0);
  readonly pageIndex = signal(0);
  readonly pageSize = signal(10);
  readonly columnFilters = signal<Record<string, string>>({});
  readonly searchTerm = signal('');
  private readonly store = inject(AppShellStore);
  private readonly ws = inject(WebSocketService);

  constructor() {
    effect(() => {
      const evt = this.ws.lastEvent();
      if (evt?.type === 'PATIENT_CREATED' || evt?.type === 'PEC_VALIDATED') {
        this.fetchPage(this.pageIndex(), this.pageSize());
      }
    });
  }

  ngOnInit(): void {
    this.fetchPage(0, this.pageSize());
  }

  onSearch(event: Event): void {
    this.searchTerm.set((event.target as HTMLInputElement).value);
    this.fetchPage(0, this.pageSize());
  }

  onColumnFilter(column: string, event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.columnFilters.update(prev => ({...prev, [column]: value}));
    this.fetchPage(0, this.pageSize());
  }
  readonly sexeFilterOptions = [
    {value: 'M', label: 'Masculin'},
    {value: 'F', label: 'Féminin'}
  ];

  columnFilterValue(column: string): string {
    return this.columnFilters()[column] ?? '';
  }

  isColumnFiltered(column: string): boolean {
    return !!(this.columnFilters()[column] ?? '').trim();
  }

  clearColumnFilter(column: string): void {
    this.columnFilters.update(prev => ({...prev, [column]: ''}));
    this.fetchPage(0, this.pageSize());
  }

  toggleColumn(column: string, checked: boolean): void {
    this.visibleColumns.update(prev => ({...prev, [column]: checked}));
  }

  isColumnVisible(column: string): boolean {
    return !!this.visibleColumns()[column];
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.fetchPage(event.pageIndex, event.pageSize);
  }

  private fetchPage(page: number, size: number): void {
    const centerId = this.store.currentCenterId();
    if (!centerId) {
      this.rows.set([]);
      this.total.set(0);
      return;
    }

    this.api.listPatients(centerId, this.auth.username() ?? 'demo', {
      page,
      size,
      search: this.searchTerm(),
      filters: this.columnFilters()
    }).subscribe({
      next: (res) => {
        const mapped = (res.items ?? []).map((p: any) => ({
          id: p.id?.value ?? p.id,
          code: p.codePatient ?? '',
          nom: p.nom ?? '',
          prenom: p.prenom ?? '',
          sexe: p.sexe ?? '',
          dateAdmission: p.dateAdmission ?? '',
          numeroAssurance: p.numeroAssurance?.value ?? p.numeroAssurance ?? '',
          etatPatient: p.etatPatient ?? 'PERMANENT',
          nonFacturable: !!p.nonFacturable
        }));
        this.rows.set(mapped);
        this.total.set(res.total ?? 0);
        this.pageIndex.set(res.page ?? page);
      },
      error: () => {
        this.rows.set([]);
        this.total.set(0);
      }
    });
  }

  printFiche(patient: PatientRow): void {
    const centerId = this.auth.centerId();
    if (!centerId) return;
    this.api.printDocument(centerId, 'FICHE_PATIENT', { patientId: patient.id }).subscribe({
      next: (blob: Blob) => {
        const url = URL.createObjectURL(blob);
        window.open(url, '_blank');
      },
      error: (err) => {
        this.snack.open('Erreur impression: ' + (err?.error?.text || err.message), 'OK', { duration: 5000 });
      }
    });
  }

  printList(): void {
    const centerId = this.auth.centerId();
    if (!centerId) return;
    this.api.printDocument(centerId, 'LISTE_PATIENTS', {}).subscribe({
      next: (blob: Blob) => {
        const url = URL.createObjectURL(blob);
        window.open(url, '_blank');
      },
      error: (err) => {
        this.snack.open('Erreur impression: ' + (err?.error?.text || err.message), 'OK', { duration: 5000 });
      }
    });
  }

  exportListExcel(): void {
    const centerId = this.auth.centerId();
    if (!centerId) return;
    this.api.printDocument(centerId, 'LISTE_PATIENTS', {}, 'EXCEL').subscribe({
      next: (blob: Blob) => {
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = 'liste-patients.xls';
        a.click();
      },
      error: (err) => this.snack.open('Erreur export: ' + (err?.error?.text || err.message), 'OK', { duration: 5000 })
    });
  }
  readonly etatFilterOptions = [
    {value: 'PERMANENT', label: 'Permanent'},
    {value: 'OCCASIONNEL', label: 'Occasionnel'},
    {value: 'VACANCIER_LOCAL', label: 'Vacancier local'},
    {value: 'VACANCIER_ETRANGER', label: 'Vacancier étranger'},
    {value: 'TRANSFERE', label: 'Transféré'},
    {value: 'DECEDE', label: 'Décédé'},
    {value: 'GREFFE', label: 'Greffé'}
  ];

  onColumnFilterValue(column: string, value: string): void {
    this.columnFilters.update(prev => ({...prev, [column]: value}));
    this.fetchPage(0, this.pageSize());
  }
}

