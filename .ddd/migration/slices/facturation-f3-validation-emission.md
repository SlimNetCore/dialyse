---
status: approved
date: 2026-10-20
slice: facturation-f3-validation-emission
order: 3 (F1 à F7, voir `.ddd/migration/roadmap.md` § 1 ter)
sources: [.ddd/tactical/facturation.md (F5-F9, F14-F15, agrégat Facture), docs/reference/regles-de-gestion/12-facturation-reglements-comptabilite.md (RG-FAC-030 à 032, RG-FAC-010), ADR 0013, ADR 0019, ADR 0024, ADR 0030, ADR 0032, .ddd/safety-net/facturation-f3-validation-emission.md, known-bugs.md (KB-FAC-22 à 36)]
---

> **Refonte sur place (ADR 0024)** : mêmes tables, même URL, mêmes DTO JSON ; retour arrière = commit précédent
> (migration
> V40 additive, laissée en place).

# F3 — Validation et émission des factures

**Comportement observable** : un administrateur valide la facturation d'une période (mois ou deux dates) : pour chaque
groupe
de séances éligibles (par patient, ou par patient et forfait, selon le corps de la requête) une facture est émise,
numérotée
d'après le modèle du centre (`FAC-{ANNEE}-{SEQ}`), avec ses lignes, ses totaux HT/TVA/TTC et ses instantanés (patient,
payeur,
période, taux de TVA, forfaits, auteur) ; les séances passent à l'état `FACTUREE` rattachées à leur facture ; la
Comptabilité
écrit les ventes. Réponse : `{createdInvoices, billedSeances}`.

| Route                               | Rôles (legacy)     | Nouveau code                                                                                                 |
|-------------------------------------|--------------------|--------------------------------------------------------------------------------------------------------------|
| `POST /api/v1/facturation/validate` | `ADMIN`, `MEDECIN` | `ValidationControleur` → `ValiderLaFacturation` ; réservé à `ADMIN` (le filtre des profils refuse `MEDECIN`) |

Tables écrites : `factures` (+ `emise_par`), `facture_lignes`, `facture_sequence`, `seances` (`statut = 'FACTUREE'`,
`facture_id`) ; puis, après commit, `ecritures_comptables` / `lignes_ecriture` (Comptabilité).

## Décisions F3 (2026-10-20, utilisateur)

| KB                | Décision                                                                                                                                                                                 |
|-------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| KB-FAC-22         | **Corrigé** : TVA en vigueur à la fin de la période facturée (langage publié `ParametrageFiscal`, ADR 0030) ; KB-FAC-23 **reproduit** (année du jour pour la séquence et `{YYYY}`)       |
| KB-FAC-24, 25     | **Corrigés** : séquence atomique (`UPDATE ... seq_value + 1`, H2 et PostgreSQL), 409 si le numéro est déjà pris ; modèle sans `{SEQ}` refusé à la saisie (déjà F1, 400)                  |
| KB-FAC-26         | **Reproduit** : 2e facture possible pour la même période et le même patient                                                                                                              |
| KB-FAC-27, 28, 29 | **Corrigés** : contrat d'événement v2 (total TTC exact, période réellement facturée, factures émises), aucun événement si rien n'est émis ; `/rebuild` de la Comptabilité conservé       |
| KB-FAC-30         | **Corrigé** : auteur de l'émission enregistré (V40) ; `previewGeneratedAt` reste ignoré                                                                                                  |
| KB-FAC-31, 35, 36 | **Reproduits** (pas d'avoir ; erreurs de protocole en 500 ; date de facture = jour de validation, date d'écriture = fin de période) : « reproduits, correction à la livraison suivante » |
| KB-FAC-32         | **Corrigé** : 422 tant qu'une séance éligible n'a pas de forfait, atomique ; la simulation garde « Forfait non defini »                                                                  |
| KB-FAC-33, 34     | **Corrigés/alignés** : 403 si le centre du corps diffère de la session ; route `ADMIN` (comportement observable inchangé pour `MEDECIN`)                                                 |

Détail des réalisations et des goldens : `known-bugs.md` § « Décisions F3 ». Décisions de conception : ADR 0032.

## État au 2026-10-20 : `built`

### Réalisation

**Contexte `facturation`** (hexagonal, ArchUnit, `check-arch` 0 violation) :

