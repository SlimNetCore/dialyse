import {ChangeDetectionStrategy, Component, inject, signal} from '@angular/core';
import {MAT_DIALOG_DATA, MatDialogModule, MatDialogRef} from '@angular/material/dialog';
import {MatButtonModule} from '@angular/material/button';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, maxLength, minLength, required} from '@angular/forms/signals';
import {TranslateModule} from '@ngx-translate/core';

export interface SupprimerSeanceDialogData {
  patient: string;
  date: string;
  statut: string;
}

/** Bornes du motif de suppression (identiques au serveur). */
export const MOTIF_SUPPRESSION = {min: 5, max: 500} as const;

/**
 * Confirmation de la suppression d'une séance par l'administrateur : rappelle la séance et ses conséquences (stock
 * restitué, volets effacés) et exige un motif. Se ferme avec le motif saisi, ou sans valeur si l'on annule.
 */
@Component({
  selector: 'app-supprimer-seance-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatDialogModule, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule, FormRoot, FormField,
    TranslateModule],
  template: `
    <h2 mat-dialog-title>
      <mat-icon aria-hidden="true" class="warn">delete_forever</mat-icon>
      {{ 'SEANCES.DELETE.TITLE' | translate }}
    </h2>
    <form (submit)="confirmer(); $event.preventDefault()" [formRoot]="motifForm" data-testid="seance-delete-form">
      <mat-dialog-content class="content">
        <p>{{ 'SEANCES.DELETE.MESSAGE' | translate: {patient: data.patient, date: data.date} }}</p>
        <p class="hint">{{ 'SEANCES.DELETE.CONSEQUENCES' | translate }}</p>
        <mat-form-field appearance="outline" class="full">
          <mat-label>{{ 'SEANCES.DELETE.MOTIF' | translate }}</mat-label>
          <textarea [formField]="motifForm.motif" data-testid="seance-delete-motif" matInput
                    rows="3"></textarea>
          <mat-hint>{{ 'SEANCES.DELETE.MOTIF_HINT' | translate: {min: bornes.min, max: bornes.max} }}</mat-hint>
        </mat-form-field>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-dialog-close mat-stroked-button type="button">{{ 'COMMON.CANCEL' | translate }}</button>
        <button [disabled]="!motifForm().valid()" color="warn" data-testid="seance-delete-confirm" mat-flat-button
                type="submit">
          {{ 'SEANCES.DELETE.CONFIRM' | translate }}
        </button>
      </mat-dialog-actions>
    </form>
  `,
  styles: [`
    .content { display: grid; gap: 8px; max-width: min(92vw, 520px); }
    .full { width: 100%; }
    .hint { margin: 0; color: var(--app-muted, #5f6b7a); }
    .warn { vertical-align: middle; color: var(--mat-sys-error, #b3261e); }
  `],
})
export class SupprimerSeanceDialogComponent {
  protected readonly data = inject<SupprimerSeanceDialogData>(MAT_DIALOG_DATA);
  protected readonly bornes = MOTIF_SUPPRESSION;
  protected readonly motifModel = signal({motif: ''});
  protected readonly motifForm = compatForm(this.motifModel, (form) => {
    required(form.motif);
    minLength(form.motif, MOTIF_SUPPRESSION.min);
    maxLength(form.motif, MOTIF_SUPPRESSION.max);
  });
  private readonly ref = inject(MatDialogRef<SupprimerSeanceDialogComponent, string>);

  protected confirmer(): void {
    const motif = this.motifModel().motif.trim();
    if (!this.motifForm().valid() || motif.length < MOTIF_SUPPRESSION.min) return;
    this.ref.close(motif);
  }
}
