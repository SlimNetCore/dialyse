import {ChangeDetectionStrategy, Component, computed, inject} from '@angular/core';
import {MatIconModule} from '@angular/material/icon';
import {MatButtonModule} from '@angular/material/button';
import {MatBadgeModule} from '@angular/material/badge';
import {MatTooltipModule} from '@angular/material/tooltip';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {WebSocketService, WsEvent} from '../ws/websocket.service';
import {DatePipe, JsonPipe} from '@angular/common';
import {NotificationBellStore} from '../state/notification-bell.store';
import {AuthStore} from '../state/auth.store';
import {RouterLink} from '@angular/router';

@Component({
  selector: 'app-notification-bell',
  standalone: true,
  imports: [
    RouterLink,
    MatIconModule,
    MatButtonModule,
    MatBadgeModule,
    MatTooltipModule,
    TranslateModule,
    DatePipe,
    JsonPipe,
  ],
  templateUrl: './notification-bell.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './notification-bell.component.css',
})
export class NotificationBellComponent {
  readonly ws = inject(WebSocketService);
  private readonly notifStore = inject(NotificationBellStore);
  private readonly translate = inject(TranslateService);
  private readonly auth = inject(AuthStore);

  readonly open = this.notifStore.open;
  readonly activeTab = this.notifStore.activeTab;
  readonly selectedEventId = this.notifStore.selectedEventId;
  readonly unreadEvents = this.notifStore.unreadEvents;
  readonly readEvents = this.notifStore.readEvents;
  readonly visibleEvents = this.notifStore.visibleEvents;
  readonly selectedEvent = this.notifStore.selectedEvent;
  readonly unreadCount = this.notifStore.unreadCount;
  readonly wsTooltip = computed(() => {
    switch (this.ws.connectionStatus()) {
      case 'stable':
        return this.translate.instant('NOTIFICATION.WS_STABLE');
      case 'interrupted':
        return this.translate.instant('NOTIFICATION.WS_INTERRUPTED');
      case 'impossible':
        return this.translate.instant('NOTIFICATION.WS_IMPOSSIBLE');
    }
  });

  togglePanel(): void {
    this.notifStore.togglePanel();
  }

  closePanel(): void {
    this.notifStore.closePanel();
  }

  setActiveTab(tab: 'unread' | 'read'): void {
    this.notifStore.setActiveTab(tab);
  }

  eventId(evt: WsEvent): string {
    return this.notifStore.eventId(evt);
  }

  isRead(evt: WsEvent): boolean {
    return this.notifStore.isRead(evt);
  }

  selectMessage(evt: WsEvent): void {
    this.notifStore.selectMessage(evt);
  }

  markAllRead(): void {
    this.notifStore.markAllRead();
  }

  iconFor(evt: WsEvent): string {
    switch (evt.type) {
      case 'PATIENT_CREATED':
        return 'person_add';
      case 'PEC_VALIDATED':
        return 'verified';
      case 'PEC_CLOSED':
        return 'event_busy';
      case 'INFIRMIER_SOUS_EFFECTIF':
        return 'groups';
      case 'INFIRMIER_ABSENCE_DECLAREE':
      case 'INFIRMIER_ABSENCE_ENREGISTREE':
      case 'ABSENCES_A_QUALIFIER':
        return 'event_busy';
      case 'GENERATEUR_INDISPONIBLE':
        return 'build_circle';
      case 'OBSERVANCE_NON_RESPECTEE':
        return 'vaccines';
      case 'INFIRMIER_SUREFFECTIF':
        return 'person_off';
      case 'SEANCES_DEPLACEES':
        return 'swap_horiz';
      case 'PATIENT_REPLACE_ISOLEMENT':
      case 'ISOLEMENT_IMPOSSIBLE':
        return 'masks';
      case 'SAISIE_INFIRMIER':
        return 'medical_services';
      case 'SEANCES_A_REGULARISER':
        return 'pending_actions';
      case 'SEANCE_DEVERROUILLEE':
        return 'lock_open';
      case 'OPTIMISATION_PROPOSITION':
        return 'auto_fix_high';
      case 'SEANCE_SUPPRIMEE':
        return 'delete';
      default:
        return 'info';
    }
  }

