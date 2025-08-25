package org.cdpg.dx.common;

public enum HttpStatusCode {

  // 1xx: Informational
  CONTINUE(100, "Continue", "continue"),
  SWITCHING_PROTOCOLS(101, "Switching Protocols", "switchingProtocols"),
  PROCESSING(102, "Processing", "processing"),
  EARLY_HINTS(103, "Early Hints", "earlyHints"),

  // 2XX: codes
  NO_CONTENT(204, "No Content", "noContent"),
  SUCCESS(200, "Success", "success"),
  CREATED(201, "Created", "success"),

  // 4xx: Client Error
  BAD_REQUEST(400, "Bad Request", "badRequest"),

  INVALID_PARAM(400, "Bad Request", "badRequest"),
  UNAUTHORIZED(401, "Not Authorized", "notAuthorized"),
  PAYMENT_REQUIRED(402, "Payment Required", "paymentRequired"),
  FORBIDDEN(403, "Forbidden", "forbidden"),
  FORBIDDEN_NO_ACCESS(403, "Forbidden", "no-access"),
  FORBIDDEN_ACCESS_PENDING(403, "Forbidden", "access-pending"),
  FORBIDDEN_ACCESS_REJECTED(403, "Forbidden", "access-rejected"),
  VERIFY_FORBIDDEN(403, "Policy does not exist", "apdDenied"),
  NOT_FOUND(404, "Not Found", "notFound"),
  METHOD_NOT_ALLOWED(405, "Method Not Allowed", "methodNotAllowed"),
  NOT_ACCEPTABLE(406, "Not Acceptable", "notAcceptable"),
  PROXY_AUTHENTICATION_REQUIRED(
    407, "Proxy Authentication Required", "proxyAuthenticationRequired"),
  REQUEST_TIMEOUT(408, "Request Timeout", "requestTimeout"),
  CONFLICT(409, "Conflict", "conflict"),
  GONE(410, "Gone", "gone"),
  LENGTH_REQUIRED(411, "Length Required", "lengthRequired"),
  PRECONDITION_FAILED(412, "Precondition Failed", "preconditionFailed"),
  REQUEST_TOO_LONG(413, "Payload Too Large", "payloadTooLarge"),
  REQUEST_URI_TOO_LONG(414, "URI Too Long", "uriTooLong"),
  UNSUPPORTED_MEDIA_TYPE(
    415, "Unsupported Media Type", "unsupportedMediaType"),
  REQUESTED_RANGE_NOT_SATISFIABLE(
    416, "Range Not Satisfiable", "rangeNotSatisfiable"),
  EXPECTATION_FAILED(417, "Expectation Failed", "expectation Failed"),
  MISDIRECTED_REQUEST(421, "Misdirected Request", "misdirected Request"),
  UNPROCESSABLE_ENTITY(422, "Unprocessable Entity", "unprocessableEntity"),
  LOCKED(423, "Locked", "locked"),
  FAILED_DEPENDENCY(424, "Failed Dependency", "failedDependency"),
  TOO_EARLY(425, "Too Early", "tooEarly"),
  UPGRADE_REQUIRED(426, "Upgrade Required", "upgradeRequired"),
  PRECONDITION_REQUIRED(428, "Precondition Required", "preconditionRequired"),
  TOO_MANY_REQUESTS(429, "Too Many Requests", "tooManyRequests"),
  REQUEST_HEADER_FIELDS_TOO_LARGE(
    431, "Request Header Fields Too Large", "requestHeaderFieldsTooLarge"),
  UNAVAILABLE_FOR_LEGAL_REASONS(
    451, "Unavailable For Legal Reasons", "unavailableForLegalReasons"),

  // 5xx: Server Error
  INTERNAL_SERVER_ERROR(500, "Internal Server Error", "internalServerError"),
  NOT_IMPLEMENTED(501, "Not Implemented", "notImplemented"),
  BAD_GATEWAY(502, "Bad Gateway", "badGateway"),
  SERVICE_UNAVAILABLE(503, "Service Unavailable", "serviceUnavailable"),
  GATEWAY_TIMEOUT(504, "Gateway Timeout", "gatewayTimeout"),
  HTTP_VERSION_NOT_SUPPORTED(
    505, "HTTP Version Not Supported", "httpVersionNotSupported"),
  VARIANT_ALSO_NEGOTIATES(
    506, "Variant Also Negotiates", "variantAlsoNegotiates"),
  INSUFFICIENT_STORAGE(507, "Insufficient Storage", "insufficientStorage"),
  LOOP_DETECTED(508, "Loop Detected", "loopDetected"),
  NOT_EXTENDED(510, "Not Extended", "notExtended"),
  NETWORK_AUTHENTICATION_REQUIRED(
    511,
    "Network Authentication Required",
    "networkAuthenticationRequired");

  private  int value;
  private  String description;
  private  String path;

  HttpStatusCode(int value, String description, String path) {
    this.value = value;
    this.description = description;
    this.path = path;
  }

  public static HttpStatusCode getByValue(int value) {
    for (HttpStatusCode status : values()) {
      if (status.value == value) {
        return status;
      }
    }
    throw new IllegalArgumentException("Invalid status code: " + value);
  }

  public int getValue() {
    return value;
  }

  public String getDescription() {
    return description;
  }

  public String getPath() {
    return path;
  }

  @Override
  public String toString() {
    return value + " " + description;
  }
}
