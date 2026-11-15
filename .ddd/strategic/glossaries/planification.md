---
status: validated
date: 2026-10-26
context: Planification (core)
sources: [.ddd/discovery/glossary.md, .ddd/discovery/event-storming.md §3]
---
# Glossaire — Planification

| Terme métier | Définition | Identifiant cible | Nom legacy | Synonymes à bannir | Règle / exemple | Statut |
|---|---|---|---|---|---|---|
| Place | Salle + créneau + générateur + jours de dialyse attribués à un patient | `Place` | colonnes de `patients`, `position_creneau` | poste | libérée après le dernier jour d'occupation (RG-PAT-033) | validated |
| Placement | Attribution d'une place à un patient | `Placement` / `PatientPlace` | affectation | affectation (sens Assuré) | refus si incohérent (RG-PLN-050) ; isolement (RG-PLN-053) | validated |
| Proposition de place | Place candidate notée 0-100 | `PropositionDePlace` | `PlanificationAffectationService.proposer` | — | RG-PLN-030 | validated |
| Générateur | Machine de dialyse vue comme ressource d'une place | `Generateur` | — | équipement (GMAO) | indisponible si `EquipementMisHorsService` | validated |
| Créneau / Salle | Plage horaire / local (référence Organisation) | `CreneauRef`, `SalleRef` | `CreneauRef`, `SalleRef` | — | conformiste à Organisation | validated |
| Jours de dialyse | Jours de la semaine où le patient est attendu | `JoursDeDialyse` | — | — | base de la détection d'absence | validated |
| Optimisation du planning | Calcul automatique d'un planning, appliqué sur décision | `RunOptimisation` | `RunOptimisation`, Timefold | — | `PropositionAppliquee` | validated |
| Case imprimable du calendrier | Ligne (salle x créneau d'une semaine) du planning calendaire figé d'une proposition, en texte prêt à imprimer : sept en-têtes et sept contenus de jour (un jour absent de la semaine est nul) ; la Planification la fournit au gabarit `planning_optimise` (DOC5c, ADR 0195) | `CaseImprimableDuCalendrier`, `CalendriersDesPropositions.imprimables` | colonnes `entete_i`/`cell_i` de `planification_calendrier_case` | — | RG-PLN-101 ; ordre semaine, créneau, salle ; bornée au centre | proposed |
| Replanification automatique | Optimisation nocturne pour les centres qui l'activent | politique `ReplanificationNocturne` | `ReplanificationAutomatiqueScheduler` | — | RG-PLN-100 | validated |
| Roulement | Affectation des infirmiers aux créneaux | `Roulement` | — | — | — | validated |
| Absence d'infirmier | Indisponibilité d'un infirmier (congé, maladie, formation) | `AbsenceInfirmier` | `AbsenceInfirmier` | absence (seul) | déclenche la recherche de remplaçant | validated |
| Remplaçant | Infirmier proposé (classé) pour couvrir une absence d'infirmier | `Remplacant` | `RemplacantService` | — | RG-INF-051 | validated |
| Sous-effectif | Créneau dont l'effectif infirmier est insuffisant | `SousEffectif` | `PresenceInfirmierScheduler` | — | horizon 14 j, ratio 4 (à confirmer) | validated |
| Capacité théorique | Nombre de places disponibles par semaine | `CapaciteTheorique` | `CapaciteTheoriqueCalculator` | — | lecture | validated |

Homonymes : *Absence d'infirmier* (ici) ≠ *Absence de patient* (Absences patients). *Générateur* (ici) ≠ *Équipement* (GMAO).

## Termes ajoutés par le modèle tactique (2026-10-26, `.ddd/tactical/planification.md`) — validés par les experts métier le 2026-10-26

| Terme métier | Définition | Identifiant cible | Nom legacy | Synonymes à bannir | Règle / exemple | Statut |
|---|---|---|---|---|---|---|
| Places du centre | Ensemble des places occupées d'un centre, qui garantit qu'un générateur ne sert qu'un patient par jour et créneau | `PlacesDuCentre` | `DonneesPlanning`, `Planning` | planning (seul) | RG-PLN-020/021/024 | validated |
| Catégorie d'isolement | Patient à risque infectieux ou sans risque | `CategorieDIsolement` | `aRisque` | — | RG-PLN-012, 052 | validated |
| Ressources du centre | Salles, créneaux, générateurs en service, jours ouverts, fermetures datées utilisés pour placer | `RessourcesDuCentre` | `DonneesPlanning` | — | RG-PLN-011, 013 | validated |
| Dernier jour d'occupation | Dernier jour où le patient occupe sa place | `DernierJourDOccupation` | `FinOccupation` | — | RG-PAT-032 | validated |
| Déplacement temporaire | Place occupée un jour donné à la place de la place habituelle (générateur indisponible) | `DeplacementTemporaire` | `DeplacementTemporaire` | — | RG-PLN-096 | validated |
| Exécution d'optimisation | Un calcul de planning, de son lancement à son application | `ExecutionDOptimisation` | `RunOptimisation` | run | RG-PLN-081 | validated |
| Périmètre | Ce que l'optimisation recalcule : patients, roulement, couverture, complet, maintenance | `Perimetre` | `PerimetreOptimisation` | — | RG-PLN-080 | validated |
| Empreinte | Preuve que le centre n'a pas changé depuis le calcul | `Empreinte` | `EmpreinteOptimisation` | — | RG-PLN-091 | validated |
| Préférence de planification | Créneau préféré et jours à choisir d'un patient | `PreferenceDePlanification` | `PreferencePatient` | — | RG-PLN-093 | validated |
| Profil de planification | Taux d'activité et compétences d'un infirmier | `ProfilDePlanification` | `ProfilInfirmier` | — | RG-PLN-097 | validated |
| Réglages de planification | Durée de vacation, temps plein, repos, replanification nocturne d'un centre | `ReglagesDePlanification` | `ReglagesOptimisation` | — | RG-PLN-098 | validated |
| Roulement de l'infirmier | Affectations hebdomadaires d'un infirmier | `RoulementDeLInfirmier` | `AffectationInfirmier` (liste) | — | RG-INF-022 | validated |
| Affectation au roulement | Un infirmier dans une salle, sur un créneau, certains jours | `AffectationInfirmier` | `AffectationInfirmier` | affectation (sens Placement/Assuré) | RG-INF-020 | validated |
| Vacation | Présence d'un infirmier sur une case | `Vacation` | `Vacation` (Timefold) | — | RG-PLN-085 | validated |
| Case | Salle × créneau × jour | `Case` | `Presence` (case) | poste | RG-INF-040 | validated |
| Effectif requis | ⌈patients / ratio⌉ d'une case | `EffectifRequis` | `Presence` | — | RG-INF-040 | validated |
| Sur-effectif | Infirmiers comptés au-delà de l'effectif requis | `SurEffectif` | `surplus` | — | RG-INF-047 | validated |
| Remplaçant éligible | Infirmier non absent, pas déjà prévu sur le créneau, habilité si isolement | `RemplacantEligible` | `RemplacantService` | — | RG-INF-050 | validated |

## Termes ajoutés par la slice P1 (2026-10-26) — statut `proposed`, à valider par les experts métier

| Terme métier | Définition | Identifiant cible | Nom legacy | Synonymes à bannir | Règle / exemple | Statut |
|---|---|---|---|---|---|---|
| Paramètres du planning | Jours d'ouverture, salles d'isolement, ratio de sécurité et patients par poste et par série d'un centre | `ParametresDuPlanning` | `PlanningParametres` | configuration | RG-PLN-001 à 006 ; remplacement total | proposed |
| Ratio de sécurité | Nombre maximal de patients par infirmier (1 à 20, défaut 4) | `RatioDeSecurite` | `patientsParInfirmier` | — | RG-PLN-003 | proposed |
| Patients par poste et par série | Base de la capacité théorique (1 à 10, défaut 3) | `PatientsParPosteEtSerie` | `patientsParPosteEtSerie` | — | RG-PLN-003 | proposed |
| Jours d'ouverture | Jours de la semaine (dimanche à samedi) où le centre dialyse | `JourDeLaSemaine` | `JourSemaine` | — | RG-PLN-002, 006 | proposed |
| Salle d'isolement | Salle réservée aux patients à risque infectieux ; propriété de la salle (Organisation) | `SalleId` + port `SallesDuCentre` | `salle.isolement` | — | RG-PLN-004 | proposed |
| Compétence | Aptitude particulière d'un infirmier (`PEDIATRIE`, `CATHETER`) | `Competence` | `CompetenceInfirmier` | — | RG-PLN-097 | proposed |
| Taux d'activité | Pourcentage d'un temps plein d'un infirmier (10 à 100) | `ProfilDePlanification.tauxActivite` | `tauxActivite` | quotité | RG-PLN-097 | proposed |
| Verrou du centre | Exclusion mutuelle des écritures de paramétrage d'un centre : la seconde écriture attend puis passe ; 409 `PLANIFICATION_ECRITURE_CONCURRENTE` seulement après 5 s d'attente ou si la même clé est créée entre-temps | `VerrouDuCentre` | — | — | ADR 0049, 0055 | proposed |

## Termes ajoutés par la slice P2 (2026-10-26) — statut `proposed`, à valider par les experts métier

| Terme métier | Définition | Identifiant cible | Nom legacy | Synonymes à bannir | Règle / exemple | Statut |
|---|---|---|---|---|---|---|
| Infirmier (fiche) | Fiche du personnel soignant d'un centre : matricule, nom, prénom, téléphone, qualification, habilité à l'isolement, état, compte lié éventuel | `Infirmier` | `Infirmier` (record) | soignant (sens générique), utilisateur | RG-INF-001 à 004 | proposed |
| Matricule | Identifiant métier d'un infirmier, unique dans le centre sans tenir compte de la casse, enregistré en majuscules (50 car. max) | `Matricule` | `matricule` | code, numéro | RG-INF-002 ; ADR 0056 | proposed |
| Qualification | `INFIRMIER`, `MAJOR`, `AIDE_SOIGNANT` | `Qualification` | `QualificationInfirmier` | grade, fonction | RG-INF-001 | proposed |
| Habilité à l'isolement | Un infirmier habilité peut travailler en salle d'isolement ; facultatif à la saisie (faux par défaut) | `Infirmier.habiliteIsolement` | `habiliteIsolement` | — | RG-INF-001, 050 | proposed |
| Compte lié | Compte utilisateur (Identité et accès) relié à une fiche ; même état que la fiche | `Infirmier.compte` (`CompteId`) | `userId` | accès, login | RG-INF-010 à 012 | proposed |
| Compte liable | Compte INFIRMIER du centre qu'aucune fiche du centre n'utilise encore | `ComptesUtilisateurs.comptesLiables` | `comptesLiables` | — | RG-INF-012 | proposed |
| Identifiant de connexion | Nom de connexion d'un compte créé depuis une fiche : 3 à 50 caractères `[A-Za-z0-9._-]`, unique tous centres sans la casse | `IdentifiantDeConnexion` | `username` | pseudo | RG-INF-011 | proposed |
| Mot de passe temporaire | Mot de passe de 14 caractères affiché une seule fois à la création d'un compte ; changement exigé à la première connexion | `GenerateurDeMotDePasse` | `GenerateurMotDePasseTemporaire` | mot de passe initial | RG-INF-011, RG-SEC-025 | proposed |
| Verrou du référentiel des infirmiers | Exclusion mutuelle des écritures du référentiel d'un centre (ligne du centre) ; 409 `PLANIFICATION_ECRITURE_CONCURRENTE` après 5 s | `VerrouDuReferentielDesInfirmiers` | — | — | ADR 0056 | proposed |
| État du compte | Vu de la fiche : absent, actif, inactif ; sert à refuser les états incohérents | `EtatDuCompte` | — | — | RG-INF-003 | proposed |

## Termes ajoutés par la slice P3 (2026-10-26) — statut `proposed`, à valider par les experts métier

| Terme métier                 | Définition                                                                                                                                                        | Identifiant de code                 | Legacy                         | Synonymes à éviter                                   | Règle            | Statut   |
|------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------|--------------------------------|------------------------------------------------------|------------------|----------|
| Roulement                    | Les affectations hebdomadaires d'un infirmier (agrégat) ; jamais deux affectations sur le même créneau un même jour                                               | `RoulementDeLInfirmier`             | `AffectationInfirmier` (liste) | planning de l'infirmier (réservé à « Mon planning ») | RG-INF-020 à 022 | proposed |
| Affectation                  | Une ligne du roulement : salle, créneau, jours de la semaine (au moins un)                                                                                        | `Affectation` / `AffectationId`     | `AffectationInfirmier`         | poste, vacation                                      | RG-INF-020       | proposed |
| Absence d'un infirmier       | Période (bornes incluses, 366 jours au plus) pendant laquelle l'infirmier n'est pas prévu ; type congé, maladie, formation, autre ; motif ≤ 255 ; auteur conservé | `AbsenceInfirmier` / `AbsenceId`    | `AbsenceInfirmier`             | congé (un des types)                                 | RG-INF-030 à 033 | proposed |
| Période d'absence            | Début, fin, bornes incluses ; deux absences d'un même infirmier ne partagent jamais un jour                                                                       | `PeriodeDAbsence`                   | —                              | —                                                    | RG-INF-030       | proposed |
| Remplacement                 | Un infirmier éligible renforce une salle sur un créneau à une date, éventuellement à la place d'un collègue                                                       | `Remplacement` / `RemplacementId`   | `RemplacementInfirmier`        | intérim                                              | RG-INF-052       | proposed |
| Remplaçant éligible          | Preuve qu'un infirmier actif, présent et libre sur le créneau (habilité en salle d'isolement) peut remplacer                                                      | `RemplacantEligible`                | candidat                       | —                                                    | RG-INF-050, 052  | proposed |
| Remplacé                     | Collègue absent ce jour-là ou prévu sur la case, que le remplaçant remplace (facultatif)                                                                          | `Remplacement.remplace`             | `remplaceId`                   | —                                                    | RG-INF-052       | proposed |
| Sur-effectif (avertissement) | Jours de la semaine courante où une affectation porte la case au-dessus de l'effectif requis ; jamais bloquant                                                    | `SurEffectifDuRoulement`            | `joursEnSureffectif`           | —                                                    | RG-INF-023, 047  | proposed |
| Mon planning                 | La semaine, les absences et les créneaux de l'infirmier dont la fiche est liée au compte connecté                                                                 | `ConsulterMonPlanning`              | `MonPlanningInfirmierService`  | —                                                    | RG-INF-032       | proposed |
| Jour du centre               | « Aujourd'hui » au fuseau du centre (Africa/Algiers par défaut), non en UTC                                                                                       | `HorlogeDesCentres`                 | `LocalDate.now(UTC)`           | date du jour                                         | ADR 0054, 0057   | proposed |
| Verrou de la fiche           | Exclusion mutuelle des écritures d'un même infirmier (ligne `infirmier`, `FOR UPDATE`) ; 409 `PLANIFICATION_ECRITURE_CONCURRENTE` après 5 s                       | `InfirmiersRepository.pourEcriture` | —                              | —                                                    | ADR 0057         | proposed |
| Absence sans chevauchement | Politique d'ensemble : deux absences d'un même infirmier ne partagent jamais un jour (contiguës acceptées), jugée sous le verrou de la fiche | `AbsencesSansChevauchement` | — | — | RG-INF-030 | proposed |
| Auteur de l'absence | Compte qui a saisi l'absence (administration, secrétariat ou l'infirmier lui-même) ; l'infirmier ne retire que ses propres déclarations ; nul avant V47 | `AbsenceInfirmier.auteur` / `declaree_par` | — | — | RG-INF-032, KB-PLN-39 | proposed |

## Termes ajoutés par la slice P4 (2026-10-27) — statut `proposed`, à valider par les experts métier

| Terme métier | Définition | Identifiant cible | Nom legacy | Synonymes à bannir | Règle / exemple | Statut |
|---|---|---|---|---|---|---|
| Présence de la semaine | Pour chaque case (salle, créneau, jour) : infirmiers prévus, absents, effectif requis, manque, surplus, statut | `PresenceDeLaSemaine`, `SemainePresence` | `PresenceInfirmierService`, `SemainePresence` | planning de présence (écran) | RG-INF-040 à 044 | proposed |
| Case | Une salle, un créneau, un jour : unité de présence et de calendrier | `CasePresence` | `CasePresence` | cellule | RG-INF-040 | proposed |
| Effectif requis | ⌈patients / ratio⌉, 0 sans patient | `PresenceDeLaSemaine.requis` | `requis` | besoin | RG-INF-040 | proposed |
| Ratio lu | Ratio de sécurité tel que la présence le lit : valeur de 1 à 20, défaut 4 si absente ou illisible (journalisée) | `RatioDeLaPresence` | `patientsParInfirmier` | — | RG-INF-046, KB-PLN-47 | proposed |
| Alerte de sous-effectif | Case en sous-effectif dans les jours à venir, avec les absents responsables | `AlertePresence` | `AlertePresence` | — | RG-INF-045 | proposed |
| Alerte de sur-effectif | Case dont le surplus d'infirmiers est positif, y compris en salle sans patient | `AlerteSureffectif` | `AlerteSureffectif` | — | RG-INF-047 | proposed |
| Double affectation | Infirmier prévu dans deux salles au même créneau le même jour ; signalée dans la semaine et par la route des alertes de sur-effectif | `AlerteDoubleAffectation` | `DOUBLE_AFFECTATION` | doublon | RG-INF-043, 047 | proposed |
| Candidat remplaçant | Infirmier proposé pour une case, noté de 0 à 100 avec ses raisons | `Candidat`, `ClassementDesRemplacants` | `RemplacantService` | suppléant | RG-INF-050, 051 | proposed |
| Charge mensuelle | Séances du roulement, remplacements un jour de présence et d'ouverture, jours d'absence ; moyenne du centre | `ChargeDesInfirmiers` | `ChargeInfirmierService` | — | RG-INF-060 | proposed |
| Ouverture du centre | Jours d'ouverture hebdomadaires moins fériés et fermetures exceptionnelles | `OuvertureDuCentre` | `CalendrierCentre` | — | RG-INF-042 | proposed |
| Contrôle de la présence | Passage quotidien de 07:15 sur tous les centres actifs ; une alerte par type, centre et jour du centre | `ControlerLaPresence` | `PresenceInfirmierScheduler` | job de présence | RG-INF-046 | proposed |
| Impression de la présence | Planning de présence d'une semaine au format du modèle du centre (PDF, HTML, EXCEL) ; sans modèle, le modèle livré | `ImprimerLaPresence` (port `ImpressionDeLaPresence`, adaptateur `PresenceImprimeeParDocuments`, PLN-DOC) | `PresenceInfirmierReportService` | — | RG-INF-061 | proposed |

## Termes ajoutés par la slice P5 (2026-10-27) — statut `proposed`, à valider par les experts métier

| Terme métier | Définition | Identifiant cible | Nom legacy | Synonymes à bannir | Règle / exemple | Statut |
|---|---|---|---|---|---|---|
| Places du centre | Ensemble des places occupées d'un centre, protégé par le verrou du centre ; racine du placement | `PlacesDuCentre` | `PlacementPatientService` | — | RG-PLN-050 | proposed |
| Place | Salle + créneau + générateur + jours d'un patient ; complète ou absente | `Place` | `Placement` | affectation | `PLACEMENT_INCOMPLET` | proposed |
| Proposition de place | Place suggérée à un patient, notée de 0 à 100 avec ses raisons ; 30 au plus | `PropositionsDePlace` | `PlanificationAffectationService` | suggestion | RG-PLN-020 à 026 | proposed |
| Salle d'isolement réservée | Une salle d'isolement n'accueille que des patients à risque infectieux | `PLACEMENT_ISOLEMENT_RESERVE` | — | — | RG-PLN-050, 052 | proposed |
| Retour en salle ordinaire proposé | Patient redevenu sans risque encore en salle d'isolement : l'administration est alertée, rien n'est déplacé | `RETOUR_PROPOSE`, alerte `RETOUR_SALLE_ORDINAIRE_PROPOSE` | — | retour automatique | RG-PLN-053 | proposed |
| Isolement impossible | Patient à risque sans place d'isolement ; alerte une seule fois par patient et par jour du centre | `ISOLEMENT_IMPOSSIBLE` | `ISOLEMENT_IMPOSSIBLE` | — | RG-PLN-053 | proposed |
| Alerte de générateur indisponible | Alerte idempotente par générateur et par jour du centre, citant 5 patients au plus et le total ; aucun déplacement | `AlerterUnGenerateurIndisponible` | `GenerateurIndisponibleService` | — | RG-NOT-007 | proposed |
| Verrou des places | Verrou du centre qui crée d'abord la ligne de paramètres par défaut puis la verrouille ; 409 pour la perdante d'une création simultanée | `VerrouDesPlaces` | — | — | ADR 0059 | proposed |

## Termes ajoutés par la slice P6 (2026-10-27) — statut `proposed`, à valider par les experts métier

| Terme métier | Définition | Identifiant cible | Nom legacy | Synonymes à bannir | Règle / exemple | Statut |
|---|---|---|---|---|---|---|
| Séjour du patient | Admission, dernier jour de place, départ le dernier jour, sommeil : l'unique calcul de présence | `SejourDuPatient` | `FinOccupation`, colonnes de `patients` | période de présence | RG-PLN-046 | proposed |
| Patient présent | Place tenue un jour donné et patient pas en sommeil : il figure au planning | `estPresentLe` | — | actif | RG-PLN-046 | proposed |
| Patient attendu | Patient présent qui n'est pas parti ce jour-là : une séance est attendue | `estAttenduLe`, `PresenceDesPatients.attendusLe` | `PatientsAttendusJdbc` | patient prévu | RG-PLN-046, RG-ABS-041 | proposed |
| File active | Patients présents au moins un jour de la période, hors sommeil | `PresenceDesPatients.fileActive` | `FILE_ACTIVE` (SQL) | effectif | RG-PLN-074 | proposed |
| Semaine du planning | Planning réel du dimanche au samedi : jours, cases, conflits, patients à replanifier | `SemaineDuPlanning.Semaine` | `SemainePlanning` | grille | RG-PLN-040 à 045 | proposed |
| Centre masqué | Centre dont la file active est de 1 à 4, exclu du total consolidé de la capacité | `centresMasques` | — | centre caché | RG-PLN-074 | proposed |
| Base de la capacité théorique | Séries, patients par poste et par série, file active d'un centre | `BaseDeLaCapaciteTheorique` | `SERIES`, `PARAMETRES` (SQL) | — | RG-PLN-072 | proposed |

## Termes ajoutés par la slice P7 (2026-10-27) — statut `proposed`, à valider par les experts métier

| Terme métier               | Définition                                                                                                                        | Identifiant cible                                   | Nom legacy                                            | Synonymes à bannir         | Règle / exemple | Statut   |
|----------------------------|-----------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------|-------------------------------------------------------|----------------------------|-----------------|----------|
| Place tenue                | Ce qu'un patient occupe encore sur sa fiche (salle, créneau, générateur, jours) avec le dernier jour où il y a droit              | `PlaceTenue`                                        | colonnes de place de `patients`                       | affectation                | RG-PLN-108      | proposed |
| Place quittée              | La salle, le créneau, le générateur et les jours effacés par une libération, figés dans le mouvement                              | `PlaceQuittee`                                      | `jours_dialyse`, `salle_id`... de `mouvement_patient` | ancienne place             | RG-PLN-110      | proposed |
| Place libérée              | Évènement : la place d'un patient dont le dernier jour est dépassé a été effacée, au jour réel du centre                          | `PlaceLiberee` (v1 d'intégration différé)           | mouvement `PLACE_LIBEREE`                             | place supprimée            | RG-PLN-110      | proposed |
| Libération des places      | Politique : une place est libérée quand son dernier jour est dépassé, un patient par transaction                                  | `LibererLesPlacesEchues`, `LibererLaPlaceDeLaFiche` | `LiberationPlacesService`                             | nettoyage des affectations | RG-PLN-108, 109 | proposed |
| Échéance jugée à la veille | À l'enregistrement d'une fiche, le dernier jour est jugé dépassé à J-1 (la veille reste conservée pour la détection des absences) | `LiberationDesPlaces.siEchue`                       | `libererALEnregistrement`                             | —                          | RG-PLN-111      | proposed |
| Patient sorti | Patient dont le dernier jour de place est dépassé à l'état qu'aura sa fiche : une nouvelle place lui est refusée (`PLACEMENT_PATIENT_SORTI`), la reprise dans la même saisie est permise | `estSorti`, `connaitreUnSorti` | `Situation.SORTI`, `FinOccupation` | patient inactif | RG-PLN-050 | proposed |
| Libération en échec        | Alerte administrateur : des patients n'ont pas pu être libérés cette nuit (une par centre et par jour)                            | `NotificationsDeLaLiberation`                       | —                                                     | erreur de nuit             | RG-PLN-109      | proposed |
| Calcul orphelin | Calcul d'optimisation resté « en cours » dont l'instance du serveur a disparu : sans battement de cœur depuis 60 s, il échoue ; celui d'une instance vivante n'est jamais touché | `ReprendreLesExecutionsInterrompues` | exécution `EN_COURS` après redémarrage | calcul zombie | RG-PLN-081, ADR 0068 | proposed |
| Battement de cœur | Signe de vie (10 s) d'une instance pour ses calculs en cours | `ExecutionsRepository.battre` | — | heartbeat | RG-PLN-081 | proposed |
| Transition gardée | Une exécution ne part que de « en cours » ; terminée, en échec ou interrompue, elle ne change plus | `OPTIMISATION_TRANSITION_INTERDITE` | upsert aveugle | — | RG-PLN-091 | proposed |
| Couverture des absences | Politique : jugement du remplaçant (éligibilité) et choix du remplacé relié à l'application d'une proposition | `CouvertureDesAbsences` | `remplace_infirmier_id` NULL | — | RG-PLN-088 | proposed |
| Alerte personnelle | Alerte adressée au compte d'un infirmier et à l'administration du centre, jamais aux autres infirmiers (roulement modifié, remplacement affecté) | `INFIRMIER_ROULEMENT_MODIFIE`, `INFIRMIER_REMPLACEMENT_AFFECTE` | alerte « par rôle » | — | RG-PLN-112 | proposed |
| Générateurs de secours | Un générateur de secours pour huit en service, arrondi au supérieur (capacité théorique) | `GenerateursDeSecours` | `CapaciteTheoriqueCalculator` | — | RG-PLN-060 | proposed |
| Capacité de la salle | Nombre maximal de générateurs affectés à une salle ; sans capacité, aucune limite ; le générateur déjà dans la salle n'est pas compté | `CapaciteDeLaSalle`, `CapaciteDesSalles.CapaciteDeLaSalleDepassee` | `CapaciteSalleRegle`, `SALLE_CAPACITE_DEPASSEE` | — | RG-PLN-061 | proposed (P9) |
| Notification de la Planification | Message émis par la Planification vers le centre de notifications (présence sous/sur-effectif, libération en échec, optimisation, replanification, séances déplacées, générateur indisponible, isolement, retour en salle ordinaire, roulement, remplacement, absences d'infirmier) : types, contenu, audience et durabilité tenus dans le catalogue de la Planification, confiés au port publié notifications.v1 par ses ACL sortantes | `CatalogueDesNotificationsDeLaPlanification`, `*ParLeCentreDeNotifications` | `notifyPresenceSousEffectif`, `notifyGenerateurIndisponible`... (retirés) | — | RG-NOT-001, RG-NOT-005 | proposed (PL13c, ADR 0155) |
| Semaine de l'infirmier | « Mon planning » : sa semaine dans la grille du centre, composée de la présence et du planning réel (un patient en sommeil n'y figure plus : ni occupant, ni nombre de patients, ni effectif requis ; un seul calcul de présence, décision du 2026-11-02) | `SemaineDeLInfirmierDuModule` | `SemaineDeLInfirmierLegacy` | — | RG-PLN-104, KB-PLN-106 | proposed (P9) |

