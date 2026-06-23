output "static_ip" {
  description = "Point the postwerk.io A-record (DNS-only / grey cloud) at this IP."
  value       = google_compute_address.static_ip.address
}

output "ssh_command" {
  description = "Connect to the VM (use --tunnel-through-iap once SSH is closed to the internet)."
  value       = "gcloud compute ssh ${var.instance_name} --project=${var.project_id} --zone=${var.zone} --tunnel-through-iap"
}

# --- CI/CD (keyless) wiring — set these as GitHub Actions repo VARIABLES ---
output "wif_provider" {
  description = "GitHub Actions variable GCP_WIF_PROVIDER."
  value       = google_iam_workload_identity_pool_provider.github.name
}

output "deployer_sa_email" {
  description = "GitHub Actions variable GCP_DEPLOYER_SA."
  value       = google_service_account.github_deployer.email
}

output "artifact_registry" {
  description = "GitHub Actions variable GCP_AR — image prefix (push <prefix>/<service>:<tag>)."
  value       = "${var.region}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.docker.repository_id}"
}
