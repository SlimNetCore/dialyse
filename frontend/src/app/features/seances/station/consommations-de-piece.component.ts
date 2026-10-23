import {ChangeDetectionStrategy, Component, effect, inject, input, signal} from '@angular/core';
import {MatIconModule} from '@angular/material/icon';
import {TranslateModule} from '@ngx-translate/core';
import {AuthStore} from '../../../core/state/auth.store';
import {
  AlerteDeConsommation,
  FavoriDeSeance,
  ImputationDeSeance,
  StockApiService,
} from '../../../core/api/stock-api.service';

/** Article tel que le poste infirmier le connaît (identifiant et libellé). */
export type ArticleDePiece = { id: string; libelle?: string | null; code?: string | null };

/**
 * Sortie pour séances au poste infirmier (S6b, ADR 0045) : les articles favoris de la pièce du jour pour le périmètre de la
 * séance (quantité sortie et ce qu'il en reste), et les alertes de la consommation (dépassement de la sortie, article hors pièce,
 * aucune pièce). Lecture seule : l'imputation reste faite par la saisie des consommables de la séance.
 */
@Component({
  selector: 'app-consommations-de-piece',
  standalone: true,
  imports: [MatIconModule, TranslateModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './consommations-de-piece.component.html',
  styleUrl: './consommations-de-piece.component.css',
})
export class ConsommationsDePieceComponent {
  readonly seanceId = input.required<string>();
  /** Jour de la séance (AAAA-MM-JJ). */
  readonly jour = input.required<string>();
  readonly articles = input<readonly ArticleDePiece[]>([]);
  /** Change à chaque saisie de consommable pour relire les restes et les alertes. */
  readonly version = input<unknown>(null);

  protected readonly favoris = signal<FavoriDeSeance[]>([]);
  protected readonly alertes = signal<ImputationDeSeance[]>([]);

  private readonly api = inject(StockApiService);
  private readonly auth = inject(AuthStore);

  constructor() {
    effect(() => {
      this.version();
      this.charger(this.seanceId(), this.jour());
    });
  }

  protected libelle(articleId: string): string {
    const a = this.articles().find(x => x.id === articleId);
    return (a?.libelle ?? a?.code ?? '').trim() || articleId;
  }

  protected cleAlerte(alerte: AlerteDeConsommation): string {
    return 'STOCK.SORTIES_SEANCES.ALERTE.' + alerte;
  }

  private charger(seanceId: string, jour: string): void {
    const centerId = this.auth.centerId();
    if (!centerId || !seanceId || !jour) {
      this.favoris.set([]);
      this.alertes.set([]);
      return;
    }
    this.api.favorisDeLaSeance(seanceId, centerId, jour).subscribe({
      next: liste => this.favoris.set(liste),
      error: () => this.favoris.set([]),
    });
    this.api.consommationDeSeance(seanceId, centerId).subscribe({
      next: c => this.alertes.set(c.imputations.filter(i => !!i.alerte)),
      error: () => this.alertes.set([]),
    });
  }
}