## Termes ajoutés par la slice SE7d (2026-11-08, ADR 0098) — statut `proposed`, à valider par les experts métier

| Terme métier | Définition | Identifiant cible | Nom legacy | Synonymes à bannir | Règle / exemple | Statut |
|---|---|---|---|---|---|---|
| Séance réalisée avec sa place (fournie) | Séance réalisée (statuts exacts) de la période avec la place figée à sa validation et le nom et le prénom bruts de la fiche, lue seulement si la fiche est dans le centre de la séance ; interface requise du langage publié de la Planification, fournie par la plateforme. La Planification compose le nom « prénom nom » et ajoute le risque infectieux (`SeanceRealisee`) | `SeancesRealiseesAvecLeurPlaceFournies` (`SeanceRealiseeAvecPlaceFournie`), ACL `acl/seance/SeancesRealiseesPubliees` | `legacyseance/SeancesRealiseesJdbc` (retiré) | « séance passée » | RG-SEA-063 ; KB-SEA-78, 80 | proposed |

## Termes ajoutés par la slice DM9 (2026-11-09, ADR 0101) — statut `proposed`, à valider par les experts métier

| Terme métier | Définition | Identifiant cible | Nom legacy | Synonymes à bannir | Règle / exemple | Statut |
|---|---|---|---|---|---|---|
| Risque infectieux (fourni) | Les patients à risque infectieux d'un centre (dernière sérologie AgHBs, anti-VHC, ARN du VHC ou VIH positive), interface requise du langage publié de la Planification, fournie par la plateforme depuis le langage publié du Dossier médical (cycle Maven évité) ; la Planification en déduit la catégorie d'isolement | `RisquesInfectieuxFournis`, ACL `acl/dossiermedical/RisquesInfectieuxPublies` | `legacydossiermedical/RisquesInfectieuxJdbc` (retiré) | — | RG-PLN-012 ; KB-MED-106 à 108 reproduits (règle du Dossier médical) | proposed |
| Porteurs d'un cathéter (fournis) | Les patients d'un centre porteurs d'un cathéter actif non terminé au jour donné, interface requise du langage publié de la Planification, fournie par la plateforme depuis le Dossier médical ; donne la compétence `CATHETER` | `AbordsVasculairesFournis`, `ContraintesDesPatientsJdbc` (patients seuls) | `ContraintesDesPatientsJdbc:34-37` (SQL sur `abords_vasculaires`, retiré) | — | RG-PLN-097 ; la règle des 18 ans (`PEDIATRIE`) reste dans la Planification | proposed |

