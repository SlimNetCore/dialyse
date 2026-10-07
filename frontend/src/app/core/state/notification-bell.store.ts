import {computed, effect, inject, untracked} from '@angular/core';
import {patchState, signalStore, withComputed, withHooks, withMethods, withState} from '@ngrx/signals';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {firstValueFrom} from 'rxjs';
import {AlerteServeur, NotificationApiService} from '../api/notification-api.service';
import {WebSocketService, WsEvent} from '../ws/websocket.service';
import {AuthStore} from './auth.store';

type NotificationBellState = {
  open: boolean;
  activeTab: 'unread' | 'read';
  selectedEventId: string | null;
  readMap: Record<string, true>;
  /** Alertes durables du journal du centre : celles survenues avant la connexion (replanification de nuit, panne). */
  persistees: WsEvent[];
};

const initialState: NotificationBellState = {
  open: false,
  activeTab: 'unread',
  selectedEventId: null,
  readMap: {},
  persistees: [],
};

/** Identifiant d'une alerte : celui du journal si elle en a un, sinon dérivé de son type et de son horodatage. */
export const eventId = (evt: WsEvent): string =>
  evt.id ?? `${evt.type}-${evt.timestamp}-${evt.payload['pecId'] ?? evt.payload['patientCode'] ?? ''}`;

const enEvenement = (a: AlerteServeur): WsEvent =>
  ({id: a.id, type: a.type, centerId: a.centerId, payload: a.payload, timestamp: a.timestamp});

export const NotificationBellStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('NotificationBellStore'),
  withComputed((store) => {
    const ws = inject(WebSocketService);
    const auth = inject(AuthStore);

    const isTargetedToCurrentUser = (evt: WsEvent): boolean => {
      const rawTargetRoles = (evt.payload['targetRoles'] ?? '').trim();
      if (!rawTargetRoles) {
        return true;
      }
      const targetRoles = rawTargetRoles
        .split(',')
        .map((role) => role.trim())
        .filter((role) => role.length > 0);
      if (targetRoles.length === 0) {
        return true;
      }
      return targetRoles.some((role) => auth.hasRole(role));
    };

    const isRead = (evt: WsEvent): boolean => !!store.readMap()[eventId(evt)];

    /** Temps réel et journal réunis (une alerte reçue en direct puis relue au journal n'apparaît qu'une fois). */
    const tous = computed(() => {
      const vus = new Set<string>();
      const reunis: WsEvent[] = [];
      for (const evt of [...ws.events(), ...store.persistees()]) {
        if (vus.has(eventId(evt))) continue;
        vus.add(eventId(evt));
        reunis.push(evt);
      }
      return reunis.sort((a, b) => Date.parse(b.timestamp) - Date.parse(a.timestamp));
    });
    const visibleByRoleEvents = computed(() => tous().filter((e) => isTargetedToCurrentUser(e)));

    const unreadEvents = computed(() => visibleByRoleEvents().filter((e) => !isRead(e)));
    const readEvents = computed(() => visibleByRoleEvents().filter((e) => isRead(e)));
    const visibleEvents = computed(() => store.activeTab() === 'unread' ? unreadEvents() : readEvents());
    const selectedEvent = computed(() => {
      const id = store.selectedEventId();
      if (!id) return null;
      return visibleByRoleEvents().find((e) => eventId(e) === id) ?? null;
    });

    return {
      unreadEvents,
      readEvents,
      visibleEvents,
      selectedEvent,
      unreadCount: computed(() => unreadEvents().length),
      tous: visibleByRoleEvents,
    };
  }),
  withMethods((store) => {
    const api = inject(NotificationApiService);
    const auth = inject(AuthStore);

    /** Les erreurs du journal ne gênent jamais : le temps réel continue de fonctionner. */
    const silencieux = (promesse: Promise<unknown>): void => {
      promesse.catch(() => undefined);
    };

    return {
      eventId,

      isRead(evt: WsEvent): boolean {
        return !!store.readMap()[eventId(evt)];
      },

      /** Recharge les alertes durables du centre actif (appelé à la connexion et à chaque changement de centre). */
      async charger(): Promise<void> {
        const centerId = auth.centerId();
        if (!centerId) return;
        try {
          const page = await firstValueFrom(api.liste(centerId, 0, 50));
          const alertes = page.items ?? [];
          const lues: Record<string, true> = {};
          alertes.filter((a) => a.lue).forEach((a) => {
            lues[a.id] = true;
          });
          patchState(store, {persistees: alertes.map(enEvenement), readMap: {...store.readMap(), ...lues}});
        } catch {
          patchState(store, {persistees: []});
        }
      },

      setActiveTab(tab: 'unread' | 'read'): void {
        patchState(store, {activeTab: tab});
      },

      togglePanel(): void {
        const next = !store.open();
        patchState(store, {open: next});
        if (!next) return;

        const first = store.visibleEvents()[0] ?? store.tous()[0] ?? null;
        if (first) {
          patchState(store, {selectedEventId: eventId(first)});
        }
      },

      closePanel(): void {
        patchState(store, {open: false});
      },

      selectMessage(evt: WsEvent): void {
        const id = eventId(evt);
        const dejaLue = !!store.readMap()[id];
        patchState(store, {
          selectedEventId: id,
          readMap: {
            ...store.readMap(),
            [id]: true
          }
        });
        const centerId = auth.centerId();
        if (evt.id && !dejaLue && centerId) silencieux(firstValueFrom(api.marquerLues(centerId, [evt.id])));
      },

      markAllRead(): void {
        const next = {...store.readMap()};
        store.tous().forEach((evt) => {
          next[eventId(evt)] = true;
        });
        patchState(store, {readMap: next});
        const centerId = auth.centerId();
        if (centerId) silencieux(firstValueFrom(api.toutMarquerLu(centerId)));
      }
    };
  }),
  withHooks({
    onInit(store) {
      const auth = inject(AuthStore);
      // Les alertes durables sont relues à la connexion, puis quand le centre actif change.
      effect(() => {
        if (auth.centerId()) untracked(() => void store.charger());
      });
    },
  }),
);
