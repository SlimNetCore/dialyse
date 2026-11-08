---
status: accepted
date: 2026-11-08
decision-makers: [utilisateur (décisions déléguées à l'assistant via le coordinateur, 2026-11-08), architecte]
context-scope: seance
related: ADR 0002, 0024 (refactoring sur place), 0045 (sortie pour séances), 0062, 0088 (SE1, même démarche) ; `.ddd/safety-net/seance-se2-validation-signature.md` (net 95ab8291), `.ddd/migration/slices/seance-se2-validation-signature.md`, KB-SEA-11 à 21
---

# 0089 - Séance, SE2 : reprise sur place de la validation, de la signature médicale et du déverrouillage pour régularisation

## RISQUES À FAIRE CONFIRMER AVANT LA MISE EN PRODUCTION (décisions REPRODUITES, conditions de mise en production)
1. **KB-SEA-11 : le signataire est lu dans le corps de la requête, jamais dans la session.** Un compte peut valider ou signer « au nom » d'un autre, y compris un médecin signer au nom d'un autre médecin. **Risque d'imputation d'une signature médicale (priorité juridique et DPO).** Décision : REPRODUIT (aucun changement de contrat sans décision). Condition de mise en production : décision écrite du juridique et du DPO parmi (a) auteur = compte de la session, `userId` du corps ignoré, (b) accepter par écrit, (c) rattacher l'usurpation à un journal d'audit dédié. Le module borne seulement le signataire à 100 caractères (400 au lieu du 500 du legacy) et garde `system` quand il est absent.
2. **KB-SEA-12 : une séance à venir (J+1, J+30) se valide sans limite**, seules les séances passées sont verrouillées (422 `SEANCE_REGULARISATION_NON_DEVERROUILLEE`, l'infirmier seul ; l'administrateur valide toujours). REPRODUIT. Condition : les métiers confirment qu'une validation anticipée est admise.
3. **KB-SEA-13 : la fenêtre de 7 jours de RG-SEA-046 ne borne pas le déverrouillage** : une séance « créée » d'il y a 400 jours se déverrouille. REPRODUIT. Condition : les métiers confirment l'absence de borne (la fenêtre ne concerne que le rappel quotidien).
4. **KB-SEA-15 : les consommables relevés sont CUMULÉS à chaque validation répétée** d'une même séance (3 puis 2 donne 5), statut inchangé, avec risque d'inflation des imputations de stock. REPRODUIT. Condition : décision entre rejeter les lignes sur une séance déjà validée ou fixer au lieu d'ajouter. Le stock des lots n'est jamais décrémenté à la validation (imputation par le Stock sur la sortie pour séances du jour, ADR 0045) : constaté, inchangé.

## À confirmer avec les métiers et le DPO
Toutes les décisions ci-dessous sont prises par délégation, EN VIGUEUR, à confirmer (données de santé, signature, audit). Détail : `known-bugs.md`, section « Décisions SE2 ».

## Contexte
SE2 est caractérisée (net 95ab8291, 33 scénarios, 33 goldens) : `POST /api/v1/seances/{id}/valider` (ADMIN, INFIRMIER), `POST /api/v1/seances/{id}/signer-medecin` (ADMIN, MEDECIN), `POST /api/v1/seances/{id}/deverrouiller-regularisation?centerId=` (ADMIN). Le contrôleur legacy `SeanceRestController` porte encore SE3 à SE7. `SeanceUseCase.validate` est aussi appelé par le scan de SE1 (via `ValidationFournie`) : la validation du module le remplace (lot B).

## Décisions
1. **Dans `backend/contexts/seance`**, mêmes tables et URL, aucune migration, aucune colonne ajoutée (ADR 0024). Écritures JDBC de `seances` : `Seances.enregistrer` met à jour statut, jalons, signataires, déverrouilleur et place mémorisée (11 colonnes) ; `trouverPourModifier` lit la ligne `FOR UPDATE` (le délai de verrou de l'unité de travail, 5 s, devient 409 `ECRITURE_CONCURRENTE`).
2. **Domaine** : `Seance` porte les intentions `validerParLInfirmier`, `memoriserPlace`, `signerParLeMedecin`, `deverrouillerPourRegularisation`, `exigerLaRegularisation` (verrou des jours passés, évalué AVANT le statut, KB-SEA-12) ; `Signataire` (borné à 100 caractères) ; `PlaceDeSeance`. Matrice de statuts du legacy reproduite (messages inclus) : facturée refusée (422 `SEANCE_FACTUREE_NON_MODIFIABLE`) ; validation : créée devient validée, validée et signée idempotentes, reliquat `ABSENT` refusé (« La seance n'est pas en statut CREE ») ; signature : validée seulement, message unique pour toute autre cause (KB-SEA-20 reproduit), ligne incohérente « déjà signée » ; déverrouillage : créée d'un jour passé seulement, idempotent.
3. **Application** : `ValiderUneSeance`, `SignerUneSeance`, `DeverrouillerUneSeance`, une unité de travail chacune. Nouveaux ports : `PlaceDuPatient` (langage publié `PlaceFournie`, enveloppe `SeancePlacePort`, retrait avec SE7), `ConsommationsALaValidation` (catalogue et `ConsommationsDeSeance` du Stock par leur langage publié ; un refus du Stock devient `ConsommationRefusee` 422 avec le code du Stock). `DiffusionsDeSeance.seanceDeverrouillee` ajoutée.
4. **Décisions par bug connu** :

| KB | Décision | Raison |
|---|---|---|
| KB-SEA-11 | REPRODUIRE (risque en tête) ; corps trop long : 400 | médico-légal, décision juridique et DPO |
| KB-SEA-12 | REPRODUIRE ; le jour est celui du CENTRE (`HorlogeDuCentre`) | une horloge de moins ; les goldens nomment `<J>` pour la JVM et le centre |
| KB-SEA-13 | REPRODUIRE ; le jour passé du déverrouillage est celui du CENTRE | idem |
| KB-SEA-14 | REPRODUIRE (rediffusion à chaque appel, place figée à la seconde validation) | diffusions reproduites, sans risque d'intégrité |
| KB-SEA-15 | REPRODUIRE (risque en tête) | règle de stock à décider |
| KB-SEA-16 | CORRIGER : corps illisible, absent, `null`, tableau, `centerId` ou identifiant illisible ou absent, ligne `null` : 400 `BAD_REQUEST` (403 avant 400, y compris `MissingServletRequestParameterException`), plus aucun message technique ni nom de classe | 400 contre 500 |
| KB-SEA-17 | REPRODUIRE : audit `SEANCES_CREATION` sur les trois routes (`entity_id` renseigné) ; incohérence notée | mécanisme d'audit par chemin inchangé, DPO |
| KB-SEA-18 | CORRIGER : ligne verrouillée avant lecture et écriture ; verrou non obtenu : 409 `ECRITURE_CONCURRENTE` | intégrité |
| KB-SEA-19 | RIEN À REPRODUIRE : aucun cache `ref.seances.*` n'existe ; l'ancien plan de slice est corrigé | |
| KB-SEA-20 | REPRODUIRE (message par cause à décider) | règle métier, golden stable |
| KB-SEA-21 | REPRODUIRE les droits (y compris `SUPERADMIN` refusé en 403 `ROLE_SCOPE`) et la place figée = place courante de la fiche, déplacement temporaire du jour prioritaire | droits, à confirmer |
| Garde séance, patient, centre inconnus | séance inconnue ou d'un autre centre : 400 « Seance introuvable » (comme SE1) ; patient disparu : 404 `PATIENT_INTROUVABLE` ; article inconnu ou inactif : 400 | décision de SE1 |

## Lot A (livré 2026-11-08) et suite
LOT A : domaine, application, ports publiés et ACL, dépôt JDBC, contrats (mémoire, H2, PostgreSQL 16 par `seance-se1-pg16-rejeu.sh`), câblage des trois cas d'usage ; AUCUNE route changée. LOT B : contrôleur, advice, droits, routage, parité 33/33, retrait des handlers et de `SeanceUseCase.validate` legacy (le scan de SE1 passe au cas d'usage du module), verrou ArchUnit. LOT C : documentation, `built`.
