# Référentiel des règles de gestion — Hemodialyse

> **Document de référence.** Il décrit, de façon exhaustive et vérifiable, toutes les règles de gestion appliquées par
> la plateforme. Il est la source des documentations fonctionnelle et commerciale.
> Version du référentiel : **1.0** — établi sur la base du code de la branche `chore/angular-22-upgrade`.

## Comment lire ce référentiel

Chaque règle porte un **identifiant stable** `RG-XXX-nnn` (les identifiants ne sont **jamais renumérotés ni
réutilisés** : une règle supprimée laisse un trou ou est marquée « supprimée »). Elle précise, quand c'est
utile, le **code d'erreur** renvoyé à l'utilisateur (`CODE_METIER`) et la **source** dans le code. Un encart **« Point
d'attention »** signale un écart connu entre l'intention métier et le comportement actuel : il
est conservé jusqu'à correction, par honnêteté envers les équipes qui s'appuient sur ce document.

| Préfixe                             | Domaine                                                                                                      | Fichier                                                                                |
|-------------------------------------|--------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------|
| `RG-TRV`                            | Règles transverses : multi-centres, erreurs, pagination, valeurs, cache, i18n, temps réel, tâches planifiées | [01-transverse.md](01-transverse.md)                                                   |
| `RG-SEC`                            | Sécurité : connexion, MFA, mots de passe, rôles, licences, journal d'audit                                   | [02-securite-acces.md](02-securite-acces.md)                                           |
| `RG-ORG`                            | Organisation : sociétés, centres, comptes administrateur et direction, logo                                  | [03-organisation.md](03-organisation.md)                                               |
| `RG-PAT` `RG-ASS` `RG-ATT` `RG-PEC` | Patients, assurés, attestations de droits, prises en charge                                                  | [04-patients-assures-pec.md](04-patients-assures-pec.md)                               |
| `RG-PLN`                            | Planification : placement, planning, isolement, salles, capacité                                             | [05-planning-placement.md](05-planning-placement.md)                                   |
| `RG-ABS`                            | Absences des patients et leur valorisation                                                                   | [06-absences-patients.md](06-absences-patients.md)                                     |
| `RG-SEA`                            | Séances de dialyse, volets, consommables, calendrier                                                         | [07-seances.md](07-seances.md)                                                         |
| `RG-STK`                            | Stock : articles, bons, lots, PMP, inventaire, alertes, groupes                                              | [08-stock.md](08-stock.md)                                                             |
| `RG-GMA`                            | GMAO : équipements, interventions, coûts, aide à la réforme                                                  | [09-gmao.md](09-gmao.md)                                                               |
| `RG-INF`                            | Infirmiers : roulement, présence, absences, remplaçants, charge                                              | [10-infirmiers.md](10-infirmiers.md)                                                   |
| `RG-MED`                            | Dossier médical, anémie, KDIGO, greffe, export                                                               | [11-dossier-medical.md](11-dossier-medical.md)                                         |
| `RG-FAC` `RG-REG` `RG-CPT`          | Facturation, TVA, règlements, comptabilité                                                                   | [12-facturation-reglements-comptabilite.md](12-facturation-reglements-comptabilite.md) |
| `RG-DIR`                            | Direction de société : tableaux de bord anonymes, alertes, instantanés                                       | [13-direction.md](13-direction.md)                                                     |
| `RG-REF` `RG-DOC` `RG-MIG`          | Référentiels, modèles de documents, imports, reprise de données                                              | [14-referentiels-documents-reprise.md](14-referentiels-documents-reprise.md)           |
| `RG-NOT`                            | Notifications et catalogue des alertes                                                                       | [15-notifications-alertes.md](15-notifications-alertes.md)                             |
| —                                   | Annexe de traçabilité : contrôleurs REST et tâches planifiées                                                | [16-annexe-tracabilite.md](16-annexe-tracabilite.md)                                   |

## Ce que le système fait au-delà du stockage de l'information

Le référentiel met en évidence trois familles de règles qui font de la plateforme un outil d' **aide à la décision** :

- **Surveiller** : détection nocturne des absences (RG-ABS-040), contrôle d'observance EPO/fer (RG-MED-073),
  sous-effectif d'infirmiers à 14 jours (RG-INF-046), conflits du planning (RG-PLN-041), alertes de
  stock (RG-STK-060), cibles cliniques KDIGO (RG-MED-081), alertes de la direction (RG-DIR-031), indisponibilité des
  équipements (RG-GMA-060), licence (RG-SEC-036) — voir le catalogue [15](15-notifications-alertes.md).
- **Proposer** : meilleures places de dialyse classées de 0 à 100 (RG-PLN-030), remplaçants d'infirmiers classés
  (RG-INF-051), réforme d'un équipement (RG-GMA-063), numérotation et simulation de facturation (RG-FAC-022), cibles de
  greffe et risque immunologique (RG-MED-082).
- **Protéger** : refus d'un placement incohérent (RG-PLN-050), replacement automatique en isolement (RG-PLN-053), gel du
  stock pendant un inventaire (RG-STK-052), clôture des absences et séances facturées (RG-ABS-024, RG-SEA-002), anonymat
  de la direction (RG-DIR-010), isolement par centre (RG-TRV-002).

## Règle de maintenance (obligatoire)

1. **Toute modification de spécification ou nouvelle fonctionnalité met à jour ce référentiel dans la même
   modification** : nouvelle règle (nouvel identifiant, jamais de renumérotation), règle modifiée (texte et
   source), règle supprimée (mention « supprimée », identifiant conservé). Un changement de comportement qui corrige
   un « point d'attention » retire cet encart.
2. Tout **code d'erreur métier** (`new BusinessException("CODE", …)`) doit apparaître entre apostrophes inversées dans
   un chapitre ; tout **contrôleur REST** et toute **tâche planifiée** doivent figurer dans
   l'annexe 16. Le test `ReglesDeGestionDocumentationTest` (module `platform`) échoue sinon, et vérifie aussi l'unicité
   et la résolution des identifiants.
3. Les documentations fonctionnelle et commerciale (`docs/client`) sont **régénérées** à partir de ce référentiel ; ne
   jamais les corriger à la main sans corriger d'abord le référentiel.
4. Les écarts d'architecture (AGENTS.md) constatés pendant la rédaction sont signalés par des encarts « Point
   d'attention » : ils ne sont ni cachés ni corrigés silencieusement.
