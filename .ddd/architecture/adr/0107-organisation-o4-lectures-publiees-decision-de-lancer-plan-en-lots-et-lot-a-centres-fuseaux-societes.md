---
status: accepted
date: 2026-11-09
review-by: 2026-12-15
decision-makers: [utilisateur (demande explicite de réaliser O4, 2026-11-09), architecte]
context-scope: organisation (publishedlanguage), absences-patients, dossier-medical, seance, patient-couverture, planification, pilotage-direction, plateforme
related: ADR 0024 et 0075 (refactoring sur place), 0063 (Organisation), 0070 (admin générique), 0094 (calendrier du centre, R1), 0098 et 0104 (ports `*Fournis`), 0102 à 0106 (modèle du Pilotage) ; `.ddd/migration/slices/organisation-o4-lectures-publiees.md`, `.ddd/safety-net/organisation-o4-lectures-publiees.md` (audit)
---

# 0107 - Organisation O4 : décision de lancer, plan en lots, lot A (centres, fuseaux, sociétés)

Refactoring SUR PLACE (ADR 0024/0075) : mêmes tables, mêmes URL, mêmes rôles, mêmes codes ; ni façade, ni flag, ni projection, ni métrique, ni
événement. Les points marqués « à confirmer » le sont par les métiers / DPO au plus tard le **2026-12-15**.

## Contexte
La fiche O4 disait « constater et consigner » (2026-11-02) : `organisation.v1` n'avait aucun consommateur ; 32 sources hors du contexte (le chiffre
« 30 » de la fiche était périmé) lisent les tables d'Organisation en SQL, inscrites dans la liste blanche de `TableDeLOrganisationTest`.

## Décisions
1. **O4 est réalisée**, par lots (demande explicite de l'utilisateur, 2026-11-09). Statut `in-progress`. Critère d'avancement : le nombre
   d'entrées de la liste blanche (test dédié) : 32 -> 22 au lot A.
2. **Plan** : A centres et sociétés (fait) ; B Stock via ports `*Fournis` + port de verrou ; C libellés salle / créneau / médecin ; D ressources et séries
   du planning (dont l'écriture de l'isolement des salles) ; E identité imprimée et admin générique ; 7 lecteurs d'auth, comptes, garde d'accès et
   provisionnement restent des résiduels NOMINATIFS (propriétaire hors Organisation : Identité et accès, Documents).
3. **Preuve du graphe de modules** (lecture des `pom.xml`, puis réacteur qui refuse tout cycle) : `contexts-organisation` dépend de shared-kernel, stock et
   gmao, et d'aucun autre contexte. Absences, Dossier médical, Séance, Patient-couverture, Planification, Pilotage et la plateforme peuvent donc dépendre
   de `contexts-organisation` (ajouté aux `pom.xml`, réacteur construit, aucun cycle). Stock et GMAO ne le peuvent pas : Stock passera par des ports
   `*Fournis` (lot B). Dans le code de production, seul le paquet `publishedlanguage` d'Organisation est importé (ArchUnit des consommateurs).
4. **Lot A, langage publié `organisation.v1`** (noms métier, sans suffixe technique) : `AnnuaireDesCentres` (`actifs` : actif ou état non renseigné, par
   identifiant ; `tous` ; `fuseauHoraireDe` : `Optional<ZoneId>`, vide si centre inconnu, fuseau absent, blanc ou illisible) et `AnnuaireDesSocietes`
   (`societesActives`, `societeDuCentre`, `societe` avec ses centres par nom). Le record `CentresActifs` existant ne convient pas (un `(id, nom)` et
   non un port) : conservé, inutilisé (O4-R6). Implémentation JDBC dans Organisation, SQL repris à l'identique de chaque lecteur retiré.
5. **Chaque consommateur garde SON choix** : le défaut de fuseau (Alger pour Planification, Patient-couverture, Séance et la plateforme ; UTC pour Stock au lot B)
   reste dans le consommateur, car le langage publié ne renvoie pas de défaut. Les définitions divergentes de « centre actif » (Absences et Planification :
   NULL = actif ; Dossier médical et Séance : tous les centres) sont conservées via `actifs()` et `tous()`.
6. **Lot A, périmètre réel : 10 lecteurs migrés** (Absences, Planification x2, Dossier médical, Séance x2, Patient-couverture, plateforme, Pilotage x2).
   **Réduction assumée** : les 2 lecteurs de centres de Stock (et 2 autres de Stock) ne sont pas migrés : cycle Maven et écart `actif = TRUE` strict (NULL exclu)
   qui demande une décision (O4-R3).
7. **Plateforme** : `JourDuCentreLegacy` garde son nom (plus aucun SQL). Le renommer exigerait un nouveau fichier hors `covered.json` : bloqué par le hook
   de sécurité legacy, sans seam inscrit (accord de l'utilisateur requis, O4-R1). `seamAllowed` reste `[]`.
8. **Filet et parité** : contrat `AnnuairesContrat` (9 scénarios) exécuté sur un faux en mémoire et sur H2 ; tests des consommateurs adaptés
   UNIQUEMENT dans la construction de l'objet testé (ex-lecteur remplacé par le traducteur câblé sur l'adapter JDBC) ; assertions inchangées ; aucun
   `.approved` modifié ; aucun écart de comportement, donc ni golden `_nouveau_code`, ni archive `_ecarts-legacy`, ni nouveau KB.

## Conséquences et risques
- Le Dossier médical (et la Séance) lisent désormais tous les centres via Organisation : un centre ajouté est visible aux contrôles de nuit comme avant.
- Trois lecteurs migrés n'avaient pas de test direct avant migration (voir fiche, réserve) : parité fondée sur l'identité du SQL et sur le contrat.
- Le contrat n'est pas encore exécuté sur PostgreSQL 16 réel (O4-R7).
