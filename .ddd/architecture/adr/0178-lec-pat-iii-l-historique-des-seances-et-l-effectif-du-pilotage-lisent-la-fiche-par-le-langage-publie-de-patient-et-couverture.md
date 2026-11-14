---
status: accepted (sous-slice (iii) livrée le 2026-11-14 ; LEC-PAT soldée hors SituationsDesFacturesJdbc)
date: 2026-11-14
decision-makers: [utilisateur, architecte]
context-scope: seance et pilotage-direction (consommateurs) + patient-couverture (langage publié patientcouverture.v1)
related: ADR 0024, 0100, 0102, 0118, 0127, 0131, 0176, 0177 ; `.ddd/migration/reviews/lec-pat-realisation.md` ; `inventaire-platform-restant-v2.md` ligne 14
---

# 0178 - LEC-PAT (iii) : l'historique des séances et l'effectif du Pilotage lisent la fiche par le langage publié de Patient et couverture

Refactoring sur place (ADR 0024) : mêmes URLs, mêmes tables, mêmes JSON, mêmes effectifs. Comportement inchangé, aucun écart décidé.

## Contexte
Deux lecteurs faisaient encore du SQL direct sur `patients`, inscrits à `TableDuPatientCouvertureTest` (revue 2026-12-15, butoir 2027-03-31) : `HistoriqueDesSeancesJdbc` (Séance : jointure séances x patients avec filtre de texte, tri et pagination en SQL) et `PatientsDesCentresJdbc` (Pilotage direction : effectif de période RG-PAT-032, sexe et naissance, patients par centre payeur, prescriptions d'EPO et de fer).

## Décision
1. **Effectif** : nouveau port publié `EffectifDesPatients` (`parCentre`, `profils`, `parCentrePayeur`, `prescriptionsDeLAnemie`, période `debut`-`fin`), adapter `EffectifDesPatientsJdbc` : les requêtes et le prédicat de présence (RG-PAT-032) sont repris mot pour mot. Aucune ligne n'identifie un patient. Côté Pilotage, ACL `EffectifsParLaFiche` (port `EffectifsDesPatients` inchangé) : traduction, regroupement par caisse via l'annuaire des payeurs (KB-SEA-84, RF5) ; le seuil d'anonymat reste dans le domaine du Pilotage. `PatientsDesCentresJdbc` et la copie `PredicatDEffectif` du Pilotage sont supprimés (il reste deux copies du prédicat : Patient et couverture, `CompteursDeCouvertureJdbc.patientsPresentsLe` pour un jour, et `EffectifDesPatientsJdbc` pour une période ; la garde de dérive `PredicatDEffectifDuPilotageGardeIntegrationTest` les compare à la référence des tests).
2. **Historique** : `LotsDePatients.rechercherDuCentre(centre, texte, ordre, decroissant)` rend les patients DU CENTRE dont le nom, le prénom ou le code contient chaque mot du texte, dans l'ordre demandé (nom puis prénom, ou code), avec un rang par valeur distincte de la clé de tri. La recherche (LOWER, LIKE échappé), le tri, la collation et la place des valeurs nulles restent ceux de la base de Patient et couverture. Côté Séance : port `PatientsDeLHistorique` (domaine), ACL `PatientsDeLHistoriqueParLaFiche`. `HistoriqueDesSeancesJdbc` ne lit plus que `seances` : les séances du centre sont filtrées (dates, statuts, déverrouillage) et ordonnées en SQL (`ORDER BY <tri de séance>, created_at DESC, id ASC`), puis écartées si leur patient n'est pas rendu par la fiche (autre centre, disparu, texte non trouvé) ; l'ordre par patient ou par code est un tri STABLE par rang, donc identique à l'ancien `ORDER BY p.nom, p.prenom, s.created_at DESC, s.id` (jamais de comparaison d'identifiants en Java : l'ordre des UUID de la base n'est pas celui de Java). La pagination porte sur les identifiants retenus, puis les lignes de la page sont lues (motif de hors planning illisible : ligne ignorée et journalisée, comme avant).
3. Les deux entrées de `TableDuPatientCouvertureTest` sont **supprimées** (jamais reportées), un test verrou interdit leur retour. `seamAllowed` reste vide.
4. **`SituationsDesFacturesJdbc` : NON isolée, entrée conservée.** La lecture de `patients` (numéro d'assurance, nom, prénom, centre payeur courant) est une jointure externe dans un `GROUP BY` dont le tri `date_facturation DESC, numero_facture DESC` n'a pas de départage (KB-FAC-49 reproduit) : retirer la jointure change le plan et donc l'ordre des factures de même date et de même numéro (l'ordre de ces égalités dépend du plan de la base). Ce qu'il faudrait : (a) une décision métiers de départager par identifiant de facture (corrige KB-FAC-49, `*_nouveau_code` + archive + KB « CORRIGÉ »), puis (b) une lecture publiée `LotsDePatients` bornée au centre (numéro d'assurance, nom, prénom, centre payeur) appliquée en mémoire à la page des factures (comme l'annuaire des payeurs l'est déjà), (c) golden d'homonymes et de fiche d'un autre centre (la jointure est bornée au centre de la facture) avant le changement.

## Alternatives écartées
- Filtrer le texte et trier en Java côté Séance : la collation et le LOWER de la base ne sont pas ceux de Java (accents, majuscules).
- Passer les identifiants de séance à Patient et couverture : la Séance garde ses colonnes, la fiche ne connaît pas les séances.
- Un port « fiche complète » du patient : sur-lecture.

## Coût connu
L'historique charge les identifiants des séances retenues du centre (et la fiche des patients du centre) avant de paginer : volumétrie bornée au centre, à surveiller (le SQL paginait en base). Aucun index ajouté.

## Filet
Goldens existants inchangés = parité byte-à-byte : liste, filtres, tris et pagination de l'historique (se1), tableau de bord de la Direction (pd1, pl9, pd4), `SeanceHistoriqueIntegrationTest`. Goldens ajoutés AVANT le changement et approuvés un par un depuis `.received` sur le code d'avant : `seance/se1/lecture/l_historique_departage_les_homonymes/homonymes_et_patients_hors_centre` (homonymes, patient transféré et décédé, code nul, prénom accentué, patient d'un autre centre, séance sans patient, pagination) et `pilotagedirection/pd1/tableau_de_bord/l_effectif_applique_la_regle_des_eta/effectif_aux_bornes` (états de sortie et de séjour aux bornes, effectif nul, autre société, centre payeur d'un autre centre). Contrats : `LecturesPublieesContrat` (double en mémoire, H2, PostgreSQL 16 pour `rechercherDuCentre` et `EffectifDesPatients`), `LecturesReprisesDeLaPlateformeContrat` (historique avec homonymes de création différente, texte, autre centre), `LecturesDuPilotageContrat`.

## Reproduits, à confirmer métiers/DPO <= 2026-12-15
Aucun nouveau. Rappel : l'historique est borné au centre (le legacy aussi), contrairement aux lectures par lot de (i) (KB-SEA-61, KB-SEA-64). L'ordre des valeurs nulles (code absent) est celui de la base, non garanti par le contrat.
