# Petites slices (SEED1, LST, PL16, HYG) : réalisation (2026-11-14)

Refactoring sur place (ADR 0024). Aucun commit, aucun report, `seamAllowed` = [], `covered.json` et `state.json` non touchés.

| Slice | État | ADR | Résumé |
|---|---|---|---|
| SEED1 | livrée | 0182 | `StockDemoDataSeeder` -> `contexts/stock/configuration/JeuDeDemonstrationDuStock`, mêmes données, même condition `app.stock.demo-data`. Test déplacé et complété (bean absent si faux). |
| LST | livrée, destination corrigée | 0183 | Vers `comptabilite` : impossible (cycle Maven, facturation dépend de comptabilite). Placés dans `facturation/infrastructure/acl/comptabilite` (`ComptabilisationDesVentesApresLaValidation`, `ComptabilisationDuReglementApresEncaissement`), `@Bean` dans `FacturationContexteConfiguration`. `DomainServiceConfig` ne contenait aucun de ces beans. KB-CPT-37 relu, inchangé. |
| PL16 | livrée | 0184 | Retirés : DDL H2 `user_center_assignment` et `stock_parametres`, 7 MERGE de `seed.sql`. Les 2 tables GMAO n'ont pas de DDL H2. Aucune table vivante, aucun Flyway touché. |
| HYG | PARTIELLE | (aucun) | `GEL.md` x2 supprimés (+ 2 commentaires de pom), journaux `backend/*.log` supprimés (tous ignorés par git, aucun suivi, aucun `*.received.json`). RENOMMAGE NON FAIT (voir ci-dessous). |

## Blocage guard-legacy (HYG, renommage)
`DatesIllisiblesDeLaDirectionAdvice.java` est dans `covered.json` (ligne 54) ; le fichier renommé `FormesIllisiblesDesRoutesAdvice.java` ne l'est pas : édition bloquée. Renommage annulé (tout remis en l'état, tests inclus). Pour le reprendre, entrée à ajouter par l'utilisateur :
`backend/platform/src/main/java/com/hemodialyse/backend/infrastructure/web/FormesIllisiblesDesRoutesAdvice.java` (et retirer la ligne 54). Plan : renommer outer en `FormesIllisiblesDesRoutesAdvice`, nested `MethodesNonSupporteesDeLaDirectionAdvice` en `MethodesNonSupporteesDesRoutesAdvice`, 2 classes de test idem, mettre à jour la Javadoc de `RoutesDeLAdministrationGeneriqueGardeesTest` et 5 sources dans `docs/reference/regles-de-gestion` (02, 06, 13) et `documentation-fonctionnelle.html`. L'ordre (`HIGHEST_PRECEDENCE`, même paquet, nom entre `Autorisation...` et `ReferentialAdmin...`) est conservé par ce nom.

## Entrées de `covered.json` à retirer (non modifié)
- `backend/platform/src/main/java/com/hemodialyse/backend/infrastructure/listener/FacturationValideeComptabiliteListener.java` (ligne 49)
- `backend/platform/src/main/java/com/hemodialyse/backend/infrastructure/listener/ReglementEncaisseComptabiliteListener.java` (ligne 50)
- (`StockDemoDataSeeder` n'y figurait pas.)

## Documents mis à jour
`docs/reference/regles-de-gestion/12-facturation-reglements-comptabilite.md` et `docs/client/documentation-fonctionnelle.html` (3 sources d'écouteurs) ; inventaire v2 lignes 18, 19, 22, 23. Glossaires : aucun terme nouveau (noms d'ACL techniques, pas de concept métier).

## Tests
Trois passes ciblées (normale, `runOrder=random`, normale) : `JeuDeDemonstrationDuStockIntegrationTest`, `ProductionProfileConfigTest`, `ArchitectureHexagonaleTest` (tous modules en amont), caractérisations Comptabilité/Facturation, `Table*Test`, `Schema*Test`, tests des advices. Build complet : voir la réponse de l'appelant.
