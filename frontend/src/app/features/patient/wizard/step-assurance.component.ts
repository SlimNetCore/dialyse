import {
  Component,
  computed,
  effect,
  EventEmitter,
  inject,
  Input,
  OnChanges,
  OnInit,
  Output,
  signal,
  SimpleChanges
} from '@angular/core';
import {FormBuilder, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatDatepickerModule} from '@angular/material/datepicker';
import {MatNativeDateModule} from '@angular/material/core';
import {MatIconModule} from '@angular/material/icon';
import {MatDividerModule} from '@angular/material/divider';
import {MatButtonModule} from '@angular/material/button';
import {MatSnackBar} from '@angular/material/snack-bar';
import {MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef} from '@angular/material/dialog';
import {TranslateModule} from '@ngx-translate/core';
import {DropdownItem, SearchableSelectComponent} from '../../../shared/searchable-select.component';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {CentresPayeursDetailsStore, CentresPayeursStore} from '../../../core/state/referentials.store';
import {PatientFicheStore} from '../state/patient-fiche.store';
import {consumeWizardActionStatus} from './wizard-action-status.util';

interface AssignmentEdit {
  id: string;
  dateDebutAffectation: Date | null;
  dateFinAffectation: Date | null;
}

// ─────────────────────────────────────────────────────────────────────────────
// Dialog : modification des informations d'un assuré
// ─────────────────────────────────────────────────────────────────────────────
@Component({
  selector: 'app-assure-edit-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatDatepickerModule, MatNativeDateModule, MatIconModule, MatButtonModule, MatDialogModule
  ],
  template: `
    <h2 mat-dialog-title style="display:flex;align-items:center;gap:8px">
      <mat-icon>edit</mat-icon>
      Modifier l'assuré
    </h2>
    <mat-dialog-content style="min-width:520px;padding-top:8px">
      <form [formGroup]="form">
        <mat-form-field appearance="outline" style="width:100%;margin-bottom:4px">
          <mat-label>N° Assurance</mat-label>
          <mat-icon matPrefix>badge</mat-icon>
          <input matInput [value]="data.numeroAssurance" disabled/>
        </mat-form-field>
        <div style="display:flex;gap:10px;margin-bottom:4px">
          <mat-form-field appearance="outline" style="flex:1">
            <mat-label>Nom *</mat-label>
            <mat-icon matPrefix>person</mat-icon>
            <input matInput formControlName="nom"/>
            @if (form.get('nom')?.hasError('required') && form.get('nom')?.touched) {
              <mat-error>Obligatoire</mat-error>
            }
          </mat-form-field>
          <mat-form-field appearance="outline" style="flex:1">
            <mat-label>Prénom *</mat-label>
            <mat-icon matPrefix>person_outline</mat-icon>
            <input matInput formControlName="prenom"/>
            @if (form.get('prenom')?.hasError('required') && form.get('prenom')?.touched) {
              <mat-error>Obligatoire</mat-error>
            }
          </mat-form-field>
          <mat-form-field appearance="outline" style="flex:1">
            <mat-label>Sexe</mat-label>
            <mat-icon matPrefix>wc</mat-icon>
            <mat-select formControlName="sexe">
              <mat-option value="">—</mat-option>
              <mat-option value="M">Masculin</mat-option>
              <mat-option value="F">Féminin</mat-option>
            </mat-select>
          </mat-form-field>
        </div>
        <div style="display:flex;gap:10px;margin-bottom:4px">
          <mat-form-field appearance="outline" style="flex:1">
            <mat-label>Date de naissance</mat-label>
            <mat-icon matPrefix>cake</mat-icon>
            <input matInput [matDatepicker]="dpDN" formControlName="dateNaissance"/>
            <mat-datepicker-toggle matSuffix [for]="dpDN"/>
            <mat-datepicker #dpDN/>
          </mat-form-field>
          <mat-form-field appearance="outline" style="flex:1">
            <mat-label>Groupe sanguin</mat-label>
            <mat-icon matPrefix>bloodtype</mat-icon>
            <mat-select formControlName="groupeSanguin">
              <mat-option value="">—</mat-option>
              <mat-option value="A+">A+</mat-option>
              <mat-option value="A-">A-</mat-option>
              <mat-option value="B+">B+</mat-option>
              <mat-option value="B-">B-</mat-option>
              <mat-option value="O+">O+</mat-option>
              <mat-option value="O-">O-</mat-option>
              <mat-option value="AB+">AB+</mat-option>
              <mat-option value="AB-">AB-</mat-option>
            </mat-select>
          </mat-form-field>
        </div>
        <div style="display:flex;gap:10px;margin-bottom:4px">
          <mat-form-field appearance="outline" style="flex:1">
            <mat-label>Tél. personnel</mat-label>
            <mat-icon matPrefix>phone</mat-icon>
            <input matInput formControlName="telPersonnel"/>
          </mat-form-field>
          <mat-form-field appearance="outline" style="flex:1">
            <mat-label>Tél. mobile</mat-label>
            <mat-icon matPrefix>phone_iphone</mat-icon>
            <input matInput formControlName="telMobile"/>
          </mat-form-field>
          <mat-form-field appearance="outline" style="flex:1">
            <mat-label>Tél. bureau</mat-label>
            <mat-icon matPrefix>phone_in_talk</mat-icon>
            <input matInput formControlName="telBureau"/>
          </mat-form-field>
        </div>
        <mat-form-field appearance="outline" style="width:100%">
          <mat-label>Adresse</mat-label>
          <mat-icon matPrefix>home</mat-icon>
          <input matInput formControlName="adresse"/>
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end" style="gap:8px;padding:12px">
      <button mat-stroked-button type="button" (click)="cancel()">
        <mat-icon>close</mat-icon>
        Annuler
      </button>
      <button mat-flat-button color="primary" type="button" (click)="save()" [disabled]="form.invalid">
        <mat-icon>save</mat-icon>
        Enregistrer
      </button>
    </mat-dialog-actions>
  `
})
export class AssureEditDialogComponent {
  readonly dialogRef = inject(MatDialogRef<AssureEditDialogComponent>);
  readonly data = inject<any>(MAT_DIALOG_DATA);
  private readonly fb = inject(FormBuilder);

