---
slice: seance-se7a-routes-residuelles
status: built
date: 2026-11-08
order: 7.1
context: seance
parent: seance-se7-lectures-publiees
sources: [.ddd/safety-net/seance-se7-lectures-publiees.md, .ddd/safety-net/known-bugs.md (KB-SEA-57 à 61)]
---

# SE7a : routes HTTP résiduelles de Séance (journal du jour, calendrier du centre, volet médical, séances récentes)

Routes encore servies par la plateforme (`SeanceRestController`, `SeanceStationRestController`, `VoletMedicalRestController`) et non par `contexts/seance`.

| Route | Rôles | Où | Filet avant SE7a | Filet SE7a |
|---|---|---|---|---|
| `GET /api/v1/seances/journal` | ADMIN INFIRMIER MEDECIN SECRETAIRE | SQL direct `seances`+`patients`, langage publié Stock | garde de centre (DM1) | contenu, tri, paramètres, droits |
| `GET /api/v1/seances/calendar` | idem | SQL `center_holiday`, `center_closure_day` | garde de centre (DM1) | bornes du mois, paramètres, droits |
| `POST /calendar/holiday`, `POST /calendar/closure` | ADMIN | idem | garde de centre (DM1) | ajout, doublon, nuls, droits |
| `DELETE /calendar/holiday/{id}`, `.../closure/{id}` | ADMIN | idem | garde de centre (DM1) | suppression, autre centre, droits |
| `PUT /{id}/volet-medical` | ADMIN MEDECIN | `VoletMedicalDomainService` | garde de centre (DM1), séance facturée (Facturation) | création/remplacement, statuts, droits, corps |
| `GET /patient/{id}/recentes` | ADMIN INFIRMIER MEDECIN | `SeanceUseCase.recentByPatient` | `SeanceStationIntegrationTest` (assertions) | tri, bornes, cloisonnement, droits |
| `PUT /{id}/volet-paramedical` | ADMIN INFIRMIER SECRETAIRE | `VoletParamedicalDomainService` | DM1 (complet) | non repris |

Cible : ces routes passent dans `contexts/seance` (journal et récentes en lectures, calendrier à confier à Organisation ou Planification : question ouverte, la table `center_holiday` n'est pas à Séance), les 5 décisions KB-SEA-57 à 61 sont à trancher par ADR.

## Avancement
### Lot A (2026-11-08) : ADR 0094, domaine, application, ports, ACL, adapters (aucun trafic changé)
- ADR : `.ddd/architecture/adr/0094-seance-se7a-…md` (accepted, à confirmer métiers / DPO, `review-by: 2026-12-15`, risques cliniques en tête, résidus R1 à R5 datés).
- Domaine : `JourDuCalendrier`, `NatureDeJour`, `MoisDuCalendrier`, `JourDejaAuCalendrier`, `CalendrierDuCentre` ; `VoletMedical`, `ObservationsMedicales`, `VoletsMedicaux`, `Seance.exigerUnVoletMedical` ; `NombreDeSeancesRecentes`, `Seances.dernieres`.
- Application : `ConsulterLeJournalDuJour`, `ConsulterLeCalendrier`, `AjouterUnJourAuCalendrier`, `RetirerUnJourDuCalendrier`, `RenseignerLeVoletMedical`, `ConsulterLesSeancesRecentes` (ports `PatientsDuJour`, `SortiesDuJour`).
- Adapters : `CalendrierDuCentreJdbc`, `VoletsMedicauxJdbc`, `SeancesJdbc.dernieres`, ACL `PatientsDuJourJdbc` (`legacypatient`), `SortiesDuJourDuStock` ; câblage dans `SeanceConfiguration` (cas d'usage non exposés en HTTP).
- Contrats (double en mémoire, H2, PostgreSQL 16 jetable via `backend/scripts/seance-se7a-pg16-rejeu.sh`) : calendrier, volets médicaux, séances (dernières).

### Lot B (2026-11-08) : contrôleur, routage par retrait des handlers legacy, parité
- `RoutesResiduellesControleur` + `ErreursDeSeanceAdvice` étendu (403 avant 400, 409 `JOUR_DEJA_AU_CALENDRIER`) ; handlers legacy retirés (journal, calendrier x5, `SeanceStationRestController`, `VoletMedicalRestController`, `recentByPatient`) ; seam inscrit puis fermé ; verrou ArchUnit `seul_contexts_seance_sert_les_routes_residuelles`.
- Parité : 15 tests, 17 goldens : 9 identiques au legacy, 8 écarts approuvés (500 vers 400, doublon 409, bornes, libellé de statut sans nom de classe), archives R100 sous `se7/_ecarts-legacy/`. Golden d'audit `se7/audit` ajouté.

### Lot C (2026-11-08) : documentation et clôture
- RG-SEA-058 (`07-seances.md`), annexe 16 (`RoutesResiduellesControleur`), glossaire de la Séance (5 termes, `proposed`), §59 de `verifications-production.md` (7 requêtes SELECT seule, requête 2 corrigée : profils de l'audit, rejouées sur PostgreSQL 16 jetable).

## Plan de bascule (refactoring sur place, ADR 0024 : ni façade, ni drapeau, ni paliers)
- La bascule EST le déploiement du commit : les routes changent de contrôleur, mêmes URL, mêmes tables. Recette avant déploiement : §59 (a à f), requêtes 1 à 7 avant et après.
- Métriques : taux de 4xx et 5xx sur les 6 routes (les 500 sur paramètre mal formé deviennent des 400), 409 `JOUR_DEJA_AU_CALENDRIER`, requêtes 1, 2 et 4 du §59 chaque semaine.
- Rollback : revenir au commit précédent ; aucune donnée à restaurer, aucune migration.

## Écarts de parité
8 goldens approuvés un par un (voir ADR 0094, section Lot B). Limite assumée : le golden d'audit n'a pas été rejoué contre le legacy retiré.

## Bilan nominatif des résidus (revue ≤ 2026-12-15 ET butoir propre pour CHACUN)
| # | Résidu | Revue | Butoir |
|---|---|---|---|
| R1 | Transfert du calendrier du centre de la Séance à l'Organisation (O4) | 2026-12-15 | 2027-03-31 |
| R2 | ACL `PatientsDuJourJdbc` lit `patients` : langage publié du Patient | 2026-12-15 | 2027-03-31 |
| R3 | Constantes des séances récentes lues via l'ACL du legacy (`VoletsDeLaSeance.paramedical`) : à remplacer avec le volet paramédical (DM1) | 2026-12-15 | 2027-06-30 |
| R4 | Arbitrage médecins / DPO de KB-SEA-59 et KB-SEA-60 | 2026-12-15 | avant toute mise en production |
| R5 | Bornes du calendrier, cohérence avec les séances posées, 404 sur retrait inexistant (KB-SEA-58) | 2026-12-15 | 2027-03-31 |
| R6 | Audit des routes résiduelles : golden `se7/audit` non rejoué contre le legacy retiré (filtre générique non modifié) ; à lever par une relecture du journal d'audit en production après déploiement | 2026-12-15 | 2027-01-31 |

## Questions ouvertes
- Propriétaire du calendrier du centre : DÉCIDÉ par délégation (à confirmer) : reste dans la Séance, résidu R1 (transfert à l'Organisation O4, butoir 2027-03-31).
- KB-SEA-59 et KB-SEA-60 : DÉCIDÉS par délégation (reproduits avec alerte, à confirmer médecins / DPO avant toute mise en production, ADR 0094 section A).
