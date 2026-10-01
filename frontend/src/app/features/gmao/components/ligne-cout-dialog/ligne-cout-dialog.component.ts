import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
import {MatDialogModule, MatDialogRef} from '@angular/material/dialog';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, required} from '@angular/forms/signals';
import {TranslateModule} from '@ngx-translate/core';
import {AjouterLigneCoutPayload} from '../../../../core/api/gmao-api.service';

type LigneCoutFormModel = {
  type: string;
  libelle: string;
  quantite: string;
  prixUnitaire: string;
};

function emptyForm(): LigneCoutFormModel {
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
    FormRoot, FormField, TranslateModule,
  ],
  templateUrl: './ligne-cout-dialog.component.html',
  styleUrl: './ligne-cout-dialog.component.css',
})
export class GmaoLigneCoutDialogComponent {
  protected readonly formModel = signal(emptyForm());
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
