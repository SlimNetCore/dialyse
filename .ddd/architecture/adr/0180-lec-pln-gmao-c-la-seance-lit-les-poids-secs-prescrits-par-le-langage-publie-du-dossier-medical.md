---
status: accepted (sous-slice (c) livrée le 2026-11-14 ; lecteurs patient-couverture de `gmao_equipements` NON traités, voir Décision 5)
date: 2026-11-14
decision-makers: [utilisateur, architecte]
context-scope: seance (consommateur, port) + dossier-medical (dossier-medical.v1, ACL fournie à la Séance)
related: ADR 0024, 0099, 0101, 0179 ; `.ddd/migration/reviews/lec-pln-gmao-realisation.md` ; `inventaire-platform-restant-v2.md` ligne 15
---

# 0180 - LEC-PLN-GMAO (c) : la Séance lit les poids secs prescrits par le langage publié du Dossier médical

Refactoring sur place (ADR 0024) : mêmes URLs, mêmes tables, mêmes JSON. Comportement inchangé, aucun écart décidé.

## Contexte
`StatistiquesDesSeancesJdbc` (Séance) lisait `prescriptions_medicales` par une sous-requête corrélée (poids sec cible en vigueur le jour de chaque séance), entrée datée de `TableDuDossierMedicalTest` (butoir 2027-03-31). Le Dossier médical dépend de la Séance (cycle Maven interdit l'import inverse).

## Décision
1. **Dossier médical, dossier-medical.v1** : `PoidsSecsPrescritsPublies.poidsSecsCiblesPrescrits(centre, patient)` -> `List<PoidsSecPrescrit(datePrescription, poidsSecCibleKg BigDecimal)>`, seules les prescriptions portant un poids sec, triées `date_prescription DESC, updated_at DESC NULLS LAST, id DESC` (ordre repris MOT POUR MOT de l'ancienne sous-requête, L8). Adapter `PoidsSecsPrescritsJdbc`. `poidsSecCibleEnVigueur` n'est PAS réutilisé (départage différent : sans `NULLS LAST` ni identifiant) et n'est pas modifié.
2. **Séance, seance.v1** : port `PoidsSecDesPrescriptions` (publishedlanguage, comme `SaisiesDeSeanceFournies` : la Séance le déclare, le Dossier médical le fournit). Écart au plan 0179 : le port est dans `publishedlanguage` et non `application` (il doit être visible de l'ACL du Dossier médical).
3. `StatistiquesDesSeancesJdbc` perd la sous-requête : la liste est lue une fois par appel, la PREMIÈRE prescription datée au plus tard du jour de la séance est appliquée en mémoire, l'ordre des clés (`date_seance, poids_avant_kg, poids_apres_kg, poids_sec_cible_kg, uf_reelle_ml`) et des séances est conservé ; aucune séance = port non appelé.
4. ACL `dossier-medical/infrastructure/acl/seance/PoidsSecsPourLaSeance`, câblée par `IndicateursEtStatistiquesConfiguration` ; `SeanceConfiguration` injecte le port.
5. **Lecteurs `gmao_equipements` de Patient et couverture (`GenerateursDuCentreJdbc`, `LectureDesMouvementsJdbc`) : NON traités** (hors budget ~12 fichiers de production, et non neutres). `FichesDesGenerateurs` exclut les équipements supprimés alors que `GenerateursDuCentreJdbc` les décrit encore (comportement reproduit, commentaire en tête) ; `LectureDesMouvementsJdbc` joint `gmao_equipements` SANS borne de centre (`g.id = m.generateur_id`) à l'intérieur d'une requête paginée. Les remplacer demande un port gmao.v1 dédié (équipements supprimés inclus, lecture par lot) et une décision sur la borne de centre. Entrées conservées avec leur revue 2026-12-15, aucun report. Sous-slice suivante : LEC-PAT-GMAO.
6. Gardes : entrée `StatistiquesDesSeancesJdbc` de `TableDuDossierMedicalTest` supprimée (`hasSize(7)` -> 6, `containsExactly` ajusté) + test verrou. `seamAllowed` = [].

## Filet et parité
Golden AJOUTÉ avant le changement : `pl4_paramedical/a_date_de_prescription_egale_le_poid/egalite_de_date` (mise à jour inconnue contre connue ; même date et même mise à jour : identifiant le plus élevé). Il passe byte-à-byte avant et après. Goldens existants inchangés (PL4 x6, DM8, SE7e, cahier). Contrats : Dossier médical `PoidsSecsPrescritsContrat` (double en mémoire, H2, PostgreSQL 16 : IT ignoré sans `-Ddossiermedical.pg16.url`) ; Séance `PilotageEtRapportsDesSeancesContrat` (le JDBC reçoit un double du port qui rend l'ordre publié) ; test de traduction `PoidsSecsPourLaSeanceTest`.

## Reproduits, à confirmer métiers/DPO <= 2026-12-15
`poidsSecCibleKg` de l'en-tête des statistiques (`poidsSecCibleEnVigueur`) départage à date et mise à jour égales sans identifiant, donc sans ordre défini, alors que la courbe par séance applique l'identifiant : les deux peuvent différer à égalité stricte (constaté sur le golden d'égalité, retiré du filet pour cette clé). Reproduit, non corrigé.
