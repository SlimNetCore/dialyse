---
status: accepted
date: 2026-11-09
review-by: 2026-12-15
decision-makers: [utilisateur (décisions déléguées à l'assistant via le coordinateur, 2026-11-09), architecte]
context-scope: pilotage-direction, plateforme (tableau de bord, répartitions et rapport de synthèse de la Direction)
related: ADR 0024 et 0075 (refactoring sur place, gel du module parallèle), 0099 (SE7e, séances lues par seance.v1), 0101 (DM9, modèle), 0100 (SE7f) ; `.ddd/safety-net/pilotage-direction.md` (audit), `.ddd/migration/slices/pilotage-direction-pd1-tableau-bord.md`, `…-pd4-export-synthese.md`, KB-DIR-01 à 08
---

# 0102 - Pilotage direction, lot A (PD1 + PD4) : le calcul du tableau de bord, des répartitions et du rapport de synthèse passe dans le contexte ; la plateforme garde le jeton et le rendu PDF

Décisions prises par délégation, EN VIGUEUR, **à confirmer métiers / DPO au plus tard le 2026-12-15**. Refactoring SUR PLACE (ADR 0024/0075) : mêmes tables, mêmes URL `/api/v1/direction/*`, mêmes rôles, mêmes codes HTTP ; aucune façade, aucun flag, aucune projection, aucun événement inventé, aucune migration. Les requêtes SQL sont reprises MOT POUR MOT (citées `chemin:ligne` de l'original dans la javadoc de chaque adaptateur).

## RISQUE DE CONFIDENTIALITÉ À TRANCHER PAR LE DPO EN PRIORITÉ : KB-DIR-01 (reproduit)
**Fuite par différence.** Chaque effectif de 1 à 4 patients est masqué par centre (publié `null`, imprimé « < seuil »), mais les totaux de la société sont calculés sur les effectifs BRUTS : total moins somme des centres publiés = effectif masqué. Vaut pour la vue d'ensemble, la répartition (sexe, âge, caisses) et le rapport PDF (famille de KB-MED-104, qui ne couvrait que `/indicators`). **Reproduit à l'identique** (option e), goldens PD1 et PD4 inchangés ; le calcul est désormais dans `BilanDuCentre.consolider` (domaine du Pilotage), testé nominativement.

| Option | Effet |
|---|---|
| a | masquer les totaux dès qu'un centre est masqué |
| b | totaux calculés sur les centres publiés seuls |
| c | arrondir par tranche de 5 |
| d | publier les agrégats de société seuls (plus de détail par centre sous le seuil) |
| e | accepter le risque par écrit (**décision actuelle : reproduit**) |

**Précondition de mise en production** : les instantanés mensuels DÉJÀ figés (RG-DIR-090) contiennent les totaux bruts ; une option a à d ne les corrige pas rétroactivement. Détection : `.ddd/migration/verifications-production.md` §69.1 (SELECT, rejouée sur PostgreSQL 16).

## Points à faire confirmer (en tête)
| KB / sujet | Comportement | Décision de cet ADR | Confirmation attendue |
|---|---|---|---|
| KB-DIR-01 | voir ci-dessus | REPRODUIRE (option e) | **DPO : condition de mise en production** (avec KB-MED-104) |
| KB-DIR-02 | Erreurs de requête en 500 au lieu de 400/405 (`from=hier` sur `/overview`, `/breakdown`, `/report`) | REPRODUIRE (code inchangé, détaillé dans la fiche) | Métiers : 400 homogène ? |
| KB-DIR-03 | « Aujourd'hui » et bornes de période = date du serveur (JVM), jamais le fuseau de la société ou du centre ; tranches d'âge au jour de l'appel | STATU QUO : `HorlogeDuPilotage` injecte une `Clock` UTC (le serveur fixe son fuseau par défaut à UTC) | Métiers : fuseau de la société, d'un centre de référence ou `Africa/Algiers` ? |
| Règle R2 (alerte) | Règle d'alerte du stock / observance évaluée par `DirectionIndicatorsQueryService` | REPRODUIRE, HORS lot (PD3) : seulement mentionnée comme reportée | Direction : à reconfirmer à la reprise de PD3 |
| Rapport PDF | Mise en page, HTML vers PDF (openhtmltopdf 1.0.10) | Le module possède le calcul ET le modèle de rapport ; la plateforme garde le rendu | — |
| Comptes | `DirectionAccountService`, `SocieteAdminAccountService`, `PasswordPolicy` | RESTENT en plateforme, jamais touchés | — |
| DPO | Les lectures changent de module, pas de destinataires, de finalité ni de rôles | Aucune donnée nouvelle, mêmes tables, mêmes routes | DPO : confirmer que le passage par le langage publié ne change rien |

## Contexte
Après le filet PD1 à PD4 (commit `9c21d51a`, 17 goldens, rejeu PG16 §68) : le tableau de bord, la répartition et le rapport vivaient dans `DirectionDashboardQueryService`, `DirectionBreakdownQueryService`, `EffectifSql` et `DirectionReportPdfService` de la plateforme, 7 lecteurs Java et SQL. Le module parallèle `backend/contexts/pilotage-direction` était GELÉ (ADR 0075), hors réacteur, sur des tables inexistantes en production.

## Décisions
1. **Module reconstruit sur le vrai modèle** : on jette les quatre tables parallèles (`tableaux_de_bord`, `instantanes_mensuels`, `alertes_direction`, `exports_synthese`), `/api/v1/pilotage/*`, le `TableauDeBord` à chaîne opaque, la règle R2 du module et ses contrôleurs sans `@PreAuthorize` ; on ne réutilise que le vocabulaire, l'horloge injectée et le style de test. Le module rentre dans le réacteur (`backend/pom.xml`), dans `platform/pom.xml` et dans le `Dockerfile`.
2. **Langages publiés** : `TableauDeBordDeLaDirection` (société, vue d'ensemble, répartition) et `RapportDeSyntheseDeLaDirection` (rapport en direct, rapport d'un instantané figé). Les enregistrements portent EXACTEMENT les noms et l'ordre du JSON servi et figé dans les instantanés (RG-DIR-090) ; les champs de comptage sont `Long`/`Integer` pour que les valeurs absentes des anciens instantanés s'impriment « — » ou « < seuil ».
3. **Domaine** : `Periode` (défauts, 422 `PERIODE_INVALIDE`/`PERIODE_TROP_LONGUE`, limite de 5 ans sur la période demandée seulement), `SeuilDAnonymat` (seuil 5, zéro reste zéro), `BilanDuCentre` (effectifs bruts, consolidation), `VueDEnsembleDeLaSociete`, `RepartitionDeLaSociete`, `TrancheDAge`, `LibelleDAlerte`, `EvolutionMensuelle`, `MatriceDesCaisses`. Aucune horloge ni identifiant dans le domaine.
4. **Application** : `ConsulterLaSociete`, `ConsulterLaVueDEnsemble`, `ConsulterLaRepartition`, `ConstituerLeRapportDeSynthese` ; ports sortants `SocietesSuivies`, `EffectifsDesPatients`, `SeancesRealiseesDesCentres`, `FacturationDesCentres`, `TraitementsDeLAnemieDesCentres`. La période est validée AVANT toute lecture.
5. **Adaptateurs** : SQL verbatim pour la société, les centres et l'effectif (`SocietesEtCentresJdbc`, `PatientsDesCentresJdbc`, `PredicatDEffectif`, copie du prédicat RG-PAT-032 de `EffectifSql` retiré) ; séances par `SeancesRealiseesParLaSeance` (seance.v1), factures et règlements par `FacturationParLaFacturation` (facturation.v1), anémie par `AnemieParLeDossierMedical` (dossier-medical.v1). `EffectifSqlTest` est retiré avec sa classe ; ses cas sont repris par le contrat H2/PG16 du module.
6. **Indicateurs** (clinique, stock, alertes) : RESTENT en plateforme (`DirectionIndicatorsQueryService`, DM9) et sont FOURNIS au module par le port `IndicateursDeLaDirectionFournis` (traducteur `IndicateursDuRapportParLaPlateforme`) : pas de cycle Maven (le module dépend de la Séance, de la Facturation et du Dossier médical, aucun ne dépend de lui).
7. **Erreurs** : `RegleDuPilotageViolee(code, message)` est traduite en 422 « Regle metier violee » + `code` par `ErreursDuPilotageAdvice` (même réponse que l'ancienne `BusinessException`). Le reste de la mécanique d'erreur (500 de KB-DIR-02) est inchangé.
8. **Plateforme** : `DirectionDashboardRestController` RESTE en plateforme (porte le jeton et la garde de société `DirectionAccessGuard`, revérifiés à chaque appel, et héberge les routes déjà caractérisées par leurs contextes : `/indicators`, `/capacite`, `/absences`, `/gmao`, `/stock-groupes`, `/alerts/history`, dont le comportement n'est pas touché) ; seuls `/societe`, `/overview`, `/breakdown`, `/report` appellent le module. `DirectionSnapshotRestController` n'a nécessité aucune édition. `DirectionReportPdfService` est réduit au RENDU (HTML vers PDF, papier à en-tête, `DocumentIdentityProvider`), les goldens texte du PDF sont inchangés. `DirectionSnapshotService`, `DirectionRealtimeService` et les quatre services Indicateurs/Absences/Capacité/GMAO/Stock-groupes ne changent que de TYPE (PD2/PD3 hors lot).
9. **Gardes** (A5) : ArchUnit du module (domaine pur, application sans framework, langage publié indépendant, aucun import du legacy, autres contextes par `publishedlanguage` seulement) ; règles de plateforme ajustées (`FrontieresDesContextesTest`) ; listes blanches de lecteurs SQL ajustées : `TableDeLOrganisationTest` (entrée du tableau de bord remplacée par `SocietesEtCentresJdbc`), `TableDuPatientCouvertureTest` (deux entrées Direction remplacées par `PatientsDesCentresJdbc`, jalon daté 2026-11-09, revue 2026-12-15, butoir 2027-03-31), `TableDesSeancesTest` (fichiers SE7e restés en plateforme : 9 -> 7), `TableDuDossierMedicalTest` ; nouvelle garde `TableDuPilotageTest` (nominative sur `direction_snapshot` et `direction_alert_history` : 2 lecteurs, `DirectionSnapshotService` et `DirectionAlertHistoryService`, entrées mortes détectées) qui remplace `LectureSeuleDuPilotageTest` (désactivée depuis le gel).
10. **Tests PC4/PC5** : ils comparaient le compteur publié du Patient et couverture à `EffectifSql` (retiré) ; la règle de référence devient `EffectifDeReference`, copie figée en test.

## Parité attendue et constatée
Les 17 goldens PD (PD1 6 tests, PD2 4, PD3 4, PD4 3), DM8, DM9, P6, A4, G6, F5, S4, SE7e, PC et la suite complète : INCHANGÉS. Aucun `.approved` modifié, aucun nouvel écart (aucun `_nouveau_code`, aucune archive `_ecarts-legacy`).

## Résidus assumés et datés (revue 2026-12-15, butoir 2027-03-31)
- `PatientsDesCentresJdbc` lit `patients` en SQL (jointure patient x centre payeur x agence x caisse dans un GROUP BY) ; `SocietesEtCentresJdbc` lit `societes` et `centers` : entrées nommées dans les gardes, à remplacer par les langages publiés du Patient et de l'Organisation.
- Le contrat des lectures n'a pas de double « en mémoire » : les lectures sont de pures requêtes sur les tables des autres contextes ; les cas d'usage sont testés avec des fakes en mémoire.
- PD2 (instantanés), PD3 (alertes temps réel) et PD5 (lectures publiées) restent à faire.

## Questions ouvertes
1. **KB-DIR-01 (DPO, priorité)** : option a à e, et traitement des instantanés déjà figés (§69.1).
2. KB-DIR-03 : fuseau de référence (société, centre ou `Africa/Algiers`).
3. KB-DIR-02 : réponse 400 homogène aux dates illisibles ?
4. Règle R2 : reconfirmation à la reprise de PD3.

## Lots
A1 cet ADR et le langage publié. A2 domaine et cas d'usage. A3 adaptateurs JDBC et ACL. A4 contrôleur et rendu PDF en plateforme. A5 gardes. A6 documentation, rejeu PostgreSQL 16, fiches de slice.
