import {TestBed} from '@angular/core/testing';
import {MAT_DIALOG_DATA, MatDialog, MatDialogRef} from '@angular/material/dialog';
import {MatSnackBar} from '@angular/material/snack-bar';
import {TranslateModule} from '@ngx-translate/core';
import {of, throwError} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {BackendApiService, ModeleVersionsPage} from '../../core/api/backend-api.service';
import {ModeleTemplateDialogComponent} from './modele-template-dialog.component';

type Exposed = {
  versions: () => unknown[];
  activeVersion: () => number | null;
  hasCustomActive: () => boolean;
  violations: () => Array<{ code: string; detail: string }>;
  selectedFile: { set: (f: File | null) => void; (): File | null };
  onFileSelected: (e: Event) => void;
  upload: () => void;
};

describe('ModeleTemplateDialogComponent', () => {
  const page = (activeVersion: number | null): ModeleVersionsPage => ({
    items: [{
      id: 'v1', version: 1, actif: activeVersion === 1, sha256: 'x', tailleOctets: 100, commentaire: null,
      uploadedBy: 'admin', uploadedAt: '2026-09-25T10:00:00Z',
    }],
    total: 1, page: 0, size: 10, activeVersion,
  });

  const api = {
    listModeleVersions: vi.fn(),
    uploadModeleVersion: vi.fn(),
    activateModeleVersion: vi.fn(),
    resetModele: vi.fn(),
    downloadModeleSource: vi.fn(),
  };

  function create(): Exposed {
    TestBed.configureTestingModule({
      imports: [ModeleTemplateDialogComponent, TranslateModule.forRoot()],
      providers: [
        {provide: BackendApiService, useValue: api},
        {
          provide: MAT_DIALOG_DATA,
          useValue: {modeleId: 'm1', centerId: 'c1', code: 'ATTESTATION', libelle: 'Attestation'}
        },
        {provide: MatDialogRef, useValue: {close: vi.fn()}},
        {provide: MatDialog, useValue: {open: vi.fn()}},
        {provide: MatSnackBar, useValue: {open: vi.fn()}},
      ],
    });
    const fixture = TestBed.createComponent(ModeleTemplateDialogComponent);
    fixture.detectChanges();
    return fixture.componentInstance as unknown as Exposed;
  }

  beforeEach(() => {
    vi.resetAllMocks();
    api.listModeleVersions.mockReturnValue(of(page(null)));
  });

  it('charge l\'historique du modèle du centre courant', () => {
    api.listModeleVersions.mockReturnValue(of(page(1)));
    const component = create();
    expect(api.listModeleVersions).toHaveBeenCalledWith('m1', 'c1', 0, 10);
    expect(component.versions()).toHaveLength(1);
    expect(component.activeVersion()).toBe(1);
    expect(component.hasCustomActive()).toBe(true);
  });

  it('utilise le modèle d\'origine tant qu\'aucune version n\'est active', () => {
    const component = create();
    expect(component.activeVersion()).toBeNull();
    expect(component.hasCustomActive()).toBe(false);
  });

  it('refuse côté client un fichier qui n\'est pas un .jrxml sans appeler le serveur', () => {
    const component = create();
    const file = new File(['x'], 'evil.exe');
    const input = document.createElement('input');
    Object.defineProperty(input, 'files', {value: {item: () => file, length: 1}});
    component.onFileSelected({target: input} as unknown as Event);
    expect(component.violations()[0].code).toBe('FILE_TYPE');
    expect(component.selectedFile()).toBeNull();
    expect(api.uploadModeleVersion).not.toHaveBeenCalled();
  });

  it('affiche les anomalies renvoyées par le serveur (422) et n\'active rien', () => {
    api.uploadModeleVersion.mockReturnValue(throwError(() => ({
      status: 422, error: {violations: [{code: 'QUERY_MODIFIED', detail: 'requête modifiée'}]},
    })));
    const component = create();
    component.selectedFile.set(new File(['<jasperReport/>'], 'a.jrxml'));
    component.upload();
    expect(api.uploadModeleVersion).toHaveBeenCalledOnce();
    expect(component.violations()).toEqual([{code: 'QUERY_MODIFIED', detail: 'requête modifiée'}]);
    expect(component.activeVersion()).toBeNull();
  });

  it('recharge l\'historique après un téléversement accepté', () => {
    api.uploadModeleVersion.mockReturnValue(of({version: 2}));
    const component = create();
    api.listModeleVersions.mockClear();
    component.selectedFile.set(new File(['<jasperReport/>'], 'a.jrxml'));
    component.upload();
    expect(api.listModeleVersions).toHaveBeenCalledOnce();
    expect(component.violations()).toEqual([]);
    expect(component.selectedFile()).toBeNull();
  });
});
