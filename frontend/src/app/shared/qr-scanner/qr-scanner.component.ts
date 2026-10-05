import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  OnDestroy,
  output,
  signal,
  viewChild,
} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatSnackBar} from '@angular/material/snack-bar';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import jsQR from 'jsqr';

type BarcodeDetectorInstance = {
  detect: (source: ImageBitmapSource) => Promise<Array<{ rawValue?: string }>>;
};
type BarcodeDetectorConstructor = new (options?: { formats?: string[] }) => BarcodeDetectorInstance;

/**
 * Lecteur de QR code réutilisable : caméra (BarcodeDetector) et lecture depuis une image (BarcodeDetector puis jsQR).
 * Il ne connaît ni la séance ni le patient : il émet seulement la valeur lue, à charge de l'appelant de la traiter.
 */
@Component({
  selector: 'app-qr-scanner',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, TranslateModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './qr-scanner.component.html',
  styleUrl: './qr-scanner.component.css',
})
export class QrScannerComponent implements OnDestroy {
  /** Valeur lue (caméra ou image), déjà nettoyée. */
  readonly scanned = output<string>();

  protected readonly cameraActive = signal(false);
  protected readonly cameraStarting = signal(false);

  private readonly snackBar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);
  private readonly cameraVideo = viewChild<ElementRef<HTMLVideoElement>>('cameraVideo');
  private readonly imageInput = viewChild<ElementRef<HTMLInputElement>>('imageInput');
  private cameraStream: MediaStream | null = null;
  private cameraFrameId: number | null = null;
  private cameraDetector: BarcodeDetectorInstance | null = null;
  private frameCanvas: HTMLCanvasElement | null = null;
  private cameraDetectionInFlight = false;

  ngOnDestroy(): void {
    this.stopCamera();
  }

  protected toggleCamera(): void {
    if (this.cameraActive()) {
      this.stopCamera();
      return;
    }
    void this.startCamera();
  }

  protected pickImage(): void {
    this.imageInput()?.nativeElement.click();
  }

  protected onImageSelected(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    const file = input?.files?.[0];
    if (!file) return;
    void this.scanImage(file);
    if (input) input.value = '';
  }

  private notify(key: string): void {
    this.snackBar.open(this.translate.instant(key), this.translate.instant('COMMON.OK'), {duration: 3000});
  }

  private detectorConstructor(): BarcodeDetectorConstructor | null {
    return (window as Window & { BarcodeDetector?: BarcodeDetectorConstructor }).BarcodeDetector ?? null;
  }

  /** La caméra exige une page en HTTPS (ou localhost) ; le décodage se rabat sur jsQR sans BarcodeDetector. */
  private supportsCamera(): boolean {
    return typeof navigator !== 'undefined' && !!navigator.mediaDevices?.getUserMedia;
  }

  private isSecureContext(): boolean {
    return typeof window === 'undefined' || window.isSecureContext !== false;
  }

  private async startCamera(): Promise<void> {
    if (!this.isSecureContext()) {
      this.notify('SEANCES.CAMERA_INSECURE_CONTEXT');
      return;
    }
    if (!this.supportsCamera()) {
      this.notify('SEANCES.CAMERA_NOT_SUPPORTED');
      return;
    }
    // La vidéo n'existe qu'une fois la zone caméra affichée : on l'affiche avant de demander le flux.
    this.cameraActive.set(true);
    this.cameraStarting.set(true);
    try {
      this.cameraStream = await navigator.mediaDevices.getUserMedia({
        video: {facingMode: {ideal: 'environment'}},
        audio: false,
      });
      await new Promise<void>((resolve) => queueMicrotask(resolve));
      const video = this.cameraVideo()?.nativeElement;
      if (!video) {
        this.stopCamera();
        return;
      }
      video.srcObject = this.cameraStream;
      await video.play();
      this.scheduleDetection();
    } catch {
      this.stopCamera();
      this.notify('SEANCES.CAMERA_ACCESS_DENIED');
    } finally {
      this.cameraStarting.set(false);
    }
  }

  private stopCamera(): void {
    if (this.cameraFrameId !== null) {
      cancelAnimationFrame(this.cameraFrameId);
      this.cameraFrameId = null;
    }
    const video = this.cameraVideo()?.nativeElement;
    if (video) {
      video.pause();
      video.srcObject = null;
    }
    this.cameraStream?.getTracks().forEach((t) => t.stop());
    this.cameraStream = null;
    this.cameraActive.set(false);
    this.cameraDetectionInFlight = false;
  }

  private scheduleDetection(): void {
    if (!this.cameraActive()) return;
    this.cameraFrameId = requestAnimationFrame(() => void this.detectFrame());
  }

  private async detectFrame(): Promise<void> {
    const video = this.cameraVideo()?.nativeElement;
    if (!this.cameraActive() || this.cameraDetectionInFlight || !video || video.readyState < 2) {
      this.scheduleDetection();
      return;
    }
    const DetectorCtor = this.detectorConstructor();
    this.cameraDetectionInFlight = true;
    try {
      let value = '';
      if (DetectorCtor) {
        this.cameraDetector ??= new DetectorCtor({formats: ['qr_code']});
        value = ((await this.cameraDetector.detect(video))[0]?.rawValue ?? '').trim();
      } else {
        // Sans BarcodeDetector (iPhone/Safari, Firefox…) : décodage jsQR d'une image réduite de la vidéo, ~6 fois/s.
        value = this.decodeFrameWithJsQr(video) ?? '';
        if (!value) await new Promise<void>((resolve) => setTimeout(resolve, 160));
      }
      if (value) {
        this.stopCamera();
        this.scanned.emit(value);
        return;
      }
    } catch {
      this.stopCamera();
      return;
    } finally {
      this.cameraDetectionInFlight = false;
    }
    this.scheduleDetection();
  }

  private async scanImage(file: File): Promise<void> {
    try {
      const image = await this.loadImage(file);
      const value = (await this.decodeWithDetector(image)) ?? this.decodeWithJsQr(image);
      if (!value) {
        this.notify('SEANCES.NO_QR_DETECTED');
        return;
      }
      this.scanned.emit(value);
    } catch {
      this.notify('SEANCES.QR_READ_ERROR');
    }
  }

  private loadImage(file: File): Promise<HTMLImageElement> {
    return new Promise<HTMLImageElement>((resolve, reject) => {
      const url = URL.createObjectURL(file);
      const image = new Image();
      image.onload = () => {
        URL.revokeObjectURL(url);
        resolve(image);
      };
      image.onerror = () => {
        URL.revokeObjectURL(url);
        reject(new Error('IMAGE_LOAD_FAILED'));
      };
      image.src = url;
    });
  }

  private async decodeWithDetector(image: HTMLImageElement): Promise<string | null> {
    const DetectorCtor = this.detectorConstructor();
    if (!DetectorCtor) return null;
    try {
      const barcodes = await new DetectorCtor({formats: ['qr_code']}).detect(image);
      return (barcodes[0]?.rawValue ?? '').trim() || null;
    } catch {
      return null;
    }
  }

  private decodeFrameWithJsQr(video: HTMLVideoElement): string | null {
    const sourceWidth = video.videoWidth;
    const sourceHeight = video.videoHeight;
    if (!sourceWidth || !sourceHeight) return null;
    const scale = Math.min(1, 640 / sourceWidth);
    const width = Math.round(sourceWidth * scale);
    const height = Math.round(sourceHeight * scale);
    const canvas = (this.frameCanvas ??= document.createElement('canvas'));
    canvas.width = width;
    canvas.height = height;
    const context = canvas.getContext('2d', {willReadFrequently: true});
    if (!context) return null;
    context.drawImage(video, 0, 0, width, height);
    const data = context.getImageData(0, 0, width, height);
    return jsQR(data.data, width, height, {inversionAttempts: 'dontInvert'})?.data?.trim() || null;
  }

  private decodeWithJsQr(image: HTMLImageElement): string | null {
    const width = image.naturalWidth || image.width;
    const height = image.naturalHeight || image.height;
    if (!width || !height) return null;
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const context = canvas.getContext('2d', {willReadFrequently: true});
    if (!context) return null;
    context.drawImage(image, 0, 0, width, height);
    const data = context.getImageData(0, 0, width, height);
    return jsQR(data.data, width, height, {inversionAttempts: 'attemptBoth'})?.data?.trim() || null;
  }
}
