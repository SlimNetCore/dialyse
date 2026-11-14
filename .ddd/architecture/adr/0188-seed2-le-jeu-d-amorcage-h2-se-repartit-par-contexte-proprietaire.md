---
status: accepted (tranche 1 livrée le 2026-11-14 ; tranches suivantes à livrer, voir « Reste »)
date: 2026-11-14
decision-makers: [architecte]
context-scope: organisation, gmao, documents, platform (configuration, gardes, démonstration RENADIAL)
related: ADR 0024, 0137, 0186, 0187 ; `inventaire-platform-restant-v2.md` ligne 21 ; `seed2-realisation.md`
---

# 0188 - SEED2 : le jeu d'amorçage H2 se répartit par contexte propriétaire

## Décision
1. `db/seed.sql` (plateforme) est découpé bloc à bloc, SANS changement de contenu, en un script `contexts/<ctx>/src/main/resources/db/h2/<ctx>-seed.sql` par propriétaire, listé dans l'ordre des dépendances de données par `spring.sql.init.data-locations` (`application.yml`). `SEED_DATA_LOCATIONS` REMPLACE toute la liste (inchangé depuis ADR 0186). Le profil `demo-renadial` répète la même liste puis ajoute `renadial-seed.sql` en dernier ; les deux listes doivent rester identiques.
2. Propriétaires (glossaires des contextes) : sociétés, centres, calendrier du centre, médecins, salles, positions/créneaux = **Organisation** ; `gmao_equipements` = **GMAO** ; `modele_document` = **Documents**. Reste dans `db/seed.sql` (plateforme) : l'identité (rôles, utilisateurs, `app_user_role`, `app_user_center`) — aucun contexte ne la possède (ADR 0140 D7) — et, TANT QUE LEURS TRANCHES NE SONT PAS LIVRÉES, le patient-couverture, la facturation, la séance, le dossier médical et le stock.
3. Ordre : `organisation-seed` EN PREMIER (`app_user_center` référence `centers` par clé étrangère ; tout le jeu référence les centres) ; puis `db/seed.sql` ; puis `gmao-seed` et `documents-seed` (références par valeur à `salle`/`centers`, sans clé étrangère) ; puis `comptabilite-seed` (inchangé).
4. Preuve permanente de strict identité : `SeedH2DesScriptsParContexteTest`. La référence est `src/test/resources/db/seed-reference-avant-decoupe.sql`, copie octet pour octet de l'ancien script unique prise AVANT la découpe : une empreinte figée dans un fichier ne tiendrait pas un jour de plus (le jeu dépend de `CURRENT_DATE`/`CURRENT_TIMESTAMP`, et d'`INSERT ... SELECT RANDOM_UUID()`). Deux contextes Spring complets (Hibernate + scripts) sont démarrés dans la même minute sur deux bases ; on compare, pour TOUTES les tables de `PUBLIC` : comptage, lignes triées (somme de contrôle SHA-256 dans le message d'échec), et la liste des colonnes (les tables créées par le script lui-même). Valeurs normalisées, nommées dans le test : instants à moins de 30 minutes de « maintenant », `password_hash` (sel aléatoire de `SeedPasswordInitializer`), `attestation_droit.id` et `prise_en_charge.id` (`RANDOM_UUID()` du script), `modele_document.id` et `license.*` (provisionnés au démarrage, hors script). La copie de référence disparaît avec la dernière tranche si une empreinte stable devient possible (horloge injectée) ; jamais avant.
5. `renadial-*.sql` : NON découpé (voir `seed2-realisation.md`) : fichier généré à identifiants aléatoires et dates relatives, produit par un générateur à sortie unique ; la découpe exige de modifier le générateur ET de régénérer 5,7 Mo, hors parité possible. Les gardes datées 2027-06-30 qui citent `renadial-seed.sql`/`renadial-purge.sql` restent donc, avec leur butoir : le solde se fera par retrait du jeu RENADIAL du dépôt (décision produit) ou par régénération par propriétaire, pas par report.

## Conséquences
- `seed.sql` ne commence plus par les sociétés : `seance-se7e-pg16-rejeu.sh` (rejeu de `seed.sql` seul sur PostgreSQL) voit désormais `MERGE INTO caisse_assurance ... KEY` en première instruction ; son contrôle de fenêtre de lignes est ajusté (3-12).
- Aucune garde `Table*Test` modifiée (les nouveaux scripts sont soit dans le module propriétaire, exclu du balayage de sa garde, soit ne mentionnent aucune table gardée) ; aucun golden modifié ; `seamAllowed` reste `[]`.

## Reste (tranches suivantes, mêmes principes)
Patient-couverture (caisses, agences, payeurs, transporteurs, catégories, forfaits, patients, assurés, attestations, prises en charge, y compris les `INSERT ... SELECT` de patients générés), Comptabilité (`tva_types`), Facturation (`facturation_settings`, factures, lignes, règlements, `UPDATE seances SET facture_id`), Séance (séances, volets, tables créées par le script), Dossier médical (analyses, prescriptions), Stock (articles, bons, lots, mouvements) ; chacune solde l'entrée correspondante des gardes `TableDesSeancesTest`/`TableDesAnalysesTest`/`TableDuDossierMedicalTest`/`EcrivainsDeLaFactureDesSeancesTest` visant `db/seed.sql` quand `seed.sql` n'écrit plus leurs tables.
