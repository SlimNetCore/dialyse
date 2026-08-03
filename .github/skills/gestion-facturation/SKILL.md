---
name: facturation-hemodialyse
description: Règles métier et architecture pour le module Maven autonome "facturation" de la plateforme hémodialyse (calcul et validation des factures à partir des séances TERMINEE, gestion des forfaits multiples, TVA paramétrable, regroupement de factures, dashboard facturation). À utiliser dès qu'une tâche touche à l'entité Facture, au passage d'une séance à l'état FACTUREE, au calcul HT/TVA/TTC, au paramétrage facturation (TVA, code facture, switch de regroupement), ou au dashboard du module facturation.
---

# Module facturation — plateforme hémodialyse

## Objectif métier

Le module `facturation` est un module Maven **autonome**, distinct du module séances, qui transforme les séances
validées (état `TERMINEE`) en factures. Il ne modifie jamais directement les séances hors du passage à l'état `FACTUREE`
en fin de processus. Respecte les contraintes archi déjà définies dans `AGENTS.md` (hexagonal + DDD, isolation
multi-centre par `centerId`, zoneless Angular + NgRx Signals, pas d'API dépréciées) et reste cohérent avec le pattern
déjà retenu dans le module séances (`gestion-seances-hemodialyse`) : figer une **copie (value object)** des données au
moment de l'événement métier plutôt que de référencer les entités vivantes.

## Vue d'ensemble du flux

```
1. L'utilisateur choisit une période (mois ou dates libres) + un centre
2. Clic "Facturer" → CALCUL EN MÉMOIRE UNIQUEMENT (aucune écriture en base)
   - regroupe les séances TERMINEE non encore facturées de la période
   - applique la règle de regroupement (switch paramètre) pour déterminer 1 ou N factures/patient
   - calcule HT, TVA, TTC, détail par forfait
   - affiche un aperçu (preview) éditable/consultable à l'écran
3. Clic "Valider la facturation" → PERSISTANCE
   - les factures et leurs lignes sont enregistrées
   - les séances concernées passent à l'état FACTUREE
   - la date de facturation = date du jour, figée automatiquement
```

Cette séparation calcul-en-mémoire / validation-persistée est une exigence explicite : **ne jamais enregistrer quoi que
ce soit au clic sur "Facturer"**, seul "Valider" écrit en base. C'est le cœur du contrat d'UX du module — l'utilisateur
doit pouvoir vérifier avant de committer.

## Cycle de vie et interaction avec les séances

- Séances éligibles à la facturation : état `TERMINEE` **et** non déjà rattachées à une facture (pas de champ
  `factureId` renseigné).
- Idempotence / anti-double-facturation : le calcul (étape 2) comme la validation (étape 3) doivent re-vérifier
  qu'aucune séance sélectionnée n'a été facturée entretemps (cas de deux utilisateurs qui lancent une facturation en
  parallèle sur le même centre/période) — verrou optimiste ou contrainte d'unicité `seance.factureId`.
- À la validation : chaque séance facturée passe à `FACTUREE` et se voit renseigner l'identifiant de la facture qui la
  couvre (traçabilité bidirectionnelle facture ↔ séances).

## Concepts du domaine

### 1. Sélection de la période

L'utilisateur choisit soit un mois calendaire, soit une période libre (date début / date fin). Le centre (`centerId`)
fait partie des critères de sélection — la facturation reste toujours scopée à un centre, cohérent avec l'isolation
multi-centre du reste de la plateforme.

### 2. Calcul du montant — un ou plusieurs forfaits

Pour un patient donné sur la période, on regroupe ses séances `TERMINEE` par forfait :

```
Si un seul forfait F1 sur la période :
   Montant HT = prix(F1) × nb_séances(F1)

Si plusieurs forfaits F1, F2, ... sur la période :
   Montant HT = (prix(F1) × nb_séances(F1)) + (prix(F2) × nb_séances(F2)) + ...
```

Le détail par forfait (prix unitaire × nombre de séances, pour chaque forfait distinct) doit systématiquement être
conservé et affiché sur la facture — jamais un simple total agrégé sans le détail des lignes.

Une fois le HT calculé : `Montant TVA = HT × taux_TVA_paramétré`, puis `Montant TTC = HT + TVA`. Le taux de TVA appliqué
doit être celui en vigueur au moment de la facturation (figé sur la facture, pas recalculé rétroactivement si le
paramétrage change plus tard — même logique de "snapshot au moment de l'événement" que pour le forfait des séances).

### 3. Switch de regroupement des factures multi-forfaits

Paramètre booléen (à activer/désactiver dans les paramètres du module) qui détermine comment sont générées les factures
pour un patient ayant plusieurs forfaits distincts sur la période :

- **Switch activé (regroupement ON)** : **une seule facture** par patient pour la période, avec autant de lignes de
  détail que de forfaits utilisés (une facture multi-lignes / multi-forfaits).
- **Switch désactivé (regroupement OFF)** : **une facture par forfait** — un patient avec 2 forfaits différents sur la
  période génère 2 factures distinctes, chacune ne portant qu'un seul forfait.

Ce paramètre s'applique à l'ensemble de la campagne de facturation lancée (pas configurable facture par facture) et doit
être lu au moment du calcul en mémoire (étape 2), donc si l'utilisateur le change puis relance "Facturer", l'aperçu doit
refléter la nouvelle règle.

### 4. Snapshot figé sur la facture (au moment de la facturation)

Comme pour les séances, la facture capture une **copie figée**, pas une référence live, des données suivantes telles
qu'elles sont au moment de la facturation :

| Champ                           | Description                                                                   |
|---------------------------------|-------------------------------------------------------------------------------|
| `patientCode`                   | Code patient                                                                  |
| `statutPatientSnapshot`         | Statut du patient au moment de la facturation (ex. Vacancier, Permanent, ...) |
| `caisseAssuranceIdSnapshot`     | Identifiant de la caisse d'assurance du patient au moment de la facturation   |
| `numeroImmatriculationSnapshot` | Numéro d'immatriculation du patient au moment de la facturation               |
| `centrePayeurIdSnapshot`        | Identifiant du centre payeur au moment de la facturation                      |
| `agenceIdSnapshot`              | Identifiant de l'agence au moment de la facturation                           |
| `idCentre`                      | Centre où la facturation a été lancée (`centerId`)                            |
| `dateFacturation`               | Date du jour, renseignée automatiquement, non modifiable par l'utilisateur    |

Raison DDD : si le statut du patient, sa caisse ou son immatriculation changent après coup, les factures déjà émises ne
doivent jamais être réécrites rétroactivement — elles doivent rester le reflet exact de la situation au moment de l'acte
de facturation (valeur probante/juridique du document).

### 5. Numérotation de la facture

Le code de facturation (préfixe/format du numéro de facture) est **paramétrable** dans les paramètres du module. Prévoir
un compteur séquentiel associé (probablement par centre et/ou par année selon convention à valider) pour garantir
l'unicité du numéro généré, formaté selon le code paramétré.

### 6. Paramétrage du module

Écran de paramètres dédié au module facturation, contenant au minimum :

- **Taux de TVA** applicable (pourcentage).
- **Code de facturation** (format/préfixe du numéro de facture).
- **Switch de regroupement des factures à forfaits multiples** (cf. section 3).

Ces paramètres doivent être versionnés dans le temps si possible (au minimum horodatés), puisque le taux de TVA
notamment doit rester figé sur les factures déjà émises même s'il change ensuite (cf. section 2).

## Dashboard du module facturation

Le module facturation a son **propre dashboard**, distinct de celui des séances, avec au minimum :

- Nombre de séances par caisse d'assurance (sur la période sélectionnée).
- Nombre de patients facturés par caisse d'assurance.
- Nombre de séances facturées par statut du patient (Vacancier, Permanent, etc.).
- Chiffre d'affaires facturé sur le mois en cours (somme des montants TTC — à confirmer si CA doit être HT ou TTC, cf.
  points ouverts).

