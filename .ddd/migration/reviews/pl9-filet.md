---
status: proposed
date: 2026-11-12
sources: inventaire-platform-restant.md (B1/PL9), pl8-filet.md, pl7-filet.md, ADR 0142/0145/0146/0147, known-bugs.md (KB-DIR-*, KB-PL9-1..8)
---

# PL9 - filet de caracterisation du rapport PDF Direction

Aucun code de production modifie, seamAllowed = []. Rien commite. `covered.json` inchange : `DirectionReportPdfService`, `DirectionDashboardRestController`, `DirectionSnapshotRestController`, `SocieteLetterheadPort`/`Adapter`, `RapportDUnInstantane` y sont deja.

## Constat de depart
- 2 entrees servies par platform : `GET /api/v1/direction/report?from&to` (rapport en direct) et `GET /api/v1/direction/snapshots/{mois}/report` (instantane fige). Le MODELE du rapport est deja dans `contexts/pilotage-direction` (`RapportDeSyntheseDeLaDirection`, PD4, ADR 0102) ; reste en platform la MISE EN FORME (HTML + openhtmltopdf, `DirectionReportPdfService` 415 l.), l'en-tete de societe (`SocieteLetterheadPort`) et la garde (`DirectionAccessGuard`).
- Filet existant PD4 (4 tests) : nominal en direct (toutes sections, effectifs masques), rapport mensuel (dont sans repartition, totaux bruts), en-tete sans identite, bornes (debut>fin, 5 ans+1j, date illisible), 11 profils + anonyme. + `DirectionReportIntegrationTest` (3). Non duplique.

## Ajout : `LeRapportDeLaDirectionPl9CharacterizationIntegrationTest` (9 tests, 9 goldens approuves un a un depuis les .received)
Harnais PD (`PilotageDirectionCharacterizationSupport`, H2, horloge du Pilotage figee). Goldens : `backend/platform/src/test/resources/characterization/pilotagedirection/pl9/rapport/`. PDF compare sur le texte extrait (PDFBox).

| Famille | Golden |
|---|---|
| 20 formes de periode : sans parametre (fin = aujourd'hui a Alger), fin seule, debut seul, debut=fin, 5 ans pile / +1 j, fin future, 1900, 2099 (debut par defaut = 1er janvier de l'annee de la FIN), vide, repete, 30 fevrier, date-heure, sans tirets, bissextile, annee 0001 | `periodes_par_defaut_et_formes` |
| Garde de societe : 16 jetons (cumul DIRECTION+ADMIN/SUPERADMIN/MEDECIN, jeton avec centre, autre societe, inconnu, inactif, detache, societe inactive/blanche/illisible, role minuscule/sans prefixe), societe sans centre, cloisonnement entre societes | `garde_de_societe_et_roles` |
| Montants enormes (800 milliards), avoir, centime, arrondi (2,50 / 3,50 / 0,50 / 1,50), stock 1,2e16, textes longs, HTML/`]]>`/`$P{}`/unicode dans noms de centre, raison sociale, adresse, pied de page, caisse | `montants_enormes_arrondis_et_textes` |
| 32 centres x 36 mois : pagination (9 pages), en-tetes repetes, matrice des caisses tronquee | `trente_deux_centres_trente_six_mois` |
| En-tete : mentions legales completes (email, web, NIS, RC), logo PNG valide, faux JPEG, octets quelconques, 2 octets, raison sociale et pied blancs | `en_tete_complet_logo_et_mentions` |
| Instantanes : cloisonnement entre societes, mois invalides/courant/futur/vide, 7 contenus figes atypiques (tout absent, `{}`, `[]`, `"x"`, `null`, totaux sans centres, 25 alertes hostiles et valeurs enormes) | `rapport_mensuel_atypique` |
| Fuseau : horodatage imprime vs horloge figee 23:30 UTC / 00:30 Alger | `horodatages_et_fuseau` |
| Audit (rapport non journalise ; capture journalisee) | `audit` |
| Methodes, chemins voisins, Accept, HEAD/OPTIONS, corps sur GET | `methodes_chemins_et_accept` |

## Execution
3 passes (la 3e `-Dsurefire.runOrder=random`) avec PD4 + `DirectionReportIntegrationTest` : 16 tests, 0 echec, 0 flaky. PL9 ~4 s apres le demarrage ; contexte Spring ~30 s (1 contexte par classe d'heritage PD, 2 au total avec `DirectionReportIntegrationTest`) ; build ~60 s par passe. Commande : `locked.ts maven -- <abs>\backend\mvnw.cmd -f backend/pom.xml -o -pl platform -am test -Dtest=LeRapportDeLaDirectionPl9...,LeRapportImprimeDeLaDirection...,DirectionReportIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false -l <log>`.

## Bugs connus (non corriges) : KB-PL9-1..8
1 horodatage imprime en UTC (pas Alger) et heure reelle pour un instantane ; 2 matrice des caisses clippee au-dela de ~22 centres (« Total societe » perdu) ; 3 instantane en JSON valide mais vide = 200 vide au lieu de 422 ; 4 routes voisines inconnues en 500, `Accept` ignore ; 5 message technique « Invalid UUID string » dans un 400 de jeton, cumuls de roles ; 6 caracteres hors police perdus, textes longs non coupes (reproduction DOC2) ; 7 montants en double / arrondi ½ haut, sans decimales ; 8 consultation non journalisee.

## Limites
- Le PDF est observe par son texte : mise en page (positions, couleurs, barres) non verrouillee ; coupures de lignes dependantes des polices du poste (ex. « 2026-01- 01 »), comme DM7/DM8/PL6-8.
- Horodatage d'instantane = horloge reelle (masque `<HEURE_REELLE_DE_LA_JVM>`) ; fuseau JVM non modifiable : seule la difference UTC/Alger du rapport en direct est verrouillee.
- Sections de repartition par sexe/age/caisse/anemie d'un instantane non couvertes au-dela de PD4 (forme du bloc `breakdown` non reconstruite ici). Rapport avec `breakdown` partiel non couvert.
- PG16 non execute (H2). Les requetes SQL des lectures Pilotage sont deja couvertes par PD1/PD6.
- Chemin 500 « echec du rendu PDF » non declenchable sans seam.

## Decisions a demander
1. Horodatage du rapport : heure d'Alger (coherente avec la periode) ou UTC ? Horodatage d'un instantane = instant de capture (a conserver) mais via l'horloge injectee ? (KB-PL9-1)
2. Matrice des caisses : paginer/transposer au-dela de ~20 centres, ou limite assumee ? (KB-PL9-2)
3. Instantane vide/illisible : 422 `INSTANTANE_ILLISIBLE` aussi pour un JSON valide sans contenu ? (KB-PL9-3)
4. 404 propre pour les routes voisines, 406 pour `Accept` incompatible ? (KB-PL9-4)
5. Cumul DIRECTION + ADMIN/SUPERADMIN : voulu ? (KB-PL9-5)
6. Montants du rapport : BigDecimal, arrondi a la paire ou ½ haut, decimales affichees ? (KB-PL9-7)
7. Journaliser la consultation du rapport (`RAPPORT_DIRECTION_IMPRESSION`) comme PL5-PL8 ? (KB-PL9-8)
8. Police et textes longs : meme decision que DOC2 (KB-PL9-6).
