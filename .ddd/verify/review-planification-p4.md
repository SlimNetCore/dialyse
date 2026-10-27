---
status: proposed
date: 2026-10-27
sources: git diff/status non commités après d5e09afe ; check-arch.ts ; ADR 0058 ; fiche planification-p4-presence-cron ; known-bugs KB-PLN-42..51
---

# Revue indépendante — slice planification-p4-presence-cron

Contrôles automatiques : `check-arch.ts` → 0 violation (0 baseline, 0 nouvelle). Aucun build/test lancé (consigne).

| # | Sévérité | Domaine | Constat | Preuve | Correction recommandée |
|---|---|---|---|---|---|
| 1 | major | tests/legacy | **Le filet P4 n'a jamais été commité dans son état « vert sur legacy »** (contrairement à P2/P3 : 01340cb5, 5ed13a71) et les 21 goldens « changés par décision » ont été **réécrits en place** : aucun fichier `*_nouveau_code.approved.json` n'existe dans `p4-presence/` (131 fichiers, 0 suffixe), et `PresenceCharacterizationSupport` n'utilise pas la convention `_nouveau_code` / `_ecarts-legacy` de P1. Le legacy étant supprimé, le comportement d'origine des 21 scénarios n'est plus traçable. | `PlanificationCharacterizationSupport.java:256-259` (convention P1) ; `git status` : `p4-presence/` et `characterization/planification/presence/` en `??` ; fiche `slices/planification-p4-presence-cron.md:37-38` | Restaurer les goldens legacy (avant adaptation) sous `_ecarts-legacy/` ou committer séparément le filet legacy puis l'adaptation ; corriger l'affirmation « 21 goldens `_nouveau_code` ». |
| 2 | major | legacy | **Écart (1) non décidé** : borne de lecture/écriture du ratio 1–20 au lieu de 1–99 décidé. Le legacy (KB-PLN-47) n'échouait qu'à partir de 100 : une valeur 21–99 en base, utilisée par le legacy, est désormais lue comme 4 (effectif requis différent, alertes différentes). L'argument « aucune route n'a pu écrire 21–99 » n'est pas prouvé (aucune contrainte en base : `schema.sql:986`). | `RatioDeLaPresence.java:13,25` ; `RatioDeSecurite.java:10` ; `ParametresDuPlanningJdbc.java` (`ratioLu`) ; ADR 0058 §6 | Faire trancher l'utilisateur au gate ; en attendant, exécuter le SELECT n°1 de `verifications-production.md` sur la production : 0 ligne ⇒ écart sans effet. |
| 3 | minor | archi/docs | Javadoc incohérente : « hors 1 à 99 » alors que le code applique 1–20. | `PersonnelDeLaPresenceJdbc.java:28` | Aligner sur la décision finale. |
| 4 | minor | tests | Nouvelle route `GET …/presence/alertes-sureffectif` absente de la matrice de droits (5 routes × 11 profils) ; seul `adminA()` l'appelle. Comportement attendu (MEDECIN seul → 403 via `DOCTOR_DENIED` par préfixe) non verrouillé. | `AlertesDeSousEffectifCharacterizationIntegrationTest.java:193,215` ; `RoleScopeFilter.java:88-104` | Ajouter la route à `AutorisationsDeLaPresenceCharacterizationIntegrationTest`. |
| 5 | minor | archi | Le cron n'isole pas l'échec de `centres.liste()` (exception hors try) : tout le run échoue sans log `[PRESENCE]`. | `ControlerLaPresence.java:50-56` | try/catch + log WARN dans `ControleDeLaPresenceDe0715.controlerLaPresence()`. |
| 6 | minor | legacy | Écart (3) : la clé (type, centre, jour) fige l'alerte du jour ; un sous-effectif apparu après 07:15 (ou une 2e exécution) n'alerte plus. Conforme à la décision « dédupliquées par clé (centre, jour, type) », mais le test d'intégration a été adapté en supprimant l'alerte avant réexécution. | `NotificationsDeLaPresenceLegacy.java:33-35,49-62` ; fiche:54-56 | Garder en question ouverte (ADR 0058) ; pas de correction sans décision. |
| 7 | minor | tests | Le scénario d'intégration « échec d'un centre » a été remplacé par « ratio illisible » : l'isolation par centre n'est plus prouvée qu'avec des doubles. | fiche:55-56 | Acceptable ; idéalement un scénario intégré provoquant une erreur (ex. fuseau illisible) sur un centre. |
| 8 | minor | stratégique | Dette (4) : double calcul de présence (`PresenceDonneesPort`, `PresenceDonneesJdbcAdapter`, `PresenceInfirmierService.construire/creneauxDe/requis`) jusqu'à P8 ; « Mon planning » garde l'ancien comportement de double affectation. Dette **acceptable** car documentée (ADR 0058 Conséquences, seams.md) et code mort supprimé, mais divergence possible entre écrans. | `SemaineDeLInfirmierLegacy.java` (diff) ; ADR 0058 Conséquences | Inscrire une date/slice de retrait explicite (P8) dans la roadmap. |
| 9 | minor | archi | Le cron unique à 07:15 Africa/Algiers sert tous les centres : un centre d'un autre fuseau est contrôlé à une autre heure locale (le « jour » est bien celui du centre). | `ControleDeLaPresenceDe0715.java:134` ; `ControlerLaPresence.java:61` | Documenter la limite (ou cron horaire + filtre heure locale si multi-fuseau réel). |
| 10 | nit | tactique | Écart (2) : « jours d'absence » de la charge compte toujours les jours fermés — **comportement legacy conservé à l'identique** (non décidé), donc pas une violation. | `ChargeDesInfirmiers.java:121-123` vs legacy `ChargeInfirmierService` (HEAD) | Question ouverte, rien à corriger. |
| 11 | nit | tactique | Read model en records à `UUID` bruts alors que `SalleId`/`CreneauId` existent ; services statiques purs (acceptable côté lecture/CQRS). | `Presence.java:43-137` | Optionnel. |
| 12 | nit | archi | `au` calculé deux fois. | `ControlerLaPresence.java:68,74` | Factoriser. |
| 13 | nit (suggestion) | sécurité | Échec de génération : message de la cause racine renvoyé au client en 422. | `ImpressionDeLaPresenceLegacy.java` (`cause()`, l. ~66-80 du corps) | Journaliser la cause, renvoyer un message générique. |

