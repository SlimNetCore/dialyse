---
slice: facturation-f7b-corrections-revue
status: built
date: 2026-10-23
order: 7 bis (correction de F7, après `.ddd/reviews/2026-10-23-facturation-f7.md`)
sources: [.ddd/reviews/2026-10-23-facturation-f7.md (constats 1, 3, 5, 6), .ddd/migration/slices/facturation-f7-cloture-mensuelle.md, ADR 0037, ADR 0042, docs/reference/regles-de-gestion/12-facturation-reglements-comptabilite.md (RG-FAC-041, RG-FAC-043)]
---

> **Slice de correction, refonte sur place (ADR 0024)** : aucune migration, aucune table, aucune URL ni aucun format de
> réponse modifiés ; aucun golden
> existant modifié. Retour arrière = commit précédent.

# F7 bis — Corrections de la revue F7

| Constat                                                                    | Correction                                                                                                                                                                                                                                                                                                                                                                                                                  | Fichiers                                                                                                                                                               |
|----------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1 (minor) — métadonnées JDBC lues à chaque verrou / numéro                 | Mémoïsation paresseuse (`volatile Boolean postgreSql`) du produit de la base, comme `MoisAbsencesJdbc` (A6)                                                                                                                                                                                                                                                                                                                 | `facturation/.../persistence/MoisDeFacturationJdbc`, `facturation/.../persistence/SequenceDeFacturationJdbc`, `absences-patients/.../transaction/UniteDeTravailSpring` |
| 3 (minor) — intervalle de validation : 3 à 4 requêtes par mois couvert     | Nouveau `MoisDeFacturationRepository.verrouillerDe(centre, premier, dernier)` (méthode par défaut = boucle sur `verrouiller`, même garantie) ; l'adaptateur JDBC le fait en **3 requêtes quelle que soit la durée** : lecture des mois présents, création par lot des lignes manquantes, un seul `SELECT ... ORDER BY annee, mois FOR UPDATE` (verrous dans l'ordre croissant) ; `GardeDesMoisClos.exigerOuverts` l'utilise | `domain/cloture/MoisDeFacturationRepository`, `application/cloture/GardeDesMoisClos`, `persistence/MoisDeFacturationJdbc`                                              |
| 5 (nit) — motif absent sur un mois comptablement clos : 422 au lieu de 400 | Ordre des refus de réouverture : mois non clos (409) → **motif (400)** → période comptable (422). `MoisDeFacturation.motifDeReouvertureValide` (statique, domaine) appelé par `RouvrirLeMois` avant l'ACL comptable ; `rouvrir` l'utilise aussi                                                                                                                                                                             | `domain/cloture/MoisDeFacturation`, `application/cloture/RouvrirLeMois`                                                                                                |
| 6 (nit) — fiche F7 périmée                                                 | Fiche F7 mise à jour (frontend livré, documentation client régénérée le 2026-10-23)                                                                                                                                                                                                                                                                                                                                         | `.ddd/migration/slices/facturation-f7-cloture-mensuelle.md`                                                                                                            |

**Non traités ici** (décision métier attendue) : constat 2 (politique commune de délai de verrou Facturation/Absences,
ADR à écrire) et constat 4 (chronologie des clôtures : la Facturation accepte le mois en cours et les mois futurs, les
Absences les refusent).

> **Complément du 2026-10-23 (après-midi)** — décisions utilisateur : constat 4 « comme les absences », constat 2 « ce
> qu'il y a de mieux ».
>
> | Constat | Décision | Réalisation |
> |---|---|---|
> | 4 | **ADR 0044** : seul un mois terminé se clôture, au fuseau du centre, sans jours de grâce ; 422 `PERIODE_NON_TERMINEE` (propriété `mois`) avant tout verrou | `MoisDeFacturation.exigerTermine`, `MoisDeFacturationNonTermine`, port `application/cloture/FuseauxDesCentres` + `infrastructure/horloge/FuseauParDefaut` (`app.facturation.fuseau-par-defaut`, défaut = fuseau des Absences), `CloturerLeMois`, `ClotureDeFacturationAdvice` ; frontend : bouton « Clôturer » désactivé pour le mois en cours et les mois futurs (`facturation-periodes.moisClosable`), clé `FACTURATION.ERROR.PERIODE_NON_TERMINEE` ; RG-FAC-040 ; ADR 0037 révisé (point 3) ; recette `verifications-production.md` § 28.1 |
> | 2 | **ADR 0043** : le délai d'attente des verrous est fixé par l'unité de travail qui **ouvre** la transaction (5 s, comme les Absences) ; refus de verrou ou interblocage = 409 `FACTURATION_ECRITURE_CONCURRENTE` | `facturation/.../transaction/UniteDeTravailSpring` (délai + traduction), `application/EcritureConcurrenteDeFacturation`, `interfaces/http/EcrituresConcurrentesDeFacturationAdvice` (tous les contrôleurs du contexte), `FacturationContexteConfiguration.DELAI_DE_VERROU` ; Absences : `UniteDeTravailSpring` ne pose plus son délai dans une transaction rejointe ; RG-FAC-042 ; clé `FACTURATION.ERROR.ECRITURE_CONCURRENTE` |
>
> Écarts de golden décidés (ADR 0044) : `cloture_sans_seance_avec_corps` porte sur 2026-03 (au lieu de 2027-03) ;
> nouveau `cloture_mois_futur_422` ;
> `cloture_de_facturation_avec_absence_a_qualifier_nouveau_code` (A6) utilise une absence du mois précédent
> (`<FIN_DU_MOIS_PRECEDENT>` au lieu de `<J-3>`).
>
> Tests ajoutés : `MoisDeFacturationTest` (mois terminé), `CloturerEtRouvrirLeMoisTest` (mois en cours et futur refusés
> sans verrou, fuseau du centre
> à la frontière du mois), `UniteDeTravailSpringTest` (traduction des refus de verrou, sans base),
> `MoisDeFacturationConcurrentPostgresContractIT`
> (délai dépassé = 409, délai du propriétaire conservé dans une transaction rejointe ; PostgreSQL 16), HTTP
> `la_cloture_du_mois_en_cours_ou_d_un_mois_futur_est_refusee_422...`,
> specs frontend (`moisClosable`, codes 422/409).
>
> Résultats : facturation 490 tests, absences-patients 388 tests, plateforme (toutes les caractérisations Facturation et
> Absences + règles + frontières) 533 tests :
> **0 échec**. Non exécutés : contrats PostgreSQL 16 (`facturation-pg16-rejeu.sh`), build complet.

