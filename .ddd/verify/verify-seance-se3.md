---
status: proposed
date: 2026-11-08
sources: [ADR 0090, .ddd/migration/slices/seance-se3-consommables.md, commits abdadcc5, c7bbd0b8, 10984a4f, 3f11952f]
reviewer: relecteur indépendant (lecture seule)
---

# Revue indépendante : slice `seance-se3-consommables`

Verdict : **pass-with-actions** (0 blocker, 1 major, 4 minor, 2 nit).

Contrôles automatiques : `check-arch.ts` = 0 violation (0 baseline, 0 nouvelle). Les tests n'ont pas été relancés (lecture seule) ; les constats portent sur le code, les goldens et les documents.

## Constats

| Sév. | Domaine | Constat | Preuve | Correction recommandée |
|---|---|---|---|---|
| major | docs | Le référentiel des règles de gestion affirme encore le contraire de KB-SEA-22 : RG-SEA-022 « restitue le stock », lots « resélectionnés au FEFO » ; RG-SEA-025 « lots et mouvements recalculés par FEFO, stock rendu restitué ». RG-SEA-054 (même fichier) dit qu'aucune sortie n'est créée. Deux règles contradictoires dans un document gardé par `ReglesDeGestionDocumentationTest`, sur un point que l'ADR classe en condition de mise en production. | `docs/reference/regles-de-gestion/07-seances.md:147-150` et `:151-155` contre `:109-125` | Réécrire RG-SEA-022 et RG-SEA-025 (déclaration = imputation sur la sortie pour séances du jour, aucun mouvement ni restitution) ou y renvoyer explicitement à RG-SEA-054 ; régénérer la doc fonctionnelle. |
| minor | legacy/docs | Les risques KB-SEA-24 et KB-SEA-31 sont en tête de l'ADR avec leur condition de mise en production, mais sans jalon daté : seuls KB-SEA-22 et 33 ont « revue au plus tard le 2026-12-15 ». L'en-tête de l'ADR ne porte aucune date. | ADR 0090 l.11-16 ; `.ddd/safety-net/known-bugs.md:1837` ; `.ddd/migration/slices/seance-se3-consommables.md` (« Jalons rouges datés ») | Ajouter la date de revue (2026-12-15) à chacun des quatre risques, dans l'en-tête de l'ADR. |
| minor | tests | Aucun test ne couvre la concurrence des consommables bout en bout (deux POST/PUT sur la même séance : le second attend ou répond 409). Seul le raccourcis a un test sous PostgreSQL ; pour les consommables, seul le mapping 409 de l'advice est testé avec un doublon. Le net SE3 l'avait déjà écarté comme « limite du net ». La recette §55 (g) est manuelle. | `RaccourcisPostgresContractIT.java:67` ; `SeancesControleurTest.java:344` ; verrou `SeancesJdbc.java:83` | Ajouter un test PG16 (deux transactions, `FOR UPDATE`, `lock_timeout`) sur `trouverPourModifier` suivi d'une écriture Stock, ou garder la recette (g) comme jalon daté. |
| minor | tactique | L'écart `matrice_des_statuts` (ajout, modification, retrait) est présenté dans l'ADR comme « statut unifié sur la cause à 400 ». La différence réelle du golden est le message du statut `EN_COURS` : `No enum constant com.hemodialyse...SeanceStatus.EN_COURS` devient « La séance <SEANCE> porte une valeur illisible : statut inconnu ». C'est bien une correction (le nom de classe n'est plus exposé), mais sa justification est imprécise. | `se3/_ecarts-legacy/ajout/matrice_des_statuts_avant_se3.legacy.json` contre `ajout/l_ajout_n_est_accepte_que_sur_une_se/matrice_des_statuts_nouveau_code.approved.json` ; ADR 0090 l.57 | Préciser dans l'ADR et `known-bugs.md` que l'écart est la fuite du nom de classe (KB-SEA-26/07). |
| minor | docs | Les commentaires du front reprennent encore le legacy faux : « seule cette ligne sort du stock », « FEFO re-issue ». | `frontend/src/app/core/api/backend-api.service.ts:905`, `:1105` ; `frontend/src/app/features/seances/state/seance.store.ts:467` | Corriger les trois commentaires avec la prochaine modification du front. |
| nit | archi | La règle ArchUnit ne porte que sur `ajouter` et `fixer`. `administrer` (dossier médical) et `enregistrerALaValidation` (Séance) sont d'autres écritures de la même consommation, légitimes aujourd'hui. Le commentaire « legacy ne déclare plus » est exact pour `ajouter` et `fixer` seulement. | `FrontieresDesContextesTest.java:148-160` ; `SortiesDeStockDuStock.java:23` | Suggestion : documenter dans la règle pourquoi ces deux appels sont hors périmètre. |
| nit | stratégique | Termes absents du glossaire : `Declaration` (record applicatif au nom très générique) et « consommable déclaré ». Les quatre termes ajoutés (Raccourcis, Quantité consommée, Article du centre, Retrait) sont corrects et `proposed`. L'ADR cite `ConsommationsDeSeanceDuStock` « lignes 35 à 52 » sans son chemin (`contexts/stock/.../interfaces/consommations`, l.35-53). | `application/Declaration.java:7` ; `.ddd/strategic/glossaries/seance.md` ; ADR l.13 | Renommer en `SeanceDeclaree` ou ajouter le terme ; ajouter le chemin dans l'ADR. |

