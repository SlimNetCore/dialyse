import { Component, EventEmitter, Input, Output, signal, OnChanges, inject } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { TranslateModule } from '@ngx-translate/core';
import { PatientQrCardComponent } from './patient-qr-card.component';
import { BackendApiService } from '../../core/api/backend-api.service';
import { AuthSessionService } from '../../core/auth/auth-session.service';

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

@Component({
  selector: 'app-patient-list',
  standalone: true,
  imports: [
    MatCardModule, MatTableModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatChipsModule, MatTooltipModule, MatSnackBarModule, TranslateModule,
    PatientQrCardComponent
  ],
  template: `
    <mat-card class="list-card">
      <mat-card-header>
        <mat-icon mat-card-avatar class="header-icon">people</mat-icon>
        <mat-card-title>{{ 'PATIENT_LIST.TITLE' | translate }}</mat-card-title>
        <mat-card-subtitle>{{ 'PATIENT_LIST.TOTAL' | translate:{ count: patients.length } }}</mat-card-subtitle>
      </mat-card-header>

      <mat-card-content>
        <!-- Toolbar -->
        <div class="list-toolbar">
          <mat-form-field appearance="outline" class="search-field">
            <mat-icon matPrefix>search</mat-icon>
            <mat-label>{{ 'PATIENT_LIST.SEARCH' | translate }}</mat-label>
            <input matInput (input)="onSearch($event)" />
          </mat-form-field>
          <button mat-flat-button color="primary" (click)="newPatient.emit()" class="btn-new">
            <mat-icon>person_add</mat-icon>
            {{ 'PATIENT_LIST.BTN_NEW' | translate }}
          </button>
          <button mat-stroked-button color="primary" (click)="printList()" [matTooltip]="'PATIENT_LIST.BTN_PRINT_LIST' | translate">
            <mat-icon>print</mat-icon> {{ 'PATIENT_LIST.BTN_PRINT' | translate }}
          </button>
          <button mat-stroked-button color="primary" (click)="exportListExcel()" [matTooltip]="'PATIENT_LIST.BTN_EXPORT_EXCEL' | translate">
            <mat-icon>table_view</mat-icon> {{ 'PATIENT_LIST.BTN_EXPORT_EXCEL' | translate }}
          </button>
        </div>

        @if (filteredPatients().length === 0) {
          <div class="empty-state">
            <mat-icon>person_off</mat-icon>
            <p>{{ 'PATIENT_LIST.EMPTY' | translate }}</p>
          </div>
        } @else {
          <div class="table-container">
            <table mat-table [dataSource]="filteredPatients()" class="patient-table">
              <ng-container matColumnDef="code">
                <th mat-header-cell *matHeaderCellDef>{{ 'PATIENT_LIST.COL_CODE' | translate }}</th>
                <td mat-cell *matCellDef="let row">
                  <span class="code-chip">{{ row.code }}</span>
                </td>
              </ng-container>

              <ng-container matColumnDef="nom">
                <th mat-header-cell *matHeaderCellDef>{{ 'PATIENT_LIST.COL_NOM' | translate }}</th>
                <td mat-cell *matCellDef="let row">
                  {{ row.nom }}
                  @if (row.nonFacturable) {
                    <mat-icon color="warn" [matTooltip]="'PATIENT_LIST.NON_FACTURABLE_TOOLTIP' | translate" style="font-size:16px;width:16px;height:16px;vertical-align:middle;margin-left:4px;">warning</mat-icon>
                  }
                </td>
              </ng-container>

              <ng-container matColumnDef="prenom">
                <th mat-header-cell *matHeaderCellDef>{{ 'PATIENT_LIST.COL_PRENOM' | translate }}</th>
                <td mat-cell *matCellDef="let row">{{ row.prenom }}</td>
              </ng-container>

              <ng-container matColumnDef="sexe">
                <th mat-header-cell *matHeaderCellDef>{{ 'PATIENT_LIST.COL_SEXE' | translate }}</th>
                <td mat-cell *matCellDef="let row">
                  <mat-icon class="sexe-icon" [class.male]="row.sexe === 'M'" [class.female]="row.sexe === 'F'">
                    {{ row.sexe === 'M' ? 'male' : 'female' }}
                  </mat-icon>
                </td>
              </ng-container>

              <ng-container matColumnDef="dateAdmission">
                <th mat-header-cell *matHeaderCellDef>{{ 'PATIENT_LIST.COL_DATE_ADMISSION' | translate }}</th>
                <td mat-cell *matCellDef="let row">{{ row.dateAdmission }}</td>
              </ng-container>

              <ng-container matColumnDef="numeroAssurance">
                <th mat-header-cell *matHeaderCellDef>{{ 'PATIENT_LIST.COL_ASSURANCE' | translate }}</th>
                <td mat-cell *matCellDef="let row"><span class="mono">{{ row.numeroAssurance }}</span></td>
              </ng-container>

              <ng-container matColumnDef="etatPatient">
                <th mat-header-cell *matHeaderCellDef>{{ 'PATIENT_LIST.COL_ETAT' | translate }}</th>
                <td mat-cell *matCellDef="let row">
                  <span class="etat-badge" [attr.data-etat]="row.etatPatient">{{ row.etatPatient }}</span>
                </td>
              </ng-container>

              <ng-container matColumnDef="actions">
                <th mat-header-cell *matHeaderCellDef>{{ 'PATIENT_LIST.COL_ACTIONS' | translate }}</th>
                <td mat-cell *matCellDef="let row">
                  <button mat-icon-button [matTooltip]="'PATIENT_LIST.BTN_VIEW' | translate" (click)="selectPatient.emit(row)">
                    <mat-icon>visibility</mat-icon>
                  </button>
                  <button mat-icon-button [matTooltip]="'PATIENT_LIST.BTN_PRINT' | translate" (click)="printFiche(row)" color="primary">
                    <mat-icon>print</mat-icon>
                  </button>
                  <app-patient-qr-card
                    [patientId]="row.id"
                    [nom]="row.nom"
                    [prenom]="row.prenom"
                    [numeroAssurance]="row.numeroAssurance"
                    [dateAdmission]="row.dateAdmission" />
                </td>
              </ng-container>

              <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
              <tr mat-row *matRowDef="let row; columns: displayedColumns;" class="patient-row"></tr>
            </table>
          </div>
        }
      </mat-card-content>
    </mat-card>
  `,
  styles: [`
    .list-card { padding: 8px; }

    .header-icon {
      background: #e8f5e9;
      color: #1b5e20;
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
      margin-bottom: 16px;
    }
    .btn-new { margin-left: auto; }

    .search-field { flex: 1; max-width: 400px; }

    .table-container {
      overflow-x: auto;
      border-radius: 12px;
      border: 1px solid #e8efe9;
    }

    .patient-table {
      width: 100%;
    }

    .patient-row:hover {
      background: #f0fdf4 !important;
    }

    th.mat-mdc-header-cell {
      font-weight: 700;
      color: #1b5e20;
      font-size: 12px;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    .code-chip {
      font-family: 'Courier New', monospace;
      font-size: 12px;
      padding: 2px 8px;
      background: #f0fdf4;
      border: 1px solid #bbf7d0;
      border-radius: 6px;
      color: #166534;
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
      background: #e8f5e9;
      color: #1b5e20;
    }
    .etat-badge[data-etat="DECEDE"] { background: #fee2e2; color: #991b1b; }
    .etat-badge[data-etat="TRANSFERE"] { background: #fef3c7; color: #92400e; }
    .etat-badge[data-etat="GREFFE"] { background: #dbeafe; color: #1e40af; }

    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 48px 24px;
      color: #94a3b8;

      mat-icon {
        font-size: 48px;
        width: 48px;
        height: 48px;
        margin-bottom: 12px;
      }

      p {
        font-style: italic;
        font-size: 15px;
      }
    }
  `]
})
export class PatientListComponent implements OnChanges {
  @Input() patients: PatientRow[] = [];
  @Output() newPatient = new EventEmitter<void>();
  @Output() selectPatient = new EventEmitter<PatientRow>();

