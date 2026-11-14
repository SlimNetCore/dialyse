---
status: accepted (sous-slice (i) livrée le 2026-11-14 ; (ii) et (iii) restent à faire)
date: 2026-11-14
decision-makers: [utilisateur, architecte]
context-scope: seance (consommateur) + patient-couverture (langage publié patientcouverture.v1)
related: ADR 0024, 0052, 0094, 0095, 0100, 0169-0175 ; `.ddd/migration/reviews/lec-pat-realisation.md` ; `inventaire-platform-restant-v2.md` ligne 14
---

# 0176 - LEC-PAT (i) : la Séance lit la programmation et les lots de patients par le langage publié de Patient et couverture

Refactoring sur place (ADR 0024) : mêmes URLs, mêmes tables, mêmes JSON. Comportement inchangé, aucun écart décidé.

## Contexte
Trois adaptateurs de la Séance lisaient la table des patients en SQL direct, inscrits à la liste blanche datée de `TableDuPatientCouvertureTest` (revue 2026-12-15, butoir 2027-03-31) : `PatientsDuJourJdbc` (journal du jour), `PatientsDuTableauDeBordJdbc` (tableau de bord mensuel, détail nominatif, exports), `PlanningDesPatientsJdbc` (planning d'un scan).

## Décision
1. **Deux ports publiés** (`patientcouverture.v1`, lecture, adaptateurs JDBC dans `infrastructure/persistence`, double en mémoire dans le contrat) :
   - `ProgrammationDesPatients` : `duCentre(centre)` et `duPatient(centre, patient)` rendent jours de dialyse, état, date d'évènement, admission, sommeil, sexe, naissance ; bornés au centre ; booléens et dates lus avec la tolérance du legacy.
   - `LotsDePatients` : `identitesDuCentre(centre)` (borné), `etatCivil(patients)` et `parNomPuisPrenom(patients)` (NON bornés au centre : la jointure du legacy ne filtrait pas le centre du patient, KB-SEA-61 et KB-SEA-64 reproduits). `parNomPuisPrenom` trie par nom puis prénom (collation de la base de Patient et couverture) et numérote les couples distincts (`rang`) : tri et collation restent côté Patient et couverture.
2. **Trois ACL côté Séance** (`infrastructure/acl/patientcouverture`) remplacent les trois adaptateurs SQL : elles lisent les séances dans la table de la Séance (`ORDER BY s.id`, donc collation d'identifiant inchangée), puis traduisent les patients par les ports. Journal du jour : jointure interne (séance sans fiche absente), tri stable par rang puis identifiant de séance : **identique** à `ORDER BY nom, prenom, s.id` du legacy, y compris pour des homonymes dont les séances s'entrelacent.
3. Les trois entrées de `TableDuPatientCouvertureTest` sont **supprimées** (jamais reportées) ; un test verrou interdit leur retour. `seamAllowed` reste vide.

## Alternatives écartées
- Un seul port bavard (une fiche par patient) : N+1 sur le journal et le tableau de bord.
- Borner `etatCivil` et `parNomPuisPrenom` au centre : changerait le comportement (patient d'un autre centre présent dans un centre) ; décision métiers/DPO non prise, reproduit.
- Trier le journal côté Séance en Java : la collation de la base (PostgreSQL) n'est pas celle de Java.

## Filet
Goldens existants (journal, tableau, détail, exports, scan, scan jour férié) inchangés = parité ; deux goldens ajoutés avant le changement et approuvés sur le code d'avant (`journal/.../homonymes_et_patients_sortis`, `tableau/.../presents_d_un_autre_centre`) ; contrat des deux ports sur le double en mémoire, H2 et PostgreSQL 16 ; tests ACL H2 de la Séance réécrits (homonymes ajouté).

## À confirmer métiers/DPO avant le 2026-12-15
KB-SEA-61 (journal : patient d'un autre centre rendu) et KB-SEA-64 (tableau : patient d'un autre centre compté présent), reproduits.

## Reste de LEC-PAT
(ii) caisse, forfait, PEC et (iii) historique des séances et effectif par centre : voir le rapport de réalisation. Butoir 2027-03-31 inchangé pour leurs entrées.
