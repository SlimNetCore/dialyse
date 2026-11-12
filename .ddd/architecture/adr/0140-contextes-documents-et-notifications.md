---
status: approved
date: 2026-11-12
decision-makers: [utilisateur, architecte, experts métier (gate strategic)]
context-scope: documents, notifications, platform
related: ADR 0001, 0008, 0010, 0011, 0024, 0066, 0121, 0138 ; `.ddd/migration/reviews/inventaire-platform-restant.md` (A7, A8, A10, B1, B7, B8) ; canvases `.ddd/strategic/canvas-documents.md`, `canvas-notifications.md`
sources: [bounded-contexts.md:131-132, context-map.md R21, inventaire-platform-restant.md, DocumentRestController.java:43-370, NotificationRestController.java:29-70, NotificationJournalPort.java, NotificationService.java:48-654, V29__notification_journal.sql, V49__notification_destinataire.sql]
---
# 0140 — Deux contextes dédiés : Documents (rendu imprimable) et Notifications (acheminement des alertes)

## Contexte et problème
`bounded-contexts.md:131-132` rangeait « Notifications » parmi des génériques « sans règle métier », et le moteur de documents n'avait aucune décision écrite (ADR 0111:31 : « reste à la plateforme »). Or `NotificationService` (654 l., ~40 `notifyXxx`, 15 importeurs) est un catalogue de messages métier d'autres contextes, et 5 rapports métier (PL5-PL9) sont rendus par la plateforme avec SQL direct (`DocumentRestController.java:141-370`). L'utilisateur a décidé (2026-11-12) : un contexte dédié chacun ; Documents = générique/support avec rendu exposé par un port publié ; mise en page et données restent aux contextes métier ; messages de notification aux émetteurs, acheminement/journal/lecture à Notifications. Refactoring sur place (ADR 0024).

