---
status: approved
date: 2026-10-21
slice: facturation-f4-reglements
order: 4 (F1 à F7, voir `.ddd/migration/roadmap.md` § 1 ter)
sources: [.ddd/tactical/facturation.md (F12-F13, agrégat Règlement), .ddd/tactical/comptabilite.md (B6, remboursement), docs/reference/regles-de-gestion/12-facturation-reglements-comptabilite.md (RG-REG-001 à 007), ADR 0013, 0019, 0024, 0030, 0033, .ddd/safety-net/facturation-f4-reglements.md, known-bugs.md (KB-FAC-37 à 51)]
---

> **Refonte sur place (ADR 0024)** : mêmes tables, mêmes URL, mêmes DTO JSON (champs ajoutés seulement) ; retour arrière =
> commit précédent (migration V41 additive, laissée en place). Pas de façade, de routage, de projection ni de métriques.

# F4 — Règlements des factures

**Comportement observable** : un administrateur ou une secrétaire enregistre le règlement d'une facture émise (paiement, ou
remboursement s'il est négatif) ; la situation de la facture (non réglée, partiellement réglée, réglée avec solde ou
trop-perçu) est une lecture calculée de la somme signée des règlements contre le total TTC figé ; la Comptabilité écrit
l'écriture de trésorerie après l'enregistrement.

| Route                                     | Rôles               | Nouveau code                                                                          |
|-------------------------------------------|---------------------|---------------------------------------------------------------------------------------|
| `POST /api/v1/reglements/{id}/paiements`  | `ADMIN`, `SECRETAIRE` | `ReglementControleur` → `EnregistrerUnReglement` (retourne la ligne de situation)      |
| `GET /api/v1/reglements/{id}/paiements`   | idem                | → `ConsulterLesReglements.historique` (+ `modeReglement`, `referencePaiement` si présents) |
| `GET /api/v1/reglements`                  | idem                | → `rechercher` (page, `{items,total,page,size}`)                                      |
| `GET /api/v1/reglements/dashboard`        | idem                | → `tableauDeBord` (reproduit, KB-FAC-45)                                              |
| `GET /api/v1/reglements/export`           | idem                | → `exporter` (CSV `;` ou Excel POI, reproduit, KB-FAC-48 ; colonnes inchangées)       |

Table écrite : `facture_reglements` (+ `mode_reglement`, `reference_paiement`, `cle_idempotence`, migration **V41**). Tables lues :
`factures`, `patients`, `centre_payeur`, `agence`, `caisse_assurance`.

## Décisions F4 (2026-10-21, utilisateur)

Détail : `known-bugs.md` § « Décisions F4 », ADR 0033. Règles marquées **à valider par le métier** : mode de règlement, remboursement,
clé d'idempotence.

| KB                  | Décision                                                                                                                                   |
|---------------------|--------------------------------------------------------------------------------------------------------------------------------------------|
| KB-FAC-37           | **Corrigé** : 403 si le centre du corps ou de la requête diffère de celui de la session (écriture et lectures)                              |
| KB-FAC-38, 50       | **Reproduits** : date non contrôlée ; mois comptable clos n'empêche pas le règlement                                                        |
| KB-FAC-39           | **Corrigé** : clé d'idempotence **facultative** dans le corps (`cleIdempotence`), par (centre, facture) ; sans clé, doublon reproduit        |
| KB-FAC-40           | **Ajouté** : mode `BANQUE`, `CHEQUE`, `CAISSE`, `ESPECES` + référence de paiement ; sans mode : banque comme avant                          |
| KB-FAC-41           | **Reproduit** : trop-perçu accepté (`REGLEE` / `TROP_PERCU`)                                                                               |
| KB-FAC-42, 43       | **Corrigé** : remboursement supérieur au total déjà réglé refusé en 422 ; la situation reste la somme signée                                |
| KB-FAC-47           | **Corrigé** (Comptabilité) : remboursement = écriture de sens inverse ; événement à montant signé ; `/rebuild` relit signe et mode           |
| Auteur              | **Corrigé** : utilisateur de la session ; `userId` du corps ignoré                                                                          |
| KB-FAC-44 à 46, 48, 49, 51 | **Reproduits**, correction à la livraison suivante                                                                                  |

Correspondance mode → Comptabilité (**à faire valider par le métier**) :

| Mode de règlement         | Opération comptable      | Journal / compte de trésorerie |
|---------------------------|--------------------------|--------------------------------|
| absent (anciens règlements) | règlement en banque    | `BQ` / 512 (comme avant)       |
| `BANQUE`, `CHEQUE`        | règlement en banque      | `BQ` / 512                     |
| `CAISSE`, `ESPECES`       | règlement en caisse      | `CA` / 530 (journal de caisse) |

## État au 2026-10-21 : `built`

### Réalisation

