---
topic: Spring Framework 7 / Boot 4 - @Scheduled (cron, zone), erreurs, pool du planificateur (crons de la Planification)
date: 2026-10-26
source: web (docs.spring.io/spring-framework/reference/integration/scheduling.html, doc 7.0.10) - non vérifié par context7 (clé API invalide)
libraryId: org.springframework:spring-context 7.0.x (Boot 4.0.8 du projet)
---
## Réponses clés
- `@Scheduled(cron = "...", zone = "...")` : `zone` = fuseau dans lequel le cron est évalué. Placeholders `${…}` : non dits par la page ; **déjà utilisés et éprouvés** dans le projet (`ControleDeNuitDesAbsences` : `zone = "${app.absences.fuseau-par-defaut:Africa/Algiers}"`).
- Méthode planifiée : `void`, sans argument. `@EnableScheduling` requis (déjà présent côté platform).
- Exception d'une méthode synchrone : non documenté par la page ; implémentation (non vérifiée ici) = gestionnaire d'erreurs qui journalise, planification maintenue. => **ne pas s'y fier** : chaque job attrape par centre (isolation par échec), comme `ControleDeNuitDesAbsences`.
- Pool : par défaut un seul thread (`spring.task.scheduling.pool.size` = 1 côté Boot). Les jobs à la même heure s'exécutent donc en série ; aucun ordre garanti entre jobs de modules différents.
- `@Scheduled` répétable ; deux déclenchements co-localisés peuvent se chevaucher.

## Conséquences pour la Planification
- Crons 04:00 (libération) et 07:15 (présence) : `interfaces/jobs`, `zone` = fuseau par défaut du serveur métier (`Africa/Algiers`) ; « aujourd'hui » calculé par centre via `FuseauxDesCentres` (V46), comme les Absences.
- Ordre 02:30 (Absences) → 04:00 (libération) tenu par l'heure, pas par une dépendance (pool à 1 thread : série de toute façon).
- Un seul serveur : pas de verrou distribué de job (ShedLock non retenu ; à revoir si plusieurs instances, cf. B3).

## Limites
- Comportement exact sur exception et résolution des placeholders de `zone` : non confirmés par la source lue.
