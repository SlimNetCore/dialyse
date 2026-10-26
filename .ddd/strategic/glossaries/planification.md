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
