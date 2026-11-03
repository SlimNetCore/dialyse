---
slice: patient-couverture-pc1-identite-mouvements
status: built
date: 2026-11-03
order: 1
context: patient-couverture
sources: [.ddd/tactical/patient-couverture.md, ADR 0004, 0024, 0064]
---

# PC1 — Identité administrative et mouvements du patient

> Statut vrai au 2026-11-02 : `planned` (module gelé, `backend/contexts/patient-couverture/GEL.md`, ADR 0073). Aucun filet PC1 propre (identité, mouvements non caractérisés). Reprise sur place : filet legacy d'abord, mêmes tables et URL, états du LEGACY (`PERMANENT`, `TRANSFERE`, `GUERRI`, `DECEDE`, `GREFFE`…), `date_evenement_etat`.
> Règle R2 « un état de sortie exige une date » : **déjà couverte par le legacy** (`FinOccupation.verifierDate`, `PATIENT_DATE_EVENEMENT_REQUISE`, RG-PAT-031, golden Planification P7 « sortie sans date refusée ») ; à reproduire telle quelle, non à ajouter.
> Lectures SQL hors contexte (relevé du 2026-11-02, à transformer en liste blanche datée au filet PC1) : **25 fichiers** de `src/main` hors `contexts/patient-couverture` lisent ou écrivent `patients`, `mouvement_patient`, `assure`, `assure_patient`, `prise_en_charge`, `attestation_droit` (grep `(FROM|JOIN|INTO|UPDATE) <table>` ; dont `PecValidationJdbc` du module seance, `SituationsDesFacturesJdbc`, `DirectionDashboardQueryService`, `PatientSummaryQueryService`, les ACL de Planification, `SeanceRestController`).

Agrégat `Patient` (root) : admission, transfert, sortie, décès, greffe, guérison. Invariant R2 (date de sortie requise),
R1 (un seul assuré principal).

## Cas d'usage

`AdmettrePatient`, `TransfererPatient`, `SortirPatient`, `DeclarerDeces`, `DeclarerGreffe`, `DeclarerGuerison`,
`ConsulterPatient`, `ListerPatients`.

## Entrées legacy

`bc-patient` (Patient, mouvements), `PatientApplicationService`, `MouvementPatientJdbcAdapter`.

## Prérequis

Bloquer la sortie sans date d'événement (décision utilisateur) ; lister cas existants (verifications-production §25.2).

## Données

Tables patient/mouvements ; aucune migration. centerId obligatoire.

## Avancement (reprise sur place, ADR 0024)

| Session | État | Contenu |
|---|---|---|
| S1 (b0144e36) | faite | domaine (`Patient`, `Fiche`, `EtatDuPatient` au vocabulaire du legacy), use cases, ports, doubles, 69 tests |
| S3 (2026-11-03) | faite | parité (écarts décidés archivés, goldens `_nouveau_code`), legacy mort supprimé, seams fermés, PIT du domaine et de l'application (258 mutants, 86 % tués, 98 % des lignes), contrats H2 + PG16 verts, build complet vert, ADR 0076 (proposed), RG-PAT-038 à 041, KB-PAT-19, glossaire (proposed), documentation client régénérée |
| S2 (2026-11-03) | faite | adapters JDBC sur les vraies colonnes (`date_evenement_etat`), lectures SQL (recherche, résumé, détail, historique d'assurés, mouvements) au même SQL que le legacy, contrôleurs et advice sur les MÊMES URLs, ACL de place (Planification P5/P7), ACL de couverture (attestation, PEC, assuré), 3 routes d'assuré avec garde de centre, jour du centre, contrats double/H2/PG16 (`backend/scripts/patient-couverture-pc1-pg16-rejeu.sh`), module remis au réacteur, contrôleurs legacy désactivés par défaut (seam ouvert) |

### Parité finale (S3) : 56 scénarios PC1 verts ; 24 scénarios en écart décidé (47 goldens `_nouveau_code`, 47 `.legacy.json` archivés octet pour octet depuis 321275cb dans `pc1/_ecarts-legacy/`), 1 PC2 (`list_assures_cross_centre_adminB`) et 1 P5 (`requetes_invalides_nouveau_code`, archive `avant_pc1.legacy.json`) ; les anciens `*.approved.json` des goldens renommés ont été supprimés après archivage (le dossier `received` ignoré par git reste local).

### Écarts de parité constatés en S2 sur le filet PC1 (56 scénarios : 32 identiques, 24 en écart, tous décidés ; hors PC1 : PC2 assuré 1/5 (garde de centre), P5 placement 1/23 (400/404 au lieu de 500/400), 2 tests du legacy retiré à adapter en S3 : `PatientFicheEcritureIntegrationTest`, `PatientGenerateurIntegrationTest`, sessions sans centre ; build `platform` : 2555 tests, 28 en écart, 0 hors liste)
Décisions du 02/11 (400, 404, 403 de garde, rôles, états fermés, date de séjour limité, naissance, unicité normalisée, ligne héritée, size ≤ 100) et KB-PAT-05 (corriger la seule date d'une sortie ne crée pas de mouvement). Écarts de détail : DÉCISION PRISE le 2026-11-03 (déléguée par l'utilisateur) : le `PUT` écrit `numero_assurance` normalisé (nécessaire à l'unicité à la modification) ; message neutre pour un type de mouvement inconnu en base ; `etatPatient` nul rendu `PERMANENT` (le `PUT` d'une ligne à état nul ne crée pas le mouvement `REPRISE` que le legacy écrivait) ; `qualiteAssure` nul reste nul (comme le legacy). Revue du 2026-11-03 corrigée : droits de rôles du legacy restaurés (M1), verrou du centre systématique et test de concurrence H2/PG16 (M3), état inconnu conservé brut et non plaçable (M4), scénario « vacancier avec attestation » rétabli par un scénario neuf (m3), plus d'hypothèse de fuseau dans le filet (m4), `covered.json` nettoyé (m5), motif du `@Disabled` à jour (m6), taille de page du mode local du front à 100 (m8). Reproduit à l'identique : `piecesJointesJson` toujours nul à la lecture (KB-PAT-19 proposé), téléphones de `assureInfo` tels que saisis, en-têtes de cache de la fiche, 400 « Attestation obligatoire », code `BUSINESS_RULE_VIOLATION` du numéro déjà utilisé.
