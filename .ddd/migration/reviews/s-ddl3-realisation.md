---
status: delivered partiellement (S-DDL2b complète ; S-DDL3 : 6 tranches sur ~14) ; build complet voir fin
date: 2026-11-14
sources: [ADR 0186, ADR 0185, ADR 0137, inventaire-platform-restant-v2.md ligne 17]
---
# S-DDL2b + S-DDL3 : DDL H2 par contexte propriétaire

## Constat avant code (mécanisme)
- `application.yml` : `spring.sql.init.mode=always`, `schema-locations: classpath:db/schema.sql`, `data-locations: ${SEED_DATA_LOCATIONS:classpath:db/seed.sql}` ; Hibernate (`ddl-auto: update`, `defer-datasource-initialization`) passe avant les scripts ; `application-prod.yml` : `never` ; profil `demo-renadial` remplace `data-locations` (seed.sql + renadial-seed.sql).
- ADR 0137 (`BaseH2UniqueParContexte`) : une URL H2 par contexte Spring de test, pas de schéma par contexte. Avant : AUCUN contexte ne portait de script H2 de production (`Grep CREATE TABLE` dans `contexts/*/src/main` : 0) ; leurs tests ont un DDL en ligne.
- Jars des contextes sur le classpath de la plateforme : un `classpath:db/h2/<ctx>-schema.sql` se résout comme `db/schema.sql`. Seules FK de `schema.sql` : identité (app_user*, centers) et `groupes_articles_items` -> `groupes_articles` (même tranche). `guard-legacy` ne couvre que `backend/**/src/main/java/**` : aucun fichier legacy Java modifié, `covered.json` non touché.

