import {ChangeDetectionStrategy, Component, inject, OnInit, signal} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {MatCardModule} from '@angular/material/card';
import {MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatDatepickerModule} from '@angular/material/datepicker';
import {MatNativeDateModule} from '@angular/material/core';
import {MatSlideToggleModule} from '@angular/material/slide-toggle';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatChipsModule} from '@angular/material/chips';
import {MatTooltipModule} from '@angular/material/tooltip';
import {TranslateModule} from '@ngx-translate/core';
import {requiredValidator, SignalForm} from '../../../shared/forms/signal-form';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {DemandeExamen, ObservationBiologique} from '../../../core/api/dossier-medical-api.service';
import {DossierMedicalAccessService} from '../dossier-medical-access.service';
import {ExamensStore} from '../state/examens.store';
import {resolvePatientIdFromRoute} from '../dossier-medical-route.util';

interface LigneFormModel {
  code: string;
  codeDisplay: string;
}

interface DemandeFormModel {
  categorie: string;
  urgent: boolean;
  dateDemande: Date | string | null;
  motif: string;
  lignes: LigneFormModel[];
}

interface ObservationFormModel {
  code: string;
  codeDisplay: string;
  valeurNum: number | null;
  unite: string;
  valeurTexte: string;
  datePrelevement: Date | string | null;
}

const CATEGORIES = ['BIOLOGIE', 'IMAGERIE', 'FONCTIONNEL', 'ANAPATH'] as const;

