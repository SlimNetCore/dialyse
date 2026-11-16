---
status: done
date: 2026-11-16
sources: ADR 0162 (option A), ADR 0208, known-bugs.md KB-POL1-1 à 7
---

# POL1 - Polices PDF : DejaVu Sans (option A) - réalisation

## Livré
- `contexts/documents/pom.xml` : `net.sf.jasperreports:jasperreports-fonts:6.21.5` (présent dans `~/.m2`, build hors-ligne) ; `pdfbox:2.0.24` en test.
- `contexts/documents/src/main/resources/jasperreports.properties` : `default.font.name` et `default.pdf.font.name` = DejaVu Sans, `default.pdf.encoding=Identity-H`, `default.pdf.embedded=true`. L'extension de polices est auto-déclarée par le jar (`jasperreports_extension.properties`). Aucun gabarit modifié.
- `PolicesDesDocumentsPdfTest` (2 tests verts) : grec, cyrillique, étoile, coche, arabe rendus ; accents, Œ/œ, € conservés ; polices DejaVuSans embarquées.
- Licence (fichier `LICENSE` du jar) : Bitstream Vera + domaine public DejaVu ; embarquement et redistribution autorisés ; interdit de vendre la police seule et de réutiliser le nom « Vera » pour une version modifiée. Consignée dans l'ADR 0208.
- ADR 0208 ; ADR 0162 : statut polices = accepted (option A). context7 : clé API refusée, les noms de propriétés ont été relus dans `default.jasperreports.properties` du jar 6.21.5.

## Goldens révisés (37 ; un par un, jetons comparés avant/après)
Outil de revue : comparaison des jetons de chaque valeur différente (reflux seul / jetons manquants / jetons ajoutés).

| Nature de l'écart | Goldens |
|---|---|
| Reflux seul (mêmes jetons, autres retours à la ligne) | `ordonnance_doc5d_riche_texte`, `rapport_mensuel_nouveau_code` (pd4), `surcharge_de_forfait_nouveau_code`, `statuts_bornes_et_egalite_nouveau_code`, `refus_nouveau_code` (se7e) |
| Cahier de dialyse : « (PMP) » de l'en-tête n'est plus affiché (cellule d'une ligne) - KB-POL1-4 | `cahier_complet`, `cahier_nu`, `volets_et_dossier_nouveau_code`, `consommables_prescription_forfait_absent_nouveau_code`, `complet_nouveau_code`, `etancheite_des_centres` |
| Procès-verbal d'inventaire : en-têtes coupés en milieu de mot, dates et montants extrêmes coupés - KB-POL1-2 | `inventaire_doc5j_pv_texte`, `inventaire_doc5j_direct_texte`, `pv_pl7_annule_texte`, `pv_pl7_contenu_limites_texte`, `pv_pl7_en_cours_partiellement_compte_texte`, `pv_pl7_provisoire_texte`, `pv_pl7_sans_ligne_cloture_texte_nouveau_code`, `pv_pl7_sans_ligne_texte_nouveau_code`, `pv_pl7_soixante_lignes` (183 -> 195 lignes de texte) |
| Texte plus long coupé dans une cellule - KB-POL1-3 | `fiche_doc5i_riche_texte` (e-mail), `liste_attestations_doc5g_riche_texte`, `planning_doc5c_complet_texte`, `planning_doc5c_une_ligne_texte` (légende), `bon_pl6_caracteres_speciaux_texte` (mot « "cle" » perdu, étoile désormais rendue) |
| Rapport direction : en-têtes coupés, 10 pages au lieu de 9 - KB-POL1-4 | `rapport_pdf_nouveau_code` (se7e), `trente_deux_centres_trente_six_mois_nouveau_code`, `rapport_mensuel_atypique_nouveau_code`, `montants_enormes_arrondis_et_textes_nouveau_code` |
| Caractères hors Cp1252 désormais rendus (étoile, arabe) - KB-POL1-1 | `textes_longs_et_unicode_nouveau_code`, `bon_pl6_caracteres_speciaux_texte` |
| Artefact d'extraction (« 1071,54 » extrait « 1 071,54 », rien de perdu) - KB-POL1-6 | `fevrier_2026_pdf_nouveau_code`, `centre_b_par_son_administrateur_nouveau_code`, `par_identifiant_de_modele_admin_totaux_enregistres_nouveau_code`, `montants_enormes_et_arrondis_nouveau_code` (synthese-pl8) |
| Page vide disparue (KB-GMAO-69 sans objet) - KB-POL1-5 | `bon_nominal_texte`, `bon_pl6_textes_tres_longs` (131 lignes au lieu de 132) ; assertion de pages du test `BonPdfCharacterizationIntegrationTest` passée de 2 à 1 |

Non approuvé : `feuille_pl7_contenu_limites` (xlsx, 17/12/2027 vs 21/12/2027, échec toléré sans lien avec la police).

## Gabarits
Aucun gabarit modifié. Débordements consignés (KB-POL1-2 à 4), à confirmer métiers/DPO avant le 2026-12-15. Expérience faite : « DejaVu Sans Condensed » supprime la plupart des coupures d'en-têtes et de dates ; proposition à soumettre à l'utilisateur (ce n'est pas la décision prise).

## Reste ouvert
- Décision : corriger les largeurs de colonnes de `inventaire_stock`, `fiche_patient`, `liste_attestations`, `planning_optimise`, `rapport_direction`, `cahier_dialyse`, ou passer à la variante Condensed.
- Confirmation visuelle des montants de la synthèse (KB-POL1-6).
- CJK : hors police (option B si besoin avéré).
