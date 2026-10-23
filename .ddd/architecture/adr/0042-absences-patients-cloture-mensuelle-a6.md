---
status: proposed
date: 2026-10-23
deciders: utilisateur (décisions du 2026-10-23) ; réalise le modèle tactique `.ddd/tactical/absences-patients.md` (agrégat `MoisAbsences`, slice A6) et l'ADR 0009 ; calqué sur l'ADR 0037 (F7)
---

# ADR 0042 — Absences de patients : clôture mensuelle des absences (A6)

## Contexte

L'ADR 0009 prévoyait une clôture mensuelle des absences, à faire avant celle de la facturation. A1 à A4 ont refait le
contexte sur place (ADR 0024) ; `TousLesMoisSontOuverts` tenait la place de la clôture (port `MoisDAbsencesOuverts`). Le
filet A6 (`.ddd/safety-net/absences-patients-a6-cloture-mensuelle.md`, 8 tests) a verrouillé le comportement « mois
ouvert » avant le changement.
Le modèle de référence est la clôture mensuelle de la Facturation (F7, ADR 0037).

## Décisions (utilisateur, 2026-10-23)

1. **Agrégat `MoisAbsences`** (identité centre + mois, un mois sans ligne est ouvert), tables `mois_absences` et
   `mois_absences_journal` (journal append-only),
   migration **additive V44** (deux tables, aucune ligne, `IF NOT EXISTS`, portable H2/PostgreSQL 16) et DDL H2 dans
   `db/schema.sql`.
   `MoisAbsencesRepository` étend `MoisDAbsencesOuverts` : un seul adaptateur (`MoisAbsencesJdbc`) pour l'état des
   mois ; `TousLesMoisSontOuverts` est supprimé.
2. **Droits** : `ADMIN` seul clôture et rouvre (`GET /api/v1/absences-patients/mois?annee=` : les quatre profils
   d'accès, l'état d'un mois sert à désactiver les écritures). **Motif de réouverture obligatoire, 500 caractères au
   plus** ; clôture sans motif. Journal SQL, événements d'intégration et journal d'audit des `POST` de la plateforme
   (comme F7).
3. **Clôture sans condition d'état, avertissement chiffré, mais seul un mois terminé se clôture** : le mois en cours et
   les mois futurs au fuseau du centre sont refusés (422 `MOIS_D_ABSENCES_NON_TERMINE`) ; aucun jour de grâce. La
   réponse porte `absencesAQualifierRestantes` et `absencesEnRetard`. Double clôture : 409
   `MOIS_D_ABSENCES_DEJA_CLOS` ; réouverture d'un mois non clos : 409 `MOIS_D_ABSENCES_NON_CLOS` ; motif absent ou trop
   long : 400 `MOTIF_DE_REOUVERTURE_OBLIGATOIRE`.
4. **Effet d'un mois clos** : déclarer, qualifier, annuler et rattraper une absence datée du mois sont refusés (422
   `MOIS_D_ABSENCES_CLOS`, propriété `mois`). Le **rattrapage**
   exige que le mois de l'absence **et** celui de la date de rattrapage soient ouverts (le second n'était pas regardé).
   L' **exclusion** d'une séance de la Facturation qui crée une
   absence dans un mois clos échoue et annule toute l'exclusion (même voie que `ABSENCE_PERIODE_FACTUREE`) ; la
   **réinclusion** d'une séance dont l'absence est dans un mois clos
   est refusée (même code, il faut rouvrir d'abord) et ne supprime rien. Les contrôles se font dans la transaction, par
   `GardeDesMoisDAbsences` (couche application, comme `GardeDesMoisClos`).
5. **Détection de nuit** (détection, réconciliation) : les jours d'un mois clos sont ignorés.
   `POST /rattrapage-detection` expose `joursIgnores` (champ additif, présent seulement
   s'il n'est pas vide, **KB-ABS-37 corrigé**). La notification de nuit ne compte que les mois ouverts. La lecture sous
   verrou (`estOuvertSousVerrou`) est refaite dans la transaction
   de chaque absence créée ou réconciliée : une clôture concurrente ne laisse jamais une absence écrite dans un mois
   clos.
6. **Lien avec la Facturation : avertissement seulement, aucune obligation d'ordre.** La clôture d'un mois de
   facturation répond un champ additif `absencesDuMoisNonCloses`
   (le port `ClotureDesAbsences`, langage publié de la Facturation, est fourni par les Absences comme
   `DeclarationDesAbsences`) ; elle n'est jamais refusée pour cela. La **réouverture
   des absences n'est pas refusée** quand la facturation du mois est close (contrairement à la proposition initiale du
   modèle tactique, décision 3 du 21/10, remplacée). L'ordre de l'ADR 0009 (absences, puis facturation) reste une
   **recommandation**, affichée par les écrans.
