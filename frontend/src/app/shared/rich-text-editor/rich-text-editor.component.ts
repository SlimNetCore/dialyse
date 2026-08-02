import {Component, ChangeDetectionStrategy, input, output, signal, ChangeDetectorRef, effect} from '@angular/core';
import {CommonModule} from '@angular/common';
import {QuillModule} from 'ngx-quill';
import {FormsModule} from '@angular/forms';

/**
 * RichTextEditorComponent - Composant éditeur de texte enrichi basé sur Quill
 * Supporte :
 * - Formatage (gras, italique, souligné, barré)
 * - Listes (ordonnées et non ordonnées)
 * - Tableaux
 * - Palette de couleurs (texte et fond)
 * - Polices multiples
 * - Tailles de police
 * - Alignement du texte
 * - Liens hypertexte
 * - Images
 */
@Component({
  selector: 'app-rich-text-editor',
  standalone: true,
  imports: [CommonModule, QuillModule, FormsModule],
  template: `
    <div class="rich-text-editor-wrapper">
      <quill-editor
        [(ngModel)]="editorContent"
        (onContentChanged)="onContentChange($event)"
        [modules]="editorModules"
        [readOnly]="readOnly()"
        [styles]="{'height': height()}"
        placeholder="Entrez votre texte ici..."
        class="editor-container"
      ></quill-editor>
    </div>
  `,
  styles: [`
    .rich-text-editor-wrapper {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .editor-container {
      background: white;
      border: 1px solid #ccc;
      border-radius: 4px;
      padding: 8px;
    }

    :deep(.ql-toolbar) {
      border-top-left-radius: 4px;
      border-top-right-radius: 4px;
      border: 1px solid #ccc;
      background-color: #f9f9f9;
    }

    :deep(.ql-container) {
      border-bottom-left-radius: 4px;
      border-bottom-right-radius: 4px;
      border: 1px solid #ccc;
      font-size: 14px;
    }

    :deep(.ql-editor) {
      min-height: 150px;
      padding: 12px;
      line-height: 1.6;
    }

    :deep(.ql-editor.ql-blank::before) {
      color: #aaa;
      font-style: italic;
    }

    :deep(.ql-snow .ql-stroke) {
      stroke: #444;
    }

    :deep(.ql-snow .ql-fill) {
      fill: #444;
    }

    :deep(.ql-snow.ql-toolbar button:hover),
    :deep(.ql-snow.ql-toolbar button:focus),
    :deep(.ql-snow.ql-toolbar button.ql-active),
    :deep(.ql-snow.ql-toolbar .ql-picker-label:hover),
    :deep(.ql-snow.ql-toolbar .ql-picker-item:hover),
    :deep(.ql-snow.ql-toolbar .ql-picker-item.ql-selected) {
      color: #1976d2;
    }

    :deep(.ql-toolbar.ql-snow .ql-picker-label) {
      color: #444;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RichTextEditorComponent {
  // Inputs
  value = input<string>('');
  readOnly = input<boolean>(false);
  height = input<string>('300px');
  placeholder = input<string>('Entrez votre texte ici...');

  // Outputs
  contentChange = output<string>();

  // State
  editorContent = signal('');

  editorModules = {
    toolbar: [
      ['bold', 'italic', 'underline', 'strike'],        // Formatage
      ['blockquote', 'code-block'],                       // Blocs
      [{'header': 1}, {'header': 2}],               // Headers
      [{'list': 'ordered'}, {'list': 'bullet'}],     // Listes
      [{'script': 'sub'}, {'script': 'super'}],      // Exposants/Indices
      [{'indent': '-1'}, {'indent': '+1'}],          // Indentation
      [{'size': ['small', false, 'large', 'huge']}],  // Tailles
      [{'font': []}],                                   // Polices
      [{'color': []}, {'background': []}],          // Couleurs
      [{'align': []}],                                 // Alignement
      ['link', 'image', 'video'],                         // Médias
      [{'table': 'cell-merge'}, 'table', 'table-insert-row', 'table-insert-col'],  // Tableaux
      ['clean']                                           // Nettoyer le formatage
    ]
  };

  constructor(private cdr: ChangeDetectorRef) {
    // Synchroniser la valeur d'entrée avec le contenu de l'éditeur
    effect(() => {
      const inputValue = this.value();
      if (inputValue && inputValue !== this.editorContent()) {
        this.editorContent.set(inputValue);
        this.cdr.markForCheck();
      }
    });
  }

  onContentChange(event: any): void {
    const newContent = event.html || '';
    this.editorContent.set(newContent);
    this.contentChange.emit(newContent);
  }
}


