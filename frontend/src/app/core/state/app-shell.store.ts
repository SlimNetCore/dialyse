import {patchState, signalStore, withMethods, withState} from '@ngrx/signals';

export type CenterRef = {
  id: string;
  name: string;
};

type AppShellState = {
  availableCenters: CenterRef[];
  currentCenterId: string | null;
};

const initialState: AppShellState = {
  availableCenters: [
    {id: '11111111-1111-1111-1111-111111111111', name: 'ANNABA 1'},
    {id: '22222222-2222-2222-2222-222222222222', name: 'ROUIBA'}
  ],
  currentCenterId: '11111111-1111-1111-1111-111111111111'
};

export const AppShellStore = signalStore(
  { providedIn: 'root' },
  withState(initialState),
  withMethods((store) => ({
    switchCenter(centerId: string): void {
      patchState(store, { currentCenterId: centerId });
    }
  }))
);
