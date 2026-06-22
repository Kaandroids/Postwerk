# Remote state in Google Cloud Storage — so state is never lost, is versioned,
# and supports locking. The bucket must exist BEFORE `terraform init`.
#
# One-time bootstrap (see README.md, step 2):
#   gcloud storage buckets create gs://postwerk-tfstate \
#     --project=postwerk --location=europe-west3 --uniform-bucket-level-access
#   gcloud storage buckets update gs://postwerk-tfstate --versioning
#
# NOTE: GCS bucket names are GLOBALLY unique. If "postwerk-tfstate" is taken,
# pick another name here AND in the bootstrap command.
terraform {
  backend "gcs" {
    bucket = "postwerk-tfstate"
    prefix = "compute/state"
  }
}
