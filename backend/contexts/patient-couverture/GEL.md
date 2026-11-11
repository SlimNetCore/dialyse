# Historique du gel du module patient-couverture (2026-11-02 au 2026-11-05) : NOTE HISTORIQUE, plus aucune source gelée

> **Le module est entièrement repris (PC1 à PC5) ; il n'y a plus aucune source gelée.** Ce fichier ne garde que l'historique.

## Chronologie
| Date | Événement | Référence |
|---|---|---|
| 2026-11-02 | Le module parallèle (tables, URL et états inventés, sans parité, sans garde de centre) est GELÉ : retiré du réacteur, non branché | ADR 0073 |
| 2026-11-03 | Reprise SUR PLACE de PC1 (fiche, états, mouvements), puis de PC2 (assuré principal) et PC3 (prise en charge) : mêmes tables, mêmes URL, vocabulaire du legacy, garde de centre | ADR 0076, 0077 |
| 2026-11-04 | Reprise de PC4 (attestations de droits) ; PC5 : langage publié `patientcouverture.v1` (`FichesDesPatients`, `CouvertureDesPatients`, `CompteursDeCouverture`) et migration de six lecteurs externes | ADR 0078, 0079 |
| 2026-11-05 | Suppression des 17 dernières sources gelées (non compilées) : `EtatPatient`, `MouvementPatient`, `MouvementPatientId`, `MouvementPatientRepository`, `MouvementType`, `NumeroDossier`, `PatientService`, `PatientUseCases`, `PatientAdmis` et les 8 DTO de `interfaces/http/dto`, avec leurs `excludes` du `pom.xml` | revue `verify-patient-couverture-2` |

## Pourquoi le gel (2026-11-02)
Colonnes et états inexistants (`patients.date_sortie` absente de Flyway ; états `ACTIF, SORTI…` ignorant `PERMANENT, OCCASIONNEL, VACANCIER_*, TRANSFERE…`), aucune parité avec le legacy, aucune garde de centre (IDOR inter-centres), URL et tables nouvelles contraires à l'ADR 0024 (refactoring sur place), pas de transaction ni de verrou, horloge non injectée, garde de tables aveugle.

## État actuel
- Le module vit dans le réacteur, la plateforme et le `Dockerfile`. Aucune exclusion de compilation.
- `TableDuPatientCouvertureTest` est ACTIF : liste blanche DATÉE des accès aux tables du patient restant hors du module (raison et échéance par entrée, ADR 0079). La dette est cette liste.
- Dette restante hors module : voir l'ADR 0079 (cycle Maven avec la Planification, colonnes de place de la Planification, jointures `GROUP BY` de Facturation et de la Direction, `SeanceRestController`, `PecValidationJdbc` code mort, outil de migration ; les modules `bc-patient` et `bc-assure` et les types JPA du patient ont été retirés en S6c, ADR 0133).