function toIsoDate(value: Date | string | null): string | null {
  if (!value) return null;
  if (typeof value === 'string') return value;
  const y = value.getFullYear();
  const m = String(value.getMonth() + 1).padStart(2, '0');
  const d = String(value.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

function emptyDemandeForm(): DemandeFormModel {
  return {
    categorie: 'BIOLOGIE',
    urgent: false,
    dateDemande: new Date(),
    motif: '',
    lignes: [{code: '', codeDisplay: ''}]
  };
}

function emptyObservationForm(): ObservationFormModel {
  return {code: '', codeDisplay: '', valeurNum: null, unite: '', valeurTexte: '', datePrelevement: new Date()};
}

/** Demandes d'examen du patient et saisie de leurs résultats (observations codées LOINC). */
@Component({
  selector: 'app-examens',
  standalone: true,
  imports: [
    MatCardModule, MatPaginatorModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatDatepickerModule, MatNativeDateModule, MatSlideToggleModule, MatProgressSpinnerModule,
    MatChipsModule, MatTooltipModule, TranslateModule,
  ],
  templateUrl: './examens.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './examens.component.css',
})
export class ExamensComponent implements OnInit {
  protected readonly access = inject(DossierMedicalAccessService);
  protected readonly store = inject(ExamensStore);
  protected readonly categories = CATEGORIES;
  protected readonly formOpen = signal(false);
  protected readonly form = new SignalForm<DemandeFormModel>(emptyDemandeForm(), {
    dateDemande: [requiredValidator()],
  });
  protected readonly expandedDemandeId = signal<string | null>(null);
  protected readonly observationFormOpen = signal(false);
  protected readonly observationForm = new SignalForm<ObservationFormModel>(emptyObservationForm(), {
    code: [requiredValidator()],
    datePrelevement: [requiredValidator()],
  });
  private readonly route = inject(ActivatedRoute);
  protected readonly patientId = resolvePatientIdFromRoute(this.route);
  private readonly appShell = inject(AppShellStore);

  ngOnInit(): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) return;
    this.store.load({centerId, patientId: this.patientId, page: 0, size: this.store.pageSize()});
  }

  onPageChange(event: PageEvent): void {
    this.store.setPagination(event.pageIndex, event.pageSize);
  }

  openCreateForm(): void {
    this.form.reset(emptyDemandeForm());
    this.formOpen.set(true);
  }

  cancelForm(): void {
    this.formOpen.set(false);
  }

  onValue<K extends keyof DemandeFormModel>(key: K, value: DemandeFormModel[K]): void {
    this.form.set(key, value);
  }

  addLigne(): void {
    this.form.set('lignes', [...this.form.value().lignes, {code: '', codeDisplay: ''}]);
  }

  removeLigne(index: number): void {
    const lignes = this.form.value().lignes.filter((_, i) => i !== index);
    this.form.set('lignes', lignes.length > 0 ? lignes : [{code: '', codeDisplay: ''}]);
  }

  onLigneValue(index: number, key: keyof LigneFormModel, value: string): void {
    const lignes = this.form.value().lignes.map((l, i) => i === index ? {...l, [key]: value} : l);
    this.form.set('lignes', lignes);
  }

  save(): void {
    this.form.markAllTouched();
    if (!this.form.valid()) return;
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) return;
    const value = this.form.value();
    const lignes = value.lignes
      .filter((l) => l.code.trim() !== '')
      .map((l) => ({
        codeSystem: 'LOINC',
        code: l.code.trim(),
        codeDisplay: l.codeDisplay || null,
        libelle: null,
        commentaire: null
      }));
    if (lignes.length === 0) return;
    this.store.create({
      patientId: this.patientId,
      payload: {
        centerId,
        prescripteurId: null,
        dateDemande: toIsoDate(value.dateDemande),
        categorie: value.categorie,
        urgent: value.urgent,
        motif: value.motif || null,
        lignes,
      },
    });
    this.formOpen.set(false);
  }

  transition(demande: DemandeExamen, action: 'preleve' | 'resultat-disponible' | 'valider' | 'annuler'): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) return;
    if (action === 'valider') {
      const conclusion = window.prompt('Conclusion de l\'examen ?') ?? '';
      this.store.transition({patientId: this.patientId, demandeId: demande.id, centerId, action, conclusion});
      return;
    }
    this.store.transition({patientId: this.patientId, demandeId: demande.id, centerId, action});
  }

  toggleExpand(demande: DemandeExamen): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) return;
    if (this.expandedDemandeId() === demande.id) {
      this.expandedDemandeId.set(null);
      this.observationFormOpen.set(false);
      return;
    }
    this.expandedDemandeId.set(demande.id);
    this.observationFormOpen.set(false);
    this.store.loadObservations({centerId, patientId: this.patientId, demandeId: demande.id});
  }

  observationsFor(demandeId: string): ObservationBiologique[] {
    return this.store.observationsParDemande()[demandeId] ?? [];
  }

  openObservationForm(): void {
    this.observationForm.reset(emptyObservationForm());
    this.observationFormOpen.set(true);
  }

  cancelObservationForm(): void {
    this.observationFormOpen.set(false);
  }

  onObservationValue<K extends keyof ObservationFormModel>(key: K, value: ObservationFormModel[K]): void {
    this.observationForm.set(key, value);
  }

  saveObservation(demande: DemandeExamen): void {
    this.observationForm.markAllTouched();
    if (!this.observationForm.valid()) return;
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) return;
    const value = this.observationForm.value();
    const hasNum = value.valeurNum !== null;
    this.store.createObservation({
      patientId: this.patientId,
      demandeId: demande.id,
      centerId,
      payload: {
        centerId,
        demandeExamenId: demande.id,
        codeSystem: 'LOINC',
        code: value.code.trim(),
        codeDisplay: value.codeDisplay || null,
        valeurNum: hasNum ? value.valeurNum : null,
        unite: hasNum ? (value.unite || null) : null,
        valeurTexte: hasNum ? null : (value.valeurTexte || null),
        datePrelevement: toIsoDate(value.datePrelevement),
        statut: null,
      },
    });
    if (demande.statut === 'PRELEVE') {
      this.transition(demande, 'resultat-disponible');
    }
    this.observationFormOpen.set(false);
  }
}
