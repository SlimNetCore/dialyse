import {computed, DestroyRef, inject, signal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {NgTableRemoteQuery} from '@sbourahla/ng-table';
import {catchError, Observable, of, Subject, switchMap, tap} from 'rxjs';
import {CorrespondanceDePage, FiltreDePageInvalide, PageServeur, parametresDePage} from './page-serveur';

/** Clé i18n de repli quand l'erreur HTTP ne porte pas de `detail` (ProblemDetail). */
export const ERREUR_DE_LISTE = 'COMMON.LISTE_SERVEUR.ERREUR';

/** Paramètres de tri/filtre identiques (hors `page`/`size`), quel que soit l'ordre des clés. */
export function memesCriteres(a: Readonly<Record<string, string>>, b: Readonly<Record<string, string>>): boolean {
  const criteres = (p: Readonly<Record<string, string>>): string[] =>
    Object.keys(p).filter((k) => k !== 'page' && k !== 'size').sort();
  const ca = criteres(a);
  const cb = criteres(b);
  return ca.length === cb.length && ca.every((k, i) => k === cb[i] && a[k] === b[k]);
}

const memesParametres = (a: Readonly<Record<string, string>>, b: Readonly<Record<string, string>>): boolean =>
  a['page'] === b['page'] && a['size'] === b['size'] && memesCriteres(a, b);

/** Clé i18n quand le serveur est injoignable (statut 0). */
export const ERREUR_RESEAU_DE_LISTE = 'COMMON.LISTE_SERVEUR.RESEAU';

/** Codes stables des refus 400 de `GET <liste>/page` (contrat `pagination-serveur`). */
export const CODES_DE_PAGINATION = ['PAGE_INVALIDE', 'TAILLE_INVALIDE', 'TRI_INVALIDE', 'FILTRE_INVALIDE',
  'PARAMETRE_INVALIDE'] as const;

/**
 * Clé i18n d'un refus de pagination (400 + `code` du ProblemDetail) ou d'un serveur injoignable ; `null` pour toute
 * autre erreur (laissée au gestionnaire du module).
 */
export function messageDErreurDePagination(err: unknown): string | null {
  if (err instanceof FiltreDePageInvalide) return err.message;
  if (!err || typeof err !== 'object') return null;
  const valeur = err as Record<string, unknown>;
  if (valeur['status'] === 0) return ERREUR_RESEAU_DE_LISTE;
  const corps = valeur['error'];
  const code = corps && typeof corps === 'object' ? (corps as Record<string, unknown>)['code'] : null;
  return typeof code === 'string' && (CODES_DE_PAGINATION as readonly string[]).includes(code)
    ? `COMMON.LISTE_SERVEUR.${code}` : null;
}

/** Message (clé i18n) d'une erreur de `GET <liste>/page` : refus de pagination, réseau, sinon message générique. */
export function messageDErreurDeListe(err: unknown): string {
  return messageDErreurDePagination(err) ?? ERREUR_DE_LISTE;
}

/**
 * Dernière page atteignable quand une page relue est vide alors que le total ne l'est pas (une écriture a réduit
 * la liste filtrée) ; `null` si la page reçue est cohérente.
 */
export function pageAtteignable(page: number, taille: number, total: number, nombreDeLignes: number): number | null {
  if (nombreDeLignes > 0 || page <= 0 || taille <= 0) return null;
  if (total <= 0) return 0;
  const derniere = Math.max(0, Math.ceil(total / taille) - 1);
  return derniere < page ? derniere : null;
}

/**
 * État d'une ng-table en mode distant (contrat `pagination-serveur`, brique commune du front) : la requête de la
 * table devient les paramètres de `GET <liste>/page` ({@link parametresDePage}) ; un nouveau tri ou filtre repart de
 * la page 0 ; une requête périmée est annulée (`switchMap`) ; {@link recharger} relit la page courante après une
 * écriture (en revenant à la dernière page si elle a disparu) ; une erreur vide la page et expose {@link erreur}
 * (à afficher, ex. `[emptyLabel]="liste.erreur() | translate"`).
 * `charger` renvoie `null` quand la liste n'a pas de contexte (pas de centre…) : la liste est alors vidée.
 * À créer dans un contexte d'injection (désabonnement à la destruction).
 */
export class ListeServeur<T, P extends PageServeur<T> = PageServeur<T>> {
  readonly total = signal(0);
  readonly pageIndex = signal(0);
  readonly pageSize = signal(20);
  readonly chargement = signal(false);
  /** Message d'erreur (detail du serveur ou clé i18n), `null` si la dernière demande a abouti. */
  readonly erreur = signal<string | null>(null);
  readonly enErreur = computed(() => this.erreur() !== null);
  /** Dernière page reçue (enveloppe complète : champs supplémentaires comme le résumé du PMP). */
  readonly derniere = signal<P | null>(null);
  readonly lignes = computed<T[]>(() => this.derniere()?.items ?? []);

  private parametres: Record<string, string> | null = null;
  private readonly demandes = new Subject<Record<string, string>>();

  constructor(charger: (parametres: Record<string, string>) => Observable<P> | null,
              private readonly correspondance: CorrespondanceDePage = {},
              tailleParDefaut = 20,
              destroyRef: DestroyRef = inject(DestroyRef)) {
    this.pageSize.set(tailleParDefaut);
    this.demandes.pipe(
      switchMap((parametres) => {
        const requete = charger(parametres);
        if (!requete) return of({page: null, erreur: null, parametres});
        this.chargement.set(true);
        return requete.pipe(
          switchMap((page) => of({page, erreur: null as string | null, parametres})),
          catchError((err: unknown) => of({page: null, erreur: messageDErreurDeListe(err), parametres})),
        );
      }),
      tap(() => this.chargement.set(false)),
      takeUntilDestroyed(destroyRef),
    ).subscribe(({page, erreur, parametres}) => this.recevoir(page, erreur, parametres));
  }

  /** Paramètres de la dernière demande (copie), `null` avant le premier chargement. */
  parametresCourants(): Record<string, string> | null {
    return this.parametres ? {...this.parametres} : null;
  }

  /**
   * Requête émise par la table : nouvelle page, taille, tri ou filtres (ignorée si rien ne change). Un filtre que le
   * serveur ne sait pas lire (ex. `!=0` sur une colonne numérique) n'est pas envoyé : la page est vidée et
   * {@link erreur} l'explique.
   */
  onRemoteQueryChange(requete: NgTableRemoteQuery): void {
    let parametres: Record<string, string>;
    try {
      parametres = parametresDePage(requete, this.correspondance);
    } catch (err) {
      if (!(err instanceof FiltreDePageInvalide)) throw err;
      this.parametres = null;
      this.pageIndex.set(0);
      this.recevoir(null, messageDErreurDeListe(err), null);
      return;
    }
    if (this.parametres && (!memesCriteres(parametres, this.parametres)
      || parametres['size'] !== this.parametres['size'])) {
      parametres['page'] = '0';
    }
    if (this.parametres && memesParametres(parametres, this.parametres)) return;
    this.demander(parametres);
  }

  /** Premier chargement (ou retour en page 0 en gardant tri et filtres). */
  charger(): void {
    this.demander({...(this.parametres ?? {}), page: '0', size: String(this.pageSize())});
  }

  /** Relit la page courante (après une création, une validation, une modification…). */
  recharger(): void {
    this.demander(this.parametres ?? {page: String(this.pageIndex()), size: String(this.pageSize())});
  }

  /**
   * Un critère porté hors de la table a changé (filtre d'état, recherche ajoutés par `charger`) : première page
   * relue, tri, taille et filtres de la table conservés.
   */
  premierePage(): void {
    this.demander({...(this.parametres ?? {size: String(this.pageSize())}), page: '0'});
  }

  /**
   * Changement de contexte (autre pièce, autre inventaire : la table est recréée) : page reçue vidée, tri et filtres
   * oubliés, première page rechargée.
   */
  reinitialiser(): void {
    this.vider();
    this.parametres = null;
    this.demander({page: '0', size: String(this.pageSize())});
  }

  /** Vide la liste (pas de centre courant). */
  vider(): void {
    this.derniere.set(null);
    this.total.set(0);
    this.erreur.set(null);
  }

  private recevoir(page: P | null, erreur: string | null, parametres: Record<string, string> | null): void {
    this.derniere.set(page);
    this.erreur.set(erreur);
    this.total.set(page?.total ?? 0);
    if (!page) return;
    const index = page.page ?? Number(parametres?.['page'] ?? 0);
    const taille = page.size || this.pageSize();
    this.pageIndex.set(index);
    this.pageSize.set(taille);
    const atteignable = pageAtteignable(index, taille, page.total ?? 0, page.items?.length ?? 0);
    if (atteignable !== null && parametres) this.demander({...parametres, page: String(atteignable)});
  }

  private demander(parametres: Record<string, string>): void {
    this.parametres = {...parametres};
    this.pageIndex.set(Number(parametres['page'] ?? 0));
    this.pageSize.set(Number(parametres['size'] ?? this.pageSize()));
    this.demandes.next({...parametres});
  }
}
