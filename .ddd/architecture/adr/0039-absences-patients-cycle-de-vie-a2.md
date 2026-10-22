---
status: proposed
date: 2026-10-22
deciders: utilisateur (décisions du 2026-10-21 et du 2026-10-22) ; réalise le modèle tactique `.ddd/tactical/absences-patients.md` (slice A2)
---

# ADR 0039 — Absences de patients : cycle de vie (A2), écarts corrigés, verrous et unicité du rattrapage

## Contexte

A1 (ADR 0038) a déplacé la consultation. A2 déplace les écritures : déclarer, qualifier, rattraper, annuler, et les deux effets de
l'exclusion d'une séance par la Facturation (déclarer, retirer). Le filet (84 tests, 101 goldens, `.ddd/safety-net/absences-patients-a2-cycle-de-vie.md`)
a révélé douze comportements suspects (KB-ABS-10 à 21). L'argent est figé à la création (A9) : toute modification de sa règle est interdite ici.

## Décision

1. **Agrégat `AbsencePatient`** (une entité) dans `contexts/absences-patients`, avec VO `Valorisation`, `Commentaire`, `DateOuverte`,
   `DroitDeCorrection`, événements de domaine internes (non publiés, aucun consommateur). Mêmes table, URLs et JSON ; le legacy de
   déclaration, qualification, rattrapage, annulation est retiré (détection et réconciliation restent au legacy, slice A3).
2. **Une transaction par écriture** (`UniteDeTravail`, rejointe si la Facturation en a déjà ouvert une) et **verrou de ligne**
   (`SELECT … FOR UPDATE`). Le rattrapage verrouille toutes les absences du patient, dans l'ordre de l'identifiant (pas d'interblocage).
   Une écriture concurrente attend puis voit l'état validé ; un verrou impossible ou une déclaration qui perd la contrainte
   `UNIQUE (center_id, patient_id, date_seance)` donne **409** `ABSENCE_ECRITURE_CONCURRENTE`. Une déclaration qui lit l'absence créée
   reste **422** `ABSENCE_EXISTANTE`, annulée comprise.
3. **Unicité du rattrapage** : index unique partiel PostgreSQL `uq_absence_patient_rattrapage ON (center_id, patient_id, date_rattrapage)
   WHERE statut = 'RATTRAPEE'` (migration additive **V43**, vérification SELECT-only préalable : `verifications-production.md` § 24). H2 n'a
   pas d'index partiel : l'invariant y est tenu par le verrou du patient, la contrainte est prouvée sur PostgreSQL 16 jetable. Une
   absence annulée qui garde sa date (données anciennes) ne réserve rien.
4. **Écarts de comportement** (goldens `*_nouveau_code`, 34) : rattrapée non annulable (`ABSENCE_NON_MODIFIABLE`, date conservée,
   KB-10) ; rattrapage réservé au droit de correction pour toute absence (KB-13) ; commentaire sans motif conservé (KB-12) ;
   entrées illisibles 400, corps non JSON 415, commentaire > 1000 : 400 (KB-17) ; ordre existence, droit, état, période facturée,
   valeurs, et absence inconnue **404** comme en A1 (KB-18) ; `tauxTva` rendu `0.00` (KB-15) ; l'exclusion diffuse la saisie
   temps réel après validation (KB-19) ; l'absence d'une exclusion ne s'annule pas, seule la réinclusion la retire (KB-20).
5. **Reproduit** : choix du forfait (effectif prioritaire même hors période, repli sur la plus récente portant un forfait, KB-16),
   taux exonéré lu 0 % (KB-CPT-19), écrasement du commentaire (KB-14), valeur figée (aucune commande ne l'écrit).
6. **Frontières** : le module lit les langages publiés `ParametrageFiscal` (Comptabilité) et `FacturationDuPatient` (Facturation) et
   **fournit** `DeclarationDesAbsences` (Facturation) par un adapter entrant du module (`interfaces/facturation/ExclusionDeLaFacturation`) :
   l'ACL `AbsencesDesPatientsLegacy` de `platform` est **supprimée** (re-pointée = déplacée dans le contexte). Le signalement temps
   réel passe par le langage publié `SignalementDesSaisies`, fourni par `SignalementDesSaisiesLegacy` (platform, ACL vers les notifications,
   après validation de la transaction).
7. **Fuseau** : reporté (décision 7 du 21/10), jour d'UTC conservé.

## Conséquences

- **KB-20 résolu (revue du 22/10, décision de l'utilisateur)** : l'absence créée par l'exclusion d'une séance **ne s'annule plus** (422
  `ABSENCE_D_EXCLUSION`) ; son origine est lue sur `seances.exclusion_absence_id` (port `ExclusionsDeSeances`, sans changer la source ni le
  schéma, donc valable aussi pour les lignes existantes). **Seule la réinclusion la retire**, quel que soit son état (plus de critère
  « travaillée » fondé sur des horodatages). Les absences d'exclusion **déjà annulées** avant A2 ne sont jamais supprimées par l'application :
  la réinclusion est refusée (`ABSENCE_D_EXCLUSION_MODIFIEE`), la liste est au § 24.4 de `verifications-production.md` et leur sortie exige une
  validation écrite. Requalifier ou rattraper une absence d'exclusion reste permis ; la réinclusion la supprime ensuite.
- **Seam accordé sur le legacy** (`.ddd/safety-net/seams.md`) : l'UPDATE de la réconciliation de nuit (conservée pour A3) porte
  `AND statut = 'A_QUALIFIER'` (`saveIfAQualifier`) : une absence qualifiée ou rattrapée par le nouveau code entre la lecture du lot et son
  écriture n'est jamais ré-annulée. Prouvé par `AbsencePatientIntegrationTest` ; disparaît avec A3.
- **Délai de verrou** : `SET LOCAL lock_timeout` de 5 s sur PostgreSQL (`UniteDeTravailSpring`) : une écriture qui attend trop reçoit 409 au lieu
  d'attendre sans limite (prouvé sur PostgreSQL 16). V43 : si 24.1 n'est pas à 0, la migration échoue et l'application ne démarre pas.
- **Commentaire** (KB-12 et 14 alignés) : conservé à la déclaration sans motif, et une qualification sans commentaire le garde ; une annulation
  ou une requalification le remplace. Aucun effet sur un montant ou un statut.
- `SignalementDesSaisies` est un port **requis** par le contexte, rangé dans `publishedlanguage` pour que la plateforme puisse le fournir sans
  toucher aux couches internes (règle de frontière) ; assumé.
- La duplication du choix du forfait (legacy `ForfaitAbsenceJdbcAdapter`, utilisé par la détection) disparaît avec A3.
- Le front masque le bouton « rattraper » sans droit de correction, connaît les codes 409 et `ABSENCE_D_EXCLUSION` (fr/en/ar/kab, écrans Absences et Facturation).

## Alternatives écartées

- Verrou optimiste par colonne `version` : migration plus lourde sans bénéfice, le verrou de ligne suffit (volume faible).
- Contrainte unique sur toutes les lignes ayant une date de rattrapage : échouerait sur les annulées qui l'ont gardée.
