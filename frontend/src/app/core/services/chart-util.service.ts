import { Injectable } from '@angular/core';

export interface ChartDataPoint {
  value: number;
}

/** Generates SVG path strings for line and area charts from timeline data points. */
@Injectable({ providedIn: 'root' })
export class ChartUtilService {

  svgLinePath(data: ChartDataPoint[], width = 600, height = 120): string {
    if (data.length < 2) return '';
    const max = Math.max(...data.map(d => d.value), 1);
    return data.map((d, i) => {
      const x = (i / (data.length - 1)) * width;
      const y = height - (d.value / max) * height;
      return `${i === 0 ? 'M' : 'L'}${x},${y}`;
    }).join(' ');
  }

  svgAreaPath(data: ChartDataPoint[], width = 600, height = 120): string {
    const line = this.svgLinePath(data, width, height);
    if (!line) return '';
    return `${line} L${width},${height} L0,${height} Z`;
  }
}
