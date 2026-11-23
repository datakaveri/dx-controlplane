package org.cdpg.dx.aaa.email.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.mail.MailMessage;
import org.cdpg.dx.aaa.credit.models.ComputeRole;
import org.cdpg.dx.email.service.EmailService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EmailComposerEnvSuffixTest {

  private String sendComputeRoleEmail(String envSuffix) {
    EmailService emailService = mock(EmailService.class);
    when(emailService.sendEmail(any())).thenReturn(Future.succeededFuture());

    JsonObject config = new JsonObject()
        .put("emailSender", "noreply@example.com")
        .put("cosAdminEmailId", "admin@example.com")
        .put("TGDxUrl", "https://portal.example.com")
        .put("senderName", "Team")
        .put("platformName", "Platform");
    if (envSuffix != null) config.put("envSuffix", envSuffix);

    EmailComposer composer = new EmailComposer(emailService, null, config, null, null, null);

    ComputeRole computeRole = mock(ComputeRole.class);
    when(computeRole.userName()).thenReturn("Alice");
    User user = mock(User.class);
    when(user.principal()).thenReturn(new JsonObject().put("email", "alice@example.com"));

    composer.sendEmailForComputeRole(computeRole, user);

    ArgumentCaptor<MailMessage> captor = ArgumentCaptor.forClass(MailMessage.class);
    verify(emailService).sendEmail(captor.capture());
    return captor.getValue().getHtml();
  }

  @Test
  void titleCarriesConfiguredEnvSuffix() {
    String html = sendComputeRoleEmail("staging");

    assertThat(html).contains("<title>Compute Role Request Notification [staging]</title>");
    assertThat(html).doesNotContain("${ENV_SUFFIX}");
  }

  @Test
  void titleHasNoSuffixWhenNotConfigured() {
    String html = sendComputeRoleEmail(null);

    assertThat(html).contains("<title>Compute Role Request Notification</title>");
    assertThat(html).doesNotContain("${ENV_SUFFIX}");
  }
}
