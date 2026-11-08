---
slice: seance-se7a-routes-residuelles
status: characterized
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
- Reste (lot B) : contrôleur(s) et advice, retrait des handlers legacy, parité 15 tests / 17 goldens, écarts approuvés un par un, verrou ArchUnit ; (lot C) RG-SEA, annexe 16, glossaire, §59, bilan des résidus, `built`.

## Questions ouvertes
- Propriétaire du calendrier du centre : DÉCIDÉ par délégation (à confirmer) : reste dans la Séance, résidu R1 (transfert à l'Organisation O4, butoir 2027-03-31).
- KB-SEA-59 et KB-SEA-60 : DÉCIDÉS par délégation (reproduits avec alerte, à confirmer médecins / DPO avant toute mise en production, ADR 0094 section A).
