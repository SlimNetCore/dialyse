---
status: proposed
date: 2026-11-12
sources: inventaire-platform-restant.md (B1/PL8), pl7-filet.md, ADR 0128 (RF3c-J1), ADR 0140 (D3), known-bugs.md (KB-FAC-52..65, KB-PL8-1..7)
---

# PL8 - filet de caracterisation de la synthese mensuelle de facturation

Aucun code de production modifie, seamAllowed = []. Rien commite. `covered.json` inchange : `FacturationSyntheseMensuelleReportService`, `synthese_mensuelle_facturation.jrxml`, `DocumentRestController`, `CaissesDesAgences(ParLAnnuaire)` y sont deja.

## Constat de depart
- La route `POST /api/v1/facturation/synthese/print` est DEJA servie par `contexts/facturation` (`SyntheseControleur`, slice F5). Reste dans platform : `FacturationSyntheseMensuelleReportService` (gabarit Jasper dynamique construit en Java, ~390 l., **SQL sur `factures` / `facture_lignes` dans le gabarit**), `CaissesDesAgences(ParLAnnuaire)`, le gabarit statique `synthese_mensuelle_facturation.jrxml` (voie `/documents/print`, jointures `agence` / `caisse_assurance`) et les gardes/totaux dans `DocumentRestController:165-242`.
- Filet existant (F5) deja riche : `SyntheseMensuelleImprimeeCharacterizationIntegrationTest` (14 tests, nominal, bornes, 2 ans, periode vide, formats, refus 400, profils, Excel nominal, barres, libelles speciaux, 6 caisses, meme code), `SyntheseViaModeleDeDocument...` (9 tests, gabarit statique, profils, centre) + 2 tests d'integration platform. Le PL8 ne les duplique pas.

## Ajout : `SyntheseMensuellePl8CharacterizationIntegrationTest` (10 tests, 20 goldens approuves un a un depuis les .received)
Meme harnais (`PilotageFacturationSupport`, H2, 1 contexte Spring). Goldens : `backend/platform/src/test/resources/characterization/facturation/pilotage/synthese-pl8/`. PDF compare sur le texte extrait (PDFBox) ; Excel sur le contenu des cellules.

| Famille | Golden(s) |
|---|---|
| Montants enormes (800 milliards), TTC proportionnel non terminant, arrondi a la paire (0,545), HT 0, avoir, centime ; PDF et Excel | `montants_enormes_et_arrondis(+_excel)` |
| Textes longs (255 car.), caisse longue, unicode (oe, u, euro, etoile, arabe, chinois), `]]>`, `$P{}`, `$P!{}` | `textes_longs_et_unicode` |
| Chaines de payeurs atypiques : code en minuscules, avec espaces, blanc, caisse d'un autre centre, agence d'un autre centre, agence inconnue | `chaines_de_payeurs_atypiques` |
| Deux agences d'une caisse, caisse sans facture | `agences_multiples_d_une_caisse` |
| Mois sans seance, seance non facturee rattachee, borne sur la date de facturation et non sur `created_at` (fuseau) | `sans_seance_bornes_et_creation` |
| 60 forfaits : 4 pages, en-tetes repetes, tri, regroupement, casse | `soixante_forfaits` |
| Excel : periode vide ; 6 caisses + sans agence | `excel_periode_sans_facture`, `excel_six_caisses` |
| Garde : centre inconnu, autre societe, secretaire autre centre, admin d'un autre centre | `centre_inconnu_`, `centre_autre_societe_`, `secretaire_autre_centre_`, `admin_d_un_centre_vide_nouveau_code` |
| Periodes extremes et formes du corps (0001..9999, +10000, date-heure, entiers, majuscules, sans tirets, vide, blanc, tableau) | `periodes_extremes_et_formes_du_corps` |
| Types de contenu, methodes, Accept, anonyme | `types_de_contenu_et_methodes` |
| Audit (200/400/403 journalises `FACTURATION_CREATION`) | `audit` |

## Execution
3 passes (la 3e `-Dsurefire.runOrder=random`) avec F5 (impression + voie documents) + PL8 : 33 tests, 0 echec, 0 flaky. PL8 ~3 s apres le demarrage ; contexte Spring ~30 s ; build ~60 s par passe. 1 contexte Spring par JVM. Commande : `locked.ts maven -- <abs>\backend\mvnw.cmd -f backend/pom.xml -o -pl platform -am test -Dtest=SyntheseMensuellePl8...,SyntheseMensuelleImprimee...,SyntheseViaModeleDeDocument... -Dsurefire.failIfNoSpecifiedTests=false -l <log>`.

