---
status: superseded by 0024
date: 2026-10-11
decision-makers: [architecte, direction technique, exploitation]
context-scope: gmao (slices G1 à G6)
research: [.ddd/research/spring-boot-4-web-data-tx.md, .ddd/research/spring-modulith-events-outbox.md]
sources: [.ddd/verify/slice-gmao-g1-review.md, .ddd/verify/slice-gmao-g2-review.md (m-6, M-G2-1, M-G2-2, n-4), .ddd/migration/slices/gmao-g1-plans-maintenance.md (Réalisation, M4, correctifs), .ddd/migration/slices/gmao-g2-intervenants.md (Réalisation, correctifs), .ddd/migration/slices/gmao-g3-cycle-vie-equipement.md, ADR 0013, 0017 (amendé), 0018, 0019, 0021, 0022, .ddd/arch-rules.json, .ddd/architecture/layout.md, ports-adapters.md, backend/contexts/gmao (code lu le 2026-10-11)]
---
# 0023 — Généraliser la mécanique Strangler de GMAO : un descripteur de tranche et des composants communs, plus de copie par slice

## Contexte et problème
G1 et G2 ont chacune leur copie de la mécanique Strangler. Seuls la séquence `BasculerUnCentre`, `TableDeRoutage`/`RoutageParTable`, `EvenementsEnAttente`, `PolitiqueDeReprise` et l'API d'exploitation sont déjà communs. Restent copiés par tranche :
- la façade : `RoutageStranglerMapping` et `RoutageStranglerIntervenantsMapping` (89 lignes sur 183 diffèrent) ;
- le propriétaire : `ProprietaireDesPlans(Jdbc)` et `ProprietaireDesIntervenants(Jdbc)` ;
- la projection : `Projeter*VersLegacy` (45 lignes sur 94 diffèrent), `SuiviDesProjections*` (port, JPA, Spring Data, entité, table V36/V38), `*EnEchec`, `Reprendre*`, `PlanificationDeLaReprise*` ;
- le rattrapage : `Rattrapage*`, `PlanificationDuRattrapage*`, `Resynchronisation*` ;
- les tranches : `TrancheDesPlans` et `TrancheDesIntervenants` ;
- les métriques par tranche dans `Metriques`.

Le défaut M-G2-1/M-G2-2 (fail-closed, cache du rattrapage) existait **dans les deux copies** et a dû être corrigé deux fois. G3 doit encore ajouter 8 routes, un rattrapage avec historique, une projection complète lue par 8 lecteurs SQL legacy et le premier événement publié vers Planification. G4 à G6 suivent. Une 3e copie multiplierait le risque sur l'argent (tarif G2, coûts G4) et le coût des revues.

## Critères
C1 risque sur l'argent et fail-closed (pondération ×3) · C2 coût et risque de migration de G1/G2 · C3 respect de `check-arch`/ArchUnit (couches) · C4 lisibilité (un invariant = un seul endroit) · C5 testabilité (les invariants sont prouvés une fois et rejoués pour chaque tranche).

## Options considérées
| | 1. Descripteur de tranche + composants génériques (composition) | 2. Interfaces + classes abstraites (méthode patron) | 3. Statu quo (copie par slice) + suite de conformité |
|---|---|---|---|
| C1 | ++ invariants codés une seule fois, dans des classes `final` ; une slice déclare des données et ne peut pas contourner le 503 | + invariants dans la classe de base, mais une sous-classe peut redéfinir un crochet (`centreDeDecision`, `versLeNouveau`) et réintroduire un repli legacy | − chaque copie peut diverger ; M-G2-1/2 montre que c'est déjà arrivé |
| C2 | − 6 étapes de refactoring, sans migration Flyway ; filet existant complet | + moins de déplacements, mais il faut quand même toucher toutes les classes | ++ aucun coût |
| C3 | ++ chaque morceau du descripteur vit dans sa couche (HTTP → `interfaces`, SQL → `infrastructure`, métier → `application`), assemblé dans `configuration` | ± héritage entre couches (base en `application`, sous-classe JPA en `infrastructure`) : tentation d'y mettre du SQL | ++ inchangé |
| C4 | + une slice = 1 fichier de déclaration + son code métier | ± logique répartie entre la base et les sous-classes | − ~1 000 lignes de mécanique à relire par slice (×4) |
| C5 | ++ une suite de conformité abstraite, exécutée sur chaque descripteur | + testable via une sous-classe de test | ± suite commune possible, mais chaque correctif doit être reporté N fois |

