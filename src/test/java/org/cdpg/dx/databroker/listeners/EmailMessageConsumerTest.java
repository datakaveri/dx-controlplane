package org.cdpg.dx.databroker.listeners;

import static org.cdpg.dx.databroker.listeners.EmailMessageConsumer.MAX_RETRIES;
import static org.cdpg.dx.databroker.listeners.EmailMessageConsumer.RETRY_DELAY_MS;
import static org.cdpg.dx.email.util.Constants.EMAIL_PERMANENT_FAILURE_CODE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbitmq.client.Envelope;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.rabbitmq.QueueOptions;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQConsumer;
import io.vertx.rabbitmq.RabbitMQMessage;
import io.vertx.serviceproxy.ServiceException;
import java.util.ArrayDeque;
import java.util.Deque;
import org.cdpg.dx.email.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EmailMessageConsumerTest {
  private static final Future<Void> TRANSIENT =
      Future.failedFuture(new ServiceException(-1, "451 try again later"));
  private static final Future<Void> PERMANENT =
      Future.failedFuture(new ServiceException(EMAIL_PERMANENT_FAILURE_CODE, "535 auth failed"));

  private final Deque<Handler<Long>> pendingTimers = new ArrayDeque<>();
  private RabbitMQClient rabbit;
  private EmailService emailService;
  private Handler<RabbitMQMessage> handler;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    Vertx vertx = mock(Vertx.class);
    when(vertx.setTimer(anyLong(), any()))
        .thenAnswer(
            call -> {
              assertEquals(RETRY_DELAY_MS, call.<Long>getArgument(0));
              pendingTimers.add(call.getArgument(1));
              return 1L;
            });

    rabbit = mock(RabbitMQClient.class);
    emailService = mock(EmailService.class);
    RabbitMQConsumer mqConsumer = mock(RabbitMQConsumer.class);
    when(rabbit.start()).thenReturn(Future.succeededFuture());
    when(rabbit.basicQos(anyInt())).thenReturn(Future.succeededFuture());
    doAnswer(
            call -> {
              Handler<AsyncResult<RabbitMQConsumer>> ready = call.getArgument(2);
              ready.handle(Future.succeededFuture(mqConsumer));
              return null;
            })
        .when(rabbit)
        .basicConsumer(anyString(), any(QueueOptions.class), any(Handler.class));

    new EmailMessageConsumer(vertx, rabbit, "email", emailService).start();

    ArgumentCaptor<Handler<RabbitMQMessage>> captor = ArgumentCaptor.forClass(Handler.class);
    verify(mqConsumer).handler(captor.capture());
    handler = captor.getValue();
  }

  private static RabbitMQMessage message(long deliveryTag, String body) {
    RabbitMQMessage message = mock(RabbitMQMessage.class);
    when(message.body()).thenReturn(Buffer.buffer(body));
    when(message.envelope()).thenReturn(new Envelope(deliveryTag, false, "", ""));
    return message;
  }

  private static RabbitMQMessage message(long deliveryTag) {
    return message(deliveryTag, new JsonObject().put("tag", deliveryTag).encode());
  }

  private void fireNextTimer() {
    pendingTimers.remove().handle(1L);
  }

  @Test
  void acksOnSuccess() {
    when(emailService.sendEmailService(any())).thenReturn(Future.succeededFuture());

    handler.handle(message(1));

    verify(rabbit).basicAck(1, false);
    verify(rabbit, never()).basicNack(anyLong(), eq(false), any(Boolean.class));
  }

  @Test
  void retriesOnTimerWithoutRequeueingThenDeadLetters() {
    when(emailService.sendEmailService(any())).thenReturn(TRANSIENT);

    handler.handle(message(1));

    for (int retry = 1; retry <= MAX_RETRIES; retry++) {
      verify(emailService, times(retry)).sendEmailService(any());
      verify(rabbit, never()).basicNack(anyLong(), eq(false), any(Boolean.class));
      assertEquals(1, pendingTimers.size());
      fireNextTimer();
    }

    verify(emailService, times(MAX_RETRIES + 1)).sendEmailService(any());
    verify(rabbit).basicNack(1, false, false);
    assertEquals(0, pendingTimers.size());
  }

  @Test
  void succeedsOnRetryAndAcks() {
    when(emailService.sendEmailService(any()))
        .thenReturn(TRANSIENT)
        .thenReturn(Future.succeededFuture());

    handler.handle(message(1));
    fireNextTimer();

    verify(rabbit).basicAck(1, false);
    verify(rabbit, never()).basicNack(anyLong(), eq(false), any(Boolean.class));
  }

  @Test
  void countsRetriesPerMessage() {
    when(emailService.sendEmailService(any())).thenReturn(TRANSIENT);

    handler.handle(message(1));
    for (int retry = 0; retry < MAX_RETRIES - 1; retry++) {
      fireNextTimer();
    }

    // Message 1 has one retry left. A new failure must start from zero and not use it up.
    handler.handle(message(2));
    fireNextTimer();
    fireNextTimer();

    verify(rabbit).basicNack(1, false, false);
    verify(rabbit, never()).basicNack(2, false, false);
  }

  @Test
  void deadLettersPermanentFailureWithoutRetrying() {
    when(emailService.sendEmailService(any())).thenReturn(PERMANENT);

    handler.handle(message(1));

    verify(emailService, times(1)).sendEmailService(any());
    verify(rabbit).basicNack(1, false, false);
    assertEquals(0, pendingTimers.size());
  }

  @Test
  void deadLettersMalformedJson() {
    handler.handle(message(1, "not json"));

    verify(emailService, never()).sendEmailService(any());
    verify(rabbit).basicNack(1, false, false);
  }

  @Test
  void limitsPrefetchBeforeConsuming() {
    verify(rabbit).basicQos(10);
  }
}
