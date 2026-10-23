import {TestBed} from '@angular/core/testing';
import {of, throwError} from 'rxjs';
import {vi} from 'vitest';
import {TranslateModule} from '@ngx-translate/core';
import {ConsommationsDePieceComponent} from './consommations-de-piece.component';
import {StockApiService} from '../../../core/api/stock-api.service';
import {AuthStore} from '../../../core/state/auth.store';

describe('ConsommationsDePieceComponent', () => {
  const api = {
    favorisDeLaSeance: vi.fn(() => of([{articleId: 'a1', quantiteSortie: 8, reste: 6}])),
    consommationDeSeance: vi.fn(() => of({
      seanceId: 's1', patientId: null, sortieId: 'p1',
      imputations: [
        {id: 'i1', articleId: 'a1', lotId: 'lot1', quantite: 2, alerte: null},
        {id: 'i2', articleId: 'a1', lotId: null, quantite: 1, alerte: 'DEPASSEMENT_DE_LA_SORTIE'},
      ],
    })),
  };
  const auth = {centerId: vi.fn(() => 'center-1' as string | null)};

  function creer() {
    const fixture = TestBed.createComponent(ConsommationsDePieceComponent);
    fixture.componentRef.setInput('seanceId', 's1');
    fixture.componentRef.setInput('jour', '2026-10-24');
    fixture.componentRef.setInput('articles', [{id: 'a1', libelle: 'Aiguille'}]);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(async () => {
    vi.clearAllMocks();
    auth.centerId.mockReturnValue('center-1');
    await TestBed.configureTestingModule({
      imports: [ConsommationsDePieceComponent, TranslateModule.forRoot()],
      providers: [
        {provide: StockApiService, useValue: api},
        {provide: AuthStore, useValue: auth},
      ],
    }).compileComponents();
  });

  it('lit les favoris et les alertes de la seance', () => {
    const fixture = creer();
    const c = fixture.componentInstance as any;
    expect(api.favorisDeLaSeance).toHaveBeenCalledWith('s1', 'center-1', '2026-10-24');
    expect(api.consommationDeSeance).toHaveBeenCalledWith('s1', 'center-1');
    expect(c.favoris().length).toBe(1);
    expect(c.alertes().length).toBe(1);
    expect(c.libelle('a1')).toBe('Aiguille');
    expect(c.libelle('inconnu')).toBe('inconnu');
  });

  it('relit les donnees quand la version change', () => {
    const fixture = creer();
    api.favorisDeLaSeance.mockClear();
    fixture.componentRef.setInput('version', [{articleId: 'a1', quantite: 3}]);
    fixture.detectChanges();
    expect(api.favorisDeLaSeance).toHaveBeenCalledTimes(1);
  });

  it('reste vide sans centre ou en cas d erreur', () => {
    auth.centerId.mockReturnValue(null);
    let c = creer().componentInstance as any;
    expect(c.favoris()).toEqual([]);
    expect(api.favorisDeLaSeance).not.toHaveBeenCalled();

    auth.centerId.mockReturnValue('center-1');
    api.favorisDeLaSeance.mockReturnValueOnce(throwError(() => new Error('403')) as never);
    c = creer().componentInstance as any;
    expect(c.favoris()).toEqual([]);
  });
});