  form: FormGroup = this.fb.group({
    nom: [this.data.nom ?? '', Validators.required],
    prenom: [this.data.prenom ?? '', Validators.required],
    sexe: [this.data.sexe ?? ''],
    dateNaissance: [this.data.dateNaissance ? new Date(this.data.dateNaissance) : null],
    groupeSanguin: [this.data.groupeSanguin ?? ''],
    telPersonnel: [this.data.telPersonnel ?? ''],
    telMobile: [this.data.telMobile ?? ''],
    telBureau: [this.data.telBureau ?? ''],
    adresse: [this.data.adresse ?? '']
  });

  cancel(): void {
    this.dialogRef.close(null);
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const val = this.form.getRawValue();
    const dn = val.dateNaissance;
    this.dialogRef.close({
      ...val,
      dateNaissance: dn instanceof Date ? dn.toISOString().slice(0, 10) : (dn ?? null)
    });
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Composant principal : étape assurance
// ─────────────────────────────────────────────────────────────────────────────
@Component({
  selector: 'app-step-assurance',
  standalone: true,
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatDatepickerModule, MatNativeDateModule, MatIconModule, MatDividerModule,
    MatButtonModule, MatDialogModule, TranslateModule, SearchableSelectComponent],
  template: `
    <div class="step-content">
      <h3 class="section-title">{{ 'PATIENT_FORM.SECTION_INSURANCE' | translate }}</h3>
      <form [formGroup]="form">

        <!-- N° assurance patient -->
        <div class="form-row">
          <mat-form-field appearance="outline" class="flex1">
            <mat-label>{{ 'PATIENT_FORM.NUMERO_ASSURANCE' | translate }} *</mat-label>
            <mat-icon matPrefix>badge</mat-icon>
            <input matInput formControlName="numeroAssurance" />
            @if (form.get('numeroAssurance')?.hasError('required') && form.get('numeroAssurance')?.touched) {
              <mat-error>{{ 'PATIENT_FORM.REQUIRED' | translate }}</mat-error>
            }
          </mat-form-field>
        </div>

        <!-- Centre payeur -->
        <div class="form-row">
          <app-searchable-select
            [items]="centresPayeurs()" [label]="'PATIENT_FORM.CENTRE_PAYEUR' | translate" [prefixIcon]="'account_balance'"
            [selectedId]="form.get('centrePayeurId')?.value" (selectionChanged)="onCentrePayeur($event)"
            [disabled]="readonly" cssClass="flex1"/>
          <mat-form-field appearance="outline" class="flex1">
            <mat-label>{{ 'PATIENT_FORM.CODE_CENTRE_PAYEUR' | translate }}</mat-label>
            <mat-icon matPrefix>pin</mat-icon>
            <input matInput [value]="codeCentrePayeur()" disabled />
          </mat-form-field>
        </div>
        <div class="form-row">
          <mat-form-field appearance="outline" class="flex1">
            <mat-label>{{ 'PATIENT_FORM.CODE_AGENCE' | translate }}</mat-label>
            <mat-icon matPrefix>domain</mat-icon>
            <input matInput [value]="codeAgence()" disabled />
          </mat-form-field>
          <mat-form-field appearance="outline" class="flex1">
            <mat-label>{{ 'PATIENT_FORM.AGENCE' | translate }}</mat-label>
            <mat-icon matPrefix>business</mat-icon>
            <input matInput [value]="libelleAgence()" disabled />
          </mat-form-field>
          <mat-form-field appearance="outline" class="flex1">
            <mat-label>{{ 'PATIENT_FORM.CAISSE' | translate }}</mat-label>
            <mat-icon matPrefix>account_balance_wallet</mat-icon>
            <input matInput [value]="libelleCaisse()" disabled />
          </mat-form-field>
        </div>

        <mat-divider style="margin: 16px 0;" />

        <!-- Section assuré principal actif -->
        <h3 class="section-title">{{ 'PATIENT_FORM.SECTION_ASSURE' | translate }}</h3>
        <div class="form-row">
          <mat-form-field appearance="outline" class="flex1">
            <mat-label>N° Assurance assuré @if (requiresAssureNumero()) {
              *
            }</mat-label>
            <mat-icon matPrefix>badge</mat-icon>
            <input matInput formControlName="assureNumeroAssurance"/>
            @if (form.get('assureNumeroAssurance')?.hasError('required') && form.get('assureNumeroAssurance')?.touched) {
              <mat-error>{{ 'PATIENT_FORM.REQUIRED' | translate }}</mat-error>
            }
          </mat-form-field>
        </div>
        <div class="form-row">
          <mat-form-field appearance="outline" class="flex1">
            <mat-label>{{ 'PATIENT_FORM.ASSURE_NOM' | translate }} *</mat-label>
            <mat-icon matPrefix>person</mat-icon>
            <input matInput formControlName="assureNom"/>
            @if (form.get('assureNom')?.hasError('required') && form.get('assureNom')?.touched) {
              <mat-error>{{ 'PATIENT_FORM.REQUIRED' | translate }}</mat-error>
            }
          </mat-form-field>
          <mat-form-field appearance="outline" class="flex1">
            <mat-label>{{ 'PATIENT_FORM.ASSURE_PRENOM' | translate }} *</mat-label>
            <mat-icon matPrefix>person_outline</mat-icon>
            <input matInput formControlName="assurePrenom"/>
            @if (form.get('assurePrenom')?.hasError('required') && form.get('assurePrenom')?.touched) {
              <mat-error>{{ 'PATIENT_FORM.REQUIRED' | translate }}</mat-error>
            }
          </mat-form-field>
          <mat-form-field appearance="outline" class="flex1">
            <mat-label>{{ 'PATIENT_FORM.ASSURE_SEXE' | translate }}</mat-label>
            <mat-icon matPrefix>wc</mat-icon>
            <mat-select formControlName="assureSexe">
              <mat-option value="M">{{ 'PATIENT_FORM.MASCULIN' | translate }}</mat-option>
              <mat-option value="F">{{ 'PATIENT_FORM.FEMININ' | translate }}</mat-option>
            </mat-select>
          </mat-form-field>
        </div>
        <div class="form-row">
          <mat-form-field appearance="outline" class="flex1">
            <mat-label>{{ 'PATIENT_FORM.ASSURE_DATE_NAISSANCE' | translate }}</mat-label>
            <mat-icon matPrefix>cake</mat-icon>
            <input matInput [matDatepicker]="dpAssure" formControlName="assureDateNaissance"/>
            <mat-datepicker-toggle matSuffix [for]="dpAssure"/>
            <mat-datepicker #dpAssure/>
          </mat-form-field>
          <mat-form-field appearance="outline" class="flex1">
            <mat-label>{{ 'PATIENT_FORM.ASSURE_TEL_PERSONNEL' | translate }}</mat-label>
            <mat-icon matPrefix>phone</mat-icon>
            <input matInput formControlName="assureTelPersonnel"/>
          </mat-form-field>
          <mat-form-field appearance="outline" class="flex1">
            <mat-label>{{ 'PATIENT_FORM.ASSURE_TEL_MOBILE' | translate }}</mat-label>
            <mat-icon matPrefix>phone_iphone</mat-icon>
            <input matInput formControlName="assureTelMobile"/>
          </mat-form-field>
          <mat-form-field appearance="outline" class="flex1">
            <mat-label>{{ 'PATIENT_FORM.ASSURE_TEL_BUREAU' | translate }}</mat-label>
            <mat-icon matPrefix>phone_in_talk</mat-icon>
            <input matInput formControlName="assureTelBureau"/>
          </mat-form-field>
        </div>
        <div class="form-row">
          <mat-form-field appearance="outline" class="flex1">
            <mat-label>{{ 'PATIENT_FORM.ASSURE_GROUPE_SANGUIN' | translate }}</mat-label>
            <mat-icon matPrefix>bloodtype</mat-icon>
            <mat-select formControlName="assureGroupeSanguin">
              <mat-option value="">—</mat-option>
              <mat-option value="A+">A+</mat-option>
              <mat-option value="A-">A-</mat-option>
              <mat-option value="B+">B+</mat-option>
              <mat-option value="B-">B-</mat-option>
              <mat-option value="O+">O+</mat-option>
              <mat-option value="O-">O-</mat-option>
              <mat-option value="AB+">AB+</mat-option>
              <mat-option value="AB-">AB-</mat-option>
            </mat-select>
          </mat-form-field>
        </div>
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>{{ 'PATIENT_FORM.ASSURE_ADRESSE' | translate }}</mat-label>
          <mat-icon matPrefix>home</mat-icon>
          <input matInput formControlName="assureAdresse"/>
        </mat-form-field>

        <!-- Boutons d'action -->
        <div class="form-row" style="justify-content:flex-end; gap:8px; margin-top: 8px;">
          <button mat-stroked-button type="button" (click)="toggleAssureCatalog()"
                  [disabled]="readonly || !canAssignAssure()">
            <mat-icon>manage_search</mat-icon>
            Consulter les assurés
          </button>
          <button mat-stroked-button type="button" (click)="toggleAssureHistory()" [disabled]="!patientId">
            <mat-icon>history</mat-icon>
            Historique des affectations
          </button>
          <button mat-stroked-button type="button" (click)="prepareNewAssure()" [disabled]="readonly">
            <mat-icon>person_add</mat-icon>
            {{ 'WIZARD.ADD_NEW_INSURED' | translate }}
          </button>
        </div>

        <!-- ── Catalogue des assurés ── -->
        @if (showCatalog()) {
          <div class="history-box" style="margin-top:12px;">
            <div class="history-title">Catalogue des assurés</div>
            <div class="form-row" style="margin-bottom:8px; align-items:center;">
              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Rechercher (N° assurance / nom / prénom)</mat-label>
                <mat-icon matPrefix>search</mat-icon>
                <input matInput [value]="assureSearch()" (input)="assureSearch.set($any($event.target).value)"
                       (keyup.enter)="searchAssures()"/>
              </mat-form-field>
              <button mat-flat-button type="button" (click)="searchAssures()" [disabled]="loadingAssures()">
                <mat-icon>search</mat-icon>
                Rechercher
              </button>
            </div>

            @for (a of assureCatalog(); track a.numeroAssurance) {
              <div class="assure-row" [class.assure-row-primary]="a.isPrimary">
                <!-- Informations assuré -->
                <div class="assure-info">
                  <div class="assure-name">
                    {{ a.nom }} {{ a.prenom }}
                    @if (a.isPrimary) {
                      <span class="primary-chip"><mat-icon
                        style="font-size:12px;height:12px;width:12px;margin-right:2px">star</mat-icon>Primaire</span>
                    }
                  </div>
                  <div class="muted">{{ a.numeroAssurance }} • {{ a.sexe || '—' }}</div>
                </div>
                <!-- Actions -->
                <div class="assure-actions">
                  <button mat-stroked-button type="button" (click)="openEditAssureDialog(a)"
                          title="Modifier les informations de l'assuré">
                    <mat-icon>edit</mat-icon>
                    Modifier
                  </button>
                  <button mat-flat-button color="primary" type="button"
                          (click)="affectAssure(a)"
                          [disabled]="readonly || !canAssignAssure() || a.isPrimary">
                    <mat-icon>person_add_alt_1</mat-icon>
                    {{ a.isPrimary ? 'Déjà primaire' : 'Affecter' }}
                  </button>
                </div>
              </div>
            }
            @if (assureCatalog().length === 0 && !loadingAssures()) {
              <div style="text-align:center;color:#9ca3af;padding:16px;font-size:13px;">
                Aucun assuré trouvé. Lancez une recherche.
              </div>
            }
          </div>
        }

        <!-- ── Historique des affectations ── -->
        @if (showHistory()) {
          <div class="history-box" style="margin-top:12px;">
            <div class="history-title">Historique des affectations</div>
            <table class="history-table">
              <thead>
              <tr>
                <th>Assuré</th>
                <th>N° Assurance</th>
                <th>Date début</th>
                <th>Date fin</th>
                <th>Statut</th>
                <th style="text-align:right">Actions</th>
              </tr>
              </thead>
              <tbody>
                @for (h of assureAssignments(); track h.id) {
                  <tr [class.row-active]="h.actif">
                    <td><strong>{{ h.nom || '—' }} {{ h.prenom || '' }}</strong></td>
                    <td>{{ h.numeroAssurance }}</td>
                    <td>{{ h.dateDebutAffectation || '—' }}</td>
                    <td>{{ h.dateFinAffectation || '—' }}</td>
                    <td>
                      @if (h.actif) {
                        <span class="badge-actif">Actif</span>
                      } @else {
                        <span class="badge-inactif">Inactif</span>
                      }
                    </td>
                    <td style="text-align:right">
                      <button mat-icon-button type="button" (click)="openEditAssignment(h)"
                              [disabled]="readonly" title="Modifier les dates">
                        <mat-icon>edit_calendar</mat-icon>
                      </button>
                    </td>
                  </tr>
                  @if (editingAssignment()?.id === h.id) {
                    <tr class="edit-row">
                      <td colspan="6">
                        <div class="edit-inline">
                          <mat-form-field appearance="outline" class="edit-field">
                            <mat-label>Date début</mat-label>
                            <mat-icon matPrefix>event</mat-icon>
                            <input matInput [matDatepicker]="dpEditDebut"
                                   [value]="editingAssignment()!.dateDebutAffectation"
                                   (dateChange)="setEditDateDebut($event.value)"/>
                            <mat-datepicker-toggle matSuffix [for]="dpEditDebut"/>
                            <mat-datepicker #dpEditDebut/>
                          </mat-form-field>
                          <mat-form-field appearance="outline" class="edit-field">
                            <mat-label>Date fin</mat-label>
                            <mat-icon matPrefix>event_busy</mat-icon>
                            <input matInput [matDatepicker]="dpEditFin"
                                   [value]="editingAssignment()!.dateFinAffectation"
                                   (dateChange)="setEditDateFin($event.value)"/>
                            <mat-datepicker-toggle matSuffix [for]="dpEditFin"/>
                            <mat-datepicker #dpEditFin/>
                          </mat-form-field>
                          <button mat-flat-button color="primary" type="button"
                                  (click)="saveEditAssignment()" [disabled]="savingEdit()">
                            <mat-icon>check</mat-icon>
                            Valider
                          </button>
                          <button mat-stroked-button type="button" (click)="cancelEdit()">
                            <mat-icon>close</mat-icon>
                            Annuler
                          </button>
                        </div>
                      </td>
                    </tr>
                  }
                }
                @if (assureAssignments().length === 0) {
                  <tr>
                    <td colspan="6" style="text-align:center;color:#9ca3af;padding:12px;">Aucun historique</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }

      </form>
    </div>
  `,
  styles: [`
    .step-content { padding: 14px 18px 18px; }
    .section-title { color: var(--app-text); font-size: 1rem; font-weight: 600; margin: 0 0 12px; }
    .form-row { display: flex; gap: 12px; margin-bottom: 8px; }
    .flex1 { flex: 1; }
    .full-width { width: 100%; }
    :host ::ng-deep .mat-mdc-form-field { font-size: 13px; }
    :host ::ng-deep .mat-mdc-form-field-subscript-wrapper { display: none; }
    :host ::ng-deep input.mat-mdc-input-element { text-align: center; }
    :host ::ng-deep .mat-mdc-select-value { text-align: center; }

    .history-box {
      border: 1px solid var(--app-border);
      border-radius: 10px;
      padding: 8px 12px;
      background: var(--app-surface);
    }

    .history-title {
      font-weight: 600;
      color: var(--app-primary);
      margin-bottom: 8px;
    }

    /* Catalogue */
    .assure-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 10px;
      border: 1px solid var(--app-border);
      border-radius: 8px;
      padding: 10px 12px;
      margin-bottom: 6px;
      background: var(--app-surface);
      transition: border-color 0.15s, background 0.15s;
    }

    .assure-row-primary {
      border-color: var(--app-primary, #3b82f6) !important;
      background: color-mix(in srgb, var(--app-primary, #3b82f6) 6%, transparent) !important;
    }

    .assure-info {
      flex: 1;
      min-width: 0;
    }

    .assure-name {
      font-weight: 600;
      font-size: 13px;
      display: flex;
      align-items: center;
      gap: 6px;
      flex-wrap: wrap;
    }

    .assure-actions {
      display: flex;
      gap: 8px;
      align-items: center;
      flex-shrink: 0;
    }

    .primary-chip {
      display: inline-flex;
      align-items: center;
      padding: 1px 8px;
      border-radius: 999px;
      background: var(--app-primary-soft, #eff6ff);
      color: var(--app-primary, #3b82f6);
      border: 1px solid var(--app-primary-outline, #bfdbfe);
      font-size: 11px;
      font-weight: 700;
    }

    .muted {
      font-size: 12px;
      color: #6b7280;
      margin-top: 2px;
    }

    /* Historique */
    .history-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 13px;
    }

    .history-table thead tr {
      background: var(--app-bg, #f3f4f6);
    }

    .history-table th {
      padding: 6px 10px;
      text-align: left;
      font-weight: 600;
      color: #374151;
      border-bottom: 1px solid var(--app-border);
    }

    .history-table td {
      padding: 6px 10px;
      border-bottom: 1px solid var(--app-border, #e5e7eb);
      color: #374151;
    }

    .history-table tr.row-active td {
      background: rgba(59, 130, 246, 0.04);
    }

    .edit-row td {
      background: #f9fafb;
      padding: 8px 10px;
    }

    .edit-inline {
      display: flex;
      align-items: center;
      gap: 10px;
      flex-wrap: wrap;
    }

    .edit-field {
      min-width: 160px;
      flex: 1;
    }

    .badge-actif {
      padding: 2px 10px;
      border-radius: 999px;
      background: #d1fae5;
      color: #065f46;
      font-size: 11px;
      font-weight: 700;
      border: 1px solid #6ee7b7;
    }

    .badge-inactif {
      padding: 2px 10px;
      border-radius: 999px;
      background: #f3f4f6;
      color: #6b7280;
      font-size: 11px;
      font-weight: 600;
      border: 1px solid #d1d5db;
    }
  `]
})
export class StepAssuranceComponent implements OnInit, OnChanges {
  @Input() patientId?: string;
  @Input() readonly = false;
  @Output() dataChange = new EventEmitter<Record<string, any>>();
  @Output() validChange = new EventEmitter<boolean>();