## Options considérées
1. Garder tout en plateforme (statu quo) — contre : catalogue métier dans un générique, PL5-PL9 sans cible.
2. **Deux contextes dédiés, rendu et acheminement publiés, contenu aux contextes métier** (décision de l'utilisateur) — pour : propriétaires uniques des tables et des règles ; contre : deux modules et deux langages publiés à tenir.
3. Notifications abonné aux événements métier, catalogue dans Notifications — contre : couplage vers tous les émetteurs, modèle canonique de messages (anti-pattern), rejeté par l'utilisateur.
4. Éclater le moteur par contexte (chacun son Jasper) — contre : duplication de la sécurité `JrxmlSecurityValidator` (409 l.) et des modèles par centre.

## Décision (option 2)
| # | Décision |
|---|---|
| D1 | `contexts/documents` (`com.hemodialyse.backend.contexts.documents`) : sous-domaine **SD16 Documents imprimables, Generic**. Possède : `modele_document`, `modele_document_version` ; moteur Jasper/openhtmltopdf/Excel ; validation de gabarits ; papier en-tête (composition). Ne possède PAS : logo/société (Organisation, table `societes`), mise en page ni données des rapports métier. |
| D2 | Langage publié `documents.v1` (types JDK) : `RenduDeDocument.rendre(centre, typeDeDocument, donnees, format) → DocumentRendu(octets, typeMime, nomDeFichier)`, `PapierEnTeteDuCentre.habiller(html, centre)` (promu depuis le port déjà publié par Dossier médical, `PapierEnTeteDuCentre.java`), `TypeDeDocumentDeclare` (code, libellé, gabarit par défaut, paramètres attendus, version du contrat). Chaque contexte déclare ses types ; Documents ne dépend d'aucun contexte. Relation : Documents **U OHS+PL** → contextes **D ACL** (port sortant propre, vocabulaire du contexte). |
| D3 | Données en entrée du rendu : le contexte fournit ses données (listes/map) ; **aucun SQL dans un gabarit livré** (prérequis RF3c-J1 pour PL8). Un gabarit personnalisé par centre (`modele_document`) se substitue au gabarit par défaut sous le même contrat de paramètres. |
| D4 | Organisation → Documents : **Customer–Supplier**, nouveau port publié d'Organisation `IdentiteImprimable` (nom, adresse, logo, pied de la société/centre) ; ACL côté Documents (remplace `SocieteJpaRepository` lu par `SocieteLogoService`). |
| D5 | `contexts/notifications` : sous-domaine **SD13, Generic**, périmètre redéfini. Possède : `notification_evenement`, `notification_lecture`, `GET /api/v1/notifications`, `POST …/lecture`, `POST …/lecture-tout`, purge 03:40. Règles propres : visibilité par rôle ciblé et centre, lecture par utilisateur, idempotence par identifiant déterministe, purge. Ne possède PAS le texte/contenu ni le choix du type : ils appartiennent à l'émetteur. |
| D6 | Langage publié `notifications.v1` : `Notifier.notifier(DemandeDeNotification(centre, type, payload Map<String,String>, audience, idDeterministe?))`, `Audience` (rôles sans préfixe `ROLE_`, ou utilisateur). Les codes de type et le payload sont définis par l'émetteur dans sa propre documentation (`type` inchangé en base et dans le JSON). Relation : Notifications **U OHS+PL** → émetteurs **D ACL**. Appel après commit de la transaction émettrice (reprend `NotificationService.java:644`). |
| D7 | Reste en plateforme : sécurité/rôles/licence/audit (A1-A3), transport STOMP (A6) derrière le port `DiffusionEnTempsReel` de Notifications. Les 12 ACL `*Legacy` de notification disparaissent avec PL13 ; les ACL d'impression (`ImpressionDe*Legacy`, `PapierEnTeteDuCentreParLaPlateforme`) deviennent des adaptateurs du port `documents.v1`. |
| D8 | URLs, tables, JSON, rôles inchangés. `/api/v1/documents/**` : ADMIN/SUPERADMIN sur écritures de modèles et versions (`DocumentRestController.java:135,151,168`, `ModeleDocumentTemplateRestController.java:43`) ; reste : session + garde de centre. `/api/v1/notifications` : session + garde de centre, visibilité par `target_roles`. |

## Signaux de frontière
Documents : linguistique (« modèle de document », « gabarit », « papier en-tête » ≠ vocabulaire métier) ; données (`modele_document*` sans autre propriétaire) ; sécurité/cadence (validation de JRXML téléversé, bibliothèques de rendu) ; cohésion (moteur 3 000 l. consommé par ≥ 8 contextes). Notifications : linguistique (« alerte » ≠ Alerte de direction ≠ alerte d'anémie) ; données (journal + lecture) ; invariants propres (visibilité, lecture par utilisateur, idempotence) ; cadence (purge, push).

## Slices (budget 5 entrées / 15 fichiers / 1 500 l.)
| Slice | Contenu | Entrées | Fichiers | Lignes | Prérequis |
|---|---|---|---|---|---|
| DOC1 | module + `documents.v1` + papier en-tête (`DocumentIdentityProvider`, `SocieteLetterheadAdapter`, `LogoImageValidator`, `FrenchFormatFactory`, `SocieteLetterheadPort`) + `IdentiteImprimable` + adaptateur transitoire de `RenduDeDocument` enveloppant `ModeleDocumentPrinter` | 0 | ~12 | ~900 | filet (hors `covered.json`) |
| DOC2 | moteur Jasper/validation vers le module (`JasperReportService`, `JrxmlSecurityValidator`, `CustomTemplateCompiler`) + gabarits standards | 0 | ~8 | ~800 | DOC1 |
| DOC3 | modèles par centre (`ModeleDocumentCatalog/Provisioner/Printer/TemplateService`, `ModeleDocumentVersion`) + `ModeleDocumentTemplateRestController` | 5 | ~8 | ~800 | DOC2 |
| DOC4a/b | = PL12 scindé : (a) `/modeles` 5 routes + port `ModelesDeDocument` hors contrôleur ; (b) `/types`, `/print`, `/print/{id}` | 5 / 3 | 4 / 3 | ~400 / ~300 | DOC3 |
| NOT1 | module + `notifications.v1` + journal + 3 routes + purge + port `DiffusionEnTempsReel` | 4 | ~12 | ~800 | filet (REST hors `covered.json`) |
| NOT2 | `NotificationService` réduit à une façade sur `Notifier` (mêmes types/payload/rôles ; après-commit déplacé) | 0 | ~4 | ~700 | NOT1 |
| PL13a-d | côté émetteur (a patient/PEC + dossier médical ; b séance ; c planification ; d absences/stock/GMAO) ; retrait des ACL `*Legacy` ; PL13d supprime `NotificationService` | 0 | 6-10 | 300-500 | NOT2, séquentiel |
Ordre : DOC1 → PL5-PL8 (parallèles) → PL9 ; NOT1 → NOT2 → PL13a → b → c → d ; DOC2-DOC4 en tâche de fond. Chaînes DOC et NOT indépendantes (enregistrer les deux modules dans `backend/pom.xml` en série).

## Conséquences
+ propriétaires uniques ; PL5-PL9 et PL13 ont une cible ; le papier en-tête n'est plus lié à la plateforme.
− deux modules, deux langages publiés versionnés ; contrat de paramètres des gabarits à tenir (un jrxml personnalisé peut casser si l'émetteur change ses données).
⚠ PDF octet pour octet : comparer le texte extrait ; ordre après commit des notifications ; admin générique de gabarits = surface de sécurité (injection JRXML).
## Révision
À la fin de DOC4 et PL13d ; ou si un 2e moteur de rendu est requis.
