status: proposed
date: 2026-11-16
sources: backend/platform/src/test/java/com/hemodialyse/backend/architecture/

# CLOT3 : plus aucun butoir ni revue datés dans les gardes d'architecture

Directive : « aucun butoir, pas d'échéance » ; un retrait se lie à un événement (condition de sortie en texte).

## Changements (tests uniquement, aucune modification de production)
| Garde | Changement |
|---|---|
| EcrivainsDeLaFactureDesSeancesTest | REVUE, SANS_ECHEANCE_DE_RETRAIT, LocalDate retirés ; record Ecrivain(nature, conditionDeSortie) ; test renommé `chaque_ecrivain_porte_une_nature_et_une_condition_de_sortie` |
| TableDesAnalysesTest, TableDesSeancesTest, TableDuDossierMedicalTest | REVUE, LocalDate, isAfter(REVUE) retirés ; Lecteur(nature, conditionDeSortie) ; test `chaque_entree_porte_une_nature_et_une_condition_de_sortie` (listes vides, entrée future exigée avec nature et condition) |
| TableDuPilotageTest | deux propriétaires : butoir 2099 remplacé par une condition de sortie textuelle ; test `la_liste_blanche_compte_deux_proprietaires_avec_nature_et_condition_de_sortie` (plus de préfixe « 2026- » exigé) |
| TableDuPatientCouvertureTest | PLACE/CYCLE sans préfixe de date, « échéance » devenu « condition de sortie » ; test `chaque_jalon_porte_une_nature_et_une_condition_de_sortie` (exige la condition, interdit toute date AAAA-MM-JJ) |
| TableDesEquipementsDeLaGmaoTest | test de jalon sans regex de date, exige « condition de sortie : » |
| TableDeLaPlanificationTest, TableDeLOrganisationTest, TableDesPayeursTest, LaFacadeDesReferentiels…Test | commentaires, Javadoc et messages sans butoir ni date |

Messages « (nommés, datés, butoir) » remplacés par « (nommés, avec nature et condition de sortie) ». La détection des nouveaux lecteurs et écrivains est inchangée (aucun affaiblissement) et aucune garde ne dépend d'une horloge. ADR non touchés.

## Vérification
Passe ciblée (tests réels, surefire-reports) : Table* 99 tests, Ecrivains 6, FrontieresDesContextes 91, 0 échec ; check-arch.ts : 0 violation. Build complet (3589 tests plateforme) : 14 échecs, aucun dans une garde d'architecture ; tous dans des tests de caractérisation ou d'intégration liés à la date du jour (périodes « 1er janvier à aujourd'hui », scan de séance « aujourd'hui », exécutés juste après minuit) ou connus (InventairePl7 feuille_de_comptage), sans rapport avec ce changement qui ne touche que des tests d'architecture.