**Contexte `facturation`** (hexagonal, ArchUnit, `check-arch` 0 violation) :

- domaine `reglement` : `Reglement` (racine immuable : montant signé arrondi, code `REG-`, remboursement contrôlé), `ModeDeReglement`,
  `FactureARegler`, `SituationDeFacture` (+ `EtatDeReglement`, `TypeDeSolde`), `RemboursementSuperieurAuTotalRegle`, port `Reglements` ;
- application `reglement` : `EnregistrerUnReglement` (une transaction : facture verrouillée, idempotence, enregistrement,
  publication), `ConsulterLesReglements`, `TableauDeBord`, `CriteresDeSituation`, `DemandeDeReglement`, `LigneDeSituation`,
  `PageDeSituations`, ports `SituationsDesFactures` (lecture) et `PublicationDesReglements` ;
- langage publié : `publishedlanguage/FactureReglementRecordedEvent` (v2 : `reglementId`, `mode`, montant signé non arrondi) ;
- infrastructure : `ReglementsJdbc` (verrou `FOR UPDATE`, clé d'idempotence), `SituationsDesFacturesJdbc` (lecture, mêmes SQL que
  le legacy), `messaging/PublicationDesReglementsSpring` ;
- interface : `ReglementControleur`, `ReglementRequete`, `PaiementReponse`, `ExportDesSituations`, `ReglementsAdvice` (422) ;
- composition : `FacturationContexteConfiguration` (beans `facturationReglements…`) ; dépendances POI ajoutées au `pom`.

**Consommateur Comptabilité** : `ComptabilisationDesVentesEtReglements.reglementEnregistre(centre, règlement, facture, montant,
date, mode)` (nouveau), `RedactionDesEcritures.reglement` (sens inverse si négatif), `ReglementAComptabiliser.enCaisse`
(`CAISSE` ou `ESPECES`), `ReglementsAComptabiliserJdbc` (lit `mode_reglement`, montant signé) ;
`ReglementEncaisseComptabiliteListener` (platform) aiguille selon la présence de l'identifiant. **Direction**
(`DirectionDashboardQueryService`), **Migration** (`OpeningBalanceJdbcAdapter`, `MigrationRollbackJdbcAdapter`) : **inchangés**
(même table, mêmes colonnes ; les colonnes ajoutées sont nullables).

**Retiré** : module `bc-reglement` (7 fichiers de production + `ReglementServiceTest`), `ReglementApplicationService`,
`ReglementRestController`, `RegisterFacturePaymentRequest`, `FactureReglementJdbcAdapter` ; entrées de `backend/pom.xml`,
`platform/pom.xml`, `Dockerfile` (ajout du `pom` de `contexts/facturation`, absent jusque-là) ; boucles des scripts PIT.

**Migration** `V41__facturation_mode_reference_et_cle_des_reglements.sql` (additive : trois colonnes nullables `IF NOT EXISTS`, index
unique `(center_id, facture_id, cle_idempotence)`, aucun DROP ni DELETE) et `db/schema.sql`. **ADR 0033**.

### Limite de la preuve (revue du 2026-10-21, M1)

Le filet F4 (6 classes de caractérisation, 193 goldens `reglements/`) **n'a pas été versionné avant le remplacement de
`bc-reglement`** : l'historique git ne contient ni les goldens legacy remplacés (les 28 `_nouveau_code`) ni la preuve « vert sur
le legacy avant ». « 153 goldens inchangés » et « 28 écarts décidés » reposent sur ce compte-rendu et non sur un diff git. Le
commit du filet et du code F4 est géré par l'utilisateur ; aucun commit n'est fait ici.

### Parité

