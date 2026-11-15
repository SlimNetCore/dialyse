# ACL (b) Stock + Facturation : réalisation (2026-11-15, ADR 0191)

Mesure avant/après (fichiers de production de `platform/.../infrastructure/acl/*`) : absences 2, dossiermedical 1, gmao 2, organisation 4, patientcouverture 4, reprise 1, seance 1 inchangés ; **facturation 4 -> 0, stock 4 -> 0** ; total 27 -> 19, sous-dossiers 9 -> 7.

| ACL | Cycle Maven ? | Destination |
|---|---|---|
| CentresDuStockParOrganisation + test, VerrouDuCentreDuStockParOrganisation | non (organisation -> stock) | organisation/infrastructure/acl/stock |
| 4 traducteurs de la Facturation | non (seance -> absences -> facturation ; pom Séance + dépendance directe facturation) | seance/infrastructure/acl/facturation |
| StatutDesSeancesParLaSeance, PerimetreDesSeancesParLaSeance + test | non (seance -> stock) | seance/infrastructure/acl/stock ; ports publiés `StatutDesSeancesFourni`, `PerimetreDesSeancesFourni` + 2 adaptateurs minces du Stock (ArchUnit interdit à la Séance le domaine du Stock) |

Câblage : `@Bean` dans OrganisationConfiguration (2), SeanceConfiguration (6), StockContexteConfiguration (2). Garde repointée : TableDesSeancesTest (dossier facturation). Aucune garde datée rattachée (rien à supprimer, aucune date reportée). Aucun cycle réel dans ce périmètre.

Lignes à retirer de `.ddd/safety-net/covered.json` (non modifié) : 74 (StatutDesSeancesParLaSeance), 75 (PerimetreDesSeancesParLaSeance), 76 (SeancesDeFacturationParLaSeance), 77 (LiaisonDesSeancesParLaSeance), 78 (SeancesFacturablesParLaSeance), 79 (SeancesDesFacturesParLaSeance), chemins `backend/platform/.../infrastructure/acl/{stock,facturation}/*` (numéros d'après le retrait de l'ACL (a)). Les deux ACL Organisation du Stock n'y figuraient pas.

Etat d'avancement (reprise après coupure) : code des 8 ACL migré et compilant ; 3 passes ciblées vertes (normale, random, normale) ; check-arch 0 violation ; ADR 0191 et inventaire ligne 20 écrits ; build complet détaché terminé : 3571 tests, 1 échec connu (InventairePl7...feuille_de_comptage, date), aucun autre [ERROR]. Reste à l'appelant : retirer les lignes de covered.json.

Vérifications : voir le rapport de l'appelant (passes ciblées, check-arch 0, build complet).