  private readonly fb = inject(FormBuilder);
  private readonly appShell = inject(AppShellStore);
  private readonly centresPayeursStore = inject(CentresPayeursStore);
  readonly centresPayeurs = this.centresPayeursStore.items as unknown as () => DropdownItem[];
  private readonly centresPayeursDetailsStore = inject(CentresPayeursDetailsStore);
  private readonly snackBar = inject(MatSnackBar);
  editingAssignment = signal<AssignmentEdit | null>(null);
  private readonly ficheStore = inject(PatientFicheStore);
  readonly assureCatalog = this.ficheStore.assureCatalog;
  readonly assureAssignments = this.ficheStore.assureAssignments;
  readonly loadingAssures = this.ficheStore.loadingAssures;
  readonly savingEdit = this.ficheStore.savingAssureEdit;
  private readonly selectedCentrePayeurId = signal<string | null>(null);
  readonly codeCentrePayeur = computed(() =>
    this.centresPayeurs().find((x: any) => String(x?.id ?? x?.ID ?? '') === this.selectedCentrePayeurId())?.['code']
    ?? this.centresPayeurs().find((x: any) => String(x?.id ?? x?.ID ?? '') === this.selectedCentrePayeurId())?.['CODE']
    ?? ''
  );
  showAssure = signal(true);
  private qualiteAssure = signal<string>('ASSURE_LUI_MEME');
  requiresAssureNumero = signal(false);
  assureSearch = signal('');
  private readonly centresPayeursDetails = this.centresPayeursDetailsStore.items;
  private readonly selectedCentrePayeurDetail = computed<any | null>(() => {
    const id = this.selectedCentrePayeurId();
    if (!id) return null;
    return this.centresPayeursDetails().find((d: any) => String(d?.id ?? d?.ID ?? '') === id) ?? null;
  });
  readonly codeAgence = computed(() =>
    this.selectedCentrePayeurDetail()?.code_agence
    ?? this.selectedCentrePayeurDetail()?.CODE_AGENCE
    ?? ''
  );
  showCatalog = signal(false);
  showHistory = signal(false);
  readonly libelleAgence = computed(() =>
    this.selectedCentrePayeurDetail()?.libelle_agence
    ?? this.selectedCentrePayeurDetail()?.LIBELLE_AGENCE
    ?? ''
  );
  readonly libelleCaisse = computed(() =>
    this.selectedCentrePayeurDetail()?.libelle_caisse
    ?? this.selectedCentrePayeurDetail()?.LIBELLE_CAISSE
    ?? ''
  );
  private readonly dialog = inject(MatDialog);
  private pendingAssignAssure = false;
  private pendingUpdateAssure = false;
  private pendingUpdateAssignment = false;
  private pendingLoadAssureHistory = false;
  private historyInFlightKey: string | null = null;
  private lastLoadedHistoryKey: string | null = null;