## Décision
**Option 1.** La raison principale est le risque : le refus d'écriture (503), le routage par propriétaire et l'absence de rattrapage sous cache sont codés une seule fois, dans des composants `final` qu'une slice ne peut pas modifier. Une slice ne fournit que des **données** (routes, tables) et des **traductions** (legacy ↔ modèle).

### Invariants à ne jamais régresser (chacun vérifié par la suite de conformité, pour chaque tranche)
| # | Invariant |
|---|---|
| I1 | La façade est un **`HandlerMapping` unique** du `DispatcherServlet`, appelé uniquement au dispatch REQUEST. Elle s'exécute **après toute la chaîne de filtres** : sécurité, licence, périmètre de rôle, audit de la route publique. `/_strangler/**` n'est accessible qu'en FORWARD (`GardeForwardSeulement`). |
| I2 | Une route **par identifiant** est routée selon le **centre propriétaire** de la ressource (table du nouveau contexte, puis table legacy). Le centre de session ne sert que pour la création, les listes, et un identifiant inconnu ou illisible. Une route « par agrégat de propriétaires » (ex. plans d'un équipement) va au nouveau code seulement si **tous** les propriétaires sont basculés. |
| I3 | **Fail-closed** : si la décision lève une exception, une **écriture** reçoit 503 + `Retry-After` avec le code `ROUTAGE_INDETERMINE` et n'est **jamais** envoyée au legacy ; une lecture est servie par le legacy. La métrique `strangler_decision_impossible_total{slice,operation}` est incrémentée. Toute méthode autre que GET est une écriture : un descripteur qui déclare le contraire est refusé au démarrage. |
| I4 | Gel : une écriture vers un centre gelé reçoit 503 `BASCULE_EN_COURS` + `Retry-After`. Les lectures ne sont jamais gelées. La décision est calculée **une seule fois** par requête. |
| I5 | Le rattrapage legacy → nouveau lit la table de routage **sans cache** (`legacyEncoreEcrivain`). Il exclut les centres `ON` **et** les centres gelés. Il évalue chaque centre une fois, au début du passage **de chaque tranche**. Une erreur de lecture annule le rattrapage. Il ne tourne **jamais** pour un centre en mode `ON`. |
| I6 | `BasculerUnCentre` attend `propagation-ms` après le gel **et** entre le changement de mode et le dégel, **dans les deux sens**. `propagation-ms` ≥ `cache-ms` + durée max d'une écriture ou d'un passage de rattrapage d'une tranche. |
| I7 | La projection n'est **jamais silencieuse**. Un échec donne un état `EN_ECHEC` persistant, un backoff, une métrique et un journal ERROR, sous point de sauvegarde JDBC. Si l'enregistrement de l'échec échoue lui-même, l'exception remonte (le registre Modulith rejoue). La projection réécrit l'état complet de façon idempotente. Elle n'est jamais rejouée vers un centre `RETOUR_LEGACY` (résultat `Ignoree`, état `ABANDONNEE`). |
| I8 | **Double garde, OFF par défaut** : `hemodialyse.strangler.enabled=false` **et** une ligne `ON` sont nécessaires. Une tranche inactive rend la façade transparente : aucun forward, aucun en-tête. |
| I9 | **Aucune ligne de routage par défaut**. Aucune migration n'insère de ligne. Sans ligne, le centre reste au legacy. Pas de joker `*`. |
| I10 | Le changement de propriétaire passe par la séquence gel → propagation → drainage (retour) ou resynchronisation (aller) → réconciliation → mode → propagation → dégel. Toute divergence le refuse. Un refus laisse le propriétaire inchangé et le centre dégelé. |
| I11 | Une route non déclarée est servie par le legacy (aucune capacité nouvelle implicite). `X-Route: nouveau\|legacy` est posé sur chaque requête reconnue. |
| I12 | Les **identifiants de tranche** (`gmao-g1-plans-maintenance`, `gmao-g2-intervenants`), les **noms et étiquettes de métriques** de G1/G2, les codes et corps des 503 et les propriétés `hemodialyse.*` restent **inchangés** : ils sont stockés dans `gmao.routage` et branchés aux alertes. |

### API cible (signatures indicatives ; noms = langage du contexte)
```java
// application/routage, application/projection, application/rattrapage : Java pur (ni Spring, ni JPA, ni Servlet)
public interface TrancheMigree<A> {                 // descripteur applicatif, un par slice
    String id();                                     // "gmao-g3-cycle-vie-equipement"
    Optional<A> charger(UUID ressource);             // via le Repository du domaine
    CentreId centreDe(A agregat);
    Optional<RefletLegacy<A>> reflet();              // vide = aucune projection (G6 en lecture seule)
    ReconciliationDeTranche reconciliation();        // spécifique : comparaison champ à champ, argent en valeur
    Optional<SourceDeRattrapage> rattrapage();       // vide = aucun rattrapage
}
public interface RefletLegacy<A> { void projeter(A agregat) throws ProjectionImpossible; }   // état complet, idempotent
public interface SourceDeRattrapage { BilanDeRattrapage recopier(Predicate<CentreId> centresOuLeLegacyEcrit); }
public interface ReconciliationDeTranche { List<DivergenceDeRoutage> divergences(CentreId centre); }
public record CleDeProjection(String tranche, UUID ressource) {}
public interface SuiviDesProjections { /* echec, reussie, enEchec, aReessayer, nombreEnEchec(tranche, centre), abandonnee : clé CleDeProjection */ }
public final class ProjeterVersLegacy { <A> Resultat executer(TrancheMigree<A> t, UUID ressource, Instant changementSurvenuLe); }
public final class ReprendreLesProjections { Bilan executer(); }                 // toutes tranches ; 1 transaction par ressource
public final class RattraperDepuisLeLegacy { Bilan executer(TrancheMigree<?> t); } // construit le prédicat I5, seul appelant de recopier()
public final class ContenuDeTrancheMigree implements ContenuDeTranche { /* remplace TrancheDesPlans / TrancheDesIntervenants */ }
public interface ProprietaireDesRessources {         // port, remplace ProprietaireDesPlans / ProprietaireDesIntervenants
    Optional<CentreId> centre(String tranche, UUID ressource);
    Set<CentreId> centres(String tranche, String critere, UUID valeur);       // ex. "equipement"
}
// Metriques : lagProjection(tranche, Duration), echecProjection(tranche), projectionIgnoree(tranche) ; l'adapter Micrometer
// garde les noms G1/G2 (lag_projection, lag_projection_intervenants, gmao_projection_*), G3+ : strangler_lag_projection{slice}, ...

// interfaces/facade : HTTP uniquement
public record RoutesDeTranche(String tranche, String prefixePublic, String prefixeInterne, List<RouteRoutable> routes) {}
public record RouteRoutable(String methode, Pattern motif, Decision decision) {}   // écriture = methode != GET (I3)
public sealed interface Decision { record CentreDeSession() {}  record CentreProprietaire(int groupe) {}
                                   record TousLesProprietaires(int groupe, String critere) {} }
public final class FacadeStrangler implements HandlerMapping, Ordered { /* (List<RoutesDeTranche>, PolitiqueDeRoutage,
    SessionCourante, ProprietaireDesRessources, Metriques, retryApres) ; refuse au démarrage : préfixes qui se chevauchent,
    tranche en double, préfixe interne hors /_strangler/ */ }

// infrastructure : SQL uniquement
record TablesDuProprietaire(String tranche, List<String> tablesParPriorite, Map<String, String> colonnesDeCritere) {}
class ProprietaireDesRessourcesJdbc implements ProprietaireDesRessources {}    // infrastructure/facade
class SuiviDesProjectionsJdbc implements SuiviDesProjections {}                // infrastructure/persistence, EmplacementDuSuivi par tranche
class PlanificationDesRattrapages / PlanificationDesReprises                   // infrastructure/planification, @Scheduled, une erreur isolée par tranche

// configuration : seul endroit qui assemble les trois morceaux du descripteur
configuration/tranches/TrancheDesPlans, TrancheDesIntervenants, (G3) TrancheDuCycleDeVieDesEquipements
```
Couches : aucune règle de `.ddd/arch-rules.json` n'est assouplie. Nouvelles règles ArchUnit (étape 6) :
- seule `FacadeStrangler` implémente `HandlerMapping` dans `gmao` ;
- seuls `ProjeterVersLegacy` et `ContenuDeTrancheMigree` appellent `RefletLegacy`/`SuiviDesProjections` ;
- seul `RattraperDepuisLeLegacy` appelle `SourceDeRattrapage.recopier` (I5 impossible à contourner) ;
- aucun `@Scheduled` hors `infrastructure/planification`.

### Ce qui reste spécifique à chaque slice
Domaine, use cases, contrôleurs `/_strangler/...` et leurs DTO ; liste des routes et décisions (`RoutesDeTranche`) ; tables propriétaires ; traducteurs legacy (table-driven) ; SQL de `RefletLegacy` et de `SourceDeRattrapage` ; règles de réconciliation (tolérances, argent) ; écouteur d'événements de 10 lignes (`@ApplicationModuleListener` typé sur l'événement de la slice → `ProjeterVersLegacy`, gardé typé pour ne pas dépendre de la résolution des génériques par Spring) ; migration du schéma de la slice ; écarts de parité (ADR 0019) ; plan shadow, seuils et dates de retrait.

