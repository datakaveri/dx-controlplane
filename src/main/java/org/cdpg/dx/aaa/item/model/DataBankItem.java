package org.cdpg.dx.aaa.item.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.vertx.core.json.JsonObject;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DataBankItem implements Item {
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

  private String fileFormat;
  private String geoCoverage;
  private String yearRange;
  private String verifiedBy;
  private String uploadFrequency;
  private String license;
  private String uploadedBy;
  private Boolean dataUploadStatus;
  private String mediaURL;

  // New field to store additional/unknown fields
  private JsonObject additionalFields = new JsonObject();

  public static DataBankItem fromJson(JsonObject json) {
    DataBankItem item = new DataBankItem();

    item.setName(json.getString("name"));
    item.setType(json.getJsonArray("type") != null ? json.getJsonArray("type").getList() : null);
    item.setLabel(json.getString("label"));
    item.setShortDescription(json.getString("shortDescription"));
    item.setDescription(json.getString("description"));
    item.setTags(json.getJsonArray("tags") != null ? json.getJsonArray("tags").getList() : null);
    item.setAccessPolicy(json.getString("accessPolicy"));
    item.setOrganizationType(json.getString("organizationType"));
    item.setOrganizationId(json.getString("organizationId"));
    item.setIndustry(json.getString("industry"));
    item.setDepartment(json.getString("department"));
    item.setId(json.getString("id"));
    item.setItemStatus(json.getString("itemStatus"));

    item.setItemCreatedAt(json.getString("itemCreatedAt"));

    item.setContext(json.getString("@context"));

    item.setFileFormat(json.getString("fileFormat"));
    item.setGeoCoverage(json.getString("geoCoverage"));
    item.setYearRange(json.getString("yearRange"));
    item.setVerifiedBy(json.getString("verifiedBy"));
    item.setUploadFrequency(json.getString("uploadFrequency"));
    item.setLicense(json.getString("license"));
    item.setUploadedBy(json.getString("uploadedBy"));
    item.setDataUploadStatus(json.getBoolean("dataUploadStatus"));
    item.setMediaURL(json.getString("mediaURL"));

    // Extract unknown fields
    Set<String> knownFields =
        new HashSet<>(
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
                "fileFormat",
                "geoCoverage",
                "yearRange",
                "verifiedBy",
                "uploadFrequency",
                "license",
                "uploadedBy",
                "dataUploadStatus",
                "mediaURL"));

    JsonObject extras = new JsonObject();
    for (String key : json.fieldNames()) {
      if (!knownFields.contains(key)) {
        extras.put(key, json.getValue(key));
      }
    }
    item.setAdditionalFields(extras);

    return item;
  }

  public JsonObject getAdditionalFields() {
    return additionalFields;
  }

  public void setAdditionalFields(JsonObject additionalFields) {
    this.additionalFields = additionalFields;
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

  public String getFileFormat() {
    return fileFormat;
  }

  public void setFileFormat(String fileFormat) {
    this.fileFormat = fileFormat;
  }

  public String getGeoCoverage() {
    return geoCoverage;
  }

  public void setGeoCoverage(String geoCoverage) {
    this.geoCoverage = geoCoverage;
  }

  public String getYearRange() {
    return yearRange;
  }

  public void setYearRange(String yearRange) {
    this.yearRange = yearRange;
  }

  public String getVerifiedBy() {
    return verifiedBy;
  }

  public void setVerifiedBy(String verifiedBy) {
    this.verifiedBy = verifiedBy;
  }

  public String getUploadFrequency() {
    return uploadFrequency;
  }

  public void setUploadFrequency(String uploadFrequency) {
    this.uploadFrequency = uploadFrequency;
  }

  public String getLicense() {
    return license;
  }

  public void setLicense(String license) {
    this.license = license;
  }

  public String getUploadedBy() {
    return uploadedBy;
  }

  public void setUploadedBy(String uploadedBy) {
    this.uploadedBy = uploadedBy;
  }

  public Boolean getDataUploadStatus() {
    return dataUploadStatus;
  }

  public void setDataUploadStatus(Boolean dataUploadStatus) {
    this.dataUploadStatus = dataUploadStatus;
  }

  public String getMediaURL() {
    return mediaURL;
  }

  public void setMediaURL(String mediaURL) {
    this.mediaURL = mediaURL;
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

    json.put("fileFormat", fileFormat);
    json.put("geoCoverage", geoCoverage);
    json.put("yearRange", yearRange);
    json.put("verifiedBy", verifiedBy);
    json.put("uploadFrequency", uploadFrequency);
    json.put("license", license);
    json.put("uploadedBy", uploadedBy);
    json.put("dataUploadStatus", dataUploadStatus);
    json.put("mediaURL", mediaURL);

    // Include additional fields
    json.mergeIn(additionalFields, true);
    return json;
  }
}
