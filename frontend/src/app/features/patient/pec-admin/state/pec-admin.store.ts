import {patchState, signalStore, withMethods, withState} from '@ngrx/signals';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {DropdownItem} from '../../../../shared/searchable-select.component';

type PecAdminState = {
  pecs: any[];
  loading: boolean;
  validatingPec: any;
  forfaits: DropdownItem[];
};

const initialState: PecAdminState = {
  pecs: [],
  loading: false,
  validatingPec: null,
  forfaits: []
};

export const PecAdminStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('PecAdminStore'),
  withMethods((store) => ({
    setPecs(pecs: any[]): void {
      patchState(store, {pecs});
    },

    setLoading(loading: boolean): void {
      patchState(store, {loading});
    },

    setValidatingPec(validatingPec: any): void {
      patchState(store, {validatingPec});
    },

    setForfaits(forfaits: DropdownItem[]): void {
      patchState(store, {forfaits});
    }
  }))
);

