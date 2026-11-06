---
status: characterized (filet vert sur le legacy, commité en 853fd67b ; PC2 repris sur place ensuite, voir la fiche)
date: 2026-11-03
sources: .ddd/migration/slices/patient-couverture-pc2-assure-principal.md, .ddd/tactical/patient-couverture.md, ADR 0024/0064/0073/0076, docs/reference/regles-de-gestion/04-patients-assures-pec.md (RG-ASS-*), known-bugs.md KB-PAT-29 à 34
---

# Filet de caractérisation : patient-couverture-pc2-assure-principal

Refactoring sur place (ADR 0024). **Aucun seam, aucune modification de production** (`seamAllowed` vide). Référence : les 5 routes d'assuré `/api/v1/patients/{id}/assures/**` et `/api/v1/patients/assures/**`, déjà reprises sur place par PC1 (`PatientsControleur`, `GererLesAssures`, `AssuresJdbc`, ACL `CouvertureDeLaFicheJdbc`, garde de centre et droits restaurés, jour du centre) ; le code gelé (`AssureService`, `AssurePatientsJdbc`, `AssuresControleur` du module, `bc-assure`, `bc-insurance`) est exclu de la compilation et n'est pas exercé : non protégé.

## Point d'observation

HTTP (MockMvc, contexte Spring complet, sécurité réelle + `RoleScopeFilter`, H2 `test`), état SQL de `assure` et `assure_patient` (jointure sur le nom du patient, aucun identifiant brut), notifications durables, diffusions, audit. Aucun mock. Harnais : `backend/platform/src/test/java/com/hemodialyse/backend/characterization/patientcouverture/CouvertureCharacterizationSupport.java` (au-dessus de `LiberationCharacterizationSupport` : décor, dates nommées, diffusions, audit, attente de minuit du fuseau du centre). Goldens : `backend/platform/src/test/resources/characterization/patientcouverture/pc2/<dossier>/<scénario tronqué à 36 caractères>/` (68 fichiers).

## Déjà verrouillé avant (non rejoué)

| Filet | Verrouille |
|---|---|
| `characterization/patientcouverture/assure` (5 scénarios, 8 goldens) | affecter, modifier assuré, modifier affectation, liste, refus d'un autre centre (nominal seulement) |
| PC1 `autorisations` | matrices 11 profils + `PHARMACIEN`, `DIRECTEUR_ADMINISTRATIF`, rôle personnalisé + anonyme des 3 écritures et 2 lectures d'assuré ; garde de centre (403) des lectures et des écritures |
| PC1 `creation`, `modification` | assuré tiers à la création, bascule de qualité d'assuré, doublons de numéro de patient |

## Scénarios ajoutés (22, 3 classes)