  form!: FormGroup;

  constructor() {
    effect(() => {
      if (!this.form || !this.selectedCentrePayeurId()) return;
      this.codeCentrePayeur();
      this.codeAgence();
      this.libelleAgence();
      this.libelleCaisse();
      this.emitAssuranceData();
    });

    consumeWizardActionStatus(this.ficheStore, ({action, success, error, message}) => {
      const effectiveError = error || this.ficheStore.error() || '';
      if (action === 'ASSIGN_ASSURE' && this.pendingAssignAssure) {
        this.pendingAssignAssure = false;
        if (success) {
          this.snackBar.open(message || this.ficheStore.infoMessage() || 'Assuré affecté au patient avec succès', 'OK', {duration: 2500});
        } else if (typeof effectiveError === 'string' && effectiveError.toLowerCase().includes('assuré lui-même')) {
          this.snackBar.open('Assuré sélectionné localement. Enregistrez le patient puis réessayez.', 'OK', {duration: 4500});
        } else if (effectiveError) {
          this.snackBar.open(effectiveError, 'OK', {duration: 3500});
        }
      }

      if (action === 'UPDATE_ASSURE' && this.pendingUpdateAssure) {
        this.pendingUpdateAssure = false;
        if (success) {
          this.snackBar.open('Assuré mis à jour', 'OK', {duration: 2500});
        } else if (effectiveError) {
          this.snackBar.open(effectiveError, 'OK', {duration: 3500});
        }
      }

      if (action === 'UPDATE_ASSURE_ASSIGNMENT' && this.pendingUpdateAssignment) {
        this.pendingUpdateAssignment = false;
        if (success) {
          this.editingAssignment.set(null);
          this.snackBar.open('Affectation mise à jour', 'OK', {duration: 2500});
        } else if (effectiveError) {
          this.snackBar.open(effectiveError, 'OK', {duration: 3500});
        }
      }

      if (action === 'LOAD_ASSURE_HISTORY' && this.pendingLoadAssureHistory) {
        this.pendingLoadAssureHistory = false;
        if (success && this.historyInFlightKey) {
          this.lastLoadedHistoryKey = this.historyInFlightKey;
        }
        this.historyInFlightKey = null;
        if (!success && effectiveError) {
          this.snackBar.open(effectiveError, 'OK', {duration: 3000});
        }
      }
    });
  }

