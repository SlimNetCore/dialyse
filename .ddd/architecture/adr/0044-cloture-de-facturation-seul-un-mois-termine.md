---
status: accepted
date: 2026-10-23
deciders: utilisateur (« comme les absences », 2026-10-23) ; suite de la revue `.ddd/reviews/2026-10-23-facturation-f7.md` (constat 4)
revises: ADR 0037 (décision « aucune précondition de chronologie »), fiche F7 « À valider » point 5
related: ADR 0009, 0042
---

# ADR 0044 — Clôture de facturation : seul un mois terminé se clôture (alignement sur les Absences)

## Contexte

F7 (ADR 0037) acceptait de clôturer n'importe quel mois, y compris le mois en cours et les mois futurs. A6 (ADR 0042)
refuse le mois en cours et les
mois futurs (`MOIS_D_ABSENCES_NON_TERMINE`). Le même terme métier, « clôturer un mois », suivait donc deux règles.
Clôturer un mois futur n'a pas de
sens métier : cela fige d'avance des séances qui n'existent pas encore.

## Décision

1. **Seul un mois terminé se clôture**, au fuseau du centre, sans jours de grâce : le mois précédent se clôture dès le
   premier jour du mois. Le mois
   en cours et les mois futurs sont refusés **422 `PERIODE_NON_TERMINEE`** (propriété `mois`), avant tout verrou, sans
   journal ni événement.
2. La règle est portée par l'agrégat (`MoisDeFacturation.exigerTermine(mois, aujourdhuiAuCentre)`) ; le cas d'usage
   `CloturerLeMois` calcule
   « aujourd'hui au centre » avec le port `FuseauxDesCentres` (adaptateur `FuseauParDefaut` :
   `app.facturation.fuseau-par-defaut`, par défaut celui des
   Absences, `Africa/Algiers`), comme `HorlogeDesCentres` des Absences.
3. Inchangé : aucune précondition d' **état** (séances restantes = avertissement), double clôture 409, réouverture avec
   motif, refus de réouverture
   si la comptabilité du mois est close.

## Conséquences

- Écart décidé : `cloture_sans_seance_avec_corps` porte désormais sur un mois passé (2026-03 au lieu de 2027-03) ;
  nouveau golden
  `cloture_mois_futur_422`. Le test A6 `la_cloture_de_facturation_ne_depend_pas_des_absences_a_qualifier...` utilise une
  absence du mois précédent (jour nommé `<FIN_DU_MOIS_PRECEDENT>`).
- Frontend : le bouton « Clôturer » est désactivé pour le mois en cours et les mois futurs (comme l'écran des mois
  d'absences) ; code d'erreur
  traduit en fr/en/ar/kab.
- Règle de gestion RG-FAC-040 mise à jour.
- Aucun mois futur n'a pu être clos en production avant ce changement sans action d'un administrateur ; contrôle de
  recette :
  `verifications-production.md` § 28.

