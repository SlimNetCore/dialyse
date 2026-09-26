import {ChangeDetectionStrategy, Component, computed, inject, OnInit, signal} from '@angular/core';
import {Router, RouterLink} from '@angular/router';
import {MatCardModule} from '@angular/material/card';
import {MatTableModule} from '@angular/material/table';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatSnackBar} from '@angular/material/snack-bar';
import {MatTooltipModule} from '@angular/material/tooltip';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, required} from '@angular/forms/signals';
import {SocieteApiService} from '../../../core/api/societe-api.service';
import {SocietesStore} from './state/societes.store';
import {
  centresSummary,
  location,
  emptyCentreForm,
  emptySocieteForm,
  isCentreFormValid,
  isSocieteFormValid,
  toCentrePayload,
  toSocietePayload,
} from './societe.util';

/**
 * Liste des sociétés (SUPERADMIN) et création d'une société avec son premier centre. La modification et la
 * gestion des centres se font dans l'écran de détail.
 */
@Component({
  selector: 'app-societes',
  standalone: true,
  imports: [
    RouterLink,
    TranslateModule,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatTooltipModule,
    FormRoot,
    FormField,
  ],
  templateUrl: './societes.component.html',
  styleUrl: './societes.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SocietesComponent implements OnInit {
  protected readonly pageSizeOptions = [10, 20, 50, 100];
  protected readonly displayedColumns = ['code', 'raisonSociale', 'localisation', 'centres', 'actif', 'actions'];
  protected readonly showForm = signal(false);
  protected readonly saving = signal(false);
  protected readonly searchModel = signal({q: ''});
  protected readonly searchForm = compatForm(this.searchModel);
  protected readonly societeModel = signal(emptySocieteForm());
  protected readonly societeForm = compatForm(this.societeModel, (f) => {
    required(f.code);
    required(f.raisonSociale);
  });
  protected readonly centreModel = signal(emptyCentreForm());
  protected readonly centreForm = compatForm(this.centreModel, (f) => {
    required(f.code);
    required(f.nom);
  });
  protected readonly canSave = computed(
    () => isSocieteFormValid(this.societeModel()) && isCentreFormValid(this.centreModel()) && !this.saving(),
  );
  protected readonly summary = centresSummary;
  protected readonly location = location;
  private readonly api = inject(SocieteApiService);
  private readonly store = inject(SocietesStore);
  protected readonly rows = this.store.rows;
  protected readonly total = this.store.total;
  protected readonly pageIndex = this.store.pageIndex;
  protected readonly pageSize = this.store.pageSize;
  protected readonly loading = this.store.loading;
  protected readonly error = this.store.error;
  private readonly router = inject(Router);
  private readonly snack = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);

  ngOnInit(): void {
    void this.store.load();
  }

  protected search(): void {
    void this.store.setSearch(this.searchModel().q);
  }

  protected onPage(event: PageEvent): void {
    void this.store.setPagination(event.pageIndex, event.pageSize);
  }

  protected openForm(): void {
    this.societeModel.set(emptySocieteForm());
    this.centreModel.set(emptyCentreForm());
    this.showForm.set(true);
  }

  protected closeForm(): void {
    this.showForm.set(false);
  }

  protected save(): void {
    if (!this.canSave()) return;
    this.saving.set(true);
    this.api.create(toSocietePayload(this.societeModel()), toCentrePayload(this.centreModel())).subscribe({
      next: (societe) => {
        this.saving.set(false);
        this.showForm.set(false);
        this.snack.open(this.translate.instant('SOCIETES.CREATED_OK'), this.translate.instant('COMMON.OK'), {duration: 3000});
        void this.router.navigate(['/admin/societes', societe.id]);
      },
      error: (err) => {
        this.saving.set(false);
        this.snack.open(err?.error?.detail ?? this.translate.instant('SOCIETES.ACTION_ERROR'),
          this.translate.instant('COMMON.OK'), {duration: 6000});
      },
    });
  }
}
