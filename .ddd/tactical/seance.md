---
status: proposed
date: 2026-10-28
context: S�ance (`seance`) -- SD1, Core
sources: [.ddd/strategic/bounded-contexts.md S�ance, .ddd/strategic/context-map.md R8, .ddd/strategic/glossaries/seance.md, .ddd/discovery/event-storming.md timeline #1-24, docs/reference/regles-de-gestion/01-seance.md (RG-SEA-0xx), backend/bc-seance (lecture cibl�e), ADR 0002/0012/0014]
---

# Mod�le tactique -- S�ance

> **Contexte Core : mod�le riche obligatoire.** 2 agr�gats (S�ance, ConsommableD�clar�) + 1 agr�gat satellite
> (D�rogationR�gularisation), pas de saga. Le legacy `bc-seance` poss�de un `VoletParamedical` (434 l.) qui d�coupe en VO
> d�di�s. Le statut `ABSENT` est supprim� (ADR 0002) ; `FACTUREE` devient un verrou pos� sur r\u00e9ception de
> `FacturationValid�e`.

## 1. Process-level event storming (filtr� S�ance)

| Acteur    | Commande                        | Agr�gat            | �v�nement de domaine                    | Politique d�clench�e                                                                 | Read model             |
|-----------|---------------------------------|--------------------|-----------------------------------------|--------------------------------------------------------------------------------------|------------------------|
| Infirmier | Cr�erS�ance                     | S�ance             | `S�anceCr��e`                           | PEC valide requise (lecture Patient & couverture)                                    | S�ances du jour        |
| Infirmier | ValiderS�ance                   | S�ance             | `S�anceValid��e` (= r�alis�e)           | jours pr�vus (Planification) ; si d�bordement > 7j d�verrouillage admin (RG-SEA-046) | S�ances valid�es       |
| M�decin   | SignerS�ance                    | S�ance             | `S�anceSign�e`                          |                                                                                      | S�ances sign�es        |
| Infirmier | D�clarerConsommables            | ConsommableD�clar� | `ConsommablesDeS�anceD�clar�s`          | motif de sortie stock (Q2)                                                           | Consommables de s�ance |
| Admin     | D�verrouillerPourR�gularisation | S�ance             | `S�anceD�verrouill�ePourR�gularisation` | s�ance d�pass�e > 7j (RG-SEA-046)                                                    | S�ances d�verrouill�es |
| Admin     | D�clarerForfaitSurcharg�        | S�ance             | `ForfaitDeS�anceSurcharg�`              | priorit� surcharge > PEC effective > PEC demand�e (RG-FAC-021)                       | Simulation facturation |
| (syst�me) | --                              | S�ance             | `S�anceVerrouill�eFacturation`          | sur r�ception `FacturationValid�e` (Facturation)                                     | S�ances facturables    |

R�gles : `CREE -> VALIDEE -> SIGNEE`, sans retour (RG-SEA-001) ; validation le jour m�me sinon d�verrouillage admin 7j
(RG-SEA-046) ; une s�ance verrouill�e pour facturation est immuable (RG-SEA-002) ; cr�ation conditionn�e par une PEC
valide.

## 2. R�gles & invariants (classement)

| #   | R�gle (RG)                                                                          | Nature                  | Porteur                               |
|-----|-------------------------------------------------------------------------------------|-------------------------|---------------------------------------|
| R1  | Cycle `CREE -> VALIDEE -> SIGNEE`, sans retour                                      | invariant               | S�ance                                |
| R2  | Une s�ance verrouill�e pour facturation est immuable                                | invariant               | S�ance                                |
| R3  | Validation le jour m�me, sinon d�verrouillage admin (7j)                            | invariant + temporel    | S�ance + politique P1                 |
| R4  | Cr�ation conditionn�e par PEC valide                                                | r�gle d'entr�e          | Use case + port `PECRepository`       |
| R5  | Statut `FACTUREE` = verrou pos� sur `FacturationValid�e`                            | invariant temporel      | S�ance (re��oit `FacturationValid�e`) |
| R6  | Pas de statut `ABSENT`                                                              | invariant d'�limination | S�ance                                |
| R7  | Consommables d�clar�s = motif de sortie stock (Q2)                                  | r�gle d'entr�e          | Use case + ACL Stock                  |
| R8  | Forfait surcharg� : priorit� surcharge > PEC effective > PEC demand�e               | invariant de valeur     | VO `ForfaitAppliqu�`                  |
| R9  | Volet param�dical et m�dical distincts                                              | d�coupage               | VO `VoletParamedical`, `VoletM�dical` |
| R10 | R�gularisation : d�verrouillage > 7j, cr��e `S�anceD�verrouill�ePourR�gularisation` | r�gle de processus      | Politique P1                          |

## 3. Carte du mod�le

```mermaid
classDiagram
  class Seance {<<AggregateRoot>> SeanceId; CentreId; PatientId; PECId; TypeS�ance; StatutS�ance; VoletParamedical; VoletM�dical; ForfaitAppliqu�?; version}
  class SeanceId {<<ValueObject>> valeur}
  class PECId {<<ValueObject>> valeur}
  class VoletParamedical {<<ValueObject>> constantes; observations; infirmier; heureD�but; heureFin}
  class VoletM�dical {<<ValueObject>> constantes; observations; m�decin; heureD�but; heureFin}
  class ConsommableD�clar� {<<AggregateRoot>> ConsommableId; SeanceId; ArticleId; Quantit�; Motif; heure}
  class D�rogationR�gularisation {<<AggregateRoot satellite>> D�rogationId; SeanceId; Motif; Autorit�; date}
  class StatutS�ance {<<Enum>> CREE, VALIDEE, SIGNEE}
  Seance *-- VoletParamedical
  Seance *-- VoletM�dical
  Seance *-- ConsommableD�clar�
  Seance *-- D�rogationR�gularisation
  Seance ..> PECId : PECId (lecture Patient & couverture)
  Seance ..> ForfaitAppliqu� : forfait (projection Facturation)
```

## 4. Aggregate Design Canvas

### S�ance (root: `Seance`) -- contexte seance -- type: Core

- **Intention** : constater et documenter chaque s�ance de dialyse r�alis�e au poste, porte le cycle de vie
  cr��e/valid�e/sign�e.
- **Invariants** : R1, R2, R3, R5, R6, R8.
- **Identit�** : `SeanceId` (g�n�r�e par le domaine via port `GenerateurIdentifiants`).
- **�tat** : `CentreId` (shared kernel, ADR 0010), `PatientId`, `PECId`, `TypeS�ance`, `StatutS�ance`,
  `VoletParamedical` (VO), `VoletM�dical` (VO), `ForfaitAppliqu�?` (VO), `HeureD�but`, `HeureFin`, `DateS�ance`,
  observations ; journal des transitions (append-only, non recharg� en m�moire).
- **Commandes -> �v�nements** :
    - `cr�er(...)` -> `S�anceCr��e`
    - `valider(auteur, t)` -> `S�anceValid��e`
    - `signer(auteur, t)` -> `S�anceSign�e`
    - `d�clarerConsommables(liste)` -> `ConsommablesDeS�anceD�clar�s`
    - `d�clarerForfaitSurcharg�(forfait, auteur, t)` -> `ForfaitDeS�anceSurcharg�`
    - `d�verrouillerPourR�gularisation(motif, auteur, t)` -> `S�anceD�verrouill�ePourR�gularisation`
    - re��oit `FacturationValid�e` -> pose verrou `FACTUREE` (pas d'�v�nement propre, transition interne)
- **R�gles de rejet** : `S�anceD�j��Valid�e`, `S�anceD�j��Sign�e`, `S�anceVerrouill�eFacturation`, `PECInvalide`,
  `ValidationHorsJour`, `ForfaitInconnu`, `ConsommableInconnu`.
- **Coh�rence** : transactionnelle interne (statut + volets + journal) ; �ventuelle avec Facturation via �v�nements
  d'int�gration (Outbox) ; lecture PEC via port `PECRepository` (Customer-Supplier).
- **Concurrence/volum�trie** : version optimiste ; une s�ance = un infirmier au poste, faible contention.
- **Tests cl�s** : *given* s�ance CREE *when* valider *then* `S�anceValid��e` ; *given* s�ance verrouill�e facturation
  *when* modifier *then* `S�anceVerrouill�eFacturation` ; *given* PEC expir�e *when* cr�er *then* `PECInvalide`.

### ConsommableD�clar� (root: `ConsommableD�clar�`) -- contexte seance -- type: Entity (satellite de S�ance)

- **Intention** : tracer un consommable d�clar� utilis� pendant une s�ance, avec motif de sortie stock.
- **Invariants** : R7.
- **Identit�** : `ConsommableId`.
- **�tat** : `SeanceId`, `ArticleId` (opaque, lecture Stock via port publi�), `Quantit�`, `Motif`, `Heure`.
- **Commandes -> �v�nements** : `d�clarer(...)` -> `ConsommableD�clar�`.
- **R�gles de rejet** : `Quantit�Nulle`, `MotifVide`, `ArticleInconnu` (lecture port publi� Stock).
- **Coh�rence** : d�pend de S�ance (agr�gat parent) ; motif = sortie stock, coherence avec Stock via �v�nement.

### D�rogationR�gularisation (root: `D�rogationR�gularisation`) -- contexte seance -- type: AggregateRoot satellite

- **Intention** : autoriser une r�gularisation hors cycle normal (ex: s�ance oubli�e > 7j).
- **Invariants** : R10.
- **Identit�** : `D�rogationId`.
- **�tat** : `SeanceId`, `Motif`, `Autorit�` (qui a autoris�), `Date`.
- **Commandes -> �v�nements** : `d�clarer(...)` -> `D�rogationR�gularisationD��clar�e`.
- **R�gles de rejet** : `S�anceD�j��Verrouill�e`, `Autorit�Inconnue`.
- **Coh�rence** : d�pend de S�ance ; cr��e `S�anceD�verrouill�ePourR�gularisation`.

## 5. Domain Events -- publi�s

| �v�nement                               | Produit par                      | Consommateurs                                                                                                  | Payload cl�s                                                                        | Version | Idempotence |
|-----------------------------------------|----------------------------------|----------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------|---------|-------------|
| `S�anceCr��e`                           | S�ance : cr��ation               | Facturation (projection s�ance facturable), Stock (consommables d�clar�s)                                      | `eventId`, `seanceId`, `centreId`, `patientId`, `pecId`, `dateS�ance`, `typeS�ance` | 1       | idem        |
| `S�anceValid��e`                        | S�ance : validation              | Facturation (verrouillage potentiel), Absences (suppression d'absence d�tect�e), Planification (place lib�r�e) | `eventId`, `seanceId`, `centreId`, `patientId`, `dateS�ance`, `forfaitAppliqu�?`    | 1       | idem        |
| `S�anceSign�e`                          | S�ance : signature m�dicale      | Dossier m�dical (lien ordonnance), Pilotage (indicateur)                                                       | `eventId`, `seanceId`, `medecinId`, `dateSignature`                                 | 1       | idem        |
| `ConsommablesDeS�anceD�clar�s`          | ConsommableD�clar� : d�claration | Stock (sortie avec motif)                                                                                      | `eventId`, `seanceId`, `centreId`, `liste` {`articleId`, `quantit�`, `motif`}       | 1       | idem        |
| `ForfaitDeS�anceSurcharg�`              | S�ance : forfait surcharg�       | Facturation (priorit� surcharge)                                                                               | `eventId`, `seanceId`, `centreId`, `forfait`, `montantSurcharge`                    | 1       | idem        |
| `S�anceD�verrouill�ePourR�gularisation` | S�ance : d�verrouillage          | Facturation (r�ouverture possible), Absences (r�ajustement)                                                    | `eventId`, `seanceId`, `centreId`, `motif`, `dateR�gularisation`                    | 1       | idem        |

R�gles : pas de fuite du mod�le interne (ni volets m�dical/param�dical d�taill�s ni transitions internes) ; `FACTUREE`
n'est pas un statut expos�, c'est un verrou interne pos� sur `FacturationValid�e`.

## 6. Domain Events -- consomm�s

| �v�nement                             | Producteur           | Usage dans S�ance                                  | Statut           |
|---------------------------------------|----------------------|----------------------------------------------------|------------------|
| `FacturationValid�e`                  | Facturation          | pose verrou `FACTUREE` sur la s�ance (immuabilit�) | requis           |
| `PECValid�e` / `PECCl��tur�e`         | Patient & couverture | lecture pour validation de cr��ation (RG-SEA-04)   | requis (lecture) |
| `PatientPlac�` / `S�ancesProgramm�es` | Planification        | lecture pour cr��ation (jour de dialyse pr�vu)     | requis (lecture) |

Aucun �v�nement externe n'est requis pour la v1 : S�ance est amont vers Facturation/Stock/Absences/Dossier m�dical, aval
vers Patient & couverture (lecture PEC) et Planification (lecture jours pr�vus).

## 7. Use cases -- agr�gats -- ports

| Use case (commande/query)                   | Agr�gat            | Ports                                                                               | Autorisation                   |
|---------------------------------------------|--------------------|-------------------------------------------------------------------------------------|--------------------------------|
| Cr�erS�ance                                 | S�ance             | S�anceRepository, PECRepository (lecture), GenerateurIdentifiants, Horloge, Outbox  | INFIRMIER centre               |
| ValiderS�ance                               | S�ance             | S�anceRepository, Horloge, Outbox                                                   | INFIRMIER centre               |
| SignerS�ance                                | S�ance             | S�anceRepository, Horloge, Outbox                                                   | M�DECIN centre                 |
| D�clarerConsommables                        | ConsommableD�clar� | S�anceRepository (lecture s�ance), StockPublishedLanguage (lecture article), Outbox | INFIRMIER centre               |
| D�clarerForfaitSurcharg�                    | S�ance             | S�anceRepository, Horloge, Outbox                                                   | INFIRMIER centre               |
| D�verrouillerPourR�gularisation             | S�ance             | S�anceRepository, Horloge, Outbox                                                   | ADMIN centre                   |
| *Handler* VerrouillageFacturation           | S�ance             | S�anceRepository, JournalDesMessagesTrait�s (idempotence), Outbox                   | syst�me                        |
| Q: S�ancesDuJour (pagination)               | read model         | LectureS�ances (SQL d�di�e)                                                         | ADMIN/SECRETAIRE centre        |
| Q: S�ancesValid�esNonSign�es                | read model         | LectureS�ances                                                                      | ADMIN/MEDECIN centre           |
| Q: FicheS�ance (volets param�dical/m�dical) | read model         | LectureS�ancesD�taill�es                                                            | ADMIN/INFIRMIER/MEDECIN centre |
| Q: ConsommablesDeS�ance                     | read model         | LectureConsommables                                                                 | ADMIN/INFIRMIER centre         |
| Q: S�ancesEnRetardValidation (> 7j)         | read model         | LectureS�ances                                                                      | ADMIN centre                   |

### Ports sortants

`S�anceRepository` (un par racine) -> `ConsommableD�clar�Repository` (satellite) -> `PECRepository` (lecture PEC valide,
Customer-Supplier Patient & couverture) -> `StockPublishedLanguage` (lecture article pour motif de sortie, OHS Stock) ->
`PublicateurDEvenements` (Outbox) -> `JournalDesMessagesTrait�s` (idempotence) -> `Horloge` ->
`GenerateurIdentifiants` -> `Habilitations` (Identit� & acc�s) -> `ParametresSeance` (seuil 7j, RG-SEA-046).

Alerte Facturation (refactoring sur place, ADR 0024) : un �couteur dans `platform`
(`S�anceEvenementsFacturationListener`) traduit `S�anceValid��e` vers `S�anceFacturableService.enregistrer` ; l'�viction
du cache `ref.s�ancesFacturables` est un `@CacheEvict` synchrone du contr�leur de s�ance.

## 8. Matrice invariant -> test (pilote le TDD de `slice-implementer`)

| #  | Invariant                     | Test unitaire (domaine pur)                                                                              | �quivalent legacy                          |
|----|-------------------------------|----------------------------------------------------------------------------------------------------------|--------------------------------------------|
| R1 | cycle sans retour             | `should_reject_signer_when_pas_validee` ; `should_reject_valider_quand_deja_signee`                      | `Seance.java:165,181`                      |
| R2 | verrou facturation = immuable | `should_reject_modification_when_verrouillee_facturation` ; `should_emit_verrou_sur_facturation_validee` | `Seance.java:201,219`                      |
| R3 | validation jour m�me          | `should_reject_validation_hors_jour` ; `should_autoriser_deverrouillage_apres_7j`                        | `Seance.java:222`                          |
| R4 | PEC valide                    | `should_reject_creation_when_pec_invalide` (service + fake port)                                         | contr�leur (\u00e0 caract\u00e9riser)      |
| R5 | pas de statut ABSENT          | `should_not_have_statut_absent` (test de d�sinstallation)                                                | migration (supprim�)                       |
| R6 | forfait prioritaire           | `should_appliquer_priorite_surcharge_pec_effective_pec_demandee`                                         | `ForfaitApplique.java:61`                  |
| R7 | motif sortie stock            | `should_reject_consommable_sans_motif` ; `should_valider_article_via_stock_published_language`           | contr\u00f4leur (\u00e0 caract\u00e9riser) |
| R8 | journal transitions           | `should_emit_one_transition_per_status_change`                                                           | `JournalStatutsService.java:55`            |
| R9 | d�verrouillage 7j             | `should_reject_deverrouillage_avant_7j` ; `should_autoriser_deverrouillage_apres_7j`                     | `RegleRegularisation.java:45`              |

## 9. R�f�rentiels & caches

| Nom                                | TTL     | Cl�                   | Invalidation                        |
|------------------------------------|---------|-----------------------|-------------------------------------|
| `ref.seances.du.jour`              | `PT3M`  | `centerId` + date     | @CacheEvict sur cr�ation/validation |
| `ref.seances.validees.non.signees` | `PT3M`  | `centerId`            | @CacheEvict sur signature           |
| `cache.pec.valide`                 | `PT15M` | `patientId` + periode | lecture seule (Customer-Supplier)   |

## 10. Ports d'entr�e (r�capitulatif pour `slice-implementer`)

| Port                                 | M�thode     | Signature                                                                            | Impl�ment� par                           |
|--------------------------------------|-------------|--------------------------------------------------------------------------------------|------------------------------------------|
| `CreerSeanceUseCase`                 | `creer`     | `(CreerSeanceCommande) -> SeanceId`                                                  | `CreerSeanceUseCaseImpl`                 |
| `ValiderSeanceUseCase`               | `valider`   | `(SeanceId, CentreId, UtilisateurId, Instant) -> void`                               | `ValiderSeanceUseCaseImpl`               |
| `SignerSeanceUseCase`                | `signer`    | `(SeanceId, CentreId, UtilisateurId, Instant) -> void`                               | `SignerSeanceUseCaseImpl`                |
| `DeclarerConsommablesUseCase`        | `declarer`  | `(SeanceId, CentreId, List<ConsommableDeclaration>, UtilisateurId, Instant) -> void` | `DeclarerConsommablesUseCaseImpl`        |
| `DeclarerForfaitSurchargeUseCase`    | `declarer`  | `(SeanceId, CentreId, Forfait, Montant, UtilisateurId, Instant) -> void`             | `DeclarerForfaitSurchargeUseCaseImpl`    |
| `DebloquerPourRegularisationUseCase` | `debloquer` | `(SeanceId, CentreId, Motif, UtilisateurId, Instant) -> void`                        | `DebloquerPourRegularisationUseCaseImpl` |

**Prochaine �tape** : `/ddd-slice seance` (premi�re slice = walking skeleton : cr�ation de s�ance avec validation PEC,
cr�ation de s�ance avec validation PEC, traversant interface -> application -> domaine -> persistance).