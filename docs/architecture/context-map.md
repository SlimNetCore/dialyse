# Context Map DDD - Hemodialyse multi-centre

## Vision
Application monolithique modulaire (modulith) avec architecture hexagonale.
Le domaine metier est isole des details techniques.

## Bounded contexts
- `PatientIdentity`: identite patient, informations administratives, pieces jointes.
- `InsuranceCoverage`: caisse/agence/centre payeur, assures, affiliations.
- `CareAuthorization`: attestations et prises en charge.
- `SessionPlanning`: regles d autorisation et planification des seances.
- `CenterResources`: medecins, creneaux, salles, generateurs, transporteurs.
- `AccessControl`: utilisateurs, roles, affectation centres, JWT.

## Relations
- `PatientIdentity` -> `InsuranceCoverage`: un patient porte ses affiliations assurances.
- `InsuranceCoverage` -> `CareAuthorization`: donnees necessaires pour l eligibility PEC.
- `CareAuthorization` -> `SessionPlanning`: la validite PEC conditionne la creation de seance.
- `AccessControl` -> tous contexts: impose `TenantScope(centerId, userId, roles)`.

## Regles de frontiere
- Les references inter-contextes se font par identifiants, pas par objets persistants partages.
- Les regles metier sont executees dans le domaine, pas dans REST/JPA.
- Les donnees globales (referentiels) sont explicitement qualifiees comme globales.

## Multi-centre
- Single database PostgreSQL avec isolation logique par `center_id`.
- Toute entite metier sensible porte `center_id` en NOT NULL.
- Les unicites sont scopees par `center_id`.

