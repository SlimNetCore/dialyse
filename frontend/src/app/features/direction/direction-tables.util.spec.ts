import {describe, expect, it} from 'vitest';
import {
  AnemieRow,
  CentreGmao,
  CentreIndicators,
  CentreStats,
  LigneCapacite,
  Snapshot,
} from '../../core/api/direction-api.service';
import {AbsencesRow} from './direction-absences.util';
import {CapaciteRow} from './direction-capacite.util';
import {StockGroupeRow} from './direction-stock-groupes.util';
import {
  absencesColumns,
  agesColumns,
  anemieColumns,
  caissesColumns,
  capaciteColumns,
  cellFormatOf,
  cellTextOf,
  centresColumns,
  clinicalColumns,
  compareColumns,
  compareRows,
  DELTA_CELL,
  DirectionColumn,
  DirectionTableContext,
  formatDelta,
  gmaoColumns,
  gmaoTopColumns,
  IndicatorRow,
  maxOf,
  motifsColumns,
  NIVEAU_CAPACITE_TONES,
  RATE_CELL,
  REPORT_STATUS_TONES,
  reportColumns,
  sexeColumns,
  stockColumns,
  stockGroupesColumns,
} from './direction-tables.util';

/** Mise en forme lisible dans les attentes : clé de traduction telle quelle, montants suffixés « DA ». */
const ctx: DirectionTableContext = {
  t: (key) => key,
  headcount: (value) => (value === null ? '< 5' : String(value)),
  money: (value) => (value === null ? '' : `${value} DA`),
  decimal: (value, digits) => value.toFixed(digits === '1.0-0' ? 0 : 1),
  dateTime: (value) => `le ${value}`,
};

const byId = <T>(columns: DirectionColumn<T>[]): Record<string, DirectionColumn<T>> =>
  Object.fromEntries(columns.map((c) => [c.id, c]));
const cellTypes = <T>(columns: DirectionColumn<T>[]): Record<string, string | undefined> =>
  Object.fromEntries(columns.map((c) => [c.id, c.cell?.type]));

const centre = (centerId: string, nom: string, over: Partial<CentreStats> = {}): CentreStats => ({
  centerId, nom, actif: true, patients: 40, patientsSousKt: 6, seances: 400, factures: 38, caHt: 900_000,
  caTtc: 1_000_000, encaisse: 800_000, resteARecouvrer: 200_000, tauxEncaissement: 80, ...over,
});

