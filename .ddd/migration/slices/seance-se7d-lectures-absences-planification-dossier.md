---
slice: seance-se7d-lectures-absences-planification-dossier
status: planned
date: 2026-11-08
order: 7.4
context: seance
parent: seance-se7-lectures-publiees
depends_on: [seance-se7a-routes-residuelles]
sources: [.ddd/safety-net/seance-se7-lectures-publiees.md, .ddd/migration/slices/seance-se7-lectures-publiees.md]
---

# SE7d : lectures d'Absences patients, Planification et Dossier médical

| Fichier | Occurrences | Nature |
|---|---|---|
| `contexts/absences-patients/.../acl/legacyseance/SeancesRealiseesJdbc.java` | 3 | lecture (séances réalisées, dates, comptage) |
| `contexts/absences-patients/.../acl/legacyseance/ExclusionsDeSeancesJdbc.java` | 1 | comptage `exclusion_absence_id` |
| `contexts/absences-patients/.../acl/legacyplanning/PatientsAttendusJdbc.java` | 1 | lecture des patients écartés |
| `contexts/planification/.../acl/legacyseance/SeancesRealiseesJdbc.java` | 1 | séances réalisées de la semaine (jointure `patients`) |
| `contexts/dossier-medical/.../persistence/HistoriqueDesConstantesJdbc.java` | 2 | historique des constantes (jointure `volet_paramedical`) |

Dettes héritées : port `attenduLe` de Planification, règle de présence recopiée dans `SeancePlanningJdbcAdapter` (bc-seance), colonnes de place de `patients` (`salle_id`, `position_id`, `generateur_id`) lues et écrites hors Séance. Filet existant : Absences A1 à A4, Planification P4 à P7, DM1 (constantes). Risque : moyen. Un lot par consommateur (d1 Absences, d2 Planification, d3 Dossier médical).
