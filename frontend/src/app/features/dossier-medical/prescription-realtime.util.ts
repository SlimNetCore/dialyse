import {WsEvent} from '../../core/ws/websocket.service';

/** Évènement temps réel émis par le serveur quand la prescription d'un patient est créée, modifiée ou supprimée. */
export const PRESCRIPTION_CHANGED = 'PRESCRIPTION_CHANGED';

/**
 * Vrai quand l'évènement signale un changement de prescription du patient et du centre affichés : l'écran doit alors
 * relire ses données. Un autre patient ou un autre centre n'a aucune raison de provoquer un rechargement.
 */
export function prescriptionChangee(
  evenement: WsEvent | null, centerId: string | null, patientId: string | null,
): boolean {
  return !!evenement && !!centerId && !!patientId
    && evenement.type === PRESCRIPTION_CHANGED
    && evenement.centerId === centerId
    && evenement.payload['patientId'] === patientId;
}
