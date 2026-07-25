---
name: gestion-seances-hemodialyse
description:
  Règles métier et architecture pour le module de gestion des séances d'hémodialyse (scan badge/QR code, création de séance, forfait, constantes paramédicales, consommables, cycle de vie de la séance: présence, terminée, facturée, absence, calcul de la perte liée aux absences). À utiliser dès qu'une tâche touche à l'entité Séance, au parcours patient en centre, à la saisie infirmier pendant une séance, à la consommation d'articles de stock liée à une séance, au workflow de statut d'une séance (présence, clôture, facturation, absence), ou à la préparation de l'intégration future du générateur d'hémodialyse.
---

# Gestion des séances d'hémodialyse

## Objectif métier

Une séance d'hémodialyse est l'unité centrale du dossier de suivi patient en centre. Elle démarre par le scan d'un
badge (QR code) à l'arrivée du patient, et se remplit progressivement pendant la séance avec des données paramédicales
et des consommables utilisés. Elle sert à la fois de dossier de soin, de pièce justificative pour la facturation CNAS (
forfait de séance), et de base pour le suivi des absences patient (nombre et valeur de la perte).

Respecte les contraintes archi déjà définies dans `AGENTS.md` (hexagonal + DDD, isolation multi-centre par `centerId`,
zoneless Angular + NgRx Signals, pas d'API dépréciées). Ce skill ne répète pas ces règles, il documente le domaine
métier spécifique aux séances.

## Cycle de vie d'une séance

Déclenché par le scan : à l'arrivée, le badge du patient (imprimé depuis l'application) contient un QR code. Le scan (
téléphone ou douchette) crée une séance à l'état `PRESENCE`.

États confirmés :

```
CREE   (scan badge, patient présent)
   → VALIDEE  (l'infirmier valide/clôture la séance)
   → FACTUREE  (la séance a été intégrée à une facture)

ABSENCE    (le patient ne s'est pas présenté — état parallèle, n'est pas atteint via une transition depuis PRESENCE)
```

Règle d'idempotence à implémenter : avant de créer une nouvelle séance sur scan, vérifier s'il existe déjà une séance
non terminée pour ce patient sur la journée courante (même `centerId`). Si oui, rouvrir/afficher celle-ci plutôt que
d'en créer une seconde — évite les doublons en cas de double scan.

`FACTUREE` ne devrait pas être un statut positionnable manuellement depuis l'écran séance : c'est le module de
facturation qui l'attribue quand la séance est effectivement intégrée à une facture (événement consommé depuis ce
module, ou mise à jour directe selon le découpage retenu). Ça évite qu'une séance se retrouve marquée facturée sans
facture réelle derrière.

### ABSENCE et calcul de la perte

Une séance `ABSENCE` alimente deux statistiques : le nombre total de séances absentes et la valeur de la perte associée
sur une période donnée. Pour que ce calcul soit possible, une séance en `ABSENCE` doit porter un **forfait attendu** (
repris comme pour une séance normale, cf. section Forfait) — c'est ce montant qui sert de base au calcul de la perte.

**Point ouvert important** : comment une séance atteint-elle l'état `ABSENCE` ? Deux pistes possibles, à trancher avec
toi :

- **Option A — planning prévisionnel** : le système connaît le protocole du patient (ex. lundi/mercredi/vendredi) et
  génère à l'avance une séance attendue par créneau. Si aucun scan n'arrive sur le créneau (constaté par un job de
  clôture de journée ou une action explicite du personnel), elle bascule en `ABSENCE`.
- **Option B — déclaration manuelle** : pas de planning formel pour l'instant ; le secrétariat ou l'infirmier déclare
  explicitement qu'un patient ne viendra pas, sans séance « attendue » pré-existante.

L'option A suppose un concept de planning/créneau qui n'a pas encore été évoqué — si ce module n'existe pas encore chez
vous, l'option B est plus rapide à mettre en place mais moins fiable pour détecter systématiquement les absences (elle
dépend de quelqu'un qui pense à les déclarer).

