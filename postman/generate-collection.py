#!/usr/bin/env python3
"""
Generates the DX ControlPlane comprehensive Postman collection.
Run: python3 postman/generate-collection.py
Output: postman/DX-ControlPlane-API-Tests.postman_collection.json
"""

import json
import uuid

# ──────────────────────────────────────────────────────────────────────
# Helpers
# ──────────────────────────────────────────────────────────────────────

def uid():
    return str(uuid.uuid4())

def make_url(raw, host_var="base_url"):
    """Build Postman URL object from a raw URL string."""
    # Replace host variable placeholder
    parts = raw.lstrip("/").split("/")
    query = []
    path_parts = []
    for p in parts:
        if "?" in p:
            pp, qs = p.split("?", 1)
            path_parts.append(pp)
            for kv in qs.split("&"):
                if "=" in kv:
                    k, v = kv.split("=", 1)
                    query.append({"key": k, "value": v})
                else:
                    query.append({"key": kv, "value": ""})
        else:
            path_parts.append(p)

    url_obj = {
        "raw": "{{" + host_var + "}}/" + "/".join(parts),
        "host": ["{{" + host_var + "}}"],
        "path": path_parts
    }
    if query:
        url_obj["query"] = query
    return url_obj

def bearer_header(token_var):
    return [
        {"key": "Authorization", "value": "Bearer {{" + token_var + "}}", "type": "text"},
        {"key": "Content-Type", "value": "application/json", "type": "text"}
    ]

def json_body(obj):
    return {
        "mode": "raw",
        "raw": json.dumps(obj, indent=2),
        "options": {"raw": {"language": "json"}}
    }

def test_status(code, extra_js=""):
    lines = [
        f'pm.test("Status {code}", function () {{',
        f'    pm.response.to.have.status({code});',
        '});',
        '',
        'pm.test("Content-Type is JSON", function () {',
        '    pm.response.to.have.header("Content-Type", /application\\/json/);',
        '});',
    ]
    if extra_js:
        lines.append('')
        lines.extend(extra_js.strip().split('\n'))
    return lines

def prereq_token(role):
    """Pre-request script to auto-fetch token for a role."""
    return [
        f'// Auto-fetch {role} token if not set or expired',
        f'const role = "{role}";',
        'const kc = pm.environment.get("kc_url");',
        'const realm = pm.environment.get("realm");',
        'const clientId = pm.environment.get("client_id");',
        f'const username = pm.environment.get(role + "_user");',
        f'const password = pm.environment.get(role + "_pass");',
        f'const tokenVar = role + "_token";',
        '',
        'const existingToken = pm.collectionVariables.get(tokenVar);',
        'if (existingToken) {',
        '    try {',
        '        const payload = JSON.parse(atob(existingToken.split(".")[1]));',
        '        if (payload.exp * 1000 > Date.now() + 30000) return;',
        '    } catch(e) {}',
        '}',
        '',
        'pm.sendRequest({',
        '    url: kc + "/realms/" + realm + "/protocol/openid-connect/token",',
        '    method: "POST",',
        '    header: { "Content-Type": "application/x-www-form-urlencoded" },',
        '    body: {',
        '        mode: "urlencoded",',
        '        urlencoded: [',
        '            { key: "grant_type", value: "password" },',
        '            { key: "client_id", value: clientId },',
        '            { key: "username", value: username },',
        '            { key: "password", value: password }',
        '        ]',
        '    }',
        '}, function (err, res) {',
        '    if (err) { console.error("Token fetch failed:", err); return; }',
        '    const json = res.json();',
        '    pm.collectionVariables.set(tokenVar, json.access_token);',
        '});',
    ]

def make_request(name, method, path, token_var, body=None, status=200,
                 extra_test="", host_var="base_url", prereq_role=None,
                 query_params=None, extra_headers=None):
    """Create a Postman request item."""
    headers = bearer_header(token_var) if token_var else [
        {"key": "Content-Type", "value": "application/json", "type": "text"}
    ]
    if extra_headers:
        headers.extend(extra_headers)

    url = make_url(path, host_var)
    if query_params:
        url.setdefault("query", [])
        for k, v in query_params.items():
            url["query"].append({"key": k, "value": v})
        # Update raw URL
        qs = "&".join(f"{k}={v}" for k, v in query_params.items())
        if "?" in url["raw"]:
            url["raw"] += "&" + qs
        else:
            url["raw"] += "?" + qs

    req = {
        "method": method,
        "header": headers,
        "url": url
    }
    if body is not None:
        req["body"] = json_body(body)

    events = []
    if prereq_role:
        events.append({
            "listen": "prerequest",
            "script": {"type": "text/javascript", "exec": prereq_token(prereq_role)}
        })
    events.append({
        "listen": "test",
        "script": {"type": "text/javascript", "exec": test_status(status, extra_test)}
    })

    return {
        "name": name,
        "event": events,
        "request": req,
        "response": []
    }

def folder(name, items):
    return {"name": name, "item": items}


# ──────────────────────────────────────────────────────────────────────
# Base path constants
# ──────────────────────────────────────────────────────────────────────
AUTH = "iudx/v2/auth"
CAT = "iudx/v2/cat"
AUD = "iudx/v2/auditing"
ACL = "iudx/acl/apd/v2"

# ──────────────────────────────────────────────────────────────────────
# 00 – Auth & Token
# ──────────────────────────────────────────────────────────────────────
def folder_00():
    items = []

    # Token requests for each role
    for role, user_var, pass_var in [
        ("COS Admin", "cosadmin_user", "cosadmin_pass"),
        ("Org Admin", "orgadmin_user", "orgadmin_pass"),
        ("Provider", "provider_user", "provider_pass"),
        ("Consumer", "consumer_user", "consumer_pass"),
        ("Delegate", "delegate_user", "delegate_pass"),
    ]:
        token_key = role.lower().replace(" ", "") + "_token"
        items.append({
            "name": f"Get {role} Token",
            "event": [
                {
                    "listen": "test",
                    "script": {
                        "type": "text/javascript",
                        "exec": [
                            'pm.test("Status 200", function () {',
                            '    pm.response.to.have.status(200);',
                            '});',
                            '',
                            'const json = pm.response.json();',
                            'pm.test("Has access_token", function () {',
                            '    pm.expect(json).to.have.property("access_token");',
                            '});',
                            '',
                            f'pm.collectionVariables.set("{token_key}", json.access_token);',
                            f'console.log("{role} token saved.");',
                        ]
                    }
                }
            ],
            "request": {
                "method": "POST",
                "header": [
                    {"key": "Content-Type", "value": "application/x-www-form-urlencoded", "type": "text"}
                ],
                "body": {
                    "mode": "urlencoded",
                    "urlencoded": [
                        {"key": "grant_type", "value": "password"},
                        {"key": "client_id", "value": "{{client_id}}"},
                        {"key": "username", "value": "{{" + user_var + "}}"},
                        {"key": "password", "value": "{{" + pass_var + "}}"},
                    ]
                },
                "url": {
                    "raw": "{{kc_url}}/realms/{{realm}}/protocol/openid-connect/token",
                    "host": ["{{kc_url}}"],
                    "path": ["realms", "{{realm}}", "protocol", "openid-connect", "token"]
                }
            },
            "response": []
        })

    # JWKS
    items.append(make_request(
        "GET /auth/jwks – Public Keys", "GET",
        f"{AUTH}/jwks", None, status=200
    ))

    # Create RS Token
    items.append(make_request(
        "POST /auth/token – Create RS Token", "POST",
        f"{AUTH}/token", "cosadmin_token",
        body={},
        status=200,
        extra_headers=[
            {"key": "clientId", "value": "{{client_id}}", "type": "text"},
            {"key": "clientSecret", "value": "{{client_secret}}", "type": "text"}
        ],
        prereq_role="cosadmin"
    ))

    return folder("00 – Auth & Token", items)


