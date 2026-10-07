import {describe, expect, it} from 'vitest';
import {WsEvent} from '../../core/ws/websocket.service';
import {prescriptionChangee} from './prescription-realtime.util';

const evt = (type: string, centerId: string, patientId: string): WsEvent =>
  ({type, centerId, payload: {patientId}, timestamp: '2026-10-07T10:00:00Z'}) as WsEvent;

describe('prescriptionChangee', () => {
  it('reconnaît un changement de prescription du patient et du centre affichés', () => {
    expect(prescriptionChangee(evt('PRESCRIPTION_CHANGED', 'c1', 'p1'), 'c1', 'p1')).toBe(true);
  });

  it('ignore un autre patient, un autre centre ou un autre type d\'évènement', () => {
    expect(prescriptionChangee(evt('PRESCRIPTION_CHANGED', 'c1', 'p2'), 'c1', 'p1')).toBe(false);
    expect(prescriptionChangee(evt('PRESCRIPTION_CHANGED', 'c2', 'p1'), 'c1', 'p1')).toBe(false);
    expect(prescriptionChangee(evt('PATIENT_UPDATED', 'c1', 'p1'), 'c1', 'p1')).toBe(false);
  });

  it('ignore l\'absence d\'évènement, de centre ou de patient', () => {
    expect(prescriptionChangee(null, 'c1', 'p1')).toBe(false);
    expect(prescriptionChangee(evt('PRESCRIPTION_CHANGED', 'c1', 'p1'), null, 'p1')).toBe(false);
    expect(prescriptionChangee(evt('PRESCRIPTION_CHANGED', 'c1', 'p1'), 'c1', null)).toBe(false);
  });
});
