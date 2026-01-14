package org.cdpg.dx.aaa.vote.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum VoteType {
  LIKE,
  DISLIKE,
  NEUTRAL;

  @JsonCreator
  public static VoteType from(String value) {
    return VoteType.valueOf(value.toUpperCase());
  }
}
