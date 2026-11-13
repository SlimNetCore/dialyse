---
status: accepted (placement, corrections de forme, audit) ; proposed (points reproduits à confirmer métiers/DPO avant le 2026-12-15)
date: 2026-11-13
decision-makers: [utilisateur, architecte]
context-scope: documents, platform
related: ADR 0024, 0142, 0162 ; `.ddd/safety-net/known-bugs.md` KB-DOC3-1 à 6 ; `.ddd/migration/reviews/doc3-realisation.md`
---

# 0163 - DOC3 : les versions de gabarit par centre passent au contexte Documents

Refactoring sur place (ADR 0024) : mêmes URL (`/api/v1/documents/modeles/{modeleId}/...`), mêmes rôles (`ADMIN`, `SUPERADMIN`, garde de classe), mêmes codes métier, mêmes tables.

## Placement
| Avant (plateforme) | Après (`contexts/documents`) |
|---|---|
| `ModeleDocumentTemplateRestController` (5 routes) | `interfaces.http.GabaritsControleur` + `ErreursDesGabaritsAdvice` |
| `ModeleDocumentTemplateService` (règles + JPA + port du moteur) | `domain.HistoriqueDesVersions` (agrégat : numérotation, une seule version en vigueur, retour au modèle d'origine), `FichierTeleverse` (extension, vide, 512 Ko, UTF-8, empreinte), `CommentaireDeDepot`, `Pagination` ; `application.GererLesGabarits` (ordre des contrôles, `JrxmlSecurityValidator`, compilation à l'essai) |
| `ModeleDocumentVersionJpaEntity` + dépôt Spring Data | `infrastructure.persistence.HistoriqueDesVersionsJdbc` (JDBC, même table) ; la même classe sert le port `application.GabaritPersonnaliseDuCentre` lu par le moteur de rendu |
| port `publishedlanguage.GabaritPersonnaliseDuCentre` | déplacé en `application` (il n'est plus publié) |
| suppression d'un modèle (`DocumentRestController`) | port publié `publishedlanguage.VersionsDeModele.retirerToutes` |

Sécurité du téléversement inchangée : `JrxmlSecurityValidator` (liste blanche), compilation à l'essai, borne 512 Ko ; rien n'est affaibli.

## Schéma
`modele_document_version` était créée par Hibernate ; `schema.sql` la déclare maintenant (`CREATE TABLE IF NOT EXISTS`, fidèle à Flyway V1 : pas de clé étrangère, contrainte `uk_modele_version (modele_id, version)`, index `idx_modele_version_modele`). Aucune migration Flyway modifiée.

## Décisions
- Formes illisibles : 400 / 415 / 405 (+ `Allow`) au lieu de 500 ; 403 avant 400 (le centre est contrôlé avant la lecture des autres paramètres) ; réactiver une version inexistante = 404 sans corps (KB-DOC3-1, 2).
- Audit dédié : `GABARIT_TELEVERSE`, `GABARIT_ACTIVE`, `GABARIT_REINITIALISE` (KB-DOC3-3). Lectures non tracées.
- Cache `reporting.customTemplates` (12 h) : reste déclaré par `CacheConfig` de la plateforme (gestionnaire de caches commun, précédent PL11 pour les caches `ref.*`) ; gardé par `CacheDesGabaritsCompilesIntegrationTest`.
- `ModeleDocumentDto` reste à la plateforme : c'est le corps de `POST/PUT /modeles` (DOC4a).
- Reproduits, à confirmer métiers/DPO avant le 2026-12-15 : KB-DOC3-4, 5, 6.

## Conséquences
- Filet : 15 scénarios MockMvc/H2 ; 8 identiques à l'existant, 7 rebasés en `*_nouveau_code.approved.json` (anciens dans `_ecarts-legacy`).
- Aucun report de butoir, aucune nouvelle date.
