import {ChangeDetectionStrategy, Component, inject, OnInit, signal} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {MatCardModule} from '@angular/material/card';
import {MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatDatepickerModule} from '@angular/material/datepicker';
import {MatNativeDateModule} from '@angular/material/core';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatTooltipModule} from '@angular/material/tooltip';
import {TranslateModule} from '@ngx-translate/core';
import {requiredValidator, SignalForm} from '../../../shared/forms/signal-form';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {BackendApiService} from '../../../core/api/backend-api.service';
import {DossierMedicalApiService, Ordonnance} from '../../../core/api/dossier-medical-api.service';
import {DossierMedicalAccessService} from '../dossier-medical-access.service';
import {OrdonnancesStore} from '../state/ordonnances.store';
import {resolvePatientIdFromRoute} from '../dossier-medical-route.util';

interface LigneFormModel {
  code: string;
  codeDisplay: string;
  posologie: string;
  voie: string;
  dureeJours: number | null;
  quantite: number | null;
}

interface OrdonnanceFormModel {
  medecinId: string;
  datePrescription: Date | string | null;
  lignes: LigneFormModel[];
}

function emptyLigne(): LigneFormModel {
  return {code: '', codeDisplay: '', posologie: '', voie: '', dureeJours: null, quantite: null};
}

function emptyForm(): OrdonnanceFormModel {
  return {medecinId: '', datePrescription: new Date(), lignes: [emptyLigne()]};
}

function toIsoDate(value: Date | string | null): string | null {
  if (!value) return null;
  if (typeof value === 'string') return value;
  const y = value.getFullYear();
  const m = String(value.getMonth() + 1).padStart(2, '0');
  const d = String(value.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

/**
 * Ordonnances médicamenteuses du patient — indépendantes des paramètres de séance de dialyse.
 * Cycle de signature BROUILLON → SIGNEE → IMPRIMEE ; une fois signée, le contenu est immuable
 * (corriger impose d'annuler et de créer une nouvelle ordonnance).
 */
@Component({
  selector: 'app-ordonnances',
  standalone: true,
  imports: [
    MatCardModule, MatPaginatorModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule,
    MatDatepickerModule, MatNativeDateModule, MatProgressSpinnerModule, MatTooltipModule, TranslateModule,
  ],
  templateUrl: './ordonnances.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './ordonnances.component.css',
})
export class OrdonnancesComponent implements OnInit {
  protected readonly access = inject(DossierMedicalAccessService);
  protected readonly store = inject(OrdonnancesStore);
  protected readonly formOpen = signal(false);
  protected readonly form = new SignalForm<OrdonnanceFormModel>(emptyForm(), {
    datePrescription: [requiredValidator()],
  });
  protected readonly exporting = signal(false);

  private readonly route = inject(ActivatedRoute);
  protected readonly patientId = resolvePatientIdFromRoute(this.route);
  private readonly appShell = inject(AppShellStore);
  private readonly backendApi = inject(BackendApiService);
  private readonly dossierApi = inject(DossierMedicalApiService);

  ngOnInit(): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) return;
    this.store.load({centerId, patientId: this.patientId, page: 0, size: this.store.pageSize()});
  }

  onPageChange(event: PageEvent): void {
    this.store.setPagination(event.pageIndex, event.pageSize);
  }

  openCreateForm(): void {
    this.form.reset(emptyForm());
    this.formOpen.set(true);
  }

  cancelForm(): void {
    this.formOpen.set(false);
  }

  onValue<K extends keyof OrdonnanceFormModel>(key: K, value: OrdonnanceFormModel[K]): void {
    this.form.set(key, value);
  }

  addLigne(): void {
    this.form.set('lignes', [...this.form.value().lignes, emptyLigne()]);
  }

  removeLigne(index: number): void {
    const lignes = this.form.value().lignes.filter((_, i) => i !== index);
    this.form.set('lignes', lignes.length > 0 ? lignes : [emptyLigne()]);
  }

  onLigneValue<K extends keyof LigneFormModel>(index: number, key: K, value: LigneFormModel[K]): void {
    const lignes = this.form.value().lignes.map((l, i) => i === index ? {...l, [key]: value} : l);
    this.form.set('lignes', lignes);
  }

  onLigneNumber(index: number, key: 'dureeJours' | 'quantite', raw: string): void {
    const value = raw === '' ? null : Number(raw);
    this.onLigneValue(index, key, (Number.isNaN(value) ? null : value) as never);
  }

  save(): void {
    this.form.markAllTouched();
    if (!this.form.valid()) return;
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) return;
    const value = this.form.value();
    const lignes = value.lignes
      .filter((l) => l.posologie.trim() !== '' && (l.code.trim() !== '' || l.codeDisplay.trim() !== ''))
      .map((l) => ({
        codeSystem: l.code.trim() !== '' ? 'ATC' : null,
        code: l.code.trim() !== '' ? l.code.trim() : null,
        codeDisplay: l.codeDisplay || null,
        libelle: l.code.trim() === '' ? (l.codeDisplay || null) : null,
        posologie: l.posologie.trim(),
        voie: l.voie || null,
        dureeJours: l.dureeJours,
        quantite: l.quantite,
        instructions: null,
      }));
    if (lignes.length === 0) return;
    this.store.create({
      patientId: this.patientId,
      payload: {
        centerId,
        medecinId: value.medecinId || null,
        datePrescription: toIsoDate(value.datePrescription),
        lignes,
      },
    });
    this.formOpen.set(false);
  }

  signer(ordonnance: Ordonnance): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) return;
    this.store.transition({patientId: this.patientId, ordonnanceId: ordonnance.id, centerId, action: 'signer'});
  }

  annuler(ordonnance: Ordonnance): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) return;
    this.store.transition({patientId: this.patientId, ordonnanceId: ordonnance.id, centerId, action: 'annuler'});
  }

  imprimer(ordonnance: Ordonnance): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) return;
    this.backendApi.printDocument(centerId, 'ORDONNANCE', {ordonnanceId: ordonnance.id}).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        window.open(url, '_blank');
        if (ordonnance.statut === 'SIGNEE' && this.patientId) {
          this.store.transition({patientId: this.patientId, ordonnanceId: ordonnance.id, centerId, action: 'imprimer'});
        }
      },
    });
  }

  exportFhir(): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) return;
    this.exporting.set(true);
    this.dossierApi.exportFhir(centerId, this.patientId).subscribe({
      next: (bundle) => {
        const blob = new Blob([JSON.stringify(bundle, null, 2)], {type: 'application/fhir+json'});
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = `dossier-medical-${this.patientId}-fhir.json`;
        anchor.click();
        URL.revokeObjectURL(url);
        this.exporting.set(false);
      },
      error: () => this.exporting.set(false),
    });
  }
}
