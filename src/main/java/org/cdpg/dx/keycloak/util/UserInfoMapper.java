package org.cdpg.dx.keycloak.util;
import org.cdpg.dx.common.model.UserInfo;
import org.keycloak.representations.idm.UserRepresentation;

import java.util.UUID;

public class UserInfoMapper {

  public static UserInfo fromUserRepresentation(UserRepresentation user) {

    return new UserInfo(
      UUID.fromString(user.getId()),
      buildName(user),
      user.getUsername(),
      user.getFirstName(),
      user.getLastName(),
      user.getEmail()
    );
  }

  private static String buildName(UserRepresentation user) {
    String firstName = user.getFirstName() != null ? user.getFirstName() : "";
    String lastName = user.getLastName() != null ? user.getLastName() : "";
    return (firstName + " " + lastName).trim();
  }
}
