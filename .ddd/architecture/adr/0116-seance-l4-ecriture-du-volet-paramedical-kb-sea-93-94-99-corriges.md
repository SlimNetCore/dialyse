---
status: accepted
date: 2026-11-10
review-by: 2026-12-15
decision-makers: [utilisateur (demande explicite de CORRIGER les anomalies reproduites jusqu'ici, 2026-11-10 ; confirmation des métiers Soins et du responsable des données attendue au plus tard le 2026-12-15), architecte]
context-scope: seance (application RenseignerLeVoletParamedical, infrastructure/persistence VoletsParamedicauxJdbc, interfaces/http ErreursDuVoletParamedicalAdvice), plateforme (goldens), tests de contrat (mémoire, H2, PostgreSQL 16)
related: ADR 0100 (SE7f, reproduction), 0024 et 0075 (refactoring sur place) ; `.ddd/migration/corrections-kb-2026-11-09.md` (lot L4, question 9) ; `.ddd/safety-net/known-bugs.md` (KB-SEA-93, 94, 99) ; `.ddd/migration/verifications-production.md` §64.3, §65.1 et §78 ; RG-SEA-069 (annexe 16)
---

# 0116 - Lot L4 (Séance, écriture du volet paramédical) : KB-SEA-93, 94 et 99 CORRIGÉS

Ce n'est PAS une migration à parité : le comportement CHANGE volontairement, à la demande de l'utilisateur. L'ADR 0100 avait REPRODUIT ces trois anomalies
(refactoring sur place, mêmes tables, mêmes URL, mêmes rôles). Ni façade, ni flag, ni projection, AUCUNE migration Flyway, AUCUNE donnée réécrite.
La **confirmation des métiers reste à obtenir (au plus tard le 2026-12-15)**.

## Décisions
| KB | Règle appliquée | Où |
|---|---|---|
| KB-SEA-93 | La fiche du patient de la séance (garde de centre) est lue DANS l'unité de travail, AVANT `volets.enregistrer`. Absente du centre : 400 `BAD_REQUEST` « Patient introuvable », donc rien d'écrit et rien de diffusé (avant : volet écrit et validé, puis la réponse échouait). Choisi plutôt que de tout enrober d'une transaction annulée au refus : la garde avant écriture est la plus simple et se prouve par un compte de volets à 0 (application et HTTP). Les diffusions restent APRÈS la validation, dans le même ordre (volet enregistré, saisie de l'infirmier `PARAMEDICAL`) | `RenseignerLeVoletParamedical` |
| KB-SEA-94 | Le volet d'une séance est cherché PAR LA SÉANCE : `deLaSeance` lit `WHERE seance_id = ?` (le centre de la séance, déjà gardé par `trouverPourModifier` sous verrou de ligne, fait foi ; `UNIQUE (seance_id)` garantit au plus une ligne). Le volet rendu porte SON `center_id` ; la mise à jour (`WHERE id = ? AND center_id = <centre de la ligne>`) le remplace SANS changer son centre et SANS INSERT : plus de violation d'unicité. Aucune donnée n'est réécrite (ni migration, ni `UPDATE` implicite) | `VoletsParamedicauxJdbc`, double en mémoire aligné |
| KB-SEA-99 | `ErreursDuVoletParamedicalAdvice` reprend le handler de `ErreursDeSeanceAdvice` pour `HttpMessageNotReadableException` et `MethodArgumentTypeMismatchException` : 400 `BAD_REQUEST`, détail fixe « Corps ou paramètre illisible » (aucun message technique), et 403 `ACCESS_DENIED` conservé AVANT le 400 pour un profil sans droit (la lecture du corps précède `@PreAuthorize`). Code et message RÉUTILISÉS, non inventés. Les bornes de valeur ne changent pas (`duree_minutes`, débit : hors périmètre) ; une saisie trop longue pour sa colonne reste 500 `DATA_ACCESS_ERROR` | `ErreursDuVoletParamedicalAdvice` |

## Ordre des refus (inchangé sauf la fiche)
centre (403) puis séance introuvable (400), séance facturée (422), mesures (422), fiche absente du centre (400, NOUVEAU : avant l'écriture), écriture, diffusions.
Une mesure refusée (422) précède toujours une fiche absente (400), comme avant.

## Autres lecteurs du volet (effet vérifié, rien d'autre n'est changé)
- Détail de la séance et séances récentes (`VoletsDeLaSeanceJdbc`) : RESTENT bornés au centre (un filtre sur le centre du volet rendu par le port) ; golden `detail_volets_au_centre` INCHANGÉ. Un volet resté sous un autre centre n'y est donc toujours pas visible, et la saisie corrigée ne le déplace pas.
- Volet médical (`VoletsMedicauxJdbc`) : inchangé (hors périmètre, même anomalie de centre, mêmes détections §65.1).
- Statistiques du patient (KB-SEA-85, golden `tous_statuts_et_volet_d_un_autre_centre`) : inchangées, non traitées ici.
- `effacer(centre, séance)` (suppression de la séance) : inchangé, borné au centre.

## Réparation des données : NON automatique
La correction du code rend inutile la réparation POUR LA SAISIE (elle ne bloque plus). Elle ne la rend PAS inutile pour les lectures bornées au centre (détail, séances
récentes, volet médical) : un volet laissé sous un autre centre y reste invisible. La réparation de `center_id` reste donc une décision du responsable des données
(question 9 de `corrections-kb-2026-11-09.md`) : `.ddd/migration/verifications-production.md` §78 fournit la détection (SELECT) et un script `UPDATE` COMMENTÉ
(« à exécuter seulement après validation écrite du responsable des données, sur copie d'abord »). Règle 20-legacy-safety n°7 : aucun `UPDATE`/`DELETE` dans Flyway.
Un volet déjà écrit par l'ancien défaut KB-SEA-93 (séance dont la fiche est hors centre) reste en base ; il est listé par §78.2, jamais supprimé.

## Conséquences
- Une saisie refusée pour fiche hors centre n'écrit rien et ne diffuse rien ; une seconde saisie répond le même refus.
- Une saisie sur une séance dont le volet est sous un autre centre réussit (200) et met à jour la ligne existante, centre inchangé.
- Un JSON illisible, un corps absent, un champ mal typé ou un identifiant de séance illisible répondent 400 `BAD_REQUEST` ; plus de 500 `INTERNAL_ERROR` ni de message technique.
- Frontend : aucun comportement attendu sur l'ancien 500.

## Tests et goldens
Tests d'abord, ÉCHEC constaté sur le comportement actuel (application : 2 échecs ; contrat mémoire et H2 : 3 échecs chacun) ; goldens approuvés à la main, un par un,
ancien archivé sous `_ecarts-legacy` :
| Golden | Changement | Archive |
|---|---|---|
| `seance/se7f/volet/.../fiche_hors_centre_nouveau_code` | « volets en base » passe de 1 à 0 (réponse et seconde saisie : 400 inchangés) | `seance/se7f/_ecarts-legacy/volet/fiche_hors_centre_avant_l4.legacy.json` |
| `seance/se7f/volet/.../volet_d_un_autre_centre_nouveau_code` | 500 `DATA_ACCESS_ERROR` devient 200 ; une seule ligne, remplacée, sous le centre B | `.../volet_d_un_autre_centre_avant_l4.legacy.json` |
| `dossiermedical/dm1/ecriture/.../erreurs_nouveau_code` | 4 cas : JSON mal formé, `centerId` illisible, corps absent, identifiant de séance illisible : 500 devient 400 | `dossiermedical/dm1/_ecarts-legacy/ecriture/erreurs_avant_l4.legacy.json` |
| `dossiermedical/dm1/ecriture/.../autres_mesures_nouveau_code` | 2 cas : `dureeMinutes` `"abc"` et `99999999999` : 500 devient 400 | `.../autres_mesures_avant_l4.legacy.json` |
`detail_volets_au_centre` est inchangé. Contrat PG16 : `VoletsParamedicauxPostgresContractIT` (mêmes tests que le double et H2) et `backend/scripts/seance-se7f-pg16-rejeu.sh` (F8, chemin SQL de la saisie corrigée).

## Alternatives écartées
- Tout enrober d'une transaction annulée au refus (KB-SEA-93) : garde le chemin d'échec tardif, plus coûteux à prouver.
- Migration `V56` qui réécrit `center_id` (plan initial de `corrections-kb-2026-11-09.md`) : écartée par la consigne (aucun `UPDATE` automatique de données médicales) ; le code corrigé supprime le blocage sans elle.
- Faire lire le détail par la séance seule : aurait rendu le détail incohérent avec le volet médical et changé un golden hors périmètre.
