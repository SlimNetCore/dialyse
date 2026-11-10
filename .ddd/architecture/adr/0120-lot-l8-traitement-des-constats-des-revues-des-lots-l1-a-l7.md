---
status: accepted
date: 2026-11-10
review-by: 2026-12-15
decision-makers: [utilisateur (décisions A à E de la demande du lot L8, 2026-11-10 ; confirmation des métiers et du DPO attendue au plus tard le 2026-12-15), architecte]
context-scope: seance (StatistiquesDesSeancesJdbc, SeancesRealiseesDesCentresJdbc, VoletsDeLaSeanceJdbc), pilotage-direction (SeuilDAnonymat, ConstituerLeRapportDeSynthese, PatientsDesCentresJdbc), patient-couverture (commentaire LectureDesMouvementsJdbc), facturation (contrat PostgreSQL de la reprise), goldens
related: ADR 0024 et 0075 (refactoring sur place), 0112 (L1, addendum), 0113 (L2), 0114 (L3), 0115 (L7), 0116 (L4), 0117 (L5a), 0118 (L5b), 0119 (L6) ; revues `.ddd/migration/reviews/revue-L1-L2.md`, `revue-L3-L4-L7.md`, `revue-L5a-L5b-L6.md` ; `.ddd/safety-net/known-bugs.md` (lot L8) ; `.ddd/migration/verifications-production.md` §72, 73, 76 à 78, 82 ; RG-DIR-104, 111, 112, 113 et RG-SEA (volet paramédical, 07-seances.md)
---

# 0120 - Lot L8 : traitement des constats des trois revues indépendantes des lots L1 à L7

Ni façade, ni flag, ni projection, AUCUNE migration Flyway, AUCUNE donnée réécrite. Les revues (lecture seule, au diff) concluaient « pass-with-actions » sans blocker ; ce lot traite chaque constat par une décision déjà prise par l'utilisateur. La **confirmation des métiers, des médecins et du DPO reste à obtenir (au plus tard le 2026-12-15)**.

