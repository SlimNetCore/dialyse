---
status: done
date: 2026-11-16
sources: pol1-realisation.md, ADR 0208 (addendum POL2), known-bugs.md KB-POL1-2 à 4, décision utilisateur du 2026-11-16 (retoucher les largeurs, police inchangée)
---

# POL2 - Débordements de gabarits dus à DejaVu Sans : largeurs retouchées

## Gabarits modifiés (`contexts/documents/src/main/resources/reports/`)
Seuls x, width, height, stretch ont changé ; aucune requête, aucun champ, aucun contenu métier, aucun ordre, aucune police.
| Gabarit | Retouche |
|---|---|
| `inventaire_stock` | lot 84->80, péremption 54->62, théorique 50->58, compté 50->50, écart 46->44, PMP 56->68, valeur écart 66->74, motif 75->51, compté par 58->52 (en-têtes, lignes, sous-totaux, total ; somme = 539) |
| `fiche_patient` | e-mail : étiquette 55->46, valeur 105->121 pt, `textAdjust=StretchHeight` |
| `liste_attestations` | code 100->80, nom 140->206, prénom 140->206, assurance 170->130, dates 126->90 ; nom/prénom étirables |
| `planning_optimise` | légende : hauteur 22->34 (bande 30->42) |
| `bon_intervention` | libellé de ligne 130->170 pt (étirable), quantité/prix/montant redistribués, filet en `positionType=Float` |
| `rapport_direction` | première colonne 130->100 ; colonnes de données élargies dans les 4 bandes (13 cellules : 58, 10-11 : 70, 8-9 : 87, <=7 : 117) ; lignes de tableau 16->14 pt (nécessaire pour retrouver 9 pages sur le cas à 32 centres) |
| `cahier_dialyse` | tableau des consommables : article 250->220, qté 70->56, unité 50->44, valeur unitaire (PMP) 85->126, valeur totale 84->93 |

## Goldens révisés (29 dont `pv_pl7_cloture_ecarts_texte` : mêmes en-têtes entiers ; un par un, jetons comparés avant/après ; approuvés après relecture)
| Golden | Nature de l'écart |
|---|---|
| `inventaire_doc5j_pv_texte`, `inventaire_doc5j_direct_texte`, `pv_pl7_annule_texte`, `pv_pl7_provisoire_texte`, `pv_pl7_sans_ligne_texte_nouveau_code` | en-têtes « PÉREMPTION » / « THÉORIQUE » entiers ; dates (31/12/2025, 01/01/2020, 20/04/2026) et montants (-99 999 999,99 ; -100 002 994,50) entiers ; plus de coupure |
| `pv_pl7_contenu_limites_texte` | idem ; le libellé de lot « LOT'; DROP TABLE » et sa date (<J-30>) tiennent ; « 99 999 999 » entier |
| `pv_pl7_soixante_lignes` | 195 -> 187 lignes de texte (moins de retours à la ligne) |
| `fiche_doc5i_riche_texte` | e-mail « eleonore@exemple.test » entier |
| `liste_attestations_doc5g_riche_texte` | noms et prénoms longs entiers (« la », « vérifier la » retrouvés) ; feuille tableur : colonne d'assurance en 7 au lieu de 5 (grille de colonnes), même contenu |
| `planning_doc5c_complet_texte`, `planning_doc5c_une_ligne_texte` | légende entière (« - infirmier remplaçant (nouveau) ... validée par l'administrateur ») sur trois lignes |
| `bon_pl6_caracteres_speciaux_texte` | mot « "cle" » du libellé de pièce retrouvé |
| `cahier_complet`, `cahier_nu`, `volets_et_dossier_nouveau_code`, `consommables_prescription_forfait_absent_nouveau_code`, `complet_nouveau_code`, `etancheite_des_centres` | « (PMP) » de l'en-tête « Val. unitaire (PMP) » de nouveau affiché |
| `rapport_pdf_nouveau_code` (se7e) | « Reste à recouvrer », « Taux d'encaissement » entiers |
| `trente_deux_centres_trente_six_mois_nouveau_code` | 10 pages -> 9 pages (marqueurs « Page n / 9 » ; aucun jeton de données changé) |
| `rapport_mensuel_atypique_nouveau_code`, `montants_enormes_arrondis_et_textes_nouveau_code` | reflux : coupures de chaînes sans espace (80 caractères) et de montants énormes à espaces insécables déplacées ; la pagination change (pied de page de la section déplacé) ; aucun jeton perdu |
| `pv_pl7_en_cours_partiellement_compte_texte`, `pv_pl7_sans_ligne_cloture_texte_nouveau_code` | en-têtes « PÉREMPTION » / « THÉORIQUE » entiers |
| `bon_doc5b_complet_texte` | libellé long de pièce entier (« ... la colonne cent trente points large ») |
| `fiche_et_attestations` (doc1) | feuille tableur : décalage d'indices de colonnes/lignes (e-mail, assurance), même contenu |
| `rapport_en_direct_nouveau_code`, `rapport_mensuel_nouveau_code` (pd4) | reflux : le tableau « Centre B » change de page ; la note de fin tient seule sur la page 3 sans pied de page (le « Page 3 / 3 » et le pied de page de cette page n'apparaissent plus ; aucune donnée perdue) |
`bon_nominal_texte` : non modifié (type « Honoraires intervenant » conservé à 110 pt après un essai à 90 pt, rejeté).
Non approuvé : `feuille_pl7_contenu_limites` (xlsx, date 17/12/2027 vs 21/12/2027, échec toléré sans lien).

## Ce qui reste (à confirmer métiers/DPO <= 2026-12-15)
- Un mot sans espace de plus de ~20 caractères, ou un montant énorme (13 chiffres et plus avec espaces insécables) dans une colonne étroite des tableaux de direction, passe à la ligne au milieu du mot : rien n'est perdu, rendu moins lisible.
- Valeur des écarts de l'inventaire : montants jusqu'à 11 chiffres tiennent sur une ligne ; au-delà, la colonne PMP / valeur peut couper.
- **Modèles personnalisés de centres** : stockés en base, non modifiables par migration. Les centres qui ont un modèle propre (inventaire, fiche patient, liste des attestations, bon d'intervention, planning, cahier, rapport) gardent leurs anciennes largeurs et peuvent présenter les coupures de KB-POL1-2 à 4 : à signaler aux administrateurs (action : régénérer ou retoucher leur modèle).
- Confirmation visuelle des montants de la synthèse (KB-POL1-6) toujours ouverte.
