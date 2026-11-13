---
status: proposed
date: 2026-11-06
historique: glossaire initial daté du 2026-10-09 (statut `validated` à l'époque) ; depuis la reprise sur place PC1 à PC5 (ADR 0076 à 0079) TOUS les termes du contexte portent le statut `proposed` dans le tableau : l'en-tête est aligné ; la validation de chaque terme relève des experts métier (l'assistant n'en valide aucun)
context: Patient & couverture (supporting)
sources: [.ddd/discovery/glossary.md, .ddd/discovery/event-storming.md §2]
---
# Glossaire — Patient & couverture

| Terme métier | Définition | Identifiant cible | Nom legacy | Synonymes à bannir | Règle / exemple | Statut |
|---|---|---|---|---|---|---|
| Patient | Personne suivie par un centre, vue sous l'angle administratif | `Patient` | `patients` | client, user | — | proposed |
| Admission | Entrée du patient dans un centre | `Patient.admettre()` / `PatientAdmis` | `createPatient` | création | mouvement `ADMISSION` | proposed |
| État du patient | Situation administrative (permanent, occasionnel, vacancier, transféré, décédé, greffé, guéri) | `EtatPatient` | `etat`, `PatientType` | statut, type | unification avec `PatientType` à décider (H5) | proposed |
| Mouvement de patient | Événement de vie administrative | `MouvementPatient` | `TypeMouvementPatient` | — | « place libérée » sort vers Planification (ADR 0005) | proposed |
| Sortie | Fin de prise en charge dans le centre (transfert, décès, greffe, guérison) | `Patient.declarerSortie()` / `PatientSorti` | `updatePatient` | — | date d'évènement exigée (RG-PAT-031) | proposed |
| Assuré | Titulaire des droits d'assurance rattaché au patient | `Assure` | `bc-assure` (retiré, S6c) | — | un seul assuré principal à la fois | proposed |
| Prise en charge (PEC) | Accord d'un payeur couvrant une période et un forfait | `PriseEnCharge` | `PriseEnCharge`, `PecStatus` | — | `CREE → VALIDEE → CLOTUREE` ; période demandée vs effective | proposed |
| Forfait de PEC | Prix HT de la séance prévu par la PEC | `ForfaitPec` | `forfaitId` | tarif | — | proposed |
| Attestation de droits | Justificatif de droits du patient : période, début et fin comprises, pendant laquelle les droits sont ouverts | `AttestationDroit` (PC4 ; `bc-insurance` retiré le 2026-11-04) | `ATTESTATION_DROITS` (cause de non-facturation) | — | absente ⇒ cause de non-facturation ; entité simple, aucun contrôle entre attestations (à décider avec les métiers) | proposed |

Homonymes : *Greffe* (état de sortie, ici) ≠ *Greffe réalisée* (statut de bilan, Dossier médical).

## Termes ajoutés par la reprise PC1 (2026-11-03, statut `proposed`, ADR 0076)

Le code et l'API gardent le vocabulaire du **legacy** ; le glossaire donne le terme métier. Identifiants du code : `EtatDuPatient` (valeurs `PERMANENT`, `OCCASIONNEL`, `VACANCIER_LOCAL`, `VACANCIER_ETRANGER`, `TRANSFERE`, `DECEDE`, `GREFFE`, `GUERRI`), `TypeDeMouvement` (`ADMISSION`, `SEJOUR_TEMPORAIRE`, `REPRISE`, `TRANSFERT`, `DECES`, `GREFFE`, `GUERISON`, `PLACE_LIBEREE`).

| Terme métier | Définition | Identifiant du code | Notes |
|---|---|---|---|
| Fiche du patient | Contenu administratif saisi d'un patient (état civil, coordonnées, suivi, couverture, affectation) | `Fiche` | `Patient.admettre`, `Patient.modifier` |
| Transféré | Patient parti vers un autre centre | `TRANSFERE`, mouvement `TRANSFERT` | état de sortie |
| Guéri | Patient sorti pour guérison | `GUERRI`, mouvement `GUERISON` | état de sortie |
| Séjour limité | Occasionnel ou vacancier : l'évènement est la fin de séjour | `OCCASIONNEL`, `VACANCIER_*` | date de fin obligatoire |
| Reprise | Retour à l'état permanent | `REPRISE` | datée du jour du centre sans date |
| Date d'évènement | Date de sortie ou de fin de séjour | `dateEvenement` (colonne `date_evenement_etat`) | jamais avant l'admission |
| Numéro d'assurance | Identifiant d'assurance du patient, unique par centre, normalisé | `NumeroAssurance` | espaces de bord retirés, majuscules |
| Garde de centre | Toute lecture et toute écriture se font dans le centre du jeton | `SessionCourante` | 403 sinon |
| Jour du centre | « Aujourd'hui » au fuseau du centre, y compris pour le tableau de bord et la séance sans date (PC4) ; jamais le jour de la JVM | `HorlogeDesCentres` (module) ; `JourDuCentre` / `JourDuCentreLegacy` de la plateforme retirés (PL3, ADR 0138) | clé `app.planification.fuseau-par-defaut`, `centers.fuseau_horaire` |

## Termes ajoutés par l'ADR 0121 (2026-11-10, référentiels orphelins ; statut `proposed`, à confirmer métiers <= 2026-12-15)

| Terme métier | Définition | Identifiant du code | Notes | Statut |
|---|---|---|---|---|
| Catalogue de forfaits | Liste des forfaits (code, libellé, prix HT) d'un centre dont les PEC choisissent leur forfait | `CatalogueDeForfaits` (port), `GererLeCatalogueDeForfaits`, `ConsulterLesForfaits`, `ForfaitsDuCentre` et `GestionDuCatalogueDeForfaits` (langage publié, RF2) ; table `forfait` | « forfait » a trois sens : de PEC (ci-dessus), appliqué (Facturation), du centre (Séance) ; prix nul : sens à définir | proposed |
| Forfait (du catalogue) | Prestation tarifée d'un centre : code (clé, sans casse ni espaces répétés, unique par centre), libellé, prix hors taxe facultatif | `Forfait` (domaine, RF2) ; table `forfait`, `prise_en_charge.forfait_demande_id`, `forfait_effectif_id` | prix : zéro permis, négatif refusé, arrondi au centime ; « tarif » est un synonyme à ne pas employer ici | proposed |
| Référencer un forfait | Inscrire un forfait dans le catalogue d'un centre | `Forfait.referencer` | impératif du domaine ; saisie refusée avec toutes ses anomalies (code, libellé, prix) | proposed |
| Usage d'un forfait | Nombre de références qui retiennent un forfait (prises en charge, séances qui l'imposent, lignes de facture) : un forfait utilisé ne se supprime pas | `UsageDuForfait` (langage publié) | chaque propriétaire de référence publie son comptage ; PC les additionne | proposed |
| Transporteur | Prestataire de transport du patient (aller, retour) | `Transporteur` (domaine, RF1), `GestionDesTransporteurs`, `Transporteurs` (langage publié) ; table `transporteur`, `patients.transporteur_aller_id`, `transporteur_retour_id` | clé : nom (sans casse ni espaces répétés), unique par centre ; téléphone facultatif | proposed |
| Catégorie de transport | Classe de transport du patient | `CategoriesDeTransport` (langage publié, lecture seule) ; table `categorie_transport` | lien patient à confirmer ; aucun écrivain (KB-RF1-02) | proposed |
| Référencer un transporteur | Inscrire un transporteur dans le carnet d'un centre | `Transporteur.referencer` | impératif du domaine ; saisie refusée avec toutes ses anomalies | proposed |
| Carnet de transporteurs | Les transporteurs d'un centre | port `CarnetDeTransporteurs` | — | proposed |
| Caisse d'assurance | Organisme d'assurance maladie dont dépendent les agences ; type STANDARD ou VACANCIER | `CaisseDAssurance` (domaine, RF3a), `Caisses` (port) ; table `caisse_assurance` | clé : code (sans casse ni espaces répétés), unique par centre | proposed |
| Agence | Antenne d'une caisse d'assurance, dont dépendent les centres payeurs | `Agence` (domaine, RF3a), `Agences` (port) ; table `agence` | une agence exige une caisse ; clé : code unique par centre | proposed |
| Centre payeur | Lieu de prise en charge financière d'un patient (attribut du patient) : code, nom, adresse facultative, agence | `CentrePayeur` (domaine, RF3a), `CentresPayeurs` (port) ; table `centre_payeur`, `patients.centre_payeur_id` | à ne pas confondre avec « centre » (Organisation) ; Facturation le fige à l'émission (instantané) | proposed |
| Chaîne des payeurs | Caisse d'assurance, puis agence, puis centre payeur | `Payeur` (interface scellée), `GererLesPayeurs`, `GestionDesPayeurs` et `RefusDePayeur` (langage publié) | `PayeursDuCentre` (annuaire en lecture seule, borné au centre, RF3b, ADR 0127) : `ChaineDuPayeur` = centre payeur + agence + caisse du centre ; lu par la Séance, le Pilotage, les impressions, la Comptabilité (`PayeursParLAnnuaire`) et la Facturation (agence figée à la reprise des soldes) ; les listes de choix des écrans sont `ChoixDesPayeurs` (RF3c, ADR 0128) | proposed |
| Référencer un payeur | Inscrire une caisse, une agence ou un centre payeur dans la chaîne d'un centre | `CaisseDAssurance.referencer`, `Agence.referencer`, `CentrePayeur.referencer` | impératif du domaine ; saisie refusée avec toutes ses anomalies | proposed |
| Caisse d'assurance | Organisme d'assurance (STANDARD ou VACANCIER), niveau 1 de la chaîne payeur | table `caisse_assurance` | appartient à PC (ADR 0121), pas à Facturation | proposed |
| Agence | Antenne d'une caisse, niveau 2 | table `agence` | idem | proposed |
| Centre payeur | Payeur rattaché à une agence, attribut du patient ; ≠ « centre » (Organisation) | table `centre_payeur`, `patients.centre_payeur_id` | Facturation en fige l'agence et le centre à l'émission | proposed |

