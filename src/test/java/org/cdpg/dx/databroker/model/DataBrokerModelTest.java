package org.cdpg.dx.databroker.model;

import static org.assertj.core.api.Assertions.*;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DataBrokerModelTest {

  @Nested
  @DisplayName("RegisterExchangeModel")
  class RegisterExchangeModelTests {

    @Test
    void constructsFromParams() {
      RegisterExchangeModel model =
          new RegisterExchangeModel("user1", "key123", "test-exchange", "localhost", 5672, "iudx-prod");

      assertThat(model.getUserId()).isEqualTo("user1");
      assertThat(model.getApiKey()).isEqualTo("key123");
      assertThat(model.getExchangeName()).isEqualTo("test-exchange");
      assertThat(model.getUrl()).isEqualTo("localhost");
      assertThat(model.getPort()).isEqualTo(5672);
      assertThat(model.getvHost()).isEqualTo("iudx-prod");
    }

    @Test
    void toJsonSerializesCorrectly() {
      RegisterExchangeModel model =
          new RegisterExchangeModel("user1", "key123", "test-exchange", "localhost", 5672, "iudx-prod");

      JsonObject json = model.toJson();

      assertThat(json.getString("username")).isEqualTo("user1");
      assertThat(json.getString("apiKey")).isEqualTo("key123");
      assertThat(json.getString("id")).isEqualTo("test-exchange");
      assertThat(json.getString("URL")).isEqualTo("localhost");
      assertThat(json.getInteger("port")).isEqualTo(5672);
      assertThat(json.getString("vHost")).isEqualTo("iudx-prod");
    }

    @Test
    void constructsFromJson() {
      JsonObject json = new JsonObject()
          .put("username", "user1")
          .put("apiKey", "key123")
          .put("id", "test-exchange")
          .put("URL", "localhost")
          .put("port", 5672)
          .put("vHost", "iudx-prod");

      RegisterExchangeModel model = new RegisterExchangeModel(json);

      assertThat(model.getUserId()).isEqualTo("user1");
      assertThat(model.getApiKey()).isEqualTo("key123");
      assertThat(model.getExchangeName()).isEqualTo("test-exchange");
      assertThat(model.getUrl()).isEqualTo("localhost");
      assertThat(model.getPort()).isEqualTo(5672);
      assertThat(model.getvHost()).isEqualTo("iudx-prod");
    }

    @Test
    void roundTrip() {
      RegisterExchangeModel original =
          new RegisterExchangeModel("user1", "key123", "test-ex", "host", 5672, "vhost");
      RegisterExchangeModel roundTripped = new RegisterExchangeModel(original.toJson());

      assertThat(roundTripped.getUserId()).isEqualTo(original.getUserId());
      assertThat(roundTripped.getApiKey()).isEqualTo(original.getApiKey());
      assertThat(roundTripped.getExchangeName()).isEqualTo(original.getExchangeName());
      assertThat(roundTripped.getUrl()).isEqualTo(original.getUrl());
      assertThat(roundTripped.getPort()).isEqualTo(original.getPort());
      assertThat(roundTripped.getvHost()).isEqualTo(original.getvHost());
    }

    @Test
    void setters() {
      RegisterExchangeModel model = new RegisterExchangeModel();
      model.setUserId("u");
      model.setApiKey("k");
      model.setExchangeName("e");
      model.setUrl("h");
      model.setPort(1234);
      model.setvHost("v");

      assertThat(model.getUserId()).isEqualTo("u");
      assertThat(model.getApiKey()).isEqualTo("k");
      assertThat(model.getExchangeName()).isEqualTo("e");
      assertThat(model.getUrl()).isEqualTo("h");
      assertThat(model.getPort()).isEqualTo(1234);
      assertThat(model.getvHost()).isEqualTo("v");
    }

    @Test
    void toStringContainsFields() {
      RegisterExchangeModel model =
          new RegisterExchangeModel("user1", "key123", "test-ex", "host", 5672, "vhost");

      assertThat(model.toString())
          .contains("user1", "test-ex", "host", "5672", "vhost");
    }
  }

  @Nested
  @DisplayName("RegisterQueueModel")
  class RegisterQueueModelTests {

    @Test
    void constructsFromParams() {
      RegisterQueueModel model =
          new RegisterQueueModel("user1", "key123", "test-queue", "localhost", 5672, "iudx-prod");

      assertThat(model.getUserId()).isEqualTo("user1");
      assertThat(model.getApiKey()).isEqualTo("key123");
      assertThat(model.getQueueName()).isEqualTo("test-queue");
      assertThat(model.getUrl()).isEqualTo("localhost");
      assertThat(model.getPort()).isEqualTo(5672);
      assertThat(model.getvHost()).isEqualTo("iudx-prod");
    }

    @Test
    void toJsonSerializesCorrectly() {
      RegisterQueueModel model =
          new RegisterQueueModel("user1", "key123", "test-queue", "localhost", 5672, "iudx-prod");

      JsonObject json = model.toJson();

      assertThat(json.getString("username")).isEqualTo("user1");
      assertThat(json.getString("apiKey")).isEqualTo("key123");
      assertThat(json.getString("id")).isEqualTo("test-queue");
      assertThat(json.getString("URL")).isEqualTo("localhost");
      assertThat(json.getInteger("port")).isEqualTo(5672);
      assertThat(json.getString("vHost")).isEqualTo("iudx-prod");
    }

    @Test
    void constructsFromJson() {
      JsonObject json = new JsonObject()
          .put("username", "user1")
          .put("apiKey", "key123")
          .put("id", "test-queue")
          .put("URL", "localhost")
          .put("port", 5672)
          .put("vHost", "iudx-prod");

      RegisterQueueModel model = new RegisterQueueModel(json);

      assertThat(model.getUserId()).isEqualTo("user1");
      assertThat(model.getQueueName()).isEqualTo("test-queue");
    }

    @Test
    void roundTrip() {
      RegisterQueueModel original =
          new RegisterQueueModel("user1", "key123", "test-q", "host", 5672, "vhost");
      RegisterQueueModel roundTripped = new RegisterQueueModel(original.toJson());

      assertThat(roundTripped.getUserId()).isEqualTo(original.getUserId());
      assertThat(roundTripped.getQueueName()).isEqualTo(original.getQueueName());
      assertThat(roundTripped.getPort()).isEqualTo(original.getPort());
    }

    @Test
    void setters() {
      RegisterQueueModel model = new RegisterQueueModel();
      model.setUserId("u");
      model.setApiKey("k");
      model.setQueueName("q");
      model.setUrl("h");
      model.setPort(1234);
      model.setvHost("v");

      assertThat(model.getUserId()).isEqualTo("u");
      assertThat(model.getQueueName()).isEqualTo("q");
    }

    @Test
    void toStringContainsFields() {
      RegisterQueueModel model =
          new RegisterQueueModel("user1", "key123", "test-q", "host", 5672, "vhost");

      assertThat(model.toString()).contains("user1", "test-q", "host");
    }
  }

  @Nested
  @DisplayName("UserResponseModel")
  class UserResponseModelTests {

    @Test
    void defaultConstructor() {
      UserResponseModel model = new UserResponseModel();
      assertThat(model.getUserId()).isNull();
      assertThat(model.getPassword()).isNull();
    }

    @Test
    void constructsFromJson() {
      JsonObject json = new JsonObject()
          .put("userId", "user123")
          .put("password", "secret");

      UserResponseModel model = new UserResponseModel(json);

      assertThat(model.getUserId()).isEqualTo("user123");
      assertThat(model.getPassword()).isEqualTo("secret");
    }

    @Test
    void toJsonSerializes() {
      UserResponseModel model = new UserResponseModel();
      model.setUserId("user123");
      model.setPassword("secret");

      JsonObject json = model.toJson();

      assertThat(json.getString("userId")).isEqualTo("user123");
      assertThat(json.getString("password")).isEqualTo("secret");
    }

    @Test
    void toJsonOmitsNulls() {
      UserResponseModel model = new UserResponseModel();

      JsonObject json = model.toJson();

      assertThat(json.containsKey("userId")).isFalse();
      assertThat(json.containsKey("password")).isFalse();
    }

    @Test
    void setters() {
      UserResponseModel model = new UserResponseModel();
      model.setUserId("u");
      model.setPassword("p");

      assertThat(model.getUserId()).isEqualTo("u");
      assertThat(model.getPassword()).isEqualTo("p");
    }
  }

  @Nested
  @DisplayName("ExchangeSubscribersResponse")
  class ExchangeSubscribersResponseTests {

    @Test
    void constructsFromMap() {
      Map<String, List<String>> subs = Map.of(
          "queue1", List.of("key1", "key2"),
          "queue2", List.of("key3"));

      ExchangeSubscribersResponse resp = new ExchangeSubscribersResponse(subs);

      assertThat(resp.getSubscribers()).hasSize(2);
      assertThat(resp.getSubscribers().get("queue1")).containsExactly("key1", "key2");
      assertThat(resp.getSubscribers().get("queue2")).containsExactly("key3");
    }

    @Test
    void toJsonSerializes() {
      Map<String, List<String>> subs = Map.of(
          "queue1", List.of("key1", "key2"));

      ExchangeSubscribersResponse resp = new ExchangeSubscribersResponse(subs);
      JsonObject json = resp.toJson();

      assertThat(json.getJsonArray("queue1")).containsExactly("key1", "key2");
    }

    @Test
    void constructsFromJsonWithJsonArray() {
      JsonObject json = new JsonObject()
          .put("queue1", new JsonArray().add("key1").add("key2"))
          .put("queue2", new JsonArray().add("key3"));

      ExchangeSubscribersResponse resp = new ExchangeSubscribersResponse(json);

      assertThat(resp.getSubscribers()).hasSize(2);
      assertThat(resp.getSubscribers().get("queue1")).containsExactly("key1", "key2");
    }

    @Test
    void toJsonHandlesEmptySubscribers() {
      ExchangeSubscribersResponse resp = new ExchangeSubscribersResponse();
      JsonObject json = resp.toJson();

      assertThat(json.isEmpty()).isTrue();
    }

    @Test
    void toJsonHandlesNullSubscribers() {
      ExchangeSubscribersResponse resp = new ExchangeSubscribersResponse();
      resp.setSubscribers(null);
      JsonObject json = resp.toJson();

      assertThat(json.isEmpty()).isTrue();
    }

    @Test
    void roundTrip() {
      Map<String, List<String>> subs = Map.of(
          "q1", List.of("r1", "r2"),
          "q2", List.of("r3"));

      ExchangeSubscribersResponse original = new ExchangeSubscribersResponse(subs);
      ExchangeSubscribersResponse roundTripped = new ExchangeSubscribersResponse(original.toJson());

      assertThat(roundTripped.getSubscribers()).hasSize(2);
      assertThat(roundTripped.getSubscribers().get("q1")).containsExactly("r1", "r2");
    }

    @Test
    void toStringContainsSubscribers() {
      Map<String, List<String>> subs = Map.of("q1", List.of("r1"));
      ExchangeSubscribersResponse resp = new ExchangeSubscribersResponse(subs);

      assertThat(resp.toString()).contains("subscribers");
    }

    @Test
    void setterWorks() {
      ExchangeSubscribersResponse resp = new ExchangeSubscribersResponse();
      resp.setSubscribers(Map.of("q", List.of("k")));

      assertThat(resp.getSubscribers()).containsKey("q");
    }
  }
}
