#!/usr/bin/env python3
import requests
import sys
import time


BASE_DOMAIN = "<OGC_DATA_PLANE_DOMAIN_HERE>"
CONTROLPLANE_DOMAIN = "<CONTROL_PLANE_DOMAIN_HERE>"

BASE_URL = f"{BASE_DOMAIN}/processes"
CONTROLPLANE_URL = f"{CONTROLPLANE_DOMAIN}/iudx/v2/cat/organisation/asset"
AUTH_TOKEN = "<PUT_YOUR_BEARER_TOKEN_HERE>"
RESOURCE_ID = "<PUT_RESOURCE_ID_HERE>"
FILE_PATH = "/path/to/your/vector/file.gpkg"
BUCKET_NAME = "iudx-v2-ogc-rs"
REGION = "ap-south-1"
TITLE= "<PUT_TITLE_HERE>"
DESCRIPTION = "<PUT_DESCRIPTION_HERE>"

headers = {
    "Authorization": f"Bearer {AUTH_TOKEN}",
    "Content-Type": "application/json"
}

# Step 1: Get processes
resp = requests.get(BASE_URL, headers=headers)
resp.raise_for_status()
processes = resp.json().get("processes")

s3_id = None
onboarding_id = None
for proc in processes:
    if proc.get("title") == "S3PreSignedURLGeneration":
        s3_id = proc["id"]
    if proc.get("title") == "CollectionOnboarding":
        onboarding_id = proc["id"]

if not s3_id or not onboarding_id:
    print("❌ Could not find required process IDs")
    sys.exit(1)

print(f"✅ Found S3PresignedUrl ID: {s3_id}")
print(f"✅ Found CollectionOnboarding ID: {onboarding_id}")

# Step 2: Call S3PresignedUrl execution
s3_exec_url = f"{BASE_URL}/{s3_id}/execution"
s3_payload = {
    "inputs": {
        "resourceId": RESOURCE_ID,
        "bucketName": BUCKET_NAME,
        "region": REGION,
        "fileType": "GeoPackage",
        "version": "1.0.0",
        "s3BucketIdentifier": "default"
    }
}
s3_resp = requests.post(s3_exec_url, headers=headers, json=s3_payload)
s3_resp.raise_for_status()
s3_data = s3_resp.json()

s3_url = s3_data.get("S3PreSignedUrl")
if not s3_url:
    print("❌ Could not extract S3PreSignedUrl")
    sys.exit(1)

print(f"✅ Got S3PreSignedUrl")

# Step 3: Upload file to S3 PreSigned URL
with open(FILE_PATH, "rb") as f:
    upload_resp = requests.put(s3_url, data=f, headers={"Content-Type": "application/octet-stream"})
    if upload_resp.status_code == 200:
        print("✅ File uploaded successfully to S3")
    else:
        print(f"❌ Upload failed: {upload_resp.status_code}, {upload_resp.text}")
        sys.exit(1)

# Step 4: Call CollectionOnboarding execution
onboarding_exec_url = f"{BASE_URL}/{onboarding_id}/execution"
onboarding_payload = {
    "inputs": {
        "fileName": f"{RESOURCE_ID}.gpkg",
        "title": TITLE,
        "description": DESCRIPTION,
        "resourceId": RESOURCE_ID,
        "s3BucketIdentifier": "default",
        "version": "1.0.0"
    },
    "response": "raw"
}

onboarding_resp = requests.post(onboarding_exec_url, headers=headers, json=onboarding_payload)
onboarding_resp.raise_for_status()

print("✅ CollectionOnboarding response:")
print(onboarding_resp.json())

job_info = onboarding_resp.json()
job_id = job_info.get("jobId")

if not job_id:
    print("❌ Could not extract job ID from onboarding response")
    sys.exit(1)

print(f"⏳ Waiting for job {job_id} to complete...")

status_url = f"https://v2.dev.ogc.iudx.io/jobs/{job_id}"

# Step 5: Poll job status until completion
while True:
    job_resp = requests.get(status_url, headers=headers)
    job_resp.raise_for_status()
    job_data = job_resp.json()

    status = job_data.get("status")
    progress = job_data.get("progress", 0)
    message = job_data.get("message", "")

    print(f"Progress: {progress}%")

    if status in ["SUCCESSFUL", "FAILED", "DISMISSED"]:
        print(f"✅ Job completed with status: {status}")
        print(f"📄 Message: {message}")

        if status == "SUCCESSFUL":
            print("🎉 Onboarding process completed successfully.")

            # Step 6: Mark dataUploadStatus=true on ControlPlane
            patch_url = f"{CONTROLPLANE_URL}?id={RESOURCE_ID}"
            patch_payload = {"dataUploadStatus": True}
            patch_resp = requests.patch(patch_url, headers=headers, json=patch_payload)
            print(patch_resp)
            if patch_resp.status_code == 200:
                print("✅ Successfully updated item in Catalogue.")
            else:
                print(f"⚠️ Failed to update ControlPlane: {patch_resp.status_code}, {patch_resp.text}")

        else:
            print("❌ Onboarding process failed. Please check logs or job message above.")
        break

    time.sleep(10)  # Wait 10 seconds before polling again
