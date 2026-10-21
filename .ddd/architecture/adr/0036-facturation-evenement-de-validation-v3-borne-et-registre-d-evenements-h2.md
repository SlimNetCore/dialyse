---
status: accepted
date: 2026-10-21
deciders: utilisateur (correctif KB-FAC-72, 2026-10-21)
---

# ADR 0036 — Événement de validation v3 de taille bornée ; table `event_publication` en H2

## Contexte

La validation d'une facturation échouait en développement (H2) : `Valeur trop longue pour la colonne "SERIALIZED_EVENT CHARACTER
VARYING(4000)"`. Le registre de publication d'événements Spring Modulith (ADR 0013) enregistre l'événement dans la transaction ; l'événement
v2 (ADR 0032) listait chaque facture (id, numéro, trois montants), soit environ 200 octets par facture (10 Ko pour 50 factures), et croissait
sans borne. En production, V35 crée la colonne en `TEXT` : pas de limite, mais un événement qui grossit avec le volume n'a pas de raison d'être.
Le seul consommateur (`FacturationValideeComptabiliteListener` -> Comptabilité) n'utilisait que centre, début et fin : la comptabilisation
des ventes relit déjà en base les factures de la période du centre sans écriture, de façon idempotente.

## Décision

1. **H2 / tests** : la table `event_publication` est créée par `db/schema.sql` (mêmes colonnes que le schéma officiel Modulith v2,
   `serialized_event VARCHAR(1000000)`, sans l'index hash propre à PostgreSQL) ; l'initialisation de schéma de Modulith est désactivée dans
   `application.yml`, comme dans `application-prod.yml` (V35, inchangée). Les tests des modules `contexts/*` gardent l'initialiseur de Modulith (petits événements).
2. **Événement v3** : `FacturationValideeEvent(centre, debut, fin, nombreDeFactures, seancesFacturees, totalTtc, survenueLe)`. La liste
   `FactureEmise` est retirée. Aucun identifiant d'émission, aucune colonne : le consommateur relit les factures de la période du centre sans
   écriture, ce qui produit les mêmes écritures qu'avec la liste (le consommateur ne l'utilisait pas). Idempotent : un rejeu par le registre ne double rien.
3. Compatibilité : une publication v2 encore incomplète dans le registre se relit (champ `factures` ignoré, `nombreDeFactures` à 0) et reste traitée à l'identique.

## Conséquences

- Taille de l'événement indépendante du nombre de factures (test à 300 factures).
- Le nom de la classe est conservé ; la version 3 est portée par cet ADR et la Javadoc (pas de champ `version`).
- Un nouveau consommateur qui aurait besoin du détail relit les factures par la lecture publiée de la Facturation (ADR 0034), pas par l'événement.
