---
status: accepted
date: 2026-11-09
review-by: 2026-12-15
decision-makers: [utilisateur (décisions déléguées à l'assistant via le coordinateur, 2026-11-09), architecte]
context-scope: pilotage-direction, plateforme (composition des indicateurs de la direction)
related: ADR 0024 et 0075 (refactoring sur place), 0101 (DM9, indicateurs cliniques publiés), 0102 à 0105 (lots A à D) ; `.ddd/migration/slices/pilotage-direction-pd6-composition-des-indicateurs.md`, KB-DIR-01, KB-DIR-07
---

# 0106 - Pilotage direction, PD6 : la composition des indicateurs de la direction passe dans le contexte (option a)

Décision prise par délégation, EN VIGUEUR, **à confirmer métiers / DPO au plus tard le 2026-12-15** pour les points de statu quo (KB-DIR-01, KB-DIR-07). Refactoring SUR PLACE (ADR 0024/0075) : mêmes URL (`GET /api/v1/direction/indicators`), mêmes rôles, mêmes codes, mêmes tables (aucune), même JSON ; aucune façade, aucun flag, aucune projection, aucun événement. Solde PD5-R5 ; PD5-R9 : voir décision 5.

## Preuve du graphe de modules (avant le choix)
Question : `contexts-pilotage-direction` peut-il dépendre des langages publiés de Dossier médical et Stock sans cycle ?
- Relevé des dépendances déclarées dans les `pom.xml` (2026-11-09 ; la sortie de `dependency:tree -q` n'a pas été conservée, la preuve reproductible est la lecture des `pom.xml` et le `grep` ci-dessous, puis le réacteur qui refuse tout cycle) : `contexts-stock` ne dépend QUE de `contexts-shared-kernel` ; `contexts-dossier-medical` dépend de shared-kernel, patient-couverture, planification (→ gmao), stock, seance (→ absences-patients, comptabilite, facturation) ; `contexts-seance` : shared-kernel, patient-couverture, stock, absences-patients, planification ; `contexts-facturation` : comptabilite, shared-kernel, patient-couverture. Le module Pilotage dépendait déjà de seance, facturation et dossier-medical : `contexts-stock` y figurait donc DÉJÀ par transitivité ; PD6 le déclare en direct.
- Aucun `pom.xml` de `contexts/*`, `shared-kernel`, `bc-patient`, `bc-assure` ne cite `pilotage` (`grep -l pilotage` : seul le `pom.xml` du Pilotage) et aucune classe de production d'un autre contexte n'importe `pilotagedirection` (`grep` vide). Dossier médical et Stock ne dépendent donc pas du Pilotage, ni directement ni via la plateforme (le module `platform` est le seul à dépendre de tous).
- Le réacteur Maven (qui refuse tout cycle) construit les 17 modules après l'ajout de la dépendance ; ArchUnit du module : seule l'infrastructure (ACL) lit un autre contexte, et seulement par son paquet `publishedlanguage`.
Conclusion : option **a** possible, sans cycle.

## Décisions
1. **Option a retenue** : le Pilotage possède la composition. Cas d'usage `ConsulterLesIndicateursDeLaDirection` (application) : période validée AVANT toute lecture (`Periode.demandee`), société et centres par `SocietesSuivies`, cliniques par le port `IndicateursCliniquesDesCentres` (ACL `CliniquesParLeDossierMedical` sur `IndicateursCliniquesPublies`), stock par le port `StockDesCentres` (ACL `StockParLeStock` sur `IndicateursDuStock`) ÉVALUÉ À LA FIN DE LA PÉRIODE (KB-DIR-07). La règle de composition est dans le domaine : `IndicateursDeLaSociete.composer` (totaux du stock = somme des valeurs NON arrondies puis un seul arrondi, `StockDuCentre`; alertes par `PolitiqueDAlertes` sur taux déjà masqués et observance brute ; société sans centre : totaux à zéro, ni centre ni alerte, stock non lu). Adaptateur entrant `IndicateursPublies` → langage publié `IndicateursDeLaDirection` dont les enregistrements portent les noms du JSON d'avant, dans le même ordre (contrat de `/indicators` ET des instantanés figés, RG-DIR-090).
2. **Le stock avait déjà un langage publié suffisant** (`IndicateursDuStock.pourCentres(centres, date)`, S9) : aucun port fourni côté Stock à créer, pas de `ValorisationArticlesService` en jeu (c'est PD5-R4, `DirectionStockGroupesQueryService`, hors PD6).
3. **Retraits dans la plateforme** : `DirectionIndicatorsQueryService` et ses trois traducteurs (`IndicateursAFigerParLaPlateforme`, `IndicateursDuRapportParLaPlateforme`, `IndicateursDuTempsReelParLaPlateforme`). Les ports `IndicateursAFigerFournis` et `IndicateursDuTempsReelFournis` disparaissent : l'instantané et le temps réel lisent `IndicateursDeLaDirection`. `IndicateursDeLaDirectionFournis` est CONSERVÉ comme forme que lit le rapport (ses enregistrements sont le contrat de `SourceDuRapport`, que la plateforme reconstruit aussi depuis un instantané figé : `RapportDUnInstantane`) ; un adaptateur du module (`IndicateursDuRapportParLesIndicateursPublies`) le sert (résiduel PD6-R1 : nom à renommer au prochain changement de version du langage publié). La plateforme garde le contrôleur (jeton, garde de société) qui lit `IndicateursDeLaDirection`.
4. **Filet** (VERT sur le legacy avant toute modification, puis inchangé) : DM8 (forme, masquage, périmètre), DM9 (fuite par différence), PD3 `seuils` (alertes, stock à la date de fin, valeur = quantité x PMP), PD1 `bornes`, plus trois goldens PD6 ajoutés pour les trous réels (`sans_centre`, `arrondi_et_inactif`, `date_de_fin`) et le test du traducteur du rapport, repris dans `IndicateursDeLaDirectionIntegrationTest` (forme JSON exacte à chaque niveau, traduction vers le rapport). Tests du module : domaine (4), cas d'usage avec ports en mémoire (4), traducteurs table-driven (3), constat du temps réel adapté (6).
5. **Seuil d'anonymat (PD5-R9)** : NON unifié. `AnonymityPolicy` (plateforme) a encore deux autres propriétaires (`DirectionAbsencesQueryService`, `DirectionCapaciteQueryService`) qui ne sont pas du Pilotage ; le test d'égalité des deux seuils (`TableDuPilotageTest`) reste. PD6 n'a plus aucune classe du Pilotage qui lise `AnonymityPolicy` : `seuilAnonymat` publié vient de `SeuilDAnonymat.SEUIL` (valeur 5, identique).
6. **Gardes** : `TableDuPilotageTest` : `RESTE_DANS_APPLICATION_DIRECTION` passe de 11 à 10 entrées, `DirectionIndicatorsQueryService.java` interdit dans la liste ; `FrontieresDesContextesTest` : retrait de la classe disparue de deux règles, deux règles ajoutées (la classe et les trois traducteurs ne réapparaissent pas ; le contrôleur lit `IndicateursDeLaDirection`) ; ArchUnit du module : règle ajoutée « seule l'infrastructure lit les autres contextes ».

## Parité
100 % : goldens PD1 à PD5, DM8, DM9 inchangés (aucun `.approved` modifié, aucun `_nouveau_code`, aucune archive `_ecarts-legacy`). Aucun écart de comportement. KB-DIR-01 (consolidés sur les bruts, fuite par différence) et KB-DIR-07 (stock à la date de fin) REPRODUITS, non corrigés.

## Seams
Tous les fichiers de plateforme touchés étaient déjà dans `covered.json` (édités ou supprimés après filet) : aucun seam, `seamAllowed` est resté `[]`. Entrées de `covered.json` supprimées avec leurs fichiers.

## Résidus
| Réf. | Résiduel | Propriétaire |
|---|---|---|
| PD6-R1 | `IndicateursDeLaDirectionFournis` : nom hérité (« fourni par la plateforme ») pour un port désormais servi par le module ; ses enregistrements servent aussi à relire un instantané figé (`RapportDUnInstantane`, plateforme) | Pilotage (renommage au prochain changement de version de `pilotagedirection.v1`) |
| PD6-R2 | `AnonymityPolicy` / `SeuilDAnonymat` : deux copies du seuil 5, égalité testée (PD5-R9 non soldé tant que Absences et Capacité lisent encore la plateforme) | Pilotage et propriétaires de PD5-R1, R2 |
| PD6-R3 | KB-DIR-01 (consolidé sur les bruts : fuite par différence) et KB-DIR-07 (stock à la date de fin) : décisions de statu quo à confirmer métiers / DPO | Métiers / DPO, avant le 2026-12-15 |
| PD6-R4 | La documentation fonctionnelle HTML est éditée à la main (section 13.15), le générateur lançant un rendu PDF Playwright ; régénérer `docs/client` à la prochaine occasion (le HTML ne contient pas encore RG-DIR-109 du lot D) | Documentation |
