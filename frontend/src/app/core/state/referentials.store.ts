import {CentrePayeurDetail, RefItem} from '../api/referential-api.service';
import {createReferentialStore} from './referential-store.factory';

export type ReferentialDropdownItem = RefItem & {
  label: string;
  prix?: string | null;
};

const mapCodeAndName = (item: RefItem): ReferentialDropdownItem => ({
  ...item,
  id: item.id,
  label: `${item.nom ?? item.code ?? ''}${item.code ? ` (${item.code})` : ''}`.trim() || item.id
});

const mapForfait = (item: RefItem): ReferentialDropdownItem => ({
  ...item,
  id: item.id,
  label: item.nom ?? item.code ?? 'Forfait',
  prix: item.libelle ?? null
});

const mapSalle = (item: RefItem): ReferentialDropdownItem => ({
  ...item,
  id: item.id,
  label: `${item.code ?? ''} - ${item.nom ?? ''}`.trim().replace(/^\s*-\s*/, '') || item.id
});

const mapMedecin = (item: RefItem): ReferentialDropdownItem => ({
  ...item,
  id: item.id,
  label: `${item.nom ?? ''} ${item.prenom ?? ''}`.trim() || item.id
});

const mapPosition = (item: RefItem): ReferentialDropdownItem => ({
  ...item,
  id: item.id,
  label: `${item.code ?? ''} - ${item.libelle ?? item.nom ?? ''}`.trim().replace(/^\s*-\s*/, '') || item.id
});

const mapTransporteur = (item: RefItem): ReferentialDropdownItem => ({
  ...item,
  id: item.id,
  label: item.nom ?? item.code ?? item.id
});

const mapCategorieTransport = (item: RefItem): ReferentialDropdownItem => ({
  ...item,
  id: item.id,
  label: item.libelle ?? item.nom ?? item.code ?? item.id
});

const mapArticle = (item: RefItem): ReferentialDropdownItem => ({
  ...item,
  id: item.id,
  label: `${item.code ?? ''} - ${item.nom ?? ''}${item.libelle ? ` (${item.libelle})` : ''}`
    .trim()
    .replace(/^\s*-\s*/, '') || item.id
});

const mapGenerateur = (item: RefItem): ReferentialDropdownItem => ({
  ...item,
  id: item.id,
  // nom = "Générateur G01", libelle = "Fresenius 5008S", adresse = salleId
  label: `${item.nom ?? item.code ?? item.id}${item.libelle ? ` — ${item.libelle.trim()}` : ''}`.trim()
});

export const ForfaitsStore = createReferentialStore<RefItem, ReferentialDropdownItem>({
  storeName: 'ForfaitsStore',
  load: (api, centerId) => api.getForfaits(centerId),
  mapItem: mapForfait
});

export const CentresPayeursStore = createReferentialStore<RefItem, ReferentialDropdownItem>({
  storeName: 'CentresPayeursStore',
  load: (api, centerId) => api.getCentresPayeurs(centerId),
  mapItem: mapCodeAndName
});

export const CentresPayeursDetailsStore = createReferentialStore<CentrePayeurDetail>({
  storeName: 'CentresPayeursDetailsStore',
  load: (api, centerId) => api.getCentresPayeursDetails(centerId)
});

export const SallesStore = createReferentialStore<RefItem, ReferentialDropdownItem>({
  storeName: 'SallesStore',
  load: (api, centerId) => api.getSalles(centerId),
  mapItem: mapSalle
});

export const MedecinsStore = createReferentialStore<RefItem, ReferentialDropdownItem>({
  storeName: 'MedecinsStore',
  load: (api, centerId) => api.getMedecins(centerId),
  mapItem: mapMedecin
});

export const PositionsStore = createReferentialStore<RefItem, ReferentialDropdownItem>({
  storeName: 'PositionsStore',
  load: (api, centerId) => api.getPositions(centerId),
  mapItem: mapPosition
});

export const TransporteursStore = createReferentialStore<RefItem, ReferentialDropdownItem>({
  storeName: 'TransporteursStore',
  load: (api, centerId) => api.getTransporteurs(centerId),
  mapItem: mapTransporteur
});

export const CategoriesTransportStore = createReferentialStore<RefItem, ReferentialDropdownItem>({
  storeName: 'CategoriesTransportStore',
  load: (api, centerId) => api.getCategoriesTransport(centerId),
  mapItem: mapCategorieTransport
});

export const AgencesStore = createReferentialStore<RefItem, ReferentialDropdownItem>({
  storeName: 'AgencesStore',
  load: (api, centerId) => api.getAgences(centerId),
  mapItem: mapCodeAndName
});

export const CaissesStore = createReferentialStore<RefItem, ReferentialDropdownItem>({
  storeName: 'CaissesStore',
  load: (api, centerId) => api.getCaisses(centerId),
  mapItem: mapCodeAndName
});

export const ArticlesStore = createReferentialStore<RefItem, ReferentialDropdownItem>({
  storeName: 'ArticlesStore',
  load: (api, centerId) => api.getArticles(centerId),
  mapItem: mapArticle
});

export const GenerateursStore = createReferentialStore<RefItem, ReferentialDropdownItem>({
  storeName: 'GenerateursStore',
  load: (api, centerId) => api.getGenerateurs(centerId),
  mapItem: mapGenerateur
});