## Prerequis RF3c-J1 : PARTIELLEMENT satisfait
Existe : `patientcouverture.publishedlanguage.PayeursDuCentre` (`caisses`, `agences`, `centresPayeurs`, `chaines(centre)`, `detail`), consomme par l'ACL platform `CaissesDesAgencesParLAnnuaire` (code de caisse par agence, bornes au centre) et par `AgenceDuPayeurParLAnnuaire` (Facturation). Donc la CHAINE agence -> caisse du rendu dynamique ne joint plus les tables des payeurs (RF3b, ADR 0127).
Manque pour retirer RF3c-J1 (ADR 0128:46, butoir 2027-03-31) :
1. Les lecteurs de la Facturation joignent toujours `agence` / `caisse_assurance` : `LectureDeLaSyntheseJdbc:53-54,66-67`, tableau de bord, `SyntheseFinanciereDesCentresJdbc` (KB-RF3-07) ; il n'y a pas de lecture publiee groupee « chaine des payeurs d'une facture (agence figee -> caisse) » avec tri/filtre/pagination reproduits sous PG16.
2. Le tableau forfait x caisse n'est PAS dans `SyntheseMensuelle` : le gabarit lit lui-meme `factures` / `facture_lignes` (SQL dans le gabarit), ce que ADR 0140 D3 interdit (« le contexte fournit ses donnees »). Il faut publier des lignes (forfait, caisse, HT, TTC part) dans le langage publie, avant de deplacer le rendu.
3. Le gabarit statique garde les jointures `agence` / `caisse_assurance` et le classement CNAS/CASNOS/CAMSSP par prefixe (KB-RF4-02).
4. Le tri du tableau par libelle depend du SGBD (H2 verrouille ici ; PG16 par contrat existant a completer).

## Bugs connus (non corriges) : KB-PL8-1..7
1 texte tronque (Montant TVA, forfait long) ; 2 colonne « Inconnu » nommee d'apres une caisse au code blanc, casse/espaces ; 3 caracteres hors police perdus (KB-PL6-4/PL7-3) ; 4 Excel = grille Jasper brute, TTC non arrondi, periode vide = cellule vide ; 5 avoirs, HT 0, seances comptees quel que soit le statut ; 6 PUT/GET/text-plain/`[]`/centre sans tirets en 500, dates entieres acceptees ; 7 journalisation generique `FACTURATION_CREATION`, tri dependant du SGBD.

## Limites
- HTML non couvert au-dela de F5 (type + debut) ; modele personnalise par centre (`modele_document`) hors perimetre.
- Horloge non injectable (aucun horodatage dans cette synthese, donc sans effet). Fuseau JVM non modifiable : seule l'independance a `created_at` est verrouillee.
- Chemin 500 « Erreur impression synthese » (KB-FAC-59) non declenchable sans seam ; fichier temporaire verifie supprime sur le chemin nominal par F5.
- Positionnement PDF verrouille dans l'ordre d'extraction PDFBox. PG16 non execute (H2).
- Le refus `ROLE_SCOPE` non journalise est deduit du decompte (4 requetes, 3 lignes de journal).

## Decisions a demander
1. Corriger ou reproduire : troncature du Montant TVA et des forfaits longs (KB-PL8-1) ; colonne « Inconnu » ambigue (KB-PL8-2).
2. Excel de la synthese : conserver la grille Jasper brute ou produire un classeur tabulaire (valeurs arrondies, une ligne par forfait) ? (KB-PL8-4)
3. Avoirs / HT 0 : le tableau doit-il etre coherent avec « Montant TTC » enregistre ? (KB-PL8-5)
4. 405/415/400 propres pour les formes de requete illisibles (comme PL3-PL7) ? (KB-PL8-6)
5. Action d'audit metier `SYNTHESE_FACTURATION_IMPRESSION` (periode, format) ? (KB-PL8-7)
6. Prerequis : publier les lignes forfait x caisse dans `SyntheseMensuelle` (et la lecture groupee de la chaine des payeurs d'une facture) AVANT PL8, ou accepter de laisser RF3c-J1 ouvert jusqu'au butoir 2027-03-31 ?
