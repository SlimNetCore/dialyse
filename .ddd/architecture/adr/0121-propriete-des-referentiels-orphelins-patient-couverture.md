---
status: accepted
date: 2026-11-10
review-by: 2026-12-15
decision-makers: [utilisateur (décisions 1 à 5, prises en interactif le 2026-11-10 ; ce sont des choix de MODÉLISATION, à confirmer par les métiers au plus tard le 2026-12-15), architecte]
context-scope: patient-couverture (propriétaire cible), facturation, seance, pilotage-direction, comptabilite, absences, organisation (lectures `/referentials`), platform (admin générique `ReferentialAdminRestController`, reporting)
related: ADR 0024 et 0075 (refactoring sur place, mêmes URLs), 0070 (hébergement de l'admin générique), 0079 (PC5, `patientcouverture.v1`), 0097 (ports fournis Facturation), 0107 à 0111 (O4) ; analyse `.ddd/migration/reviews/analyse-referentiels-orphelins.md` ; fiche O5 `.ddd/migration/slices/organisation-o5-retrait-bc-referential.md` ; fiches `.ddd/migration/slices/patient-couverture-rf1-transporteurs.md`, `rf2-forfaits.md`, `rf3-payeurs.md`
---

# 0121 - Propriété des cinq référentiels « orphelins » : Patient & couverture

Ni façade, ni flag, ni projection : refactoring SUR PLACE (ADR 0024), mêmes tables, mêmes URLs, rôles ADMIN/SUPERADMIN et garde de centre conservés. Cet ADR décide uniquement QUI possède les données ; le code n'est pas touché ici. L'analyse statique est dans `.ddd/migration/reviews/analyse-referentiels-orphelins.md` (elle recommandait Facturation pour les payeurs : écartée par la décision 2).

## Contexte
L'admin générique (`ReferentialKind`) expose neuf types ; cinq n'ont pas de propriétaire depuis le retrait de `bc-referential` (O5) : payeurs (en réalité TROIS tables chaînées `caisse_assurance` -> `agence` -> `centre_payeur`), forfaits (`forfait`), transporteurs (`transporteur`, et sa sœur `categorie_transport`). Soit 6 tables. Un seul écrivain : le CRUD et l'import CSV/Excel générique (SQL dynamique). Le module `migration` n'écrit dans aucune de ces tables (il ne cite que des identifiants de forfait : vérifié par l'utilisateur). La difficulté est la LECTURE inter-contextes et les contrôles d'usage.

## Décisions
| N° | Décision | Raison |
|---|---|---|
| 1 | Le **forfait (tarif)** relève de **Patient & couverture** (accord de prise en charge), pas de Facturation | la PEC est le point d'ancrage tarifaire (`patient-couverture.md` : « Forfait de PEC ») ; Facturation, Séance et Absences dépendent déjà de PC en Maven (`facturation/pom.xml:99`, Séance, Absences) : aucun cycle ; le prix de PEC se joint déjà en interne (`CouvertureDesPatientsJdbc`) |
| 2 | Le **centre payeur est un attribut du PATIENT** (`patients.centre_payeur_id`) : **caisses d'assurance, agences et centres payeurs** appartiennent à **Patient & couverture** | la clé est portée par la fiche patient, écrite et validée par PC ; Facturation ne fait que FIGER l'agence et le centre à l'émission (`agence_id_snapshot`, `centre_payeur_id_snapshot`) |
| 3 | **Comptabilité** (lit `centre_payeur` via `PayeursDuCentreJdbc`) lit désormais le langage publié `patientcouverture.v1` (`PayeursDuCentre`) : plus de SQL direct sur une table d'un autre contexte. **Pas de port dans le shared-kernel.** **RÉVISÉE le 2026-11-10** : la décision initiale (lecture SQL en ACL) reposait sur un cycle Maven que les pom.xml n'établissent pas (voir l'écart de vérification ci-dessous) ; l'utilisateur a choisi le langage publié en interactif | Aucun cycle : `patient-couverture` ne dépend ni de `comptabilite` ni de `facturation`. Le lien `comptabilite -> patient-couverture` est à ajouter en RF3c, après relevé des pom et du réacteur |
| 4 | **Transporteurs** (et `categorie_transport`) : **Patient & couverture** | `patients.transporteur_aller_id/retour_id`, seul consommateur métier |
| 5 | Écrivain unique actuel = CRUD/import générique ; le module `migration` n'écrit rien | vérifié |

Choix de modélisation : **« à confirmer métiers ≤ 2026-12-15 »** (décisions 1, 2 et 4 surtout : sens métier de « tarif », « payeur attribut du patient »). Tant que non confirmé, aucun déplacement physique de données : les slices RF1 à RF3 ne déplacent que le code et les lectures.

### Écart de vérification des pom.xml (à lire)
Le raisonnement « Comptabilité ne peut pas dépendre de Patient & couverture sans cycle » n'est **PAS confirmé** par les pom (relevé du 2026-11-10) :
- `comptabilite` -> `shared-kernel`, `stock` seulement (`comptabilite/pom.xml:27,86`) ;
- `patient-couverture` -> `shared-kernel`, `organisation`, `planification` (pom:79-93) ; `organisation` -> `shared-kernel`, `stock`, `gmao` ; `planification` -> `gmao`, `shared-kernel`, `organisation` ; `facturation` -> `comptabilite`, `patient-couverture` (`facturation/pom.xml:28,99`) ; aucun module du fil PC ne dépend de `comptabilite` ni de `facturation`.
Donc `comptabilite -> patient-couverture` n'ajouterait PAS de cycle Maven (le cycle visé par l'analyse venait de l'hypothèse « payeurs chez Facturation »). La décision 3 (ACL SQL) reste prise par l'utilisateur, mais sa vraie raison est le COUPLAGE (tirer organisation, planification, gmao et stock dans le fil de Comptabilité pour trois colonnes de libellé), non le cycle. **À reconfirmer** : si le métier préfère l'API publiée, la liste blanche de Comptabilité se réduit à zéro (une seule entrée aujourd'hui).

## Cible
- Langages publiés `patientcouverture.v1` NOUVEAUX : `Transporteurs`, `ForfaitsDuCentre` (UN seul, remplaçant les deux ACL JDBC dupliquées de Séance et de Facturation), `PayeursDuCentre` (caisse, agence, centre payeur). Convention de PC5 : interfaces de `publishedlanguage`, `UUID`/`java.time`, bornées au centre.
- Gardes de table nominatives (une par référentiel, modèle `TableDeLOrganisationTest` / `TableDuPatientCouvertureTest`) : liste blanche NOMMÉE et DATÉE des lecteurs SQL directs hors PC, effectif asserté, échec sur entrée morte.
- L'écran `/api/v1/admin/referentials` reste une façade aux mêmes URLs et rôles ; ses types migrent un à un vers le CRUD de PC (ordre : transporteurs -> forfaits -> payeurs).
- Lectures `/api/v1/referentials/{transporteurs,categories-transport,forfaits,caisses,agences,centres-payeurs,centres-payeurs-details}` : le code d'Organisation (`ReferentielsCommunsJdbc`, partie concernée) est retiré ; Organisation ne publie plus ces lectures.

## Conséquences
- Facturation, Séance, Absences, Pilotage et le reporting lisent via `patientcouverture.v1` (Facturation et Absences dépendent déjà de PC ; Séance aussi ; Pilotage et la plateforme via ports fournis/ACL minces, sans nouveau lien de module dans le sens inverse).
- Glossaire Facturation : l'attribution de « Payeur » et « Caisse d'assurance / Agence » à Facturation (`facturation.md:17-18`) est un **écart à corriger** (statut `proposed`, note ajoutée) ; Facturation garde « Payeur figé » (instantané à l'émission).
- Le terme « forfait » a TROIS sens (PEC, appliqué, du centre) : le catalogue n'a pas de terme propre ; « Catalogue de forfaits » proposé.
- Glossaires : termes absents ajoutés en `proposed` (Transporteur, Catégorie de transport, Catalogue de forfaits, Caisse d'assurance, Agence, Centre payeur).

## Risques
| Risque | Mesure |
|---|---|
| Cycles Maven (Comptabilité, Planification, Pilotage) | lecture des pom faite (voir écart) ; aucun nouveau lien de module sans relevé ; Comptabilité lit le langage publié (décision 3 révisée) ; le lien `comptabilite -> patient-couverture` est à vérifier au réacteur en RF3c |
| Jointures SQL des lecteurs (`GROUP BY` Facturation, Pilotage, reporting platform, Séance) sur `agence`/`centre_payeur`/`caisse_assurance` | découper changerait groupement et tri (leçon PC5 #10, KB-FAC-49) : ces lecteurs restent en jalons nominatifs datés ; seuls les lecteurs « annuaire » (nom, code, liste) migrent |
| Caches `ref.*` (`CacheConfig.java:48-57`), invalidés par `ReferentialAdminTables` | le propriétaire reprend l'invalidation, APRÈS commit, clés incluant `centerId` ; test d'invalidation par type |
| Contrôles d'usage à la suppression (`Usage`, `ReferentialAdminTables.java:19-73` : `COUNT` dans patients, `prise_en_charge`, `facture_lignes`, `agence`, séances) | à REMPLACER par des lectures publiées d'usage (modèle `UsagePublie`, SE7e) : `UsageDuForfait` (PEC dans PC, lignes de facture par Facturation, séances par Séance), `UsageDuPayeur`, `UsageDuTransporteur` ; messages d'erreur identiques |
| Import CSV/Excel (ordre 1/2/3 caisse -> agence -> centre payeur, validation `phone`, clé naturelle) | caractérisé avant déplacement (goldens d'import : vérification, import, rejeu, refus) ; même contrat de gabarit |
| Faux « propriétaire » sans valeur tant que l'écran générique reste | la valeur n'apparaît qu'au retrait du type dans `ReferentialKind` ; conditions de retrait dans chaque fiche |
| Entités JPA `CentrePayeurJpaEntity`, `CaisseAssuranceJpaEntity` (platform) non analysées | inventaire en tête de RF3 ; à traiter avec `SocieteJpaEntity` (O5) |
| Comptabilité lit `centre_payeur` en SQL | une entrée de liste blanche nominative, datée, butoir 2027-03-31 |

## Alternatives écartées
- **Facturation propriétaire des payeurs** (recommandation de l'analyse) : plus gros lecteur et langage déjà décrit, mais le centre payeur est une donnée de la fiche patient (décision 2) et PC ne peut pas dépendre de Facturation (`facturation -> patient-couverture`).
- **Facturation propriétaire du forfait** : naturel côté prix, mais PC ne pourrait plus joindre le prix de PEC sans cycle et Séance n'en dépend pas.
- **Garder transporteurs et forfaits dans Organisation (« annuaire commun »)** : coût nul, mais perpétue un contexte propriétaire de tables qu'il ne comprend pas.
- **Port dans le shared-kernel pour Comptabilité** : élargit le noyau partagé pour un seul lecteur ; contraire au principe d'un langage publié par contexte propriétaire.
- **Référentiel partagé entre centres** (au lieu de tables à `center_id`) : hors périmètre ; question ouverte.

## Questions ouvertes
1. **`forfait.prix` nul** : sens métier d'un forfait sans prix (KB-SEA-45/47 le tolèrent) ? Facturation affiche « Forfait non defini » et refuse l'émission (422) ; faut-il interdire un prix nul à la saisie du catalogue ?
2. **Le terme « forfait » a trois sens** : forfait de PEC (`patient-couverture.md:19`), forfait appliqué (`facturation.md:16`), forfait du centre (`seance.md:43`) ; le catalogue lui-même n'a pas de nom : « Catalogue de forfaits » à valider.
3. **Termes absents des glossaires** (ajoutés en `proposed`) : transporteur, catégorie de transport, caisse d'assurance, agence, centre payeur (côté PC), catalogue de forfaits.
4. Comptabilité : TRANCHÉE le 2026-11-10 par l'utilisateur : API publiée `PayeursDuCentre` (possible sans cycle).
5. `CentrePayeurJpaEntity` et `CaisseAssuranceJpaEntity` : qui les utilise ?
6. Tables isolées par centre dupliquées par centre (modèle actuel) ou référentiel partagé ?
7. Le métier valide-t-il « centre payeur = attribut du patient » ? Un payeur par facture (changement de payeur en cours de suivi) reste couvert par le figeage à l'émission.
