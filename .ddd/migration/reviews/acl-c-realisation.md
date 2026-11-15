# ACL (c) finale : réalisation (2026-11-15, ADR 0192)

Mesure avant/après (fichiers de production de `platform/.../infrastructure/acl/*`) : **absences 2 -> 0, dossiermedical 1 -> 0, seance 1 -> 0** ; gmao 2 -> 1, organisation 4, patientcouverture 4, reprise 1 inchangés ; total 19 -> 10, sous-dossiers 7 -> 4 (gmao, organisation, patientcouverture, reprise).

| ACL | Cycle Maven ? | Destination |
|---|---|---|
| SeancesRealiseesParLaSeance + test, LiensDExclusionParLaSeance | non (seance -> absences-patients) | seance/infrastructure/acl/absencespatients |
| FichesFourniesParPatientCouverture + test | non (seance -> patient-couverture, planification) | seance/infrastructure/acl/patientcouverture |
| PapierEnTeteDuCentreParLaPlateforme (renommé PapierEnTeteDuCentreParDocuments) | non (dossier-medical -> seance -> documents ; documents -/-> dossier-medical) ; pom du Dossier médical : `contexts-documents` déclaré | dossier-medical/infrastructure/acl/documents |
| CapaciteDesSallesDePlanification | non (planification -> gmao) | scindé : gmao/infrastructure/acl/planification/CapaciteDesSallesDeLaPlanification + port fourni gmao.publishedlanguage.CapaciteDesSallesFournie ; planification/infrastructure/acl/gmao/CapaciteDesSallesFournieParLaPlanification ; exception `SalleADCapaciteMaximale` + handler 422 dans ErreursDeLaGmaoAdvice (même titre, détail, code) |
| 8 ACL organisation/patientcouverture, AppariementParLesContextes, GenerateursDeRepriseGmao | OUI : implémentent des ports de la plateforme (`ReferentialAdminDelegate`, `ReferentialKind`, `AppariementDeReferentiels`, `GenerateursDeReprise`) ; contexte hôte -> platform = cycle `platform -> contexts -> platform` | restent (noyau ADR 0140 D7 : façade des référentiels et reprise) |

Câblage : `@Bean` dans SeanceConfiguration (3), LecturesPublieesConfiguration (1), GmaoConfiguration (1), PlanificationConfiguration (1). Tests ajoutés : CapaciteDesSallesDeLaPlanificationTest (gmao, 3), CapaciteDesSallesFournieParLaPlanificationTest (planification, 2). Garde repointée : TableDesSeancesTest. Goldens inchangés (dont capacite_salle_depassee, modifier_capacite_salle_depassee).

Gardes datées : F-R1 et M-R1 SOLDÉS (entrées supprimées : fiche SE7f, ADR 0100, fiche DM9), aucune date reportée. F-R2..F-R7 non soldables (sans rapport avec les ACL : lectures SQL transitoires, décisions KB, champs de volet, port à rétrograder, écart de concurrence).

Lignes à retirer de `.ddd/safety-net/covered.json` (non modifié) : les chemins `backend/platform/src/main/java/com/hemodialyse/backend/infrastructure/acl/{absences/LiensDExclusionParLaSeance, absences/SeancesRealiseesParLaSeance, dossiermedical/PapierEnTeteDuCentreParLaPlateforme, gmao/CapaciteDesSallesDePlanification, seance/FichesFourniesParPatientCouverture}.java` = lignes 3 (Papier), 46 (CapaciteDesSalles), 73 (`acl/seance/*.java`), 75 (SeancesRealisees), 76 (LiensDExclusion) du fichier actuel. Les 10 fichiers restants gardent leur ligne.
