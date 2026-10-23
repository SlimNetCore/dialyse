---
status: accepted (point 3 révisé par l'ADR 0044 le 2026-10-23)
date: 2026-10-21
deciders: utilisateur (règles décidées le 2026-10-21) ; réalise l'ADR 0009 (clôture mensuelle de facturation, tactique F16)
---

# ADR 0037 — Clôture mensuelle de facturation

## Contexte

L'ADR 0009 retenait le principe d'une clôture mensuelle de la facturation et laissait ouverts le grain, les préconditions, la course avec
une validation en cours et la réouverture (tactique F16, questions Q2). L'utilisateur a tranché le 2026-10-21 (voir « Décision »). La
Comptabilité a déjà sa propre clôture (`PeriodeComptable`, RG-CPT-005, irréversible) : la clôture de facturation est indépendante mais
doit rester cohérente avec elle.

## Décision

1. **Grain** : un mois (année-mois) par centre. Agrégat `MoisDeFacturation` (identité centre + mois), nommé ainsi car
   `PeriodeDeFacturation` désigne déjà l'intervalle de dates d'une simulation (`domain.simulation`). Un mois sans ligne est ouvert ;
   aucune ligne n'est créée par la migration.
2. **Effet d'un mois clos** (422 `PERIODE_DE_FACTURATION_CLOTUREE`, message avec le mois) : la validation (`POST /validate`) dont la
   période touche au moins un mois clos du centre (tout mois couvert par l'intervalle, même d'un seul jour) ; l'exclusion, la
   réinclusion et le changement de forfait d'une séance dont la **date de séance** est dans un mois clos (la période demandée est
   sans effet). Restent permis : simulation (`POST /preview`), toutes les lectures, les règlements (F4).
3. **Préconditions de clôture** : aucune, l'administrateur décide. *(Révisé le 2026-10-23 par l'ADR 0044 : aucune
   précondition d' **état**,
   mais seul un mois terminé se clôture, au fuseau du centre — 422 `PERIODE_NON_TERMINEE`, comme les absences.)* La
   réponse porte l'avertissement chiffré `seancesEligiblesRestantes`
   (séances `VALIDEE`/`SIGNEE`, non facturées, non exclues du mois). Clôturer deux fois : **409 `PERIODE_DEJA_CLOTUREE`** (comme la
   clôture comptable ; l'alternative 200 inchangé a été écartée : elle cache à l'appelant qu'un autre administrateur a déjà clos).
4. **Réouverture** : `ADMIN`, motif obligatoire (400 `MOTIF_DE_REOUVERTURE_OBLIGATOIRE`, borné à 500 caractères), auteur = utilisateur de
   la session. Refusée 422 `PERIODE_COMPTABLE_CLOTUREE` si la période comptable du même mois et du même centre est clôturée (lue par
   le port publié `PeriodesComptables.estCloturee(centre, mois)` de la Comptabilité : la Facturation dépend de la Comptabilité, jamais
   l'inverse, ADR 0035). Réouvrir un mois non clos : 409 `PERIODE_NON_CLOTUREE` (contrôlé avant la comptabilité). Un mois rouvert peut
   être clos à nouveau.
5. **Journal append-only** `periodes_facturation_journal` : chaque clôture (auteur, instant, séances éligibles restantes) et chaque
   réouverture (auteur, instant, motif). La ligne du mois ne garde que la dernière clôture et la dernière réouverture.
6. **Concurrence : verrou pessimiste de la ligne du mois**. `MoisDeFacturationRepository.verrouiller` crée la ligne ouverte si elle manque
   (`ON CONFLICT DO NOTHING` sur PostgreSQL, `WHERE NOT EXISTS` sur H2) puis la verrouille (`SELECT ... FOR UPDATE`) jusqu'à la fin de la
   transaction, comme la séquence de facturation (`UPDATE ... seq_value + 1`) et le quota des pièces jointes. La clôture, la réouverture,
   la validation et les trois modifications de la simulation verrouillent le mois **dans leur transaction**, les mois d'un intervalle du
   plus ancien au plus récent (pas d'interblocage). Conséquences : une validation en cours fait **attendre** la clôture, qui compte alors
   des séances restantes exactes ; une clôture en cours fait attendre la validation, qui est ensuite refusée. Prouvé par
   `MoisDeFacturationConcurrentContrat` (H2 et PostgreSQL 16 ; les deux tests d'ordre échouent sans `FOR UPDATE`). Une validation ou une
   modification crée donc, au premier passage, la ligne ouverte du mois : c'est sans effet métier.
7. **Événements** (langage publié, Outbox ADR 0013) : `PeriodeDeFacturationCloturee(centre, mois, cloturePar, cloturee,
   seancesEligiblesRestantes)` et `PeriodeDeFacturationRouverte(centre, mois, rouvertPar, rouverte, motif)`, publiés dans la transaction
   de l'acte (jamais s'il est annulé). Aucun consommateur pour l'instant (Absences, pilotage Direction ensuite) : le registre
   Spring Modulith n'enregistre une publication que par écouteur transactionnel, donc **sans écouteur il n'accumule aucune
   publication incomplète** ; aucun écouteur de journalisation n'est ajouté (le journal SQL fait foi). Un futur consommateur s'inscrit
   avec `@ApplicationModuleListener` et sera idempotent par (centre, mois, instant).
8. **Persistance** : migration additive `V42` (deux tables, `IF NOT EXISTS`, aucune ligne, portable H2/PostgreSQL) et DDL H2 dans
   `db/schema.sql`. Le journal est append-only par construction du code (aucun UPDATE ni DELETE) ; pas de déclencheur SQL (non portable).

## Conséquences

- Les gardes sont portées par le service d'application `GardeDesMoisClos` (cohérence transactionnelle), pas par la simulation :
  aucun comportement existant ne change tant qu'aucun mois n'est clos (les scénarios de caractérisation F1 à F6 sont inchangés).
- Un mois clos n'interdit pas les règlements, ni la comptabilisation des factures déjà émises.
- Une séance éligible oubliée dans un mois clos ne se facture qu'après réouverture (motif tracé) : l'avertissement chiffré sert à l'éviter.
- Le frontend doit afficher l'état des mois, proposer Clôturer/Rouvrir (ADMIN) et gérer les nouveaux codes (voir la fiche de la slice).
- Retour arrière : désactiver n'est pas prévu (pas de flag, ADR 0024) ; en cas de besoin, rouvrir les mois ou `UPDATE` manuel validé par
  écrit ; retirer la fonctionnalité = commit précédent (les tables V42 restent, sans effet).
