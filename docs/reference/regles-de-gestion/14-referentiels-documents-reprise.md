# 14 — Référentiels du centre, modèles de documents, imports et reprise de données

> Préfixes `RG-REF` (référentiels), `RG-DOC` (modèles de documents), `RG-MIG` (reprise de données). Le système **valide
chaque ligne** avant d'écrire, **n'enregistre rien tant qu'un fichier
> contient une anomalie**, **rapproche** au lieu de dupliquer et **défait** une reprise qui n'a pas encore servi.
> Sources : `ReferentialKind`, `ReferentialField`, 
> `ReferentialAdminDomainService` (façade fine qui distribue aux contextes, ADR 0161), `ReferentialAdminRestController`, `ModeleDocumentCatalog`,
> `ModeleDocumentProvisioner`, `ModeleDocumentPrinter`,
> `GererLesGabarits`, `HistoriqueDesVersions`, `JrxmlSecurityValidator`, `DocumentRestController`, `TabularFileReader`,
> `MigrationDomainService`, `MigrationBatch`, `*Migrator`, `MigrationRestController`.

## 14.1 Référentiels administrables

- **RG-REF-001** — Référentiels propres à chaque centre, gérés par `ADMIN` (le `SUPERADMIN` est refusé : 403 `ROLE_SCOPE`,
  décision reproduite à confirmer par les métiers avant le 2026-12-15). Une seule URL d'administration ; elle distribue chaque
  référentiel au contexte qui le publie (descripteurs de Patient et couverture, PL11-1 ; créneaux de l'Organisation, PL11-2 :
  supprimer un créneau déjà utilisé par un patient, une séance, un planning ou une affectation d'infirmier le désactive au lieu de
  l'effacer, et si l'usage ne peut être vérifié la suppression est refusée ; ressaisir le code d'un créneau désactivé le réactive,
  à confirmer par les métiers avant le 2026-12-15) ; JSON seul (405/406/415 propres), audit
  avec le type de référentiel et l'identifiant créé : **forfaits** (code,
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
  `REFERENTIAL_NOT_FOUND`. **Créneaux (PL11-2)** : un créneau utilisé est désactivé (204) au lieu d'être supprimé ; si l'usage ne
  peut être vérifié, la suppression est refusée (`REFERENTIAL_USAGE_UNVERIFIABLE`, 422). **Médecins (PL11-3, ADR 0159)** : un
  médecin, utilisé ou non, est désactivé (204, historique conservé) ; créer ou réimporter le nom et le prénom d'un médecin désactivé
  le réactive ; un nom et un prénom (casse et espaces ignorés) désignent un seul médecin par centre, dans l'administration comme dans
  la route d'Organisation. **Salles (PL11-4, ADR 0160)** : une salle référencée par un patient, un mouvement, une séance, un
  planning, un infirmier ou un équipement est désactivée (204) au lieu d'être supprimée et n'est plus proposée à la
  planification ; une salle modifiée ou réimportée sans capacité ni isolement les conserve ; si l'usage ne peut être
  vérifié, la suppression est refusée (`REFERENTIAL_USAGE_UNVERIFIABLE`). Les forfaits aux PEC et séances ; etc.
  Pour un **transporteur**, seuls comptent les patients **du centre du transporteur** (aller ou retour), un patient d'un
  autre centre ne bloque pas la suppression (KB-RF1-03 corrigé, ADR 0123, à confirmer par les métiers avant le
  2026-12-15). Sur les routes d'administration des transporteurs, un corps ou un paramètre illisible répond **400**
  `BAD_REQUEST` (et non plus 500) ; le refus de droits (403) passe avant (KB-RF1-01 corrigé).
  Pour un **forfait** (catalogue de forfaits de Patient et couverture, RF2, ADR 0124), le refus compte les prises en
  charge (forfait demandé ou effectif) **du centre du forfait** (une prise en charge d'un autre centre ne bloque pas la
  suppression), ainsi que les lignes de facture et les séances qui imposent le forfait (KB-RF2-02 corrigé, ADR 0125, à
  confirmer par les métiers avant le 2026-12-15). Sur les routes d'administration des forfaits, un corps ou un paramètre
  illisible répond **400** `BAD_REQUEST` (et non plus 500), de même qu'un `centerId` absent de la liste de choix ; le refus
  de droits (403) passe avant (KB-RF2-01 corrigé). Le **prix** d'un forfait est facultatif :
  un prix omis à la modification **efface** le prix ; le sens d'un prix absent reste à définir (KB-RF2-04).
  Pour une **caisse d'assurance**, une **agence** et un **centre payeur** (chaîne des payeurs de Patient et couverture,
  RF3a, ADR 0126), le refus compte les agences d'une caisse, les centres payeurs d'une agence et les patients d'un centre
  payeur (comptés par identité **et limités au centre** : une ligne d'un autre centre ne retient jamais un payeur,
  KB-RF3-05 corrigé, ADR 0130, à confirmer par les métiers avant le 2026-12-15). Sur **toutes** les routes d'administration des référentiels (caisses,
  agences, centres payeurs, mais aussi forfaits, transporteurs, créneaux, salles, médecins et générateurs), un corps, un
  identifiant, un centre ou une page illisible répond **400** `BAD_REQUEST` (et non plus 500), de même qu'un `centerId`
  absent des listes de choix des payeurs et des catégories de transport ; le refus de droits (403) passe avant
  (KB-RF3-01 à 03 corrigés, ADR 0129, à confirmer par les métiers avant le 2026-12-15).
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
  de l'écran appelant. Voir RG-DOC-010 pour la gestion des modèles.
