package org.cdpg.dx.aaa.organization.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.vertx.core.json.JsonObject;
import java.util.UUID;
import org.cdpg.dx.aaa.organization.config.Constants;
import org.cdpg.dx.common.exception.DxValidationException;
import org.junit.jupiter.api.Test;

class OrganizationJoinRequestRoleTest {

  private static JsonObject body() {
    return new JsonObject()
        .put(Constants.ORGANIZATION_ID, UUID.randomUUID().toString())
        .put(Constants.USER_ID, UUID.randomUUID().toString())
        .put(Constants.USER_NAME, "Test User")
        .put(Constants.JOB_TITLE, "Lead")
        .put(Constants.EMP_ID, "EMP1")
        .put(Constants.OFFICIAL_EMAIL, "lead@example.com")
        .put(Constants.REQUESTED_NODE_ID, UUID.randomUUID().toString());
  }

  @Test
  void defaultsToMemberWithoutAdditionalInfo() {
    OrganizationJoinRequest request = OrganizationJoinRequest.fromJson(body());

    assertThat(request.requestedRole()).isEqualTo(Role.USER);
    assertThat(request.requestsDeptAdmin()).isFalse();
    assertThat(request.additionalInfo()).isNull();
    assertThat(request.toJson().getString(Constants.REQUESTED_ROLE)).isEqualTo("member");
    assertThat(request.toJson().containsKey(Constants.ADDITIONAL_INFO)).isFalse();
    assertThat(request.toJsonForUsers().containsKey("additionalInfo")).isFalse();
  }

  @Test
  void deptAdminNoLongerNeedsAdditionalInfo() {
    OrganizationJoinRequest request =
        OrganizationJoinRequest.fromJson(body().put(Constants.REQUESTED_ROLE, "dept_admin"));

    assertThat(request.requestsDeptAdmin()).isTrue();
    assertThat(request.additionalInfo()).isNull();
  }

  @Test
  void keepsAdditionalInfoAsIsForAnyRole() {
    JsonObject info =
        new JsonObject().put("purpose", "I lead this team").put("teamSize", 12);

    for (String role : new String[] {"member", "dept_admin"}) {
      OrganizationJoinRequest request =
          OrganizationJoinRequest.fromJson(
              body().put(Constants.REQUESTED_ROLE, role).put(Constants.ADDITIONAL_INFO, info.copy()));

      assertThat(request.additionalInfo()).isEqualTo(info);
      assertThat(request.toJson().getJsonObject(Constants.ADDITIONAL_INFO)).isEqualTo(info);
      assertThat(request.toJsonForUsers().getJsonObject("additionalInfo")).isEqualTo(info);
      assertThat(request.toNonEmptyFieldsMap()).containsEntry(Constants.ADDITIONAL_INFO, info);
    }
  }

  @Test
  void readsAdditionalInfoStoredAsJsonbText() {
    OrganizationJoinRequest request =
        OrganizationJoinRequest.fromJson(
            body().put(Constants.ADDITIONAL_INFO, "{\"purpose\":\"from db\"}"));

    assertThat(request.additionalInfo()).isEqualTo(new JsonObject().put("purpose", "from db"));
  }

  @Test
  void treatsEmptyAdditionalInfoAsAbsent() {
    OrganizationJoinRequest request =
        OrganizationJoinRequest.fromJson(body().put(Constants.ADDITIONAL_INFO, new JsonObject()));

    assertThat(request.additionalInfo()).isNull();
  }

  @Test
  void rejectsNonObjectAdditionalInfo() {
    assertThatThrownBy(
            () ->
                OrganizationJoinRequest.fromJson(
                    body().put(Constants.ADDITIONAL_INFO, new io.vertx.core.json.JsonArray().add(1))))
        .isInstanceOf(DxValidationException.class)
        .hasMessageContaining("additionalInfo must be a JSON object");
  }

  @Test
  void rejectsOversizedAdditionalInfo() {
    JsonObject huge =
        new JsonObject()
            .put("purpose", "x".repeat(OrganizationJoinRequest.MAX_ADDITIONAL_INFO_LENGTH));

    assertThatThrownBy(
            () -> OrganizationJoinRequest.fromJson(body().put(Constants.ADDITIONAL_INFO, huge)))
        .isInstanceOf(DxValidationException.class)
        .hasMessageContaining("additionalInfo must be at most");
  }

  @Test
  void rejectsOrgAdminAndUnknownRoles() {
    assertThatThrownBy(
            () -> OrganizationJoinRequest.fromJson(body().put(Constants.REQUESTED_ROLE, "admin")))
        .isInstanceOf(DxValidationException.class);
    assertThatThrownBy(
            () -> OrganizationJoinRequest.fromJson(body().put(Constants.REQUESTED_ROLE, "owner")))
        .isInstanceOf(DxValidationException.class);
  }
}
