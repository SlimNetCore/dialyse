import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
import {MAT_DIALOG_DATA, MatDialogModule, MatDialogRef} from '@angular/material/dialog';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, required} from '@angular/forms/signals';
import {TranslateModule} from '@ngx-translate/core';
import {localInputToUtcIso, utcIsoToLocalInput} from '../../gmao-options.util';

export type RectifyInterventionData = { dateDebut: string };

/** Motif et nouvelle date de début éventuelle ; `dateDebut` est null si elle n'a pas été modifiée. */
export type RectifyInterventionResult = { motif: string; dateDebut: string | null };

type RectifyFormModel = { motif: string; dateDebut: string };

/**
 * Rectification d'une intervention terminée : elle est rouverte (en cours) pour correction. Le motif est
 * obligatoire et tracé dans la ligne de temps ; les lignes automatiques et la date de fin sont recalculées à la
 * nouvelle clôture.
 */
@Component({
  selector: 'app-gmao-rectify-intervention-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule,
    FormRoot, FormField, TranslateModule,
  ],
  templateUrl: './rectify-intervention-dialog.component.html',
  styleUrl: './rectify-intervention-dialog.component.css',
})
export class GmaoRectifyInterventionDialogComponent {
  // L'ordre des champs est significatif (initialisation séquentielle) : ne pas les réordonner.
  private readonly data = inject<RectifyInterventionData>(MAT_DIALOG_DATA);
  private readonly initialDebut = utcIsoToLocalInput(this.data.dateDebut);
  protected readonly formModel = signal<RectifyFormModel>({motif: '', dateDebut: this.initialDebut});
  protected readonly rectifyForm = compatForm(this.formModel, (form) => {
    required(form.motif);
    required(form.dateDebut);
  });
  protected readonly canSave = computed(() =>
    !!this.formModel().motif.trim() && !!this.formModel().dateDebut);
  private readonly dialogRef = inject(MatDialogRef<GmaoRectifyInterventionDialogComponent>);

  protected save(): void {
    if (!this.canSave()) return;
    const form = this.formModel();
    const result: RectifyInterventionResult = {
      motif: form.motif.trim(),
      dateDebut: form.dateDebut !== this.initialDebut ? localInputToUtcIso(form.dateDebut) : null,
    };
    this.dialogRef.close(result);
  }

  protected cancel(): void {
    this.dialogRef.close(null);
  }
}
