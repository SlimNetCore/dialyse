import {ChangeDetectionStrategy, Component, effect, inject, OnInit} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatDatepickerModule} from '@angular/material/datepicker';
import {MatNativeDateModule} from '@angular/material/core';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {TranslateModule} from '@ngx-translate/core';
import {SignalForm} from '../../../shared/forms/signal-form';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {DossierMedicalAccessService} from '../dossier-medical-access.service';
import {DossierMedicalStore} from '../state/dossier-medical.store';
import {resolvePatientIdFromRoute} from '../dossier-medical-route.util';

interface SyntheseModel {
  nephropathieInitiale: string;
  dateMiseEnDialyse: Date | string | null;
  hepatiteBStatut: string;
  hepatiteCStatut: string;
  observationGlobale: string;
}

const HEPATITE_B_STATUTS = ['NEGATIF', 'PORTEUR', 'VACCINE', 'IMMUNE', 'INCONNU'] as const;
const HEPATITE_C_STATUTS = ['NEGATIF', 'POSITIF', 'TRAITE', 'INCONNU'] as const;

function toIsoDate(value: Date | string | null): string | null {
  if (!value) return null;
  if (typeof value === 'string') return value;
  const y = value.getFullYear();
  const m = String(value.getMonth() + 1).padStart(2, '0');
  const d = String(value.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

/** Dossier de base du patient : néphropathie initiale, mise en dialyse, sérologies hépatite B/C. */
@Component({
  selector: 'app-dossier-synthese',
  standalone: true,
  imports: [
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    TranslateModule,
  ],
  templateUrl: './dossier-synthese.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './dossier-synthese.component.css',
})
export class DossierSyntheseComponent implements OnInit {
  protected readonly access = inject(DossierMedicalAccessService);
  protected readonly store = inject(DossierMedicalStore);
  protected readonly hepatiteBStatuts = HEPATITE_B_STATUTS;
  protected readonly hepatiteCStatuts = HEPATITE_C_STATUTS;
  protected readonly form = new SignalForm<SyntheseModel>({
    nephropathieInitiale: '',
    dateMiseEnDialyse: null,
    hepatiteBStatut: '',
    hepatiteCStatut: '',
    observationGlobale: '',
  });
  private readonly route = inject(ActivatedRoute);
  protected readonly patientId = resolvePatientIdFromRoute(this.route);
  private readonly appShell = inject(AppShellStore);

  constructor() {
    // Le dossier arrive de manière asynchrone (chargement réseau) : dès qu'il est disponible,
    // on (ré)initialise le formulaire avec ses valeurs plutôt que de le lire une seule fois.
    effect(() => {
      const dossier = this.store.dossier();
      if (!dossier) return;
      this.form.reset({
        nephropathieInitiale: dossier.nephropathieInitiale ?? '',
        dateMiseEnDialyse: dossier.dateMiseEnDialyse,
        hepatiteBStatut: dossier.hepatiteBStatut ?? '',
        hepatiteCStatut: dossier.hepatiteCStatut ?? '',
        observationGlobale: dossier.observationGlobale ?? '',
      });
    });

    effect(() => {
      this.form.setDisabled(!this.access.canEdit());
    });
  }

  ngOnInit(): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) return;
    this.store.load({centerId, patientId: this.patientId});
  }

  onText<K extends keyof SyntheseModel>(key: K, value: string): void {
    this.form.set(key, value as SyntheseModel[K]);
  }

  onDate(key: 'dateMiseEnDialyse', value: Date | null): void {
    this.form.set(key, value);
  }

  save(): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId || !this.access.canEdit()) return;
    const value = this.form.value();
    this.store.save({
      patientId: this.patientId,
      payload: {
        centerId,
        nephropathieInitiale: value.nephropathieInitiale || null,
        dateMiseEnDialyse: toIsoDate(value.dateMiseEnDialyse),
        hepatiteBStatut: value.hepatiteBStatut || null,
        hepatiteCStatut: value.hepatiteCStatut || null,
        observationGlobale: value.observationGlobale || null,
      },
    });
  }
}
