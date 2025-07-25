package org.cdpg.dx.aaa.organization.models;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.organization.config.Constants;
import org.cdpg.dx.common.exception.DxValidationException;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

import static org.cdpg.dx.common.util.DateTimeHelper.FORMATTER;
import static org.cdpg.dx.common.util.DateTimeHelper.parseDateTime;
import static org.cdpg.dx.common.util.ValidationUtils.requireNonNull;

public record OrganizationCreateRequest(
        UUID id,
        UUID requestedBy,
        String name,
        String logoPath,
        String entityType,
        String orgSector,
        String websiteLink,
        String address,
        String certificatePath,
        String pancardPath,
        String relevantDocPath,
        String status,
        String userName,
        String empId,
        String jobTitle,
        String orgManagerphoneNo,
        String managerEmail,
        String orgDocuments,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) implements BaseEntity<OrganizationCreateRequest> {

  public static OrganizationCreateRequest fromJson(JsonObject json) {
    try {
      return new OrganizationCreateRequest(
              json.getString(Constants.ORG_CREATE_ID) != null
                      ? UUID.fromString(json.getString(Constants.ORG_CREATE_ID))
                      : null,
              UUID.fromString(requireNonNull(json.getString(Constants.REQUESTED_BY), Constants.REQUESTED_BY)),
              requireNonNull(json.getString(Constants.ORG_NAME), Constants.ORG_NAME),
              json.getString(Constants.ORG_LOGO),
              requireNonNull(json.getString(Constants.ENTITY_TYPE), Constants.ENTITY_TYPE),
              requireNonNull(json.getString(Constants.ORG_SECTOR), Constants.ORG_SECTOR),
              requireNonNull(json.getString(Constants.ORG_WEBSITE), Constants.ORG_WEBSITE),
              requireNonNull(json.getString(Constants.ORG_ADDRESS), Constants.ORG_ADDRESS),
              requireNonNull(json.getString(Constants.CERTIFICATE), Constants.CERTIFICATE),
              requireNonNull(json.getString(Constants.PANCARD), Constants.PANCARD),
              json.getString(Constants.RELEVANT_DOC),
              json.getString(Constants.STATUS) != null
                      ? json.getString(Constants.STATUS)
                      : Status.PENDING.getStatus(),
        requireNonNull(json.getString(Constants.USER_NAME), Constants.USER_NAME),
        requireNonNull(json.getString(Constants.EMP_ID), Constants.EMP_ID),
              requireNonNull(json.getString(Constants.JOB_TITLE), Constants.JOB_TITLE),
              requireNonNull(json.getString(Constants.PHONE_NO), Constants.PHONE_NO),
        requireNonNull(json.getString(Constants.MANAGER_EMAIL), Constants.MANAGER_EMAIL),
        requireNonNull(json.getString(Constants.ORG_DOCUMENTS), Constants.ORG_DOCUMENTS),
        parseDateTime(json.getString(Constants.CREATED_AT)),
              parseDateTime(json.getString(Constants.UPDATED_AT))
      );
    } catch (IllegalArgumentException e) {
      throw new DxValidationException("Missing or invalid required field: " + e.getMessage());
    }
  }

  @Override
  public JsonObject toJson() {
    JsonObject json = new JsonObject();

    if (id != null) json.put(Constants.ORG_CREATE_ID, id.toString());
    json.put(Constants.REQUESTED_BY, requestedBy.toString());
    json.put(Constants.ORG_NAME, name);
    if (logoPath != null && !logoPath.isEmpty()) json.put(Constants.ORG_LOGO, logoPath);
    json.put(Constants.ENTITY_TYPE, entityType);
    json.put(Constants.ORG_SECTOR, orgSector);
    json.put(Constants.ORG_WEBSITE, websiteLink);
    json.put(Constants.ORG_ADDRESS, address);
    json.put(Constants.CERTIFICATE, certificatePath);
    json.put(Constants.PANCARD, pancardPath);
    if (relevantDocPath != null && !relevantDocPath.isEmpty()) json.put(Constants.RELEVANT_DOC, relevantDocPath);
    json.put(Constants.STATUS, status);
    json.put(Constants.USER_NAME, userName);
    json.put(Constants.EMP_ID, empId);
    json.put(Constants.JOB_TITLE, jobTitle);
    json.put(Constants.PHONE_NO, orgManagerphoneNo);
    json.put(Constants.MANAGER_EMAIL,managerEmail);
    if (orgDocuments != null && !orgDocuments.isEmpty()) json.put(Constants.ORG_DOCUMENTS, orgDocuments);
    if (createdAt != null) json.put(Constants.CREATED_AT, createdAt.format(FORMATTER));
    if (updatedAt != null) json.put(Constants.UPDATED_AT, updatedAt.format(FORMATTER));

    return json;
  }

  public JsonObject toJsonForUsers() {
    JsonObject json = new JsonObject();

    if (id != null) json.put(Constants.ORG_CREATE_ID, id.toString());
    json.put("requestedBy", requestedBy.toString());
    json.put("name", name);
    if (logoPath != null && !logoPath.isEmpty()) json.put("logoPath", logoPath);
    json.put("entityType", entityType);
    json.put("orgSector", orgSector);
    json.put("websiteLink", websiteLink);
    json.put("address", address);
    json.put("certificatePath", certificatePath);
    json.put("pancardPath", pancardPath);
    if (relevantDocPath != null && !relevantDocPath.isEmpty()) json.put("relevantDocPath", relevantDocPath);
    json.put("status", status);
    json.put("userName", userName);
    json.put("empId", empId);
    json.put("jobTitle", jobTitle);
    json.put("orgManagerphoneNo", orgManagerphoneNo);
    json.put("orgDocuments", orgDocuments);
    json.put("managerEmail",managerEmail);
    if (createdAt != null) json.put("createdAt", createdAt.format(FORMATTER));
    if (updatedAt != null) json.put("updatedAt", updatedAt.format(FORMATTER));

    return json;
  }

  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();

    if (id != null) map.put(Constants.ORG_CREATE_ID, id);
    map.put(Constants.REQUESTED_BY, requestedBy.toString());
    if (!name.isEmpty()) map.put(Constants.ORG_NAME, name);
    if (logoPath != null && !logoPath.isEmpty()) map.put(Constants.ORG_LOGO, logoPath);
    if (!entityType.isEmpty()) map.put(Constants.ENTITY_TYPE, entityType);
    if (!orgSector.isEmpty()) map.put(Constants.ORG_SECTOR, orgSector);
    if (!websiteLink.isEmpty()) map.put(Constants.ORG_WEBSITE, websiteLink);
    if (!address.isEmpty()) map.put(Constants.ORG_ADDRESS, address);
    if (!certificatePath.isEmpty()) map.put(Constants.CERTIFICATE, certificatePath);
    if (!pancardPath.isEmpty()) map.put(Constants.PANCARD, pancardPath);
    if (relevantDocPath != null && !relevantDocPath.isEmpty()) map.put(Constants.RELEVANT_DOC, relevantDocPath);
    if (!status.isEmpty()) map.put(Constants.STATUS, status);
    if (!userName.isEmpty()) map.put(Constants.USER_NAME, userName);
    if (!empId.isEmpty()) map.put(Constants.EMP_ID, empId);
    if (!jobTitle.isEmpty()) map.put(Constants.JOB_TITLE, jobTitle);
    if (!orgManagerphoneNo.isEmpty()) map.put(Constants.PHONE_NO, orgManagerphoneNo);
    if (!managerEmail.isEmpty()) map.put(Constants.MANAGER_EMAIL, managerEmail);
    if (!orgDocuments.isEmpty()) map.put(Constants.ORG_DOCUMENTS, orgDocuments);
    if (createdAt != null) map.put(Constants.CREATED_AT, createdAt.format(FORMATTER));
    if (updatedAt != null) map.put(Constants.UPDATED_AT, updatedAt.format(FORMATTER));

    return map;
  }

  @Override
  public String getTableName() {
    return Constants.ORG_CREATE_REQUEST_TABLE;
  }
}
