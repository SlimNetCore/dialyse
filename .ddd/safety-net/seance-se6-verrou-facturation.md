---
slice: seance-se6-verrou-facturation
status: characterized
date: 2026-11-08
sources: [.ddd/migration/slices/seance-se6-verrou-facturation.md, ADR 0024, 0062, 0074, 0088-0092, SeanceSuppressionIntegrationTest]
---

# SE6 : modification de date et suppression d'une séance (filet)

Périmètre redéfini (la clé d'état est conservée) : les deux dernières routes legacy de `SeanceRestController`/`SeanceSuppressionRestController` :
`PUT /api/v1/seances/{id}` (changement de date) et `DELETE /api/v1/seances/{id}?centerId=` (suppression, corps `{"motif"}`).
La pose du verrou de facturation (statut `FACTUREE` + `facture_id`) appartient à la slice Facturation et reste hors périmètre.
KB-SEA-49 (le changement de date efface `facture_id`) reste caractérisé par `se5/date/` : non répété ici.

## Point d'observation

HTTP (MockMvc, contexte Spring complet, sécurité réelle, `RoleScopeFilter`, `CenterAccessGuard`, H2 `test`), état SQL des tables réelles
(`seances`, `seance_suppression`, volets, `administrations_anemie`, consommations/imputations, lots/articles/mouvements de stock, raccourcis),
diffusions, notifications, audit. Aucun mock du code caractérisé, aucun seam. Jours relatifs = jour du centre `<J>` ; aucun test ne lit l'heure.
Tests : `backend/platform/src/test/java/com/hemodialyse/backend/characterization/seance/se6/` (harnais `Se6CharacterizationSupport` au-dessus de Se1..Se5) ;
goldens : `backend/platform/src/test/resources/characterization/seance/se6/{suppression,date}/`.

## Scénarios (20 tests, 21 goldens)

| Dossier | Golden | Contenu |
|---|---|---|
| suppression | nominal | séance VALIDEE complète (volets médical et paramédical, administration, 2 consommations, raccourcis) : 204, tout effacé, journal, diffusion `SEANCE_SUPPRIMEE`, aucune notification, autre séance intacte, seconde suppression 422 |
| suppression | avec_consommations_validees | validation avec consommation puis suppression : stock et lots inchangés (KB-SEA-51) |
| suppression | matrice_des_statuts | 6 statuts x avec/sans `facture_id` |
| suppression | avec_facture | VALIDEE et SIGNEE avec `facture_id` supprimées (KB-SEA-50) |
| suppression | ordre_des_gardes | séance, puis motif, puis statut ; centre ; 10 combinaisons |
| suppression | motif | bornes 5/500, trim, absent, accents |
| suppression | parametres | centre absent/illisible, id, corps absent/null/illisible, types |
| suppression | profils | 15 profils + anonyme |
| suppression | cloisonnement | A/B, SUPERADMIN + ADMIN |
| suppression | date_et_patient | J-30, J, J+30 SIGNEE ; patient absent |
| suppression | audit | `SEANCES_SUPPRESSION` 204/403/422 |
| date | nominal | seule la colonne `date_seance` change (SIGNEE, place conservée, consommations inchangées, aucune diffusion/notification) |
| date | matrice_des_statuts | seul FACTUREE refusé |
| date | bornes | 14 dates (absente, nulle, vide, 1900, 9999, nombre, format, heure) |
| date | regles_ignorees | admission, fermeture, férié, jour non programmé, doublon patient/jour |
| date | verrou_des_jours_passes | interaction SE2 (KB-SEA-55) |
| date | sorties_de_stock | consommations sans date, rien ne bouge |
| date | parametres, profils, cloisonnement, audit | contrôles d'entrée, droits, centre, `SEANCES_MODIFICATION` |

## Bugs connus

KB-SEA-50 (intégrité de facturation, confirmé) à KB-SEA-56 dans `.ddd/safety-net/known-bugs.md`. Aucun corrigé.

## Seams, fichiers protégés

Aucun seam demandé. Ajoutés à `covered.json` : `SeanceSuppressionRestController`, `SuppressionSeanceService`, `SuppressionSeance`,
`MotifSuppressionSeance`, `SuppressionSeanceJournalPort`, `SuppressionSeanceJournalJdbcAdapter` (les fichiers `SeanceRestController`,
`SeanceDomainService`, `Seance`, `SeanceRepositoryAdapter`, `SeanceApplicationService` l'étaient déjà).

## Exécution

3 exécutions consécutives vertes (dont une avec `-Dsurefire.runOrder=random`). Aucun test flaky.

## Limites (non verrouillé)

- Pas d'outil de mutation (PIT) configuré : mutation et couverture de branches non mesurées.
- Atomicité « tout ou rien » de la suppression (échec en cours de route) non provoquée : aucun point d'injection sans seam.
- Les adapters `VoletParamedicalRepositoryAdapter`, `VoletMedicalRepositoryAdapter`, `AdministrationTraitementRepositoryAdapter` ne sont exercés que par leur méthode de
  suppression : non ajoutés à `covered.json`.
- Restitution de stock réelle (sorties sur pièce, `bons_sortie`) : aucune pièce n'est créée par le harnais (alerte `AUCUNE_PIECE`) ; les cas avec pièce de sortie ne sont pas couverts.
- Concurrence (suppression et validation simultanées), fuseau minuit du centre non rejoués ici (hérités de SE1/SE2).
- Le lien avec une vraie facture (table de facturation) n'est pas posé : `facture_id` est un identifiant libre, comme dans `se5/date/`.
