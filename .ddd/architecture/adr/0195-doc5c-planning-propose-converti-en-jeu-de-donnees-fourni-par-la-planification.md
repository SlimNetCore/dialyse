---
status: accepted (livré le 2026-11-15, slice DOC5c, suite des ADR 0193 et 0194)
date: 2026-11-15
decision-makers: [utilisateur, architecte]
context-scope: planification, documents, platform (filet)
related: ADR 0024 (refactoring sur place), 0140 (D3), 0162 à 0167 (Documents, PLN-DOC), 0193 (DOC5a), 0194 (DOC5b) ; `doc5c-realisation.md` ; `inventaire-platform-restant-v2.md` ligne 7 (DOC5)
---

# 0195 - DOC5c : le planning proposé par l'optimisation devient un jeu de données fourni par la Planification

## Contexte
Après DOC5b (ADR 0194), 10 gabarits embarquaient encore une requête SQL servie par le port transitoire `ConnexionDeRendu`. Le planning proposé (`planning_optimise.jrxml`, type `PLANNING_OPTIMISE`) lisait, par une requête de 8 lignes, UNE table de la Planification (`planification_calendrier_case`) : les lignes (salle x créneau) du calendrier figé d'une proposition, bornées au centre et à l'exécution, triées par semaine, créneau, salle. La Planification possède la table, l'écrit (`CalendriersDesPropositionsJdbc.enregistrer`) et fournit déjà tous les autres paramètres du document (`CalendrierDeLaPropositionImprimeParDocuments`).

## Décision
Le gabarit perd sa requête (`queryString`) ; ses champs (`SEMAINE_DEBUT`, `SALLE_NOM`, `CRENEAU_LIBELLE`, `CRENEAU_ORDRE`, `ENTETE_0..6`, `CELL_0..6`) et ses paramètres (dont `RUN_ID`, devenu inutilisé mais conservé) sont inchangés. La Planification fournit les lignes sous `LIGNES`.

| Élément | Choix |
|---|---|
| Port | `CalendriersDesPropositions.imprimables(centre, execution)` (port sortant EXISTANT de l'application, une méthode de plus) : les lignes en texte prêt à imprimer, ordre semaine, créneau, salle, borné au centre |
| Langage | `CaseImprimableDuCalendrier` (début de semaine, salle, créneau, 7 en-têtes, 7 contenus ; un jour absent de la semaine reste nul) |
| Adapter | `CalendriersDesPropositionsJdbc.imprimables` : l'ancienne requête reprise telle quelle, côté propriétaire de la table (lit les colonnes déjà écrites, pas de recalcul) |
| ACL | `CalendrierDeLaPropositionImprimeParDocuments` reçoit le port et traduit chaque ligne en champs du gabarit, dans les types déclarés (`java.sql.Date`, entier, texte) ; traduction testée champ par champ |
| Fake | `OptimisationEnMemoire.calendriers` implémente `imprimables` ; le MÊME jeu de tests de contrat passe sur le fake, H2 et PostgreSQL |

Aucun nouveau port ni nouvel adapter hors de cette méthode ; `CalendrierEnTexte` devient public pour que le fake de test produise les mêmes textes que l'écriture.

## Parité
Filet `characterization/planification/p8-optimisation/doc5c/*` (2 scénarios, TOUT le texte du PDF) approuvé sur l'ANCIEN code puis vert à l'identique : proposition à deux semaines, trois salles (« Salle 1 », « Salle 10 », « Salle Étoile »), deux créneaux, lignes écrites dans le désordre, cases vides, FERMÉ avec motif, textes sur plusieurs lignes et très longs, plusieurs pages (en-tête de semaine repris), lignes d'une autre proposition, d'un autre centre et du même identifiant d'exécution dans un autre centre non imprimées ; puis une ligne unique dont les deux derniers jours sont absents. Les 6 filets existants de l'impression (statut, format du modèle, modèle désactivé, exécution sans calendrier) restent verts, aucun golden modifié.

## Comportement de bord changé (KB-DOC5C-1, même nature que KB-DOC5A-1 et KB-DOC5B-1)
Le validateur « requête identique » (RG-DOC-004) compare la requête du modèle téléversé à celle du gabarit livré. Le planning livré n'en ayant plus, le téléversement d'un modèle de ce type qui porte encore l'ANCIENNE requête (fichier téléchargé avant DOC5c) est refusé `QUERY_MODIFIED` ; téléchargé après, il est accepté. AUCUNE règle « accepter l'ancienne requête ». Les modèles DÉJÀ stockés (avec l'ancienne requête) s'impriment sans changement : la source de données fournie prime, mêmes noms de champs. À confirmer métiers <= 2026-12-15.

## Limites
- Ordre : identique à l'ancienne requête (`ORDER BY semaine_debut, creneau_ordre, salle_ordre`, exécutée par le même SGBD) ; deux lignes de même clé (impossible tant que salle et créneau sont uniques par semaine) resteraient départagées par le SGBD.
- Le planning figé est lu en une fois en mémoire (une ligne par salle, créneau et semaine : quelques centaines au plus) ; aucune pagination.

## Gardes datées
Aucune entrée de garde ne concernait ce gabarit (`TableDeLaPlanificationTest` : aucune ligne datée sur `planification_calendrier_case`) : rien à retirer, aucune date reportée.

## Conséquences
- 9 gabarits à SQL embarqué restent : `inventaire_stock`, `planning_presence_infirmiers`, six gabarits patient (`fiche_patient`, `attestation`, `liste_attestations`, `liste_patients`, `liste_pec`, `prise_en_charge`), `ordonnance`. Prochain candidat : `planning_presence_infirmiers` (Planification, plusieurs tables) ou `ordonnance` (butoir daté 2027-03-31).
- Le port `ConnexionDeRendu` est conservé pour les 9 restants.
- Aucun changement de route, de droit, de code d'erreur ni de rendu ; `seamAllowed` reste vide ; aucun fichier legacy de la plateforme modifié.