Exigence explicite : IHM **intuitive**, **élégante** et **responsive** — pas un simple tableau brut. Privilégier des
cartes de synthèse (KPI) en tête de dashboard, puis des graphiques/tableaux de répartition (par caisse, par statut
patient) en dessous.

## Placement architectural (hexagonal / DDD)

- **Module Maven séparé** (`facturation`), avec sa propre couche domaine/application/infrastructure, qui **consomme** en
  lecture le module séances (et probablement le module patient/tiers pour les caisses/agences/centres payeurs) via des
  ports, sans dépendance inverse.
- **Domaine** :
    - Agrégat racine `Facture` (numéro, période, `centerId`, snapshot patient/caisse/agence/centre payeur, montants
      HT/TVA/TTC, statut, liste de `LigneFacture`).
    - Entité `LigneFacture` (forfaitId, libellé forfait, prix unitaire au moment de la facturation, nombre de séances,
      montant de la ligne).
    - Value object `ParametresFacturation` (taux TVA, code facturation, switch regroupement), versionné/horodaté.
    - Statuts possibles de `Facture` à valider avec toi — au minimum une distinction entre **aperçu non persisté**
      (résultat du calcul en mémoire, qui n'est pas un état d'entité tant qu'il n'est pas enregistré) et **`VALIDEE`**
      une fois enregistrée. Si un état intermédiaire genre `BROUILLON` persisté est utile pour toi (sauvegarde d'un
      calcul avant validation finale), c'est un point ouvert.
