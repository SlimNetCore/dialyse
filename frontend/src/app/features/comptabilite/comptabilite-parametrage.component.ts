import {ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked} from '@angular/core';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, maxLength, readonly, required} from '@angular/forms/signals';
import {RouterLink} from '@angular/router';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatTableModule} from '@angular/material/table';
import {MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatSlideToggleModule} from '@angular/material/slide-toggle';
import {MatTooltipModule} from '@angular/material/tooltip';
import {MatDialog} from '@angular/material/dialog';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {JournalItem, OperationComptable, OPERATIONS_COMPTABLES} from '../../core/api/comptabilite-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';
import {ConfirmDialogComponent} from '../../shared/confirm-dialog.component';
import {ComptabiliteParametrageStore} from './state/comptabilite-parametrage.store';
import {PlanComptableStore} from './state/plan-comptable.store';
import {CompteSelectComponent} from './compte-select.component';
import {ModelesPieceComponent} from './modeles-piece.component';
import {PlanComptableComponent} from './plan-comptable.component';
import {
  CODE_JOURNAL_MAX,
  CompteCle,
  COMPTES_GENERAUX,
  COMPTES_OBLIGATOIRES,
  COMPTES_STOCK,
  comptesDe,
  comptesInvalides,
  comptesVides,
  JournauxParOperation,
  journalValide,
  LIBELLE_JOURNAL_MAX,
  normaliserCodeJournal,
  operationsDuJournal,
  operationsSansJournal,
  periodeParDefaut,
  periodeValide,
} from './comptabilite-parametrage.util';

function journalVide() {
  return {code: '', libelle: ''};
}

/**
 * Paramétrage comptable du centre : ses journaux, le journal de chaque opération, ses comptes (ventes, clients,
 * trésorerie, stock) et la comptabilisation du stock à la demande. Réservé à l'administrateur du centre.
 */
@Component({
  selector: 'app-comptabilite-parametrage',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink, MatCardModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule, MatIconModule,
    MatTableModule, MatPaginatorModule, MatProgressBarModule, MatSlideToggleModule, MatTooltipModule,
    FormRoot, FormField, TranslateModule, CompteSelectComponent, PlanComptableComponent, ModelesPieceComponent,
  ],
  templateUrl: './comptabilite-parametrage.component.html',
  styleUrl: './comptabilite-parametrage.component.css',
})
export class ComptabiliteParametrageComponent {
  protected readonly store = inject(ComptabiliteParametrageStore);
  /** Plan comptable du centre : tout compte se choisit dans cette liste. */
  protected readonly plan = inject(PlanComptableStore);
  protected readonly columns = ['code', 'libelle', 'actif', 'actions'];
  protected readonly operations = OPERATIONS_COMPTABLES;
  protected readonly comptesGeneraux = COMPTES_GENERAUX;
  protected readonly comptesStock = COMPTES_STOCK;

  // ─── Journaux ────────────────────────────────────────────────────────────
  /** Code du journal dont on modifie le libellé ; `null` = création. */
  protected readonly editingCode = signal<string | null>(null);
  protected readonly journalModel = signal(journalVide());
  protected readonly journalForm = compatForm(this.journalModel, (form) => {
    required(form.code);
    required(form.libelle);
    // le code identifie le journal : seul le libellé se modifie
    readonly(form.code, {when: () => !!this.editingCode()});
    maxLength(form.code, CODE_JOURNAL_MAX);
    maxLength(form.libelle, LIBELLE_JOURNAL_MAX);
  });
  protected readonly canSaveJournal = computed(() =>
    journalValide(this.journalModel().code, this.journalModel().libelle) && !this.store.saving());

  // ─── Comptes et journal de chaque opération ──────────────────────────────
  protected readonly comptesModel = signal(comptesVides());
  protected readonly comptesForm = compatForm(this.comptesModel);
  protected readonly choix = signal<JournauxParOperation | null>(null);
  protected readonly comptesEnErreur = computed(() => comptesInvalides(this.comptesModel(), this.plan.actifs()));
  protected readonly operationsEnErreur = computed(() => {
    const choix = this.choix();
    return choix ? operationsSansJournal(choix, this.store.journauxActifs()) : [];
  });
  protected readonly canSaveComptes = computed(() =>
    !!this.choix() && this.comptesEnErreur().length === 0 && this.operationsEnErreur().length === 0
    && !this.store.saving());

