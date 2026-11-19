import {DestroyRef, inject, signal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {NgTableRemoteQuery} from '@sbourahla/ng-table';
import {catchError, Observable, of, Subject, switchMap, tap} from 'rxjs';
import {CorrespondanceDePage, PageServeur, parametresDePage} from './page-serveur';

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

/**
 * État d'une ng-table en mode distant (contrat `pagination-serveur`) : la requête de la table devient les paramètres
 * de `GET <liste>/page` ({@link parametresDePage}) ; un nouveau tri ou filtre repart de la page 0 ; une requête
 * périmée est annulée (`switchMap`) ; {@link recharger} relit la page courante après une écriture.
 * À créer dans un contexte d'injection (désabonnement à la destruction).
 */
export class ListeServeur<T> {
  readonly lignes = signal<T[]>([]);
  readonly total = signal(0);
  readonly pageIndex = signal(0);
  readonly pageSize = signal(20);
  readonly chargement = signal(false);
  readonly enErreur = signal(false);
  /** Dernière page reçue (enveloppe complète : champs supplémentaires comme le résumé du PMP). */
  readonly derniere = signal<PageServeur<T> | null>(null);

  private parametres: Record<string, string> | null = null;
  private readonly demandes = new Subject<Record<string, string>>();

  constructor(charger: (parametres: Record<string, string>) => Observable<PageServeur<T>>,
              private readonly correspondance: CorrespondanceDePage = {},
              tailleParDefaut = 20,
              destroyRef: DestroyRef = inject(DestroyRef)) {
    this.pageSize.set(tailleParDefaut);
    this.demandes.pipe(
      tap(() => this.chargement.set(true)),
      switchMap((parametres) => charger(parametres).pipe(catchError(() => of(null)))),
      takeUntilDestroyed(destroyRef),
    ).subscribe((page) => {
      this.chargement.set(false);
      this.derniere.set(page);
      this.enErreur.set(!page);
      this.lignes.set(page?.items ?? []);
      this.total.set(page?.total ?? 0);
      if (page) {
        this.pageIndex.set(page.page ?? 0);
        this.pageSize.set(page.size || this.pageSize());
      }
    });
  }

  /** Paramètres de la dernière demande (copie), `null` avant le premier chargement. */
  parametresCourants(): Record<string, string> | null {
    return this.parametres ? {...this.parametres} : null;
  }

  /** Requête émise par la table : nouvelle page, taille, tri ou filtres (ignorée si rien ne change). */
  onRemoteQueryChange(requete: NgTableRemoteQuery): void {
    const parametres = parametresDePage(requete, this.correspondance);
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

  /** Vide la liste (pas de centre courant). */
  vider(): void {
    this.lignes.set([]);
    this.total.set(0);
  }

  private demander(parametres: Record<string, string>): void {
    this.parametres = {...parametres};
    this.pageIndex.set(Number(parametres['page'] ?? 0));
    this.pageSize.set(Number(parametres['size'] ?? this.pageSize()));
    this.demandes.next({...parametres});
  }
}
