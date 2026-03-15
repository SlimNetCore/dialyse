import { Component, EventEmitter, Input, Output, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDividerModule } from '@angular/material/divider';

export interface DesignBlock {
  id: string;
  type: 'header_image' | 'footer_image' | 'title' | 'text' | 'field' | 'table' | 'separator' | 'spacer';
  label: string;
  content: string;         // text content or token e.g. {{patient.nom}}
  style: string;           // inline CSS
  tableColumns?: string[]; // for table type: column aliases
  tableHeaders?: string[]; // for table type: column headers
  fontSize?: string;
  alignment?: string;
  bold?: boolean;
}

@Component({
  selector: 'app-report-designer',
  standalone: true,
  imports: [
    CommonModule, FormsModule, DragDropModule,
    MatIconModule, MatButtonModule, MatFormFieldModule,
    MatInputModule, MatSelectModule, MatTooltipModule, MatDividerModule
  ],
  template: `
    <div class="designer-layout">
      <!-- Palette -->
      <aside class="palette">
        <h4><mat-icon>widgets</mat-icon> Blocs</h4>
        <div cdkDropList #paletteList="cdkDropList"
             [cdkDropListData]="paletteBlocks"
             [cdkDropListConnectedTo]="[canvasList]"
             cdkDropListSortingDisabled
             class="palette-list">
          @for (b of paletteBlocks; track b.id) {
            <div cdkDrag class="palette-item" [cdkDragData]="b">
              <mat-icon>{{ iconFor(b.type) }}</mat-icon>
              <span>{{ b.label }}</span>
            </div>
          }
        </div>

        <h4 style="margin-top:16px"><mat-icon>data_object</mat-icon> Tokens</h4>
        <div class="token-list">
          @for (t of tokens; track t) {
            <button class="token-chip" (click)="insertToken(t)" [matTooltip]="'Insérer ' + t">{{ t }}</button>
          }
        </div>
      </aside>

      <!-- Canvas -->
      <div class="canvas-wrapper">
        <div class="canvas-toolbar">
          <button mat-stroked-button (click)="generateHtml()"><mat-icon>code</mat-icon> Générer HTML</button>
          <button mat-stroked-button color="warn" (click)="clearCanvas()"><mat-icon>delete_sweep</mat-icon> Tout effacer</button>
        </div>

        <div class="canvas-paper" [style.max-width]="paperWidth()">
          <div cdkDropList #canvasList="cdkDropList"
               [cdkDropListData]="canvasBlocks()"
               [cdkDropListConnectedTo]="[paletteList]"
               (cdkDropListDropped)="onDrop($event)"
               class="canvas-drop-zone">

            @if (canvasBlocks().length === 0) {
              <div class="canvas-placeholder">
                <mat-icon>drag_indicator</mat-icon>
                <p>Glissez des blocs ici pour construire votre modèle</p>
              </div>
            }

            @for (block of canvasBlocks(); track block.id; let i = $index) {
              <div cdkDrag class="canvas-block" [class.selected]="selectedBlockId() === block.id"
                   (click)="selectBlock(block)">
                <div class="block-handle" cdkDragHandle>
                  <mat-icon>drag_indicator</mat-icon>
                </div>
                <div class="block-content" [ngSwitch]="block.type">
                  <!-- Header / Footer Image -->
                  <div *ngSwitchCase="'header_image'" class="block-img-placeholder">
                    <mat-icon>image</mat-icon> Image entête
                  </div>
                  <div *ngSwitchCase="'footer_image'" class="block-img-placeholder">
                    <mat-icon>image</mat-icon> Image pied de page
                  </div>
                  <!-- Title -->
                  <div *ngSwitchCase="'title'" class="block-title"
                       [style.font-size]="block.fontSize || '20px'"
                       [style.text-align]="block.alignment || 'center'"
                       [style.font-weight]="block.bold ? 'bold' : 'normal'">
                    {{ block.content || 'Titre' }}
                  </div>
                  <!-- Text -->
                  <div *ngSwitchCase="'text'" class="block-text"
                       [style.font-size]="block.fontSize || '13px'"
                       [style.text-align]="block.alignment || 'left'">
                    {{ block.content || 'Texte libre...' }}
                  </div>
                  <!-- Field token -->
                  <div *ngSwitchCase="'field'" class="block-field">
                    <span class="field-token">{{ block.content || '{{token}}' }}</span>
                  </div>
                  <!-- Table -->
                  <div *ngSwitchCase="'table'" class="block-table">
                    <table>
                      <thead>
                        <tr>
                          @for (h of (block.tableHeaders || ['Colonne 1']); track h) {
                            <th>{{ h }}</th>
                          }
                        </tr>
                      </thead>
                      <tbody>
                        <tr class="sample-row">
                          @for (c of (block.tableColumns || ['col1']); track c) {
                            <td>{{ '{{row.' + c + '}}' }}</td>
                          }
                        </tr>
                      </tbody>
                    </table>
                  </div>
                  <!-- Separator -->
                  <div *ngSwitchCase="'separator'" class="block-separator"><hr /></div>
                  <!-- Spacer -->
                  <div *ngSwitchCase="'spacer'" class="block-spacer"></div>
                </div>
                <button mat-icon-button class="block-delete" (click)="removeBlock(i); $event.stopPropagation()" matTooltip="Supprimer">
                  <mat-icon>close</mat-icon>
                </button>
              </div>
            }
          </div>
        </div>
      </div>

      <!-- Properties panel -->
      <aside class="properties" *ngIf="selectedBlock()">
        <h4><mat-icon>tune</mat-icon> Propriétés</h4>

        <mat-form-field appearance="outline" class="full">
          <mat-label>Contenu / Token</mat-label>
          <textarea matInput rows="3" [(ngModel)]="selectedBlock()!.content" (ngModelChange)="refreshCanvas()"></textarea>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full">
          <mat-label>Taille police</mat-label>
          <mat-select [(ngModel)]="selectedBlock()!.fontSize" (ngModelChange)="refreshCanvas()">
            <mat-option value="10px">10px</mat-option>
            <mat-option value="12px">12px</mat-option>
            <mat-option value="13px">13px</mat-option>
            <mat-option value="14px">14px</mat-option>
            <mat-option value="16px">16px</mat-option>
            <mat-option value="18px">18px</mat-option>
            <mat-option value="20px">20px</mat-option>
            <mat-option value="24px">24px</mat-option>
            <mat-option value="28px">28px</mat-option>
            <mat-option value="32px">32px</mat-option>
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full">
          <mat-label>Alignement</mat-label>
          <mat-select [(ngModel)]="selectedBlock()!.alignment" (ngModelChange)="refreshCanvas()">
            <mat-option value="left">Gauche</mat-option>
            <mat-option value="center">Centre</mat-option>
            <mat-option value="right">Droite</mat-option>
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full">
          <mat-label>Style CSS inline</mat-label>
          <input matInput [(ngModel)]="selectedBlock()!.style" (ngModelChange)="refreshCanvas()" />
        </mat-form-field>

        @if (selectedBlock()?.type === 'table') {
          <mat-form-field appearance="outline" class="full">
            <mat-label>En-têtes colonnes (séparés par ;)</mat-label>
            <input matInput [ngModel]="selectedBlock()!.tableHeaders?.join(';')"
                   (ngModelChange)="onTableHeadersChange($event)" />
          </mat-form-field>
          <mat-form-field appearance="outline" class="full">
            <mat-label>Alias SQL colonnes (séparés par ;)</mat-label>
            <input matInput [ngModel]="selectedBlock()!.tableColumns?.join(';')"
                   (ngModelChange)="onTableColumnsChange($event)" />
          </mat-form-field>
        }
      </aside>
    </div>
  `,
  styles: [`
    .designer-layout {
      display: grid; grid-template-columns: 220px 1fr 240px; gap: 12px;
      min-height: 60vh;
    }
    /* Palette */
    .palette { background: #f8faf9; border-radius: 10px; padding: 12px; border: 1px solid #e5e7eb; }
    .palette h4 { display: flex; align-items: center; gap: 6px; color: #1b5e20; margin: 0 0 8px; font-size: 13px; }
    .palette-list { display: flex; flex-direction: column; gap: 6px; }
    .palette-item {
      display: flex; align-items: center; gap: 8px; padding: 8px 10px;
      background: #fff; border: 1px solid #e0e4e1; border-radius: 8px;
      cursor: grab; font-size: 12px; transition: box-shadow 0.15s;
    }
    .palette-item:hover { box-shadow: 0 2px 8px rgba(27,94,32,0.12); }
    .palette-item mat-icon { font-size: 18px; width: 18px; height: 18px; color: #1b5e20; }

    .token-list { display: flex; flex-wrap: wrap; gap: 4px; }
    .token-chip {
      font-size: 10px; padding: 3px 7px; border: 1px solid #c8e6c9; border-radius: 12px;
      background: #e8f5e9; color: #1b5e20; cursor: pointer;
    }
    .token-chip:hover { background: #c8e6c9; }

    /* Canvas */
    .canvas-wrapper { display: flex; flex-direction: column; }
    .canvas-toolbar { display: flex; gap: 8px; margin-bottom: 8px; }
    .canvas-paper {
      background: #fff; border: 2px dashed #c8e6c9; border-radius: 10px;
      padding: 20px; min-height: 500px; margin: 0 auto; width: 100%;
    }
    .canvas-drop-zone { min-height: 400px; }
    .canvas-placeholder {
      display: flex; flex-direction: column; align-items: center; justify-content: center;
      color: #9e9e9e; padding: 60px 0;
    }
    .canvas-placeholder mat-icon { font-size: 48px; width: 48px; height: 48px; }

    .canvas-block {
      display: flex; align-items: flex-start; gap: 4px; padding: 6px 8px;
      border: 1px solid transparent; border-radius: 6px; margin-bottom: 4px;
      transition: border-color 0.15s, background 0.15s; position: relative;
    }
    .canvas-block:hover { border-color: #a5d6a7; background: #f1f8e9; }
    .canvas-block.selected { border-color: #1b5e20; background: #e8f5e9; }
    .block-handle { cursor: grab; color: #9e9e9e; padding-top: 2px; }
    .block-content { flex: 1; min-width: 0; }
    .block-delete { position: absolute; top: 2px; right: 2px; }

    .block-img-placeholder {
      display: flex; align-items: center; gap: 8px; padding: 12px;
      background: #f5f5f5; border-radius: 6px; color: #757575; font-size: 12px;
    }
    .block-title { padding: 4px 0; }
    .block-text { padding: 4px 0; color: #424242; }
    .block-field .field-token {
      display: inline-block; padding: 3px 8px; background: #e3f2fd; border-radius: 4px;
      font-family: monospace; font-size: 12px; color: #1565c0;
    }
    .block-table table {
      width: 100%; border-collapse: collapse; font-size: 11px;
    }
    .block-table th {
      background: #e8f5e9; padding: 5px 8px; border: 1px solid #c8e6c9;
      font-weight: 600; color: #1b5e20;
    }
    .block-table td {
      padding: 5px 8px; border: 1px solid #e0e4e1; font-family: monospace; color: #616161;
    }
    .block-separator hr { border: none; border-top: 1px solid #bdbdbd; margin: 8px 0; }
    .block-spacer { height: 20px; }

    /* Properties */
    .properties { background: #f8faf9; border-radius: 10px; padding: 12px; border: 1px solid #e5e7eb; }
    .properties h4 { display: flex; align-items: center; gap: 6px; color: #1b5e20; margin: 0 0 10px; font-size: 13px; }
    .full { width: 100%; }

    /* CDK drag preview */
    .cdk-drag-preview { box-shadow: 0 4px 16px rgba(0,0,0,0.18); border-radius: 6px; }
    .cdk-drag-placeholder { opacity: 0.3; }
  `]
})
export class ReportDesignerComponent {
  @Input() headerImage = '';
  @Input() footerImage = '';
  @Output() htmlGenerated = new EventEmitter<string>();