  ngOnInit(): void {
    this.form = this.fb.group({
      numeroAssurance: ['', Validators.required],
      centrePayeurId: [null],
      assureNumeroAssurance: [''],
      assureNom: [''],
      assurePrenom: [''],
      assureSexe: [''],
      assureDateNaissance: [null],
      assureTelPersonnel: [''],
      assureTelMobile: [''],
      assureTelBureau: [''],
      assureGroupeSanguin: [''],
      assureAdresse: ['']
    });
    this.form.valueChanges.subscribe(() => {
      this.emitAssuranceData();
      this.validChange.emit(this.form.valid);
    });
    this.form.get('centrePayeurId')?.valueChanges.subscribe((value) => {
      this.selectedCentrePayeurId.set(value ? String(value) : null);
    });

    const cid = this.appShell.currentCenterId();
    if (cid) {
      void this.centresPayeursStore.ensureLoaded(cid);
      void this.centresPayeursDetailsStore.ensureLoaded(cid);
    }
    this.applyReadonly();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['readonly']) this.applyReadonly();
    if (changes['patientId'] && this.patientId) void this.loadAssureHistory();
  }

  private applyReadonly(): void {
    if (!this.form) return;
    this.readonly ? this.form.disable({ emitEvent: false }) : this.form.enable({ emitEvent: false });
  }

