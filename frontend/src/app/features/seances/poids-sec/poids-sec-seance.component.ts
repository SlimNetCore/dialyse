import {ChangeDetectionStrategy, Component, computed, effect, inject, input, signal} from '@angular/core';
import {MatIconModule} from '@angular/material/icon';
import {TranslateModule} from '@ngx-translate/core';
import {DossierMedicalApiService} from '../../../core/api/dossier-medical-api.service';

/** Seuil (kg) au-delà duquel la surcharge hydrique est signalée au médecin. */
export const SURCHARGE_ALERTE_KG = 3;

/** Différence arrondie au centième, ou null si une des deux valeurs manque. */
export function ecartKg(poids: number | null | undefined, poidsSec: number | null | undefined): number | null {
  if (poids == null || poidsSec == null || !Number.isFinite(poids) || !Number.isFinite(poidsSec)) return null;
  return Math.round((poids - poidsSec) * 100) / 100;
}

/**
 * Rappel, pendant la séance, du poids sec cible prescrit par le médecin (prescription en vigueur à
 * la date de la séance) et calcul en direct de la surcharge hydrique (poids avant − poids sec) et de
 * l'écart de fin de séance (poids après − poids sec). Lecture seule : seul le médecin fixe le poids sec,
 * dans le dossier médical.
 */
@Component({
  selector: 'app-poids-sec-seance',
  standalone: true,
  imports: [MatIconModule, TranslateModule],
  templateUrl: './poids-sec-seance.component.html',
  styleUrl: './poids-sec-seance.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PoidsSecSeanceComponent {
  readonly centerId = input<string | null>(null);
  readonly patientId = input<string | null>(null);
  readonly seanceDate = input<string | null>(null);
  readonly poidsAvantKg = input<number | null>(null);
  readonly poidsApresKg = input<number | null>(null);

  protected readonly poidsSecCibleKg = signal<number | null>(null);
  protected readonly loaded = signal(false);
  protected readonly surchargeKg = computed(() => ecartKg(this.poidsAvantKg(), this.poidsSecCibleKg()));
  protected readonly ecartFinKg = computed(() => ecartKg(this.poidsApresKg(), this.poidsSecCibleKg()));
  protected readonly surchargeAlerte = computed(() => (this.surchargeKg() ?? 0) > SURCHARGE_ALERTE_KG);

  private readonly api = inject(DossierMedicalApiService);
  private lastKey: string | null = null;

  constructor() {
    effect(() => {
      const centerId = this.centerId();
      const patientId = this.patientId();
      const date = this.seanceDate();
      const key = `${centerId ?? ''}|${patientId ?? ''}|${date ?? ''}`;
      if (!centerId || !patientId || key === this.lastKey) return;
      this.lastKey = key;
      this.loaded.set(false);
      this.api.getActivePrescription(centerId, patientId, date).subscribe({
        next: (prescription) => {
          this.poidsSecCibleKg.set(prescription?.poidsSecCibleKg ?? null);
          this.loaded.set(true);
        },
        error: () => {
          this.poidsSecCibleKg.set(null);
          this.loaded.set(true);
        },
      });
    });
  }

  protected signed(value: number): string {
    return `${value > 0 ? '+' : ''}${value.toFixed(2)} kg`;
  }
}

