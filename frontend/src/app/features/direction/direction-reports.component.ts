import {CurrencyPipe, DatePipe} from '@angular/common';
import {ChangeDetectionStrategy, Component, computed} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatSelectModule} from '@angular/material/select';
import {TranslateModule} from '@ngx-translate/core';
import {DirectionViewBase} from './direction-view.base';
import {delta, lastCompleteMonths} from './direction.util';

/**
 * Rapports de la direction (/direction/rapports) : rapports mensuels figés (PDF) des douze derniers mois écoulés et
 * comparaison de deux mois figés.
 */
@Component({
  selector: 'app-direction-reports',
  standalone: true,
  imports: [CurrencyPipe, DatePipe, TranslateModule, MatCardModule, MatButtonModule, MatIconModule, MatFormFieldModule,
    MatSelectModule],
  templateUrl: './direction-reports.component.html',
  styleUrl: './direction.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DirectionReportsComponent extends DirectionViewBase {
  /** Douze derniers mois écoulés, avec leur instantané s'il est déjà figé. */
  protected readonly reportRows = computed(() => {
    const frozen = new Map(this.store.snapshots().map((s) => [s.mois, s.generatedAt]));
    return lastCompleteMonths(new Date(), 12).map((mois) => ({mois, generatedAt: frozen.get(mois) ?? null}));
  });
  /** Mois déjà figés, sélectionnables dans le comparateur (un mois non figé n'a pas encore d'instantané). */
  protected readonly frozenMonths = computed(() =>
    [...this.store.snapshots()].map((s) => s.mois).sort().reverse());
  /**
   * Delta société entre les deux mois figés choisis dans le comparateur (activité et finances) ; `null` tant que
   * les deux ne sont pas chargés.
   */
  protected readonly compareDeltas = computed(() => {
    const a = this.store.compareA();
    const b = this.store.compareB();
    if (!a || !b) return null;
    const ta = a.overview.totaux;
    const tb = b.overview.totaux;
    return {
      patients: {a: ta.patients, b: tb.patients},
      seances: {a: ta.seances, b: tb.seances, d: delta(tb.seances, ta.seances)},
      caTtc: {a: ta.caTtc, b: tb.caTtc, d: delta(tb.caTtc, ta.caTtc)},
      encaisse: {a: ta.encaisse, b: tb.encaisse, d: delta(tb.encaisse, ta.encaisse)},
      tauxEncaissement: {a: ta.tauxEncaissement, b: tb.tauxEncaissement},
    };
  });

  /** Fige le mois au besoin, puis enregistre le rapport PDF sur le poste. */
  protected async downloadReport(mois: string): Promise<void> {
    this.save(await this.store.report(mois), `rapport-direction-${mois}.pdf`);
  }

  /** Charge le mois choisi dans l'emplacement A ou B du comparateur de mois figés. */
  protected setCompareMonth(slot: 'a' | 'b', mois: string | null): void {
    if (mois) void this.store.loadCompare(slot, mois);
  }
}
