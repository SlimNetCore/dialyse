import {describe, expect, it} from 'vitest';
import {stepStatus} from './migration-entity-card.component';
import {reprisePeriodStart} from './reprise-donnees.component';
import {MigrationRun} from '../../../core/api/migration-api.service';

function run(partial: Partial<MigrationRun>): MigrationRun {
  return {
    entity: 'patients', fileName: 'p.csv', totalRows: 1, created: 1, updated: 0, missingColumns: [], ignoredColumns: [],
    errors: [], warnings: [], valid: true, dryRun: true, applied: false, executedBy: null, executedAt: '', ...partial,
  };
}

describe('reprise — fonctions pures', () => {
  it('déduit l\'état d\'une étape de son dernier compte rendu', () => {
    expect(stepStatus(null)).toBe('TODO');
    expect(stepStatus(run({valid: false}))).toBe('TO_FIX');
    expect(stepStatus(run({}))).toBe('CHECKED');
    expect(stepStatus(run({applied: true, dryRun: false}))).toBe('IMPORTED');
  });

  it('calcule le début de la période reprise (N dernières années)', () => {
    expect(reprisePeriodStart(3, new Date(2026, 8, 30))).toBe('2023-09-30');
    expect(reprisePeriodStart(1, new Date(2024, 1, 29))).toBe('2023-03-01');
  });
});

