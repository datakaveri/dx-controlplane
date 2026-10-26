package org.cdpg.dx.aaa.apiserver;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * With isCentralCatEnabled=true the API server loads docs/central-openapi.yaml instead of
 * docs/openapi.yaml, so the central spec must contain every control-plane operation or the
 * server fails to start. Regenerate with {@code make docs} if this fails.
 */
class OpenApiSpecConsistencyTest {

  private static final Pattern OPERATION_ID =
      Pattern.compile("^\\s+operationId:\\s*['\"]?(.+?)['\"]?\\s*$", Pattern.MULTILINE);

  private static Set<String> operationIds(String spec) throws IOException {
    Matcher m = OPERATION_ID.matcher(Files.readString(Path.of(spec)));
    return m.results().map(r -> r.group(1)).collect(Collectors.toSet());
  }

  @Test
  @DisplayName("central-openapi.yaml contains every operation in openapi.yaml")
  void centralSpecContainsAllControlPlaneOperations() throws IOException {
    Set<String> controlPlaneOps = operationIds("docs/openapi.yaml");
    Set<String> centralOps = operationIds("docs/central-openapi.yaml");

    assertThat(controlPlaneOps).isNotEmpty();
    assertThat(centralOps).containsAll(controlPlaneOps);
  }
}
