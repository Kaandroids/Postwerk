import { test, expect } from '../../fixtures/test-fixtures';
import { AdminBackgroundJobsPage } from '../../pages';

test.describe('Admin Background Jobs', () => {
  let bj: AdminBackgroundJobsPage;

  test.beforeEach(async ({ adminPage }) => {
    bj = new AdminBackgroundJobsPage(adminPage);
    await adminPage.goto('/dashboard/admin/jobs');
  });

  test('should render the KPI strip', async () => {
    await expect(bj.kpis).toBeVisible();
    await expect(bj.kpis).toContainText('4'); // scheduled jobs
  });

  test('should render the failing-job alert strip', async () => {
    await expect(bj.alert).toBeVisible();
    await expect(bj.alert).toContainText('Data-retention sweep');
  });

  test('should render the queue summary cards', async () => {
    await expect(bj.queues).toBeVisible();
    await expect(bj.queues).toContainText('Approval queue');
    await expect(bj.queues).toContainText('Delayed-email queue');
  });

  test('should render the jobs table rows', async () => {
    await expect(bj.table).toBeVisible();
    await expect(bj.rows).toHaveCount(4);
    await expect(bj.table).toContainText('Email-sync scheduler');
    await expect(bj.table).toContainText('Automation poller');
  });

  test('should open the job detail modal with recent runs', async () => {
    await bj.rows.first().click();
    await expect(bj.modal).toBeVisible();
    await expect(bj.modal).toContainText('Data-retention sweep');
    // Recent-runs tab shows the failure message from the detail mock.
    await expect(bj.modal).toContainText('FK violation');
  });
});
