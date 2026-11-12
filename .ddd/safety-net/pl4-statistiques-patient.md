# Filet PL4 (statistiques du patient : paramédical + médical, export)

Date 2026-11-12. Niveau : risque faible à moyen, HTTP intégré (socle DM8 : `@SpringBootTest` MOCK + MockMvc, H2, profil `characterization`). Aucun code de production modifié, aucun seam (`seamAllowed` = []).

## Constat de départ (grep)
| Route (`PatientStatsRestController`) | Filet existant | Manque |
|---|---|---|
| `GET stats/medical`, `POST stats/medical/search` | DM8 `stats/*` (agrégats, bornes, vide, recherche, droits 14 profils, cloisonnement, audit), DM1 garde | rien |
| `GET stats/export` (csv, pdf), `POST stats/export/search` | DM8 (CSV 6 colonnes, PDF texte normalisé + `%PDF`, formats, droits ADMIN seul, centre B, audit), SE7e (PDF paramédical) | rien |
| `GET stats/paramedical` | SE7e (comptes, statuts réalisés, bornes, volet d'un autre centre, PDF), DM1 (garde, MEDECIN), `PatientStatsRestControllerTest` (unitaire) | formes de tension, poids sec selon `to`, patient vide/inconnu, droits par profil, entrées mal formées, audit |
| `POST stats/paramedical/search` | DM1 garde (centre B) seulement | tout le reste |

## Ajout
`backend/platform/src/test/java/com/hemodialyse/backend/characterization/dossiermedical/pl4/LesStatistiquesParamedicalesDuPatientCharacterizationIntegrationTest.java` (6 scénarios). Goldens (relus un par un dans le `.received`, puis renommés en `.approved`, jamais `-Dcharacterization.approve=true`) dans `src/test/resources/characterization/dossiermedical/dm8/pl4_paramedical/` (le répertoire `dm8` vient du `theme()` figé du socle DM8).

| Scénario | Golden |
|---|---|
| tensions texte (130/80, 120-70, 140/90 mmHg ; 9/5, 1000/50, bizarre, absente = null), moyennes ignorant les valeurs absentes, UF déduite de la perte de poids, séance réalisée sans volet, parité GET/POST, `type`/`format` ignorés, Cache-Control, aucune écriture | `tensions_et_agregats` |
| périodes : une séance, poids sec cible selon `to` (aucun / 1re / 2e prescription, borne incluse), période vide, `from` > `to` 400, recherche un jour | `periodes` |
| patient sans séance, inconnu, d'un autre centre, centre B voit les siens | `sans_donnees` |
| 14 entrées mal formées GET/POST | `entrees_mal_formees` |
| 14 profils + anonyme sur GET et POST, garde de centre (B visé, centre inconnu, sans centre, SUPERADMIN+ADMIN) | `droits_et_garde_de_centre` |
| audit : GET non journalisé, POST journalisé `PATIENTS_CREATION` (200 et 403) | `audit` |

## Bugs connus
KB-PL4-1 à KB-PL4-5 dans `known-bugs.md` (500 sur entrées illisibles, patient inconnu = 200 de zéros, tension illisible = null, POST de lecture audité comme création, plage inversée testée avant la garde de centre / anonyme 403). Aucun corrigé.

## Limites
- Le poids sec sans `to` lit `LocalDate.now()` : verrouillé seulement parce que les prescriptions sont en janvier 2026 (stable tant que la date du jour reste postérieure). Horloge non injectable.
- Moyennes à 12 décimales H2 (ex. 69.166666666667) : précision propre au SGBD (cf. KB-SEA-87), à comparer avec tolérance lors de la parité sur un autre SGBD.
- Export : pas de PDF binaire complet (DM8 : texte normalisé, horodatage masqué). Pas de test du PDF contre `identity.decorateHtml` pour un centre sans nom.
- Ordre à date égale non verrouillé (fixtures à dates distinctes ; cf. KB-SEA-86).

## Exécution
5 classes (nouvelle + DM8 stats + SE7e statistiques + DM1 garde + test unitaire du contrôleur, 28 tests) x 3 passes avec `-Dsurefire.runOrder=random` : 3 x vert (~53 s par passe, démarrage Spring ~28 s). Contextes Spring démarrés : 1 par passe pour la caractérisation (profil partagé). Journaux : scratchpad `pass1..3.log`. `check-arch` : 0 violation.

## Fichiers de production inscrits (covered.json)
Déjà présents, aucun ajout : `PatientStatsRestController.java`, `PatientStatsQueryService.java`, `PatientStatsSearchRequest.java`.

## Questions ouvertes
- Reproduire ou corriger KB-PL4-1 (500 vs 400, comme KB-PL3-2 corrigé) et KB-PL4-2 (404 patient inconnu) lors de la migration vers `contexts/dossier-medical` ?
- KB-PL4-4 : un POST de lecture doit-il rester audité `PATIENTS_CREATION` ?
