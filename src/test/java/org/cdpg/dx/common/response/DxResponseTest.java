package org.cdpg.dx.common.response;

import static org.assertj.core.api.Assertions.*;

import io.vertx.core.json.JsonObject;
import java.util.List;
import org.cdpg.dx.common.util.PaginationInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DxResponseTest {

  @Nested
  @DisplayName("DxResponse")
  class DxResponseTests {

    @Test
    void defaultConstructor() {
      DxResponse<String> resp = new DxResponse<>();
      assertThat(resp.getType()).isNull();
      assertThat(resp.getTitle()).isNull();
      assertThat(resp.getDetail()).isNull();
      assertThat(resp.getResult()).isNull();
      assertThat(resp.getPaginationInfo()).isNull();
    }

    @Test
    void fullConstructor() {
      DxResponse<String> resp = new DxResponse<>(
          "urn:dx:acl:success", "Success", "Operation completed", "result-data", null);

      assertThat(resp.getType()).isEqualTo("urn:dx:acl:success");
      assertThat(resp.getTitle()).isEqualTo("Success");
      assertThat(resp.getDetail()).isEqualTo("Operation completed");
      assertThat(resp.getResult()).isEqualTo("result-data");
    }

    @Test
    void settersWork() {
      DxResponse<List<String>> resp = new DxResponse<>();
      resp.setType("urn:dx:acl:error");
      resp.setTitle("Error");
      resp.setDetail("Something went wrong");
      resp.setResult(List.of("a", "b"));

      assertThat(resp.getType()).isEqualTo("urn:dx:acl:error");
      assertThat(resp.getTitle()).isEqualTo("Error");
      assertThat(resp.getDetail()).isEqualTo("Something went wrong");
      assertThat(resp.getResult()).containsExactly("a", "b");
    }

    @Test
    void acceptsGenericResult() {
      JsonObject data = new JsonObject().put("key", "value");
      DxResponse<JsonObject> resp = new DxResponse<>(
          "urn:dx:type", "Title", "Detail", data, null);

      assertThat(resp.getResult().getString("key")).isEqualTo("value");
    }

    @Test
    void paginationInfoSetterWorks() {
      DxResponse<String> resp = new DxResponse<>();
      PaginationInfo info = PaginationInfo.from(1, 10, 100);
      resp.setPaginationInfo(info);

      assertThat(resp.getPaginationInfo()).isNotNull();
    }
  }

  @Nested
  @DisplayName("DxErrorResponse")
  class DxErrorResponseTests {

    @Test
    void constructsWithFields() {
      DxErrorResponse resp = new DxErrorResponse("urn:dx:error", "Bad Request", "Invalid input");

      JsonObject json = resp.toJson();

      assertThat(json.getString("type")).isEqualTo("urn:dx:error");
      assertThat(json.getString("title")).isEqualTo("Bad Request");
      assertThat(json.getString("detail")).isEqualTo("Invalid input");
    }

    @Test
    void toJsonContainsAllFields() {
      DxErrorResponse resp = new DxErrorResponse("t", "ti", "d");
      JsonObject json = resp.toJson();

      assertThat(json.fieldNames()).containsExactlyInAnyOrder("type", "title", "detail");
    }

    @Test
    void toJsonWithNullValues() {
      DxErrorResponse resp = new DxErrorResponse(null, null, null);
      JsonObject json = resp.toJson();

      assertThat(json.getString("type")).isNull();
      assertThat(json.getString("title")).isNull();
      assertThat(json.getString("detail")).isNull();
    }
  }
}
