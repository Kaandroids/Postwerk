import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { IconComponent } from '../icon/icon.component';

/** Reusable empty-state card: animated glyph dots, title, optional sub text and CTA button. */
@Component({
  selector: 'app-empty-state',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  templateUrl: './empty-state.component.html',
  styleUrl: './empty-state.component.scss',
})
export class EmptyStateComponent {
  title = input.required<string>();
  sub = input<string>('');
  ctaLabel = input<string>('');
  ctaIcon = input<string>('plus');
  ctaTestid = input<string>('');
  glyphColors = input<string[]>(['#e89c2a', '#8b5cf6', '#19a563']);

  cta = output<void>();
}
