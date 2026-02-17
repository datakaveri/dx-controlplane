package org.cdpg.dx.aaa.kyc.util;

public enum KYCAuditOperation
{
  VERIFY("Verify kyc"),
  CONFIRM("confirm kyc"),
  REVOKE("Revoke kyc");

  private final String value;

  KYCAuditOperation(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
