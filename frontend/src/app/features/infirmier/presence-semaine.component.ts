import {ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked} from '@angular/core';
import {DatePipe, SlicePipe} from '@angular/common';
import {ActivatedRoute} from '@angular/router';
import {MatButtonModule} from '@angular/material/button';
import {MatCardModule} from '@angular/material/card';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatSnackBar} from '@angular/material/snack-bar';
import {MatTooltipModule} from '@angular/material/tooltip';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {AlertePresence, CasePresence, InfirmierApiService} from '../../core/api/infirmier-api.service';
import {JOURS_SEMAINE, JourSemaine} from '../../core/api/planning-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';
import {AuthStore} from '../../core/state/auth.store';
import {openPdf} from '../stock/inventaire/inventaire.util';
import {CaseSelection, PresenceStore} from './presence.store';
import {classeStatut, trouverCase} from './presence.util';

/**
 * Planning de présence des infirmiers : pour chaque salle, créneau et jour, qui est prévu face au nombre requis par le
 * ratio de sécurité. Les créneaux en sous-effectif sont signalés à l'avance et un remplaçant peut être affecté depuis la
 * liste classée proposée pour la case ouverte.
 */
@Component({
  selector: 'app-presence-semaine',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe, SlicePipe, MatButtonModule, MatCardModule, MatIconModule, MatProgressBarModule, MatTooltipModule,
    TranslateModule,
  ],
  templateUrl: './presence-semaine.component.html',
  styleUrl: './presence-semaine.component.css',
})
export class PresenceSemaineComponent {
  protected readonly store = inject(PresenceStore);
  protected readonly jours = JOURS_SEMAINE;
  protected readonly printing = signal(false);
  protected readonly classe = classeStatut;
  protected readonly lignes = computed(() => {
    const s = this.store.semaine();
    if (!s) return [];
    return s.salles.flatMap((salle) => s.creneaux.map((creneau) => ({salle, creneau})));
  });
  protected readonly caseOuverte = computed<CasePresence | null>(() => {
    const s = this.store.semaine();
    const sel = this.store.selection();
    return s && sel ? trouverCase(s, sel.salleId, sel.creneauId, sel.jour) ?? null : null;
  });
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(InfirmierApiService);
  private readonly shell = inject(AppShellStore);
  private readonly auth = inject(AuthStore);
  protected readonly peutModifier = computed(() => this.auth.hasRole('ADMIN') || this.auth.hasRole('SECRETAIRE'));
  private readonly snackBar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);

  constructor() {
    // Recharge la semaine courante et les alertes à l'ouverture et à chaque changement de centre actif.
    effect(() => {
      this.shell.currentCenterId();
      untracked(() => {
        this.store.selectionnerCase(null);
        // `?date=yyyy-MM-dd` (lien « Remplacer » d'une absence) ouvre directement la semaine concernée
        const date = this.route.snapshot.queryParamMap.get('date');
        this.store.chargerSemaine(date && /^\d{4}-\d{2}-\d{2}$/.test(date) ? date : null);
        this.store.chargerAlertes();
      });
    });
  }

  protected cellule(salleId: string, creneauId: string, jour: JourSemaine): CasePresence | undefined {
    const s = this.store.semaine();
    return s ? trouverCase(s, salleId, creneauId, jour) : undefined;
  }

  protected jourPlanning(jour: JourSemaine) {
    return this.store.semaine()?.jours.find((j) => j.jour === jour);
  }

  protected salleNom(id: string): string {
    return this.store.semaine()?.salles.find((s) => s.id === id)?.nom ?? '';
  }

  protected creneauLibelle(id: string): string {
    return this.store.semaine()?.creneaux.find((c) => c.id === id)?.libelle ?? '';
  }

  protected estSelectionnee(c: CasePresence | undefined): boolean {
    const sel = this.store.selection();
    return !!c && !!sel && sel.date === c.date && sel.salleId === c.salleId && sel.creneauId === c.creneauId;
  }

  protected ouvrir(c: CasePresence | undefined): void {
    if (!c || c.statut === 'FERME') return;
    this.store.selectionnerCase({date: c.date, jour: c.jour, salleId: c.salleId, creneauId: c.creneauId});
  }

  protected voirAlerte(a: AlertePresence): void {
    const sel: CaseSelection = {date: a.date, jour: a.jour, salleId: a.salleId, creneauId: a.creneauId};
    const semaine = this.store.semaine();
    const dansLaSemaine = !!semaine && a.date >= semaine.debut && a.date <= semaine.fin;
    if (!dansLaSemaine) this.store.chargerSemaine(a.date);
    this.store.selectionnerCase(sel);
  }

  protected fermer(): void {
    this.store.selectionnerCase(null);
  }

  protected affecter(infirmierId: string): void {
    const remplaceId = this.caseOuverte()?.absents[0]?.infirmierId ?? null;
    this.store.affecter({infirmierId, remplaceId});
  }

  protected suivante(): void {
    this.store.changerSemaine(1);
  }

  protected precedente(): void {
    this.store.changerSemaine(-1);
  }

  protected aujourdhui(): void {
    this.store.chargerSemaine(null);
  }

  protected async imprimer(): Promise<void> {
    const centerId = this.shell.currentCenterId();
    const debut = this.store.semaine()?.debut;
    if (!centerId || !debut) return;
    this.printing.set(true);
    const error = await openPdf(this.api.imprimer(centerId, debut), `presence-infirmiers-${debut}.pdf`);
    this.printing.set(false);
    if (error !== null) {
      this.snackBar.open(error || this.translate.instant('INFIRMIER.PRESENCE.PRINT_ERROR'), 'OK', {duration: 7000});
    }
  }
}
