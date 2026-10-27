---
status: proposed
date: 2026-10-26
decision-makers: [utilisateur (décisions du 2026-10-26, communiquées pour la slice P3), architecte]
context-scope: planification
related: ADR 0024, 0049, 0051, 0053, 0054, 0055, 0056 ; `.ddd/tactical/planification.md` P14 à P16 ; KB-PLN-25 à 39
---

# 0057 — Planification P3 : verrou de la fiche de l'infirmier, fuseau du centre, auteur des absences, lecture publiée du roulement

## Contexte et problème

La slice P3 (roulement, absences, remplacements, « Mon planning » : 12 routes) écrit trois tables
(`infirmier_affectation`,
`infirmier_absence`, `infirmier_remplacement`) dont les invariants d'ensemble n'étaient protégés que par du contrôle
puis écriture sans
verrou (B4, B5 : deux affectations chevauchantes créées en parallèle ; 500 sur la contrainte d'unicité des
remplacements). Les lectures de la
présence, de la charge et de l'optimisation (P4, P8) restent dans le legacy et lisaient ces tables directement.

## Décisions

1. **Verrou de la fiche de l'infirmier** (ADR 0049 : « roulement : verrou de la ligne `infirmier` »). Chaque écriture du
   roulement, de
   l'absence d'un infirmier et chaque affectation de remplaçant lit la fiche par `InfirmiersRepository.pourEcriture`
   (`SELECT … FOR UPDATE`)
   **avant** de juger ; le jugement et l'écriture se font dans la même transaction (`UniteDeTravail`, `lock_timeout` 5
   s). **Seules les écritures qui jugent une règle d'ensemble prennent ce verrou** (ajout/modification d'une affectation,
   déclaration d'absence, affectation d'un remplaçant). Les trois **suppressions** (`RetirerLAffectation` le prend quand même
   pour lire un roulement cohérent ; `RetirerUneAbsence`, `RetirerMonAbsence`, `AnnulerLeRemplacement` ne le prennent pas)
   ne protègent aucun invariant : supprimer une ligne par identifiant est idempotent et ne peut violer ni le non-chevauchement
   ni l'unicité. Les écritures ne portent que sur la ligne touchée (`ajouter`, `remplacer`, `retirer`) : une ligne écrite par
   ailleurs n'est jamais réécrite ni supprimée par déduction. Un verrou non
   obtenu, un interblocage ou une contrainte d'unicité violée devient `EcritureConcurrente` = 409
   `PLANIFICATION_ECRITURE_CONCURRENTE`. **Ordre global des verrous inchangé** (ADR 0056 § 2) : centre → fiches
   `infirmier` par identifiant → `app_user` → verrou consultatif.
   P3 ne prend **pas** le verrou du centre (aucune règle d'ensemble du centre n'est en jeu) et ne verrouille jamais qu'
   **une** fiche par
   transaction (le remplaçant) : un infirmier remplacé est lu sans verrou. Aucun cycle possible avec P2 (centre →
   fiche → compte).
