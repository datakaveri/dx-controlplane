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
  void defaultsToMemberAndDropsPurpose() {
    OrganizationJoinRequest request =
        OrganizationJoinRequest.fromJson(body().put(Constants.PURPOSE, "not needed"));

    assertThat(request.requestedRole()).isEqualTo(Role.USER);
    assertThat(request.requestsDeptAdmin()).isFalse();
    assertThat(request.purpose()).isNull();
    assertThat(request.toJson().getString(Constants.REQUESTED_ROLE)).isEqualTo("member");
    assertThat(request.toJson().containsKey(Constants.PURPOSE)).isFalse();
  }

  @Test
  void deptAdminKeepsTrimmedPurpose() {
    OrganizationJoinRequest request =
        OrganizationJoinRequest.fromJson(
            body()
                .put(Constants.REQUESTED_ROLE, "dept_admin")
                .put(Constants.PURPOSE, "  I lead this team  "));

    assertThat(request.requestsDeptAdmin()).isTrue();
    assertThat(request.purpose()).isEqualTo("I lead this team");
    assertThat(request.toJson().getString(Constants.REQUESTED_ROLE)).isEqualTo("dept_admin");
    assertThat(request.toNonEmptyFieldsMap()).containsEntry(Constants.PURPOSE, "I lead this team");
  }

  @Test
  void deptAdminRequiresPurpose() {
    assertThatThrownBy(
            () ->
                OrganizationJoinRequest.fromJson(
                    body().put(Constants.REQUESTED_ROLE, "dept_admin").put(Constants.PURPOSE, " ")))
        .isInstanceOf(DxValidationException.class)
        .hasMessageContaining("purpose is required");
  }

  @Test
  void rejectsOverlongPurpose() {
    assertThatThrownBy(
            () ->
                OrganizationJoinRequest.fromJson(
                    body()
                        .put(Constants.REQUESTED_ROLE, "dept_admin")
                        .put(Constants.PURPOSE, "x".repeat(OrganizationJoinRequest.MAX_PURPOSE_LENGTH + 1))))
        .isInstanceOf(DxValidationException.class);
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
