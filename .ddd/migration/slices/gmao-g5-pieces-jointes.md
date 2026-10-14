---
status: approved
date: 2026-10-12
slice: gmao-g5-pieces-jointes
order: 5 (score 18/30)
sources: [.ddd/tactical/gmao.md (PièceJointe, R22, R23, Q8), ADR 0019/0024 (amendement G5), .ddd/safety-net/gmao-g5-pieces-jointes.md, known-bugs.md KB-GMAO-58 à 68]
---

> **ADR 0024 (2026-10-11)** : le Strangler progressif est abandonné (ni façade, ni routage, ni projection, ni rattrapage, ni shadow, ni paliers, ni rollback à chaud). La slice est refaite **sur place** : mêmes tables, mêmes URLs, filet de caractérisation, livraison en une fois. L'ancienne version de cette fiche (plan Strangler) est remplacée par celle-ci.

# G5 — Pièces jointes d'intervention (joindre, lister, télécharger, retirer)

**Comportement observable** : un administrateur du centre joint un justificatif (PDF, PNG ou JPEG, 5 Mo au plus, 50 au plus par intervention), le liste, le télécharge ou le retire. `POST` multipart, `GET` liste, `GET /{documentId}`, `DELETE /{documentId}` sous `/api/v1/gmao/interventions/{interventionId}/documents`.

## État final
`built` (2026-10-12). Backend complet vert (`./mvnw -o test` : module 627 tests, platform 1125 dont 4 ignorés hors G5), `check-arch.ts` : 0 violation, G1 (36), G2 (39), G3 (54), G4 (67) inchangés et verts, G5 : 48 tests de caractérisation verts contre le nouveau code. Frontend non modifié. Rien n'est à retirer avec G6 pour cette slice.

## Réalisation (`backend/contexts/gmao`)
| Couche | Fichiers |
|---|---|
| domaine `domain/piecejointe/` | `PieceJointe` (agrégat satellite, une seule entité), `PieceJointeId`, `TypeDePiece`, `FormatReconnu` (structure PDF/PNG/JPEG), `NomDeFichier` (assainissement), `InterventionHote` (centre, statut, supprimée), `PieceJointeRepository`, événements `PieceJointeAjoutee` / `PieceJointeRetiree`, refus `FichierRefuse`, `QuotaDePiecesAtteint`, `InterventionFermee`, `TypeDePieceInconnu` |
| application `application/piecejointe/` | `JoindrePieceJointe`, `RetirerPieceJointe`, `ListerPiecesJointes`, `TelechargerPieceJointe`, ports `InterventionsHotes` (avec verrou) et `LecturePiecesJointes` (liste sans octets), `PieceJointeVue`, `PageDePiecesJointes`, `PieceTelechargee`, `PieceJointeIntrouvable` |
| infrastructure `infrastructure/persistence/` | `PieceJointeJpa` (table `gmao_documents_intervention`, colonnes historiques, `contenu` en `BYTEA`), `PieceJointeSpringData`, `PieceJointeMapper`, `PieceJointeRepositoryJpa`, `LecturePiecesJointesJpa`, `InterventionsHotesJpa` (+ `verrouiller` dans `InterventionSpringData`) ; événements : `JournalDesEvenements` écoute aussi `EvenementDePieceJointe` |
| interfaces `interfaces/http/` | `PieceJointeControleur`, `PieceJointeReponse` |
| configuration | `GmaoConfiguration` : 7 beans |
| tests (module) | domaine : `FormatReconnuTest` (37), `NomDeFichierTest` (33), `PieceJointeTest` (28) ; application : `CasDUsagePieceJointeTest` (28, doubles `PiecesJointesEnMemoire`, `InterventionsHotesEnMemoire`) ; contrats : `PieceJointeContrat` (12) contre le double et l'adapter JPA/H2 (+ 2 tests : la liste et le retrait ne lisent jamais `contenu`, SQL inspecté) et `PieceJointePostgresContractIT` ; `InterventionsHotesContrat` (7) contre le double, JPA/H2 (+ preuve du verrou) et PG16 ; outbox et concurrence : `RegistreDEvenementsPieceJointeTest` (4) |
| tests (platform) | 48 tests de caractérisation `documents/` (goldens inchangés sauf `*_nouveau_code`), `ModelesDePersistanceAlignesIntegrationTest` étendu (`pieces_jointes_d_intervention`) |

