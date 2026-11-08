---
slice: seance-se7a-routes-residuelles
status: characterized
date: 2026-11-08
sources: [.ddd/migration/slices/seance-se7a-routes-residuelles.md, .ddd/safety-net/known-bugs.md]
---

# Filet SE7a : routes HTTP résiduelles de Séance

## Point d'observation
HTTP (MockMvc, contexte Spring complet, sécurité réelle + `RoleScopeFilter` + `CenterAccessGuard`, H2 `test`), état SQL des tables réelles (`center_holiday`, `center_closure_day`, `volet_medical`, `bons_sortie`). Aucun mock du code caractérisé, aucun seam. Aucun test ne lit l'heure : séances relatives au jour du centre (`<J>`), calendrier en dates fixes (`<D+n>`), borne `before` par défaut évitée à un jour près. Harnais `Se7CharacterizationSupport` (au-dessus de SE6 à SE1). Tests : `backend/platform/src/test/java/com/hemodialyse/backend/characterization/seance/se7/`, goldens : `backend/platform/src/test/resources/characterization/seance/se7/`.

## Scénarios (15 tests, 17 goldens)
| Classe | Tests | Verrouille |
|---|---|---|
| `LeJournalDuJour...` | 3 | contenu (tri par nom, tous statuts, autres jours et autre centre exclus, cumul des bons de sortie du jour par article, KB-SEA-61), paramètres (7 cas, 500), droits (15 profils + anonyme) |
| `LeCalendrierDuCentre...` | 5 | bornes du mois (1er et dernier jour inclus, veille et lendemain exclus, ordre, autre centre), paramètres (10 cas), ajout (doublon 500, nuls, 1900, 2000 caractères, état en base), suppression (inconnu, autre table, autre centre, idempotence), droits par profil sur 5 routes + état en base |
| `LeVoletMedicalDUneSeance...` | 4 | création puis remplacement total (champ absent efface), matrice des 6 statuts, droits (15 profils) et corps, cloisonnement |
| `LesSeancesRecentesDUnPatient...` | 3 | tri, `before` exclusif, `limit`, constantes, séance sans volet, cloisonnement, droits |

## Bugs connus
KB-SEA-57 à 61 (500 au lieu de 400, calendrier sans contrôle, volet médical effaçable et modifiable après signature, constantes de séances non validées exposées, `sortiesArticles` = bons de sortie).

## Exécution
3 exécutions vertes (2 normales, 1 `-Dsurefire.runOrder=random`) puis suite complète `./mvnw -o test -fae` (résultat dans le compte rendu). Aucun `-Dcharacterization.approve=true` : goldens relus et écrits un par un.

## Fichiers protégés (`covered.json`)
`SeanceRestController` (journal, calendrier seulement : le tableau de bord reste couvert par A4/PC1, sans garantie complète), `SeanceStationRestController`, `VoletMedicalRestController`, `VoletMedicalDomainService`, `SeanceApplicationService` (déjà présents), plus `VoletMedical`, `VoletMedicalUseCase`, `VoletMedicalRepositoryPort` et l'adaptateur de persistance du volet médical.

## Limites (NON verrouillé)
- Tableau de bord mensuel (`dashboard`, `details`, `export`) : couvert seulement par A4 et PC1 ; tranches d'âge, répartitions, mois sans séance non vérifiés (SE7b).
- `PUT /{id}/volet-paramedical` : couvert par DM1, non repris.
- Calendrier : aucun lien vérifié avec la création de séance ni le planning (fermeture exceptionnelle et séance : SE1).
- Audit, diffusions et notifications des routes résiduelles : seules les diffusions du volet médical sont observées (aucune). Journal d'audit non caractérisé.
- Concurrence (deux ajouts simultanés du même jour), `limit` maximal, sortie CSV/PDF non concernés ici.
- Mutation testing : aucun outil installé pour la stack, non exécuté. Couverture de branches non mesurée (pas de JaCoCo configuré dans ce lot).
