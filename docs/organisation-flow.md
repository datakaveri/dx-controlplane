# Organisation Flow — End-to-End Documentation

> **Module:** `org.cdpg.dx.aaa.organization`  
> **Base path:** `/iudx/v2/auth`  
> **Framework:** Vert.x (async / Future-based)

---

## Table of Contents

1. [Overview](#overview)
2. [Domain Model & Enums](#domain-model--enums)
3. [Database Schema](#database-schema)
4. [Architecture Layers](#architecture-layers)
5. [APIs — Request & Response](#apis--request--response)
   - [Organisation Create Requests](#1-organisation-create-requests)
   - [Organisation (Core)](#2-organisation-core)
   - [Organisation Join Requests](#3-organisation-join-requests)
   - [Organisation Users](#4-organisation-users)
   - [Provider Role Requests](#5-provider-role-requests)
   - [Reports (CSV exports)](#6-reports-csv-exports)
6. [Key Workflows](#key-workflows)
7. [Authorization & Access Control](#authorization--access-control)
8. [Audit Logging](#audit-logging)
9. [External Integrations](#external-integrations)
10. [Error Responses](#error-responses)

---

## Overview

The organisation module manages the full lifecycle of organisations on the Data Exchange (DX) platform:

| Domain | What it covers |
|---|---|
| **Organisation Create** | Users request platform entry as a new organisation; COS Admin approves/rejects |
| **Organisation Join** | Users request to join an existing org; Org Admin approves/rejects |
| **Organisation Management** | CRUD on approved organisations |
| **Organisation Users** | Member management, role assignment, removal |
| **Provider Role** | Members escalate from Consumer → Provider within their org |

Every operation that mutates state is audit-logged and triggers transactional side-effects in Keycloak.

---

## Domain Model & Enums

### `Role` (enum)

| Value | String | Meaning |
|---|---|---|
| `ADMIN` | `"admin"` | Organisation administrator |
| `USER` | `"member"` | Regular organisation member |

### `Status` (enum)

| Value | String | Applies to |
|---|---|---|
| `PENDING` | `"pending"` | All request types |
| `GRANTED` | `"granted"` | All request types |
| `REJECTED` | `"rejected"` | All request types |
| `WITHDRAWN` | `"withdrawn"` | Join requests only |

### `OrganisationAuditOperation` (enum)

Used to tag every audit log entry. Values include:
`UPDATE_ORG_CREATE_REQUEST`, `UPDATE_ORG_JOIN_REQUEST`, `UPDATE_PROVIDER_REQUEST`,
`REQUEST_ORG_JOIN`, `REQUEST_ORG_CREATE`, `REQUEST_PROVIDER_ROLE`, `CREATE_PROVIDER_ROLE`,
`WITHDRAW_PENDING_ORG_JOIN_REQUEST`, `WITHDRAW_PENDING_ORG_CREATE_REQUEST`,
`GET`, `GET_PROVIDER_REQS`, `GET_ORG`, `GET_USER_INFO`, `GET_USERS`,
`UPDATE_USER_INFO`, `DELETE_USER`, `DELETE_PENDING_ORG_CREATE_REQUEST`,
`DELETE_PENDING_ORG_JOIN_REQUEST`, `DELETE_PENDING_PROVIDER_REQUEST`,
`DELETE_ORG`, `UPDATE_ORG`

---

## Domain Model Details

### `Organization`

| Field | Type | DB Column | Notes |
|---|---|---|---|
| `id` | `UUID` | `id` | PK, auto-generated |
| `orgName` | `String` | `name` | Unique across platform |
| `orgLogo` | `String` | `logo_path` | Nullable |
| `entityType` | `String` | `entity_type` | Required |
| `orgSector` | `String` | `org_sector` | Required |
| `websiteLink` | `String` | `website_link` | Required |
| `address` | `String` | `address` | Required |
| `certificatePath` | `String` | `certificate_path` | Required |
| `pancardPath` | `String` | `pancard_path` | Required |
| `relevantDocPath` | `String` | `relevant_doc_path` | Nullable |
| `orgDocuments` | `String` | `organisation_documents` | Required |
| `createdAt` | `LocalDateTime` | `created_at` | Auto-set |
| `updatedAt` | `LocalDateTime` | `updated_at` | Auto-updated |

`toFilteredJson()` — returns a subset without sensitive document paths for public-facing list responses.

---

### `OrganizationCreateRequest`

| Field | Type | DB Column | Notes |
|---|---|---|---|
| `id` | `UUID` | `id` | PK |
| `requestedBy` | `UUID` | `requested_by` | FK → Keycloak user |
| `name` | `String` | `name` | Proposed org name |
| `logoPath` | `String` | `logo_path` | Nullable |
| `entityType` | `String` | `entity_type` | |
| `orgSector` | `String` | `org_sector` | |
| `websiteLink` | `String` | `website_link` | |
| `address` | `String` | `address` | |
| `certificatePath` | `String` | `certificate_path` | |
| `pancardPath` | `String` | `pancard_path` | |
| `relevantDocPath` | `String` | `relevant_doc_path` | Nullable |
| `status` | `String` | `status` | `pending \| granted \| rejected` |
| `userName` | `String` | `user_name` | Fetched from Keycloak |
| `empId` | `String` | `emp_id` | |
| `jobTitle` | `String` | `job_title` | |
| `orgManagerphoneNo` | `String` | `phone_no` | Defaults to `"*"` if absent |
| `managerEmail` | `String` | `manager_email` | Required, unique |
| `orgDocuments` | `String` | `organisation_documents` | |
| `createdAt` | `LocalDateTime` | `created_at` | |
| `updatedAt` | `LocalDateTime` | `updated_at` | |

---

### `OrganizationJoinRequest`

| Field | Type | DB Column | Notes |
|---|---|---|---|
| `id` | `UUID` | `id` | PK |
| `organizationId` | `UUID` | `organization_id` | FK → organizations |
| `userId` | `UUID` | `user_id` | FK → Keycloak user |
| `userName` | `String` | `user_name` | |
| `status` | `String` | `status` | `pending \| granted \| rejected \| withdrawn` |
| `jobTitle` | `String` | `job_title` | |
| `empId` | `String` | `emp_id` | |
| `officialEmail` | `String` | `official_email` | Unique |
| `requestedAt` | `LocalDateTime` | `requested_at` | |
| `processedAt` | `LocalDateTime` | `processed_at` | Nullable |

---

### `OrganizationUser`

| Field | Type | DB Column | Notes |
|---|---|---|---|
| `id` | `UUID` | `id` | PK |
| `organizationId` | `UUID` | `organization_id` | FK → organizations |
| `userId` | `UUID` | `user_id` | FK → Keycloak user, unique |
| `userName` | `String` | `user_name` | |
| `role` | `Role` | `role` | `admin \| member` |
| `jobTitle` | `String` | `job_title` | |
| `empId` | `String` | `emp_id` | |
| `orgManagerPhoneNo` | `String` | `phone_no` | Nullable |
| `officialEmail` | `String` | `official_email` | Unique |
| `createdAt` | `LocalDateTime` | `created_at` | |
| `updatedAt` | `LocalDateTime` | `updated_at` | |

---

### `ProviderRoleRequest`

| Field | Type | DB Column | Notes |
|---|---|---|---|
| `id` | `UUID` | `id` | PK |
| `userId` | `UUID` | `user_id` | FK → Keycloak user |
| `orgId` | `UUID` | `organization_id` | FK → organizations |
| `status` | `String` | `status` | `pending \| granted \| rejected` |
| `createdAt` | `LocalDateTime` | `created_at` | |
| `updatedAt` | `LocalDateTime` | `updated_at` | |

Table: `provider_requests`

---

### Update DTOs

| DTO | Fields |
|---|---|
| `UpdateOrgDTO` | `orgName`, `orgLogo`, `entityType`, `orgSector`, `websiteLink`, `address`, `certificatePath`, `pancardPath`, `relevantDocPath`, `updatedAt` |
| `UpdateOrgCreateRequestDTO` | All `Optional<String>`: `name`, `logoPath`, `entityType`, `orgSector`, `websiteLink`, `address`, `certificatePath`, `pancardPath`, `relevantDocPath`, `status`, `empId`, `jobTitle`, `orgManagerphoneNo`, `updatedAt` |
| `UpdateOrgJoinRequestDTO` | `status`, `jobTitle`, `empId`, `Optional<String> processedAt` |
| `UpdateOrgUserDTO` | `role (Role)`, `jobTitle`, `empId`, `Optional<String> updatedAt` |

---

## Database Schema

### Final state (after all migrations V1→V60)

```sql
-- ============================================================
-- TABLE: organizations
-- ============================================================
CREATE TABLE organizations (
    id                    UUID    DEFAULT gen_random_uuid() PRIMARY KEY,
    name                  VARCHAR NOT NULL UNIQUE,
    logo_path             VARCHAR,
    entity_type           VARCHAR NOT NULL,
    org_sector            VARCHAR NOT NULL,
    website_link          VARCHAR NOT NULL,
    address               VARCHAR NOT NULL,
    certificate_path      VARCHAR NOT NULL,
    pancard_path          VARCHAR NOT NULL,
    relevant_doc_path     VARCHAR,
    organisation_documents VARCHAR NOT NULL,       -- added V6, made NOT NULL V7
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- TABLE: organization_create_requests
-- ============================================================
CREATE TABLE organization_create_requests (
    id                    UUID    DEFAULT gen_random_uuid() PRIMARY KEY,
    requested_by          UUID    NOT NULL,
    name                  VARCHAR NOT NULL,         -- unique constraint removed V8
    logo_path             VARCHAR,
    entity_type           VARCHAR NOT NULL,
    org_sector            VARCHAR NOT NULL,
    website_link          VARCHAR NOT NULL,
    address               VARCHAR NOT NULL,
    certificate_path      VARCHAR NOT NULL,
    pancard_path          VARCHAR NOT NULL,
    relevant_doc_path     VARCHAR,
    user_name             VARCHAR NOT NULL,
    emp_id                VARCHAR NOT NULL,
    job_title             VARCHAR NOT NULL,
    phone_no              VARCHAR NOT NULL,
    manager_email         VARCHAR NOT NULL,         -- added V9, unique constraint removed V12
    organisation_documents VARCHAR NOT NULL,        -- added V6, made NOT NULL V7
    status                VARCHAR NOT NULL CHECK (status IN ('pending','granted','rejected')),
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- TABLE: organization_join_requests
-- ============================================================
CREATE TABLE organization_join_requests (
    id               UUID    DEFAULT gen_random_uuid() PRIMARY KEY,
    organization_id  UUID    NOT NULL REFERENCES organizations(id),
    user_id          UUID    NOT NULL,              -- unique constraint removed V11
    user_name        VARCHAR NOT NULL,
    job_title        VARCHAR NOT NULL,
    emp_id           VARCHAR NOT NULL,
    official_email   VARCHAR NOT NULL,              -- added V13
    status           VARCHAR NOT NULL CHECK (status IN ('pending','granted','rejected','withdrawn')),  -- withdrawn added V44
    requested_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at     TIMESTAMP
);

-- ============================================================
-- TABLE: organization_users
-- ============================================================
CREATE TABLE organization_users (
    id               UUID    DEFAULT gen_random_uuid() PRIMARY KEY,
    organization_id  UUID    NOT NULL REFERENCES organizations(id),
    user_id          UUID    NOT NULL UNIQUE,
    user_name        VARCHAR NOT NULL,
    job_title        VARCHAR NOT NULL,
    emp_id           VARCHAR NOT NULL,
    phone_no         VARCHAR,
    official_email   VARCHAR NOT NULL UNIQUE,       -- added V13
    role             VARCHAR NOT NULL CHECK (role IN ('admin','member')),
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- TABLE: provider_requests
-- ============================================================
CREATE TABLE provider_requests (
    id               UUID    DEFAULT gen_random_uuid() PRIMARY KEY,
    organization_id  UUID    NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id          UUID    NOT NULL REFERENCES organization_users(user_id) ON DELETE CASCADE,
    status           VARCHAR NOT NULL CHECK (status IN ('pending','granted','rejected')),
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- renamed from requested_at V14
    updated_at       TIMESTAMP                                        -- renamed from processed_at V14
);
```

### Key DB Constraints Summary

| Table | Constraint | Detail |
|---|---|---|
| `organizations` | `UNIQUE(name)` | Org names are platform-unique |
| `organization_users` | `UNIQUE(user_id)` | A user can be in only one org |
| `organization_users` | `UNIQUE(official_email)` | Official emails are unique across all orgs |
| `organization_join_requests` | Status CHECK | `pending`, `granted`, `rejected`, `withdrawn` |
| `provider_requests` | `ON DELETE CASCADE` | Deleted when org or user removed |

---

## Architecture Layers

```
HTTP Request
    │
    ▼
OrganizationController / OrganizationReportController
    │  (route binding, auth chain, KYC guard)
    ▼
Handler (OrganizationCreateRequestHandler / OrganizationJoinRequestHandler /
         OrganizationCommandHandler / OrganizationQueryHandler /
         OrganizationUserHandler / ProviderRoleHandler)
    │  (request parsing, validation, business rules, email triggers)
    ▼
OrganizationServiceImpl
    │  (delegates to sub-services)
    ├──► OrganizationLifecycleServiceImpl   (org & create-request CRUD)
    ├──► OrganizationMembershipServiceImpl  (join requests & user management)
    └──► ProviderRoleServiceImpl            (provider role lifecycle)
    │
    ▼
DAO Layer (AbstractBaseDAO + custom overrides)
    │
    ▼
PostgresService (reactive pg client)
    │
    ▼
PostgreSQL Database
```

Side-effects at service layer:
- **KeycloakUserService** — role assignment/removal, user attribute sync
- **EmailComposer** — confirmation & approval emails
- **ItemService** — ownership transfer on provider user removal

---

## APIs — Request & Response

### Common Response Envelope

All successful responses return:

```json
{
  "type": "urn:dx:controlPlane:success",
  "title": "Success",
  "result": { ... }          // object or array
}
```

Error responses:

```json
{
  "type": "urn:dx:controlPlane:<errorCode>",
  "title": "<Human title>",
  "detail": "<message>"
}
```

### Pagination Object (where applicable)

```json
{
  "paginationInfo": {
    "page": 1,
    "size": 10,
    "totalCount": 346,
    "totalPages": 35,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

---

## 1. Organisation Create Requests

### POST `/iudx/v2/auth/organisations/requests`

**Summary:** User submits a new organisation creation request.  
**Auth:** Authenticated user token. KYC check required.  
**Audit op:** `REQUEST_ORG_CREATE`

**Request Body:**

```json
{
  "name": "Quantum Labs",
  "logo_path": "/assets/logos/quantumlabs.png",
  "entity_type": "Startup",
  "org_sector": "Research & Development",
  "website_link": "https://quantumlabs.io",
  "address": "42 Tech Boulevard, Hyderabad, India",
  "certificate_path": "/docs/certificates/quantum_cert.pdf",
  "pancard_path": "/docs/ids/quantum_pancard.pdf",
  "emp_id": "EMP98765",
  "job_title": "Operations Lead",
  "phone_no": "+91-9123456789",
  "organisation_documents": "/docs/organisations/quantum_docs.zip",
  "manager_email": "manager@gmail.com",
  "relevant_doc_path": null
}
```

**Required fields:** `name`, `entity_type`, `org_sector`, `website_link`, `address`, `certificate_path`, `pancard_path`, `emp_id`, `job_title`, `organisation_documents`, `manager_email`

**Handler validation steps:**
1. Parse body and validate required fields
2. Fetch `userName` from Keycloak using authenticated user ID
3. Check: no existing `PENDING` or `GRANTED` request for this user
4. Check: org name is unique among `PENDING`/`GRANTED` requests
5. Check: `manager_email` is unique
6. Insert into `organization_create_requests` (status = `pending`)
7. Send confirmation email to user

**Response 200:**

```json
{
  "type": "urn:dx:controlPlane:success",
  "title": "Success",
  "result": {
    "id": "4202493c-7c64-49b0-8f61-2000ca3d147c",
    "requestedBy": "c908f4ed-69ef-4d27-8e50-8f95f89b6c4d",
    "name": "Quantum Labs",
    "logoPath": "/assets/logos/quantumlabs.png",
    "entityType": "Startup",
    "orgSector": "Research & Development",
    "websiteLink": "https://quantumlabs.io",
    "address": "42 Tech Boulevard, Hyderabad, India",
    "certificatePath": "/docs/certificates/quantum_cert.pdf",
    "pancardPath": "/docs/ids/quantum_pancard.pdf",
    "relevantDocPath": null,
    "status": "pending",
    "userName": "John Doe",
    "empId": "EMP98765",
    "jobTitle": "Operations Lead",
    "orgManagerphoneNo": "+91-9123456789",
    "managerEmail": "manager@gmail.com",
    "orgDocuments": "/docs/organisations/quantum_docs.zip",
    "createdAt": "2025-06-03T16:25:25.167724",
    "updatedAt": "2025-06-03T16:25:25.167724",
    "tableName": "organization_create_requests"
  }
}
```

**Error Responses:**

| Status | Type | Condition |
|---|---|---|
| 401 | `urn:dx:as:InvalidAuthenticationToken` | Token invalid/expired |
| 409 | `urn:dx:controlPlane:conflict` | Duplicate org name or pending request exists |

---

### GET `/iudx/v2/auth/organisations/requests`

**Summary:** List all org creation requests (paginated).  
**Auth:** COS Admin only.  
**Audit op:** `GET`

**Query Parameters:**

| Param | Type | Description |
|---|---|---|
| `page` | integer | Page number |
| `size` | integer | Items per page |
| `sort` | string | Sort field + direction |
| `entityType` | string | Filter by entity type |
| `status` | string | Filter by status |
| `orgName` | string | Filter by name |
| `orgSector` | string | Filter by sector |
| `searchTerm` | string | Free-text search |

**Response 200:** Array of `OrganizationCreateRequest` objects (full schema) + `paginationInfo`

```json
{
  "type": "urn:dx:controlPlane:success",
  "title": "Success",
  "result": [
    {
      "id": "41290efb-057b-4296-872c-a764f6ff5615",
      "requestedBy": "c908f4ed-69ef-4d27-8e50-8f95f89b6c4d",
      "name": "Quant Sandbox",
      "status": "pending",
      "userName": "test test",
      "empId": "EMP12345",
      "jobTitle": "Manager",
      "managerEmail": "manager@gmail.com",
      "createdAt": "2025-06-03T16:01:59.532782",
      "updatedAt": "2025-06-03T16:01:59.532782",
      "tableName": "organization_create_requests"
    }
  ],
  "paginationInfo": { "page": 1, "size": 1, "totalCount": 346, "totalPages": 346, "hasNext": true, "hasPrevious": false }
}
```

---

### GET `/iudx/v2/auth/user/organisations/requests`

**Summary:** Get the calling user's own create requests.  
**Auth:** Authenticated user.  
**Audit op:** `GET`

**Response 200:** Array of `OrganizationCreateRequest` objects (user-friendly JSON — no internal fields).

---

### DELETE `/iudx/v2/auth/user/organisations/requests/{id}`

**Summary:** User deletes their own pending create request.  
**Auth:** Authenticated user (owner only).  
**Audit op:** `DELETE_PENDING_ORG_CREATE_REQUEST`

**Path param:** `id` (UUID) — request ID

**Validation:**
- Request must be in `PENDING` status
- `requestedBy` must match calling user ID

**Response 200:** Empty success.

**Errors:**

| Status | Condition |
|---|---|
| 400 | Request not in PENDING status |
| 401 | Unauthorized |

---

### POST `/iudx/v2/auth/organisations/requests/approve`  *(legacy route)*

**Summary:** COS Admin approves or rejects an org creation request.  
**Auth:** COS Admin.  
**Audit op:** `UPDATE_ORG_CREATE_REQUEST`

**Request Body:**

```json
{
  "req_id": "41290efb-057b-4296-872c-a764f6ff5615",
  "status": "granted"
}
```

**On `status = "granted"` — side effects:**
1. Creates `Organization` record from request data
2. Creates `OrganizationUser` record (role = `admin`) for `requestedBy`
3. Assigns `ORG_ADMIN` role in Keycloak
4. Assigns `PROVIDER` role in Keycloak
5. Sets org attributes on Keycloak user (`organization_id`, `organization_name`)
6. Sends approval email to user

**Response 201:**

```json
{
  "type": "urn:dx:controlPlane:success",
  "title": "Success",
  "detail": "Updated Successfully"
}
```

**Errors:**

| Status | Condition |
|---|---|
| 400 | No request found with given ID |
| 401 | Unauthorized |
| 403 | Caller lacks `cos_admin` role |
| 409 | Org with same name already exists |

---

## 2. Organisation (Core)

### GET `/iudx/v2/auth/organisations`

**Summary:** List all organisations (authenticated user).  
**Auth:** Any authenticated user.  
**Audit op:** `GET_ORG`

**Query Parameters:** `page`, `size`, `sort`, `searchTerm`, `entityType`, `orgSector`

**Response 200:** Array of `Organization.toFilteredJson()` (excludes sensitive document paths) + pagination.

```json
{
  "type": "urn:dx:controlPlane:success",
  "title": "Success",
  "result": [
    {
      "id": "e87246e2-5da5-40b0-973c-690d2f48f879",
      "orgName": "Quant Sandbox",
      "orgLogo": "/logos/ai.png",
      "entityType": "Private",
      "orgSector": "Technology",
      "websiteLink": "https://ai.example.com",
      "address": "123 Quant Labs, Bangalore, India",
      "createdAt": "2025-06-03T16:28:24.205541",
      "updatedAt": "2025-06-03T16:28:24.205541",
      "tableName": "organizations"
    }
  ],
  "paginationInfo": { ... }
}
```

---

### GET `/iudx/v2/auth/organisations/{id}`

**Summary:** Get a single organisation by ID.  
**Auth:** Authenticated user.  
**Audit op:** `GET_ORG`

**Path param:** `id` (UUID)

**Response 200:**

```json
{
  "type": "urn:dx:controlPlane:success",
  "title": "Success",
  "result": {
    "id": "e87246e2-5da5-40b0-973c-690d2f48f879",
    "name": "Quant Sandbox",
    "logo_path": "/logos/ai.png",
    "entity_type": "Private",
    "org_sector": "Technology",
    "website_link": "https://ai.example.com",
    "address": "123 Quant Labs, Bangalore, India",
    "certificate_path": "/documents/certificate.pdf",
    "pancard_path": "/documents/pancard.pdf",
    "created_at": "2025-06-03T16:28:24.205541",
    "updated_at": "2025-06-03T16:28:24.205541"
  }
}
```

---

### PUT `/iudx/v2/auth/organisations/{id}`

**Summary:** Update organisation details.  
**Auth:** COS Admin.  
**Audit op:** `UPDATE_ORG`

**Path param:** `id` (UUID)

**Request Body** (all fields optional):

```json
{
  "name": "Updated Org Name",
  "logo_path": "/logos/new.png",
  "entity_type": "Private",
  "org_sector": "Healthcare",
  "website_link": "https://new.example.com",
  "address": "New Address",
  "certificate_path": "/docs/new_cert.pdf",
  "pancard_path": "/docs/new_pan.pdf",
  "relevant_doc_path": null
}
```

**Response 200:** Updated `Organization` object.

```json
{
  "type": "urn:dx:controlPlane:success",
  "title": "Success",
  "result": {
    "id": "e87246e2-5da5-40b0-973c-690d2f48f879",
    "orgName": "Updated Org Name",
    "orgSector": "Healthcare",
    "createdAt": "2025-06-03T16:28:24.205541",
    "updatedAt": "2025-06-04T09:15:00.000000",
    "tableName": "organizations"
  }
}
```

---

### DELETE `/iudx/v2/auth/organisations/{id}`

**Summary:** Delete an organisation.  
**Auth:** COS Admin **or** Org Admin (with org-scope check).  
**Audit op:** `DELETE_ORG`

**Path param:** `id` (UUID)  
**Query param:** `delegatorId` (UUID, optional — for delegated access)

**Response 200:** Empty success.

---

## 3. Organisation Join Requests

### POST `/iudx/v2/auth/organisations/{id}/join_requests`

**Summary:** User submits a join request for an organisation.  
**Auth:** Authenticated user. KYC check required.  
**Audit op:** `REQUEST_ORG_JOIN`

**Path param:** `id` (UUID) — organisation ID

**Request Body:**

```json
{
  "job_title": "Employee",
  "emp_id": "EMP8790",
  "official_email": "john.doe@company.com"
}
```

**Handler validation:**
1. Official email must be unique (not used by any `organization_users` record)
2. No active (`PENDING` or `GRANTED`) join request for this user + org combination
3. Insert into `organization_join_requests` (status = `pending`)
4. Send confirmation email

**Response 200:**

```json
{
  "type": "urn:dx:controlPlane:success",
  "title": "Success",
  "detail": "Created Join request"
}
```

**Errors:**

| Status | Condition |
|---|---|
| 400 | Invalid organisation ID |
| 401 | Unauthorized / KYC not complete |
| 409 | Duplicate join request or email already in use |

---

### GET `/iudx/v2/auth/organisations/{id}/join_requests`

**Summary:** Org Admin lists all join requests for their org.  
**Auth:** Org Admin.  
**Audit op:** `GET`

**Path param:** `id` (UUID)  
**Query params:** `status` (pending|granted|rejected), `page`, `size`, `sort`, `searchTerm`, `delegatorId`

**Response 200:** Array of join request objects enriched with user roles from Keycloak.

```json
{
  "type": "urn:dx:controlPlane:success",
  "title": "Success",
  "result": [
    {
      "id": "5fc228bb-9a20-4052-968e-427c733d2264",
      "organizationId": "e87246e2-5da5-40b0-973c-690d2f48f879",
      "userId": "ac2f3390-c7b0-4d4e-bb72-0bb8d9170309",
      "userName": "coder coder",
      "status": "pending",
      "jobTitle": "Employee",
      "empId": "EMP8790",
      "officialEmail": "member@gmail.com",
      "requestedAt": "2025-06-03T16:35:48.404509",
      "processedAt": null,
      "tableName": "organization_join_requests",
      "roles": ["consumer", "provider"]
    }
  ],
  "paginationInfo": { ... }
}
```

---

### PUT `/iudx/v2/auth/organisations/{org_id}/join_requests/{req_id}`

**Summary:** Org Admin approves or rejects a join request.  
**Auth:** Org Admin.  
**Audit op:** `UPDATE_ORG_JOIN_REQUEST`

**Request Body:**

```json
{
  "status": "granted"
}
```

**On `status = "granted"` — side effects:**
1. Updates `organization_join_requests.status = granted` and `processed_at = now()`
2. Creates `OrganizationUser` record (role = `member`)
3. Sets org attributes in Keycloak (`organization_id`, `organization_name`)
4. Sends approval/rejection email to user

**Response 200:**

```json
{
  "type": "urn:dx:controlPlane:success",
  "title": "Success",
  "detail": "Approved Organisation Join Request"
}
```

**Errors:**

| Status | Condition |
|---|---|
| 400 | No request found with given ID |
| 401 | Unauthorized |
| 403 | Caller is not the org admin |
| 404 | Request not found |

---

### GET `/iudx/v2/auth/user/organisations/join_requests`

**Summary:** Get the calling user's own join requests.  
**Auth:** Authenticated user.  
**Audit op:** `GET`

**Query params:** `delegatorId`, `page`, `size`, `sort`, `status`

**Response 200:** Array of join request objects enriched with user roles.

---

### DELETE `/iudx/v2/auth/user/organisations/join_requests/{id}`

**Summary:** User deletes their own pending join request.  
**Auth:** Authenticated user (owner only).  
**Audit op:** `DELETE_PENDING_ORG_JOIN_REQUEST`

**Validation:** Only `PENDING` status requests can be deleted.

**Response 200:** Empty success.

---

### PATCH `/iudx/v2/auth/organisation/join-request/{id}`

**Summary:** User withdraws a pending join request.  
**Auth:** Authenticated user.  
**Audit op:** `WITHDRAW_PENDING_ORG_JOIN_REQUEST`

**Request Body:**

```json
{
  "status": "withdrawn"
}
```

**Validation:** Request must be in `PENDING` status.

**Response 200:**

```json
{
  "type": "urn:dx:controlPlane:success",
  "title": "Success",
  "detail": "Withdrawn organisation join request"
}
```

**Errors:**

| Status | Condition |
|---|---|
| 400 | Request not in withdrawable state |
| 404 | No join request found with given ID |

---

## 4. Organisation Users

### GET `/iudx/v2/auth/organisations/{id}/users`

**Summary:** Org Admin lists all members of their organisation.  
**Auth:** Org Admin.  
**Audit op:** `GET_USERS`

**Path param:** `id` (UUID)  
**Query params:** `page`, `size`, `sort`, `searchTerm`, `delegatorId`

**Response 200:** Array of `OrganizationUser` objects enriched with Keycloak roles and account status.

```json
{
  "type": "urn:dx:controlPlane:success",
  "title": "Success",
  "result": [
    {
      "id": "81980801-85b6-4b7d-ae54-5df38a3535bd",
      "organizationId": "e87246e2-5da5-40b0-973c-690d2f48f879",
      "userId": "c908f4ed-69ef-4d27-8e50-8f95f89b6c4d",
      "userName": "test test",
      "role": "ADMIN",
      "jobTitle": "Manager",
      "empId": "EMP12345",
      "orgManagerPhoneNo": "+91-9876543210",
      "officialEmail": "manager@company.com",
      "createdAt": "2025-06-03T16:28:24.852025",
      "updatedAt": "2025-06-03T16:28:24.852025",
      "roles": ["consumer", "provider"],
      "accountEnabled": true,
      "tableName": "organization_users"
    }
  ],
  "paginationInfo": { ... }
}
```

---

### GET `/iudx/v2/auth/organisations/{id}/users/{user_id}`

**Summary:** Get detailed info for a specific org member.  
**Auth:** Org Admin.  
**Audit op:** `GET_USER_INFO`

**Response 200:** Single `OrganizationUser` object enriched with roles and account status.

---

### PUT `/iudx/v2/auth/organisations/{id}/users/{user_id}/role`

**Summary:** Update a member's role (admin ↔ member).  
**Auth:** Org Admin.  
**Audit op:** `UPDATE_USER_INFO`

**Request Body:**

```json
{
  "role": "admin"
}
```

**Response 200:** Updated `OrganizationUser`.

---

### DELETE `/iudx/v2/auth/organisations/{id}/users/{user_id}`

**Summary:** Remove a member from the organisation.  
**Auth:** Org Admin.  
**Audit op:** `DELETE_USER`

**Validation:**
- User must exist in the org
- User must NOT be an admin (cannot delete admin user)

**Side effects on deletion:**
1. Delete from `organization_users`
2. Clear org attributes in Keycloak (`organization_id`, `organization_name` set to empty)
3. **If user has PROVIDER role:**
   - Remove `PROVIDER` role from Keycloak
   - Transfer item/asset ownership via `ItemService.ownerShipTransfer()`
4. Delete associated `organization_join_requests`
5. Delete associated `provider_requests`

**Response 200:** Empty success.

---

## 5. Provider Role Requests

### POST `/iudx/v2/auth/organization/user/provider_role/requests`

**Summary:** User requests elevation from Consumer → Provider within their org.  
**Auth:** Authenticated user. KYC check required.  
**Audit op:** `REQUEST_PROVIDER_ROLE`

**Request Body:** None (org derived from user's Keycloak attributes)

**Handler validation:**
1. User must be a member of an organisation
2. No `PENDING` or `GRANTED` provider role request for this user (only allows new request if previous was `REJECTED`)
3. Insert into `provider_requests` (status = `pending`)
4. Send confirmation email

**Response 200:**

```json
{
  "type": "urn:dx:controlPlane:success",
  "title": "Success",
  "detail": "Created request"
}
```

---

### GET `/iudx/v2/auth/organization/user/provider_role/requests`

**Summary:** Org Admin views all pending provider role requests in their org.  
**Auth:** Org Admin.  
**Audit op:** `GET_PROVIDER_REQS`

**Query params:** `delegatorId`

**Response 200:** Array of provider requests enriched with org user info (user_name, job_title, emp_id).

```json
{
  "type": "urn:dx:controlPlane:success",
  "title": "Success",
  "result": [
    {
      "id": "e970320f-ffed-4946-ae68-cb13a9f7d7d2",
      "user_id": "719b5e7c-3a20-4f77-91f9-1f43ac4ef6a7",
      "organization_id": "0706147d-2c77-4165-8550-812804d114be",
      "status": "pending",
      "created_at": "2025-08-25 05:28:20.356043",
      "updated_at": "2025-08-25 05:28:20.356043",
      "user_name": "Jane Doe",
      "job_title": "Engineer",
      "emp_id": "EMP001"
    }
  ],
  "paginationInfo": { ... }
}
```

---

### PUT `/iudx/v2/auth/organization/user/provider_role/requests/{id}`

**Summary:** Org Admin approves or rejects a provider role request.  
**Auth:** Org Admin.  
**Audit op:** `UPDATE_PROVIDER_REQUEST`

**Path param:** `id` (UUID) — provider request ID  
**Query param:** `delegatorId` (optional)

**Request Body:**

```json
{
  "status": "granted"
}
```

**On `status = "granted"` — side effects:**
1. Update `provider_requests.status = granted`
2. Assign `PROVIDER` role in Keycloak for the requesting user
3. Send notification email to user

**Response 200:**

```json
{
  "type": "urn:dx:controlPlane:success",
  "title": "Success",
  "detail": "Provider role updated"
}
```

---

### GET `/iudx/v2/auth/organization/user/provider_requests`

**Summary:** User checks their own provider role request(s).  
**Auth:** Authenticated user.  
**Audit op:** `GET_PROVIDER_REQS`

**Query param:** `delegatorId` (optional)

**Response 200:** Array of own provider requests with status and Keycloak roles.

```json
{
  "type": "urn:dx:controlPlane:success",
  "title": "Success",
  "result": [
    {
      "id": "e970320f-ffed-4946-ae68-cb13a9f7d7d2",
      "user_id": "719b5e7c-3a20-4f77-91f9-1f43ac4ef6a7",
      "organization_id": "0706147d-2c77-4165-8550-812804d114be",
      "status": "granted",
      "created_at": "2025-08-25 05:28:20.356043",
      "updated_at": "2025-08-25 05:30:14.607398",
      "roles": ["consumer", "compute", "provider"],
      "account_enabled": true
    }
  ]
}
```

---

### DELETE `/iudx/v2/auth/organization/user/provider-requests/{id}`

**Summary:** User deletes their own pending provider request.  
**Auth:** Authenticated user.  
**Audit op:** `DELETE_PENDING_PROVIDER_REQUEST`

**Validation:** Only `PENDING` status requests can be deleted.

**Response 200:** Empty success.

---

### POST `/iudx/v2/auth/organization/user/provider`

**Summary:** Org Admin directly grants provider role to a user.  
**Auth:** Org Admin.  
**Audit op:** `CREATE_PROVIDER_ROLE`

**Request Body:**

```json
{
  "user_id": "436cb796-5826-458b-a914-a54721f70997",
  "organization_id": "436cb796-5826-458b-a914-a54721f70888",
  "status": "granted"
}
```

**Handler logic:**
1. Validate user is a member of the organisation
2. If no provider request exists → create one + assign `PROVIDER` in Keycloak
3. If request exists and is `PENDING` → update to `GRANTED` + assign `PROVIDER` in Keycloak

**Response 200:** `"Provider role granted successfully"`

---

## 6. Reports (CSV exports)

All report endpoints are restricted to COS Admin and stream a CSV file as an attachment.

| Endpoint | Content |
|---|---|
| `GET /iudx/v2/auth/organisations/requests/report` | All org create requests |
| `GET /iudx/v2/auth/organisations/report` | All organisations |
| `GET /iudx/v2/auth/organisations/{id}/join_requests/report` | Join requests for one org |
| `GET /iudx/v2/auth/organization/user/provider_role/requests/report` | Provider role requests |

**Response:** `Content-Type: text/csv`, `Content-Disposition: attachment; filename="..."`

---

## Key Workflows

### Workflow 1 — Organisation Creation

```
User                   System                      COS Admin
 │                       │                              │
 │ POST /org-create-requests                            │
 │──────────────────────►│                              │
 │                       │ Fetch user from Keycloak     │
 │                       │ Validate uniqueness          │
 │                       │ INSERT organization_create_requests (pending)
 │                       │ Send email: "request received"
 │◄──────────────────────│                              │
 │ 200 OK (pending)      │                              │
 │                       │                              │
 │                       │       GET /org-create-requests
 │                       │◄─────────────────────────────│
 │                       │──────────────────────────────►│
 │                       │       POST /organisations/requests/approve {status: granted}
 │                       │◄─────────────────────────────│
 │                       │ UPDATE status = granted       │
 │                       │ INSERT organizations          │
 │                       │ INSERT organization_users (role=admin)
 │                       │ Keycloak: add ORG_ADMIN + PROVIDER roles
 │                       │ Keycloak: set org attributes  │
 │                       │ Send email: "request approved"
 │◄──────────────────────│──────────────────────────────►│
 │ (email notification)  │ 201 Created                  │
```

---

### Workflow 2 — Organisation Join

```
User                   System                      Org Admin
 │                       │                              │
 │ POST /organisations/{id}/join_requests               │
 │──────────────────────►│                              │
 │                       │ Validate email uniqueness    │
 │                       │ Check no active request      │
 │                       │ INSERT organization_join_requests (pending)
 │                       │ Send email: "request received"
 │◄──────────────────────│                              │
 │ 200 OK                │                              │
 │                       │     GET /organisations/{id}/join_requests
 │                       │◄─────────────────────────────│
 │                       │──────────────────────────────►│
 │                       │     PUT /organisations/{id}/join_requests/{req_id} {status: granted}
 │                       │◄─────────────────────────────│
 │                       │ UPDATE status = granted       │
 │                       │ INSERT organization_users (role=member)
 │                       │ Keycloak: set org attributes  │
 │                       │ Send email: "welcome to org"  │
 │                       │──────────────────────────────►│
 │ (email notification)  │ 200 OK                       │
```

---

### Workflow 3 — Provider Role Request

```
Org Member             System                      Org Admin
 │                       │                              │
 │ POST /provider_role/requests                         │
 │──────────────────────►│                              │
 │                       │ Validate org membership      │
 │                       │ Check no pending/granted     │
 │                       │ INSERT provider_requests (pending)
 │                       │ Send email: "request received"
 │◄──────────────────────│                              │
 │ 200 OK                │                              │
 │                       │     GET /provider_role/requests
 │                       │◄─────────────────────────────│
 │                       │──────────────────────────────►│
 │                       │     PUT /provider_role/requests/{id} {status: granted}
 │                       │◄─────────────────────────────│
 │                       │ UPDATE status = granted       │
 │                       │ Keycloak: add PROVIDER role   │
 │                       │ Send email: "role granted"    │
 │                       │──────────────────────────────►│
 │ (email notification)  │ 200 OK                       │
```

---

### Workflow 4 — User Removal from Organisation

```
Org Admin              System
 │                       │
 │ DELETE /organisations/{id}/users/{user_id}
 │──────────────────────►│
 │                       │ 1. Verify user exists in org
 │                       │ 2. Check user is NOT admin
 │                       │ 3. Check if user has PROVIDER role?
 │                       │    YES → Remove PROVIDER from Keycloak
 │                       │        → ItemService.ownerShipTransfer()
 │                       │ 4. DELETE organization_users
 │                       │ 5. Keycloak: clear org attributes
 │                       │ 6. DELETE organization_join_requests for user
 │                       │ 7. DELETE provider_requests for user
 │◄──────────────────────│
 │ 200 OK                │
```

---

## Authorization & Access Control

### Role Matrix

| Operation | COS Admin | Org Admin | Any Auth User |
|---|---|---|---|
| List all org create requests | ✓ | | |
| Approve/reject org create request | ✓ | | |
| Submit org create request | | | ✓ (KYC required) |
| Delete own org create request | | | ✓ (owner only) |
| List orgs | ✓ | ✓ | ✓ |
| Get org by ID | ✓ | ✓ | ✓ |
| Update org | ✓ | | |
| Delete org | ✓ | ✓ (own org) | |
| Submit join request | | | ✓ (KYC required) |
| List join requests (org) | | ✓ | |
| Approve/reject join request | | ✓ | |
| Get/delete own join requests | | | ✓ |
| List org users | | ✓ | |
| Update user role | | ✓ | |
| Delete org user | | ✓ | |
| Submit provider role request | | | ✓ (KYC + org member required) |
| List/approve provider role requests | | ✓ | |
| Get/delete own provider requests | | | ✓ |
| Directly grant provider role | | ✓ | |
| All CSV reports | ✓ | | |

### Delegation Support

Most Org Admin endpoints accept an optional `delegatorId` query parameter. When present:

1. `OrganizationAccessOrchestrator.assertOrgManagementAccess()` is called
2. Validates delegation record exists in `delegations` table
3. Validates delegator is org admin of the target org (`OrgOwnershipValidator`)
4. Validates delegation is not expired
5. If valid: operation proceeds under delegated authority

---

## Audit Logging

Every mutating operation creates an audit log via `OrganizationAuditHelper`:

```
UserActivityAuditLogBuilder
  .logType("USER_ACTION")
  .originServer("AAA")
  .operation(OrganisationAuditOperation.xxx)
  .requestId(body.getString("id") or request body id)
  .delegatorId(if delegated context)
```

The log is committed via `CpRoutingContextHelper.setAuditingLogV2(ctx, log)` and persisted to the `user_activity_audit_log` table (V46).

---

## External Integrations

### Keycloak (KeycloakUserService)

| Method | When called |
|---|---|
| `getUserById(UUID)` | Fetch `userName` when creating org/join requests |
| `addRoleToUser(UUID, DxRole.ORG_ADMIN)` | When org create request is approved |
| `addRoleToUser(UUID, DxRole.PROVIDER)` | When provider role granted |
| `removeRoleFromUser(UUID, DxRole.PROVIDER)` | When provider user is removed from org |
| `setOrganisationDetails(UUID, UUID, String)` | When user joins org (set `organization_id`, `organization_name`) |
| `updateUserAttributes(UUID, Map)` | When user removed from org (clear org attributes) |

### Email Service (EmailComposer)

| Method | Trigger |
|---|---|
| `sendEmailForCreatingOrg()` | After org create request submitted |
| `sendUserEmailForOrgCreateRequestApproval()` | After org create request approved/rejected |
| `sendEmailForJoiningOrg()` | After join request submitted |
| `sendUserEmailForOrgJoinRequestApproval()` | After join request approved/rejected |
| `sendEmailForProviderRole()` | After provider role request submitted |
| `sendUserEmailForProviderRoleApproval()` | After provider role request approved/rejected |

### Item Service (ItemService)

| Method | When called |
|---|---|
| `ownerShipTransfer(UUID fromUser, UUID toUser)` | When a provider org member is deleted — transfers their assets |

---

## Error Responses

| HTTP Status | URN | Meaning |
|---|---|---|
| 400 | `urn:dx:controlPlane:badRequest` | Invalid request (e.g., deleting non-pending request, request not found) |
| 401 | `urn:dx:as:InvalidAuthenticationToken` | Token invalid, expired, or inactive |
| 401 | `urn:dx:controlPlane:notAuthorized` | User lacks permission for the operation |
| 403 | `urn:dx:controlPlane:forbidden` | Access explicitly forbidden (e.g., not org admin, not cos_admin) |
| 404 | `urn:dx:controlPlane:notFound` | Resource not found |
| 409 | `urn:dx:controlPlane:conflict` | Duplicate (org name, email, active request already exists) |

---

*Generated from source: `src/main/java/org/cdpg/dx/aaa/organization/` and `docs/controlplane-openapi/paths/organisations.yaml`*