Choix de modèle : `PieceJointe` est un agrégat satellite (et non une entité de `Intervention`) pour ne jamais charger jusqu'à 250 Mo avec l'intervention ; la règle « intervention ouverte » est portée par `InterventionHote` ; le port `StockageDesPiècesJointes` du modèle tactique n'est pas créé (octets en base par décision). Une seule transaction par commande : intervention verrouillée, contrôles, écriture, publication des événements.

## Écarts de comportement décidés (utilisateur, 2026-10-12)
Détail, ancien comportement et impacts : `.ddd/safety-net/known-bugs.md`, « Décisions G5 ». Goldens `*_nouveau_code` : `contenu_apres_signature_non_verifie`, `extension_non_verifiee`, `matrice_nettoyage_nom`, `matrice_nom_long`, `matrice_signatures`, `matrice_statut_intervention_envoi`, `quota_masque_autres_erreurs`, `retirer_erreurs_et_statuts`, `telechargement_content_disposition`, `telechargement_pieces_heritees_non_conformes`, `type_mime_declare_ignore` (11). Quatre goldens ne changent que par la taille du jeu d'essai PNG (11 octets avant, 69 après) : `joindre_png_photo`, `liste_tri_et_metadonnees`, `parcours_complet`, `telechargement_entetes_par_type`. Les 33 autres sont inchangés.
- **KB-GMAO-59** : structure du fichier vérifiée et extension cohérente (des fichiers aujourd'hui acceptés seront refusés).
- **KB-GMAO-60** : nom assaini (`..`, contrôle, U+202E, U+0085, emoji non coupé ; espaces Unicode de tête et de queue retirés, dont l'espace insécable U+00A0, pour que `facture.exe` suivi d'une telle espace ne contourne pas le contrôle d'extension, revue 2026-10-12).
- **KB-GMAO-61** : nom et type MIME re-contrôlés à la liste et au téléchargement.
- **KB-GMAO-64** : joindre et retirer refusés (422) sur une intervention annulée, terminée ou supprimée : **on ne pourra plus joindre un justificatif après la clôture**.
- **KB-GMAO-58** : quota de 50 tenu sous concurrence (verrou de l'intervention).
- **Reproduits** : KB-GMAO-62 et 63 (500 protocole, 400 introuvable, 403 anonyme, texte `No enum constant …TypeDocumentIntervention`), KB-GMAO-65 (audit POST/DELETE seul, ADMIN seul, suppression physique), KB-GMAO-66 (5 Mo exacts, octets en mémoire), KB-GMAO-67 (liste, `size` sans plafond), KB-GMAO-68 (doublons, centre de la pièce ignoré).

## À valider par le métier
1. Refuser tout ajout et tout retrait après la clôture (ou l'annulation) : faut-il une exception pour un justificatif tardif ?
2. Refus de fichiers jusqu'ici acceptés (PDF sans `%%EOF` final ou suivi de données, JPEG sans EOI, noms dont l'extension ne correspond pas) : tolérance à prévoir pour certains scanners ?
3. Journaliser les téléchargements ? (reproduit : non). Plafonner `size` de la liste ? (reproduit : non).
4. Limites connues : `malware.exe.pdf` est accepté (seule la dernière extension compte) ; PDF et JPEG ne sont vérifiés que par leur en-tête et leur fin, un fichier polyglotte (PDF + HTML) passe (risque résiduel, antivirus hors périmètre). Le fichier est servi en pièce jointe (`attachment`, `nosniff`) avec un type limité à trois valeurs.

## Frontend (non modifié)
`fiche-intervention.component.ts` affiche des messages génériques (`GMAO.DOCUMENTS.UPLOAD_ERROR`, `DELETE_ERROR`) : l'écran doit désormais gérer le **422** (intervention close) et les nouveaux **400** (« Fichier PDF/PNG/JPEG incomplet ou altéré », « L'extension « .x » ne correspond pas au contenu… »), idéalement en affichant le `detail` renvoyé, et masquer ou désactiver l'envoi et le retrait pour une intervention terminée ou annulée. Le nom d'une pièce peut changer au dépôt (assaini) : afficher le nom renvoyé. Le type MIME du téléchargement peut être `application/octet-stream` pour une pièce historique non conforme.

## Mesures
- Suite : 48/48 verts, ordre par défaut ; module : 627 tests verts ; concurrence : le test échoue sans le verrou (vérifié en retirant le verrou), vert avec.
- Mutation PIT 1.30.0 sur `domain.piecejointe.*` (`backend/scripts/pit-g5-documents.sh`, réécrit pour le nouveau domaine) : voir « Mutation ».
- Rejeu PostgreSQL 16 : voir « PostgreSQL 16 » ci-dessous.

## Mutation
**252 mutants, 240 tués (95 %), force des tests 95 %, aucun mutant sans couverture** (ligne : 186/188), sur `FormatReconnu`, `NomDeFichier`, `PieceJointe`, `InterventionHote`, `TypeDePiece` et les événements, par les tests unitaires de domaine et de cas d'usage (20 s). Une première passe donnait 81 % (18 sans couverture : combinaisons valides de l'en-tête PNG, blocs auxiliaires, fenêtre de fin du PDF, bornes de version) ; 8 tests ont été ajoutés. 12 survivants non tués, tous des mutants de bornes (`<` / `<=`, `+`/`-` d'indices) à réexaminer si le domaine évolue, dans `FormatReconnu.lettres`, `enteteCoherent`, `pdfComplet`, `pngComplet` (longueur minimale de boucle) et `NomDeFichier.aRetirer` / `extension` ; aucun n'a été jugé équivalent à la main. Le filet de caractérisation d'origine avait 91 % sur l'ancien `DocumentIntervention` (43 mutants) ; le critère de la fiche d'origine (70 %) est tenu.

## PostgreSQL 16
`backend/scripts/gmao-pg16-rejeu.sh 55436` (cluster jetable, port 55436, jamais 5432, détruit en sortie ; log en arrière-plan) : **code de sortie 0**. Schéma Flyway pur : `gmao_documents_intervention` a 10 colonnes, `contenu` est un vrai `bytea`, **0 clé étrangère**. Contrats PG du module : `PieceJointePostgresContractIT` (14 : même suite que le double et H2, colonnes historiques, `bytea`), `InterventionsHotesPostgresContractIT` (8 : même suite + preuve que `SELECT ... FOR UPDATE` fait attendre une seconde transaction sur un vrai PostgreSQL 16), puis G1 à G4 et leurs suites HTTP (129 tests) toujours verts. La suite HTTP de G5 reste sur H2 (ses interventions sont semées en SQL sans les clés étrangères de `gmao_interventions`).

## Reste à retirer avec G6
Rien pour G5 : le contrôleur legacy, `DocumentInterventionResponse`, `DocumentIntervention`, `TypeDocumentIntervention`, `DocumentInterventionRepositoryPort`, son adaptateur, l'entité et le dépôt JPA sont **retirés** (aucune autre classe ne les référence ; le bon d'intervention PDF et le suivi ne lisent pas les pièces). Tests legacy retirés avec eux : `InterventionDocumentRestControllerTest`, `DocumentInterventionTest`, le test des documents de `InterventionCostQueriesIntegrationTest`. La table `gmao_documents_intervention` reste (même table, aucune migration).

## Données et plan de bascule
Mêmes tables, mêmes URLs, aucune migration pour G5 (la V36 du déploiement est celle des tâches de G4), aucune copie : pas de plan de bascule (ADR 0024). Vérifications de production G5 en lecture seule : `.ddd/migration/verifications-production.md` §13. Retour arrière = revenir au commit précédent.

## Retrait sans lire les octets (revue 2026-10-12, m2)
`RetirerPieceJointe` lit la pièce par `PieceJointeRepository.trouverPourRetrait` (requête sur les colonnes sauf `contenu`, octets vides) puis supprime par identifiant avec contrôle d'appartenance à l'intervention ; plus aucune lecture de jusqu'à 5 Mo. Preuve : `PieceJointeJpaContratTest.should_not_read_the_bytes_when_counting_or_withdrawing` (SQL inspecté, couvre aussi `trouverPourRetrait`) et le scénario de contrat `should_find_a_piece_for_withdrawal_with_its_fields_but_without_bytes` (double et adaptateur JPA).

## Dette connue (revue 2026-10-12, non traitée volontairement)
- Les tests d'outbox (harnais de caractérisation G4) attendent que **toute** la table `event_publication` se vide ; un écouteur en échec peut bloquer la suite (attendre par écouteur : plus tard).
- Fichiers du module, goldens et écouteurs ACL non suivis par git : les commiter pour que « goldens inchangés » soit vérifiable par diff.
- L'écouteur de platform (`GmaoEvenementsReformeListener`) importe le domaine interne du module (`domain.equipement.*`) au lieu d'un événement de `publishedlanguage` ; `arch-rules.json` exclut `backend/platform/**`, donc `check-arch` ne le voit pas.
