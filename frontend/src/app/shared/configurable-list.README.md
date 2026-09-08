# Configurable List (shared)

Composant réutilisable de tableau Angular Material avec colonnes paramétrables (visibilité, ordre par
drag-and-drop, largeur), tri et filtres par colonne (menu ou inline) en mode **local ou distant**
(`dataMode`), sélection de lignes, ligne de détail dépliable, menu contextuel (clic droit), et un
système de vues sauvegardées (colonnes/ordre/tri/filtres/pagination, persistées en `localStorage` ou
déléguées au parent).

Le composant est autonome : aucune dépendance à un module métier. Il peut être copié tel quel dans un
autre projet Angular (avec `column-filter-renderer`, `dynamic-filter-host` et `hemodialysis-loader`),
plus `@ngx-translate` et Angular Material.

**Documentation complète, référence API et exemples d'utilisation :**
voir [`CONFIGURABLE_LIST_DOCUMENTATION.md`](./CONFIGURABLE_LIST_DOCUMENTATION.md).