### Ce que cela change pour G3
- G3 déclare `TrancheDuCycleDeVieDesEquipements` et ses 8 routes, sans façade, suivi, reprise, planification ni `Tranche*` à écrire. La suite de conformité s'exécute dès le premier commit.
- Le suivi utilise une **table commune** `gmao.projection_legacy (tranche, ressource_id, …)`, de même forme que V36/V38, créée par la migration additive de G3. G1 et G2 gardent leurs tables jusqu'à leur retrait (voir Q2).
- L'historique des statuts est reflété **par état** : le reflet réécrit l'état courant et ajoute les lignes d'historique du nouveau journal absentes du legacy (clé = identifiant de ligne). L'idempotence ne dépend donc pas de l'ordre des événements (voir Q3).
- **Hors mécanique commune** :
  - l'ACL `legacyplanification` est un consommateur de `gmao.v1`, idempotent par `eventId` (ADR 0013), et non une projection ; ses publications en attente comptent dans le drainage, car `EvenementsEnAttente` compte tout le contexte ;
  - le seam ADR 0022 est un chemin d'écriture legacy. Il doit consulter `PolitiqueDeRoutage` + `ProprietaireDesRessources` (centre de l'équipement) avec la même règle fail-closed que I3 (voir Q4).

## Conséquences
+ un invariant = un seul endroit ; G3 à G6 n'ajoutent que du métier et des traductions ; n-4 (ordre de deux `HandlerMapping`) disparaît ; la revue d'une slice se concentre sur les traducteurs et la réconciliation.
− environ 6 commits de refactoring avant G3, sans gain fonctionnel ; une seule façade pour toutes les tranches : un défaut touche toutes les slices (parade : double garde, kill-switch, suite de conformité).
⚠ Surveillé par :
- la suite de conformité ;
- le test différentiel ancienne/nouvelle façade (étape 5) ;
- l'instantané des noms de métriques (I12) ;
- le rejeu PG16 à chaque étape.