Les 94 scénarios du filet F4 tournent contre le nouveau code : **153 goldens inchangés**, **28 écarts décidés** portés par des goldens
`*_nouveau_code` ; les **28 goldens caducs ont été retirés** :
`enregistrement/` `partiel_base`, `arrondi_base`, `auteur_nettoye_base`, `sans_date_ni_auteur_base`, `remboursement_base`,
`dates_non_controlees_base`, `champs_inconnus_base`, `centre_du_corps_autre_que_la_session_200` ;
`evenement/` `nominal_evenements`, `defauts_evenements`, `montant_non_arrondi_evenements`, `montant_non_arrondi_base`,
`mois_cloture_evenements`, `remboursement_evenements`, `remboursement_ecritures` ;
`autorisations/` `a_ecrit_chez_b`, `a_lit_le_dashboard_de_b`, `a_exporte_b`, `centre_inconnu_recherche`,
`centre_inconnu_enregistrement`, `parcours_4_historique`, `parcours_6_evenements`, `parcours_7_ecritures` ;
`recherche/` `centre_b_demande_par_un_admin_du_centre_a`, `historique_centre_b_demande_par_admin_a`,
`apres_enregistrement_historique` ; `situation/` `remboursement_sans_reglement` ; `tableau-de-bord-export/` `centre_b`.
Suite C5 : **2 goldens `_nouveau_code`** (remboursement dans l'équilibre des écritures et dérivé négatif) ; les goldens C5
**nominaux** (règlement positif) sont inchangés. Nouveaux scénarios (`ReglementsNouveauCodeCharacterizationIntegrationTest`, 13,
goldens `nouveau-code/`) : mode espèces/chèque/caisse, mode inconnu, idempotence, remboursement 422/total/trop-perçu, auteur,
reconstruction (caisse, remboursement, sans mode), 403 pour la secrétaire.

**Retouches de test pour cause de fixture ou de décision** (aucun golden conservé ni assertion non décidée modifiés) :
`ReglementsEnregistresObserves` et `VentesEtReglementsCharacterizationIntegrationTest` (import du nouvel événement ; **revue du
2026-10-21 : constructeur d'avant F4 retiré**, le test C5 publie l'événement v2 avec `rid(facture)` et sans mode ; assertions de
contenu et goldens inchangés, sauf l'assertion de l'identifiant de source, qui était l'identifiant dérivé d'avant F4 et est
maintenant l'identifiant du règlement de l'événement) ;
`ExclusionEtForfaitCharacterizationIntegrationTest` (F2) : alias `<J-4>` ajouté au harnais et golden
`reinclusion_refus_etat_base_A_nouveau_code` réordonné, car il contenait une date littérale (`2026-10-16`, J-4 non nommé) qui a
expiré avec le calendrier ; l'ordre des séances suit le texte des alias (`<J-10>` passerait avant `<J-2>`), à trier par date réelle
avant la normalisation si un jeu de test change ; `ReglementsCharacterizationSupport.goldenEvenements` (le mode n'est écrit que s'il est présent) ;
`ReglementRestControllerIntegrationTest` (utilisateur de test portant son centre : un `User` Spring sans centre est refusé en 403) ;
deux assertions sur le nombre de règlements du centre B (1 → 0) dans `un_administrateur_du_centre_a_agit_sur_le_centre_b…` et
`le_centre_du_corps_n_est_pas_controle…` (KB-FAC-37 corrigé) ; `ModelesDePersistanceAlignesFacturationIntegrationTest` (méthode
ajoutée pour `facture_reglements`) ; noms des goldens des scénarios modifiés (`_nouveau_code`) avec un commentaire « Nouveau code F4 ».

### Tests

Domaine : `ReglementTest`, `SituationDeFactureTest` ; application : `EnregistrerUnReglementTest`, `ConsulterLesReglementsTest`
(doubles en mémoire `ReglementsEnMemoire`, `PublicationDesReglementsEnMemoire`) ; contrats mémoire / H2 / PostgreSQL 16 :
`ReglementsContrat` (+ `ReglementsJdbcContrat`, unicité de la clé), `ReglementsConcurrentsContrat` ; alignement des colonnes sur le
schéma figé (`facture_reglements`) ; ArchUnit du module.

**Preuve de l'idempotence** : `ReglementsConcurrentsContrat.should_create_one_payment_and_one_event_when_the_same_key_is_sent_twice_at_the_same_time`
— le premier enregistrement reste ouvert (sa publication attend) après avoir inséré ; le second envoi, même clé, **attend** (il ne
se termine pas en 700 ms) puis, le premier validé, répond la même situation : **1 règlement, 1 événement, 0 événement pour le
second**. Le test jumeau prouve qu'un second remboursement concurrent de 60 sur 100 réglés est refusé (422) après le premier.
Vert sur H2 et PostgreSQL 16 ; sans le `SELECT ... FOR UPDATE`, le second ne verrait rien et créerait un doublon.

**Résultats (2026-10-21)** : `./mvnw -o clean test` complet vert (1722 tests dans `platform`, 334 dans `contexts/facturation`, GMAO et
Comptabilité C1..C7 verts), `check-arch.ts` 0 violation. Mutation (PIT 1.30.0, `bash backend/scripts/pit-f4-reglements.sh`, domaine et
application des règlements) : **104 mutants tués sur 110 (95 %)**, couverture de lignes 99 % ; les 6 survivants sont des accesseurs
d'exception, `estUnRemboursement()` (non lu par le code de production), deux bornes de `Reglement.enregistrer` déjà couvertes par
les cas limites de `ReglementTest` mais équivalentes à l'arrondi, et la garde « facture disparue entre l'écriture et la lecture »
(inatteignable sous le verrou). Contrats PostgreSQL 16 (`facturation-pg16-rejeu.sh 55447`, cluster
jetable) : V1..V41 appliquées, V41 rejouable, `ReglementsPostgresContractIT` et `ReglementsConcurrentsPostgresContractIT` (19)
verts, F1 à F3 et TVA verts, code de sortie 0.

### Écarts de comportement (nouveau code)

1. 403 si le centre du corps ou de la requête n'est pas celui de la session (était : action sur le centre demandé) ; un centre
   inconnu donne 403 (était : 400 « facture introuvable » ou liste vide).
2. Auteur = utilisateur de la session (identifiant dans `saisi_par` et dans l'événement) ; le `userId` du corps est ignoré (était :
   le texte du corps, `system` par défaut).
3. 422 pour un remboursement supérieur au total déjà réglé (était : accepté, `montantRegle` négatif).
4. Remboursement comptabilisé en écriture de sens inverse (était : encaissement en valeur absolue) — **écart sur l'argent**.
5. Mode, référence, clé d'idempotence acceptés (étaient ignorés) ; mode inconnu : 400.
6. L'événement porte l'identifiant du règlement et le mode (v2) ; la Comptabilité écrit « source = règlement ». Le chemin
   « événement v1 » (constructeur à 5 arguments, branche `reglementId == null`, surcharge du langage publié, recherche du
   règlement par ses valeurs) est retiré (revue du 2026-10-21).
7. **Libellé des écritures de remboursement « Remboursement … » : gardé (décision utilisateur, 2026-10-21)** ; avant F4 le
   libellé était « Règlement … ». Écart visible dans les journaux (ADR 0033, décision 7 ; `known-bugs.md` KB-FAC-R03).
8. **Export CSV : cellules de texte neutralisées** (KB-FAC-R02, décision utilisateur du 2026-10-21) : toute cellule de texte
   (numéro de facture, assurance, nom, prénom, caisse, agence, centre payeur) qui commence par `=`, `+`, `-`, `@`, une tabulation
   ou un retour chariot est préfixée d'une apostrophe `'` ; les montants numériques ne sont pas touchés. Le classeur Excel n'est
   pas concerné : `setCellValue(String)` écrit des cellules de texte, jamais des formules (constaté dans `ExportDesSituations`).
   Golden `tableau-de-bord-export/csv_formules_neutralisees_nouveau_code`.

### Points à valider par le métier

1. La **table mode → journal** ci-dessus (chèque en banque, espèces en caisse) et la liste des modes (virement, carte absents).
2. Le **remboursement** : refus au-delà du total réglé (y compris du trop-perçu) et écriture de sens inverse.
3. La **clé d'idempotence** : renvoi = situation courante, contenu du renvoi ignoré.
4. Les remboursements **déjà comptabilisés** en valeur absolue avant le déploiement (point 20.3 de `verifications-production.md`) :
   à régulariser par extourne avec le comptable (aucune écriture existante n'est réécrite).
5. L'auteur est maintenant un **identifiant** (les anciens règlements gardent leur texte libre) : un écran qui affiche `saisiPar`
   montrera l'identifiant tant que le nom de l'utilisateur n'est pas résolu.

### Retiré par F6

Retiré ou passé par le langage publié par la slice F6 (fiche `facturation-f6-retrait-bc-facturation.md`, 2026-10-21) : le module `bc-facturation` n'existe plus.

### Plan de bascule

Refonte sur place (ADR 0024) : mêmes URL, pas de façade ni de flag ; retour arrière = commit précédent (V41 reste en place, les
colonnes sont ignorées par l'ancien code). **Déploiement** : migration V41 appliquée au démarrage (additive, rejouable) ;
contrôles de production : `verifications-production.md` point 20. **Frontend** : déjà adapté (mode, référence, clé d'idempotence,
403 et 422 ; `reglement-payment.util.ts`, `reglement.store.ts`) ; la liste ci-dessous reste le contrat à respecter. Reste à faire
côté frontend : vider aussi la clé d'idempotence quand le mode ou la référence changent (nit n5 de la revue) :

- saisie du **mode de règlement** (liste `BANQUE`, `CHEQUE`, `CAISSE`, `ESPECES`, facultative) et de la **référence de paiement** ;
  affichage de `modeReglement` / `referencePaiement` dans l'historique des paiements (champs absents pour les anciens règlements) ;
- envoyer une **clé d'idempotence** (`cleIdempotence`, par exemple un UUID généré à l'ouverture du formulaire) pour que le
  renvoi après une erreur réseau ne crée pas de doublon ;
- **403** si le centre envoyé n'est pas celui de la session (corps, recherche, tableau de bord, export, historique) ;
- **422** `REMBOURSEMENT_SUPERIEUR_AU_TOTAL_REGLE` (message explicite : afficher `detail`) ; **400** pour un mode inconnu ;
- l'auteur n'est plus saisi : le champ `userId` du corps est ignoré (peut être retiré) ; `saisiPar` est un identifiant pour les
  nouveaux règlements.