# ──────────────────────────────────────────────────────────────────────
# 01 – Resource Servers
# ──────────────────────────────────────────────────────────────────────
def folder_01():
    items = []

    items.append(make_request(
        "POST /resource_servers – Create", "POST",
        f"{AUTH}/resource_servers", "cosadmin_token",
        body={
            "name": "NGSI-LD",
            "url": "test-rs.datakaveri.org",
            "type": "data testing",
            "visibility": "PUBLIC",
            "query_type": ["ATTR", "TEMPORAL"],
            "injection_type": "api"
        },
        status=201,
        extra_test='''pm.test("Has result with id", function () {
    const body = pm.response.json();
    if (body.results && body.results.id) {
        pm.collectionVariables.set("rs_id", body.results.id);
    }
});''',
        prereq_role="cosadmin"
    ))

    items.append(make_request(
        "GET /resource_servers – List All", "GET",
        f"{AUTH}/resource_servers", "cosadmin_token",
        status=200, prereq_role="cosadmin"
    ))

    items.append(make_request(
        "GET /resource_servers/:id – Get By ID", "GET",
        f"{AUTH}/resource_servers/{{{{rs_id}}}}", "cosadmin_token",
        status=200, prereq_role="cosadmin"
    ))

    items.append(make_request(
        "DELETE /resource_servers/:id – Delete", "DELETE",
        f"{AUTH}/resource_servers/{{{{rs_id}}}}", "cosadmin_token",
        status=204, prereq_role="cosadmin"
    ))

    return folder("01 – Resource Servers", items)


# ──────────────────────────────────────────────────────────────────────
# 02 – Organisation Management
# ──────────────────────────────────────────────────────────────────────
def folder_02():
    items = []

    # Create org request
    items.append(make_request(
        "POST /organisations/requests – Create Org Request", "POST",
        f"{AUTH}/user/organisations/requests", "orgadmin_token",
        body={
            "name": "Test Organisation",
            "logo_path": "/assets/logos/test-org.png",
            "entity_type": "Startup",
            "org_sector": "Research & Development",
            "website_link": "https://test-org.example.com",
            "address": "42 Tech Boulevard, Hyderabad, India",
            "certificate_path": "/docs/certificates/test_cert.pdf",
            "pancard_path": "/docs/ids/test_pancard.pdf",
            "emp_id": "EMP12345",
            "job_title": "Operations Lead",
            "phone_no": "+91-9123456789",
            "organisation_documents": "/docs/organisations/test_docs.zip",
            "manager_email": "manager@test-org.example.com"
        },
        status=200,
        extra_test='''pm.test("Save org request id", function () {
    const body = pm.response.json();
    if (body.results && body.results.id) {
        pm.collectionVariables.set("org_request_id", body.results.id);
    }
});''',
        prereq_role="orgadmin"
    ))

    # Get org requests (cos_admin)
    items.append(make_request(
        "GET /organisations/requests – List (cos_admin)", "GET",
        f"{AUTH}/organisations/requests", "cosadmin_token",
        status=200, prereq_role="cosadmin"
    ))

    # Approve org request
    items.append(make_request(
        "POST /organisations/requests/approve – Approve", "POST",
        f"{AUTH}/organisations/requests/approve", "cosadmin_token",
        body={"id": "{{org_request_id}}"},
        status=200, prereq_role="cosadmin"
    ))

    # List organisations
    items.append(make_request(
        "GET /organisations – List All", "GET",
        f"{AUTH}/organisations", "cosadmin_token",
        status=200,
        extra_test='''pm.test("Save org id", function () {
    const body = pm.response.json();
    if (body.results && Array.isArray(body.results) && body.results.length > 0) {
        pm.collectionVariables.set("org_id", body.results[0].id);
    }
});''',
        prereq_role="cosadmin"
    ))

    # Get org by ID
    items.append(make_request(
        "GET /organisations/:id – Get By ID", "GET",
        f"{AUTH}/organisations/{{{{org_id}}}}", "cosadmin_token",
        status=200, prereq_role="cosadmin"
    ))

    # Update org
    items.append(make_request(
        "PUT /organisations/:id – Update", "PUT",
        f"{AUTH}/organisations/{{{{org_id}}}}", "orgadmin_token",
        body={"name": "Updated Test Organisation", "website_link": "https://updated-test-org.example.com"},
        status=200, prereq_role="orgadmin"
    ))

    # Get org users
    items.append(make_request(
        "GET /organisations/:id/users – List Users", "GET",
        f"{AUTH}/organisations/{{{{org_id}}}}/users", "orgadmin_token",
        status=200, prereq_role="orgadmin"
    ))

    # Create join request
    items.append(make_request(
        "POST /user/organisations/join_requests – Join Org", "POST",
        f"{AUTH}/user/organisations/join_requests", "provider_token",
        body={"organisation_id": "{{org_id}}", "emp_id": "EMP99999", "job_title": "Data Engineer"},
        status=200,
        extra_test='''pm.test("Save join request id", function () {
    const body = pm.response.json();
    if (body.results && body.results.id) {
        pm.collectionVariables.set("join_request_id", body.results.id);
    }
});''',
        prereq_role="provider"
    ))

    # Get join requests for org
    items.append(make_request(
        "GET /organisations/:id/join_requests – List Join Requests", "GET",
        f"{AUTH}/organisations/{{{{org_id}}}}/join_requests", "orgadmin_token",
        status=200, prereq_role="orgadmin"
    ))

    # Approve join request
    items.append(make_request(
        "PUT /organisations/:org_id/join_requests/:req_id – Approve Join", "PUT",
        f"{AUTH}/organisations/{{{{org_id}}}}/join_requests/{{{{join_request_id}}}}", "orgadmin_token",
        body={"status": "approved"},
        status=200, prereq_role="orgadmin"
    ))

    # Get user's org requests
    items.append(make_request(
        "GET /user/organisations/requests – My Org Requests", "GET",
        f"{AUTH}/user/organisations/requests", "orgadmin_token",
        status=200, prereq_role="orgadmin"
    ))

    # Get user's join requests
    items.append(make_request(
        "GET /user/organisations/join_requests – My Join Requests", "GET",
        f"{AUTH}/user/organisations/join_requests", "provider_token",
        status=200, prereq_role="provider"
    ))

    # Provider role request
    items.append(make_request(
        "POST /organization/user/provider_role/requests – Request Provider Role", "POST",
        f"{AUTH}/organization/user/provider_role/requests", "provider_token",
        body={"justification": "Need provider access for data publishing"},
        status=200,
        extra_test='''pm.test("Save provider role request id", function () {
    const body = pm.response.json();
    if (body.results && body.results.id) {
        pm.collectionVariables.set("provider_role_request_id", body.results.id);
    }
});''',
        prereq_role="provider"
    ))

    # Get provider requests
    items.append(make_request(
        "GET /organization/user/provider_requests – List Provider Requests", "GET",
        f"{AUTH}/organization/user/provider_requests", "orgadmin_token",
        status=200, prereq_role="orgadmin"
    ))

    # Update provider role request
    items.append(make_request(
        "PUT /organization/user/provider_role/requests/:id – Approve Provider Role", "PUT",
        f"{AUTH}/organization/user/provider_role/requests/{{{{provider_role_request_id}}}}", "orgadmin_token",
        body={"status": "approved"},
        status=200, prereq_role="orgadmin"
    ))

    # Reports
    items.append(make_request(
        "GET /organisations/requests/report – Org Requests Report (CSV)", "GET",
        f"{AUTH}/organisations/requests/report", "cosadmin_token",
        status=200, prereq_role="cosadmin"
    ))

    items.append(make_request(
        "GET /organisations/:id/join_requests/report – Join Requests Report (CSV)", "GET",
        f"{AUTH}/organisations/{{{{org_id}}}}/join_requests/report", "orgadmin_token",
        status=200, prereq_role="orgadmin"
    ))

    items.append(make_request(
        "GET /organization/user/provider_role/requests/report – Provider Role Report (CSV)", "GET",
        f"{AUTH}/organization/user/provider_role/requests/report", "orgadmin_token",
        status=200, prereq_role="orgadmin"
    ))

    return folder("02 – Organisation Management", items)


