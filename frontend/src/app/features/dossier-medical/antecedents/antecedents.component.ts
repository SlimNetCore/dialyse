import {ChangeDetectionStrategy, Component, inject, OnInit, signal} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {MatTableModule} from '@angular/material/table';
import {MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatDatepickerModule} from '@angular/material/datepicker';
import {MatNativeDateModule} from '@angular/material/core';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatTooltipModule} from '@angular/material/tooltip';
import {MatChipsModule} from '@angular/material/chips';
import {TranslateModule} from '@ngx-translate/core';
import {requiredValidator, SignalForm} from '../../../shared/forms/signal-form';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {Allergie, Antecedent} from '../../../core/api/dossier-medical-api.service';
import {DossierMedicalAccessService} from '../dossier-medical-access.service';
import {AntecedentsStore} from '../state/antecedents.store';
import {AllergiesStore} from '../state/allergies.store';
import {resolvePatientIdFromRoute} from '../dossier-medical-route.util';

interface AntecedentFormModel {
  type: string;
  codeSystem: string;
  code: string;
  codeDisplay: string;
  libelleLibre: string;
  dateDebut: Date | string | null;
  dateFin: Date | string | null;
  severite: string;
  note: string;
}

interface AllergieFormModel {
  codeSystem: string;
  code: string;
  codeDisplay: string;
  categorie: string;
  criticite: string;
  typeReaction: string;
  manifestations: string;
  dateConstatation: Date | string | null;
  statutVerification: string;
}

const TYPES_ANTECEDENT = ['MEDICAL', 'CHIRURGICAL', 'FAMILIAL', 'OBSTETRICAL', 'COMORBIDITE'] as const;
const CATEGORIES_ALLERGIE = ['MEDICAMENT', 'ALIMENT', 'ENVIRONNEMENT', 'BIOLOGIQUE'] as const;
const CRITICITES = ['BASSE', 'HAUTE'] as const;
const TYPES_REACTION = ['ALLERGIE', 'INTOLERANCE'] as const;

