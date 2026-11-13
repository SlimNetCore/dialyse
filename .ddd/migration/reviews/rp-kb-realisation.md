---
status: realise (KB-S6a-6 : maintien du refus, a confirmer metiers/DPO <= 2026-12-15)
date: 2026-11-13
sources: ADR 0134, ADR 0168, `.ddd/safety-net/known-bugs.md`, `inventaire-platform-restant-v2.md` ligne 8
---

# RP-KB - réalisation

| KB | Décision | Code | Filet |
|---|---|---|---|
| S6a-4 | corrigé : code ou nom complet du jour, sinon `INVALID_DAYS` | `LegacyValueParser.parseDays` | golden `patients_formats_invalides_et_assures_manquants_nouveau_code` ; ancien archivé `_ecarts-legacy/patients/..._avant_rp_kb.legacy.json` (écart : ligne « Lunch » refusée, 0 patient créé) |
| S6a-6 | refus du SUPERADMIN maintenu (aucun accès modifié) ; doc RG-MIG-001 corrigée ; **à confirmer métiers/DPO <= 2026-12-15** | aucun | golden `droits_et_garde_de_centre` inchangé |
| S6a-7 | corrigé : une exécution par donnée, départage par `id` | `MigrationBatchJdbcAdapter.findLatestRuns` | test `le_lot_ne_liste_qu_un_compte_rendu_par_donnee_meme_a_horodatage_egal` |

Fichiers legacy de platform modifiés (tous déjà dans `covered.json`) : `LegacyValueParser.java`, `MigrationBatchJdbcAdapter.java`. `RoleScopeFilter` et `CenterAccessGuard` : non modifiés. `seamAllowed` = []. Aucun report de butoir.

Suite possible (hors périmètre, sans filet sur les autres appelants) : retirer SUPERADMIN du `@PreAuthorize` de `MigrationRestController` et la branche SUPERADMIN de `CenterAccessGuard`.

Résultats des passes : voir la réponse de l'agent (logs `backend/rpkb-p1.log`, `rpkb-p2.log`, `rpkb-p3.log`, `rpkb-full.log`).