- **RG-DOC-010** — **Gestion des modèles de document (contexte Documents, DOC4a, ADR 0164)** : mêmes URL, rôles et codes qu'avant
  (liste `GET /modeles?centerId=` et recherche `POST /modeles/search` : toute session ; création, modification, suppression :
  `ADMIN`, `SUPERADMIN`). Le centre demandé doit être celui de la session (`403` sinon ; `SUPERADMIN` transverse). Un modèle ne peut
  désigner qu'un gabarit **livré** : `reports/<nom>.jrxml` (lettres sans accent, chiffres, `-`, `_`), jamais un chemin libre (`400`).
  Code, libellé, type de document et format d'impression sont obligatoires et bornés (50, 255, 50, 20 caractères ; description 500,
  facultative) : `400` sinon ; la recherche exige une page de 0 ou plus et une taille de 1 à 200 (`400` sinon, jamais ramenée). Un code
  déjà pris dans le centre, ou un identifiant déjà attribué, répond `409 CONFLICT` (corrigé le 2026-11-13, KB-DOC4A-4). Supprimer un
  modèle supprime aussi l'historique de ses versions de gabarit, en une seule opération. Les formes illisibles sont propres : `400`
  (paramètre, identifiant ou corps), `415` (corps non JSON), `405` avec `Allow` (verbe non géré), `406` (`Accept` indisponible)
  (KB-DOC4A-1, 2) ; le profil est contrôlé avant la lecture du corps (`403` avant `400`, KB-DOC4A-3). Création, modification,
  suppression et recherche ont leur propre action d'audit `MODELE_CREE`, `MODELE_MODIFIE`, `MODELE_SUPPRIME`, `MODELE_RECHERCHE`
  (KB-DOC4A-5). Reproduits, à confirmer par les métiers/DPO avant le 2026-12-15 : modifier ou supprimer un modèle inexistant ou d'un
  autre centre répond `200` sans rien changer ; code et libellé peuvent être blancs ; type et format sont libres ; le filtre de type
  de la liste distingue majuscules et minuscules (KB-DOC4A-6, 7).
