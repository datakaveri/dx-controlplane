package org.cdpg.dx.aaa.credit.models;

public enum CreditRequestAuditOperation
{
  APPROVE("Accept credit/compute request status"),
  REJECT("Reject credit/compute request status"),
  UPDATE("Update request status"),
  REQUEST_COMPUTE("Request for compute role"),
  REQUEST_CREDITS("Request for credits"),
  CREDIT("Addition of credits"),
  DEBIT("Debit of credits"),
  GET("Check balance or requests"),
  DELETE("Delete pending request");

  private final String value;

  CreditRequestAuditOperation(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
