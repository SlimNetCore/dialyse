---
status: accepted
date: 2026-11-12
decision-makers: [utilisateur, architecte]
context-scope: pilotage-direction, documents, platform
related: ADR 0024, 0102 (PD4), 0140 (D2, D3), 0145, 0146, 0147 ; `.ddd/migration/reviews/pl9-filet.md`, `pl9-realisation.md` ; `.ddd/safety-net/known-bugs.md` KB-PL9-1 à 8
---

# 0148 - PL9 : le rapport PDF de la direction est rendu par Documents depuis le Pilotage ; corrections KB-PL9

Refactoring sur place (ADR 0024) : mêmes URL (`GET /api/v1/direction/report`, `GET /api/v1/direction/snapshots/{mois}/report`), même rôle DIRECTION, mêmes tables. Pas de flag ; retour arrière = revert du commit.

## Décisions
1. **Le Pilotage met en page et fournit ses données (ADR 0140 D3)** : `MiseEnPageDuRapport` produit une `PageDuRapport` (textes formatés, lignes de 13 cellules au plus, sections ordonnées) ; `ImprimerLeRapport` (cas d'usage), ports `ImpressionDuRapport`, `SourceDUnInstantane`, `SessionDeLaDirection` ; ACL `acl/documents/RapportRenduParDocuments` (vers `RenduDeDocument`, type `RAPPORT_DIRECTION`), `acl/instantane`, `acl/legacyidentite/SessionLegacy` (même garde que `DirectionAccessGuard`). Contrôleur `RapportDeLaDirectionControleur` dans le module. Supprimés de la plateforme : `DirectionReportPdfService`, `SocieteLetterheadPort`, `SocieteLetterheadAdapter` (interdits de retour par ArchUnit).
2. **Gabarit sans requête** : `reports/rapport_direction.jrxml` (grille générique, groupe par section, cellules extensibles), déclaré au catalogue des modèles (`RAPPORT_DIRECTION`, provisionné par centre) ; test d'architecture « gabarit sans requête ». Le moteur Jasper reste en plateforme jusqu'à DOC2.
3. **Ajout au port documents.v1** : `RenduDeDocument.rendrePourLaSociete(societe, type, donnees, format)` (méthode par défaut ; `MoteurDeRendu.rendreParTypePourLaSociete`), pour une société SANS centre : gabarit standard du catalogue + papier en-tête de la société, sans modèle de centre. Décision utilisateur : la société sans centre garde son 200 (reproduit). Avec au moins un centre, le modèle (activable, personnalisable) et le papier en-tête sont ceux du PREMIER centre par nom ; le gabarit n'imprime pas `CENTRE_*`.
4. **Paramètres réservés** : le préfixe `RAPPORT_` rejoint `ParametresReserves` : `RAPPORT_DIRECTION` n'est pas imprimable par l'API générique `/print` (données fournies par le Pilotage seul).
5. **Corrections (décisions du 2026-11-12)** : KB-PL9-1 horodatages au fuseau d'Alger ; KB-PL9-2 matrice des caisses paginée (10 centres par tableau, « Total société » répété) ; KB-PL9-3 instantané vide = 422 `INSTANTANE_ILLISIBLE` ; KB-PL9-4 routes voisines du rapport = 404, `Accept` sans PDF = 406 (`MethodesNonSupporteesDeLaDirectionAdvice`) ; KB-PL9-5 (message) 400 « Jeton de session illisible » ; KB-PL9-6 textes longs à la ligne ; KB-PL9-7 montants BigDecimal, arrondi à la paire, 0 décimale ; KB-PL9-8 action d'audit `DIRECTION_RAPPORT_IMPRESSION` (2 routes).
6. **Reproduit** : cumul DIRECTION + ADMIN/SUPERADMIN ouvre le rapport (KB-PL9-5) ; caractères hors police (KB-PL9-6, DOC2).

## Dette et écarts de mise en page
Dette acceptée : barres de « Part du CA » et d'évolution remplacées par « x % » en texte.
Écarts non décidés, **rapprochés de l'origine le 2026-11-12** (correctif PL9) : (1) le papier en-tête précède le titre (titre en bande `columnHeader` de la première page) ; (2) tableaux sexe et âge de nouveau côte à côte (une rangée de 11 cellules, `MiseEnPageDuRapport`) ; (3) en-têtes sans mot coupé : quatre bandes de tableau selon le nombre de cellules (champ `NB` ; colonnes de 56, 67, 84 ou 112 pt), seul un nom de centre sans espace plus long que sa colonne se coupe encore ; (4) couleurs du stock (seuil, périmés en rouge ; péremption proche en orange) et de l'anémie (non administrées en orange) restaurées par styles conditionnels sans SQL ; (5) profil sans droit + date illisible sur `/report` : « Accès refusé » (`RefusDuRapportAdvice`), 403 inchangé.
Restent hors de portée : la période (« <début> au <fin> ») n'est plus répétée à droite de l'en-tête de page (elle figure dans la ligne de contexte sous le titre) ; le pied de page est extrait après le corps (ordre de dessin de JasperReports) ; les couleurs ne sont pas vérifiées par le texte extrait des goldens (contrôle visuel à faire).

## Questions ouvertes (métiers/DPO, avant le 2026-12-15)
Nombre de décimales des montants (0 retenu) ; cumul DIRECTION + ADMIN ; centre porteur du modèle pour une société à plusieurs centres.
