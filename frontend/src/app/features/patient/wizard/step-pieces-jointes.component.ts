import { Component, Input, Output, EventEmitter, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatListModule } from '@angular/material/list';
import { TranslateModule } from '@ngx-translate/core';

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
  template: `
    <div class="step-content">
      <h3 class="section-title">{{ 'PATIENT_FORM.PIECES_JOINTES' | translate }}</h3>

      <div class="upload-zone" [class.disabled]="readonly" (click)="!readonly && fileInput.click()" (dragover)="!readonly && $event.preventDefault()" (drop)="onDrop($event)">
        <mat-icon class="upload-icon">cloud_upload</mat-icon>
        <p>{{ 'WIZARD.PJ_DROP' | translate }}</p>
        <p class="hint">{{ 'WIZARD.PJ_MAX_SIZE' | translate }}</p>
        <input #fileInput type="file" multiple hidden (change)="onFiles($event)" />
      </div>

      @if (error()) {
        <div class="error-msg">{{ error() }}</div>
      }

      @if (files().length > 0) {
        <mat-list class="file-list">
          @for (f of files(); track f.name; let i = $index) {
            <mat-list-item class="file-item">
              <mat-icon matListItemIcon>{{ getIcon(f.type) }}</mat-icon>
              <div matListItemTitle>{{ f.name }}</div>
              <div matListItemLine>{{ formatSize(f.size) }} — {{ f.type }}</div>
              <div matListItemMeta>
                @if (f.type.startsWith('image/')) {
                  <button mat-icon-button (click)="preview(f)" [disabled]="readonly"><mat-icon>visibility</mat-icon></button>
                }
                <button mat-icon-button color="warn" (click)="remove(i)" [disabled]="readonly"><mat-icon>delete</mat-icon></button>
              </div>
            </mat-list-item>
          }
        </mat-list>
      }

      <!-- Preview dialog -->
      @if (previewUrl()) {
        <div class="preview-overlay" (click)="previewUrl.set(null)">
          <img [src]="previewUrl()" alt="Preview" class="preview-img" />
        </div>
      }
    </div>
  `,
  styles: [`
    .step-content {   padding: 12px 20px 20px; }
    .section-title { color: #1b5e20; font-size: 1rem; font-weight: 600; margin: 0 0 12px; }
    .upload-zone {
      border: 2px dashed #ccc; border-radius: 12px; padding: 40px; text-align: center;
      cursor: pointer; background: #fafafa; transition: border-color 0.2s;
    }
    .upload-zone:hover { border-color: #1b5e20; background: #f0fdf4; }
    .upload-zone.disabled { opacity:.7; cursor:default; }
    .upload-icon { font-size: 48px; width: 48px; height: 48px; color: #1b5e20; }
    .hint { font-size: 12px; color: #999; }
    .error-msg { background: #fef2f2; color: #991b1b; padding: 8px 12px; border-radius: 8px; margin-top: 8px; font-size: 13px; }
    .file-list { margin-top: 16px; }
    .file-item { border-bottom: 1px solid #f0f0f0; }
    .preview-overlay {
      position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.8);
      display: flex; align-items: center; justify-content: center; z-index: 1000; cursor: pointer;
    }
    .preview-img { max-width: 80%; max-height: 80%; border-radius: 8px; }
  `]
})
export class StepPiecesJointesComponent {
  @Input() data: Record<string, any> = {};
  @Input() readonly = false;
  @Output() dataChange = new EventEmitter<Record<string, any>>();

  files = signal<AttachedFile[]>([]);
  error = signal('');
  previewUrl = signal<string | null>(null);

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

  private addFiles(fileList: File[]): void {
    this.error.set('');
    for (const f of fileList) {
      if (f.size > this.MAX_SIZE) {
        this.error.set(`${f.name}: fichier trop volumineux (max 5 Mo)`);
        continue;
      }
      const reader = new FileReader();
      reader.onload = () => {
        this.files.update(list => [...list, { name: f.name, size: f.size, type: f.type, dataUrl: reader.result as string }]);
        this.data['piecesJointes'] = this.files();
        this.dataChange.emit(this.data);
      };
      reader.readAsDataURL(f);
    }
  }

  remove(index: number): void {
    if (this.readonly) return;
    this.files.update(list => list.filter((_, i) => i !== index));
    this.data['piecesJointes'] = this.files();
    this.dataChange.emit(this.data);
  }

  preview(f: AttachedFile): void { if (!this.readonly) this.previewUrl.set(f.dataUrl); }

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
