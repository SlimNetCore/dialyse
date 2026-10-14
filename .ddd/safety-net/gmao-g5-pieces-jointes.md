---
status: proposed
date: 2026-10-12
slice: gmao-g5-pieces-jointes
sources: [.ddd/migration/slices/gmao-g5-pieces-jointes.md (note ADR 0024 : Strangler abandonné, refonte sur place), ADR 0019/0024, gmao-g4-interventions.md, known-bugs.md KB-GMAO-58 à 68]
---
# Filet de caractérisation — G5 pièces jointes d'intervention (refonte sur place)

## Périmètre : G5 contre G6
| Élément | Appartient à | Verrouillé ici ? |
|---|---|---|
| `InterventionDocumentRestController` : POST multipart, GET liste, GET `/{documentId}`, DELETE `/{documentId}` sous `/api/v1/gmao/interventions/{interventionId}/documents` | **G5 uniquement** (contrôleur non partagé avec G6) | oui |
| `DocumentIntervention` (+ `TypeDocumentIntervention`, port `DocumentInterventionRepositoryPort`), adaptateur, entité, dépôt JPA, `DocumentInterventionResponse` ; table `gmao_documents_intervention` (V7) | **G5 uniquement** : aucune autre classe de production ne les référence (grep) | oui (`covered.json`) |
| Lecture de l'intervention (`InterventionRepositoryPort.findById`) | G4 (déjà couvert) | observée : centre, id inconnu, supprimée logique |
| `/bon` (PDF), `/indicateurs`, `/chronologie` (`InterventionSuiviRestController`) | **G6** | non ; seul constat : aucune de ces routes ne cite une pièce (golden `consommateurs_ne_lisent_pas_les_pieces`) ; `/bon` et `/indicateurs` répondent 400 sur l'intervention semée, donc ce golden est **faible** : la preuve forte est l'absence de toute référence à la table hors G5 |
Pas de découpage à faire : G5 et G6 ne partagent aucun fichier legacy.

## Stockage et règles réellement appliquées
- **Binaires en base** : colonne `contenu BYTEA NOT NULL` de `gmao_documents_intervention` (id, intervention_id, centre_id, type, nom ≤ 150, content_type ≤ 50, taille, contenu, ajoute_par, ajoute_le) ; aucun fichier sur disque, aucun FK ni CHECK. Métadonnées de liste lues par projection (sans octets) ; téléchargement et suppression chargent la ligne entière.
- **R22** : type reconnu **aux premiers octets seulement** (`%PDF-`, `89 50 4E 47`, `FF D8 FF`), jamais au type déclaré ni à l'extension ; 5 Mo exacts acceptés, +1 octet refusé, vide refusé ; nom nettoyé (basename, caractères `"<>:*?|` et ASCII de contrôle retirés), 150 derniers caractères.
- **R23** : 50 par intervention, contrôle **avant** type/contenu, compte-puis-insère (course non verrouillée, non testée).
- Ordre des validations : autorisation (ADMIN) → intervention du centre → quota → type de pièce (`valueOf`) → contenu/taille/signature.
- Plafonds du conteneur (non observables en MockMvc) : `max-file-size=5MB`, `max-request-size=6MB` (configuration figée par golden).

## Point d'observation et harnais
HTTP via MockMvc multipart sur le contexte Spring complet (`@SpringBootTest` MOCK, profil `test`, Spring Security réelle, JPA réel sur **H2**) + lecture SQL de `gmao_documents_intervention` (octets compris), `gmao_interventions`, `notification_evenement`, `event_publication`. Harnais réutilisé : `GoldenMaster` (G1), scrubber UUID en alias, `ajouteLe` en `<HORLOGE>`. Interventions et pièces semées par SQL (indépendantes de G4). Aucun mock du code caractérisé, **aucun seam**, aucun code de production modifié.
- Tests : `backend/platform/src/test/java/com/hemodialyse/backend/characterization/gmao/documents/` : `DocumentsCharacterizationSupport`, `DocumentsEnvoiCharacterizationIntegrationTest` (25), `DocumentsLectureSuppressionCharacterizationIntegrationTest` (14), `DocumentsAutorisationsCharacterizationIntegrationTest` (9) = **48 tests**.
- Goldens : `backend/platform/src/test/resources/characterization/gmao/documents/*.approved.json` (48 fichiers, une ligne de texte par cas dans les matrices). Approuver : `-DargLine=-Dcharacterization.approve=true` (revue humaine du diff obligatoire).