## Révision
À la fin de G3 : le descripteur a-t-il suffi sans exception ? Sinon, nouvel ADR avant G4. Retrait avec la façade (ADR 0017), quand la dernière slice GMAO est retirée.

## Plan de refactoring (sans changement de comportement ; rien n'est activé ; aucune migration Flyway pour G1/G2)
Règle de chaque étape : un commit réversible, **les assertions et les goldens du filet ne sont pas modifiés** (seule la construction des objets dans les tests peut changer). L'étape n'est terminée que si tout est vert :
- module H2 ;
- plateforme : caractérisation **35 + 39**, parité **37 + 42**, chaîne de filtres réelle 3 + 3, centres mixtes 3 + 3, API d'exploitation 5 ;
- `FacadesFailClosedTest` 8, `RattrapageDeuxInstancesApresBasculeIntegrationTest` 5 ;
- `check-arch.ts` 0 violation ;
- `backend/scripts/gmao-pg16-rejeu.sh` (rejeu PG16, contrats 76/76, plateforme 170/170).

| Étape | Contenu | Filet spécifique / preuve d'équivalence |
|---|---|---|
| 0 | Référence : relever les compteurs ci-dessus. Écrire la **suite de conformité abstraite** `ConformiteDeTrancheContrat` (I1 à I12) et l'exécuter contre **le code actuel** de G1 et G2 ; elle doit être verte avant tout déplacement. Ajouter l'instantané des noms et étiquettes de métriques et des corps 503. | La suite caractérise les invariants sur l'existant. |
| 1 | Introduire `SuiviDesProjections` générique (clé `CleDeProjection`) et l'adapter `SuiviDesProjectionsJdbc` paramétré par `EmplacementDuSuivi` (table V36 / V38, colonne d'identifiant). Les anciens ports deviennent des délégations, puis sont supprimés. | `SuiviDesProjectionsContrat` + `…DIntervenantsContrat` inchangés × {in-memory, H2, PG16} ; point de sauvegarde (`Projection*LegacyPostgresIT`). |
| 2 | `TrancheMigree` + `ProjeterVersLegacy` + `ReprendreLesProjections` toutes tranches + `PlanificationDesReprises`. Les écouteurs restent par slice. `Metriques` passe à la forme paramétrée par tranche, l'adapter conserve les anciens noms. | `ProjectionVersLegacyTest`, `ProjectionDIntervenantsVersLegacyTest`, `PolitiqueDeRepriseTest` inchangés ; instantané des métriques. |
| 3 | `RattraperDepuisLeLegacy` (prédicat I5) + `PlanificationDesRattrapages` (même propriété `hemodialyse.gmao.rattrapage.enabled` ; erreur isolée par tranche ; prédicat évalué au début de chaque tranche). `RattrapageDesPlans` et `RattrapageDesIntervenants` **gardent leur nom** et deviennent des `SourceDeRattrapage`, car ils sont injectés par les tests de parité. Les deux `Resynchronisation*` disparaissent (résynchronisation = même source avec `centre::equals`). | `RattrapageDeuxInstancesApresBasculeIntegrationTest` **étendu aux plans** (aujourd'hui intervenants seulement) ; `AclPlans/IntervenantsLegacyIntegrationTest`. |
| 4 | `ContenuDeTrancheMigree` remplace `TrancheDesPlans` et `TrancheDesIntervenants`. Suppression du constructeur G1 seul de `BasculerUnCentre`. | `BasculerUnCentreTest` 17, `…DesIntervenantsTest`, `Rollback*SansRedemarrageTest`, `ExploitationDuRoutageControleurTest` : séquences d'étapes identiques. |
| 5 | `FacadeStrangler` + `RoutesDeTranche` G1/G2 + `ProprietaireDesRessourcesJdbc`. **Test différentiel** : les deux anciennes façades et la nouvelle reçoivent le même corpus (méthode × route × propriétaire connu/inconnu/illisible × mode OFF/ON/RETOUR_LEGACY × gel × exception injectée × façade éteinte), et l'on exige 0 écart de décision, de statut et d'en-tête. On supprime ensuite les anciennes façades. `EN_TETE_ROUTE` est conservé en alias, puis les imports des tests plateforme changent dans un commit isolé qui ne contient que ce changement. | `FacadeActiveeTest` 16, `FacadeIntervenantsActiveeTest`, `FacadeDesactiveeParDefautTest`, centres mixtes (module + plateforme), chaîne de filtres réelle, parité 37 + 42. |
| 6 | Nettoyage : règles ArchUnit (ci-dessus), PIT ≥ 80 % sur les composants génériques, mise à jour de `ports-adapters.md` et des fiches G1/G2 (« Réalisation »), revue indépendante (`/ddd-verify`) avant G3. | `ArchitectureHexagonaleTest`, `check-arch.ts`, rejeu PG16 complet. |

### Risques
| Risque | Parade |
|---|---|
| Régression du fail-closed ou du routage par propriétaire pendant l'étape 5 (argent : tarif G2) | test différentiel exhaustif + `FacadesFailClosedTest` inchangé + suite de conformité ; aucune activation pendant le refactoring |
| Passage JPA → JDBC du suivi (flush, transaction appelante) | contrats existants sur H2 et PG16 ; point de sauvegarde re-testé |
| Noms de métriques modifiés : alertes muettes | instantané I12 |
| Un seul planificateur sérialise les tranches, et un passage lent allonge la fenêtre de I6 | prédicat évalué au début de chaque tranche ; mesurer la durée par tranche et ajuster `propagation-ms` |
| Sur-généralisation (G5 binaire, G6 lecture seule, G4 multi-agrégats) | morceaux optionnels (`reflet`, `rattrapage`) ; exception = nouvel ADR, jamais un crochet ad hoc |
| G3 retardée par le refactoring | G3 peut avancer en parallèle sur domaine/application ; seule sa partie Strangler attend l'étape 5 |

## Questions ouvertes
1. Faut-il extraire la mécanique générique dans un module technique partagé (`contexts/strangler`) pour les futurs contextes (Planification, Séance…) ? Par défaut : non, elle reste dans `gmao`, nouvel ADR au 2e contexte (ADR 0010 : shared kernel minimal).
2. Faut-il regrouper les tables de suivi G1/G2 dans `gmao.projection_legacy` ? Par défaut : non. Cela demanderait une copie de données hors périmètre de ce refactoring ; ces tables disparaissent avec leurs projections (G2 : 2027-12-01, G1 : avec G6).
3. G3 : reflet de l'historique par état (lignes absentes ajoutées) plutôt que par événement (`eventId`, comme le dit la fiche G3) : à confirmer à la caractérisation des 8 lecteurs SQL.
4. Seam ADR 0022 : si la décision de routage est impossible dans une transaction d'intervention legacy, faut-il refuser l'intervention (fail-closed, cohérent avec I3) ou appliquer le comportement legacy ? À trancher avec l'utilisateur avant G3.
5. Après un `RETOUR_LEGACY`, une alerte Planification en attente doit-elle être livrée (c'est un effet, pas une copie de données) ou abandonnée comme une projection ?
6. Faut-il garder l'étiquette de tranche dans les nouveaux noms de métriques (`strangler_lag_projection{slice}`) et prévoir une période de double émission pour aligner G1/G2 plus tard ?
