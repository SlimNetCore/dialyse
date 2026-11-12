# PL8b - réalisation (solde RF3c-J1)

Refactoring sur place, ADR 0149. Rapport partiel, complété par le résultat du build complet (voir la réponse de l'appelant).

| Élément | Résultat |
|---|---|
| `LectureDuTableauDeBordJdbc` | agence figée lue sur les factures, caisse via `PayeursDuCentre.caissesDesAgences` (non bornée, KB-FAC-65 reproduit), tri Java stable par code |
| `SituationsDesFacturesJdbc` | identités brutes en SQL (tri inchangé), chaîne centre payeur / agence / caisse résolue en mémoire (bornée au centre), filtres + total + page en mémoire après le tri |
| `SyntheseFinanciereDesCentresJdbc` | montants par (centre, agence figée) en SQL, agence bornée au centre (KB-SEA-84), caisse non bornée (KB-RF3-07 reproduit) |
| `PayeursDuCentre` | + `caissesDesAgences(Collection<UUID>)` (Jdbc, double en mémoire, contrat commun) |
| `TableDesPayeursTest` | exceptions vides : tableau, butoir et tests d'échéance supprimés ; règle permanente conservée |
| Contrat PG16 | écrit (mêmes classes abstraites), NON exécuté : aucune base jetable disponible (`-Dfacturation.pg16.url`) |
| Goldens / JSON | inchangés |
