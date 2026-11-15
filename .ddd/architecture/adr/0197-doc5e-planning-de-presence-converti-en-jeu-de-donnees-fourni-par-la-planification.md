---
status: accepted (livré le 2026-11-15, slice DOC5e, suite des ADR 0193 à 0196)
date: 2026-11-15
decision-makers: [utilisateur, architecte]
context-scope: planification, organisation (lecture publiée), documents, platform (filet)
related: ADR 0024 (refactoring sur place), 0107 à 0111 et 0115 (organisation.v1), 0140 (D3), 0167 (PLN-DOC), 0193 à 0196 (DOC5a à DOC5d) ; `doc5e-realisation.md` ; `inventaire-platform-restant-v2.md` ligne 7 (DOC5)
---

# 0197 - DOC5e : le planning de présence des infirmiers devient un jeu de données fourni par la Planification

## Contexte
Après DOC5d (ADR 0196), 8 gabarits embarquaient encore une requête SQL servie par le port transitoire `ConnexionDeRendu`. Le planning de présence (`planning_presence_infirmiers.jrxml`, type `PLANNING_PRESENCE_INFIRMIERS`) lisait, par une requête de 120 lignes (sept fois la même, un bloc par jour de la semaine), quatre tables de la Planification (`infirmier`, `infirmier_affectation`, `infirmier_absence`, `infirmier_remplacement`) ET deux tables d'Organisation (`salle`, `position_creneau`), cette seconde lecture échappant à la garde `TableDeLOrganisationTest` qui ne regarde que le Java.

## Décision
Le gabarit perd sa requête ; ses champs (`DATE_JOUR`, `SALLE`, `CRENEAU_CODE`, `CRENEAU`, `INFIRMIER`, `STATUT`) et ses paramètres (`CENTER_ID`, `DATE_0` à `DATE_6`, `SEMAINE_LABEL`...) sont inchangés. La Planification fournit les lignes sous `LIGNES`.

| Élément | Choix |
|---|---|
| Langage | `LigneDuPlanningDePresence` (jour, salle, code et libellé du créneau, infirmier, `Situation` PREVU / ABSENT / REMPLACANT portant son libellé imprimé) |
| Port d'application | `PlanningDePresenceImprimable.lignes(centre, dimanche)` : les lignes ordonnées, prêtes à imprimer |
| Port sortant possédé | `RoulementImprimableDeLaSemaine` : le roulement de la semaine avec des identifiants de salle et de créneau, sans ordre ; adapter `RoulementImprimableDeLaSemaineJdbc` (les seules tables de la Planification, ancienne requête reprise jour par jour sans les deux jointures d'Organisation) |
| ACL | `PlanningDePresenceImprimableAvecLesLibellesPublies` (acl/organisation) : lit par lot `LibellesDeLOrganisation.nomsDesSalles` et `creneauxDuCentre` (organisation.v1, bornés au centre), écarte les lignes sans libellé, trie |
| ACL de Documents | `PresenceImprimeeParDocuments` reçoit le port et traduit chaque ligne en champs du gabarit (`java.sql.Date`, texte) ; le planning n'est pas lu pour un format non servi |
| Fake | `RoulementImprimableDeLaSemaineEnMemoire` ; le MÊME contrat (10 tests) passe sur le fake et sur H2 (pas de variante PostgreSQL IT) |

Un seul filet de rendu, avant toute modification : `characterization/planification/p4-presence/doc5e/*` (3 scénarios, TOUT le texte du PDF et le nombre de pages) approuvé sur l'ANCIEN code puis vert à l'identique : semaine riche à deux pages (cinq salles et trois créneaux dont « Salle 1 », « Salle 10 », « Salle 2 », « Salle Étoile », noms accentués, sans prénom, particule, deux affectations d'un même infirmier, absences aux bornes de la semaine et à cheval, fiche inactive, remplaçants dont un inactif, remplaçant aussi prévu, données d'un autre centre), semaine sans aucune ligne (une page blanche), jours écrits de façon inhabituelle (minuscules, mots collés, séparateur, vide). Les 11 goldens existants de l'impression de la présence restent verts, aucun golden modifié.

## Comportements de bord changés
- **KB-DOC5E-1** (même nature que KB-DOC5A-1 à KB-DOC5D-1) : le téléversement d'un modèle `PLANNING_PRESENCE_INFIRMIERS` qui porte encore l'ANCIENNE requête (fichier téléchargé avant DOC5e) est refusé `QUERY_MODIFIED` ; les modèles déjà stockés s'impriment sans changement (la source de données fournie prime, mêmes noms de champs). À confirmer métiers <= 2026-12-15.
- **KB-DOC5E-2** (écart possible en production) : l'ancienne requête laissait le tri au SGBD ; les noms de salle et codes de créneau viennent d'Organisation, le tri est fait par le serveur, par points de code. Identique sur H2 et sur PostgreSQL en locale C (rejeux) ; une base en locale linguistique pouvait ordonner autrement les accents, majuscules et blancs. À confirmer avec l'exploitation (collation de la base de production).
- **KB-DOC5E-3** (marginal, non observé) : l'ancienne jointure se faisait par identifiant seul, la lecture publiée est bornée au centre ; une salle ou un créneau d'un autre centre référencé par erreur n'est plus imprimé.

## Limites
- Les lignes de la semaine (quelques dizaines à quelques centaines) sont lues et triées en mémoire ; aucune pagination.
- Pas de variante PostgreSQL du contrat du roulement (la requête est la même qu'avant, hors jointures).
- Une affectation dont le champ `jours` contient plusieurs noms de jours collés est lue par sous-chaîne, comme avant.

## Gardes datées
Aucune entrée de garde ne concernait ce gabarit (`TableDeLaPlanificationTest` : liste blanche vide ; `TableDeLOrganisationTest` : liste vide, et ne scrutait pas les gabarits) : rien à retirer, aucune date reportée. Un test est ajouté à `TableDeLaPlanificationTest` (le gabarit ne porte plus de requête ni de table de la Planification). La première version de la conversion lisait `salle` et `position_creneau` depuis la Planification : la garde permanente d'Organisation l'a refusée, d'où le passage par organisation.v1.

## Conséquences
- 7 gabarits à SQL embarqué restent : `inventaire_stock` (Stock) et six gabarits patient (`fiche_patient`, `attestation`, `liste_attestations`, `liste_patients`, `liste_pec`, `prise_en_charge`).
- Le port `ConnexionDeRendu` est conservé pour ces 7 gabarits.
- Aucun changement de route, de droit, de code d'erreur ni de rendu observable sur le filet ; `seamAllowed` reste vide ; aucun fichier legacy de `platform/src/main` modifié.
