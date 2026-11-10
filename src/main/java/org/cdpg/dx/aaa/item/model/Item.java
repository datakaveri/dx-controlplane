package org.cdpg.dx.aaa.item.model;

import io.vertx.core.json.JsonObject;
import java.util.List;

/** Common interface for all item types. */
public interface Item {

  String getName();

  List<String> getType();

  String getLabel();

  String getShortDescription();

  String getDescription();

  List<String> getTags();

  String getAccessPolicy();

  String getOrganizationType();

  String getOrganizationId();

  String getIndustry();

  String getDepartment();

  /**
   * Which organization department (hierarchy node) owns this item, if the
   * owning organization has departments enabled. Distinct from {@link
   * #getDepartment()}, which is unrelated free-text catalog metadata (e.g.
   * "which government department this dataset is about"), not an org
   * hierarchy reference.
   */
  String getHierarchyNodeId();

  String getId();

  String getItemStatus();

  String getItemCreatedAt();

  String getContext();

  // Serialize this object to JsonObject
  JsonObject toJson();
}
