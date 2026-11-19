import {ChangeDetectionStrategy, Component} from '@angular/core';
import {MatCardModule} from '@angular/material/card';
import {MatIconModule} from '@angular/material/icon';
import {MatTabsModule} from '@angular/material/tabs';
import {TranslateModule} from '@ngx-translate/core';
import {FacturationClotureComponent} from '../../facturation/cloture/facturation-cloture.component';
import {MoisDAbsencesComponent} from '../../absences/mois-d-absences.component';

/**
 * Administration › Paramétrage › Clôtures mensuelles : la clôture et la réouverture mensuelles de la facturation (F7)
 * et des séances (mois d'absences, A6) réunies en un seul écran, réservé à l'ADMIN du centre comme les actions côté
 * serveur. Les composants et les stores sont ceux d'origine : comportement et appels inchangés.
 */
@Component({
  selector: 'app-clotures-mensuelles',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatCardModule, MatIconModule, MatTabsModule, TranslateModule, FacturationClotureComponent,
    MoisDAbsencesComponent],
  templateUrl: './clotures-mensuelles.component.html',
  styleUrl: './clotures-mensuelles.component.css',
})
export class CloturesMensuellesComponent {
}
