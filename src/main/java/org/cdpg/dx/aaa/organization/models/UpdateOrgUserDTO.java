package org.cdpg.dx.aaa.organization.models;

import org.cdpg.dx.aaa.organization.config.Constants;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public record UpdateOrgUserDTO(
  Role role,
  String jobTitle,
  String empId,
  Optional<String> updatedAt
) {
  public Map<String, Object> toNonEmptyFieldsMap() {
    HashMap<String, Object> map = new HashMap<>();

    if (role != null && role.getRoleName() != null && !role.getRoleName().isEmpty()) {
      map.put(Constants.ROLE, role.getRoleName());
    }
    if (jobTitle != null && !jobTitle.isEmpty()) {
      map.put(Constants.JOB_TITLE, jobTitle);
    }
    if (empId != null && !empId.isEmpty()) {
      map.put(Constants.EMP_ID, empId);
    }
    updatedAt.ifPresent(update -> map.put(Constants.UPDATED_AT, update));

    return map;
  }
}


