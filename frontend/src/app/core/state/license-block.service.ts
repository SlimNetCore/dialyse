import {Injectable, signal} from '@angular/core';

/** Holds the reason for the last 402 (license required) response, read by the blocking screen. */
@Injectable({providedIn: 'root'})
export class LicenseBlockService {
  readonly reason = signal<string | null>(null);

  setReason(reason: string | null): void {
    this.reason.set(reason);
  }
}
