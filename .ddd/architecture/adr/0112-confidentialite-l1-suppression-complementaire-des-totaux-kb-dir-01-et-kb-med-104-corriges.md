---
status: accepted
date: 2026-11-09
review-by: 2026-12-15
decision-makers: [utilisateur (demande explicite de CORRIGER KB-DIR-01 et KB-MED-104, 2026-11-09 ; validation du DPO attendue au plus tard le 2026-12-15), architecte]
context-scope: pilotage-direction (domain, application, interfaces/publie), dossier-medical (domain, application), plateforme (goldens, rendu PDF inchangé)
related: ADR 0087 (KB-MED-104 reproduit, condition de mise en production), 0101 (DM9, politiques d'anonymat propres à chaque contexte), 0102 (PD1/PD4), 0103 (PD2, instantanés), 0106 (PD6, indicateurs), 0024 et 0075 (refactoring sur place) ; `.ddd/migration/corrections-kb-2026-11-09.md` (lot L1) ; `.ddd/safety-net/known-bugs.md` (KB-DIR-01, KB-MED-104) ; `.ddd/safety-net/pilotage-direction.md` §9.1
---

# 0112 - Lot L1 (confidentialité) : suppression complémentaire des totaux, KB-DIR-01 et KB-MED-104 CORRIGÉS

Ce n'est PAS une migration à parité : le comportement CHANGE volontairement, à la demande de l'utilisateur. Les ADR 0087, 0101, 0102, 0103 et 0106
avaient REPRODUIT l'anomalie pendant le refactoring sur place (mêmes tables, mêmes URL, mêmes rôles). Ni façade, ni flag, ni projection, ni
migration de schéma. La **confirmation du DPO reste à obtenir (au plus tard le 2026-12-15)** ; la correction est faite avant, car elle ne réduit que
ce qui est publié.

## Contexte
Chaque effectif de patients de 1 à 4 est masqué par centre (`SeuilDAnonymat` du Pilotage, `EffectifMasque` du Dossier médical, seuil 5). Mais les totaux
(société, total par caisse, consolidés cliniques) additionnaient les effectifs BRUTS puis n'étaient masqués que s'ils étaient eux-mêmes inférieurs à 5.
« Total moins somme des centres publiés » rendait donc l'effectif masqué ; deux centres masqués dont la somme atteignait 5 publiaient leur somme.
Le rapport PDF et les instantanés mensuels figés (RG-DIR-090) portaient les mêmes totaux.

## Décision
**Suppression complémentaire** (option 9.1(a) de l'audit) : un total est publié `null` (« < seuil ») dès qu'AU MOINS UN des effectifs qui le composent est masqué
(1 à 4). Les valeurs par centre restent masquées comme avant. Règle unique, nommée « suppression complémentaire des totaux » (RG-DIR-111, RG-MED-049g).
Choisie plutôt que (b) totaux sur les seuls centres publiés (le total n'en serait plus un), (c) arrondi par 5 (nouveau modèle) et (d) agrégats seuls
(perd la vue par centre).

| Périmètre | Ancien comportement | Nouveau comportement |
|---|---|---|
| `GET /overview` : `totaux.patients`, `totaux.patientsSousKt` | somme des bruts, masquée si < 5 | `null` si un centre est masqué pour cette colonne |
| `GET /breakdown` : `caisseTotaux[].patients` | somme des bruts par caisse | `null` si un centre portant CETTE caisse est masqué |
| `GET /indicators` : 7 effectifs consolidés (AgHBs, anti-VHC, VIH, observance, attente, bilans, greffes) | somme des bruts, masquée si < 5 | `null` si un centre le porte masqué (champ par champ) |
| `GET /indicators` : patients évalués et 3 taux des 5 marqueurs consolidés | calculés sur tous les patients, masqués si < 5 | supprimés ensemble si un centre est masqué (un taux déduirait aussi le centre) |
| `GET /report` et `GET /snapshots/{mois}/report` (PDF) | totaux de la source | « Total société », total de chaque caisse, synthèse et qualité des soins imprimés « < seuil » |
| `GET /snapshots/{mois}` (instantanés déjà figés) | totaux bruts figés servis tels quels | masqués À LA LECTURE ; `direction_snapshot.payload` n'est JAMAIS réécrit |

Inchangés : séances, chiffre d'affaires, factures, administrations (pas des effectifs de patients) ; zéro reste zéro ; 5 exactement n'est pas masqué ; le booléen
brut `observanceEnRetard` qui sert l'alerte de la Direction (RG-DIR-105).

## Mise en œuvre
- **Domaine Pilotage** : `BilanDuCentre` porte deux indicateurs « composé de masqués » calculés AVANT le masque à `consolider` (propagation à un total de totaux) ;
  `RepartitionDeLaSociete.CaisseDeLaSociete` porte `patientsComposesDeMasques` ; `SeuilDAnonymat.estMasque` et `totalPublieDepuis` (règle relue sur du contenu déjà publié).
- **Domaine Dossier médical** : `EffectifMasque.consolider` / `composeDeMasques` ; `CalculerLesIndicateursCliniques` consolide champ par champ.
- **Instantanés** : `MasqueDesTotauxFiges` (adaptateur `interfaces/publie`) applique `totalPublieDepuis` à l'arbre JSON lu (vue d'ensemble, indicateurs, répartition) dans
  `InstantanesMensuelsPublies.traduire` ; le rapport d'un instantané ancien passe par ce chemin. Défense en profondeur : `ConstituerLeRapportDeSynthese` applique la même règle
  à sa source (totaux de la synthèse, de la ligne « Total société », de chaque caisse, de la qualité des soins), même si la source est un contenu ancien.
- **Frontend** : aucune modification. Les types TS (`CentreStats.patients`, `CentreIndicators`, `CaisseTotal.patients`, `ClinicalIndicators`) sont déjà `number | null` et
  `formatHeadcount(null, seuil)` affiche « < 5 » (KPI « Patients », tableaux, matrice des caisses, comparaison de périodes).
- **Rendu PDF** : inchangé (`count(null)` imprime « < seuil »). Le texte d'aide du pied de page n'est pas modifié (le texte extrait de tous les goldens `pd4` changerait).

## Goldens (un par un, jamais `-Dcharacterization.approve=true` ; anciens archivés dans `_ecarts-legacy/**/*_avant_l1.legacy.json`)
`pd1/tableau_de_bord` : `overview`, `breakdown` ; `pd4/rapport` : `rapport_en_direct` ; `dm8/direction` : `greffes` ; `dm9/direction` : `un_centre_masque`, `deux_centres_masques`,
`serologies_et_greffes` ; chacun devient `*_nouveau_code`. Ajoutés : `pd2/instantanes/ancien_instantane_masque_a_la_lecture_nouveau_code` et
`pd4/rapport/rapport_ancien_instantane_nouveau_code`. `DirectionIntegrationTest` (deux assertions « total publiable ») est mis à jour. Aucun autre golden ne change.

## Recoupements résiduels (consignés, NON traités, DPO)
1. **Effectif d'un centre moins sa ventilation** : `centres[].patients` publié moins la somme des cellules sexe / âge / caisse publiées du même centre révèle une cellule
   masquée unique (la ventilation partitionne l'effectif). Une suppression complémentaire de cellule serait nécessaire (choix de la cellule à sacrifier : décision DPO).
2. **Pourcentages cliniques** par centre sur de petits échantillons (≥ 5 évalués mais un seul dans une classe) : hors du périmètre de KB-MED-104.
3. **Instantanés en base** : leur contenu brut reste dans `direction_snapshot.payload` (immuable, RG-DIR-090) et lisible par un accès SQL ; seule la lecture applicative masque.
4. **PDF et rapports déjà émis** : non rappelés.
5. **Société d'un seul petit centre** : plus jamais de total patients (effet voulu, à annoncer à la Direction).
6. **Le texte de légende du PDF** ne mentionne pas explicitement les totaux supprimés.

## Conséquences
- Les ADR 0087, 0101, 0102, 0103, 0106 restent historiques (comportement reproduit à l'époque) ; ce document les remplace pour ce point.
- Question ouverte pour le DPO : valider la suppression complémentaire (option 9.1(a)), le masquage à la lecture des instantanés anciens, et le traitement des résiduels 1 et 2.
- Retour arrière : revert du lot (aucune donnée n'est modifiée) ; les goldens `_avant_l1.legacy.json` décrivent l'ancien comportement.

## Addendum du 2026-11-10 (lot L8, ADR 0120)
L'intitulé « KB-DIR-01 corrigé » est BORNÉ aux **totaux**. Le recoupement « effectif réel d'un centre moins la ventilation sexe / âge / caisse publiée » reste un **résiduel OUVERT** : **condition de mise en production avec avis du DPO** (2026-12-15), à ne pas lever sans réponse. « L'effectif n'est plus déductible » ne vaut que pour la différence « total moins somme des centres ». Détail et règle unique : ADR 0120.
