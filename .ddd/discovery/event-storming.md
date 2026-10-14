---
status: draft
date: 2026-10-09
sources:
  - .ddd/scan/summary.md, .ddd/discovery/legacy-analysis.md
  - query.ts entrypoints (15 cron)
  - "Grep ciblés : enums de statut, méthodes mutatrices du domaine, @Scheduled, @EventListener, publishEvent"
  - docs/reference/regles-de-gestion (lecture 12-facturation l.1-110 ; extraits RG ciblés de 04, 06, 07, 08, 10, 11, 13, 16)
  - "Lecture de code brut : 0 fichier Java ouvert (lignes issues de Grep)"
validation: timeline NON validée par les experts — questions consolidées en fin de fichier
---

# Event Storming (Big Picture, reconstruit depuis le legacy)

Abréviations de chemin : `S/`=`backend/bc-seance/src/main/java/com/hemodialyse/backend/domain/`, `P/`=`backend/platform/src/main/java/com/hemodialyse/backend/`, `B<x>/`=`backend/bc-<x>/src/main/java/com/hemodialyse/backend/domain/<x>/`, `RG-*` = `docs/reference/regles-de-gestion/`. Confiance : **code** (méthode/enum/cron trouvé) · **doc** (référentiel RG, dérivé du code) · **à valider**.

## Timeline

### 1. Organisation et accès
| # | Événement | Commande | Acteur | Politique / déclencheur | Source | Confiance |
|---|---|---|---|---|---|---|
| 1 | Centre transféré vers une autre société | TransférerCentre | Admin société | — | `Breferential/.../organisation/service/SocieteDomainService.java:100` | code |
| 2 | Licence vérifiée en ligne | (cron 03:15) | Système | chaque nuit → bloque si licence invalide | `P/infrastructure/scheduling/LicenseOnlineCheckJob.java:49`, RG-SEC-032 | code |

