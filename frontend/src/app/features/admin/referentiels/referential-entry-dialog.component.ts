import {ChangeDetectionStrategy, Component, computed, inject} from '@angular/core';
import {MAT_DIALOG_DATA, MatDialogModule, MatDialogRef} from '@angular/material/dialog';
import {MatButtonModule} from '@angular/material/button';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {TranslateModule} from '@ngx-translate/core';
import {SignalForm, SignalFormValidator} from '../../../shared/forms/signal-form';
import {
  ReferentialEntry,
  ReferentialFieldDef,
  ReferentialKindDef
} from '../../../core/api/referential-admin-api.service';
import {ReferentialAdminStore, RefOption} from './state/referential-admin.store';
import {fieldValidators} from './referential-admin.util';
import {ReferentialLabels} from './referential-labels.service';

export interface ReferentialEntryDialogData {
  kind: ReferentialKindDef;
  entry: ReferentialEntry | null;
}

/** Création / modification d'une ligne de référentiel : formulaire généré à partir de la description des champs. */
@Component({
  selector: 'app-referential-entry-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatIconModule,
    MatProgressBarModule, TranslateModule],
  template: `
    <h2 mat-dialog-title>
      {{ (data.entry ? 'ADMIN.REFERENTIALS.EDIT_TITLE' : 'ADMIN.REFERENTIALS.CREATE_TITLE') | translate: {kind: kindLabel()} }}
    </h2>
    <mat-dialog-content>
      <form class="entry-form" (submit)="$event.preventDefault(); submit()" data-testid="referential-entry-form">
        @for (field of data.kind.fields; track field.key) {
          <mat-form-field appearance="outline" [class.full]="field.maxLength > 100">
            <mat-label>{{ fieldLabel(field) }}</mat-label>
            @switch (field.type) {
              @case ('ENUM') {
                <mat-select [value]="form.value()[field.key]" (selectionChange)="set(field.key, $event.value)"
                            (closed)="form.markTouched(field.key)" [attr.data-testid]="'field-' + field.key">
                  @for (option of field.allowedValues; track option) {
                    <mat-option [value]="option">{{ enumLabel(option) }}</mat-option>
                  }
                </mat-select>
              }
              @case ('REFERENCE') {
                <mat-select [value]="form.value()[field.key]" (selectionChange)="set(field.key, $event.value)"
                            (closed)="form.markTouched(field.key)" [attr.data-testid]="'field-' + field.key">
                  @for (option of optionsFor(field); track option.value) {
                    <mat-option [value]="option.value">{{ option.label }}</mat-option>
                  }
                </mat-select>
                @if (optionsFor(field).length === 0) {
                  <mat-hint>{{ 'ADMIN.REFERENTIALS.NO_REFERENCE_OPTIONS' | translate: {target: targetLabel(field)} }}</mat-hint>
                }
              }
              @default {
                <input matInput [value]="form.value()[field.key] ?? ''"
                       (input)="set(field.key, $any($event.target).value)"
                       (blur)="form.markTouched(field.key)" [attr.maxlength]="field.maxLength || null"
                       [attr.inputmode]="field.type === 'DECIMAL' ? 'decimal' : field.type === 'PHONE' ? 'tel' : null"
                       [attr.data-testid]="'field-' + field.key"/>
              }
            }
            @if (form.showError(field.key)) {
              <mat-error>{{
                  form.firstError(field.key)! | translate: {
                    field: fieldLabel(field),
                    max: field.maxLength
                  }
                }}
              </mat-error>
            }
            @if (serverIssue(field.key); as issue) {
              <mat-hint class="server-issue" data-testid="server-issue">{{ issue }}</mat-hint>
            }
          </mat-form-field>
        }
        <button type="submit" hidden aria-hidden="true" tabindex="-1"></button>
      </form>

      @if (store.error() && store.formIssues().length === 0) {
        <p class="feedback error" role="alert">{{ store.error()! | translate }}</p>
      }
      @if (store.saving()) {
        <mat-progress-bar mode="indeterminate"/>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-stroked-button type="button" (click)="dialogRef.close(false)">{{ 'COMMON.CANCEL' | translate }}
      </button>
      <button mat-flat-button color="primary" type="button" (click)="submit()" [disabled]="store.saving()"
              data-testid="referential-entry-save">
        <mat-icon>save</mat-icon>
        {{ 'COMMON.SAVE' | translate }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .entry-form {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 4px 16px;
      padding-top: 8px;
    }

    .entry-form .full {
      grid-column: 1 / -1;
    }

    .server-issue {
      color: #c62828;
    }

    .feedback.error {
      padding: 10px 14px;
      border-radius: 8px;
      background: #fce4ec;
      color: #c62828;
    }

    @media (max-width: 767px) {
      .entry-form {
        grid-template-columns: 1fr;
      }
    }
  `],
})
export class ReferentialEntryDialogComponent {
  protected readonly data = inject<ReferentialEntryDialogData>(MAT_DIALOG_DATA);
  protected readonly dialogRef = inject(MatDialogRef<ReferentialEntryDialogComponent, boolean>);
  protected readonly store = inject(ReferentialAdminStore);
  protected readonly form = new SignalForm<Record<string, string | null>>(
    this.initialValues(),
    Object.fromEntries(this.data.kind.fields.map((f) => [f.key, fieldValidators(f)])) as
      Record<string, SignalFormValidator<string>[]>,
  );
  private readonly labels = inject(ReferentialLabels);
  protected readonly kindLabel = computed(() => this.labels.kind(this.data.kind));

  constructor() {
    this.store.clearFormIssues();
  }

  protected set(key: string, value: string | null): void {
    this.form.set(key, value);
  }

  protected fieldLabel(field: ReferentialFieldDef): string {
    return this.labels.field(field);
  }

  protected enumLabel(value: string): string {
    return this.labels.enumValue(value);
  }

  protected targetLabel(field: ReferentialFieldDef): string {
    const target = this.store.kinds().find((k) => k.slug === field.reference);
    return target ? this.labels.kind(target) : (field.reference ?? '');
  }

  protected optionsFor(field: ReferentialFieldDef): RefOption[] {
    return field.reference ? (this.store.referenceOptions()[field.reference] ?? []) : [];
  }

  /** Anomalie renvoyée par le serveur pour ce champ (ex. code déjà utilisé), dans la langue courante. */
  protected serverIssue(key: string): string | null {
    const issue = this.store.formIssues().find((i) => i.field === key);
    return issue ? this.labels.issue(issue, this.data.kind) : null;
  }

  protected async submit(): Promise<void> {
    this.form.markAllTouched();
    if (this.form.invalid()) return;
    const values = Object.fromEntries(
      Object.entries(this.form.value()).map(([k, v]) => [k, typeof v === 'string' && v.trim() === '' ? null : v]),
    );
    if (await this.store.save(values, this.data.entry?.id)) {
      this.dialogRef.close(true);
    }
  }

  private initialValues(): Record<string, string | null> {
    return Object.fromEntries(this.data.kind.fields.map((f) => [
      f.key,
      this.data.entry ? (this.data.entry.values[f.key] ?? null) : (f.defaultValue ?? null),
    ]));
  }
}

