# GEL du module patient-couverture (2026-11-02) : LEVÉ POUR PC1 le 2026-11-03 (ADR 0076)

> **Mise à jour 2026-11-03 (PC2 et PC3, ADR 0077)** : assuré principal et prise en charge sont repris sur place ; les sources gelées Assure, Pec et leurs `excludes` sont supprimées. Restent gelées (exclues de la compilation) : Attestation, `PatientService/UseCases`, `PatientAdmis`, `MouvementPatient*`, `EtatPatient`, `NumeroDossier`, `MouvementType` (PC4 et PC5).
>
> **Levée du gel (2026-11-03)** : le module est **repris sur place** pour la slice PC1 (fiche, états, mouvements, recherche, synthèses, 3 routes d'assuré), réécrit contre les vraies colonnes (`date_evenement_etat`), le vocabulaire des états du legacy, les mêmes URL, et remis dans `backend/pom.xml`, `platform/pom.xml` et `Dockerfile`. Les raisons ci-dessous sont corrigées pour PC1. **Dette consignée** : les sources gelées d'Assure, Pec et Attestation (et `publishedlanguage` d'origine) restent **exclues de la compilation** par les `excludes` du `pom.xml` du module ; elles sont à supprimer et à réécrire par PC2 (assuré), PC3 (PEC), PC4 (attestations) ; le langage publié sera défini à PC5. `TableDuPatientCouvertureTest` reste désactivé jusqu'à PC5.

Statut : **gelé, non branché, hors réacteur Maven** (`backend/pom.xml` ne le liste plus, `backend/Dockerfile` ne copie plus son `pom.xml`, `platform/pom.xml` ne l'a jamais référencé). Le dossier, ses sources, ses tests, l'ADR 0064, les fiches (`.ddd/migration/slices/patient-couverture-pc*.md`), le tactique (`.ddd/tactical/patient-couverture.md`) et le glossaire sont conservés pour la reprise. Décision déléguée à l'assistant par l'utilisateur le 2026-11-02. ADR : 0073.

## Pourquoi
- **Colonnes et états inexistants** : `PatientsJdbc` lit `patients.date_sortie` (absente de Flyway V1..V52 ; la colonne réelle est `date_evenement_etat`) ; `EtatPatient` (`ACTIF, SORTI, DECEDE, GREFFE, GUERI`) ignore `PERMANENT, OCCASIONNEL, VACANCIER_*, TRANSFERE, GUERRI` du legacy (`valueOf` planterait ; les lecteurs Absences et Planification ne reconnaîtraient pas `SORTI`/`GUERI`).
- **Aucune parité** : les 17 goldens `characterization/patientcouverture` ciblent les URL legacy ; aucun golden `_nouveau_code` ; `MouvementsJdbc` n'écrit rien (les mouvements sont perdus).
- **Pas de garde de centre** : centre pris dans le corps ou la requête, aucun principal ni `@PreAuthorize` (IDOR inter-centres).
- **URL et tables nouvelles contraires à l'ADR 0024** (refactoring sur place : mêmes tables, mêmes URL) : `/api/v1/patient-couverture/*` au lieu de `/api/v1/patients`, `/api/v1/pec`…
- Autres défauts : pas de transaction ni de verrou optimiste, horloge non injectée, entités de domaine exposées en JSON, langage publié jamais émis, garde de tables aveugle (`/persistence/` exclu, `platform` non scanné).

## Ce qui a été fait au gel
- Audit de production : aucun code legacy patient/assuré/PEC modifié sans filet (entités JPA inchangées, `schema.sql` sans table patient-couverture ajoutée, aucune entrée patient-couverture dans `covered.json` à retirer). Preuve : `backend/scripts/patient-couverture-pg16-rejeu.sh`.
- `TableDuPatientCouvertureTest` désactivé (`@Disabled` motivé) ; module exclu du balayage de `ReglesDeGestionDocumentationTest` ; règle `FrontieresDesContextesTest` conservée (vide, `allowEmptyShould`).
- Règle « un état de sortie exige une date » : **déjà imposée par le legacy** (`FinOccupation.verifierDate`, `PATIENT_DATE_EVENEMENT_REQUISE`, RG-PAT-031) ; non à réimplémenter.

## Plan de reprise (sur place)
1. **Filet de caractérisation du legacy patient / assuré / PEC d'abord** : étendre les 17 goldens existants (PC2 assuré principal, PC3 PEC) à PC1 (identité, mouvements, états) et PC4 (attestations de droits).
2. **Établir la liste blanche datée** des lectures SQL hors contexte (relevé initial dans la fiche PC1) avant de réécrire la garde de tables.
3. **Slices PC1 à PC5 sur place** : mêmes tables (`patients`, `mouvement_patient`, `assure`, `assure_patient`, `attestation_droit`, `prise_en_charge`), mêmes URL, vocabulaire d'états du **legacy** (`PERMANENT`, `TRANSFERE`, `GUERRI`, `DECEDE`, `GREFFE`…), `date_evenement_etat` (et non `date_sortie`), garde de centre issue de la session, transactions, horloge injectée (fuseau du centre), DTO de réponse, mouvements réellement écrits.
4. Rebrancher le module dans `backend/pom.xml`, `platform/pom.xml`, `Dockerfile` seulement après parité verte ; langage publié défini à PC5.
