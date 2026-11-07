---
slice: dossier-medical-dm4-serologie
status: characterized
date: 2026-11-07
sources: [.ddd/safety-net/dossier-medical-inventaire.md, ADR 0024, 0072, 0081, 0082, known-bugs.md KB-MED-45 à 57]
---

# Filet DM4 : sérologies du patient (legacy, refactoring sur place)

## Périmètre vérifié par lecture du code
`SerologieRestController` est la seule route de sérologie (GET liste, GET synthese, POST, PUT, DELETE). Table réelle : `serologies_patient` (entité `SerologieJpaEntity`) ; le schéma parallèle gelé `serologies` du module `contexts/dossier-medical` (GEL.md, ADR 0072) n'est ni lu ni écrit. L'inventaire n'omettait aucune route (mise à jour : lecteurs hors module). Lecteurs : Planification (`RisquesInfectieuxJdbc`, SQL), écriture couplée `ReaffectationEnIsolementDeLaSerologie` (une seule transaction), KDIGO, Direction, import et rollback d'historique (non observés : limites).

## Point d'observation
HTTP (MockMvc, contexte Spring complet, sécurité réelle + `RoleScopeFilter` + `CenterAccessGuard`, H2 `test`), état SQL de `serologies_patient`, audit (`AuditRequestFilter`), diffusions, notifications durables, placement du patient (Planification P5, observé sans la modifier). Aucun mock du code caractérisé. **Aucun seam, aucun code de production modifié** (`seamAllowed` reste vide).

## Scénarios (26 tests, 41 goldens `src/test/resources/characterization/dossiermedical/dm4/<dossier>/<scénario>/*.approved.json`)
| Classe | Tests | Contenu |
|---|---|---|
| `LEnregistrementDUneSerologieCharacterizationIntegrationTest` | 8 | forme de la réponse et de la ligne, matrice 4 résultats x 4 conduites (positif sans conduite = 422), 13 marqueurs + valeurs refusées, dates (défaut, futur, 1900, illisible), doublons marqueur+date, bornes de colonnes (titre, unité, laboratoire), corps invalides, patient inconnu / autre centre (422 Planification) |
| `LaConsultationEtLaCorrectionDesSerologiesCharacterizationIntegrationTest` | 7 | tri et pagination (11 bornes), forme complète avec `Cache-Control`, synthèse (dernier résultat par marqueur), correction (champs ignorés, conduite effacée), introuvable, suppression silencieuse, lectures sans garde de patient |
| `LesDroitsEtLesEffetsDesSerologiesCharacterizationIntegrationTest` | 5 | droits 14 profils + anonyme x 5 routes, refus sans écriture (500 avant 403 pour un corps illisible), cloisonnement par centre (13 cas dont SUPERADMIN), audit `SEROLOGIE_*`, événement `SerologiePositiveDetectee` non publié |
| `LaSerologieEtLeRisqueInfectieuxDeLaPlanificationCharacterizationIntegrationTest` | 6 | 13 marqueurs positifs (4 déplacent en isolement), négatif / douteux / en cours, dernière sérologie par marqueur (positive antidatée), patient déjà positif, correction et suppression, atomicité sérologie + réaffectation |

## Déterminisme
Dates explicites fixes `<D+n>` ; seules la date par défaut et la limite « futur » lisent le jour de la JVM (`<J>`, `<J+1>`), avec garde de minuit (attente si moins de 2 min) ; aucune dépendance au jour UTC ni à Africa/Algiers. Identifiants et horodatages masqués. Données fictives (« Patient <alias> », conduites inventées). 3 exécutions consécutives (dont une en ordre aléatoire) : 0 flaky.

## Limites (non verrouillé)
Concurrence (doublons simultanés, KB-MED-56), KDIGO / Direction / import d'historique (lecteurs SQL ou port, hors HTTP de DM4), diffusion réelle de `SerologiePositiveDetectee` (jamais publié), schéma PostgreSQL (H2 seul : arrondi du titre et bornes de colonnes à rejouer), détail du déplacement en isolement (verrouillé par `p5-placement/isolement`), pas de mutation testing (aucun outil installé pour la stack, non lancé).

## Questions ouvertes (à confirmer avec les métiers et le DPO)
Reproduire ou corriger KB-MED-45 à 57, en particulier : DELETE silencieux, audit des lectures (VIH), consultation infirmière du statut, conduite effacée à la correction, unicité en base.
