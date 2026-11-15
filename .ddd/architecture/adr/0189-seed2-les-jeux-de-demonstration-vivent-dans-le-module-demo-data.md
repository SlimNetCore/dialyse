---
status: accepted (décision utilisateur du 2026-11-15, livrée le même jour)
date: 2026-11-15
decision-makers: [utilisateur, architecte]
context-scope: platform (configuration, gardes), demo-data (nouveau module, hors contextes)
related: ADR 0024, 0137, 0186, 0187, 0188 ; `seed2-realisation.md` ; `inventaire-platform-restant-v2.md` ligne 21
---

# 0189 - SEED2 : le jeu dérivé multi-contextes et le jeu RENADIAL vivent dans le module `demo-data`

## Contexte
Après ADR 0188, `db/seed.sql` gardait le jeu dérivé multi-contextes (séances, volets, relevés, prescriptions, analyses, consommables, bons de sortie, factures, `UPDATE seances.facture_id`) : chaque instruction lit les tables de plusieurs contextes. Le jeu RENADIAL (`renadial-seed.sql`, `renadial-purge.sql`, générateur) écrit aussi dans toutes les tables. Les placer dans un contexte créerait de nouveaux lecteurs SQL hors liste blanche ; les inscrire sur les gardes datées 2027-06-30 était un report.

## Décision
1. Nouveau module Maven `backend/demo-data` (artifactId `demo-data`), déclaré dans le pom parent. Ce n'est PAS un contexte délimité : pas de Java, pas de modèle, uniquement des scripts sous `src/main/resources/db/demo/` : `jeu-derive-seed.sql`, `renadial-seed.sql`, `renadial-purge.sql`, `generate-renadial-seed.cjs` (sortie unique à côté du script, inchangé), `README.md`. Sa raison d'être est de COMPOSER les contextes : ses lectures inter-contextes sont légitimes ici et seulement ici.
2. La plateforme le déclare en dépendance `runtime` (les scripts sont chargés par `spring.sql.init.data-locations` hors production ; `mode: never` en production, donc inerte ; le jar embarque ~5,7 Mo de plus). Aucun contexte ni le noyau partagé ne dépend de lui.
3. `db/seed.sql` (plateforme) ne garde que l'identité (rôles, utilisateurs, `app_user_role`, `app_user_center`). Ordre de chargement : organisation, comptabilité, facturation, patient-couverture, `db/seed.sql`, `db/demo/jeu-derive-seed.sql`, gmao, documents, stock ; le profil `demo-renadial` ajoute `db/demo/renadial-seed.sql` en dernier. Parité stricte conservée : `SeedH2DesScriptsParContexteTest` reste vert, aucun golden modifié.
4. Gardes : les entrées `db/seed.sql`, `db/demo/renadial-seed.sql`, `db/demo/renadial-purge.sql` de `TableDesSeancesTest`, `TableDesAnalysesTest`, `TableDuDossierMedicalTest` et l'écrivain `db/seed.sql` de `EcrivainsDeLaFactureDesSeancesTest` sont SUPPRIMÉES (butoir 2027-06-30 sans objet, aucun report). La règle devient structurelle, sans liste datée : `DonneesDeDemonstrationTest` impose que le module ne contienne que des SQL sous `db/demo/`, qu'aucun contexte n'en dépende, que seule la plateforme le déclare (runtime) et que `db/seed.sql` ne touche que l'identité. Les gardes ne balayent que les modules ayant du Java : `demo-data` en est hors par construction.
5. `.ddd/arch-rules.json` et `check-arch` ne gouvernent que `backend/contexts/**` : le module n'y est pas soumis, aucune exception à ajouter.

## Conséquences
- `seance-se7e-pg16-rejeu.sh` rejoue `db/seed.sql` seul : première instruction `MERGE INTO app_role` ; le jeu dérivé se rejoue depuis `demo-data`.
- Le Dockerfile copie `demo-data/pom.xml` pour le cache Maven.
- L'ADR 0188, point 5 et section « Reste », est résolu par la présente décision.
