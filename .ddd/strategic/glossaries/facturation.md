---
status: validated
date: 2026-10-09
context: Facturation (supporting)
sources: [.ddd/discovery/glossary.md, .ddd/discovery/event-storming.md §6, réponses Q1/Q5/Q9]
---
# Glossaire — Facturation

| Terme métier | Définition | Identifiant cible | Nom legacy | Synonymes à bannir | Règle / exemple | Statut |
|---|---|---|---|---|---|---|
| Séance facturable | Séance réalisée, non facturée, dans la période, couverte, prise comme ligne de facture | `SeanceFacturable` (projection) | lecture SQL `seances` | séance (seul) | signature médecin non exigée (RG-FAC-020) | proposed |
| Exclusion de facturation | Retrait d'une séance de la facture depuis la simulation | `SeanceFacturable.exclure(motif)` / `SeanceExclueDeFacturation` | statut `ABSENT` | absent, absence | RG-FAC-023 ; renommage décidé (Q1) | proposed |
| Simulation de facturation | Aperçu des factures sans enregistrement | `SimulationFacturation` | `preview` | — | — | proposed |
| Validation de facturation | Émission atomique des factures d'une période ; fige prix, TVA, payeur | `Facturation.valider()` / `FacturationValidee` | `validate`, `FacturationValideeEvent` | valider (seul) | échoue si une séance n'est plus éligible (RG-FAC-030) | proposed |
| Facture | Document figeant patient, payeur, période, forfaits et TVA | `Facture`, `LigneFacture` | `FactureAggregate` | — | numéro `FAC-{ANNEE}-{SEQ}` | proposed |
| Forfait appliqué | Prix HT retenu : surcharge > PEC effective > PEC demandée | `ForfaitApplique` | — | tarif | RG-FAC-021 | proposed |
| Payeur | Organisme qui paie la facture (caisse, agence, centre payeur) | `Payeur` | `payeurs`, `centre_payeur` | centre (seul) | centre payeur ≠ centre de dialyse (H13) | proposed |
| Caisse d'assurance / Agence | Organisme payeur et son antenne | `CaisseAssurance`, `Agence` | `caisse_assurance`, `agence` | — | — | proposed |
| Type de TVA | Taux par période de validité et type de prestation | `TypeTva` | `TypeTVA` | `RegleTVA` | sans type actif : 0 % | proposed |
| Règlement | Paiement (ou remboursement si négatif) rattaché à une facture | `Reglement` / `ReglementEnregistre` | `FacturePayment`, `payment` | paiement | — | proposed |
| Situation de facture | Non réglée / partiellement réglée / réglée / trop-perçu | `SituationFacture` | `NON_REGLEE`… | — | — | proposed |
| Clôture mensuelle de facturation | Arrêt du mois : plus aucune facture ni exclusion sur la période | `PeriodeFacturation.cloturer()` / `PeriodeDeFacturationCloturee` | — (à créer) | clôture (seul) | ADR 0009 | proposed |
