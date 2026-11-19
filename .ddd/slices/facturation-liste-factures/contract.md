---
status: approved
date: 2026-11-19
slice: facturation-liste-factures
sources: >
  ReglementControleur.java, SituationsDesFacturesJdbc.java (patron lecture factures + LotsDePatients + PayeursDuCentre),
  facturation-schema.sql / V1 (factures, facture_lignes, facture_reglements, seances.facture_id),
  SeancesDesFacturesFournies / SeancesDesFacturesPubliees (ADR 0097), RoleScopeFilter, shell.component.ts, facturation.routes.ts,
  @sbourahla/ng-table d.ts (detailRowTemplate, expandedRowKeys, detailToggle, remote mode)
---

# Contrat — Liste des factures (master-detail) + indicateur d'étapes compact

Une slice, deux comportements livrés ensemble par le même écran de module : (1) consulter les factures émises sur une période,
paginées et filtrées, avec le détail d'une facture déroulé (séances, forfait, montants) ; (2) remplacer le bloc « Cycle de
facturation » (3 cartes) de l'écran de calcul par un indicateur d'étapes d'une ligne. **Lecture seule, aucune migration, mêmes tables.**

## 1. Règles de gestion (nouvelles) — texte à reporter dans `docs/reference/regles-de-gestion/12-facturation-reglements-comptabilite.md`

Nouvelle section `## 12.4 ter Consultation des factures émises` (le fichier appartient au travailleur **frontend**, qui reporte
RG-FAC-046 à 048 telles quelles et ajoute RG-FAC-049) :

- **RG-FAC-046** — La **liste des factures** (`GET /api/v1/facturation/factures`, `ADMIN` ; centre de la requête = centre de la
  session sinon 403 ; médecin « seul » refusé par le filtre de rôles) présente les factures **émises** du centre dont la **date de
  facturation** est dans la période `du`–`au` (bornes incluses ; par défaut le mois courant dans le fuseau du centre ; période ≤ 366
  jours, `du` ≤ `au`, sinon 400). Pour chaque facture : numéro, date, période facturée, patient (fiche du centre sinon instantané de
  la facture), caisse (chaîne figée puis courante, comme les règlements RG-FAC-04x), nombre de séances **facturées** (somme des
  quantités des lignes figées), HT / TVA / TTC **figés**, réglé (somme signée des règlements), reste et **état de règlement**
  (`NON_REGLEE`, `PARTIELLEMENT_REGLEE`, `REGLEE`, calculé par `SituationDeFacture` à chaque lecture). Filtres cumulables : numéro
  (contient, sans casse), patient (contient, sans casse, sur « nom prénom » ou numéro d'assurance), caisse, état, montant TTC min /
  max. Tri par défaut : date de facturation décroissante, puis numéro décroissant, puis identifiant. Page de 1 à 100 lignes. Les
  totaux renvoyés portent sur **toutes** les factures filtrées, pas seulement la page.
- **RG-FAC-047** — Le **détail d'une facture** (`GET /api/v1/facturation/factures/{id}/lignes`, mêmes droits) donne les lignes
  figées (forfait, prix unitaire HT, nombre de séances, HT ; TVA de ligne = HT × taux figé de la facture arrondi au centime
  demi-supérieur, TTC = HT + TVA) et les totaux **figés de la facture** (qui font foi ; la somme des TVA de ligne peut différer d'un
  centime), plus le réglé et le reste. Une facture d'un autre centre ou inconnue : **404** (aucune fuite d'existence).
- **RG-FAC-048** — Les **séances d'une facture** sont celles du centre rattachées à la facture (`facture_id`), quel que soit leur
  statut, triées par date puis identifiant, lues par la Séance (`seance.v1`). Le forfait de chaque séance est **attribué** sans
  recalcul : une séance portant une surcharge de forfait reçoit la ligne de même forfait ; les autres reçoivent la seule ligne qui a
  encore des séances non attribuées (quantité figée − séances déjà attribuées) ; s'il n'y en a pas exactement une, le forfait de la
  séance est **non attribuable** (affiché « — »), les lignes figées restant la référence des montants. Montant HT d'une séance = prix
  unitaire HT de sa ligne.
