# 14 — Référentiels du centre, modèles de documents, imports et reprise de données

> Préfixes `RG-REF` (référentiels), `RG-DOC` (modèles de documents), `RG-MIG` (reprise de données). Le système **valide
chaque ligne** avant d'écrire, **n'enregistre rien tant qu'un fichier
> contient une anomalie**, **rapproche** au lieu de dupliquer et **défait** une reprise qui n'a pas encore servi.
> Sources : `ReferentialKind`, `ReferentialField`, `ReferentialValuesValidator`,
> `ReferentialAdminDomainService`, `ReferentialAdminTables`, `ReferentialAdminRestController`, `ModeleDocumentCatalog`,
> `ModeleDocumentProvisioner`, `ModeleDocumentPrinter`,
> `ModeleDocumentTemplateService`, `JrxmlSecurityValidator`, `DocumentRestController`, `TabularFileReader`,
> `MigrationDomainService`, `MigrationBatch`, `*Migrator`, `MigrationRestController`.

## 14.1 Référentiels administrables

- **RG-REF-001** — Référentiels propres à chaque centre, gérés par `ADMIN` (et `SUPERADMIN`) : **forfaits** (code,
  libellé, prix), **créneaux** (code, libellé), **salles** (code, nom, isolement oui/non, capacité en
  générateurs), **médecins traitants** (nom, prénom, spécialité), **caisses** (code, nom, type standard/vacancier),
  **agences** (code, nom, caisse), **centres payeurs** (code, nom, adresse, agence), **transporteurs** (nom, téléphone).
  Les générateurs ne sont plus un référentiel plat : ce sont des équipements GMAO (RG-GMA-010).
- **RG-REF-002** — **Clé naturelle** (unicité par centre, casse et espaces ignorés) : le **code** pour forfaits,
  créneaux, salles, caisses, agences, centres payeurs ; **nom + prénom** pour les médecins ; **nom**
  pour les transporteurs. Un doublon est refusé (`ALREADY_EXISTS`).
- **RG-REF-003** — **Hiérarchie** : une agence référence une caisse, un centre payeur référence une agence ; la
  référence se donne par identifiant (formulaire) ou par code (fichier) et doit exister dans le centre
  (`REFERENCE_NOT_FOUND`). Ordre d'import conseillé : ordre 1 (forfaits, créneaux, salles, médecins, caisses,
  transporteurs), puis agences, puis centres payeurs.
- **RG-REF-004** — **Validation d'un champ** (formulaire comme import) : obligatoire (`REQUIRED`) après application de
  la valeur par défaut ; texte à longueur maximale (`TOO_LONG`) ; téléphone au format
  `[+0-9 ().-/]` d'au moins 6 caractères (`INVALID_PHONE`) ; nombre décimal positif ou nul, virgule acceptée, arrondi à
  2 décimales, maximum 99 999 999,99 (`INVALID_NUMBER`, `NEGATIVE_NUMBER`,
  `NUMBER_TOO_LARGE`) ; entier **de 1 à 999** (`INVALID_INTEGER`, `INTEGER_OUT_OF_RANGE`) ; liste de valeurs fermée,
  accents et casse tolérés (`INVALID_VALUE`).
- **RG-REF-005** — **Suppression** : refusée tant que l'élément est utilisé (`REFERENTIAL_IN_USE` : patients, prises en
  charge, séances, équipements, autres référentiels) ; élément inconnu :
  `REFERENTIAL_NOT_FOUND`. Les salles servent aux patients et équipements ; les forfaits aux PEC et séances ; etc.
- **RG-REF-006** — La capacité d'une salle (en générateurs) limite l'affectation de générateurs (RG-PLN-061) ; la
  modifier **ne contrôle pas rétroactivement** les générateurs déjà affectés. L'indicateur
  d'isolement est une propriété de la salle (RG-PLN-004).
- **RG-REF-007** — Les listes sont paginées (20 par défaut, 100 maximum) avec recherche texte. Toute modification vide
  les caches de référentiels concernés (RG-TRV-040) ; les caches sont par centre.

## 14.2 Import de fichiers de référentiels

- **RG-REF-010** — Formats acceptés : fichiers tableur (Excel) ou texte tabulé ; **5 Mo maximum**
  (`IMPORT_FILE_TOO_LARGE`), non vide (`IMPORT_EMPTY_FILE`), classeur **non protégé par mot de passe**
  (`IMPORT_ENCRYPTED_FILE`), lisible (`IMPORT_UNREADABLE_FILE`), format reconnu (`IMPORT_UNSUPPORTED_FORMAT`). Un
  **modèle** de fichier avec exemples est téléchargeable pour chaque référentiel.
- **RG-REF-011** — Les colonnes sont reconnues par leur libellé ou leurs **alias** ; une colonne inconnue est ignorée
  (signalée) ; une colonne obligatoire absente rejette le fichier ; **5 000 lignes
  maximum** (`TOO_MANY_ROWS`) ; **500 anomalies** maximum affichées.