2. **Remplaçant éligible = preuve construite sous verrou** : `RemplacantEligible.prouver(Situation)` (domaine) ; la
   situation (actif, habilité,
   salle d'isolement, absent ce jour, déjà prévu sur le créneau par le roulement ou un autre remplacement) est constatée
   par le cas d'usage
   sous le verrou de la fiche du remplaçant. La contrainte `UNIQUE (center_id, date_jour, creneau_id, infirmier_id)`
   reste le dernier filet.
3. **Absences : pas de chevauchement** (`ABSENCE_CHEVAUCHEMENT`, 422), règle d'ensemble de l'infirmier jugée par la
   politique
   `AbsencesSansChevauchement` sous le verrou de sa fiche ; vaut pour l'administration comme pour « Mon planning ». Les
   absences contiguës sont
   acceptées. Les chevauchements **déjà en base** ne sont pas corrigés (contrôle SELECT-only de recette :
   `verifications-production.md` § 34).
4. **« Aujourd'hui » au fuseau du centre** (ADR 0054) : port `HorlogeDesCentres`, ACL
   `legacycentres/HorlogeDesCentresLegacy`
   (`centers.fuseau_horaire`, V46 ; à défaut `app.planification.fuseau-par-defaut`, `Africa/Algiers`). Utilisé pour la
   semaine par défaut de
   « Mon planning », `ABSENCE_PASSEE`, `ABSENCE_NON_ANNULABLE`, la date d'un remplacement et l'avertissement de
   sur-effectif.
5. **Auteur des absences** (KB-PLN-39) : **seule migration de la slice**, V47 `infirmier_absence.declaree_par uuid NULL`
   (additive, sans index,
   sans donnée modifiée, retour arrière = ignorée par l'ancien code). L'administration et le secrétariat enregistrent
   leur compte ; « Mon
   planning » enregistre celui de l'infirmier. Un infirmier ne retire que les absences **dont il est l'auteur** (ou sans
   auteur connu :
   comportement historique conservé pour l'existant, à resserrer plus tard si l'exploitation le décide) ; le retrait par
   l'administration/secrétariat reste permis pour toute absence. *Interprétation retenue de « retrait d'une absence
   créée par l'administration
   réservé à son auteur »* : l'infirmier concerné ne peut pas retirer ce que l'administration a saisi (il pouvait le
   faire avant) ; l'auteur
   étant conservé, restreindre aussi l'administration à l'auteur exact reste possible sans nouvelle migration.
   **Validé par l'utilisateur (confirmation écrite du 2026-10-27)** : l'infirmier ne retire que ses propres déclarations ;
   l'administration et le secrétariat retirent toute absence ; les absences sans auteur (antérieures à V47) restent
   retirables par l'infirmier concerné.
6. **Ports vers le legacy (ACL dans `platform/.../infrastructure/acl/planification/`, temporaires)** :
   `CalendrierDuCentreLegacy` (jour fermé),
   `SurEffectifDuRoulementLegacy` et `SemaineDeLInfirmierLegacy` (lectures de présence et de planning de la semaine,
   P4/P5),
   `NotificationsDesAbsencesDInfirmiersLegacy` (journal durable + diffusion temps réel). Le calcul de présence reste
   dans `bc-seance`.
7. **Langage publié de lecture** `publishedlanguage.RoulementEtAbsencesDesInfirmiers` (roulement des infirmiers actifs,
   absences d'une période,
   remplacements d'une période) : `PresenceDonneesJdbcAdapter` (présence, charge, alertes, ordonnanceur, optimisation)
   le consomme au lieu de lire
   les tables. Le modèle de présence du legacy (`AffectationInfirmier`, `AbsenceInfirmier`, `RemplacementInfirmier`)
   reste en lecture dans `bc-seance`.
8. **Entrées** alignées sur P1/P2 : 400 `BAD_REQUEST` (corps, jours, dates, types, identifiants, `centerId`, motif >
   255, pagination négative), 415
   hors JSON, **403 avant le corps**, `size` plafonnée à 100. Les entrées illisibles passent avant les 422 (ordre :
   entrée, existence, droits/état).
9. **Notification du retrait d'une absence** (`INFIRMIER_ABSENCE_RETIREE`, durable, diffusée) pour rafraîchir le
   planning en temps réel.
10. **Audit métier** : 9 libellés (`AFFECTATION_AJOUTEE|MODIFIEE|RETIREE`, `ABSENCE_DECLAREE|RETIREE`,
    `ABSENCE_DECLAREE|RETIREE_PAR_L_INFIRMIER`,
    `REMPLACANT_AFFECTE`, `REMPLACEMENT_ANNULE`) dans `AuditActionResolver`, identifiant de l'entité écrite transmis par
    l'attribut
    `hemodialyse.audit.entityId`.

## Dette connue

- `OptimisationApplicationService` (P8) écrit encore `infirmier_affectation` et `infirmier_remplacement` par les ports
  legacy (`AffectationInfirmierRepositoryPort`, `RemplacementInfirmierRepositoryPort`). **Risque traité (revue P3, constat 1)** :
  (1) les écritures de P3 ne touchent plus que la ligne concernée, donc une ligne écrite par P8 pendant une écriture de P3 n'est
  plus supprimée ni écrasée ; (2) P8 prend désormais le verrou des fiches concernées avant de lire/écrire (port publié
  `FichesDesInfirmiers.verrouillerLesFiches`, par identifiant croissant, dans sa transaction `@Transactional`), donc P3 et P8
  se sérialisent par infirmier ; prouvé par `should_make_the_optimisation_wait…` (H2 et PostgreSQL 16) et
  `should_not_lose_an_assignment_written_directly…`. **Reste** : P8 écrit toujours par SQL direct (ports legacy) et ne juge pas
  l'invariant de non-chevauchement du roulement (RG-INF-070 le promettait par le calcul de la proposition, pas par la base) ;
  retrait avec P8, qui passera par le contexte.
- L'infirmier concerné d'une absence antérieure à V47 (sans auteur) peut encore retirer une absence saisie par
  l'administration.
- `SemaineDeLInfirmierLegacy` recalcule toute la semaine de présence et de planning à chaque lecture (comme avant).
- L'ACL `legacyinfirmier/InfirmiersDuCentreJdbc` (P1) devrait lire `InfirmiersRepository` (sans changement de
  comportement).

## Conséquences

+ B4, B5, B6, B9 corrigés ; 500 de la contrainte d'unicité des remplacements supprimé ; écritures de l'infirmier
  sérialisées ; plus de lecture
  directe des trois tables par la présence.
+ Retour arrière = redéploiement (V47 est ignorée par l'ancien code).
  − Une écriture de roulement, d'absence ou de remplacement attend les autres écritures du **même infirmier** (admin
  seulement, volume négligeable).
  − Un nouveau refus 422 (`ABSENCE_CHEVAUCHEMENT`, `INFIRMIER_INACTIF`, `REMPLACEMENT_DATE_PASSEE`,
  `REMPLACEMENT_JOUR_FERME`,
  `REMPLACEMENT_REMPLACE_NON_CONCERNE`) : les clients (frontend) les traduisent en fr/en/ar/kab.
