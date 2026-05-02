import {Component, EventEmitter, inject, Input, OnChanges, OnInit, Output, signal, SimpleChanges} from '@angular/core';
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
import {TranslateModule} from '@ngx-translate/core';
import {DropdownItem, SearchableSelectComponent} from '../../../shared/searchable-select.component';
import {CentrePayeurDetail, ReferentialApiService} from '../../../core/api/referential-api.service';
import {BackendApiService} from '../../../core/api/backend-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';

@Component({
  selector: 'app-step-assurance',
  standalone: true,
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatDatepickerModule, MatNativeDateModule, MatIconModule, MatDividerModule, MatButtonModule, TranslateModule, SearchableSelectComponent],
  template: `
    <div class="step-content">
      <h3 class="section-title">{{ 'PATIENT_FORM.SECTION_INSURANCE' | translate }}</h3>
      <form [formGroup]="form">
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

        <div class="form-row">
          <app-searchable-select
            [items]="centresPayeurs()" [label]="'PATIENT_FORM.CENTRE_PAYEUR' | translate" [prefixIcon]="'account_balance'"
            [selectedId]="form.get('centrePayeurId')?.value" (selectionChanged)="onCentrePayeur($event)"
            [disabled]="readonly"
            cssClass="flex1" />
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

        <h3 class="section-title">{{ 'PATIENT_FORM.SECTION_ASSURE' | translate }}</h3>
          <div class="form-row">
            <mat-form-field appearance="outline" class="flex1">
              <mat-label>N° Assurance assuré @if (requiresAssureNumero()) { * }</mat-label>
              <mat-icon matPrefix>badge</mat-icon>
              <input matInput formControlName="assureNumeroAssurance" />
              @if (form.get('assureNumeroAssurance')?.hasError('required') && form.get('assureNumeroAssurance')?.touched) {
                <mat-error>{{ 'PATIENT_FORM.REQUIRED' | translate }}</mat-error>
              }
            </mat-form-field>
          </div>
          <div class="form-row">
            <mat-form-field appearance="outline" class="flex1">
              <mat-label>{{ 'PATIENT_FORM.ASSURE_NOM' | translate }} *</mat-label>
              <mat-icon matPrefix>person</mat-icon>
              <input matInput formControlName="assureNom" />
              @if (form.get('assureNom')?.hasError('required') && form.get('assureNom')?.touched) {
                <mat-error>{{ 'PATIENT_FORM.REQUIRED' | translate }}</mat-error>
              }
            </mat-form-field>
            <mat-form-field appearance="outline" class="flex1">
              <mat-label>{{ 'PATIENT_FORM.ASSURE_PRENOM' | translate }} *</mat-label>
              <mat-icon matPrefix>person_outline</mat-icon>
              <input matInput formControlName="assurePrenom" />
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
              <input matInput [matDatepicker]="dpAssure" formControlName="assureDateNaissance" />
              <mat-datepicker-toggle matSuffix [for]="dpAssure" /><mat-datepicker #dpAssure />
            </mat-form-field>
            <mat-form-field appearance="outline" class="flex1">
              <mat-label>{{ 'PATIENT_FORM.ASSURE_TEL_PERSONNEL' | translate }}</mat-label>
              <mat-icon matPrefix>phone</mat-icon>
              <input matInput formControlName="assureTelPersonnel" />
            </mat-form-field>
            <mat-form-field appearance="outline" class="flex1">
              <mat-label>{{ 'PATIENT_FORM.ASSURE_TEL_MOBILE' | translate }}</mat-label>
              <mat-icon matPrefix>phone_iphone</mat-icon>
              <input matInput formControlName="assureTelMobile" />
            </mat-form-field>
            <mat-form-field appearance="outline" class="flex1">
              <mat-label>{{ 'PATIENT_FORM.ASSURE_TEL_BUREAU' | translate }}</mat-label>
              <mat-icon matPrefix>phone_in_talk</mat-icon>
              <input matInput formControlName="assureTelBureau" />
            </mat-form-field>
          </div>
          <div class="form-row">
            <mat-form-field appearance="outline" class="flex1">
              <mat-label>{{ 'PATIENT_FORM.ASSURE_GROUPE_SANGUIN' | translate }}</mat-label>
              <mat-icon matPrefix>bloodtype</mat-icon>
              <mat-select formControlName="assureGroupeSanguin">
                <mat-option value="">—</mat-option>
                <mat-option value="A+">A+</mat-option><mat-option value="A-">A-</mat-option>
                <mat-option value="B+">B+</mat-option><mat-option value="B-">B-</mat-option>
                <mat-option value="O+">O+</mat-option><mat-option value="O-">O-</mat-option>
                <mat-option value="AB+">AB+</mat-option><mat-option value="AB-">AB-</mat-option>
              </mat-select>
            </mat-form-field>
          </div>
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>{{ 'PATIENT_FORM.ASSURE_ADRESSE' | translate }}</mat-label>
            <mat-icon matPrefix>home</mat-icon>
            <input matInput formControlName="assureAdresse" />
          </mat-form-field>

        <div class="form-row">
          <mat-form-field appearance="outline" class="flex1">
            <mat-label>Date début affectation</mat-label>
            <mat-icon matPrefix>event</mat-icon>
            <input matInput [matDatepicker]="dpDebAffect" formControlName="dateDebutAffectation"/>
            <mat-datepicker-toggle matSuffix [for]="dpDebAffect"/>
            <mat-datepicker #dpDebAffect/>
          </mat-form-field>
          <mat-form-field appearance="outline" class="flex1">
            <mat-label>Date fin affectation</mat-label>
            <mat-icon matPrefix>event_busy</mat-icon>
            <input matInput [matDatepicker]="dpFinAffect" formControlName="dateFinAffectation"/>
            <mat-datepicker-toggle matSuffix [for]="dpFinAffect"/>
            <mat-datepicker #dpFinAffect/>
          </mat-form-field>
        </div>

        <div class="form-row" style="justify-content:flex-end; gap:8px; margin-top: 8px;">
            <button mat-stroked-button type="button" (click)="toggleAssureCatalog()" [disabled]="readonly || !canAssignAssure()">
              <mat-icon>manage_search</mat-icon>
              Consulter les assurés
            </button>
            <button mat-stroked-button type="button" (click)="toggleAssureHistory()" [disabled]="!patientId">
              <mat-icon>history</mat-icon>
              Historique des affectations
            </button>
          </div>

          @if (showCatalog()) {
            <div class="history-box">
              <div class="history-title">Catalogue des assurés</div>
              <div class="form-row" style="margin-bottom:0;">
                <mat-form-field appearance="outline" class="full-width">
                  <mat-label>Rechercher (N° assurance / nom / prénom)</mat-label>
                  <mat-icon matPrefix>search</mat-icon>
                  <input matInput [value]="assureSearch()" (input)="assureSearch.set($any($event.target).value)" />
                </mat-form-field>
                <button mat-flat-button type="button" (click)="searchAssures()" [disabled]="loadingAssures()">
                  <mat-icon>search</mat-icon>
                  Rechercher
                </button>
              </div>
              @for (a of assureCatalog(); track a.numeroAssurance) {
                <div class="assure-row">
                  <div>
                    <strong>{{ a.nom }} {{ a.prenom }}</strong>
                    <div class="muted">
                      {{ a.numeroAssurance }} • {{ a.sexe || '—' }}
                      @if (a.isPrimary) {
                        <span class="primary-chip">Primaire</span>
                      }
                    </div>
                    @if (a.dateDebutAffectation || a.dateFinAffectation || a.isPrimary) {
                      <div class="muted">Affectation: {{ a.dateDebutAffectation || '—' }}
                        → {{ a.dateFinAffectation || 'en cours' }}
                      </div>
                    }
                  </div>
                  <button mat-stroked-button type="button" (click)="affectAssure(a)"
                          [disabled]="readonly || !canAssignAssure() || a.isPrimary">
                    <mat-icon>person_add_alt_1</mat-icon>
                    {{ a.isPrimary ? 'Déjà primaire' : 'Affecter au patient' }}
                  </button>
                </div>
              }
            </div>
          }

          @if (showHistory()) {
            <div class="history-box">
              <div class="history-title">Historique d'affectation</div>
              @for (h of assureAssignments(); track h.numeroAssurance + '-' + h.dateAffectation) {
                <div class="history-item">
                  {{ h.nom }} {{ h.prenom }} - {{ h.numeroAssurance }} - {{ h.dateAffectation || '—' }}
                  @if (h.isPrimary) { <strong> (primaire)</strong> }
                  <div class="muted">Période: {{ h.dateDebutAffectation || '—' }}
                    → {{ h.dateFinAffectation || 'en cours' }}
                  </div>
                </div>
              }
              @if (assureAssignments().length === 0) {
                <div class="history-item">Aucun historique d'affectation</div>
              }
            </div>
          }
        <div class="form-row" style="justify-content:flex-end; margin-top: 16px;">
          <button mat-stroked-button type="button" (click)="prepareNewAssure()" [disabled]="readonly">
            <mat-icon>person_add</mat-icon>
            {{ 'WIZARD.ADD_NEW_INSURED' | translate }}
          </button>
        </div>
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
    .history-box { border: 1px solid var(--app-border); border-radius: 10px; padding: 8px 12px; margin-top: 8px; background: var(--app-surface); }
    .history-title { font-weight: 600; color: var(--app-primary); margin-bottom: 6px; }
    .history-item { font-size: 12px; color: #4b5563; margin-bottom: 2px; }
    .assure-row {
      display:flex; align-items:center; justify-content:space-between; gap:10px;
      border:1px solid var(--app-border); border-radius:8px; padding:8px 10px; margin-bottom:6px; background:var(--app-surface);
    }

    .primary-chip {
      margin-left: 6px;
      padding: 1px 8px;
      border-radius: 999px;
      border: 1px solid var(--app-primary-outline);
      background: var(--app-primary-soft);
      color: var(--app-primary);
      font-size: 11px;
      font-weight: 600;
    }
    .muted { font-size: 12px; color: #6b7280; }
  `]
})
export class StepAssuranceComponent implements OnInit, OnChanges {
  @Input() patientId?: string;
  @Input() readonly = false;
  @Output() dataChange = new EventEmitter<Record<string, any>>();
  @Output() validChange = new EventEmitter<boolean>();