# ──────────────────────────────────────────────────────────────────────
# 03 – User & Role Management
# ──────────────────────────────────────────────────────────────────────
def folder_03():
    items = []

    items.append(make_request(
        "GET /user – Get Profile", "GET",
        f"{AUTH}/user", "consumer_token",
        status=200,
        extra_test='''pm.test("Has user info", function () {
    const body = pm.response.json();
    pm.expect(body.results).to.have.property("userId");
    pm.collectionVariables.set("consumer_user_id", body.results.userId);
});''',
        prereq_role="consumer"
    ))

    items.append(make_request(
        "GET /user/basic – Search User", "GET",
        f"{AUTH}/user/basic", "cosadmin_token",
        query_params={"email": "provider1@test.com"},
        status=200, prereq_role="cosadmin"
    ))

    items.append(make_request(
        "POST /user/update – Update Profile", "POST",
        f"{AUTH}/user/update", "consumer_token",
        body={"firstName": "Updated", "lastName": "Consumer"},
        status=200, prereq_role="consumer"
    ))

    items.append(make_request(
        "POST /user/password – Change Password", "POST",
        f"{AUTH}/user/password", "consumer_token",
        body={"currentPassword": "consumer123", "newPassword": "consumer123"},
        status=200, prereq_role="consumer"
    ))

    # Admin user management
    items.append(make_request(
        "GET /admin/user – Search Users (cos_admin)", "GET",
        f"{AUTH}/admin/user", "cosadmin_token",
        query_params={"email": "provider1@test.com"},
        status=200, prereq_role="cosadmin"
    ))

    items.append(make_request(
        "GET /admin/user/:id – Get User (cos_admin)", "GET",
        f"{AUTH}/admin/user/{{{{consumer_user_id}}}}", "cosadmin_token",
        status=200, prereq_role="cosadmin"
    ))

    items.append(make_request(
        "PUT /admin/:id/update – Update User (cos_admin)", "PUT",
        f"{AUTH}/admin/{{{{consumer_user_id}}}}/update", "cosadmin_token",
        body={"firstName": "AdminUpdated", "lastName": "Consumer"},
        status=200, prereq_role="cosadmin"
    ))

    # Custom roles
    items.append(make_request(
        "POST /user/custom/role – Create Custom Role", "POST",
        f"{AUTH}/user/custom/role", "provider_token",
        body={
            "role_name": "test_custom_role",
            "scopes": ["read", "write"]
        },
        status=200,
        extra_test='''pm.test("Save custom role id", function () {
    const body = pm.response.json();
    if (body.results && body.results.id) {
        pm.collectionVariables.set("custom_role_id", body.results.id);
    }
});''',
        prereq_role="provider"
    ))

    items.append(make_request(
        "GET /auth/v2/custom-role/requester – Get Custom Roles", "GET",
        "auth/v2/custom-role/requester", "provider_token",
        status=200, prereq_role="provider"
    ))

    # User description info
    items.append(make_request(
        "POST /user/description/info – Create Description", "POST",
        f"{AUTH}/user/description/info", "provider_token",
        body={"description": "Test provider user for development", "website": "https://provider.test.com"},
        status=200, prereq_role="provider"
    ))

    items.append(make_request(
        "PATCH /user/description/info – Update Description", "PATCH",
        f"{AUTH}/user/description/info", "provider_token",
        body={"description": "Updated test provider description"},
        status=200, prereq_role="provider"
    ))

    items.append(make_request(
        "GET /user/description/info – Get Description", "GET",
        f"{AUTH}/user/description/info", "provider_token",
        status=200, prereq_role="provider"
    ))

    return folder("03 – User & Role Management", items)


# ──────────────────────────────────────────────────────────────────────
# 04 – KYC Verification
# ──────────────────────────────────────────────────────────────────────
def folder_04():
    items = []

    items.append(make_request(
        "POST /kyc/verify – Initiate KYC", "POST",
        f"{AUTH}/kyc/verify", "provider_token",
        body={"auth_code": "test-auth-code", "code_verifier": "test-code-verifier"},
        status=200, prereq_role="provider"
    ))

    items.append(make_request(
        "GET /kyc/confirm/:id – Confirm KYC", "GET",
        f"{AUTH}/kyc/confirm/{{{{consumer_user_id}}}}", "cosadmin_token",
        status=200, prereq_role="cosadmin"
    ))

    items.append(make_request(
        "POST /kyc/revoke – Revoke KYC (cos_admin)", "POST",
        f"{AUTH}/kyc/revoke", "cosadmin_token",
        body={"user_id": "{{consumer_user_id}}"},
        status=200, prereq_role="cosadmin"
    ))

    return folder("04 – KYC Verification", items)