## Limite assumée du constat 3

L'intervalle reste **non borné** (aucune règle métier nouvelle) : une validation sur une très longue période crée encore
une ligne `OUVERT` par mois
couvert (sans effet métier, comme avant), mais en 3 requêtes au lieu de ~4 par mois. Borner la période de validation
(par ex. 12 mois, refus 400) reste
possible si le métier le décide.

## Concurrence

- Les verrous de l'intervalle sont posés par un seul `SELECT ... ORDER BY annee, mois FOR UPDATE` : ordre croissant,
  donc toujours aucun interblocage
  entre deux validations, ni avec une clôture (un seul mois) ou une exclusion/réinclusion/changement de forfait (un seul
  mois).
- Sur PostgreSQL, la création par lot `ON CONFLICT DO NOTHING` n'attend que les lignes **insérées** et non validées par
  une autre transaction (insertions
  dans l'ordre croissant des deux côtés : pas de cycle) ; elle n'attend pas une ligne existante verrouillée par
  `FOR UPDATE`.

## Tests

| Niveau                                                                | Ajout                                                                                                                                                     |
|-----------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------|
| Domaine `MoisDeFacturationTest`                                       | `should_validate_a_reopening_reason_on_its_own_strip_it_and_bound_it_to_500_characters`                                                                   |
| Application `CloturerEtRouvrirLeMoisTest`                             | `should_check_the_reason_before_the_accounting_period_when_both_refuse`                                                                                   |
| Contrat `MoisDeFacturationContrat` (mémoire / H2 / PostgreSQL 16)     | intervalle verrouillé dans l'ordre croissant avec création des seules lignes manquantes ; intervalle d'un mois et idempotence ; intervalle inversé refusé |
| Concurrence `MoisDeFacturationConcurrentContrat` (H2 / PostgreSQL 16) | une validation sur juillet-septembre fait attendre la clôture de septembre                                                                                |
| HTTP `ClotureEtReouvertureNouveauCodeIntegrationTest`                 | motif blanc sur un mois comptablement clos : **400** (assertion ajoutée, goldens inchangés)                                                               |

## Documentation

RG-FAC-043 précise l'ordre des refus de réouverture (mois non clos, motif, période comptable) ; documentation client à
régénérer avec la prochaine livraison.

## Résultats (2026-10-23)

- `./mvnw -o -pl contexts/facturation,contexts/absences-patients install` : **BUILD SUCCESS** — facturation 484 tests (1
  ignoré, préexistant), absences-patients 388 tests, 0 échec.
- Plateforme (`-pl platform -am`, tests ciblés) : `ClotureEtReouvertureNouveauCodeIntegrationTest` (13),
  `GardesDuMoisClosNouveauCodeIntegrationTest` (13),
  `ClotureMensuelleDesAbsencesNouveauCodeIntegrationTest` (10), `ModelesDePersistanceAlignesFacturationIntegrationTest`
  (10), `ReglesDeGestionDocumentationTest` (5),
  `FrontieresDesContextesTest` (4) : 55 verts ; **aucun golden modifié**.
- Un seul test existant adapté :
  `ValiderLaFacturationTest.should_refuse_a_period_when_any_month_it_covers_is_closed_and_lock_the_months_in_order` —
  les trois mois
  de l'intervalle (août à octobre) sont désormais verrouillés avant le refus du premier mois clos (septembre), au lieu
  de s'arrêter à septembre ; la transaction est annulée.
- `check-arch.ts` : 0 violation.
- **Non exécutés ici** : contrats PostgreSQL 16 (`MoisDeFacturationPostgresContractIT`,
  `MoisDeFacturationConcurrentPostgresContractIT`, nouveaux cas compris) — à rejouer
  avec `backend/scripts/facturation-pg16-rejeu.sh` ; build complet `./mvnw -o test`.



