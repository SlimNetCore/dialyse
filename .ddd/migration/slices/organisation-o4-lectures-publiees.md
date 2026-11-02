---
slice: organisation-o4-lectures-publiees
status: planned
date: 2026-11-02
order: 4
context: organisation
sources: [.ddd/tactical/organisation.md, ADR 0063]
---

# O4 — Lectures publiées et retrait du legacy Organisation

> **Constat au 2026-11-02 (ne pas lire la suite comme un fait)** : `organisation.v1` n'a AUCUN consommateur. Les 7 types de
> `publishedlanguage/` (`CentresActifs`, `MachineReference`, `MedecinReference`, `ProtocoleReference`, `SalleOuverte`, `SalleFermee`,
> `CreneauCree`) ne sont importés par aucun autre module ; les événements ne sont jamais émis. 30 sources hors du contexte (dont l'admin générique, ADR 0070) lisent les
> tables de l'Organisation en SQL direct (liste blanche datée dans `TableDeLOrganisationTest`, qui échoue si un lecteur est migré sans
> que l'entrée soit retirée, ou si un nouveau lecteur apparaît). La slice est donc `planned` : elle n'est pas implémentée ici (décision
> de l'utilisateur : constater et consigner). Le texte ci-dessous est l'intention d'origine.

Tous les consommateurs (Planification, Stock, Seance, Facturation, GMAO, Pilotage) passent par organisation.v1. Retrait
de bc-referential pour l'organisation. Garde « pas de SQL sur les tables d'Organisation hors contexte » (modèle
TableDuStockTest).