---
status: approved
date: 2026-11-13
sources: [pl11-analyse.md, décisions de l'utilisateur du 2026-11-13]
---
# PL11 — décisions de l'utilisateur (à appliquer par toutes les slices PL11-x)

Référence : D1..D12 de `pl11-analyse.md`. Les décisions sont définitives ; elles ne sont « à confirmer métiers/DPO ≤ 2026-12-15 » que là où c'est dit.

| Réf. | Décision |
|---|---|
| D1, D2 | Façade fine `/api/v1/admin/referentials/{kind}` conservée (mêmes URL, rôles ADMIN/SUPERADMIN, codes) ; elle distribue aux contextes ; chaque contexte publie ses descripteurs. |
| D11 | Corriger dans chaque slice concernée (pas de slice de correction séparée), précédée du filet complémentaire (PL11-0). Protocole `*_nouveau_code.approved.json` + archive `_ecarts-legacy/**/*_avant_pl11-N.legacy.json`, goldens relus un par un, KB « CORRIGÉ (date) ». |
| D3 | Le contrôle d'usage compte TOUS les usages (patients, équipements, infirmiers, planification, séances, …), pas seulement patients et équipements. |
| D4 | Salles et créneaux utilisés : désactivation logique au lieu de suppression physique. |
| D10 | Si le contrôle d'usage échoue (erreur technique) : refus de la suppression (jamais « zéro usage » supposé). |
| D5 | Désactiver un médecin utilisé est autorisé (l'historique est conservé). |
| D6 | Un réimport ou une création d'un médecin de même nom réactive le médecin désactivé au lieu de créer un doublon. |
| D7 | Une seule règle d'unicité par centre, appliquée partout (admin ET route d'Organisation). |
| D8 | Une salle modifiée sans capacité conserve sa capacité existante. |
| D9 | L'effet de la fermeture d'une salle sur la planification est hors PL11 (question ouverte à confirmer métiers ≤ 2026-12-15). |
| KB-PL11-13 | SUPERADMIN reste refusé (403 ROLE_SCOPE) sur l'admin des référentiels : REPRODUIT, annotation trompeuse nettoyée, à confirmer métiers ≤ 2026-12-15. |
| KB-PL11-12 | Formes illisibles → 400/405/406/415 propres (JSON seul). Audit enrichi : type de référentiel et identifiant créé enregistrés. |
| D12 | AUCUN report : pas de nouvelle date de butoir, pas d'ADR de report. Les butoirs 2027-03-31 sont soldés par la suppression des entrées. Si un obstacle bloque, STOP et rapporter. |
