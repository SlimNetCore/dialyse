import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  OnDestroy,
  signal,
  untracked,
} from '@angular/core';
import {RouterLink} from '@angular/router';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {AuthStore} from '../../../core/state/auth.store';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {WebSocketService} from '../../../core/ws/websocket.service';
import {ArticleStock} from '../../../core/api/backend-api.service';
import {DropdownItem, SearchableSelectComponent} from '../../../shared/searchable-select.component';
import {RichTextEditorComponent} from '../../../shared/rich-text-editor/rich-text-editor.component';
import {QrScannerComponent} from '../../../shared/qr-scanner/qr-scanner.component';
import {SeanceStore, scanSuccessMessage} from '../state/seance.store';
import {AdministrationAnemieSeanceComponent} from '../administration-anemie/administration-anemie-seance.component';
import {PoidsSecSeanceComponent} from '../poids-sec/poids-sec-seance.component';
import {
  generatorNeedsAttention,
  initials,
  parseDecimal,
  pendingWindow,
  QUICK_ARTICLES_MAX,
  quickArticleIds,
  STATION_STEPS,
  StationStep,
  todayIsoDate,
  toNullableText,
} from './station.util';
import {PatientQrCardComponent} from '../../patient/patient-qr-card.component';
import {PendingSeancesComponent} from './pending-seances.component';
import {RecentSeancesPanelComponent} from './recent-seances-panel.component';
import {ShortcutsConfigComponent} from './shortcuts-config.component';

/** Délai d'enregistrement automatique après la dernière frappe dans une zone de texte libre. */
const AUTOSAVE_DELAY_MS = 800;

/**
 * Poste infirmier : un seul écran pour scanner, choisir le patient du jour dans la file et saisir sa séance en quatre
 * étapes (constantes, consommables, anémie, remarques). La disposition ne dépend que de la largeur disponible : une
 * colonne sur mobile (file puis séance), deux colonnes sur tablette, toutes les sections visibles sur PC.
 * Traçabilité : station → SeanceStore → BackendApiService → /api/v1/seances/** → SeanceUseCase.
 */