- **Application** — use cases dédiés :
    - `CalculerFacturationPeriode` (lecture seule, retourne un DTO d'aperçu — **aucune écriture**).
    - `ValiderFacturation` (persiste les factures calculées + fait transiter les séances vers `FACTUREE`, dans une même
      transaction).
    - `GererParametresFacturation` (CRUD des paramètres : TVA, code, switch).
    - `ObtenirDashboardFacturation` (agrégations pour les KPI du dashboard).
- **Ports sortants** : `SeanceRepositoryPort` (lecture des séances `TERMINEE` éligibles + écriture du passage à
  `FACTUREE`), `PatientPort` (statut, caisse, immatriculation, centre payeur, agence au moment T), `ForfaitCatalogPort`,
  `ParametresFacturationPort`, `FactureRepositoryPort`.
- **Transaction de validation** : l'écriture des factures ET la mise à jour des séances (`TERMINEE` → `FACTUREE`)
  doivent être atomiques — pas de facture enregistrée sans que les séances correspondantes soient marquées facturées, et
  inversement.
- **Isolation multi-centre** : `centerId` porté par `Facture` et systématiquement filtré dans les requêtes de sélection
  des séances éligibles — jamais de facturation croisant plusieurs centres dans une même campagne.

## UI/UX

- **Étape 1 — sélection** : sélecteur de mois (raccourci) ou de période libre, sélecteur de centre. Bouton **"
  Facturer"** proéminent.
- **Étape 2 — aperçu (en mémoire)** : liste des factures qui seraient générées (regroupées ou non selon le switch), avec
  par facture : patient, statut, caisse, détail des lignes (forfait × nb séances), HT, TVA, TTC. Doit être clairement
  identifié visuellement comme un **aperçu non enregistré** (ex. bandeau/label "Aperçu — non enregistré"), pour éviter
  toute confusion avec une facture définitive. Bouton **"Valider la facturation"** pour committer.
- **Étape 3 — confirmation** : une fois validé, retour visuel clair (toast/résumé : nombre de factures créées, montant
  total TTC) et bascule vers la liste des factures enregistrées / le dashboard.
- **Dashboard** : cartes KPI en tête (CA du mois, nb séances facturées, nb patients facturés), puis répartitions par
  caisse et par statut patient sous forme de graphiques (barres/donut) + tableaux détaillés en dessous. Responsive : les
  cartes KPI passent en colonne unique sur mobile/tablette, les tableaux de répartition en scroll horizontal si
  nécessaire.
- **Paramètres** : écran dédié, simple formulaire (taux TVA, code facturation, switch de regroupement), avec sauvegarde
  explicite et rappel que le changement ne s'applique qu'aux facturations futures (pas rétroactif).

## Points ouverts à valider avec toi

1. Le CA du dashboard doit-il être exprimé en HT ou en TTC (ou les deux affichés côte à côte) ?
2. Faut-il un état persisté intermédiaire type `BROUILLON` pour sauvegarder un calcul avant validation finale, ou
   l'aperçu reste-t-il strictement volatile (perdu si l'utilisateur quitte l'écran) ?
3. Convention exacte de numérotation : le compteur séquentiel du code de facturation se réinitialise-t-il par centre,
   par année, les deux, ou est-il global ?
4. Le switch de regroupement est-il un paramètre global unique, ou faut-il l'historiser/versionner comme le taux de TVA
   pour garantir la cohérence des factures déjà émises ?
5. Que se passe-t-il si une séance `TERMINEE` est corrigée/annulée après qu'elle a été incluse dans un aperçu non validé
   (avant le clic "Valider") ? Faut-il une revérification systématique juste avant la persistance (probablement oui, cf.
   section idempotence) ?

Je peux préparer une maquette de l'écran d'aperçu de facturation et du dashboard si tu veux visualiser l'agencement
avant de coder.
