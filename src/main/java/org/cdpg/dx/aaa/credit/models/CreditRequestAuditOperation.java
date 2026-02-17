package org.cdpg.dx.aaa.credit.models;

public enum CreditRequestAuditOperation
{
  UPDATE("Update request status"),
  REQUEST_COMPUTE("Request for compute role"),
  REQUEST_CREDITS("Request for credits"),
  CREDIT("Addition of credits"),
  DEBIT("Debit of credits"),
  GET_CREDIT_REQUESTS("Get credit requests"),
  GET_COMPUTE_REQUESTS("Get compute requests"),
  GET_BALANCE("Get Balance"),
  DELETE("Delete pending request");

  private final String value;

  CreditRequestAuditOperation(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
