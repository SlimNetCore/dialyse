import {TestBed} from '@angular/core/testing';
import {provideZonelessChangeDetection} from '@angular/core';
import {MAT_DIALOG_DATA, MatDialogRef} from '@angular/material/dialog';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {TranslateModule} from '@ngx-translate/core';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {SupprimerSeanceDialogComponent} from './supprimer-seance-dialog.component';

describe('SupprimerSeanceDialogComponent', () => {
  let close: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    close = vi.fn();
    TestBed.configureTestingModule({
      imports: [SupprimerSeanceDialogComponent, TranslateModule.forRoot(), NoopAnimationsModule],
      providers: [
        provideZonelessChangeDetection(),
        {provide: MAT_DIALOG_DATA, useValue: {patient: 'Karim B.', date: '2026-10-05', statut: 'VALIDEE'}},
        {provide: MatDialogRef, useValue: {close}},
      ],
    });
  });

  async function render() {
    const fixture = TestBed.createComponent(SupprimerSeanceDialogComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    return {fixture, cmp: fixture.componentInstance as any, root: fixture.nativeElement as HTMLElement};
  }

  it('n\'autorise la confirmation qu\'avec un motif suffisant', async () => {
    const {fixture, cmp, root} = await render();
    const bouton = () => root.querySelector('[data-testid="seance-delete-confirm"]') as HTMLButtonElement;
    expect(bouton().disabled).toBe(true);

    cmp.motifModel.set({motif: 'abc'});
    fixture.detectChanges();
    expect(bouton().disabled).toBe(true);
    cmp.confirmer();
    expect(close).not.toHaveBeenCalled();

    cmp.motifModel.set({motif: '  Séance saisie en double  '});
    fixture.detectChanges();
    expect(bouton().disabled).toBe(false);
    cmp.confirmer();
    expect(close).toHaveBeenCalledWith('Séance saisie en double');
  });

  it('refuse un motif fait d\'espaces', async () => {
    const {cmp} = await render();
    cmp.motifModel.set({motif: '        '});
    cmp.confirmer();
    expect(close).not.toHaveBeenCalled();
  });
});
