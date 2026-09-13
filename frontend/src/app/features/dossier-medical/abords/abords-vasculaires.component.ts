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
import {MatSlideToggleModule} from '@angular/material/slide-toggle';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatTooltipModule} from '@angular/material/tooltip';
import {TranslateModule} from '@ngx-translate/core';
import {SignalForm, requiredValidator} from '../../../shared/forms/signal-form';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {AbordVasculaire} from '../../../core/api/dossier-medical-api.service';
import {DossierMedicalAccessService} from '../dossier-medical-access.service';
import {AbordsVasculairesStore} from '../state/abords-vasculaires.store';
import {resolvePatientIdFromRoute} from '../dossier-medical-route.util';

interface AbordFormModel {
  typeAbord: string;
  cote: string;
  localisation: string;
  dateCreation: Date | string | null;
  dateFin: Date | string | null;
  actif: boolean;
  complications: string;
}

const TYPES_ABORD = ['FAV', 'PTFE', 'KT_TUNNELISE', 'KT_AIGU'] as const;
const COTES = ['GAUCHE', 'DROIT'] as const;

function toIsoDate(value: Date | string | null): string | null {
  if (!value) return null;
  if (typeof value === 'string') return value;
  const y = value.getFullYear();
  const m = String(value.getMonth() + 1).padStart(2, '0');
  const d = String(value.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

function emptyForm(): AbordFormModel {
  return {
    typeAbord: '',
    cote: '',
    localisation: '',
    dateCreation: null,
    dateFin: null,
    actif: true,
    complications: '',
  };
}

/** Historique des abords vasculaires du patient (FAV, PTFE, cathéters). */
@Component({
  selector: 'app-abords-vasculaires',
  standalone: true,
  imports: [
    MatTableModule,
    MatPaginatorModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatSlideToggleModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    TranslateModule,
  ],
  templateUrl: './abords-vasculaires.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './abords-vasculaires.component.css',
})
export class AbordsVasculairesComponent implements OnInit {
  protected readonly access = inject(DossierMedicalAccessService);
  protected readonly store = inject(AbordsVasculairesStore);
  protected readonly displayedColumns = ['typeAbord', 'cote', 'localisation', 'dateCreation', 'dateFin', 'actif', 'actions'];
  protected readonly typesAbord = TYPES_ABORD;
  protected readonly cotes = COTES;
  protected readonly formOpen = signal(false);
  protected readonly editingId = signal<string | null>(null);
  protected readonly form = new SignalForm<AbordFormModel>(emptyForm(), {
    typeAbord: [requiredValidator()],
  });
  private readonly route = inject(ActivatedRoute);
  protected readonly patientId = resolvePatientIdFromRoute(this.route);
  private readonly appShell = inject(AppShellStore);

  ngOnInit(): void {
    this.refresh(0, this.store.pageSize());
  }

  onPageChange(event: PageEvent): void {
    this.store.setPagination(event.pageIndex, event.pageSize);
  }

  openCreateForm(): void {
    this.editingId.set(null);
    this.form.reset(emptyForm());
    this.formOpen.set(true);
  }

  openEditForm(abord: AbordVasculaire): void {
    this.editingId.set(abord.id);
    this.form.reset({
      typeAbord: abord.typeAbord,
      cote: abord.cote ?? '',
      localisation: abord.localisation ?? '',
      dateCreation: abord.dateCreation,
      dateFin: abord.dateFin,
      actif: abord.actif,
      complications: abord.complications ?? '',
    });
    this.formOpen.set(true);
  }

  cancelForm(): void {
    this.formOpen.set(false);
    this.editingId.set(null);
  }

  onText<K extends keyof AbordFormModel>(key: K, value: AbordFormModel[K]): void {
    this.form.set(key, value);
  }

  save(): void {
    this.form.markAllTouched();
    if (!this.form.valid()) return;
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) return;
    const value = this.form.value();
    this.store.save({
      patientId: this.patientId,
      abordId: this.editingId(),
      payload: {
        centerId,
        typeAbord: value.typeAbord,
        cote: value.cote || null,
        localisation: value.localisation || null,
        dateCreation: toIsoDate(value.dateCreation),
        dateFin: toIsoDate(value.dateFin),
        actif: value.actif,
        complications: value.complications || null,
      },
    });
    this.formOpen.set(false);
    this.editingId.set(null);
  }

  remove(abord: AbordVasculaire): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) return;
    this.store.delete({centerId, patientId: this.patientId, abordId: abord.id});
  }

  private refresh(page: number, size: number): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) return;
    this.store.load({centerId, patientId: this.patientId, page, size});
  }
}
