---
status: proposed
date: 2026-11-07
sources: commits 9e60ec9f (filet), 0cfb9cc6 (reprise), ADR 0086/0024/0072, check-arch
---
# Revue DM7 pré-greffe (bilan, RCP, étapes, donneurs vivants, KDIGO/PDF gardés)

Contrôles outillés : `check-arch` = 0 violation (0 baseline, 0 nouvelle). Aucun build Maven ni scan lancé : les 38/38 verts, les contrats PG16 et ArchUnit sont repris des déclarations de l'ADR/commit, NON rejoués ici.

Vérifié par preuve :
- Goldens legacy : 19 renames `R100` (`git diff -M 9e60ec9f HEAD`) vers `*/_ecarts-legacy/<scénario>__<nom>.legacy.json` = octet pour octet ; 19 `*_nouveau_code.approved.json` ; 38 fichiers dans dm7 au net comme au HEAD (19 inchangés + 19 renommés/remplacés). Chaque `arbre("..._nouveau_code")` porte un commentaire KB-MED-85..91 (diff des 6 classes de test).
- Domaine pur : aucun setter, horloge ni framework dans `domain/BilanPreGreffe|EtapeDeBilan|DonneurVivant|ListeStandardDesEtapes` (grep) ; horloge injectée (`HorlogeDuCentre`) ; machine à états portée par l'enum `StatutDuBilanPreGreffe.java:15-30`.
- Langage publié : `publishedlanguage/GardeDePatient.java`, `SousLaGardeDuDossierMedical.java` ; aucun import de `domain|application|infrastructure|interfaces` du contexte depuis `backend/platform/src/main` (grep vide) ; KDIGO/PDF : `centerAccessGuard.requireCenter` puis `patients.exiger` après `@PreAuthorize` (`KdigoGreffeRestController.java:40-45`, `GreffeExportRestController.java:39-44`) : 403 avant 404, aucun SQL cross-context.
- Unicité : `BilansPreGreffeJdbc.java:95-101` (verrou consultatif `bilan-pre-greffe|…`, relecture sous `FOR UPDATE` puis création), lecture ordonnée `created_at, id` avec WARN de l'identifiant seul (`:62-69`), 422 pour statut illisible (`:80-84`), décisions jamais supprimées (`:118-127`). Verrou consultatif no-op sur H2 documenté (`VerrousJdbc.java:31-37`), prouvé sur PG16 par les `*PostgresContractIT`.
- DELETE sans effet : `EtapesDeBilanControleur.java:82-86` + `SupprimerUneEtapeDeBilan` (retour booléen) + `AuditSansTrace.ATTRIBUT` figé par `AttributSansTraceDeLAuditTest`.
- Sécurité : handlers 404/409/422 sans message technique (`ErreursDuDossierMedicalAdvice`), advice étendu aux 3 contrôleurs et au marqueur ; WARN sans donnée de santé.
- §51 verifications-production : SELECT seul ; les listes d'énumérations de la requête 5 = enums du code (15 catégories, 7 statuts, etc.). Script PG16 : refuse 5432, cluster temporaire détruit par `trap`, SELECT/psql sur base jetable, colonnes exactes et absence d'unicité vérifiées.
- seams/covered : section DM7 « FERMÉE », `seamAllowed` vide, 22 entrées retirées de `covered.json` = 3 contrôleurs + 12 DTO + 3 UseCase + 4 services listés dans `seams.md`. ADR 0086 : alerte KB-MED-94 en tête (l.11-12), liste nominative des résidus et revue au plus tard 2026-12-15 (l.33). RG-MED-046..049 présentes (`11-dossier-medical.md:394-439`). `state.json` : `characterized` -> `built`.

| Sév. | Domaine | Constat | Preuve | Correction recommandée |
|---|---|---|---|---|
| minor | legacy | KB-MED-94 (alerte sérologique manquante, aide à l'inscription greffe) reproduit ; l'ADR l'affiche bien mais aucun critère bloquant n'est posé pour la mise en production | ADR 0086 l.12 ; `verifications-production.md` §51 recette (d) | Ajouter explicitement « décision médicale KB-MED-94 requise (ou acceptation écrite du risque) avant mise en production » comme condition de bascule ; ouvrir une tâche datée avant la revue du 2026-12-15 |
| minor | tests | Assertion affaiblie sur H2 : « lecture suivante réussit toujours » remplace « 200 ssi une seule ligne » (H2 sans verrou consultatif) ; seule la preuve PG16 couvre l'unicité réelle | diff `LesDroitsLeCloisonnement…IntegrationTest` (`droits/lectures_simultanees`) ; ADR l.42 | Acceptable (justifié KB-MED-90). Garder le rejeu `dossier-medical-dm7-pg16-rejeu.sh` comme pré-requis de bascule et consigner sa date/résultat dans la slice |
| minor | legacy | Chemins d'écriture legacy morts mais présents (`save`/`deleteById`/`findById`, `TransitionStatutBilanGreffeSpecification`, comportements d'écriture des agrégats) : double modèle sans appelant d'écriture | ADR 0086 l.33 ; `BilanPreGreffeRepositoryAdapter` | Violation évitée par la liste nominative + échéance ; suggestion : un test ArchUnit/grep interdisant tout nouvel appelant d'écriture d'ici la revue |
| minor | tactique | Primitive obsession reproduite : groupe sanguin, typage HLA, PRA en `String`/`BigDecimal` sans VO ; aucune règle de plausibilité | KB-MED-93 ; `BilanPreGreffeJdbc` colonnes `COLONNES` | Suggestion (pas une violation : parité) ; VO `GroupeSanguin`/`PraEnPourcentage` quand les métiers tranchent KB-MED-93 |
| minor | archi | Garde de patient appelée dans le contrôleur plateforme (KDIGO/PDF) ; acceptable (pas de règle métier, via port publié) mais le contrôle reste hors transaction/use case | `KdigoGreffeRestController.java:45` | Suggestion : à la reprise de KDIGO/PDF (DM8) le déplacer dans le cas d'usage |
| nit | tests | Mot de passe du cluster jetable passé en `-D` sur la ligne de commande Maven (visible dans `ps`), cluster local 127.0.0.1 détruit en sortie | `dossier-medical-dm7-pg16-rejeu.sh` dernier `./mvnw` | Négligeable ; lire via variable d'environnement si le script sort du poste dev |
| nit | procédure | Entorses de l'implémenteur (`cat > /dev/null`, commandes shell lecture seule) non vérifiables depuis les artefacts : aucune trace dans le dépôt, aucun écart de périmètre constaté (diff limité à `contexts/dossier-medical`, `platform` listé en seams, `.ddd`, docs) | `git show --stat HEAD` | Aucune action ; pas de blocker |

Aucun blocker, aucun major. Aucun SQL cross-context, aucun port nommé par la techno, aucun DTO/entité ORM traversant les couches du nouveau code, câblage dans `DossierMedicalConfiguration` (composition root du contexte).

## Verdict : pass-with-actions

Top 3 actions
1. Poser KB-MED-94 comme condition explicite de mise en production (décision médicale/DPO ou acceptation écrite) et la dater avant la revue du 2026-12-15.
2. Exécuter et consigner le rejeu `dossier-medical-dm7-pg16-rejeu.sh` + la suite complète (38/38) avant bascule (non rejoués par cette revue).
3. Verrouiller (test d'archi) l'absence de nouvel appelant des méthodes d'écriture legacy résiduelles jusqu'à leur retrait.
