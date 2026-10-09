import {ComponentRef, provideZonelessChangeDetection} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {TranslateModule} from '@ngx-translate/core';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {
  CalendrierProposition,
  CaseCalendrier,
  JourCalendrier,
} from '../../../core/api/planning-optimisation-api.service';
import {PlanningProposeComponent} from './planning-propose.component';

const JOURS = ['DIMANCHE', 'LUNDI', 'MARDI', 'MERCREDI', 'JEUDI', 'VENDREDI', 'SAMEDI'] as const;

function jour(index: number, surcharge: Partial<JourCalendrier> = {}): JourCalendrier {
  return {
    jour: JOURS[index], date: `2026-09-${27 + index}`, ferme: false, motifFermeture: null, requis: 0, manque: 0,
    patients: [], infirmiers: [], ...surcharge,
  };
}

function ligne(actifs: Record<number, Partial<JourCalendrier>>): CaseCalendrier {
  return {
    semaineDebut: '2026-09-27', salleId: 's1', salleNom: 'Salle A', salleOrdre: 1, creneauId: 'c1',
    creneauLibelle: 'Matin', creneauOrdre: 1, jours: JOURS.map((_, i) => jour(i, actifs[i] ?? {})),
  };
}

const PATIENT = {
  patientId: 'p1',
  nom: 'Benali',
  generateurCode: 'A-G1',
  aRisque: false,
  deplace: false,
  temporaire: false
};

function calendrier(surcharge: Partial<CalendrierProposition> = {}): CalendrierProposition {
  return {
    semaines: ['2026-09-27', '2026-10-04'], semaine: '2026-09-27',
    cases: [ligne({
      1: {
        requis: 1,
        manque: 1,
        patients: [PATIENT, {...PATIENT, patientId: 'p2', nom: 'Kaci', aRisque: true, deplace: true}],
        infirmiers: [{nom: 'Sara', situation: 'PREVU'}, {nom: 'Lila', situation: 'REMPLACANT'}],
      },
      2: {ferme: true, motifFermeture: 'Férié'},
    })],
    ...surcharge,
  };
}

