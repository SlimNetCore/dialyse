---
status: accepted (livré le 2026-11-15, slice DOC5b, suite de l'ADR 0193)
date: 2026-11-15
decision-makers: [utilisateur, architecte]
context-scope: gmao, documents, platform (filet)
related: ADR 0024 (refactoring sur place), 0140 (D3), 0145 (PL6, bon d'intervention), 0162 à 0166 (Documents), 0193 (DOC5a) ; `doc5b-realisation.md` ; `inventaire-platform-restant-v2.md` ligne 7 (DOC5)
---

# 0194 - DOC5b : le bon d'intervention devient un jeu de données fourni par la GMAO

## Contexte
Après DOC5a (ADR 0193), 11 gabarits embarquaient encore une requête SQL servie par le port transitoire `ConnexionDeRendu`. Le bon d'intervention (`bon_intervention.jrxml`, type `BON_INTERVENTION`) lisait, par une requête de 17 lignes, deux tables de la GMAO (`gmao_interventions`, `gmao_lignes_cout_intervention`) : une jointure externe de l'intervention vers ses lignes de coût, bornée au centre et à l'intervention, triée par libellé. La GMAO possède ces deux tables et fournit déjà tous les autres paramètres du bon (`BonRenduParDocuments`).

## Décision
Le gabarit perd sa requête (`queryString`) ; ses champs (`LIGNE_TYPE`, `LIGNE_LIBELLE`, `LIGNE_QUANTITE`, `LIGNE_PRIX`, `LIGNE_MONTANT`) et ses paramètres sont inchangés. La GMAO fournit les lignes sous `LIGNES` (ACL `BonRenduParDocuments.lignesDuBon`, traduction testée champ par champ). AUCUN nouveau port, AUCUN nouvel adapter : les lignes de coût font déjà partie de l'intervention chargée par `ImprimerLeBon` (donc bornée au centre, soit la condition `centre_id` de l'ancienne requête) et de `InterventionVue.lignes()`.

| Aspect de l'ancienne requête | Reprise en Java |
|---|---|
| libellé du type (`CASE l.type`) : Pièce, Main d'œuvre, Honoraires intervenant, Autre | `libelleDuType` (même texte) |
| montant = quantité x prix, sans arrondi | `LigneVue.montant()` (même produit) |
| `ORDER BY l.libelle` | tri croissant des libellés (ordre des caractères), STABLE : à libellé égal, l'ordre de l'intervention |
| jointure externe : un bon sans ligne imprime quand même sa fiche | UN enregistrement vide (cinq champs nuls) quand il n'y a aucune ligne ; sans lui, une source vide n'imprimerait pas le groupe « fiche » (titre, équipement, constat) |

## Parité
Filet `characterization/gmao/pilotage/bon_doc5b_*` (2 scénarios, tout le texte du PDF : huit lignes de coût de quatre types, libellés qui départagent majuscules, minuscules, accent et chiffres, quantité décimale, grand montant, libellé long, lignes d'une autre intervention du centre ; puis un bon sans ligne), approuvé sur l'ANCIEN code puis vert à l'identique sur le nouveau. Goldens PL6 du bon (nominal, arrondis, sans ligne, nombreuses lignes, montants limites, caractères spéciaux), `BonInterventionReportTemplateTest`, validateur et compilation des 14 gabarits : inchangés et verts.

## Comportement de bord changé (KB-DOC5B-1, même nature que KB-DOC5A-1)
Le validateur « requête identique » (RG-DOC-004) compare la requête du modèle téléversé à celle du gabarit livré. Le bon livré n'en ayant plus, le téléversement d'un modèle du bon qui porte encore l'ANCIENNE requête (fichier téléchargé avant DOC5b) est refusé `QUERY_MODIFIED` ; téléchargé après, il est accepté. Traitement identique à KB-DOC5A-1 et à l'ADR 0193 : AUCUNE règle « accepter l'ancienne requête » (17 lignes de SQL mort à garder pour un cas qui disparaît au premier téléchargement). Les modèles du bon DÉJÀ stockés (avec l'ancienne requête) s'impriment sans changement : la source de données fournie prime, mêmes noms de champs (garanti par le moteur, déjà vérifié par DOC5a). À confirmer métiers <= 2026-12-15.

## Limites
- Ordre des libellés : l'ancienne requête laissait l'ordre au SGBD (collation de la base) ; le nouvel ordre est celui des caractères (identique à la base de test). Sur une base PostgreSQL à collation linguistique (`fr_FR`, `en_US`), des libellés qui ne diffèrent que par la casse ou l'accent pourraient s'imprimer dans un autre ordre qu'avant (question ouverte : collation de la base de production).
- À libellé égal, l'ancien ordre était indéterminé ; il suit désormais l'ordre de l'intervention (déterministe).
- Pas de test de contrat dédié fake/réel : il n'y a pas d'adapter nouveau.

## Gardes datées
Aucune entrée de garde ne concernait ce gabarit (pas de butoir sur les tables de la GMAO) : rien à retirer, aucune date reportée.

## Conséquences
- 10 gabarits à SQL embarqué restent : `inventaire_stock`, `planning_presence_infirmiers`, `planning_optimise`, six gabarits patient (`fiche_patient`, `attestation`, `liste_attestations`, `liste_patients`, `liste_pec`, `prise_en_charge`), `ordonnance`. Prochain candidat : `ordonnance` (Dossier médical : butoir daté 2027-03-31 de `TableDuDossierMedicalTest`, requête de 16 lignes sur 4 tables). Alternative la moins coûteuse : `planning_optimise` (une table de Planification).
- Le port `ConnexionDeRendu` est conservé pour les 10 restants.
- Aucun changement de route, de droit, de code d'erreur ni de rendu ; `seamAllowed` reste vide ; aucun fichier legacy de la plateforme modifié.