  readonly canvasBlocks = signal<DesignBlock[]>([]);
  readonly selectedBlockId = signal<string | null>(null);
  readonly selectedBlock = computed(() => {
    const id = this.selectedBlockId();
    return this.canvasBlocks().find(b => b.id === id) ?? null;
  });

  paperWidth = signal('700px');

  private nextId = 1;

  paletteBlocks: DesignBlock[] = [
    { id: 'p-header', type: 'header_image', label: 'Image entête', content: '', style: '', fontSize: '', alignment: 'center' },
    { id: 'p-title', type: 'title', label: 'Titre', content: 'Mon titre', style: '', fontSize: '20px', alignment: 'center', bold: true },
    { id: 'p-text', type: 'text', label: 'Texte libre', content: 'Texte...', style: '', fontSize: '13px', alignment: 'left' },
    { id: 'p-field', type: 'field', label: 'Champ / Token', content: '{{patient.nom}}', style: '', fontSize: '13px', alignment: 'left' },
    { id: 'p-table', type: 'table', label: 'Tableau données', content: '', style: '', tableHeaders: ['Nom', 'Prénom'], tableColumns: ['patient_nom', 'patient_prenom'] },
    { id: 'p-sep', type: 'separator', label: 'Séparateur', content: '', style: '' },
    { id: 'p-spacer', type: 'spacer', label: 'Espacement', content: '', style: '' },
    { id: 'p-footer', type: 'footer_image', label: 'Image pied de page', content: '', style: '', fontSize: '', alignment: 'center' }
  ];

