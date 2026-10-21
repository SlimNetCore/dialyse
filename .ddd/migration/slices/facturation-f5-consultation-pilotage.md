---
status: approved
date: 2026-10-21
slice: facturation-f5-consultation-pilotage
order: 5 (F1 à F7, voir `.ddd/migration/roadmap.md` § 1 ter)
sources: [.ddd/tactical/facturation.md, docs/reference/regles-de-gestion/12-facturation-reglements-comptabilite.md (RG-FAC-024 à 026), ADR 0013, 0019, 0024, 0030, 0034, .ddd/safety-net/facturation-f5-consultation-pilotage.md, known-bugs.md (KB-FAC-52 à 66)]
---

> **Refonte sur place (ADR 0024)** : mêmes tables, mêmes URL, mêmes DTO JSON ; **aucune migration** ; retour arrière = commit
> précédent. Pas de façade, de routage, de projection ni de métriques.

# F5 — Consultation et pilotage de la facturation

**Comportement observable** : un administrateur, un médecin avec un autre rôle ou une secrétaire consulte le tableau de bord de
facturation d'un mois et imprime la synthèse mensuelle ; la Direction lit la part financière de la facturation (chiffre
d'affaires, encaissé, répartition par caisse, part du CA des absences). Lectures seules : aucune route de F5 n'écrit.

| Route                                        | Rôles                         | Nouveau code                                                                                           |
|----------------------------------------------|-------------------------------|--------------------------------------------------------------------------------------------------------|
| `GET /api/v1/facturation/dashboard`          | `ADMIN`, `MEDECIN`, `SECRETAIRE` | `TableauDeBordControleur` → `ConsulterLeTableauDeBord` → `LectureDuTableauDeBord` (JDBC)               |
| `POST /api/v1/facturation/synthese/print`    | idem                          | `SyntheseControleur` → `ImprimerLaSynthese` → `LectureDeLaSynthese` (JDBC) + port `ImpressionDeLaSynthese` |
| `POST /api/v1/documents/print` (`SYNTHESE_FACTURATION_MENSUELLE`) | `ADMIN`, `SECRETAIRE` (nouveau) | `DocumentRestController` (reste dans `platform`) + `ConsulterLaSynthese` pour les totaux ; gabarit statique |
| `GET /api/v1/direction/overview`, `/breakdown`, `/absences` | `DIRECTION`   | services Direction (restent dans `platform`) → lecture publiée `SyntheseFinanciereDesCentres`          |

Tables lues : `factures`, `facture_reglements`, `seances`, `agence`, `caisse_assurance`, `facture_lignes` (gabarits Jasper).

## Décisions F5 (2026-10-21, utilisateur)

Détail : `known-bugs.md` § « Décisions F5 », ADR 0034.

| KB                | Décision                                                                                                                                                    |
|-------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|
| KB-FAC-52         | **Corrigé** : 403 si le centre de la requête ou du corps diffère de celui de la session (tableau de bord, synthèse, voie documents)                          |
| KB-FAC-60         | **Corrigé** : la voie documents exige le même profil que la synthèse de la Facturation (ADMIN ou SECRETAIRE) ; les autres documents ne changent pas          |
| KB-FAC-56, 57     | **Corrigé (chiffres affichés)** : la synthèse (deux voies) suit les **totaux enregistrés** des factures ; Montant TVA ajouté ; à valider avec la direction et la comptabilité |
| KB-FAC-61         | **Reproduit** : deux voies conservées avec leurs différences (dynamique par caisse, statique à 4 colonnes aux totaux non formatés)                            |
| KB-FAC-53, 59     | **Reproduits** : erreurs de protocole en 500                                                                                                                  |
| KB-FAC-54, 55, 58, 62 à 66 | **Reproduits**, correction à la livraison suivante. **À trancher avec la direction** : CA par caisse (agence figée sur la facture) contre séances par caisse (payeur actuel du patient) ; encaissé de la Direction sur les règlements de toutes dates ; séances facturées contre somme des répartitions |