Le comptage et la valorisation de la perte (nombre de séances `ABSENCE` sur une période × leur forfait respectif) sont a
priori un besoin de reporting — à traiter comme une requête/read-model dédiée plutôt qu'un champ recalculé et stocké,
sauf besoin avéré de performance sur de gros volumes historiques.

## Concepts du domaine

### 1. Snapshot patient

À la création, la séance capture une **copie figée** (value object, pas une référence live) des informations patient :
nom, prénom, sexe, âge (calculé depuis la date de naissance au moment du scan), groupe sanguin. C'est un choix DDD
volontaire : le dossier de séance doit rester fidèle à ce qu'il était au moment des soins, même si la fiche patient est
corrigée plus tard. Toute correction de la fiche patient ne doit pas rétroactivement modifier les séances passées.

### 2. Forfait de séance

Le forfait est repris par défaut depuis la fiche patient (probablement lié à la distinction kit CNAS / hors CNAS déjà
identifiée pour la facturation), mais reste **modifiable au niveau de la séance**. Points à traiter :

- Stocker le forfait comme un champ propre à la séance (copié à la création, pas une simple référence calculée à la
  volée), pour que la facturation d'une séance passée reste stable même si le forfait par défaut du patient change
  ensuite.
- Toute modification du forfait par défaut doit être tracée (qui, quand, ancien/nouveau forfait) — c'est une donnée
  sensible pour la facturation CNAS, donc traçabilité = exigence, pas option.
- Valider le forfait choisi contre le catalogue des forfaits actifs pour le centre (`centerId`).

### 3. Constantes paramédicales

Aujourd'hui saisies manuellement par l'infirmier, en attendant l'intégration future avec le générateur d'hémodialyse. *
*Le plus important ici : concevoir le modèle et les ports pour que la source de la donnée (manuelle vs automatique) soit
un détail d'infrastructure, invisible du domaine.**

Approche recommandée (hexagonal) :

- Un port `SaisieConstantesPort` (ou équivalent) avec une seule implémentation aujourd'hui : `SaisieManuelleAdapter` (
  formulaire infirmier).
- Le jour où le générateur est intégré, on ajoute `GenerateurHemodialyseAdapter` qui implémente le même port — aucune
  modification du domaine ni de l'use case `SaisirConstantesParamedicales`.
- Champ `source` sur l'entité constantes (`MANUELLE` / `GENERATEUR`) pour la traçabilité, même si un seul adaptateur
  existe pour l'instant.
- Prévoir un flag de validation/verrouillage (l'infirmier confirme les valeurs saisies ou reçues avant qu'elles soient
  figées sur la séance).

Champs typiques à discuter avec l'équipe médicale (liste indicative, **à valider**, pas à considérer comme définitive) :
poids sec cible, poids avant/après séance, tension artérielle avant/pendant/après, fréquence cardiaque, température,
débit sang (Qb), débit dialysat (Qd), volume d'ultrafiltration prévu/réalisé, débit héparine, durée effective,
observations libres.

### 4. Consommables de séance

L'infirmier saisit les articles consommés (dialyseur, seringue, gants, ampoule EPO, ampoule fer, etc.), issus du *
*module de gestion des stocks** déjà conçu (PMP, FEFO, bons de sortie).

Règles d'intégration :

- Chaque ligne de consommable ajoutée à une séance doit générer (ou alimenter) un **bon de sortie** rattaché au centre
  de la séance — pas de sortie de stock qui échappe au module stock.
- La sélection du lot suit la règle **FEFO** automatiquement ; ne pas laisser l'infirmier choisir le lot manuellement
  sauf cas d'exception explicite (override rare, à motiver).
- Le coût (valorisation PMP) est calculé côté stock, pas saisi par l'infirmier — **ne pas exposer le coût
  unitaire/valorisé sur l'écran infirmier**, ce n'est pas son information de travail et ça alourdit l'écran. Réservé aux
  vues admin/facturation.
- Vérifier la disponibilité en stock du centre (`centerId`) avant de permettre l'ajout ; bloquer ou avertir si rupture.

> Section Traitements (médecin) volontairement retirée pour l'instant — à réintroduire plus tard si besoin.

## Placement architectural (hexagonal / DDD)

