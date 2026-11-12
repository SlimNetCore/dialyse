---
status: accepted
date: 2026-11-12
decision-makers: [utilisateur, architecte]
context-scope: documents, organisation, platform
related: ADR 0024, 0111, 0140, 0141 ; `.ddd/safety-net/not1-doc1-filet.md` ; `.ddd/safety-net/known-bugs.md` KB-DOC1-1 à 5 ; `.ddd/migration/reviews/doc1-realisation.md`
---

# 0142 - DOC1 : le rendu et le papier en-tête passent au contexte Documents ; corrections KB-DOC1-1, 2, 5(c)

Refactoring sur place (ADR 0024) : mêmes tables, mêmes URLs `POST /api/v1/documents/print` et `/print/{modeleId}`, mêmes rôles, JSON et codes, hors écarts décidés. Pas de façade ni de flag. Module Maven `contexts/documents` (ADR 0011), enregistré dans `backend/pom.xml` et dépendance de `platform`. Les routes `/modeles` et `/types` restent en plateforme. Le moteur Jasper/openhtmltopdf (`JasperReportService`, `ModeleDocumentPrinter`) reste en plateforme : il migre en DOC2.

## Décision de placement
| Élément | Où |
|---|---|
| `DocumentIdentityProvider`, `PrintRequest`, `SocieteLogoService`, `LogoImageValidator` (plateforme) | supprimés (composition remplacée par `PapierEnTete`, logo porté par Organisation `IdentiteImprimee`, ADR 0111) |
| Domaine | `PapierEnTete`, `IdentiteImprimable`, `ParametresReserves`, `DemandeDImpression`, `DemandeInvalide`, `FormatDeSortie`, `ModeleDeDocument` |
| Application | `ImprimerUnDocument` (par type, par modèle), `RendreUnDocument` ; ports `SessionCourante`, `ModelesDeDocument`, `IdentitesImprimables`, `PapierEnTeteDesDocuments` |
| Adapters | `ModelesDeDocumentJdbc` (même contrat de test que le double en mémoire), `IdentitesImprimablesParOrganisation` (ACL), `SessionLegacy` (ACL de session), `ImpressionControleur` + `ErreursDeLImpressionAdvice` |
| Langage publié `documents.v1` | `RenduDeDocument` + `DocumentRendu`, `PapierEnTeteDuCentre` (promu du port du Dossier médical), `TypeDeDocumentDeclare` ; ports FOURNIS par la plateforme : `MoteurDeRendu`, `ReglesDImpressionDesTypesSensibles` |

## Décisions de conception
- D1. Aucun nouveau fichier de plateforme (le hook legacy bloque la création hors `covered.json`) : `ModeleDocumentPrinter` implémente `MoteurDeRendu`, `DocumentRestController` implémente `ReglesDImpressionDesTypesSensibles` (précédent NOT1, ADR 0141 D1).
- D2. Les types sensibles historiques (ORDONNANCE, synthèse de facturation) gardent leurs règles par le port `ReglesDImpressionDesTypesSensibles` ; elles passent avant la garde de centre générale.
- D3. Ordre d'exécution : règles des types sensibles, validation de la demande, garde de centre de la session, modèle, paramètres, rendu.
- D4. Paramètres réservés (toujours fournis par le serveur, valeur client ignorée) : `CENTER_ID` et ses variantes de casse/écriture, toute clé `SOCIETE_*`, `CENTRE_*`, `CENTRE_ADRESSE/CONTACT/LIGNE`. Inventaire dans `ParametresReserves`.
- D5. Le papier en-tête reproduit à l'identique le rendu historique (dont le `<body>` reconnu seulement sans attribut et `FrenchFormatFactory` absent de la route générique) : dette d'affichage, voir ci-dessous.
- D6. `PapierEnTeteDuCentre` (Dossier médical) est promu dans `documents.v1` ; l'ACL du Dossier médical (`PapierEnTeteDuCentreParLaPlateforme`) délègue au langage publié.

## Écarts décidés par l'utilisateur (2026-11-12)
| KB | Décision |
|---|---|
| KB-DOC1-1 | Corrigé : l'impression exige que le centre demandé soit celui de la session, 403 `ACCESS_DENIED` sinon (par type et par modèle) ; SUPERADMIN traité comme ailleurs (transverse) ; un ADMIN sans centre est désormais refusé |
| KB-DOC1-2 | Corrigé : les paramètres réservés sont toujours fournis par le serveur, la valeur du client est ignorée |
| KB-DOC1-3 | Reproduit : tout rôle authentifié imprime, l'anonyme est refusé. Question métier « matrice rôle x type de document » à confirmer avant le 2026-12-15 |
| KB-DOC1-4 | Reproduit : document sans données = document vide sans en-tête (dette d'affichage) |
| KB-DOC1-5 | (c) corrigé : entrées illisibles de `/print` (corps absent ou cassé, `centerId`/`params` illisibles) et `modeleId` non UUID = 400 `BAD_REQUEST` ; `patientId` illisible = 400 texte. Reproduits et consignés en dette : (a) dates avec `00:00`, (b) fiche patient sur 2 pages, (d) gabarit introuvable = 500 texte, (e) format inconnu servi en PDF, (f) `<body>` avec attribut, (g) `FrenchFormatFactory` |

## Conséquences
- Golden DM2 modifié (1) : `dossiermedical/dm2/impression/.../erreurs` (cas `ordonnanceId illisible` : 500 texte devient 400 `ordonnanceId illisible : un identifiant est attendu`, KB-DOC1-5 c) passe en `erreurs_nouveau_code`, ancien archivé dans `dm2/_ecarts-legacy/impression/`.
- Goldens DOC1 modifiés (6) : `requetes_refusees`, `patient_inconnu_ou_autre_centre`, `profils`, `center_id_du_client`, `impression_par_modele`, `centre_du_corps_non_garde` passent en `*_nouveau_code.approved.json` ; anciens archivés dans `documents/doc1/_ecarts-legacy/*_avant_doc1.legacy.json`. Les autres scénarios (papier en-tête, rendus par route, formats, casse, provisionnement) sont identiques.
- `FrontieresDesContextesTest` verrouille la disparition des quatre classes et l'usage du seul langage publié.
- Questions ouvertes : ORDONNANCE non gardé par modèle (reproduit) ; gabarit introuvable = 500 reproduit ; les contextes consommateurs n'utilisent pas encore `RenduDeDocument` (DOC2) ; `SocieteLetterheadPort` conservé jusqu'à PD ; matrice de rôles à confirmer (2026-12-15).
- Règles de gestion et annexe de traçabilité mises à jour. Glossaire Documents : termes proposés. Docs fonctionnelle et maquette : non régénérées ici.
