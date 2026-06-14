import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  EventEmitter,
  inject,
  Injector,
  Input,
  OnInit,
  Output,
  signal,
} from '@angular/core';
import {CommonModule} from '@angular/common';
import {MatCardModule} from '@angular/material/card';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatSnackBar} from '@angular/material/snack-bar';
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
          <form class="form-grid cahier-field-size">
            <section class="section-block">
              <h4>{{ 'CAHIER.PARAMEDICAL_PRESEANCE' | translate }}</h4>
              <div class="fields-grid">
                <mat-form-field appearance="outline">
                  <mat-label>{{ 'CAHIER.PARAMEDICAL_DATE_SEANCE' | translate }}</mat-label>
                  <input matInput type="date" [value]="formModel().dateSeance"
                         (input)="onStringInput('dateSeance', $event)" [readonly]="readonly"/>
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>{{ 'CAHIER.PARAMEDICAL_POIDS_AVANT' | translate }}</mat-label>
                  <input
                    matInput
                    type="number"
                    min="0"
                    step="0.1"
                    [value]="formModel().poidsAvantKg ?? ''"
                    (input)="onNumberInput('poidsAvantKg', $event)"
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
                    [value]="formModel().poidsApresKg ?? ''"
                    (input)="onNumberInput('poidsApresKg', $event)"
                    [readonly]="readonly"
                  />
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>{{ 'CAHIER.PARAMEDICAL_TA_AVANT' | translate }}</mat-label>
                  <input
                    matInput
                    [value]="formModel().taAvant"
                    (input)="onStringInput('taAvant', $event)"
                    [readonly]="readonly"
                    placeholder="120/80"
                  />
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>{{ 'CAHIER.PARAMEDICAL_TA_APRES' | translate }}</mat-label>
                  <input
                    matInput
                    [value]="formModel().taApres"
                    (input)="onStringInput('taApres', $event)"
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
                    [value]="formModel().dureeMinutes ?? ''"
                    (input)="onNumberInput('dureeMinutes', $event)"
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
                    [value]="formModel().debitSangMlMin ?? ''"
                    (input)="onNumberInput('debitSangMlMin', $event)"
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
                    [value]="formModel().ultrafiltrationMl ?? ''"
                    (input)="onNumberInput('ultrafiltrationMl', $event)"
                    [readonly]="readonly"
                  />
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>{{ 'CAHIER.PARAMEDICAL_DIALYSAT' | translate }}</mat-label>
                  <input matInput [value]="formModel().typeDialysat" (input)="onStringInput('typeDialysat', $event)"
                         [readonly]="readonly"/>
                </mat-form-field>
              </div>
            </section>

            <section class="section-block section-wide">
              <h4>{{ 'CAHIER.PARAMEDICAL_SURVEILLANCE' | translate }}</h4>
              <div class="fields-grid">
                <mat-form-field appearance="outline">
                  <mat-label>{{ 'CAHIER.PARAMEDICAL_ANTICOAGULANT' | translate }}</mat-label>
                  <input matInput [value]="formModel().anticoagulant" (input)="onStringInput('anticoagulant', $event)"
                         [readonly]="readonly"/>
                </mat-form-field>
                <mat-form-field appearance="outline" class="full-width textarea-field">
                  <mat-label>{{ 'CAHIER.PARAMEDICAL_INCIDENTS' | translate }}</mat-label>
                  <textarea
                    matInput
                    rows="3"
                    [value]="formModel().incidents"
                    (input)="onStringInput('incidents', $event)"
                    [readonly]="readonly"
                  ></textarea>
                </mat-form-field>
              </div>
            </section>

            <section class="section-block section-wide">
              <h4>{{ 'CAHIER.PARAMEDICAL_ARTICLES' | translate }}</h4>
              <div class="articles-grid">
                <div
                  class="article-row"
                  *ngFor="let item of formModel().consommations; let i = index"
                  [attr.data-article-row]="item.articleId || ''"
                >
                  <mat-form-field appearance="outline">
                    <mat-label>{{ 'CAHIER.PARAMEDICAL_ARTICLE' | translate }}</mat-label>
                    <mat-select
                      [value]="item.articleId"
                      (selectionChange)="onConsommationArticleChange(i, $event.value)"
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
                      [value]="item.quantite"
                      (input)="onConsommationQuantiteInput(i, $event)"
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
              [disabled]="!formValid() || saving()"
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
  readonly formModel = signal<ParamedicalFormModel>({
    dateSeance: '',
    poidsAvantKg: null,
    poidsApresKg: null,
    taAvant: '',
    taApres: '',
    dureeMinutes: null,
    debitSangMlMin: null,
    ultrafiltrationMl: null,
    anticoagulant: '',
    typeDialysat: '',
    incidents: '',
    consommations: [],
  });
  readonly formValid = computed(() => {
    const model = this.formModel();
    if (!model.dateSeance) {
      return false;
    }
    if (!this.isNumberFieldValid(model.poidsAvantKg, 0)) return false;
    if (!this.isNumberFieldValid(model.poidsApresKg, 0)) return false;
    if (!this.isNumberFieldValid(model.dureeMinutes, 30)) return false;
    if (!this.isNumberFieldValid(model.debitSangMlMin, 0)) return false;
    if (!this.isNumberFieldValid(model.ultrafiltrationMl, 0)) return false;
    if ((model.taAvant ?? '').length > 32) return false;
    if ((model.taApres ?? '').length > 32) return false;
    if ((model.anticoagulant ?? '').length > 120) return false;
    if ((model.typeDialysat ?? '').length > 120) return false;
    if ((model.incidents ?? '').length > 800) return false;
    if (model.consommations.length === 0) {
      return false;
    }
    return model.consommations.every((item) => {
      const articleId = (item.articleId ?? '').trim();
      const quantite = Number(item.quantite ?? 0);
      return articleId.length > 0 && quantite >= 0.01;
    });
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
  private readonly injector = inject(Injector);
  private seanceId: string | null = null;
  private lastSeanceDate: string | null = null;

  ngOnInit(): void {
    this.formModel.update((model) => ({
      ...model,
      dateSeance: this.todayIsoDate(),
    }));
    if (this.formModel().consommations.length === 0) this.addConsommation();

    const centerId = this.appShell.currentCenterId();
    if (centerId) {
      this.articlesStore.ensureLoaded(centerId);
    }

    this.validChange.emit(this.formValid());
    effect(() => {
      const value = this.formModel();
      this.dataChange.emit(value);
      this.validChange.emit(this.formValid());
    }, {injector: this.injector});
  }

  save(): void {
    if (this.readonly || !this.formValid() || this.saving()) return;
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
            poidsAvantKg: this.formModel().poidsAvantKg,
            poidsApresKg: this.formModel().poidsApresKg,
            taAvant: this.nullableText(this.formModel().taAvant),
            taApres: this.nullableText(this.formModel().taApres),
            dureeMinutes: this.formModel().dureeMinutes,
            debitSangMlMin: this.formModel().debitSangMlMin,
            ultrafiltrationMl: this.formModel().ultrafiltrationMl,
            anticoagulant: this.nullableText(this.formModel().anticoagulant),
            typeDialysat: this.nullableText(this.formModel().typeDialysat),
            incidents: this.nullableText(this.formModel().incidents),
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
        this.dataChange.emit({...this.formModel(), response});
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
    this.validChange.emit(this.formValid());
  }

  addConsommation(): void {
    this.formModel.update((model) => ({
      ...model,
      consommations: [...model.consommations, {articleId: '', quantite: 1}],
    }));
  }

  removeConsommation(index: number): void {
    this.formModel.update((model) => ({
      ...model,
      consommations: model.consommations.filter((_, i) => i !== index),
    }));
    if (this.formModel().consommations.length === 0) {
      this.addConsommation();
    }
  }

  onConsommationArticleChange(index: number, articleId: string | null): void {
    this.patchConsommation(index, {articleId: articleId ?? ''});
  }

  onConsommationQuantiteInput(index: number, event: Event): void {
    const raw = (event.target as HTMLInputElement | null)?.value;
    const quantite = raw == null || raw === '' ? 0 : Number(raw);
    this.patchConsommation(index, {quantite: Number.isFinite(quantite) ? quantite : 0});
  }

  onStringInput(
    key: 'dateSeance' | 'taAvant' | 'taApres' | 'anticoagulant' | 'typeDialysat' | 'incidents',
    event: Event,
  ): void {
    const value = (event.target as HTMLInputElement | HTMLTextAreaElement | null)?.value ?? '';
    this.formModel.update((model) => ({
      ...model,
      [key]: value,
    }));
  }

  onNumberInput(
    key: 'poidsAvantKg' | 'poidsApresKg' | 'dureeMinutes' | 'debitSangMlMin' | 'ultrafiltrationMl',
    event: Event,
  ): void {
    const raw = (event.target as HTMLInputElement | null)?.value;
    const parsed = raw == null || raw === '' ? null : Number(raw);
    this.formModel.update((model) => ({
      ...model,
      [key]: Number.isFinite(parsed ?? NaN) ? parsed : null,
    }));
  }

  private async ensureSeanceId(centerId: string, patientId: string): Promise<string> {
    const selectedDate = this.formModel().dateSeance || this.todayIsoDate();
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
    return this.formModel().consommations
      .map((item) => ({
        articleId: String(item.articleId ?? '').trim(),
        quantite: Number(item.quantite ?? 0),
      }))
      .filter((item) => item.articleId.length > 0 && item.quantite > 0);
  }

  private patchConsommation(index: number, patch: Partial<ConsommationModel>): void {
    this.formModel.update((model) => ({
      ...model,
      consommations: model.consommations.map((item, i) => (i === index ? {...item, ...patch} : item)),
    }));
  }

  private isNumberFieldValid(value: number | null, min: number): boolean {
    if (value == null) {
      return true;
    }
    return Number(value) >= min;
  }

  private todayIsoDate(): string {
    return new Date().toISOString().slice(0, 10);
  }

  private nullableText(value: string | null | undefined): string | null {
    const text = (value ?? '').trim();
    return text ? text : null;
  }
}

type ConsommationModel = {
  articleId: string;
  quantite: number;
};

type ParamedicalFormModel = {
  dateSeance: string;
  poidsAvantKg: number | null;
  poidsApresKg: number | null;
  taAvant: string;
  taApres: string;
  dureeMinutes: number | null;
  debitSangMlMin: number | null;
  ultrafiltrationMl: number | null;
  anticoagulant: string;
  typeDialysat: string;
  incidents: string;
  consommations: ConsommationModel[];
};