- **RG-REF-012** — **Vérification à blanc** (« dry run ») disponible : elle produit le compte rendu (lignes à créer, à
  mettre à jour, anomalies) sans rien écrire. L'import réel est **tout ou rien** : une
  seule anomalie (valeur invalide, `DUPLICATE_IN_FILE`, référence introuvable…) empêche toute écriture. Une ligne dont
  la clé naturelle existe **met à jour** l'élément ; sinon elle le **crée**.

## 14.3 Modèles de documents (tout rapport imprimé est un modèle)

- **RG-DOC-001** — Documents livrés : fiche signalétique patient, attestation d'ouverture de droit, prise en charge,
  liste des patients, liste des PEC, liste des attestations, synthèse mensuelle de
  facturation, ordonnance, procès-verbal d'inventaire, bon d'intervention GMAO, planning de présence des infirmiers,
  cahier de dialyse (RG-SEA-045).
  **Aucun rapport ne contourne le catalogue** : déclaration au catalogue, provisionnement
  par centre (au démarrage et à la première impression), impression **uniquement** via le service d'impression des
  modèles. Un test échoue si un modèle livré n'est pas au catalogue.
- **RG-DOC-002** — Pour chaque centre, un modèle peut être **désactivé** (jamais réactivé automatiquement) ; sinon le
  modèle actif imprime la **version personnalisée active** si elle existe, sinon le
  modèle d'origine. L'identité de la société et du centre (logo, en-tête, pied de page, coordonnées) est **posée par le
  serveur** et ne peut être retirée d'un modèle personnalisé.
- **RG-DOC-002b** — Imprimer un document dont le modèle est **désactivé** pour le centre est refusé
  (`DOCUMENT_MODEL_INACTIVE`) ; toute autre défaillance de génération est signalée par `DOCUMENT_GENERATION_FAILED`
  avec la cause.
- **RG-DOC-003** — **Personnalisation** (`ADMIN`, `SUPERADMIN`) : téléverser une version, voir l'historique, **réactiver
  une ancienne version**, revenir au modèle d'origine, télécharger la source. Toute opération
  est bornée au centre ; un modèle d'un autre centre est inexistant ; commentaire de version 500 caractères maximum ;
  nom de modèle sûr (`[A-Za-z0-9_-]+.jrxml`).
- **RG-DOC-004** — **Un modèle téléversé est refusé s'il n'est pas sûr** (liste blanche) : fichier ≤ 512 Ko
  (`TOO_LARGE`), XML sans DOCTYPE ni entité externe, ≤ 5 000 éléments, éléments de mise en page
  uniquement (pas de scriptlet, sous-rapport, graphique), **requête SQL strictement identique à celle du modèle
  d'origine**, paramètres et champs sous-ensemble de l'original avec mêmes types, en-tête
  société/centre conservé, seule image autorisée = logo de la société (jamais de chemin ni d'URL), expressions limitées
  (≤ 2 000 caractères, grammaire minimale, aucun appel de méthode arbitraire).
  Aucune donnée n'est conservée si une anomalie est détectée.
- **RG-DOC-005** — Les modèles utilisent les polices standard uniquement (aucune police externe n'est garantie sur le
  serveur) ; l'impression produit un PDF ou un HTML.
- **RG-DOC-006** — La liste des modèles (types et modèles du centre) est paginée ; création, modification et suppression
  d'un modèle de document : `ADMIN`, `SUPERADMIN` ; l'impression est ouverte aux profils
  de l'écran appelant.

## 14.4 Reprise de données d'un logiciel existant

- **RG-MIG-001** — La reprise se fait par **lot** (`MigrationBatch`) propre à un centre : libellé obligatoire
  (`MIGRATION_LABEL_REQUIRED`), système d'origine facultatif, **date de début de reprise** facultative
  non future (`MIGRATION_START_IN_FUTURE`) qui borne les données historiques (les lignes antérieures sont ignorées avec
  avertissement `OUT_OF_PERIOD`). **Un seul lot actif par centre**
  (`MIGRATION_BATCH_ALREADY_OPEN`) ; lot inconnu : `MIGRATION_BATCH_NOT_FOUND`. Réservé à `ADMIN` (son centre) et
  `SUPERADMIN`.
- **RG-MIG-002** — Statuts d'un lot : `EN_COURS` (fichiers importables **autant de fois que nécessaire**, la reprise est
  rejouable), `TERMINE`, `ANNULE` ; passé `TERMINE` ou `ANNULE`, plus aucun import (`MIGRATION_BATCH_CLOSED`).
