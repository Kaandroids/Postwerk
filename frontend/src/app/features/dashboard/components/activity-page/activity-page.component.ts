import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { I18nService } from '../../../../core/services/i18n.service';
import { ActivityService } from '../../../../core/services/activity.service';
import { ActivityEntry } from '../../../../models/activity.model';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { ErrorBannerComponent } from '../../../../shared/components/error-banner/error-banner.component';
import { PageContentComponent } from '../page-content/page-content.component';
import { humanizeError } from '../../../../shared/utils/error.util';

/** Production activity feed (#3d): what the user's automations did to incoming email, with AI reasoning. */
@Component({
  selector: 'app-activity-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, ErrorBannerComponent, PageContentComponent, DatePipe],
  templateUrl: './activity-page.component.html',
  styleUrl: './activity-page.component.scss',
})
export class ActivityPageComponent implements OnInit {
  protected i18n = inject(I18nService);
  private service = inject(ActivityService);

  entries = signal<ActivityEntry[]>([]);
  loading = signal(true);
  error = signal('');

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading.set(true);
    this.error.set('');
    this.service.recent().subscribe({
      next: page => { this.entries.set(page.content); this.loading.set(false); },
      error: (err) => { this.error.set(humanizeError(err, this.i18n.t('activity_load_failed'))); this.loading.set(false); },
    });
  }
}