## Livré (fichiers de production : 10)
`contexts/<ctx>/src/main/resources/db/h2/` : `comptabilite-schema.sql` (app_settings), `comptabilite-seed.sql` (4 MERGE, retirés de `seed.sql`), `documents-schema.sql` (modele_document, modele_document_version), `notifications-schema.sql` (notification_evenement/_lecture + ALTER), `absences-patients-schema.sql` (absence_patient, mois_absences, mois_absences_journal), `planification-schema.sql` (planification_optimisation + 2 ALTER, planification_calendrier_case, planning_preference_patient, infirmier_profil_planning, planification_reglages, deplacement_temporaire), `stock-schema.sql` (19 tables, 2 ALTER de stock_movements). `application.yml` (liste ordonnée + seed), `application-demo-renadial.yml`, `schema.sql`/`seed.sql` réduits (bloc à bloc, commentaires de renvoi).
Ordre de chargement : `schema.sql` puis comptabilite, documents, notifications, absences-patients, planification, stock (aucune FK inter-scripts : l'ordre est sans effet, prouvé ci-dessous ; l'ordre global n'a pas pu rester identique ligne à ligne car les blocs étaient entrelacés).

## Preuve de strict identité (permanente)
`SchemaH2DesScriptsParContexteTest` (platform) : rejoue la liste de `application.yml` sur H2 vierge, compare INFORMATION_SCHEMA (1100 colonnes avec rang/type/largeur/précision/nullité/défaut/identité, contraintes, CHECK, index) à `src/test/resources/db/schema-h2-reference.txt`, relevé AVANT la scission sur l'ancien script unique (vert), puis vert après les 6 tranches. Noms H2 engendrés normalisés (`CONSTRAINT_xx`, `_INDEX_hex`). Régénération : `-Dschema.reference.ecrire=true`.

## Tests
Trois passes ciblées (normale, `-Dsurefire.runOrder=random`, normale) : `Schema*`, `Table*Test`, `GoldensNouveauCodeGardeTest`, caractérisation stock, comptabilité, documents, notifications, absences-patients, planification : 0 [ERROR]. `DemoRenadialProfileValidationTest` vert. Goldens : aucun `.approved` modifié.

## Gardes datées 2027-06-30
Aucune entrée soldable dans cette tranche : les trois qui citent `schema.sql` (`TableDesSeancesTest`, `TableDesAnalysesTest`, `TableDuDossierMedicalTest`) portent sur `seances/volet_*`, les analyses et le dossier médical, dont le DDL reste dans `schema.sql`. Les entrées stock/comptabilité/documents/notifications/absences/planification n'existaient pas (aucune garde ne citait leur DDL). Aucun report.

## MISE À JOUR S-DDL3b (2026-11-14, ADR 0187) : LIVRÉE
63 tables déplacées (organisation 9, patient-couverture 12, pilotage-direction 2, planification +5, comptabilité +5 dont `tva_types`, facturation 7, séance 5, dossier médical 18). RESTE dans `schema.sql` : 15 tables (identité, licence, audit, reprise, outbox : ADR 0140 D7), par propriétaire : plateforme 15. Ordre : organisation avant `schema.sql` (FK `app_user_center` -> `centers`) et avant planification (UPDATE `salle`). Les `ALTER seances` de facturation étaient des no-op (colonnes déjà au CREATE) : supprimés. Parité : référence inchangée, test vert ; gardes `TableDesSeancesTest`, `TableDesAnalysesTest`, `TableDuDossierMedicalTest` : entrée `schema.sql` supprimée. Section ci-dessous = état AVANT S-DDL3b (historique).

## (historique) RESTE : 78 tables dans `schema.sql` (par propriétaire)
- seance (5) : `seances`, `volet_paramedical`, `volet_medical`, `seance_suppression`, `seance_raccourcis_articles` (propriétaire à confirmer). Bloquant : les `ALTER ... seances` de facturation (forfait_override_*, facture_id, exclusion) doivent migrer avec ou après ; solde les gardes `TableDesSeancesTest` (schema.sql).
- dossier-medical (18) : `resultats_analyses`, `abords_vasculaires`, `administrations_anemie`, `alertes_observance`, `allergies_patient`, `antecedents_medicaux`, `bilans_pre_greffe`, `decisions_rcp`, `demandes_examen`, `donneurs_vivants`, `dossier_medical_patient`, `etapes_bilan_pre_greffe`, `lignes_demande_examen`, `lignes_ordonnance`, `observations_biologiques`, `ordonnances`, `prescriptions_medicales`, `serologies_patient` ; solde `TableDesAnalysesTest` / `TableDuDossierMedicalTest` (schema.sql).
- patient-couverture (12) : `patients`, `caisse_assurance`, `agence`, `centre_payeur`, `transporteur`, `categorie_transport`, `forfait`, `assure`, `assure_patient`, `prise_en_charge`, `attestation_droit`, `mouvement_patient`.
- organisation (9) : `medecin`, `machine`, `protocole`, `salle` (+ ALTER isolement/capacite et UPDATE croisés avec `planning_parametres`), `position_creneau`, `societes`, `centers`, `center_holiday`, `center_closure_day`.
- planification (5) : `planning_parametres` (liée aux UPDATE de reprise des salles d'isolement), `infirmier`, `infirmier_affectation`, `infirmier_absence`, `infirmier_remplacement` (+ ALTER declaree_par, user_id, index unique).
- facturation (7) : `facturation_settings`, `facture_sequence`, `factures`, `facture_lignes`, `facture_reglements`, `periodes_facturation`, `periodes_facturation_journal`.
- comptabilite (5) : `comptes_comptables`, `comptes_payeurs`, `modeles_piece`, `modeles_piece_lignes`, `tva_types` (partagée facturation).
- pilotage-direction (2) : `direction_snapshot`, `direction_alert_history`.
- plateforme (identité, licence, audit, reprise, outbox : 15) : `license`, `app_role`, `app_user`, `app_user_role`, `app_user_center`, `auth_refresh_token`, `app_user_societe`, `app_user_mfa`, `app_user_mfa_recovery`, `audit_log`, `migration_batch`, `migration_entity_run`, `migration_id_map`, `migration_value_map`, `event_publication` (identité : reste en plateforme tant qu'aucun contexte ne la possède).
Total : 78. Méthode pour la suite : copier un bloc dans `db/h2/<ctx>-schema.sql`, le retirer de `schema.sql`, ajouter le chemin à `schema-locations`, relancer `SchemaH2DesScriptsParContexteTest`.

## Seed
Seul `app_settings` (S-DDL2b). SEED2..n inchangé : `seed.sql` (2 443 l. moins 14), `renadial-*.sql`. Note : `SEED_DATA_LOCATIONS` doit lister `classpath:db/h2/comptabilite-seed.sql` ; `seance-se7e-pg16-rejeu.sh` ne rejoue plus les 4 compteurs.
