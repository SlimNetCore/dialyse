---
status: accepted (aucun changement de comportement)
date: 2026-11-13
decision-makers: [utilisateur, architecte]
context-scope: platform (outil de reprise de données), dossier-medical, stock
related: ADR 0024, 0134, 0168, 0169, 0170, 0171 ; `.ddd/migration/reviews/rp3b-realisation.md` ; `.ddd/migration/reviews/inventaire-platform-restant-v2.md` ligne 11
---

# 0172 - RP3b : l'import de l'historique médical passe par le langage publié du Dossier médical, le contrôle des bons de sortie par celui du Stock

Refactoring sur place (ADR 0024) : mêmes URL, mêmes tables. Deuxième des trois sous-slices de RP3 (RP3a couverture : ADR 0171 ; RP3c séance historique et soldes d'ouverture reste à faire). Suite de RP2 (ADR 0170), qui avait déjà fait passer l'annulation des mêmes natures par `DonneesMedicalesDeLaReprise`.

## Décision
- **Dossier médical** : nouveau langage publié `RepriseDeLHistoriqueMedical` (dossier-medical.v1), mêmes cinq natures que `DonneesMedicalesDeLaReprise.NATURES_DU_LOT` (dossier de base, antécédents, sérologies, abords vasculaires, analyses ; liste fermée) : `dejaPresente(centre, nature, cle)` (plus ancienne ligne du centre à la clé ; une valeur nulle se lit « est nulle »), `reprendre(centre, nature, valeurs)` (ajout dans l'état final, date de création posée, date de modification posée sauf pour les abords vasculaires), `completer(centre, nature, donnee, valeurs)` (borné au centre, ne change jamais le patient). L'adapter `RepriseDeLHistoriqueMedicalJdbc` reprend MOT POUR MOT les descripteurs (liste blanche des champs par nature) et les requêtes de `HistoricalRecordJdbcAdapter` ; nature et champs sont contrôlés sur des listes fermées (`IllegalArgumentException`), aucune valeur d'un fichier n'est concaténée. Bean dans `DossierMedicalConfiguration`. Les règles de reprise (`HistoryRules` : contrôles, clés, valeurs) restent dans la plateforme.
- **Stock** : nouveau langage publié `BonsDeSortieDeLaReprise` (stock.v1), lecture seule : `bonsDeSortieDuPatient(patient)` (bons rattachés au patient, de N'IMPORTE QUEL centre : prudence voulue, comme KB-S6a-9 pour le dossier médical) et la constante `NATURE` (libellé que le refus d'annulation imprime). Adapter `BonsDeSortieDeLaRepriseJdbc` (même requête), bean dans `StockContexteConfiguration`.
- **Plateforme** : `HistoricalRecordJdbcAdapter` n'écrit plus aucune requête (plus de descripteur `TABLES`, plus de `JdbcTemplate`) : table de correspondance `MigrationEntity` -> nature (constantes du langage publié) ; `MigrationRollbackJdbcAdapter` n'a plus de `JdbcTemplate` ni de SQL : il lit les bons de sortie par le Stock.

## Gardes de table (butoirs soldés, aucun report)
- Entrée de `HistoricalRecordJdbcAdapter` SUPPRIMÉE de `TableDuDossierMedicalTest` (8 -> 7) et de `TableDesAnalysesTest` (5 -> 4), butoir 2027-06-30 : un test de verrou par garde (plus dans la liste blanche, ne lit ni n'écrit plus les tables).
- `TableDuStockTest` : l'exclusion des répertoires « /migration/ » (qui masquait le contrôle `bons_sortie` de l'annulation) est SUPPRIMÉE ; test de verrou (aucun source de la plateforme ne mentionne une table du Stock, l'adapter n'a plus de `JdbcTemplate`).
- `TableDesSeancesTest` : l'exemption nommée `MigrationEntity.java` (faux positif lié au constructeur de requêtes de `HistoricalRecordJdbcAdapter`) est SOLDÉE (devenue morte), `MENTIONS_NON_SQL` vide.
- ArchUnit (4 règles) : `RepriseDeLHistoriqueMedical` n'est utilisable que par `HistoricalRecordJdbcAdapter` qui doit l'utiliser ; `BonsDeSortieDeLaReprise` n'est utilisable que par `MigrationRollbackJdbcAdapter` qui doit l'utiliser. `seamAllowed` reste vide.

## Comportement
Aucun changement observable. Filet écrit sur le code d'avant (aucun golden d'import n'existait pour ces cinq natures ni pour `bons_sortie`) : 7 goldens `*_rp3b` (import du dossier de base, des antécédents, des sérologies, des abords vasculaires et des analyses : contrôles, formats limites de date, doublons dans le fichier, rejeu et mise à jour, valeurs vides qui n'effacent rien, ligne de la plateforme à la même clé, période de reprise ; autre centre et rôles (403 avant 400, `centerId` d'un autre centre, lot d'un autre centre, lot inconnu) ; annulation refusée par un bon de sortie, ordre des contrôles autour de lui, bon d'un autre centre). Approuvés un par un depuis `.received` (renommage, jamais `-Dcharacterization.approve`), identiques avant et après le refactoring : aucun `_nouveau_code`, aucune archive `_avant_rp3b`, aucune KB corrigée.

## Constats reproduits (à confirmer métiers/DPO <= 2026-12-15)
- Les règles de la plateforme (`HistoryRules`) n'écrivent pas les champs vides du fichier, y compris dans la clé de recherche : un abord vasculaire sans date de création est cherché par patient et type seulement (golden `import_abords_vasculaires_…` : une ligne sans date reprend l'abord du même type posé sans date par la plateforme). Le port reproduit ce comportement à l'identique (il n'en est pas la cause ; la lecture « valeur nulle » du port reste prévue par son contrat) ; la clé d'un abord n'est pas fiable si la date manque : **à confirmer métiers/DPO <= 2026-12-15**.
- Un champ vide du fichier n'efface jamais la valeur déjà écrite lors d'un rejeu (mise à jour partielle).
- Un bon de sortie d'un autre centre pour le même identifiant de patient bloque l'annulation (même prudence que KB-S6a-9).

## Suite possible
RP3c : séance historique (`HistoryRules` des séances) et soldes d'ouverture (`OpeningBalanceJdbcAdapter`).
