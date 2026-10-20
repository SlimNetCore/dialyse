---
status: accepted
date: 2026-10-20
deciders: utilisateur (décisions F3 du 2026-10-20)
---

# ADR 0032 — Facturation : numérotation atomique, refus d'émission et contrat de l'événement de validation

## Contexte

La validation de facturation (`POST /api/v1/facturation/validate`) lisait la séquence de numérotation, calculait
`séquence + 1`
puis l'écrivait, sans verrou : deux validations simultanées d'un centre pouvaient prendre le même numéro (KB-FAC-24). Un
doublon de numéro (séquence en retard sur des factures existantes, modèle sans `{SEQ}`) échouait sur la contrainte
unique avec
un 500 (KB-FAC-25). L'événement `FacturationValideeEvent` annonçait un total TTC toujours nul, la période du corps de la
requête (et non celle réellement facturée) et était publié même sans facture émise (KB-FAC-27/28/29). Des séances sans
forfait étaient
facturées à zéro sous un libellé « Forfait non defini » (KB-FAC-32).

## Décision

1. **Réservation atomique de la séquence** (`SequenceDeFacturation.reserverLaSuivante`, adapter
   `SequenceDeFacturationJdbc`) :
   `UPDATE facture_sequence SET seq_value = seq_value + 1 WHERE center_id = ? AND seq_year = ?` puis lecture de la
   valeur dans
   la même transaction. L'`UPDATE` prend le verrou de la ligne jusqu'à la fin de la transaction : une émission
   concurrente du
   même centre attend le commit (ou le rollback) de la première, puis incrémente la valeur à jour. Aucun verrou propre à
   un
   SGBD (pas d'advisory lock), même code sur H2 et PostgreSQL 16. La ligne d'une année nouvelle est créée à 0 avant
   l'incrément (`ON CONFLICT DO NOTHING` sur PostgreSQL, qui évite l'erreur si deux transactions la créent ensemble).
   Option
   écartée : `SELECT ... FOR UPDATE` (même effet, une requête de plus, et rien à verrouiller pour une année nouvelle).
   Preuve : `SequenceConcurrenteContrat` (H2 et PostgreSQL 16) échoue si l'on remplace l'incrément atomique par un
   « lire puis écrire ».
2. **Numéro déjà pris** : l'émission vérifie le numéro réservé (`Factures.numeroDejaPris`) avant l'insertion, et
   l'adapter
   traduit la violation de la contrainte `UNIQUE (center_id, numero_facture)` (V1) en `NumeroDeFactureDejaPris` :
   réponse **409**
   `NUMERO_DE_FACTURE_DEJA_PRIS`, transaction annulée (séquence restituée), aucun événement. Aucune contrainte nouvelle
   en base (celle de V1 existe déjà) ; les contrôles de production sont au point 19 de `verifications-production.md`. Un
   modèle sans
   `{SEQ}` est déjà refusé à la saisie des réglages (F1, 400) ; un modèle posé hors saisie donne le 409 à la deuxième
   facture.
3. **Séances sans forfait** : la validation est refusée (**422** `SEANCES_SANS_FORFAIT`, avec le nombre de séances) tant
   qu'une
   séance éligible n'a ni surcharge ni prise en charge ; aucune facture n'est créée (le contrôle précède toute
   réservation de
   numéro). La simulation (F2) garde l'affichage « Forfait non defini ».
4. **Contrat de l'événement `FacturationValideeEvent`, version 2**, langage publié du contexte
   (`contexts/facturation/publishedlanguage`) : `centre`, `debut` et `fin` (période réellement facturée : celle du mois,
   qui
   l'emporte, ou des dates, qui ont choisi les séances et les factures), `factures` (identifiant, numéro, HT, TVA, TTC
   de chaque
   facture émise), `seancesFacturees`, `totalTtc` (somme exacte), `survenueLe`. **Aucun événement** quand aucune facture
   n'est
   émise. Publié dans la transaction de la validation par un port (`PublicationDeLaValidation`) ; l'adapter utilise le
   bus
   d'événements de l'application et la Comptabilité le reçoit **après le commit** (ADR 0027), sans file de messages :
   l'outbox (ADR 0013) reste la cible quand un consommateur hors processus existera.
5. **Consommateur Comptabilité** : `FacturationValideeComptabiliteListener` lit `centre`, `debut`, `fin` (mêmes appels à
   `ComptabilisationDesVentesEtReglements.facturationValidee`) : les factures émises ont exactement la période de
   l'événement,
   les écritures de ventes sont les mêmes qu'avant pour les cas nominaux. Les factures non comptabilisées (mois
   clôturé...) se
   régénèrent par `POST /api/v1/comptabilite/rebuild`, qui n'est plus déclenché par une validation vide.
6. **Auteur de l'émission** (KB-FAC-30) : colonne `factures.emise_par UUID` nullable, migration additive
   `V40__facturation_auteur_de_l_emission.sql` ; elle reçoit l'utilisateur de la session (le `userId` du corps reste
   exigé
   mais n'est plus la source de l'auteur ; `previewGeneratedAt` reste ignoré).
7. **Accès** : route réservée à `ADMIN` ; le centre du corps doit être celui de la session (403 sinon, comme F2).
8. **Reproduits** (« correction à la livraison suivante ») : année de la séquence et du modèle = année du jour
   (KB-FAC-23),
   deuxième facture possible pour la même période et le même patient (KB-FAC-26), pas d'avoir (KB-FAC-31), date de
   facture =
   jour de validation et date d'écriture = fin de période, mois comptable clos = facture sans écriture (KB-FAC-36),
   erreurs de
   protocole en 500 (KB-FAC-35). La TVA est celle en vigueur à la **fin de la période** facturée (KB-FAC-22 corrigé,
   comme F2),
   lue par `ParametrageFiscal` (ADR 0030).

## Conséquences

- Les validations d'un même centre se sérialisent sur sa ligne de séquence ; un centre n'est pas bloqué par un autre.
- Un numéro réservé par une transaction annulée n'est jamais perdu (pas de trou) ; une séquence en retard produit un 409
  tant
  qu'elle n'a pas rattrapé les numéros existants (point 19.2 de `verifications-production.md`).
- Le frontend doit gérer 409, 422 et 403 (voir la fiche de la slice) ; le consommateur de l'événement n'a plus à
  interroger la
  base pour connaître les montants.
- Le contrat d'événement v2 casse le type Java de l'ancien événement (`FacturationValideeEvent` du module
  `bc-facturation`,
  supprimé) : seul consommateur, la Comptabilité, est adapté.