# ──────────────────────────────────────────────────────────────────────
# 05 – Credit Management
# ──────────────────────────────────────────────────────────────────────
def folder_05():
    items = []

    items.append(make_request(
        "POST /credit/request – Create Credit Request", "POST",
        f"{AUTH}/credit/request", "consumer_token",
        body={"amount": 100, "justification": "Need credits for testing API access"},
        status=200,
        extra_test='''pm.test("Save credit request id", function () {
    const body = pm.response.json();
    if (body.results && body.results.id) {
        pm.collectionVariables.set("credit_request_id", body.results.id);
    }
});''',
        prereq_role="consumer"
    ))

    items.append(make_request(
        "GET /credit/request – List Credit Requests (cos_admin)", "GET",
        f"{AUTH}/credit/request", "cosadmin_token",
        status=200, prereq_role="cosadmin"
    ))

    items.append(make_request(
        "PUT /credit/request – Approve Credit Request (cos_admin)", "PUT",
        f"{AUTH}/credit/request", "cosadmin_token",
        body={"id": "{{credit_request_id}}", "status": "approved", "amount": 100},
        status=200, prereq_role="cosadmin"
    ))

    items.append(make_request(
        "GET /user/credit/balance – My Balance", "GET",
        f"{AUTH}/user/credit/balance", "consumer_token",
        status=200, prereq_role="consumer"
    ))

    items.append(make_request(
        "GET /admin/user/credit/balance/:id – User Balance (cos_admin)", "GET",
        f"{AUTH}/admin/user/credit/balance/{{{{consumer_user_id}}}}", "cosadmin_token",
        status=200, prereq_role="cosadmin"
    ))

    items.append(make_request(
        "PUT /admin/user/credit/add – Add Credits (cos_admin)", "PUT",
        f"{AUTH}/admin/user/credit/add", "cosadmin_token",
        body={"user_id": "{{consumer_user_id}}", "amount": 50},
        status=200, prereq_role="cosadmin"
    ))

    items.append(make_request(
        "PUT /admin/user/credit/deduct – Deduct Credits (cos_admin)", "PUT",
        f"{AUTH}/admin/user/credit/deduct", "cosadmin_token",
        body={"user_id": "{{consumer_user_id}}", "amount": 10},
        status=200, prereq_role="cosadmin"
    ))

    items.append(make_request(
        "GET /user/credit/request – My Credit Requests", "GET",
        f"{AUTH}/user/credit/request", "consumer_token",
        status=200, prereq_role="consumer"
    ))

    items.append(make_request(
        "DELETE /user/credit/request/:id – Delete Credit Request", "DELETE",
        f"{AUTH}/user/credit/request/{{{{credit_request_id}}}}", "consumer_token",
        status=200, prereq_role="consumer"
    ))

    items.append(make_request(
        "GET /credit/request/report – Credit Report (CSV)", "GET",
        f"{AUTH}/credit/request/report", "cosadmin_token",
        status=200, prereq_role="cosadmin"
    ))

    return folder("05 – Credit Management", items)


# ──────────────────────────────────────────────────────────────────────
# 06 – Compute Requests
# ──────────────────────────────────────────────────────────────────────
def folder_06():
    items = []

    items.append(make_request(
        "POST /compute/requests – Create Compute Request", "POST",
        f"{AUTH}/compute/requests", "consumer_token",
        body={"justification": "Need compute access for data analysis"},
        status=200,
        extra_test='''pm.test("Save compute request id", function () {
    const body = pm.response.json();
    if (body.results && body.results.id) {
        pm.collectionVariables.set("compute_request_id", body.results.id);
    }
});''',
        prereq_role="consumer"
    ))

    items.append(make_request(
        "GET /user/compute/requests – My Compute Requests", "GET",
        f"{AUTH}/user/compute/requests", "consumer_token",
        status=200, prereq_role="consumer"
    ))

    items.append(make_request(
        "GET /compute/requests – All Compute Requests (cos_admin)", "GET",
        f"{AUTH}/compute/requests", "cosadmin_token",
        status=200, prereq_role="cosadmin"
    ))

    items.append(make_request(
        "PUT /compute/requests/:id – Approve (cos_admin)", "PUT",
        f"{AUTH}/compute/requests/{{{{compute_request_id}}}}", "cosadmin_token",
        body={"status": "approved"},
        status=200, prereq_role="cosadmin"
    ))

    items.append(make_request(
        "DELETE /user/compute/requests/:id – Delete Request", "DELETE",
        f"{AUTH}/user/compute/requests/{{{{compute_request_id}}}}", "consumer_token",
        status=200, prereq_role="consumer"
    ))

    items.append(make_request(
        "GET /compute/requests/report – Compute Report (CSV)", "GET",
        f"{AUTH}/compute/requests/report", "cosadmin_token",
        status=200, prereq_role="cosadmin"
    ))

    return folder("06 – Compute Requests", items)


# ──────────────────────────────────────────────────────────────────────
# 07 – Delegation
# ──────────────────────────────────────────────────────────────────────
def folder_07():
    items = []

    items.append(make_request(
        "POST /delegation – Grant Delegation", "POST",
        f"{AUTH}/delegation", "provider_token",
        body={
            "delegate_id": "{{consumer_user_id}}",
            "justification": "Delegating access for testing",
            "expiry_at": "2027-12-31T23:59:59",
            "roles": [
                {
                    "role": "consumer",
                    "constraints": [
                        {
                            "scope": "data_access",
                            "entity_id": ["{{item_id}}"],
                            "entity_type": "adex:DataBank",
                            "expiry_at": "2027-12-31T23:59:59"
                        }
                    ]
                }
            ]
        },
        status=200,
        extra_test='''pm.test("Save delegation id", function () {
    const body = pm.response.json();
    if (body.results && body.results.id) {
        pm.collectionVariables.set("delegation_id", body.results.id);
    }
});''',
        prereq_role="provider"
    ))

    items.append(make_request(
        "GET /delegation/delegate – My Delegations (as delegate)", "GET",
        f"{AUTH}/delegation/delegate", "consumer_token",
        status=200, prereq_role="consumer"
    ))

    items.append(make_request(
        "GET /delegation/delegator – My Delegations (as delegator)", "GET",
        f"{AUTH}/delegation/delegator", "provider_token",
        status=200, prereq_role="provider"
    ))

    items.append(make_request(
        "GET /delegation/:id – Get By ID", "GET",
        f"{AUTH}/delegation/{{{{delegation_id}}}}", "provider_token",
        status=200, prereq_role="provider"
    ))

    items.append(make_request(
        "DELETE /delegation/:id – Revoke", "DELETE",
        f"{AUTH}/delegation/{{{{delegation_id}}}}", "provider_token",
        status=200, prereq_role="provider"
    ))

    return folder("07 – Delegation", items)


