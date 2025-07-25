package org.cdpg.dx.aaa.organization.models;

import org.cdpg.dx.aaa.organization.config.Constants;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public record UpdateOrgJoinRequestDTO(
  String status,
  String jobTitle,
  String empId,
  Optional<String> processedAt
) {
  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();

    if (status != null && !status.isEmpty()) {
      map.put(Constants.STATUS, status);
    }
    if (jobTitle != null && !jobTitle.isEmpty()) {
      map.put(Constants.JOB_TITLE, jobTitle);
    }
    if (empId != null && !empId.isEmpty()) {
      map.put(Constants.EMP_ID, empId);
    }
    processedAt.ifPresent(val -> map.put(Constants.PROCESSED_AT, val));

    return map;
  }
}