## Points vérifiés sans écart

1. **Hexagonal / langage** : domaine et application sans `org.*`, `jakarta` ni horloge (grep sur `domain` et `application`). ACL Stock traduit les refus (`CODES_DE_SAISIE_INVALIDE` donne 400, le reste 422). Câblage dans `SeanceConfiguration`. Verrou ArchUnit présent.
2. **Fidélité au legacy** : matrice de statuts (ajout `VALIDEE`/`SIGNEE`, facturée en 422 ; modification et retrait : tout sauf facturée, `Seance.java:197-217`) conforme à `SeanceDomainService` d'avant (`git show 10984a4f~1`). Ordre des contrôles de l'ajout conservé (quantité, séance, statut, article). Auteur : session pour l'ajout, corps pour la modification (`system` par défaut), paramètre pour le retrait, session pour la notification `CONSOMMABLE_*` : identique au legacy. Diffusions `SEANCE_CONSOMMABLE_CHANGED` puis `SAISIE_INFIRMIER` après commit, à chaque appel. Audit par chemin inchangé et assertion de golden (`monterLAudit` sur les quatre classes). `PUT {}` efface (`RaccourcisDeConsommables.composer`, liste nulle devient vide). Cache : purge `afterCommit`, par centre.
3. **Écarts** : 11 + 1 tous décidés et corrigés. Les 11 archives `_ecarts-legacy` sont identiques octet pour octet à `git show abdadcc5:<chemin>` (`cmp` : 11/11 OK). L'archive du filet Stock l'est à `10984a4f~1` (OK). Les 17 autres goldens du net n'ont pas bougé (diff `abdadcc5..HEAD` : 11 ajouts `nouveau_code` et des renommages R100, aucune modification). Aucun écart silencieux détecté. Le test Stock modifié n'ajoute qu'un patient et renomme un scénario.
4. **Sécurité** : 403 avant 400 réel (le binding précède `@PreAuthorize` ; `ErreursDeSeanceAdvice` rejoue la garde via `DroitsDeLaRoute`, testé pour `SECRETAIRE`). Centre de session imposé dans les cinq cas d'usage et filtre `center_id` dans `trouverPourModifier` ; articles d'un autre centre refusés (goldens `cloisonnement`). `FOR UPDATE` plus `SET LOCAL lock_timeout` dans la même transaction que l'écriture Stock (`UniteDeTravailSpring`) ; verrou consultatif des raccourcis (`pg_advisory_xact_lock`, 55P03 donne 409). Aucune classe interne exposée : messages sans nom de classe.
5. **Risques en tête d'ADR** : KB-SEA-33, 22, 24, 31 présents avec condition de mise en production (voir le minor sur les dates).
6. **Déterminisme** : aucun `now()`, `Thread.sleep` ni horloge système dans `se3/**`, `contexts/seance/src/test` ni `SeanceSuppressionIntegrationTest` (grep). Les jours sont relatifs au jour du centre `<J>`.
7. **Tests supprimés ou réécrits** : `SeanceScanValidationControllerTest` n'avait qu'un cas (200, diffusion, notification `CONSOMMABLE_AJOUT`, assertions `assertTrue(true)`) : couvert par `SeancesControleurTest`, `ConsommablesDeLaSeanceTest` (auteur, deux diffusions), le golden `ajout/nominal`. `SeanceSuppressionIntegrationTest` : seule la mise en place passe par `ConsommationsDeSeance.ajouter` (jour `LUNDI` fixe) ; l'objet du test (restitution à la suppression) est inchangé. Aucune perte de couverture.
8. **§55** : 6 requêtes, SELECT seuls ; colonnes vérifiées contre `V1`, `V20`, `V45` (`articles.active`, `centers.id`, `seance_raccourcis_articles.center_id/article_id`, `consommations_de_seance.seance_id/center_id`, `consommations_de_seance_imputations.seance_id`, `seances.statut`). Numérotation 55 sans doublon.
9. **Résidus** : tableau complet dans le fichier de slice, revue 2026-12-15 (aujourd'hui 2026-11-08, non échue), butoir 2027-01-31.

## Verdict

**pass-with-actions** : aucun blocker ; la slice peut être fusionnée. La mise en production reste conditionnée aux décisions DPO et métiers de l'ADR 0090 (KB-SEA-33 d'abord).

## Top 3 actions

1. Corriger RG-SEA-022 et RG-SEA-025 (`07-seances.md:147-155`), contradictoires avec RG-SEA-054 et KB-SEA-22 (major).
2. Dater les quatre risques de l'en-tête de l'ADR 0090 (revue 2026-12-15) et préciser la justification de l'écart `matrice_des_statuts`.
3. Couvrir ou tracer en jalon daté la concurrence des consommables (test PG16 `FOR UPDATE` plus écriture Stock) ; corriger les trois commentaires front.
