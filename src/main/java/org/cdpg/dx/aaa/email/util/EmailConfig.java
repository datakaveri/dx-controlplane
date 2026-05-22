package org.cdpg.dx.aaa.email.util;

import io.vertx.core.json.JsonObject;

/**
 * Holds all configurable email settings: template paths, subject lines, and
 * static template variable values (e.g. the admin display name).
 *
 * <p>Read from two optional sub-objects in the application config:
 * <ul>
 *   <li>{@code emailTemplates}  – template path and subject per email type</li>
 *   <li>{@code emailDefaults}   – static values substituted into templates</li>
 * </ul>
 *
 * <p>Every key falls back to a built-in default, so existing deployments need
 * no config changes to stay functional.
 *
 * <pre>{@code
 * "emailTemplates": {
 *   "createOrg":           { "template": "templates/request-create-organization.html",
 *                            "subject":  "Organization Creation Request" },
 *   "joinOrg":             { "template": "templates/request-join-organization.html",
 *                            "subject":  "Join Organization Request" },
 *   "computeRole":         { "template": "templates/request-compute-role.html",
 *                            "subject":  "Compute Role Request" },
 *   "providerRole":        { "template": "templates/request-provider-role.html",
 *                            "subject":  "Provider Role Request" },
 *   "creditRequest":       { "template": "templates/request-credit.html",
 *                            "subject":  "Credit Request" },
 *   "approvedJoinOrg":     { "template": "templates/approved-join-organization.html",
 *                            "subject":  "Organization Join Request Status Update" },
 *   "approvedComputeRole": { "template": "templates/approved-compute-role.html",
 *                            "subject":  "Compute Role Access Request – {STATUS} | {PLATFORM_NAME} Platform" },
 *   "approvedCreditRequest":{ "template": "templates/approved-credit-request.html",
 *                            "subject":  "Credit Request Status Update" },
 *   "approvedProviderRole":{ "template": "templates/approved-pending-role.html",
 *                            "subject":  "Provider Role Request Status Update" },
 *   "approvedCreateOrg":   { "template": "templates/approved-create-organization.html",
 *                            "subject":  "Organization Creation Status Update" },
 *   "userStatus":          { "template": "templates/approved-user-status.html",
 *                            "subject":  "Your account has been {STATUS}" },
 *   "userStatusByAdmin":   { "template": "templates/approved-user-status-by-admin.html",
 *                            "subject":  "User account {USER_EMAIL_ID} has been {STATUS}" }
 * },
 * "emailDefaults": {
 *   "adminFirstName": "Admin",
 *   "adminLastName":  ""
 * }
 * }</pre>
 *
 * <p>Subject lines may contain {@code {TOKEN}} placeholders (e.g. {@code {STATUS}},
 * {@code {PLATFORM_NAME}}, {@code {USER_EMAIL_ID}}) resolved at send time by
 * {@code EmailComposer}.
 */
