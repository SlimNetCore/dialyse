---
slice: seance-se7f-retrait-bc-seance-et-garde
status: planned
date: 2026-11-08
order: 7.6
context: seance
parent: seance-se7-lectures-publiees
depends_on: [seance-se7a-routes-residuelles, seance-se7b-tableau-de-bord-mensuel, seance-se7c-lectures-facturation-stock, seance-se7d-lectures-absences-planification-dossier, seance-se7e-lectures-pilotage-et-rapports]
sources: [.ddd/safety-net/seance-se7-lectures-publiees.md]
---

# SE7f : retrait de bc-seance et garde « pas de SQL sur les tables de Séance hors contexte »

Résidu `bc-seance` : 36 fichiers main et 7 fichiers de test (domaine `Seance`, `SeanceDomainService`, `VoletParamedical` 567 lignes, `VoletMedical`, `ResultatAnalyse`, `domain/planning/*`, ports) ; plateforme : `SeanceApplicationService`, `SeanceRepositoryAdapter`, `SeanceJpaEntity`, `SeanceJpaRepository`, `SeancePlaceJdbcAdapter`, `SeancePlanningJdbcAdapter`, `VoletMedical/Paramedical RepositoryAdapter`, `ResultatAnalyseRepositoryAdapter`, `DomainServiceConfig`, `SeanceBillingEligibilityParLaCouverture`, `PlanningParametresDuContexte` ; utilisateurs hors Séance de `domain.seance`/`domain.planning` : `DirectionCapaciteQueryService`, `KdigoGreffeQueryService`. Les 12 adaptateurs `infrastructure/acl/seance/*Legacy` (langage publié `seance.v1`) migrent dans `contexts/seance`.

Garde : réécrire `TableDesSeancesTest` (aujourd'hui `@Disabled`, aveugle aux lecteurs de `platform/src/main` et des dossiers `/acl/` et `/persistence/`) avec liste blanche nominative datée ; réactiver à la fin de SE7c/d/e. Risque : élevé (retrait irréversible), à faire en dernier, après période d'observation. Les volets (paramédical 567 lignes, médical, analyses) n'ont pas encore de contexte cible : question ouverte pour l'ADR (Séance ou Dossier médical).
