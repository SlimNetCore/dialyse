---
status: characterized
date: 2026-11-06
sources: [.ddd/safety-net/dossier-medical-inventaire.md, ADR 0007, 0065, 0072, GEL.md, known-bugs.md KB-MED-01 à 14]
---

# Filet DM1 : constantes (legacy, refactoring sur place)

## Périmètre réel
Le legacy n'a qu'**une** route « constantes » : `GET /api/v1/patients/{id}/constantes` (lecture seule, vue longitudinale de `volet_paramedical` par séance). Il n'existe ni création, ni modification, ni suppression de constante. Écrivain unique : `PUT /api/v1/seances/{id}/volet-paramedical` (propriété Séance SE2) ; effacement : `DELETE /api/v1/seances/{id}`. FHIR et KDIGO ne lisent pas les constantes. Inventaire complet : `.ddd/safety-net/dossier-medical-inventaire.md`.

## Point d'observation
HTTP (MockMvc, contexte Spring complet, sécurité réelle + `RoleScopeFilter`, H2 `test`), état SQL de `volet_paramedical`, audit (`AuditRequestFilter`), diffusions (canal du courtier), notifications durables. Aucun mock, aucun seam, aucun code de production modifié. Harnais : `characterization/dossiermedical/dm1/ConstantesCharacterizationSupport` (au-dessus des harnais Planification : dates nommées `<J-4>`, garde de minuit du fuseau du centre, onze profils). Données fictives uniquement (« Patient PAT_A », valeurs inventées).

## Scénarios (26 tests, dont 4 historiques `dossiermedical/constantes`)
Lecture (`LectureDesConstantesDuPatient...`, 9) : liste tous statuts de séance et valeurs brutes (tri, égalité de date, absence d'identité du patient) ; pagination page par page ; bornes de pagination ; patient inconnu / autre centre ; volet ou séance d'un autre centre ignorés ; 11 profils + rôle personnalisé + anonyme + garde de centre (7 cas) ; aucune trace d'audit, diffusion ni notification ; lecture fraîche (pas de cache) ; autres lecteurs (stats paramédicales, détail de séance) y compris inter-centres.
Écriture (`EnregistrementDesConstantesDeSeance...`, 13) : première saisie puis relecture ; seconde saisie (remplacement total) ; 22 formes de tension ; poids (bornes, arrondi) pour avant et après ; durée, débit, UF, longueurs ; 5 statuts de séance ; erreurs (séance inconnue, corps invalide) ; droits par profil ; centre du corps non confronté à la session ; audit + notifications + diffusions ; concurrence de deux premières saisies (invariants seulement) ; suppression de la séance ; lecteurs après saisie.
Goldens : `src/test/resources/characterization/dossiermedical/dm1/{lecture,ecriture}/<scénario>/*.approved.json` (35).

## Déterminisme
Dates par alias nommés, jamais de date réelle ni de jour du mois ; `created_at` de fixture fixe ; `updatedAt` normalisé et contrôlé « récent » par assertion ; date des statistiques ramenée au jour de la JVM (KB-MED-14) ; corps JSON relus en arbres à clés triées (une `Map` de réponse n'a pas d'ordre stable : flakiness trouvée et corrigée pendant le filet). Garde de minuit héritée. Stabilité : 3 exécutions vertes (1 ordre par défaut, 2 ordres aléatoires), 26 tests.

## Seams
Aucun requis. `seamAllowed` reste vide.

## Limites (NON verrouillé)
- H2, pas PostgreSQL : précision `numeric(38,2)` et messages d'erreur SGBD (non verrouillés, masqués).
- Cahier de dialyse (état PDF/jrxml), récap `SeanceStationRestController`, export de statistiques (`stats/export`) et recherche (`stats/*/search`) : non observés ici.
- Règles de signature, validation et facturation de la séance (SE2, SE6) : seule la non-modification d'une séance FACTUREE est verrouillée.
- Concurrence : invariants seulement (un seul volet, statuts dans {200, 409, 500}).
- Rôles personnalisés réels (table `roles`) : seul un rôle inconnu du serveur est simulé.
- Pas de couverture de branches ni de mutation mesurées (non exécutées dans cette session).

## Fichiers legacy ajoutés à `covered.json`
`ConstantesRestController`, `ConstantesQueryService`, `ConstanteSeanceResponse`, `VoletParamedicalRestController`, `UpsertVoletParamedicalRequest`, `VoletParamedicalRepositoryAdapter`, `VoletParamedicalJpaEntity`, `VoletParamedicalJpaRepository`, `TensionArterielle`, `Poids` (`VoletParamedicalDomainService` y était déjà).

## Bugs connus
KB-MED-01 à KB-MED-14 (`.ddd/safety-net/known-bugs.md`).

## Questions de décision pour l'utilisateur
Droits (KB-MED-08), garde de centre sur écriture et lectures (KB-MED-01, 02), audit des lectures (KB-MED-04), identité dans les diffusions (KB-MED-05), bornes (KB-MED-09, 10), propriété du `PUT volet-paramedical` entre DM1 et SE2.