- **RG-DOC-011** — **Types de document proposés (contexte Documents, DOC4b, ADR 0165)** : `GET /api/v1/documents/types`, toute session,
  même URL et mêmes champs (`code`, `label`) qu'avant. La liste est celle des types que les contextes **déclarent** à Documents (langage
  publié : code en majuscules, libellé, gabarit par défaut, version), dans l'ordre des déclarations, suivie du rapport personnalisé
  `CUSTOM` que Documents seul possède ; un type déclaré deux fois, ou le code `CUSTOM` déclaré par un autre, empêche le démarrage. Les
  types livrés sont déclarés par leur contexte propriétaire depuis DOC4c (ADR 0166) : Documents pour les six documents du patient et de
  ses listes, Facturation (synthèse mensuelle), Dossier médical (ordonnance), Stock (inventaire), GMAO (bon d'intervention),
  Planification (présence, planning optimisé), Séance (cahier de dialyse) et Pilotage de la direction (rapport) ; l'ordre est
  inchangé. Un verbe d'écriture répond `405`
  avec `Allow` (journalisé sous `TYPE_DE_DOCUMENT_ECRITURE_REFUSEE`) et un `Accept` indisponible `406` (corrigé le 2026-11-13, KB-DOC4B-1, 2).
  La liste ne dépend ni du profil ni du centre (reproduit, à confirmer par les métiers/DPO avant le 2026-12-15, KB-DOC4B-3).
- **RG-DOC-012** — **Règles d'impression des types sensibles (publiées à Documents, DOC4c, ADR 0166)** : chaque contexte qui possède un
  type de document publie sa règle (garde de profil et de centre, paramètres posés par le serveur) ; Documents l'applique par le code
  du type (sans casse ni espaces), **avant toute validation de la demande et toute lecture de données** (`403` avant `400`). Ordonnance
  (`ORDONNANCE`, Dossier médical, KB-MED-18) : `MEDECIN` ou `ADMIN` (même s'ils portent d'autres rôles), puis le centre du corps doit
  être celui de la session (`SUPERADMIN` transverse) ; `403 ACCESS_DENIED` sinon ; seule la voie par type est gardée (reproduit, à
  confirmer avant le 2026-12-15, KB-DOC4C-1). Synthèse mensuelle (`SYNTHESE_FACTURATION_MENSUELLE`, Facturation, KB-FAC-52 et 60) :
  `ADMIN` ou `SECRETAIRE`, même profil que `/facturation/synthese/print`, sur les deux voies (par type et par modèle, d'après le type du
  modèle chargé, jamais un paramètre du client) ; les montants globaux sont les totaux enregistrés des factures de la période (ADR 0034),
  posés par le serveur et prioritaires sur ceux du client ; période absente ou inversée : aucun total posé ; période illisible : échec
  d'impression (`500`). Un SUPERADMIN seul est refusé pour ces deux types (KB-DOC4C-2). Un type sans règle n'est pas gardé (RG-DOC-007).
- **RG-DOC-007** — **Impression (contexte Documents, DOC1, ADR 0142)** : `POST /api/v1/documents/print` (par type) et
  `/print/{modeleId}` (par modèle). Tout profil authentifié imprime un type sans règle propre (matrice rôle x type de document
  **à confirmer avant le 2026-12-15**) ; l'anonyme est refusé. L'ordonnance et la synthèse mensuelle de facturation ont leur règle
  propre, publiée par leur contexte (RG-DOC-012). Le centre demandé doit être celui de la session (`403 ACCESS_DENIED`
  sinon ; `SUPERADMIN` transverse). Les paramètres d'identité (`CENTER_ID`, `SOCIETE_*`, `CENTRE_*`) sont toujours
  fournis par le serveur : la valeur du client est ignorée. Une entrée illisible (corps absent ou cassé, identifiant de
  modèle non UUID) est refusée en `400 BAD_REQUEST`. Dette d'affichage reproduite : dates avec `00:00`, document sans
  données imprimé vide, format inconnu servi en PDF.
- **RG-DOC-008** — **Moteur de rendu (contexte Documents, DOC2, ADR 0162)** : le rendu (Jasper), la validation de sécurité
  des gabarits téléversés (liste blanche JRXML), leur compilation, le catalogue des types livrés et le provisionnement du
  modèle d'un centre sont portés par le contexte Documents ; aucun comportement ne change. Un refus de rendu (type inconnu
  `DOCUMENT_TYPE_UNKNOWN`, modèle désactivé `DOCUMENT_MODEL_INACTIVE`, génération impossible `DOCUMENT_GENERATION_FAILED`)
  répond `422` avec ce code. Les caractères hors de la police du document (japonais, étoile, arabe) restent perdus sans
  erreur (dette KB-PL6-4, KB-PL7-3, KB-PL8-3, KB-PL9-6 : décision de l'utilisateur en attente, ADR 0162).
  **DOC5a (ADR 0193)** : les gabarits qui embarquent encore une requête SQL (11 sur 14) la lisent par un **port de connexion transitoire**
  (`ConnexionDeRendu` : connexion de lecture seule, valable le temps du rendu, refermée) ; le moteur ne connaît aucune table. Un gabarit dont
  le contexte propriétaire fournit les lignes (`LIGNES`) n'ouvre aucune connexion : déjà le rapport de la direction et la synthèse
  mensuelle, désormais le **cahier de dialyse** (RG-SEA-045b). Règle de transition de RG-DOC-004 : la requête d'un gabarit livré sans requête
  est « identique » quand elle est absente ; un modèle personnalisé déjà stocké avec l'ancienne requête continue de s'imprimer (KB-DOC5A-1).
  **DOC5b (ADR 0194)** : le **bon d'intervention** (GMAO, RG-GMA-068) est converti à son tour (jeu de données fourni par la GMAO, même règle de
  transition, KB-DOC5B-1) : 10 gabarits sur 14 embarquent encore une requête.
