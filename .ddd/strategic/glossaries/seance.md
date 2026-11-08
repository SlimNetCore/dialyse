---
status: validated
date: 2026-10-09
context: Séance (core)
sources: [.ddd/discovery/glossary.md, .ddd/discovery/event-storming.md §4]
---
# Glossaire — Séance

| Terme métier | Définition | Identifiant cible | Nom legacy | Synonymes à bannir | Règle / exemple | Statut |
|---|---|---|---|---|---|---|
| Séance | Acte de dialyse d'un patient, un jour donné, dans un centre, documenté au poste | `Seance` | `seances`, `Seance` | session, séance facturable | `CREE → VALIDEE → SIGNEE` sans retour (RG-SEA-001) | proposed |
| Séance programmée | Séance créée à partir du planning | `Seance` (origine `PROGRAMMEE`) | — | — | issue de `PatientPlacé` | proposed |
| Validation de séance | Constat par l'infirmier que la dialyse a eu lieu (scan QR) ; rend la séance **réalisée** | `Seance.valider()` / `SeanceValidee` | `validate`, `scanAndValidate` | « valider » seul (6 sens, H2) | jour même, sinon régularisation | proposed |
| Séance réalisée | Séance validée ou signée | `Seance.estRealisee()` | — | séance faite | — | proposed |
| Signature médicale | Visa du médecin sur la séance ; **non requis pour facturer** | `Seance.signer()` / `SeanceSignee` | `signByMedecin` | — | RG-FAC-020 confirmée (Q9) | proposed |
| Régularisation | Validation a posteriori d'une séance passée restée créée, après déverrouillage | `Seance.deverrouillerPourRegularisation()` | `unlockForRegularisation` | rattrapage | admin seul, 7 j (RG-SEA-046) | proposed |
| Dérogation hors planning | Séance réalisée hors de la place prévue, avec motif | `DerogationPlanning` | `marquerHorsPlanning` | — | motif obligatoire | proposed |
| Consommable déclaré | Article utilisé pendant la séance, déclaré par l'infirmier | `ConsommableDeclare` | `addSeanceConsommation` | consommation (≠ sortie de stock) | le stock décide des lots (FEFO) | proposed |
| Volet paramédical / médical | Données cliniques saisies pendant la séance | `VoletParamedical`, `VoletMedical` | `VoletParamedical` (434 l.) | — | à redécouper au tactique | proposed |
| Forfait surchargé | Forfait imposé sur une séance, prioritaire sur la PEC | `Seance.surchargerForfait()` | `updateForfait` | tarif | RG-FAC-021 | proposed |
| Verrou de facturation | Interdiction de modifier une séance prise dans une facturation validée | `Seance.verrouillerPourFacturation()` | statut `FACTUREE` | statut « facturée » comme étape de soin | posé sur `FacturationValidee` (ADR 0002) | proposed |
| Jour du centre | Date du jour dans le fuseau du centre ; « aujourd'hui » pour la création et le scan d'une séance | `JourDuCentre`, `HorlogeDuCentre` | `LocalDate.now()` (fuseau serveur) | date du jour serveur | KB-SEA-01 (RG-SEA-052) | proposed |
| Séance du jour ambiguë | Plusieurs séances du patient le même jour : le scan ne peut pas choisir | `SEANCE_DU_JOUR_AMBIGUE` | 500 | doublon | KB-SEA-02, 409 | proposed |
| Scan de séance | Lecture du QR du patient qui crée ou valide la séance du jour | `ScannerUnPatient` | `createFromQr`, `scanAndValidate` | pointage | RG-SEA-012 | proposed |
| Statut illisible | Statut en base hors des valeurs connues ; la ligne est ignorée de la liste | — | — | statut cassé | KB-SEA-07, requête 53.2 | proposed |
| Validation de séance (cas d'usage) | Passage de la séance de créée à validée par l'infirmier, avec place figée et consommables relevés | `ValiderUneSeance`, `Seance.validerParLInfirmier()` | `validate`, `validerParInfirmier` (retirés) | valider (seul) | RG-SEA-020, 021, 053 (SE2) | proposed |
| Signature de séance | Visa du médecin sur une séance validée | `SignerUneSeance`, `Seance.signerParLeMedecin()` | `signByMedecin` (retiré), `signerParMedecin` | — | RG-SEA-053 | proposed |
| Déverrouillage pour régularisation | Autorisation donnée par l'administrateur de valider après coup une séance créée d'un jour passé | `DeverrouillerUneSeance`, `Seance.deverrouillerPourRegularisation()` | `unlockForRegularisation` (retiré) | rattrapage | RG-SEA-046, 053 | proposed |
| Signataire | Compte enregistré comme auteur d'une validation ou d'une signature ; lu dans le corps de la requête (KB-SEA-11), 100 caractères au plus | `Signataire` | `userId` du corps | auteur, utilisateur | RG-SEA-053, risque à confirmer | proposed |
| Place de la séance | Salle, créneau et générateur où la séance a eu lieu, figés à la validation | `PlaceDeSeance`, `PlaceDuPatient` | `PlaceSeance`, `SeancePlacePort` | emplacement | RG-SEA-049 | proposed |
| Verrou des jours passés | Refus à l'infirmier de valider une séance d'un jour passé non déverrouillée (l'administrateur valide toujours) | `Seance.exigerLaRegularisation()` | `exigerDeverrouillagePourSeancePassee` (retiré) | — | RG-SEA-046, KB-SEA-12 | proposed |
| Écriture concurrente | Verrou de la séance non obtenu dans le délai : l'appel est refusé, rien n'est écrit | `EcritureConcurrente`, 409 `ECRITURE_CONCURRENTE` | — | conflit | KB-SEA-18 corrigé | proposed |
| Séance déclarée | Séance dont les consommables viennent d'être déclarés, modifiés ou retirés, avec la fiche du patient lue avant l'écriture (pour les diffusions) ; valeur interne de l'application | `SeanceDeclaree` | `Declaration` (nom générique, renommé) | — | RG-SEA-054 | proposed |
| Raccourcis de consommables | Liste ordonnée (12 articles actifs du centre au plus, sans doublon) proposée en un toucher à l'infirmier ; propre au centre | `RaccourcisDeConsommables`, `ConsulterLesRaccourcis`, `RemplacerLesRaccourcis` | `SeanceRaccourciDomainService` (retiré) | favoris, raccourcis d'articles | RG-SEA-027, 054 ; KB-SEA-31 | proposed |
| Quantité consommée | Quantité strictement positive d'un article déclarée sur une séance ; l'ajout cumule, la modification remplace | `QuantiteConsommee` (`aAjouter`, `nouvelle`) | `BigDecimal` nu | — | RG-SEA-025, 054 | proposed |
| Article du centre | Article du catalogue du Stock, actif et du centre, seul admis en consommable ou en raccourci ; le Stock reste seul juge | `ArticleDuCentre`, `ArticlesDuCentre` (port) | `Article` du Stock | référence article | RG-SEA-054, KB-SEA-25 | proposed |
| Retrait d'un consommable | Mise à zéro de la consommation déclarée d'un article sur une séance (imputation, aucun mouvement de stock) | `RetirerUnConsommable` | `removeConsommable` (retiré) | suppression de ligne | RG-SEA-022, 054 ; KB-SEA-22, 24 | proposed |

Homonymes : *Séance* (ici) ≠ *Séance facturable* (Facturation) ≠ *Séance, motif de sortie* (Stock). `ABSENT` n'est plus un statut de cycle de vie de ce contexte : c'est un reliquat LISIBLE du legacy (ADR 0024, `StatutDeSeance.ABSENT`), jamais produit ni validable ici (le scan le refuse en 422) ; l'exclusion de facturation relève de Facturation.
