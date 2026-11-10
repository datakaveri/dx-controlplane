package org.cdpg.dx.email.model;

public enum TemplateType {
  ASSET_REQUEST_CREATE("templates/AssetRequestEmailTemplate.html"),
  ASSET_REQUEST_APPROVAL("templates/AssetRequestApprovedEmailTemplate.html");

  private String path;

  TemplateType(String path) {
    this.path = path;
  }

  public String getPath() {
    return path;
  }
}
