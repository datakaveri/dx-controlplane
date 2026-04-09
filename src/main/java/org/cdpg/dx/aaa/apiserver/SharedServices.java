package org.cdpg.dx.aaa.apiserver;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.credit.factory.CreditControllerFactory;
import org.cdpg.dx.aaa.credit.service.CreditService;
import org.cdpg.dx.aaa.delegation.factory.DelegationControllerFactory;
import org.cdpg.dx.aaa.delegation.service.DelegationService;
import org.cdpg.dx.aaa.email.factory.EmailComposerFactory;
import org.cdpg.dx.aaa.email.util.EmailComposer;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.service.ItemServiceImpl;
import org.cdpg.dx.aaa.organization.factory.OrganizationControllerFactory;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.aaa.user.dao.CustomRoleDAO;
import org.cdpg.dx.aaa.user.dao.impl.CustomRoleDAOImpl;
import org.cdpg.dx.aaa.user.factory.UserControllerFactory;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.acl.policy.dao.PolicyDao;
import org.cdpg.dx.acl.policy.dao.impl.PolicyDaoImpl;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.cdpg.dx.keycloak.service.KeycloakUserServiceImpl;

import static org.cdpg.dx.aaa.common.Constants.DOC_INDEX;
import static org.cdpg.dx.aaa.common.Constants.DOC_USER_INDEX;
import static org.cdpg.dx.database.elastic.util.Constants.APD_URL;

/**
 * Groups the shared domain services and cross-cutting concerns that are created once during
 * application startup and injected into multiple controllers.
 *
 * <p>Built from {@link InfrastructureServices} + config. All services share the same instances,
 * avoiding the duplicate-service-instance problem that previously existed in EmailComposerFactory.
 *
 * @param keycloakUserService Keycloak identity provider
 * @param auditingHandler cross-cutting auditing handler
 * @param emailComposer email composition and sending
 * @param itemService item/catalogue service
 * @param creditService credit management service
 * @param organizationService organization management service
 * @param userService user management service
 * @param delegationService delegation management service
 * @param policyDao policy data access
 */
public record SharedServices(
    KeycloakUserService keycloakUserService,
    AuditingHandler auditingHandler,
    EmailComposer emailComposer,
    ItemService itemService,
    CreditService creditService,
    OrganizationService organizationService,
    UserService userService,
    DelegationService delegationService,
    PolicyDao policyDao) {

  /**
   * Create all shared services from infrastructure services and application config.
   *
   * <p>Services are constructed in dependency order: infrastructure → DAOs → domain services →
   * cross-cutting (email, auditing).
   */
  public static SharedServices create(InfrastructureServices infra, JsonObject config) {
    final String docIndex = config.getString(DOC_INDEX);
    final String docUserIndex = config.getString(DOC_USER_INDEX);
    final String apdURL = config.getString(APD_URL);

    // Identity provider
    KeycloakUserService keycloakUserService = new KeycloakUserServiceImpl(config);

    // Auditing
    String auditingExchange = config.getString("auditingExchange");
    String routingKey = config.getString("auditingRoutingKey");
    boolean isRemoteAudit = config.getBoolean("isRemoteAudit", false);
    AuditingHandler auditingHandler =
        new AuditingHandler(infra.dataBrokerService(), auditingExchange, routingKey, isRemoteAudit);

    // DAOs
    PolicyDao policyDao = new PolicyDaoImpl(infra.pgService());

    // Domain services (order matters due to dependencies)
    ItemService itemService =
        new ItemServiceImpl(
            infra.esService(), keycloakUserService, infra.pgService(), policyDao,
            infra.webClient(), docIndex, apdURL);

    CreditService creditService =
        CreditControllerFactory.createService(infra.pgService(), keycloakUserService, config);

    OrganizationService organizationService =
        OrganizationControllerFactory.createService(
            infra.pgService(), keycloakUserService, itemService);

    DelegationService delegationService =
        DelegationControllerFactory.createService(
            infra.pgService(), keycloakUserService, organizationService, itemService);

    CustomRoleDAO customRoleDAO = new CustomRoleDAOImpl(infra.pgService());
    UserService userService =
        UserControllerFactory.createService(
            keycloakUserService, organizationService, creditService,
            infra.esService(), customRoleDAO, docUserIndex);

    // EmailComposer shares the same service instances
    EmailComposer emailComposer =
        EmailComposerFactory.create(
            infra.emailService(), keycloakUserService, config,
            organizationService, userService, creditService);

    return new SharedServices(
        keycloakUserService, auditingHandler, emailComposer, itemService,
        creditService, organizationService, userService, delegationService, policyDao);
  }
}
