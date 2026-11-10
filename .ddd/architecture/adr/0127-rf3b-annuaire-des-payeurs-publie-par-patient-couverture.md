---
status: accepted
date: 2026-11-10
review-by: 2026-12-15
decision-makers: [architecte (mise en oeuvre de l'ADR 0121, décision 2, suite de l'ADR 0126)]
context-scope: patient-couverture (propriétaire), seance, pilotage-direction, platform (impressions)
related: ADR 0126 (RF3a), 0124 (RF2, modèle suivi), 0121 (propriété), 0120 et 0118 (chaîne bornée, KB-SEA-84), 0079 (`patientcouverture.v1`), 0024 (refactoring sur place) ; fiche `.ddd/migration/slices/patient-couverture-rf3-payeurs.md`
---

# 0127 - RF3b : l'annuaire des payeurs `PayeursDuCentre` publié par Patient & couverture

Refactoring SUR PLACE : mêmes tables (`caisse_assurance`, `agence`, `centre_payeur`), mêmes URLs, mêmes rôles, mêmes codes, même JSON ; ni façade, ni flag, ni projection. Périmètre RF3b : créer le langage publié `patientcouverture.v1` `PayeursDuCentre` et migrer les lecteurs qui ne font QUE nommer ou regrouper par la chaîne des payeurs (« annuaire »). Les jointures `GROUP BY` de la Facturation, le lien Comptabilité et les lectures `/referentials/*` de l'Organisation restent à RF3c.

Parité : aucun golden modifié (seance, pilotage-direction, patient-couverture dont les 45 goldens de RF3a, facturation, absences, comptabilité, organisation) ; aucun golden `_nouveau_code`, aucune archive `_ecarts-legacy`.

## Décisions
| N° | Décision | Raison |
|---|---|---|
| 1 | Interface `PayeursDuCentre` : `caisses(centre)`, `agences(centre)`, `centresPayeurs(centre)`, `detail(centre, centrePayeur)`, `chaines(centre)`, `existe(centre, centrePayeur)` ; enregistrements `CaissePubliee`, `AgencePubliee`, `CentrePayeurPublie`, `ChaineDuPayeur` | lecture seule, toujours bornée au centre indiqué, aucune règle d'accès |
| 2 | Ajustement par rapport à la fiche : `agences(centre, caisse)` devient `agences(centre)` (aucun lecteur ne filtre par caisse) ; `chaine(centre, centrePayeur)` est fusionnée dans `detail` (qui rend le centre payeur, son agence et sa caisse) ; `chaines(centre)` est ajoutée (une seule lecture pour regrouper les patients ou les séances par caisse) | besoin réel des lecteurs ; pas de requête par ligne |
| 3 | La chaîne est bornée à CHAQUE étage : l'agence n'est suivie que si elle est du centre du centre payeur, la caisse que si elle est du centre ; sinon `agence` ou `caisse` est nul (le centre payeur garde l'identifiant d'agence enregistré) | ADR 0120 ; KB-SEA-84 |
| 4 | Un adaptateur JDBC unique `PayeursDuCentreJdbc` (une requête pour la chaîne, jointures bornées par `center_id`) ; le double en mémoire de RF3a le porte aussi. Contrat UNIQUE double en mémoire / H2 / PostgreSQL 16 (`PayeursContrat`, 6 cas ajoutés) | le fake et l'adapter réel ne peuvent pas diverger |
| 5 | ACL mince côté consommateur : Séance `ChaineDesPayeursParLAnnuaire` (port `ChaineDesPayeursDuCentre`), Pilotage `CaissesParLAnnuaireDesPayeurs` (port `CaissesDesCentresPayeurs`), plateforme `CaissesDesAgencesParLAnnuaire` (port `CaissesDesAgences` des impressions) | modèle RF2 ; le pilotage dépend désormais du module patient-couverture (aucun cycle Maven : PC ne dépend ni du pilotage, ni de la Séance, ni de la Facturation) |
| 6 | Lecteurs migrés : le `LEFT JOIN` de la chaîne disparaît du SQL ; les lecteurs `GROUP BY` regroupent par centre payeur en SQL (compteurs identiques), puis additionnent par code de caisse en Java (code vide et nom nul sans caisse ; plus grand nom sinon, comme `MAX(ca.nom)`) | résultats identiques, ordre de sortie inchangé pour les consommateurs |
| 7 | Impression de la synthèse mensuelle : les deux `LEFT JOIN` du gabarit dynamique deviennent une expression `CASE f.agence_id_snapshot WHEN CAST('<agence>' AS UUID) THEN '<code>' ...` construite à l'impression depuis l'annuaire du centre (même borne qu'avant sur l'agence et la caisse) ; gabarit statique et jointures du gabarit `synthese_mensuelle_facturation.jrxml` inchangés | le gabarit n'a pas d'accès au langage publié ; pas de lecture par ligne |
| 8 | Garde `TableDesPayeursTest` : quatre entrées retirées (effectif 11 -> 7), règle ArchUnit `les_lecteurs_annuaire_des_payeurs_passent_par_le_langage_publie` | modèle RF2 |

