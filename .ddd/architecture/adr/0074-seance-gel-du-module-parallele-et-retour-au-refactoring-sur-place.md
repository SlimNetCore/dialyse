---
status: accepted
date: 2026-11-02
decision-makers: [utilisateur (décision du 2026-11-02, décision déléguée à l'assistant pour l'acceptation formelle), architecte]
context-scope: seance
related: ADR 0024 (refactoring sur place), 0062, 0002, 0072 et 0073 (même démarche pour le dossier médical et Patient et couverture) ; `.ddd/verify/verify-seance.md` ; `backend/contexts/seance/GEL.md`
---

# 0074 - Séance : gel du module parallèle et reprise sur place

## Contexte
La vérification indépendante (`verify-seance.md`, verdict fail, 3 blockers) a établi que `backend/contexts/seance` n'a jamais été branché (`platform/pom.xml` ne le référence pas) : 100 % du trafic passe par le legacy (`bc-seance`, `SeanceRestController`, `SeanceApplicationService`, adapters JDBC de place, de facturation et de raccourcis). Le module : lit et écrit des colonnes inexistantes de `seances` (`pec_id`, `type_seance`, `verrouille_facturation`) ; redéfinit `StatutSeance` sans `FACTUREE` ni `ABSENT` (`valueOf` planterait sur les lignes réelles) ; ne persiste ni forfait surchargé ni déverrouillage ; n'a aucune garde de centre (centre fourni par le client) ; déclare `/api/v1/seances`, déjà servi par le legacy (mapping ambigu) ; n'a aucune parité (aucun golden `_nouveau_code`).

Audit de production (demande A, 2026-11-02) : depuis `2978e639^`, aucun fichier legacy de Séance (`bc-seance`, `SeanceRestController`, `SeanceApplicationService`, adapters `SeancePlace*`, `SeanceBilling*`, `SeanceRaccourci*`, `SeanceJpaEntity`) n'a été modifié sans filet : les seuls changements sont les suppressions de Planification P9 (caractérisées, commits propres). Trois résidus de `2978e639` :
1. `CacheConfig` : deux caches (`ref.seances.du.jour`, `ref.seances.validees.non.signees`) utilisés par le seul module gelé : **restauré** (seam inscrit dans `seams.md` et `seamAllowed` avant l'édition, puis fermé).
2. `db/schema.sql` (H2 de test) : colonne `seances.verrouille_facturation` absente de Flyway de production : **retirée** (H2 redevient fidèle à la production).
3. Migration `V22__derogation_regularisation.sql` (doublon, déjà renumérotée V50 par `16d27b7b`) : table `derogations_regularisation` utilisée par le seul module gelé : **conservée** (additive, idempotente, vide), consignée dans la fiche SE4 et `verifications-production.md` § 39.

`covered.json` : l'entrée `SeanceUseCase.java` (ajoutée par `2978e639` sans golden) est **retirée** ; les quatre autres entrées Séance de ce commit étaient des doublons d'entrées légitimes antérieures (filets Stock S6b, Planification P6, Facturation) et n'ont pas été touchées. Preuve : `backend/scripts/seance-pg16-rejeu.sh` (PostgreSQL 16 jetable : V1..V52, `SeanceJpaEntity` conforme à `information_schema`).

## Décision
1. **Geler le module** : retiré du réacteur (`backend/pom.xml`) et du `Dockerfile` ; dossier, ADR 0062, fiches, tactique et glossaire conservés (`GEL.md`).
2. **Statuts de séance = ceux du LEGACY** (`SeanceStatus` : y compris `FACTUREE` et `ABSENT`). Le retrait d'`ABSENT` du modèle cible (ADR 0002) est traité avec Facturation F2 et Absences, pas ici.
3. **Ordre de reprise : Séance reste la DERNIÈRE** (cœur le plus couplé, 18 lecteurs SQL de `seances` hors contexte). Jalons déjà posés par Planification (port `attenduLe`, règle de présence dupliquée dans `SeancePlanningJdbcAdapter`, `SeanceRestController` de 1 315 lignes à découper) et par Stock (`ConsommationsDeSeance`) consignés dans la roadmap.
4. **Désactiver** `TableDesSeancesTest` (motif dans `@Disabled`) ; exclure le module du balayage de `ReglesDeGestionDocumentationTest` ; la règle ArchUnit `FrontieresDesContextesTest` (vide, `allowEmptyShould`) est conservée.
5. **Statuts réels** : SE1 à SE7 `planned` (aucune n'est `characterized` : aucun golden Séance dédié).
6. L'ADR 0062 est **partiellement remplacée** : le contexte unique et le refactoring sur place restent valides ; le module `contexts/seance` tel que codé (URL, colonnes, statuts) est abandonné.

## Conséquences
- Plus de risque de mapping ambigu ni de démarrage en échec ; le travail gelé reste matière de réflexion.
- Aucune route nouvelle n'a jamais été exposée en production : rien à retirer côté client.
