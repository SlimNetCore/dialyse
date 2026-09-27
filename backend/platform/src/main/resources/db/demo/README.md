# Jeu de test RENADIAL

Jeu de données de démonstration pour tester le **tableau de bord de la direction** avec un volume réaliste :
une société **RENADIAL**, **17 centres**, **12 mois de données glissants** (séances, factures, règlements,
biologie/KDIGO, sérologies, anémie EPO/fer, stock, alertes, greffe).

Ces scripts ne sont **jamais exécutés automatiquement** (contrairement à `db/seed.sql`) : à jouer manuellement,
seulement quand vous voulez ce jeu de données.

## Contenu généré

| Donnée                        | Volume approx.                                                                                |
|-------------------------------|-----------------------------------------------------------------------------------------------|
| Centres                       | 17, tailles variées (2 à 52 patients — certains volontairement sous le seuil d'anonymat de 5) |
| Patients                      | ~410                                                                                          |
| Séances (12 mois)             | ~13 000                                                                                       |
| Factures / règlements         | ~4 400 / ~3 700                                                                               |
| Résultats de biologie (KDIGO) | ~3 900                                                                                        |
| Sérologies                    | ~1 200                                                                                        |
| Administrations EPO/fer       | ~3 600                                                                                        |
| Articles / lots de stock      | ~150 / ~180 (dont sous seuil, périmés, péremption proche)                                     |
| Alertes observance ouvertes   | ~20                                                                                           |
| Bilans pré-greffe             | ~40                                                                                           |

Trois centres (tailles 2, 3, 4) restent **sous le seuil d'anonymat** pour vérifier le masquage (`< 5`) sur le
tableau de bord direction. Les centres ont des « profils » de qualité de soins différents (bon / moyen / en
difficulté) pour obtenir des indicateurs KDIGO contrastés, utiles pour tester le classement des centres.

## Utilisation — chargement automatique au démarrage (recommandé)

Un profil Spring dédié, **`demo-renadial`**, ajoute `renadial-seed.sql` au chargement rejoué à chaque démarrage
(`spring.sql.init.data-locations`), **en plus** du seed habituel — jamais à sa place. Activez-le :

```bash
# Terminal
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo-renadial
```

```bash
# Ou variable d'environnement
SPRING_PROFILES_ACTIVE=demo-renadial ./mvnw spring-boot:run
```

Sûr en H2 (dev par défaut) : la base est neuve à chaque démarrage, donc rejouable sans limite. **À éviter sur une
base PostgreSQL persistante** sans purge préalable (`renadial-purge.sql`) : les identifiants du jeu de données sont
fixes, une deuxième exécution contre la même base violerait les contraintes d'unicité (numéro de facture, etc.).

Une fois démarré :

1. Connectez-vous en propriétaire (SUPERADMIN) et créez un **compte direction** pour la société « RENADIAL »
   (Administration → Sociétés → RENADIAL → Comptes direction) — procédure normale de l'application, volontairement
   pas court-circuitée par ce script (pas de mot de passe en clair dans un fichier SQL versionné).
2. Connectez-vous en Direction sur RENADIAL et explorez le tableau de bord : filtre par centre, raccourcis de
   période, export CSV, comparateur de mois (figez d'abord un mois via le bouton correspondant), rapport PDF.

## Chargement manuel (sans redémarrer avec le profil)

Ouvrez la console H2 (`http://localhost:8090/h2-console`) et exécutez `renadial-seed.sql` (fichier volumineux :
préférez « Exécuter un script SQL depuis un fichier » plutôt qu'un copier-coller). Sur PostgreSQL :
`psql "$DB_URL" -f renadial-seed.sql`.

## Purge

`renadial-purge.sql` supprime uniquement les données rattachées à la société RENADIAL (jamais les autres sociétés
ni centres). À rejouer si vous voulez repartir d'une base propre.

## Régénérer avec des dates fraîches

Le script est produit par `generate-renadial-seed.cjs` (Node.js, aucune dépendance externe). Les 12 mois de
données sont calculés par rapport à la date d'exécution du script — régénérez-le si le jeu de données existant
a trop vieilli :

```bash
node backend/platform/src/main/resources/db/demo/generate-renadial-seed.cjs
```

Écrit `renadial-seed.sql` à côté du script (écrase l'existant). Les identifiants (société, centres, patients...)
sont aléatoires à chaque génération : rejouez d'abord `renadial-purge.sql` si un ancien jeu de données RENADIAL
est encore présent, pour ne pas dupliquer les centres.