# ──────────────────────────────────────────────────────────────────────
# 08 – Catalogue CRUD
# ──────────────────────────────────────────────────────────────────────
def folder_08():
    items = []

    # Create DataBank
    items.append(make_request(
        "POST /cat/item – Create DataBank", "POST",
        f"{CAT}/item", "provider_token",
        body={
            "@context": "https://agrijson.org",
            "type": ["adex:DataBank"],
            "name": "test-weather-data",
            "label": "Test Weather Data",
            "description": "Weather-related statistics for testing purposes.",
            "shortDescription": "Test weather dataset",
            "tags": ["environment", "weather", "test"],
            "organizationId": "{{org_id}}",
            "accessPolicy": "OPEN",
            "organizationType": "Private",
            "fileFormat": ".csv",
            "department": "Department of Testing",
            "resourceType": "DATASET",
            "dataReadiness": 85,
            "industry": "Climate Research",
            "uploadedBy": "provider1@test.com",
            "geoCoverage": "Pan India",
            "yearRange": "2020-2025",
            "verifiedBy": "Test Authority",
            "uploadFrequency": "Weekly",
            "license": "CC-BY 4.0"
        },
        status=201,
        extra_test='''pm.test("Save DataBank item id", function () {
    const body = pm.response.json();
    if (body.results && body.results.id) {
        pm.collectionVariables.set("databank_item_id", body.results.id);
        pm.collectionVariables.set("item_id", body.results.id);
    }
});''',
        prereq_role="provider"
    ))

    # Create AI Model
    items.append(make_request(
        "POST /cat/item – Create AiModel", "POST",
        f"{CAT}/item", "provider_token",
        body={
            "@context": "https://agrijson.org",
            "type": ["adex:AiModel"],
            "name": "test-crop-detection",
            "label": "Test Crop Disease Detection Model",
            "shortDescription": "Detects diseases in crop leaves.",
            "description": "A test AI model for detecting crop disease.",
            "tags": ["ai", "ml", "test"],
            "organizationId": "{{org_id}}",
            "accessPolicy": "RESTRICTED",
            "organizationType": "Private",
            "department": "Agriculture and Co-operation",
            "modelType": "ImageClassifier",
            "fileFormat": "ipynb",
            "mediaURL": "https://example.com/model",
            "industry": "Agriculture",
            "uploadedBy": "provider1@test.com",
            "license": "MIT",
            "fileSize": "15MB"
        },
        status=201,
        extra_test='''pm.test("Save AI Model item id", function () {
    const body = pm.response.json();
    if (body.results && body.results.id) {
        pm.collectionVariables.set("aimodel_item_id", body.results.id);
    }
});''',
        prereq_role="provider"
    ))

    # Create Apps
    items.append(make_request(
        "POST /cat/item – Create Apps", "POST",
        f"{CAT}/item", "provider_token",
        body={
            "@context": "https://agrijson.org",
            "type": ["adex:Apps"],
            "name": "test-analytics-app",
            "label": "Test Analytics App",
            "shortDescription": "An analytics application for testing.",
            "description": "A test application built using weather datasets.",
            "tags": ["app", "analytics", "test"],
            "organizationId": "{{org_id}}",
            "accessPolicy": "OPEN",
            "organizationType": "Private",
            "department": "Department of Testing",
            "industry": "Technology",
            "uploadedBy": "provider1@test.com",
            "license": "Apache-2.0"
        },
        status=201,
        extra_test='''pm.test("Save Apps item id", function () {
    const body = pm.response.json();
    if (body.results && body.results.id) {
        pm.collectionVariables.set("apps_item_id", body.results.id);
    }
});''',
        prereq_role="provider"
    ))

    # Get item
    items.append(make_request(
        "GET /cat/item?id= – Get Item", "GET",
        f"{CAT}/item", "provider_token",
        query_params={"id": "{{item_id}}"},
        status=200, prereq_role="provider"
    ))

    # Get item access
    items.append(make_request(
        "GET /cat/item/access?id= – Get Item Access", "GET",
        f"{CAT}/item/access", "consumer_token",
        query_params={"id": "{{item_id}}"},
        status=200, prereq_role="consumer"
    ))

    # Update item
    items.append(make_request(
        "PUT /cat/item – Update Item", "PUT",
        f"{CAT}/item", "provider_token",
        body={
            "id": "{{item_id}}",
            "description": "Updated description for weather dataset"
        },
        status=200, prereq_role="provider"
    ))

    # Delete item
    items.append(make_request(
        "DELETE /cat/item?id= – Delete Item", "DELETE",
        f"{CAT}/item", "provider_token",
        query_params={"id": "{{apps_item_id}}"},
        status=200, prereq_role="provider"
    ))

    # Download script
    items.append(make_request(
        "GET /items/scripts/download/:filename – Download Script", "GET",
        "items/scripts/download/test-script.py", None,
        status=200
    ))

    return folder("08 – Catalogue CRUD", items)


# ──────────────────────────────────────────────────────────────────────
# 09 – Discovery & Search
# ──────────────────────────────────────────────────────────────────────
def folder_09():
    items = []

    # Text search
    items.append(make_request(
        "POST /cat/search – Text Search", "POST",
        f"{CAT}/search", "consumer_token",
        body={
            "searchType": "text",
            "q": "weather",
            "limit": 10,
            "offset": 0
        },
        status=200, prereq_role="consumer"
    ))

    # Complex search
    items.append(make_request(
        "POST /cat/search – Complex Search", "POST",
        f"{CAT}/search", "consumer_token",
        body={
            "searchType": "complex",
            "filters": {
                "accessPolicy": ["OPEN"],
                "organizationType": ["Private"]
            },
            "limit": 10,
            "offset": 0
        },
        status=200, prereq_role="consumer"
    ))

    # Flattened term search
    items.append(make_request(
        "POST /cat/search – Flattened Term Search", "POST",
        f"{CAT}/search", "consumer_token",
        body={
            "searchType": "flattenedTerm",
            "term": "industry",
            "value": "Agriculture"
        },
        status=200, prereq_role="consumer"
    ))

    # Count
    items.append(make_request(
        "POST /cat/count – Count Entities", "POST",
        f"{CAT}/count", None,
        body={
            "searchType": "text",
            "q": "weather"
        },
        status=200
    ))

    # My assets
    items.append(make_request(
        "GET /cat/search/myassets – Get My Assets", "GET",
        f"{CAT}/search/myassets", "provider_token",
        query_params={"page": "0", "size": "10"},
        status=200, prereq_role="provider"
    ))

    items.append(make_request(
        "POST /cat/search/myassets – Search My Assets", "POST",
        f"{CAT}/search/myassets", "provider_token",
        body={
            "filters": {"accessPolicy": ["OPEN"]}
        },
        query_params={"page": "0", "size": "10"},
        status=200, prereq_role="provider"
    ))

    # List filters
    items.append(make_request(
        "POST /cat/list – List Filter Values", "POST",
        f"{CAT}/list", None,
        body={"filter": "accessPolicy"},
        status=200
    ))

    # Platform assets (cos_admin)
    items.append(make_request(
        "GET /cat/getAllAssets – Platform Assets (cos_admin)", "GET",
        f"{CAT}/getAllAssets", "cosadmin_token",
        query_params={"page": "0", "size": "10"},
        status=200, prereq_role="cosadmin"
    ))

    items.append(make_request(
        "POST /cat/getAllAssets – Platform Assets with Filters (cos_admin)", "POST",
        f"{CAT}/getAllAssets", "cosadmin_token",
        body={
            "filters": {"accessPolicy": ["OPEN"]}
        },
        query_params={"page": "0", "size": "10"},
        status=200, prereq_role="cosadmin"
    ))

    return folder("09 – Discovery & Search", items)


# ──────────────────────────────────────────────────────────────────────
# 10 – Organisation Assets
# ──────────────────────────────────────────────────────────────────────
def folder_10():
    items = []

    items.append(make_request(
        "GET /cat/organisation/asset – Org Assets", "GET",
        f"{CAT}/organisation/asset", "orgadmin_token",
        query_params={"page": "0", "size": "10"},
        status=200, prereq_role="orgadmin"
    ))

    items.append(make_request(
        "POST /cat/organisation/asset – Org Assets with Filters", "POST",
        f"{CAT}/organisation/asset", "orgadmin_token",
        body={
            "filters": {"accessPolicy": ["OPEN"]}
        },
        query_params={"page": "0", "size": "10"},
        status=200, prereq_role="orgadmin"
    ))

    items.append(make_request(
        "PATCH /cat/organisation/asset – Update Asset Status", "PATCH",
        f"{CAT}/organisation/asset", "orgadmin_token",
        body={"id": "{{item_id}}", "status": "active"},
        query_params={"id": "{{item_id}}"},
        status=200, prereq_role="orgadmin"
    ))

    return folder("10 – Organisation Assets", items)


