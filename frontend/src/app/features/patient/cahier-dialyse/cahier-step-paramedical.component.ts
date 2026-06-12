import {ChangeDetectionStrategy, Component, EventEmitter, inject, Input, OnInit, Output, signal,} from '@angular/core';
import {CommonModule} from '@angular/common';
import {MatCardModule} from '@angular/material/card';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatSnackBar} from '@angular/material/snack-bar';
import {FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {BackendApiService} from '../../../core/api/backend-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {AuthStore} from '../../../core/state/auth.store';
import {ArticlesStore} from '../../../core/state/referentials.store';

@Component({
  selector: 'app-cahier-step-paramedical',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    TranslateModule,
  ],
  template: `
    <div class="step-content">
      <mat-card class="paramedical-card" data-testid="cahier-step-paramedical">
        <mat-card-header>
          <mat-card-title>{{ 'CAHIER.STEP_PARAMEDICAL' | translate }}</mat-card-title>
          <mat-card-subtitle>{{ 'CAHIER.STEP_PARAMEDICAL_DESC' | translate }}</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="form" class="form-grid cahier-field-size">
            <section class="section-block">
              <h4>{{ 'CAHIER.PARAMEDICAL_PRESEANCE' | translate }}</h4>
              <div class="fields-grid">
                <mat-form-field appearance="outline">
                  <mat-label>{{ 'CAHIER.PARAMEDICAL_DATE_SEANCE' | translate }}</mat-label>
                  <input matInput type="date" formControlName="dateSeance" [readonly]="readonly" />
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>{{ 'CAHIER.PARAMEDICAL_POIDS_AVANT' | translate }}</mat-label>
                  <input
                    matInput
                    type="number"
                    min="0"
                    step="0.1"
                    formControlName="poidsAvantKg"
                    [readonly]="readonly"
                  />
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>{{ 'CAHIER.PARAMEDICAL_POIDS_APRES' | translate }}</mat-label>
                  <input
                    matInput
                    type="number"
                    min="0"
                    step="0.1"
                    formControlName="poidsApresKg"
                    [readonly]="readonly"
                  />
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>{{ 'CAHIER.PARAMEDICAL_TA_AVANT' | translate }}</mat-label>
                  <input
                    matInput
                    formControlName="taAvant"
                    [readonly]="readonly"
                    placeholder="120/80"
                  />
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>{{ 'CAHIER.PARAMEDICAL_TA_APRES' | translate }}</mat-label>
                  <input
                    matInput
                    formControlName="taApres"
                    [readonly]="readonly"
                    placeholder="120/80"
                  />
                </mat-form-field>
              </div>
            </section>

            <section class="section-block">
              <h4>{{ 'CAHIER.PARAMEDICAL_MACHINE' | translate }}</h4>
              <div class="fields-grid">
                <mat-form-field appearance="outline">
                  <mat-label>{{ 'CAHIER.PARAMEDICAL_DUREE' | translate }}</mat-label>
                  <input
                    matInput
                    type="number"
                    min="30"
                    step="1"
                    formControlName="dureeMinutes"
                    [readonly]="readonly"
                  />
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>{{ 'CAHIER.PARAMEDICAL_DEBIT_SANG' | translate }}</mat-label>
                  <input
                    matInput
                    type="number"
                    min="0"
                    step="1"
                    formControlName="debitSangMlMin"
                    [readonly]="readonly"
                  />
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>{{ 'CAHIER.PARAMEDICAL_UF' | translate }}</mat-label>
                  <input
                    matInput
                    type="number"
                    min="0"
                    step="1"
                    formControlName="ultrafiltrationMl"
                    [readonly]="readonly"
                  />
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>{{ 'CAHIER.PARAMEDICAL_DIALYSAT' | translate }}</mat-label>
                  <input matInput formControlName="typeDialysat" [readonly]="readonly" />
                </mat-form-field>
              </div>
            </section>

            <section class="section-block section-wide">
              <h4>{{ 'CAHIER.PARAMEDICAL_SURVEILLANCE' | translate }}</h4>
              <div class="fields-grid">
                <mat-form-field appearance="outline">
                  <mat-label>{{ 'CAHIER.PARAMEDICAL_ANTICOAGULANT' | translate }}</mat-label>
                  <input matInput formControlName="anticoagulant" [readonly]="readonly" />
                </mat-form-field>
                <mat-form-field appearance="outline" class="full-width textarea-field">
                  <mat-label>{{ 'CAHIER.PARAMEDICAL_INCIDENTS' | translate }}</mat-label>
                  <textarea
                    matInput
                    rows="3"
                    formControlName="incidents"
                    [readonly]="readonly"
                  ></textarea>
                </mat-form-field>
              </div>
            </section>

            <section class="section-block section-wide">
              <h4>{{ 'CAHIER.PARAMEDICAL_ARTICLES' | translate }}</h4>
              <div class="articles-grid" formArrayName="consommations">
                <div
                  class="article-row"
                  *ngFor="let _item of consommations.controls; let i = index"
                  [attr.data-article-row]="_item.value?.articleId || ''"
                  [formGroup]="consommationAt(i)"
                >
                  <mat-form-field appearance="outline">
                    <mat-label>{{ 'CAHIER.PARAMEDICAL_ARTICLE' | translate }}</mat-label>
                    <mat-select
                      formControlName="articleId"
                      [disabled]="readonly || articlesLoading()"
                    >
                      <mat-option value="">{{
                          'CAHIER.PARAMEDICAL_SELECT_ARTICLE' | translate
                        }}
                      </mat-option>
                      <mat-option *ngFor="let article of articles()" [value]="article.id">
                        {{ article.label }}
                      </mat-option>
                    </mat-select>
                  </mat-form-field>
                  <mat-form-field appearance="outline">
                    <mat-label>{{ 'CAHIER.PARAMEDICAL_QTE' | translate }}</mat-label>
                    <input
                      matInput
                      type="number"
                      min="0.01"
                      step="0.01"
                      formControlName="quantite"
                      [readonly]="readonly"
                    />
                  </mat-form-field>
                  <button
                    mat-icon-button
                    color="warn"
                    type="button"
                    *ngIf="!readonly"
                    (click)="removeConsommation(i)"
                    [attr.aria-label]="'Supprimer ligne article ' + (i + 1)"
                  >
                    <mat-icon>delete</mat-icon>
                  </button>
                </div>
              </div>
              <div class="article-actions" *ngIf="!readonly">
                <button
                  mat-stroked-button
                  color="primary"
                  type="button"
                  (click)="addConsommation()"
                >
                  <mat-icon>add</mat-icon>
                  {{ 'CAHIER.PARAMEDICAL_ADD_ARTICLE' | translate }}
                </button>
              </div>
              <p class="articles-hint" *ngIf="articlesLoading()">
                {{ 'CAHIER.PARAMEDICAL_ARTICLES_LOADING' | translate }}
              </p>
              <p class="articles-hint articles-error" *ngIf="articlesError()">
                {{ articlesError() }}
              </p>
            </section>
          </form>

          <div class="helper-line" *ngIf="!readonly">
            <mat-icon>info</mat-icon>
            <span>{{ 'CAHIER.PARAMEDICAL_HINT' | translate }}</span>
          </div>

          <div class="actions">
            <button
              mat-raised-button
              color="primary"
              *ngIf="!readonly"
              (click)="save()"
              [disabled]="!form.valid || saving()"
            >
              <mat-icon>save</mat-icon>
              {{
                saving() ? ('CAHIER.PARAMEDICAL_SAVING' | translate) : ('COMMON.SAVE' | translate)
              }}
            </button>
            <button mat-stroked-button color="primary" (click)="proceed()">
              <mat-icon>arrow_forward</mat-icon>
              {{ 'COMMON.NEXT_STEP' | translate }}
            </button>
          </div>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.Eager,
  styles: [
    `
      .step-content {
        padding: 12px 0;
      }

      .paramedical-card {
        margin: 0;
      }

      .form-grid {
        display: grid;
        gap: 14px;
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }

      .section-block {
        border: 1px solid var(--app-border);
        border-radius: 16px;
        background: var(--app-surface-soft);
        padding: 14px;
      }

      .section-wide {
        grid-column: 1 / -1;
      }

      .section-block h4 {
        margin: 0 0 10px;
        font-size: 12px;
        text-transform: uppercase;
        letter-spacing: 0.08em;
        color: var(--app-primary);
      }

      .fields-grid {
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        gap: 10px;
      }

      .full-width {
        grid-column: 1 / -1;
      }

      .articles-grid {
        display: grid;
        gap: 10px;
      }

      .article-row {
        display: grid;
        grid-template-columns: 1.7fr 0.8fr auto;
        gap: 8px;
        align-items: center;
      }

      .article-actions {
        margin-top: 8px;
        display: flex;
        justify-content: flex-start;
      }

      .articles-hint {
        margin: 8px 0 0;
        font-size: 12px;
        color: var(--app-muted);
      }

      .articles-error {
        color: #b91c1c;
      }

      .helper-line {
        margin-top: 12px;
        display: inline-flex;
        align-items: center;
        gap: 6px;
        color: var(--app-muted);
        font-size: 12px;
      }

      .helper-line mat-icon {
        width: 16px;
        height: 16px;
        font-size: 16px;
      }

      .actions {
        display: flex;
        gap: 12px;
        justify-content: flex-end;
        margin-top: 16px;
      }

      @media (max-width: 960px) {
        .form-grid,
        .fields-grid {
          grid-template-columns: 1fr;
        }
        .article-row {
          grid-template-columns: 1fr;
        }
        .section-wide,
        .full-width {
          grid-column: auto;
        }
      }
    `,
  ],
})
export class CahierStepParamedicalComponent implements OnInit {
  @Input() patientId!: string;
  @Input() readonly = false;
  @Output() dataChange = new EventEmitter<any>();
  @Output() validChange = new EventEmitter<boolean>();
  readonly saving = signal(false);
  private readonly fb = inject(FormBuilder);
  readonly form = this.fb.group({
    dateSeance: ['', [Validators.required]],
    poidsAvantKg: [null as number | null, [Validators.min(0)]],
    poidsApresKg: [null as number | null, [Validators.min(0)]],
    taAvant: ['', [Validators.maxLength(32)]],
    taApres: ['', [Validators.maxLength(32)]],
    dureeMinutes: [null as number | null, [Validators.min(30)]],
    debitSangMlMin: [null as number | null, [Validators.min(0)]],
    ultrafiltrationMl: [null as number | null, [Validators.min(0)]],
    anticoagulant: ['', [Validators.maxLength(120)]],
    typeDialysat: ['', [Validators.maxLength(120)]],
    incidents: ['', [Validators.maxLength(800)]],
    consommations: this.fb.array([]),
  });
  private readonly api = inject(BackendApiService);
  private readonly appShell = inject(AppShellStore);
  private readonly auth = inject(AuthStore);
  private readonly articlesStore = inject(ArticlesStore);
  readonly articles = this.articlesStore.items;
  readonly articlesLoading = this.articlesStore.loading;
  readonly articlesError = this.articlesStore.error;
  private readonly snackBar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);
  private seanceId: string | null = null;
  private lastSeanceDate: string | null = null;

  get consommations(): FormArray {
    return this.form.get('consommations') as FormArray;
  }

  ngOnInit(): void {
    this.form.patchValue({dateSeance: this.todayIsoDate()}, {emitEvent: false});
    if (this.consommations.length === 0) this.addConsommation();

    const centerId = this.appShell.currentCenterId();
    if (centerId) {
      this.articlesStore.ensureLoaded(centerId);
    }

    if (this.readonly) {
      this.form.disable({emitEvent: false});
    }
    this.validChange.emit(this.form.valid);
    this.form.valueChanges.subscribe((value) => {
      this.dataChange.emit(value);
      this.validChange.emit(this.form.valid);
    });
  }

  save(): void {
    if (this.readonly || this.form.invalid || this.saving()) return;
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) {
      this.snackBar.open(this.translate.instant('COMMON.ERROR_MISSING_DATA'), 'OK', {
        duration: 3000,
      });
      return;
    }

    this.saving.set(true);
    this.ensureSeanceId(centerId, this.patientId)
      .then((seanceId) => {
        if (!seanceId) throw new Error('missing-seance-id');
        const userId = this.auth.username() ?? 'infirmier';
        const consommations = this.validConsommations();

        return this.api
          .upsertVoletParamedical(seanceId, {
            centerId,
            poidsAvantKg: this.form.value.poidsAvantKg,
            poidsApresKg: this.form.value.poidsApresKg,
            taAvant: this.nullableText(this.form.value.taAvant),
            taApres: this.nullableText(this.form.value.taApres),
            dureeMinutes: this.form.value.dureeMinutes,
            debitSangMlMin: this.form.value.debitSangMlMin,
            ultrafiltrationMl: this.form.value.ultrafiltrationMl,
            anticoagulant: this.nullableText(this.form.value.anticoagulant),
            typeDialysat: this.nullableText(this.form.value.typeDialysat),
            incidents: this.nullableText(this.form.value.incidents),
          })
          .toPromise()
          .then((volet) => ({seanceId, userId, consommations, volet}));
      })
      .then((state) => {
        if (!state) return null;
        return this.api
          .validateSeance(state.seanceId, {
            centerId,
            userId: state.userId,
            consommations: state.consommations,
          })
          .toPromise()
          .then((validation) => ({...state, validation}));
      })
      .then((response) => {
        this.dataChange.emit({...this.form.getRawValue(), response});
        this.validChange.emit(true);
        this.snackBar.open(this.translate.instant('CAHIER.PARAMEDICAL_SAVE_OK'), 'OK', {
          duration: 2500,
        });
      })
      .catch(() => {
        this.snackBar.open(this.translate.instant('CAHIER.PARAMEDICAL_SAVE_KO'), 'OK', {
          duration: 3000,
        });
      })
      .finally(() => this.saving.set(false));
  }

  proceed(): void {
    this.validChange.emit(this.form.valid);
  }

  addConsommation(): void {
    this.consommations.push(
      this.fb.group({
        articleId: ['', [Validators.required]],
        quantite: [1, [Validators.required, Validators.min(0.01)]],
      }),
    );
  }

  removeConsommation(index: number): void {
    this.consommations.removeAt(index);
    if (this.consommations.length === 0) {
      this.addConsommation();
    }
  }

  consommationAt(index: number): FormGroup {
    return this.consommations.at(index) as FormGroup;
  }

  private async ensureSeanceId(centerId: string, patientId: string): Promise<string> {
    const selectedDate = this.form.value.dateSeance || this.todayIsoDate();
    if (this.lastSeanceDate !== selectedDate) {
      this.seanceId = null;
      this.lastSeanceDate = selectedDate;
    }
    if (this.seanceId) return this.seanceId;
    const response = await this.api
      .createSeance({
        centerId,
        patientId,
        dateSeance: selectedDate,
      })
      .toPromise();
    this.seanceId = response?.id ?? null;
    return this.seanceId ?? '';
  }

  private validConsommations(): Array<{ articleId: string; quantite: number }> {
    return this.consommations.controls
      .map((ctrl) => ({
        articleId: String(ctrl.get('articleId')?.value ?? '').trim(),
        quantite: Number(ctrl.get('quantite')?.value ?? 0),
      }))
      .filter((item) => item.articleId.length > 0 && item.quantite > 0);
  }

  private todayIsoDate(): string {
    return new Date().toISOString().slice(0, 10);
  }

  private nullableText(value: string | null | undefined): string | null {
    const text = (value ?? '').trim();
    return text ? text : null;
  }
}
