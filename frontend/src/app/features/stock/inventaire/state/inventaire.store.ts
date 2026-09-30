import {computed, inject} from '@angular/core';
import {patchState, signalStore, withComputed, withMethods, withState} from '@ngrx/signals';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {firstValueFrom} from 'rxjs';
import {
  AjoutLignePayload,
  EtatInventaire,
  Inventaire,
  InventaireApiService,
  InventaireResume,
} from '../../../../core/api/inventaire-api.service';
import {AppShellStore} from '../../../../core/state/app-shell.store';
import {httpErrorMessage} from '../../../admin/referentiels/referential-admin.util';
import {progression} from '../inventaire.util';

type InventaireState = {
  etat: EtatInventaire | null;
  history: InventaireResume[];
  total: number;
  pageIndex: number;
  pageSize: number;
  current: Inventaire | null;
  loading: boolean;
  /** Ligne en cours d'enregistrement (indicateur discret sur la ligne). */
  savingLineId: string | null;
  busy: boolean;
  error: string | null;
};

const initialState: InventaireState = {
  etat: null, history: [], total: 0, pageIndex: 0, pageSize: 10, current: null, loading: false,
  savingLineId: null, busy: false, error: null,
};

/** Inventaire de stock du centre actif : situation (gel), historique, comptage et cycle de vie. */
export const InventaireStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('InventaireStore'),
  withComputed((store) => ({
    mouvementsBloques: computed(() => !!store.etat()?.mouvementsBloques),
    enCours: computed(() => store.current()?.statut === 'EN_COURS'),
    progression: computed(() => progression(store.current())),
    pretACloturer: computed(() => {
      const inv = store.current();
      return !!inv && inv.statut === 'EN_COURS' && inv.lignes > 0 && inv.comptees === inv.lignes && inv.ecartsSansMotif === 0;
    }),
  })),
  withMethods((store, api = inject(InventaireApiService), appShell = inject(AppShellStore)) => {
    const centerId = (): string | null => appShell.currentCenterId();

    async function run(action: (center: string) => Promise<Inventaire>, errorKey: string, lineId?: string): Promise<boolean> {
      const center = centerId();
      if (!center) return false;
      patchState(store, {error: null, savingLineId: lineId ?? null, busy: !lineId});
      try {
        patchState(store, {current: await action(center)});
        return true;
      } catch (err) {
        patchState(store, {error: httpErrorMessage(err, errorKey)});
        return false;
      } finally {
        patchState(store, {savingLineId: null, busy: false});
      }
    }

    async function refreshEtat(): Promise<void> {
      const center = centerId();
      if (!center) return;
      try {
        patchState(store, {etat: await firstValueFrom(api.etat(center))});
      } catch {
        patchState(store, {etat: null});
      }
    }

    async function loadHistory(): Promise<void> {
      const center = centerId();
      if (!center) return;
      patchState(store, {loading: true});
      try {
        const page = await firstValueFrom(api.list(center, store.pageIndex(), store.pageSize()));
        patchState(store, {history: page.items, total: page.total});
      } catch (err) {
        patchState(store, {error: httpErrorMessage(err, 'STOCK.INVENTORY.LOAD_ERROR')});
      } finally {
        patchState(store, {loading: false});
      }
    }

    return {
      refreshEtat,
      loadHistory,

      async init(): Promise<void> {
        patchState(store, {error: null});
        await Promise.all([refreshEtat(), loadHistory()]);
      },

      async setPage(pageIndex: number, pageSize: number): Promise<void> {
        patchState(store, {pageIndex, pageSize});
        await loadHistory();
      },

      async load(id: string): Promise<void> {
        const center = centerId();
        if (!center) return;
        patchState(store, {loading: true, error: null});
        try {
          patchState(store, {current: await firstValueFrom(api.get(center, id))});
        } catch (err) {
          patchState(store, {current: null, error: httpErrorMessage(err, 'STOCK.INVENTORY.LOAD_ERROR')});
        } finally {
          patchState(store, {loading: false});
        }
      },

      async ouvrir(date: string | null, commentaire: string | null): Promise<Inventaire | null> {
        const ok = await run((c) => firstValueFrom(api.ouvrir(c, date, commentaire)), 'STOCK.INVENTORY.OPEN_ERROR');
        if (ok) await Promise.all([refreshEtat(), loadHistory()]);
        return ok ? store.current() : null;
      },

      compter(ligneId: string, quantite: number, motif: string | null): Promise<boolean> {
        const id = store.current()?.id;
        if (!id) return Promise.resolve(false);
        return run((c) => firstValueFrom(api.compter(c, id, ligneId, quantite, motif)), 'STOCK.INVENTORY.COUNT_ERROR', ligneId);
      },

      ajouterLigne(payload: AjoutLignePayload): Promise<boolean> {
        const id = store.current()?.id;
        if (!id) return Promise.resolve(false);
        return run((c) => firstValueFrom(api.ajouterLigne(c, id, payload)), 'STOCK.INVENTORY.COUNT_ERROR');
      },

      retirerLigne(ligneId: string): Promise<boolean> {
        const id = store.current()?.id;
        if (!id) return Promise.resolve(false);
        return run((c) => firstValueFrom(api.retirerLigne(c, id, ligneId)), 'STOCK.INVENTORY.COUNT_ERROR', ligneId);
      },

      reporterTheorique(): Promise<boolean> {
        const id = store.current()?.id;
        if (!id) return Promise.resolve(false);
        return run((c) => firstValueFrom(api.reporterTheorique(c, id)), 'STOCK.INVENTORY.COUNT_ERROR');
      },

      async cloturer(): Promise<boolean> {
        const id = store.current()?.id;
        if (!id) return false;
        const ok = await run((c) => firstValueFrom(api.cloturer(c, id)), 'STOCK.INVENTORY.CLOSE_ERROR');
        if (ok) await Promise.all([refreshEtat(), loadHistory()]);
        return ok;
      },

      async annuler(): Promise<boolean> {
        const id = store.current()?.id;
        if (!id) return false;
        const ok = await run((c) => firstValueFrom(api.annuler(c, id)), 'STOCK.INVENTORY.CANCEL_ERROR');
        if (ok) await Promise.all([refreshEtat(), loadHistory()]);
        return ok;
      },

      clearError(): void {
        patchState(store, {error: null});
      },
    };
  }),
);