### 2. Patient, couverture, prise en charge
| # | Événement | Commande | Acteur | Politique / déclencheur | Source | Confiance |
|---|---|---|---|---|---|---|
| 3 | Patient admis | AdmettrePatient (legacy `createPatient`) | Secrétaire | mouvement `ADMISSION` | `Bpatient/service/PatientDomainService.java:34`, `TypeMouvementPatient.java:10` | code (nom CRUD) |
| 4 | Assuré principal affecté (l'ancien est clôturé) | AffecterAssuréPrincipal | Secrétaire | — | RG-PAT doc 04 l.140-147 | doc |
| 5 | Prise en charge demandée | DemanderPEC | Secrétaire | statut `CREE` | `Bpec/service/PecDomainService.java:37`, `PecStatus.java:4` | code |
| 6 | Prise en charge validée (période et forfait effectifs) | ValiderPEC | Secrétaire/Admin | conditionne la facturabilité | `Bpec/model/PriseEnCharge.java:62` | code |
| 7 | Prise en charge clôturée | CloturerPEC | Secrétaire/Admin | → notifie le centre (RG-PEC-004) | `PriseEnCharge.java:70` | code |
| 8 | Attestation de droits enregistrée | EnregistrerAttestation | Secrétaire | cause de non-facturation si absente | `S/seance/port/SeanceBillingEligibilityPort.java:37` | à valider |
| 9 | Patient sorti (transfert, décès, greffe, guérison) / fin de séjour | DéclarerSortie | Secrétaire/Médecin | date d'évènement exigée (RG-PAT-031) | `TypeMouvementPatient.java:19-22`, RG-PAT-030 | code |
| 10 | **Place libérée** (salle, créneau, générateur, jours retirés) | (cron 04:00) + à l'enregistrement | Système | quand dernier jour d'occupation dépassé | `Bpatient/model/Patient.java:126`, `P/.../scheduling/LiberationPlacesScheduler.java:28`, RG-PAT-033 | code |

### 3. Planning, placement, infirmiers
| # | Événement | Commande | Acteur | Politique / déclencheur | Source | Confiance |
|---|---|---|---|---|---|---|
| 11 | Places proposées (score 0-100) | ProposerPlacement | Secrétaire/Admin | — (calcul) | `S/planning/service/PlanificationAffectationService.java:101`, RG-PLN-030 | code |
| 12 | **Patient placé** (salle/créneau/jours) | PlacerPatient | Secrétaire/Admin | refus si incohérent (RG-PLN-050), isolement auto (RG-PLN-053) | `P/application/planning/PlacementPatientService.java:98` | à valider |
| 13 | Optimisation du planning lancée | LancerOptimisation | Admin | — | `P/application/planning/optimisation/OptimisationPlanningService.java:82` | code |
| 14 | Optimisation terminée | — | Solveur | — | `S/planning/optimisation/model/RunOptimisation.java:59` | code |
| 15 | Proposition d'optimisation appliquée | AppliquerProposition | Admin | — | `RunOptimisation.java:75`, `OptimisationApplicationService.java:94` | code |
| 16 | Planning replanifié automatiquement | (cron 02:30) | Système | centres ayant activé l'option ; couverture absences infirmiers ≤ 8 sem. | `P/.../scheduling/ReplanificationAutomatiqueScheduler.java:30`, RG-PLN-100 | code |
| 17 | Absence d'infirmier déclarée | DéclarerAbsenceInfirmier | Infirmier / Admin | → déclenche besoin de remplaçant | `P/application/infirmier/AbsenceInfirmierService.java:39`, `MonPlanningInfirmierService.java:102` | code |
| 18 | Remplaçants proposés (classés) | ProposerRemplaçants | Admin | — | `S/infirmier/service/RemplacantService.java:44`, RG-INF-051 | code |
| 19 | Remplaçant affecté | AffecterRemplaçant | Admin | — | `P/application/infirmier/RemplacementInfirmierService.java:36` | code |
| 20 | Sous-effectif infirmier détecté (14 j) | (cron 07:15) | Système | → notification `INFIRMIER_SOUS_EFFECTIF` | `P/.../scheduling/PresenceInfirmierScheduler.java:45`, RG-INF-046 | code |

### 4. Réalisation de la séance (cœur pressenti)
| # | Événement | Commande | Acteur | Politique / déclencheur | Source | Confiance |
|---|---|---|---|---|---|---|
| 21 | Séance créée (programmée ou par scan QR) | CréerSéance | Infirmier/Système | statut `CREE` ; contrôle PEC (`canCreateSession`) | `S/seance/service/SeanceDomainService.java:92,147`, `PecDomainService.java:72` | code |
| 22 | Séance marquée hors planning (dérogation) | AccorderDérogation | Infirmier/Admin | motif obligatoire | `S/seance/model/Seance.java:60`, `MotifHorsPlanning.java:6` | code |
| 23 | Consommable de séance ajouté / modifié / retiré | AjouterConsommable | Infirmier | — | `SeanceDomainService.java:177,330,341` | code |
| 24 | **Séance validée par l'infirmier** | ValiderSéance (QR) | Infirmier | quand validée → sortie de stock des consommables (FEFO) | `Seance.java:90`, `SeanceDomainService.java:158,259` | code |
| 25 | Sortie de stock de séance enregistrée | (politique) | Système | lots consommés FEFO | `Bstock/service/BonSortieService.java:245,379`, `Lot.java:72` | code |
| 26 | Séance signée par le médecin | SignerSéance | Médecin | non exigé pour facturer (RG-FAC-020) | `Seance.java:178`, `SeanceDomainService.java:321` | code |
| 27 | Forfait de séance surchargé | SurchargerForfait | Admin/Médecin | prioritaire sur forfait PEC (RG-FAC-021) | `SeanceDomainService.java:241` | code |
| 28 | Séance déverrouillée pour régularisation | DéverrouillerPourRégularisation | Admin | seulement séance passée restée `CREE` → notifie infirmiers | `SeanceDomainService.java:251`, RG-SEA-046 | code |
| 29 | Régularisations en attente rappelées | (cron 07:00) | Système | → notification `SEANCES_A_REGULARISER` | `P/.../scheduling/SeanceRegularisationScheduler.java:41` | code |

### 5. Absences de patients
| # | Événement | Commande | Acteur | Politique / déclencheur | Source | Confiance |
|---|---|---|---|---|---|---|
| 30 | **Absence de patient détectée** (« à qualifier ») | (cron 02:30 + démarrage) | Système | jour de dialyse sans séance réalisée sur 7 j glissants | `P/.../scheduling/AbsencePatientScheduler.java:47,58`, RG-ABS-040 | code |
| 31 | Absence de patient déclarée | DéclarerAbsence | Infirmier/Secrétaire | refus si séance réalisée (RG-ABS-012) ou période facturée (RG-ABS-024) | `P/application/absence/AbsencePatientService.java:97` | code |
| 32 | Absence valorisée (montant TTC figé) | (politique à la création) | Système | forfait PEC × TVA active | `S/absence/service/ValorisationAbsenceService.java`, RG-ABS-030 | doc |
| 33 | Absence qualifiée (justifiée / non justifiée) | QualifierAbsence | Infirmier/Secrétaire | — | `S/absence/model/AbsencePatient.java:113` | code |
| 34 | Absence rattrapée (séance de rattrapage) | RattraperAbsence | Infirmier/Secrétaire | — | `AbsencePatient.java:137` | code |
| 35 | Absence annulée | AnnulerAbsence | Secrétaire | — | `AbsencePatient.java:157` | code |

### 6. Facturation et règlements
| # | Événement | Commande | Acteur | Politique / déclencheur | Source | Confiance |
|---|---|---|---|---|---|---|
| 36 | Séance exclue de la facturation (statut `ABSENT`) | ExclureSéance | Médecin/Admin | depuis la simulation | `Bfacturation/service/FacturationDomainService.java:77`, RG-FAC-023 | code |
| 37 | Forfait de séance changé en simulation | ChangerForfait | Médecin/Admin | — | `FacturationDomainService.java:89` | code |
| 38 | **Facturation validée** (factures émises, séances `FACTUREE`, prix/TVA/payeur figés) | ValiderFacturation | Admin/Médecin | → écriture de ventes ; → clôt absences ; → séances immuables | `FacturationDomainService.java:101`, `P/application/facturation/FacturationApplicationService.java:56` (`FacturationValideeEvent`) | code |
| 39 | Règlement de facture enregistré | EnregistrerRèglement | Secrétaire/Admin | → écriture de trésorerie | `Breglement/aggregate/FactureReglementAggregate.java:28`, `ReglementApplicationService.java:43` (`FactureReglementRecordedEvent`) | code |

### 7. Comptabilité
| # | Événement | Commande | Acteur | Politique / déclencheur | Source | Confiance |
|---|---|---|---|---|---|---|
| 40 | Écriture de ventes générée | (politique) | Système | quand facturation validée ; sauf période clôturée | `P/infrastructure/listener/FacturationValideeComptabiliteListener.java:41`, `ComptabiliteService.java:60` | code |
| 41 | Écriture de règlement générée | (politique) | Système | quand règlement enregistré ; sauf période clôturée (règlement conservé) | `ReglementEncaisseComptabiliteListener.java:37`, `ComptabiliteService.java:83` | code |
| 42 | Stock comptabilisé (différentiel rejouable, 35 j) | (cron 03:15) / ComptabiliserStock | Système / Admin | réceptions, sorties, inventaires | `P/.../scheduling/ComptabiliteStockScheduler.java:37`, RG-CPT-015 | code |
| 43 | Pièce comptable saisie | SaisirPièce | Comptable/Admin | — | `Bcomptabilite/service/PiecesComptablesService.java:86` | code |
| 44 | Écriture extournée | ExtournerÉcriture | Comptable/Admin | — | `PiecesComptablesService.java:128` | code |
| 45 | Écriture validée | ValiderÉcriture | Comptable/Admin | `BROUILLON → VALIDEE` | `Bcomptabilite/aggregate/EcritureComptable.java:96` | code |
| 46 | Écritures exportées (immuables) | ExporterÉcritures | Admin | vers logiciel comptable | `EcritureComptable.java:107`, RG-CPT-004 | code |
| 47 | **Période comptable clôturée** (mois) | CloturerPériode | Admin | bloque toute génération ultérieure | `Bcomptabilite/service/ComptabiliteService.java:143`, RG-CPT-005 | code |

### 8. Stock et articles
| # | Événement | Commande | Acteur | Politique / déclencheur | Source | Confiance |
|---|---|---|---|---|---|---|
| 48 | Bon de commande validé | ValiderBonCommande | Pharmacien | — | `Bstock/model/BonCommande.java:39` | code |
| 49 | Bon de commande reçu | — | Pharmacien | — | `BonCommande.java:49` | code |
| 50 | Réception validée (entrée de lots) | ValiderRéception | Pharmacien | → recalcul PMP | `Bstock/model/BonReception.java:42` | code |
| 51 | PMP recalculé (article verrouillé pendant le calcul) | — | Système | → réécriture comptable différentielle | `Bstock/service/PmpRecalculationCoordinator.java:114`, RG-STK-024 | à valider |
| 52 | Sorties de séance annulées | AnnulerSortiesSéance | Système/Infirmier | interdit si séance facturée (RG-SEA-002) | `BonSortieService.java:216` | code |
| 53 | Inventaire ouvert / reporté | OuvrirInventaire | Pharmacien/Admin | gèle le stock (RG-STK-052) | `InventaireStatut.java:10`, doc 08 l.176 | doc |
| 54 | **Inventaire clôturé** (période de stock close) | CloturerInventaire | Pharmacien/Admin | → mouvement `INVENTAIRE` ; mouvements antérieurs figés (RG-STK-056) | `Bstock/model/Inventaire.java:160` | code |
| 55 | Inventaire annulé | AnnulerInventaire | Pharmacien/Admin | — | `Inventaire.java:177` | code |
| 56 | Lots proches de l'expiration consignés | (cron 07:00) | Système | journal technique uniquement (pas de notification) | `P/.../scheduling/ExpirationAlertScheduler.java:38`, RG-STK-061 | code |

### 9. Suivi médical et greffe
| # | Événement | Commande | Acteur | Politique / déclencheur | Source | Confiance |
|---|---|---|---|---|---|---|
| 57 | Ordonnance signée / imprimée / annulée | SignerOrdonnance… | Médecin | signée ⇒ contenu verrouillé | `Bmedical/ordonnance/aggregate/Ordonnance.java:86,100,104` | code |
| 58 | Examen demandé → prélevé → résultat disponible → validé / annulé | DemanderExamen… | Médecin/Infirmier/Labo | — | `Bmedical/examen/aggregate/DemandeExamen.java:95,102,110,115` | code |
| 59 | Observation biologique enregistrée / corrigée | EnregistrerObservation | Médecin/Labo | → évaluation KDIGO ? | `Bmedical/observation/aggregate/ObservationBiologique.java:61,105` | code |
| 60 | Sérologie enregistrée / corrigée | EnregistrerSérologie | Médecin | — | `Bmedical/serologie/aggregate/Serologie.java:58,95` | code |
| 61 | Allergie déclarée | DéclarerAllergie | Médecin | — | `Bmedical/allergie/aggregate/Allergie.java:58` | code |
| 62 | Antécédent résolu | RésoudreAntécédent | Médecin | — | `Bmedical/antecedent/aggregate/Antecedent.java:100` | code |
| 63 | Traitement anémie administré (EPO/fer) | EnregistrerAdministration | Infirmier | — | `Bmedical/anemie/aggregate/AdministrationTraitement.java:70` | code |
| 64 | Retard d'observance constaté (alerte) / alerte résolue | (cron 06:30) / RésoudreAlerte | Système / Médecin | prescription dose × fréquence non respectée | `P/.../scheduling/ObservancePrescriptionScheduler.java:61`, `AlerteObservance.java:89` | code |
| 65 | Bilan pré-greffe ouvert (étapes standard générées) | OuvrirBilan | Néphrologue | — | `Bmedical/greffe/aggregate/BilanPreGreffe.java:69`, `EtapeBilanPreGreffeDomainService.java:54` | code |
| 66 | Décision RCP rendue (favorable / défavorable / ajourné) | RendreDécisionRCP | RCP | — | `BilanPreGreffe.java:125`, `AvisRcp.java:8-10` | code |
| 67 | **Statut greffe changé** (éligible, inscrit liste d'attente, CI temp./déf., greffe réalisée) | ChangerStatut (CRUD) | Néphrologue | greffe réalisée → sortie patient ? | `BilanPreGreffe.java:89`, `StatutBilanGreffe.java:4-10` | code (transitions à valider) |
| 68 | Donneur vivant enregistré | EnregistrerDonneur | Néphrologue | crossmatch | `Bmedical/greffe/aggregate/DonneurVivant.java:70` | code |

### 10. GMAO (équipements)
| # | Événement | Commande | Acteur | Politique / déclencheur | Source | Confiance |
|---|---|---|---|---|---|---|
| 69 | Intervention planifiée / démarrée / terminée / annulée / rectifiée | DémarrerIntervention… | Technicien | terminée → état équipement après ; temps valorisé (RG-GMA-027) | `Bgmao/model/Intervention.java:184,202,250,294`, `EvenementIntervention.java:17-22` | code |
| 70 | Équipement mis hors service | MarquerHorsService | Technicien | → indisponibilité (RG-GMA-060) → impact planning ? | `Bgmao/model/Equipement.java:164` | code |
| 71 | Équipement réformé | Réformer | Admin/Technicien | proposition auto (RG-GMA-063) | `Equipement.java:218` | code |
| 72 | Maintenance préventive exécutée | EnregistrerExécution | Technicien | — | `Bgmao/model/PlanMaintenance.java:115` | code |

### 11. Direction (lecture)
| # | Événement | Commande | Acteur | Politique / déclencheur | Source | Confiance |
|---|---|---|---|---|---|---|
| 73 | Instantané mensuel de la société figé (jamais recalculé) | (cron 1er du mois 02:30) | Système | ou à la 1re demande d'un mois écoulé | `P/.../scheduling/DirectionSnapshotScheduler.java:20`, RG-DIR-090 | code |
| 74 | Alerte de direction levée | — | Système | seuils (sévérité) ; temps réel 1 s/10 s | `P/application/direction/DirectionAlertPolicy.java:57`, `DirectionRealtimeScheduler.java:21,26` | à valider |

Exclus (techniques) : `AuditScheduler.java:27,32` (flush/purge audit), `NotificationPurgeScheduler.java:29`, `OptimisationPlanningRecovery.java:26`.

**Comptage** : 74 lignes d'événements (≈ 85 en dépliant les cycles #57, #58, #69) ; 58 commandes nommées ; 15 crons dont 11 portent une politique métier ; 4 politiques par événement applicatif (`FacturationValideeEvent`, `FactureReglementRecordedEvent`, validation séance → sortie de stock, facture → clôture des absences).

## Pivotal events (changements de phase)
1. **Patient admis** (#3) → entrée dans le centre ; **Place libérée** (#10) → sortie de l'activité de dialyse.
2. **Patient placé** (#12) → passage du « qui » au « quand/où ».
3. **Séance validée par l'infirmier** (#24) → la séance est « réalisée » (déclenche stock, ferme la détection d'absence).
4. **Facturation validée** (#38) → séances et absences figées, comptabilité alimentée.
5. **Période comptable clôturée** (#47) et **Inventaire clôturé** (#54) → verrouillage temporel.
6. **Statut greffe = greffe réalisée** (#67) → sortie de la dialyse (lien avec #9 à confirmer).

## Systèmes externes
| Système | Sens | Source |
|---|---|---|
| Logiciel comptable (export écritures) | sortant | `ExportComptablePort`, RG-CPT-004 |
| Serveur de licences | entrant/sortant | `LicenseOnlineCheckJob.java:49` |
| Échanges FHIR | sortant | `FhirExportService` (legacy-analysis) |
| Solveur Timefold | interne (algorithme) | `TimefoldOptimiseurAdapter` |
| Fichiers Excel/PDF (import feuille de comptage, rapports direction, documents) | entrant/sortant | `FeuilleComptageExcel`, `DirectionReportPdfService` |
| Laboratoire d'analyses | entrant ? | à valider (`SourceObservation`) |
| Caisses d'assurance (PEC, attestations) | à valider (saisie manuelle ?) | `caisse_assurance`, `agence`, `centre_payeur` |

## Read models
Planning semaine & capacité théorique (`PlanningSemaineService`, `CapaciteTheoriqueCalculator`) · poste infirmier « à régulariser » · simulation de facturation (`FacturationDomainService.java:44`) · tableau de bord facturation (RG-FAC-024) · tableau de bord règlements / situation de facture (`NON_REGLEE`/`PARTIELLEMENT_REGLEE`/`REGLEE`, trop-perçu) · tableau de bord stock & alertes (RG-STK-060) · tableaux de bord direction + instantanés · notifications (catalogue RG-NOT) · récap séances d'un patient (`SeanceDomainService.java:199`).

## Hotspots / désaccords (rose vif)
| # | Hotspot | Pourquoi |
|---|---|---|
| H1 | **`ABSENT` (statut de séance) ≠ Absence de patient ≠ Absence d'infirmier** | même mot, 3 concepts ; `ABSENT` = exclue de facturation, pas « patient absent » (RG-FAC-023) |
| H2 | **« Valider »** a 6 sens : séance (infirmier), PEC, facturation, écriture, bon de commande/réception, examen | homonyme d'action ; risque de confusion des cycles de vie |
| H3 | **« Clôture »** : période comptable (mois), inventaire, PEC, intervention ; et « clôture des absences par la facture » — aucune clôture mensuelle de la *facturation* elle-même | quelle est la clôture de référence pour le métier ? |
| H4 | **« Compte »** : compte comptable SCF, compte client 411 (patient / payeur), centre payeur « sans compte propre », compte d'article (stock), compte utilisateur | ≥ 4 sens |
| H5 | **Patient** : état (PERMANENT, OCCASIONNEL, VACANCIER_*, TRANSFERE, DECEDE, GREFFE, GUERRI) vs `PatientType` VACANCIER/NON_VACANCIER vs mouvements (ADMISSION, TRANSFERT, PLACE_LIBEREE…) vs Assuré | trois modèles du statut patient ; patient ≠ assuré ? |
| H6 | **Séance** : acte de soin (volets, consommables) vs ligne facturable (forfait) vs motif de sortie de stock | même identifiant, besoins différents ; facturation lit les séances en SQL direct |
| H7 | **Greffe** : état patient `GREFFE`, mouvement `GREFFE`, statut bilan `GREFFE_REALISEE` | une greffe réalisée déclenche-t-elle la sortie du patient ? |
| H8 | **Ordonnance** vs **Prescription médicale** (`prescriptions_medicales`, observance EPO/fer) | synonymes ou deux documents ? |
| H9 | Crons qui portent des règles métier (11 sur 15) : ordre implicite 02:30 absences → 04:00 libération des places ; 02:30 replanification ; 03:15 compta stock | dépendances temporelles non explicites |
| H10 | Lots proches expiration : contrôle sans notification (RG-STK-061) | règle douteuse ou incomplète |
| H11 | Signature médecin non exigée pour facturer (RG-FAC-020) | règle douteuse ? |
| H12 | Changement de statut greffe via `changerStatut` générique (CRUD) | transitions autorisées inconnues |
| H13 | Centre / Center / Société / Agence / Centre payeur | hiérarchie organisationnelle vs payeurs, deux langues |

## Regroupements par vocabulaire (indices pour la phase stratégique, non décisionnels)
Patient & couverture (#3-10) · Planning & présence infirmière (#11-20) · Réalisation de séance (#21-29) · Absences patients (#30-35) · Facturation & règlements (#36-39) · Comptabilité (#40-47) · Stock (#48-56) · Suivi médical & greffe (#57-68) · GMAO (#69-72) · Direction (#73-74). Frontières linguistiques candidates : H1, H4, H5, H6.

## Réponses de l'utilisateur (2026-10-09)
- Q1 : `ABSENT` = « exclue de facturation » → à renommer dans le langage cible.
- Q2 : « Séance » = trois concepts distincts (acte de soin, ligne facturable, motif de sortie de stock).
- Q5 : clôtures mensuelles attendues : comptable, facturation, absences, stock/inventaire (facturation et absences à créer).
- Q9 : facturer une séance non signée par le médecin est voulu (RG-FAC-020 conservée).

## Questions ouvertes (restantes, à valider par les experts)
3. (H5) Patient vs Assuré : un patient peut-il être couvert par un assuré différent de lui (ayant droit) ? Quel est *le* statut d'un patient : état, type vacancier, ou dernier mouvement ?
4. (H4) Lister les « comptes » que vous manipulez ; « centre payeur » = caisse/agence qui paie ?
5. (H3) Quelle clôture mensuelle attendez-vous : comptable seule (existe), facturation, absences (à définir selon la mémoire projet) ?
6. (H9) Les crons de nuit : ordre impératif ? la replanification automatique doit-elle s'exécuter avant ou après la détection des absences (même heure 02:30) ?
7. (H7) Greffe réalisée ⇒ sortie automatique du patient et libération de place ?
8. (H8) Ordonnance et prescription médicale : même document ?
10. (H10) Les lots proches de l'expiration doivent-ils alerter quelqu'un ?
11. (#12) Le placement d'un patient est-il un acte explicite (qui le fait, quand) ou un effet de l'optimisation ?
12. (#51) Le recalcul de PMP est-il un événement métier visible (pharmacien) ou un détail de calcul ?
