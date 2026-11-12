---
status: approved
date: 2026-11-12
sources: [ADR 0140, .ddd/migration/reviews/inventaire-platform-restant.md (A8, B1, B7), DocumentRestController.java, ModeleDocumentTemplateRestController.java, PapierEnTeteDuCentre.java (dossier-medical)]
---
# Bounded Context Canvas — Documents (`documents`)

| Rubrique | Contenu |
|---|---|
| Nom / module | Documents · `backend/contexts/documents` · `com.hemodialyse.backend.contexts.documents` · propriétés `hemodialyse.contexts.documents.enabled` (ADR 0011) |
| But | Rendre en PDF/Excel tout document imprimable du centre, avec le papier en-tête et le gabarit actif du centre ; laisser l'administrateur personnaliser les gabarits. Ne décide d'aucun contenu métier. |
| Classification | **Generic** (SD16) : moteur adopté (JasperReports, openhtmltopdf, POI) derrière un port ; modèle léger. Aucun DDD tactique complet. |
| Rôle | gateway (rendu) + specification légère (catalogue de modèles) |
| Langage | modèle de document, type de document, version de modèle, gabarit, rendu, document rendu, papier en-tête, identité imprimée, format d'impression (glossaire `glossaries/documents.md`) |
| Règles | un seul modèle actif par (centre, type) ; sans modèle actif : refus `DOCUMENT_MODEL_INACTIVE` ; type inconnu : `DOCUMENT_TYPE_UNKNOWN` ; gabarit téléversé validé par liste blanche (`JrxmlSecurityValidator`) ; logo validé (`LogoImageValidator`) ; version activable/réinitialisable ; modèle provisionné à la première impression (`ModeleDocumentProvisioner`, hors lecture seule KB-PLN-51) |
| Données possédées | `modele_document`, `modele_document_version`. Lues (non possédées) : identité imprimable de la société/centre (Organisation, `societes`). |
| Commandes reçues | `rendre(centre, type, données, format)`, `habiller(html, centre)` (ports `documents.v1`, appel synchrone) ; REST inchangé : `GET /api/v1/documents/modeles`, `POST …/modeles/search`, `POST|PUT|DELETE …/modeles[/{id}]` (ADMIN, SUPERADMIN), `GET …/types`, `POST …/print`, `POST …/print/{modeleId}`, `GET …/modeles/{id}/versions|source`, `POST …/versions`, `…/versions/{v}/activate`, `…/reset` (ADMIN, SUPERADMIN) |
| Événements émis | aucun (appels synchrones de rendu). Éventuel `ModeleDeDocumentModifie` pour le journal d'audit, via l'audit de plateforme (à confirmer). |
| Dépendances | Amont : Organisation (OHS `IdentiteImprimable`, ACL côté Documents) ; plateforme (session, rôles, audit : Conformist). Aval : Séance, GMAO, Stock, Facturation, Pilotage, Dossier médical, Planification, Patient & couverture (OHS+PL `documents.v1`, ACL côté consommateur). Chaque contexte fournit son `TypeDeDocumentDeclare` + ses données (Documents conformiste au contrat déclaré). |
| Reste en plateforme | chaîne de sécurité, rôles, garde de centre, audit des écritures, licence ; routage des URL |
| Hypothèses | les 8 types « pilotés par le front » (attestation, ordonnance, fiche patient, listes patients/PEC/attestations, prise en charge, plannings) gardent leurs gabarits standards dans Documents jusqu’à déclaration par leur propriétaire (Q6) |
| Questions | voir `.ddd/architecture/adr/0140` et la section ci-dessous |
| Verdict | équipe plateforme ; extractible en dernier (aucune règle de domaine) ; test de caractérisation : texte extrait du PDF, pas les octets |

## Slices
DOC1 (port + papier en-tête, ~12 f., ~900 l.) → DOC2 (moteur, ~8 f., ~800 l.) → DOC3 (modèles par centre, 5 routes, ~8 f.) → DOC4a/b (= PL12 : `/modeles` 5 routes ; `/types` + `/print*` 3 routes). PL5-PL9 consomment `documents.v1` dès DOC1 (adaptateur transitoire).

## Questions ouvertes
1. Voir `.ddd/strategic/bounded-contexts.md` § Questions ouvertes (Q6-Q9) : types pilotés par le front, SQL dans les gabarits, Excel, logo.
