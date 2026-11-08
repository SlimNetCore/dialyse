---
status: proposed
date: 2026-11-08
sources: ADR 0092, commits 6ee001cc / 5b7aab25 / 08b69098 / 62fcd12a, backend/contexts/seance, backend/platform
---
# Revue indépendante : Séance SE5 (surcharge du forfait, « imposer un forfait »)

Contrôles automatiques : `check-arch` = 0 violation (0 baselinée, 0 nouvelle). Revue en lecture seule, constats vérifiés (preuve citée).

## Constats

| Sév. | Domaine | Constat | Preuve | Correction recommandée |
|---|---|---|---|---|
| major | legacy / argent | Le résidu 2 de l'ADR est sous-estimé. Il parle de « six colonnes `forfait_override_*` ». En réalité `SeanceRepositoryAdapter.save` fait `jpa.save(toJpa(...))` (merge d'une entité reconstruite) : toutes les colonnes de la ligne sont réécrites avec l'état lu par `findById` (statut, jalons, signatures, place). `toJpa` ne renseigne jamais `factureId` : un `updateDate` sur une séance portant `facture_id` mais de statut non `FACTUREE` (cas KB-SEA-43) semble effacer `facture_id`. Lu dans le code, non rejoué à l'exécution. Le risque d'écrasement de l'imposition est réel mais étroit : `updateDate` est transactionnel sans verrou ; l'`UPDATE` attend le verrou de ligne de l'imposition puis écrit les valeurs périmées, sans erreur. Les deux routes sont réservées à ADMIN, d'où une probabilité faible, mais l'enjeu est financier. | `platform/.../persistence/adapter/SeanceRepositoryAdapter.java:47-48,192-215` (aucun `setFactureId`) ; `bc-seance/.../SeanceDomainService.java:190-198` ; ADR 0092:55 | Corriger l'ADR et le fichier de slice : écrasement de toute la ligne, `facture_id` compris. Ajouter à l'ADR 0092 une condition de mise en production pour `facture_id` effacé. Faire rejouer le cas par un test de caractérisation de `PUT /{id}` sur une séance avec `facture_id`. Avancer la reprise de `updateDate` ou lui faire prendre le verrou `FOR UPDATE`, avant 2027-01-31 si possible. |
| minor | legacy | 4e écart d'ordre des refus non déclaré. `AuteurDuForfaitImpose` est construit avant le verrou, la lecture de la séance et le contrôle de facturation. Un auteur de 101 caractères sur une séance `FACTUREE` donne désormais 400, alors que le legacy donnait 422 (la garde de facturation passait avant l'écriture qui échouait en 500). Les 3 écarts décidés ne le couvrent pas : le scénario 11 de `auteur` vise une séance non facturée. Pas d'effet sur l'argent (refus dans les deux cas). | `application/ImposerUnForfait.java:44-47` ; `SeanceDomainService.updateForfait` (retiré en 08b69098) : `findById`, forfait, puis `modifierForfait` qui lève `SeanceFactureeNonModifiable` | Le déclarer comme écart (4e ligne de l'ADR) ou construire l'auteur dans l'unité de travail après `imposerUnForfait`. |
| minor | tests / docs | §57 se contredit : « Les requêtes 1 à 7 sont rejouées … (8 requêtes) ». Le script en rejoue bien 8. | `.ddd/migration/verifications-production.md:2165` ; `backend/scripts/seance-se5-pg16-rejeu.sh:70-79` | Écrire « 1 à 8 ». |
| nit | stratégique | `ports-adapters-seance.md` liste toujours `ForfaitDeSeanceSurcharge` comme événement à publier vers 6 consommateurs. L'ADR 0092 dit « aucune diffusion » sans la raison (elle n'est que dans le fichier de slice). | `.ddd/architecture/ports-adapters-seance.md:27` ; ADR 0092:34 ; slice:63 | Marquer l'événement « non émis, aucun consommateur » dans le tableau et reprendre la raison dans l'ADR. |
| nit | legacy | L'écart `EN_COURS` change tout le message (« La séance <SEANCE> porte une valeur illisible : statut inconnu »), pas seulement le nom de classe d'enum. Le golden le montre et c'est justifié (aucune donnée technique divulguée). | `se5/surcharge/la_surcharge_n_est_refusee_que_pour_/matrice_des_statuts_nouveau_code.approved.json:4` | RAS, ADR à nuancer d'un mot. |
| question | argent | La Facturation écrit `forfait.prixHt()` dans `forfait_override_prix`, alors que la Séance y recopie le `prix` du référentiel. La colonne peut donc porter deux sémantiques (HT ou prix référentiel). Non vérifié ici ; hors SE5, déjà proche de KB-SEA-47. | `SeancesDeFacturationJdbc.java:69-75` | À trancher avec le résidu 1, dans la slice Facturation. |

