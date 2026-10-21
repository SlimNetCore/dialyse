---
status: accepted
date: 2026-10-21
deciders: utilisateur (décisions F4 du 2026-10-21) ; règles métier marquées « à valider par le métier »
---

# ADR 0033 — Facturation : règlements (mode, remboursement, idempotence, auteur) et événement v2

## Contexte

Le legacy enregistrait un règlement (`POST /api/v1/reglements/{factureId}/paiements`) sans contrôle de centre (KB-FAC-37), sans
mode de règlement ni référence (KB-FAC-40), sans idempotence (KB-FAC-39), avec l'auteur lu dans le corps (KB-FAC auteur) et un
remboursement (montant négatif) accepté sans limite (KB-FAC-42/43) puis comptabilisé comme un encaissement en valeur absolue
(KB-FAC-47). L'événement `FactureReglementRecordedEvent` ne portait pas l'identifiant du règlement : la Comptabilité le retrouvait
par ses valeurs (facture, date, montant, auteur).

## Décision

1. **Mode de règlement** (ajout, à valider par le métier) : `BANQUE`, `CHEQUE`, `CAISSE`, `ESPECES`, facultatif, avec une
   **référence de paiement** libre (≤ 120 caractères). Colonnes nullables `mode_reglement`, `reference_paiement`
   (migration additive V41). Un règlement **sans mode** (anciens règlements, requêtes qui n'en envoient pas, reprise de données)
   reste comptabilisé comme avant : banque, journal `BQ`. Correspondance (Comptabilité, opérations `REGLEMENT_BANQUE` /
   `REGLEMENT_CAISSE`) : `BANQUE`, `CHEQUE`, absent → banque, compte `512`, journal `BQ` ; `CAISSE`, `ESPECES` → caisse, compte de
   caisse du mapping, journal `CA`. L'export et le tableau de bord ne changent pas.
2. **Remboursement** : un règlement négatif est un remboursement. À l'enregistrement, il est refusé (**422**
   `REMBOURSEMENT_SUPERIEUR_AU_TOTAL_REGLE`) s'il dépasse le total déjà réglé de la facture (somme signée, y compris le trop-perçu).
   Le trop-perçu reste accepté (KB-FAC-41 reproduit) ; la situation reste la lecture calculée de la somme signée. Côté Comptabilité,
   un montant négatif produit l'écriture de **sens inverse** (débit du compte client, crédit de la banque ou de la caisse) au lieu
   de la valeur absolue en encaissement (KB-FAC-47 corrigé, décision déjà actée dans le tactical de la Comptabilité, B6).
3. **Idempotence** : **clé facultative dans le corps** (`cleIdempotence`, ≤ 120 caractères), plus simple pour le frontend qu'un
   en-tête. Un renvoi pour la **même facture et le même centre** avec la même clé ne crée ni règlement ni événement : il répond la
   situation courante de la facture (le contenu du renvoi est ignoré). Sans clé, comportement d'avant (doublon possible, reproduit).
   Garanties : (a) **verrou de la facture** (`SELECT ... FOR UPDATE`, H2 et PostgreSQL) avant tout contrôle, donc deux
   enregistrements simultanés s'exécutent l'un après l'autre (idempotence et contrôle du remboursement sans course) ; (b) index
   unique `(center_id, facture_id, cle_idempotence)` en filet (les valeurs NULL ne s'excluent pas, portable H2 et PostgreSQL).
4. **Auteur** : l'utilisateur de la session (`saisi_par` reçoit son identifiant) ; le `userId` du corps est ignoré ; `system` si
   la session ne le donne pas. **Centre** : 403 si le centre du corps ou de la requête diffère de celui de la session, en
   écriture comme en lecture (comme F2, F3).
5. **Événement v2** (`publishedlanguage/FactureReglementRecordedEvent`) : ajoute `reglementId` et `mode` ; le montant reste le
   montant saisi non arrondi et **signé** (KB-FAC-46 reproduit). La Comptabilité comptabilise « source = règlement » (idempotent
   par identifiant) ; `POST /comptabilite/rebuild` relit `facture_reglements` (montant signé, mode). La forme d'avant F4 (sans
   identifiant, recherche du règlement par ses valeurs) est **retirée** (revue du 2026-10-21) : elle n'avait plus de producteur et
   les événements ne sont pas persistés, aucun événement d'avant F4 ne peut être en attente.
   **Garantie de livraison : au plus une fois.** L'écouteur est en mémoire, après le commit
   (`@TransactionalEventListener(AFTER_COMMIT)`) ; un arrêt entre le commit et l'écouteur laisse un règlement sans écriture, sans
   signal ; seul `POST /comptabilite/rebuild` le rattrape. Les consommateurs restent idempotents (par `reglementId`). L'Outbox
   prévue par le tactical reste une question ouverte.
7. **Libellé des écritures de remboursement : « Remboursement … » (décision utilisateur, 2026-10-21)**. Une écriture de
   remboursement porte « Remboursement … » (au lieu de « Règlement … » avant F4) ; écart visible dans les journaux, **gardé**.
6. **Reproduits** (correction à la livraison suivante) : date non contrôlée (KB-FAC-38), mois clos n'empêchant pas le règlement
   (KB-FAC-50), erreurs de protocole en 500 et facture introuvable en 400 (KB-FAC-44), tableau de bord (KB-FAC-45), export
   (KB-FAC-48), tris sans départage (KB-FAC-49), compte client de l'écriture (KB-FAC-51).

## Conséquences

- Le frontend a été adapté (écran des règlements : mode, référence, clé d'idempotence, traitement des 403 et 422 ; revue du
  2026-10-21) ; champs ajoutés au corps (`modeReglement`, `referencePaiement`, `cleIdempotence`) et à l'historique des
  paiements (`modeReglement`, `referencePaiement`, omis quand ils sont absents).
- Le module `bc-reglement` est retiré ; les tables, URL et DTO JSON sont conservés. Retour arrière = commit précédent ; V41 reste
  en place (colonnes ignorées par l'ancien code).
- Les règlements négatifs historiques sans règlement préalable restent en base ; les contrôles de production
  (`verifications-production.md`) les listent.
