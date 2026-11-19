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
  /** ids de colonne dont le filtre est numérique (syntaxe serveur `[= > >= < <=]nombre`, voir {@link filtreNumerique}). */
  numeriques?: readonly string[];
}

/** Clé i18n du message affiché quand un filtre numérique ne peut pas être envoyé au serveur. */
export const FILTRE_NUMERIQUE_INVALIDE = 'COMMON.LISTE_SERVEUR.FILTRE_NUMERIQUE_INVALIDE';

/** Filtre de colonne que le serveur ne sait pas lire : la requête n'est pas envoyée (sinon 400 `FILTRE_INVALIDE`). */
export class FiltreDePageInvalide extends Error {
  constructor(readonly colonne: string, readonly valeur: string) {
    super(FILTRE_NUMERIQUE_INVALIDE);
    this.name = 'FiltreDePageInvalide';
  }
}

const NOMBRE = String.raw`[+-]?(?:\d+(?:[.,]\d*)?|[.,]\d+)`;
const COMPARAISON = new RegExp(`^(>=|<=|>|<|=)?(${NOMBRE})$`);
const BORNE_BASSE = new RegExp(`^(${NOMBRE})\\.\\.$`);
const BORNE_HAUTE = new RegExp(`^\\.\\.(${NOMBRE})$`);
const INTERVALLE = new RegExp(`^(${NOMBRE})\\.\\.(${NOMBRE})$`);

/**
 * Saisie d'un filtre `type: 'number'` de ng-table ⇒ valeur acceptée par le serveur (`[= > >= < <=]nombre`, contrat
 * section 1). Les bornes ouvertes de ng-table sont traduites (`10..` ⇒ `>=10`, `..50` ⇒ `<=50`, `5..5` ⇒ `=5`) ;
 * `null` pour ce que le serveur ne sait pas exprimer en un seul paramètre (`!=0`, intervalle `10..50`, texte).
 */
export function filtreNumerique(saisie: string): string | null {
  const v = saisie.replace(/\s+/g, '');
  const comparaison = COMPARAISON.exec(v);
  if (comparaison) return `${comparaison[1] ?? ''}${comparaison[2]}`;
  const basse = BORNE_BASSE.exec(v);
  if (basse) return `>=${basse[1]}`;
  const haute = BORNE_HAUTE.exec(v);
  if (haute) return `<=${haute[1]}`;
  const intervalle = INTERVALLE.exec(v);
  if (intervalle && Number(intervalle[1].replace(',', '.')) === Number(intervalle[2].replace(',', '.'))) {
    return `=${intervalle[1]}`;
  }
  return null;
}

/**
 * Requête distante de ng-table ⇒ paramètres HTTP de `GET <liste>/page`. Seul le premier niveau de tri est envoyé ;
 * valeurs vides ignorées ; taille bornée à {@link TAILLE_MAXIMALE_DE_PAGE} ; filtres numériques normalisés
 * ({@link filtreNumerique}).
 * @throws FiltreDePageInvalide si un filtre numérique n'est pas exprimable côté serveur
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
    if (!nom || valeur == null || String(valeur).trim() === '') continue;
    const brute = String(valeur).trim();
    if (correspondance.numeriques?.includes(colonne)) {
      const normalisee = filtreNumerique(brute);
      if (normalisee == null) throw new FiltreDePageInvalide(colonne, brute);
      parametres[nom] = normalisee;
    } else {
      parametres[nom] = brute;
    }
  }
  const recherche = requete.search?.trim();
  if (correspondance.recherche && recherche) parametres[correspondance.recherche] = recherche;
  return parametres;
}