- **RG-FAC-049** (frontend) — L'écran de calcul affiche les trois étapes Sélection → Aperçu → Validation sur une seule ligne ; l'étape
  courante est déduite de l'état de l'écran (pas d'aperçu : Sélection ; aperçu calculé non validé : Aperçu ; validation réussie :
  Validation) ; l'explication de chaque étape est en info-bulle et lue par les lecteurs d'écran.

## 2. API HTTP (contexte Facturation)

Contrôleur `FacturesControleur`, `@RequestMapping("/api/v1/facturation/factures")`, `@PreAuthorize("hasRole('ADMIN')")` sur chaque
méthode (même règle que l'entrée de module « Calcul de la facturation » et que `/facturation/preview`). Centre :
`session.centre(centerId)` (403 si autre centre). `RoleScopeFilter` couvre déjà le préfixe `/api/v1/facturation` (rien à changer).

### 2.1 `GET /api/v1/facturation/factures`

| Paramètre | Type | Obligatoire | Sémantique |
|---|---|---|---|
| `centerId` | UUID | oui | centre de la session |
| `du`, `au` | `yyyy-MM-dd` | non (les deux ou aucun) | période sur `date_facturation`, bornes incluses. Aucun des deux ⇒ 1er → dernier jour du mois courant (`FuseauxDesCentres.de(centre)` + `Clock`). Un seul des deux, `du > au`, ou plus de 366 jours ⇒ 400 `PERIODE_INVALIDE` |
| `page` | int | non, défaut 0 | < 0 ⇒ 0 |
| `size` | int | non, défaut 20 | < 1 ⇒ 20 ; > 100 ⇒ 100 (borné, pas d'erreur) |
| `sort` | `champ,asc\|desc` | non | champs : `dateFacturation`, `numeroFacture`, `patient` (nom puis prénom), `caisse`, `nombreDeSeances`, `montantTtc`, `montantRegle`, `reste`, `etat` (ordre NON_REGLEE < PARTIELLEMENT_REGLEE < REGLEE). Départage toujours `dateFacturation desc, numeroFacture desc, factureId`. Direction absente ⇒ `asc`. Champ inconnu ⇒ 400 `TRI_INVALIDE`. Comparaison de textes sans casse (`Collator` fr ou `String.CASE_INSENSITIVE_ORDER`) |
| `numero` | string | non | contient, sans casse, sur `numeroFacture` (vide/blanc = absent) |
| `patient` | string | non | contient, sans casse, sur `patientNom + " " + patientPrenom` OU `numeroAssurance` |
| `caisseId` | UUID | non | égalité sur la caisse résolue |
| `etat` | `NON_REGLEE\|PARTIELLEMENT_REGLEE\|REGLEE` | non | inconnu ⇒ 400 `ETAT_INVALIDE` |
| `montantMin`, `montantMax` | décimal | non | bornes incluses sur `montantTtc` ; min > max ⇒ 400 `MONTANT_INVALIDE` |

Réponse 200 `PageDeFacturesReponse` :

```json
{
  "items": [{
    "factureId": "uuid", "numeroFacture": "FAC-2026-000123",
    "dateFacturation": "2026-11-05", "periodeDebut": "2026-10-01", "periodeFin": "2026-10-31",
    "patientId": "uuid", "numeroAssurance": "123456789012", "patientNom": "BENALI", "patientPrenom": "Karim",
    "caisseId": "uuid|null", "caisse": "CNAS|Non renseignee",
    "nombreDeSeances": 13, "tauxTva": 9.00,
    "montantHt": 104000.00, "montantTva": 9360.00, "montantTtc": 113360.00,
    "montantRegle": 50000.00, "reste": 63360.00, "tropPercu": 0.00,
    "etat": "PARTIELLEMENT_REGLEE"
  }],
  "total": 57, "page": 0, "size": 20,
  "du": "2026-11-01", "au": "2026-11-30",
  "totaux": {"nombreDeFactures": 57, "montantHt": 0.00, "montantTva": 0.00, "montantTtc": 0.00, "montantRegle": 0.00, "reste": 0.00}
}
```
Montants : nombres JSON à 2 décimales (`BigDecimal` scale 2 HALF_UP), jamais de chaîne. `du`/`au` renvoient la période effective
(utile quand elle a été déduite). Champs nullables : `caisseId`, `patientId` jamais null (colonne NOT NULL).

### 2.2 `GET /api/v1/facturation/factures/{factureId}/lignes?centerId=`

Réponse 200 `DetailDeFactureReponse` ; 404 (corps vide) si facture inconnue ou d'un autre centre :

```json
{
  "factureId": "uuid", "numeroFacture": "FAC-2026-000123", "tauxTva": 9.00,
  "lignes": [{
    "forfaitId": "uuid|null", "forfaitLibelle": "Hémodialyse standard",
    "prixUnitaireHt": 8000.00, "nombreDeSeances": 13,
    "montantHt": 104000.00, "montantTva": 9360.00, "montantTtc": 113360.00
  }],
  "seances": [{
    "seanceId": "uuid", "dateSeance": "2026-10-02", "statut": "FACTUREE",
    "forfaitId": "uuid|null", "forfaitLibelle": "Hémodialyse standard|null", "prixUnitaireHt": 8000.00
  }],
  "totaux": {"montantHt": 104000.00, "montantTva": 9360.00, "montantTtc": 113360.00, "montantRegle": 50000.00, "reste": 63360.00}
}
```
Lignes triées `forfait_label ASC, unit_price_ht ASC, id`. `montantHt` de ligne = `line_ht` stocké. `totaux.montantHt/Tva/Ttc` =
`factures.total_*`. Pas de code de forfait : `facture_lignes` ne stocke que le libellé (le code n'est pas figé) — volontaire, pas
de migration. `forfaitLibelle`/`forfaitId`/`prixUnitaireHt` de séance null ⇔ non attribuable (RG-FAC-048).

### 2.3 Erreurs

| Statut | Cas | Corps |
|---|---|---|
| 400 | `PERIODE_INVALIDE`, `TRI_INVALIDE`, `ETAT_INVALIDE`, `MONTANT_INVALIDE` | `ProblemDetail` avec propriété `code` (même forme que `ClotureDeFacturationAdvice.requeteInvalide`) |
| 400 | paramètre illisible (UUID, date, nombre) | défaut Spring |
| 403 | rôle ≠ ADMIN, centre ≠ session, médecin seul | défaut sécurité |
| 404 | détail : facture inconnue / autre centre | vide (`ResponseEntity.notFound()`) |

`RequeteInvalide` (package-private, déjà existante) est réutilisée ; un nouvel advice `FacturesAdvice`
(`@RestControllerAdvice(assignableTypes = FacturesControleur.class)`) la mappe en 400 (ne pas élargir `ClotureDeFacturationAdvice`).

## 3. Backend — noms, packages, ports (hexagonal)

Racine `com.hemodialyse.backend.contexts.facturation` (`backend/contexts/facturation`).

| Couche | Type | Rôle |
|---|---|---|
| `domain.facture` | `AttributionDesForfaits` (final, statique pur) + record `SeanceAttribuee(UUID seance, LocalDate date, String statut, LigneFacture ligne /*null = non attribuable*/)` | RG-FAC-048. `static List<SeanceAttribuee> attribuer(List<LigneFacture> lignes, List<SeanceDeFacture…>)` — entrée : id, date, statut, `UUID forfaitSurcharge` (nullable). Réutilise `LigneFacture` existant |
| `application.consultation` | `ConsulterLesFactures` (use case, classe finale) | `PageDeFactures rechercher(CriteresDeFactures criteres, int page, int taille)` ; `Optional<DetailDeFacture> detail(CentreId centre, UUID facture)`. Filtre, trie, pagine et totalise **en mémoire** (comme `SituationsDesFacturesJdbc`, PL8b) |
| `application.consultation` | records `CriteresDeFactures(CentreId centre, LocalDate du, LocalDate au, String numero, String patient, UUID caisse, EtatDeReglement etat, BigDecimal montantMin, BigDecimal montantMax, TriDesFactures tri)`, `TriDesFactures(ChampDeTri champ, boolean descendant)`, enum `ChampDeTri`, `FactureListee(...)` (champs du JSON 2.1 ; `etat` = `EtatDeReglement`), `TotauxDesFactures`, `PageDeFactures(List<FactureListee> items, long total, int page, int taille, LocalDate du, LocalDate au, TotauxDesFactures totaux)`, `LigneDeFactureDetaillee`, `SeanceDeFactureDetaillee`, `DetailDeFacture` | modèles de lecture |
| `application.consultation` | port sortant `ListeDesFactures` | `List<FactureListee> entre(CentreId centre, LocalDate du, LocalDate au)` (non filtrée hors période, tri par défaut) ; `Optional<FactureListee> deLaFacture(CentreId centre, UUID facture)` ; `List<LigneFacture> lignes(CentreId centre, UUID facture)` |
| `application.consultation` | port sortant `SeancesDeLaFacture` | `List<SeanceRattachee> de(CentreId centre, UUID facture)` ; record `SeanceRattachee(UUID seance, LocalDate date, String statut, UUID forfaitSurcharge)` |
| `application.consultation` | port `PeriodeParDefaut` **ou** réutiliser `FuseauxDesCentres` (application.cloture) + `Clock` | mois courant pour `du`/`au` absents (calculé dans le contrôleur ou le use case — choix du travailleur, testé) |
| `infrastructure.persistence` | `ListeDesFacturesJdbc implements ListeDesFactures` | SQL sur `factures` + `facture_reglements` (somme signée, comme `SituationsDesFacturesJdbc`) + `SUM(facture_lignes.seance_count)` (sous-requête) ; fiche patient via `LotsDePatients.aFacturerDuCentre` par lots de 500, caisse via `PayeursDuCentre` (mêmes fallbacks « Non renseignee »). `lignes()` lit `facture_lignes` borné au centre. **Aucun SQL sur `seances`** (garde `TableDesSeancesTest`) |
| `infrastructure.acl.seance` | `SeancesDeLaFacturePubliees implements SeancesDeLaFacture` | délègue à `SeancesDesFacturesFournies.seancesDeLaFacture` |
| `publishedlanguage` | `SeancesDesFacturesFournies` + méthode `List<SeanceDeFactureFournie> seancesDeLaFacture(UUID centre, UUID facture)` + record imbriqué `SeanceDeFactureFournie(UUID id, LocalDate date, String statut, UUID forfaitSurcharge)` | côté fourni |
| `interfaces.http` | `FacturesControleur`, `FacturesAdvice`, DTO dans `interfaces.http.dto` : `PageDeFacturesReponse`, `FactureListeeReponse`, `DetailDeFactureReponse` (lignes/séances/totaux en records imbriqués) | mapping explicite depuis l'application |
| `configuration` | `FacturationContexteConfiguration` : beans `ListeDesFacturesJdbc`, `SeancesDeLaFacturePubliees`, `ConsulterLesFactures` | |

Contexte **Séance** (`backend/contexts/seance`, fournisseur ADR 0097) :
- `publishedlanguage.SeancesDesFacturesPubliees` + `List<SeanceDeFacturePubliee> seancesDeLaFacture(UUID centre, UUID facture)`,
  record imbriqué `SeanceDeFacturePubliee(UUID id, LocalDate date, String statut, UUID forfaitSurcharge)`.
- `FacturationDesSeancesJdbc` : `SELECT id, date_seance, statut, forfait_override_id FROM seances WHERE center_id = ? AND facture_id = ? ORDER BY date_seance, id`.
- `SeancesDesFacturesParLaSeance` : délégation + traduction du record. `SeanceConfiguration` inchangée (même bean).
- Doubles : `FacturationDesSeancesEnMemoire` + contrat `FacturationDesSeancesContrat` (seance tests) ; `SeancesDesFacturesSurLaTable` (facturation tests).

Tests attendus (backend) : unitaires `AttributionDesForfaitsTest` (surcharge, une ligne restante, ambiguïté ⇒ null), `ConsulterLesFacturesTest`
(filtres, tri, départage, bornage page/size, totaux sur tout le filtré, période par défaut), contrat JDBC de `ListeDesFacturesJdbc`
(H2, centre étranger invisible, somme signée), contrat Séance (`seancesDeLaFacture` borné au centre), WebMvc/HTTP
`FacturesControleurTest` (403 secrétaire/autre centre, 400 codes, 404 détail autre centre, JSON exact). Gardes de tables et
`check-arch` verts. Commande : `locked.ts maven -- mvnw.cmd -f backend/pom.xml -pl contexts/facturation,contexts/seance -am test -Dtest=<Tests> -Dsurefire.failIfNoSpecifiedTests=false`.

## 4. Frontend

### 4.1 Types et méthodes `BackendApiService` (`frontend/src/app/core/api/backend-api.service.ts`)

```ts
export type FactureEtat = ReglementEtat; // 'NON_REGLEE' | 'PARTIELLEMENT_REGLEE' | 'REGLEE'

export interface FacturesQuery {
  du?: string | null; au?: string | null;          // yyyy-MM-dd ; les deux ou aucun
  page: number; size: number;                      // size ≤ 100
  sort?: { champ: FactureChampDeTri; direction: 'asc' | 'desc' } | null;
  numero?: string | null; patient?: string | null; caisseId?: string | null;
  etat?: FactureEtat | null; montantMin?: number | null; montantMax?: number | null;
}
export type FactureChampDeTri = 'dateFacturation' | 'numeroFacture' | 'patient' | 'caisse' | 'nombreDeSeances'
  | 'montantTtc' | 'montantRegle' | 'reste' | 'etat';

export interface FactureListee {
  factureId: string; numeroFacture: string; dateFacturation: string; periodeDebut: string; periodeFin: string;
  patientId: string; numeroAssurance: string; patientNom: string; patientPrenom: string;
  caisseId: string | null; caisse: string; nombreDeSeances: number; tauxTva: number;
  montantHt: number; montantTva: number; montantTtc: number; montantRegle: number; reste: number; tropPercu: number;
  etat: FactureEtat;
}
export interface TotauxDesFactures {
  nombreDeFactures: number; montantHt: number; montantTva: number; montantTtc: number; montantRegle: number; reste: number;
}
export interface PageDeFactures extends PagedResponse<FactureListee> { du: string; au: string; totaux: TotauxDesFactures; }

export interface LigneDeFactureDetaillee {
  forfaitId: string | null; forfaitLibelle: string; prixUnitaireHt: number; nombreDeSeances: number;
  montantHt: number; montantTva: number; montantTtc: number;
}
export interface SeanceDeFactureDetaillee {
  seanceId: string; dateSeance: string; statut: string;
  forfaitId: string | null; forfaitLibelle: string | null; prixUnitaireHt: number | null;
}
export interface DetailDeFacture {
  factureId: string; numeroFacture: string; tauxTva: number;
  lignes: LigneDeFactureDetaillee[]; seances: SeanceDeFactureDetaillee[];
  totaux: { montantHt: number; montantTva: number; montantTtc: number; montantRegle: number; reste: number };
}

// méthodes
listFactures(centerId: string, query: FacturesQuery): Observable<PageDeFactures>;      // GET /facturation/factures (params vides omis ; sort = `${champ},${direction}`)
getFactureDetail(centerId: string, factureId: string): Observable<DetailDeFacture>;   // GET /facturation/factures/{id}/lignes
```
`PagedResponse` est importé de `admin-api.service.ts` (déjà utilisé par `listReglements`).

### 4.2 Écran `/facturation/factures`
- Route lazy dans `facturation.routes.ts` : `path: 'factures'`, `canActivate: [centreAdminGuard]`,
  `loadComponent: () => import('./factures/factures-list.component')`.
- `shell.component.ts`, module `facturation` : après l'entrée calcul, `{route: '/facturation/factures', label:
  'NAV.FACTURATION_FACTURES', icon: 'receipt_long', role: 'ADMIN'}` (kind travail = défaut). Titre de page : vérifier la table
  des segments (l. ~105-170) pour `factures` sous `facturation`.
- Composant `features/facturation/factures/factures-list.component.{ts,html,css,spec.ts}` + store
  `features/facturation/factures/factures-list.store.ts` (patron `patient-list.store.ts` mode `remote`).
- Barre de période au-dessus de la table : `du`/`au` (défaut mois courant, raccourcis mois précédent/courant optionnels) ; un
  changement relance la page 0 et replie le détail.
- `ng-table` `dataMode="remote"`, `(remoteQueryChange)` ⇒ `FacturesQuery` (filtres colonne : `numeroFacture`→`numero`,
  `patient`→`patient`, `caisse`→`caisseId` (liste `OrganisationApiService.listerCaisses`), `etat`→`etat` (liste des 3 états),
  `montantTtc`→ min/max si le filtre de plage est simple, sinon omis). Colonne `etat` en badge (tons : NON_REGLEE danger,
  PARTIELLEMENT_REGLEE warning, REGLEE success). Montants `fr-DZ`/DZD via le pipe/format déjà utilisé par Règlements. Ligne de
  totaux (pied) depuis `totaux`.
- Master-detail : `[detailRowTemplate]`, mode contrôlé `[expandedRowKeys]` (une seule clé = une facture à la fois),
  `(detailToggle)` et clic de ligne ; au premier dépli, `getFactureDetail` (cache `Map<factureId, DetailDeFacture>` vidé au
  changement de période/centre ; état chargement / erreur dans la ligne). Contenu : petite table HTML (pas un 2e ng-table) des
  séances (date, forfait — « — » si null —, PU HT) puis lignes par forfait (forfait, quantité, PU HT, HT, TVA, TTC) et totaux
  figés + réglé + reste.
- Export : `(remoteExportRequested)` ⇒ relecture page par page (size 100) avec les mêmes filtres puis génération locale, comme
  la liste patients — seulement si bon marché, sinon pas d'export (à signaler).
- Libellés ng-table via `NgTableTranslatedLabels` (`features/direction/ng-table-labels.ts`). **Ne pas importer ng-table dans
  `app.config`.**

### 4.3 Indicateur d'étapes (écran de calcul)
Supprimer le bloc « Cycle de facturation » de `facturation-workspace.component.html` (+ CSS mort). Le remplacer par une ligne
`<ol>` de 3 pastilles numérotées « Sélection → Aperçu → Validation », hauteur ≤ 48 px, étape courante mise en évidence
(`aria-current="step"`), explication complète par étape en `title` + `aria-describedby` (texte visuellement masqué), RTL ok.
Clés i18n réutilisées si existantes (textes des anciennes cartes), sinon nouvelles sous `FACTURATION.ETAPES.*`. 4 langues fr/en/ar/kab.

### 4.4 i18n (fr/en/ar/kab, `frontend/public/i18n/*.json`)
`NAV.FACTURATION_FACTURES` (« Factures »), `FACTURATION.FACTURES.*` (titre, période du/au, colonnes, états, détail : séances,
lignes, totaux, non attribuable, chargement, erreur, aucune facture), `FACTURATION.ETAPES.*`.

## 5. Propriété des fichiers
Voir `ownership.json` (clés `backend`, `frontend`, disjointes). Le fichier unique de règles
`docs/reference/regles-de-gestion/12-facturation-reglements-comptabilite.md` et `docs/client/**` sont au **frontend** : le
travailleur backend ne les édite pas ; RG-FAC-046..048 sont déjà rédigées ici (§1) et le frontend les reporte. Tout écart d'API
découvert par le backend se signale `CONTRAT:` (pas d'édition côté frontend).

## Questions ouvertes
- Rôle : ADMIN seul (comme le module Facturation au front). La SECRETAIRE voit déjà factures + règlements dans « Règlements » ;
  l'ouvrir ici ? (défaut : non).
- RG-FAC-048 : attribution du forfait aux séances sans surcharge quand une facture a plusieurs lignes non surchargées
  (changement de prise en charge en cours de période) ⇒ « non attribuable ». Figer le forfait par séance demanderait une migration
  (hors slice).
- Pas de code de forfait dans le détail (non figé en base) ; libellé figé seulement.
