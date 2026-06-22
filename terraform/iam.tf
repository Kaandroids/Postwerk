# ---------------------------------------------------------------------------
# Secret Manager + the VM's identity.
# The VM reads secrets (DB password, JWT, encryption key, Gemini key) at deploy
# time via its service account — keyless, audit-logged, versioned.
# ---------------------------------------------------------------------------

resource "google_project_service" "secretmanager" {
  service            = "secretmanager.googleapis.com"
  disable_on_destroy = false
}

# Least-privilege identity attached to the VM (see service_account block in main.tf)
resource "google_service_account" "vm" {
  account_id   = "${var.instance_name}-sa"
  display_name = "Postwerk VM service account"
}

# Allow the VM identity to READ secret values (not manage them).
# Project-level for simplicity; tighten to per-secret bindings later if desired.
resource "google_project_iam_member" "vm_secret_accessor" {
  project = var.project_id
  role    = "roles/secretmanager.secretAccessor"
  member  = "serviceAccount:${google_service_account.vm.email}"
}
