import {NgTableRemoteQuery} from '@sbourahla/ng-table';

/**
 * Pagination côté serveur (contrat `.ddd/slices/pagination-serveur/contract.md`) : `GET <liste>/page` avec
 * `page` (base 0), `size` (1 à 100), `sort=champ,asc|desc` et un paramètre par filtre de colonne ; réponse
 * `{items, total, page, size}` (champs supplémentaires possibles, ex. le résumé du PMP).
 */
export interface PageServeur<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

/** Taille maximale acceptée par le serveur (au-delà : 400 `TAILLE_INVALIDE`). */
export const TAILLE_MAXIMALE_DE_PAGE = 100;

export interface CorrespondanceDePage {
  /** id de colonne ng-table → champ de tri de l'API (absent : colonne non triable côté serveur, le tri n'est pas envoyé). */
  tris?: Readonly<Record<string, string>>;
  /** id de colonne ng-table → nom du paramètre de filtre de l'API (absent : filtre non envoyé). */
  filtres?: Readonly<Record<string, string>>;
  /** Nom du paramètre recevant la recherche globale (`search`), s'il y en a un (ex. `q`). */
  recherche?: string;
}

/**
 * Requête distante de ng-table ⇒ paramètres HTTP de `GET <liste>/page`. Seul le premier niveau de tri est envoyé ;
 * valeurs vides ignorées ; taille bornée à {@link TAILLE_MAXIMALE_DE_PAGE}.
 */
export function parametresDePage(requete: NgTableRemoteQuery, correspondance: CorrespondanceDePage):
  Record<string, string> {
  const parametres: Record<string, string> = {
    page: String(Math.max(0, requete.page?.index ?? 0)),
    size: String(Math.min(TAILLE_MAXIMALE_DE_PAGE, Math.max(1, requete.page?.size ?? 20))),
  };
  const tri = requete.sort;
  const champ = tri?.direction ? correspondance.tris?.[tri.columnId] : undefined;
  if (champ) parametres['sort'] = `${champ},${tri.direction}`;
  for (const [colonne, valeur] of Object.entries(requete.filters ?? {})) {
    const nom = correspondance.filtres?.[colonne];
    if (nom && valeur != null && String(valeur).trim() !== '') parametres[nom] = String(valeur).trim();
  }
  const recherche = requete.search?.trim();
  if (correspondance.recherche && recherche) parametres[correspondance.recherche] = recherche;
  return parametres;
}
