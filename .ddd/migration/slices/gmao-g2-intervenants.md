---
status: approved
date: 2026-10-11
slice: gmao-g2-intervenants
sources: [.ddd/tactical/gmao.md (Intervenant, R24), ADR 0019, ADR 0024, .ddd/safety-net/known-bugs.md (KB-GMAO-22 à 31)]
---
# G2 — Intervenants (référentiel et tarif horaire), refactoring sur place, état : `built`

**Comportement observable** : l'administrateur du centre enregistre, corrige (coordonnées, tarif) et désactive les intervenants internes ou externes ; le tarif valorise ensuite le temps d'intervention (clôture, legacy jusqu'à G4). Routes `/api/v1/gmao/intervenants` : POST, GET liste paginée, GET `/{id}`, PUT `/{id}`, POST `/{id}/desactiver`.

## Réalisation
| Couche | Contenu |
|---|---|
| domain | `Intervenant` (racine), `NatureIntervenant`, `TarifIntervenant` (Q-G2-2), erreurs (`NomRequis`, `NatureRequise`, `NatureInconnue`, `TarifNegatif`), événements `IntervenantEnregistre`, `CoordonneesIntervenantCorrigees`, `TarifIntervenantRevise`, `IntervenantDesactive`, port `IntervenantRepository` |
| application | `EnregistrerIntervenant`, `CorrigerIntervenant`, `DesactiverIntervenant`, `ListerIntervenants`, `ConsulterIntervenant`, port `LectureIntervenants` |
| infrastructure | `IntervenantJpa` sur la table `gmao_intervenants` (colonnes `type`, `tarif_horaire_defaut`, comme l'ancienne entité), mapper explicite, `IntervenantRepositoryJpa`, `LectureIntervenantsJpa` |
| interfaces | `IntervenantControleur` (mêmes routes et `@PreAuthorize`) |

La base arrondit le tarif à deux décimales et refuse les dépassements (KB-GMAO-26/27) comme avant ; la réponse reprend l'échelle reçue.

## Preuve d'équivalence
Suite unique `.../characterization/gmao/intervenants/IntervenantHttpCharacterizationIntegrationTest` (39 scénarios, goldens `src/test/resources/characterization/gmao/intervenants/`), verte contre le nouveau code, y compris les scénarios de clôture d'intervention (qui lisent la même table via le code legacy). Tests du module : domaine, cas d'usage avec fakes, contrat de repository (en mémoire + JPA/H2), architecture.

## Écarts de comportement décidés
| KB | Décision | Goldens |
|---|---|---|
| KB-GMAO-22 | `GET /{id}` : 200 pour un intervenant du centre, 404 sinon, 400 si l'id est illisible, 403 non-admin (le legacy répondait 500) | `consulter_par_id_*_nouveau_code` (5) ; `consulter_par_id_anonyme` inchangé |
Les 4 goldens 500 du legacy (`consulter_par_id_existant`, `_inconnu`, `_invalide`, `_non_admin`) ont été retirés (scénario remplacé). KB-GMAO-24, 25, 26, 27, 30 reproduits. KB-GMAO-27 (longueurs non validées) et KB-GMAO-30 (`PUT` = remplacement total) : correction à planifier — date à fixer par l'utilisateur (le retrait du legacy a lieu au déploiement, ADR 0024).
**Isolation de centre (revue 2026-10-12)** : déjà appliquée par le nouveau code (intervenant d'un autre centre = inconnu, 400 sur les commandes, 404 sur `GET /{id}`) ; pas de défaut du type KB-GMAO-15 ici. Seul point à noter, hors décision : la clôture d'intervention legacy ignore silencieusement un intervenant d'un autre centre (KB-GMAO-31).

## Legacy retiré / conservé
| Retiré | `IntervenantRestController`, `CreateIntervenantRequest`, `IntervenantResponse`, `IntervenantRestControllerTest` |
|---|---|
| Conservé (clôture d'intervention, bon d'intervention : lecture du tarif et de la nature) | `bc-gmao` : `Intervenant`, `TypeIntervenant`, `IntervenantRepositoryPort` ; `platform` : `IntervenantRepositoryAdapter`, `IntervenantEntity`, `IntervenantJpaRepository` |
À retirer quand G4 (interventions) sera migré. `IntervenantJpa` (module) et `IntervenantEntity` (legacy) décrivent la même table : garde `ModelesDePersistanceAlignesIntegrationTest` (platform), à retirer avec l'entité legacy (G4). Vérifications de production : `.ddd/migration/verifications-production.md`. KB-GMAO-10 (snapshot de nature) reste à traiter en G4.

## Livraison
En une fois, sans migration, avec G1. Retour arrière : redéployer la version précédente. Rejeu PostgreSQL 16 : `backend/scripts/gmao-pg16-rejeu.sh`. Nettoyage de la base de dev : voir la fiche G1.
