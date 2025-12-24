package org.cdpg.dx.aaa.email.factory;

import static org.cdpg.dx.aaa.common.Constants.DOC_INDEX;
import static org.cdpg.dx.aaa.common.Constants.DOC_USER_INDEX;
import static org.cdpg.dx.database.elastic.util.Constants.APD_URL;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import org.cdpg.dx.aaa.credit.dao.CreditDAOFactory;
import org.cdpg.dx.aaa.credit.service.CreditServiceImpl;
import org.cdpg.dx.aaa.email.util.EmailComposer;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.service.ItemServiceImpl;
import org.cdpg.dx.aaa.organization.dao.OrganizationDAOFactory;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.aaa.organization.service.OrganizationServiceImpl;
import org.cdpg.dx.aaa.user.service.UserServiceImpl;
import org.cdpg.dx.acl.policy.dao.PolicyDao;
import org.cdpg.dx.acl.policy.dao.impl.PolicyDaoImpl;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.email.service.EmailService;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

public class EmailComposerFactory {

  private EmailComposerFactory() {}

  public static EmailComposer create(
      EmailService emailService,
      KeycloakUserService keycloakUserService,
      PostgresService pgService,
      ElasticsearchService esService,
      WebClient webClient,
      JsonObject config) {
    String docIndex = config.getString(DOC_INDEX);
    final String apdURL = config.getString(APD_URL);
    OrganizationDAOFactory organizationDAOFactory = new OrganizationDAOFactory(pgService);

    PolicyDao policyDao = new PolicyDaoImpl(pgService);

    ItemService itemService =
        new ItemServiceImpl(esService, keycloakUserService, policyDao, webClient, docIndex, apdURL);

    OrganizationService organizationService =
        new OrganizationServiceImpl(organizationDAOFactory, keycloakUserService, itemService);

    CreditDAOFactory creditDAOFactory = new CreditDAOFactory(pgService);
    CreditServiceImpl creditService =
        new CreditServiceImpl(creditDAOFactory, keycloakUserService, config);

    final String docUserIndex = config.getString(DOC_USER_INDEX);

    UserServiceImpl userService =
        new UserServiceImpl(
            keycloakUserService, organizationService, creditService, esService, docUserIndex);

    return new EmailComposer(
        emailService, keycloakUserService, config, organizationService, userService, creditService);
  }
}
