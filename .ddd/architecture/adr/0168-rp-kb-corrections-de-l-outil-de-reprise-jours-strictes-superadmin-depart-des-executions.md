---
status: accepted pour KB-S6a-4 et KB-S6a-7 ; KB-S6a-6 = maintien du refus, A CONFIRMER métiers/DPO <= 2026-12-15
date: 2026-11-13
decision-makers: [utilisateur, architecte]
context-scope: platform (outil de reprise de données)
related: ADR 0024, 0131, 0134 ; `.ddd/safety-net/known-bugs.md` KB-S6a-4, KB-S6a-6, KB-S6a-7 ; `.ddd/migration/reviews/rp-kb-realisation.md`
---

# 0168 - RP-KB : jours de dialyse stricts, politique SUPERADMIN de la reprise, départage des exécutions d'un lot

Refactoring sur place (ADR 0024) : mêmes URL, mêmes tables. Filet : goldens de la reprise (ADR 0134).

## KB-S6a-4 - jours de dialyse (CORRIGÉ)
Avant : un mot dont les 3 premières lettres sont un jour était accepté (« Lunch » = LUN). Après : un jeton est accepté s'il est un code (LUN, MAR, MER, JEU, VEN, SAM, DIM) ou un nom complet (Lundi à Dimanche, accents et casse ignorés) ; sinon `INVALID_DAYS`. Les formes courantes (« Lundi, Mercredi, Vendredi », « Lun et Mer ») restent acceptées. Écart : seules les abréviations de 4+ lettres (« Merc ») et les mots parasites commençant par un jour sont désormais refusés. Golden `patients_formats_invalides_et_assures_manquants` -> `_nouveau_code` (la ligne « Lunch » devient une anomalie, 0 patient créé) ; ancien golden archivé `_ecarts-legacy/patients/..._avant_rp_kb.legacy.json`.

## KB-S6a-7 - exécutions de même horodatage (CORRIGÉ)
Avant : `executed_at = MAX(...)` sans départage : deux exécutions d'une même donnée au même instant étaient toutes deux listées par `GET /batches/{id}`. Après : une seule exécution par donnée (la plus récente ; à égalité, le plus grand `id`), tri `executed_at, id`. Aucun changement hors égalité d'horodatage. Verrouillé par `le_lot_ne_liste_qu_un_compte_rendu_par_donnee_meme_a_horodatage_egal`.

## KB-S6a-6 - SUPERADMIN (AUCUN CHANGEMENT DE COMPORTEMENT ; décision de sécurité à confirmer)
Constat exact, verrouillé par le golden `droits_et_garde_de_centre` (inchangé) :
| Qui | Aujourd'hui sur `/api/v1/admin/migration/**` |
|---|---|
| `ADMIN` d'un centre | voit et modifie les lots, imports, correspondances, annulations de SON centre ; `centerId` d'un autre centre = 403 (garde de centre) ; lot d'un autre centre = 422 |
| `SUPERADMIN` | **403 `ROLE_SCOPE`** sur toutes les routes (GET et POST), car `RoleScopeFilter` ne lui ouvre que sociétés, organisation, licences, audit, supervision et `auth/system`. Il ne voit ni ne modifie rien dans la reprise |
| autres rôles | 403 (golden `la_reprise_est_reservee...`) |
Incohérences relevées : `@PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")` sur `MigrationRestController`, la branche SUPERADMIN de `CenterAccessGuard.requireCenter` et la règle RG-MIG-001 (« réservé à ADMIN et SUPERADMIN ») annoncent un accès que le filtre rend inatteignable.

Options : (A) maintenir le refus (aligné sur la doctrine du filtre : le SUPERADMIN n'accède à aucune donnée de centre, secret médical, isolation entre clients) ; (B) ouvrir la reprise au SUPERADMIN (donnée de santé de tout centre en lecture/écriture : ÉLARGISSEMENT d'accès, y compris annulation destructrice de lots) ; (C) retirer SUPERADMIN du `@PreAuthorize` et de la branche du garde (nettoyage sans effet visible, mais la branche sert aussi les contrôleurs du dossier médical : non couvert ici).
**Décision : A.** Aucun accès n'est élargi ni restreint ; seule la documentation est corrigée (RG-MIG-001 : SUPERADMIN refusé). Le code mort (@PreAuthorize, branche du garde) est laissé en place faute de filet sur les autres appelants du garde ; son retrait (C) est une suite possible.
**À confirmer métiers/DPO <= 2026-12-15** : le SUPERADMIN doit-il pouvoir assister une reprise (option B, avec journalisation d'audit et accord client) ? Sans réponse, A reste en vigueur.
