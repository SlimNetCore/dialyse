---
status: accepted
date: 2026-10-23
deciders: utilisateur (réponses du 2026-10-23 au modèle tactique du Stock)
related: ADR 0006, 0009, 0024, 0029, 0043 ; `.ddd/tactical/stock.md`
---

# ADR 0045 — Stock : sortie pour séances, rapprochement des consommations et décisions du modèle tactique

## Contexte

Aujourd'hui, chaque consommable déclaré pour une séance sort immédiatement du stock (FEFO) par un appel direct de la
Séance au Stock (`BonSortieUseCase`, 4 appelants). Le modèle tactique du Stock (`.ddd/tactical/stock.md`) posait six
questions ; l'utilisateur y a répondu et a
demandé un nouveau processus pour la consommation en séance.

## Décisions

1. **Sortie pour séances** (nouvelle pièce, agrégat `SortiePourSeances`) : le gestionnaire de stock la crée **la
   veille** pour le lendemain ; **plusieurs
   pièces par jour**, chacune pour un **périmètre** (salle et/ou demi-journée `MATIN` / `APRES_MIDI`). À
   l'enregistrement, les lots sortent **réellement** du
   stock (FEFO, PMP courant) ; les mouvements et l'écriture comptable sont datés du **jour d'enregistrement**.
2. **Consommation de séance** (agrégat `ConsommationDeSeance`, un par séance) : les articles de la pièce du périmètre
   sont les **favoris** de l'infirmier ;
   il impute ses consommations sur les **lots de la pièce** (FEFO entre eux) **sans mouvement de stock**. Une quantité
   au-delà du reste, un article hors pièce
   ou l'absence de pièce sont **acceptés avec alerte**. La Séance appelle le port publié synchrone
   `ConsommationsDeSeance` fourni par le Stock (fin des
   appels directs à `BonSortieUseCase`).
3. **Rapprochement** : par lot, écart = consommé − sorti ; tout écart non nul exige un **motif**. La validation
   (`PHARMACIEN` — gestionnaire de stock —,
   `ADMIN`, **`DIRECTEUR_ADMINISTRATIF`**) crée une **sortie supplémentaire** (écart en plus, PMP courant) ou un
   **retour en stock** dans le lot d'origine (écart en
   moins, valorisé à la **valeur de sortie**, nouveau mouvement `RETOUR` traité comme une entrée au prix). Le
   rapprochement est **obligatoire avant la prochaine
   pièce** du même périmètre, et avant l'ouverture d'un inventaire.
4. **Nouveau profil `DIRECTEUR_ADMINISTRATIF`** : rapprochement des sorties pour séances et **lecture seule** du stock ;
   aucune autre écriture.
5. **Verrou de recalcul du PMP** : le verrou mémoire (`PmpRecalculationCoordinator`, `ConcurrentHashMap`) est remplacé
   par le **verrou de ligne** de l'article ;
   rejeu synchrone par article à la réception ; recalcul massif asynchrone, une transaction par article.
6. **Transactions multi-articles** (réception, sortie, sortie pour séances, rapprochement, clôture d'inventaire) : **un
   bon entier ou rien**, verrous par identifiant
   d'article croissant (délai ADR 0043).
7. **Clôture mensuelle du stock** : la clôture d'inventaire est la clôture (ADR 0009) ; pas de clôture de mois sans
   comptage.
8. **Lots proches de l'expiration** : notification (pharmacien, admin) au lieu du seul journal technique (slice S8).
9. **`ANNULE`** retiré du glossaire des bons : un brouillon se modifie.

## Conséquences

- Écart de comportement majeur pour les infirmiers et le gestionnaire de stock : la séance ne fait plus baisser le
  stock ; le stock baisse à la préparation et se
  corrige au rapprochement. La valorisation comptable suit les pièces (sortie pour séances, sorties supplémentaires,
  retours), plus les séances.
- Slice dédiée **S6b** (migration additive : pièces, lignes, consommations, mouvement `RETOUR`) ; les sorties de séance
  historiques restent lisibles.
- Points restant à trancher avec S6b (`tactical/stock.md`, questions ouvertes) : paramètre de bascule par centre, qui
  saisit le motif d'un écart, lot de la sortie
  supplémentaire, base du coût par séance pour le pilotage.
- Le nouveau rôle touche le contexte générique Identité & accès (création de comptes, écrans, i18n) et la documentation
  des profils (RG-SEC).