- **Domaine** : agrégat racine `Seance` (ou `SeanceHemodialyse`), value objects `PatientSnapshot` et
  `ConstantesParamedicales`, entité `LigneConsommable`. Événements de domaine à considérer : `SeanceCreee` (= passage à
  `PRESENCE`), `ConsommableAjoute`, `ForfaitModifie`, `SeanceTerminee`, `SeanceFacturee`, `SeanceMarqueeAbsente`.
- **Application** : use cases dédiés plutôt qu'un gros service `SeanceService` fourre-tout — ex.
  `CreerSeanceDepuisBadge`, `ModifierForfaitSeance`, `SaisirConstantesParamedicales`, `AjouterConsommableSeance`,
  `ClôturerSeance` (→ `TERMINEE`), `MarquerSeanceAbsente` (→ `ABSENCE`), plus une requête dédiée pour les statistiques
  d'absence (nombre + valeur de la perte sur une période).
- **Ports sortants** : `PatientRepositoryPort` (lecture snapshot), `StockPort` (disponibilité + création bon de sortie),
  `ForfaitCatalogPort`, `SaisieConstantesPort` (cf. section 3).
- **Adaptateurs** : contrôleur REST pour le endpoint de scan (ex. `POST /api/seances/scan`), adaptateur de persistance,
  adaptateur interne vers le module stock (appel direct ou via SQS selon le pattern déjà utilisé ailleurs dans le
  projet).
- **Isolation multi-centre** : `centerId` porté par `Seance` dès la création (dérivé du point de scan/de la borne), et
  systématiquement injecté dans les requêtes vers le module stock — jamais de consommation d'un lot d'un autre centre.

## UI/UX — écran infirmier (priorité : rapidité de saisie des consommables)

Contraintes fortes côté ergonomie, l'infirmier est en situation de charge clinique, pas confortablement assis :

- **Accès rapide aux articles fréquents** : chips/boutons pré-affichés pour les consommables récurrents (dialyseur,
  seringue, gants, ampoule EPO, ampoule fer) avec stepper de quantité — zéro recherche nécessaire pour le cas courant.
- **Recherche/autocomplete** pour les articles moins fréquents, filtrée sur le catalogue stock disponible du centre.
- **Feedback immédiat** : état local en signal (optimistic UI), la ligne apparaît instantanément dans la liste de la
  séance sans attendre l'aller-retour serveur ; réconciliation silencieuse ensuite.
- **Pas de bruit visuel** : aucune donnée de coût/valorisation sur cet écran (cf. section 4).
- Support des deux modes de scan du badge : caméra du téléphone (`getUserMedia`) et douchette USB/Bluetooth qui se
  comporte comme un clavier — le champ de saisie du scan doit gérer les deux sans configuration différente côté
  utilisateur.
- Suggestion de découpage d'écran : bandeau patient (snapshot + forfait éditable + statut de la séance) en haut, onglets
  ou sections en dessous pour Constantes / Consommables, avec Consommables mis en avant (c'est l'écran le plus
  utilisé/le plus répétitif dans la journée).
- Le statut `ABSENCE` ne s'affiche pas sur cet écran infirmier orienté « séance en cours » — il relève plutôt d'un écran
  de déclaration/planning séparé (cf. point ouvert sur l'origine de l'état `ABSENCE`).

## Points ouverts à valider avec toi

Je les ai marqués au fil du document, je les regroupe ici pour référence rapide :

1. Comment une séance atteint l'état `ABSENCE` — planning prévisionnel (option A)  ? Cf. section « ABSENCE et calcul de
   la perte ».
2. Liste définitive des champs de constantes paramédicales à saisir/à terme récupérer du générateur.
3. Modalités exactes de traçabilité du changement de forfait (champ d'audit dédié, ou événement de domaine suffisant ?).
4. Mécanisme exact de déclenchement de la transition `FACTUREE` côté module facturation (événement, appel synchrone,
   batch) — à affiner quand ce module sera plus avancé.

Je peux te préparer une maquette de l'écran consommables infirmier si tu veux visualiser l'agencement avant de le coder.
