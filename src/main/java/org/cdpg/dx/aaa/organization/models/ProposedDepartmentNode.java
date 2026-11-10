package org.cdpg.dx.aaa.organization.models;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.List;

/**
 * One department of a tree proposed by an org_admin, expressed as a nested
 * structure: each department carries its own {@code subDepartments}. The
 * organization itself is the implicit top of the tree, so the proposal is a
 * list of top-level departments. The client never supplies ids -- the server
 * generates them when the approved tree is applied.
 */
public record ProposedDepartmentNode(String name, List<ProposedDepartmentNode> subDepartments) {

  public static final String NAME = "name";
  public static final String SUB_DEPARTMENTS = "subDepartments";

  public ProposedDepartmentNode {
    subDepartments = subDepartments == null ? List.of() : List.copyOf(subDepartments);
  }

  public static ProposedDepartmentNode fromJson(JsonObject json) {
    if (json == null) {
      throw new IllegalArgumentException("Department must be a JSON object");
    }

    List<ProposedDepartmentNode> subDepartments = new ArrayList<>();
    JsonArray subDepartmentsJson = json.getJsonArray(SUB_DEPARTMENTS);
    if (subDepartmentsJson != null) {
      for (int i = 0; i < subDepartmentsJson.size(); i++) {
        subDepartments.add(fromJson(subDepartmentsJson.getJsonObject(i)));
      }
    }

    return new ProposedDepartmentNode(json.getString(NAME), subDepartments);
  }

  public static List<ProposedDepartmentNode> listFromJson(JsonArray json) {
    if (json == null) {
      throw new IllegalArgumentException("Departments must be a JSON array");
    }

    List<ProposedDepartmentNode> departments = new ArrayList<>();
    for (int i = 0; i < json.size(); i++) {
      departments.add(fromJson(json.getJsonObject(i)));
    }
    return departments;
  }

  public static JsonArray listToJson(List<ProposedDepartmentNode> departments) {
    JsonArray json = new JsonArray();
    departments.forEach(department -> json.add(department.toJson()));
    return json;
  }

  public JsonObject toJson() {
    return new JsonObject().put(NAME, name).put(SUB_DEPARTMENTS, listToJson(subDepartments));
  }
}