function toIsoDate(value: Date | string | null): string | null {
  if (!value) return null;
  if (typeof value === 'string') return value;
  const y = value.getFullYear();
  const m = String(value.getMonth() + 1).padStart(2, '0');
  const d = String(value.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

function emptyAntecedentForm(): AntecedentFormModel {
  return {
    type: 'MEDICAL', codeSystem: 'CIM10', code: '', codeDisplay: '', libelleLibre: '',
    dateDebut: new Date(), dateFin: null, severite: '', note: '',
  };
}

function emptyAllergieForm(): AllergieFormModel {
  return {
    codeSystem: 'LOCAL', code: '', codeDisplay: '', categorie: 'MEDICAMENT', criticite: 'BASSE',
    typeReaction: 'ALLERGIE', manifestations: '', dateConstatation: new Date(), statutVerification: 'SUSPECTEE',
  };
}

/** Antécédents/comorbidités et allergies du patient — regroupés dans un même onglet. */
@Component({
  selector: 'app-antecedents',
  standalone: true,
  imports: [
    MatTableModule, MatPaginatorModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatDatepickerModule, MatNativeDateModule, MatProgressSpinnerModule, MatTooltipModule,
    MatChipsModule, TranslateModule,
  ],
  templateUrl: './antecedents.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './antecedents.component.css',
})
export class AntecedentsComponent implements OnInit {
  protected readonly access = inject(DossierMedicalAccessService);
  protected readonly antecedentsStore = inject(AntecedentsStore);
  protected readonly allergiesStore = inject(AllergiesStore);
  protected readonly typesAntecedent = TYPES_ANTECEDENT;
  protected readonly categoriesAllergie = CATEGORIES_ALLERGIE;
  protected readonly criticites = CRITICITES;
  protected readonly typesReaction = TYPES_REACTION;
  protected readonly antecedentColumns = ['type', 'diagnostic', 'dateDebut', 'dateFin', 'statutClinique', 'actions'];
  protected readonly allergieColumns = ['substance', 'categorie', 'criticite', 'manifestations', 'actions'];
  protected readonly antecedentFormOpen = signal(false);
  protected readonly editingAntecedentId = signal<string | null>(null);
  protected readonly antecedentForm = new SignalForm<AntecedentFormModel>(emptyAntecedentForm(), {
    dateDebut: [requiredValidator()],
  });
  protected readonly allergieFormOpen = signal(false);
  protected readonly allergieForm = new SignalForm<AllergieFormModel>(emptyAllergieForm(), {
    code: [requiredValidator()],
  });
  private readonly route = inject(ActivatedRoute);
  protected readonly patientId = resolvePatientIdFromRoute(this.route);
  private readonly appShell = inject(AppShellStore);

  ngOnInit(): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) return;
    this.antecedentsStore.load({centerId, patientId: this.patientId, page: 0, size: this.antecedentsStore.pageSize()});
    this.allergiesStore.load({centerId, patientId: this.patientId, page: 0, size: this.allergiesStore.pageSize()});
    this.allergiesStore.loadCritiques({centerId, patientId: this.patientId});
  }

  onAntecedentPageChange(event: PageEvent): void {
    this.antecedentsStore.setPagination(event.pageIndex, event.pageSize);
  }

  onAllergiePageChange(event: PageEvent): void {
    this.allergiesStore.setPagination(event.pageIndex, event.pageSize);
  }

  openCreateAntecedentForm(): void {
    this.editingAntecedentId.set(null);
    this.antecedentForm.reset(emptyAntecedentForm());
    this.antecedentFormOpen.set(true);
  }

  openEditAntecedentForm(antecedent: Antecedent): void {
    this.editingAntecedentId.set(antecedent.id);
    this.antecedentForm.reset({
      type: antecedent.type,
      codeSystem: antecedent.codeSystem ?? 'CIM10',
      code: antecedent.code ?? '',
      codeDisplay: antecedent.codeDisplay ?? '',
      libelleLibre: antecedent.libelleLibre ?? '',
      dateDebut: antecedent.dateDebut,
      dateFin: antecedent.dateFin,
      severite: antecedent.severite ?? '',
      note: antecedent.note ?? '',
    });
    this.antecedentFormOpen.set(true);
  }

  cancelAntecedentForm(): void {
    this.antecedentFormOpen.set(false);
  }

  onAntecedentValue<K extends keyof AntecedentFormModel>(key: K, value: AntecedentFormModel[K]): void {
    this.antecedentForm.set(key, value);
  }

  saveAntecedent(): void {
    this.antecedentForm.markAllTouched();
    if (!this.antecedentForm.valid()) return;
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) return;
    const value = this.antecedentForm.value();
    this.antecedentsStore.save({
      patientId: this.patientId,
      antecedentId: this.editingAntecedentId(),
      payload: {
        centerId,
        type: value.type,
        codeSystem: value.code ? value.codeSystem : null,
        code: value.code || null,
        codeDisplay: value.code ? value.codeDisplay || null : null,
        libelleLibre: value.code ? null : (value.libelleLibre || null),
        dateDebut: toIsoDate(value.dateDebut) ?? new Date().toISOString().slice(0, 10),
        dateFin: toIsoDate(value.dateFin),
        statutClinique: this.editingAntecedentId() ? 'ACTIF' : null,
        severite: value.severite || null,
        note: value.note || null,
      },
    });
    this.antecedentFormOpen.set(false);
  }

  resoudreAntecedent(antecedent: Antecedent): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) return;
    this.antecedentsStore.resoudre({
      patientId: this.patientId, antecedentId: antecedent.id, centerId,
      dateResolution: new Date().toISOString().slice(0, 10),
    });
  }

  removeAntecedent(antecedent: Antecedent): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) return;
    this.antecedentsStore.delete({centerId, patientId: this.patientId, antecedentId: antecedent.id});
  }

  openCreateAllergieForm(): void {
    this.allergieForm.reset(emptyAllergieForm());
    this.allergieFormOpen.set(true);
  }

  cancelAllergieForm(): void {
    this.allergieFormOpen.set(false);
  }

  onAllergieValue<K extends keyof AllergieFormModel>(key: K, value: AllergieFormModel[K]): void {
    this.allergieForm.set(key, value);
  }

  saveAllergie(): void {
    this.allergieForm.markAllTouched();
    if (!this.allergieForm.valid()) return;
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) return;
    const value = this.allergieForm.value();
    this.allergiesStore.create({
      patientId: this.patientId,
      payload: {
        centerId,
        codeSystem: value.codeSystem,
        code: value.code,
        codeDisplay: value.codeDisplay || null,
        categorie: value.categorie,
        criticite: value.criticite,
        typeReaction: value.typeReaction,
        manifestations: value.manifestations || null,
        dateConstatation: toIsoDate(value.dateConstatation),
        statutVerification: value.statutVerification || null,
      },
    });
    this.allergieFormOpen.set(false);
  }

  removeAllergie(allergie: Allergie): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) return;
    this.allergiesStore.delete({centerId, patientId: this.patientId, allergieId: allergie.id});
  }
}
