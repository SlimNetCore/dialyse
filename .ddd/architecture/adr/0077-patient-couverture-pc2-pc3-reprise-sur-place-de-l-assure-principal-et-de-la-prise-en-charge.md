---
status: accepted
date: 2026-11-04
decision-makers: [utilisateur (décision déléguée à l'assistant, acceptée le 2026-11-04 après la revue PC2/PC3), architecte]
context-scope: patient-couverture
related: ADR 0024 (refactoring sur place), 0052, 0061 (ordre global des verrous), 0073, 0076 (PC1) ; `.ddd/migration/slices/patient-couverture-pc2-assure-principal.md` et `...-pc3-pec.md` ; `.ddd/safety-net/known-bugs.md` KB-PAT-20 à 36
---

# 0077 - Patient et couverture, PC2 et PC3 : reprise sur place de l'assuré principal et de la prise en charge

## Contexte
Suite de l'ADR 0076. Le filet PC2/PC3 (52 scénarios, 147 goldens, commité vert sur le legacy en 853fd67b) a verrouillé `AssureService`, `PecDomainService`, `PecRestController` et les lecteurs de PEC. Mêmes tables (`assure`, `assure_patient`, `prise_en_charge`), mêmes URL (`/api/v1/patients/assures/**`, `/api/v1/pec/**` hors attestations), vocabulaire du legacy.

## Décision
1. **Assuré principal (PC2)** : `AssurePrincipal` n'est PAS un agrégat mais un ensemble de **règles sans état** du domaine (méthodes statiques) ; R1 est tenue par l'orchestration sous le verrou du centre et par le SQL des adapters (`fermer…`, `ajouter…`, `retirer…`, `lierSansDates`) ; un vrai agrégat `AffectationsDuPatient` est la cible (dette). **R1 (un seul assuré principal actif par patient)** garantie sous le **verrou du centre pris en tête** de `affecter` et de la correction de dates (ordre global centre, fiches, app_user, ADR 0061) ; pas de contrainte SQL (les doublons existants se relèvent par `verifications-production.md` §42). Test de concurrence réel (H2, PostgreSQL 16) : deux affectations simultanées, une seule principale.
2. **Assuré d'un autre centre : capture REFUSÉE, y compris en course** : déclarer un numéro d'assuré détenu par un autre centre est refusé 422 `ASSURE_D_UN_AUTRE_CENTRE` (le legacy basculait `center_id` et écrasait les coordonnées, KB-PAT-31). Les deux centres ayant des verrous différents, le contrôle ne suffit pas : `AssuresJdbc.declarer` écrit `UPDATE … WHERE numero_assurance = ? AND center_id = ?`, puis `INSERT … WHERE NOT EXISTS` ; une violation de clé primaire (l'autre centre vient de créer le numéro) ou une insertion vide lève le même 422 et annule la fiche. Prouvé par un test de course A/B (deux centres, même numéro) sur H2 et PostgreSQL 16 (revue du 2026-11-04, majeur 1).
3. **Idempotence** : enregistrer la fiche d'un ayant droit sans changement d'assuré n'ajoute plus de ligne `assure_patient` (KB-PAT-32).
4. **`PUT` assuré** : exige le **nom** (400), le prénom reste facultatif ; le remplacement total reste (les autres champs absents sont effacés) (décision (h), KB-PAT-33).
5. **Prise en charge (PC3)** : agrégat `PriseEnCharge` (`CREE`, `VALIDEE`, `CLOTUREE`, transitions invalides 422 `BUSINESS_RULE_VIOLATION`), `PecControleur` sur les MÊMES URL et variables de chemin (`pecId`, `patientId`). **Droits du legacy inchangés** : validation `ADMIN` seul ; autres routes ouvertes à tout utilisateur authentifié.
6. **Garde de centre sur toutes les routes de PEC** (centre du jeton, 403 ; session sans centre refusée) : le legacy croyait le `centerId` du corps (KB-PAT-22). PEC inconnue ou d'un autre centre : 404 `PEC_INTROUVABLE` (au lieu de 400 « cache Spring », KB-PAT-21). Formats illisibles ou manquants : 400 ; type de contenu texte : 415 (au lieu de 500, KB-PAT-23). Statut stocké inconnu : 400 « Statut de prise en charge inconnu : X ».
7. **Demande de PEC saisie sur la fiche** : un `pecId` déjà pris par un autre patient n'annule plus la fiche : la fiche est enregistrée, la PEC n'est pas créée, réponse **409 `PEC_IDENTIFIANT_DEJA_UTILISE`** avec l'identité de la fiche (au lieu de 500 qui annulait tout, KB-PAT-35). Interprétation de « sans annuler la fiche » : à valider.
8. **Diffusions et audit** : `patientNom` réel dans `PEC_VALIDATED` et `PEC_CLOSED` (le legacy diffusait le littéral « Patient », KB-PAT-27) ; audit `PEC_VALIDATION` et `PEC_CLOTURE`, `entity_id` renseigné à la création (KB-PAT-28).
9. **« Aujourd'hui » = le jour du centre** pour l'attestation du non-vacancier (KB-PAT-20, fuseau JVM abandonné).
10. **ARGENT reproduit à l'identique, À DÉCIDER AVEC LES MÉTIERS** : deux règles « PEC valide pour une séance » (`session-allowed` : statut seul plus attestation du non-vacancier ; création de séance : `SeanceBillingEligibilityPort`, qui reste la règle de production) ; forfait lu par Absences sans regarder le statut de la PEC (KB-PAT-26) ; aucun contrôle de période, de forfait ni de chevauchement, plusieurs PEC validées simultanées (KB-PAT-24) ; suppression d'une PEC validée.
11. **Retrait du legacy** : `PecDomainService`, `PecUseCase`, `PecSpecifications`, DTO de PEC, moitié PEC de `PecReadQueryService`, bean `pecDomainService`, routes de PEC de `PecRestController` (liste dans `seams.md`). Les routes d'attestation restent au legacy (PC4).

12. **Écrivains de `assure` / `assure_patient` HORS du module : RISQUE ACCEPTÉ ET DOCUMENTÉ** (revue du 2026-11-04, majeur 2). L'import de migration (`AssureMigrator`, `AffectationMigrator`, `bc-assure` JPA) écrit ces tables SANS le verrou du centre : pendant un import concurrent d'une saisie, R1 (un seul principal actif) peut être violée et un assuré peut être écrasé. Modifier les migrateurs (constructeur, transaction, tests) n'est pas minimal ; **règle d'exploitation : lancer l'import de migration hors activité du centre** (aucune saisie de fiche ni d'assuré pendant l'import), puis rejouer `verifications-production.md` §42-1 et §42-2. Dette : faire passer l'import par `VerrouDuCentre` (PC5).
13. **Diffusions** : `patientNom` réel (prénom et nom) est diffusé à tout le centre (décision (f) conservée) ; une fiche héritée sans nom ni prénom diffuse la valeur de repli « Patient » (jamais nulle).
14. **Métier à confirmer** : les décisions 7 (409 fiche enregistrée) et 10 (argent reproduit à l'identique) sont **décidées** (déléguées par l'utilisateur à l'assistant) et **à confirmer avec les métiers** ; elles ne sont pas revalidées par eux à ce jour.
15. **Règles de PEC dans le domaine** : la règle « session-allowed » est `PriseEnCharge.autoriseSeance(vacancier, attestationValide)` ; le contrôle des dates d'une affectation est `AssurePrincipal.verifierLesDatesDeLAffectation`.
16. **Code mort retiré** (revue du 2026-11-04) : le module `bc-pec` entier (`PriseEnCharge`, `PecStatus`, `PecRepositoryPort`), `PecRepositoryAdapter`, `PecJpaEntity`, `PecJpaRepository` ; la table `prise_en_charge` est définie en H2 par `db/schema.sql` (plus par l'entité). La section « Dette » ci-dessous qui les citait est caduque.

## Alternatives écartées
- Index unique SQL pour R1 : écarté avant nettoyage des doublons de production.
- Reprise de la ligne d'un assuré d'un autre centre : risque de fuite entre centres.

## Conséquences
- Écarts archivés en `pc2/_ecarts-legacy` et `pc3/_ecarts-legacy` (`*.legacy.json`) ; nouveaux goldens `_nouveau_code`.
- Dette : `PecValidationJdbc` du module gelé de Séance non touché (le code PEC mort de `bc-pec` est retiré, point 16) ; import de migration hors verrou (point 12) ; sources et `excludes` gelés d'Attestation (PC4) ; ACL d'attestation temporaire.
- À confirmer avec les métiers (décidé, voir point 14) : les 4 points de l'argent ci-dessus, le 409 du `pecId`, le refus de capture d'un assuré.
