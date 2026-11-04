---
slice: patient-couverture-pc2-assure-principal
status: built
date: 2026-11-04
order: 2
context: patient-couverture
sources: [.ddd/tactical/patient-couverture.md, ADR 0064]
---

# PC2 — Assuré principal

> Statut vrai au 2026-11-04 : **`built`** (revue du 2026-11-04 traitée, ADR 0077 `accepted`). Historique : au 2026-11-02 `characterized` (7 goldens legacy `characterization/patientcouverture/assure`, URL legacy). Module gelé (`GEL.md`, ADR 0073) : le code du module n'est ni câblé ni en parité ; R1 vit dans le service applicatif. Reprise sur place après le filet PC1.

Agrégat `Assure` (root). Un seul assuré principal par patient (R1).

## Cas d'usage

`AffecterAssurePrincipal`, `ModifierAssure`, `ConsulterAssure`.

## Entrées legacy

`bc-assure`, `bc-insurance`.

## Données

Tables assure ; aucune migration.

## Avancement (reprise sur place, ADR 0024 ; ADR PC2/PC3 à venir en S3)

| Session | État | Contenu |
|---|---|---|
| S3 PC2+PC3 (2026-11-03) | **built** | parité atteinte : filet PC2 vert sur le nouveau code, 6 archives `pc2/_ecarts-legacy` (assuré d'un autre centre refusé, idempotence de la ligne d'affectation, corps vide du `PUT` assuré refusé 400), ADR 0077 (proposed), KB-PAT-29 à 34 statuées, `verifications-production.md` §42, build complet `./mvnw -o test` vert, contrat H2 et PG16 (courses réelles R1) verts, PIT domaine et application 85 % (`pit-pc2-pc3-module.sh`), `check-arch` 0 violation, seams fermés. Reste (dette) : décisions métier KB-PAT-30 et 34 reproduits ; contrainte SQL R1 après nettoyage. |
| S1 PC2 (2026-11-03) | faite | domaine `AssurePrincipal` (assuré d'un autre centre refusé 422 `ASSURE_D_UN_AUTRE_CENTRE`, idempotence de la ligne d'affectation, nom obligatoire à la modification), `PrincipalDeLaFiche`, verrou du centre en tête de `affecter` et de la correction de dates, port `AssuresDuCentre` étendu, sources gelées Assure supprimées et `excludes` retirés, 3 goldens `_nouveau_code` (6 archives `pc2/_ecarts-legacy`) |

Décisions prises le 2026-11-03 (déléguées) : (b) pas de capture d'un assuré d'un autre centre ; (c) R1 sous le verrou du centre, sans contrainte SQL ; (g) pas de ligne `assure_patient` ajoutée à chaque enregistrement ; **(h) le `PUT` d'un assuré exige le NOM (400), le prénom reste facultatif, le remplacement total reste documenté (les autres champs absents sont effacés)**.