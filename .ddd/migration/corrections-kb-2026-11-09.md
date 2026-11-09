---
status: proposed
date: 2026-11-09
sources: .ddd/safety-net/known-bugs.md (KB-ORG-09/10 l.1312-1313, KB-MED-104/106 l.1732/1758/1772-1776, KB-SEA-71..99 l.1959-2034, KB-DIR-01..08 l.2042-2049) ; ADR 0100, 0101, 0102 à 0106, 0108, 0109 ; .ddd/safety-net/pilotage-direction.md §9 ; .ddd/migration/verifications-production.md §62 à §73 ; code actuel lu le 2026-11-09
---

# Catalogue de corrections des KB reproduites (refactoring sur place, ADR 0024)

Périmètre : KB-DIR-01 à 08, KB-MED-104, KB-MED-106, KB-SEA-71 à 99, KB-ORG-09, KB-ORG-10. Ce document ne modifie aucun code. Chemins relatifs à `backend/` ; `PD` = `contexts/pilotage-direction/src/main/java/com/hemodialyse/backend/contexts/pilotagedirection`, `SE` = `contexts/seance/src/main/java/com/hemodialyse/backend/contexts/seance`, `DM` = `contexts/dossier-medical/src/main/java/com/hemodialyse/backend/contexts/dossiermedical`, `PF` = `platform/src/main/java/com/hemodialyse/backend`.