describe('PlanningProposeComponent', () => {
  let cible: ComponentRef<PlanningProposeComponent>;
  let root: HTMLElement;
  let mobile: boolean;

  async function render(valeur: CalendrierProposition | null, entrees: Record<string, unknown> = {}) {
    await TestBed.configureTestingModule({
      imports: [PlanningProposeComponent, TranslateModule.forRoot(), NoopAnimationsModule],
      providers: [provideZonelessChangeDetection()],
    }).compileComponents();
    window.matchMedia = ((q: string) => ({
      matches: mobile && q.includes('767px'), media: q, addListener: () => undefined, removeListener: () => undefined,
      addEventListener: () => undefined, removeEventListener: () => undefined,
    })) as unknown as typeof window.matchMedia;
    const fixture = TestBed.createComponent(PlanningProposeComponent);
    cible = fixture.componentRef;
    cible.setInput('calendrier', valeur);
    cible.setInput('aujourdhui', '2026-09-28');
    Object.entries(entrees).forEach(([nom, v]) => cible.setInput(nom, v));
    fixture.detectChanges();
    await fixture.whenStable();
    root = fixture.nativeElement as HTMLElement;
    return fixture;
  }

  const q = (id: string) => root.querySelector(`[data-testid="${id}"]`) as HTMLElement | null;
  const qa = (id: string) => Array.from(root.querySelectorAll(`[data-testid="${id}"]`)) as HTMLElement[];

  const matchMediaOrigine = window.matchMedia;

  beforeEach(() => {
    mobile = false;
  });
  afterEach(() => {
    window.matchMedia = matchMediaOrigine;
    TestBed.resetTestingModule();
  });

  it('affiche la grille de la semaine : une ligne salle/créneau, patients avec générateur et infirmiers', async () => {
    await render(calendrier());

    expect(qa('pp-ligne')).toHaveLength(1);
    expect(root.textContent).toContain('Salle A');
    expect(root.textContent).toContain('Matin');
    expect(qa('pp-patient').map((p) => p.textContent)).toEqual([
      expect.stringContaining('Benali'), expect.stringContaining('Kaci'),
    ]);
    expect(qa('pp-patient')[0].textContent).toContain('A-G1');
    expect(qa('pp-patient')[1].classList).toContain('risk');
    expect(qa('pp-patient')[1].classList).toContain('moved');
    expect(q('pp-infirmiers')?.textContent).toContain('Sara');
    expect(q('pp-infirmiers')?.textContent).toContain('Lila');
    expect(q('pp-infirmiers')?.querySelector('[data-situation="REMPLACANT"]')).not.toBeNull();
  });

  it('signale l\'effectif, le manque d\'infirmiers et le jour fermé', async () => {
    await render(calendrier());

    expect(q('pp-effectif')?.textContent).toContain('PLANNING.OPTIM.CALENDRIER.MANQUE');
    expect(q('pp-ferme')?.textContent).toContain('Férié');
    expect(qa('pp-cell').some((c) => c.classList.contains('manque'))).toBe(true);
  });

  it('indique en infobulle la place d\'avant d\'un patient déplacé et la place habituelle d\'une place temporaire', async () => {
    await render(calendrier());
    const {placeAvant} = cible.instance as unknown as {
      placeAvant(p: typeof PATIENT & Record<string, unknown>): string
    };
    const avant = (p: Record<string, unknown>) => placeAvant.call(cible.instance, {...PATIENT, ...p} as never);

    expect(avant({})).toBe('');
    expect(avant({deplace: true, avant: 'Salle B · Matin · B-G2'})).toBe('PLANNING.OPTIM.CALENDRIER.AVANT');
    expect(avant({deplace: true, avant: null})).toBe('PLANNING.OPTIM.CALENDRIER.AVANT_AUCUNE');
    expect(avant({temporaire: true, avant: 'Salle A · Matin · A-G1'})).toBe('PLANNING.OPTIM.CALENDRIER.HABITUELLE');
  });

  it('compte les infirmiers affectés de la case, absents exclus', async () => {
    await render(calendrier());
    const {affectes} = cible.instance as unknown as { affectes(j: JourCalendrier): number };

    const jourAvecAbsent = jour(1, {
      infirmiers: [{nom: 'Sara', situation: 'PREVU'}, {nom: 'Lila', situation: 'ABSENT'},
        {nom: 'Rym', situation: 'NOUVEAU'}]
    });

    expect(affectes.call(cible.instance, jourAvecAbsent)).toBe(2);
    expect(affectes.call(cible.instance, jour(2))).toBe(0);
  });

  it('signale les infirmiers en trop d\'une case, et seulement celles-là', async () => {
    const avecSurplus = calendrier({
      cases: [ligne({
        1: {
          requis: 1, surplus: 2, patients: [PATIENT], infirmiers: [{nom: 'Sara', situation: 'PREVU'},
            {nom: 'Lila', situation: 'PREVU'}, {nom: 'Rym', situation: 'NOUVEAU'}]
        },
        3: {requis: 1, patients: [PATIENT], infirmiers: [{nom: 'Sara', situation: 'PREVU'}]},
      })],
    });
    await render(avecSurplus);

    const surplus = qa('pp-surplus');
    expect(surplus).toHaveLength(1);
    expect(surplus[0].textContent).toContain('PLANNING.OPTIM.CALENDRIER.SURPLUS');
    expect(q('pp-legend')?.textContent).toContain('PLANNING.OPTIM.CALENDRIER.LEGENDE.SURPLUS');
  });

  it('navigue entre les semaines : émet la semaine voisine et bloque aux extrémités', async () => {
    await render(calendrier());
    const choisies: string[] = [];
    cible.instance.semaineChoisie.subscribe((s) => choisies.push(s));

    expect((q('pp-prev') as HTMLButtonElement).disabled).toBe(true);
    (q('pp-next') as HTMLButtonElement).click();

    expect(choisies).toEqual(['2026-10-04']);
  });

  it('indique la position de la semaine affichée parmi celles de la proposition (Semaine 3 sur 4)', async () => {
    await render(calendrier({
      semaines: ['2026-09-27', '2026-10-04', '2026-10-11', '2026-10-18'], semaine: '2026-10-11',
    }));

    expect(q('pp-pos')!.textContent).toContain('PLANNING.OPTIM.CALENDRIER.POSITION');
    expect((q('pp-prev') as HTMLButtonElement).disabled).toBe(false);
    expect((q('pp-next') as HTMLButtonElement).disabled).toBe(false);
    expect(q('pp-une-semaine')).toBeNull();
  });

  it('explique qu\'une proposition d\'une seule semaine ne se parcourt pas et comment en obtenir plusieurs', async () => {
    await render(calendrier({semaines: ['2026-09-27'], semaine: '2026-09-27'}));

    expect(q('pp-une-semaine')!.textContent).toContain('PLANNING.OPTIM.CALENDRIER.UNE_SEMAINE');
    expect(q('pp-pos')).toBeNull();
    expect((q('pp-prev') as HTMLButtonElement).disabled).toBe(true);
    expect((q('pp-next') as HTMLButtonElement).disabled).toBe(true);
  });

  it('émet la demande d\'impression, sauf pendant l\'impression ou sans ligne', async () => {
    const fixture = await render(calendrier());
    const imprimer = vi.fn();
    cible.instance.imprimer.subscribe(imprimer);

    (q('pp-imprimer') as HTMLButtonElement).click();
    expect(imprimer).toHaveBeenCalledTimes(1);

    cible.setInput('printing', true);
    fixture.detectChanges();
    expect((q('pp-imprimer') as HTMLButtonElement).disabled).toBe(true);

    cible.setInput('printing', false);
    cible.setInput('calendrier', calendrier({cases: []}));
    fixture.detectChanges();
    expect((q('pp-imprimer') as HTMLButtonElement).disabled).toBe(true);
    expect(q('pp-vide')).not.toBeNull();
  });

  it('propose de relancer le calcul quand la proposition n\'a aucune semaine', async () => {
    await render({semaines: [], semaine: '', cases: []});

    expect(q('pp-absent')).not.toBeNull();
    expect(q('pp-table')).toBeNull();
  });

  it('sur mobile, ouvre la vue jour : cartes du jour actif (le lendemain d\'aujourd\'hui sans activité → premier jour actif)', async () => {
    mobile = true;
    await render(calendrier());

    expect(q('pp-table')).toBeNull();
    expect(qa('pp-day-card')).toHaveLength(1);
    expect(qa('pp-day-card')[0].textContent).toContain('Salle A');
    expect(qa('pp-day-card')[0].textContent).toContain('Benali');
  });

  it('bascule de la grille à la vue jour', async () => {
    const fixture = await render(calendrier());
    expect(q('pp-table')).not.toBeNull();

    (root.querySelector('[data-testid="pp-view-jour"] button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(q('pp-table')).toBeNull();
    expect(q('pp-day-view')).not.toBeNull();
  });
});
