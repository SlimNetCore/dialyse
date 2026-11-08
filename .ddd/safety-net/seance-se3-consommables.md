---
slice: seance-se3-consommables
status: characterized
date: 2026-11-08
sources: [.ddd/migration/slices/seance-se3-consommables.md, ADR 0024, ADR 0088, ADR 0089, .ddd/safety-net/known-bugs.md (KB-SEA-22 à 34)]
---

# Filet de caractérisation — SE3 Consommables déclarés

> Remplace la version du 2026-11-01, qui annonçait des scénarios non exécutés (aucun test `characterization/seance/se3`, table `seance_consommables` inexistante). Celle-ci décrit ce qui tourne.

## Point d'observation

HTTP (MockMvc, contexte Spring complet, sécurité réelle + `RoleScopeFilter` + `CenterAccessGuard`, H2 `test`), état SQL des consommations du Stock (`consommations_de_seance`, `…_imputations`), `stock_movements`, `articles`, `lots`, `seance_raccourcis_articles`, diffusions temps réel, audit. Aucun mock du code caractérisé. Harnais : `Se3CharacterizationSupport` (sur `Se2CharacterizationSupport`, `Se1CharacterizationSupport`). Tests : `backend/platform/src/test/java/com/hemodialyse/backend/characterization/seance/se3/` ; goldens : `backend/platform/src/test/resources/characterization/seance/se3/<dossier>/` (28 goldens approuvés un par un).

## Scénarios (28)

| Dossier | Nb | Contenu |
|---|---|---|
| `ajout` (`POST /seances/{id}/consommables`) | 10 | nominal (imputation sans mouvement, cumul, décimal, diffusions) ; matrice des 6 statuts ; quantités et formes (13) ; articles (actif, inactif, inconnu, autre centre, ordre des gardes) ; sortie pour séances (dépassement, hors pièce) ; pièce rapprochée (ajout, modification, retrait refusés) ; profils (15) ; cloisonnement ; patient disparu ; audit |
| `modification` (`PUT …/{articleId}`) | 6 | nominal (remplacement, quantité identique rediffusée) ; matrice des statuts ; quantités, article inactif, formes ; profils ; cloisonnement ; audit |
| `retrait` (`DELETE …/{articleId}?centerId=`) | 6 | nominal (aucune restitution de stock) ; matrice des statuts ; paramètres ; profils ; cloisonnement ; audit |
| `raccourcis` (`GET|PUT /seances/raccourcis-consommables`) | 6 | nominal (ordre, remplacement complet) ; bornes (12, doublon, nul, inactif, inconnu, autre centre, formes) ; cache par centre ; profils ; cloisonnement ; audit |

## Seams

Aucun (aucun code de production modifié). Dates : séances posées au jour du centre `<J>` ou `<J+1>` ; aucun test ne lit l'heure, ni le fuseau système.

## Couverture et mutation

Couverture de branches non mesurée (pas de JaCoCo configuré pour la zone) ; mutation non exécutée (pas de PIT dans le dépôt). Revue par lecture du code : branches de `SeanceDomainService.addConsommableSeance`/`removeConsommableSeance`/`updateConsommableSeance` (quantité, séance, statut `FACTUREE`, `VALIDEE`/`SIGNEE`, article, actif), `SeanceRaccourciDomainService.replace` (liste nulle, > 12, nul, doublon, introuvable, inactif), `SeanceRaccourciApplicationService` (cache, purge), `SeanceStationRestController` (raccourcis), `SeanceRaccourciJdbcAdapter`, `GestionDesConsommations` (pièce, rapprochée, `AUCUNE_PIECE`, `DEPASSEMENT`, hors pièce) toutes exercées.

## Stabilité

3 exécutions consécutives de `seance.se3` (ordre par défaut, `-Dsurefire.runOrder=random` ×2) : 0 échec ; suite complète `./mvnw -o test -fae` voir la réponse de l'appelant.

## Limites (non verrouillé)

- Écritures simultanées (deux ajouts parallèles du même article) : non observées (lecture puis écriture non verrouillée hors pièce ; résultat non déterministe sur H2). À traiter à la reprise (verrou ou version).
- `GET /api/v1/stock/consommations/{seance}` et favoris (`ConsommationsControleur`) : lectures du Stock, filet Stock.
- Consommables de la validation (`valider` avec `consommations`) : SE2 ; suppression d'une séance (restitution) : `SeanceSuppressionIntegrationTest`.
- Article devenu inactif déjà présent dans les raccourcis lus : non scénarisé.
- PostgreSQL : seul H2 est exécuté (arrondi à deux décimales à confirmer sur le moteur de production).
- Rapprochement : seul le parcours sans écart est joué (écarts et motifs : filet Stock).
- Facturation (forfait) : aucun effet observé sur le forfait depuis ces routes.

## Bugs connus

KB-SEA-22 à KB-SEA-34 dans `.ddd/safety-net/known-bugs.md` (non corrigés).