Conventions : la dernière migration Flyway est `V53__alertes_observance_detail.sql` ; toute nouvelle migration est `V54` et suivantes. Le schéma Flyway est partagé entre PostgreSQL 16 et H2 des tests : ne pas utiliser `NOT VALID` ni `btrim` (syntaxe PostgreSQL seule). Chaque correction qui change un golden suit la règle : legacy archivé dans `_ecarts-legacy/`, nouveau golden `*_nouveau_code`, approbation humaine un par un (jamais par l'assistant). Chaque correction exige aussi : mise à jour de `known-bugs.md` (état « CORRIGÉ »), un ADR court (skill `adr-writing`), la mise à jour du référentiel `docs/reference/regles-de-gestion` et de son test garde, et la régénération de la doc fonctionnelle.

Écarts constatés entre les KB et le code actuel (vérifiés) :
- KB-SEA-71 est DÉJÀ CORRIGÉ (garde `facture_id IS NULL`, `SE/infrastructure/persistence/FacturationDesSeancesJdbc.java:96-97`, ADR 0097) ; la demande de correction est sans objet.
- KB-SEA-97, KB-SEA-98 sont DÉJÀ CLOS (retrait de `bc-seance`, ADR 0100) ; KB-SEA-92 aussi (hors périmètre).
- Les chemins `legacyseance/*`, `HistoriqueDesConstantesJdbc`, `KdigoGreffeQueryService`, `RisquesInfectieuxJdbc` cités par les KB n'existent plus ; la table de correspondance est donnée dans chaque section.
- `DirectionDashboardQueryService`, `DirectionBreakdownQueryService`, `DirectionIndicatorsQueryService`, `DirectionAlertHistoryService`, `DirectionSnapshotService` n'existent plus (reprises dans `PD`) ; `DirectionReportPdfService` (rendu) et `DatesIllisiblesDeLaDirectionAdvice` restent en plateforme.
- La Direction lit désormais « aujourd'hui » via `PD/application/HorlogeDuPilotage.java:23` (`LocalDate.now(horloge)`, `Clock.systemUTC()` câblé `PD/configuration/PilotageDirectionConfiguration.java:84`), non plus `LocalDate.now()` direct.

---

## 1. Pilotage direction et indicateurs cliniques (confidentialité)

### KB-DIR-01 + KB-MED-104 : fuite par différence des totaux (même règle, à traiter ensemble)

| Point | Contenu |
|---|---|
| 1. Comportement actuel | Chaque effectif de 1 à 4 est masqué par centre (`PD/domain/SeuilDAnonymat.java:19-21`, `DM/domain/EffectifMasque.java:19-21`), mais les totaux additionnent les BRUTS puis ne sont masqués que s'ils sont eux-mêmes < 5 : `PD/domain/BilanDuCentre.java:19-34` (`consolider`, `patients`, `patientsSousKt`, publiés par `patientsPublies()` l.36-42), `PD/domain/RepartitionDeLaSociete.java:86-101` (totaux par caisse, `CaisseDeLaSociete.patientsPublies()` l.169-173), `DM/application/CalculerLesIndicateursCliniques.java:55-59` et `Brut.publier()` l.88-91 (7 effectifs : vhb, vhc, vih, observance, greffeAttente, greffeBilan, greffes). Le rapport PDF réutilise ces valeurs publiées (`PD/application/ConstituerLeRapportDeSynthese.java:81, 104, 110, 174-182`) ; les instantanés figés contiennent les mêmes totaux (payload). Conforme à la KB : constat vérifié par lecture. |
| 2. Nature | Sécurité-confidentialité (priorité 1, condition de mise en production posée par l'ADR 0087). |
| 3. Correction retenue | **Suppression complémentaire du total** (option 9.1(a) de l'audit `pilotage-direction.md`) : un total (société, ou total d'une caisse) est publié `null` dès qu'AU MOINS UN des effectifs qui le composent est masqué (valeur de 1 à 4). Règle unique, nommée « suppression complémentaire des totaux », appliquée à : `totaux.patients`, `totaux.patientsSousKt` (vue d'ensemble), `caisseTotaux[].patients`, et les 7 consolidés cliniques. Elle est rejetée pour la seule vue par centre : les valeurs des centres restent masquées comme aujourd'hui. Implémentation : un indicateur « au moins un centre masqué » calculé AVANT le masque (`BilanDuCentre.consolider` et `Cellule` de `RepartitionDeLaSociete` portent un booléen `composeDeMasques` ; `Brut.fusionner` idem côté Dossier médical, un booléen par effectif). Choisie plutôt que (b) totaux sur les seuls centres publiés (le total ne serait plus un total), (c) arrondi par 5 (nouveau modèle) et (d) agrégats seuls (perd la vue par centre). Le chiffre d'affaires, les séances et les administrations ne sont pas des effectifs de patients : inchangés. |
| 4. Impact | Goldens à réapprouver : `characterization/pilotagedirection/pd1/tableau_de_bord/la_vue_d_ensemble_compte_l_effectif_` (étape « 02 fuite par difference »), `.../la_repartition_detaille_sexe_age_cai` (caisse « sans code »), `pd4/rapport/le_rapport_en_direct_porte_l_en_tete` ; `dossiermedical/dm8/direction/*` (`greffes`, `serologies`) et `dm9/direction/{l_effectif_masque_d_un_centre_se_ded,deux_centres_masques_publient_leur_s,les_serologies_positives_et_les_bila}` (les trois tests de dm9 EXISTENT justement pour prouver la déduction : ils deviennent les tests de la correction) ; tests unitaires `CalculerLesIndicateursCliniquesTest`, tests du domaine Pilotage (`BilanDuCentre`, `RepartitionDeLaSociete`) ; rejeu `pilotage-direction-pg16-rejeu.sh` (D1-D8 et §68.1) et détection §66.5. Aucune table, aucune migration. Consommateurs : le langage publié `indicateurs` du Dossier médical garde `Long` nullable (le champ consolidé devient plus souvent `null`) ; `IndicateursPublies`, `TableauDeBordPublie` (`PD/interfaces/publie/TableauDeBordPublie.java:52`) inchangés en forme. Frontend : tableaux Direction (vue d'ensemble, répartition, indicateurs) et PDF doivent déjà gérer `null` (« < seuil ») pour les lignes par centre ; à vérifier que les cartes « total société » le font aussi (sinon affichage vide). PDF : la ligne « Total société » et « Caisse non renseignée » s'impriment « < seuil » ; largeur de colonne à contrôler. |
| 5. Risque | (a) Les instantanés mensuels DÉJÀ figés contiennent les totaux bruts (immuabilité RG-DIR-090) : décider de les masquer à la lecture (règle dérivable du JSON : total publié `null` si une valeur de centre de la même colonne est `null`) ; ne PAS les réécrire en base. Les PDF et rapports déjà émis ne se rappellent pas. (b) Fuites résiduelles par recoupement HORS du périmètre de la KB : effectif patients d'un centre (vue d'ensemble) moins la somme de ses effectifs sexe/âge/caisse publiés ; consolidés cliniques de pourcentage sur de petits échantillons. À faire valider par le DPO (non traité ici). (c) Une société de deux centres dont un est petit n'aura plus jamais de total patients : effet voulu, à annoncer à la Direction. |
| 6. Taille | M (deux modules, 2 contextes, goldens nombreux, décision de lecture des anciens instantanés). |

### KB-DIR-02 : erreurs de requête en 500 au lieu de 400/405

| Point | Contenu |
|---|---|
| 1. Comportement actuel | `PF/infrastructure/web/DatesIllisiblesDeLaDirectionAdvice.java:33-34` ne reconnaît que `/absences`, `/stock-groupes`, `/capacite`, `/indicators` ; `/overview` (`DirectionDashboardRestController.java:95`), `/gmao` (:118), `/breakdown` (:166), `/report` (:178) ne sont pas dans la liste ; `GET /alerts/history?limit=abc` (:196-198, `int limit`) idem ; `POST /overview` : aucun gestionnaire de `HttpRequestMethodNotSupportedException` en dehors de `ApiExceptionHandler.handleUnexpected` (`:130`, 500). Un instantané au payload non JSON : 500 à la lecture (`DirectionSnapshotRestController.java:51-58`). Conforme à la KB. |
| 2. Nature | Bug d'intégrité d'API (erreur client rendue en 500, bruit d'alerte) ; pas de fuite. |
| 3. Correction retenue | (a) Ajouter `/overview`, `/gmao`, `/breakdown`, `/report`, `/alerts/history` à `ROUTES` : `MethodArgumentTypeMismatchException` devient 400 `BAD_REQUEST` (le 403 avant 400 est déjà géré par `droitsSuffisants`). (b) Dans le même advice, un handler `HttpRequestMethodNotSupportedException` limité aux chemins `/api/v1/direction/` : 405 avec en-tête `Allow`, code `METHOD_NOT_ALLOWED` (pas d'élargissement au reste de l'application). (c) Instantané illisible : 422 `INSTANTANE_ILLISIBLE` (règle métier, sans détail technique) à la lecture ; il reste listé (la liste n'ouvre pas le payload). Choisi plutôt qu'un advice du contexte Pilotage parce que les contrôleurs restent en plateforme (ADR 0102) et que la logique de priorité 403/400 est déjà là. |
| 4. Impact | Goldens `pd1/tableau_de_bord/les_bornes_de_periode_et_les_valeurs` (« date illisible selon la route »), `le_tableau_de_bord_est_reserve_a_la_` (droits), `pd3/alertes/*historique*`, `pd2/instantanes/*cloisonnement_et_anciens*`, `pd4/rapport/l_en_tete_sans_identite_retombe_sur_` ; `DatesIllisiblesDeLaDirectionAdviceTest`. Aucune migration. Frontend : aucun appel ne dépend du 500 (à vérifier par grep des intercepteurs d'erreur de `backend-api.service.ts`). Documentation OpenAPI/routes. |
| 5. Risque | Faible. Valider côté métier : le code `INSTANTANE_ILLISIBLE` et le libellé. Un instantané corrompu reste corrompu (réparation de donnée = hors périmètre). |
| 6. Taille | S |

### KB-DIR-03 : horloge de la JVM, pas le fuseau du centre

| Point | Contenu |
|---|---|
| 1. Comportement actuel | Horloge injectée mais figée sur UTC : `PD/application/HorlogeDuPilotage.java:23-27`, `PilotageDirectionConfiguration.java:84` ; tranches d'âge au jour de l'appel ; mois écoulé des instantanés `PD/application/CapturerLesInstantanesDuMoisEcoule.java` (`YearMonth.from(horloge.aujourdhui())`, `CapturerUnInstantaneMensuel.java:30`) ; cron 02:30 (`PD/interfaces/planifie/CaptureDuMoisEcoulePlanifiee.java`). `centers.fuseau_horaire` (V46) existe et est publié par Organisation (`AnnuaireDesCentres.fuseauHoraireDe`, `AnnuaireDesCentresJdbc.java`) mais n'est pas lu par la Direction. Conforme. |
| 2. Nature | Décision métier pure (modèle) : la société peut avoir plusieurs centres, plusieurs fuseaux ; `societes` n'a pas de fuseau. Effet réel : seulement entre 23 h et minuit UTC, et si le fuseau du centre diffère d'UTC. |
| 3. Correction retenue | **Non corrigeable en code sans décision de modèle.** Action de clôture honnête : documenter dans le glossaire du Pilotage que « aujourd'hui » et « mois écoulé » sont en UTC (option 9.3(d) de l'audit), et ajouter un test qui fige cette règle avec une `Clock` fixe à 23:30 UTC. Si la décision est prise : fuseau « de référence » de la société = fuseau du premier centre actif (ou `Africa/Algiers` par défaut comme A3/A4/P9), à lire via `AnnuaireDesCentres.fuseauHoraireDe`. |
| 4. Impact | Si décidé : `HorlogeDuPilotage` reçoit un fuseau par société ; goldens `pd1/.../les_bornes_de_periode_*` et `pd2/instantanes/*` (les mois sont relatifs à la JVM) à réapprouver ; instantanés mensuels : le mois écoulé change de définition pour ceux capturés de 23 h à minuit. |
| 5. Risque | Instantané capturé deux fois pour un mois ou jamais ; à valider par la Direction. |
| 6. Taille | S (documentation + test) ; L si le fuseau de société est créé (migration `societes`, API Organisation). |

### KB-DIR-04 : historique des alertes, valeur et nom figés

| Point | Contenu |
|---|---|
| 1. Comportement actuel | `PD/domain/RapprochementDesAlertes.java:10-36` : une alerte ouverte n'est jamais mise à jour ; réapparition = nouvelle ligne ; `PD/infrastructure/persistence/HistoriqueDesAlertesJdbc.java`. Conforme (reproduit par le lot C, ADR 0104). |
| 2. Nature | Décision métier pure (règle R2, audit §9.2). |
| 3. Correction retenue | **Aucune sans décision.** Une valeur « figée à la première apparition » est un choix d'historique plausible (valeur à l'ouverture) ; la changer modifie la sémantique de l'historique. Clôture honnête : documenter dans le glossaire que la valeur est celle de l'ouverture, test déjà en place (`ouverture_et_resolution`). |
| 4. Impact | Si option 9.2(b) : golden `pd3/alertes/ouverture_et_resolution` étape « 02 » ; aucune migration. |
| 5. Risque | Valeurs historiques réécrites si on met à jour à chaud ; décision métiers/DPO attendue avant le 2026-12-15. |
| 6. Taille | S (si (b)), sinon doc. |

### KB-DIR-05 : séance d'un patient d'une autre fiche, comptée en vue d'ensemble mais pas en caisse

| Point | Contenu |
|---|---|
| 1. Comportement actuel | `SE/infrastructure/persistence/SeancesRealiseesDesCentresJdbc.java:49` (`parCentre` sans jointure) contre `:81` (`parCaisse`, `INNER JOIN patients p ... AND p.center_id = s.center_id`). Même chaîne que KB-SEA-83. Conforme. |
| 2. Nature | Bug métier (incohérence entre deux chiffres de la même page). |
| 3. Correction retenue | Aligner `parCaisse` sur `parCentre` : `LEFT JOIN patients p` (borne de centre conservée) ; une séance sans fiche dans le centre tombe sur la caisse de code vide (`COALESCE(ca.code, '')` existe déjà) : la somme des caisses égale la vue d'ensemble. Choisi plutôt que de retirer la séance de la vue d'ensemble (perte d'activité réelle du centre) ; c'est aussi le prédicat de la vue d'ensemble et des absences, déjà majoritaire. À traiter avec KB-SEA-83 et 84 (même fichier). |
| 4. Impact | Goldens `pd1/tableau_de_bord/la_vue_d_ensemble_*` et `la_repartition_*` (B : 2 et 2), `seance/se7e/pilotage/repartition_par_caisse` ; contrat `PilotageEtRapportsDesSeancesContrat` (mémoire, H2, PG16 : le double en mémoire `PilotageEtRapportsDesSeancesEnMemoire` doit suivre) ; détection §68.4 et E1/E4 (`seance-se7e-pg16-rejeu.sh`). Le golden `breakdown` est PARTAGÉ avec KB-DIR-01 : ordonner les lots (voir plus bas). Rapport PDF : la ligne « caisse non renseignée » grossit. |
| 5. Risque | La ligne « sans code » change de valeur affichée (historique recalculé en direct ; les instantanés figés gardent l'ancienne). Valider que la caisse vide est bien le bon libellé pour des fiches transférées. |
| 6. Taille | S |

### KB-DIR-06 : instantanés mensuels (a à e)

| Point | Contenu |
|---|---|
| 1. Comportement actuel | (a) `"breakdown": null` pour un ancien instantané ; (b) mois futur : 422 `SNAPSHOT_INTROUVABLE` (`PD/application/ConsulterUnInstantane.java:11, 24`) ; (c) société active sans centre figée vide ; (d) trois `generatedAt` ; (e) payload illisible : 500, cf. KB-DIR-02. Conforme. |
| 2. Nature | (a) à (d) : comportements à confirmer, pas des défauts (l'audit §9.7 propose seulement (b) `MOIS_NON_TERMINE`) ; (e) intégrité d'API. |
| 3. Correction retenue | **(e) seule est corrigée**, avec KB-DIR-02(c). (a), (b), (c), (d) : clôture par documentation et tests existants ; (b) est déjà un 422 stable et lisible, aucun gain à changer le code. (c) : ne pas capturer une société sans centre est plausible mais change `tache_planifiee` sans bénéfice établi : question posée plus bas. |
| 4. Impact | Goldens `pd2/instantanes/*cloisonnement_et_anciens*` (pour (e) uniquement). |
| 5. Risque | Faible. |
| 6. Taille | S (comprise dans KB-DIR-02). |

### KB-DIR-07 : alertes de stock évaluées à la date de fin de période

| Point | Contenu |
|---|---|
| 1. Comportement actuel | Stock lu à `periode.fin()` (`PD/application/ConsulterLesIndicateursDeLaDirection.java`, ADR 0106) ; `valeurStock = stock_quantity × COALESCE(pmp_courant, 0)` ; seuil 0 = jamais sous le seuil. Conforme. |
| 2. Nature | Décision métier pure (date de référence). |
| 3. Correction retenue | **Aucune sans décision.** Les deux lectures sont défendables (rétrospectif contre état courant). Clôture honnête : documenter que le stock est évalué « à la fin de la période demandée » et conserver le golden `pd6/indicateurs/date_de_fin`. Le PMP absent valant 0 (valeur de stock sous-estimée) peut être traité à part (afficher « PMP absent ») : non retenu. |
| 4. Impact | Si changé : goldens `pd3/alertes/seuils`, `pd6/indicateurs/date_de_fin`, rejeu D6. |
| 5. Risque | Alertes différentes selon la période consultée ; décision Direction. |
| 6. Taille | S |

### KB-DIR-08 : répartition (sexe, âge, caisse, PDF)

| Point | Contenu |
|---|---|
| 1. Comportement actuel | `PD/domain/RepartitionDeLaSociete.java:113-116` (sexe par initiale, autre valeur = « autre »), `TrancheDAge.de(...)` (âge au jour de l'appel) ; ligne CASNOS à 0 par facture figée ; en-têtes d'âge coupés dans le PDF (`PF/application/direction/DirectionReportPdfService.java`). Conforme à la KB (« à confirmer par les métiers, pas des bugs avérés »). |
| 2. Nature | Décision métier pure, sauf l'en-tête PDF coupé (rendu / outillage). |
| 3. Correction retenue | Sexe, âge, ligne CASNOS : **aucune correction** (règles à confirmer ; documentées dans le glossaire). En-tête PDF « 18- 29 ans » : correction cosmétique optionnelle (espace insécable ou largeur de colonne) dans `DirectionReportPdfService` ; tous les goldens de texte PDF de `pd4` changent (le texte extrait), donc À NE PAS faire isolément : à regrouper avec la prochaine correction PDF (KB-DIR-01). |
| 4. Impact | Goldens `pd4/rapport/*` (texte). |
| 5. Risque | Aucun fonctionnel. |
| 6. Taille | S (doc) ; S (PDF, optionnel). |

### KB-MED-104 : voir KB-DIR-01 (même règle et mêmes lots).

---

## 2. Dossier médical (risque clinique)

### KB-MED-106 : un résultat DOUTEUX ou EN_COURS plus récent lève le risque infectieux

| Point | Contenu |
|---|---|
| 1. Comportement actuel | `DM/infrastructure/persistence/PatientsARisqueInfectieuxJdbc.java:18-25` (le `NOT EXISTS` ne cherche aucune sérologie du même marqueur plus récente, QUELLE QUE SOIT sa valeur) et sa forme Java `DM/domain/RisqueInfectieux.java` (méthode `patientsARisque`, `plusRecenteExiste`). Marqueurs surveillés : AG_HBS, AC_VHC, ARN_VHC, VIH_AC. Résultats possibles (`ResultatSerologique`) : POSITIF, NEGATIF, DOUTEUX, EN_COURS. Conforme à la KB (ancien chemin `RisquesInfectieuxJdbc.java:21-28`). |
| 2. Nature | Risque clinique (isolement, RG-PLN-012). |
| 3. Correction retenue | **Le risque n'est levé que par un résultat négatif conclusif.** Règle nommée : « dernier résultat tranché » : pour chaque patient et marqueur, on retient le dernier résultat dans {POSITIF, NEGATIF} (date de prélèvement, puis saisie) ; DOUTEUX et EN_COURS sont ignorés pour décider ; le patient est à risque si ce dernier résultat tranché est POSITIF. SQL : dans le `NOT EXISTS`, ajouter `AND n.resultat IN ('POSITIF','NEGATIF')` ; Java : filtrer `plusRecenteExiste` sur ces deux résultats. Un POSITIF suivi seulement de DOUTEUX/EN_COURS reste à risque ; un POSITIF suivi d'un NEGATIF plus récent sort du risque ; un DOUTEUX ou EN_COURS seul ne met jamais un patient à risque (il ne passe pas le filtre `s.resultat = 'POSITIF'`). Choisie plutôt que de traiter DOUTEUX comme positif (élargit la population isolée sans preuve). KB-MED-107 (égalité totale : le positif l'emporte) reste valable et compatible : inchangé. |
| 4. Impact | Goldens `dossiermedical/dm9/planification/seule_la_derniere_serologie_de_chaqu/derniere_serologie` (cas 07, 08) et `dm4/planification/la_regle_porte_sur_la_derniere_serol/derniere_serologie` à réapprouver ; `RisqueInfectieuxTest`, contrat `RisquesInfectieuxContrat` (+ `RisquesInfectieuxEnMemoireContratTest`, `RisquesInfectieuxJdbcContrat` H2 et PG16) ; `contexts/planification/.../contrat/RisquesInfectieuxDeTest` (double : à relire). Détection production §66.1 à relancer : « patients dont une sérologie plus récente DOUTEUX/EN_COURS masque un POSITIF » (liste des patients qui entreront en isolement). Aucune migration. Effet sur d'autres contextes : la Planification (placement, `ReaffecterEnIsolementSiRisque`, propositions de place) consomme le langage publié `RisquesInfectieuxPublies` ; davantage de patients sont isolés : charge des salles d'isolement et réaffectations automatiques au prochain placement ; la Direction compte ses porteurs positifs par une autre règle (KB-MED-105, `IndicateursCliniquesJdbc.porteursPositifs`) : divergence entre isolement et indicateur Direction à assumer ou à aligner (non traité ici). |
| 5. Risque | Patients aujourd'hui hors isolement qui y retournent (capacité de salles, notifications) ; à faire valider PAR LES MÉDECINS avant livraison (question plus bas), avec la liste de §66.1. Aucune donnée historique modifiée. |
| 6. Taille | S |

---

## 3. Séance : intégrité et argent

### KB-SEA-71 : DÉJÀ CORRIGÉ

| Point | Contenu |
|---|---|
| 1. Comportement actuel | `FacturationDesSeancesJdbc.java:94-98` : `lierALAbsence` porte `facture_id IS NULL AND statut = 'ABSENT' AND statut_avant_exclusion IS NOT NULL`. |
| 2. Nature | Déjà clos (ADR 0097, golden `lier_a_l_absence_une_seance_absente_deja_facturee_nouveau_code`). |
| 3. Correction retenue | Aucune. Action de clôture : passer la ligne de `known-bugs.md` à « CORRIGÉ (SE7c2 lot B) » sans phrase « NON corrigé » ; la garde est une défense en profondeur sans effet HTTP (ADR 0097). Détection §62.3 déjà disponible pour les données historiques. |
| 6. Taille | S (documentation) |

### KB-SEA-72 : mémoire d'exclusion blanche

| Point | Contenu |
|---|---|
| 1. Comportement actuel | `trouver` convertit le blanc en nul (`FacturationDesSeancesJdbc.java:77-79`, `avant.isBlank()`) ; `lierALAbsence` (:94-98) et `reinclure` (:102-106) testent `statut_avant_exclusion IS NOT NULL`. Conforme. |
| 2. Nature | Intégrité (argent : statut d'une séance facturable). |
| 3. Correction retenue | Aligner les deux écritures sur la lecture : remplacer `statut_avant_exclusion IS NOT NULL` par `statut_avant_exclusion IS NOT NULL AND LENGTH(TRIM(statut_avant_exclusion)) > 0` dans `lierALAbsence` et `reinclure` (SQL portable H2/PostgreSQL). Réclamé par la KB (« reproduire la garde du domaine dans le port »). Aucune migration ni contrainte de colonne (une contrainte exigerait la détection §62.6 d'abord). |
| 4. Impact | Golden `facturation/simulation/se7c2-ecritures/statut_avant_exclusion_blanc` ; contrat `SeancesContrat` (Facturation) et contrat PG16 `LiaisonConcurrentePostgresContractIT` n'ont pas à bouger ; détection §62.6 (SELECT existant) pour mesurer les lignes blanches. |
| 5. Risque | Faible : seule une ligne déjà incohérente est concernée ; sa réinclusion par le port était auparavant possible et devient refusée (« non réinclusible »). Valider qu'aucun flux n'utilise cette réinclusion de fait. |
| 6. Taille | S |

### KB-SEA-73 : casse du statut et garde du port

| Point | Contenu |
|---|---|
| 1. Comportement actuel | Statut comparé en casse exacte partout ; `exclure` appelé sur le port avec le statut lu (`validee`) réussit (`FacturationDesSeancesJdbc.java:88-92` : `statut = ?` reçoit le statut lu). Conforme. |
| 2. Nature | Intégrité (argent) pour le point de garde ; la casse elle-même relève de la donnée (voir KB-SEA-82). |
| 3. Correction retenue | (a) **Garde du port** : dans `exclure`, ajouter `AND statut IN ('VALIDEE','SIGNEE')` (le domaine l'impose déjà via `estFacturable()`, le port le redit). (b) La casse du statut : voir KB-SEA-82 (contrainte de donnée), NON corrigée ici. KB-FAC-17 reste reproduit. |
| 4. Impact | Golden `statut_en_minuscules_matrice_des_ecritures_et_lectures` (la cellule `exclure` passe de réussite à refus au niveau du port) ; contrat Facturation. |
| 5. Risque | Aucun sur le trafic HTTP (le domaine arrête déjà). |
| 6. Taille | S |

### KB-SEA-74 : unicité « une séance, une facture » et clé étrangère absente

| Point | Contenu |
|---|---|
| 1. Comportement actuel | `FacturationDesSeancesJdbc`/`LiaisonDesSeancesJdbc.java:28-30` : `WHERE ... facture_id IS NULL AND statut IN ('VALIDEE','SIGNEE')` + transaction d'émission ; aucune clé étrangère `seances.facture_id` vers `factures` (le champ est UNE colonne : l'unicité par séance est déjà structurelle ; le risque est l'identifiant orphelin ou d'un autre centre). Conforme. |
| 2. Nature | Intégrité / argent. |
| 3. Correction retenue | **Clé étrangère `seances.facture_id REFERENCES factures(id)`** (migration `V54__seances_facture_id_cle_etrangere.sql`, `ALTER TABLE ... ADD CONSTRAINT fk_seances_facture FOREIGN KEY (facture_id) REFERENCES factures(id)`, sans `ON DELETE CASCADE`). Pas de clé composite (un `UNIQUE (id, center_id)` sur `factures` change le schéma plus que nécessaire) ; l'appartenance au centre reste contrôlée par la requête 62.2. Le comportement concurrent (verrou de ligne, annulation entière) est inchangé et déjà prouvé sur PG16. |
| 4. Impact | **Détection préalable obligatoire EN PRODUCTION avant la migration** : `verifications-production.md` §62 requête 2 (« séances dont facture_id désigne une facture inexistante ou d'un autre centre ») : TOUTE ligne avec `f.id IS NULL` fait échouer la migration (Flyway s'arrête, démarrage bloqué) ; à réparer d'abord (décision écrite : remettre `facture_id` à NULL et statut `VALIDEE`, ou restaurer la facture) ; les lignes « autre centre » ne sont pas bloquées par la clé mais doivent être examinées. Tests : toutes les fixtures qui posent un `facture_id` sans créer la facture (tests H2 de la Séance, `SeancesContrat`, goldens de `se6`, `se7c2`) échoueront : à relire une par une ; vérifier qu'aucun flux ne supprime une facture avant de remettre `facture_id` à NULL (grep `DELETE FROM factures`). Schéma PG16 et H2 : même migration. |
| 5. Risque | Blocage du démarrage en cas d'orphelin ; refus de suppression d'une facture encore liée (voulu). À faire valider par la Facturation : conserver ou non la possibilité de supprimer des factures. |
| 6. Taille | M (migration + relecture des fixtures) |

### KB-SEA-75 : deux définitions de « séance facturée »

| Point | Contenu |
|---|---|
| 1. Comportement actuel | Tableau de bord : `statut = 'FACTUREE'` et date de séance ; répartitions et synthèse : `facture_id` des factures du mois (`FacturationDesSeancesJdbc.java:125` et suivantes, `SE/.../TableauDeBordDesSeances*`). Conforme. |
| 2. Nature | Décision métier pure (la KB demande « à trancher par les métiers »). |
| 3. Correction retenue | **Aucune sans décision.** Aucun des deux prédicats n'est faux ; en choisir un change des chiffres de gestion. Question plus bas. Clôture honnête provisoire : libellés distincts à l'écran (« séances au statut facturé » contre « séances rattachées à une facture du mois »). |
| 4. Impact | Si tranché : goldens `facturation/simulation/se7c2-ecritures/deux_definitions_*`, tableau de bord `seance/se7b`, synthèse ; détection §62.5. |
| 5. Risque | Chiffres mensuels qui bougent ; à valider par la comptabilité. |
| 6. Taille | M |

### KB-SEA-82 : casse et blancs du statut ignorés par les lecteurs (complète 70, 73)

| Point | Contenu |
|---|---|
| 1. Comportement actuel | `LecturesDesSeancesRealiseesJdbc.java:22` (`REALISEE = s.statut IN ('VALIDEE','SIGNEE','FACTUREE')`), :60 ; idem `SeancesRealiseesDesCentresJdbc.java:50, 60, 70, 83`, `SeancesDuCahierEtForfaitsImposesJdbc.java:27-28`. Conforme. |
| 2. Nature | Intégrité de donnée (statut chaîne libre `VARCHAR(255)` sans contrainte). |
| 3. Correction retenue | Pas de correction de code (normaliser les comparaisons en `UPPER(TRIM(...))` dans 5 lecteurs étendrait le périmètre et diverge de la Facturation, KB-FAC-17). **Correction de donnée gardée** : migration `V55__seances_statut_canonique.sql` : `ALTER TABLE seances ADD CONSTRAINT ck_seances_statut_canonique CHECK (statut = UPPER(TRIM(statut)))`, SEULEMENT si la détection §62.7 rend 0 ligne en production ; sinon, normalisation préalable validée statut par statut. Elle supprime en une fois les cas KB-SEA-70, 73 (casse), 82 et 88 (casse). |
| 4. Impact | Détection préalable en production : §62.7 (`statut <> upper(statut)`) ET une variante pour les blancs (`statut <> trim(statut)`) à ajouter à §62 ; tests H2 de la Séance qui insèrent un statut en minuscules ou avec blanc (matrices `matrice_des_statuts_et_de_la_casse`, `statut_en_minuscules_*`, `rattrapage_casse_et_doublons`) deviennent impossibles (contrainte) : ces scénarios ne peuvent plus être rejoués tels quels et seront remplacés par un test de la contrainte. |
| 5. Risque | Migration qui échoue si une ligne non canonique existe ; normalisation d'un `facturee` change les comptes du tableau de bord et du Stock (KB-SEA-70). À valider par métiers et Stock. |
| 6. Taille | M (migration, décision de nettoyage, remplacement de goldens) |

---

## 4. Séance : volet paramédical (écriture)

### KB-SEA-93 : le volet est écrit puis la réponse échoue (« Patient introuvable »)

| Point | Contenu |
|---|---|
| 1. Comportement actuel | `SE/application/RenseignerLeVoletParamedical.java:58-75` : l'écriture est dans `uniteDeTravail.executer(...)` (l.58-71), la fiche est relue APRÈS (l.73) ; absente du centre : `SaisieInvalide("Patient introuvable")`, volet en base, aucune diffusion. Conforme. |
| 2. Nature | Intégrité (écriture partielle sans transaction). |
| 3. Correction retenue | **Garde de la fiche AVANT l'écriture** : déplacer `patients.fiche(centre, trouvee[0].patient())` à l'intérieur de l'unité de travail, avant `volets.enregistrer(...)`, avec la même erreur 400 « Patient introuvable » ; les diffusions restent après. Choisi plutôt que de tout enrober d'une transaction (qui annulerait l'écriture mais maintiendrait un chemin d'échec tardif) : résultat identique pour l'appelant (400) sans écrire. |
| 4. Impact | Golden `seance/se7f/volet/fiche_hors_centre` (« volets en base » passe à 0) ; test d'application `should_keep_the_written_sheet_and_answer_patient_not_found_without_diffusion_when_the_record_is_in_another_centre` (à inverser) ; DM1 golden `fiche_hors_centre` du filet `dossiermedical/dm1`. Aucune migration. Détection : fiches de patient hors centre sur séances (§65, famille KB-SEA-80). |
| 5. Risque | Faible ; un volet déjà écrit par ce défaut en production reste en base (à lister, ne pas supprimer). |
| 6. Taille | S |

### KB-SEA-94 : volet posé sous un autre centre que sa séance

| Point | Contenu |
|---|---|
| 1. Comportement actuel | `SE/infrastructure/persistence/VoletsParamedicauxJdbc.java:37` lit `WHERE seance_id = ? AND center_id = ?` ; l'insertion (:57) viole `UNIQUE (seance_id)` : 500 `DATA_ACCESS_ERROR`. Conforme. |
| 2. Nature | Intégrité (donnée incohérente, 500). |
| 3. Correction retenue | **Réparer la donnée, puis lire par la séance** : (a) détection préalable §65.1 / requête 64.3 ; (b) si des lignes existent, migration `V56__volets_centre_de_la_seance.sql` : `UPDATE volet_paramedical SET center_id = (SELECT s.center_id FROM seances s WHERE s.id = volet_paramedical.seance_id) WHERE ... différent`, idem `volet_medical` ; (c) code : `deLaSeance` lit par `seance_id` seul (la séance a déjà été contrôlée au centre par `trouverPourModifier`), `UNIQUE (seance_id)` garantit une ligne. Pas de contrainte de base multi-tables (trigger) : le coût dépasse le bénéfice. Choisi plutôt que « lire par seance_id seul » sans réparation (les autres lecteurs, détail, cahier, volets fournis, resteraient aveugles). |
| 4. Impact | Goldens `seance/se7f/volet/volet_d_un_autre_centre` (500 devient réécriture), `detail_volets_au_centre` (le volet devient visible) ; contrat mémoire/H2/PG16 `should_refuse_a_second_sheet_for_the_same_session_even_under_another_centre...` ; statistiques (KB-SEA-85) : inchangées (séance seule). |
| 5. Risque | La migration réécrit un centre sur des données médicales : validation écrite du responsable données avant exécution (règle 20-legacy-safety n°7 : pas de modification destructive sans validation) ; une séance elle-même au mauvais centre (KB-SEA-69) propage l'erreur. |
| 6. Taille | M |

### KB-SEA-99 : corps illisible du PUT en 500 au lieu de 400

| Point | Contenu |
|---|---|
| 1. Comportement actuel | `SE/interfaces/http/ErreursDuVoletParamedicalAdvice.java` (assignable à `VoletParamedicalControleur`) omet volontairement les handlers de corps illisible et de conversion du chemin : 500 `INTERNAL_ERROR`. Conforme (KB). |
| 2. Nature | Bug d'API (parité DM1 reproduite, RG-SEA-069). |
| 3. Correction retenue | Ajouter à cet advice les handlers de `ErreursDeSeanceAdvice` pour `HttpMessageNotReadableException` et `MethodArgumentTypeMismatchException` : 400 `BAD_REQUEST`, 403 avant 400 conservé. Pas de modification des bornes de valeur (`duree_minutes`, débit : hors périmètre). |
| 4. Impact | Goldens `dossiermedical/dm1/ecriture/seance_inconnue_corps_invalide_et_ce/erreurs` (4 cas) et `.../duree_debit_et_ultrafiltration_n_ont/autres_mesures` (2 cas) ; test `EnregistrementDesConstantesDeSeanceCharacterizationIntegrationTest`. Frontend : aucun comportement attendu sur le 500. Doc RG-SEA-069. |
| 5. Risque | Faible. Valider le message de détail. |
| 6. Taille | S |

---

## 5. Séance : lectures, tris, cloisonnement

| KB | 1. Comportement actuel (lieu vérifié) | 2. Nature | 3. Correction retenue | 4. Impact | 5. Risque | 6. |
|---|---|---|---|---|---|---|
| KB-SEA-77 | `SE/infrastructure/persistence/LecturesDesSeancesRealiseesJdbc.java:35-36` (`joursRealisesDuCentre`, `SELECT DISTINCT s.patient_id, s.date_seance ... ORDER BY s.date_seance`) | Outillage/tri non déterministe | `ORDER BY s.date_seance, s.patient_id` (colonnes déjà dans la sélection `DISTINCT`). Les deux autres tris (:44 sur dates distinctes) sont déjà déterministes. | Golden `seance/se7d/absences/semaine_une_ligne_par_patient_et_par_jour` (ordre intra-jour jusque-là non verrouillé : le golden trie) : inchangé ; contrat `SeancesRealiseesEtConstantesContrat` | Aucun (affichage) | S |
| KB-SEA-78 | `LecturesDesSeancesRealiseesJdbc.java:73-77` (`duCentre`, `ORDER BY s.date_seance`) ; la Javadoc annonçait « date puis identifiant ». `planification/.../domain/SemaineDuPlanning.java:156` garde la première lue | Bug métier mineur (case affichée aléatoire) | `ORDER BY s.date_seance, s.created_at NULLS LAST, s.id` : la première lue devient la plus ancienne créée, déterminisme prouvé sur H2 et PG16 ; règle écrite dans le port publié. Choisi plutôt que « dernière créée » : conserve l'intention du code de la Planification (première rencontrée). | Golden `seance/se7d/planification/deux_seances_le_meme_jour` (laquelle des cases reste devient figée) ; contrat | La case affichée change pour un patient avec deux séances le même jour (rare) | S |
| KB-SEA-79 | `SE/infrastructure/persistence/ConstantesDesSeancesJdbc.java:24` (`ORDER BY s.date_seance DESC, s.created_at DESC, s.id DESC`, `created_at` nullable) : H2 nulle en dernier, PG16 nulle en premier | Divergence SGBD | `s.created_at DESC NULLS FIRST` : conserve l'ordre de PRODUCTION (PG16), aligne H2 dessus ; alternative sémantiquement plus juste `NULLS LAST` (une création inconnue est la plus ancienne), écartée parce qu'elle change l'ordre déjà servi aux utilisateurs. | Golden `seance/se7d/dossier-medical/creation_nulle_sur_h2` (à renommer, plus rien de spécifique à H2) ; détection : `SELECT center_id, count(*) FROM seances WHERE created_at IS NULL GROUP BY center_id` : si 0, aucune ligne concernée et le choix devient indifférent | Faible | S |
| KB-SEA-80 | `LecturesDesSeancesRealiseesJdbc.java:74-75` : `JOIN patients p ON p.id = s.patient_id AND p.center_id = s.center_id` ; la branche `prenom == null` reste inatteignable (prénom NOT NULL) | Décision métier pure (dépend du transfert de patient) | **Aucune sans décision.** `LEFT JOIN` ferait apparaître la séance dans la semaine sans nom (pas d'identité d'une fiche d'un autre centre) mais la Planification n'a pas de « case sans identité » ; décision liée au transfert (la fiche change-t-elle de `center_id` ? ADR transfert). Question plus bas. | Goldens `fiche_dans_un_autre_centre`, `chaque_centre_ses_seances` ; rejeu P1 | Fuite de nom inter-centres si `LEFT JOIN` non borné | S après décision |
| KB-SEA-81 | `LecturesDesSeancesRealiseesJdbc.java:58-61` (`absenceLiee` : `center_id = ? AND exclusion_absence_id = ?` sans statut) | Bug métier (blocage d'une annulation par un lien résiduel) | Ajouter `AND statut = 'ABSENT'` : seul un lien porté par une séance réellement exclue bloque l'annulation. Le lien résiduel d'une séance revenue à `VALIDEE` ne bloque plus ; il reste en base (nettoyage = UPDATE de données, non fait ici). | Golden `seance/se7d/absences/annulation_et_lien_d_exclusion` (port et HTTP) ; détection SELECT donnée par la KB (`... statut <> 'ABSENT' ...`, §63) | Une annulation d'absence devient possible malgré un lien orphelin : valider la règle avec le métier absences ; ne pas nettoyer les liens sans validation | S |
| KB-SEA-83 | `SeancesRealiseesDesCentresJdbc.java:49, 59` (sans jointure) contre `:81` (jointure patient bornée) ; mêmes lecteurs pour `/absences` | Bug métier (voir KB-DIR-05) | Même correction que KB-DIR-05 (`LEFT JOIN` dans `parCaisse`) : un seul prédicat, celui de la séance elle-même, pour les trois vues. | Goldens `seance/se7e/pilotage/repartition_par_caisse`, `vue_d_ensemble`, `taux_d_absenteisme` ; contrat `PilotageEtRapportsDesSeancesContrat` ; rejeu E1, E4, E5 | Idem DIR-05 | S (avec DIR-05) |
| KB-SEA-84 | `SeancesRealiseesDesCentresJdbc.java:24-27` (`CAISSE_JOIN` : `cp.id = p.centre_payeur_id` sans `cp.center_id`) ; tri `PD/domain/RepartitionDeLaSociete.java:95-98` sur le centre puis `caHt` | Bug métier + tri non déterministe | (a) borner le centre payeur : `AND cp.center_id = p.center_id` ; une chaîne payeur d'un autre centre tombe sur la caisse vide ; (b) départage du tri Java : `thenComparing(code)` après `caHt` décroissant. Le chiffre d'affaires par caisse suit sa propre source (`FacturationParLaFacturation`), à vérifier que la même borne y existe sinon la matrice des caisses perd sa cohérence (lecture à faire avant). | Golden `seance/se7e/pilotage/repartition_par_caisse` ; goldens `pd1/.../la_repartition_detaille_sexe_age_cai` et `pd4/rapport/*` (ordre de lignes) ; rejeu E4 | La caisse d'un patient dont le payeur est d'un autre centre change de ligne | S |
| KB-SEA-85 | `SE/infrastructure/persistence/StatistiquesDesSeancesJdbc.java:50-53` (`baseFrom` : aucun statut ; `LEFT JOIN volet_paramedical vp ON vp.seance_id = s.id` sans centre) ; PDF `PF/application/query/PatientStatsQueryService.java:137` (`Seances: 6.00`) | (a) cloisonnement ; (b) décision métier ; (c) cosmétique | (a) **Borner le volet au centre** : `AND vp.center_id = s.center_id` (comme le cahier et le Dossier médical). (b) **Statuts : décision, non corrigé** (statistiques des séances réalisées seulement ? question plus bas). (c) Formater `seanceCount` en entier dans le PDF (`PatientStatsQueryService:137`). | Goldens `seance/se7e/statistiques/tous_statuts_et_volet_d_un_autre_centre`, `export_pdf` (texte) ; contrat ; rejeu E6 | Moyennes qui perdent les volets d'un autre centre (rares : requête 64.3) ; après réparation KB-SEA-94 l'écart disparaît | S |
| KB-SEA-86 | `StatistiquesDesSeancesJdbc.java:76-84, 86-94` (`ORDER BY s.date_seance ASC` ; `queryForList` : clés `DATE_SEANCE` sur H2, `date_seance` sur PG16). Le frontend (`patient-stats.component.ts:107-110`) et le PDF (`PatientStatsQueryService.java:164-167`, `safeCell`) lisent les deux casses | Divergence SGBD + tri | (a) `ORDER BY s.date_seance, s.created_at NULLS LAST, s.id` ; (b) normaliser les clés en minuscules dans l'adaptateur (`evolutionDuPoids`), ce qui est le contrat de PRODUCTION actuel (PG16 minuscules) : aucune modification de contrat réel. | Goldens `seance/se7e/statistiques/*` (H2 : les clés passent en minuscules) ; contrat `PilotageEtRapportsDesSeancesContrat` ; `cahier-step-stats` et `patient-stats` specs frontend inchangés (lisent les deux) | Aucun côté production | S |
| KB-SEA-87 | `StatistiquesDesSeancesJdbc.java:55-69` : `AVG(numeric)` converti en `Double` (12 décimales sur H2, 16 sur PG16) | Précision dépendante du SGBD | **Non corrigeable en code sans changer le contrat.** Clôture honnête : documenter que seules 10 décimales sont garanties et comparer par tolérance (delta) dans le contrat ; le PDF arrondit déjà à 2 décimales. Option : arrondir à 2 décimales dans l'adaptateur (décision d'affichage : question plus bas). | Contrat `PilotageEtRapportsDesSeancesContrat` (valeurs différenciées par SGBD) ; golden `bornes_de_periode` | Aucun | S |
| KB-SEA-88 | `platform/src/main/resources/reports/cahier_dialyse.jrxml:45-47` (`CASE WHEN s.forfait_override_id IS NOT NULL THEN s.forfait_override_code ...`) ; `CahierDialyseReportService.java` (`CAHIER_VIDE`) | Bug métier (page imprimée avec « Forfait : - ») | **Retomber sur le référentiel** : `COALESCE(s.forfait_override_code, fo.code)` avec `LEFT JOIN forfait fo ON fo.id = s.forfait_override_id AND fo.center_id = s.center_id` (jointure à ajouter au jrxml, centre borné). NON corrigé ici : la cause (`poserLeForfait` n'écrit pas le code, KB-SEA-47, `FacturationDesSeancesJdbc.java:109-114`) ; la casse du statut (voir KB-SEA-82). | Golden `seance/se7e/cahier/surcharge_de_forfait` ; rejeu E10 ; PDF du cahier | Cahiers déjà imprimés gardent leur page vide ; valider avec l'infirmier référent que le code du référentiel est le bon libellé (le prix reste celui de la surcharge) | S |
| KB-SEA-89 | `SE/infrastructure/persistence/SeancesDeLaRepriseJdbc.java:49-62` (`seanceDejaPresente` par centre, date, patient, tout statut) | Décision métier pure | **Aucune sans décision.** Question plus bas. | Golden `seance/se7e/reprise/deja_presente_et_annulation_bloquee` ; rejeu E12 | — | S après décision |
| KB-SEA-90 | `SeancesDeLaRepriseJdbc.java:97-99` (`seancesDuPatient`, sans centre) ; `:102-105` (`factureDeLaSeance`) | Prudence | **Ne pas corriger : conforme à la prudence.** Une séance d'un autre centre pour le même patient doit empêcher la suppression du patient (sinon séance orpheline inter-centres, `retirer` est borné par centre). Clôture : documenter comme règle voulue « bloquant sans borne de centre » (RG-REPRISE), test existant `autre_centre_puis_annulation` déjà en place. Optionnel : départage de l'ordre des bloquants (`ORDER BY created_at, id`) côté `migration_id_map`, hors du contexte Séance. | — | — | S (doc) |
| KB-SEA-91 | `SE/infrastructure/persistence/SeancesDuCahierEtForfaitsImposesJdbc.java:34` (`COUNT(*) FROM seances WHERE forfait_override_id = ?`, sans centre ni statut) | Prudence | **Ne pas corriger : conforme à la prudence** (la KB le dit). Clôture : documenter ; aucun forfait ne doit rester référencé par une séance de n'importe quel centre. | — | — | S (doc) |
| KB-SEA-96 | `DM/infrastructure/persistence/DerniereCreatinineJdbc.java:31-38` (`ORDER BY date_prelevement DESC` seul puis `max(date)` : date égale = ordre du SGBD) ; `DM/domain/EvaluationKdigoDuCandidat.java:55` (créatinine ≤ 0 = non évaluable) | Risque clinique (stade KDIGO) + outillage (ordre) | (a) **Départage déterministe** : `ORDER BY date_prelevement DESC, created_at DESC NULLS LAST, id DESC`, première ligne à créatinine non nulle (supprimer `max(...)`, déjà redondant). (b) **Créatinine à 0 : non corrigé sans décision médicale** : sauter une valeur nulle ferait staguer le patient sur une mesure ancienne (plus risqué que « non évaluable ») ; la bonne correction est de refuser le 0 à la saisie (DM6, `AnalyseDemande`), question plus bas. | Goldens `seance/se7f/analyses/egalite_de_dates` (stade « l'une des deux » devient une valeur) ; contrat `DerniereCreatinineContrat` (mémoire, H2, PG16) ; rejeu F6 | Un patient dont deux analyses de même date donnent des stades différents verra un stade fixe ; valider le choix « dernière saisie » avec le médecin (alternative : la plus haute créatinine, prudence clinique) | S |

---

## 6. Organisation

### KB-ORG-09 : « centre actif » et `centers.actif` NULL

| Point | Contenu |
|---|---|
| 1. Comportement actuel | `AnnuaireDesCentresJdbc.java:25-29` (`actifs()` : `COALESCE(actif, TRUE)`) contre `:31-36` (`actifsAuSensStrict()` : `actif = TRUE`, utilisé seulement par `PF/infrastructure/acl/stock/CentresDuStockParOrganisation.java`). La colonne `centers.actif` est `boolean` sans `NOT NULL` (V1, ligne 509). Conforme. |
| 2. Nature | Intégrité de donnée / divergence de sens. |
| 3. Correction retenue | **Supprimer la cause : donnée, pas code.** (a) Détection §72.1 en production ; (b) migration `V57__centers_actif_not_null.sql` : `UPDATE centers SET actif = TRUE WHERE actif IS NULL; ALTER TABLE centers ALTER COLUMN actif SET DEFAULT TRUE; ALTER COLUMN actif SET NOT NULL` (`TRUE` car tous les consommateurs sauf le Stock lisent NULL comme actif) ; (c) retirer `actifsAuSensStrict()` du langage publié `organisation.v1` et faire utiliser `actifs()` au Stock (`CentresActifsFournis`). Si §72.1 rend `nuls = 0` : migration = contrainte seulement, effet nul. Si `nuls > 0` : ces centres ENTRENT dans le contrôle de nuit des péremptions du Stock (seul effet comportemental). |
| 4. Impact | Tests : `AnnuairesContrat.les_centres_actifs_au_sens_strict_excluent_un_etat_non_renseigne` (supprimé ou remplacé par le test de la contrainte), `AnnuairesEnMemoire`, `CentresActifsFournisTest`, `CentresDuStockParOrganisationTest`, `PeremptionsPostgresContractIT`, `JourDuCentrePublieTest` (Séance, utilise l'annuaire) ; ADR 0108 amendé ; fixtures H2 qui insèrent un centre sans `actif` doivent passer le défaut. Goldens du Stock inchangés si 0 nul. Langage publié : suppression d'une méthode (`organisation.v1` : un seul consommateur, le Stock). |
| 5. Risque | Contrôle de nuit des péremptions et comptabilisation de nuit sur des centres jusque-là ignorés (notifications nouvelles) ; valider avec les métiers Stock (déjà demandé : revue ≤ 2026-12-15). |
| 6. Taille | S (si 0 nul) ; M sinon |

### KB-ORG-10 : le journal des mouvements joint salle et créneau sans borne de centre

| Point | Contenu |
|---|---|
| 1. Comportement actuel | `ORG/.../infrastructure/persistence/LibellesDeLOrganisationJdbc.java:70-79` (`nomsDesSalles` : `FROM salle WHERE id IN (:ids)`) et :82-91 (`libellesDesCreneaux`) ; consommé par `patient-couverture/.../LectureDesMouvementsJdbc.java:77-78`. Les lectures sœurs `creneauxDuCentre` (:56-68), `codesDesSalles`, `nomsDesMedecins` sont bornées. Conforme. |
| 2. Nature | Sécurité-confidentialité (fuite d'affichage inter-centres). |
| 3. Correction retenue | **Borner la lecture au centre du mouvement** : `nomsDesSalles(centre, salles)` et `libellesDesCreneaux(centre, creneaux)` (`AND center_id = :centre`), le journal passe son centre ; une salle d'un autre centre rend un libellé vide, comme un libellé disparu (`LibellesDeLOrganisationDansLesLecturesTest... vide_si_disparus`). Pour ne pas casser `organisation.v1`, ajouter les nouvelles signatures et retirer les anciennes dans le même lot (un seul consommateur). Pas de contrainte de base (une contrainte inter-tables exigerait un trigger). |
| 4. Impact | Tests : `LibellesJdbcContratTest`, `LibellesEnMemoireContratTest.les_noms_de_salles_et_libelles_de_creneaux_ne_sont_pas_bornes_au_centre` (à inverser), `LibellesDeLOrganisationDansLesLecturesTest.le_journal_rend_le_nom_de_la_salle_et_le_libelle_du_creneau_sans_borne_de_centre_et_vide_si_disparus` ; goldens PC1 du journal des mouvements si l'un référence une salle étrangère (probablement aucun). Détection préalable §73.1 : le compte d'abord. Frontend : colonne salle/créneau du journal vide pour ces lignes. |
| 5. Risque | **Vérifier le lien avec le transfert de patient** : un mouvement enregistré lors d'un transfert A vers B peut légitimement référencer la salle ou le créneau de A ; si §73.1 rend des lignes issues de transferts, borner efface une information historique légitime. Dans ce cas, ne pas corriger sans décision (question plus bas). |
| 6. Taille | S |

---

## 7. KB déjà closes ou sans objet

| KB | État | Action de clôture |
|---|---|---|
| KB-SEA-71 | Corrigé (ADR 0097) | Mettre à jour la ligne de `known-bugs.md` ; voir section 3 |
| KB-SEA-97 | Clos (ADR 0100 décision 5) | Rien |
| KB-SEA-98 | Clos (outillage) | Rien |
| KB-SEA-87 | Non corrigeable (SGBD) | Documentation + test tolérant |
| KB-SEA-90, 91 | Conformes à la prudence | Documentation |
| KB-DIR-03, 04, 07, 08 | Décisions métier | Documentation, voir questions |

---

## Découpage en lots

Ordre recommandé : argent, sécurité clinique, confidentialité, puis intégrité d'écriture, puis lectures, puis décisions ; les migrations de données (V54 et suivantes) sont rassemblées dans les lots qui les nécessitent et ne sont livrées qu'après leur détection en production.

| Lot | Contenu | Fichiers touchés (module) | Dépendances / goldens partagés | Condition de livraison |
|---|---|---|---|---|
| **L1 Confidentialité** | KB-DIR-01 + KB-MED-104 (règle « suppression complémentaire des totaux ») | `PD/domain/BilanDuCentre`, `RepartitionDeLaSociete`, `PD/application/ConstituerLeRapportDeSynthese` (si besoin), `DM/application/CalculerLesIndicateursCliniques`, publication `IndicateursCliniquesPublies` ; masque à la lecture des anciens instantanés (`PD/application/ConsulterUnInstantane`) | Goldens `pd1/.../vue_d_ensemble`, `.../repartition`, `pd4/rapport/*`, `dm8/direction/*`, `dm9/direction/*` : à réapprouver EN UNE FOIS ; le golden `repartition` est partagé avec L5 (DIR-05, SEA-84) | Décision DPO écrite (question 1) |
| **L2 Clinique** | KB-MED-106 | `DM/domain/RisqueInfectieux`, `DM/infrastructure/persistence/PatientsARisqueInfectieuxJdbc`, doubles de contrat | Goldens dm4 et dm9 `derniere_serologie` ; aucun partage | Accord des médecins + liste §66.1 (question 2) |
| **L3 Argent et intégrité de facturation** | KB-SEA-72, 73 (garde du port), 74 (V54, clé étrangère) ; KB-SEA-71 : clôture documentaire | `SE/infrastructure/persistence/FacturationDesSeancesJdbc`, migration V54, fixtures H2 | Goldens `facturation/simulation/se7c2-ecritures/*` ; contrat PG16 `LiaisonConcurrentePostgresContractIT` (à rejouer) | Détection §62.2 à 0 ligne `f.id IS NULL` (question 3) |
| **L4 Séance : écriture du volet** | KB-SEA-93, 94 (V56 après §65.1), 99 | `SE/application/RenseignerLeVoletParamedical`, `SE/infrastructure/persistence/VoletsParamedicauxJdbc`, `SE/interfaces/http/ErreursDuVoletParamedicalAdvice` | Goldens `dm1/ecriture/*` partagés avec KB-SEA-99 ; `seance/se7f/volet/*` partagés entre 93 et 94 : traiter dans un seul lot | Validation écrite de la réparation de centre (94) |
| **L5 Séance : lectures et tris** | KB-SEA-77, 78, 79, 81, 83 + KB-DIR-05, 84, 85 (volet borné et PDF entier), 86, 88 (jrxml), 96 (tri) | `LecturesDesSeancesRealiseesJdbc`, `ConstantesDesSeancesJdbc`, `SeancesRealiseesDesCentresJdbc`, `StatistiquesDesSeancesJdbc`, `cahier_dialyse.jrxml`, `PatientStatsQueryService`, `DM/.../DerniereCreatinineJdbc`, `PD/domain/RepartitionDeLaSociete` (tri) | Golden `pd1/.../repartition` et `rapport_en_direct` PARTAGÉS avec L1 : livrer L1 avant, ou re-réapprouver après ; les contrats `SeancesRealiseesEtConstantesContrat` et `PilotageEtRapportsDesSeancesContrat` (mémoire/H2/PG16) servent aux sous-lots 5a (tris 77-79, 86, 96), 5b (cloisonnement 81, 83, 84, 85, DIR-05), 5c (cahier 88) | Sous-lots indépendants |
| **L6 Pilotage : qualité d'API** | KB-DIR-02, KB-DIR-06(e), documentation de DIR-03, 04, 06(a-d), 07, 08 | `PF/infrastructure/web/DatesIllisiblesDeLaDirectionAdvice`, `PD/application/ConsulterUnInstantane` | Goldens `pd1/bornes`, `droits`, `pd2/instantanes`, `pd3/historique`, `pd4/en_tete` : aucun partage avec L1 sauf `pd2/cloisonnement` (instantanés) | Aucune |
| **L7 Organisation** | KB-ORG-09 (V57), KB-ORG-10 | `ORG/.../AnnuaireDesCentresJdbc`, `LibellesDeLOrganisationJdbc`, `PF/infrastructure/acl/stock/CentresDuStockParOrganisation`, `patient-couverture/.../LectureDesMouvementsJdbc` | Aucun golden partagé ; tests de contrat du langage publié | Détections §72.1 et §73.1 |
| **L8 Documentation et clôtures** | KB-SEA-71, 87, 90, 91, 97, 98 ; mise à jour de `known-bugs.md`, `verifications-production.md` (nouvelles détections : 62 variante blancs ; liste des patients de §66.1), ADR des lots, référentiel des règles de gestion | `.ddd/**`, `docs/reference/**` | Après chaque lot | Aucune |

Ordre : L3, L2, L1 (en parallèle si les équipes diffèrent : fichiers disjoints), puis L7 et L4, puis L5 (après L1), puis L6, puis L8 au fil de l'eau. Chaque lot : filet vert sur le code actuel, correction, goldens modifiés approuvés par l'humain, rejeu PG16 du script de la slice.

### Corrections à NE PAS faire sans décision préalable

| KB | Question exacte à poser |
|---|---|
| 1. KB-DIR-01 / MED-104 | DPO : valider la suppression complémentaire des totaux (option 9.1(a)) ; les instantanés déjà figés doivent-ils être masqués à la lecture (oui/non) ; accepte-t-on que la société n'ait plus de total patients dès qu'un centre est sous le seuil ? Les recoupements résiduels (effectif d'un centre contre sa ventilation sexe/âge) sont-ils à traiter ? |
| 2. KB-MED-106 | Médecins : un résultat DOUTEUX ou EN_COURS plus récent doit-il laisser le dernier résultat tranché (positif) en vigueur pour l'isolement ? Valident-ils la liste des patients de §66.1 qui reviendront en isolement ? |
| 3. KB-SEA-74 / 82 | Production : la requête §62.2 (orphelins) et §62.7 (statuts non canoniques) rendent-elles 0 ligne ? Sinon, quelle réparation pour chaque ligne ? La Facturation peut-elle supprimer une facture encore liée à des séances ? |
| 4. KB-SEA-75 | Comptabilité : « séance facturée » = statut `FACTUREE` ou rattachée à une facture du mois ? Quelle définition pour le tableau de bord ? |
| 5. KB-SEA-80 | Planification / transfert : une séance réalisée du centre A sur une fiche transférée au centre B doit-elle apparaître dans la semaine de A (sans nom), de B, ou nulle part ? |
| 6. KB-SEA-85 | Statistiques paramédicales du patient : seulement les séances réalisées (`VALIDEE`, `SIGNEE`, `FACTUREE`) ou aussi les autres statuts ? |
| 7. KB-SEA-87 | Arrondir à 2 décimales dans l'API (changement de contrat) ou documenter la précision SGBD ? |
| 8. KB-SEA-89 | Reprise : une séance annulée de la plateforme doit-elle empêcher d'importer la séance de l'ancien logiciel du même jour ? |
| 9. KB-SEA-94 | Responsable des données : autorise-t-il la réécriture du `center_id` des volets sur celui de leur séance (liste fournie par §65.1) ? |
| 10. KB-SEA-95 | Un jour férié doit-il avoir son propre motif (`JOUR_FERIE`) à la création hors planning par l'administrateur ? |
| 11. KB-SEA-96 | Médecin : à date égale, la dernière saisie fait-elle foi, ou la créatinine la plus haute ? Une créatinine à 0 doit-elle être refusée à la saisie ? |
| 12. KB-DIR-03 | Direction : quel fuseau pour « aujourd'hui » et le « mois écoulé » (fuseau du premier centre, de la société à créer, `Africa/Algiers`, ou UTC documenté) ? |
| 13. KB-DIR-04 | Direction : l'historique des alertes garde-t-il la valeur à l'ouverture, ou la valeur la plus récente (option 9.2) ? |
| 14. KB-DIR-06(c), DIR-07, DIR-08 | Direction : une société sans centre doit-elle être capturée vide ? Le stock est-il évalué à la fin de la période ou aujourd'hui ? Le sexe par initiale et la tranche d'âge au jour de l'appel sont-ils à conserver ? |
| 15. KB-ORG-10 | Y a-t-il des mouvements issus de transferts de patients qui référencent la salle ou le créneau d'un autre centre (résultat de §73.1) : si oui, borner efface-t-il une information légitime ? |

---

## Tableau récapitulatif

| KB | Lot | Taille | Nature |
|---|---|---|---|
| KB-DIR-01 | L1 | M | Sécurité-confidentialité |
| KB-MED-104 | L1 | M | Sécurité-confidentialité |
| KB-MED-106 | L2 | S | Risque clinique |
| KB-SEA-72 | L3 | S | Intégrité / argent |
| KB-SEA-73 | L3 | S | Intégrité / argent (garde du port) |
| KB-SEA-74 | L3 | M | Intégrité / argent (clé étrangère, migration) |
| KB-SEA-71 | L8 | S | Déjà corrigé (ADR 0097) |
| KB-SEA-75 | décision | M | Décision métier pure |
| KB-SEA-82 | décision puis L3 | M | Intégrité de donnée (contrainte) |
| KB-SEA-93 | L4 | S | Intégrité (écriture partielle) |
| KB-SEA-94 | L4 | M | Intégrité (donnée + migration) |
| KB-SEA-99 | L4 | S | Bug d'API (500 en 400) |
| KB-SEA-77 | L5 | S | Outillage / tri |
| KB-SEA-78 | L5 | S | Bug métier mineur (tri) |
| KB-SEA-79 | L5 | S | Divergence SGBD |
| KB-SEA-80 | décision | S | Décision métier pure |
| KB-SEA-81 | L5 | S | Bug métier |
| KB-SEA-83 | L5 (avec DIR-05) | S | Bug métier |
| KB-DIR-05 | L5 (après L1) | S | Bug métier |
| KB-SEA-84 | L5 (après L1) | S | Bug métier + tri |
| KB-SEA-85 | L5 (borne du volet, PDF) ; statuts : décision | S | Cloisonnement / décision métier |
| KB-SEA-86 | L5 | S | Divergence SGBD + tri |
| KB-SEA-87 | L8 | S | Précision dépendante du SGBD (non corrigeable) |
| KB-SEA-88 | L5 | S | Bug métier (impression) |
| KB-SEA-89 | décision | S | Décision métier pure |
| KB-SEA-90 | L8 | S | Prudence (documentation) |
| KB-SEA-91 | L8 | S | Prudence (documentation) |
| KB-SEA-95 | décision | S | Décision métier pure |
| KB-SEA-96 | L5 (tri) ; zéro : décision | S | Risque clinique + outillage |
| KB-SEA-97 | L8 | S | Déjà clos |
| KB-SEA-98 | L8 | S | Déjà clos (outillage) |
| KB-DIR-02 | L6 | S | Bug d'API (500 en 400/405) |
| KB-DIR-03 | décision | S ou L | Décision de modèle (fuseau) |
| KB-DIR-04 | décision | S | Décision métier pure |
| KB-DIR-06 | L6 (e seul) | S | Intégrité d'API (e) ; confirmation (a à d) |
| KB-DIR-07 | décision | S | Décision métier pure |
| KB-DIR-08 | L8 (+ PDF optionnel avec L1) | S | Décision métier pure / rendu PDF |
| KB-ORG-09 | L7 | S ou M | Intégrité de donnée (migration) |
| KB-ORG-10 | L7 | S | Sécurité-confidentialité (cloisonnement) |

## Questions ouvertes

- Les lignes de `RepartitionDeLaSociete` et le calcul du chiffre d'affaires par caisse (`FacturationParLaFacturation`) n'ont pas été relus jusqu'à la source SQL du chiffre d'affaires : à vérifier avant d'appliquer la borne de centre de KB-SEA-84.
- Le lieu exact de lecture du JSON des instantanés (KB-DIR-02 c) n'a pas été localisé ; `PD/infrastructure/persistence/InstantanesMensuelsJdbc.java:38-39` ne lit que la chaîne.
- Les suppressions de factures (`DELETE FROM factures`) n'ont pas été recherchées : prérequis de KB-SEA-74.
- Le comportement du transfert de patient (la fiche change-t-elle de `center_id` ?) n'a pas été relu ; il conditionne KB-SEA-80 et KB-ORG-10.
- Numéros de migration V54 à V57 indicatifs (derniers : V53) : à renuméroter à l'écriture selon l'ordre de livraison des lots.