## État au 2026-10-21 : `built`

### Réalisation

**Contexte `facturation`** (hexagonal, ArchUnit, `check-arch` 0 violation) :

- application `pilotage` : `ConsulterLeTableauDeBord`, `TableauDeBordDuMois` (+ `RepartitionParCaisse`, `RepartitionParSituation`),
  `ImprimerLaSynthese`, `ConsulterLaSynthese`, `SyntheseMensuelle` (+ `Caisse`, `Repartition`, `colonnes()`),
  `BarresDeRepartition`, `FormatDImpression`, ports sortants `LectureDuTableauDeBord`, `LectureDeLaSynthese`, `ImpressionDeLaSynthese` ;
- langage publié : `publishedlanguage/SyntheseFinanciereDesCentres` (chiffres par centre, par mois, CA HT par caisse, cumul) ;
- infrastructure : `LectureDuTableauDeBordJdbc`, `LectureDeLaSyntheseJdbc`, `SyntheseFinanciereDesCentresJdbc` (mêmes requêtes que le legacy) ;
- interface : `TableauDeBordControleur` (+ `TableauDeBordReponse`, noms JSON inchangés), `SyntheseControleur` (+ `SyntheseRequete`) ;
- composition : `FacturationContexteConfiguration` (beans `facturationLecture…`, `facturationConsulter…`, `facturationImprimerLaSynthese`).

**Dans `platform`** (modifications minimales) : `FacturationSyntheseMensuelleReportService` devient l'**adaptateur** de
`ImpressionDeLaSynthese` (gabarit Jasper dynamique construit à la volée, plus aucun accès JDBC : il reçoit les données du module) ;
`reports/synthese_mensuelle_facturation.jrxml` (gabarit statique) suit les totaux enregistrés ; `DocumentRestController` : garde
de profil et de centre avant le `try` pour ce seul type de document, totaux posés par le serveur ; `DirectionDashboardQueryService`,
`DirectionBreakdownQueryService`, `DirectionAbsencesQueryService` : seule la **source financière** est remplacée par la lecture
publiée (endpoints, alertes, garde de société, parties patients/anémie/séances/motifs inchangés).

**Retiré** : `FacturationRestController`, `FacturationApplicationService`, `SeanceFacturationJdbcAdapter`,
`FacturationSynthesePrintRequest` (platform) ; `FacturationDashboardQuery/Result/Bucket/StatusBucket`, `FacturationUseCase`,
`FacturationDomainService`, `SeanceFacturationPort` (bc-facturation). Recompilation complète de `platform` : aucune référence restante.

### Parité

Les 46 scénarios du filet F5 tournent contre le nouveau code : **les goldens non cités sont inchangés** (dont les 12 scénarios
Direction, les erreurs de protocole, le classement, les bornes, les formats, les refus de corps) ; **20 goldens caducs retirés**,
remplacés par leur version `*_nouveau_code` (24 goldens `_nouveau_code`, dont 4 scénarios positifs ajoutés : chacun lit son centre) :
`tableau-de-bord/` `centre_sans_facture`, `centre_inconnu`, `centre_invalide`, `fevrier_2026_centre_a_vu_par_admin_b`,
`fevrier_2026_centre_b_vu_par_admin_a` (403) ; `synthese-imprimee/` `fevrier_2026_pdf`, `jour_unique_2026_02_28`,
`periode_31_janvier_au_1er_mars`, `deux_annees`, `barres_arrondies`, `libelles_speciaux`, `caisses_de_meme_code`, `six_caisses`,
`excel_fevrier_2026` (Montant TVA, totaux enregistrés), `centre_sans_facture`, `centre_b_vu_par_admin_a` (403) ;
`synthese-modele-document/` `fevrier_2026_pdf` (totaux enregistrés), `profils` (COMPTABLE, INFIRMIER seul, MEDECIN seul, sans rôle :
403), `centre_b_vu_par_admin_a` (403) ; `coherence-nouveau-module/` `synthese_apres_validation` (Montant TVA).

