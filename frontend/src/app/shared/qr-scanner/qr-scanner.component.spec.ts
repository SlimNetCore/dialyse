import {TestBed} from '@angular/core/testing';
import {provideZonelessChangeDetection} from '@angular/core';
import {MatSnackBar} from '@angular/material/snack-bar';
import {TranslateModule} from '@ngx-translate/core';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {QR_DECODER, QrScannerComponent} from './qr-scanner.component';

describe('QrScannerComponent', () => {
  const snackBar = {open: vi.fn()};
  /** Décodeur injecté à la place de jsQR : aucune dépendance à un mock de module. */
  const decoder = vi.fn();

  beforeEach(() => {
    snackBar.open.mockClear();
    decoder.mockReset();
    TestBed.configureTestingModule({
      imports: [QrScannerComponent, TranslateModule.forRoot()],
      providers: [
        provideZonelessChangeDetection(),
        {provide: MatSnackBar, useValue: snackBar},
        {provide: QR_DECODER, useValue: decoder},
      ],
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    delete (window as unknown as { BarcodeDetector?: unknown }).BarcodeDetector;
  });

  function create() {
    const fixture = TestBed.createComponent(QrScannerComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('explique que la caméra exige le HTTPS quand la page n\'est pas sécurisée', async () => {
    vi.stubGlobal('isSecureContext', false);
    const fixture = create();

    await fixture.componentInstance['startCamera']();

    expect(snackBar.open).toHaveBeenCalledWith('SEANCES.CAMERA_INSECURE_CONTEXT', expect.anything(), expect.anything());
    expect(fixture.componentInstance['cameraActive']()).toBe(false);
  });

  it('signale une caméra non prise en charge quand getUserMedia est absent', async () => {
    vi.stubGlobal('isSecureContext', true);
    vi.stubGlobal('navigator', {});
    const fixture = create();

    await fixture.componentInstance['startCamera']();

    expect(snackBar.open).toHaveBeenCalledWith('SEANCES.CAMERA_NOT_SUPPORTED', expect.anything(), expect.anything());
  });

  it('ne dépend plus de BarcodeDetector : la caméra démarre et le flux est demandé', async () => {
    vi.stubGlobal('isSecureContext', true);
    const getUserMedia = vi.fn().mockRejectedValue(new Error('denied'));
    vi.stubGlobal('navigator', {mediaDevices: {getUserMedia}});
    const fixture = create();

    await fixture.componentInstance['startCamera']();

    expect(getUserMedia).toHaveBeenCalled();
    expect(snackBar.open).toHaveBeenCalledWith('SEANCES.CAMERA_ACCESS_DENIED', expect.anything(), expect.anything());
  });

  it('lit une image avec jsQR quand BarcodeDetector est absent et émet la valeur lue', async () => {
    decoder.mockReturnValue({data: ' PAT:IMG-001 '});
    const fixture = create();
    const component = fixture.componentInstance as any;
    component['loadImage'] = vi.fn().mockResolvedValue({naturalWidth: 4, naturalHeight: 4});
    const getContext = vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({
      drawImage: vi.fn(), getImageData: vi.fn().mockReturnValue({data: new Uint8ClampedArray(64)}),
    } as unknown as CanvasRenderingContext2D);
    const scanned: string[] = [];
    fixture.componentInstance.scanned.subscribe((v: string) => scanned.push(v));

    await component['scanImage'](new File(['qr'], 'qr.png', {type: 'image/png'}));

    expect(scanned).toEqual(['PAT:IMG-001']);
    expect(decoder).toHaveBeenCalledWith(expect.anything(), 4, 4, {inversionAttempts: 'attemptBoth'});
    getContext.mockRestore();
  });

  it('prévient quand aucun QR n\'est détecté dans l\'image', async () => {
    decoder.mockReturnValue(null);
    const fixture = create();
    const component = fixture.componentInstance as any;
    component['loadImage'] = vi.fn().mockResolvedValue({naturalWidth: 4, naturalHeight: 4});
    const getContext = vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({
      drawImage: vi.fn(), getImageData: vi.fn().mockReturnValue({data: new Uint8ClampedArray(64)}),
    } as unknown as CanvasRenderingContext2D);

    await component['scanImage'](new File(['qr'], 'qr.png', {type: 'image/png'}));

    expect(snackBar.open).toHaveBeenCalledWith('SEANCES.NO_QR_DETECTED', expect.anything(), expect.anything());
    getContext.mockRestore();
  });
});
