---
status: accepted (aucun changement de comportement ; KB-S6a-8 et KB-S6a-9 reproduits, A CONFIRMER métiers/DPO <= 2026-12-15)
date: 2026-11-13
decision-makers: [utilisateur, architecte]
context-scope: platform (outil de reprise de données), seance, dossier-medical
related: ADR 0024, 0099, 0101, 0134, 0168, 0169 ; `.ddd/migration/reviews/rp2-realisation.md` ; `.ddd/safety-net/known-bugs.md` KB-S6a-8, KB-S6a-9
---

# 0170 - RP2 : l'annulation d'un lot de reprise lit les volets par la Séance et supprime le dossier médical par son langage publié

Refactoring sur place (ADR 0024) : mêmes URL, mêmes tables. Suite de RP1 (ADR 0169). Filet : goldens de la reprise (ADR 0134, 0168, 0169) complétés par trois goldens RP2.

## Décision
- **Séance** : `SeancesDeLaReprisePubliees` (seance.v1, SE7e) gagne `laSeanceEstCompletee(seance)` : vrai si un volet médical OU paramédical existe, sans borne de centre (les deux requêtes de l'ancien contrôle, mot pour mot). Les séances du patient et la facture de la séance étaient déjà publiées (E14, E15).
- **Dossier médical** : nouveau langage publié `DonneesMedicalesDeLaReprise` (dossier-medical.v1) : `lignesDuPatient(patient, nature)` (tous centres, comme l'ancien SQL) et `supprimerLaDonneeDuLot(centre, nature, id)` (borné au centre du lot, natures du lot seulement : dossier de base, antécédents, sérologies, abords vasculaires, analyses). Les natures sont une liste fermée, dans l'ordre historique du contrôle, publiée par le contrat ; une autre nature lève `IllegalArgumentException`. Adapter `DonneesMedicalesDeLaRepriseJdbc`, bean dans `DossierMedicalConfiguration`. Les analyses (`resultats_analyses`) sont écrites et supprimées par ce même port (elles appartiennent au Dossier médical, ADR 0100).
- **Plateforme** : `MigrationRollbackJdbcAdapter` n'écrit ni ne lit plus `volet_medical`, `volet_paramedical`, `dossier_medical_patient`, `prescriptions_medicales`, `antecedents_medicaux`, `allergies_patient`, `serologies_patient`, `abords_vasculaires`, `resultats_analyses`, `observations_biologiques`, `ordonnances`, `demandes_examen`, `administrations_anemie`, `bilans_pre_greffe`. Il garde l'orchestration : ordre des contrôles, texte du refus `MIGRATION_CANCEL_BLOCKED` (inchangé), ordre de suppression. Restent en SQL dans l'adapter : `bons_sortie` (Stock, hors périmètre RP2) et la suppression des attestations et prises en charge (nom de table calculé, `HistoricalRecordJdbcAdapter` en reste l'écrivain : RP3).

## Gardes de table
Entrées de `MigrationRollbackJdbcAdapter` SUPPRIMÉES de `TableDesSeancesTest` (butoir 2027-03-31), `TableDuDossierMedicalTest` et `TableDesAnalysesTest` (butoir 2027-06-30) : soldées, aucun report. Un test de verrou par garde vérifie que l'adaptateur n'est plus dans la liste blanche ET ne lit plus les tables. ArchUnit : `DonneesMedicalesDeLaReprise` n'est utilisable que par l'adapter d'annulation, qui doit l'utiliser.

## Comportement
Aucun changement : les 3 goldens RP2, les goldens RP1, S6a et SE7e sont inchangés (parité). Constats toujours reproduits faute de décision : KB-S6a-8 (patient modifié depuis l'import supprimé sans avertissement) et KB-S6a-9 (lectures bloquantes sans borne de centre, y compris les volets, les analyses et les autres données médicales ; refus nommant la première nature) : **à confirmer métiers/DPO <= 2026-12-15**. Pas de fichier `_nouveau_code` ni d'archive `_avant_rp2`.

## Suite possible
RP3 : écritures historiques (`HistoricalRecordJdbcAdapter`, `OpeningBalanceJdbcAdapter`) par un port d'import par destinataire ; contrôle `bons_sortie` par le Stock.
