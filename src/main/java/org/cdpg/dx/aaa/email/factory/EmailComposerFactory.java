package org.cdpg.dx.aaa.email.factory;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.credit.service.CreditService;
import org.cdpg.dx.aaa.email.util.EmailComposer;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.email.service.EmailService;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

/**
 * Factory for creating {@link EmailComposer} instances.
 *
 * <p>Accepts pre-built service instances to avoid creating duplicate service objects. Previously
 * this factory constructed its own OrganizationServiceImpl, ItemServiceImpl, CreditServiceImpl,
 * and UserServiceImpl — completely separate from the instances in ControllerFactory. Now it shares
 * the same service instances used by the rest of the application.
 */
public class EmailComposerFactory {

  private EmailComposerFactory() {}

  /**
   * Create an {@link EmailComposer} using shared service instances.
   *
   * @param emailService the email sending service
   * @param keycloakUserService the Keycloak user service
   * @param config application configuration
   * @param organizationService the shared organization service
   * @param userService the shared user service
   * @param creditService the shared credit service
   * @return a configured EmailComposer
   */
  public static EmailComposer create(
      EmailService emailService,
      KeycloakUserService keycloakUserService,
      JsonObject config,
      OrganizationService organizationService,
      UserService userService,
      CreditService creditService) {

    return new EmailComposer(
        emailService, keycloakUserService, config, organizationService, userService, creditService);
  }
}
