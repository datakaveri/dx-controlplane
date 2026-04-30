package org.cdpg.dx.acl.accessRequest.config;

import io.vertx.core.http.HttpMethod;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Constants {

  // Header params
  public static final String HEADER_AUTHORIZATION = "Authorization";
  public static final String HEADER_X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
  public static final String X_CONTENT_TYPE_OPTIONS_NOSNIFF = "nosniff";

  public static final String HEADER_TOKEN = "token";
  public static final String HEADER_BEARER_AUTHORIZATION = "Authorization";
  public static final String HEADER_TOKEN_BEARER = "Bearer";
  public static final String HEADER_HOST = "Host";
  public static final String HEADER_ACCEPT = "Accept";
  public static final String HEADER_CONTENT_LENGTH = "Content-Length";
  public static final String HEADER_CONTENT_TYPE = "Content-Type";
  public static final String HEADER_ORIGIN = "Origin";
  public static final String HEADER_REFERER = "Referer";
  public static final String HEADER_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
  public static final Set<String> ALLOWED_HEADERS =
      new HashSet<>(
          Arrays.asList(HEADER_AUTHORIZATION,
              HEADER_ACCEPT,
              HEADER_TOKEN,
              HEADER_CONTENT_LENGTH,
              HEADER_CONTENT_TYPE,
              HEADER_HOST,
              HEADER_ORIGIN,
              HEADER_REFERER,
              HEADER_ALLOW_ORIGIN));

  public static final Set<HttpMethod> ALLOWED_METHODS =
      new HashSet<>(
          Arrays.asList(
              HttpMethod.GET,
              HttpMethod.POST,
              HttpMethod.OPTIONS,
              HttpMethod.DELETE,
              HttpMethod.PATCH,
              HttpMethod.PUT));
  // request/response params
  public static final String ID = "id";
  public static final String CONTENT_TYPE = "content-type";
  public static final String AUTHORIZATION_KEY = "Authorization";
  public static final String CAT_SUCCESS_URN = "urn:dx:cat:Success";
  public static final String RESULTS = "results";
  public static final String TOTAL_HITS = "totalHits";
  public static final String APPLICATION_JSON = "application/json";
  public static final String ROUTE_STATIC_SPEC = "/apis/spec";
  public static final String ROUTE_DOC = "/apis";
  public static final String MIME_TEXT_HTML = "text/html";
  public static final String MSG_BAD_QUERY = "Bad query";

  public static final String TITLE = "title";
  public static final String STATUS_CODE = "statusCode";
  public static final String RESULT = "results";
  public static final String DETAIL = "detail";
  public static final String USER_ID = "userId";
  public static final String ROLE = "role";
  public static final String RESOURCE_SERVER_URL = "resourceServer";
  public static final String CLIENT_ID = "clientId";
  public static final String CLIENT_SECRET = "clientSecret";
  public static final String DELEGATE_EMAILS = "delegateEmails";
  public static final String EMAIL_OPTIONS = "emailOptions";
  public static final String API_ENDPOINT = "apiEndpoint";
  public static final String API_METHOD = "method";

  public static final String EPOCH_TIME = "epochTime";
  public static final String ISO_TIME = "isoTime";
  public static final String API = "api";
  public static final String RESPONSE_SIZE = "response_size";
  public static final String HTTP_METHOD = "httpMethod";

  public static final String USER = "user";
  public static final String BODY = "body";

  // endpoints
  public static final String ACCESS_REQUEST_HAS_ACCESS_PATH = "/access_request/has_access";
  public static final String ACCESS_REQUEST_API = "/access_request";
  public static final String VERIFY_API_PATH = "/iudx/acl/apd/v2/verify";


  //operation ids
  public static final String GET_ACCESS_REQUEST_PROVIDER_API = "get-auth-v2-access-requests-provider";
  public static final String GET_ACCESS_REQUEST_CONSUMER_API = "get-auth-v2-access-requests-consumer";
  public static final String CREATE_ACCESS_REQUEST_API = "post-auth-v2-access-requests";
  public static final String UPDATE_ACCESS_REQUEST_API = "put-auth-v2-access-requests";
  public static final String CHECK_ACCESS_REQUEST_API = "post-auth-v2-access-requests-has-access";
  public static final String GET_ACCESS_REQUEST_REPORT_API = "get-auth-v2-access-requests-provider-report";
  public static final String GET_ACCESS_REQUEST_REPORT_FOR_ORG_ADMIN_API = "get-auth-v2-access-requests-org-admin-report";
  public static final String GET_ACCESS_REQUEST_FOR_ORG_ADMIN_API = "get-auth-v2-access-requests-organisation";
  public static final String GET_ACCESS_REQUEST_FOR_COS_ADMIN_API = "get-auth-v2-access-requests-platform";
  public static final String WITHDRAW_ACCESS_REQUEST_API_FOR_CONSUMER = "patch-auth-v2-access-requests";
  public static final String CREATE_POLICY_API = "post-auth-v1-policies";
  public static final String GET_POLICY_API = "get-auth-v1-policies";
  public static final String GET_POLICIES_CONSUMER_API = "get-policies-consumer-api";
  public static final String GET_POLICIES_PROVIDER_API = "get-policies-provider-api";
  public static final String GET_POLICIES_FOR_ORG_ADMIN_API = "get-policies-org_admin-api";
  public static final String GET_POLICIES_FOR_COS_ADMIN_API = "get-policies-cos_admin-api";
  public static final String DELETE_POLICY_API = "deactivate-auth-v1-policies";
  public static final String VERIFY_API = "get-auth-v1-verify";


  public static final String FIRST_NAME = "firstName";
  public static final String LAST_NAME = "lastName";
  public static final String EMAIL_ID = "emailId";
  public static final String RS_SERVER_URL = "resourceServerUrl";
  public static final String USER_ROLE = "userRole";
  public static final String OWNER_EMAIL_ID = "ownerEmailId";
  public static final String OWNER_FIRST_NAME = "ownerFirstName";
  public static final String OWNER_LAST_NAME = "ownerLastName";
  public static final String CONSUMER_EMAIL_ID = "consumerEmailId";
  public static final String CONSUMER_FIRST_NAME = "consumerFirstName";
  public static final String CONSUMER_LAST_NAME = "consumerLastName";


}
