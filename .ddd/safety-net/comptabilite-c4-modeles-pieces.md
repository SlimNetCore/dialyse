---
status: parity-verified
date: 2026-10-15
slice: comptabilite-c4-modeles-pieces
sources: [.ddd/tactical/comptabilite.md §2 §4 §8 §9 §10 §11 §12, .ddd/migration/slices/comptabilite-c4-modeles-pieces.md, ADR 0019, ADR 0024, .ddd/safety-net/known-bugs.md KB-CPT-24 à 36]
---

# Filet de caractérisation — Comptabilité C4 « Modèles et pièces »

Refactoring sur place (ADR 0024), mêmes routes et tables. La suite vise les contrats externes et les effets d'écritures
sur le legacy ; elle ne met en œuvre aucune règle nouvelle et n'introduit aucun seam.

## Périmètre et observation

Contrôleur : `ComptabiliteParametrageRestController`, préfixe `/api/v1/comptabilite`.

| Route                                                        | Rôles             | Observation figée                                                                                |
|--------------------------------------------------------------|-------------------|--------------------------------------------------------------------------------------------------|
| `GET /modeles`                                               | ADMIN, SECRETAIRE | modèle JSON, tri par code, pages, `actifs`, isolement du centre                                  |
| `POST /modeles`, `PUT /modeles/{id}`, `DELETE /modeles/{id}` | ADMIN             | normalisation du code, validation des lignes, unicité, plan/journal, remplacement et suppression |
| `POST /pieces`                                               | ADMIN, SECRETAIRE | statut/numéro/dates/libellés de réponse et état complet des écritures/lignes                     |
| `POST /pieces/{ecritureId}/extourne`                         | ADMIN             | écriture inverse, numéro partagé, période et protection contre les doublons                      |

Tables observées par SQL : `modeles_piece`, `modeles_piece_lignes`, `ecritures_comptables`, `lignes_ecriture` ; état
de période, journaux et comptes préparés par les fixtures de la suite existante. Harnais HTTP MockMvc sur le contexte
Spring complet, sécurité réelle et H2 ; aucun mock de l'adaptateur ou du service. Classe :
`backend/platform/src/test/java/com/hemodialyse/backend/characterization/comptabilite/ecritures/ModelesEtPiecesCharacterizationIntegrationTest.java`.
Elle partage les fixtures et le scrubber C3 de `EcrituresCharacterizationSupport`.

## Scénarios et références

10 tests couvrent : liste par défaut, pagination/filtre actif et autre centre ; création, mise à jour, suppression,
unicité et lignes SQL ; journal/compte/sens invalide ; rôles et centre ; saisie nominale avec statut VALIDEE, lien au
modèle, date, numéro, lignes et équilibre ; montant nul omis et libellé par défaut ; modèle inactif, montants manquants,
négatifs ou tous nuls, déséquilibre et période close ; extourne inverse, date du jour UTC, source, numéro,
écritures/lignes,
pièce déjà extournée, extourne d'extourne, écriture système ; conservation des pièces après suppression du modèle.

45 goldens JSON/SQL approuvés sous
`backend/platform/src/test/resources/characterization/comptabilite/ecritures/pieces/`.
UUID générés sont aliasés ; la date du jour UTC de l'extourne est normalisée en `<AUJOURDHUI_UTC>` ; messages techniques
500 sont masqués par le scrubber. Les valeurs `.received.json` ont été capturées sur le legacy et examinées avant
approbation. Résultat : 10 tests, 0 échec, 0 erreur ; 3 exécutions consécutives en ordre aléatoire réussies.

## Constat suspect détecté

`PUT /modeles/{id}` avec un identifiant existant d'un autre centre répond **500 `DATA_ACCESS_ERROR`** : la recherche
scopée au centre ne trouve pas le modèle puis l'insertion entre en conflit avec la clé primaire globale. `DELETE` pour
le même identifiant répond 422 `MODELE_INTROUVABLE`. Scénario `modele_modification_autre_centre_500`; consigné comme
KB-CPT-36. Par défaut ADR 0019 : reproduire ; aucune correction ni seam ici.

Le filet confirme aussi B8 : l'extourne porte la date d'écriture du jour, conserve la date de pièce d'origine ; le
numéro est calculé dans l'année de la date d'écriture. Aucun correctif de comportement n'est décidé par la capture.

## Parité sur le nouveau code (2026-10-15)

La même classe de caractérisation passe avec `comptabilite.c4.nouveau-code=true` : 10 scénarios, 0 échec, 0 erreur ;
les 45 goldens approuvés restent inchangés. Le contexte comptabilité passe ses 272 tests ; `check-arch.ts` signale
0 violation. Les 16 tests ciblés de non-régression C3/C5/C6 passent après ajout des verrous partagés.

`SequenceDesNumerosDePiecesJdbcTest` ajoute deux essais concurrents H2 : deux transactions attribuent des numéros
successifs uniques, et deux traitements de la même source n'insèrent qu'une écriture. En PostgreSQL, les adaptateurs
contexte et legacy partagent des advisory locks transactionnels sur les mêmes clés (centre/journal/année ou source).
Le filet HTTP reste sous H2 : le chemin PostgreSQL/advisory-lock n'est pas exécuté par ce test.

## Limites et non-déterminisme

- Les scénarios HTTP utilisent H2 ; les contraintes spécifiques PostgreSQL ne sont pas exercées. Les courses B1/B2 sont
  couvertes par le test de contrat H2 du port de séquence ; le comportement PostgreSQL/advisory-lock reste à vérifier
  sur une base PostgreSQL préproduction. `covered.json` n'accorde aucun seam.
- Le jour est UTC comme dans le contrôleur et normalisé pour les goldens ; aucun test ne fait avancer une horloge.
- Pas de test navigateur/Angular, de journalisation d'audit, de performance à grand volume ni d'import Sage 100.
- La réponse d'édition vers un autre centre est capturée comme bug, pas approuvée comme comportement métier souhaité.

## Fichiers legacy ajoutés à `covered.json`

`ModelePiece`, `ModelePieceRepositoryPort`, `PiecesComptablesUseCase`, `PiecesComptablesService` et
`ModelePieceJdbcAdapter`. `ComptabiliteParametrageRestController` est déjà couvert par C1 ; agrégat/adapter d'écriture
et
support HTTP partagés déjà couverts par C3. Aucun fichier de production n'a été modifié.

## Lancer

Depuis `backend/` :
`./mvnw -o -pl platform -am test -Dtest=ModelesEtPiecesCharacterizationIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false`
Depuis le 2026-10-19, le legacy C4 est retiré : la suite cible toujours le nouveau contrôleur (plus de flag
`comptabilite.c4.nouveau-code`). Les fichiers retirés restent listés dans `covered.json` pour l'historique.
