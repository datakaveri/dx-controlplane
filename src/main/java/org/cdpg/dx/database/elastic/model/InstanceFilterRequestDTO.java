package org.cdpg.dx.database.elastic.model;

public class InstanceFilterRequestDTO {
  private String instance;

  public InstanceFilterRequestDTO(String instance) {
    this.instance = instance;
  }

  public String getInstance() {
    return instance;
  }

  public void setInstance(String instance) {
    this.instance = instance;
  }
}
