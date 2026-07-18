import {ChangeDetectionStrategy, Component, computed, inject, OnInit, signal} from '@angular/core';
import {MatTableModule} from '@angular/material/table';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatChipsModule} from '@angular/material/chips';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatDatepickerModule} from '@angular/material/datepicker';
import {MatNativeDateModule} from '@angular/material/core';
import {MatSnackBar, MatSnackBarModule} from '@angular/material/snack-bar';
import {MatCardModule} from '@angular/material/card';
import {SlicePipe} from '@angular/common';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, required} from '@angular/forms/signals';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {AuthStore} from '../../../core/state/auth.store';
import {SearchableSelectComponent} from '../../../shared/searchable-select.component';
import {PecAdminStore} from './state/pec-admin.store';

@Component({
  selector: 'app-pec-admin',
  standalone: true,
  imports: [
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatSnackBarModule,
    MatCardModule,
    SlicePipe,
    TranslateModule,
    SearchableSelectComponent,
    FormRoot,
    FormField,
  ],
  templateUrl: './pec-admin.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './pec-admin.component.css',
})
export class PecAdminComponent implements OnInit {
  private readonly pecAdminStore = inject(PecAdminStore);
  readonly store = inject(AppShellStore);
  readonly auth = inject(AuthStore);
  private readonly snackBar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);

  readonly pecs = this.pecAdminStore.pecs;
  readonly loading = this.pecAdminStore.loading;
  readonly validatingPec = this.pecAdminStore.validatingPec;
  readonly forfaits = this.pecAdminStore.forfaits;
  readonly validateModel = signal({
    dateDebutEffectif: null as Date | null,
    dateFinEffectif: null as Date | null,
    forfaitEffectifId: null as string | null,
  });
  readonly validateForm = compatForm(this.validateModel, (form) => {
    required(form.dateDebutEffectif);
    required(form.dateFinEffectif);
  });
  readonly validateDisabled = computed(() => {
    const model = this.validateModel();
    return !model.dateDebutEffectif || !model.dateFinEffectif;
  });
  columns = ['patientId', 'dateDebutDemande', 'dateFinDemande', 'status', 'actions'];

  ngOnInit(): void {
    const cid = this.store.currentCenterId();
    if (cid) {
      // ✅ Utiliser le store pour charger
      this.pecAdminStore.loadPecs({centerId: cid});
      this.pecAdminStore.loadForfaits({centerId: cid});
    }
  }

  openValidate(pec: any): void {
    this.pecAdminStore.setValidatingPec(pec);
    this.validateModel.set({
      dateDebutEffectif: null,
      dateFinEffectif: null,
      forfaitEffectifId: null,
    });
  }

  setForfait(forfaitId: string | null): void {
    this.validateModel.update((model) => ({...model, forfaitEffectifId: forfaitId}));
  }

  cancelValidate(): void {
    this.pecAdminStore.setValidatingPec(null);
  }

  confirmValidate(): void {
    const pec = this.validatingPec();
    if (!pec || this.validateDisabled()) return;
    const cid = this.store.currentCenterId()!;
    const v = this.validateModel();
    const toDate = (d: any) => (d instanceof Date ? d.toISOString().slice(0, 10) : d);

    this.pecAdminStore.validatePec({
      pecId: pec.id,
      centerId: cid,
      userId: this.auth.username() ?? 'admin',
      dateDebutEffectif: toDate(v.dateDebutEffectif),
      dateFinEffectif: toDate(v.dateFinEffectif),
      forfaitEffectifId: v.forfaitEffectifId ?? undefined,
    });
    this.snackBar.open(this.translate.instant('PEC_ADMIN.VALIDATED_OK') || 'PEC validée', 'OK', {
      duration: 3000,
    });
  }

  closePec(pec: any): void {
    const cid = this.store.currentCenterId()!;
    this.pecAdminStore.closePec({
      pecId: pec.id,
      centerId: cid,
      userId: this.auth.username() ?? 'admin',
    });
    this.snackBar.open(this.translate.instant('PEC_ADMIN.CLOSED_OK') || 'PEC clôturée', 'OK', {
      duration: 3000,
    });
  }
}
