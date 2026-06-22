output "static_ip" {
  description = "Point the postwerk.io A-record (DNS-only / grey cloud) at this IP."
  value       = google_compute_address.static_ip.address
}

output "ssh_command" {
  description = "Connect to the VM."
  value       = "gcloud compute ssh ${var.instance_name} --project=${var.project_id} --zone=${var.zone}"
}
