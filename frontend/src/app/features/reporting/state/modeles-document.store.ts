import {patchState, signalStore, withMethods, withState} from '@ngrx/signals';
import {withDevtools} from '@angular-architects/ngrx-toolkit';

type ModelesDocumentState = {
  modeles: any[];
  documentTypes: Array<{ code: string; label: string }>;
  showForm: boolean;
  editingId: string | null;
};

const initialState: ModelesDocumentState = {
  modeles: [],
  documentTypes: [],
  showForm: false,
  editingId: null
};

export const ModelesDocumentStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('ModelesDocumentStore'),
  withMethods((store) => ({
    setModeles(modeles: any[]): void {
      patchState(store, {modeles});
    },

    setDocumentTypes(documentTypes: Array<{ code: string; label: string }>): void {
      patchState(store, {documentTypes});
    },

    setShowForm(showForm: boolean): void {
      patchState(store, {showForm});
    },

    setEditingId(editingId: string | null): void {
      patchState(store, {editingId});
    }
  }))
);

