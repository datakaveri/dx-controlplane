package org.cdpg.dx.aaa.item.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AppsItem implements Item {
  private static final Set<String> knownFields =
      Set.of(
          "name",
          "type",
          "label",
          "shortDescription",
          "description",
          "tags",
          "accessPolicy",
          "organizationType",
          "organizationId",
          "industry",
          "department",
          "id",
          "itemStatus",
          "itemCreatedAt",
          "@context",
          "sections");
  private String name;
  private List<String> type;
  private String label;
  private String shortDescription;
  private String description;
  private List<String> tags;
  private String accessPolicy;
  private String organizationType;
  private String organizationId;
  private String industry;
  private String department;
  private String id;
  private String itemStatus;
  private String itemCreatedAt;
  private String context;
  private JsonObject additionalProperties = new JsonObject();
  private List<Section> sections;

  private static JsonArray toSubSectionJsonArray(List<Section.SubSection> list) {
    JsonArray array = new JsonArray();
    for (Section.SubSection ss : list) {
      JsonObject obj =
          new JsonObject()
              .put("subtitle", ss.getSubtitle())
              .put("content", ss.getContent())
              .put("points", ss.getPoints());
      if (ss.getSubsections() != null) {
        obj.put("subsections", toSubSectionJsonArray(ss.getSubsections()));
      }
      array.add(obj);
    }
    return array;
  }

  public static AppsItem fromJson(JsonObject json) {
    AppsItem item = new AppsItem();
    item.setName(json.getString("name"));
    item.setType(json.getJsonArray("type").getList());
    item.setLabel(json.getString("label"));
    item.setShortDescription(json.getString("shortDescription"));
    item.setDescription(json.getString("description"));
    item.setTags(json.getJsonArray("tags").getList());
    item.setAccessPolicy(json.getString("accessPolicy"));
    item.setOrganizationType(json.getString("organizationType"));
    item.setOrganizationId(json.getString("organizationId"));
    item.setIndustry(json.getString("industry"));
    item.setDepartment(json.getString("department"));
    item.setId(json.getString("id"));
    item.setItemStatus(json.getString("itemStatus"));
    item.setItemCreatedAt(json.getString("itemCreatedAt"));
    item.setContext(json.getString("@context"));

    if (json.containsKey("sections")) {
      List<Section> sections =
          json.getJsonArray("sections").stream()
              .map(o -> (JsonObject) o)
              .map(AppsItem::parseSection)
              .collect(Collectors.toList());
      item.setSections(sections);
    }

    JsonObject additional = new JsonObject();
    for (String key : json.fieldNames()) {
      if (!knownFields.contains(key)) {
        additional.put(key, json.getValue(key));
      }
    }
    item.setAdditionalProperties(additional);

    return item;
  }

  private static Section parseSection(JsonObject json) {
    Section section = new Section();
    section.setTitle(json.getString("title"));
    section.setContent(json.getString("content"));
    section.setPoints(
        json.getJsonArray("points") != null ? json.getJsonArray("points").getList() : null);

    if (json.containsKey("subsections")) {
      List<Section.SubSection> subs =
          json.getJsonArray("subsections").stream()
              .map(o -> (JsonObject) o)
              .map(AppsItem::parseSubSection)
              .collect(Collectors.toList());
      section.setSubsections(subs);
    }

    return section;
  }

  private static Section.SubSection parseSubSection(JsonObject json) {
    Section.SubSection ss = new Section.SubSection();
    ss.setSubtitle(json.getString("subtitle"));
    ss.setContent(json.getString("content"));
    ss.setPoints(
        json.getJsonArray("points") != null ? json.getJsonArray("points").getList() : null);

    if (json.containsKey("subsections")) {
      List<Section.SubSection> nestedSubs =
          json.getJsonArray("subsections").stream()
              .map(o -> (JsonObject) o)
              .map(AppsItem::parseSubSection)
              .collect(Collectors.toList());
      ss.setSubsections(nestedSubs);
    }

    return ss;
  }

  public JsonObject getAdditionalProperties() {
    return additionalProperties;
  }

  public void setAdditionalProperties(JsonObject additionalProperties) {
    this.additionalProperties = additionalProperties;
  }

  @Override
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Override
  public List<String> getType() {
    return type;
  }

  public void setType(List<String> type) {
    this.type = type;
  }

  @Override
  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  @Override
  public String getShortDescription() {
    return shortDescription;
  }

  public void setShortDescription(String shortDescription) {
    this.shortDescription = shortDescription;
  }

  @Override
  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  @Override
  public List<String> getTags() {
    return tags;
  }

  public void setTags(List<String> tags) {
    this.tags = tags;
  }

  @Override
  public String getAccessPolicy() {
    return accessPolicy;
  }

  public void setAccessPolicy(String accessPolicy) {
    this.accessPolicy = accessPolicy;
  }

  @Override
  public String getOrganizationType() {
    return organizationType;
  }

  public void setOrganizationType(String organizationType) {
    this.organizationType = organizationType;
  }

  @Override
  public String getOrganizationId() {
    return organizationId;
  }

  public void setOrganizationId(String organizationId) {
    this.organizationId = organizationId;
  }

  @Override
  public String getIndustry() {
    return industry;
  }

  public void setIndustry(String industry) {
    this.industry = industry;
  }

  @Override
  public String getDepartment() {
    return department;
  }

  public void setDepartment(String department) {
    this.department = department;
  }

  @Override
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  @Override
  public String getItemStatus() {
    return itemStatus;
  }

  public void setItemStatus(String itemStatus) {
    this.itemStatus = itemStatus;
  }

  @Override
  public String getItemCreatedAt() {
    return itemCreatedAt;
  }

  public void setItemCreatedAt(String itemCreatedAt) {
    this.itemCreatedAt = itemCreatedAt;
  }

  @Override
  public String getContext() {
    return context;
  }

  public void setContext(String context) {
    this.context = context;
  }

  public List<Section> getSections() {
    return sections;
  }

  public void setSections(List<Section> sections) {
    this.sections = sections;
  }

  @Override
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    json.put("name", name);
    json.put("type", type);
    json.put("label", label);
    json.put("shortDescription", shortDescription);
    json.put("description", description);
    json.put("tags", tags);
    json.put("accessPolicy", accessPolicy);
    json.put("organizationType", organizationType);
    json.put("organizationId", organizationId);
    json.put("industry", industry);
    json.put("department", department);
    json.put("id", id);
    json.put("itemStatus", itemStatus);
    json.put("itemCreatedAt", itemCreatedAt != null ? itemCreatedAt.toString() : null);
    json.put("@context", context);

    if (sections != null) {
      JsonArray sectionArray = new JsonArray();
      for (Section s : sections) {
        JsonObject secJson =
            new JsonObject()
                .put("title", s.getTitle())
                .put("content", s.getContent())
                .put("points", s.getPoints());

        if (s.getSubsections() != null) {
          secJson.put("subsections", toSubSectionJsonArray(s.getSubsections()));
        }
        sectionArray.add(secJson);
      }
      json.put("sections", sectionArray);
    }

    for (String key : additionalProperties.fieldNames()) {
      json.put(key, additionalProperties.getValue(key));
    }
    return json;
  }

  public static class Section {
    private String title;
    private String content;
    private List<String> points;
    private List<SubSection> subsections;

    public String getTitle() {
      return title;
    }

    // Getters/setters

    public void setTitle(String title) {
      this.title = title;
    }

    public String getContent() {
      return content;
    }

    public void setContent(String content) {
      this.content = content;
    }

    public List<String> getPoints() {
      return points;
    }

    public void setPoints(List<String> points) {
      this.points = points;
    }

    public List<SubSection> getSubsections() {
      return subsections;
    }

    public void setSubsections(List<SubSection> subsections) {
      this.subsections = subsections;
    }

    public static class SubSection {
      private String subtitle;
      private String content;
      private List<String> points;
      private List<SubSection> subsections;

      public String getSubtitle() {
        return subtitle;
      }

      public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
      }

      public String getContent() {
        return content;
      }

      public void setContent(String content) {
        this.content = content;
      }

      public List<String> getPoints() {
        return points;
      }

      public void setPoints(List<String> points) {
        this.points = points;
      }

      public List<SubSection> getSubsections() {
        return subsections;
      }

      public void setSubsections(List<SubSection> subsections) {
        this.subsections = subsections;
      }
    }
  }
}
