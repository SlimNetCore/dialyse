---
slice: comptabilite-c3-ecritures
status: built
date: 2026-10-15
---

# C3 — Écritures et périodes

## Périmètre livré dans le contexte Comptabilité

- `GET /api/v1/comptabilite/ecritures` : consultation paginée, filtres sur centre, période, journal et statut.
- `POST /api/v1/comptabilite/ecritures/{id}/valider` : validation idempotente d'une écriture validée, refus d'une
  écriture exportée.
- `GET /api/v1/comptabilite/ecritures/export` : fichier Sage 100, marquage des validations dans la transaction.
- `POST /api/v1/comptabilite/periodes/cloturer` : clôture irréversible et isolée par centre.

Les routes C3 ont été retirées de `ComptabiliteRestController` après le rejeu préalable du filet legacy ; le contrôleur
plateforme garde les routes C5/C6 (reconstruction et stock). Les routes, rôles, formes HTTP, erreurs métier et données
restent servis par le contexte `contexts/comptabilite`. Les accès SQL sont bornés par `center_id`.

## Modèle et adaptateurs

- `EcritureComptable` porte l'équilibre, l'idempotence de validation, l'immuabilité après export et les lignes
  immuables.
- `PeriodeComptable` porte la clôture irréversible par mois et par centre.
- `EcritureComptableRepositoryJdbc` et `PeriodeComptableRepositoryJdbc` sont des ports-adaptateurs JDBC sur les tables
  historiques. Pas de dépendance du contexte vers `platform` et pas de migration de schéma.
- `updated_at` est mis à jour lors du passage à `EXPORTEE` et relu comme `exporteeLe`. Cette colonne existante est la
  date
  persistée de l'export ; l'auteur d'export n'est pas collecté (hors décision C3). `cloturee_at` et `cloturee_by`
  restent
  les colonnes d'audit de période.
- Le format CSV est isolé dans `Sage100ExportAdapter`; les dates du fichier restent les dates de pièce, le filtrage
  reste
  sur les dates d'écriture.

## Écarts au legacy décidés

Décision métier C3 datée du 2026-10-14 (tactique Comptabilité, B7) : un export n'inclut plus les écritures déjà
`EXPORTEE`, et la date de transition est conservée. Les brouillons restent dans le fichier mais ne sont pas marqués ;
seules les nouvelles `VALIDEE` deviennent `EXPORTEE`. Les écritures des périodes closes restent exportables.

Les références legacy `*.approved.json` ne sont pas écrasées. Le 2026-10-15, l'utilisateur a explicitement approuvé les
six candidats séparés après revue du contenu (les brouillons restent exportés, les déjà-exportées sont exclues et les
réexports ne contiennent que l'en-tête) :

| Candidat                                                                            | Comportement observé                                                   |
|-------------------------------------------------------------------------------------|------------------------------------------------------------------------|
| `export/export_deuxieme_fichier_identique_200_nouveau_code.approved.json`           | second export = en-tête seul                                           |
| `export/export_libelle_de_ligne_nul_en_base_fichier_200_nouveau_code.approved.json` | réexport après modification = en-tête seul                             |
| `export/export_mixte_fichier_200_nouveau_code.approved.json`                        | inclut BROUILLON et VALIDEE, exclut EXPORTEE; marque seulement VALIDEE |
| `export/journal_avec_espaces_rogne_200_nouveau_code.approved.json`                  | journal BQ déjà exporté = en-tête seul                                 |
| `parcours/etape_5_reexport_ventes_fichier_identique_200_nouveau_code.approved.json` | réexport des ventes = en-tête seul                                     |
| `parcours/etape_10_reexport_du_mois_clos_fichier_200_nouveau_code.approved.json`    | mois clos déjà exporté = en-tête seul                                  |

Les six références sont promues, gardées distinctes du comportement legacy, et rejouées par les tests C3.

## Vérification

- Baseline C3 sur le legacy : 102 tests, 0 échec (après correction du comparateur de golden pour ignorer uniquement les
  différences de mise en forme JSON; les valeurs JSON restent comparées structurellement).
- Nouveau code, 102 tests : 0 échec, goldens nouveau-code approuvés et vérifiés sans mode de collecte.
- Plateforme complète via `mvn -o -pl platform -am test` : 1 471 tests, 0 échec / 0 erreur, 4 ignorés ; tous les modules
  du réacteur réussissent.
- Module `contexts/comptabilite` : 270 tests, 0 échec, dont les nouvelles règles de domaine/application et
  l'architecture
  hexagonale.
- Fichier racine de migration : aucune migration nécessaire; C3 ne change pas le schéma.

## Limites / déploiement

- La date exportée dépend de `updated_at` : elle est durable, mais la date historique d'export n'est pas reconstruite
  pour
  les lignes qui étaient déjà `EXPORTEE` avant C3.
- Vérifier avant déploiement la présence de `updated_at` et l'absence d'écritures exportées avec des lignes illisibles
  ou
  déséquilibrées. Toute corruption touchée par la page ou le fichier fait échouer l'opération entière.
- L'ordre CSV est désormais déterministe (date d'écriture, numéro, id de ligne), contrairement à l'ordre non défini du
  legacy.

