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
import {Serologie} from '../../../core/api/dossier-medical-api.service';
import {DossierMedicalAccessService} from '../dossier-medical-access.service';
import {SerologiesStore} from '../state/serologies.store';
import {resolvePatientIdFromRoute} from '../dossier-medical-route.util';

interface SerologieFormModel {
  marqueur: string;
  resultat: string;
  datePrelevement: Date | string | null;
  laboratoire: string;
  conduiteATenir: string;
}

const MARQUEURS = [
  'VIH_AC', 'AG_HBS', 'AC_HBS', 'AC_HBC', 'AC_VHC', 'ARN_VHC', 'TPHA',
  'CMV_IGG', 'CMV_IGM', 'TOXO_IGG', 'TOXO_IGM', 'EBV_IGG', 'EBV_IGM',
] as const;
const RESULTATS = ['NEGATIF', 'POSITIF', 'DOUTEUX', 'EN_COURS'] as const;

function toIsoDate(value: Date | string | null): string | null {
  if (!value) return null;
  if (typeof value === 'string') return value;
  const y = value.getFullYear();
  const m = String(value.getMonth() + 1).padStart(2, '0');
  const d = String(value.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

function emptyForm(): SerologieFormModel {
  return {marqueur: 'AG_HBS', resultat: 'NEGATIF', datePrelevement: new Date(), laboratoire: '', conduiteATenir: ''};
}

/** Résultats sérologiques du patient (VIH, hépatites B/C, syphilis...). */
@Component({
  selector: 'app-serologies',
  standalone: true,
  imports: [
    MatTableModule, MatPaginatorModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatDatepickerModule, MatNativeDateModule, MatProgressSpinnerModule, MatTooltipModule,
    MatChipsModule, TranslateModule,
  ],
  templateUrl: './serologies.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './serologies.component.css',
})
export class SerologiesComponent implements OnInit {
  protected readonly access = inject(DossierMedicalAccessService);
  protected readonly store = inject(SerologiesStore);
  protected readonly marqueurs = MARQUEURS;
  protected readonly resultats = RESULTATS;
  protected readonly displayedColumns = ['marqueur', 'resultat', 'datePrelevement', 'laboratoire', 'conduiteATenir'];
  protected readonly formOpen = signal(false);
  protected readonly form = new SignalForm<SerologieFormModel>(emptyForm(), {
    datePrelevement: [requiredValidator()],
  });
  private readonly route = inject(ActivatedRoute);
  protected readonly patientId = resolvePatientIdFromRoute(this.route);
  private readonly appShell = inject(AppShellStore);

  ngOnInit(): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) return;
    this.store.load({centerId, patientId: this.patientId, page: 0, size: this.store.pageSize()});
    this.store.loadSynthese({centerId, patientId: this.patientId});
  }

  onPageChange(event: PageEvent): void {
    this.store.setPagination(event.pageIndex, event.pageSize);
  }

  isPositif(serologie: Serologie): boolean {
    return serologie.resultat === 'POSITIF';
  }

  openCreateForm(): void {
    this.form.reset(emptyForm());
    this.formOpen.set(true);
  }

  cancelForm(): void {
    this.formOpen.set(false);
  }

  onValue<K extends keyof SerologieFormModel>(key: K, value: SerologieFormModel[K]): void {
    this.form.set(key, value);
  }

  save(): void {
    this.form.markAllTouched();
    if (!this.form.valid()) return;
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) return;
    const value = this.form.value();
    this.store.create({
      patientId: this.patientId,
      payload: {
        centerId,
        marqueur: value.marqueur,
        resultat: value.resultat,
        titre: null,
        unite: null,
        datePrelevement: toIsoDate(value.datePrelevement),
        laboratoire: value.laboratoire || null,
        dateProchainControle: null,
        conduiteATenir: value.conduiteATenir || null,
      },
    });
    this.formOpen.set(false);
  }
}