## Décisions
| Constat | Décision | Où |
|---|---|---|
| M1 (V55 `NOT VALID` évalué à chaque UPDATE) | **V55 GARDÉE `NOT VALID`.** 76.1, 77.1 et 78.1 deviennent des **PRÉREQUIS BLOQUANTS du déploiement** (résultat joint à la checklist) : un CHECK `NOT VALID` n'est pas ignoré, il est appliqué à chaque `UPDATE` d'une ligne, y compris d'une colonne sans rapport ; une séance au statut non canonique serait non modifiable. §72 et §73 sont marquées « REMPLACÉ PAR §77 » (m3) | `verifications-production.md` §72, 73, 76, 77, 78, 82 |
| M2 (volet paramédical : saisie par séance, lecture bornée au centre) | **Lecture alignée sur la saisie** : `VoletsDeLaSeanceJdbc.paramedical` lit par l'identifiant de séance (la séance est déjà bornée au centre en amont) ; le volet médical reste borné au centre. Un volet posé sous un autre centre est vu ; la donnée n'est PAS réécrite (réparation §78 : décision manuelle) | `VoletsDeLaSeanceJdbc`, RG-SEA (volet paramédical) |
| KB-DIR-01 (constat 1, major) | Libellé **« corrigé (totaux) + résiduel ouvert (recoupement : effectif réel du centre moins ventilation publiée) »** ; **condition de mise en production avec avis du DPO** ; addendum dans l'ADR 0112, known-bugs, RG-DIR-111 | ADR 0112 (addendum), known-bugs, RG-DIR-111 |
| Duplication de la règle de masquage (constat 3) | UNE définition dans le domaine : `SeuilDAnonymat.totalPublie(totalBrut, composantsBruts)` ; `totalPublieDepuis` (relecture d'un contenu déjà masqué) en est la même règle lue sur des valeurs publiées ; **test de parité** `LaSuppressionComplementaireEstUneSeuleRegleTest` (calcul en direct = relecture figée sur 8 jeux). `BilanDuCentre` et `RepartitionDeLaSociete` gardent leurs drapeaux mais s'appuient sur le prédicat commun `estMasque` | `SeuilDAnonymat` |
| Asymétrie `ABSENT` / `""` du PDF (constat 4) | Clé de comparaison unique `cleDeCaisse` (code nul ou « — » = vide) pour les cellules ET les totaux ; un total de caisse à code nul est désormais masqué si l'une de ses cellules l'est | `ConstituerLeRapportDeSynthese` |
| `porteursPositifs` vs isolement (constat 2) | **Règle NON changée** ; question ouverte médecins (même échéance). §66.1 marquée historique, §75.1 liste de référence (constat 7) | RG-DIR-111, §66 |
| L5b, 3 goldens sans suffixe (constat 1) | Renommés `pdf_nouveau_code`, `nom_du_centre_nouveau_code`, `repartition_par_caisse_nouveau_code` (nouveau fichier écrit à l'identique, ancien `git rm` ; leurs archives `_avant_l5b.legacy.json` existaient déjà) ; le nom est porté par `arbre(...)` du test | goldens se7e, dm8, dm9 |
| Poids sec nullable (constat 2 de L5) | `ORDER BY pm.date_prescription DESC, pm.updated_at DESC NULLS LAST, pm.id DESC` : `updated_at` est NULLABLE (V1) ; H2 (nulls en dernier) et PostgreSQL (nulls en premier) divergeaient | `StatistiquesDesSeancesJdbc` |
| Agence non bornée (constat 3 de L5) | `ag.center_id = cp.center_id` ajouté aux deux jointures (effectifs par caisse, séances par caisse) : la chaîne payeur est bornée à tous les étages, comme la facturation | `PatientsDesCentresJdbc`, `SeancesRealiseesDesCentresJdbc` |
| `DirectionGmaoQueryService` : `LocalDate.now()` (constat 4 de L6) | **RÉSIDUEL DÉCLARÉ, non corrigé.** Possible sans cycle de modules, mais il faut un nouveau port publié du Pilotage (jour de la Direction) + son adaptateur + un changement de constructeur du service de la plateforme et de ses tests + les tables d'architecture (`TableDuPilotageTest`), pour un effet limité à la fin de période PAR DÉFAUT de `/gmao` entre 23 h et minuit UTC. À traiter avec le résiduel « fuseau par centre » (ADR 0119), qui remplacera de toute façon cette horloge | known-bugs, RG-DIR-113 |
| 405 sans 403 (constat 5 de L6) | **Documenté, non corrigé** : l'erreur est levée avant le choix du contrôleur, donc avant `@PreAuthorize` ; retrouver la garde de la route GET voisine n'est pas trivial. Atténuation : `anyRequest().authenticated()` (sans jeton : 401) ; l'information divulguée est la liste des méthodes d'une route déjà connue du client | RG-DIR-112 |
| Casse stricte de `'ABSENT'` (constat 6 de L5) | **Documenté, non corrigé** : forme canonique seule reconnue, comme `FacturationDesSeancesJdbc` et le CHECK V55 ; aucune donnée en minuscules (§79) ; l'élargir créerait une 3e convention | known-bugs KB-SEA-81 |
| KB-SEA-96 (b), KB-SEA-85 (b), âge des anciens instantanés | **Questions ouvertes** (référent médical à nommer + ticket DM6 ; confirmation infirmiers/médecins = condition de déploiement ; absence de marqueur de version des tranches d'âge figées) | known-bugs, RG-DIR-113 |
| m1 (DELETE de `RepriseDesSoldesJdbc.supprimer` face à la FK V54) | Test de contrat PostgreSQL 16 ajouté (suppression d'une facture de reprise possible alors qu'une autre facture du patient est liée à une séance). **Compilé, NON exécuté ici** (exige `-Dfacturation.pg16.url`) ; aucun rouge possible (il verrouille un comportement existant) | `RepriseDesSoldesPostgresContractIT` |
| m2 (V56 : nouvelles notifications de péremption) | Effet documenté ; SELECT de comptage en lecture seule **82.1** (borne haute par centre) ; checklist | `verifications-production.md` §82 |
| m4 (commentaire du journal des mouvements) | Commentaire corrigé : « centre demandé = centre du mouvement » (`m.center_id = ?`) | `LectureDesMouvementsJdbc` |
| Constats 5 et 6 de L1-L2 (consolidation clinique dans l'application, relecture heuristique) | Constat 6 documenté (RG-DIR-111) ; constat 5 (déplacer `consolider` dans le domaine) **non traité** : refactor sans effet de comportement, hors du lot ; à regrouper avec une prochaine passe sur `CalculerLesIndicateursCliniques` | known-bugs |

## Tests d'abord, ROUGE VÉRIFIÉ avant le code
| Test | Rouge constaté |
|---|---|
| `PilotageEtRapportsDesSeancesJdbcContratTest` : agence d'un autre centre, poids sec (`updated_at` nul puis identifiant) | 2 échecs sur 18 sur H2 (le double en mémoire était étendu avec la règle : il passait déjà) |
| `LecturesDuPilotageJdbcContratTest` : agence d'un autre centre que son centre payeur | 1 échec sur 9 (code attendu `""`, la caisse de l'autre centre était rendue) |
| `ConstituerLeRapportDeSyntheseTest` : total de caisse à code nul | 1 échec sur 11 (le total n'était pas masqué) |
| `VoletsDeLaSeanceLuParIdDeSeanceTest` : volet d'un autre centre visible au détail | 1 échec sur 2 |
| `LaSuppressionComplementaireEstUneSeuleRegleTest` (parité) | rouge par ABSENCE de `SeuilDAnonymat.totalPublie` (échec de compilation, puis vert une fois la méthode ajoutée) |
| Golden `detail_volets_au_centre` | l'ancien golden affichait `paramedical : null` sous le centre B ; le `.received` montre le volet (relu, approuvé à la main) |
| `RepriseDesSoldesPostgresContractIT` (m1) | non exécuté (PostgreSQL 16 requis) |
Passes ciblées : ordre par défaut, `-Dsurefire.runOrder=random`, puis les caractérisations Séance / Pilotage / Dossier médical et les tests d'architecture (695 tests) ; suite complète `./mvnw -o test -fae` : voir la réponse du lot.

## Goldens
Approuvés à la main, un par un, après lecture du `.received` (jamais `-Dcharacterization.approve=true`).
| Golden | Changement | Archive |
|---|---|---|
| `seance/se7f/volet/le_detail_de_la_seance_relit_les_vol/detail_volets_au_centre_nouveau_code` (ex sans suffixe) | le volet paramédical posé sous le centre B est lu au détail de la séance du centre A (avant : `null`) ; volet médical inchangé (`null`, borné au centre) ; test renommé `…le_paramedical_par_la_seance_comme_la_saisie_et_le_medical_au_centre` (même dossier : nom tronqué à 35 caractères) | `seance/se7f/_ecarts-legacy/volet/detail_volets_au_centre_avant_l8.legacy.json` |
| `dm8/stats/…/pdf_nouveau_code`, `dm9/stats/…/nom_du_centre_nouveau_code`, `seance/se7e/pilotage/…/repartition_par_caisse_nouveau_code` | RENOMMAGE SEUL (contenu identique) | `_avant_l5b.legacy.json` existants |

## Conséquences
- Le détail d'une séance montre un volet paramédical que la saisie vient d'enregistrer, même s'il reste posé sous un autre centre (cohérence écriture / lecture).
- Les effectifs et séances par caisse n'attribuent plus une caisse d'une agence d'un autre centre.
- La mise en production dépend d'une checklist de prérequis bloquants (§82).
- Résiduels ouverts : recoupement de ventilation (DPO), `porteursPositifs`, horloge de `/gmao`, 405 sans 403.

## Alternatives écartées
- Valider V55 d'office ou la différer : décision A (garder `NOT VALID` + prérequis bloquant).
- Refuser la saisie (409) tant que le volet est hors centre : plus lourd pour l'utilisateur que l'alignement de la lecture.
- Un nouveau port publié du Pilotage pour l'horloge de `/gmao` : disproportionné, voir ci-dessus.
- Changer la règle de `porteursPositifs` : décision C (question ouverte).
