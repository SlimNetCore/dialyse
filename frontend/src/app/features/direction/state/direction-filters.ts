import {Injectable, signal} from '@angular/core';
import {CaisseMetric} from '../direction-breakdown.util';

/**
 * Choix d'affichage communs aux trois pages du tableau de bord de la direction (vue d'ensemble, analyses, rapports) :
 * le centre isolé par le filtre et la mesure choisie pour les caisses (reprise par l'export CSV). La période vit dans
 * {@link DirectionStore}. Le centre est aussi reflété dans l'URL (`?centre=`) par la page parente.
 */
@Injectable({providedIn: 'root'})
export class DirectionFilters {
  /** Centre isolé par la direction (`null` = tous les centres, vue consolidée). */
  readonly centre = signal<string | null>(null);
  /** Mesure choisie par caisse (patients, séances ou CA HT). */
  readonly caisseMetric = signal<CaisseMetric>('caHt');
}
