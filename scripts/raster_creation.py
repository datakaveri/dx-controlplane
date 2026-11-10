#!/usr/bin/env python3
import requests
import sys

BASE_URL = "https://v2.dev.ogc.iudx.io/processes"
AUTH_TOKEN = "<PUT_YOUR_BEARER_TOKEN_HERE>"
RESOURCE_ID = "0a5bd026-f448-40b2-854d-983e71c57ee7"
FILE_PATH = "/home/cdpg-user/Documents/datakaveri/Extra/OGC-RS_Onboarding/sample_raster.tif"
BUCKET_NAME = "iudx-v2-ogc-rs"
REGION = "ap-south-1"

headers = {
    "Authorization": f"Bearer {AUTH_TOKEN}",
    "Content-Type": "application/json"
}

# Step 1: Get processes
resp = requests.get(BASE_URL, headers=headers)
resp.raise_for_status()
processes = resp.json()

s3_id = None
onboarding_id = None
for proc in processes:
    if proc.get("title") == "S3PresignedUrl":
        s3_id = proc["id"]
    if proc.get("title") == "CollectionOnboarding":
        onboarding_id = proc["id"]

if not s3_id or not onboarding_id:
    print("❌ Could not find required process IDs")
    sys.exit(1)

print(f"✅ Found S3PresignedUrl ID: {s3_id}")
print(f"✅ Found CollectionOnboarding ID: {onboarding_id}")

# Step 2: Call S3PresignedUrl execution for raster data
s3_exec_url = f"{BASE_URL}/{s3_id}/execution"
s3_payload = {
    "inputs": {
        "resourceId": RESOURCE_ID,
        "bucketName": BUCKET_NAME,
        "region": REGION,
        "fileType": "GeoTIFF",
        "version": "1.0.0",
        "s3BucketIdentifier": "default"
    }
}
s3_resp = requests.post(s3_exec_url, headers=headers, json=s3_payload)
s3_resp.raise_for_status()
s3_data = s3_resp.json()

s3_url = s3_data.get("outputs", {}).get("S3PreSignedUrl")
if not s3_url:
    print("❌ Could not extract S3PreSignedUrl")
    sys.exit(1)

print(f"✅ Got S3PreSignedUrl: {s3_url}")

# Step 3: Upload raster file to S3 PreSigned URL
with open(FILE_PATH, "rb") as f:
    upload_resp = requests.put(s3_url, data=f, headers={"Content-Type": "application/octet-stream"})
    if upload_resp.status_code == 200:
        print("✅ Raster file uploaded successfully to S3")
    else:
        print(f"❌ Upload failed: {upload_resp.status_code}, {upload_resp.text}")
        sys.exit(1)

# Step 4: Call CollectionOnboarding execution for raster data
onboarding_exec_url = f"{BASE_URL}/{onboarding_id}/execution"
onboarding_payload = {
    "inputs": {
        "fileName": f"{RESOURCE_ID}.tif",
        "title": "Raster Data Collection",
        "description": "Raster data collection for OGC onboarding",
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
