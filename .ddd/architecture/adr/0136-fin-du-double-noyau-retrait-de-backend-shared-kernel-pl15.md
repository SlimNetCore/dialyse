---
status: accepted
date: 2026-11-11
decision-makers: [architecte, utilisateur]
context-scope: platform, shared-kernel
related: ADR 0010, 0024, 0135 ; `.ddd/migration/reviews/pl15-decommissionnement-shared-kernel.md` ; rapport `.ddd/migration/reviews/pl15-realisation.md`
---

# 0136 - Fin du double noyau : retrait de `backend/shared-kernel` (PL15)

Refactoring sur place (ADR 0024) : mêmes tables, mêmes URLs, mêmes contrats JSON. Aucune règle métier touchée.

## Contexte
Deux noyaux coexistent : l'ancien `backend/shared-kernel` (package `com.hemodialyse.backend.domain.shared`, consommé uniquement par `platform`, 0 import
depuis `contexts/*`) et le noyau cible `backend/contexts/shared-kernel` (ADR 0010 : `CentreId`, `SocieteId`, `Mois`, garde de centre).

## Décision
| Type de l'ancien noyau | Destination |
|---|---|
| `Money`, `Quantite`, `Poids`, `TransactionRunner` (+ `SpringTransactionRunner`), `SeanceFactureeNonModifiableException` (+ handler 422 orphelin de `ApiExceptionHandler`) | Supprimés (0 consommateur ; le code `SEANCE_FACTUREE_NON_MODIFIABLE` reste servi par les advices de `seance`) |
| `TenantScope` | `platform`, `infrastructure.security` |
| `Email`, `PhoneNumber` | `platform`, `infrastructure.migration.service` (seul consommateur : `LegacyValueParser`) |
| `BusinessException` | `platform` (package d'erreurs applicatives), jamais dans le noyau cible (hors ADR 0010) |
| `PagedResult` | `platform` adopte `contexts.sharedkernel.domain.PagedResult` (forme et JSON identiques) : addition au noyau cible déjà présente, simple swap d'import |
| `CenterId` | Fusionné dans `CentreId` (accesseur `valeur()`, message d'erreur de `CentreId` : "Centre requis") |

## Conséquences
- Le noyau cible n'est pas élargi (ADR 0010 respecté) : `PagedResult` y figure déjà ; aucun nouveau type n'y entre.
- Si un golden change à cause du message d'erreur de `CentreId`, protocole habituel (`*_nouveau_code.approved.json`, archive `_ecarts-legacy`, KB « CORRIGÉ »).
- Le module `backend/shared-kernel` est retiré du réacteur Maven, de `platform/pom.xml`, du `Dockerfile` et de `.ddd/arch-rules.json` en PL15g.
- `backend/db/migration-legacy` (archive H2 jamais exécutée) et les scripts `pit-*.sh` périmés sont supprimés ; `docs/FLYWAY.md` et `flyway.yml` mis à jour.
- Garde-fou : une règle ArchUnit interdit tout nouvel import de `com.hemodialyse.backend.domain.shared`.
