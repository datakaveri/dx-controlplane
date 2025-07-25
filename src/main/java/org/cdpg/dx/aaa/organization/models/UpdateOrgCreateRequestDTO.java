package org.cdpg.dx.aaa.organization.models;

import org.cdpg.dx.aaa.organization.config.Constants;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public record UpdateOrgCreateRequestDTO(
  Optional<String> name,
  Optional<String> logoPath,
  Optional<String> entityType,
  Optional<String> orgSector,
  Optional<String> websiteLink,
  Optional<String> address,
  Optional<String> certificatePath,
  Optional<String> pancardPath,
  Optional<String> relevantDocPath,
  Optional<String> status,
  Optional<String> empId,
  Optional<String> jobTitle,
  Optional<String> orgManagerphoneNo,
  Optional<String> updatedAt
) {
  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();

    name.ifPresent(value -> map.put(Constants.ORG_NAME, value));
    logoPath.ifPresent(value -> map.put(Constants.ORG_LOGO, value));
    entityType.ifPresent(value -> map.put(Constants.ENTITY_TYPE, value));
    orgSector.ifPresent(value -> map.put(Constants.ORG_SECTOR, value));
    websiteLink.ifPresent(value -> map.put(Constants.ORG_WEBSITE, value));
    address.ifPresent(value -> map.put(Constants.ORG_ADDRESS, value));
    certificatePath.ifPresent(value -> map.put(Constants.CERTIFICATE, value));
    pancardPath.ifPresent(value -> map.put(Constants.PANCARD, value));
    relevantDocPath.ifPresent(value -> map.put(Constants.RELEVANT_DOC, value));
    status.ifPresent(value -> map.put(Constants.STATUS, value));
    empId.ifPresent(value -> map.put(Constants.EMP_ID, value));
    jobTitle.ifPresent(value -> map.put(Constants.JOB_TITLE, value));
    orgManagerphoneNo.ifPresent(value->map.put(Constants.PHONE_NO,value));
    updatedAt.ifPresent(value -> map.put(Constants.UPDATED_AT, value));

    return map;
  }
}

