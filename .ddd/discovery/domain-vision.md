---
status: draft
date: 2026-10-09
sources:
  - .ddd/scan/summary.md
  - .ddd/discovery/legacy-analysis.md
  - docs/reference/regles-de-gestion/README.md (l. 30-47), 12-facturation-reglements-comptabilite.md (l. 1-110)
  - Grep ciblés (statuts, méthodes mutatrices, @Scheduled, @EventListener) — voir event-storming.md
validation: aucune validation experte à ce stade (atelier non tenu, outil de question indisponible pour le sous-agent)
---

# Vision du domaine — Hémodialyse

## Vision (≤ 10 lignes)
Permettre à une société exploitant un ou plusieurs centres d'hémodialyse de **réaliser, tracer et valoriser chaque séance de dialyse** :
placer chaque patient sur une place (salle, créneau, générateur, jours), garantir qu'il est dialysé (ou que son absence est
détectée, qualifiée et valorisée), consommer et tracer le matériel, documenter le suivi médical (dont la préparation à la greffe),
puis **facturer les séances couvertes par une prise en charge** et en tirer la comptabilité.
La plateforme ne se limite pas à stocker : elle **surveille** (absences, observance, sous-effectif, expirations), **propose**
(places, remplaçants, planning optimisé) et **protège** (clôtures, immuabilité des séances facturées, isolement par centre)
— cf. `docs/reference/regles-de-gestion/README.md:34-47`.

## Utilisateurs (rôles observés)
| Rôle | Usage principal | Source |
|---|---|---|
| Infirmier | valide la séance au poste (QR), saisit volet paramédical et consommables, déclare ses absences | `Seance.java:90`, `MonPlanningInfirmierService.java:102` |
| Médecin / néphrologue | signe la séance, ordonnances, examens, bilan pré-greffe, simulation/validation facturation | `Seance.java:178`, `Ordonnance.java:86`, RG-FAC-022/030 |
| Secrétaire | patients, assurés, PEC, règlements, absences | RG-REG-001, doc 04 |
| Administrateur (centre) | paramétrage, déverrouillage pour régularisation, clôture de période comptable | RG-SEA-046, RG-CPT-005 |
| Pharmacien | stock, inventaires | RG-STK (doc 08 l. 176) |
| Technicien / GMAO | équipements, interventions, maintenance préventive | `Intervention.java:184` |
| Direction (société) | tableaux de bord anonymisés multi-centres, alertes, instantanés mensuels | RG-DIR-010, RG-DIR-090 |

## Valeur / différenciation (hypothèses à valider)
- La **séance réalisée et facturable** est l'unité de valeur : tout converge vers elle (planning en amont, stock/absences/facturation en aval) — co-changement maximal `bc-seance`.
- **Ne perdre aucune séance** : détection nocturne des absences + valorisation de la perte (TTC → HT), régularisation des séances oubliées.
- **Planification assistée** (placement 0-100, optimisation, replanification nocturne, remplaçants infirmiers) — investissement algorithmique fort (Timefold).
- Spécificité locale : plan comptable **SCF**, caisses d'assurance / agences / centres payeurs, PEC à forfait, attestations de droits.

## Indices de cœur de domaine (à confirmer, pas une décision)
| Indice | Sous-domaine pressenti | Signal |
|---|---|---|
| Fort | Réalisation de la séance (cycle CREE → VALIDEE → SIGNEE → FACTUREE) | hotspots, co-change 982, règles RG-SEA |
| Fort | Planning / placement / présence infirmiers | cluster C1 cohésion 0.92, optimisation dédiée |
| Moyen | Absences patients et valorisation | règles nombreuses RG-ABS, cron + rattrapage |
| Moyen | Suivi médical et parcours greffe | 10+ sous-ensembles cohérents |
| Support | Stock (PMP, lots, inventaire), GMAO, facturation/règlements, comptabilité SCF | règles réglementaires standard |
| Générique | Sécurité/MFA, licence, audit, notifications, import/reprise | outillage |

## Hors périmètre (supposé)
- Frontend (exclu du scan), infrastructure, outillage d'import ponctuel (`bc-migration`, à confirmer — Q12 legacy-analysis).
- Systèmes externes : logiciel comptable (réception de l'export), laboratoires (résultats ?), échanges FHIR, serveur de licences.

## Questions ouvertes
1. Qui « gagne de l'argent » : la société (multi-centres) ou chaque centre ? La direction est-elle cliente ou utilisatrice ?
2. Priorité business pour la première slice (GMAO isolé ? comptabilité ? facturation ?) — reprise de Q11 legacy-analysis.
3. Consommateurs externes de l'API/base (BI, caisses, laboratoires) ? — Q10 legacy-analysis.