| Classe | Nb | Verrouille |
|---|---|---|
| `pc2.AffectationDeLAssurePrincipal` | 9 | affectation nominale (réponse, ligne, historique) ; R1 : second assuré ferme le principal à la même date, retour au premier, un seul principal actif ; même affectation deux fois le même jour ; refus (12 cas : patient assuré lui-même, qualité nulle ou inconnue acceptées, assuré inconnu, casse, espaces, autre centre, patient inconnu, centre absent ou illisible) ; correction de dates (11 cas : fin avant début 400, fin = début, nuls, fin sans début, début futur, corps vide, date illisible, affectation inconnue, autre patient 403) ; principale fermée par date de fin ; un assuré partagé entre deux patients ; historique vide et assuré disparu ; deux principaux hérités |
| `pc2.ModificationEtRechercheDesAssures` | 6 | modification nominale et relecture par la fiche (`assureInfo`) ; corps vide efface tout ; date de naissance (9 formes) ; téléphones, sexe, groupe, longueurs 255/256 sans validation (500 à 256) ; refus (inconnu, casse, corps illisible, `[]`, `null`, champ inconnu, type de contenu, centre) ; recherche (17 valeurs de `q`, jokers, accents, tri, centre B, centre absent) |
| `pc2.AssurePrincipalDeLaFiche` | 7 | R1 par la fiche (changement d'assuré, retour, re-saisie : lignes ajoutées, anciennes actives sans dates) ; bascule vers assuré lui-même ; deux patients, un numéro ; numéro détenu par un autre centre repris ; ayant droit sans numéro 400, qualité inconnue acceptée ; audit, diffusions, notifications des 3 écritures ; deux affectations simultanées |

## Non-déterminisme

Dates par alias relatifs nommés (`<J>`, `<J-30>`, `<J-9000>`...), jamais de date réelle ni de jour de mois ou de semaine : le harnais échoue si une date non nommée reste dans un golden. Horodatages masqués (`<HORODATAGE>`), identifiants nommés ou `<UUID_GENERE_n>`, code patient masqué. Historique ordonné par horodatage de création : pauses de 15 ms entre appels successifs, lignes de fixture à horodatage fixe. Course (deux `affecter`) : seul l'ensemble des issues possibles est verrouillé. Les routes d'assuré datent au jour du centre (KB-PAT-17 corrigé) : aucun scénario ignoré. Interclassement et NULL en tête : H2 seulement.

## Stabilité

Trois exécutions des 120 tests de `characterization/patientcouverture/**` (PC2 et PC3 ajoutés, PC1 et les 10 existants) : ordre alphabétique, puis deux ordres aléatoires (`-Dsurefire.runOrder=random`) : 0 échec, 0 ignoré, 0 flaky.

Commande : `cd backend; ./mvnw -o -q -pl platform -am test -Dtest='com.hemodialyse.backend.characterization.patientcouverture.pc2.*IntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false` (`-Dcharacterization.approve=true` pour régénérer après revue ; jamais deux builds Maven en parallèle).

## Couverture et mutation

Pas de JaCoCo ni de PIT dans la chaîne Maven hors ligne pour le code de la zone : aucune mesure de branches ni de mutants. Couverture raisonnée : chaque branche de `GererLesAssures` (patient inconnu, assuré lui-même, assuré inconnu, autre centre, fenêtre de dates, fermeture de la principale), de `AssuresJdbc` (recherche avec et sans texte, historique, fermeture, ajout, mise à jour) et de la partie assuré de `CouvertureDeLaFicheJdbc` (assuré lui-même, nouvel assuré, assuré existant, date illisible) est exercée par au moins un scénario. 5 fichiers inscrits dans `covered.json`.

## Limites (NON verrouillé)

- PostgreSQL (H2 seulement) : interclassement, tri des NULL, types, verrous, vraie concurrence (une paire d'appels, issues bornées) ; volumétrie.
- Droits de rôles et garde de centre : verrouillés par PC1, non rejoués ; le rôle `SECRETAIRE` et la direction n'ont pas de scénario propre en PC2.
- Le code gelé (`bc-assure`, `bc-insurance`, `AssureService`, `AssurePatientsJdbc`, contrôleur du module) n'est ni compilé ni exercé : non déclaré couvert. `AttestationService` relève de PC4.
- Caches `patient.assure.*`, `patient.assignment.*` de `CacheConfig` : plus utilisés par le code repris, non éprouvés.
- Lecteurs de l'assuré hors module (migration `MigrationRollbackJdbcAdapter`, `AffectationMigrator`) : relevé PC1, non rejoué ; aucun écrivain hors module de `assure`/`assure_patient` trouvé hors migration.
- Fuseau : bascule de minuit non rejouée (attente de minuit héritée).

## Comportements suspects

`.ddd/safety-net/known-bugs.md` : KB-PAT-29 à KB-PAT-34 (R1 non garantie et dates libres, affecter permissif, numéro d'assuré global repris entre centres, lignes ajoutées à chaque fiche, modification totale sans validation, recherche non rognée).

## Questions de décision pour l'utilisateur (PC2)

1. R1 : verrou de centre sur `affecter` (comme les écritures de fiche) et contrainte SQL d'unicité du principal actif ? Fermer la ligne lors de la bascule vers « assuré lui-même » ? Refuser une qualité nulle ou inconnue à l'affectation ?
2. Doublons : une re-saisie identique de la fiche doit-elle créer une ligne ? Affecter deux fois le même assuré le même jour ? Numéro d'assuré unique par centre (clé composite) pour mettre fin à la reprise entre centres (KB-PAT-31) ?
3. Correction de dates : refuser début nul, fin sans début, chevauchement avec une autre affectation ? Une principale fermée par date de fin doit-elle perdre `is_primary` ?
4. Modification d'un assuré : remplacement total ou mise à jour partielle ? Valider téléphone, sexe, groupe sanguin et longueurs (400 au lieu de 500) ?
5. Droits : l'écriture d'assuré reste fermée au médecin seul (RG-PAT-013) ; confirmer la direction et le superadmin refusés.
6. Fuseau : jour du centre partout (déjà le cas pour l'affectation).
7. Vocabulaire : « assuré principal » (glossaire) contre `is_primary` ; `numero_assurance` du patient contre celui de l'assuré (`assure_numero_assurance`).
