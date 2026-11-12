---
status: accepted
date: 2026-11-12
decision-makers: [utilisateur, architecte]
context-scope: facturation, documents, seance, platform
related: ADR 0024, 0128 (RF3c-J1), 0140 (D3), 0145, 0146 ; `.ddd/migration/reviews/pl8-filet.md`, `pl8-realisation.md` ; `.ddd/safety-net/known-bugs.md` KB-PL8-1 à 7, KB-RF4-02
---

# 0147 - PL8 : la synthèse mensuelle est rendue par Documents avec les données de la Facturation ; corrections KB-PL8

Refactoring sur place (ADR 0024) : mêmes tables, mêmes URL (`POST /api/v1/facturation/synthese/print`, `POST /api/v1/documents/print` pour le type `SYNTHESE_FACTURATION_MENSUELLE`), mêmes rôles. Pas de flag ; retour arrière = revert du commit.

## Décisions
1. **La Facturation fournit ses données (ADR 0140 D3)** : `SyntheseMensuelle.lignes` (forfait x code de caisse normalisé, HT, part du TTC). La caisse de l'agence figée sur la facture vient de l'annuaire `PayeursDuCentre` (port `CaissesDesAgences`, ACL `infrastructure/acl/patientcouverture`), sans jointure sur `agence` / `caisse_assurance` : RF3c-J1 est soldé POUR LA SYNTHÈSE.
2. **Rendu par `documents.v1`** : ACL `SyntheseRendueParDocuments` (Facturation) -> `RenduDeDocument` ; paramètres et lignes (`ModeleDeLaSynthese`, clé `LIGNES` servie au gabarit comme source de données par le moteur transitoire). Le gabarit `synthese_mensuelle_facturation.jrxml` (platform jusqu'à DOC2) n'a plus de requête (tableau croisé à colonnes dynamiques). Voie `/documents/print` : mêmes données, classées en CNAS / CASNOS / CAMSSP / Autres (KB-RF4-02 soldé) avec le graphique (`AVEC_GRAPHIQUE`). Supprimés : `FacturationSyntheseMensuelleReportService`, `CaissesDesAgences`, `CaissesDesAgencesParLAnnuaire` (platform) ; règles ArchUnit empêchant leur retour et le SQL dans le gabarit.
3. **Excel** : classeur POI produit par la Facturation, hors moteur de mise en page (KB-PL8-4).
4. **Corrections décidées le 2026-11-12** : KB-PL8-1 (troncature), 2 (code normalisé, « Inconnu » unique), 4 (Excel tabulaire), 5 (tableau cohérent avec le TTC enregistré ; séances au statut `FACTUREE` pour la synthèse seulement, nouvelle lecture `nombreParFactureAuStatutFacturee` de `seance.v1`, le tableau de bord inchangé), 6 (400/405/415 propres, date en entier refusée), 7 (action `SYNTHESE_FACTURATION_IMPRESSION`, libellé = période et format). **Reproduits** : KB-PL8-3 (police, DOC2), KB-FAC-59 (500 texte d'impression), date blanche lue comme 1970-01-01, date-heure tronquée, période illisible sur la voie documents = 500, arrondi à la paire.
5. Tri du tableau et des répartitions en Java (ordre des caractères) : indépendant de la collation du SGBD.

## Conséquences / à confirmer par les métiers avant le 2026-12-15
- Mise en page : tableau croisé sur A4 paysage (au-delà de 4 caisses il se coupe horizontalement au lieu d'élargir la page) ; séparateur de milliers du moteur (« 1 150,45 ») ; en-têtes sur deux lignes ; le graphique de la voie documents occupe une bande réservée.
- Critère « séance valide » (`FACTUREE`) ; somme des cellules arrondies pouvant différer de quelques centimes du « Montant TTC » enregistré.

## RF3c-J1 : ce qui reste (slice dédiée PL8b, butoir 2027-03-31)
Entrées de `TableDesPayeursTest.LECTEURS_CONNUS` (3) : `LectureDuTableauDeBordJdbc` (jointures non bornées au centre, KB-FAC-65), `SituationsDesFacturesJdbc` (COALESCE instantané / payeur courant, filtres et pagination sur la chaîne), `SyntheseFinanciereDesCentresJdbc` (somme par caisse de plusieurs centres, KB-RF3-07). Prérequis : lecture publiée groupée de la chaîne des payeurs d'une facture, preuve de parité sous PG16.
