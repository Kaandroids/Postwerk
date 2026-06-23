# ---------------------------------------------------------------------------
# Private container registry. CI builds images ONCE and pushes here; the VM
# pulls with its own service account (no public images, no registry password).
# Image prefix: europe-west3-docker.pkg.dev/postwerk/<ar_repo_id>
# ---------------------------------------------------------------------------
resource "google_artifact_registry_repository" "docker" {
  location      = var.region
  repository_id = var.ar_repo_id
  format        = "DOCKER"
  description   = "Postwerk container images — built in GitHub Actions, pulled by the VM."

  depends_on = [google_project_service.required]
}

# The VM's identity may PULL images.
resource "google_artifact_registry_repository_iam_member" "vm_reader" {
  location   = google_artifact_registry_repository.docker.location
  repository = google_artifact_registry_repository.docker.repository_id
  role       = "roles/artifactregistry.reader"
  member     = "serviceAccount:${google_service_account.vm.email}"
}

# The GitHub Actions deployer (via WIF) may PUSH images.
resource "google_artifact_registry_repository_iam_member" "deployer_writer" {
  location   = google_artifact_registry_repository.docker.location
  repository = google_artifact_registry_repository.docker.repository_id
  role       = "roles/artifactregistry.writer"
  member     = "serviceAccount:${google_service_account.github_deployer.email}"
}
