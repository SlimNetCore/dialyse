import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {provideZonelessChangeDetection} from '@angular/core';
import {RichTextEditorComponent} from './rich-text-editor.component';
import {QuillModule} from 'ngx-quill';

describe('RichTextEditorComponent', () => {
  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [RichTextEditorComponent, QuillModule],
      providers: [provideZonelessChangeDetection()],
    });
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(RichTextEditorComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should initialize with empty content', () => {
    const fixture = TestBed.createComponent(RichTextEditorComponent);
    expect(fixture.componentInstance.editorContent()).toBe('');
  });

  it('should emit contentChange when editor content changes', async () => {
    const fixture = TestBed.createComponent(RichTextEditorComponent);
    const component = fixture.componentInstance;

    const listener = vi.fn();
    component.contentChange.subscribe(listener);

    const newHtml = '<p><strong>Test content</strong></p>';
    component.onContentChange({html: newHtml});

    await new Promise(resolve => setTimeout(resolve, 10));
    expect(listener).toHaveBeenCalledWith(newHtml);
  });

  it('should update editorContent signal on content change', () => {
    const fixture = TestBed.createComponent(RichTextEditorComponent);
    const component = fixture.componentInstance;

    const newHtml = '<p>Test</p>';
    component.onContentChange({html: newHtml});

    expect(component.editorContent()).toBe(newHtml);
  });

  it('should respect readOnly input', () => {
    const fixture = TestBed.createComponent(RichTextEditorComponent);
    fixture.componentRef.setInput('readOnly', true);

    expect(fixture.componentInstance.readOnly()).toBe(true);
  });

  it('should support custom height', () => {
    const fixture = TestBed.createComponent(RichTextEditorComponent);
    fixture.componentRef.setInput('height', '500px');

    expect(fixture.componentInstance.height()).toBe('500px');
  });

  it('should initialize editorContent from value input', async () => {
    const fixture = TestBed.createComponent(RichTextEditorComponent);
    const component = fixture.componentInstance;

    const testValue = '<p><em>Initial content</em></p>';
    fixture.componentRef.setInput('value', testValue);

    fixture.detectChanges();

    await new Promise(resolve => setTimeout(resolve, 50));
    expect(component.editorContent()).toBe(testValue);
  });

  it('should have toolbar with formatting options', () => {
    const fixture = TestBed.createComponent(RichTextEditorComponent);
    const component = fixture.componentInstance;

    const toolbar = component.editorModules.toolbar;

    // Check for bold, italic, underline
    expect(toolbar).toContainEqual(['bold', 'italic', 'underline', 'strike']);

    // Check for lists
    const listConfig = toolbar.find((item: any) =>
      Array.isArray(item) && item.some((x: any) => x.list !== undefined)
    );
    expect(listConfig).toBeTruthy();

    // Check for colors
    const colorConfig = toolbar.find((item: any) =>
      Array.isArray(item) && item.some((x: any) => x.color !== undefined)
    );
    expect(colorConfig).toBeTruthy();

    // Check for tables
    const tableItems = toolbar.flat().filter((item: any) =>
      typeof item === 'string' && item.includes('table')
    );
    expect(tableItems.length).toBeGreaterThan(0);
  });

  it('should handle empty content change', () => {
    const fixture = TestBed.createComponent(RichTextEditorComponent);
    const component = fixture.componentInstance;

    const listener = vi.fn();
    component.contentChange.subscribe(listener);

    component.onContentChange({html: ''});

    expect(component.editorContent()).toBe('');
    expect(listener).toHaveBeenCalledWith('');
  });
});