- **RG-DOC-009** — **Versions de gabarit par centre (contexte Documents, DOC3, ADR 0163)** : mêmes URL, mêmes rôles
  (`ADMIN`, `SUPERADMIN`) et mêmes codes qu'avant (RG-DOC-003, 004). Chaque dépôt prend le numéro suivant (jamais réutilisé, même
  après un retour au modèle d'origine), désactive les autres et devient la version en vigueur ; réactiver un numéro inexistant
  répond 404 sans corps ; le retour au modèle d'origine garde l'historique. Les règles de forme sont désormais propres :
  paramètre illisible `400 BAD_REQUEST`, corps non multipart `415 UNSUPPORTED_MEDIA_TYPE`, partie `file` absente `400`, verbe non
  géré `405` avec `Allow` (corrigé le 2026-11-13, KB-DOC3-1) ; le centre est contrôlé avant la forme des autres paramètres
  (`403` avant `400`, KB-DOC3-2) ; le dépôt, l'activation et le retour au modèle d'origine ont leur propre action d'audit
  `GABARIT_TELEVERSE`, `GABARIT_ACTIVE`, `GABARIT_REINITIALISE` (KB-DOC3-3). Reproduits, à confirmer par les métiers/DPO avant le
  2026-12-15 : retour au modèle d'origine d'un modèle sans gabarit livré et répété = `204` ; `Accept: application/xml` sur la
  liste répond en XML ; collision de dépôts simultanés (clé unique modèle + numéro) = `500 DATA_ACCESS_ERROR`.

## 14.4 Reprise de données d'un logiciel existant

