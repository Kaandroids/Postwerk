import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { CategoryService } from '../../../../core/services/category.service';import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { ButtonComponent } from '../../../../shared/components/button/button.component';
import { ErrorBannerComponent } from '../../../../shared/components/error-banner/error-banner.component';
import { ColorPickerComponent } from '../../../../shared/components/color-picker/color-picker.component';
import { EmptyStateComponent } from '../../../../shared/components/empty-state/empty-state.component';
import { PageContentComponent } from '../page-content/page-content.component';
import { Category, CategoryRequest } from '../../../../models/category.model';
import { CrudPageBase } from '../../../../shared/utils/crud-page.base';
import { humanizeError } from '../../../../shared/utils/error.util';

const DEFAULT_COLORS = [
  '#e89c2a', '#8b5cf6', '#19a563', '#5e6b7a',
  '#dc4444', '#3b82f6', '#e6448e', '#14b8b8',
];

/** CRUD page for AI email categories with accuracy scoring, color picker, and import/export. */
@Component({
  selector: 'app-categories-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, ButtonComponent, ErrorBannerComponent, ColorPickerComponent, EmptyStateComponent, PageContentComponent],
  templateUrl: './categories-page.component.html',
  styleUrl: './categories-page.component.scss',
})
export class CategoriesPageComponent extends CrudPageBase {
  private categoryService = inject(CategoryService);
  readonly colors = DEFAULT_COLORS;

  categories = signal<Category[]>([]);

  // Form fields
  name = signal('');
  color = signal(DEFAULT_COLORS[0]);
  description = signal('');
  positiveExample = signal('');
  negativeExample = signal('');

  // New: focus tracking for insight panel
  focused = signal<null | 'description' | 'positive' | 'negative'>(null);

  // New: animated accuracy count-up
  animatedAccuracy = signal(0);

  // Accuracy scoring
  descScore = computed(() => {
    const len = this.description().length;
    return len >= 30 ? 60 : Math.round((len / 30) * 60);
  });

  posBoost = computed(() => this.positiveExample().trim().length > 0 ? 22 : 0);
  negBoost = computed(() => this.negativeExample().trim().length > 0 ? 15 : 0);

  accuracy = computed(() => Math.min(this.descScore() + this.posBoost() + this.negBoost(), 97));

  // Accuracy tone
  accuracyTone = computed(() => {
    const v = this.accuracy();
    return v < 50 ? 'warn' : v < 80 ? 'mid' : 'ok';
  });

  canSave = computed(() => this.name().trim().length > 0 && this.description().trim().length >= 30);

  constructor() {
    super();
    this.loadCategories();
    this.reloadOnAiMutation('categories', () => this.loadCategories());

    // Count-up animation effect
    let rafId = 0;
    effect(() => {
      const target = this.accuracy();
      const from = this.animatedAccuracy();
      if (from === target) return;
      const startTime = performance.now();
      const startVal = from;
      const duration = 600;

      cancelAnimationFrame(rafId);

      const tick = (now: number) => {
        const t = Math.min(1, (now - startTime) / duration);
        const eased = 1 - Math.pow(1 - t, 3); // easeOutCubic
        const val = Math.round(startVal + (target - startVal) * eased);
        this.animatedAccuracy.set(val);
        if (t < 1) {
          rafId = requestAnimationFrame(tick);
        }
      };

      rafId = requestAnimationFrame(tick);
    });
  }

  toggleLock(cat: Category): void {
    this.categoryService.toggleLock(cat.id).subscribe(updated => this.replaceInList(this.categories, updated));
  }

  editCategory(cat: Category): void {
    this.resetForm();
    this.editId.set(cat.id);
    this.name.set(cat.name);
    this.color.set(cat.color);
    this.description.set(cat.description);
    this.positiveExample.set(cat.positiveExample || '');
    this.negativeExample.set(cat.negativeExample || '');
    this.view.set('form');
  }

  deleteCategory(id: string): void {
    this.deleteWithConfirm('cat_delete_confirm', () => this.categoryService.delete(id), () => this.loadCategories());
  }

  submit(event: Event): void {
    event.preventDefault();
    this.error.set('');
    this.fieldErr.set({});

    const errs: Record<string, boolean> = {};

    if (!this.name() || this.name().length < 3) errs['name'] = true;
    if (!this.description() || this.description().length < 30) errs['description'] = true;

    if (Object.keys(errs).length > 0) {
      this.error.set(this.i18n.t('cat_error_required'));
      this.fieldErr.set(errs);
      return;
    }

    this.loading.set(true);

    const req: CategoryRequest = {
      name: this.name(),
      color: this.color(),
      description: this.description(),
    };

    if (this.positiveExample()) req.positiveExample = this.positiveExample();
    if (this.negativeExample()) req.negativeExample = this.negativeExample();

    const op$ = this.editId()
      ? this.categoryService.update(this.editId()!, req)
      : this.categoryService.create(req);

    op$.subscribe({
      next: () => {
        this.loading.set(false);
        this.editId.set(null);
        this.loadCategories();
        this.view.set('list');
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(humanizeError(err, this.i18n.t('cat_error_required')));
      },
    });
  }

  private loadCategories(): void {
    this.loadList(this.categoryService.list(), this.categories);
  }

  exportData(): void {
    this.exportJson(this.categoryService.export(), 'categories.json');
  }

  importData(event: Event): void {
    this.importJson(event, (d) => this.categoryService.import(d), 'cat_import_error', () => this.loadCategories());
  }

  protected override resetForm(): void {
    this.name.set('');
    this.color.set(DEFAULT_COLORS[Math.floor(Math.random() * DEFAULT_COLORS.length)]);
    this.description.set('');
    this.positiveExample.set('');
    this.negativeExample.set('');
    this.error.set('');
    this.fieldErr.set({});
    this.focused.set(null);
  }
}