## Lecteurs
| Lecteur | État |
|---|---|
| `SeancesRealiseesDesCentresJdbc.parCaisse` (Séance) | migré |
| `FacturationDesSeancesJdbc.dansLaPeriode` (Séance ; l'agence du centre payeur, pas seulement un nom) | migré |
| `PatientsDesCentresJdbc.parCaisse` (Pilotage) | migré |
| `FacturationSyntheseMensuelleReportService` (plateforme, impression) | migré |
| Absences de patients | aucune entrée dans la liste blanche : rien à migrer |
| Facturation (5 lecteurs `GROUP BY`), Comptabilité `PayeursDuCentreJdbc`, Organisation `ReferentielsCommunsJdbc` | RF3c (inchangés, nommés et datés dans la garde) |

## Écarts de parité
Aucun observé par les goldens. Une nuance de modèle est consignée (RF3b-R1) : les lecteurs de la Séance et du Pilotage joignaient la caisse SANS borne de centre (`ca.id = ag.caisse_id`) alors que l'agence et le centre payeur étaient bornés ; l'annuaire borne aussi la caisse (comme l'impression). Les données sont identiques tant qu'aucune agence ne pointe une caisse d'un autre centre (impossible par la saisie et l'import de RF3a, qui résolvent le parent DANS le centre ; non vérifié sur les données réelles).

## Résiduels nominatifs
| ID | Résiduel | Raison / retrait |
|---|---|---|
| RF3b-R1 | Caisse bornée au centre dans l'annuaire, non bornée dans les anciennes jointures de la Séance et du Pilotage | requête de contrôle sur la base réelle avant la bascule : `SELECT count(*) FROM agence a JOIN caisse_assurance c ON c.id = a.caisse_id WHERE c.center_id <> a.center_id` doit rendre 0 |
| RF3b-R2 | Une lecture de l'annuaire par centre et par appel (`chaines(centre)`) dans les regroupements par caisse | volumétrie d'un centre (quelques centaines de centres payeurs) ; pas de cache |
| RF3b-R3 | `MAX(ca.nom)` : plus grand nom au sens de `String.compareTo` (codes Unicode) et non de la collation de la base, quand deux caisses du centre partagent un code | le code est unique par centre et par niveau : sans effet attendu |
| RF3b-R4 | `PayeursDuCentre.existe` n'a pas encore de consommateur (prévu pour RF3c : Comptabilité, affectation) | RF3c |
| RF3b-R5 | Les contrats PG16 de la Séance et du Pilotage (rejeus propres à leurs scripts) lisent désormais aussi `PayeursDuCentreJdbc` ; le rejeu `patient-couverture-rf3-pg16-rejeu.sh` couvre l'annuaire, les requêtes de lecture des trois lecteurs et l'expression `CASE` de l'impression | non rejoués en entier dans cette passe si leur script n'est pas lancé |

## Conséquences
- Fichiers de la plateforme touchés : `FacturationSyntheseMensuelleReportService` (couvert par le filet de caractérisation), `CaissesDesAgences` et `CaissesDesAgencesParLAnnuaire` (nouveaux, inscrits dans `covered.json`). Aucun seam (`seamAllowed` reste `[]`).
- Retour arrière : revert Git.

## Questions ouvertes
- Confirmation métier de la propriété (ADR 0121, décision 2) avant le 2026-12-15.
- Le contrôle RF3b-R1 est-il à lancer sur la base de production avant la bascule ?
