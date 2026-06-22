import { Injectable } from '@angular/core';

/** Utility service for downloading data as JSON files and reading uploaded JSON files for import. */
@Injectable({ providedIn: 'root' })
export class ExportImportService {

  downloadJson(data: unknown, filename: string): void {
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
    this.downloadBlob(blob, filename);
  }

  downloadBlob(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }

  readJsonFile<T>(event: Event): Promise<T> {
    return new Promise((resolve, reject) => {
      const input = event.target as HTMLInputElement;
      const file = input.files?.[0];
      if (!file) { reject(new Error('No file selected')); return; }
      const reader = new FileReader();
      reader.onload = () => {
        try {
          resolve(JSON.parse(reader.result as string));
        } catch (e) {
          reject(e);
        }
      };
      reader.onerror = () => reject(reader.error);
      reader.readAsText(file);
      input.value = '';
    });
  }
}
