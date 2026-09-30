import {ChangeDetectionStrategy, Component, effect, inject, untracked} from '@angular/core';
import {DatePipe} from '@angular/common';
import {RouterLink} from '@angular/router';
import {MatIconModule} from '@angular/material/icon';
import {MatButtonModule} from '@angular/material/button';
import {TranslateModule} from '@ngx-translate/core';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {WebSocketService} from '../../../core/ws/websocket.service';
import {InventaireStore} from './state/inventaire.store';

/**
 * Bandeau affiché sur tous les écrans du stock pendant un inventaire : les mouvements sont suspendus.
 * Se met à jour en temps réel (événement STOCK_INVENTORY_CHANGED).
 */
@Component({
  selector: 'app-inventaire-banner',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, MatIconModule, MatButtonModule, TranslateModule],
  template: `
    @if (store.etat()?.mouvementsBloques) {
      @let etat = store.etat()!;
      <div class="freeze-banner" role="status" data-testid="inventory-freeze-banner">
        <span class="pulse" aria-hidden="true"><mat-icon>lock_clock</mat-icon></span>
        <div class="text">
          <strong>{{ 'STOCK.INVENTORY.BANNER_TITLE' | translate: {ref: etat.inventaireEnCoursReference} }}</strong>
          <span>{{
              'STOCK.INVENTORY.BANNER_TEXT' | translate: {
                date: (etat.inventaireEnCoursDate | date: 'dd/MM/yyyy'),
                user: etat.ouvertPar ?? '-'
              }
            }}</span>
        </div>
        <a mat-flat-button class="cta" [routerLink]="['/stock/inventaires', etat.inventaireEnCoursId]">
          <mat-icon>checklist</mat-icon>
          {{ 'STOCK.INVENTORY.GO_TO_COUNT' | translate }}
        </a>
      </div>
    }
  `,
  styles: [`
    .freeze-banner {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 14px;
      margin: 0 0 16px;
      padding: 12px 18px;
      border-radius: 16px;
      color: #5d3a00;
      background: linear-gradient(120deg, #fff4d6 0%, #ffe3a3 100%);
      border: 1px solid #f5c35b;
      box-shadow: 0 6px 18px rgba(245, 158, 11, 0.18);
    }

    .pulse {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 40px;
      height: 40px;
      border-radius: 50%;
      background: #f59e0b;
      color: #fff;
      animation: pulse 2s ease-in-out infinite;
    }

    @keyframes pulse {
      0%, 100% { box-shadow: 0 0 0 0 rgba(245, 158, 11, 0.45); }
      50% { box-shadow: 0 0 0 10px rgba(245, 158, 11, 0); }
    }

    .text {
      display: flex;
      flex-direction: column;
      gap: 2px;
      flex: 1 1 260px;
      min-width: 0;
    }

    .cta {
      --mdc-filled-button-container-color: #b45309;
      --mdc-filled-button-label-text-color: #fff;
    }

    @media (prefers-reduced-motion: reduce) {
      .pulse { animation: none; }
    }

    @media (max-width: 767px) {
      .cta { width: 100%; }
    }
  `],
})
export class InventaireBannerComponent {
  protected readonly store = inject(InventaireStore);
  private readonly appShell = inject(AppShellStore);
  private readonly ws = inject(WebSocketService);

  constructor() {
    effect(() => {
      if (!this.appShell.currentCenterId()) return;
      untracked(() => void this.store.refreshEtat());
    });
    effect(() => {
      const evt = this.ws.lastEvent();
      if (evt?.type === 'STOCK_INVENTORY_CHANGED') untracked(() => void this.store.refreshEtat());
    });
  }
}