- domaine `facture` : `Facture` (racine, immuable : instantanés, totaux déduits des lignes et du taux, TVA HALF_UP sur
  le total),
  `LigneFacture`, ports `Factures` (repository), `LiaisonDesSeances`, refus `NumeroDeFactureDejaPris`,
  `SeancesSansForfait` ;
  réutilisation du calcul de F2 (`Simulation.etablir`, `Regroupement`, `PolitiqueDeForfaitApplique`) : `Facture.emettre`
  prend
  le groupe `FactureSimulee` ; `ForfaitApplique.estDefini`, `PatientFacture` étendu (immatriculation, centre payeur,
  agence) ;
  `SequenceDeFacturation.reserverLaSuivante` (port étendu) ;
- application `validation` : `ValiderLaFacturation` (une transaction : éligibilité, refus des séances sans forfait, taux
  de fin de
  période, réservation des numéros, vérification du numéro, enregistrement, liaison des séances, publication),
  `ResultatDeLaValidation`, port `PublicationDeLaValidation` ;
- langage publié : `publishedlanguage/FacturationValideeEvent` (version 2, ADR 0032) ;
- infrastructure : `FacturesJdbc` (traduit la violation d'unicité en 409),
  `SequenceDeFacturationJdbc.reserverLaSuivante`,
  ACL `acl/legacyseance/LiaisonDesSeancesJdbc`, `SeancesFacturablesJdbc` (payeur du patient),
  `messaging/PublicationDeLaValidationSpring` ;
- interface : `ValidationControleur` (`POST /api/v1/facturation/validate`, `ADMIN`), `ValidationRequete` (mêmes champs
  que
  `FacturationValidateRequest`), `EmissionDesFacturesAdvice` (409 `NUMERO_DE_FACTURE_DEJA_PRIS`, 422
  `SEANCES_SANS_FORFAIT`) ;
- composition : `FacturationContexteConfiguration` (beans `facturation…`).

**Consommateur Comptabilité** (modification minimale) : `FacturationValideeComptabiliteListener` (platform) lit
`centre`, `debut`,
`fin` du nouvel événement (mêmes appels à `ComptabilisationDesVentesEtReglements.facturationValidee`) ; suite C5 verte,
goldens C5
inchangés (le test C5 publie l'événement avec la nouvelle signature : retouche de fixture).

**Migration** `V40__facturation_auteur_de_l_emission.sql` (additive : `factures.emise_par UUID` nullable,
`IF NOT EXISTS`) et
`db/schema.sql` ; **ADR 0032** (séquence atomique, refus, contrat d'événement). La contrainte
`UNIQUE (center_id, numero_facture)`
existait déjà (V1) : aucune contrainte ajoutée.

**Contrat JSON** : requête et réponse inchangées (`userId` reste exigé, `previewGeneratedAt` ignoré) ; nouveaux refus
409, 422,
403 (voir « Frontend à adapter »).

**Retiré du legacy** : `FacturationRestController.validate`, `FacturationValidateRequest` (DTO),
`FacturationDomainService.validate` et le calcul d'aperçu interne (`preview`, `buildPreviewInvoices`…),
`FacturationUseCase.validate`
/ `getSettings` / `updateSettings`, `FactureJdbcAdapter`, `FacturationSettingsJdbcAdapter`, `FactureAggregate`,
`LigneFacture`
(entité), `FacturationValideeEvent` (legacy), `FactureRepositoryPort`, `FactureRepository`,
`FacturationValidateCommand`,
`FacturationValidationResult`, `FacturationPreview{Query,Result,Invoice,Line,Seance}`, `SeanceFacturationCandidate`,
`SeanceEligibleForFacturationSpecification`, `FactureNumberTemplate` (+ test), `FacturationPeriod`,
`ParametresFacturation` (VO),
`FacturationSettings{Query,Command,RepositoryPort}`, `SeanceFacturationPort.findEligibleSeances` / `markAsBilled`,
`SeanceFacturationJdbcAdapter` (lecture des séances éligibles, `markAsBilled`, calcul du forfait),
`FacturationDomainServiceTest`
(deux tests de `validate` et d'aperçu, remplacés par les tests du contexte).

### Parité

Les 53 scénarios du filet F3 tournent contre le nouveau code : **94 goldens inchangés**, **23 écarts décidés** portés
par des
goldens `*_nouveau_code` ; les **23 goldens caducs ont été retirés** :
`emission/` `forfait_non_defini_{200,etat,ecritures}`, `tva_du_jour_etat` ;
`evenement/` `mois_et_dates_divergents_{evenements,ecritures}`, `montant_nul_lignes_ecritures`, `nominal_evenements`,
`periode_cloturee_evenements`, `periode_par_dates_evenements`, `rejeu_evenements`,
`sans_seance_{evenements,ecritures}` ;
`numerotation/` `doublon_500`, `doublon_nouvelle_tentative_500`, `modele_sans_sequence_deux_factures_500`,
`modele_sans_sequence_seconde_validation_500` ;
`refus/` `centre_A_par_admin_B{,_etat,_ecritures}`, `centre_inconnu_evenements`, `echec_derniere_facture_500` ;
`seances/` `evenements_des_deux_validations`.
Quatre scénarios s'ajoutent (`EmissionNouveauCodeIntegrationTest`, goldens `nouveau_code/`) : auteur de l'émission,
factures
d'avant V40 sans auteur, refus atomique d'une seule séance sans forfait, surcharge qui lève le refus.

**Retouches de test pour cause de fixture** (aucune assertion ni aucun golden conservé modifié) :
`ValidationCharacterizationSupport.goldenEvenements` et `EvenementsFacturationValideeEnregistres` (nouveau type
d'événement :
champs `centre`, `debut`, `fin`, `factures`, `seancesFacturees`, `totalTtc`, `survenueLe`) ;
`VentesEtReglementsCharacterizationIntegrationTest`
(C5 : construction de l'événement avec la nouvelle signature, `List.of()` pour les factures, que le consommateur
n'utilise pas) ;
noms des goldens des scénarios modifiés (`_nouveau_code`) et un commentaire « Nouveau code F3 » sur chacun ;
`SeancesJdbcContratTest` (tables `patients.centre_payeur_id` et `centre_payeur`, lues par la simulation depuis F3) et
`SequenceJdbcContratTest` (clé primaire de `facture_sequence`).

### Tests

Domaine : `FactureTest`, `LigneFactureTest` ; application : `ValiderLaFacturationTest` (doubles en
mémoire : `FacturesEnMemoire`, `SeancesEnMemoire` qui implémente aussi `LiaisonDesSeances`, `SequenceEnMemoire`,
`PublicationEnMemoire`) ; contrats mémoire/H2/PostgreSQL 16 : `FacturesContrat`, `SequenceContrat` (réservation),
`SeancesContrat` (payeur, liaison), `SequenceConcurrenteContrat` (concurrence déterministe) ; alignement des colonnes
sur le schéma
figé (`ModelesDePersistanceAlignesFacturationIntegrationTest` : `factures`, `facture_lignes`, payeur du patient) ;
ArchUnit du
module (règle ajoutée sur le langage publié).

**Preuve du verrou** : `SequenceConcurrenteContrat` fait réserver une première transaction qui reste ouverte ; la
seconde doit
attendre (elle ne se termine pas en 700 ms) puis obtient la valeur suivante après le commit. En remplaçant
temporairement
l'incrément atomique par « lire la valeur puis écrire valeur + 1 », le test échoue sur H2 ; code restauré.

**Résultats (2026-10-20)** : suite complète `./mvnw -o clean test` verte (1614 tests dans `platform`, 240 dans
`contexts/facturation`,
GMAO et Comptabilité C1..C7 verts, `check-arch.ts` 0 violation). Filet F3 : 53 scénarios + 4 nouveaux, verts. Contrats
PostgreSQL 16 (`backend/scripts/facturation-pg16-rejeu.sh 55443`, cluster jetable) : V1..V40 appliquées, V40 rejouable,
`ParametresPostgresContractIT`
(5), `SeancesPostgresContractIT` (27), `SequencePostgresContractIT` (6), `FacturesPostgresContractIT` (8),
`SequenceConcurrentePostgresContractIT` (3, dont la création simultanée de la ligne d'une année),
`RegleTvaPostgresContractIT` (15) : tout
vert, code de sortie 0. Mutation (PIT 1.30.0, domaine et application du module) : 210 mutants tués sur 219 (96 %) ; sur
le domaine
d'émission (`Facture`, `LigneFacture`, `SeancesSansForfait`, `NumeroDeFactureDejaPris`) et `ValiderLaFacturation` :
52/54 avant
l'ajout de deux tests qui tuent les deux survivants (identité de la facture, numéro de l'exception).

### Écarts de comportement (nouveau code)

1. TVA de la facture = taux à la fin de la période (était : taux du jour).
2. 409 `NUMERO_DE_FACTURE_DEJA_PRIS` (était : 500 `DATA_ACCESS_ERROR`), y compris pour un modèle sans `{SEQ}` posé en
   base.
3. 422 `SEANCES_SANS_FORFAIT` (était : facture à zéro « Forfait non defini »).
4. 403 si le centre du corps diffère de la session (était : émission pour le centre du corps) ; un centre inconnu donne
   donc 403 (était : 200 `{0,0}` + événement).
5. Événement v2 : total TTC exact, factures émises, période réellement facturée ; aucun événement si rien n'est émis ;
   une validation
   sans séance éligible ne régénère plus les ventes manquantes (`/rebuild`).
6. Auteur de l'émission enregistré (`factures.emise_par`).

### Points à valider

1. Une validation « vide » ne régénère plus les écritures de ventes manquantes ; seul `POST /comptabilite/rebuild` le
   fait.
2. La contrainte d'unicité du numéro par centre existe déjà (V1) : l'audit de production (point 19) dit si des séquences
   sont en
   retard sur des numéros existants (le 409 s'appliquerait jusqu'au rattrapage).
3. Le refus 422 des séances sans forfait bloque tout le mois tant qu'une séance n'a pas de forfait : prévoir la
   régularisation avant
   la bascule (point 19.6).
4. Le `userId` du corps reste obligatoire (400 sans lui) mais n'est plus la source de l'auteur ; à retirer du contrat
   quand le
   frontend le permet.

### Retiré par F6

Retiré ou passé par le langage publié par la slice F6 (fiche `facturation-f6-retrait-bc-facturation.md`, 2026-10-21) : le module `bc-facturation` n'existe plus.

### Plan de bascule

Refonte sur place (ADR 0024) : mêmes URL, pas de façade ni de flag ; retour arrière = commit précédent (V40 reste en
place, la
colonne est ignorée par l'ancien code). **Déploiement** : migration V40 appliquée au démarrage (additive, rejouable) ;
contrôles de
production : `verifications-production.md` point 19. **Frontend à adapter** (non modifié ici) :

- **409** `NUMERO_DE_FACTURE_DEJA_PRIS` (message explicite, séquence du centre en retard : contacter l'administrateur) ;
- **422** `SEANCES_SANS_FORFAIT` (propriété `seancesSansForfait`, message listant le nombre de séances) : inviter à
  donner un
  forfait (prise en charge ou changement de forfait dans la simulation) ; l'aperçu continue d'afficher « Forfait non
  defini » ;
- **403** si le centre envoyé n'est pas celui de la session (le corps doit porter le centre de la session) ;
- **TVA** de la facture calculée à la fin de la période (l'écran affiche le taux de la simulation, déjà à la fin de
  période) ;
- l' **auteur** de l'émission est l'utilisateur de la session (le champ `userId` du corps reste exigé) ;
- écran réservé à `ADMIN` (comportement inchangé pour `MEDECIN`, déjà refusé).

## Correctif 2026-10-21 : événement v3 borné et table `event_publication` en H2 (KB-FAC-72, ADR 0036)

- Cause : en H2, Modulith créait `serialized_event VARCHAR(4000)` ; l'événement v2 (une ligne par facture) le dépassait dès ~20 factures ; la validation entière était annulée.
- Correction 1 : `event_publication` créée dans `db/schema.sql` (colonne `VARCHAR(1000000)`), initialisation Modulith désactivée dans `application.yml` ; V35 inchangée.
- Correction 2 : `FacturationValideeEvent` v3 = centre, période, `nombreDeFactures`, séances facturées, total TTC, instant. La Comptabilité relit les factures de la période sans écriture (inchangé : elle n'utilisait pas la liste) ; écritures identiques.
- Tests : validation de 300 factures sur H2 (échouait avant) ; goldens d'événement migrés (forme seule) ; `ValiderLaFacturationTest` : assertions sur les factures émises au lieu de l'événement.