  setQualiteAssure(qa: string): void {
    this.qualiteAssure.set(qa || 'ASSURE_LUI_MEME');
    const needsAssure = !!(qa && qa !== 'ASSURE_LUI_MEME');
    this.showAssure.set(true);
    const needsAssureNumero = ['ENFANT', 'CONJOINT', 'ASCENDANT', 'AUTRE'].includes(qa || '');
    this.requiresAssureNumero.set(needsAssureNumero);
    if (needsAssure) {
      this.form.get('assureNom')?.setValidators(Validators.required);
      this.form.get('assurePrenom')?.setValidators(Validators.required);
    } else {
      this.form.get('assureNom')?.clearValidators();
      this.form.get('assurePrenom')?.clearValidators();
    }
    if (needsAssureNumero) {
      this.form.get('assureNumeroAssurance')?.setValidators(Validators.required);
    } else {
      this.form.get('assureNumeroAssurance')?.clearValidators();
    }
    this.form.get('assureNom')?.updateValueAndValidity();
    this.form.get('assurePrenom')?.updateValueAndValidity();
    this.form.get('assureNumeroAssurance')?.updateValueAndValidity();
  }

  onCentrePayeur(item: DropdownItem | null): void {
    if (this.readonly) return;
    const selectedId = item ? String((item as any).id ?? (item as any).ID ?? '') : null;
    this.form.patchValue({centrePayeurId: selectedId});
    this.selectedCentrePayeurId.set(selectedId);
    this.emitAssuranceData();
  }