## Points vérifiés conformes
1. Hexagonal : le domaine est pur (pas d'horloge ni d'ORM ; l'instant passe en paramètre). L'ACL `ForfaitsDuCentreParLeLegacy` traduit `libelle` en `nom`. `SeancesJdbc` est un adapter sans règle. Câblage dans `SeanceConfiguration`. Les noms respectent le glossaire (« imposer un forfait »).
2. Argent : `ForfaitDuCentre` et `ForfaitImpose` recopient `prix` en `BigDecimal` tel quel, sans arrondi ni TVA. La requête `SELECT id, code, libelle, prix FROM forfait WHERE center_id=? AND id=?` est identique à celle de `SeanceForfaitCatalogAdapter` (retiré). Le code, le nom, l'instantané et le `trim` de l'auteur sont identiques au legacy. Le repli `"system"` du legacy est inatteignable (`@NotBlank` conservé). La priorité surcharge > effectif > demandé et le repli relèvent de la lecture SE1 (non modifiée) ; les goldens `forfait_courant` sont verts.
3. Ordre des refus (hors le point minor ci-dessus) : centre 403, puis séance 400, forfait 400, facturée 422, conforme au legacy. Le `@Valid` reste avant le 403, comme avant.
4. Écarts : les goldens archivés sont R100 (`git diff -M 6ee001cc 08b69098`). Le `diff` des `*_nouveau_code` contre l'original ne montre que les étapes 06, 07, 09, 10, 11 de `parametres`, l'étape 11 de `auteur` et `EN_COURS`. Aucun autre écart.
5. Droits : `@PreAuthorize("hasRole('ADMIN')")` (`SeancesControleur.java:156`) ; ROLE_SCOPE et 403 inchangés, couverts par le net.
6. Verrou : `trouverPourModifier` en `FOR UPDATE` et 409 `ECRITURE_CONCURRENTE` (`SeancesJdbc.java:84-90`). `enregistrerLeForfaitImpose` n'écrit que les six colonnes `forfait_override_*` et lève une exception si le nombre de lignes n'est pas 1 (`SeancesJdbc.java:109-117`). Pas d'effet de bord sur les autres colonnes du côté du module.
7. ADR 0092 : les risques KB-41/42/43/46/47 sont en tête, avec condition de production, requête §57 et jalon 2026-12-15 ; les résidus 1 à 4 portent des butoirs nominatifs (2027-01-31, et 2027-03-31 pour le 4) ; revue ≤ 2026-12-15.
8. Tests : aucun `Instant.now`, `LocalDate.now` ni `OffsetDateTime.now` dans `contexts/seance/src/test` ni dans `characterization/seance/se5`. Les invariants du domaine sont testés dans `ForfaitImposeALaSeanceTest`.
9. §57 : 8 requêtes en SELECT seul. Les colonnes `forfait_override_*`, `facture_id`, `statut`, `date_seance` et celles de `prise_en_charge` sont celles du SQL legacy ; elles sont rejouées sur le schéma PG16 par le script.
10. ArchUnit : deux verrous ajoutés (`FrontieresDesContextesTest`). Seuls `Seance` (legacy) et `SeanceRepositoryAdapter` peuvent appeler `setForfaitOverride*`. Le verrou ne couvre pas les `UPDATE` SQL de la Facturation (hors périmètre).
11. Événement `ForfaitDeSeanceSurchargé` non émis : cohérent avec « pas d'événement nouveau » (voir le nit).

## Verdict : pass-with-actions
Aucun blocker. 1 major, 2 minor, 2 nit, 1 question.

Top 3 actions :
1. Corriger l'ADR et la slice sur la portée réelle du résidu 2 (toute la ligne réécrite, `facture_id` possiblement effacé). Ajouter un test de caractérisation de `PUT /{id}` sur une séance avec `facture_id` et le rejouer. Décider de la reprise anticipée de `updateDate`, ou de lui faire prendre le verrou.
2. Déclarer ou supprimer le 4e écart d'ordre (auteur de 101 caractères sur séance facturée : 400 au lieu de 422).
3. Rectifier §57 ("1 à 8") et `ports-adapters-seance.md:27` (événement non émis). Trancher la sémantique du prix HT ou référentiel avec le résidu 1.
