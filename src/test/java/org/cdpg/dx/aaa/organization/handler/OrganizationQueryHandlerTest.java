package org.cdpg.dx.aaa.organization.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.cdpg.dx.aaa.organization.audit.OrganizationAuditHelper;
import org.cdpg.dx.aaa.organization.models.Organization;
import org.cdpg.dx.aaa.organization.models.OrganizationHierarchyNode;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.CpRoutingContextHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrganizationQueryHandlerTest {

  @Mock private OrganizationService organizationService;
  @Mock private URNGenerator urnGenerator;
  @Mock private RoutingContext ctx;

  private MockedStatic<ResponseBuilder> responseBuilder;
  private MockedStatic<OrganizationAuditHelper> auditHelper;
  private MockedStatic<CpRoutingContextHelper> routingHelper;

  private OrganizationQueryHandler handler;
  private final UUID orgId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    responseBuilder = mockStatic(ResponseBuilder.class);
    auditHelper = mockStatic(OrganizationAuditHelper.class);
    routingHelper = mockStatic(CpRoutingContextHelper.class);
    handler = new OrganizationQueryHandler(organizationService, urnGenerator);
    when(ctx.pathParam("id")).thenReturn(orgId.toString());
  }

  @AfterEach
  void tearDown() {
    responseBuilder.close();
    auditHelper.close();
    routingHelper.close();
  }

  private Organization org() {
    return new Organization(orgId, "Org", null, "Private", "Tech", "https://x", "addr",
        "cert.pdf", "pan.pdf", null, "docs", LocalDateTime.now(), LocalDateTime.now());
  }

  private JsonObject sentBody() {
    ArgumentCaptor<JsonObject> captor = ArgumentCaptor.forClass(JsonObject.class);
    responseBuilder.verify(
        () -> ResponseBuilder.sendSuccess(eq(ctx), captor.capture(), eq(urnGenerator)));
    return captor.getValue();
  }

  @Test
  void getByIdIncludesDepartmentsWithIdNamePathLevelAndParent() {
    OrganizationHierarchyNode eng = new OrganizationHierarchyNode(
        UUID.randomUUID(), orgId, null, "Engineering", 1, "engineering", null, null);
    OrganizationHierarchyNode data = new OrganizationHierarchyNode(
        UUID.randomUUID(), orgId, eng.id(), "Data Platform", 2, "engineering.data_platform",
        null, null);
    when(organizationService.getOrganizationById(orgId)).thenReturn(Future.succeededFuture(org()));
    when(organizationService.getHierarchyNodesByOrganizationIds(List.of(orgId)))
        .thenReturn(Future.succeededFuture(Map.of(orgId, List.of(eng, data))));

    handler.getOrganizationById(ctx);

    JsonObject body = sentBody();
    // existing camelCase shape is preserved
    assertThat(body.getString("id")).isEqualTo(orgId.toString());
    assertThat(body.getString("orgName")).isEqualTo("Org");
    assertThat(body.getString("certificatePath")).isEqualTo("cert.pdf");
    assertThat(body.getString("orgDocuments")).isEqualTo("docs");
    assertThat(body.getJsonArray("departments")).isEqualTo(new JsonArray()
        .add(new JsonObject().put("id", eng.id().toString()).put("name", "Engineering")
            .put("path", "engineering").put("level", 1))
        .add(new JsonObject().put("id", data.id().toString()).put("name", "Data Platform")
            .put("path", "engineering.data_platform").put("level", 2)
            .put("parentId", eng.id().toString())));
  }

  @Test
  void getByIdReturnsEmptyDepartmentsForFlatOrganization() {
    when(organizationService.getOrganizationById(orgId)).thenReturn(Future.succeededFuture(org()));
    when(organizationService.getHierarchyNodesByOrganizationIds(any()))
        .thenReturn(Future.succeededFuture(Map.of()));

    handler.getOrganizationById(ctx);

    assertThat(sentBody().getJsonArray("departments")).isEmpty();
  }
}
