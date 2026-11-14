---
status: accepted (sous-slices (a) et (b) livrées le 2026-11-14 ; (c) poids sec NON livrée, décrite ci-dessous)
date: 2026-11-14
decision-makers: [utilisateur, architecte]
context-scope: seance (consommateur) + planification (planification.v1) + gmao (gmao.v1) ; dossier-medical (dossier-medical.v1) pour (c)
related: ADR 0024, 0099, 0100, 0101, 0176, 0177, 0178 ; `.ddd/migration/reviews/lec-pln-gmao-realisation.md` ; `inventaire-platform-restant-v2.md` ligne 15
---

# 0179 - LEC-PLN-GMAO : la Séance lit le déplacement du jour et le générateur par les langages publiés de la Planification et de la GMAO

Refactoring sur place (ADR 0024) : mêmes URLs, mêmes tables, mêmes JSON. Comportement inchangé, aucun écart décidé.

## Contexte
Trois lectures SQL de la Séance sur des tables d'autres contextes, inscrites à des gardes datées (revue 2026-12-15, butoir 2027-03-31) : `PlaceDuPatientJdbc` (`deplacement_temporaire`, `TableDeLaPlanificationTest`), `GenerateursDesPatientsJdbc` (`gmao_equipements`, `TableDesEquipementsDeLaGmaoTest`), `StatistiquesDesSeancesJdbc` (`prescriptions_medicales`, `TableDuDossierMedicalTest`).

## Décision
1. **(a) Planification** : port publié `DeplacementsDuJourDesPatients.duPatient(centre, patient, jour)` (record `DeplacementDuJour(salle, creneau, generateur)`), servi par le cas d'usage `ConsulterLesDeplacementsDuJour` au-dessus du dépôt existant `DeplacementsTemporairesRepository.entre(centre, jour, jour)` (aucun SQL nouveau, dépôt déjà couvert par `OptimisationContrat` en mémoire, H2 et PostgreSQL 16). Côté Séance, ACL `PlaceDuPatientParLaPlanification` : le déplacement prime sur la place de la fiche (`PlacesDesPatients`), sans salle ou sans créneau = vide. `PlaceDuPatientJdbc` supprimé ; entrée de `TableDeLaPlanificationTest` supprimée, liste blanche VIDE + test verrou.
2. **(b) GMAO** : port publié `FichesDesGenerateurs.duCentre(centre, equipement)` (record `FicheDuGenerateur(id, code, fabricant, statut)`), adapter `FichesDesGenerateursJpa` (requête JPQL `ficheDuCentre` : centre, identifiant, supprimé exclu, TOUT type d'équipement comme le SQL repris). `GenerateursDuCentre` n'est pas modifié (il ne porte pas le fabricant et ses autres consommateurs, caches et doubles restent intacts). Côté Séance, ACL `GenerateursDesPatientsParLaGmao` ; dépendance Maven explicite `contexts-gmao` (déjà transitive par la Planification). `GenerateursDesPatientsJdbc` supprimé ; entrée de `TableDesEquipementsDeLaGmaoTest` supprimée + test verrou.
3. Contraintes de données : la table réelle porte `UNIQUE (center_id, patient_id, date_seance)` et `NOT NULL` sur salle, créneau, générateur : le cas « déplacement sans créneau » du contrat de la Séance n'existe pas en base ; il est conservé comme test de l'ACL sur un port double (déplacement incomplet ignoré).
4. **(c) Poids sec : NON livrée (budget de fichiers), à faire.** Contrainte découverte : `contexts-dossier-medical` dépend de `contexts-seance` (ACL `SeancesDuPatientPubliees`) ; la Séance ne peut donc PAS dépendre de dossier-medical.v1 (cycle Maven). Plan retenu : (i) dossier-medical.v1 ajoute `poidsSecsCiblesPrescrits(centre, patient)` -> liste (date de prescription, poids en `BigDecimal` NUMERIC(5,2), pour garder le JSON byte-à-byte) triée par `date_prescription DESC, updated_at DESC NULLS LAST, id DESC` (tri côté propriétaire, repris de `POIDS_SEC_A_LA_SEANCE_EXPR`) ; NE PAS réutiliser `poidsSecCibleEnVigueur` (départage différent : pas de `NULLS LAST` ni d'identifiant) ; (ii) la Séance déclare son port `PoidsSecDesPrescriptions` (application) ; (iii) `StatistiquesDesSeancesJdbc` retire la sous-requête corrélée et applique en mémoire « première prescription datée <= jour de la séance » à chaque ligne en conservant l'ordre des clés (`date_seance, poids_avant_kg, poids_apres_kg, poids_sec_cible_kg, uf_reelle_ml`) ; (iv) l'implémentation du port de la Séance est une ACL dans dossier-medical (`infrastructure/acl/seance`, qui dépend déjà de la Séance), câblée par `IndicateursEtStatistiquesConfiguration` ; (v) contrat triple sur la liste (en mémoire, H2, PostgreSQL 16) avec cas d'égalité de date et `updated_at` nul ; (vi) supprimer l'entrée `StatistiquesDesSeancesJdbc` de `TableDuDossierMedicalTest` (`hasSize(7)` -> 6 et `containsExactly` à ajuster). L'entrée garde son butoir 2027-03-31 d'origine jusqu'à cette sous-slice (aucun nouveau report).

## Alternatives écartées
- Étendre `PlacesDesPatients` : tous ses doubles et consommateurs devraient changer pour une lecture qui n'est pas la place habituelle.
- Ajouter `fabricant` à `GenerateursDuCentre.Generateur` : le record est construit par la Planification, Patient et couverture, l'Organisation (cache) et les doubles du pilotage.
- Chercher le déplacement par une requête dédiée : le dépôt couvre déjà `entre(centre, jour, jour)` ; coût = les déplacements du centre pour ce jour (peu).

## Filet et parité
Goldens existants du consommateur inchangés (byte-à-byte) : validation (SE2), lectures de place consommées par la Séance (P6), déplacements et séances réalisées (planification/semaine), générateur du patient. Aucun golden ajouté (aucun comportement observable nouveau). Contrats : gmao `PilotageContrat` +1 (en mémoire, JPA H2, PostgreSQL 16 : IT ignoré sans `-D...pg16.url`), Séance `LecturesReprisesDeLaPlateformeContrat` (déplacement via l'adapter JDBC réel de la Planification sur H2 et PostgreSQL 16), `ConsulterLesDeplacementsDuJourTest`.

## Reproduits, à confirmer métiers/DPO <= 2026-12-15
Aucun nouveau. Le générateur n'est pas filtré par type d'équipement (comme le SQL d'origine).
