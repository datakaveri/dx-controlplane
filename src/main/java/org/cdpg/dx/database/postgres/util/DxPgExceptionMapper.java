package org.cdpg.dx.database.postgres.util;

import io.vertx.pgclient.PgException;
import org.cdpg.dx.common.exception.*;

public class DxPgExceptionMapper {
  public static DxPgException from(Throwable t) {
    if (t instanceof PgException pgEx) {
      return switch (pgEx.getCode()) {
        // Class 42 — Syntax Error or Access Rule Violation
        case "42703" -> new InvalidColumnNameException(pgEx.getMessage());
        case "42P01" -> new DxPgException("Undefined table", pgEx);
        case "42P02" -> new DxPgException("Undefined parameter", pgEx);
        case "42601" -> new DxPgException("Syntax error", pgEx);

        // Class 23 — Integrity Constraint Violation
        case "23505" -> new UniqueConstraintViolationException(pgEx.getMessage());
        case "23503" -> new DxPgException("Foreign key violation", pgEx);
        case "23502" -> new DxPgException("Not null violation", pgEx);
        case "23514" -> new DxPgException("Check constraint violation", pgEx);

        // Class 53 — Insufficient Resources
        case "53100" -> new DxPgException("Disk full", pgEx);
        case "53200" -> new DxPgException("Out of memory", pgEx);
        case "53300" -> new DxPgException("Too many connections", pgEx);

        // Class 08 — Connection Exception
        case "08000" -> new DxPgException("Connection exception", pgEx);
        case "08006" -> new DxPgException("Connection failure", pgEx);

        default -> new DxPgException("Postgres Error: " + pgEx.getMessage(), pgEx);
      };
    }
    return new DxPgException("Unknown DB Error", t);
  }
}
