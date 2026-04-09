package org.cdpg.dx.testutil;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.cdpg.dx.aaa.credit.models.ComputeRole;
import org.cdpg.dx.aaa.credit.models.CreditRequest;
import org.cdpg.dx.aaa.credit.models.CreditTransaction;
import org.cdpg.dx.aaa.credit.models.UserCredit;
import org.cdpg.dx.aaa.organization.models.Organization;
import org.cdpg.dx.aaa.organization.models.OrganizationCreateRequest;
import org.cdpg.dx.aaa.organization.models.OrganizationJoinRequest;
import org.cdpg.dx.aaa.organization.models.OrganizationUser;
import org.cdpg.dx.aaa.organization.models.Role;
import org.cdpg.dx.common.model.DxUser;

/**
 * Centralized test data factory. Avoids duplicating multi-argument record constructors across test
 * files.
 */
public final class TestDataFactory {

  private TestDataFactory() {}

  // ---- Organizations ----

  public static OrganizationCreateRequest anOrgCreateRequest() {
    return anOrgCreateRequest(UUID.randomUUID(), UUID.randomUUID());
  }

  public static OrganizationCreateRequest anOrgCreateRequest(UUID requestId, UUID userId) {
    return new OrganizationCreateRequest(
        requestId,        // id
        userId,           // requestedBy
        "Test Organization", // name
        "/logo.png",      // logoPath
        "Private",        // entityType
        "Tech",           // orgSector
        "https://example.com", // websiteLink
        "123 Main St",    // address
        "/cert.pdf",      // certificatePath
        "/pan.pdf",       // pancardPath
        "/doc.pdf",       // relevantDocPath
        "pending",        // status
        "Test User",      // userName
        "EMP001",         // empId
        "Developer",      // jobTitle
        "9876543210",     // orgManagerphoneNo
        "mgr@example.com", // managerEmail
        "org documents",  // orgDocuments
        null,             // createdAt
        null);            // updatedAt
  }

  public static OrganizationJoinRequest anOrgJoinRequest(UUID orgId, UUID userId) {
    return new OrganizationJoinRequest(
        UUID.randomUUID(),
        orgId,
        userId,
        "Test User",
        "pending",
        "Developer",
        "EMP001",
        "user@example.com",
        null,
        null);
  }

  public static OrganizationUser anOrgUser(UUID orgId, UUID userId) {
    return new OrganizationUser(
        UUID.randomUUID(),
        orgId,
        userId,
        "Test User",
        Role.USER,
        "Developer",
        "EMP001",
        "9876543210",
        "user@example.com",
        null,
        null);
  }

  // ---- Credits ----

  public static CreditRequest aCreditRequest(UUID userId) {
    return aCreditRequest(userId, "pending");
  }

  public static CreditRequest aCreditRequest(UUID userId, String status) {
    return new CreditRequest(
        UUID.randomUUID(),
        userId,
        "Test User",
        new JsonObject().put("reason", "testing"),
        status,
        LocalDateTime.now(),
        null);
  }

  public static UserCredit aUserCredit(UUID userId, double balance) {
    return new UserCredit(
        UUID.randomUUID(), userId, balance, LocalDateTime.now().plusDays(30), LocalDateTime.now());
  }

  public static CreditTransaction aCreditTransaction(
      UUID userId, double amount, String type, UUID transactedBy) {
    return new CreditTransaction(
        null, userId, amount, transactedBy, "success", type, null, LocalDateTime.now(), amount);
  }

  public static ComputeRole aComputeRole(UUID userId) {
    return aComputeRole(userId, "pending");
  }

  public static ComputeRole aComputeRole(UUID userId, String status) {
    return new ComputeRole(
        UUID.randomUUID(),
        userId,
        "Test User",
        status,
        null,
        new JsonObject().put("reason", "testing"),
        null,
        null);
  }

  // ---- DxUser (for auth context) ----

  public static DxUser aDxUser(UUID userId, String... roles) {
    return new DxUser(
        List.of(roles), // roles
        null, // organisationId
        null, // organisationName
        userId, // sub
        true, // emailVerified
        false, // kycVerified
        "Test User", // name
        "test", // preferredUsername
        "Test", // givenName
        "User", // familyName
        "test@example.com", // email
        List.of(), // pendingRoles
        null, // organisation
        null, // createdAt
        null, // kycData
        null, // twitter_account
        null, // linkedin_account
        null, // github_account
        true, // account_enabled
        null, // did
        null, // aud
        new JsonArray() // scopes
        );
  }

  public static DxUser aCosAdmin() {
    return aDxUser(UUID.randomUUID(), "cos_admin");
  }

  public static DxUser aProvider(UUID userId) {
    return aDxUser(userId, "provider");
  }

  public static DxUser aConsumer(UUID userId) {
    return aDxUser(userId, "consumer");
  }
}
