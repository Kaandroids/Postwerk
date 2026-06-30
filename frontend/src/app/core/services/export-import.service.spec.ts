import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { ExportImportService } from './export-import.service';

describe('ExportImportService', () => {
  let service: ExportImportService;
  let clickSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ExportImportService);
    // jsdom implements neither URL.createObjectURL nor anchor navigation — stub both.
    URL.createObjectURL = vi.fn(() => 'blob:mock');
    URL.revokeObjectURL = vi.fn();
    clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
  });

  it('downloadBlob() wires an anchor with the filename and triggers a click', () => {
    const blob = new Blob(['x']);
    service.downloadBlob(blob, 'report.json');
    expect(URL.createObjectURL).toHaveBeenCalledWith(blob);
    expect(clickSpy).toHaveBeenCalled();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:mock');
  });

  it('downloadJson() serializes the data to a JSON blob and downloads it', () => {
    service.downloadJson({ a: 1 }, 'data.json');
    const blob = (URL.createObjectURL as ReturnType<typeof vi.fn>).mock.calls[0][0] as Blob;
    expect(blob.type).toBe('application/json');
    expect(clickSpy).toHaveBeenCalled();
  });

  it('readJsonFile() resolves the parsed contents of the selected file', async () => {
    const file = new File(['{"hello":"world"}'], 'in.json', { type: 'application/json' });
    const event = { target: { files: [file], value: 'x' } } as unknown as Event;
    await expect(service.readJsonFile(event)).resolves.toEqual({ hello: 'world' });
  });

  it('readJsonFile() rejects when no file is selected', async () => {
    const event = { target: { files: [], value: '' } } as unknown as Event;
    await expect(service.readJsonFile(event)).rejects.toThrow('No file selected');
  });

  it('readJsonFile() rejects on invalid JSON', async () => {
    const file = new File(['{not valid'], 'in.json');
    const event = { target: { files: [file], value: '' } } as unknown as Event;
    await expect(service.readJsonFile(event)).rejects.toBeDefined();
  });
});
