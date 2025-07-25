package org.cdpg.dx.auditing.model;

public enum RequestType {
  DOWNLOAD("download");

  private final String request;

  RequestType(String request) {
    this.request = request;
  }

  public String getRequest() {
    return this.request;
  }
}
