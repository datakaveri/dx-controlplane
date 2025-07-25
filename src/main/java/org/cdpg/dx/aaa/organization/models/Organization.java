package org.cdpg.dx.aaa.organization.models;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.organization.config.Constants;
import org.cdpg.dx.common.exception.DxValidationException;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.cdpg.dx.common.util.DateTimeHelper.parseDateTime;
import static org.cdpg.dx.common.util.DateTimeHelper.FORMATTER;

import static org.cdpg.dx.common.util.ValidationUtils.requireNonNull;

public record Organization(
        UUID id,
        String orgName,
        String orgLogo,
        String entityType,
        String orgSector,
        String websiteLink,
        String address,
        String certificatePath,
        String pancardPath,
        String relevantDocPath,
        String orgDocuments,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
)  implements BaseEntity<Organization> {
  public static Organization fromJson(JsonObject orgDetails) {
    try {
      return new Organization(
              orgDetails.getString(Constants.ORG_ID) != null
                      ? UUID.fromString(orgDetails.getString(Constants.ORG_ID))
                      : null,
              requireNonNull(orgDetails.getString(Constants.ORG_NAME), Constants.ORG_NAME),
              orgDetails.getString(Constants.ORG_LOGO),
              requireNonNull(orgDetails.getString(Constants.ENTITY_TYPE), Constants.ENTITY_TYPE),
              requireNonNull(orgDetails.getString(Constants.ORG_SECTOR), Constants.ORG_SECTOR),
              requireNonNull(orgDetails.getString(Constants.ORG_WEBSITE), Constants.ORG_WEBSITE),
              requireNonNull(orgDetails.getString(Constants.ORG_ADDRESS), Constants.ORG_ADDRESS),
              requireNonNull(orgDetails.getString(Constants.CERTIFICATE), Constants.CERTIFICATE),
              requireNonNull(orgDetails.getString(Constants.PANCARD), Constants.PANCARD),
              orgDetails.getString(Constants.RELEVANT_DOC),
              requireNonNull(orgDetails.getString(Constants.ORG_DOCUMENTS), Constants.ORG_DOCUMENTS),
              parseDateTime(orgDetails.getString(Constants.CREATED_AT)),
              parseDateTime(orgDetails.getString(Constants.UPDATED_AT))
      );
    } catch (IllegalArgumentException e) {
      throw new DxValidationException("Missing or invalid required field: " + e.getMessage());
    }
  }



  @Override
  public JsonObject toJson() {
    JsonObject json = new JsonObject();

    json.put(Constants.ORG_ID, id.toString());
    json.put(Constants.ORG_NAME, orgName);
    if (orgLogo != null && !orgLogo.isEmpty()) json.put(Constants.ORG_LOGO, orgLogo);
    json.put(Constants.ENTITY_TYPE, entityType);
    json.put(Constants.ORG_SECTOR, orgSector);
    json.put(Constants.ORG_WEBSITE, websiteLink);
    json.put(Constants.ORG_ADDRESS, address);
    json.put(Constants.CERTIFICATE, certificatePath);
    json.put(Constants.PANCARD, pancardPath);
    if (relevantDocPath != null && !relevantDocPath.isEmpty()) json.put(Constants.RELEVANT_DOC, relevantDocPath);
    json.put(Constants.ORG_DOCUMENTS,orgDocuments);
    if (createdAt != null) json.put(Constants.CREATED_AT, createdAt.format(FORMATTER));
    if (updatedAt != null) json.put(Constants.UPDATED_AT, updatedAt.format(FORMATTER));

    return json;
  }

  public JsonObject toFilteredJson() {
    JsonObject json = new JsonObject();

    json.put("id", id.toString());
    json.put("orgName", orgName);
    if (orgLogo != null && !orgLogo.isEmpty()) json.put("orgLogo", orgLogo);
    json.put("entityType", entityType);
    json.put("orgSector", orgSector);
    json.put("websiteLink", websiteLink);
    json.put("address", address);
    if (createdAt != null) json.put("createdAt", createdAt.format(FORMATTER));
    if (updatedAt != null) json.put("updatedAt", updatedAt.format(FORMATTER));
    return json;
  }

  @Override
  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();

    if (id != null) map.put(Constants.ORG_ID, id.toString());
    if (!orgName.isEmpty()) map.put(Constants.ORG_NAME, orgName);
    if (orgLogo != null && !orgLogo.isEmpty()) map.put(Constants.ORG_LOGO, orgLogo);
    if (!entityType.isEmpty()) map.put(Constants.ENTITY_TYPE, entityType);
    if (!orgSector.isEmpty()) map.put(Constants.ORG_SECTOR, orgSector);
    if (!websiteLink.isEmpty()) map.put(Constants.ORG_WEBSITE, websiteLink);
    if (!address.isEmpty()) map.put(Constants.ORG_ADDRESS, address);
    if (!certificatePath.isEmpty()) map.put(Constants.CERTIFICATE, certificatePath);
    if (!pancardPath.isEmpty()) map.put(Constants.PANCARD, pancardPath);
    if (relevantDocPath != null && !relevantDocPath.isEmpty()) map.put(Constants.RELEVANT_DOC, relevantDocPath);
    if (!orgDocuments.isEmpty()) map.put(Constants.ORG_DOCUMENTS, orgDocuments);
    if (createdAt != null) map.put(Constants.CREATED_AT, createdAt.format(FORMATTER));
    if (updatedAt != null) map.put(Constants.UPDATED_AT, updatedAt.format(FORMATTER));

    return map;
  }

  @Override
  public String getTableName() {
    return Constants.ORGANIZATION_TABLE;
  }
}
