import type {NgTableTone} from '@sbourahla/ng-table';
import {ReglementEtat, ReglementSoldeType} from '../../../core/api/backend-api.service';
import {CorrespondanceDePage} from '../../../core/api/page-serveur';

/** Tailles de page proposées par la liste des factures à régler (20 par défaut, comme le store). */
export const TAILLES_DE_PAGE_REGLEMENT: readonly number[] = [10, 20, 50, 100];

/** Valeurs de `EtatDeReglement` (filtre énuméré envoyé tel quel au serveur). */
export const ETATS_DE_REGLEMENT: readonly ReglementEtat[] = ['NON_REGLEE', 'PARTIELLEMENT_REGLEE', 'REGLEE'];

/** Valeurs de `TypeDeSolde` (paramètre `soldeType`). */
export const TYPES_DE_SOLDE: readonly ReglementSoldeType[] = ['RESTE', 'REGLE', 'TROP_PERCU'];

/** Teinte de l'état de règlement d'une facture. */
export const TONS_ETAT_REGLEMENT: Record<ReglementEtat, NgTableTone> = {
  NON_REGLEE: 'danger',
  PARTIELLEMENT_REGLEE: 'warning',
  REGLEE: 'success',
};

/** Teinte du solde : soldée en succès, trop-perçu en alerte, reste dû en danger. */
export const TONS_SOLDE_REGLEMENT: Record<ReglementSoldeType, NgTableTone> = {
  REGLE: 'success',
  TROP_PERCU: 'warning',
  RESTE: 'danger',
};

const CHAMPS_TRIABLES = ['numeroFacture', 'numeroAssurance', 'patientNom', 'patientPrenom', 'caisse', 'agence',
  'centrePayeur', 'montantFacture', 'montantRegle', 'etat', 'codeReglement', 'dateFacturation'] as const;

const CHAMPS_FILTRABLES = ['numeroFacture', 'numeroAssurance', 'patientNom', 'patientPrenom', 'caisse', 'agence',
  'centrePayeur', 'codeReglement', 'montantFacture', 'montantRegle', 'etat'] as const;

/**
 * Colonnes de la table ⇒ paramètres de `GET /reglements/page` (RG-REG-009) : liste blanche de tri du serveur ;
 * la colonne Solde n'est pas triable et son filtre devient le paramètre `soldeType`.
 */
export const CORRESPONDANCE_REGLEMENT: CorrespondanceDePage = {
  tris: Object.fromEntries(CHAMPS_TRIABLES.map((c) => [c, c])),
  filtres: {...Object.fromEntries(CHAMPS_FILTRABLES.map((c) => [c, c])), solde: 'soldeType'},
  /** Filtres numériques (`[= > >= < <=]montant`) : une saisie que le serveur ne lit pas n'est pas envoyée. */
  numeriques: ['montantFacture', 'montantRegle'],
};
