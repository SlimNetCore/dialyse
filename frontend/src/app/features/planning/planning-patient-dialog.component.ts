import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
import {DatePipe} from '@angular/common';
import {RouterLink} from '@angular/router';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, required} from '@angular/forms/signals';
import {MAT_DIALOG_DATA, MatDialogModule, MatDialogRef} from '@angular/material/dialog';
import {MatButtonModule} from '@angular/material/button';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {TranslateModule} from '@ngx-translate/core';
import {AbsenceSemaine, MOTIFS_ABSENCE, MotifAbsence} from '../../core/api/absence-patient-api.service';
import {OccupantPlanning} from '../../core/api/planning-api.service';
import {PlanningStore} from './planning.store';

/** Données transmises à la fenêtre : la séance cliquée du planning. */
export interface PlanningPatientDialogData {
  occupant: OccupantPlanning;
  date: string;
  salleNom: string;
  creneauLibelle: string;
  absence: AbsenceSemaine | undefined;
  /** Séance validée par l'infirmier : le patient était présent, aucune absence possible. */
  realisee: boolean;
  /** Jour ouvert, passé ou du jour : l'absence peut être déclarée. */
  peutDeclarer: boolean;
}

/**
 * Détail d'un patient depuis le planning : séance concernée, liens vers sa fiche et son dossier, et déclaration de
 * l'absence à cette séance (motif obligatoire). Une fois déclarée, la séance est considérée absente et change de
 * couleur dans le planning.
 */
@Component({
  selector: 'app-planning-patient-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe, RouterLink, MatDialogModule, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule,
    MatSelectModule, FormRoot, FormField, TranslateModule,
  ],
  templateUrl: './planning-patient-dialog.component.html',
  styleUrl: './planning-patient-dialog.component.css',
})
export class PlanningPatientDialogComponent {
  protected readonly data = inject<PlanningPatientDialogData>(MAT_DIALOG_DATA);
  protected readonly store = inject(PlanningStore);
  protected readonly motifs = MOTIFS_ABSENCE;
  protected readonly model = signal({motif: '' as MotifAbsence | '', commentaire: ''});
  protected readonly form = compatForm(this.model, (f) => {
    required(f.motif);
  });
  protected readonly commentaireRequis = computed(() => this.model().motif === 'AUTRE');
  protected readonly canSave = computed(() =>
    this.form().valid() && !this.store.absenceSaving()
    && (!this.commentaireRequis() || this.model().commentaire.trim().length > 0));
  private readonly ref = inject(MatDialogRef<PlanningPatientDialogComponent>);

  constructor() {
    this.store.clearAbsenceError();
  }

  protected async declarer(): Promise<void> {
    if (!this.canSave()) return;
    const m = this.model();
    const ok = await this.store.declarerAbsence({
      patientId: this.data.occupant.patientId,
      dateSeance: this.data.date,
      motif: m.motif as MotifAbsence,
      commentaire: m.commentaire.trim() || null,
    });
    if (ok) this.ref.close(true);
  }
}
