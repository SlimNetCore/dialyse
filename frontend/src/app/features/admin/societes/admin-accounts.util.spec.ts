import {describe, expect, it} from 'vitest';
import {AdminAccount} from '../../../core/api/societe-admin-api.service';
import {adminToForm, centreNames, toUpdatePayload} from './admin-accounts.util';

const account: AdminAccount = {
  userId: 'u1', username: 'admin', fullName: 'Administrateur', email: null, active: true,
  centres: [{id: 'c1', name: 'ROUIBA'}, {id: 'c2', name: 'ANNABA'}],
};

describe('admin-accounts.util', () => {
  it('préremplit le formulaire avec les centres actuels', () => {
    const form = adminToForm(account);
    expect(form.username).toBe('admin');
    expect(form.email).toBe('');
    expect(form.centerIds).toEqual(['c1', 'c2']);
  });

  it('construit la charge utile de modification', () => {
    const payload = toUpdatePayload({...adminToForm(account), fullName: '  Nouveau nom ', email: ' '});
    expect(payload).toEqual({fullName: 'Nouveau nom', email: undefined, centerIds: ['c1', 'c2']});
  });

  it("liste les centres d'un administrateur", () => {
    expect(centreNames(account)).toBe('ROUIBA, ANNABA');
  });
});
