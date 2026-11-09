---
status: proposed
date: 2026-11-09
scope: slice pilotage-direction-pd6-composition-des-indicateurs (commit 8384a2bd, ADR 0106), refactoring sur place (ADR 0024)
sources: [git show HEAD, HEAD~1 (DirectionIndicatorsQueryService, 3 traducteurs), ADR 0106, fiche PD6, poms, goldens pd6, 13-direction.md, annexe 16, HTML client, glossaire, covered.json, roadmap, state.json]
reviewer: relecteur independant (lecture seule ; un build Maven a la fois)
---

# Revue PD6 : composition des indicateurs de la direction

## Verdict : **pass-with-actions**
0 blocker, 0 major, 3 minor, 3 nit. Fidelite au legacy etablie ligne a ligne ; goldens PD6 verts sur le legacy (rejoues sur HEAD~1) ; aucun `.approved` preexistant modifie ; aucun cycle de modules.

## Controles
| Controle | Resultat |
|---|---|
| Fidelite (point 1) | Ordre : periode validee puis societe (comme avant) ; defauts fin = aujourd'hui, debut = 1er janvier (`Periode.demandee`) ; horloge UTC = defaut JVM fixe par `BackendApplication.java:15` ; `genereLe` UTC. Clinique : masquage seuil 5 et consolides sur bruts restent chez le Dossier medical (KB-DIR-01), non recalcules (`CliniquesParLeDossierMedical`). Stock lu a `periode.fin()` (KB-DIR-07), centre inconnu = `VIDE`, null = 0 ; arrondi demi-superieur par centre, total = somme des valeurs non arrondies puis un arrondi (`StockDuCentre.java`) ; societe sans centre : totaux a zero, stock non lu, cliniques tout de meme demandees ; centres inactifs servis ; alertes centre par centre sur taux masques + observance brute. JSON : enregistrements `IndicateursDeLaDirection` dans l'ordre des anciens (`centerId`, `generatedAt`, `lotsPeremptionProche`, `seuilAnonymat`) ; traducteur du rapport et constat du temps reel identiques aux supprimes (greffeBilanEnCours conserve cote rapport ; le temps reel lit le sous-ensemble `ktV:dansCible`... comme avant). Aucun ecart trouve. |
| Cycle (point 2) | Aucun `pom.xml` hors `pilotage-direction` ne cite pilotage ; aucun import `pilotagedirection` hors module et plateforme ; `contexts-stock` ne depend que de shared-kernel ; dossier-medical (shared-kernel, patient-couverture, planification, stock, seance) sans pilotage. Reacteur Maven construit. `dependency:tree -q` rejoue : sortie vide, inexploitable (preuve par poms). |
| Goldens (point 3) | Un seul commit ajoute les 3 `.approved` (A, 0 M) ; ils sont dans le meme commit que la migration. Rejoues en mode verifier sur worktree jetable de HEAD~1 (legacy) avec le test et les goldens de HEAD : 3 tests, 0 echec. Worktree retire. |
| Hexagonal (point 4) | Domaine sans import hors `java.*` et hors module ; regle ArchUnit ajoutee (seule l'infrastructure lit les autres contextes) ; 2 regles `FrontieresDesContextesTest` ajoutees. `check-arch.ts` : 0 violation. |
| Docs/etat (point 5) | RG-DIR-110 conforme au code (`13-direction.md:220`, annexe 16:175, HTML 13.15 `id="RG-DIR-110"`). **RG-DIR-109 absent du HTML : confirme** (13.14 s'arrete a RG-DIR-108). Glossaire : 3 termes `proposed`. `TableDuPilotageTest` : 10 entrees + `doesNotContainKey`. `covered.json` valide, `seamAllowed` `[]`. `state.json` pd6 `built`, roadmap alignee. |
| Tests (point 7) | Ordre par defaut : module 168 tests (somme des classes), platform 150 (PD1-PD6, DM8, DM9, TableDuPilotageTest 13, Frontieres 47, ReglesDeGestion 7, IndicateursDeLaDirectionIntegrationTest 2, Realtime 5), BUILD SUCCESS. `-Dsurefire.runOrder=random` : BUILD SUCCESS. |
| Cloture (point 8) | `application/direction` : 10 fichiers (AnonymityPolicy, Absences, Account, Capacite, Gmao, ReportPdf, StockGroupes, PasswordPolicy, SocieteAdminAccount, SocieteLetterheadPort), aucun du Pilotage au sens de `TableDuPilotageTest`. |

## Constats
| # | Severite | Constat | Preuve | Correction |
|---|---|---|---|---|
| m1 | minor | Goldens et migration dans un seul commit : le message dit « 3 goldens added before the move » mais l'historique ne le prouve pas. Verifie ici par rejeu sur HEAD~1 (vert), donc sans consequence de fond. | `git show HEAD --name-status` | A l'avenir, committer le filet seul avant le deplacement. |
| m2 | minor | HTML client sans RG-DIR-109 (lot D) alors que 13.15 est ajoutee ; PD6-R4 le dit. | `documentation-fonctionnelle.html` (13.14 = RG-DIR-105 a 108) | Regenerer `docs/client`. |
| m3 | minor | ADR 0106 cite `dependency:tree` comme preuve ; non reproductible ici (sortie vide en `-q`). La preuve reelle est le grep de poms (valide). | ADR 0106 § Preuve | Joindre la sortie du tree ou ne citer que les poms. |
| n1 | nit | Un centre absent de `parCentre` du Dossier medical provoque un NPE (`clinique.ktV()`), comme le legacy ; non caracterise. | `IndicateursDeLaSociete.java` (boucle des centres) | Residuel a noter ou garde explicite. |
| n2 | nit | Nom du contrat `lotsPeremptionProche` vs domaine `lotsProches` : voulu (noms du JSON), a garder dans le glossaire. | `IndicateursDeLaDirection.java` | Aucune. |
| n3 | nit | Residuels PD6-R1..R4 honnetes et completes ; PD6-R2 depend de PD5-R1/R2 (Absences, Capacite) toujours en plateforme. | ADR 0106 § Residus | Aucune. |

## Suites données (2026-11-09)
Revue lecture seule : aucune correction appliquee par le relecteur. Actions recommandees : m2 (regeneration HTML), m3 (reformuler la preuve), m1 (process), n1 (caracterisation du centre absent).
