# Revue indépendante des lots L3 (d023fdd3), L7 (d4e613eb), L4 (faee2d1b) — 2026-11-10

Méthode : diffs git uniquement, aucun test/build/scan lancé. Les « rouge puis vert » ne sont pas rejouables ici : preuve = tests modifiés dans le même commit (assertions inversées), pas de trace d'exécution rouge.

Verdict : **pass-with-actions** (0 blocker, 2 major, 4 minor).

## Vérifications demandées

| # | Point | Résultat |
|---|---|---|
| 1 | KB annoncées corrigées | OK. KB-SEA-72 : `FacturationDesSeancesJdbc.java:90` (MEMOIRE_PRESENTE) lu et écrit. KB-SEA-73 : `:93`, `exclure`. KB-SEA-74 : `LiaisonDesSeancesJdbc` (EXISTS factures même centre) + `V54...sql:26`. KB-SEA-82 : `V55...sql:26`. KB-ORG-09 : `V56` + `actifsAuSensStrict` supprimé. KB-ORG-10 : `LibellesDeLOrganisationJdbc` (center_id = :centre). KB-SEA-93/94/99 : `RenseignerLeVoletParamedical` (fiche gardée avant l'écriture), `VoletsParamedicauxJdbc.deLaSeance` (par séance), `ErreursDuVoletParamedicalAdvice.illisible`. |
| 2 | Test rouge-puis-vert | Tests inversés dans les mêmes commits (contrat `FacturationDesSeancesContrat`, `LibellesContrat`, `RenseignerLeVoletParamedicalTest`, `VoletsParamedicauxContrat`, `LiaisonConcurrentePostgresContractIT` sur PG16). Rouge non prouvé par artefact. |
| 3 | Goldens | OK. Archives `_ecarts-legacy/..._avant_l3 / _avant_l4 .legacy.json` (renommages R100) + `*_nouveau_code.approved.json` ajoutés (2 L3, 2 DM1 + 2 SE7F L4). |
| 4 | Migrations | V54, V55 : NOT VALID, additives, ne peuvent pas bloquer le démarrage ; V56 : UPDATE avant SET NOT NULL, idempotente, table `centers` minuscule. Ordre V54<V55<V56 sans trou. Voir M1, m1. |
| 5 | Régressions | Pas de régression de montant ni de statut de facture trouvée : le statut `FACTUREE` et les prix ne sont pas recalculés ; la liaison exige facture existante du même centre. Voir M1, m2. |
| 6 | SELECT de production | §76.1/76.2, §77.1/77.2, §78.1-78.3 : lecture seule, justes. La réparation §78 est commentée, sans DELETE. Voir m3. |
| 7 | Hexagonal / langage | Pas d'inversion de dépendance ; ports renommés (`centresActifs`) ; termes du glossaire. RAS. |

## Constats

| Sév. | Constat | Preuve | Correction proposée |
|---|---|---|---|
| **major** M1 | V55 : un CHECK NOT VALID est réévalué par PostgreSQL sur toute ligne MISE À JOUR, quelle que soit la colonne. Toute séance de production au statut non canonique (`validee`, `VALIDEE `) devient non modifiable : 500 à l'écriture (consommation, signature, exclusion…). Risque documenté mais l'exécution de 76.1 n'est qu'une consigne, pas un garde-fou de déploiement. | `V55__seances_statut_canonique.sql:8` ; `verifications-production.md:2531` | Faire de 76.1 un prérequis bloquant du déploiement (checklist de mise en production, résultat joint) ; si > 0, normaliser par décision écrite AVANT V55, ou différer V55 à une migration ultérieure. |
| **major** M2 | KB-SEA-94 : la saisie remplace un volet posé sous un autre centre, mais la lecture du détail le filtre encore (`filter(v -> v.centre().equals(centre))`). L'utilisateur reçoit 200 puis ne revoit pas ce qu'il vient d'enregistrer, tant que §78 n'est pas exécuté. Incohérence écriture/lecture assumée (golden `detail_volets_au_centre` inchangé) mais visible en production. | `VoletsDeLaSeanceJdbc.java` (`paramedical`, filtre ajouté par L4) ; `VoletsParamedicauxJdbc.java:52-56` | Exécuter la détection §78.1 avant déploiement ; si > 0, réparation §78 obligatoire (accord écrit) avant mise en service, ou refuser la saisie (409) tant que le volet est hors centre. |
| minor m1 | V54 : la FK interdit désormais de supprimer une facture encore liée à une séance. Le seul `DELETE FROM factures` de production (`RepriseDesSoldesJdbc.supprimer`) vise des factures de reprise sans séance liée : OK, mais le lien n'est pas testé. | `RepriseDesSoldesJdbc.java:69` ; `LiaisonConcurrentePostgresContractIT.java:212` | Ajouter un test de contrat PG16 : supprimer une facture de reprise reste possible. |
| minor m2 | KB-ORG-09 : V56 change le comportement (centres sans état = actifs, ils entrent dans le contrôle de nuit des péremptions du Stock, donc nouvelles notifications). Effet documenté, conditionné à §72.1/77.1, pas à un garde-fou. | `V56__centers_actif_not_null.sql:3-4` ; `verifications-production.md:2539` | Exécuter 77.1 avant déploiement ; si `nuls > 0`, fixer `actif = FALSE` par décision écrite avant V56. |
| minor m3 | §72 et §73 restent rédigés comme « refactoring sur place, aucune migration, ne rien corriger avant décision », ce qui contredit §77 (V56 livrée, borne appliquée). Risque d'exécution de la mauvaise consigne. | `verifications-production.md:2470`, `:2481`, `:2493` | Ajouter en tête de §72/§73 : « Remplacé par §77 (ADR 0115) ». |
| minor m4 | La borne de KB-ORG-10 utilise le centre demandé (`centre.valeur()`), pas `m.center_id` ; équivalent car le journal est filtré au centre, mais le commentaire et §77 parlent de « centre du mouvement ». Les SELECT 73.1/77.2 comparent bien `m.center_id`. | `LectureDesMouvementsJdbc.java` (`libelles.nomsDesSalles(centre.valeur(), …)`) | Aligner le libellé du commentaire ; confirmer que la requête principale filtre `m.center_id`. |

## Top actions avant déploiement
1. Rendre bloquants 76.1 (V55), 77.1 (V56), 78.1 (L4) dans la checklist de mise en production.
2. Trancher M2 : réparation §78 ou refus explicite de la saisie.
3. Marquer §72/§73 comme remplacés par §77.