public record EmailConfig(

  // ── Template paths ───────────────────────────────────────────────────

  String createOrgTemplate,
  String joinOrgTemplate,
  String computeRoleTemplate,
  String providerRoleTemplate,
  String creditRequestTemplate,

  String approvedJoinOrgTemplate,
  String approvedComputeRoleTemplate,
  String approvedCreditRequestTemplate,
  String approvedProviderRoleTemplate,
  String approvedCreateOrgTemplate,

  String userStatusTemplate,
  String userStatusByAdminTemplate,

  // ── Subject lines ────────────────────────────────────────────────────

  String createOrgSubject,
  String joinOrgSubject,
  String computeRoleSubject,
  String providerRoleSubject,
  String creditRequestSubject,

  String approvedJoinOrgSubject,
  String approvedComputeRoleSubject,
  String approvedCreditRequestSubject,
  String approvedProviderRoleSubject,
  String approvedCreateOrgSubject,

  String userStatusSubject,
  String userStatusByAdminSubject,

  // ── Static template variable values ──────────────────────────────────

  String adminFirstName,
  String adminLastName
) {

  // ── Template defaults ────────────────────────────────────────────────

  private static final String D_CREATE_ORG_T         = "templates/request-create-organization.html";
  private static final String D_JOIN_ORG_T            = "templates/request-join-organization.html";
  private static final String D_COMPUTE_ROLE_T        = "templates/request-compute-role.html";
  private static final String D_PROVIDER_ROLE_T       = "templates/request-provider-role.html";
  private static final String D_CREDIT_REQUEST_T      = "templates/request-credit.html";
  private static final String D_APPR_JOIN_ORG_T       = "templates/approved-join-organization.html";
  private static final String D_APPR_COMPUTE_ROLE_T   = "templates/approved-compute-role.html";
  private static final String D_APPR_CREDIT_T         = "templates/approved-credit-request.html";
  private static final String D_APPR_PROVIDER_T       = "templates/approved-pending-role.html";
  private static final String D_APPR_CREATE_ORG_T     = "templates/approved-create-organization.html";
  private static final String D_USER_STATUS_T         = "templates/approved-user-status.html";
  private static final String D_USER_STATUS_ADMIN_T   = "templates/approved-user-status-by-admin.html";

  // ── Subject defaults ─────────────────────────────────────────────────

  private static final String D_CREATE_ORG_S         = "Organization Creation Request";
  private static final String D_JOIN_ORG_S            = "Join Organization Request";
  private static final String D_COMPUTE_ROLE_S        = "Compute Role Request";
  private static final String D_PROVIDER_ROLE_S       = "Provider Role Request";
  private static final String D_CREDIT_REQUEST_S      = "Credit Request";
  private static final String D_APPR_JOIN_ORG_S       = "Organization Join Request Status Update";
  private static final String D_APPR_COMPUTE_ROLE_S   = "Compute Role Access Request – {STATUS} | {PLATFORM_NAME} Platform";
  private static final String D_APPR_CREDIT_S         = "Credit Request Status Update";
  private static final String D_APPR_PROVIDER_S       = "Provider Role Request Status Update";
  private static final String D_APPR_CREATE_ORG_S     = "Organization Creation Status Update";
  private static final String D_USER_STATUS_S         = "Your account has been {STATUS}";
  private static final String D_USER_STATUS_ADMIN_S   = "User account {USER_EMAIL_ID} has been {STATUS}";

  // ── Static value defaults ─────────────────────────────────────────────

  private static final String D_ADMIN_FIRST_NAME = "Admin";
  private static final String D_ADMIN_LAST_NAME  = "";

  // ── Factory ──────────────────────────────────────────────────────────

  /**
   * Build an {@link EmailConfig} from the application {@link JsonObject}.
   * Reads {@code emailTemplates} and {@code emailDefaults} sub-objects;
   * falls back to built-in defaults for any absent key.
   */
  public static EmailConfig fromConfig(JsonObject config) {
    JsonObject t = config.getJsonObject("emailTemplates", new JsonObject());
    JsonObject d = config.getJsonObject("emailDefaults",  new JsonObject());

    return new EmailConfig(
      // templates
      template(t, "createOrg",            D_CREATE_ORG_T),
      template(t, "joinOrg",              D_JOIN_ORG_T),
      template(t, "computeRole",          D_COMPUTE_ROLE_T),
      template(t, "providerRole",         D_PROVIDER_ROLE_T),
      template(t, "creditRequest",        D_CREDIT_REQUEST_T),
      template(t, "approvedJoinOrg",      D_APPR_JOIN_ORG_T),
      template(t, "approvedComputeRole",  D_APPR_COMPUTE_ROLE_T),
      template(t, "approvedCreditRequest",D_APPR_CREDIT_T),
      template(t, "approvedProviderRole", D_APPR_PROVIDER_T),
      template(t, "approvedCreateOrg",    D_APPR_CREATE_ORG_T),
      template(t, "userStatus",           D_USER_STATUS_T),
      template(t, "userStatusByAdmin",    D_USER_STATUS_ADMIN_T),

      // subjects
      subject(t, "createOrg",            D_CREATE_ORG_S),
      subject(t, "joinOrg",              D_JOIN_ORG_S),
      subject(t, "computeRole",          D_COMPUTE_ROLE_S),
      subject(t, "providerRole",         D_PROVIDER_ROLE_S),
      subject(t, "creditRequest",        D_CREDIT_REQUEST_S),
      subject(t, "approvedJoinOrg",      D_APPR_JOIN_ORG_S),
      subject(t, "approvedComputeRole",  D_APPR_COMPUTE_ROLE_S),
      subject(t, "approvedCreditRequest",D_APPR_CREDIT_S),
      subject(t, "approvedProviderRole", D_APPR_PROVIDER_S),
      subject(t, "approvedCreateOrg",    D_APPR_CREATE_ORG_S),
      subject(t, "userStatus",           D_USER_STATUS_S),
      subject(t, "userStatusByAdmin",    D_USER_STATUS_ADMIN_S),

      // static variable values
      d.getString("adminFirstName", D_ADMIN_FIRST_NAME),
      d.getString("adminLastName",  D_ADMIN_LAST_NAME)
    );
  }

  // ── Private helpers ──────────────────────────────────────────────────

  private static String template(JsonObject templates, String key, String defaultValue) {
    return templates.getJsonObject(key, new JsonObject()).getString("template", defaultValue);
  }

  private static String subject(JsonObject templates, String key, String defaultValue) {
    return templates.getJsonObject(key, new JsonObject()).getString("subject", defaultValue);
  }
}
