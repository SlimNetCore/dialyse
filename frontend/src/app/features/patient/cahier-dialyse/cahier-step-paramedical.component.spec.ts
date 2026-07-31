import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TranslateModule} from '@ngx-translate/core';
import {MatSnackBar} from '@angular/material/snack-bar';
import {BackendApiService} from '../../../core/api/backend-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {AuthStore} from '../../../core/state/auth.store';
import {ArticlesStore} from '../../../core/state/referentials.store';
import {CahierStepParamedicalComponent} from './cahier-step-paramedical.component';

describe('CahierStepParamedicalComponent', () => {
  const appShellMock = {currentCenterId: vi.fn(() => 'center-1')};
  const authMock = {username: vi.fn(() => 'nurse-1')};
  const articlesStoreMock = {
    items: signal([{id: 'article-1', label: 'Article 1'}]),
    loading: signal(false),
    error: signal<string | null>(null),
    ensureLoaded: vi.fn(),
  };
  const snackBarMock = {open: vi.fn()};

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [CahierStepParamedicalComponent, TranslateModule.forRoot()],
      providers: [
        {provide: BackendApiService, useValue: {}},
        {provide: AppShellStore, useValue: appShellMock},
        {provide: AuthStore, useValue: authMock},
        {provide: ArticlesStore, useValue: articlesStoreMock},
        {provide: MatSnackBar, useValue: snackBarMock},
      ],
    }).compileComponents();
  });

  it('should create with patientId input', () => {
    const fixture = TestBed.createComponent(CahierStepParamedicalComponent);
    fixture.componentInstance.patientId = 'patient-1';
    fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should expose summaryFields with expected keys', () => {
    const fixture = TestBed.createComponent(CahierStepParamedicalComponent);
    const keys = fixture.componentInstance.summaryFields.map(f => f.key);
    expect(keys).toContain('poidsAvantKg');
    expect(keys).toContain('taAvant');
    expect(keys).toContain('incidents');
  });

  it('formatValue should return "-" for null/undefined', () => {
    const fixture = TestBed.createComponent(CahierStepParamedicalComponent);
    expect(fixture.componentInstance.formatValue(null)).toBe('-');
    expect(fixture.componentInstance.formatValue(undefined)).toBe('-');
    expect(fixture.componentInstance.formatValue('')).toBe('-');
  });

  it('formatValue should convert numbers to string', () => {
    const fixture = TestBed.createComponent(CahierStepParamedicalComponent);
    expect(fixture.componentInstance.formatValue(42)).toBe('42');
    expect(fixture.componentInstance.formatValue(3.14)).toBe('3.14');
  });

  it('formatValue should return string values as-is', () => {
    const fixture = TestBed.createComponent(CahierStepParamedicalComponent);
    expect(fixture.componentInstance.formatValue('test value')).toBe('test value');
  });

  it('should display paramedicalData when provided', () => {
    const fixture = TestBed.createComponent(CahierStepParamedicalComponent);
    fixture.componentInstance.patientId = 'patient-1';
    fixture.componentInstance.paramedicalData = {poidsAvantKg: 70, taAvant: '120/80'};
    fixture.detectChanges();
    expect(fixture.componentInstance.paramedicalData).toBeDefined();
  });
});
