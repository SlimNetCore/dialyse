import {ChangeDetectionStrategy, Component, EventEmitter, inject, Input, Output, signal,} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatChipsModule} from '@angular/material/chips';
import {MatListModule} from '@angular/material/list';
import {TranslateModule, TranslateService} from '@ngx-translate/core';

interface AttachedFile {
  name: string;
  size: number;
  type: string;
  dataUrl: string;
}

@Component({
  selector: 'app-step-pieces-jointes',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, MatChipsModule, MatListModule, TranslateModule],
  templateUrl: './step-pieces-jointes.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './step-pieces-jointes.component.css',
})
export class StepPiecesJointesComponent {
  @Input() data: Record<string, any> = {};
  @Input() readonly = false;
  @Output() dataChange = new EventEmitter<Record<string, any>>();

  files = signal<AttachedFile[]>([]);
  error = signal('');
  previewUrl = signal<string | null>(null);
  private readonly translate = inject(TranslateService);

  private readonly MAX_SIZE = 5 * 1024 * 1024; // 5MB

  onFiles(event: Event): void {
    if (this.readonly) return;
    const input = event.target as HTMLInputElement;
    if (input.files) this.addFiles(Array.from(input.files));
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    if (this.readonly) return;
    if (event.dataTransfer?.files) this.addFiles(Array.from(event.dataTransfer.files));
  }

  openAttachment(f: AttachedFile): void {
    if (f.type.startsWith('image/')) {
      this.preview(f);
      return;
    }
    const win = window.open(f.dataUrl, '_blank');
    if (!win) {
      this.error.set(this.translate.instant('WIZARD.PJ_PREVIEW_BLOCKED'));
    }
  }

  remove(index: number): void {
    if (this.readonly) return;
    this.files.update((list) => list.filter((_, i) => i !== index));
    this.data['piecesJointes'] = this.files();
    this.dataChange.emit(this.data);
  }

  preview(f: AttachedFile): void {
    this.previewUrl.set(f.dataUrl);
  }

  private addFiles(fileList: File[]): void {
    this.error.set('');
    for (const f of fileList) {
      if (f.size > this.MAX_SIZE) {
        this.error.set(this.translate.instant('WIZARD.PJ_FILE_TOO_LARGE', {name: f.name}));
        continue;
      }
      const reader = new FileReader();
      reader.onload = () => {
        this.files.update((list) => [
          ...list,
          {name: f.name, size: f.size, type: f.type, dataUrl: reader.result as string},
        ]);
        this.data['piecesJointes'] = this.files();
        this.dataChange.emit(this.data);
      };
      reader.readAsDataURL(f);
    }
  }

  formatSize(bytes: number): string {
    if (bytes < 1024) return bytes + ' o';
    if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' Ko';
    return (bytes / 1048576).toFixed(1) + ' Mo';
  }

  getIcon(type: string): string {
    if (type.startsWith('image/')) return 'image';
    if (type.includes('pdf')) return 'picture_as_pdf';
    return 'insert_drive_file';
  }

  patchData(data: Record<string, any>): void {
    const pieces = Array.isArray(data['piecesJointes']) ? data['piecesJointes'] : [];
    this.files.set(pieces);
  }
}