# ──────────────────────────────────────────────────────────────────────
# 11 – Asset Requests
# ──────────────────────────────────────────────────────────────────────
def folder_11():
    items = []

    items.append(make_request(
        "POST /asset/request – Create Asset Request", "POST",
        f"{AUTH}/asset/request", "consumer_token",
        body={
            "asset_id": "{{item_id}}",
            "justification": "Need access to this dataset for research"
        },
        status=200,
        extra_test='''pm.test("Save asset request id", function () {
    const body = pm.response.json();
    if (body.results && body.results.id) {
        pm.collectionVariables.set("asset_request_id", body.results.id);
    }
});''',
        prereq_role="consumer"
    ))

    items.append(make_request(
        "GET /asset/request – List Asset Requests", "GET",
        f"{AUTH}/asset/request", "orgadmin_token",
        query_params={"page": "0", "size": "10"},
        status=200, prereq_role="orgadmin"
    ))

    items.append(make_request(
        "PUT /asset/request/:id – Approve/Reject", "PUT",
        f"{AUTH}/asset/request/{{{{asset_request_id}}}}", "orgadmin_token",
        body={"status": "approved"},
        status=200, prereq_role="orgadmin"
    ))

    items.append(make_request(
        "DELETE /asset/request/:id – Delete", "DELETE",
        f"{AUTH}/asset/request/{{{{asset_request_id}}}}", "consumer_token",
        status=200, prereq_role="consumer"
    ))

    return folder("11 – Asset Requests", items)


# ──────────────────────────────────────────────────────────────────────
# 12 – Subscriptions
# ──────────────────────────────────────────────────────────────────────
def folder_12():
    items = []

    items.append(make_request(
        "POST /subscriptions – Create", "POST",
        "iudx/v2/subscriptions", "consumer_token",
        body={
            "subscriptionName": "test-subscription",
            "type": "Subscription",
            "entities": ["{{item_id}}"]
        },
        status=201,
        extra_test='''pm.test("Save subscription id", function () {
    const body = pm.response.json();
    if (body.results && body.results.id) {
        pm.collectionVariables.set("subscription_id", body.results.id);
    } else if (body.id) {
        pm.collectionVariables.set("subscription_id", body.id);
    }
});''',
        prereq_role="consumer"
    ))

    items.append(make_request(
        "GET /subscriptions – List All", "GET",
        "iudx/v2/subscriptions", "consumer_token",
        query_params={"limit": "10", "offset": "0"},
        status=200, prereq_role="consumer"
    ))

    items.append(make_request(
        "GET /subscriptions/:id – Get By ID", "GET",
        "iudx/v2/subscriptions/{{subscription_id}}", "consumer_token",
        status=200, prereq_role="consumer"
    ))

    items.append(make_request(
        "PATCH /subscriptions/:id – Update", "PATCH",
        "iudx/v2/subscriptions/{{subscription_id}}", "consumer_token",
        body={
            "expiryAt": "2027-12-31T23:59:59Z",
            "type": "Subscription",
            "entities": ["{{item_id}}"]
        },
        status=200, prereq_role="consumer"
    ))

    items.append(make_request(
        "DELETE /subscriptions/:id – Delete", "DELETE",
        "iudx/v2/subscriptions/{{subscription_id}}", "consumer_token",
        status=200, prereq_role="consumer"
    ))

    return folder("12 – Subscriptions", items)


# ──────────────────────────────────────────────────────────────────────
# 13 – App Management
# ──────────────────────────────────────────────────────────────────────
def folder_13():
    items = []

    items.append(make_request(
        "POST /auth/app – Create App", "POST",
        f"{AUTH}/app", "provider_token",
        body={"expiry_at": "2027-12-31T23:59:59"},
        status=200,
        extra_test='''pm.test("Save app id", function () {
    const body = pm.response.json();
    if (body.results && body.results.id) {
        pm.collectionVariables.set("app_id", body.results.id);
    }
});''',
        prereq_role="provider"
    ))

    items.append(make_request(
        "GET /auth/app – List Apps", "GET",
        f"{AUTH}/app", "provider_token",
        query_params={"page": "0", "size": "10"},
        status=200, prereq_role="provider"
    ))

    items.append(make_request(
        "PATCH /auth/app/:appId – Update Status", "PATCH",
        f"{AUTH}/app/{{{{app_id}}}}", "provider_token",
        query_params={"status": "active"},
        status=200, prereq_role="provider"
    ))

    items.append(make_request(
        "POST /auth/v2/app/token – Generate App Token", "POST",
        "iudx/auth/v2/app/token", "provider_token",
        body={"appId": "{{app_id}}"},
        status=200,
        extra_test='''pm.test("Has app token", function () {
    const body = pm.response.json();
    pm.expect(body).to.have.property("access_token");
    pm.collectionVariables.set("app_token", body.access_token);
});''',
        prereq_role="provider"
    ))

    items.append(make_request(
        "DELETE /auth/app/:appId – Delete App", "DELETE",
        f"{AUTH}/app/{{{{app_id}}}}", "provider_token",
        status=200, prereq_role="provider"
    ))

    return folder("13 – App Management", items)


# ──────────────────────────────────────────────────────────────────────
# 14 – Client Management
# ──────────────────────────────────────────────────────────────────────
def folder_14():
    items = []

    items.append(make_request(
        "POST /auth/client – Create Client Credentials", "POST",
        f"{AUTH}/client", "provider_token",
        body={},
        status=201,
        extra_test='''pm.test("Has client credentials", function () {
    const body = pm.response.json();
    if (body.results) {
        pm.collectionVariables.set("client_secret", body.results.clientSecret || "");
    }
});''',
        prereq_role="provider"
    ))

    return folder("14 – Client Management", items)


# ──────────────────────────────────────────────────────────────────────
# 15 – Leaderboard
# ──────────────────────────────────────────────────────────────────────
def folder_15():
    items = []

    items.append(make_request(
        "GET /leaderboard/asset – Asset Leaderboard", "GET",
        "iudx/v2/leaderboard/asset", None,
        query_params={"page": "0", "size": "10"},
        status=200
    ))

    items.append(make_request(
        "GET /leaderboard/provider – Provider Leaderboard", "GET",
        "iudx/v2/leaderboard/provider", None,
        query_params={"page": "0", "size": "10"},
        status=200
    ))

    items.append(make_request(
        "GET /leaderboard/organization – Org Leaderboard", "GET",
        "iudx/v2/leaderboard/organization", None,
        query_params={"page": "0", "size": "10"},
        status=200
    ))

    return folder("15 – Leaderboard", items)


