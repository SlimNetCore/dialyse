import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
import {MAT_DIALOG_DATA, MatDialogModule, MatDialogRef} from '@angular/material/dialog';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, required} from '@angular/forms/signals';
import {DecimalPipe} from '@angular/common';
import {TranslateModule} from '@ngx-translate/core';
import {AjouterLigneCoutPayload} from '../../../../core/api/gmao-api.service';

/** Préremplissage : 'MAIN_OEUVRE' = saisie guidée de la main d'œuvre (heures × taux horaire). */
export type LigneCoutDialogData = {
  preset?: 'MAIN_OEUVRE';
  libelle?: string;
  heures?: number;
  tauxHoraire?: number | null;
};

type LigneCoutFormModel = {
  type: string;
  libelle: string;
  quantite: string;
  prixUnitaire: string;
};

function emptyForm(data: LigneCoutDialogData | null): LigneCoutFormModel {
  if (data?.preset === 'MAIN_OEUVRE') {
    return {
      type: 'MAIN_OEUVRE',
      libelle: data.libelle ?? '',
      quantite: data.heures ? String(data.heures) : '1',
      prixUnitaire: data.tauxHoraire != null ? String(data.tauxHoraire) : '',
    };
  }
  return {type: 'PIECE', libelle: '', quantite: '1', prixUnitaire: ''};
}

/**
 * Dialogue d'ajout d'une ligne de coût à une intervention GMAO (pièce, main d'œuvre, intervenant...) —
 * aide à la décision sur le coût réel de maintenance.
 */
@Component({
  selector: 'app-gmao-ligne-cout-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule, MatIconModule,
    FormRoot, FormField, TranslateModule, DecimalPipe,
  ],
  templateUrl: './ligne-cout-dialog.component.html',
  styleUrl: './ligne-cout-dialog.component.css',
})
export class GmaoLigneCoutDialogComponent {
  protected readonly data = inject<LigneCoutDialogData | null>(MAT_DIALOG_DATA, {optional: true});
  protected readonly mainOeuvre = this.data?.preset === 'MAIN_OEUVRE';
  protected readonly formModel = signal(emptyForm(this.data));
  /** Montant de la ligne (quantité × prix unitaire), affiché pour contrôle avant enregistrement. */
  protected readonly montant = computed(() =>
    (Number(this.formModel().quantite) || 0) * (Number(this.formModel().prixUnitaire) || 0));
  protected readonly ligneCoutForm = compatForm(this.formModel, (form) => {
    required(form.libelle);
    required(form.quantite);
    required(form.prixUnitaire);
  });
  protected readonly canSave = computed(() =>
    !!this.formModel().libelle.trim()
    && Number(this.formModel().quantite) > 0
    && Number(this.formModel().prixUnitaire) >= 0);
  private readonly dialogRef = inject(MatDialogRef<GmaoLigneCoutDialogComponent>);

  protected save(): void {
    if (!this.canSave()) return;
    const form = this.formModel();
    const payload: AjouterLigneCoutPayload = {
      type: form.type as never,
      libelle: form.libelle.trim(),
      quantite: Number(form.quantite),
      prixUnitaire: Number(form.prixUnitaire),
    };
    this.dialogRef.close(payload);
  }

  protected cancel(): void {
    this.dialogRef.close(null);
  }
}
