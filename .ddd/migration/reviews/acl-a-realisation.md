# ACL (a) Planification : réalisation (2026-11-15, ADR 0190)

Mesure avant/après (fichiers de production de `platform/.../infrastructure/acl/*`) : absences 2, dossiermedical 1, facturation 4, gmao 2, organisation 4, patientcouverture 4, **planification 4 -> 0**, reprise 1, seance 1, stock 4 ; total 31 -> 27, sous-dossiers 10 -> 9.

| ACL | Cycle Maven ? | Destination |
|---|---|---|
| CachesDesPatientsLegacy | non | planification/infrastructure/acl/patientcouverture |
| RisquesInfectieuxParLeDossierMedical | non (dossier-medical -> planification) | dossier-medical/infrastructure/acl/planification |
| AbordsVasculairesParLeDossierMedical | non | idem |
| SeancesRealiseesAvecLeurPlaceParLaSeance + test | non (seance -> planification) | seance/infrastructure/acl/planification |

Câblage : `@Bean` dans PlanificationConfiguration, IndicateursEtStatistiquesConfiguration, SeanceConfiguration. Garde repointée : TableDesSeancesTest (chemin du traducteur). Aucune garde datée à solder (ces fichiers n'ont pas de SQL ni d'entrée F-R/M-R) ; aucune date reportée.

Lignes à retirer de `.ddd/safety-net/covered.json` (non modifié par moi) : 60 (CachesDesPatientsLegacy), 74 (RisquesInfectieuxParLeDossierMedical), 75 (AbordsVasculairesParLeDossierMedical), 86 (SeancesRealiseesAvecLeurPlaceParLaSeance), chemins `backend/platform/.../infrastructure/acl/planification/*`.

Vérifications : voir le rapport de l'appelant (passes ciblées et build complet).
