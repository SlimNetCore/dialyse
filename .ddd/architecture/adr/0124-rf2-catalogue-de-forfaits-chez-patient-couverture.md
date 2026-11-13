---
status: accepted
date: 2026-11-10
review-by: 2026-12-15
decision-makers: [architecte (mise en oeuvre de l'ADR 0121, décision 1)]
context-scope: patient-couverture (propriétaire), platform (administration générique des référentiels, ACL, reprise de l'historique), organisation (lectures `/referentials/forfaits` retirées), seance et facturation (deux lectures SQL dupliquées remplacées par le langage publié)
related: ADR 0121 (propriété), 0122 et 0123 (RF1, modèle suivi), 0024 et 0075 (refactoring sur place), 0079 (`patientcouverture.v1`), 0070 (admin générique), 0099 (forfait imposé, SE7e), 0100 (SE7f) ; fiche `.ddd/migration/slices/patient-couverture-rf2-forfaits.md` ; known-bugs KB-RF2-01 à 06
---

# 0124 - RF2 : catalogue de forfaits chez Patient & couverture

Refactoring SUR PLACE : même table (`forfait`), mêmes URLs, mêmes rôles, mêmes codes d'erreur, même JSON ; ni façade, ni flag, ni projection, ni événement. Parité : les 16 tests de caractérisation HTTP (écrits et verts sur le legacy AVANT tout changement de production) passent sans un seul golden modifié ; aucun golden `_nouveau_code`, aucune archive `_ecarts-legacy`. Les goldens existants de la Séance, de la Facturation, des Absences, du pilotage et du reporting sont inchangés.

## Décisions
| N° | Décision | Raison |
|---|---|---|
| 1 | Domaine pur `Forfait` (code obligatoire <= 50, libellé obligatoire <= 255, prix FACULTATIF, zéro permis, négatif refusé, plafond 99 999 999,99, arrondi demi vers le haut au centime, séparateurs d'espaces et virgule décimale acceptés ; clé naturelle = code sans casse ni espaces répétés), port `CatalogueDeForfaits`, cas d'usage `GererLeCatalogueDeForfaits` (créer, modifier, supprimer, lister, importer tout-ou-rien) et `ConsulterLesForfaits` | règles auparavant dans le validateur générique et le SQL dynamique ; messages et codes repris à la lettre |
| 2 | Langage publié UNIQUE `patientcouverture.v1` : `ForfaitsDuCentre` (lister, trouver, parCode, prix, existe), `GestionDuCatalogueDeForfaits` + `RefusDeForfait` (SAISIE_INVALIDE, DEJA_EXISTANT, INTROUVABLE, UTILISE), `UsageDuForfait` | remplace les deux lectures SQL dupliquées (Séance, Facturation) ; l'administration parle au contexte par un port publié |
| 3 | Une seule classe JDBC `ForfaitsJdbc` (catalogue, page d'administration, langage publié) ; `UsageDuForfaitParLesPec` (deux `COUNT` sans centre). Contrat de tests UNIQUE : double en mémoire, H2 et PostgreSQL 16 (12 tests, rejeu `backend/scripts/patient-couverture-rf2-pg16-rejeu.sh`, vert) | le fake et l'adapter réel ne peuvent pas diverger |
| 4 | Usage à la suppression ADDITIONNÉ par PC auprès de chaque propriétaire de référence, via `List<UsageDuForfait>` injectée : PC (prises en charge, demandée puis effective), Séance (`UsageDuForfaitParLesSeances`, forfait imposé, langage publié SE7e), Facturation (`UsageDuForfaitParLesFactures`, `facture_lignes`). Même total, même message qu'avant | aucun contexte ne lit les tables d'un autre ; l'ordre d'addition est sans effet |
| 5 | L'administration générique GARDE la lecture du fichier et DÉLÈGUE (modèle RF1) : le port `ReferentialAdminDelegate` est implémenté par l'ACL `ForfaitsParPatientCouverture` ; l'assembleur `ReferentialAdminDelegates` confie chaque référentiel au premier délégué qui le prend (transporteurs RF1, forfaits RF2) | le service générique n'en connaît qu'un |
| 6 | Lectures `GET /api/v1/referentials/forfaits` (`centerId` obligatoire) et `/api/v1/organisation/referentiels/forfaits` (`centreId` facultatif) servies par `ForfaitsControleur` de PC ; code Organisation retiré (contrôleurs, cas d'usage, port, JDBC, test) ; même cache `ref.forfaits` (clé = identifiant du centre), garde de centre AVANT le cache ; `IllegalArgumentException` (centre illisible) -> 400 `BAD_REQUEST` par un advice borné | réponses identiques |
| 7 | Invalidation de `ref.forfaits` APRÈS commit à chaque écriture (`InvalidationDeLaListeDesForfaits`) | comportement identique |
| 8 | Séance et Facturation gardent leur port requis (`ForfaitsDuCentre` côté Séance, côté Facturation) avec un traducteur mince `ForfaitsDuCentreParLeCatalogue` ; les deux classes `ForfaitsDuCentreJdbc` sont supprimées. Facturation : un prix nul reste zéro (`ForfaitApplique`) ; Séance : un prix nul reste nul | parité ; ARGENT reproduit |
| 9 | La reprise de l'historique (`HistoryRules`, via `ReferentialAdminRepositoryPort.findAllForMatching`) retrouve les forfaits par `ForfaitsDuCentre.lister` ; `ReferentialAdminTables` n'a plus d'entrée forfait ni `UsagePublie` ; `ReferentialAdminJdbcAdapter` ne dépend plus de `ForfaitsImposesAuxSeancesPublies` | plus de SQL sur `forfait` hors PC (sauf RF2-R2) |
| 10 | Garde nominative `TableDesForfaitsTest` (liste blanche NOMMÉE et DATÉE, effectif asserté 1, échec sur entrée morte) ; la règle ArchUnit « l'administration compte les forfaits imposés » devient « `UsageDuForfaitParLesSeances` compte par `seance.v1` » | modèle `TableDesTransporteursTest` |

## Écarts de parité
Aucun. Les comportements du legacy sont REPRODUITS et consignés (KB-RF2-01 à 06). Aucune décision « corriger » n'est prise sans accord de l'utilisateur.

## Résiduels nominatifs
| ID | Résiduel | Raison / retrait |
|---|---|---|
| RF2-R1 | `ReferentialKind.FORFAIT` (descripteur : champs, alias d'en-têtes, modèle d'import, libellé) reste dans l'administration générique | **SOLDÉ par PL11-1 (2026-11-13, ADR 0157)** : le descripteur est publié par Patient et couverture (`DescripteursDeReferentiels.FORFAIT`) et reçu par la façade (SPI `descripteurs()`) ; plus de butoir 2027-03-31 ; garde anti-retour : `LaFacadeDesReferentielsNeDecritQueLesReferentielsEncoreDirectsTest` |
| RF2-R2 | `ForfaitsDeSeanceJdbc` (Séance) joint `prise_en_charge` à `forfait` pour le forfait courant d'une séance | requête reprise mot pour mot (SE7f, ADR 0100) ; c'est une lecture de la prise en charge, pas du catalogue ; à porter dans le langage publié de la couverture avec le retrait de la lecture `prise_en_charge` par la Séance |
| RF2-R3 | `CouvertureDesPatientsJdbc` (PC) garde la jointure du prix de PEC | même contexte ; parité du prix (PC5) |
| RF2-R4 | Le tri `ORDER BY code` suit la collation de la base : binaire sous H2 (`B-50%, D-HD, G_MED, Z-HD, a-hd`), celle du cluster sous PostgreSQL | inchangé par rapport au legacy ; non testé sous collation linguistique |
| RF2-R5 | ~~Le 500 sur corps/identifiant/`centerId` illisibles (KB-RF2-01)~~ CORRIGÉ le 2026-11-10 (RF2c, ADR 0125) avec KB-RF2-02 | décision de l'utilisateur, à confirmer métiers <= 2026-12-15 |

## Conséquences
- Fichiers de la plateforme touchés (filet RF2 vert, inscrits dans `covered.json`) : `ReferentialAdminTables`, `ReferentialAdminJdbcAdapter`, `DomainServiceConfig`, `ForfaitsParPatientCouverture` (nouveau), `ReferentialAdminDelegates` (nouveau). Aucun seam (`seamAllowed` reste `[]`).
- Séance, Facturation, Organisation : adaptés par leur propre code (hors `legacyPaths`).
- Retour arrière : revert Git.

## Questions ouvertes
- Confirmation métier de la propriété (ADR 0121, décision 1) avant le 2026-12-15.
- Sens métier d'un `forfait.prix` NUL (gratuit ? non défini ? à négocier ?) : reproduit tel quel (toléré à la saisie, ligne à zéro en facturation, refusé à l'émission, KB-SEA-45/47) ; faut-il l'interdire à la saisie ?
- « Forfait » a trois sens (de PEC, appliqué, du centre) ; « Catalogue de forfaits » désigne le référentiel : à valider.
- Pas de validité temporelle du prix : un changement de prix s'applique-t-il aux PEC en cours ?
- « Tarif » (synonyme) dans un contexte clinique : à trancher avec les experts.
- ~~Faut-il corriger KB-RF2-01 et KB-RF2-02 comme RF1c ?~~ Oui, fait le 2026-11-10 : voir l'ADR 0125 (le prix nul est gardé).