# ──────────────────────────────────────────────────────────────────────
# 16 – User Interactions & Feedback
# ──────────────────────────────────────────────────────────────────────
def folder_16():
    items = []

    # Interactions
    items.append(make_request(
        "POST /user/interactions – Like/Bookmark", "POST",
        "iudx/v2/user/interactions", "consumer_token",
        body={
            "assetId": "{{item_id}}",
            "assetType": "DataBank",
            "actionType": "like"
        },
        status=200, prereq_role="consumer"
    ))

    items.append(make_request(
        "GET /user/interactions – Get Interactions", "GET",
        "iudx/v2/user/interactions", "consumer_token",
        query_params={"page": "0", "size": "10"},
        status=200, prereq_role="consumer"
    ))

    items.append(make_request(
        "GET /user/interactions/sync – Sync Metrics (cos_admin)", "GET",
        "iudx/v2/user/interactions/sync", "cosadmin_token",
        status=200, prereq_role="cosadmin"
    ))

    # User Feedback
    items.append(make_request(
        "POST /user/feedback – Post Feedback", "POST",
        "iudx/v2/user/feedback", "consumer_token",
        body={
            "asset_id": "{{item_id}}",
            "rating": 4,
            "comment": "Great dataset, very useful for our research."
        },
        status=200,
        extra_test='''pm.test("Save feedback id", function () {
    const body = pm.response.json();
    if (body.results && body.results.id) {
        pm.collectionVariables.set("user_feedback_id", body.results.id);
    }
});''',
        prereq_role="consumer"
    ))

    items.append(make_request(
        "GET /user/feedback – Get Feedback", "GET",
        "iudx/v2/user/feedback", "consumer_token",
        query_params={"page": "0", "size": "10"},
        status=200, prereq_role="consumer"
    ))

    items.append(make_request(
        "DELETE /user/feedback – Delete Feedback", "DELETE",
        "iudx/v2/user/feedback", "consumer_token",
        query_params={"id": "{{user_feedback_id}}"},
        status=200, prereq_role="consumer"
    ))

    # Provider Feedback
    items.append(make_request(
        "POST /provider/feedback – Post Provider Feedback", "POST",
        "iudx/v2/provider/feedback", "provider_token",
        body={
            "asset_id": "{{item_id}}",
            "type": "response",
            "comment": "Thank you for using our dataset."
        },
        status=200,
        extra_test='''pm.test("Save provider feedback id", function () {
    const body = pm.response.json();
    if (body.results && body.results.id) {
        pm.collectionVariables.set("provider_feedback_id", body.results.id);
    }
});''',
        prereq_role="provider"
    ))

    items.append(make_request(
        "GET /provider/feedback – Get Provider Feedback", "GET",
        "iudx/v2/provider/feedback", "provider_token",
        query_params={"page": "0", "size": "10"},
        status=200, prereq_role="provider"
    ))

    items.append(make_request(
        "DELETE /provider/feedback – Delete Provider Feedback", "DELETE",
        "iudx/v2/provider/feedback", "provider_token",
        query_params={"id": "{{provider_feedback_id}}"},
        status=200, prereq_role="provider"
    ))

    return folder("16 – User Interactions & Feedback", items)


# ──────────────────────────────────────────────────────────────────────
# 17 – Auditing & Reports
# ──────────────────────────────────────────────────────────────────────
def folder_17():
    items = []

    items.append(make_request(
        "GET /auditing/consumer/activity – Consumer Activity", "GET",
        f"{AUD}/consumer/activity", "consumer_token",
        query_params={"page": "0", "size": "10"},
        status=200, prereq_role="consumer"
    ))

    items.append(make_request(
        "GET /auditing/admin/activity – Admin Activity (cos_admin)", "GET",
        f"{AUD}/admin/activity", "cosadmin_token",
        query_params={"page": "0", "size": "10"},
        status=200, prereq_role="cosadmin"
    ))

    items.append(make_request(
        "GET /auditing/reportactivity/admin – Admin Report CSV", "GET",
        f"{AUD}/reportactivity/admin", "cosadmin_token",
        query_params={"page": "0", "size": "10"},
        status=200, prereq_role="cosadmin"
    ))

    items.append(make_request(
        "GET /auditing/reportactivity/consumer – Consumer Report CSV", "GET",
        f"{AUD}/reportactivity/consumer", "consumer_token",
        query_params={"page": "0", "size": "10"},
        status=200, prereq_role="consumer"
    ))

    return folder("17 – Auditing & Reports", items)


# ──────────────────────────────────────────────────────────────────────
# 18 – Dashboard
# ──────────────────────────────────────────────────────────────────────
def folder_18():
    items = []

    items.append(make_request(
        "GET /dashboard/usage-summary – Usage Summary", "GET",
        "iudx/v2/dashboard/usage-summary", None,
        status=200
    ))

    return folder("18 – Dashboard", items)


# ──────────────────────────────────────────────────────────────────────
# 19 – ACL APD APIs
# ──────────────────────────────────────────────────────────────────────
def folder_19():
    items = []

    # Create access request
    items.append(make_request(
        "POST /access_request – Create", "POST",
        f"{ACL}/access_request", "consumer_token",
        body={
            "itemId": "{{item_id}}",
            "requestType": "DOWNLOAD",
            "additionalInfo": {
                "name": "Test Consumer",
                "email": "consumer1@test.com",
                "phone": "9876543210",
                "purpose": "testing",
                "description": "Need access for testing purposes"
            }
        },
        status=200,
        extra_test='''pm.test("Save access request id", function () {
    const body = pm.response.json();
    if (body.results && body.results.id) {
        pm.collectionVariables.set("access_request_id", body.results.id);
    }
});''',
        host_var="acl_url",
        prereq_role="consumer"
    ))

    # Update access request (approve)
    items.append(make_request(
        "PUT /access_request – Approve", "PUT",
        f"{ACL}/access_request", "provider_token",
        body={
            "requestId": "{{access_request_id}}",
            "status": "granted",
            "expiryAt": "2027-12-31T23:59:59",
            "constraints": {
                "access": [
                    {
                        "accessType": "api",
                        "expiry": 1861920000
                    }
                ]
            }
        },
        status=200,
        host_var="acl_url",
        prereq_role="provider"
    ))

    # Update access request (reject)
    items.append(make_request(
        "PUT /access_request – Reject", "PUT",
        f"{ACL}/access_request", "provider_token",
        body={
            "requestId": "{{access_request_id}}",
            "status": "rejected"
        },
        status=200,
        host_var="acl_url",
        prereq_role="provider"
    ))

    # Get provider access requests
    items.append(make_request(
        "GET /access_request/provider – Provider Requests", "GET",
        f"{ACL}/access_request/provider", "provider_token",
        status=200,
        host_var="acl_url",
        prereq_role="provider"
    ))

    # Get consumer access requests
    items.append(make_request(
        "GET /access_request/consumer – Consumer Requests", "GET",
        f"{ACL}/access_request/consumer", "consumer_token",
        status=200,
        host_var="acl_url",
        prereq_role="consumer"
    ))

    # Get organisation access requests
    items.append(make_request(
        "GET /access_request/organisation – Org Requests", "GET",
        f"{ACL}/access_request/organisation", "orgadmin_token",
        status=200,
        host_var="acl_url",
        prereq_role="orgadmin"
    ))

    # Check access
    items.append(make_request(
        "POST /access_request/has_access – Check Access", "POST",
        f"{ACL}/access_request/has_access", "consumer_token",
        body={
            "itemId": "{{item_id}}",
            "requestType": "DOWNLOAD"
        },
        status=200,
        host_var="acl_url",
        prereq_role="consumer"
    ))

    # Provider report
    items.append(make_request(
        "GET /access_request/provider/report – Provider Report (CSV)", "GET",
        f"{ACL}/access_request/provider/report", "provider_token",
        status=200,
        host_var="acl_url",
        prereq_role="provider"
    ))

    # Org report
    items.append(make_request(
        "GET /access_request/organisation/report – Org Report (CSV)", "GET",
        f"{ACL}/access_request/organisation/report", "orgadmin_token",
        status=200,
        host_var="acl_url",
        prereq_role="orgadmin"
    ))

    # Create policy
    items.append(make_request(
        "POST /policy – Create Policy", "POST",
        f"{ACL}/policy", "provider_token",
        body={
            "request": [
                {
                    "userEmail": "consumer1@test.com",
                    "itemId": "{{item_id}}",
                    "itemType": "DATABANK",
                    "expiryTime": "2027-12-31T23:59:59",
                    "constraints": {
                        "access": [
                            {
                                "accessType": "api",
                                "expiry": 1861920000,
                                "limits": {
                                    "apiHits": 1000
                                }
                            }
                        ]
                    }
                }
            ]
        },
        status=200,
        extra_test='''pm.test("Save policy id", function () {
    const body = pm.response.json();
    if (body.results && Array.isArray(body.results) && body.results.length > 0) {
        pm.collectionVariables.set("policy_id", body.results[0].id || body.results[0].policyId);
    }
});''',
        host_var="acl_url",
        prereq_role="provider"
    ))

    # Get policies
    items.append(make_request(
        "GET /policy – List Policies", "GET",
        f"{ACL}/policy", "provider_token",
        status=200,
        host_var="acl_url",
        prereq_role="provider"
    ))

    # Deactivate policy
    items.append(make_request(
        "PUT /policy – Deactivate Policy", "PUT",
        f"{ACL}/policy", "provider_token",
        query_params={"id": "{{policy_id}}"},
        status=200,
        host_var="acl_url",
        prereq_role="provider"
    ))

    # Verify (internal)
    items.append(make_request(
        "POST /verify – Verify Policy (Internal)", "POST",
        f"{ACL}/verify", "cosadmin_token",
        body={
            "itemId": "{{item_id}}",
            "userId": "{{consumer_user_id}}"
        },
        status=200,
        host_var="acl_url",
        prereq_role="cosadmin"
    ))

    return folder("19 – ACL APD APIs", items)


