---
status: accepted
date: 2026-10-21
deciders: utilisateur (décisions F5 du 2026-10-21) ; chiffres affichés marqués « à valider avec la direction et la comptabilité avant déploiement »
---

# ADR 0034 — Facturation : lectures de pilotage, lectures publiées pour la Direction, totaux stockés comme base unique

## Contexte

Le tableau de bord de facturation, la synthèse mensuelle imprimée (deux voies : gabarit dynamique par caisse et gabarit statique à
quatre colonnes du module Documents) et la part financière des trois lectures de la Direction (vue d'ensemble, répartition,
absences) lisaient les mêmes tables (`factures`, `facture_reglements`, `seances`) avec du SQL dispersé dans `platform`. Trois
défauts d'argent ou de sécurité en découlaient : aucune isolation de centre (KB-FAC-52), une voie documents sans contrôle de profil
(KB-FAC-60), et deux bases de calcul du chiffre d'affaires (KB-FAC-56, 57) : le tableau de bord et la Direction additionnent les
totaux **stockés** des factures (`total_ht`, `total_tva`, `total_ttc`, arrondis par facture à l'émission) alors que la synthèse
recalculait le TTC **par ligne** sans arrondi puis l'affichait arrondi à la paire (écart de 0,41 sur le jeu de février).

## Décision

1. **Le tableau de bord est une lecture du module** `contexts/facturation` (cas d'usage `ConsulterLeTableauDeBord`, port de
   lecture `LectureDuTableauDeBord`, adaptateur JDBC). `FacturationDomainService` disparaît (sa seule règle, « le mois est
   obligatoire », est dans le cas d'usage). Même URL, même JSON (noms anglais conservés par `TableauDeBordReponse`).
2. **La synthèse mensuelle** : le module lit les données (`LectureDeLaSynthese` : caisses, indicateurs, répartitions, totaux
   stockés) et imprime par le port `ImpressionDeLaSynthese` ; le **rendu Jasper reste dans `platform`**
   (`FacturationSyntheseMensuelleReportService`, gabarit dynamique construit à la volée), comme `ImpressionDuBon` en GMAO G6.
   `BarresDeRepartition` (barres de texte) et `FormatDImpression` sont dans le module et testés.
3. **Deux voies conservées** (décision utilisateur) et leurs différences reproduites : dynamique par caisse (une colonne HT et TTC
   par caisse, indicateurs, barres) et statique à 4 colonnes (CNAS, CASNOS, CAMSSP, Autres par préfixe, totaux non formatés).
4. **Sécurité (corrige KB-FAC-52 et KB-FAC-60)** : 403 si le centre de la requête ou du corps diffère de celui de la session
   (`SessionCourante`, comme F2 à F4) sur `/facturation/dashboard` et `/facturation/synthese/print` ; la voie
   `POST /documents/print` avec `SYNTHESE_FACTURATION_MENSUELLE` exige le **même profil** que `/facturation/synthese/print`
   (administrateur ou secrétaire ; le médecin seul, l'infirmier, tout autre rôle et un compte sans rôle sont refusés) et le même
   contrôle de centre. Les autres types de document de `/documents/print` ne changent pas.
   **Amendement 2026-10-21 (revue F5/F6, M1 et m1).** (a) `POST /documents/print/{modeleId}` appliquait ni profil ni centre :
   quand le **type du modèle chargé** (jamais un paramètre du client, `trim` + majuscules) est la synthèse, il applique la même
   garde de profil, le même contrôle de centre (403) et pose les mêmes totaux enregistrés que `/documents/print`. (b) La règle de
   profil est écrite une seule fois, `ProfilDeLaSynthese` (langage publié) : **le rôle ADMIN ou le rôle SECRETAIRE, même pour un
   compte à plusieurs rôles** ; `@PreAuthorize` de `/facturation/synthese/print` (qui autorisait MEDECIN, le médecin seul étant
   refusé plus loin par `RoleScopeFilter`) et la garde des deux voies documents l'utilisent. Écart observable : un compte
   MEDECIN + INFIRMIER recevait 200 sur `/facturation/synthese/print` et 403 sur la voie documents ; il reçoit 403 partout
   (KB-FAC-73). `/facturation/dashboard` garde `ADMIN`, `MEDECIN`, `SECRETAIRE` (hors périmètre de l'impression).
5. **Totaux stockés = base unique** (décision utilisateur) : les « Montant HT / TVA / TTC » de la synthèse (deux voies) sont la
   somme de `total_ht`, `total_tva`, `total_ttc` des factures de la période, comme le tableau de bord et la Direction. Elles
   comptent donc aussi les factures sans ligne. Dans le tableau forfait x caisse, le HT d'une ligne reste `line_ht` et son TTC est
   **sa part du TTC stocké** de la facture (`line_ht x total_ttc / total_ht`, zéro si le HT stocké est nul) : la somme des lignes
   d'une facture redonne son TTC stocké sans recalcul de TVA. **Écart de comportement sur des chiffres affichés.**
6. **Lecture publiée pour la Direction** : `publishedlanguage/SyntheseFinanciereDesCentres` (chiffres par centre : factures, CA HT
   et TTC, encaissé ; CA par mois ; CA HT par caisse). Les trois services Direction restent dans `platform` avec leur endpoint,
   leurs alertes et leur garde de société, et ne changent que leur source financière (les parties patients, anémie, séances,
   motifs ne sont pas touchées). Le langage publié ne contient que des types simples (aucun modèle interne). Pas d'ACL à
   traduire : l'interface est consommée telle quelle.
7. **Reproduits, à trancher avec la direction** (non décidés) : CA par caisse (agence figée sur la facture) contre séances par
   caisse (payeur actuel du patient, KB-FAC-65) ; encaissé de la Direction sur les règlements de toutes dates (KB-FAC-62) ;
   séances facturées contre somme des répartitions (KB-FAC-54) ; erreurs de protocole en 500 (KB-FAC-53, 59) ; échelle JSON
   hétérogène (KB-FAC-64) ; PDF vide sans facture (KB-FAC-58).
8. **Aucune migration** : mêmes tables, mêmes colonnes. Pas de façade ni de routage (ADR 0024) ; retour arrière = commit précédent.

## Conséquences

- Le tableau de bord, la synthèse (deux voies) et la Direction partagent la même base de montants ; la synthèse affiche
  désormais un « Montant TVA » (**les deux gabarits**, dynamique et statique `synthese_mensuelle_facturation.jrxml`) et les montants globaux deviennent ceux des factures. À valider avec la direction
  et la comptabilité avant déploiement ; contrôles SQL dans `verifications-production.md` (point 21).
- Le frontend doit gérer le 403 de centre et le profil de la synthèse (voir la fiche de slice).
- `bc-facturation` ne contient plus de classe Java : le module Maven lui-même est retiré en F6.
