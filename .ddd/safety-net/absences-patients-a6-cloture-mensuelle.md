---
status: approved
date: 2026-10-23
sources: .ddd/tactical/absences-patients.md (MoisAbsences, A18, section 10 A6), facturation-f7-cloture-mensuelle, ADR 0009/0037, filets A2/A3/A4
---

# Filet A6 - clôture mensuelle des absences (nouveauté : on verrouille le « mois ouvert » aux points d'accroche)

Aucun code de production modifié, aucun seam. Seul changement hors tests : helper `jourNomme` ajouté au harnais de test
`CycleDeVieAbsencesCharacterizationSupport` (additif).

## Points d'accroche du code existant (là où un mois clos devra refuser ou ignorer)

| #  | Point                                                                                                                               | Aujourd'hui                                                | Verrouillé « mois ouvert » par                                                                        |
|----|-------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------|-------------------------------------------------------------------------------------------------------|
| 1  | `DeclarerUneAbsence:69` `DateOuverte.exiger` (aussi via `ExclusionDeLaFacturation.declarer`, dans la transaction de la Facturation) | refus période facturée seulement                           | A2 `declaration`, `exclusion-facturation` (refus 422 `ABSENCE_PERIODE_FACTUREE` propagé, tout annulé) |
| 2  | `AbsencePatient.qualifier:177`                                                                                                      | idem                                                       | A2 `qualification`                                                                                    |
| 3  | `AbsencePatient.rattraper:199` (date de l'ABSENCE seulement ; la date de rattrapage n'est jamais regardée)                          | idem                                                       | A2 `rattrapage` + **A6** `rattrapage_dans_un_autre_mois_que_l_absence`                                |
| 4  | `AbsencePatient.annuler:241`                                                                                                        | idem                                                       | A2 `annulation` + **A6** `anciens_mois_ecritures_permises`                                            |
| 5  | `RetirerLAbsenceDeLExclusion` (réinclusion Facturation) : aucun contrôle de période, suppression physique (B11)                     | jamais refusée                                             | A2 `reinclusion_*` (aucun contrôle de mois d'absences : décision A6)                                  |
| 6  | `DetectionAbsences:62-65` (jour ignoré) et `:82` `DateOuverte.siOuverte` (course) ; `joursIgnores` non exposé en HTTP (KB-ABS-37)   | via port `MoisDAbsencesOuverts` = `TousLesMoisSontOuverts` | A3 + **A6** `rattrapage_sur_deux_mois_ouverts`, `controle_de_nuit_avec_facturation_close`             |
| 7  | `ReconciliationAbsences:57` (absences des mois clos laissées)                                                                       | idem                                                       | A3 `reconciliation`                                                                                   |
| 8  | `NotificationDesAbsencesAQualifier.synthese` (soustrait `moisClos`)                                                                 | idem                                                       | **A6** `notification_sur_deux_mois_ouverts` (2 à qualifier / 2 en retard sur 2 mois)                  |
| 9  | Câblage : `AbsencesPatientsContexteConfiguration:214` bean `MoisDAbsencesOuverts` → remplacer `TousLesMoisSontOuverts`              | —                                                          | —                                                                                                     |
| 10 | Lectures (`/synthese`, liste, suivi, pilotage) : comptent tous les mois, rien à refuser                                             | —                                                          | A1/A4                                                                                                 |

## Interaction avec la Facturation (F7)

- Aucun lien aujourd'hui : un mois de facturation clos (`periodes_facturation`) ne ferme ni déclaration, ni
  qualification, ni rattrapage, ni annulation, ni détection (rattrapage et nuit) d'absences.
  `PeriodesFactureesDeLaFacturation` ne regarde que les factures du patient, pas `MoisDeFacturation`. Golden :
  `facturation_close_ecritures_directes_permises`, `rattrapage_avec_facturation_close`,
  `controle_de_nuit_avec_facturation_close`.
- Ordre ADR 0009 non imposé : la clôture de facturation réussit avec des absences à qualifier dans le mois (200,
  `seancesEligiblesRestantes` ne compte que des séances), les laisse intactes et ne publie aucun champ « absences ».
  Golden : `cloture_de_facturation_avec_absence_a_qualifier`.
- Garde de la Facturation sur l'exclusion : `ExclureUneSeance:41`/`ReinclureUneSeance:39` testent le mois de FACTURATION
  avant d'appeler les absences (F7 `GardesDuMoisClos`, 422 `PERIODE_DE_FACTURATION_CLOTUREE`, aucune absence déclarée).
  Un mois d'absences clos refusera donc APRES, par la même voie que `ABSENCE_PERIODE_FACTUREE` (422 + rollback de
  l'exclusion). Le legacy de la Facturation ne demande aucune modification.
- Piège de test : la garde F7 crée une ligne de mois OUVERTE au premier verrouillage (exclusion) ; les fixtures de
  clôture doivent supprimer avant d'insérer (fait).

## Scénarios ajoutés (8 tests, 8 goldens) - harnais A2/A3, HTTP + SQL, aucun mock

- `cyclevie.MoisOuvertDesEcrituresDAbsenceA6CharacterizationIntegrationTest` (4) : theme `cycle-de-vie/mois-ouvert-a6`.
- `detection.DetectionEtNotificationSurPlusieursMoisA6CharacterizationIntegrationTest` (4) : theme
  `detection/mois-ouvert-a6`.
  Dates : alias relatifs `<J-n>` ou jours de calendrier nommés (`<DERNIER_JOUR_DU_MOIS-2>`, `<PREMIER_JOUR_DU_MOIS-1>`,
  `<QUINZE_DU_MOIS-3>`), aucune chaîne `AAAA-MM` dans un golden, attente de minuit héritée du harnais.

## Seam

Aucun. Fichiers legacy à modifier pour A6 : aucun hors `contexts/` (nouveau code) ; migration additive + schema.sql
(ressources). Si l'utilisateur veut que la Facturation lise l'état du mois d'absences (Q lien), ce serait un port publié
consommé via ACL dans `contexts/facturation` (nouveau code, pas de seam legacy).

## Limites (non verrouillé)

Concurrence clôture/écriture (nouvelle, à prouver en A6 par `FOR UPDATE`) ; mutation et branches non mesurables (ni PIT
ni JaCoCo configurés) ; nuit exacte au passage de minuit (le harnais attend) ; exclusion d'une séance de plus de 60
jours ou à cheval sur deux mois (délai de 60 jours, dépendant du jour : non testé à cheval) ; contenu exact de
`seancesEligiblesRestantes` avec séances (F7).

## Stabilité

3 exécutions vertes (1 ordre par défaut, 2 `MethodOrderer$Random` + `ClassOrderer$Random`), avec voisins
`ExclusionEtReinclusionDeLaFacturation...` (11) et `ControleDeNuitDesAbsences...` (13) : 0 flaky. Un premier essai a
révélé la collision de clé sur `periodes_facturation` (lignes OUVERTES laissées par la classe d'exclusion), corrigée
dans les fixtures de test.
Commande : `./mvnw -o -q -pl platform -am test -Dtest="<classes complètes>" -Dsurefire.failIfNoSpecifiedTests=false`.

## Décisions à prendre pour A6

Voir le retour du filet : qui clôture/rouvre, motif, avertissement, lien Facturation, détection sur mois clos, jours de
grâce, mois de référence du rattrapage, réinclusion.
