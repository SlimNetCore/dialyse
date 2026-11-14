---
status: accepted (sous-slice (ii) livrée le 2026-11-14 ; (iii) reste à faire)
date: 2026-11-14
decision-makers: [utilisateur, architecte]
context-scope: seance (consommateur) + patient-couverture (langage publié patientcouverture.v1)
related: ADR 0024, 0097, 0098, 0099, 0100, 0118, 0124, 0127, 0176 ; `.ddd/migration/reviews/lec-pat-realisation.md` ; `inventaire-platform-restant-v2.md` ligne 14
---

# 0177 - LEC-PAT (ii) : la Séance lit la fiche, la caisse et le forfait par le langage publié de Patient et couverture (ARGENT)

Refactoring sur place (ADR 0024) : mêmes URLs, mêmes tables, mêmes JSON, mêmes montants. Comportement inchangé, aucun écart décidé.

## Contexte
Quatre lecteurs de la Séance faisaient du SQL direct sur `patients`, `prise_en_charge` et `forfait`, inscrits aux listes blanches datées (revue 2026-12-15, butoir 2027-03-31) : `ForfaitsDeSeanceJdbc` (forfait courant, `TableDuPatientCouvertureTest` et `TableDesForfaitsTest` clock-checked), `FacturationDesSeancesJdbc` (séances à facturer, tri nom/prénom), `SeancesRealiseesDesCentresJdbc` (séances par caisse), `LecturesDesSeancesRealiseesJdbc` (séances réalisées publiées à la Planification avec nom et prénom).

## Décision
1. `CouvertureDesPatients.ForfaitApplicable` gagne `code` (le forfait courant de la Séance l'expose) : même requête, une colonne de plus.
2. `LotsDePatients` gagne deux lectures **bornées au centre** : `aFacturerDuCentre(centre, patients)` (code, nom, prénom, état, numéro d'assurance, centre payeur, rang de (nom, prénom), triés par la collation de la base de Patient et couverture) et `centresPayeursDuCentre(centre, patients)`. `identitesDuCentre` (déjà publiée) sert les noms de la Planification. Contrat commun : double en mémoire, H2, PostgreSQL 16.
3. Côté Séance : port `FichesDesPatientsDuCentre` (domaine) et `ForfaitDeLaPriseEnCharge` (application), ACL `FichesDesPatientsDuCentreParLaFiche` et `ForfaitDeLaPriseEnChargeParLaCouverture` (`infrastructure/acl/patientcouverture`). Les séances restent lues dans la table de la Séance ; la jointure interne ou externe de l'ancien SQL est reproduite par l'ACL : séance dont le patient n'est pas une fiche du centre écartée (facturation, séances publiées), ou rangée sous la caisse vide (pilotage). Le tri nom puis prénom de la facturation est le rang rendu par la fiche, appliqué par un tri stable à des séances déjà ordonnées par date puis identifiant : identique à `ORDER BY p.nom, p.prenom, s.date_seance, s.id`. Le forfait imposé sur la séance reste lu dans la table de la Séance et prime.
4. Les quatre entrées de `TableDuPatientCouvertureTest` et l'entrée unique de `TableDesForfaitsTest` sont **supprimées** (jamais reportées) ; `TableDesForfaitsTest` n'a plus aucun lecteur connu ; un test verrou interdit leur retour. `seamAllowed` reste vide.
5. `SituationsDesFacturesJdbc` (entrée JOINTURE, six tables, KB-FAC-49) n'est PAS touchée : hors ligne 14, la jointure `factures` x `patients` x `facture_reglements` est dans un GROUP BY dont découper la lecture changerait le résultat ; elle reste une entrée de la liste (butoir inchangé).

## Alternatives écartées
- Un port « fiche complète » : N+1 ou sur-lecture ; les lectures par intention restent minimales.
- Borner le forfait courant autrement : `forfaitApplicable` est la règle exacte du legacy (effectif d'abord, plus récente d'abord, repli sans date), vérifiée par le contrat existant.
- Trier la facturation en Java : la collation de PostgreSQL n'est pas celle de Java.

## Filet
Goldens existants inchangés = parité byte-à-byte : forfait courant (se5), aperçu, émission, exclusion/forfait, séances de la facture (facturation), pilotage de la Direction (se7e), séances lues par la Planification (se7d). Golden ajouté avant le changement et approuvé un par un sur le code d'avant : `facturation/simulation/apercu/ordre_nom_prenom_homonymes_fiche_autre_centre_{regroupement,par_forfait}_200` (homonymes, patient transféré, sans prise en charge, fiche d'un autre centre, prise en charge échue, forfaits multiples). Contrats des nouvelles lectures sur le double en mémoire, H2 et PostgreSQL 16.

## Reproduits, à confirmer métiers/DPO <= 2026-12-15
KB-SEA-80 (séance publiée à la Planification : fiche d'un autre centre écartée), KB-SEA-83 (séance sur fiche d'un autre centre rangée sous la caisse vide), KB-PAT-25 (statut de la prise en charge non regardé pour le forfait).