@Component({
  selector: 'app-seance-station',
  standalone: true,
  imports: [
    RouterLink, MatButtonModule, MatIconModule, TranslateModule, SearchableSelectComponent, RichTextEditorComponent,
    QrScannerComponent, AdministrationAnemieSeanceComponent, PoidsSecSeanceComponent, RecentSeancesPanelComponent,
    ShortcutsConfigComponent, PendingSeancesComponent, PatientQrCardComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './seance-station.component.html',
  styleUrl: './seance-station.component.css',
})
export class SeanceStationComponent implements OnDestroy {
  protected readonly store = inject(SeanceStore);
  protected readonly initials = initials;
  protected readonly steps = STATION_STEPS;
  protected readonly step = signal<StationStep>('constantes');
  /** Mobile uniquement : quel volet est affiché (la file ou la séance). */
  protected readonly view = signal<'queue' | 'seance'>('queue');
  protected readonly savedOnce = signal(false);
  protected readonly summary = computed(() => this.store.summary());
  protected readonly status = computed(() => this.summary()?.seance.status ?? null);
  protected readonly isFacturee = computed(() => this.status() === 'FACTUREE');
  protected readonly isValidated = computed(() => this.store.isSeanceAlreadyValidated());
  protected readonly isClinicalUser = computed(() => this.hasAnyRole('ADMIN', 'INFIRMIER'));
  protected readonly canScan = computed(() => this.hasAnyRole('ADMIN', 'INFIRMIER', 'SECRETAIRE'));
  protected readonly canEdit = computed(() => this.isClinicalUser() && !this.isFacturee());
  protected readonly canAdministerAnemie = computed(() => this.hasAnyRole('INFIRMIER', 'MEDECIN') && !this.isFacturee());
  protected readonly canValidate = computed(() => this.isClinicalUser() && this.status() === 'CREE');
  protected readonly scanKey = computed(() => {
    const scan = this.store.lastScan();
    return scan ? scanSuccessMessage(scan) : '';
  });
  protected readonly queue = computed(() => this.store.journalPatients());
  protected readonly doneCount = computed(() => this.queue().filter((p) => p.status !== 'CREE').length);
  protected readonly patientName = computed(() => {
    const p = this.summary()?.patient;
    return p ? `${(p.nom ?? '').trim()} ${(p.prenom ?? '').trim()}`.trim() : '';
  });
  protected readonly generatorWarning = computed(() => {
    const p = this.summary()?.patient;
    return p && generatorNeedsAttention(p.generateurEtat) ? (p.generateurEtat ?? '').trim().toUpperCase() : null;
  });
  protected readonly generatorLabel = computed(() => {
    const p = this.summary()?.patient;
    return [(p?.generateurNom ?? '').trim(), (p?.generateurMarque ?? '').trim()].filter(Boolean).join(' · ');
  });
  protected readonly forfaitLabel = computed(() => {
    const f = this.summary()?.forfait;
    return f ? (f.nom?.trim() || f.code?.trim() || '') : '';
  });
  protected readonly stepIndex = computed(() => this.steps.indexOf(this.step()));
  protected readonly isLastStep = computed(() => this.stepIndex() === this.steps.length - 1);
  /** Articles proposés en un toucher : les plus sortis aujourd'hui dans le centre, à défaut les premiers du catalogue. */
  protected readonly quickArticles = computed<ArticleStock[]>(() => {
    const active = this.store.availableArticles().filter((a) => a.active);
    const byId = new Map(active.map((a) => [a.id, a]));
    const configured = this.store.raccourcisIds().map((id) => byId.get(id)).filter((a): a is ArticleStock => !!a);
    if (configured.length > 0) return configured;
    const ranked = quickArticleIds(this.store.journalArticles()).map((id) => byId.get(id)).filter((a): a is ArticleStock => !!a);
    const rest = active.filter((a) => !ranked.includes(a));
    return [...ranked, ...rest].slice(0, QUICK_ARTICLES_MAX);
  });
  /** Seul l'administrateur choisit les raccourcis du centre. */
  protected readonly canConfigureShortcuts = computed(() => this.hasAnyRole('ADMIN'));
  protected readonly activeArticles = computed(() => this.store.availableArticles().filter((a) => a.active));
  protected readonly recentSeances = computed(() => this.store.recentSeances());
  protected readonly pendingSeances = computed(() => this.store.pendingSeances());
  /** La régularisation des séances oubliées (jours passés) est réservée à l'administrateur. */
  protected readonly isAdmin = computed(() => this.hasAnyRole('ADMIN'));
  protected readonly articleItems = computed<DropdownItem[]>(() =>
    this.store.availableArticles().filter((a) => a.active)
      .map((a) => ({id: a.id, label: `${a.code} — ${a.libelle}`}))
  );
  private readonly auth = inject(AuthStore);
  private readonly appShell = inject(AppShellStore);
  protected readonly centerId = computed(() => this.appShell.currentCenterId());
  private readonly ws = inject(WebSocketService);
  private readonly translate = inject(TranslateService);
  protected readonly todayLabel = computed(() => {
    const lang = this.translate.currentLang || this.translate.getDefaultLang() || 'fr';
    return new Intl.DateTimeFormat(lang, {weekday: 'long', day: 'numeric', month: 'long', year: 'numeric'})
      .format(new Date());
  });
  private saveTimer: ReturnType<typeof setTimeout> | null = null;
  private lastPastedAt: number | null = null;

  constructor() {
    // Chargement dès que le centre actif est connu (et à chaque changement de centre), pour tous les profils.
    effect(() => {
      const centerId = this.centerId();
      if (!centerId) return;
      untracked(() => {
        this.store.setJournalDate(todayIsoDate());
        this.reloadQueue();
        this.store.loadArticlesStock({centerId});
        this.store.loadRaccourcis({centerId});
      });
    });
    // Après un scan réussi : la séance est déjà chargée par le store ; on bascule sur elle et on rafraîchit la file.
    effect(() => {
      const scan = this.store.lastScan();
      if (!scan) return;
      untracked(() => {
        this.step.set('constantes');
        this.view.set('seance');
        this.reloadQueue();
      });
    });
    effect(() => {
      const event = this.ws.lastEvent();
      const centerId = this.centerId();
      if (!event || !centerId || event.centerId !== centerId || !event.type.startsWith('SEANCE_')) return;
      untracked(() => {
        this.reloadQueue();
        const id = this.store.selectedSeanceId();
        if (id) this.store.loadSeanceSummary({seanceId: id, centerId});
      });
    });
    // Rappel des dernières séances du patient dès qu'une séance est ouverte.
    effect(() => {
      const s = this.summary();
      const centerId = this.centerId();
      if (!s || !centerId) return;
      untracked(() => this.store.loadRecentSeances({
        centerId,
        patientId: s.seance.patientId,
        before: s.seance.dateSeance
      }));
    });
    // Code patient copié depuis la liste des patients : repris dans le champ de scan.
    effect(() => {
      const centerId = this.centerId();
      const clipboard = this.appShell.seanceScanClipboard();
      if (!centerId || !clipboard || clipboard.centerId !== centerId || clipboard.copiedAt === this.lastPastedAt) return;
      this.lastPastedAt = clipboard.copiedAt;
      untracked(() => this.store.setQrCode(clipboard.patientCode));
    });
    effect(() => {
      if (this.store.savingParamedical()) this.savedOnce.set(false);
      else if (this.store.scanMessage() === 'SEANCES.PARAMEDICAL_SAVED') this.savedOnce.set(true);
    });
  }

  ngOnDestroy(): void {
    this.flushSave();
  }

  // --- Scan ---
  protected onQrInput(event: Event): void {
    this.store.setQrCode((event.target as HTMLInputElement).value ?? '');
  }

  protected scan(value?: string): void {
    const centerId = this.centerId();
    if (value) this.store.setQrCode(value);
    const qr = this.store.qrCode().trim();
    if (!this.canScan() || !centerId || !qr) return;
    this.flushSave();
    this.store.setDateSeance(todayIsoDate());
    this.store.scanQr({centerId, qrCode: qr});
  }

  // --- File du jour ---
  protected openFromQueue(seanceId: string): void {
    const centerId = this.centerId();
    if (!centerId || !this.isClinicalUser()) return;
    this.flushSave();
    this.step.set('constantes');
    this.store.selectSeance(seanceId);
    this.store.loadSeanceSummary({seanceId, centerId});
    this.view.set('seance');
  }

  protected backToQueue(): void {
    this.flushSave();
    this.view.set('queue');
    this.reloadQueue();
  }

  // --- Étapes ---
  protected goTo(step: StationStep): void {
    this.flushSave();
    this.step.set(step);
  }

  protected next(): void {
    if (this.isLastStep()) {
      this.backToQueue();
      return;
    }
    this.goTo(this.steps[this.stepIndex() + 1]);
  }

  protected previous(): void {
    if (this.stepIndex() > 0) this.goTo(this.steps[this.stepIndex() - 1]);
  }

  // --- Constantes (enregistrement automatique à la sortie du champ) ---
  protected setDecimal(
    field: 'poidsAvantKg' | 'poidsApresKg' | 'dureeMinutes' | 'debitSangMlMin' | 'ultrafiltrationMl',
    event: Event,
  ): void {
    this.store.patchParamedical({[field]: parseDecimal((event.target as HTMLInputElement).value)});
  }

  protected setText(field: 'taAvant' | 'taApres' | 'anticoagulant' | 'typeDialysat', event: Event): void {
    this.store.patchParamedical({[field]: (event.target as HTMLInputElement).value ?? ''});
  }

  protected onRemarques(content: string): void {
    this.store.patchParamedical({incidents: content ?? ''});
    this.scheduleSave();
  }

  protected save(): void {
    this.clearTimer();
    const centerId = this.centerId();
    const seanceId = this.summary()?.seance.id;
    if (!centerId || !seanceId || !this.canEdit()) return;
    this.store.saveParamedical({
      seanceId,
      payload: {
        centerId,
        taAvant: toNullableText(this.store.taAvant()),
        taApres: toNullableText(this.store.taApres()),
        poidsAvantKg: this.store.poidsAvantKg(),
        poidsApresKg: this.store.poidsApresKg(),
        dureeMinutes: this.store.dureeMinutes(),
        debitSangMlMin: this.store.debitSangMlMin(),
        ultrafiltrationMl: this.store.ultrafiltrationMl(),
        anticoagulant: toNullableText(this.store.anticoagulant()),
        typeDialysat: toNullableText(this.store.typeDialysat()),
        incidents: toNullableText(this.store.incidents()),
      },
    });
  }

  // --- Consommables ---
  protected addOne(articleId: string): void {
    const article = this.store.availableArticles().find((a) => a.id === articleId);
    const centerId = this.centerId();
    const seanceId = this.summary()?.seance.id;
    if (!article || !this.canEdit() || !centerId || !seanceId) return;
    if (this.isValidated()) {
      this.store.addConsommableToSeance({seanceId, centerId, articleId, quantite: 1});
    } else {
      this.store.addConsommable(article, 1);
    }
  }

  // --- Raccourcis de consommables du centre (administrateur) ---
  protected saveShortcuts(articleIds: string[]): void {
    const centerId = this.centerId();
    if (!centerId || !this.canConfigureShortcuts()) return;
    this.store.saveRaccourcis({centerId, articleIds});
  }

  protected onOtherArticle(item: DropdownItem | null): void {
    if (item?.id) this.addOne(item.id);
  }

  protected decrease(articleId: string, quantite: number): void {
    if (!this.canEdit()) return;
    if (!this.isValidated()) {
      this.store.adjustConsommable(articleId, -1);
      return;
    }
    if (quantite <= 1) {
      this.remove(articleId);
      return;
    }
    const ctx = this.validatedContext();
    if (ctx) this.store.updateConsommableQuantiteInSeance({...ctx, articleId, quantite: quantite - 1});
  }

  protected remove(articleId: string): void {
    if (!this.canEdit()) return;
    if (!this.isValidated()) {
      this.store.removeConsommable(articleId);
      return;
    }
    const ctx = this.validatedContext();
    if (ctx) this.store.removeConsommableFromSeance({...ctx, articleId});
  }

  protected articleLabel(articleId: string, fallback?: string | null): string {
    const a = this.store.availableArticles().find((x) => x.id === articleId);
    return a ? `${a.code} — ${a.libelle}` : (fallback ?? articleId);
  }

  // --- Validation (séance encore à l'état CREE, ex. créée par la secrétaire) ---
  protected validate(): void {
    const centerId = this.centerId();
    const seanceId = this.summary()?.seance.id;
    const userId = this.auth.username();
    if (!centerId || !seanceId || !userId || !this.canValidate()) return;
    this.flushSave();
    this.store.validateSeance({
      seanceId,
      payload: {
        centerId, userId,
        consommations: this.store.consommables().map((c) => ({articleId: c.articleId, quantite: c.quantite})),
      },
    });
    this.store.loadSeanceSummary({seanceId, centerId});
    this.reloadQueue();
  }

  protected statusKey(status: string): string {
    return `SEANCES.STATION.STATUS_${status}`;
  }

  private validatedContext(): { seanceId: string; centerId: string; userId: string } | null {
    const centerId = this.centerId();
    const seanceId = this.summary()?.seance.id;
    const userId = this.auth.username();
    return centerId && seanceId && userId ? {seanceId, centerId, userId} : null;
  }

  private reloadQueue(): void {
    const centerId = this.centerId();
    if (!centerId) return;
    this.store.loadJournal({centerId, date: todayIsoDate()});
    if (this.isAdmin()) this.store.loadPendingSeances({centerId, ...pendingWindow()});
  }

  private scheduleSave(): void {
    this.clearTimer();
    this.saveTimer = setTimeout(() => this.save(), AUTOSAVE_DELAY_MS);
  }

  private flushSave(): void {
    if (this.saveTimer !== null) this.save();
  }

  private clearTimer(): void {
    if (this.saveTimer !== null) {
      clearTimeout(this.saveTimer);
      this.saveTimer = null;
    }
  }

  private hasAnyRole(...roles: string[]): boolean {
    return roles.some((role) => this.auth.hasRole(role));
  }
}
