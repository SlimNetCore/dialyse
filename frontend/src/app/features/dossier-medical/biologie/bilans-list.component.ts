import {ChangeDetectionStrategy, Component, inject, OnInit, signal} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {MatTableModule} from '@angular/material/table';
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
import {ResultatAnalyse} from '../../../core/api/dossier-medical-api.service';
import {DossierMedicalAccessService} from '../dossier-medical-access.service';
import {AnalysesStore} from '../state/analyses.store';
import {resolvePatientIdFromRoute} from '../dossier-medical-route.util';

interface AnalyseFormModel {
  datePrelevement: Date | string | null;
  hbGDl: number | null;
  htPct: number | null;
  plaquettes: number | null;
  ferritineNgMl: number | null;
  cstfPct: number | null;
  ureePreMgDl: number | null;
  ureePostMgDl: number | null;
  creatinineMgDl: number | null;
  ktVMensuel: number | null;
  phosphoreMgDl: number | null;
  calciumMgDl: number | null;
  pthPgMl: number | null;
  albumineGDl: number | null;
  crpMgL: number | null;
}

/**
 * Seuils indicatifs (repère visuel uniquement, pas une règle métier codée en domaine) —
 * la logique clinique complète (KDIGO, alertes) est du ressort d'une phase ultérieure.
 */
const REFERENCE_RANGES: Partial<Record<keyof AnalyseFormModel, [number, number]>> = {
  hbGDl: [10, 12],
  ferritineNgMl: [200, 500],
  ktVMensuel: [1.2, 2],
  phosphoreMgDl: [2.5, 4.5],
  calciumMgDl: [8.4, 10.2],
  pthPgMl: [130, 600],
  albumineGDl: [4, 5.5],
  crpMgL: [0, 5],
};

function isOutOfRange(key: keyof AnalyseFormModel, value: number | null): boolean {
  if (value === null) return false;
  const range = REFERENCE_RANGES[key];
  if (!range) return false;
  return value < range[0] || value > range[1];
}

function toIsoDate(value: Date | string | null): string | null {
  if (!value) return null;
  if (typeof value === 'string') return value;
  const y = value.getFullYear();
  const m = String(value.getMonth() + 1).padStart(2, '0');
  const d = String(value.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

function emptyForm(): AnalyseFormModel {
  return {
    datePrelevement: new Date(),
    hbGDl: null,
    htPct: null,
    plaquettes: null,
    ferritineNgMl: null,
    cstfPct: null,
    ureePreMgDl: null,
    ureePostMgDl: null,
    creatinineMgDl: null,
    ktVMensuel: null,
    phosphoreMgDl: null,
    calciumMgDl: null,
    pthPgMl: null,
    albumineGDl: null,
    crpMgL: null,
  };
}

/** Résultats de bilans biologiques du patient (NFS, bilan martial, adéquation, phospho-calcique...). */
@Component({
  selector: 'app-bilans-list',
  standalone: true,
  imports: [
    MatTableModule,
    MatPaginatorModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    TranslateModule,
  ],
  templateUrl: './bilans-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './bilans-list.component.css',
})
export class BilansListComponent implements OnInit {
  protected readonly access = inject(DossierMedicalAccessService);
  protected readonly store = inject(AnalysesStore);
  protected readonly displayedColumns = [
    'datePrelevement', 'hbGDl', 'htPct', 'ferritineNgMl', 'ktVMensuel',
    'phosphoreMgDl', 'calciumMgDl', 'pthPgMl', 'albumineGDl', 'crpMgL', 'actions',
  ];
  protected readonly formOpen = signal(false);
  protected readonly editingId = signal<string | null>(null);
  protected readonly form = new SignalForm<AnalyseFormModel>(emptyForm(), {
    datePrelevement: [requiredValidator()],
  });
  private readonly route = inject(ActivatedRoute);
  protected readonly patientId = resolvePatientIdFromRoute(this.route);
  private readonly appShell = inject(AppShellStore);

  ngOnInit(): void {
    this.refresh();
  }

  onPageChange(event: PageEvent): void {
    this.store.setPagination(event.pageIndex, event.pageSize);
  }

  isOutOfRange(row: ResultatAnalyse, key: keyof AnalyseFormModel): boolean {
    return isOutOfRange(key, (row as unknown as Record<string, number | null>)[key] ?? null);
  }

  openCreateForm(): void {
    this.editingId.set(null);
    this.form.reset(emptyForm());
    this.formOpen.set(true);
  }

  openEditForm(analyse: ResultatAnalyse): void {
    this.editingId.set(analyse.id);
    this.form.reset({
      datePrelevement: analyse.datePrelevement,
      hbGDl: analyse.hbGDl,
      htPct: analyse.htPct,
      plaquettes: analyse.plaquettes,
      ferritineNgMl: analyse.ferritineNgMl,
      cstfPct: analyse.cstfPct,
      ureePreMgDl: analyse.ureePreMgDl,
      ureePostMgDl: analyse.ureePostMgDl,
      creatinineMgDl: analyse.creatinineMgDl,
      ktVMensuel: analyse.ktVMensuel,
      phosphoreMgDl: analyse.phosphoreMgDl,
      calciumMgDl: analyse.calciumMgDl,
      pthPgMl: analyse.pthPgMl,
      albumineGDl: analyse.albumineGDl,
      crpMgL: analyse.crpMgL,
    });
    this.formOpen.set(true);
  }

  cancelForm(): void {
    this.formOpen.set(false);
    this.editingId.set(null);
  }

  onValue<K extends keyof AnalyseFormModel>(key: K, value: AnalyseFormModel[K]): void {
    this.form.set(key, value);
  }

  onNumber<K extends keyof AnalyseFormModel>(key: K, raw: string): void {
    const value = raw === '' ? null : Number(raw);
    this.form.set(key, (Number.isNaN(value) ? null : value) as AnalyseFormModel[K]);
  }

  save(): void {
    this.form.markAllTouched();
    if (!this.form.valid()) return;
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) return;
    const value = this.form.value();
    this.store.save({
      patientId: this.patientId,
      analyseId: this.editingId(),
      payload: {
        centerId,
        datePrelevement: toIsoDate(value.datePrelevement) ?? new Date().toISOString().slice(0, 10),
        hbGDl: value.hbGDl,
        htPct: value.htPct,
        plaquettes: value.plaquettes,
        ferritineNgMl: value.ferritineNgMl,
        cstfPct: value.cstfPct,
        epoEndogeneMuiMl: null,
        ureePreMgDl: value.ureePreMgDl,
        ureePostMgDl: value.ureePostMgDl,
        creatinineMgDl: value.creatinineMgDl,
        ktVMensuel: value.ktVMensuel,
        phosphoreMgDl: value.phosphoreMgDl,
        calciumMgDl: value.calciumMgDl,
        pthPgMl: value.pthPgMl,
        albumineGDl: value.albumineGDl,
        proteinesGDl: null,
        crpMgL: value.crpMgL,
      },
    });
    this.formOpen.set(false);
    this.editingId.set(null);
  }

  remove(analyse: ResultatAnalyse): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) return;
    this.store.delete({centerId, patientId: this.patientId, analyseId: analyse.id});
  }

  private refresh(): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) return;
    this.store.load({centerId, patientId: this.patientId, page: this.store.pageIndex(), size: this.store.pageSize()});
  }
}
