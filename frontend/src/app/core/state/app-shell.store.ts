import {patchState, signalStore, withMethods, withState} from '@ngrx/signals';

export type CenterRef = {
  id: string;
  name: string;
};

export type SeanceScanClipboard = {
  centerId: string;
  patientCode: string;
  copiedAt: number;
};

type AppShellState = {
  availableCenters: CenterRef[];
  currentCenterId: string | null;
  seanceScanClipboard: SeanceScanClipboard | null;
};

const initialState: AppShellState = {
  availableCenters: [],
  currentCenterId: '11111111-1111-1111-1111-111111111111',
  seanceScanClipboard: null,
};

export const AppShellStore = signalStore(
  { providedIn: 'root' },
  withState(initialState),
  withMethods((store) => ({
    /** Centres accessibles à l'utilisateur connecté (chargés après la connexion). */
    setAvailableCenters(centers: CenterRef[]): void {
      patchState(store, {availableCenters: centers});
    },
    switchCenter(centerId: string): void {
      patchState(store, { currentCenterId: centerId });
    },
    setSeanceScanClipboard(centerId: string, patientCode: string): void {
      const normalized = (patientCode ?? '').trim();
      if (!centerId || !normalized) {
        return;
      }
      patchState(store, {
        seanceScanClipboard: {
          centerId,
          patientCode: normalized,
          copiedAt: Date.now(),
        },
      });
    },
    clearSeanceScanClipboard(): void {
      patchState(store, {seanceScanClipboard: null});
    },
  }))
);
