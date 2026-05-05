import {computed, inject} from '@angular/core';
import {patchState, signalStore, withComputed, withMethods, withState} from '@ngrx/signals';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {WebSocketService, WsEvent} from '../ws/websocket.service';

type NotificationBellState = {
  open: boolean;
  activeTab: 'unread' | 'read';
  selectedEventId: string | null;
  readMap: Record<string, true>;
};

const initialState: NotificationBellState = {
  open: false,
  activeTab: 'unread',
  selectedEventId: null,
  readMap: {}
};

export const NotificationBellStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('NotificationBellStore'),
  withComputed((store) => {
    const ws = inject(WebSocketService);

    const eventId = (evt: WsEvent): string =>
      `${evt.type}-${evt.timestamp}-${evt.payload['pecId'] ?? evt.payload['patientCode'] ?? ''}`;

    const isRead = (evt: WsEvent): boolean => !!store.readMap()[eventId(evt)];

    const unreadEvents = computed(() => ws.events().filter((e) => !isRead(e)));
    const readEvents = computed(() => ws.events().filter((e) => isRead(e)));
    const visibleEvents = computed(() => store.activeTab() === 'unread' ? unreadEvents() : readEvents());
    const selectedEvent = computed(() => {
      const id = store.selectedEventId();
      if (!id) return null;
      return ws.events().find((e) => eventId(e) === id) ?? null;
    });

    return {
      unreadEvents,
      readEvents,
      visibleEvents,
      selectedEvent,
      unreadCount: computed(() => unreadEvents().length)
    };
  }),
  withMethods((store) => {
    const ws = inject(WebSocketService);

    const eventId = (evt: WsEvent): string =>
      `${evt.type}-${evt.timestamp}-${evt.payload['pecId'] ?? evt.payload['patientCode'] ?? ''}`;

    return {
      eventId,

      isRead(evt: WsEvent): boolean {
        return !!store.readMap()[eventId(evt)];
      },

      setActiveTab(tab: 'unread' | 'read'): void {
        patchState(store, {activeTab: tab});
      },

      togglePanel(): void {
        const next = !store.open();
        patchState(store, {open: next});
        if (!next) return;

        const first = store.visibleEvents()[0] ?? ws.events()[0] ?? null;
        if (first) {
          patchState(store, {selectedEventId: eventId(first)});
        }
      },

      closePanel(): void {
        patchState(store, {open: false});
      },

      selectMessage(evt: WsEvent): void {
        const id = eventId(evt);
        patchState(store, {
          selectedEventId: id,
          readMap: {
            ...store.readMap(),
            [id]: true
          }
        });
      },

      markAllRead(): void {
        const next = {...store.readMap()};
        ws.events().forEach((evt) => {
          next[eventId(evt)] = true;
        });
        patchState(store, {readMap: next});
      }
    };
  })
);

