---
status: proposed
date: 2026-11-12
sources: inventaire-platform-restant.md (PL5), doc1-realisation.md, pl4-realisation.md, known-bugs.md (KB-PL5-1..6)
---

# PL5 - filet de caracterisation du cahier de dialyse imprime

Aucun code de production modifie, seamAllowed = []. Rien commite.

## Niveau et point d'observation
`GET /api/v1/cahier-dialyse/{patientId}/imprimer` (MockMvc, contexte Spring complet, securite reelle, H2). PDF decode en texte (PDFBox), date d'edition masquee ; jamais les octets. Les formats Excel/HTML (choisis par le modele de document du centre) restent couverts par `CahierDialyseRestControllerTest` (unitaire), non au niveau HTTP.

## Test ajoute (8 scenarios, 8 goldens approuves un a un)
`backend/platform/src/test/java/com/hemodialyse/backend/characterization/seance/pl5/LeCahierDeDialyseHttpPl5CharacterizationIntegrationTest.java` ; goldens sous `backend/platform/src/test/resources/characterization/seance/pl5/cahier/`.

| Scenario | Golden |
|---|---|
| Cahier complet : identite, volets, consommables valorises, prescription en vigueur, dossier ; meme texte pour une secretaire | `complet` |
| Champs vides/blancs/zeros/textes longs/caracteres speciaux | `volets_et_dossier` |
| Consommables (regroupement, tri, prix absent, ENTREE ignoree), forfait absent, prescription (egalite de jour, posterieure, dose nulle, article absent) | `consommables_prescription_forfait_absent` |
| Etancheite inter-centres (volets, dossier, prescription, stock du centre B rattaches a une seance A) | `etancheite_des_centres` |
| Droits : 11 profils + 4 cumuls + anonyme | `droits_par_profil` |
| Garde de centre : centerId absent/identique/etranger/inconnu/illisible/vide, patient d'un autre centre, ADMIN sans centre, SUPERADMIN | `garde_de_centre` |
| Parametres : patientId/from/to illisibles, bornes, from>to, fuseaux (decalage en heures), POST | `parametres` |
| Audit : aucune ecriture, journal vide | `audit` |

Complete (sans le dupliquer) le filet SE7e `LeCahierDeDialyseImprimeCharacterizationIntegrationTest` (statuts, bornes, tri, surcharge de forfait, CAHIER_VIDE/PERIODE_INVALIDE/PATIENT_INTROUVABLE) et les tests existants du service/controleur.

## Execution
3 passes (dont `-Dsurefire.runOrder=random`) avec le test SE7e voisin : 11 tests, 0 echec, 0 flaky. Duree ~40 s la classe (un seul contexte Spring, demarrage ~30 s), ~57 s le build. 1 contexte Spring. Commande : `locked.ts maven -- backend/mvnw.cmd -o -pl platform -am test -Dtest=...` (mvn/mvnd absents du PATH). Le decalage de fuseau est arrondi a l'heure (l'heure de l'appel n'est pas figee, aucune horloge injectable).

## Fichiers legacy proteges (covered.json, deja inscrits, aucun ajout)
`CahierDialyseRestController.java`, `CahierDialyseReportService.java`, `reports/cahier_dialyse.jrxml`, `ModeleDocumentPrinter.java`, `CenterAccessGuard.java`.

## Bugs connus (KB-PL5-1..6, non corriges)
1 entrees illisibles et POST = 500 ; 2 ligne d'identite tronquee ; 3 impression non journalisee ; 4 matrice des roles (secretaire lit le medical) ; 5 consommables : seules les SORTIE comptent ; 6 defauts d'affichage mineurs et 422 au lieu de 404.

## Limites
- Excel/HTML au niveau HTTP non couverts (modele personnalise par centre ; unitaire seulement).
- Pagination d'une seance a tres nombreux consommables (debordement de page) non couverte.
- Logo, papier en-tete societe/centre (`SOCIETE_*`, `CENTRE_*`) : seul le nom du centre est observe (couvert par DOC1 pour le papier en-tete).
- Anonyme = 403 (et non 401) : transverse, cf. KB-GMAO-19.

## Decisions a demander a l'utilisateur
1. KB-PL5-1 : corriger (400 `BAD_REQUEST`, 405 pour un mauvais verbe, comme PL3/PL4) ou reproduire ? Recommande : corriger.
2. KB-PL5-3 : journaliser l'impression du cahier (action de lecture dediee, comme `STATISTIQUES_PATIENT_CONSULTATION`) ?
3. KB-PL5-4 : le secretaire doit-il imprimer le volet medical (hepatites, conclusions) ? DIRECTION/SUPERADMIN refuses : a conserver ?
4. KB-PL5-5 : les retours au stock (ENTREE/AJUSTEMENT) doivent-ils minorer la valorisation du cahier ?
5. KB-PL5-2 et 6 : corriger l'affichage pendant la migration (troncature identite, point decimal, "DA" orphelin) ou reproduire ?
6. KB-PL5-6(e) : PATIENT_INTROUVABLE en 404 (comme PL4) au lieu de 422 ?
