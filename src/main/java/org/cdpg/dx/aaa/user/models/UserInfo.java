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
    user.setExperience(Optional.ofNullable(json.getJsonArray("experience"))
      .map(JsonArray::<JsonObject>getList)
      .orElse(Collections.emptyList()));
    user.setEducation(Optional.ofNullable(json.getJsonArray("education"))
      .map(JsonArray::<JsonObject>getList)
      .orElse(Collections.emptyList()));
    user.setProjects(Optional.ofNullable(json.getJsonArray("projects"))
      .map(JsonArray::<JsonObject>getList)
      .orElse(Collections.emptyList()));
    user.setPublications(Optional.ofNullable(json.getJsonArray("publications"))
      .map(JsonArray::<JsonObject>getList)
      .orElse(Collections.emptyList()));
    user.setSkills(Optional.ofNullable(json.getJsonArray("skills"))
      .map(JsonArray::<String>getList)
      .orElse(Collections.emptyList()));

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
    return Collections.unmodifiableList(experience);
  }

  public void setExperience(List<JsonObject> experience) {
    this.experience = experience;
  }

  public List<JsonObject> getEducation() {
    return Collections.unmodifiableList(education);
  }

  public void setEducation(List<JsonObject> education) {
    this.education = education;
  }

  public List<JsonObject> getProjects() {
    return Collections.unmodifiableList(projects);
  }

  public void setProjects(List<JsonObject> projects) {
    this.projects = projects;
  }

  public List<JsonObject> getPublications() {
    return Collections.unmodifiableList(publications);
  }

  public void setPublications(List<JsonObject> publications) {
    this.publications = publications;
  }

  public List<String> getSkills() {
    return Collections.unmodifiableList(skills);
  }

  public void setSkills(List<String> skills) {
    this.skills = skills;
  }
}