- **RG-MIG-001** — La reprise se fait par **lot** (`MigrationBatch`) propre à un centre : libellé obligatoire
  (`MIGRATION_LABEL_REQUIRED`), système d'origine facultatif, **date de début de reprise** facultative
  non future (`MIGRATION_START_IN_FUTURE`) qui borne les données historiques (les lignes antérieures sont ignorées avec
  avertissement `OUT_OF_PERIOD`). **Un seul lot actif par centre**
  (`MIGRATION_BATCH_ALREADY_OPEN`) ; lot inconnu : `MIGRATION_BATCH_NOT_FOUND`. Réservé à `ADMIN` (son centre). Le
  `SUPERADMIN` est **refusé** (`403 ROLE_SCOPE`, filtre de portée des rôles : il n'accède à aucune donnée d'un centre) ;
  maintenu par l'ADR 0168 (KB-S6a-6), **à confirmer métiers/DPO ≤ 2026-12-15**. Le détail d'un lot liste **une seule**
  exécution (la dernière) par donnée, y compris à horodatage égal (KB-S6a-7).
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
  renseignées sont reportées. Un générateur situé dans une autre salle est signalé (`GENERATEUR_OTHER_ROOM`). Les
  **jours de dialyse** s'écrivent par leur code (Lun, Mar, Mer, Jeu, Ven, Sam, Dim) ou leur nom complet (Lundi…
  Dimanche), séparés par une virgule, « et » ou un blanc ; tout autre mot est refusé (`INVALID_DAYS`) — « Lunch » n'est
  plus accepté (KB-S6a-4, ADR 0168).
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
  est journalisée (fichier, lignes, créations, mises à jour, anomalies, auteur, date). Depuis RP1 (ADR 0169) la suppression
  des patients, assurés et affectations créés par le lot est faite par **Patient et couverture** (langage publié
  `AnnulationDeLaReprise`, bornée au centre) ; comportement inchangé. Depuis RP2 (ADR 0170) la lecture « séance complétée » (volet
  médical ou paramédical) est faite par la **Séance** (`SeancesDeLaReprisePubliees.laSeanceEstCompletee`) et la lecture et la
  suppression des données médicales et des analyses du lot par le **Dossier médical** (langage publié `DonneesMedicalesDeLaReprise`,
  suppression bornée au centre) ; comportement inchangé. Depuis RP3a (ADR 0171) l'**import** des attestations et des prises en
  charge (recherche d'une ligne déjà présente par patient, centre et début, ajout, mise à jour des seules valeurs fournies) et
  leur **suppression** à l'annulation sont faits par **Patient et couverture** (langages publiés `RepriseDeLHistoriqueDeCouverture`
  et `AnnulationDeLaReprise`, tout borné au centre) ; comportement inchangé. Depuis RP3b (ADR 0172) l'**import** du dossier de
  base, des antécédents, des sérologies, des abords vasculaires et des analyses (recherche d'une ligne déjà présente par clé et
  centre, ajout daté, mise à jour des seules valeurs fournies sans jamais changer le patient) est fait par le **Dossier médical**
  (langage publié `RepriseDeLHistoriqueMedical`, tout borné au centre), et le contrôle des **bons de sortie** d'un patient repris
  par le **Stock** (langage publié `BonsDeSortieDeLaReprise`, lecture seule) ; comportement inchangé. Depuis RP3c (ADR 0173) un test d'architecture garantit que l'outil de reprise ne nomme plus aucune
  table d'un autre contexte (séances par `SeancesDeLaReprisePubliees`, soldes d'ouverture par la **Facturation**, `RepriseDesSoldes`) ; comportement inchangé. L'écriture directe des colonnes de place de la fiche
  par la reprise des patients est gardée (**à confirmer métiers/DPO avant le 2026-12-15**). **Blocages** : un patient repris est refusé s'il a une
  prise en charge, une attestation, une séance, une facture ou des données de dossier médical que le lot n'a pas créées (le
  refus nomme la première catégorie contrôlée) ; une séance reprise est refusée si elle est facturée (motif prioritaire) ou
  complétée par un volet dans la plateforme ; un assuré repris est refusé s'il est rattaché à une affectation ou à un
  patient du centre que le lot n'a pas créés. **Constats reproduits, à confirmer métiers/DPO avant le 2026-12-15** (KB-S6a-8,
  KB-S6a-9) : un patient modifié sur sa fiche depuis l'import est supprimé sans avertissement ; les contrôles de prise en
  charge, d'attestation et de séance ne sont pas bornés au centre.