**Retouches de test pour cause de fixture ou de décision** (aucun golden conservé ni assertion non décidée modifiés) : dans les
classes de caractérisation F5, les noms de goldens des scénarios décidés (`_nouveau_code`) et, pour les réponses 403 (qui n'ont pas
la forme d'un PDF), `golden(...)` à la place de `goldenPdf(...)` ; scénarios positifs ajoutés dans les mêmes tests ;
`DocumentRestControllerTest` : le constructeur du contrôleur reçoit désormais le cas d'usage `ConsulterLaSynthese` (un mock).
Avant approbation : sauvegarde du dossier des goldens et comparaison par lignes retirées / ajoutées pour ne laisser passer que les
écarts décidés (aucun `approve` global).

### Tests

Domaine et application : `BarresDeRepartitionTest`, `FormatDImpressionTest`, `SyntheseMensuelleTest`, `ConsultationsDePilotageTest`
(doubles en mémoire `PilotageEnMemoire`) ; contrats des trois lectures : `PilotageContrat` exécuté contre le double
(`PilotageEnMemoireContratTest`), H2 (`PilotageJdbcContratTest`) et PostgreSQL 16 (`PilotagePostgresContractIT`, plus l'échelle
`numeric(14,2)`, l'extraction année/mois et le comptage des séances) ; ArchUnit du module.

**Résultats (2026-10-21)** : `./mvnw -o test` complet vert (reactor entier : 1769 tests dans `platform`, 379 dans
`contexts/facturation` dont 1 ignoré, GMAO et Comptabilité C1..C7 verts), les 46 scénarios F5 verts, `check-arch.ts` 0 violation.
Les 52 tests d'intégration voisins (voie documents, synthèse legacy, séances, référentiel, planning, société) rejoués seuls : verts.
Une première exécution complète avait échoué sur des classes sans rapport (`NoClassDefFoundError` sur des classes internes) pendant
qu'une autre commande Maven et l'EDI utilisaient les mêmes `target/` : à ne pas lancer deux builds en parallèle.

### Mesures

- **Mutation PIT 1.30.0** (`bash backend/scripts/pit-f5-pilotage.sh`, `application.pilotage.*`, tests unitaires et contrat contre le
  double) : **26 mutants tués sur 27 (96 %)**, couverture de lignes 96 %. Le survivant est équivalent (`BarresDeRepartition.barre`,
  garde `maximum <= 0` : `0/0` arrondi donne déjà 0). Cible ≥ 70 % atteinte. Les anciennes mesures de la mutation HTTP (88 %)
  portaient sur les classes retirées.
- **PostgreSQL 16** (`bash backend/scripts/facturation-pg16-rejeu.sh 55447`, cluster jetable, port non standard, auto-détruit) :
  V1..V41 appliquées, `PilotagePostgresContractIT` (16 tests : mêmes tests que le double et H2, plus l'échelle `numeric(14,2)`
  des sommes, l'extraction année/mois et le comptage distinct des séances) vert, F1 à F4 et TVA verts, code de sortie 0.

### Écarts de comportement (nouveau code)

1. 403 si le centre de la requête (tableau de bord) ou du corps (synthèse, deux voies) n'est pas celui de la session (était :
   lecture de n'importe quel centre).
2. Voie documents : ADMIN ou SECRETAIRE seulement (était : tout compte authentifié hors Direction et SUPERADMIN).
3. **Écart sur des chiffres affichés** : la synthèse (deux voies) suit les totaux enregistrés des factures. Exemple (jeu de test,
   février 2026) : Montant HT 1760,35 → 1837,00 ; Montant TTC 1995,54 → 2072,13 (deux effets : l'ancienne synthèse ignorait une
   facture sans ligne de 77,00 et recalculait le TTC par ligne) ; cellule « Inconnu TTC » 10,12 → 10,13 (10,00 HT à 1,25 %,
   TTC enregistré 10,13). Voie statique : « Montant HT: 1850.35 » → « 1850.00 », « Montant TTC: 2085.541500… » → « 2085.13 ».
   Le tableau de bord et la Direction additionnaient déjà les totaux enregistrés : leurs chiffres ne changent pas.
4. « Montant TVA » ajouté à la synthèse (boîte dans le gabarit dynamique, ligne dans le gabarit statique) ; une colonne
   supplémentaire apparaît dans la sortie Excel.

### Points à valider par le métier

1. **Direction et comptabilité** : les nouveaux montants de la synthèse (point 21.2 de `verifications-production.md`) avant déploiement.
2. Le **profil de la voie documents** : ADMIN ou SECRETAIRE comme la synthèse de la Facturation (la décision écrivait « ADMIN »
   en parenthèse ; ici c'est exactement le profil de l'autre route, pour que les deux voies se comportent pareil).
3. À **trancher avec la direction** (reproduits) : CA par caisse (agence figée) contre séances par caisse (payeur actuel du
   patient) ; encaissé de la Direction calculé sur des règlements de toutes dates ; séances facturées contre somme des répartitions.

### Retiré par F6

Retiré ou passé par le langage publié par la slice F6 (fiche `facturation-f6-retrait-bc-facturation.md`, 2026-10-21) : le module `bc-facturation` n'existe plus.

### Plan de bascule

Refonte sur place (ADR 0024) : mêmes URL, pas de façade ni de flag ; retour arrière = commit précédent (aucune migration).
**Déploiement** : contrôles de production `verifications-production.md` point 21 (SELECT seulement), surtout 21.2.
**Frontend (à adapter, non modifié ici)** :

- **403** si le centre envoyé n'est pas celui de la session sur `GET /facturation/dashboard`, `POST /facturation/synthese/print` et
  `POST /documents/print` (synthèse) : le front doit toujours envoyer le centre de la session et afficher le refus ;
- la **synthèse** (dont l'impression par modèle de document) n'est plus accessible qu'à `ADMIN` et `SECRETAIRE` (le médecin seul
  est refusé, aussi par la voie documents) : masquer ou désactiver le bouton pour les autres profils ;
- **chiffres de la synthèse qui changent** (totaux enregistrés, Montant TVA, colonne Excel en plus) : prévenir la direction et la
  comptabilité ; aucun changement de JSON.

### Corrections de la revue F5/F6 (2026-10-21)

- **M1/m1** : `POST /documents/print/{modeleId}` applique, quand le type du modèle chargé est la synthèse, la garde de profil, le 403 de
  centre et les totaux enregistrés (comme `/documents/print`). Règle de profil unique `ProfilDeLaSynthese` : ADMIN ou SECRETAIRE, y
  compris pour un compte multi-rôles ; écart : MEDECIN + INFIRMIER passe de 200 à 403 sur `/facturation/synthese/print` (KB-FAC-73).
  Goldens `par_identifiant_de_modele_*_nouveau_code` et `profils_multi_roles_trois_voies_nouveau_code`.
- **M3** : requêtes de part de TTC non déplacées, rejouées sur PostgreSQL 16 (`SyntheseMensuelleSqlPostgresContractIT`), consignées dans l'ADR 0035 (amendement).
- **Frontend** : le bouton de la synthèse reste réservé à ADMIN/SECRETAIRE (déjà le cas) ; aucune route `/print/{modeleId}` appelée.
- **Dette** : `LocalDate.now()` reste dans les normaliseurs de goldens (`<AUJOURD_HUI>`, alias nommé dans le harnais) ; une exécution à cheval sur minuit peut les faire échouer.
