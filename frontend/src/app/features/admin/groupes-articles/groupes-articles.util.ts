import {ArticleStock} from '../../../core/api/stock-api.service';

/** Normalise pour la recherche : minuscules et sans accents. */
function normalise(value: string): string {
  return value.normalize('NFD').replace(/\p{M}+/gu, '').toLowerCase();
}

/**
 * Articles dont le code ou le libellé contient la saisie (insensible à la casse et aux accents), triés par
 * libellé. Une saisie vide renvoie tous les articles.
 */
export function filterArticles(articles: readonly ArticleStock[], query: string): ArticleStock[] {
  const q = normalise(query.trim());
  return articles
    .filter((a) => !q || normalise(a.code).includes(q) || normalise(a.libelle).includes(q))
    .sort((a, b) => a.libelle.localeCompare(b.libelle, 'fr'));
}
