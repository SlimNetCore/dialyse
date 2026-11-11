status: draft
date: 2026-11-11
sources: grep/git ls-files sur backend/ (pas de scan.ts), `.ddd/config.json` (sliceBudget: 5 entrypoints, 15 fichiers legacy, 1500 LOC), `.ddd/arch-rules.json`, `backend/pom.xml`, `backend/Dockerfile`, ADR 0010, `.ddd/migration/reviews/inventaire-platform-restant.md` (C7, question 7)

# PL15 - Décommissionnement de `backend/shared-kernel` (ancien noyau)

## Résumé
- Seul **`platform`** dépend de l'ancien noyau (`platform/pom.xml:17`). Aucun `contexts/*` n'importe `com.hemodialyse.backend.domain.shared` (0 import, 0 dépendance pom). Le décommissionnement est donc une affaire interne à `platform` + build.
- Sur 11 types, **6 sont morts** côté production : `Money`, `Quantite`, `Poids` (0 import ; les `Poids` de seance/stock/planification/dossier-medical sont des types propres), `TransactionRunner` (seul `SpringTransactionRunner` l'implémente, aucun consommateur), `SeanceFactureeNonModifiableException` (aucun `throw` : seulement le handler `ApiExceptionHandler.java:61-66`), et en pratique `Email`/`PhoneNumber` (1 seul usage : `LegacyValueParser.java:157,166`).
- Vivants : `CenterId` (39 main + 13 test), `BusinessException` (27 main + 15 test ; ~90 sites `new`/`extends`), `PagedResult` (26 main + 6 test), `TenantScope` (2 main + 1 test).
- Piège principal : `CenterId.value()` vs `CentreId.valeur()` (accesseur renommé), messages d'erreur différents ("CenterId cannot be null" vs "Centre requis"), sérialisation Jackson `{"value":..}` vs `{"valeur":..}`. Le swap n'est pas un simple changement d'import.
- Plan : 7 sous-slices (PL15a..g), chacune <= 15 fichiers, ordre du moins risqué au plus risqué, suppression du module en dernier (PL15g).
- `backend/db/migration-legacy` : **archive morte, non doublon** (H2, V1..V38 jamais exécutés) ; à traiter hors PL15 (décision utilisateur, Q8).

## 1. Inventaire des 16 fichiers (décompte de fichiers consommateurs)
Comptage par `import com.hemodialyse.backend.domain.shared.*` hors du module lui-même (0 pour `contexts/*` partout).

| # | Type (fichier) | contexts/* | platform main | platform test | Équivalent existant | Destination proposée |
|---|---|---|---|---|---|---|
| 1 | `vo/CenterId` | 0 | 39 | 13 | `contexts/shared-kernel` `CentreId` (record `valeur`) ; usages de `CentreId` dans platform : 5 main + 30 test | Remplacer par `CentreId` (noyau cible, ADR 0010) - PL15e/f |
| 2 | `exception/BusinessException` | 0 | 27 | 15 (18 réf. mot-à-mot) | Aucun ; `organisation`/`pilotage` ont leurs propres Advice (`ErreursDeLOrganisationAdvice`, `ErreursDuPilotageAdvice`) qui reproduisent le 422 | **platform** (package `application`/`web` d'erreurs) ; jamais dans le noyau (non listé ADR 0010) - PL15c/d |
| 3 | `PagedResult` | 0 | 26 | 6 | `contexts/shared-kernel` `PagedResult` : forme identique (items,total,page,size ; `of` identique). Utilisé par organisation 15+2 tests, seance 3+4, stock 1 (commentaire seul) | Voir Q1 : soit platform adopte le `PagedResult` du noyau cible (swap d'import pur, JSON identique), soit copie locale platform - PL15b |
| 4 | `TenantScope` | 0 | 2 (`CenterAccessGuard`, `NotificationRestController`) | 1 | Aucun ; champ `UUID centerId`, indépendant de `CenterId` | **platform** `infrastructure/security` - PL15a |
| 5 | `vo/Email` | 0 | 1 (`LegacyValueParser`) | 2 (EmailTest + 1) | `patient-couverture` a son propre Email | **platform** `infrastructure/migration/service` (ou suppression avec LegacyValueParser si outil de migration retiré) - PL15a |
| 6 | `vo/PhoneNumber` | 0 | 1 (`LegacyValueParser`) | 0 + PhoneNumberTest (module) | idem | idem |
| 7 | `vo/Money` | 0 | 0 | 0 | - | **Suppression** (+ MoneyTest) - PL15a |
| 8 | `vo/Quantite` | 0 | 0 | 0 | - | **Suppression** (+ QuantiteTest) |
| 9 | `vo/Poids` | 0 | 0 | 0 | types `Poids` propres aux contextes | **Suppression** (+ PoidsTest) |
| 10 | `port/TransactionRunner` | 0 | 1 (`SpringTransactionRunner` qui l'implémente) | 0 | - | **Suppression** avec `SpringTransactionRunner` (aucun injecteur trouvé) - PL15a, à reconfirmer par grep Spring (injection par interface) |
| 11 | `exception/SeanceFactureeNonModifiableException` | 0 | 1 (handler uniquement) | 0 | Facturation/seance contexts ont leur propre refus (F6, KB-FAC-70) | Handler orphelin : **supprimer** handler + type si aucune levée (Q4) - PL15a |
| 12-16 | 5 tests `EmailTest, MoneyTest, PhoneNumberTest, PoidsTest, QuantiteTest` | | | | | suivent leur type (supprimés ou déplacés) |

Total fichiers de production du module : 11 types + `pom.xml` + 5 tests = 17 suivis par git (16 sources + pom).

## 2. Points de couplage
| Point | Référence | Action |
|---|---|---|
| Réacteur Maven | `backend/pom.xml:27` (`<module>shared-kernel</module>`) | retirer en PL15g |
| Gestion de dépendance | `backend/pom.xml:49-53` (`shared-kernel` 0.0.1-SNAPSHOT) | retirer en PL15g |
| Dépendance | `backend/platform/pom.xml:16-18` | retirer en PL15g (après 0 import) |
| Docker | `backend/Dockerfile:13` (`COPY shared-kernel/pom.xml`), ligne 15 = noyau cible (à garder) | retirer ligne 13 (et son éventuelle copie `src`, à vérifier) |
| arch-rules | `.ddd/arch-rules.json:4` (`legacy` inclut `backend/shared-kernel/**`) et `:52` (`exclude`) | retirer les 2 entrées en PL15g ; l'entrée `shared-kernel` l.5 (cible) reste |
| ArchUnit | `FrontieresDesContextesTest` : aucune référence à `domain.shared`/`sharedkernel` (grep vide) ; les autres tests `architecture/` (Table*Test) ne l'utilisent pas | rien à adapter ; ajouter éventuellement une règle "plus aucun import de `domain.shared`" |
| check-arch | `tools/ddd-scan/check-arch.ts` pilotée par arch-rules.json | relancer après PL15g |
| Scripts | `backend/scripts/pit-a1-consultation.sh:14`, `pit-c3-ecritures.sh:16`, `pit-f3-validation.sh:15` citent `shared-kernel` avec des `bc-*` (modules déjà supprimés) | scripts périmés : à nettoyer ou ignorer (Q7) |
| CI | `.github/workflows/*` : aucune référence à shared-kernel trouvée | rien |
| covered.json / guard-legacy | `legacyPaths = backend/**/src/main/java/**` : toute édition d'un fichier platform listé ci-dessus exige une entrée `.ddd/safety-net/covered.json` | à prévoir par slice (Q6) |

### Doublons et conversions aujourd'hui
- **`PagedResult`** : deux records de même forme. Aucune conversion automatique : les contrats de contexte (organisation, seance) retournent le `PagedResult` du noyau cible, les contrôleurs de contexte le sérialisent eux-mêmes ; platform n'importe jamais le `PagedResult` cible (0 import dans platform). Les ACL platform reconstruisent un `PagedResult` legacy à la main (`PagedResult.of(items, total, page, size)`, ex. `ForfaitsParPatientCouverture.java:71`, `PayeursParPatientCouverture.java:103`). Les deux types cohabitent sans pont.
- **`CenterId` / `CentreId`** : pas de pont ; platform ACL (`acl/patientcouverture/*`) signent encore en `CenterId` et passent `.value()` au contexte. Un seul `new CentreId(...)` en main (`StockDemoDataSeeder.java:35`) ; 5 fichiers main importent `CentreId` (acl gmao, acl stock x2, `BonInterventionReportService`, seeder).
- **Écart de contrat** : accesseur `value()` (ancien) vs `valeur()` (cible) ; message d'erreur "CenterId cannot be null" (IllegalArgumentException) vs "Centre requis" ; `CenterId` a `of`, `generate`, `toString()=UUID`, que `CentreId` n'a pas (toString de record `CentreId[valeur=..]`). Impacts à vérifier : concaténations de clés/logs/SQL utilisant `toString()`, et sérialisation JSON.

### Types liés à des gardes, goldens ou messages
- `BusinessException` : `ApiExceptionHandler.java:48-55` produit le ProblemDetail 422 ("Regle metier violee", `detail=message`, `code=getCode()`). Les messages sont des contrats de golden (les Advice des contextes les recopient "à l'identique", cf. `ErreursDeLOrganisationAdvice.java:27`). Le déplacement de package ne change ni message ni code, **mais** vérifier qu'aucun golden n'embarque le FQCN (`com.hemodialyse.backend.domain.shared.exception...`) : grep ciblé à faire en PL15c (non fait ici).
- `TenantScope` : exposé par `CenterAccessGuard.currentScope()` ; non sérialisé (usage interne `NotificationRestController`).
- `CenterId` : JSON `{"value":...}` si un DTO/réponse l'embarque (à grepper : DTO `web/dto` contenant `CenterId`) - non constaté dans cette passe.
- `SeanceFactureeNonModifiableException.CODE` : code stable renvoyé au front (problem property "code") - si le handler est retiré, le code disparaît du contrat HTTP (voir Q4).

## 3. Découpage en sous-slices (budget : <= 15 fichiers, <= 1500 LOC, <= 5 entrypoints)
Les fichiers sont ceux touchés (main + test). Ordre = dépendances croissantes.

| Slice | Contenu | Fichiers | Risque | Tests à lancer |
|---|---|---|---|---|
| **PL15a** morts + petits | Supprimer Money, Quantite, Poids, TransactionRunner + `SpringTransactionRunner`, (SeanceFacturee* si Q4) ; déplacer TenantScope (3 fichiers) vers platform security ; déplacer Email/PhoneNumber + 3 tests de VO vers platform migration (ou supprimer) | ~16 (>15 : scinder a1 morts / a2 TenantScope+Email+PhoneNumber) | Faible | `mvn -pl platform test` ciblé : CenterAccessGuard, NotificationRestController, LegacyValueParser tests ; compilation platform |
| **PL15b** PagedResult | 26 main + 6 test : swap d'import vers `contexts.sharedkernel.domain.PagedResult` (si Q1=A) | 32 -> scinder b1 (application + web/rest + PagedResponse + notification, ~13), b2 (persistence adapters + reporting + acl, ~13), b3 (tests, 6) | Moyen : JSON identique (même champs), mais `PagedResponse.from` (`web/dto/response/PagedResponse.java:23`) et records générés | tests REST paginés, Audit, Notification, Referential admin, golden de pagination |
| **PL15c** BusinessException - application | 27 main : application/auth(2), direction(6), setup, supervision, acl(5), importer(2) ... nouveau package platform | ~13 | Moyen : 90 sites de création, `extends` (`ReferentialValidationException`) ; handler | Tests unitaires `application/*`, `ApiExceptionHandler` test, goldens 422 |
| **PL15d** BusinessException - infra + tests | infrastructure (migration, referential, reporting, web/rest) + 15 tests | ~15 (+ scinder les tests si >15) | Moyen | suite web/rest + goldens d'erreur |
| **PL15e** CenterId - migration & référentiels | `infrastructure/migration/*` (port 7, service 8, model 1, adapter 6) = 22 -> e1 (port+model+adapter, ~14), e2 (service, 8) | 22 | **Élevé** : `value()`->`valeur()`, message d'erreur, toString dans clés/SQL | tests migration (batch, reprise), replay PG16 (ADR 0134) |
| **PL15f** CenterId - reste | referential (8), acl patientcouverture (3), security (1), web/rest (3), application (3) + 13 tests | ~31 -> f1 (main ~18 -> 2), f2 (tests 13) | Élevé (sécurité, isolement RG-TRV-002) | tests d'isolation par centre, `CenterAccessGuard`, ArchUnit complet, golden ACL |
| **PL15g** retrait du module | pom.xml (3 endroits), Dockerfile l.13, arch-rules l.4 et 52, suppression de `backend/shared-kernel/` (17 fichiers) | ~5 édités + 17 supprimés | Faible si 0 import | `mvn -q -DskipTests=false verify` complet (lancé par l'orchestrateur sous verrou), `check-arch.ts`, build Docker |

Ordre recommandé : a -> b -> c/d -> e -> f -> g. e/f ne se parallélisent pas avec c/d (mêmes fichiers `infrastructure/...`). Les slices PL1 en cours (entités JPA stock, bons, app_settings, payeurs) ne sont pas analysées ici ; à croiser avec e/f (`TableDesPayeursTest`, `acl/stock` importent déjà `CentreId`) pour éviter les conflits de fichiers.

Alternative moins coûteuse pour CenterId : garder un type `CenterId` **local à platform** (copie, sans changer d'API) à la place de le fusionner dans `CentreId`. Gain : e/f disparaissent (simple déplacement de package, mécanique), mais maintient deux types d'identifiant de centre dans platform, contraire à l'esprit de l'ADR 0010 (Q2).

## 4. Statut de `backend/db/migration-legacy` (39 fichiers : README + V1..V38)
- **Non référencé par le build** : Flyway lit `platform/src/main/resources/db/migration` (55 fichiers, V1__baseline_schema + V2..V57) ; aucun `locations` ne pointe vers `backend/db`. Références seulement documentaires : `docs/FLYWAY.md:7`, `.github/workflows/flyway.yml:49,72` (tolère l'archivage vers ce dossier), `.ddd/safety-net/dossier-medical-dm2-prescriptions.md:39` (cite V24 comme preuve de FK patient).
- **Pas un doublon** : README du dossier : scripts jamais exécutés, dialecte H2 (`MERGE ... KEY`), données de démo ; la numérotation V1..V38 recouvre des numéros de la chaîne actuelle (V1..V57) avec un contenu différent. Ils ne servent que d'historique.
- **Statut : mort mais documenté** (une seule modification git, commit 00bd5117). Suppression possible sans effet build ; décision patrimoniale (perte de l'historique SQL H2, il reste dans git). Règle 20-legacy-safety : pas de DROP/DELETE de données ; ici fichiers uniquement, mais à valider (Q8). Si suppression : adapter `docs/FLYWAY.md:7` et le commentaire/règle `flyway.yml:49,72`.

## Limites de l'analyse statique
- Décomptes par grep d'imports FQN : un usage via wildcard (`import ...domain.shared.*`) ou même package est exclu par construction (aucun wildcard trouvé dans la regex testée, non exhaustif). Les mots "Poids", "Email", "BusinessException" apparaissent dans des contextes avec leur propre type : exclus du décompte consommateur.
- Injection Spring par interface (TransactionRunner), Jackson (sérialisation de `CenterId`), SpEL/XML, SQL contenant `toString()` d'identifiants : non visibles. Aucun test exécuté, aucun scan relancé.
- Les goldens (FQCN, JSON) n'ont pas été lus ; seule la structure est inférée.

## Questions ouvertes
1. **PagedResult** : platform adopte-t-il le `PagedResult` de `contexts/shared-kernel` (A, swap d'import) ou la copie locale (B) ? ADR 0010 dit "PagedResult relégué à l'infrastructure" alors que le noyau cible le contient encore : la décision de le garder ou non dans le noyau exige un ADR (addition/retrait du noyau).
2. **CenterId** : fusion dans `CentreId` (accesseur `valeur()`, message "Centre requis") ou conservation d'un type local platform ? Les messages d'erreur et toString doivent-ils rester identiques (goldens) ?
3. Les messages/FQCN de `BusinessException` apparaissent-ils dans des goldens ou documents de référence (`docs/reference/regles-de-gestion`) ? À grepper avant PL15c.
4. `SeanceFactureeNonModifiableException` n'est levée nulle part : le handler 422 + code stable (`F6`, KB-FAC-70) doit-il être conservé pour compatibilité front, ou supprimé ?
5. `SpringTransactionRunner`/`TransactionRunner` : confirmer l'absence d'injection (par interface ou par nom) avant suppression.
6. Les fichiers platform édités nécessitent-ils des entrées `covered.json` (hook `guard-legacy`) ou un accord global pour des modifications purement mécaniques (déplacement de package/import) ?
7. Les scripts `backend/scripts/pit-*.sh` (références à `bc-*` disparus) : à supprimer ?
8. `backend/db/migration-legacy` : supprimer (historique reste dans git) ou conserver archivé ? Si suppression : trancher le sort du commentaire `flyway.yml:49,72` et de `docs/FLYWAY.md:7`.
9. `LegacyValueParser` (outil de reprise) : Email/PhoneNumber doivent-ils survivre (déplacés vers platform) ou l'outil de reprise est-il à retirer prochainement (ADR 0134) ?
10. Faut-il un ADR pour acter la fin du double noyau (décision structurante : règle 00-pipeline-protocol) ? Proposé : ADR "Retrait de `backend/shared-kernel`" en tête de PL15a.
