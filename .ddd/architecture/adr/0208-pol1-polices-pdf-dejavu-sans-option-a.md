---
status: accepted
date: 2026-11-16
decision-makers: [utilisateur, architecte]
context-scope: documents
related: ADR 0162 (section polices, option A), 0024 ; `.ddd/safety-net/known-bugs.md` KB-PL6-4, KB-PL7-3, KB-PL8-3, KB-PL9-6, KB-POL1-* ; `.ddd/migration/reviews/pol1-realisation.md`
---

# 0208 - Polices PDF : DejaVu Sans (option A)

## Décision
L'utilisateur a retenu, le 2026-11-16, l'option A de l'ADR 0162 : la police par défaut de tous les documents PDF du moteur de rendu (contexte Documents) est **DejaVu Sans**, PDF **Identity-H embarqué**.

## Mise en oeuvre
- Dépendance `net.sf.jasperreports:jasperreports-fonts:6.21.5` dans `contexts/documents/pom.xml` (présente dans le dépôt Maven local ; build hors-ligne). L'artefact déclare lui-même l'extension de polices (`jasperreports_extension.properties` -> `SimpleFontExtensionsRegistryFactory` -> `net/sf/jasperreports/fonts/fonts.xml`) : la famille « DejaVu Sans » (normal, gras, italique, gras italique) est `pdfEncoding=Identity-H`, `pdfEmbedded=true`.
- `contexts/documents/src/main/resources/jasperreports.properties` : `net.sf.jasperreports.default.font.name=DejaVu Sans`, `default.pdf.font.name=DejaVu Sans`, `default.pdf.encoding=Identity-H`, `default.pdf.embedded=true`. Aucun gabarit ne déclare `fontName`/`pdfFontName` : les 14 gabarits sont inchangés.
- Les noms de propriétés et leurs valeurs par défaut (SansSerif, Helvetica, Cp1252, non embarquée) ont été lus dans `default.jasperreports.properties` du jar 6.21.5. La documentation context7 n'a pas pu être interrogée (clé API refusée) ; la déclaration de l'extension vient du jar lui-même.
- Test : `PolicesDesDocumentsPdfTest` (grec, cyrillique, étoile, coche, arabe en formes de présentation ; accents, Œ/œ, € ; polices DejaVuSans embarquées ; texte extrait par PDFBox, dépendance de test).

## Couverture et limites
Latin étendu, grec, cyrillique, étoile et symboles, arabe en formes de présentation (lettres isolées, pas de mise en forme bidi : ordre non garanti). **Pas de CJK** : japonais et chinois restent hors police (autre option B si le besoin apparaît).

## Licence (vérifiée sur `net/sf/jasperreports/fonts/dejavu/LICENSE` de l'artefact)
« Fonts are (c) Bitstream. DejaVu changes are in public domain » ; licence Bitstream Vera : usage, copie, fusion, publication, distribution et vente autorisés gratuitement ; l'avis de copyright et de licence doit accompagner toute copie des polices ; les polices modifiées doivent être renommées sans « Bitstream » ni « Vera » ; interdiction de vendre la police seule. Aucune modification des polices ici : conforme. L'embarquement dans les PDF est autorisé. Le fichier de licence voyage dans le jar `jasperreports-fonts`.

## Conséquences
- Les métriques diffèrent d'Helvetica (DejaVu est plus large) : retours à la ligne et largeurs changent. Les goldens PDF (texte extrait par page) ont été révisés un à un : voir `.ddd/migration/reviews/pol1-realisation.md`.
- Les PDF sont plus lourds (sous-ensemble de la police embarqué).
- Les KB-PL6-4, PL7-3, PL8-3, PL9-6 (caractères hors police perdus) sont résolues sauf pour le CJK.
- Reste ouvert : besoin CJK (métiers/DPO, à confirmer avant le 2026-12-15).