## Points vérifiés sans constat
- Calculs : `requis` = arrondi supérieur, statut/manque/surplus, isolement, absences, remplacements, semaine dimanche→samedi identiques au legacy (`PresenceInfirmierService` HEAD:120-217) ; charge identique hors écarts décidés (tri nom/prénom, remplacements ignorés absence/fermeture).
- Fuseau : « aujourd'hui »/mois par défaut via `HorlogeDesCentres` par centre ; cron `zone` paramétrée.
- Idempotence multi-instance : insertion à id déterministe **avant** diffusion (`NotificationService.java:494-518`), violation d'unicité relue (`NotificationsDeLaPresenceLegacy.java:53-60`) ; contrôles sous/sur-effectif et centres isolés par try/catch.
- Centres : `centers.actif = TRUE` uniquement (`CentresActifsLegacy.java:235`), centres sans infirmier inclus.
- Droits : annotation inchangée ; centre résolu par `session.centre(centerId)` sur chaque route.
- Seams : fichiers legacy édités/supprimés tous dans `covered.json` (dont `NotificationService`, `SemaineDeLInfirmierLegacy`) ; `seamAllowed: []`.
- Fichiers : aucun BOM, aucun mojibake dans les fichiers de la slice ; CRLF seulement là où HEAD l'avait déjà. Dates = 2026-10-27 (date système). Tests unitaires/contrat sans `now()`.

## Verdict : **pass-with-actions**

Top 3 actions :
1. Restaurer la traçabilité des goldens legacy (constat 1) avant commit.
2. Faire trancher la borne du ratio 1–20 vs 1–99 au gate + exécuter le SELECT n°1 (constat 2) ; corriger la Javadoc (constat 3).
3. Verrouiller les droits de `alertes-sureffectif` et isoler l'échec de `centres.liste()` (constats 4, 5).
