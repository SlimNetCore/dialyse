import {Component, provideZonelessChangeDetection} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {provideNoopAnimations} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';
import {TranslateModule} from '@ngx-translate/core';
import {EMPTY} from 'rxjs';
import {describe, expect, it} from 'vitest';
import {AbsencePatientApiService} from '../../core/api/absence-patient-api.service';
import {BackendApiService} from '../../core/api/backend-api.service';
import {AuthStore} from '../../core/state/auth.store';
import {AbsencesPatientsComponent} from './absences-patients.component';
import {MoisDAbsencesComponent} from './mois-d-absences.component';

/** Vue des mois d'absences remplacée par un témoin : seul son affichage (ou non) est vérifié ici. */
@Component({selector: 'app-mois-d-absences', standalone: true, template: ''})
class MoisDAbsencesTemoin {
}

/** Chaque appel serveur se termine sans réponse : l'écran reste vide, seul le rôle compte. */
const apiSansReponse = new Proxy({}, {get: () => () => EMPTY});

describe('AbsencesPatientsComponent — clôture mensuelle des séances (Paramétrage › Clôtures mensuelles)', () => {
  async function afficherPour(role: string): Promise<HTMLElement> {
    TestBed.configureTestingModule({
      imports: [AbsencesPatientsComponent, TranslateModule.forRoot()],
      providers: [
        provideZonelessChangeDetection(),
        provideNoopAnimations(),
        provideRouter([]),
        {provide: AbsencePatientApiService, useValue: apiSansReponse},
        {provide: BackendApiService, useValue: apiSansReponse},
        {provide: AuthStore, useValue: {hasRole: (r: string) => r === role, username: () => 'u'}},
      ],
    });
    TestBed.overrideComponent(AbsencesPatientsComponent, {
      remove: {imports: [MoisDAbsencesComponent]},
      add: {imports: [MoisDAbsencesTemoin]},
    });
    const fixture = TestBed.createComponent(AbsencesPatientsComponent);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('l\'ADMIN voit le lien vers Paramétrage › Clôtures mensuelles, sans la vue des mois', async () => {
    const dom = await afficherPour('ADMIN');
    const lien = dom.querySelector('[data-testid="lien-clotures-mensuelles"]');
    expect(lien).not.toBeNull();
    expect(lien?.getAttribute('href')).toBe('/admin/parametrage/clotures');
    expect(dom.querySelector('app-mois-d-absences')).toBeNull();
  });

  it.each(['SECRETAIRE', 'MEDECIN'])('%s garde l\'état des mois en lecture, sans lien d\'administration',
    async (role) => {
      const dom = await afficherPour(role);
      expect(dom.querySelector('app-mois-d-absences')).not.toBeNull();
      expect(dom.querySelector('[data-testid="lien-clotures-mensuelles"]')).toBeNull();
    });
});