7. **Concurrence** : verrou pessimiste de la ligne du mois (`SELECT ... FOR UPDATE`) pris par la clôture, la réouverture
   et chaque écriture d'absence du mois ; ligne créée ouverte si absente (`ON CONFLICT DO NOTHING` sur PostgreSQL,
   `WHERE NOT EXISTS` avec reprise sur H2). Ordre des verrous : absence, puis mois ; mois croissants (rattrapage) ; la
   clôture ne prend que le
   mois (pas d'interblocage possible). Un verrou non obtenu dans le délai de la transaction (`SET LOCAL lock_timeout`, 5
   s sur PostgreSQL, délai propre de H2) : 409
   `ABSENCE_ECRITURE_CONCURRENTE`, comme A2.
8. **Événements** (Outbox Spring Modulith, ADR 0013) :
   `MoisDAbsencesCloture(centre, mois, cloturePar, cloture, absencesAQualifierRestantes, absencesEnRetard)` et
   `MoisDAbsencesRouvert(centre, mois, rouvertPar, rouvert, motif)`, publiés dans la transaction de l'acte par
   `PublicationDesMoisDAbsencesSpring` ; aucun consommateur pour
   l'instant, donc aucune publication incomplète au registre (comme F7).

## Conséquences

- Écarts de comportement décidés, goldens `*_nouveau_code` :
  `cloture_de_facturation_avec_absence_a_qualifier_nouveau_code` (champ additif `absencesDuMoisNonCloses` ; ancien
  golden gardé dans `cycle-de-vie/_ecarts-legacy/mois-ouvert-a6/*.legacy.json`) et les deux goldens F7
  `cloture_nominale_avertissement`, `cloture_sans_seance_avec_corps` (même champ additif). Les sept autres tests du
  filet A6 restent verts sans modification (mois ouverts).
- Un mois d'absences clos qui contient une séance exclue ne peut plus être réincluse tant qu'il n'est pas rouvert
  (arbitrage assumé par l'utilisateur).
- La première clôture d'un mois peut laisser des absences « à qualifier » : elles ne se qualifient plus avant la
  réouverture (motif tracé). Requêtes de recette en lecture seule :
  `.ddd/migration/verifications-production.md` § 27.
- `AbsencePatient` ne connaît pas le mois d'absences : la règle exige un verrou de ligne dans la transaction, donc un
  service d'application (`GardeDesMoisDAbsences`), comme F7.
- Retour arrière : commit précédent ; les tables V44 restent, sans effet (pas de flag, ADR 0024).

## Points à valider

1. ✅ (2026-10-23) Lecture de l'état des mois ouverte aux quatre profils pour désactiver les actions côté écran ; le
   motif de réouverture et les identifiants (`cloturePar`, `rouvertPar`) ne sont renvoyés qu'aux `ADMIN` (correction de
   revue, `.ddd/verify/review-absences-a6.md`).
2. ✅ (2026-10-23) Un seul code `MOIS_D_ABSENCES_CLOS` pour toutes les écritures, la réinclusion et l'exclusion de la
   Facturation.
3. ⏳ Traductions arabe et kabyle : relecture technique faite le 2026-10-23 (arabe : « qualifier » aligné sur « تحديد
   السبب » de l'écran des absences ; kabyle : caractère
   grec `γ` remplacé par `ɣ`, « asiggez » au lieu de « afaturt » comme en F7, « ar ugar » pour « au plus ») ;
   **relecture par des locuteurs natifs toujours requise**
   (suivi hors ADR).
