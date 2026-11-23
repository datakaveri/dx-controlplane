package org.cdpg.dx.aaa.email.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.mail.MailMessage;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.cdpg.dx.aaa.organization.models.Organization;
import org.cdpg.dx.aaa.organization.models.OrganizationHierarchyDefinitionRequest;
import org.cdpg.dx.aaa.organization.models.OrganizationHierarchyNode;
import org.cdpg.dx.aaa.organization.models.OrganizationJoinRequest;
import org.cdpg.dx.aaa.organization.models.OrganizationUser;
import org.cdpg.dx.aaa.organization.models.ProposedDepartmentNode;
import org.cdpg.dx.aaa.organization.models.Role;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.email.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Request notifications reach every approver and describe the department request. */
class EmailComposerApproverTest {

  private final UUID orgId = UUID.randomUUID();
  private final UUID platform = UUID.randomUUID();
  private final UUID payments = UUID.randomUUID();
  private final UUID orgAdmin = UUID.randomUUID();
  private final UUID platformAdmin = UUID.randomUUID();

  private EmailService emailService;
  private OrganizationService organizationService;
  private UserService userService;
  private EmailComposer composer;
  private User user;

  @BeforeEach
  void setUp() {
    emailService = mock(EmailService.class);
    when(emailService.sendEmail(any())).thenReturn(Future.succeededFuture());
    organizationService = mock(OrganizationService.class);
    userService = mock(UserService.class);

    JsonObject config = new JsonObject()
        .put("emailSender", "noreply@example.com")
        .put("cosAdminEmailId", "cos@example.com")
        .put("TGDxUrl", "https://portal.example.com")
        .put("senderName", "Team")
        .put("platformName", "Platform");
    composer = new EmailComposer(emailService, null, config, organizationService, userService, null);

    user = mock(User.class);
    when(user.principal())
        .thenReturn(new JsonObject().put("email", "alice@example.com").put("name", "Alice"));

    when(organizationService.getHierarchyNodesByOrganizationIds(List.of(orgId)))
        .thenReturn(Future.succeededFuture(Map.of(orgId, List.of(
            new OrganizationHierarchyNode(platform, orgId, null, "Platform", 1, "platform", null, null),
            new OrganizationHierarchyNode(payments, orgId, platform, "Payments", 2, "platform.payments", null, null)))));
    givenUserEmail(orgAdmin, "owner@example.com");
    givenUserEmail(platformAdmin, "platform-head@example.com");
  }

  private void givenUserEmail(UUID userId, String email) {
    DxUser dxUser = mock(DxUser.class);
    when(dxUser.email()).thenReturn(email);
    when(userService.getUserInfoByID(userId)).thenReturn(Future.succeededFuture(dxUser));
  }

  private List<MailMessage> sent(int count) {
    ArgumentCaptor<MailMessage> captor = ArgumentCaptor.forClass(MailMessage.class);
    verify(emailService, times(count)).sendEmail(captor.capture());
    return captor.getAllValues();
  }

  @Test
  void adminJoinRequestGoesToEveryApproverWithDetails() {
    OrganizationJoinRequest request = new OrganizationJoinRequest(
        UUID.randomUUID(), orgId, UUID.randomUUID(), "Alice", "pending", "Lead", "E1",
        "alice@example.com", payments, null, null, Role.DEPT_ADMIN, "I lead <b>payments</b>");
    when(organizationService.getRequestApproverUserIds(orgId, payments, true))
        .thenReturn(Future.succeededFuture(List.of(orgAdmin, platformAdmin)));

    composer.sendEmailForJoiningOrg(request, user);

    List<MailMessage> mails = sent(2);
    assertThat(mails).extracting(m -> m.getTo().getFirst())
        .containsExactlyInAnyOrder("owner@example.com", "platform-head@example.com");
    String html = mails.getFirst().getHtml();
    assertThat(html)
        .contains("Platform / Payments")
        .contains("Requested role:</strong> Admin")
        .contains("I lead &lt;b&gt;payments&lt;/b&gt;")
        .doesNotContain("<b>payments</b>")
        .doesNotContain("${");
  }

  @Test
  void fallsBackToTheOrgAdminWhenApproversCannotBeResolved() {
    OrganizationJoinRequest request = new OrganizationJoinRequest(
        UUID.randomUUID(), orgId, UUID.randomUUID(), "Alice", "pending", "Dev", "E1",
        "alice@example.com", payments, null, null, Role.USER, null);
    when(organizationService.getRequestApproverUserIds(orgId, payments, false))
        .thenReturn(Future.failedFuture("db down"));
    OrganizationUser admin = mock(OrganizationUser.class);
    when(admin.userId()).thenReturn(orgAdmin);
    when(organizationService.getOrganisationAdminId(orgId))
        .thenReturn(Future.succeededFuture(List.of(admin)));

    composer.sendEmailForJoiningOrg(request, user);

    assertThat(sent(1).getFirst().getTo()).containsExactly("owner@example.com");
  }

  @Test
  void departmentRequestTellsCosAdminWhatIsProposed() {
    Organization org = mock(Organization.class);
    when(org.orgName()).thenReturn("ABC & Co");
    when(organizationService.getOrganizationById(orgId)).thenReturn(Future.succeededFuture(org));
    OrganizationHierarchyDefinitionRequest request = new OrganizationHierarchyDefinitionRequest(
        UUID.randomUUID(), orgId, platform,
        List.of(new ProposedDepartmentNode("Data", List.of(new ProposedDepartmentNode("BI", null)))),
        "pending", null, null, null);

    composer.sendEmailForDepartmentRequest(request, user);

    MailMessage mail = sent(1).getFirst();
    assertThat(mail.getTo()).containsExactly("cos@example.com");
    assertThat(mail.getHtml())
        .contains("ABC &amp; Co")
        .contains("Add under:</strong> Platform")
        .contains("<ul><li>Data<ul><li>BI</li></ul></li></ul>")
        .doesNotContain("${");
  }

  @Test
  void escapesHtml() {
    assertThat(EmailComposer.escapeHtml("<a href=\"x\">'&'</a>"))
        .isEqualTo("&lt;a href=&quot;x&quot;&gt;&#39;&amp;&#39;&lt;/a&gt;");
  }
}
