status: proposed
date: 2026-11-15
sources: directive « aucun butoir, pas d'échéance »

# CLOT2 : mécanique de butoir daté retirée des gardes de tables

- `ButoirsDesGardesDeTables` renommée `ExclusionsDesGardesDeTables` (ne garde que `estUneEntiteJpaConnue`, exclusion nominative) ; `butoir(...)` et `butoirsAbsentsOuDepasses(...)` supprimées.
- `TableDesTransporteursTest` : assertion `butoirsAbsentsOuDepasses` retirée, test renommé `les_entites_jpa_ne_sont_plus_exclues_en_bloc` ; Javadoc « sans échéance ».
- `TableDesForfaitsTest` : assertion retirée, test renommé `le_dernier_lecteur_est_solde_et_non_reporte`, import `LocalDate` retiré, commentaires sans butoir.
- `TableDeLOrganisationTest` : import de l'outillage mis à jour (n'utilisait pas la comparaison de date).
- Les autres tests (`TableDuPilotage`, `TableDuDossierMedical`, `TableDesSeances`, `TableDesAnalyses`, `EcrivainsDeLaFactureDesSeances`) portent un champ `butoir()` de leur propre record : hors périmètre, non modifiés.
- Production inchangée. Vérifications : Table*Test ciblés verts (Organisation 6, Forfaits 7, Transporteurs 6), check-arch 0 violation.