# ──────────────────────────────────────────────────────────────────────
# Assemble collection
# ──────────────────────────────────────────────────────────────────────

collection = {
    "info": {
        "_postman_id": uid(),
        "name": "DX Control Plane API Tests",
        "description": "Comprehensive API test suite for DX Control Plane and ACL APD.\n\nCovers all 130+ endpoints with role-based authentication via Keycloak.\n\n## Setup\n1. Import the matching environment file (local-docker or deployed)\n2. Run folder '00 – Auth & Token' first to populate tokens\n3. Run folders sequentially — later folders depend on IDs created by earlier ones\n\n## Roles\n- **cosadmin**: COS Administrator (full platform access)\n- **orgadmin**: Organisation Administrator\n- **provider**: Data Provider (publishes assets)\n- **consumer**: Data Consumer (accesses assets)\n- **delegate**: Delegate (acts on behalf of others)",
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
    },
    "item": [
        folder_00(),
        folder_01(),
        folder_02(),
        folder_03(),
        folder_04(),
        folder_05(),
        folder_06(),
        folder_07(),
        folder_08(),
        folder_09(),
        folder_10(),
        folder_11(),
        folder_12(),
        folder_13(),
        folder_14(),
        folder_15(),
        folder_16(),
        folder_17(),
        folder_18(),
        folder_19(),
    ],
    "event": [
        {
            "listen": "prerequest",
            "script": {
                "type": "text/javascript",
                "exec": [
                    "// Collection-level pre-request: helper to auto-fetch token by role",
                    "// Usage in folder/request pre-request scripts:",
                    "//   eval(pm.collectionVariables.get('getTokenFn'));",
                    "//   getToken('cosadmin');",
                    "",
                    "pm.collectionVariables.set('getTokenFn', `",
                    "function getToken(role) {",
                    "    const kc = pm.environment.get('kc_url');",
                    "    const realm = pm.environment.get('realm');",
                    "    const clientId = pm.environment.get('client_id');",
                    "    const username = pm.environment.get(role + '_user');",
                    "    const password = pm.environment.get(role + '_pass');",
                    "    const tokenVar = role + '_token';",
                    "",
                    "    const existing = pm.collectionVariables.get(tokenVar);",
                    "    if (existing) {",
                    "        try {",
                    "            const payload = JSON.parse(atob(existing.split('.')[1]));",
                    "            if (payload.exp * 1000 > Date.now() + 30000) return;",
                    "        } catch(e) {}",
                    "    }",
                    "",
                    "    pm.sendRequest({",
                    "        url: kc + '/realms/' + realm + '/protocol/openid-connect/token',",
                    "        method: 'POST',",
                    "        header: { 'Content-Type': 'application/x-www-form-urlencoded' },",
                    "        body: {",
                    "            mode: 'urlencoded',",
                    "            urlencoded: [",
                    "                { key: 'grant_type', value: 'password' },",
                    "                { key: 'client_id', value: clientId },",
                    "                { key: 'username', value: username },",
                    "                { key: 'password', value: password }",
                    "            ]",
                    "        }",
                    "    }, function (err, res) {",
                    "        if (err) { console.error('Token fetch failed:', err); return; }",
                    "        pm.collectionVariables.set(tokenVar, res.json().access_token);",
                    "    });",
                    "}",
                    "`);",
                ]
            }
        },
        {
            "listen": "test",
            "script": {
                "type": "text/javascript",
                "exec": [
                    "// Collection-level test: common response validation"
                ]
            }
        }
    ],
    "variable": [
        {"key": "cosadmin_token", "value": "", "type": "string"},
        {"key": "orgadmin_token", "value": "", "type": "string"},
        {"key": "provider_token", "value": "", "type": "string"},
        {"key": "consumer_token", "value": "", "type": "string"},
        {"key": "delegate_token", "value": "", "type": "string"},
        {"key": "rs_id", "value": "", "type": "string"},
        {"key": "org_id", "value": "", "type": "string"},
        {"key": "org_request_id", "value": "", "type": "string"},
        {"key": "join_request_id", "value": "", "type": "string"},
        {"key": "provider_role_request_id", "value": "", "type": "string"},
        {"key": "consumer_user_id", "value": "", "type": "string"},
        {"key": "custom_role_id", "value": "", "type": "string"},
        {"key": "credit_request_id", "value": "", "type": "string"},
        {"key": "compute_request_id", "value": "", "type": "string"},
        {"key": "delegation_id", "value": "", "type": "string"},
        {"key": "item_id", "value": "", "type": "string"},
        {"key": "databank_item_id", "value": "", "type": "string"},
        {"key": "aimodel_item_id", "value": "", "type": "string"},
        {"key": "apps_item_id", "value": "", "type": "string"},
        {"key": "asset_request_id", "value": "", "type": "string"},
        {"key": "subscription_id", "value": "", "type": "string"},
        {"key": "app_id", "value": "", "type": "string"},
        {"key": "app_token", "value": "", "type": "string"},
        {"key": "client_secret", "value": "", "type": "string"},
        {"key": "user_feedback_id", "value": "", "type": "string"},
        {"key": "provider_feedback_id", "value": "", "type": "string"},
        {"key": "access_request_id", "value": "", "type": "string"},
        {"key": "policy_id", "value": "", "type": "string"},
        {"key": "getTokenFn", "value": "", "type": "string"},
    ]
}

# Write output
import os
output_path = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                           "DX-ControlPlane-API-Tests.postman_collection.json")
with open(output_path, "w") as f:
    json.dump(collection, f, indent=2)

# Count
total = 0
for f in collection["item"]:
    count = len(f["item"])
    total += count
    print(f"  {f['name']}: {count} requests")
print(f"\nTotal: {total} requests")
print(f"Written to: {output_path}")