  prepareNewAssure(): void {
    if (!this.canAssignAssure()) {
      this.snackBar.open('Pour ajouter/affecter un assuré, choisissez ENFANT/CONJOINT/ASCENDANT/AUTRE.', 'OK', { duration: 3500 });
      return;
    }
    this.form.patchValue({
      assureNumeroAssurance: '', assureNom: '', assurePrenom: '',
      assureSexe: '', assureDateNaissance: null,
      assureTelPersonnel: '', assureTelMobile: '', assureTelBureau: '',
      assureAdresse: '', assureGroupeSanguin: ''
    });
  }

  canAssignAssure(): boolean {
    return this.showAssure() && this.qualiteAssure() !== 'ASSURE_LUI_MEME';
  }

  toggleAssureCatalog(): void {
    this.showCatalog.set(!this.showCatalog());
    if (this.showCatalog() && this.assureCatalog().length === 0) this.searchAssures();
  }

  toggleAssureHistory(): void {
    this.showHistory.set(!this.showHistory());
    if (this.showHistory()) this.loadAssureHistory(true);
  }

  searchAssures(): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId) return;
    this.ficheStore.searchAssures({centerId, q: this.assureSearch()});
  }

  // ── Dialog : modification d'un assuré ──────────────────

  openEditAssureDialog(a: any): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId) return;
    const ref = this.dialog.open(AssureEditDialogComponent, {
      data: {...a},
      width: '600px',
      disableClose: false
    });
    ref.afterClosed().subscribe(result => {
      if (!result) return;
      const isCurrentAssure = this.form.get('assureNumeroAssurance')?.value === a.numeroAssurance;
      this.pendingUpdateAssure = true;
      this.ficheStore.updateAssure({
        centerId,
        numeroAssurance: a.numeroAssurance,
        payload: result
      });

      if (isCurrentAssure) {
        this.form.patchValue({
          assureNom: result.nom,
          assurePrenom: result.prenom,
          assureSexe: result.sexe,
          assureDateNaissance: result.dateNaissance ?? null,
          assureTelPersonnel: result.telPersonnel,
          assureTelMobile: result.telMobile,
          assureTelBureau: result.telBureau,
          assureGroupeSanguin: result.groupeSanguin,
          assureAdresse: result.adresse
        });
      }
    });
  }

  // ── Affecter un assuré (mise à jour en temps réel) ─────

  affectAssure(a: any): void {
    if (!this.canAssignAssure()) return;
    if (a?.isPrimary) return;
    const centerId = this.appShell.currentCenterId();
    if (!centerId) return;

    const patchAssureForm = () => {
      this.form.patchValue({
        assureNumeroAssurance: a.numeroAssurance,
        assureNom: a.nom ?? '',
        assurePrenom: a.prenom ?? '',
        assureSexe: a.sexe ?? '',
        assureDateNaissance: a.dateNaissance ?? null,
        assureTelPersonnel: a.telPersonnel ?? '',
        assureTelMobile: a.telMobile ?? '',
        assureTelBureau: a.telBureau ?? '',
        assureAdresse: a.adresse ?? '',
        assureGroupeSanguin: a.groupeSanguin ?? ''
      });
    };

    patchAssureForm();

    this.ficheStore.assignAssure({
      centerId,
      patientId: this.patientId ?? null,
      a
    });
    this.pendingAssignAssure = true;
  }

  // ── Édition dates historique ────────────────────────────

  openEditAssignment(h: any): void {
    this.editingAssignment.set({
      id: h.id,
      dateDebutAffectation: h.dateDebutAffectation ? new Date(h.dateDebutAffectation) : null,
      dateFinAffectation: h.dateFinAffectation ? new Date(h.dateFinAffectation) : null
    });
  }

  setEditDateDebut(value: Date | null): void {
    const cur = this.editingAssignment();
    if (cur) this.editingAssignment.set({...cur, dateDebutAffectation: value});
  }

  setEditDateFin(value: Date | null): void {
    const cur = this.editingAssignment();
    if (cur) this.editingAssignment.set({...cur, dateFinAffectation: value});
  }

  cancelEdit(): void {
    this.editingAssignment.set(null);
  }

  saveEditAssignment(): void {
    const edit = this.editingAssignment();
    const centerId = this.appShell.currentCenterId();
    if (!edit || !this.patientId || !centerId) return;
    const toStr = (d: Date | null) => d ? d.toISOString().slice(0, 10) : null;
    const debut = toStr(edit.dateDebutAffectation);
    const fin = toStr(edit.dateFinAffectation);
    if (debut && fin && fin < debut) {
      this.snackBar.open('La date de fin doit être >= à la date de début', 'OK', {duration: 3000});
      return;
    }
    this.ficheStore.updateAssureAssignment({
      centerId,
      patientId: this.patientId,
      assignmentId: edit.id,
      debut,
      fin
    });
    this.pendingUpdateAssignment = true;
  }

  // ── Chargement & synchronisation ───────────────────────

  patchData(data: Record<string, any>): void {
    if (!this.form) return;

    const resolveCentrePayeurId = (): string | null => {
      const direct = data['centrePayeurId'] ?? data['centerPayeurId'] ?? data['centre_payeur_id'];
      if (direct) return String(direct);
      const cp = data['centrePayeur'] ?? data['centerPayeur'];
      if (cp && (cp.id ?? cp.ID)) return String(cp.id ?? cp.ID);
      return null;
    };

    const qualiteFromData = data['qualiteAssure'] ?? 'ASSURE_LUI_MEME';
    const isSelf = qualiteFromData === 'ASSURE_LUI_MEME';
    const patch = {
      numeroAssurance: data['numeroAssurance'] ?? '',
      centrePayeurId: resolveCentrePayeurId(),
      assureNumeroAssurance: data['assureNumeroAssurance'] ?? (isSelf ? (data['numeroAssurance'] ?? '') : ''),
      assureNom: data['assureNom'] ?? (isSelf ? (data['nom'] ?? '') : ''),
      assurePrenom: data['assurePrenom'] ?? (isSelf ? (data['prenom'] ?? '') : ''),
      assureSexe: data['assureSexe'] ?? (isSelf ? (data['sexe'] ?? '') : ''),
      assureDateNaissance: data['assureDateNaissance'] ?? (isSelf ? (data['dateNaissance'] ?? null) : null),
      assureTelPersonnel: data['assureTelPersonnel'] ?? (isSelf ? (data['telPersonnel'] ?? '') : ''),
      assureTelMobile: data['assureTelMobile'] ?? (isSelf ? (data['telMobile'] ?? '') : ''),
      assureTelBureau: data['assureTelBureau'] ?? (isSelf ? (data['telBureau'] ?? '') : ''),
      assureGroupeSanguin: data['assureGroupeSanguin'] ?? (isSelf ? (data['groupeSanguin'] ?? '') : ''),
      assureAdresse: data['assureAdresse'] ?? (isSelf ? (data['adresse'] ?? '') : '')
    };
    this.form.patchValue(patch, { emitEvent: false });

    const hasAssureData = [
      patch.assureNom, patch.assurePrenom, patch.assureSexe, patch.assureDateNaissance,
      patch.assureTelPersonnel, patch.assureTelMobile, patch.assureTelBureau,
      patch.assureGroupeSanguin, patch.assureAdresse
    ].some(v => v !== null && v !== undefined && String(v).trim() !== '');

    const qualite = data['qualiteAssure'] ?? (hasAssureData ? 'AUTRE' : 'ASSURE_LUI_MEME');
    this.setQualiteAssure(qualite);

    if (this.patientId) this.loadAssureHistory();

    this.selectedCentrePayeurId.set(patch.centrePayeurId);
    this.emitAssuranceData();
    this.validChange.emit(this.form.valid);
    this.applyReadonly();
  }

  private emitAssuranceData(): void {
    if (!this.form) return;
    this.dataChange.emit({
      ...this.form.getRawValue(),
      codeCentrePayeur: this.codeCentrePayeur(),
      codeAgence: this.codeAgence(),
      libelleAgence: this.libelleAgence(),
      libelleCaisse: this.libelleCaisse()
    });
  }

  private loadAssureHistory(force = false): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) return;

    const key = `${centerId}|${this.patientId}`;
    if (this.historyInFlightKey === key) return;
    if (!force && this.lastLoadedHistoryKey === key) return;

    this.pendingLoadAssureHistory = true;
    this.historyInFlightKey = key;
    this.ficheStore.loadAssureHistory({centerId, patientId: this.patientId});
  }

  // ── API publique ────────────────────────────────────────

  markTouched(): void { this.form.markAllAsTouched(); }
  isValid(): boolean { return this.form.valid; }

}