## Termes ajoutés par la slice SE7f (2026-11-08, ADR 0100) — statut `proposed`, à valider par les experts métier

| Terme métier | Définition | Identifiant cible | Nom legacy | Synonymes à bannir | Règle / exemple | Statut |
|---|---|---|---|---|---|---|
| Capacité théorique des centres (publiée) | Capacité théorique d'un centre sur une période et son taux d'occupation (SE7f, ADR 0100 : la méthode quitte `bc-seance`) : postes actifs = générateurs en service moins un de secours pour huit (arrondi supérieur), capacité = postes x séries par jour x patients par poste et par série (3 par défaut), file active de la période ; niveau `SANS_CAPACITE`, `MARGE`, `PROCHE` (90 %), `ATTEINTE` ; consolidation par addition. Les générateurs en service viennent de la GMAO, lus par l'appelant (Direction) | `CapaciteTheoriqueDesCentres` (`Capacite`, `Niveau`), `ConsulterLaCapaciteTheorique`, `CapaciteTheorique` (domaine) | `CapaciteTheoriqueCalculator` (bc-seance, retiré), `DirectionCapaciteQueryService` (consommateur, inchangé dans ses résultats) | « capacité de placement » (`CapaciteDesCentres`, autre notion : places hebdomadaires) | RG-PLN-070 à 074, RG-SEA-071 ; goldens P6 inchangés | proposed |