  private readonly fb = inject(FormBuilder);
  private readonly refApi = inject(ReferentialApiService);
  private readonly api = inject(BackendApiService);
  private readonly store = inject(AppShellStore);
  private readonly snackBar = inject(MatSnackBar);

  centresPayeurs = signal<DropdownItem[]>([]);
  codeCentrePayeur = signal('');
  codeAgence = signal('');
  libelleAgence = signal('');
  libelleCaisse = signal('');
  private centresPayeursDetails = signal<CentrePayeurDetail[]>([]);
  showAssure = signal(true);
  private qualiteAssure = signal<string>('ASSURE_LUI_MEME');
  requiresAssureNumero = signal(false);
  assureSearch = signal('');
  assureCatalog = signal<any[]>([]);
  assureAssignments = signal<any[]>([]);
  showCatalog = signal(false);
  showHistory = signal(false);
  loadingAssures = signal(false);

  form!: FormGroup;

  ngOnInit(): void {
    this.form = this.fb.group({
      numeroAssurance: ['', Validators.required],
      centrePayeurId: [null],
      assureNumeroAssurance: [''],
      assureNom: [''],
      assurePrenom: [''],
      assureSexe: [''],
      assureDateNaissance: [null],
      dateDebutAffectation: [null],
      dateFinAffectation: [null],
      assureTelPersonnel: [''],
      assureTelMobile: [''],
      assureTelBureau: [''],
      assureGroupeSanguin: [''],
      assureAdresse: ['']
    });

    this.form.valueChanges.subscribe(() => {
      this.dataChange.emit(this.form.getRawValue());
      this.validChange.emit(this.form.valid);
    });

    const cid = this.store.currentCenterId();
    if (cid) {
      this.refApi.getCentresPayeurs(cid).subscribe(list => {
        this.centresPayeurs.set(list.map((i: any) => ({ ...i, id: i.id, label: `${i.nom} (${i.code ?? ''})` })));
        this.applyCentrePayeurDisplay(this.form.get('centrePayeurId')?.value ?? null);
      });
      this.refApi.getCentresPayeursDetails(cid).subscribe(rows => {
        this.centresPayeursDetails.set(rows);
        this.applyCentrePayeurDisplay(this.form.get('centrePayeurId')?.value ?? null);
      });
    }
    this.applyReadonly();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['readonly']) this.applyReadonly();
    if (changes['patientId'] && this.patientId) this.loadAssureHistory();
  }

  private applyReadonly(): void {
    if (!this.form) return;
    this.readonly ? this.form.disable({ emitEvent: false }) : this.form.enable({ emitEvent: false });
  }

  /** Called externally with qualiteAssure from step 1 */
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
    this.form.patchValue({ centrePayeurId: item?.id ?? null });
    this.applyCentrePayeurDisplay(item?.id ?? null);
    this.dataChange.emit({
      ...this.form.getRawValue(),
      codeCentrePayeur: this.codeCentrePayeur(),
      codeAgence: this.codeAgence(),
      libelleAgence: this.libelleAgence(),
      libelleCaisse: this.libelleCaisse()
    });
  }

  private applyCentrePayeurDisplay(centrePayeurId: string | null): void {
    const item = this.centresPayeurs().find(x => x.id === centrePayeurId);
    this.codeCentrePayeur.set(item?.['code'] ?? '');
    const details = this.centresPayeursDetails().find(d => d.id === centrePayeurId);
    this.codeAgence.set(details?.code_agence ?? '');
    this.libelleAgence.set(details?.libelle_agence ?? '');
    this.libelleCaisse.set(details?.libelle_caisse ?? '');
  }

  prepareNewAssure(): void {
    if (!this.canAssignAssure()) {
      this.snackBar.open('Pour ajouter/affecter un assuré, choisissez ENFANT/CONJOINT/ASCENDANT/AUTRE.', 'OK', { duration: 3500 });
      return;
    }
    this.form.patchValue({
      assureNumeroAssurance: '',
      assureNom: '',
      assurePrenom: '',
      assureSexe: '',
      assureDateNaissance: null,
      dateDebutAffectation: null,
      dateFinAffectation: null,
      assureTelPersonnel: '',
      assureTelMobile: '',
      assureTelBureau: '',
      assureAdresse: '',
      assureGroupeSanguin: ''
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
    if (this.showHistory()) this.loadAssureHistory();
  }

  searchAssures(): void {
    const centerId = this.store.currentCenterId();
    if (!centerId) return;
    this.loadingAssures.set(true);
    this.api.searchAssures(centerId, this.assureSearch()).subscribe({
      next: rows => {
        this.assureCatalog.set(rows ?? []);
        this.syncCatalogWithAssignments();
        this.loadingAssures.set(false);
      },
      error: () => { this.loadingAssures.set(false); this.snackBar.open('Erreur chargement des assurés', 'OK', { duration: 3000 }); }
    });
  }

  affectAssure(a: any): void {
    if (!this.canAssignAssure()) return;
    if (a?.isPrimary) return;
    const centerId = this.store.currentCenterId();
    if (!centerId) return;

    const patchAssure = () => {
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

    if (!this.patientId) {
      patchAssure();
      this.snackBar.open('Assuré sélectionné pour ce patient', 'OK', {duration: 2500});
      return;
    }

    const toDate = (v: any): string | null => {
      if (!v) return null;
      if (v instanceof Date) return v.toISOString().slice(0, 10);
      return String(v).slice(0, 10);
    };

    this.api.assignAssureToPatient(centerId, this.patientId, a.numeroAssurance, {
      dateDebutAffectation: toDate(this.form.get('dateDebutAffectation')?.value),
      dateFinAffectation: toDate(this.form.get('dateFinAffectation')?.value)
    }).subscribe({
      next: () => {
        patchAssure();
        this.loadAssureHistory();
        this.assureCatalog.update(list => list.map(row => ({
          ...row,
          isPrimary: row.numeroAssurance === a.numeroAssurance,
          dateDebutAffectation: row.numeroAssurance === a.numeroAssurance ? toDate(this.form.get('dateDebutAffectation')?.value) : row.dateDebutAffectation,
          dateFinAffectation: row.numeroAssurance === a.numeroAssurance ? toDate(this.form.get('dateFinAffectation')?.value) : row.dateFinAffectation
        })));
        this.snackBar.open('Assuré affecté au patient', 'OK', { duration: 2500 });
      },
      error: (err) => {
        const detail = err?.error?.detail || err?.error?.message || '';
        if (typeof detail === 'string' && detail.toLowerCase().includes('assuré lui-même')) {
          // Patient quality may be changed locally but not persisted yet: keep UI data and ask for save.
          patchAssure();
          this.snackBar.open('Assuré sélectionné localement. Enregistrez le patient puis réessayez l\'affectation.', 'OK', {duration: 4500});
          return;
        }
        if (err?.status === 401 || err?.status === 403) {
          // Auth errors are handled globally by the HTTP interceptor (refresh or redirect to /login).
          return;
        }
        this.snackBar.open(detail || 'Erreur affectation assuré', 'OK', {duration: 3500});
      }
    });
  }

  private loadAssureHistory(): void {
    const centerId = this.store.currentCenterId();
    if (!centerId || !this.patientId) return;
    this.api.listPatientAssureHistory(centerId, this.patientId).subscribe(rows => {
      this.assureAssignments.set(rows ?? []);
      this.syncCatalogWithAssignments();
    });
  }

  private syncCatalogWithAssignments(): void {
    const assignmentsByAssure = new Map<string, any>();
    for (const h of this.assureAssignments()) {
      assignmentsByAssure.set(h.numeroAssurance, h);
    }
    if (this.assureCatalog().length === 0) return;
    this.assureCatalog.update(list => list.map(a => {
      const m = assignmentsByAssure.get(a.numeroAssurance);
      return {
        ...a,
        isPrimary: !!m?.isPrimary,
        dateDebutAffectation: m?.dateDebutAffectation ?? null,
        dateFinAffectation: m?.dateFinAffectation ?? null
      };
    }));
  }

  markTouched(): void { this.form.markAllAsTouched(); }
  isValid(): boolean { return this.form.valid; }

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
      dateDebutAffectation: data['dateDebutAffectation'] ?? null,
      dateFinAffectation: data['dateFinAffectation'] ?? null,
      assureTelPersonnel: data['assureTelPersonnel'] ?? (isSelf ? (data['telPersonnel'] ?? '') : ''),
      assureTelMobile: data['assureTelMobile'] ?? (isSelf ? (data['telMobile'] ?? '') : ''),
      assureTelBureau: data['assureTelBureau'] ?? (isSelf ? (data['telBureau'] ?? '') : ''),
      assureGroupeSanguin: data['assureGroupeSanguin'] ?? (isSelf ? (data['groupeSanguin'] ?? '') : ''),
      assureAdresse: data['assureAdresse'] ?? (isSelf ? (data['adresse'] ?? '') : '')
    };
    this.form.patchValue(patch, { emitEvent: false });

    const hasAssureData = [
      patch.assureNom,
      patch.assurePrenom,
      patch.assureSexe,
      patch.assureDateNaissance,
      patch.assureTelPersonnel,
      patch.assureTelMobile,
      patch.assureTelBureau,
      patch.assureGroupeSanguin,
      patch.assureAdresse
    ].some(v => v !== null && v !== undefined && String(v).trim() !== '');

    const qualite = data['qualiteAssure'] ?? (hasAssureData ? 'AUTRE' : 'ASSURE_LUI_MEME');
    this.setQualiteAssure(qualite);

    if (this.patientId) this.loadAssureHistory();

    this.applyCentrePayeurDisplay(patch.centrePayeurId);
    this.dataChange.emit(this.form.getRawValue());
    this.validChange.emit(this.form.valid);
    this.applyReadonly();
  }
}
