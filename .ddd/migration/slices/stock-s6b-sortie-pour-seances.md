---
slice: stock-s6b-sortie-pour-seances
status: in-progress
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
questions ouvertes 2 à 4 du modèle tactique à trancher avant le code.## Avancement (2026-10-23) — phase A livrée (pièce
et rapprochement), phases B et C restantes **Questions ouvertes tranchées (hypothèses et recommandation du modèle, à
confirmer)** : 1) paramètre par centre
`stock_parametres.sortie_pour_seances_activee` (FALSE par défaut) ; 2) le motif d'un écart est saisi par l'infirmier
et/ou le
gestionnaire, seule la validation est réservée ; 3) la sortie supplémentaire part des lots de la pièce s'il en reste,
sinon FEFO ;

4) les consommations imputées restent la base du coût par séance. **Phase A (faite)** : migration additive `V45` (5
   tables, jouée sur PostgreSQL 16 jetable) et `schema.sql` ; type de mouvement
   `RETOUR` traité comme une entrée au prix de sortie (PMP, valorisation, rejeu, lectures) ; module `contexts/stock` :
   domaine
   `SortiePourSeances` (`constater`, `justifier`, `rapprocher`), cas d'usage `GestionDesSortiesPourSeances` (enregistrer
   en
   FEFO, préparer le rapprochement, justifier, valider : sortie supplémentaire ou retour), adapter
   `SortiesPourSeancesJdbc`, routes
   `/api/v1/stock/sorties-pour-seances` (rôles : enregistrement `ADMIN`/`PHARMACIEN` ; rapprochement avec
   `DIRECTEUR_ADMINISTRATIF`). 12 tests (T35, T36, T40, T41) ; filet legacy et ArchUnit verts. **Phase B (faite,
   2026-10-23)** : langage publié synchrone ``ConsommationsDeSeance`` (ajouter, fixer, annuler, administrer,
   enregistrerALaValidation), ``ConsommationRefusee`` (422, code stable, traité par ``ApiExceptionHandler``) et
   ``ParametresDuStockDuCentre`` ; contexte : ``ConsommationDeSeance`` (imputation sur les lots de la pièce en FEFO,
   aucun mouvement de stock, alertes ``DEPASSEMENT_DE_LA_SORTIE`` / ``ARTICLE_HORS_PIECE`` / ``AUCUNE_PIECE``),
   ``GestionDesConsommations`` (pièce du jour au périmètre le plus précis puis « journée », séance facturée et pièce
   rapprochée refusées, pièce verrouillée pendant l imputation), ACL ``PerimetreDesSeancesLegacy`` (salle = code de la
   salle, demi-journée lue dans le créneau), routes de lecture (favoris, consommation d une séance) et paramétrage du
   centre (``GET/PUT /api/v1/stock/parametres``, écriture ``ADMIN``). Plateforme : ``ConsommationsDeSeanceAiguillage``
   (``@Primary``, par centre) entre ``ConsommationsDeSeanceDuStock`` et ``ConsommationsDeSeanceLegacy`` (comportement
   historique inchangé, FEFO et bon unique) ; les 4 appelants de la Séance (``SeanceDomainService``,
   ``SeanceApplicationService``, ``SuppressionSeanceService``, ``AdministrationTraitementRestController``) n utilisent
   plus ``BonSortieUseCase`` (règle ArchUnit ajoutée dans ``FrontieresDesContextesTest``). Tests : +8
   (``GestionDesConsommationsTest``), ``SortiesPourSeancesIntegrationTest`` (parcours complet, rôles, mode historique),
   ``ConsommationsDeSeanceLegacyTest`` (FEFO déplacé depuis ``SeanceDomainServiceTest``), annexe de traçabilité des
   règles de gestion mise à jour ; suite backend complète verte. **Reste de B** : test de concurrence T44 (consommation
   pendant la validation) et retrait des méthodes de séance de ``BonSortieService`` (encore utilisées par l adapter
   historique, à retirer quand tous les centres auront basculé).`r`n`r`n **Phase C (à faire)** : garde T43 (inventaire
   refusé tant qu'une pièce n'est pas rapprochée, avec S7) ; rôle
   `DIRECTEUR_ADMINISTRATIF` en lecture seule sur les routes de lecture du stock et dans la gestion des comptes ;
   comptabilité C6 (les mouvements `RETOUR` doivent être pris en compte dans `OperationsDeStock`) ; écrans (préparation,
   favoris et alertes,
   rapprochement), i18n fr/en/ar/kab ; RG-STK 8.9 et documentation client.

## Avancement (2026-10-24) — phase C : livrée sauf documentation client

- **Garde d'inventaire T43** : `InventaireApplicationService` refuse l'ouverture tant qu'une pièce n'est pas rapprochée
  (`INVENTORY_SORTIE_POUR_SEANCES_NON_RAPPROCHEE`, port publié `SortiesPourSeancesEnAttente`) ; test
  `l_inventaire_est_refuse_tant_qu_une_sortie_pour_seances_n_est_pas_rapprochee`.
- **Rôle `DIRECTEUR_ADMINISTRATIF`** : lecture seule sur les routes du stock et de l'inventaire, validation du
  rapprochement ; aucune écriture (403) ; test `le_directeur_administratif_lit_le_stock_sans_pouvoir_l_ecrire`. Le rôle
  est une ligne de `app_role` créée par l'administrateur (aucune migration).
- **Comptabilité C6** : `OperationsDeStockJdbc` lit `SORTIE` et `RETOUR` ; un retour est une sortie négative au prix de
  sortie ; test `les_retours_en_stock_sont_comptabilises_comme_des_sorties_negatives`.
- **Écrans** : `/stock/sorties-pour-seances` (préparation de la pièce, liste, rapprochement : constat, motif par écart,
  validation ; bascule du centre pour `ADMIN`) et panneau `app-consommations-de-piece` au poste infirmier (articles de
  la pièce avec le reste, alertes de consommation). Nouvelle route
  `GET /api/v1/stock/consommations-de-seance/{seanceId}/favoris` : le périmètre (salle, demi-journée) est résolu côté
  serveur par l'ACL, le frontend ne le devine pas.
- **i18n** fr/en/ar/kab : `STOCK.SORTIES_SEANCES.*` et `NAV.STOCK_SORTIES_SEANCES` (ar et kab : à faire relire par un
  locuteur).
- **Tests** : +1 test de module (`les_favoris_d_une_seance_suivent_le_perimetre...`), 2 specs frontend
  (`sorties-pour-seances`, `consommations-de-piece`) ; module stock + comptabilité 310 tests verts, ArchUnit vert, build
  Angular vert.
- **Reste pour « built »** : test de concurrence T44 ; retrait des méthodes de séance de `BonSortieService` (quand tous
  les centres auront basculé) ; documentation client (`docs/client`, générée avec captures d'écran) ; l'infirmier seul
  n'atteint pas `/stock` (`role-scope.guard`) : la justification d'un écart par l'infirmier est possible par l'API mais
  n'a pas d'écran dédié.