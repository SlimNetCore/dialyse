import {effect, Signal} from '@angular/core';

export type WizardActionStatus = {
  id: number;
  action: string | null;
  success: boolean | null;
  error: string | null;
  message: string | null;
  meta: Record<string, any> | null;
};

type WizardActionStoreSignals = {
  lastActionId: Signal<number>;
  lastAction: Signal<string | null>;
  lastSuccess: Signal<boolean | null>;
  lastError: Signal<string | null>;
  lastMessage: Signal<string | null>;
  lastActionMeta: Signal<Record<string, any> | null>;
};

export function consumeWizardActionStatus(
  store: WizardActionStoreSignals,
  onAction: (status: WizardActionStatus) => void
): void {
  let handledActionId = 0;

  effect(() => {
    const id = store.lastActionId();
    if (!id || id === handledActionId) return;

    handledActionId = id;
    onAction({
      id,
      action: store.lastAction(),
      success: store.lastSuccess(),
      error: store.lastError(),
      message: store.lastMessage(),
      meta: store.lastActionMeta()
    });
  });
}