- **RG-MIG-003** — **Ordre des données** : 1 assurés, 2 patients, 3 affectations assuré↔patient, 4 attestations, 5
  prises en charge, 6 dossiers médicaux, 7 antécédents, 8 sérologies, 9 abords vasculaires, 10 résultats
  d'analyses, 11 séances, 12 soldes d'ouverture. Les référentiels du centre (RG-REF) doivent exister avant les
  patients ; chaque donnée rattachée à un patient le désigne par son **identifiant d'origine**
  (`PATIENT_NOT_MIGRATED` s'il n'a pas été repris).
- **RG-MIG-004** — Mêmes règles de fichier que les référentiels (5 Mo, 5 000 lignes, 500 anomalies, modèle
  téléchargeable, alias de colonnes, vérification à blanc, **tout ou rien par fichier**). Les valeurs de
  l'ancien logiciel sont converties par **correspondances de valeurs** enregistrées par le centre (colonne à liste
  fermée seulement : `MIGRATION_MAPPING_COLUMN_INVALID`, valeur d'origine obligatoire
  `MIGRATION_MAPPING_SOURCE_REQUIRED`, cible dans les valeurs autorisées `MIGRATION_MAPPING_TARGET_INVALID`) ; entité
  non disponible : `MIGRATION_ENTITY_UNSUPPORTED`.
- **RG-MIG-005** — **Assurés** : le n° d'assurance est **unique sur toute la plateforme** : un n° déjà rattaché à un
  autre centre est refusé (`OTHER_CENTER`) ; déjà présent dans le centre, l'assuré est **rapproché** (mis à jour) et non
  dupliqué (`MATCHED_EXISTING`) ; doublons dans le fichier refusés (`DUPLICATE_IN_FILE`) ; conflit d'identifiant
  d'origine (`LEGACY_ID_CONFLICT`).
- **RG-MIG-006** — **Patients** : nom, prénom, n° d'assurance, date de naissance et d'admission obligatoires ; naissance
  non future (`DATE_IN_FUTURE`), admission non antérieure à la naissance (`INCONSISTENT_DATES`) ; pour un état
  transféré, décédé, greffé ou guéri la date d'événement est attendue (`EVENT_DATE_MISSING`) ; un patient du centre au
  même n° d'assurance est **rapproché**
  (`MATCHED_EXISTING`), un n° pris par un autre patient est refusé (`NUMERO_USED_BY_OTHER`), un code patient déjà pris
  est refusé (`CODE_ALREADY_USED`), un homonyme probable est signalé (`POSSIBLE_DUPLICATE`) ; un patient ayant droit
  exige le n° de l'assuré (`REQUIRED`) qui doit exister (`ASSURE_NOT_FOUND`). Une reprise **n'efface jamais** une donnée
  déjà saisie : seules les valeurs
  renseignées sont reportées. Un générateur situé dans une autre salle est signalé (`GENERATEUR_OTHER_ROOM`).
- **RG-MIG-007** — **Affectations assuré↔patient** : patient et assuré repris (`PATIENT_NOT_MIGRATED`,
  `ASSURE_NOT_FOUND`), fin non antérieure au début, une affectation principale ne peut être terminée (`PRIMARY_ENDED`)
  et il n'y a **qu'une affectation principale** par patient (`DUPLICATE_PRIMARY`).
- **RG-MIG-008** — **Données historiques** (attestations, PEC, dossiers médicaux, antécédents, sérologies, abords,
  analyses, séances) : une ligne déjà présente (même clé métier) est **mise à jour** ou, si la
  donnée ne l'autorise pas, **conservée** (`ALREADY_PRESENT_KEPT`) ; dates incohérentes refusées (`INCONSISTENT_DATES`),
  dates futures refusées (`DATE_IN_FUTURE`), ligne d'analyse sans aucun résultat refusée (`NO_RESULT`), élément actif
  mais terminé signalé (`ACTIVE_BUT_ENDED`). Une **séance** historique est reprise dans son état final : **`FACTUREE`**
  si elle était facturée dans l'ancien logiciel (jamais
  refacturée), sinon **`SIGNEE`** (donc facturable) ; une séance déjà présente n'est jamais modifiée.
- **RG-MIG-009** — **Soldes d'ouverture** : seules les factures **non soldées** sont reprises avec le montant déjà
  encaissé ; montant positif (`INVALID_AMOUNT`), réglé ≤ facturé (`OVERPAID`), période et date
  valides, factures soldées ignorées (`FULLY_PAID`), facture déjà encaissée dans la plateforme non modifiable
  (`PAID_IN_PLATFORM`).
- **RG-MIG-010** — **Annulation d'un lot** : supprime les données **créées** par le lot (dans l'ordre inverse des
  dépendances) et ses correspondances ; **refusée si une donnée reprise a déjà été utilisée**
  (`MIGRATION_CANCEL_BLOCKED` avec la liste des blocages). Terminer un lot est définitif. Chaque exécution de fichier
  est journalisée (fichier, lignes, créations, mises à jour, anomalies, auteur, date).