## Termes ajoutés par la reprise PC5 (2026-11-04, ADR 0079)

| Terme métier | Définition | Identifiant du code | Notes | Statut |
|---|---|---|---|---|
| Langage publié de Patient et couverture | Lectures par intention offertes aux autres contextes, bornées au centre, sans règle nouvelle | `patientcouverture.v1` (`publishedlanguage`) | remplace les lectures SQL directes | proposed |
| Fiches des patients | Existence, noms et situation (inconnu, suivi, parti) des patients d'un centre | `FichesDesPatients` | port publié | proposed |
| Couverture des patients | Forfait applicable, prises en charge tarifées, cause de non-facturation, PEC validée | `CouvertureDesPatients` | port publié ; argent reproduit, à confirmer avec les métiers | proposed |
| Compteurs de couverture | Comptes du tableau de bord administratif (PEC, attestations, patients, effectif d'un jour) | `CompteursDeCouverture` | port publié | proposed |
| Cause de non-facturation | Pourquoi une séance du jour n'est pas facturable : aucune PEC couvrante, PEC non validée, attestation absente | `CauseDeNonFacturation` | trois étages dans cet ordre | proposed |
| Jalon de lecture | Source hors module qui lit encore les tables du patient en SQL, inscrite à la liste blanche datée | `TableDuPatientCouvertureTest` | raison et échéance par entrée | proposed |

## Termes ajoutés par la reprise PC4 (2026-11-04, ADR 0078)

Les termes « Attestation de droits » et « Jour du centre » sont définis une seule fois, plus haut. Tous les termes ci-dessous sont `proposed`.

| Terme métier | Définition | Identifiant du code | Notes | Statut |
|---|---|---|---|---|
| Attestation valide un jour | Attestation dont la période couvre ce jour, bornes incluses | `AttestationDroit.couvre`, `Attestations.valideLe` | conditionne PEC et séance du non-vacancier | proposed |
| Attestation de la fiche | Attestation saisie avec la fiche du patient ; jamais dupliquée si la même période existe | `CouvertureDeLaFiche.enregistrer` | message « Date fin avant date debut » | proposed |
| Patient introuvable pour l'attestation | Patient inexistant ou d'un autre centre | code `ATTESTATION_PATIENT_INTROUVABLE` | 422 ; à confirmer avec les métiers | proposed |
| Attestation introuvable | Identifiant inconnu du centre (ou d'un autre patient) à la suppression | code `ATTESTATION_INTROUVABLE` | 404, sans diffusion ; à confirmer avec les métiers | proposed |
| Attestation à échéance | Attestation dont la fin est entre le jour du centre et le seuil d'alerte, bornes incluses ; les échues sont exclues | compteur `attestationExpiring` | corrigé le 2026-11-12 (avant : échues comprises), à confirmer avec les métiers avant le 2026-12-15 (ADR 0138) | proposed |
| Tableau de bord administratif | Compteurs du centre (PEC créées et validées, PEC et attestations à échéance, attestations, patients) à un jour du centre, éventuellement filtrés par mois | `ConsulterLeTableauDeBordAdministratif`, `TableauDeBordAdministratifControleur` (PL3) | garde de centre, 400 sur saisie illisible (ADR 0138) | proposed |
| Délai d'échéance | Jours, à partir du jour du centre, qui fixent le seuil d'alerte (30 par défaut, zéro admis, négatif refusé) | `DelaiDEcheance` (champ `expirationDays`) | PL3 | proposed |
| PEC à échéance | PEC validée dont la fin demandée est entre le jour du centre et le seuil, bornes incluses ; les échues sont exclues | compteur `pecExpiring` | corrigé le 2026-11-11, à confirmer avec les métiers avant le 2026-12-15 (ADR 0138) | proposed |

## Termes ajoutés par RP1 (2026-11-13, statut `proposed`, ADR 0169)

| Terme métier | Définition | Identifiant du code | Notes |
|---|---|---|---|
| Annulation de la reprise | Suppression par Patient et couverture des patients, assurés et affectations qu'un lot de reprise a créés, bornée au centre ; l'appelant (outil de reprise) décide de l'ordre et du compte rendu des refus | `AnnulationDeLaReprise` (langage publié `patientcouverture.v1`), `AnnulerLaReprise` | comportement inchangé ; KB-S6a-8/9 à confirmer métiers/DPO <= 2026-12-15 |
| Données créées par le lot | Affectations et patients que le lot a créés : ils ne retiennent jamais l'assuré repris | `DonneesCreeesParLeLot` | |
| Assuré retenu hors du lot | Assuré repris encore rattaché à une affectation ou à un patient du centre que le lot n'a pas créés : il ne peut plus être supprimé | `assureRetenuHorsDuLot` | refus `MIGRATION_CANCEL_BLOCKED` |

## Termes ajoutés par la reprise PC2 et PC3 (2026-11-03, statut `proposed`, ADR 0077)

| Terme métier | Définition | Identifiant du code | Notes |
|---|---|---|---|
| Assuré principal | Assuré actif désigné comme principal du patient ; un seul à la fois (R1) | règles `AssurePrincipal` (règles sans état, pas un agrégat), colonne `is_primary` | tenu sous le verrou du centre ; agrégat `AffectationsDuPatient` = cible |
| Affectation d'assuré | Lien daté entre un patient et un assuré | `Affectation`, table `assure_patient` | active tant que sans date de fin |
| Assuré d'un autre centre | Assuré détenu par un autre centre : ne peut pas être capturé | code `ASSURE_D_UN_AUTRE_CENTRE` | 422 |
| Demande de prise en charge | Période et forfait demandés à la création de la PEC | `dateDebutDemande`, `dateFinDemande`, `forfaitDemandeId` | saisie aussi sur la fiche |
| Prise en charge accordée | Période et forfait effectifs fixés à la validation | `dateDebutEffectif`, `dateFinEffectif`, `forfaitEffectifId` | aucun contrôle de période ni de forfait (à décider) |
| Catalogue des notifications de la fiche | Messages diffusés par le contexte vers Notifications (`notifications.v1`), à tous les utilisateurs du centre, jamais au journal : `PATIENT_CREATED/UPDATED` (patientId, patientCode, nom, prenom), `PEC_VALIDATED/CLOSED` (pecId, patientId, patientNom), `PEC_DELETED`, `ATTESTATION_CREATED/DELETED` (identifiant, patientId facultatif) | `CatalogueDesNotificationsDeLaFiche`, `NotificationsDeLaFicheParLeCentreDeNotifications` | `notifyPatient*`, `notifyPec*`, `notifyAttestation*` | — | PL13a, ADR 0153 ; une identité incomplète fait échouer l'émission (reproduit) | proposed |
| Descripteur de référentiel | Description publiée par le contexte d'un référentiel qu'il possède (transporteurs, forfaits, caisses, agences, centres payeurs) : champs, alias d'en-têtes, exemples du modèle d'import, libellé, clé d'unicité, ordre d'import ; la façade d'administration la distribue sans la décrire | `DescripteurDeReferentiel`, `DescripteursDeReferentiels` | `ReferentialKind.TRANSPORTEUR/FORFAIT/CAISSE/AGENCE/CENTRE_PAYEUR` | — | PL11-1, ADR 0157 | proposed |
| Statut de prise en charge | `CREE`, `VALIDEE`, `CLOTUREE` | `StatutPec` | tout autre : refusé |
| Identifiant de PEC déjà utilisé | Demande de la fiche dont l'identifiant de PEC appartient à une autre PEC | code `PEC_IDENTIFIANT_DEJA_UTILISE` | fiche enregistrée, PEC non créée, 409 |
| PEC valide pour une séance | Deux règles aujourd'hui : `session-allowed` (statut + attestation du non-vacancier) et `SeanceBillingEligibilityPort` | `GererLesPec.autoriseUneSeance` | à décider avec les métiers |
