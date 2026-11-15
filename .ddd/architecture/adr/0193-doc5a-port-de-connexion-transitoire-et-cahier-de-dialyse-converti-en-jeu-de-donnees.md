---
status: accepted (livré le 2026-11-15, slice DOC5a, décision D1 de l'utilisateur : « port de connexion puis conversion »)
date: 2026-11-15
decision-makers: [utilisateur, architecte]
context-scope: documents, seance, stock, dossier-medical, platform (gardes)
related: ADR 0024 (refactoring sur place), 0099 (E-R3), 0140 (D3, D7), 0144 (cahier), 0162 à 0166 (Documents), 0192 ; `doc5a-realisation.md` ; `inventaire-platform-restant-v2.md` ligne 7 (DOC5)
---

# 0193 - DOC5a : un port de connexion transitoire pour les gabarits à SQL embarqué, et le cahier de dialyse converti en jeu de données

## Contexte
12 des 14 gabarits livrés embarquent une requête SQL qui lit les tables de plusieurs contextes (ADR 0140 D3 l'interdit en principe ; ADR 0144 D1 le tolérait pour le cahier). Le moteur (`JasperReportService`) tenait un `DataSource` brut. Le cahier lisait, dans un seul `SELECT`, `seances`, `volet_paramedical`, `volet_medical`, `dossier_medical_patient`, `prescriptions_medicales`, `forfait`, `prise_en_charge`, `stock_movements` et `articles` (six contextes). Butoir daté : 2027-06-30 (garde `TableDesSeancesTest`, F-R8).

## Décision 1 : un port de connexion transitoire (documents)
`application/ConnexionDeRendu` (`avecLaConnexion(travail)`) remplace le `DataSource` du moteur ; l'adapter `ConnexionDeRenduDeLaBase` ouvre une connexion du pool, la passe en LECTURE SEULE le temps du travail, la rend dans son état d'origine et la referme (y compris sur échec). Le moteur et la plateforme ne connaissent aucune table. Un gabarit qui reçoit ses lignes (`LIGNES` : rapport de la direction, synthèse, cahier) n'ouvre aucune connexion. Le port DISPARAÎT avec le dernier gabarit à SQL embarqué (11 aujourd'hui). Pas de garde ArchUnit datée supplémentaire : aucune garde de ce type n'existait pour le `DataSource` ; la garde existante par contexte (`ArchitectureHexagonaleTest` : l'application ne dépend pas de l'infrastructure) couvre le port.

## Décision 2 : le cahier de dialyse devient un jeu de données de la Séance
Le gabarit `cahier_dialyse.jrxml` perd sa requête (`queryString`). La Séance, propriétaire du cahier, compose ses lignes (`ComposerLesLignesDuCahier`, application) par ports et les fournit sous `LIGNES` (ACL `CahierRenduParDocuments`, traducteur table-driven `champs`). Sources :
| Donnée | Propriétaire | Voie |
|---|---|---|
| séances imprimables, forfait imposé, volets paramédical et médical | Séance | `SeancesImprimablesDuCahierJdbc` (ses trois tables) |
| forfait de la prise en charge ; forfait du référentiel (code, nom d'un forfait imposé) | Patient et couverture | ports existants `ForfaitDeLaPriseEnCharge`, `ForfaitsDuCentre` |
| sorties de stock valorisées | Stock | NOUVEAU langage publié `SortiesValoriseesDesSeances` (la sous-requête de l'ancien gabarit, mot pour mot, restreinte à une séance) |
| dossier de base, prescription en vigueur (EPO, fer) | Dossier médical | NOUVEAU langage `seance.v1` `DossierEtTraitementDuCahier`, FOURNI par le Dossier médical (il dépend de la Séance, comme `PoidsSecDesPrescriptions`, ADR 0180) |
La mise en forme de la requête (tirets, virgule décimale, libellés, concaténation EPO/fer) est reprise en Java, testée table par table. Le dossier, la prescription et le volet médical ne sont pas lus pour un profil sans droit (KB-PL5-4).

## Pourquoi un langage Stock distinct de `ConsommationsDeSeance.historique`
L'historique lit aussi les consommations imputées sur la sortie pour séances (sans mouvement) : le cahier n'a jamais imprimé que les mouvements. Réutiliser l'historique aurait CHANGÉ le cahier d'un centre en mode « sortie pour séances » : refusé (KB-DOC5A-2, reproduit).

## Parité
Filet écrit AVANT sur l'ancien code (`doc5a/cahier` : tout le texte du PDF, 5 séances dont une non imprimable, dossier, trois prescriptions successives, EPO et fer, articles à deux prix, entrée et ajustement qui annulent des sorties, surcharge avec et sans code, trois profils dont celui sans volet médical ; patient nu), approuvé sur l'ancien code, puis vert à l'identique sur le nouveau. Goldens PL5 et SE7e du cahier, `CahierDialyseReportIntegrationTest` (rempli désormais par les lignes composées), validateur, compilation des 14 gabarits : inchangés et verts.

## Transition du validateur « requête identique » (RG-DOC-004) et modèles stockés
Un modèle personnalisé déjà stocké qui porte l'ancienne requête reçoit les mêmes lignes (la source de données prime sur la requête ; les noms de champs sont inchangés) : rendu inchangé, vérifié par test. Seul un NOUVEAU téléversement du cahier avec l'ancienne requête est refusé (`QUERY_MODIFIED`) : conséquence directe de D1, consignée KB-DOC5A-1 (à confirmer métiers). Aucune règle « accepter l'ancienne requête » (garder 98 lignes de SQL mort dans le dépôt).

## Gardes datées
- `TableDesSeancesTest` : entrée du cahier SUPPRIMÉE (butoir 2027-06-30 soldé, aucun report) ; la liste blanche est vide.
- `TableDuDossierMedicalTest` : entrée du cahier SUPPRIMÉE (butoir 2027-03-31 soldé, aucun report) ; reste l'ordonnance.
- Autres entrées (ordonnance, F-R2..F-R7) : inchangées, aucune date reportée.

## Conséquences
- 11 gabarits à SQL embarqué restent (bon_intervention, inventaire_stock, planning_presence_infirmiers, planning_optimise, 6 gabarits patient, ordonnance) ; méthode éprouvée : filet de rendu sur l'ancien code, ports du contexte propriétaire, mise en forme en Java, retrait de la requête, retrait de l'entrée de garde. Prochain : `bon_intervention` (une requête de 17 lignes sur les tables de la GMAO).
- Les 11 restent servis par `ConnexionDeRendu`.
- Aucun changement de route, de droit, de code d'erreur ni de rendu ; `seamAllowed` reste vide ; aucun fichier legacy de la plateforme modifié (hors tests de garde).