describe('tableaux de la direction — cellules ng-table par colonne', () => {
  it('activité par centre : rang en pastille, centre en avatar, montants et effectifs en barres, taux en progression', () => {
    const rows = [centre('a', 'Centre Alpha'), centre('b', 'Centre Beta', {patients: null, caTtc: 250_000, tauxEncaissement: null})];
    const columns = centresColumns(ctx, rows, (id) => (id === 'a' ? 1 : 2), (id) => (id === 'a' ? 80 : 20));

    expect(cellTypes(columns)).toEqual({
      rang: 'badge', centre: 'avatar', patients: 'dataBar', patientsSousKt: 'dataBar', seances: 'dataBar',
      factures: 'dataBar', caTtc: 'dataBar', encaisse: 'dataBar', resteARecouvrer: 'dataBar',
      tauxEncaissement: 'progress', partCa: 'progress',
    });
    const c = byId(columns);
    // barre de zéro au plus grand CA de la colonne ; le montant reste écrit en dinars
    expect(c['caTtc'].cell).toEqual({type: 'dataBar', min: 0, max: 1_000_000});
    expect(c['caTtc'].valueAccessor(rows[1])).toBe(250_000);
    expect(cellTextOf(c['caTtc'], rows[1])).toBe('250000 DA');
    expect(c['resteARecouvrer'].cell).toMatchObject({tone: 'warning'});
    // effectif masqué : « < 5 », sans barre
    expect(cellTextOf(c['patients'], rows[1])).toBe('< 5');
    expect(cellFormatOf(c['patients'], rows[1], null, '< 5')).toBeNull();
    // taux : bornes fixes 0-100, valeur exacte écrite à côté, couleur selon le niveau de recouvrement
    expect(c['tauxEncaissement'].cell).toBe(RATE_CELL);
    expect(RATE_CELL).toEqual({type: 'progress', max: 100, showValue: false});
    expect(cellTextOf(c['tauxEncaissement'], rows[0])).toBe('80 %');
    expect(c['tauxEncaissement'].emphasis?.(rows[0])).toBe('good');
    expect(cellTextOf(c['tauxEncaissement'], rows[1])).toBe('—');
    expect(cellFormatOf(c['tauxEncaissement'], rows[1], null, '—')).toBeNull();
    expect(cellTextOf(c['partCa'], rows[1])).toBe('20 %');
    expect(c['rang'].valueAccessor(rows[1])).toBe(2);
    expect(columns.every((col) => col.sortable)).toBe(true);
  });

  const marker = (pct: number | null) => ({evalues: 10, pctDansCible: pct, pctSousCible: null, pctAuDessus: null});
  const indicators = (nom: string | null, ktv: number | null, sousSeuil: number): CentreIndicators => ({
    centerId: nom, nom, actif: true,
    clinique: {
      ktV: marker(ktv), hemoglobine: marker(70), phosphore: marker(50), pth: marker(40), albumine: marker(null),
      vhbPositifs: 2, vhcPositifs: null, vihPositifs: 0, patientsObservanceEnRetard: 3, greffeListeAttente: 1,
      greffeBilanEnCours: 0, greffesPeriode: 0,
    },
    stock: {articlesActifs: 120, articlesSousSeuil: sousSeuil, lotsPerimes: 0, lotsPeremptionProche: 4, valeurStock: 5000},
  });
  const alpha: IndicatorRow = {c: indicators('Centre Alpha', 62.5, 3), total: false};
  const total: IndicatorRow = {c: indicators(null, 55, 9), total: true};

  it('indicateurs cliniques : cibles KDIGO en progression colorée, effectifs en barres, total en texte simple', () => {
    const columns = clinicalColumns(ctx, [alpha]);
    const c = byId(columns);

    expect(cellTypes(columns)).toMatchObject({
      centre: 'avatar', ktV: 'progress', hemoglobine: 'progress', phosphore: 'progress', pth: 'progress',
      albumine: 'progress', vhbPositifs: 'dataBar', greffesPeriode: 'dataBar',
    });
    expect(cellTextOf(c['ktV'], alpha)).toBe('62.5 %');
    expect(c['ktV'].emphasis?.(alpha)).toBe('warn');
    expect(c['hemoglobine'].emphasis?.(alpha)).toBe('good');
    expect(c['pth'].emphasis?.(alpha)).toBe('bad');
    expect(c['albumine'].emphasis?.(alpha)).toBeNull();
    expect(cellTextOf(c['vhcPositifs'], alpha)).toBe('< 5');
    // ligne de total : libellé sans avatar, pas de barre (elle écraserait l'échelle des centres), le taux garde sa barre
    expect(cellTextOf(c['centre'], total)).toBe('DIRECTION.TOTAL');
    expect(cellFormatOf(c['centre'], total, 'DIRECTION.TOTAL', 'DIRECTION.TOTAL')).toBeNull();
    expect(cellFormatOf(c['vhbPositifs'], total, 2, '2')).toBeNull();
    expect(cellFormatOf(c['vhbPositifs'], alpha, 2, '2')).toEqual({type: 'dataBar', min: 0, max: 2});
    expect(cellFormatOf(c['ktV'], total, 55, '55 %')).toBe(RATE_CELL);
  });

  it('stock : alertes en barres teintées et texte coloré, échelle mesurée sur les centres seulement', () => {
    const c = byId(stockColumns(ctx, [alpha]));

    expect(c['articlesSousSeuil'].cell).toEqual({type: 'dataBar', min: 0, max: 3, tone: 'danger'});
    expect(c['articlesSousSeuil'].emphasis?.(alpha)).toBe('bad');
    expect(c['lotsPerimes'].emphasis?.(alpha)).toBeNull();
    expect(c['lotsPeremptionProche'].cell).toMatchObject({tone: 'warning'});
    expect(cellTextOf(c['valeurStock'], alpha)).toBe('5000 DA');
  });

  it('GMAO : compteurs et coûts en barres, classement des équipements avec ratio en progression et réforme en pastille', () => {
    const gmao: CentreGmao = {
      centerId: 'a', nom: 'Centre Alpha', nbEquipements: 12, nbHorsService: 1, nbEnMaintenance: 0, nbReformes: 2,
      interventionsEnCours: 3, coutMaintenancePeriode: 45_000, indisponibiliteHeuresCumulees: 12.5,
      patientsSurEquipementIndisponible: 4, nbReformeRecommandee: 1, topEquipements: [],
    };
    const row = {c: gmao, total: false};
    const c = byId(gmaoColumns(ctx, [row]));
    expect(c['nbHorsService'].cell).toMatchObject({type: 'dataBar', tone: 'danger'});
    expect(c['nbHorsService'].emphasis?.(row)).toBe('bad');
    expect(c['nbEnMaintenance'].emphasis?.(row)).toBeNull();
    expect(cellTextOf(c['indisponibiliteHeuresCumulees'], row)).toBe('12.5 h');
    expect(cellTextOf(c['coutMaintenancePeriode'], row)).toBe('45000 DA');

    const equipement = (reforme: boolean, ratio: number | null) => ({
      centre: 'Centre Alpha',
      e: {
        equipementId: 'e', code: 'GEN-01', designation: 'Générateur', statut: 'EN_SERVICE', coutPeriode: 9000,
        coutCumule: 30_000, prixAcquisition: 50_000, ratioMaintenance: ratio, indisponibiliteHeures: 6,
        reformeRecommandee: reforme,
      },
    });
    const top = [equipement(true, 0.6), equipement(false, null)];
    const columns = gmaoTopColumns(ctx, top);
    const t = byId(columns);
    expect(cellTypes(columns)).toEqual({
      centre: 'avatar', equipement: undefined, coutPeriode: 'dataBar', coutCumule: 'dataBar',
      ratioMaintenance: 'progress', indisponibiliteHeures: 'dataBar', reformeRecommandee: 'badge',
    });
    expect(cellTextOf(t['equipement'], top[0])).toBe('GEN-01 — Générateur');
    expect(t['ratioMaintenance'].valueAccessor(top[0])).toBe(60);
    expect(cellTextOf(t['ratioMaintenance'], top[0])).toBe('60 %');
    expect(cellTextOf(t['ratioMaintenance'], top[1])).toBe('—');
    expect(t['reformeRecommandee'].cell).toEqual({type: 'badge', tone: {true: 'warning'}});
    expect(cellTextOf(t['reformeRecommandee'], top[0])).toBe('DIRECTION.GMAO.A_REFORMER');
    // pas de pastille vide quand la réforme n'est pas recommandée
    expect(cellFormatOf(t['reformeRecommandee'], top[1], false, '')).toBeNull();
  });

  it('capacité : occupation en progression à bornes fixes et niveau en pastille teintée', () => {
    const ligne = (over: Partial<LigneCapacite>): LigneCapacite => ({
      generateurs: 10, generateursSecours: 1, postesActifs: 9, series: 3, patientsParPosteEtSerie: 2, capacite: 54,
      fileActive: 50, tauxOccupation: 92.6, niveau: 'PROCHE', atteinte: false, ...over,
    });
    const alphaRow: CapaciteRow = {centerId: 'a', nom: 'Centre Alpha', ligne: ligne({}), total: false};
    const masque: CapaciteRow = {
      centerId: 'b', nom: 'Centre Beta', total: false,
      ligne: ligne({capacite: null, fileActive: null, tauxOccupation: null, niveau: null, atteinte: null}),
    };
    const totalRow: CapaciteRow = {centerId: null, nom: null, ligne: ligne({series: 0}), total: true};
    const c = byId(capaciteColumns(ctx, [alphaRow, masque]));

    expect(NIVEAU_CAPACITE_TONES).toEqual({MARGE: 'success', PROCHE: 'warning', ATTEINTE: 'danger', SANS_CAPACITE: 'neutral'});
    expect(c['niveau'].cell).toEqual({type: 'badge', tone: NIVEAU_CAPACITE_TONES});
    expect(c['niveau'].valueAccessor(alphaRow)).toBe('PROCHE');
    expect(cellTextOf(c['niveau'], alphaRow)).toBe('DIRECTION.CAPACITE.NIVEAU.PROCHE');
    expect(cellTextOf(c['niveau'], masque)).toBe('—');
    expect(c['tauxOccupation'].cell).toBe(RATE_CELL);
    expect(cellTextOf(c['tauxOccupation'], alphaRow)).toBe('92.6 %');
    expect(c['capacite'].cell).toEqual({type: 'dataBar', min: 0, max: 54});
    expect(cellTextOf(c['capacite'], masque)).toBe('—');
    expect(cellTextOf(c['fileActive'], masque)).toBe('< 5');
    expect(cellTextOf(c['series'], totalRow)).toBe('—');
    expect(cellTextOf(c['centre'], totalRow)).toBe('DIRECTION.TOTAL');
  });

  it('absences : effectifs en barres, taux et part du CA en progression, motifs en pastilles', () => {
    const row: AbsencesRow = {
      centerId: 'a', nom: 'Centre Alpha', total: false,
      stats: {
        nbAbsences: 9, nbJustifiees: 3, nbNonJustifiees: 2, nbAQualifier: 4, nbRattrapees: 0, nbSeances: 90,
        valorisationHt: null, valorisationTtc: null, caHt: 9000, tauxAbsenteisme: 10, partCaHt: 1.5,
      },
    };
    const columns = absencesColumns(ctx, [row]);
    const c = byId(columns);
    expect(cellTypes(columns)).toEqual({
      centre: 'avatar', nbAbsences: 'dataBar', nbJustifiees: 'dataBar', nbNonJustifiees: 'dataBar',
      nbAQualifier: 'dataBar', nbRattrapees: 'dataBar', tauxAbsenteisme: 'progress', valorisationHt: 'dataBar',
      caHt: 'dataBar', partCaHt: 'progress',
    });
    expect(c['nbAQualifier'].emphasis?.(row)).toBe('warn');
    expect(cellTextOf(c['valorisationHt'], row)).toBe('—');
    expect(cellTextOf(c['partCaHt'], row)).toBe('1.5 %');

    const motifs = [{motif: 'MALADIE', nb: 6, valorisationHt: 600}, {motif: 'NON_QUALIFIE', nb: 2, valorisationHt: null}];
    const m = byId(motifsColumns(ctx, motifs));
    expect(cellTextOf(m['motif'], motifs[0])).toBe('ABSENCES.MOTIFS.MALADIE');
    expect(cellTextOf(m['motif'], motifs[1])).toBe('DIRECTION.ABSENCES.NON_QUALIFIE');
    const tone = (m['motif'].cell as { tone: (value: unknown, row: unknown) => string }).tone;
    expect(tone('', motifs[0])).toBe('info');
    expect(tone('', motifs[1])).toBe('warning');
    expect(m['part'].valueAccessor(motifs[0])).toBe(75);
    expect(cellTextOf(m['part'], motifs[0])).toBe('75 %');
    expect(m['nb'].cell).toEqual({type: 'dataBar', min: 0, max: 6});
  });

  it('groupes d\'articles : non triable, barres mesurées sur les centres, autres variations en dégradé centré sur zéro', () => {
    const valeurs = (autres: number, fin: number) => ({valeurDebut: 100, entrees: 50, sorties: 30, autresVariations: autres, valeurFin: fin});
    const rows: StockGroupeRow[] = [
      {key: 'g|total', nom: 'Dialyseurs', centre: null, nbArticles: 8, valeurs: valeurs(-10, 900), total: true},
      {key: 'g|a', nom: 'Dialyseurs', centre: 'Centre Alpha', nbArticles: 5, valeurs: valeurs(-40, 500), total: false},
      {key: 'g|b', nom: 'Dialyseurs', centre: 'Centre Beta', nbArticles: 3, valeurs: valeurs(30, 400), total: false},
    ];
    const columns = stockGroupesColumns(ctx, rows);
    const c = byId(columns);

    expect(columns.every((col) => col.sortable === false)).toBe(true);
    expect(c['valeurFin'].cell).toEqual({type: 'dataBar', min: 0, max: 500});
    expect(c['autresVariations'].cell).toEqual({type: 'colorScale', diverging: true, min: -40, max: 40});
    expect(c['autresVariations'].emphasis?.(rows[1])).toBe('warn');
    expect(c['autresVariations'].emphasis?.(rows[2])).toBeNull();
    expect(cellTextOf(c['centre'], rows[0])).toBe('DIRECTION.TOTAL');
    // le total consolidé garde l'avatar du groupe mais s'écrit sans barre ni dégradé
    expect(cellFormatOf(c['groupe'], rows[0], 'Dialyseurs', 'Dialyseurs')).toEqual({type: 'avatar'});
    expect(cellFormatOf(c['valeurFin'], rows[0], 900, '900 DA')).toBeNull();
    expect(cellFormatOf(c['autresVariations'], rows[0], -10, '-10 DA')).toBeNull();
    expect(cellFormatOf(c['autresVariations'], rows[1], -40, '-40 DA')).toBe(c['autresVariations'].cell);
  });

  it('répartitions : sexe et tranches d\'âge sur une échelle commune, caisses par centre selon la mesure', () => {
    const sexe = [
      {centerId: 'a', nom: 'Centre Alpha', masculin: 20, feminin: 12, autre: null},
      {centerId: 'b', nom: 'Centre Beta', masculin: 7, feminin: 30, autre: 0},
    ];
    const s = byId(sexeColumns(ctx, sexe));
    expect(s['masculin'].cell).toEqual({type: 'dataBar', min: 0, max: 30});
    expect(s['autre'].cell).toEqual({type: 'dataBar', min: 0, max: 30});
    expect(cellTextOf(s['autre'], sexe[0])).toBe('< 5');

    const ages = [{centerId: 'a', nom: 'Centre Alpha', tranches: [{code: '0_17', count: null}, {code: '60_PLUS', count: 30}]}];
    const a = byId(agesColumns(ctx, ages, ['0_17', '60_PLUS']));
    expect(a['age_60_PLUS'].header).toBe('DIRECTION.BREAKDOWN.AGE.60_PLUS');
    expect(a['age_60_PLUS'].cell).toEqual({type: 'dataBar', min: 0, max: 30});
    expect(cellTextOf(a['age_0_17'], ages[0])).toBe('< 5');

    const centres = [{id: 'a', nom: 'Centre Alpha'}, {id: 'b', nom: 'Centre Beta'}];
    const lines = [{code: 'CNAS', nom: 'CNAS Nationale', cells: {a: 440, b: null}, total: 440}, {code: '', nom: '', cells: {a: 5, b: 0}, total: 5}];
    const seances = byId(caissesColumns(ctx, centres, lines, 'seances'));
    expect(seances['centre_a'].header).toBe('Centre Alpha');
    expect(seances['centre_a'].cell).toEqual({type: 'dataBar', min: 0, max: 440});
    expect(cellTextOf(seances['centre_a'], lines[0])).toBe('440');
    expect(cellTextOf(seances['caisse'], lines[1])).toBe('DIRECTION.BREAKDOWN.CAISSE_INCONNUE');
    expect(seances['caisse'].cell?.type).toBe('avatar');
    const patients = byId(caissesColumns(ctx, centres, lines, 'patients'));
    expect(cellTextOf(patients['centre_b'], lines[0])).toBe('< 5');
    const ca = byId(caissesColumns(ctx, centres, lines, 'caHt'));
    expect(cellTextOf(ca['total'], lines[0])).toBe('440 DA');
    expect(cellTextOf(ca['centre_b'], lines[0])).toBe('');

    const anemie: AnemieRow = {
      centerId: 'a', nom: 'Centre Alpha', patientsEpo: 20, patientsFer: null, administreesEpo: 66, administreesFer: 30,
      nonAdministrees: 2, tauxAdministration: 97.1, patientsSousEpo: 22, patientsSousFer: 9,
    };
    const an = byId(anemieColumns(ctx, [anemie]));
    expect(an['tauxAdministration'].cell).toBe(RATE_CELL);
    expect(cellTextOf(an['tauxAdministration'], anemie)).toBe('97.1 %');
    expect(an['nonAdministrees'].cell).toMatchObject({type: 'dataBar', tone: 'danger'});
    expect(an['nonAdministrees'].emphasis?.(anemie)).toBe('bad');
  });

  it('rapports : état du mois en pastille (figé / non figé)', () => {
    const c = byId(reportColumns(ctx));
    const fige = {mois: '2026-08', generatedAt: '2026-09-01T08:00:00'};
    const nonFige = {mois: '2026-09', generatedAt: null};

    expect(REPORT_STATUS_TONES).toEqual({FROZEN: 'success', NOT_FROZEN: 'neutral'});
    expect(c['statut'].cell).toEqual({type: 'badge', tone: REPORT_STATUS_TONES});
    expect(c['statut'].valueAccessor(fige)).toBe('FROZEN');
    expect(cellTextOf(c['statut'], fige)).toBe('DIRECTION.REPORTS.FROZEN_ON le 2026-09-01T08:00:00');
    expect(c['statut'].valueAccessor(nonFige)).toBe('NOT_FROZEN');
    expect(cellTextOf(c['statut'], nonFige)).toBe('DIRECTION.REPORTS.NOT_FROZEN');
  });

  it('comparateur de mois : variation en dégradé centré sur zéro, à bornes fixes ± 100 %', () => {
    const snapshot = (mois: string, over: Partial<CentreStats>): Snapshot =>
      ({mois, overview: {totaux: centre('', 'Société', over)}} as unknown as Snapshot);
    const rows = compareRows(ctx, snapshot('2026-07', {seances: 10, caTtc: 1000, encaisse: 500, patients: null}),
      snapshot('2026-08', {seances: 20, caTtc: 900, encaisse: 600}));
    const columns = compareColumns(ctx, '2026-07', '2026-08');
    const c = byId(columns);
    const row = (id: string) => rows.find((r) => r.id === id)!;

    expect(rows.map((r) => r.id)).toEqual(['patients', 'seances', 'caTtc', 'encaisse', 'tauxEncaissement']);
    expect(columns.map((col) => col.header)).toEqual(['DIRECTION.COL.CENTRE', '2026-07', '2026-08', 'DIRECTION.COMPARE.DELTA']);
    expect(cellTypes(columns)).toEqual({indicateur: undefined, a: undefined, b: undefined, delta: 'colorScale'});
    expect(DELTA_CELL).toEqual({type: 'colorScale', diverging: true, min: -100, max: 100});
    expect(c['delta'].cell).toBe(DELTA_CELL);
    expect(row('patients').a).toBe('< 5');
    expect(row('caTtc').a).toBe('1000 DA');
    expect(c['delta'].valueAccessor(row('seances'))).toBe(100);
    expect(cellTextOf(c['delta'], row('seances'))).toBe('+100.0 %');
    expect(c['delta'].emphasis?.(row('seances'))).toBe('good');
    expect(cellTextOf(c['delta'], row('caTtc'))).toBe('-10.0 %');
    expect(c['delta'].emphasis?.(row('caTtc'))).toBe('bad');
    expect(cellTextOf(c['delta'], row('encaisse'))).toBe('+20.0 %');
    // patients et taux : pas de variation calculée, ni dégradé
    expect(cellTextOf(c['delta'], row('patients'))).toBe('—');
    expect(cellFormatOf(c['delta'], row('patients'), null, '—')).toBeNull();
  });

  it('outils : maximum des valeurs publiées et format d\'une variation', () => {
    expect(maxOf([{a: 3, b: null}, {a: null, b: 8}], (r) => r.a, (r) => r.b)).toBe(8);
    expect(maxOf([] as { a: number }[], (r) => r.a)).toBe(0);
    expect(formatDelta({pct: 4.24, direction: 'up'})).toBe('+4.2 %');
    expect(formatDelta({pct: null, direction: 'up'})).toBe('');
    expect(formatDelta(null)).toBe('');
  });
});