  tokens = [
    '{{patient.nom}}', '{{patient.prenom}}', '{{patient.sexe}}',
    '{{patient.dateNaissance}}', '{{patient.adresse}}', '{{patient.telMobile}}',
    '{{patient.numeroAssurance}}', '{{center.name}}',
    '{{attestation.dateDebut}}', '{{attestation.dateFin}}',
    '{{pec.dateDebutDemande}}', '{{pec.dateFinDemande}}', '{{pec.statut}}',
    '{{generatedAt}}', '{{data.rowCount}}'
  ];

  iconFor(type: string): string {
    switch (type) {
      case 'header_image': case 'footer_image': return 'image';
      case 'title': return 'title';
      case 'text': return 'notes';
      case 'field': return 'data_object';
      case 'table': return 'table_chart';
      case 'separator': return 'horizontal_rule';
      case 'spacer': return 'expand';
      default: return 'widgets';
    }
  }

  onDrop(event: CdkDragDrop<DesignBlock[]>): void {
    if (event.previousContainer === event.container) {
      // Reorder within canvas
      const blocks = [...this.canvasBlocks()];
      moveItemInArray(blocks, event.previousIndex, event.currentIndex);
      this.canvasBlocks.set(blocks);
    } else {
      // Drop from palette — clone the block
      const source: DesignBlock = event.item.data;
      const newBlock: DesignBlock = {
        ...source,
        id: 'b-' + (this.nextId++),
        tableHeaders: source.tableHeaders ? [...source.tableHeaders] : undefined,
        tableColumns: source.tableColumns ? [...source.tableColumns] : undefined
      };
      const blocks = [...this.canvasBlocks()];
      blocks.splice(event.currentIndex, 0, newBlock);
      this.canvasBlocks.set(blocks);
      this.selectedBlockId.set(newBlock.id);
    }
  }

