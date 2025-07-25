package org.cdpg.dx.auth.authentication.exception;

import org.cdpg.dx.common.exception.BaseDxException;

import static org.cdpg.dx.common.exception.DxErrorCodes.UNAUTHORIZED;

public class AuthenticationException extends BaseDxException {
  public AuthenticationException(String message) {
    super(UNAUTHORIZED, message);
  }
}
