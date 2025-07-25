package org.cdpg.dx.database.elastic.model;

public class AccessPolicyRequestDTO {
  private String sub;
  private Boolean myAssetsReq;

  public AccessPolicyRequestDTO(String sub, Boolean myAssetsReq) {
    this.sub = sub;
    this.myAssetsReq = myAssetsReq;
  }

  public String getSub() {
    return sub;
  }

  public void setSub(String sub) {
    this.sub = sub;
  }

  public Boolean getMyAssetsReq() {
    return myAssetsReq;
  }

  public void setMyAssetsReq(Boolean myAssetsReq) {
    this.myAssetsReq = myAssetsReq;
  }
}
