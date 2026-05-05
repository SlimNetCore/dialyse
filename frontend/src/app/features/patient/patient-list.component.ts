import {Component, computed, effect, EventEmitter, HostListener, inject, OnInit, Output, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
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
  dateEvenementEtat?: string;
  nonFacturable?: boolean;
  medecinTraitantId?: string;
  positionId?: string;
  transporteurAllerId?: string;
  transporteurRetourId?: string;
  joursDialyse?: any;
  pecStatus?: string;
  pecForfaitId?: string;
}

type FilterType = 'text' | 'date';

@Component({
  selector: 'app-patient-list',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule, MatTableModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatChipsModule, MatTooltipModule, MatSnackBarModule,
    MatMenuModule, MatCheckboxModule, MatPaginatorModule, TranslateModule,
    PatientQrCardComponent, ColumnFilterRendererComponent
  ],
  template: `
    <mat-card class="list-card">
      <mat-card-header>
        <div class="header-copy">
          <mat-card-title>{{ 'PATIENT_LIST.TITLE' | translate }}</mat-card-title>
          <mat-card-subtitle>{{ 'PATIENT_LIST.TOTAL' | translate:{count: total()} }}</mat-card-subtitle>
        </div>
      </mat-card-header>

      <mat-card-content>
        <div class="list-toolbar">
          <div class="toolbar-group">
            <button mat-stroked-button color="warn" (click)="clearAllColumnFilters()" [disabled]="!hasActiveFilters()">
              <mat-icon>filter_alt_off</mat-icon>
              Réinitialiser filtres
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

            <button mat-stroked-button color="primary" (click)="printList()"
                    [matTooltip]="'PATIENT_LIST.BTN_PRINT_LIST' | translate">
              <mat-icon>print</mat-icon>
              {{ 'PATIENT_LIST.BTN_PRINT' | translate }}
            </button>
            <button mat-stroked-button color="primary" (click)="exportListExcel()"
                    [matTooltip]="'PATIENT_LIST.BTN_EXPORT_EXCEL' | translate">
              <mat-icon>table_view</mat-icon>
              {{ 'PATIENT_LIST.BTN_EXPORT_EXCEL' | translate }}
            </button>
          </div>

          <div class="toolbar-group toolbar-group-end">
            <button mat-flat-button color="primary" (click)="newPatient.emit()" class="btn-new">
              <mat-icon>person_add</mat-icon>
              {{ 'PATIENT_LIST.BTN_NEW' | translate }}
            </button>
          </div>
        </div>

        <div class="table-container">
            <table mat-table [dataSource]="rows()" class="patient-table">
              <ng-container matColumnDef="code">
                <th mat-header-cell *matHeaderCellDef>
                  <div class="th-wrap" [class.open]="isFilterOpen('code')">
                    <div class="th-top"><span>{{ 'PATIENT_LIST.COL_CODE' | translate }}</span>
                      <mat-icon class="filter-ind"
                                (click)="toggleFilterPanel('code', $event)"
                                [class.active]="isColumnFiltered('code')">{{ isColumnFiltered('code') ? 'filter_alt' : 'filter_alt_off' }}
                      </mat-icon>
                    </div>
                    <div class="th-filter">
                      <app-column-filter-renderer type="text" labelKey="PATIENT_LIST.COL_CODE"
                                                  [value]="columnFilterValue('code')"
                                                  (valueChange)="onColumnFilterValue('code', $event)"
                                                  (clear)="clearColumnFilter('code')"/>
                    </div>
                  </div>
                </th>
                <td mat-cell *matCellDef="let row"><span class="code-chip">{{ row.code }}</span></td>
              </ng-container>

              <ng-container matColumnDef="nom">
                <th mat-header-cell *matHeaderCellDef>
                  <div class="th-wrap" [class.open]="isFilterOpen('nom')">
                    <div class="th-top"><span>{{ 'PATIENT_LIST.COL_NOM' | translate }}</span>
                      <mat-icon class="filter-ind"
                                (click)="toggleFilterPanel('nom', $event)"
                                [class.active]="isColumnFiltered('nom')">{{ isColumnFiltered('nom') ? 'filter_alt' : 'filter_alt_off' }}
                      </mat-icon>
                    </div>
                    <div class="th-filter">
                      <app-column-filter-renderer type="text" labelKey="PATIENT_LIST.COL_NOM"
                                                  [value]="columnFilterValue('nom')"
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
                  <div class="th-wrap" [class.open]="isFilterOpen('prenom')">
                    <div class="th-top"><span>{{ 'PATIENT_LIST.COL_PRENOM' | translate }}</span>
                      <mat-icon class="filter-ind"
                                (click)="toggleFilterPanel('prenom', $event)"
                                [class.active]="isColumnFiltered('prenom')">{{ isColumnFiltered('prenom') ? 'filter_alt' : 'filter_alt_off' }}
                      </mat-icon>
                    </div>
                    <div class="th-filter">
                      <app-column-filter-renderer type="text" labelKey="PATIENT_LIST.COL_PRENOM"
                                                  [value]="columnFilterValue('prenom')"
                                                  (valueChange)="onColumnFilterValue('prenom', $event)"
                                                  (clear)="clearColumnFilter('prenom')"/>
                    </div>
                  </div>
                </th>
                <td mat-cell *matCellDef="let row">{{ row.prenom }}</td>
              </ng-container>

              <ng-container matColumnDef="sexe">
                <th mat-header-cell *matHeaderCellDef>
                  <div class="th-wrap" [class.open]="isFilterOpen('sexe')">
                    <div class="th-top"><span>{{ 'PATIENT_LIST.COL_SEXE' | translate }}</span>
                      <mat-icon class="filter-ind"
                                (click)="toggleFilterPanel('sexe', $event)"
                                [class.active]="isColumnFiltered('sexe')">{{ isColumnFiltered('sexe') ? 'filter_alt' : 'filter_alt_off' }}
                      </mat-icon>
                    </div>
                    <div class="th-filter">
                      <app-column-filter-renderer type="enum" labelKey="PATIENT_LIST.COL_SEXE"
                                                  [options]="sexeFilterOptions"
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
                  <div class="th-wrap" [class.open]="isFilterOpen('dateAdmission')">
                    <div class="th-top"><span>{{ 'PATIENT_LIST.COL_DATE_ADMISSION' | translate }}</span>
                      <mat-icon class="filter-ind"
                                (click)="toggleFilterPanel('dateAdmission', $event)"
                                [class.active]="isColumnFiltered('dateAdmission')">{{ isColumnFiltered('dateAdmission') ? 'filter_alt' : 'filter_alt_off' }}
                      </mat-icon>
                    </div>
                    <div class="th-filter">
                      <app-column-filter-renderer type="date" labelKey="PATIENT_LIST.COL_DATE_ADMISSION"
                                                  [value]="columnFilterValue('dateAdmission')"
                                                  (valueChange)="onColumnFilterValue('dateAdmission', $event)"
                                                  (clear)="clearColumnFilter('dateAdmission')"/>
                    </div>
                  </div>
                </th>
                <td mat-cell *matCellDef="let row">{{ row.dateAdmission }}</td>
              </ng-container>

              <ng-container matColumnDef="numeroAssurance">
                <th mat-header-cell *matHeaderCellDef>
                  <div class="th-wrap" [class.open]="isFilterOpen('numeroAssurance')">
                    <div class="th-top"><span>{{ 'PATIENT_LIST.COL_ASSURANCE' | translate }}</span>
                      <mat-icon class="filter-ind"
                                (click)="toggleFilterPanel('numeroAssurance', $event)"
                                [class.active]="isColumnFiltered('numeroAssurance')">{{ isColumnFiltered('numeroAssurance') ? 'filter_alt' : 'filter_alt_off' }}
                      </mat-icon>
                    </div>
                    <div class="th-filter">
                      <app-column-filter-renderer type="text" labelKey="PATIENT_LIST.COL_ASSURANCE"
                                                  [value]="columnFilterValue('numeroAssurance')"
                                                  (valueChange)="onColumnFilterValue('numeroAssurance', $event)"
                                                  (clear)="clearColumnFilter('numeroAssurance')"/>
                    </div>
                  </div>
                </th>
                <td mat-cell *matCellDef="let row"><span class="mono">{{ row.numeroAssurance }}</span></td>
              </ng-container>

              <ng-container matColumnDef="etatPatient">
                <th mat-header-cell *matHeaderCellDef>
                  <div class="th-wrap" [class.open]="isFilterOpen('etatPatient')">
                    <div class="th-top"><span>{{ 'PATIENT_LIST.COL_ETAT' | translate }}</span>
                      <mat-icon class="filter-ind"
                                (click)="toggleFilterPanel('etatPatient', $event)"
                                [class.active]="isColumnFiltered('etatPatient')">{{ isColumnFiltered('etatPatient') ? 'filter_alt' : 'filter_alt_off' }}
                      </mat-icon>
                    </div>
                    <div class="th-filter">
                      <app-column-filter-renderer type="enum" labelKey="PATIENT_LIST.COL_ETAT"
                                                  [options]="etatFilterOptions"
                                                  [value]="columnFilterValue('etatPatient')"
                                                  (valueChange)="onColumnFilterValue('etatPatient', $event)"
                                                  (clear)="clearColumnFilter('etatPatient')"/>
                    </div>
                  </div>
                </th>
                <td mat-cell *matCellDef="let row">
                  <div class="etat-cell">
                    <span class="etat-badge"
                          [attr.data-etat]="row.etatPatient"
                          [class.has-event-tooltip]="hasEventTooltip(row)"
                          [matTooltip]="eventDateTooltip(row)"
                          matTooltipClass="patient-theme-tooltip"
                          [matTooltipDisabled]="!hasEventTooltip(row)">{{ row.etatPatient }}</span>
                  </div>
                </td>
              </ng-container>

              <ng-container matColumnDef="nonFacturable">
                <th mat-header-cell *matHeaderCellDef>
                  <div class="th-wrap" [class.open]="isFilterOpen('nonFacturable')">
                    <div class="th-top"><span>Facturation</span>
                      <mat-icon class="filter-ind"
                                (click)="toggleFilterPanel('nonFacturable', $event)"
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

              <ng-container matColumnDef="pecStatus">
                <th mat-header-cell *matHeaderCellDef>
                  <div class="th-wrap" [class.open]="isFilterOpen('pecStatus')">
                    <div class="th-top"><span>{{ 'PATIENT_LIST.COL_PEC' | translate }}</span>
                      <mat-icon class="filter-ind"
                                (click)="toggleFilterPanel('pecStatus', $event)"
                                [class.active]="isColumnFiltered('pecStatus')">{{ isColumnFiltered('pecStatus') ? 'filter_alt' : 'filter_alt_off' }}
                      </mat-icon>
                    </div>
                    <div class="th-filter">
                      <app-column-filter-renderer type="enum" [options]="pecFilterOptions"
                                                  [value]="columnFilterValue('pecStatus')"
                                                  (valueChange)="onColumnFilterValue('pecStatus', $event)"
                                                  (clear)="clearColumnFilter('pecStatus')"/>
                    </div>
                  </div>
                </th>
                <td mat-cell *matCellDef="let row">
                  <span class="etat-badge" *ngIf="row.pecStatus">{{ row.pecStatus }}</span>
                  <span *ngIf="!row.pecStatus" style="color: #999;">-</span>
                </td>
              </ng-container>

              <ng-container matColumnDef="medecinTraitantId">
                <th mat-header-cell *matHeaderCellDef>
                  <div class="th-wrap" [class.open]="isFilterOpen('medecinTraitantId')">
                    <div class="th-top"><span>{{ 'PATIENT_FORM.MEDECIN_TRAITANT' | translate }}</span>
                      <mat-icon class="filter-ind"
                                (click)="toggleFilterPanel('medecinTraitantId', $event)"
                                [class.active]="isColumnFiltered('medecinTraitantId')">{{ isColumnFiltered('medecinTraitantId') ? 'filter_alt' : 'filter_alt_off' }}
                      </mat-icon>
                    </div>
                    <div class="th-filter">
                      <app-column-filter-renderer type="text" [value]="columnFilterValue('medecinTraitantId')"
                                                  (valueChange)="onColumnFilterValue('medecinTraitantId', $event)"
                                                  (clear)="clearColumnFilter('medecinTraitantId')"/>
                    </div>
                  </div>
                </th>
                <td mat-cell *matCellDef="let row">
                  <span *ngIf="row.medecinTraitantId">{{ row.medecinTraitantId }}</span>
                  <span *ngIf="!row.medecinTraitantId" style="color: #999;">-</span>
                </td>
              </ng-container>

              <ng-container matColumnDef="positionId">
                <th mat-header-cell *matHeaderCellDef>
                  <div class="th-wrap" [class.open]="isFilterOpen('positionId')">
                    <div class="th-top"><span>{{ 'PATIENT_FORM.POSITION' | translate }}</span>
                      <mat-icon class="filter-ind"
                                (click)="toggleFilterPanel('positionId', $event)"
                                [class.active]="isColumnFiltered('positionId')">{{ isColumnFiltered('positionId') ? 'filter_alt' : 'filter_alt_off' }}
                      </mat-icon>
                    </div>
                    <div class="th-filter">
                      <app-column-filter-renderer type="text" [value]="columnFilterValue('positionId')"
                                                  (valueChange)="onColumnFilterValue('positionId', $event)"
                                                  (clear)="clearColumnFilter('positionId')"/>
                    </div>
                  </div>
                </th>
                <td mat-cell *matCellDef="let row">
                  <span *ngIf="row.positionId">{{ row.positionId }}</span>
                  <span *ngIf="!row.positionId" style="color: #999;">-</span>
                </td>
              </ng-container>

              <ng-container matColumnDef="transporteurAllerId">
                <th mat-header-cell *matHeaderCellDef>
                  <div class="th-wrap" [class.open]="isFilterOpen('transporteurAllerId')">
                    <div class="th-top"><span>{{ 'PATIENT_FORM.TRANSPORTEUR_ALLER' | translate }}</span>
                      <mat-icon class="filter-ind"
                                (click)="toggleFilterPanel('transporteurAllerId', $event)"
                                [class.active]="isColumnFiltered('transporteurAllerId')">{{ isColumnFiltered('transporteurAllerId') ? 'filter_alt' : 'filter_alt_off' }}
                      </mat-icon>
                    </div>
                    <div class="th-filter">
                      <app-column-filter-renderer type="text" [value]="columnFilterValue('transporteurAllerId')"
                                                  (valueChange)="onColumnFilterValue('transporteurAllerId', $event)"
                                                  (clear)="clearColumnFilter('transporteurAllerId')"/>
                    </div>
                  </div>
                </th>
                <td mat-cell *matCellDef="let row">
                  <span *ngIf="row.transporteurAllerId">{{ row.transporteurAllerId }}</span>
                  <span *ngIf="!row.transporteurAllerId" style="color: #999;">-</span>
                </td>
              </ng-container>

              <ng-container matColumnDef="transporteurRetourId">
                <th mat-header-cell *matHeaderCellDef>
                  <div class="th-wrap" [class.open]="isFilterOpen('transporteurRetourId')">
                    <div class="th-top"><span>{{ 'PATIENT_FORM.TRANSPORTEUR_RETOUR' | translate }}</span>
                      <mat-icon class="filter-ind"
                                (click)="toggleFilterPanel('transporteurRetourId', $event)"
                                [class.active]="isColumnFiltered('transporteurRetourId')">{{ isColumnFiltered('transporteurRetourId') ? 'filter_alt' : 'filter_alt_off' }}
                      </mat-icon>
                    </div>
                    <div class="th-filter">
                      <app-column-filter-renderer type="text" [value]="columnFilterValue('transporteurRetourId')"
                                                  (valueChange)="onColumnFilterValue('transporteurRetourId', $event)"
                                                  (clear)="clearColumnFilter('transporteurRetourId')"/>
                    </div>
                  </div>
                </th>
                <td mat-cell *matCellDef="let row">
                  <span *ngIf="row.transporteurRetourId">{{ row.transporteurRetourId }}</span>
                  <span *ngIf="!row.transporteurRetourId" style="color: #999;">-</span>
                </td>
              </ng-container>

              <ng-container matColumnDef="joursDialyse">
                <th mat-header-cell *matHeaderCellDef>
                  <div class="th-wrap" [class.open]="isFilterOpen('joursDialyse')">
                    <div class="th-top"><span>{{ 'PATIENT_FORM.JOURS_DIALYSE' | translate }}</span>
                      <mat-icon class="filter-ind"
                                (click)="toggleFilterPanel('joursDialyse', $event)"
                                [class.active]="isColumnFiltered('joursDialyse')">{{ isColumnFiltered('joursDialyse') ? 'filter_alt' : 'filter_alt_off' }}
                      </mat-icon>
                    </div>
                    <div class="th-filter">
                      <app-column-filter-renderer type="text" [value]="columnFilterValue('joursDialyse')"
                                                  (valueChange)="onColumnFilterValue('joursDialyse', $event)"
                                                  (clear)="clearColumnFilter('joursDialyse')"/>
                    </div>
                  </div>
                </th>
                <td mat-cell *matCellDef="let row">
                   <span
                     *ngIf="row.joursDialyse && (row.joursDialyse.dimanche || row.joursDialyse.lundi || row.joursDialyse.mardi || row.joursDialyse.mercredi || row.joursDialyse.jeudi || row.joursDialyse.vendredi || row.joursDialyse.samedi)">
                     {{ (row.joursDialyse.dimanche ? 'Dim ' : '') + (row.joursDialyse.lundi ? 'Lun ' : '') + (row.joursDialyse.mardi ? 'Mar ' : '') + (row.joursDialyse.mercredi ? 'Mer ' : '') + (row.joursDialyse.jeudi ? 'Jeu ' : '') + (row.joursDialyse.vendredi ? 'Ven ' : '') + (row.joursDialyse.samedi ? 'Sam' : '') }}
                   </span>
                  <span
                    *ngIf="!row.joursDialyse || (!row.joursDialyse.dimanche && !row.joursDialyse.lundi && !row.joursDialyse.mardi && !row.joursDialyse.mercredi && !row.joursDialyse.jeudi && !row.joursDialyse.vendredi && !row.joursDialyse.samedi)"
                    style="color: #999;">-</span>
                </td>
              </ng-container>

              <ng-container matColumnDef="pecForfaitId">
                <th mat-header-cell *matHeaderCellDef>
                  <div class="th-wrap" [class.open]="isFilterOpen('pecForfaitId')">
                    <div class="th-top"><span>{{ 'PATIENT_LIST.COL_FORFAIT' | translate }}</span>
                      <mat-icon class="filter-ind"
                                (click)="toggleFilterPanel('pecForfaitId', $event)"
                                [class.active]="isColumnFiltered('pecForfaitId')">{{ isColumnFiltered('pecForfaitId') ? 'filter_alt' : 'filter_alt_off' }}
                      </mat-icon>
                    </div>
                    <div class="th-filter">
                      <app-column-filter-renderer type="text" [value]="columnFilterValue('pecForfaitId')"
                                                  (valueChange)="onColumnFilterValue('pecForfaitId', $event)"
                                                  (clear)="clearColumnFilter('pecForfaitId')"/>
                    </div>
                  </div>
                </th>
                <td mat-cell *matCellDef="let row">
                  <span *ngIf="row.pecForfaitId">{{ row.pecForfaitId }}</span>
                  <span *ngIf="!row.pecForfaitId" style="color: #999;">-</span>
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
              <tr mat-row *matRowDef="let row; columns: displayedColumns();" class="patient-row"
                  [attr.data-row-id]="row.id"></tr>
              <tr class="mat-mdc-row" *matNoDataRow>
                <td class="mat-mdc-cell no-data-cell" [attr.colspan]="displayedColumns().length">
                  {{ 'PATIENT_LIST.EMPTY' | translate }}
                </td>
              </tr>
            </table>
        </div>
        <mat-paginator [length]="total()" [pageIndex]="pageIndex()" [pageSize]="pageSize()"
                       [pageSizeOptions]="[5,10,20,50]" (page)="onPageChange($event)"></mat-paginator>
      </mat-card-content>
    </mat-card>
  `,
  styles: [`
    .list-card {
      padding: 14px;
    }

    :host ::ng-deep .list-card .mat-mdc-card-header {
      display: flex;
      justify-content: flex-start;
      align-items: center;
      margin-bottom: 10px;
      padding: 8px;
    }

    :host ::ng-deep .list-card .mat-mdc-card-header-text {
      margin: 0;
    }

    .header-copy {
      min-width: 0;
      text-align: left;
    }

    .list-toolbar {
      display: flex;
      justify-content: space-between;
      gap: 12px;
      margin-bottom: 16px;
      flex-wrap: wrap;
    }

    .toolbar-group {
      display: flex;
      gap: 10px;
      flex-wrap: wrap;
      align-items: center;
    }

    .toolbar-group-end {
      margin-left: auto;
    }

    .btn-new {
      min-height: 46px;
      padding-inline: 18px;
    }

    .table-container {
      overflow-x: auto;
      border-radius: 20px;
      border: 1px solid var(--app-border);
      background: var(--app-surface-solid);
      position: relative;
      isolation: isolate;
    }

    /* Prevent clipping when a filter panel is open */
    .table-container:has(.th-wrap.open) {
      overflow: visible;
    }

    .patient-table {
      width: 100%;
    }

    .patient-row:hover {
      background: var(--app-row-hover) !important;
    }

    th.mat-mdc-header-cell {
      font-weight: 700;
      color: var(--app-primary);
      font-size: 12px;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      overflow: visible !important;
      position: relative;
      z-index: 5;
    }

    th.mat-mdc-header-cell:has(.filter-ind:hover),
    th.mat-mdc-header-cell:has(.th-filter:hover),
    th.mat-mdc-header-cell:has(.filter-ind.active),
    th.mat-mdc-header-cell:has(.th-wrap.open),
    th.mat-mdc-header-cell:focus-within {
      z-index: 2100;
      overflow: visible !important;
      position: relative;
    }

    /* Keep header row above data rows while interacting with filters */
    :host ::ng-deep .patient-table .mat-mdc-header-row {
      position: relative;
      z-index: 20;
    }

    :host ::ng-deep .patient-table .mat-mdc-row {
      position: relative;
      z-index: 1;
    }

    .patient-table,
    .patient-table .mat-mdc-header-row,
    .patient-table .mat-mdc-row,
    .patient-table .mat-mdc-cell,
    .patient-table .mat-mdc-header-cell {
      overflow: visible;
    }

    .code-chip {
      font-family: 'Manrope', sans-serif;
      font-size: 12px;
      padding: 4px 10px;
      background: rgba(97, 216, 223, 0.12);
      border: 1px solid var(--app-primary-outline);
      border-radius: 999px;
      color: var(--app-primary);
      font-weight: 700;
    }

    .mono {
      font-family: 'Manrope', sans-serif;
      font-size: 12px;
      color: color-mix(in srgb, var(--app-text) 88%, var(--app-primary));
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
      padding: 4px 11px;
      border-radius: 20px;
      font-size: 11px;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.3px;
      background: rgba(97, 216, 223, 0.12);
      color: var(--app-primary);
      border: 1px solid var(--app-primary-outline);
    }

    .etat-cell {
      display: inline-flex;
      flex-direction: column;
      align-items: flex-start;
      gap: 4px;
    }

    .etat-badge.has-event-tooltip {
      cursor: help;
    }

    .etat-event-date {
      font-size: 11px;
      line-height: 1.3;
      color: var(--app-muted);
      white-space: nowrap;
    }

    .etat-badge[data-etat="DECEDE"] {
      background: rgba(239, 68, 68, 0.14);
      color: #ff9b9b;
      border-color: rgba(239, 68, 68, 0.22);
    }

    .etat-badge[data-etat="TRANSFERE"] {
      background: rgba(245, 158, 11, 0.14);
      color: #ffcc87;
      border-color: rgba(245, 158, 11, 0.22);
    }

    .etat-badge[data-etat="GREFFE"] {
      background: rgba(59, 130, 246, 0.14);
      color: #9ec4ff;
      border-color: rgba(59, 130, 246, 0.22);
    }

    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 48px 24px;
      color: var(--app-muted);
      border-radius: 22px;
      border: 1px dashed var(--app-border-strong);
      background: var(--app-frost);
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

      .no-data-cell {
        text-align: center;
        padding: 16px;
        color: var(--app-muted);
        font-weight: 600;
      }
      position: relative;
      overflow: visible;
      z-index: 6;
    }

    .th-wrap.open {
      z-index: 2101;
    }

    .th-top {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 6px;
    }

    .th-filter {
      display: none;
      align-items: center;
      gap: 6px;
      padding: 8px;
      position: absolute;
      top: calc(100% + 4px);
      left: 0;
      min-width: 240px;
      width: max-content;
      max-width: 360px;
      z-index: 2200;
      border-radius: 16px;
      box-shadow: var(--app-shadow-soft);
      background: var(--app-filter-panel-bg);
      border: 1px solid var(--app-border-strong);
      backdrop-filter: blur(18px);
    }

    .th-wrap.open .th-filter {
      display: flex;
    }

    .filter-ind {
      font-size: 17px;
      width: 17px;
      height: 17px;
      color: #94a3b8;
      cursor: pointer;
    }

    .filter-ind.active {
      color: #dc2626;
    }

    .th-filter :where(app-column-filter-renderer) { width: 100%; }

    @media (max-width: 960px) {
      :host ::ng-deep .list-card .mat-mdc-card-header {
        grid-template-columns: 1fr;
      }
      .toolbar-group-end {
        margin-left: 0;
        justify-content: flex-start;
      }
    }
  `]
})
export class PatientListComponent implements OnInit {
  @Output() newPatient = new EventEmitter<void>();
  @Output() selectPatient = new EventEmitter<PatientRow>();
  readonly hasActiveFilters = computed(() =>
    Object.values(this.columnFilters()).some(v => !!v?.toString().trim())
  );

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
    {key: 'pecStatus', labelKey: 'PATIENT_LIST.COL_PEC', type: 'text' as FilterType},
    {key: 'medecinTraitantId', labelKey: 'PATIENT_FORM.MEDECIN_TRAITANT', type: 'text' as FilterType},
    {key: 'positionId', labelKey: 'PATIENT_FORM.POSITION', type: 'text' as FilterType},
    {key: 'transporteurAllerId', labelKey: 'PATIENT_FORM.TRANSPORTEUR_ALLER', type: 'text' as FilterType},
    {key: 'transporteurRetourId', labelKey: 'PATIENT_FORM.TRANSPORTEUR_RETOUR', type: 'text' as FilterType},
    {key: 'joursDialyse', labelKey: 'PATIENT_FORM.JOURS_DIALYSE', type: 'text' as FilterType},
    {key: 'pecForfaitId', labelKey: 'PATIENT_LIST.COL_FORFAIT', type: 'text' as FilterType},
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
    pecStatus: false,
    medecinTraitantId: false,
    positionId: false,
    transporteurAllerId: false,
    transporteurRetourId: false,
    joursDialyse: false,
    pecForfaitId: false,
    actions: true
  });
  private readonly snack = inject(MatSnackBar);
  readonly displayedColumns = computed(() => this.allColumnsConfig.filter(c => this.visibleColumns()[c.key]).map(c => c.key));
  readonly rows = signal<PatientRow[]>([]);
  readonly total = signal(0);
  readonly pageIndex = signal(0);
  readonly pageSize = signal(10);
  readonly columnFilters = signal<Record<string, string>>({});
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

  readonly sexeFilterOptions = [
    {value: 'M', label: 'Masculin'},
    {value: 'F', label: 'Féminin'}
  ];

  readonly pecFilterOptions = [
    {value: 'CREE', label: 'Créée'},
    {value: 'VALIDEE', label: 'Validée'},
    {value: 'CLOTUREE', label: 'Clôturée'}
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
  private readonly openFilterColumn = signal<string | null>(null);

  toggleFilterPanel(column: string, event: MouseEvent): void {
    event.stopPropagation();
    this.openFilterColumn.update((current) => (current === column ? null : column));
  }

  isFilterOpen(column: string): boolean {
    return this.openFilterColumn() === column;
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    const target = event.target as HTMLElement | null;
    if (!target) {
      this.openFilterColumn.set(null);
      return;
    }
    if (target.closest('.th-wrap')) {
      return;
    }
    this.openFilterColumn.set(null);
  }

  clearAllColumnFilters(): void {
    this.openFilterColumn.set(null);
    this.columnFilters.set({});
    this.fetchPage(0, this.pageSize());
  }

  toggleColumn(column: string, checked: boolean): void {
    this.visibleColumns.update(prev => ({...prev, [column]: checked}));
  }

  isColumnVisible(column: string): boolean {
    return this.visibleColumns()[column] ?? false;
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.fetchPage(event.pageIndex, event.pageSize);
  }

  eventDateTooltip(row: PatientRow): string {
    if (!this.hasEventTooltip(row)) return '';
    const formattedDate = this.formatEventDate(row.dateEvenementEtat ?? '');
    return formattedDate ? `Date de l'evenement: ${formattedDate}` : "Date de l'evenement non renseignee";
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

  hasEventTooltip(row: PatientRow): boolean {
    const etat = (row.etatPatient ?? '').toUpperCase();
    return etat === 'TRANSFERE' || etat === 'GREFFE' || etat === 'DECEDE';
  }

  visibleEventDate(row: PatientRow): string {
    const formattedDate = this.formatEventDate(row.dateEvenementEtat ?? '');
    return formattedDate ? `Événement: ${formattedDate}` : 'Événement non renseigné';
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
      filters: this.columnFilters()
    }).subscribe({
      next: (res) => {
        const mapped = (res.items ?? []).map((p: any) => ({
          id: p.id,
          code: p.codePatient ?? '',
          nom: p.nom ?? '',
          prenom: p.prenom ?? '',
          sexe: p.sexe ?? '',
          dateAdmission: p.dateAdmission ?? '',
          numeroAssurance: p.numeroAssurance ?? '',
          etatPatient: p.etatPatient ?? 'PERMANENT',
          dateEvenementEtat: p.dateEvenementEtat ?? p.dateEvenement ?? '',
          nonFacturable: !!p.nonFacturable,
          medecinTraitantId: p.medecinTraitantId ?? '',
          positionId: p.positionId ?? '',
          transporteurAllerId: p.transporteurAllerId ?? '',
          transporteurRetourId: p.transporteurRetourId ?? '',
          joursDialyse: {
            dimanche: p.jourDimanche ?? false,
            lundi: p.jourLundi ?? false,
            mardi: p.jourMardi ?? false,
            mercredi: p.jourMercredi ?? false,
            jeudi: p.jourJeudi ?? false,
            vendredi: p.jourVendredi ?? false,
            samedi: p.jourSamedi ?? false
          },
          pecStatus: p.pecStatus ?? '',
          pecForfaitId: p.pecForfaitId ?? ''
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

  private formatEventDate(rawDate: string): string {
    const raw = (rawDate ?? '').trim();
    if (!raw) return '';
    const match = raw.match(/^(\d{4})-(\d{2})-(\d{2})/);
    if (!match) return raw;
    return `${match[3]}/${match[2]}/${match[1]}`;
  }

  onColumnFilterValue(column: string, value: string): void {
    this.columnFilters.update(prev => ({...prev, [column]: value}));
    this.fetchPage(0, this.pageSize());
  }
}

