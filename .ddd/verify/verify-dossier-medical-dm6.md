---
status: proposed
date: 2026-11-07
sources: commits 19b797f2 (filet), fb27c123 (reprise), ADR 0085/0082, check-arch
---
# Revue DM6 biologie (observations biologiques + analyses)

Contrôles outillés : `check-arch` = 0 violation (0 baseline, 0 nouvelle). Aucun build Maven lancé (les tests n'ont donc pas été rejoués ici).

| Sév. | Domaine | Constat | Preuve | Correction recommandée |
|---|---|---|---|---|
| minor | tests | Le test DM3 `updated_at > created_at` n'est pas un vrai flaky démontré : l'horloge est `Clock.systemUTC()` (non figée), `created_at` est posé à la création HTTP et `updated_at` par une requête HTTP ultérieure, avec une colonne timestamptz au microseconde. Il dépend toutefois de l'horloge murale (égalité possible sur résolution grossière ou recul NTP). | `LesTransitionsDeLaDemandeDExamenCharacterizationIntegrationTest.java:58-59`, `HorlogeDuCentre.java:34`, `DossierMedicalConfiguration.java:147` | Durcir sans changer le comportement : `>=` plus assertion `updated_at <> created_at` seulement après `Thread.sleep(5)`, ou injecter une horloge de test avancée. Ne pas le classer « flaky à corriger » sans un échec reproduit (rejouer 20 fois isolément). |
| minor | archi | `ATTRIBUT_SANS_TRACE = "hemodialyse.audit.sansTrace"` est dupliqué en chaîne littérale dans 6 contrôleurs et dans `AuditRequestFilter` (couplage par chaîne magique, aucun test unique ne garantit la synchro). | `AnalysesControleur.java:43`, `ObservationsBiologiquesControleur.java:42`, `AuditRequestFilter.java:69` | Une seule constante dans le shared kernel, ou test qui compare les 7 valeurs. |
| minor | archi | `droitsSuffisants` déduit les rôles en parsant par regex le texte de `@PreAuthorize` : correct pour `hasRole`/`hasAnyRole`, silencieusement faux si l'expression devient plus riche (retourne 403 par défaut, donc sûr, mais peut masquer un 400 légitime). | `ErreursDuDossierMedicalAdvice.java:93-102` | Test de non-régression sur toutes les annotations du contexte, ou annotation propre `@RolesRequis`. |
| minor | stratégique | Écarts de comportement assumés « à confirmer métiers/DPO » (droits KB-MED-78, recherche POST journalisée comme création KB-MED-81, absence de règles de valeur KB-MED-79) : décisions par défaut, non validées. | `AnalysesControleur.java:14-22`, known-bugs « Décisions DM6 » (l.1656) | Les garder dans « Questions ouvertes » tant que non confirmées ; ne pas les publier comme règles actées. |

## Vérifié conforme (sans constat)
- Legacy archivé : 24 renames, tous `R100` (git diff -M 19b797f2..fb27c123) = 1 DM3 (`references_souples`) + 11 observations + 12 analyses ; contenu identique octet pour octet.
- Parité : 37 goldens DM6 = 14 inchangés (aucun `M` sur un `.approved.json`) + 23 `_nouveau_code` (11 + 12), chacun annoté dans le test par KB-MED-72/73/74/75/77 et un legacy `_ecarts-legacy` associé. Les 6 fichiers de test DM6 ne changent que le nom d'arbre et un commentaire : pas d'affaiblissement d'assertion.
- DM3 `references_souples` : renommé `_nouveau_code` avec justification KB-MED-44/77 (lecture par demande limitée au centre et au patient de l'URL, écriture reste souple, reproduit).
- Domaine pur : aucun import Spring/JDBC/jakarta dans `domain/` ni `application/` ; pas de `now()` dans le domaine, horloge passée via `HorlogeDuCentre` (application) ; `ValeurMesuree`/`MesuresDuBilan` sont des records validés.
- SQL : seules les tables `observations_biologiques`, `resultats_analyses` (propres) et `demandes_examen` (même contexte, lecture seule, `DemandesDuPatientJdbc`) sont touchées ; pas de SQL cross-context.
- Sécurité : handler 403 avant 400 pour corps/paramètre illisible, message générique « Corps ou paramètre illisible » (aucun nom de classe interne) ; patient/centre contrôlés dans la transaction (`EnregistrerUneAnalyse`) ; `FOR UPDATE` + SQLSTATE 55P03 vers 409 distincts (`OBSERVATION_/ANALYSE_ECRITURE_CONCURRENTE`) ; PUT analyse ne crée ni ne déplace plus (404).
- Audit : seule la suppression sans effet est exclue (`sansTrace`), documentée KB-MED-75 ; les écritures effectives restent auditées.
- Lecteurs inchangés : tables et colonnes inchangées, ports legacy (FHIR, KDIGO, stats, anémie `BilansBiologiquesJdbc`) conservés et listés dans seams.md avec jalon 2026-12-15.
- Strangler/legacy : `covered.json` ne retire que les fichiers supprimés ; seams.md consigne l'ouverture/fermeture, `seamAllowed` vide ; aucune façade ni double écriture (conforme ADR 0024, refactoring sur place).
- Script `dossier-medical-dm6-pg16-rejeu.sh` : refuse explicitement le port 5432 (l.12), port par défaut 55476, instance temporaire ; vérifications-production §50 limitées à SELECT.
- RG-MED-038 et RG-MED-039 présentes dans `11-dossier-medical.md` (l.190, 217).

## Verdict : pass-with-actions
Aucun blocker, aucun major.

Top 3 actions :
1. Rejouer la suite DM3/DM6 (isolément, sans Maven parallèle) pour confirmer la parité verte ; durcir l'assertion `updated_at > created_at` (horloge injectée ou marge).
2. Centraliser `ATTRIBUT_SANS_TRACE` et remplacer le parsing regex de `@PreAuthorize` par un mécanisme testé.
3. Obtenir la confirmation métier/DPO des décisions KB-MED-78/79/81 avant le retrait legacy du 2026-12-15.