## Scénarios (48 tests)
| Thème | Scénarios |
|---|---|
| Joindre, nominal | PDF sans type (classé AUTRE), PNG PHOTO, JPEG FACTURE, PDF BON_INTERVENTION ; octets et métadonnées en base ; type MIME déclaré ignoré ; extension non vérifiée ; doublon accepté |
| Type de pièce | matrice de 13 valeurs (casse, vide, espace, `null`, inconnu) ; type validé avant contenu et quota |
| Contenu | matrice de 21 signatures (limites 4/5 octets PDF, 7/8 PNG, 2/3 JPEG, BOM, casse, GIF/ZIP/MZ/HTML/SVG, vide) ; contenu après signature non vérifié |
| Taille | 5 Mo - 1, 5 Mo, 5 Mo + 1 octet ; configuration multipart du conteneur |
| Nom | matrice de 48 noms (traversée, `\`, `C:\`, `..`, contrôle, Unicode, RTL, JNDI, vide) ; longueurs 149/150/151/300/5000, emoji coupé |
| Quota | 50 acceptées, 51ᵉ refusée (valide, invalide, type invalide), autre intervention libre, réouverture après retrait ; quota masquant les autres erreurs ; 52 pièces semées |
| Intervention cible | statuts PLANIFIEE, EN_COURS, TERMINEE, ANNULEE, EN_ATTENTE_VALIDATION, supprimée logique, autre centre, inconnue ; id illisible |
| Multipart illisible | partie absente / mal nommée / vide / doublée, `type` répété, corps JSON/octet-stream/sans type/faux multipart, méthodes non prévues |
| Télécharger | octets exacts, en-têtes par type, `Content-Disposition` (12 noms), pièces héritées non conformes (html, MIME invalide, nom `../../`), centre de la pièce incohérent, erreurs (11 cas), tous statuts |
| Lister | vide, tri et métadonnées sans octets, pagination (22 combinaisons), statuts et isolation |
| Retirer | suppression physique, 204 sans corps, deuxième retrait, matrice d'erreurs et de statuts, aucun effet sur l'intervention ni sur les autres pièces |
| Autorisations | non ADMIN, habilitations GMAO sans ADMIN, anonyme, ADMIN ; refus avant validation ; isolation de centre sur les 4 routes dans les 2 sens ; traçabilité d'audit ; aucun effet de bord (notification, événement, intervention) |
| Parcours | joindre, lister, télécharger, retirer, relister |

## Mesures
- **Suite** : 3 exécutions consécutives vertes (la 1ʳᵉ en ordre par défaut, les 2 autres en ordre `MethodOrderer$Random`), 48/48, 0 flaky après correction d'un défaut du filet (ex aequo de `ajoute_le` dans le parcours complet, désormais daté en base).
- **Couverture** : lignes de `DocumentIntervention` 22/23 (96 %) par PIT ; le contrôleur est exercé sur ses 4 routes et toutes ses branches d'erreur (mesure JaCoCo non lancée).
- **Mutation PIT 1.30.0** sur `DocumentIntervention` (`backend/scripts/pit-g5-documents.sh`) : **43 mutants, 39 tués (91 %)**, force des tests 91 %, aucun mutant sans couverture. 4 survivants, tous acceptés : `creer` L30 et L32 (garde `interventionId/centreId == null` et `type == null`, inatteignables par HTTP car le contrôleur ne les fournit jamais nuls), `nettoyerNom` L62 (`nom == null`, inatteignable : un multipart donne `""`) et L65 (borne `> 150`, mutant équivalent : à exactement 150 caractères la troncature rend la même chaîne).

## Lancement
Depuis `backend/` : `./mvnw -o -pl platform -am test -Dtest='Documents*CharacterizationIntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false` ; ordre aléatoire : ajouter `'-DargLine=-Djunit.jupiter.testmethod.order.default=org.junit.jupiter.api.MethodOrderer$Random'`. Mutation : `./mvnw -o -q -pl platform -am test-compile`, `./mvnw -o -q -pl platform -am dependency:build-classpath -Dmdep.outputFile=<fichier>`, puis `CPFILE=<fichier> bash backend/scripts/pit-g5-documents.sh`.

## Seams
Aucun appliqué ni nécessaire ; deux seams optionnels décrits dans `seams.md` (horloge/UUID de `DocumentIntervention.creer`, course du quota). `seamAllowed` inchangé.

## Bugs connus
KB-GMAO-58 à 68 (`known-bugs.md`) : quota/course, contenu après signature non vérifié, extension non vérifiée, nom (`..`, U+202E, troncature), MIME stocké servi sans revérification, 500 sur erreurs de protocole, 400 au lieu de 404 et 403 au lieu de 401, statut d'intervention sans effet (y compris supprimée logique), suppression physique et téléchargement non tracé, mémoire, tri sans critère secondaire et `size` sans plafond, doublons.

## Non verrouillé (limites)
- **Course du quota** (deux POST simultanés au 50ᵉ) : non déterministe, lue dans le code uniquement.
- **Plafonds du conteneur** (`max-file-size`, `max-request-size`) : MockMvc ne passe pas par Tomcat ; seule la configuration est figée, pas le rejet réel (statut et corps d'un fichier de plus de 5 Mo ou d'une requête de plus de 6 Mo).
- Volumétrie réelle, performances, latence, mémoire sous 50 x 5 Mo ; comportement de PostgreSQL (`bytea`, tri des ex aequo) : tests sur H2.
- Le **bon d'intervention PDF** et la fiche équipement (G6) : indépendance démontrée statiquement, pas par un scénario contenant une pièce (les routes répondent 400 sur les données semées).
- Données historiques non conformes réelles (faux types, noms hostiles) : simulées par SQL, pas mesurées sur une copie de production.
- Ordre des ex aequo de `ajoute_le` ; horodatage exact (normalisé).
- Messages techniques des 500 (volontairement masqués dans les goldens).

## Questions ouvertes pour la conception sur place
1. **Stockage des binaires** : rester en `bytea` (mêmes tables, ADR 0024) ou passer au système de fichiers/objet ? Aujourd'hui aucun chemin n'est construit à partir du nom (pas de traversée) ; passer aux fichiers créerait ce risque.
2. **Défauts de sécurité** : valider la structure des fichiers (KB-59), refuser `..`/U+202E dans le nom (KB-60), re-nettoyer le nom et le MIME à la lecture (KB-61) : corriger ou reproduire ?
3. **Limites réelles contre R22/R23** : confirmer 5 Mo (octets exacts), 50 par intervention sans verrou (course acceptée), nom gardé sur ses 150 derniers caractères (R22 dit « nettoyé ≤ 150 »), type reconnu sur 3 à 8 octets seulement.
4. **Statut de l'intervention** : autoriser les pièces sur annulée/terminée/supprimée logique (comportement actuel, KB-64) ?
5. **Codes d'erreur** : conserver 400 pour « introuvable » et 500 pour les erreurs de protocole (parité) ou normaliser plus tard (transverse) ?
6. **Autorisation** : ADMIN seul (aucune habilitation GMAO propre) ; journaliser les téléchargements ?

## Suite donnée (2026-10-12)
Les six questions ci-dessus sont tranchées (voir `known-bugs.md`, « Décisions G5 ») : octets gardés en base ; KB-GMAO-59, 60, 61 corrigés ; limites 5 Mo exacts et 50 reproduites, avec quota réellement tenu (KB-GMAO-58) ; KB-GMAO-64 corrigé (422) ; codes d'erreur reproduits ; ADMIN seul et téléchargements non journalisés reproduits. La suite de 48 tests tourne désormais contre le nouveau code (`contexts/gmao`) ; onze scénarios ont des goldens `*_nouveau_code`, quatre ne changent que par la taille du PNG d'essai (69 octets).