  selectBlock(block: DesignBlock): void {
    this.selectedBlockId.set(block.id);
  }

  removeBlock(index: number): void {
    const blocks = [...this.canvasBlocks()];
    const removed = blocks.splice(index, 1);
    this.canvasBlocks.set(blocks);
    if (removed[0]?.id === this.selectedBlockId()) {
      this.selectedBlockId.set(null);
    }
  }

  clearCanvas(): void {
    this.canvasBlocks.set([]);
    this.selectedBlockId.set(null);
  }

  refreshCanvas(): void {
    // Force reactivity
    this.canvasBlocks.set([...this.canvasBlocks()]);
  }

  insertToken(token: string): void {
    const sel = this.selectedBlock();
    if (sel) {
      sel.content = (sel.content || '') + ' ' + token;
      this.refreshCanvas();
    }
  }

  onTableHeadersChange(value: string): void {
    const sel = this.selectedBlock();
    if (sel) {
      sel.tableHeaders = value.split(';').map(s => s.trim()).filter(Boolean);
      this.refreshCanvas();
    }
  }

  onTableColumnsChange(value: string): void {
    const sel = this.selectedBlock();
    if (sel) {
      sel.tableColumns = value.split(';').map(s => s.trim()).filter(Boolean);
      this.refreshCanvas();
    }
  }

  generateHtml(): void {
    let html = '';
    for (const block of this.canvasBlocks()) {
      const style = block.style ? ` style="${block.style}"` : '';
      switch (block.type) {
        case 'header_image':
          html += `<div style="text-align:center;margin-bottom:14px"><img src="{{headerImage}}" style="max-width:100%;max-height:110px" /></div>\n`;
          break;
        case 'footer_image':
          html += `<div style="text-align:center;margin-top:14px"><img src="{{footerImage}}" style="max-width:100%;max-height:80px" /></div>\n`;
          break;
        case 'title':
          html += `<h2 style="font-size:${block.fontSize || '20px'};text-align:${block.alignment || 'center'};font-weight:${block.bold ? 'bold' : 'normal'}"${style}>${block.content}</h2>\n`;
          break;
        case 'text':
          html += `<p style="font-size:${block.fontSize || '13px'};text-align:${block.alignment || 'left'}"${style}>${block.content}</p>\n`;
          break;
        case 'field':
          html += `<p style="font-size:${block.fontSize || '13px'};text-align:${block.alignment || 'left'}"${style}>${block.content}</p>\n`;
          break;
        case 'table': {
          const headers = block.tableHeaders || [];
          const cols = block.tableColumns || [];
          html += `<table style="width:100%;border-collapse:collapse;font-size:12px"${style}>\n<thead><tr>\n`;
          for (const h of headers) {
            html += `  <th style="border:1px solid #999;padding:5px 8px;background:#e8f5e9">${h}</th>\n`;
          }
          html += `</tr></thead>\n<tbody>\n` + '{{#each data.rows}}\n<tr>\n';
          for (const c of cols) {
            html += `  <td style="border:1px solid #ccc;padding:5px 8px">{{row.${c}}}</td>\n`;
          }
          html += `</tr>\n` + '{{/each}}\n</tbody>\n</table>\n';
          break;
        }
        case 'separator':
          html += `<hr style="border:none;border-top:1px solid #999;margin:10px 0"${style} />\n`;
          break;
        case 'spacer':
          html += `<div style="height:20px"${style}></div>\n`;
          break;
      }
    }
    this.htmlGenerated.emit(html);
  }
}


