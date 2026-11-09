# Flux rapide : agents parallèles bornés

- **Une slice = un comportement** (un use case). Jamais de « lot » d'un seul tenant : un lot (« PD1+PD4 ») n'est qu'un
  regroupement de slices indépendantes, livrées chacune par `/ddd-ship`. Hors budget `sliceBudget` ⇒ découper, ne pas
  lancer.
- **Contrat avant parallélisme** : aucun travailleur ne démarre sans `contract.md` + stubs compilables commités. Un
  désaccord d'interface se tranche dans le contrat, pas dans le code d'un travailleur.
- **Propriété des fichiers** : chaque travailleur n'écrit que dans ses chemins (`.ddd/slices/<s>/ownership.json`, hook
  `guard-ownership`). Un besoin hors périmètre se signale (`contractChanges`/`CONTRAT:`), jamais s'édite.
- **Un travailleur ne pose pas de question** : il renvoie `NEEDS_APPROVAL`/`BLOCKED` ; l'orchestrateur regroupe les
  questions en une seule fois.
- **Un travailleur ne touche pas `.ddd/state.json`** (conflits de fusion) ; l'orchestrateur le met à jour.
- **Builds lourds sous verrou** : jamais `mvn`/`ng` nus en parallèle ; toujours
  `tools/ddd-scan/locked.ts <ressource> -- <cmd>` (créneaux dans `config.locks`). Ne jamais lancer le build complet pour
  une slice.
- **Les tests ne tournent qu'une fois par état du code** : le gate (`gate.ts <s>`) produit `gate-<s>.json` ; **les
  relecteurs ne lancent ni tests, ni build, ni scan** — ils lisent ce JSON et le diff.
- **Revue au diff** : relecteurs ≤ 15 tours, ≤ 12 constats chacun, tout constat avec preuve `fichier:ligne` ; la revue
  globale (`ddd-reviewer`) est réservée aux jalons.
- **Corrections routées** : un constat va au propriétaire du fichier, en parallèle ; max 2 tours puis escalade à
  l'humain.
- **Mesurer** : chaque étape de `/ddd-ship` est chronométrée (`state.ts log` / `state.ts timings`) ; on optimise ce que
  la mesure montre.
- Pas de push, pas d'activation de flag, pas de suppression de legacy par un agent.
