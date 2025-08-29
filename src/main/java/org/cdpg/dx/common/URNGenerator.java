package org.cdpg.dx.common;

public class URNGenerator {

  private final String baseUrnPrefix;

  public URNGenerator(String baseUrnPrefix) {
    this.baseUrnPrefix = baseUrnPrefix;
  }

  public String generateUrn(String identifier) {
    return baseUrnPrefix + identifier;
  }

  public String getBaseUrnPrefix() {
    return baseUrnPrefix;
  }
}
