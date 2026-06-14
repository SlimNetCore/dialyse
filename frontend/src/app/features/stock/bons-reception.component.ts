import {ChangeDetectionStrategy, Component, computed, effect, inject, OnDestroy, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, required} from '@angular/forms/signals';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatDatepickerModule} from '@angular/material/datepicker';
import {MatNativeDateModule} from '@angular/material/core';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatTableModule} from '@angular/material/table';
import {MatChipsModule} from '@angular/material/chips';
import {MatSnackBar} from '@angular/material/snack-bar';
import {MatDialog} from '@angular/material/dialog';
import {MatTooltipModule} from '@angular/material/tooltip';
import {AuthStore} from '../../core/state/auth.store';
import {WebSocketService} from '../../core/ws/websocket.service';
import {BonReception, Emplacement, Fournisseur, StockApiService} from '../../core/api/stock-api.service';
import {ReferentialApiService, RefItem} from '../../core/api/referential-api.service';
import {PmpRecalcDialogComponent} from './pmp-recalc-dialog.component';

@Component({
  selector: 'app-bons-reception',
  standalone: true,
  imports: [
    CommonModule, MatCardModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatDatepickerModule, MatNativeDateModule, MatButtonModule, MatIconModule,
    MatTableModule, MatChipsModule, MatTooltipModule, FormRoot, FormField,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {provide: MatNativeDateModule, useClass: MatNativeDateModule},
  ],
  template: `
    <section class="app-page">
      <div class="app-hero-card">
        <span class="app-eyebrow">Stock · Réception</span>
        <h1 class="app-section-title">Bons de réception</h1>
        <p class="app-section-copy">Saisie des lots (n° + péremption). La validation calcule le PMP en cascade.</p>
      </div>

      <mat-card>
        <mat-card-header>
          <mat-card-title>{{ editingId() ? 'Éditer' : 'Nouveau' }} bon de réception</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form [formRoot]="receptionForm" (submit)="save(); $event.preventDefault()">
            <div class="head-row">
              @if (lockedArticleIds().length > 0) {
                <mat-chip class="lock-chip">{{ lockedArticleIds().length }} article(s) en recalcul</mat-chip>
              }
              <mat-form-field appearance="outline" class="flex2">
                <mat-label>Fournisseur</mat-label>
                <mat-select [formField]="receptionForm.fournisseurId">
                  @for (f of fournisseurs(); track f.id) {
                    <mat-option [value]="f.id">{{ f.raisonSociale }}</mat-option>
                  }
                </mat-select>
              </mat-form-field>
              <mat-form-field appearance="outline" class="flex1">
                <mat-label>Date de réception</mat-label>
                <input matInput [matDatepicker]="dp" [formField]="receptionForm.dateReception"/>
                <mat-datepicker-toggle matIconSuffix [for]="dp"></mat-datepicker-toggle>
                <mat-datepicker #dp></mat-datepicker>
                @if (showDateError()) {
                  <mat-error>Date obligatoire</mat-error>
                }
              </mat-form-field>
            </div>

            <div class="lines">
              @for (ligne of formModel().lignes; track $index) {
                <div class="line-row" [attr.data-line-article]="ligne.articleId || ''">
                  <mat-form-field appearance="outline" class="flex2">
                    <mat-label>Article</mat-label>
                    <mat-select [value]="ligne.articleId" (selectionChange)="onArticleSelected($index, $event.value)">
                      @for (a of articles(); track a.id) {
                        <mat-option [value]="a.id" [disabled]="isArticleLocked(a.id)">
                          {{ articleLabel(a) }}
                          @if (isArticleLocked(a.id)) {
                            - recalcul en cours
                          }
                        </mat-option>
                      }
                    </mat-select>
                    @if (showLigneError($index, 'article')) {
                      <mat-error>Article obligatoire</mat-error>
                    }
                  </mat-form-field>
                  <mat-form-field appearance="outline" class="flex1">
                    <mat-label>Quantité</mat-label>
                    <input matInput type="number" [value]="ligne.quantite ?? ''"
                           (input)="onQuantiteInput($index, $event)"/>
                    @if (showLigneError($index, 'quantite')) {
                      <mat-error>Quantité invalide</mat-error>
                    }
                  </mat-form-field>
                  <mat-form-field appearance="outline" class="flex1">
                    <mat-label>Prix unitaire</mat-label>
                    <input matInput type="number" [value]="ligne.prixUnitaire ?? ''"
                           (input)="onPrixUnitaireInput($index, $event)"/>
                    @if (showLigneError($index, 'prix')) {
                      <mat-error>Prix invalide</mat-error>
                    }
                  </mat-form-field>
                  <mat-form-field appearance="outline" class="flex1">
                    <mat-label>N° lot</mat-label>
                    @if (isLotManagedRow($index)) {
                      <mat-hint>Obligatoire (article géré par lot)</mat-hint>
                    } @else {
                      <mat-hint>Optionnel (lot auto si vide)</mat-hint>
                    }
                    <input matInput [value]="ligne.numeroLot" (input)="onNumeroLotInput($index, $event)"/>
                    @if (showLigneError($index, 'lot')) {
                      <mat-error>N° lot obligatoire</mat-error>
                    }
                  </mat-form-field>
                  <mat-form-field appearance="outline" class="flex1">
                    <mat-label>Péremption</mat-label>
                    @if (isLotManagedRow($index)) {
                      <mat-hint>Obligatoire (article géré par lot)</mat-hint>
                    } @else {
                      <mat-hint>Optionnel</mat-hint>
                    }
                    <input matInput [matDatepicker]="dpl" [value]="ligne.datePeremption"
                           (dateChange)="onDatePeremptionChange($index, $event.value)"/>
                    <mat-datepicker-toggle matIconSuffix [for]="dpl"></mat-datepicker-toggle>
                    <mat-datepicker #dpl></mat-datepicker>
                    @if (showLigneError($index, 'peremption')) {
                      <mat-error>Date de péremption obligatoire</mat-error>
                    }
                  </mat-form-field>
                  <button mat-icon-button type="button" (click)="removeLigne($index)" aria-label="Supprimer">
                    <mat-icon>delete</mat-icon>
                  </button>
                </div>
              }
            </div>

            <div class="app-button-cluster">
              <button mat-stroked-button type="button" (click)="addLigne()">
                <mat-icon>add</mat-icon>
                Ajouter un lot
              </button>
              <div class="spacer"></div>
              @if (editingId()) {
                <button mat-stroked-button type="button" (click)="cancel()">Annuler</button>
              }
              <button mat-flat-button color="primary" type="submit"
                      [disabled]="!canSave() || saving() || hasLockedLines()">
                <mat-icon>save</mat-icon>
                {{ editingId() ? 'Mettre à jour' : 'Créer (brouillon)' }}
              </button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>

      <mat-card>
        <mat-card-header>
          <mat-card-title>Bons de réception</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <table mat-table [dataSource]="bons()" class="full-width">
            <ng-container matColumnDef="reference">
              <th mat-header-cell *matHeaderCellDef>Référence</th>
              <td mat-cell *matCellDef="let b">{{ b.reference }}</td>
            </ng-container>
            <ng-container matColumnDef="date">
              <th mat-header-cell *matHeaderCellDef>Date</th>
              <td mat-cell *matCellDef="let b">{{ b.dateReception }}</td>
            </ng-container>
            <ng-container matColumnDef="statut">
              <th mat-header-cell *matHeaderCellDef>Statut</th>
              <td mat-cell *matCellDef="let b">
                <mat-chip>{{ b.statut }}</mat-chip>
              </td>
            </ng-container>
            <ng-container matColumnDef="lignes">
              <th mat-header-cell *matHeaderCellDef>Lots</th>
              <td mat-cell *matCellDef="let b">{{ b.lignes.length }}</td>
            </ng-container>
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef>Actions</th>
              <td mat-cell *matCellDef="let b">
                <div class="action-buttons">
                  <button mat-icon-button (click)="edit(b)" matTooltip="Éditer">
                    <mat-icon>edit</mat-icon>
                  </button>
                  @if (b.statut === 'BROUILLON') {
                    <button mat-flat-button color="accent" (click)="valider(b)">
                      Valider
                    </button>
                  }
                </div>
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="cols"></tr>
            <tr mat-row *matRowDef="let row; columns: cols" [attr.data-row-id]="row?.id"></tr>
          </table>
        </mat-card-content>
      </mat-card>
    </section>
  `,
  styles: [`
    .full-width {
      width: 100%;
    }

    .head-row {
      display: flex;
      gap: 12px;
    }

    .lines {
      display: grid;
      gap: 8px;
      margin: 12px 0;
    }

    .line-row {
      display: flex;
      gap: 8px;
      align-items: center;
      flex-wrap: wrap;
    }

    .flex1 {
      flex: 1;
      min-width: 130px;
    }

    .flex2 {
      flex: 2;
      min-width: 200px;
    }

    mat-card {
      margin-top: 16px;
    }

    .action-buttons {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
    }

    .app-button-cluster {
      display: flex;
      gap: 12px;
      align-items: center;
      margin-top: 16px;
    }

    .spacer {
      flex: 1;
    }

    .lock-chip {
      background: #fff3e0;
      border: 1px solid #ffb74d;
      color: #e65100;
      align-self: center;
    }
  `],
})
export class BonsReceptionComponent implements OnDestroy {
  protected readonly cols = ['reference', 'date', 'statut', 'lignes', 'actions'];
  protected readonly bons = signal<BonReception[]>([]);
  protected readonly fournisseurs = signal<Fournisseur[]>([]);
  protected readonly emplacements = signal<Emplacement[]>([]);
  protected readonly articles = signal<RefItem[]>([]);
  protected readonly lockedArticleIds = signal<string[]>([]);
  protected readonly saving = signal(false);
  protected readonly submitAttempted = signal(false);
  protected readonly editingId = signal<string | null>(null);
  protected readonly editingStatus = signal<'BROUILLON' | 'VALIDE' | 'RECU' | 'ANNULE' | null>(null);
  protected readonly formModel = signal<ReceptionFormModel>(this.createInitialForm());
  protected readonly receptionForm = compatForm(this.formModel, (form) => {
    required(form.dateReception);
  });
  protected readonly canSave = computed(() => {
    const model = this.formModel();
    if (!model.dateReception || model.lignes.length === 0) {
      return false;
    }
    return model.lignes.every(ligne => {
      const quantite = Number(ligne.quantite ?? 0);
      const prixUnitaire = Number(ligne.prixUnitaire ?? -1);
      if (!ligne.articleId || quantite <= 0 || prixUnitaire < 0) {
        return false;
      }
      if (!this.isLotManagedArticle(ligne.articleId)) {
        return true;
      }
      return !!ligne.numeroLot.trim() && !!ligne.datePeremption;
    });
  });
  private readonly api = inject(StockApiService);
  private readonly refApi = inject(ReferentialApiService);
  private readonly auth = inject(AuthStore);
  private readonly ws = inject(WebSocketService);
  private readonly snack = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);
  private lockTimer: ReturnType<typeof setInterval> | null = null;

  constructor() {
    this.reload();
    this.lockTimer = setInterval(() => this.refreshLocks(), 5000);
    effect(() => {
      const evt = this.ws.lastEvent();
      const centerId = this.auth.centerId();
      if (!evt || !centerId) {
        return;
      }
      if (evt.type === 'STOCK_RECALC_LOCKS_CHANGED' && evt.centerId === centerId) {
        this.refreshLocks();
      }
    });
  }

  ngOnDestroy(): void {
    if (this.lockTimer) {
      clearInterval(this.lockTimer);
    }
  }

  protected addLigne(): void {
    this.formModel.update(model => ({
      ...model,
      lignes: [...model.lignes, this.newLigne()],
    }));
  }

  protected removeLigne(i: number): void {
    this.formModel.update(model => ({
      ...model,
      lignes: model.lignes.filter((_, index) => index !== i),
    }));
  }

  protected save(): void {
    this.submitAttempted.set(true);
    const centerId = this.auth.centerId();
    const model = this.formModel();
    if (!centerId || !this.canSave()) {
      return;
    }
    if (this.hasLockedLines()) {
      this.snack.open('Un ou plusieurs articles sont en recalcul. Reessayez plus tard.', 'Fermer', {duration: 4000});
      return;
    }
    this.saving.set(true);
    const lignes = model.lignes.map(ligne => ({
      articleId: ligne.articleId!,
      quantite: Number(ligne.quantite ?? 0),
      prixUnitaire: Number(ligne.prixUnitaire ?? 0),
      numeroLot: this.normalizedString(ligne.numeroLot),
      datePeremption: this.toIso(ligne.datePeremption),
    }));

    const editingId = this.editingId();
    const saveOp = editingId
      ? this.api.updateBonReception(editingId, {
        centerId,
        fournisseurId: model.fournisseurId ?? undefined,
        dateReception: this.toIso(model.dateReception),
        lignes,
      })
      : this.api.createBonReception({
        centerId,
        fournisseurId: model.fournisseurId ?? undefined,
        dateReception: this.toIso(model.dateReception),
        userId: this.auth.username() ?? undefined,
        lignes,
      });

    saveOp.subscribe({
      next: () => {
        const message = editingId ? 'Bon de réception mis à jour' : 'Bon de réception créé';
        this.snack.open(message, 'OK', {duration: 2500});

        if (editingId && this.editingStatus() && this.editingStatus() !== 'BROUILLON') {
          const articleIds = Array.from(new Set(lignes.map(l => l.articleId).filter(Boolean)));
          if (articleIds.length > 0) {
            this.dialog.open(PmpRecalcDialogComponent, {
              width: '560px',
              hasBackdrop: false,
              data: {centerId, articleIds},
            });
          }
        }

        this.formModel.set(this.createInitialForm());
        this.submitAttempted.set(false);
        this.editingId.set(null);
        this.editingStatus.set(null);
        this.reload();
      },
      complete: () => this.saving.set(false),
      error: () => {
        this.saving.set(false);
        this.snack.open('Erreur lors de l\'opération', 'Fermer', {duration: 4000});
      },
    });
  }

  protected edit(b: BonReception): void {
    this.submitAttempted.set(false);
    this.editingId.set(b.id);
    this.editingStatus.set(b.statut);
    this.formModel.set({
      fournisseurId: b.fournisseurId ?? null,
      dateReception: b.dateReception ? new Date(b.dateReception) : new Date(),
      lignes: b.lignes.map(l => ({
        articleId: l.articleId,
        quantite: Number(l.quantite ?? 0),
        prixUnitaire: Number(l.prixUnitaire ?? 0),
        numeroLot: l.numeroLot ?? '',
        datePeremption: l.datePeremption ? new Date(l.datePeremption) : null,
      })),
    });

    // Scroller vers le formulaire
    setTimeout(() => {
      document.querySelector('.app-page')?.scrollIntoView({behavior: 'smooth'});
    }, 100);
  }

  protected cancel(): void {
    this.editingId.set(null);
    this.editingStatus.set(null);
    this.submitAttempted.set(false);
    this.formModel.set(this.createInitialForm());
  }

  protected onArticleSelected(index: number, articleId: string | null): void {
    if (this.isArticleLocked(articleId)) {
      this.snack.open('Recalcul en cours pour cet article. Saisie temporairement bloquee.', 'Fermer', {duration: 4000});
      this.patchLigne(index, {articleId: null, numeroLot: '', datePeremption: null});
      return;
    }
    this.patchLigne(index, {articleId});
  }

  protected onQuantiteInput(index: number, event: Event): void {
    const raw = (event.target as HTMLInputElement | null)?.value;
    const quantite = raw == null || raw === '' ? null : Number(raw);
    this.patchLigne(index, {quantite: Number.isFinite(quantite ?? NaN) ? quantite : null});
  }

  protected onPrixUnitaireInput(index: number, event: Event): void {
    const raw = (event.target as HTMLInputElement | null)?.value;
    const prixUnitaire = raw == null || raw === '' ? null : Number(raw);
    this.patchLigne(index, {prixUnitaire: Number.isFinite(prixUnitaire ?? NaN) ? prixUnitaire : null});
  }

  protected onNumeroLotInput(index: number, event: Event): void {
    const numeroLot = (event.target as HTMLInputElement | null)?.value ?? '';
    this.patchLigne(index, {numeroLot});
  }

  protected onDatePeremptionChange(index: number, datePeremption: Date | null): void {
    this.patchLigne(index, {datePeremption});
  }


  protected valider(b: BonReception): void {
    const centerId = this.auth.centerId();
    if (!centerId) {
      return;
    }
    this.api.validerBonReception(b.id, centerId, this.auth.username() ?? undefined).subscribe({
      next: () => {
        this.snack.open('Réception validée · PMP recalculé', 'OK', {duration: 3000});
        this.reload();
      },
      error: () => this.snack.open('Erreur de validation', 'Fermer', {duration: 4000}),
    });
  }

  protected isLotManagedRow(index: number): boolean {
    return this.isLotManagedArticle(this.formModel().lignes[index]?.articleId ?? null);
  }

  protected articleLabel(a: RefItem): string {
    const code = (a.code ?? '').trim();
    const libelle = (a.libelle ?? a.nom ?? '').trim();
    const unite = (a.unite ?? '').trim();
    const left = code ? `${code} - ${libelle}` : libelle;
    const withUnit = unite ? `${left} (${unite})` : left;
    return a.gereParLot ? `${withUnit} · lot` : `${withUnit} · sans lot`;
  }

  protected isArticleLocked(articleId?: string | null): boolean {
    if (!articleId) {
      return false;
    }
    return this.lockedArticleIds().includes(articleId);
  }

  protected hasLockedLines(): boolean {
    return this.formModel().lignes.some(ligne => this.isArticleLocked(ligne.articleId));
  }

  protected showDateError(): boolean {
    return this.submitAttempted() && !this.formModel().dateReception;
  }

  protected showLigneError(index: number, field: 'article' | 'quantite' | 'prix' | 'lot' | 'peremption'): boolean {
    if (!this.submitAttempted()) {
      return false;
    }
    const ligne = this.formModel().lignes[index];
    if (!ligne) {
      return false;
    }
    if (field === 'article') {
      return !ligne.articleId;
    }
    if (field === 'quantite') {
      return Number(ligne.quantite ?? 0) <= 0;
    }
    if (field === 'prix') {
      return Number(ligne.prixUnitaire ?? -1) < 0;
    }
    const lotManaged = this.isLotManagedArticle(ligne.articleId);
    if (field === 'lot') {
      return lotManaged && !ligne.numeroLot.trim();
    }
    return lotManaged && !ligne.datePeremption;
  }

  private newLigne(): ReceptionLigneForm {
    return {
      articleId: null,
      quantite: 1,
      prixUnitaire: 0,
      numeroLot: '',
      datePeremption: null,
    };
  }

  private reload(): void {
    const centerId = this.auth.centerId();
    if (!centerId) {
      return;
    }
    this.api.listBonsReception(centerId).subscribe({next: (b) => this.bons.set(b)});
    this.api.listFournisseurs(centerId).subscribe({next: (f) => this.fournisseurs.set(f)});
    this.api.listEmplacements(centerId).subscribe({next: (e) => this.emplacements.set(e)});
    this.refApi.getArticles(centerId).subscribe({
      next: (a) => this.articles.set(a),
    });
    this.refreshLocks();
  }

  private isLotManagedArticle(articleId?: string | null): boolean {
    if (!articleId) {
      return false;
    }
    return this.articles().some(a => a.id === articleId && !!a.gereParLot);
  }

  private patchLigne(index: number, patch: Partial<ReceptionLigneForm>): void {
    this.formModel.update(model => ({
      ...model,
      lignes: model.lignes.map((ligne, ligneIndex) => ligneIndex === index ? {...ligne, ...patch} : ligne),
    }));
  }

  private normalizedString(value: unknown): string | undefined {
    const raw = typeof value === 'string' ? value.trim() : '';
    return raw ? raw : undefined;
  }

  private refreshLocks(): void {
    const centerId = this.auth.centerId();
    if (!centerId) {
      return;
    }
    this.api.listPmpRecalcLocks(centerId).subscribe({
      next: (ids) => this.lockedArticleIds.set(ids),
      error: () => this.lockedArticleIds.set([]),
    });
  }

  private toIso(d: unknown): string | undefined {
    if (!d) {
      return undefined;
    }
    const date = d instanceof Date ? d : new Date(d as string);
    return date.toISOString().substring(0, 10);
  }

  private createInitialForm(): ReceptionFormModel {
    return {
      fournisseurId: null,
      dateReception: new Date(),
      lignes: [this.newLigne()],
    };
  }
}

type ReceptionFormModel = {
  fournisseurId: string | null;
  dateReception: Date | string | null;
  lignes: ReceptionLigneForm[];
};

type ReceptionLigneForm = {
  articleId: string | null;
  quantite: number | null;
  prixUnitaire: number | null;
  numeroLot: string;
  datePeremption: Date | string | null;
};

