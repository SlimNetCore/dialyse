import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
import {MAT_DIALOG_DATA, MatDialogModule, MatDialogRef} from '@angular/material/dialog';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {DatePipe} from '@angular/common';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, required} from '@angular/forms/signals';
import {TranslateModule} from '@ngx-translate/core';
import {StatutEquipement} from '../../../../core/api/gmao-api.service';
import {
  dureeLabel,
  dureeMinutes,
  ETATS_APRES_INTERVENTION,
  localInputToUtcIso,
  nowDatetimeLocal
} from '../../gmao-options.util';

export type FinishInterventionResult = {
  actions: string;
  etatEquipementApres: StatutEquipement;
  dateFin: string;
  cause: string | null;
};

export type FinishInterventionData = { dateDebut: string };

type FinishFormModel = { actions: string; etatEquipementApres: string; dateFin: string; cause: string };

/**
 * Clôture d'une intervention : actions réalisées et état de l'équipement après intervention.
 * « À réformer » n'est qu'une proposition — la réforme est décidée par la personne habilitée.
 */
@Component({
  selector: 'app-gmao-finish-intervention-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule, MatIconModule,
    FormRoot, FormField, TranslateModule, DatePipe,
  ],
  templateUrl: './finish-intervention-dialog.component.html',
  styleUrl: './finish-intervention-dialog.component.css',
})
export class GmaoFinishInterventionDialogComponent {
  protected readonly etats = ETATS_APRES_INTERVENTION;
  protected readonly formModel = signal<FinishFormModel>({
    actions: '',
    etatEquipementApres: '',
    dateFin: nowDatetimeLocal(),
    cause: ''
  });
  protected readonly finishForm = compatForm(this.formModel, (form) => {
    required(form.actions);
    required(form.etatEquipementApres);
    required(form.dateFin);
  });
  protected readonly data = inject<FinishInterventionData>(MAT_DIALOG_DATA);
  /** Fin avant le début : refusée (le serveur la refuse aussi). */
  protected readonly endBeforeStart = computed(() => {
    const fin = this.formModel().dateFin;
    return !!fin && new Date(fin).getTime() < new Date(this.data.dateDebut).getTime();
  });
  protected readonly duree = computed(() => dureeLabel(dureeMinutes(this.data.dateDebut, this.formModel().dateFin)));
  protected readonly canSave = computed(() =>
    !!this.formModel().actions.trim() && !!this.formModel().etatEquipementApres
    && !!this.formModel().dateFin && !this.endBeforeStart());
  protected readonly reformProposed = computed(() => this.formModel().etatEquipementApres === 'A_REFORMER');
  private readonly dialogRef = inject(MatDialogRef<GmaoFinishInterventionDialogComponent>);

  protected save(): void {
    if (!this.canSave()) return;
    const form = this.formModel();
    const result: FinishInterventionResult = {
      actions: form.actions.trim(),
      etatEquipementApres: form.etatEquipementApres as StatutEquipement,
      dateFin: localInputToUtcIso(form.dateFin),
      cause: form.cause.trim() || null,
    };
    this.dialogRef.close(result);
  }

  protected cancel(): void {
    this.dialogRef.close(null);
  }
}
