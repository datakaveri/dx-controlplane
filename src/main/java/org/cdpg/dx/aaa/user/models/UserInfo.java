package org.cdpg.dx.aaa.user.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UserInfo {
  private String userId;
  private String about;
  private List<JsonObject> experience;
  private List<JsonObject> education;
  private List<JsonObject> projects;
  private List<JsonObject> publications;
  private List<String> skills;



  public static UserInfo fromJson(JsonObject json) {
    UserInfo user = new UserInfo();

    user.setUserId(json.getString("userId"));
    user.setAbout(json.getString("about"));
    user.setExperience(json.getJsonArray("experience") != null
      ? json.getJsonArray("experience").getList()
      : Collections.emptyList());
    user.setEducation(json.getJsonArray("education") != null
      ? json.getJsonArray("education").getList()
      : Collections.emptyList());
    user.setProjects(json.getJsonArray("projects") != null
      ? json.getJsonArray("projects").getList()
      : Collections.emptyList());
    user.setPublications(json.getJsonArray("publications") != null
      ? json.getJsonArray("publications").getList()
      : Collections.emptyList());
    user.setSkills(json.getJsonArray("skills") != null
      ? json.getJsonArray("skills").getList()
      : Collections.emptyList());

//    // Handle additional fields
//    Set<String> knownKeys = new HashSet<>(Set.of(
//      "userid", "about", "experience", "education", "projects", "publications", "skills"
//    ));
//
//    JsonObject extras = new JsonObject();
//    for (String key : json.fieldNames()) {
//      if (!knownKeys.contains(key.toLowerCase())) {
//        extras.put(key.toLowerCase(), json.getValue(key));
//      }
//    }
//    user.setAdditionalFields(extras);

    return user;
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    json.put("userId", userId);
    json.put("about", about);
    json.put("experience", new JsonArray(experience));
    json.put("education", new JsonArray(education));
    json.put("projects", new JsonArray(projects));
    json.put("publications", new JsonArray(publications));
    json.put("skills", new JsonArray(skills));

//    if (additionalFields != null) {
//      json.mergeIn(additionalFields, true);
//    }

    return json;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getAbout() {
    return about;
  }

  public void setAbout(String about) {
    this.about = about;
  }

  public List<JsonObject> getExperience() {
    return experience;
  }

  public void setExperience(List<JsonObject> experience) {
    this.experience = experience;
  }

  public List<JsonObject> getEducation() {
    return education;
  }

  public void setEducation(List<JsonObject> education) {
    this.education = education;
  }

  public List<JsonObject> getProjects() {
    return projects;
  }

  public void setProjects(List<JsonObject> projects) {
    this.projects = projects;
  }

  public List<JsonObject> getPublications() {
    return publications;
  }

  public void setPublications(List<JsonObject> publications) {
    this.publications = publications;
  }

  public List<String> getSkills() {
    return skills;
  }

  public void setSkills(List<String> skills) {
    this.skills = skills;
  }

//  public JsonObject getAdditionalFields() {
//    return additionalFields;
//  }
//
//  public void setAdditionalFields(JsonObject additionalFields) {
//    this.additionalFields = additionalFields;
//  }
}