  private readonly api = inject(BackendApiService);
  private readonly auth = inject(AuthSessionService);
  private readonly snack = inject(MatSnackBar);

  readonly displayedColumns = ['code', 'nom', 'prenom', 'sexe', 'dateAdmission', 'numeroAssurance', 'etatPatient', 'actions'];
  readonly filteredPatients = signal<PatientRow[]>([]);

  private searchTerm = '';

  ngOnChanges(): void {
    this.applyFilter();
  }

  onSearch(event: Event): void {
    this.searchTerm = (event.target as HTMLInputElement).value.toLowerCase();
    this.applyFilter();
  }

  /** Imprimer la fiche patient via JasperReports */
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

  /** Imprimer la liste complète des patients via JasperReports */
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
        const url = URL.createObjectURL(blob);
        a.href = url;
        a.download = 'liste-patients.xls';
        a.click();
      },
      error: (err) => this.snack.open('Erreur export: ' + (err?.error?.text || err.message), 'OK', { duration: 5000 })
    });
  }

  private applyFilter(): void {
    if (!this.searchTerm) {
      this.filteredPatients.set(this.patients);
    } else {
      this.filteredPatients.set(
        this.patients.filter(p =>
          p.nom.toLowerCase().includes(this.searchTerm) ||
          p.prenom.toLowerCase().includes(this.searchTerm) ||
          p.code.toLowerCase().includes(this.searchTerm) ||
          p.numeroAssurance.toLowerCase().includes(this.searchTerm)
        )
      );
    }
  }
}