  iconClass(evt: WsEvent): string {
    switch (evt.type) {
      case 'PATIENT_CREATED':
        return 'patient';
      case 'PEC_VALIDATED':
        return 'pec';
      case 'PEC_CLOSED':
      case 'INFIRMIER_SOUS_EFFECTIF':
      case 'INFIRMIER_ABSENCE_DECLAREE':
      case 'INFIRMIER_ABSENCE_ENREGISTREE':
      case 'ABSENCES_A_QUALIFIER':
      case 'SEANCES_A_REGULARISER':
      case 'PATIENT_REPLACE_ISOLEMENT':
      case 'ISOLEMENT_IMPOSSIBLE':
      case 'GENERATEUR_INDISPONIBLE':
      case 'INFIRMIER_SUREFFECTIF':
      case 'OBSERVANCE_NON_RESPECTEE':
        return 'warning';
      case 'OPTIMISATION_PROPOSITION':
        return evt.payload['motif'] === 'GAIN' ? 'pec' : 'warning';
      default:
        return '';
    }
  }

  /**
   * Écran où traiter l'alerte : l'optimisation du bon périmètre (ou la proposition elle-même), ou le planning. Réservé
   * aux profils qui peuvent lancer l'optimisation ; le médecin n'a que le planning.
   */
  lienFor(evt: WsEvent): { label: string; commands: string[]; queryParams: Record<string, string> } | null {
    const peutOptimiser = this.auth.hasRole('ADMIN') || this.auth.hasRole('SECRETAIRE');
    switch (evt.type) {
      case 'OPTIMISATION_PROPOSITION':
        return peutOptimiser && evt.payload['runId']
          ? {
            label: 'NOTIFICATION.ACTION.PROPOSITION', commands: ['/seances/optimisation'],
            queryParams: {run: evt.payload['runId']}
          }
          : null;
      case 'GENERATEUR_INDISPONIBLE':
        return peutOptimiser
          ? {
            label: 'NOTIFICATION.ACTION.MAINTENANCE', commands: ['/seances/optimisation'],
            queryParams: {perimetre: 'MAINTENANCE'}
          }
          : null;
      case 'INFIRMIER_SOUS_EFFECTIF':
      case 'INFIRMIER_ABSENCE_DECLAREE':
      case 'INFIRMIER_ABSENCE_ENREGISTREE':
        return peutOptimiser
          ? {
            label: 'NOTIFICATION.ACTION.COUVERTURE', commands: ['/seances/optimisation'],
            queryParams: {perimetre: 'COUVERTURE'}
          }
          : null;
      case 'OBSERVANCE_NON_RESPECTEE':
        return evt.payload['patientId'] && (this.auth.hasRole('MEDECIN') || this.auth.hasRole('ADMIN'))
          ? {
            label: 'NOTIFICATION.ACTION.ANEMIE',
            commands: ['/patients', evt.payload['patientId'], 'dossier-medical', 'anemie'], queryParams: {}
          }
          : null;
      case 'INFIRMIER_SUREFFECTIF':
        return peutOptimiser
          ? {
            label: 'NOTIFICATION.ACTION.ROULEMENT', commands: ['/seances/optimisation'],
            queryParams: {perimetre: 'ROULEMENT'}
          }
          : null;
      case 'SEANCES_DEPLACEES':
        return {
          label: 'NOTIFICATION.ACTION.PLANNING', commands: [peutOptimiser ? '/seances/planning' : '/medecin'],
          queryParams: {}
        };
      default:
        return null;
    }
  }

