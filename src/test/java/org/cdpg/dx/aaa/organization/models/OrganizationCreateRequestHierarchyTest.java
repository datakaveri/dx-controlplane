package org.cdpg.dx.aaa.organization.models;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.cdpg.dx.aaa.organization.config.Constants;
import org.cdpg.dx.testutil.TestDataFactory;
import org.junit.jupiter.api.Test;

class OrganizationCreateRequestHierarchyTest {

  private static OrganizationCreateRequest withDepartments(List<ProposedDepartmentNode> depts) {
    OrganizationCreateRequest r = TestDataFactory.anOrgCreateRequest(UUID.randomUUID(), UUID.randomUUID());
    return new OrganizationCreateRequest(r.id(), r.requestedBy(), r.name(), r.logoPath(),
        r.entityType(), r.orgSector(), r.websiteLink(), r.address(), r.certificatePath(),
        r.pancardPath(), r.relevantDocPath(), r.status(), r.userName(), r.empId(), r.jobTitle(),
        r.orgManagerphoneNo(), r.managerEmail(), r.orgDocuments(), null, null, depts);
  }

  @Test
  void flatRequestOmitsProposedHierarchy() {
    OrganizationCreateRequest request = TestDataFactory.anOrgCreateRequest();

    assertThat(request.hasProposedHierarchy()).isFalse();
    assertThat(request.toNonEmptyFieldsMap()).doesNotContainKey(Constants.PROPOSED_HIERARCHY);
    assertThat(request.toJson().containsKey(Constants.PROPOSED_HIERARCHY)).isFalse();
    assertThat(OrganizationCreateRequest.fromJson(request.toJson()).departments()).isEmpty();
  }

  @Test
  void proposedHierarchyRoundTripsThroughJsonbColumn() {
    List<ProposedDepartmentNode> departments = List.of(
        new ProposedDepartmentNode("Engineering",
            List.of(new ProposedDepartmentNode("Data Platform", null))),
        new ProposedDepartmentNode("Finance", null));
    OrganizationCreateRequest request = withDepartments(departments);

    Map<String, Object> row = request.toNonEmptyFieldsMap();
    assertThat(row.get(Constants.PROPOSED_HIERARCHY)).isInstanceOf(JsonObject.class);

    // JSONB may come back from the driver as an object or as a string.
    JsonObject asObject = request.toJson();
    JsonObject asString = request.toJson()
        .put(Constants.PROPOSED_HIERARCHY, asObject.getJsonObject(Constants.PROPOSED_HIERARCHY).encode());

    assertThat(OrganizationCreateRequest.fromJson(asObject).departments()).isEqualTo(departments);
    assertThat(OrganizationCreateRequest.fromJson(asString).departments()).isEqualTo(departments);
    assertThat(request.toJsonForUsers().containsKey("proposedHierarchy")).isTrue();
  }
}