  // ─── Comptabilisation du stock ───────────────────────────────────────────
  protected readonly periodeModel = signal(periodeParDefaut(new Date()));
  protected readonly periodeForm = compatForm(this.periodeModel, (form) => {
    required(form.from);
    required(form.to);
  });
  protected readonly canSynchroniser = computed(() =>
    periodeValide(this.periodeModel().from, this.periodeModel().to) && !this.store.saving());

  private readonly shell = inject(AppShellStore);
  private readonly dialog = inject(MatDialog);
  private readonly translate = inject(TranslateService);

  constructor() {
    // le paramétrage est celui du centre actif : on le recharge quand l'utilisateur change de centre
    effect(() => {
      this.shell.currentCenterId();
      untracked(() => {
        this.cancelJournal();
        this.store.load();
        this.plan.load();
      });
    });
    effect(() => {
      const mapping = this.store.mapping();
      if (!mapping) return;
      this.comptesModel.set(comptesDe(mapping));
      this.choix.set({...mapping.journaux});
    });
    effect(() => {
      if (this.store.successMessage()) untracked(() => this.cancelJournal());
    });
  }

  protected compteFacultatif(compte: CompteCle): boolean {
    return !COMPTES_OBLIGATOIRES.includes(compte);
  }

  protected setCompte(compte: CompteCle, numero: string): void {
    this.comptesModel.update((m) => ({...m, [compte]: numero}));
  }

  protected operationEnErreur(operation: OperationComptable): boolean {
    return this.operationsEnErreur().includes(operation);
  }

  /** Un journal choisi pour une opération ne peut être ni désactivé ni supprimé. */
  protected utilise(journal: JournalItem): boolean {
    return operationsDuJournal(this.store.mapping()?.journaux, journal.code).length > 0;
  }

  protected choisir(operation: OperationComptable, code: string): void {
    const choix = this.choix();
    if (choix) this.choix.set({...choix, [operation]: code});
  }

  protected saveJournal(): void {
    if (!this.canSaveJournal()) return;
    const code = normaliserCodeJournal(this.journalModel().code);
    const existant = this.store.journaux().find((j) => j.code === code);
    this.store.saveJournal({code, libelle: this.journalModel().libelle.trim(), actif: existant?.actif ?? true});
  }

  protected editJournal(journal: JournalItem): void {
    this.store.clearMessages();
    this.editingCode.set(journal.code);
    this.journalModel.set({code: journal.code, libelle: journal.libelle});
  }

  protected cancelJournal(): void {
    this.editingCode.set(null);
    this.journalModel.set(journalVide());
  }

  protected toggleActif(journal: JournalItem, actif: boolean): void {
    this.store.saveJournal({...journal, actif});
  }

  protected removeJournal(journal: JournalItem): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: 'min(96vw, 460px)',
      data: {
        title: this.translate.instant('COMPTABILITE.PARAMETRAGE.SUPPRIMER_TITRE'),
        message: this.translate.instant('COMPTABILITE.PARAMETRAGE.SUPPRIMER_MESSAGE', {code: journal.code}),
        confirmLabel: this.translate.instant('COMMON.CONFIRM'),
        cancelLabel: this.translate.instant('COMMON.CANCEL'),
        color: 'warn',
        icon: 'delete',
      },
    });
    ref.afterClosed().subscribe((confirmed) => {
      if (confirmed) this.store.deleteJournal(journal.code);
    });
  }

  protected onPageChange(event: PageEvent): void {
    this.store.setJournauxPagination({pageIndex: event.pageIndex, pageSize: event.pageSize});
  }

  protected saveComptes(): void {
    const choix = this.choix();
    if (!choix || !this.canSaveComptes()) return;
    const comptes = this.comptesModel();
    const saisis = Object.fromEntries(
      (Object.keys(comptes) as CompteCle[]).map((cle) => [cle, comptes[cle].trim()])) as Record<CompteCle, string>;
    this.store.saveMapping({...saisis, journaux: choix});
  }

  protected synchroniser(): void {
    if (!this.canSynchroniser()) return;
    this.store.synchroniserStock(this.periodeModel());
  }
}
