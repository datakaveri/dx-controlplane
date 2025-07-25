package org.cdpg.dx.aaa.organization.models;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.organization.config.Constants;

import java.util.HashMap;
import java.util.Map;

import static org.cdpg.dx.common.util.DateTimeHelper.FORMATTER;

public record UpdateOrgDTO(
        String orgName,
        String orgLogo,
        String entityType,
        String orgSector,
        String websiteLink,
        String address,
        String certificatePath,
        String pancardPath,
        String relevantDocPath,
        String updatedAt
) {

  public Map<String, Object> toNonEmptyFieldsMap() {
    HashMap<String, Object> map = new HashMap<>();

    if (orgName != null) map.put(Constants.ORG_NAME, orgName);
    if (orgLogo != null) map.put(Constants.ORG_LOGO, orgLogo);
    if (entityType != null) map.put(Constants.ENTITY_TYPE, entityType);
    if (orgSector != null) map.put(Constants.ORG_SECTOR, orgSector);
    if (websiteLink != null) map.put(Constants.ORG_WEBSITE, websiteLink);
    if (address != null) map.put(Constants.ORG_ADDRESS, address);
    if (certificatePath != null) map.put(Constants.CERTIFICATE, certificatePath);
    if (pancardPath != null) map.put(Constants.PANCARD, pancardPath);
    if (relevantDocPath != null) map.put(Constants.RELEVANT_DOC, relevantDocPath);
    if (updatedAt != null) map.put(Constants.UPDATED_AT, updatedAt);

    return map;
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();

    json.put(Constants.ORG_NAME, orgName);
    if (orgLogo != null && !orgLogo.isEmpty()) json.put(Constants.ORG_LOGO, orgLogo);
    json.put(Constants.ENTITY_TYPE, entityType);
    json.put(Constants.ORG_SECTOR, orgSector);
    json.put(Constants.ORG_WEBSITE, websiteLink);
    json.put(Constants.ORG_ADDRESS, address);
    json.put(Constants.CERTIFICATE, certificatePath);
    json.put(Constants.PANCARD, pancardPath);
    if (relevantDocPath != null && !relevantDocPath.isEmpty()) json.put(Constants.RELEVANT_DOC, relevantDocPath);
    if (updatedAt != null) json.put(Constants.UPDATED_AT, updatedAt.format(String.valueOf(FORMATTER)));

    return json;
  }

  public static UpdateOrgDTO fromJson(JsonObject orgDetails) {
    return new UpdateOrgDTO(
            orgDetails.getString(Constants.ORG_NAME),
            orgDetails.getString(Constants.ORG_LOGO),
            orgDetails.getString(Constants.ENTITY_TYPE),
            orgDetails.getString(Constants.ORG_SECTOR),
            orgDetails.getString(Constants.ORG_WEBSITE),
            orgDetails.getString(Constants.ORG_ADDRESS),
            orgDetails.getString(Constants.CERTIFICATE),
            orgDetails.getString(Constants.PANCARD),
            orgDetails.getString(Constants.RELEVANT_DOC),
            orgDetails.getString(Constants.UPDATED_AT)
    );
  }
}
