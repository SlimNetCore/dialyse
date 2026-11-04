---
status: accepted
date: 2026-11-04
decision-makers: [utilisateur (décision déléguée à l'assistant, 2026-11-04), architecte]
context-scope: patient-couverture
related: ADR 0024 (refactoring sur place), 0061 (ordre global des verrous), 0076 (PC1), 0077 (PC2 et PC3) ; `.ddd/migration/slices/patient-couverture-pc4-attestations.md` ; `.ddd/safety-net/patient-couverture-pc4-attestations.md` ; `.ddd/safety-net/known-bugs.md` KB-PAT-38 à 48
---

# 0078 - Patient et couverture, PC4 : reprise sur place des attestations de droits

## Contexte
Suite des ADR 0076 et 0077. Le filet PC4 (28 scénarios, commité vert sur le legacy en 945b872c) a verrouillé les quatre routes d'attestation de l'ancien `PecRestController` (`/api/v1/pec/attestations**`), `AttestationDomainService`, `AttestationRepositoryAdapter` (cache), `PecReadQueryService` (recherche) et leurs lecteurs. Mêmes tables (`attestation_droit`), mêmes URL et mêmes variables de chemin (`patientId`, `attestationId` : le journal d'audit en dépend), vocabulaire du legacy.

## Décision
1. **Reprise** : `AttestationsControleur`, `GererLesAttestations`, `RechercherLesAttestations`, `AttestationDroit` et le port `Attestations` vivent dans `contexts/patient-couverture`. Les ACL temporaires de PC1 et PC3 (`CouvertureDeLaFicheJdbc`, `AttestationsDuPatientJdbc`) sont remplacées par `AttestationsJdbc` ; `CouvertureDeLaFiche` devient un cas d'usage (attestation de la fiche jamais dupliquée si la même période existe). `AttestationDroit` est une entité simple (règles : dates obligatoires, fin non antérieure au début), pas un agrégat riche.
2. **Droits de rôles du legacy inchangés** : aucune restriction par route, tout compte authentifié de centre est admis ; DIRECTION et SUPERADMIN restent refusés plus haut (403 `ROLE_SCOPE`).
3. **Garde de centre** sur les quatre routes (centre du jeton, 403 ; une session sans centre est refusée) : le legacy croyait le `centerId` de la requête (KB-PAT-38).
4. **Rattachement** : une attestation pour un patient inexistant ou d'un autre centre est refusée en 422 `ATTESTATION_PATIENT_INTROUVABLE` (KB-PAT-39).
5. **Suppression** : un identifiant inconnu, d'un autre centre ou (si le patient est donné) d'un autre patient donne 404 `ATTESTATION_INTROUVABLE` SANS diffusion ; la diffusion `ATTESTATION_DELETED` porte le patient réel (KB-PAT-40). Création et suppression prennent le verrou du centre en tête (ordre global centre, fiches, app_user, ADR 0061) : de deux suppressions simultanées, une réussit et l'autre reçoit 404.
6. **Formats illisibles ou manquants : 400** (corps, UUID, date, `centerId`, `patientId`), type de contenu texte 415 ; route ou verbe inconnu : handler global inchangé (KB-PAT-42).
7. **Audit** : actions dédiées `ATTESTATION_CREATION` (identifiant de l'attestation tracé), `ATTESTATION_SUPPRESSION` et `ATTESTATION_RECHERCHE` (KB-PAT-43).
8. **Recherche** : `%`, `_` et `\` des filtres texte sont des caractères ordinaires (échappés) ; une date de filtre illisible est un 400 (au lieu d'être ignorée) ; tri, formats de date, intervalle redressé et jointure du patient inchangés (KB-PAT-45).
9. **Jour du centre** : le tableau de bord (échéance des attestations et des PEC) et la création ou le changement de date d'une séance sans date utilisent le jour du centre (`JourDuCentre`, `centers.fuseau_horaire`), plus celui de la JVM (KB-PAT-47). `SeanceBillingEligibilityPort` et son adapter sont inchangés.
10. **Code mort retiré** (KB-PAT-48) : le squelette d'attestation du module (route servie en 500), `existsValidAt` et son cache, le module `bc-insurance`, `AttestationRepositoryAdapter`, l'entité et le dépôt JPA, `AttestationSpecifications`, les DTO, `PecReadQueryService`, le bean `AttestationDomainService`. La table H2 `attestation_droit` est définie par `db/schema.sql` (plus par l'entité). **Jalon : `bc-assure` reste** (l'import de migration `AssureMigrator`, `AffectationMigrator`, `PatientMigrator` et ses adapters JPA le référencent) ; l'import de migration écrit aussi `attestation_droit` (`HistoricalRecordJdbcAdapter`) hors verrou du centre : même règle d'exploitation qu'à l'ADR 0077 point 12, import hors activité du centre.
11. **ARGENT / MÉTIER reproduit à l'identique : à confirmer avec les métiers** (KB-PAT-41, 46, 47) : début = fin accepté ; période échue, future ou antérieure à l'admission acceptée ; doublons, chevauchements, inclusions et contiguïtés acceptés (la même période peut exister plusieurs fois) ; l'attestation reste exigée pour FACTURER un vacancier (`SeanceBillingEligibilityPort`, règle de production de la séance) alors que la saisie en dispense le vacancier ; suppression possible d'une attestation sous une PEC validée (séance non facturable, `session-allowed` faux) ; le tableau de bord compte aussi les attestations DÉJÀ échues dans « à échéance » et toutes les lignes dans `attestationTotal` ; la règle R5 (« attestation liée à une PEC active »), jamais implémentée, n'est pas retenue.

## Alternatives écartées
- Contrainte SQL ou refus des chevauchements : écarté avant la décision métier (argent) et avant nettoyage des données de production.
- Passer l'import de migration par le verrou du centre : édition non minimale (voir point 10), dette consignée.

## Conséquences
- 21 goldens changent d'issue, tous par décision ; l'ancien comportement est archivé en `pc4/_ecarts-legacy/**/*.legacy.json`, les nouveaux goldens portent le suffixe `_nouveau_code`.
- Dette : `bc-assure` et l'import de migration (point 10), `JourDuCentre` lit `centers` en SQL (liste blanche de `TableDeLOrganisationTest`, à remplacer par organisation.v1), lecteurs SQL de `attestation_droit` hors module (`SeanceBillingEligibilityJdbcAdapter`, tableau de bord, impression, migration historique) à remplacer par le langage publié à PC5.
- À confirmer avec les métiers (décidé, déléguée à l'assistant) : les points d'argent du point 11 et le refus du rattachement (point 4).
