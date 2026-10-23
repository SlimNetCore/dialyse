---
slice: stock-s6b-sortie-pour-seances
status: planned
date: 2026-10-23
order: 7 (S6b)
context: stock (+ Séance pour l'appelant, Identité & accès pour le profil)
sources: [.ddd/tactical/stock.md (SortiePourSeances, ConsommationDeSeance, T35 à T44), ADR 0045, RG-SEA-002, ADR 0024, 0043]
---

# S6b — Sortie pour séances, consommations imputées et rapprochement (nouvelle fonctionnalité)

> Nouvelle fonctionnalité décidée le 2026-10-23 (ADR 0045). Le filet porte sur le **comportement actuel** des
> consommations de séance (sortie immédiate FEFO),
> qui devient un écart décidé : la séance ne fait plus baisser le stock.

## Cas d'usage

`EnregistrerUneSortiePourSeances`, `ConsulterLesSortiesPourSeances`, `ImputerLesConsommationsDUneSeance`,
`RemplacerLesConsommations`, `AnnulerLesConsommations`,
`ListerLesArticlesFavoris`, `PreparerLeRapprochement`, `JustifierUnEcart`, `ValiderLeRapprochement` ; port publié
**synchrone** `ConsommationsDeSeance` (appelé par
la Séance) ; nouveau rôle `DIRECTEUR_ADMINISTRATIF` (rapprochement + lecture seule du stock).

## Entrées legacy remplacées

`BonSortieUseCase.addSeanceConsommation`, `setSeanceConsommation`, `annulerSortiesSeance`, `createViaFefo`
(`BonSortieService`) et leurs **4 appelants** :
`SeanceDomainService:53`, `SeanceApplicationService:52`, `SuppressionSeanceService:33`,
`AdministrationTraitementRestController:44` ; favoris actuels
`SeanceRaccourciJdbcAdapter` (`seance_raccourcis_articles`, raccourcis par centre) : à réconcilier avec les favoris du
périmètre (garder les raccourcis comme repli
quand aucune pièce ne couvre la séance ?).

## Données (migration **additive**)

Nouvelles tables (noms indicatifs) : `sorties_pour_seances` (centre, référence `SPS`, jour, salle?, demi-journée?,
statut, enregistrée le/par, validée le/par),
`sorties_pour_seances_lignes` (article, lot, quantité sortie, valeur unitaire de sortie, consommé, écart, motif),
`consommations_de_seance` (séance, patient, pièce?)
et `consommations_de_seance_imputations` (article, lot?, quantité, alerte) ; type de mouvement `RETOUR` dans
`stock_movements` ; rôle `DIRECTEUR_ADMINISTRATIF`
(table des rôles / énumération de sécurité). Pas de reprise des sorties de séance historiques (elles restent des
`bons_sortie` lisibles).

## Bascule (question ouverte 1 du modèle)

Recommandation : **paramètre par centre** « sortie pour séances activée » (désactivé par défaut) ; tant qu'il est
désactivé, le port publié garde le comportement actuel (sortie immédiate FEFO) ; activation centre par centre après
formation. Retour arrière : désactiver le paramètre (les pièces déjà enregistrées doivent être rapprochées).

## Filet

- Comportement actuel (avant écart) : goldens des consommations de séance (sortie FEFO immédiate, remplacement,
  annulation, séance facturée refusée, dose de traitement
  EPO/fer convertie par le dosage).
- Nouveau comportement (`*_nouveau_code`) : T35 à T44 du modèle tactique (FEFO à l'enregistrement, pièce précédente non
  rapprochée, imputation sans mouvement,
  dépassement avec alerte, écart sans motif, sortie supplémentaire, retour à la valeur de sortie, rôles, inventaire
  bloqué, concurrence pendant la validation).

## Critères de « built »

Les deux modes testés ; 4 appels directs de la Séance retirés (`FrontieresDesContextesTest`) ; écrans : préparation des
pièces (gestionnaire de stock), favoris et
alertes au poste infirmier, rapprochement (gestionnaire, admin, directeur administratif) ; i18n fr/en/ar/kab ; RG-STK
(nouvelle section 8.9) et documentation client.

## Retrait

Méthodes de séance de `BonSortieService` et `BonSortieUseCase` ; `SeanceRaccourciJdbcAdapter` si les favoris du
périmètre le remplacent.

## Dépendances et risques

Dépend de S5 et S6 (`StockArticle`, `BonSortie`). **Risque élevé** : nouveau processus métier, argent (sorties,
retours), changement des habitudes infirmières ;
questions ouvertes 2 à 4 du modèle tactique à trancher avant le code.