  textFor(evt: WsEvent): string {
    switch (evt.type) {
      case 'PATIENT_CREATED': {
        const nomComplet = `${evt.payload['nom'] ?? ''} ${evt.payload['prenom'] ?? ''}`.trim();
        return this.translate.instant('NOTIFICATION.PATIENT_CREATED', {nom: nomComplet});
      }
      case 'PEC_VALIDATED':
        return this.translate.instant('NOTIFICATION.PEC_VALIDATED', {
          nom: evt.payload['patientNom'] ?? '',
        });
      case 'PEC_CLOSED':
        return this.translate.instant('NOTIFICATION.PEC_CLOSED', {
          nom: evt.payload['patientNom'] ?? '',
        });
      case 'INFIRMIER_ABSENCE_DECLAREE':
        return this.translate.instant('NOTIFICATION.INFIRMIER_ABSENCE_DECLAREE', {
          infirmier: evt.payload['infirmier'] ?? '',
          debut: evt.payload['debut'] ?? '',
          fin: evt.payload['fin'] ?? '',
        });
      case 'INFIRMIER_ABSENCE_ENREGISTREE':
        return this.translate.instant('NOTIFICATION.INFIRMIER_ABSENCE_ENREGISTREE', {
          infirmier: evt.payload['infirmier'] ?? '',
          debut: evt.payload['debut'] ?? '',
          fin: evt.payload['fin'] ?? '',
        });
      case 'GENERATEUR_INDISPONIBLE':
        return this.translate.instant('NOTIFICATION.GENERATEUR_INDISPONIBLE', {
          generateur: evt.payload['generateur'] ?? '',
          statut: this.translate.instant(`GMAO.STATUT_EQUIPEMENT.${evt.payload['statut'] ?? ''}`),
          count: evt.payload['nbPatients'] ?? '0',
          patients: evt.payload['patients'] ?? '',
        });
      case 'OBSERVANCE_NON_RESPECTEE': {
        const nature = evt.payload['typeAlerte'];
        // alertes d'avant le détail : texte d'origine du serveur
        if (!nature) return evt.payload['message'] || evt.type;
        const unite = evt.payload['unite'] ?? '';
        const attendu = Number(evt.payload['attendu'] ?? 0);
        const administre = Number(evt.payload['administre'] ?? 0);
        return this.translate.instant(
          `NOTIFICATION.OBSERVANCE_NON_RESPECTEE.${unite ? 'DOSE' : 'COMPTE'}_${nature}`, {
            traitement: this.translate.instant(
              `DOSSIER_MEDICAL.ALERTE_OBSERVANCE_EXPLICATION.TRAITEMENT.${evt.payload['typeTraitement'] ?? 'EPO'}`),
            attendu, administre, manque: Math.max(0, attendu - administre), unite,
            debut: evt.payload['debut'] ?? '', fin: evt.payload['fin'] ?? '',
          });
      }
      case 'INFIRMIER_SUREFFECTIF':
        return this.translate.instant('NOTIFICATION.INFIRMIER_SUREFFECTIF', {
          count: evt.payload['nbCreneaux'] ?? '0',
          date: evt.payload['premiereDate'] ?? '',
          vacations: evt.payload['nbVacations'] ?? '0',
          heures: evt.payload['heures'] ?? '0',
        });
      case 'SEANCES_DEPLACEES':
        return this.translate.instant('NOTIFICATION.SEANCES_DEPLACEES', {
          patients: evt.payload['nbPatients'] ?? '0',
          temporaires: evt.payload['nbSeancesTemporaires'] ?? '0',
        });
      case 'PATIENT_REPLACE_ISOLEMENT':
        return this.translate.instant('NOTIFICATION.PATIENT_REPLACE_ISOLEMENT', {
          nom: evt.payload['patientNom'] ?? '',
          salle: evt.payload['salle'] ?? '',
        });
      case 'ISOLEMENT_IMPOSSIBLE':
        return this.translate.instant('NOTIFICATION.ISOLEMENT_IMPOSSIBLE', {nom: evt.payload['patientNom'] ?? ''});
      case 'ABSENCES_A_QUALIFIER':
        return this.translate.instant('NOTIFICATION.ABSENCES_A_QUALIFIER', {
          count: evt.payload['nbAQualifier'] ?? '',
          late: evt.payload['nbEnRetard'] ?? '',
        });
      case 'SEANCES_A_REGULARISER':
        return this.translate.instant('NOTIFICATION.SEANCES_A_REGULARISER', {
          count: evt.payload['nbSeances'] ?? '',
          date: evt.payload['plusAncienne'] ?? '',
        });
      case 'SEANCE_DEVERROUILLEE':
        return this.translate.instant('NOTIFICATION.SEANCE_DEVERROUILLEE', {
          nom: `${evt.payload['patientNom'] ?? ''} ${evt.payload['patientPrenom'] ?? ''}`.trim(),
          date: evt.payload['dateSeance'] ?? '',
        });
      case 'INFIRMIER_SOUS_EFFECTIF':
        return this.translate.instant('NOTIFICATION.INFIRMIER_SOUS_EFFECTIF', {
          count: evt.payload['nbCreneaux'] ?? '',
          date: evt.payload['premiereDate'] ?? '',
        });
      case 'SEANCE_SUPPRIMEE':
        return this.translate.instant('NOTIFICATION.SEANCE_SUPPRIMEE', {date: evt.payload['dateSeance'] ?? ''});
      case 'OPTIMISATION_PROPOSITION':
        return this.translate.instant(`NOTIFICATION.OPTIMISATION_PROPOSITION.${evt.payload['motif'] ?? 'GAIN'}`, {
          n: evt.payload['valeur'] ?? '',
        });
      case 'SAISIE_INFIRMIER':
        return this.translate.instant(`NOTIFICATION.SAISIE.${evt.payload['saisie'] ?? 'DEFAULT'}`, {
          nom: `${evt.payload['patientNom'] ?? ''} ${evt.payload['patientPrenom'] ?? ''}`.trim(),
          auteur: evt.payload['auteur'] ?? '',
          date: evt.payload['date'] ?? '',
        });
      default:
        return evt.type;
    }
  }
}
