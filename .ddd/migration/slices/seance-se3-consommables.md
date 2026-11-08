---
slice: seance-se3-consommables
status: built
date: 2026-11-08
order: 3
context: seance
sources: [.ddd/tactical/seance.md, ADR 0062, 0074, 0090, .ddd/safety-net/seance-se3-consommables.md]
---

# SE3 — Consommables déclarés et raccourcis de consommables

> Historique : `planned` au 2026-11-02. Caractérisée le 2026-11-08 (net abdadcc5 : 28 scénarios, 28 goldens, KB-SEA-22 à 34). `built` depuis le 2026-11-08 (lots A, B, C). Reprise sur place (ADR 0024) : mêmes tables, mêmes URLs, pas de façade, pas de flag, retour arrière par git.

La Séance **déclare** ; le Stock reste seul propriétaire de la consommation (`ConsommationsDeSeance`, imputation sur la « sortie pour séances », aucun mouvement de stock : KB-SEA-22) et seul juge de l'article (`CatalogueDArticles`).

## Cas d'usage (module `backend/contexts/seance`)
`AjouterUnConsommable` (`POST /api/v1/seances/{id}/consommables`), `ModifierLaQuantiteDUnConsommable` (`PUT …/consommables/{articleId}`), `RetirerUnConsommable` (`DELETE …/consommables/{articleId}?centerId=`), toutes `ADMIN`, `INFIRMIER` ; `ConsulterLesRaccourcis` (`GET /api/v1/seances/raccourcis-consommables`, `ADMIN`, `INFIRMIER`, `SECRETAIRE`), `RemplacerLesRaccourcis` (`PUT`, `ADMIN`).

## Fait (lots A, B et C, 2026-11-08)
| Couche | Contenu | Tests |
|---|---|---|
| Domaine | `RaccourcisDeConsommables` (12 au plus, sans doublon, actifs du centre), `QuantiteConsommee`, `ArticleId`, `ArticleDuCentre`, `Seance.exigerLAjoutDeConsommables` / `exigerLaModificationDesConsommables`, `MotifDeRefus.CONSOMMABLES_AVANT_VALIDATION` | `RaccourcisDeConsommablesTest`, `ConsommablesDeLaSeanceTest` (domaine) |
| Application | 5 cas d'usage, ports `ConsommablesDeLaSeance`, `ArticlesDuCentre`, `RaccourcisDesCentres`, `DiffusionsDeSeance.consommablesModifies` | `ConsommablesDeLaSeanceTest` 16, `RaccourcisDeConsommablesTest` 4 (doubles en mémoire) |
| Sortant | ACL Stock (`ConsommablesDeLaSeanceDuStock`, `ArticlesDuCentreDuStock`), `RaccourcisDesCentresJdbc` (verrou consultatif, mêmes tables), `RaccourcisDesCentresEnCache` (cache `seance.raccourcis`, purge après validation), plateforme `DiffusionsFournieesLegacy.consommablesModifies` | contrat raccourcis : mémoire, H2, PG16 (`seance-se3-pg16-rejeu.sh`, 9 tests) ; traducteurs table-driven |
| Entrant | `SeancesControleur` (6 routes), `ErreursDeSeanceAdvice`, DTO `AjoutDeConsommableDemande`, `ModificationDeConsommableDemande`, `RaccourcisDemande` | `SeancesControleurTest` (+4) |
| Routage | handlers legacy de `SeanceRestController` et `SeanceStationRestController`, famille `SeanceRaccourci*`, 3 méthodes de `SeanceUseCase`, `SeanceDomainService`, `SeanceApplicationService` retirés ; seam inscrit puis refermé (`seams.md`), `seamAllowed` vide | |
| Parité | 28/28 : 17 identiques, 11 écarts décidés ; **+ 1 écart (12e) dans le filet Stock** ; legacy archivé en R100 | 28 + filet Stock |
| Verrou | `seul_contexts_seance_declare_ou_fixe_une_consommation_de_seance` (`FrontieresDesContextesTest`) | |
| Docs | RG-SEA-054, annexe 16, glossaire (proposed), `verifications-production.md` §55 (SELECT seulement), ADR 0090 | `ReglesDeGestionDocumentationTest` |

Tests réécrits : `SeanceSuppressionIntegrationTest` (mise en place par le langage publié du Stock) ; `SeanceScanValidationControllerTest` supprimé (son seul cas est couvert : voir ADR 0090, lot B).

## Écarts de parité (12, tous décidés, ADR 0090)
Ajout : matrice des statuts, patient disparu (404), articles (statut unifié 400), quantités et formes (500 vers 400) · Modification : matrice des statuts, quantités et articles, cloisonnement · Retrait : matrice des statuts, paramètres · Raccourcis : bornes, cloisonnement · **12e (filet Stock, `ArticlesDeLaSeanceCharacterizationIntegrationTest`)** : `consommable_quantite_nulle`, `VALIDATION_ERROR` devenu `BAD_REQUEST` ; le test insère aussi le patient de la séance (golden `consommable_article_inconnu` inchangé).

## Jalons rouges datés (known-bugs.md, « Décisions SE3 »)
KB-SEA-33 (auteur ignoré par l'adapter du Stock, médico-légal), KB-SEA-22 (aucune sortie de stock), KB-SEA-24 (gardes de statut) et KB-SEA-31 (`PUT {}` efface les raccourcis) : mise en production conditionnée à la décision DPO et métiers, revue au plus tard le 2026-12-15.

## Bilan des résidus (revue au plus tard le 2026-12-15 ; date butoir de retrait propre : 2027-01-31)
| Résidu | Où | Sort |
|---|---|---|
| `DiffusionsFournieesLegacy.consommablesModifies` et ACL `DiffusionsParLeLegacy` | `platform/.../acl/seance`, `contexts/seance/.../acl/legacyseance` | disparaissent avec la reprise des notifications temps réel (NotificationService) ; 2027-01-31 |
| `NotificationService.notifySeanceUpdated`, `notifySaisieInfirmier` (types `CONSOMMABLE_*`) | `platform/.../application/notification` | idem, hors slices Séance |
| Lecture `ConsommationsDeSeance.sortiesParDate` (consommables les plus sortis du jour) et `LocalDate.now()` serveur | `SeanceRestController` | SE7 (lectures publiées) ; 2027-01-31 |
| Annulation des consommations à la suppression d'une séance | `SuppressionSeanceService` | slice de la suppression, hors SE3 ; revue 2026-12-15 |
| Mentions « consommable » de `SeanceUseCase`/`SeanceDomainService` | commentaires seulement | aucun code ; à nettoyer avec SE4 |
| Test de concurrence des consommables bout en bout (deux écritures sur la même séance sous PostgreSQL : `FOR UPDATE`, `lock_timeout`, écriture Stock) : seul le mapping 409 de l'advice et les raccourcis sont testés | `SeancesJdbc.trouverPourModifier`, `UniteDeTravailSpring` | à écrire (PG16 jetable) au plus tard le 2027-01-31 ; d'ici là recette manuelle §55 (g) |
Aucun appelant legacy d'écriture de consommation ne subsiste : verrou ArchUnit ci-dessus.

## Décisions à confirmer avant la mise en production
Toutes les décisions de l'ADR 0090 sont « à confirmer métiers et DPO » : risques en tête (KB-SEA-33, 22, 24, 31) ; corrections KB-SEA-25, 26, 27 ; écritures concurrentes (409). Recette et requêtes SELECT : `verifications-production.md` §55. Rollback : revenir au commit précédent.

## Reste à faire
Rien pour SE3. Suite : `/ddd-verify seance-se3-consommables`, puis SE4.
