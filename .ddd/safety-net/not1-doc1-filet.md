---
status: proposed
date: 2026-11-12
sources: [ADR 0140, canvas-notifications.md, canvas-documents.md]
---
# Filet NOT1 (Notifications) et DOC1 (Documents : rendu et papier en-tête)

Niveau : risque medium, HTTP MockMvc + JDBC réel H2 (profil `test`), contexte Spring partagé avec les autres tests (même configuration, aucun mock, aucun seam). Goldens dans `backend/platform/src/test/resources/characterization/{notifications/not1,documents/doc1}/<scénario>/`. Protocole : un `.received` relu puis promu en `.approved` (jamais `-Dcharacterization.approve`). Aucun code de production modifié ; `seamAllowed` reste `[]`.

## Déjà couvert avant (non dupliqué)
`NotificationRestControllerTest` (unitaire, mocks), `NotificationJournalIntegrationTest` (port + service), `DocumentIdentityIntegrationTest` (assertions), impression ordonnance (DM2) et synthèse de facturation (pilotage). Manquaient : HTTP des 3 routes, goldens, rendu générique, formats, droits, PDF/Excel.

## NOT1 : tests (`characterization/notifications/not1/`)
- `RoutesDuJournalCharacterizationIntegrationTest` (12) : liste vide, alerte lue/non lue, visibilité par profil (11 profils), rôle avec `_`, fenêtre 30 j et ordre, pagination/bornes, paramètres illisibles, marquer lues, lecture-tout, corps mal formés, garde de centre/anonyme, cloisonnement.
- `DiffusionEtPurgeCharacterizationIntegrationTest` (8) : diffusion STOMP captée sur `brokerChannel` (sans réseau), évènement ponctuel, alerte personnelle, rejeu idempotent, après-commit/rollback, audience/centre, purge (job appelé directement), cron `0 40 3 * * *` + conservation 60 j.
Limites : `Instant.now()` en dur (dates semées relatives, masquées `<HORLOGE>`, l'ordre reste vérifié) ; la livraison à un vrai client WebSocket et la branche « purge en échec » (exception avalée) ne sont pas observées ; la purge est de 60 jours (canvas : « durée à confirmer, Q10 »).

## DOC1 : tests (`characterization/documents/doc1/`)
- `RenduParLesRoutesCharacterizationIntegrationTest` (13) : `POST /print` et `/print/{modeleId}` : listes riches/vides, PDF (texte extrait par page, pages, images, en-tête `%PDF`), Excel (cellules), HTML (PDF intégré décodé), identité usurpée, `CENTER_ID` client, fiche/attestations/PEC, formats et casse, 400 en texte brut, gabarit introuvable, droits par profil, centre du corps non gardé, impression par identifiant.
- `PapierEnTeteEtModelesCharacterizationIntegrationTest` (11) : `paramsFor`, `decorateHtml`, `societeIdentity`, `stripReservedParams`, `ModeleDocumentPrinter.print` (provisionnement, format du modèle, refus métier, échec enveloppé).
Volatiles : seul « Imprimé le jj/mm/aaaa hh:mm » est masqué (`<HORODATAGE>`) ; métadonnées PDF jamais comparées.
Non couvert : `ModeleDocumentTemplateService`/`CustomTemplateCompiler`/`JrxmlSecurityValidator` (DOC2/DOC3, filet `ModeleDocumentTemplateIntegrationTest` existant), plannings/inventaire/bon d'intervention/cahier (couverts par leurs slices), `FrenchFormatFactory` (aucun gabarit de DOC1 ne formate de nombre), PEC avec données.

## Routes existantes de `DocumentRestController` hors DOC1 (à filet DOC3/DOC4)
`GET /modeles`, `POST /modeles/search`, `POST|PUT|DELETE /modeles[/{id}]` (ADMIN, SUPERADMIN), `GET /types`. Les routes `/modeles/{id}/versions|source|activate|reset` sont dans `ModeleDocumentTemplateRestController`.

## Bugs connus
Voir `.ddd/safety-net/known-bugs.md`, section « NOT1 / DOC1 » (KB-NOT1-1..3, KB-DOC1-1..5).
