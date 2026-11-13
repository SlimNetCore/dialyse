---
status: accepted (aucun changement de comportement ; colonnes de place gardées en écriture directe, A CONFIRMER métiers/DPO <= 2026-12-15)
date: 2026-11-13
decision-makers: [utilisateur, architecte]
context-scope: platform (outil de reprise de données), patient-couverture
related: ADR 0024, 0134, 0168, 0169, 0170 ; `.ddd/migration/reviews/rp3a-realisation.md` ; `.ddd/migration/reviews/inventaire-platform-restant-v2.md` ligne 11 ; `.ddd/safety-net/known-bugs.md` KB-S6a-3
---

# 0171 - RP3a : l'import de la couverture historique passe par le langage publié de Patient et couverture

Refactoring sur place (ADR 0024) : mêmes URL, mêmes tables. Première des trois sous-slices de RP3 (RP3a couverture, RP3b dossier médical et analyses + contrôle `bons_sortie` par le Stock, RP3c séance historique + soldes d'ouverture). Suite de RP1 (ADR 0169) et RP2 (ADR 0170).

## Décision
- **Patient et couverture** : nouveau langage publié `RepriseDeLHistoriqueDeCouverture` (patientcouverture.v1) : `attestationDebutee` / `priseEnChargeDemandee` (plus ancienne ligne du patient dans le centre dont le début est celui du fichier), `reprendreUneAttestation` / `reprendreUnePriseEnCharge` (ajout dans l'état final, date de création posée, aucune date de modification), `completerUneAttestation` / `completerUnePriseEnCharge` (seules les valeurs fournies sont écrites : une reprise n'efface rien). Tout est borné au centre. Cas d'usage `ReprendreLHistoriqueDeCouverture`, port sortant `CouvertureDeLaReprise`, adapter `CouvertureDeLaRepriseJdbc` (noms de colonnes littéraux, aucun nom de table venant d'un fichier). Bean dans `PatientCouvertureConfiguration`.
- **Annulation** : `AnnulationDeLaReprise` (RP1) gagne `supprimerUneAttestation(centre, id)` et `supprimerUnePriseEnCharge(centre, id)` (même requête, bornée au centre).
- **Plateforme** : `HistoricalRecordJdbcAdapter` ne décrit plus `attestation_droit` ni `prise_en_charge` (descripteur `TABLES` : 7 -> 5 entrées) et traduit les valeurs du fichier vers le langage publié ; `MigrationRollbackJdbcAdapter` ne supprime plus ces deux natures en SQL (la branche SQL générique a disparu) et nomme leurs lignes par les constantes du langage publié. Les règles de reprise (`HistoryRules`, contrôles, forfaits, valeurs par défaut de la demande) restent dans la plateforme.

## Gardes de table
Entrée de `HistoricalRecordJdbcAdapter` SUPPRIMÉE de `TableDuPatientCouvertureTest` (butoir de la reprise, retrait du pilotage legacy 2027-06-30 / 2027-03-31) : soldée, aucun report. Test de verrou : l'adaptateur n'est plus dans la liste blanche, ne mentionne plus ces tables et utilise le langage publié. ArchUnit : `RepriseDeLHistoriqueDeCouverture` n'est utilisable que par les deux adapters de la reprise, et `HistoricalRecordJdbcAdapter` doit l'utiliser. Restent à `HistoricalRecordJdbcAdapter` (liste blanche des autres gardes) : dossier médical, antécédents, sérologies, abords, analyses (RP3b).

## Colonnes de place écrites par la reprise (D5, ADR 0134, KB-S6a-3)
Décision par défaut de l'utilisateur : **garder l'écriture directe** des colonnes de place de la fiche par `RepriseDesPatients` (pas de passage par `PlacementDesPatients.placer`, qui ajouterait des refus). Cette décision ne change aucun code en RP3a (les patients sont déjà repris par leur langage publié depuis S6b-2) ; elle est tracée ici. **A confirmer métiers/DPO <= 2026-12-15.**

## Comportement
Aucun changement observable. Filet écrit sur le code d'avant (aucun golden d'import n'existait pour ces deux données) : 5 goldens `*_rp3a` (import des attestations : contrôles, formats limites de date, doublons, rejeu et mise à jour, ligne d'un autre centre, rôles avec 403 avant 400 ; import des prises en charge : forfaits inconnus ou d'un autre centre, valeurs par défaut de la demande, rejeu ; annulation du lot et lignes voisines ; annulation refusée par une ligne de la plateforme). Approuvés un par un depuis `.received`, identiques avant et après le refactoring : aucun `_nouveau_code`, aucune archive `_avant_rp3a`, aucune KB corrigée.

## Suite possible
RP3b : dossier médical, antécédents, sérologies, abords, analyses (import) et contrôle `bons_sortie` par le Stock. RP3c : séance historique et soldes d'ouverture (`OpeningBalanceJdbcAdapter`).
