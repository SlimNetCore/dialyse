import {TestBed} from '@angular/core/testing';
import {vi} from 'vitest';
import {FacturationSettingsComponent} from './facturation-settings.component';
import {FacturationStore} from '../facturation/state/facturation.store';
import {AppShellStore} from '../../core/state/app-shell.store';
import {AuthStore} from '../../core/state/auth.store';

describe('FacturationSettingsComponent', () => {
  const facturationStoreMock = {
    setActiveCenterId: vi.fn(),
    loadSettings: vi.fn(),
    patchSettingsDraft: vi.fn(),
    saveSettings: vi.fn(),
  };

  const appShellMock = {
    currentCenterId: vi.fn(() => '11111111-1111-1111-1111-111111111111'),
  };

  const authMock = {
    username: vi.fn(() => 'admin.user'),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      providers: [
        {provide: FacturationStore, useValue: facturationStoreMock},
        {provide: AppShellStore, useValue: appShellMock},
        {provide: AuthStore, useValue: authMock},
      ],
    });
  });

  it('propage centerId et userId lors de la sauvegarde', () => {
    const component = TestBed.runInInjectionContext(() => new FacturationSettingsComponent());

    component['onSaveSettings']();

    expect(facturationStoreMock.saveSettings).toHaveBeenCalledWith({
      centerId: '11111111-1111-1111-1111-111111111111',
      userId: 'admin.user',
    });
  });
});

