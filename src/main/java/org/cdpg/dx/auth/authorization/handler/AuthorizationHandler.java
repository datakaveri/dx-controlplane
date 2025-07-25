package org.cdpg.dx.auth.authorization.handler;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import org.cdpg.dx.auth.authorization.model.DxRole;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxUnauthorizedException;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class AuthorizationHandler {

    public static Handler<RoutingContext> forRoles(DxRole... roles) {
        Set<String> allowed = Arrays.stream(roles)
                .map(DxRole::getRole)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        boolean isOnlyCompute = roles.length == 1 && roles[0] == DxRole.COMPUTE;
        
        return ctx -> {
            User user = ctx.user();
            if (user == null) {
                ctx.fail(new DxUnauthorizedException("User not authenticated.")); // HTTP 401
                return;
            }

            JsonObject principal = user.principal();
            JsonObject realmAccess = principal.getJsonObject("realm_access");

            if (realmAccess == null || !realmAccess.containsKey("roles")) {
                ctx.fail(new DxForbiddenException("No roles assigned to the user.")); // HTTP 403
                return;
            }

            JsonArray userRoles = realmAccess.getJsonArray("roles");

            boolean allowedRole = userRoles.stream()
                    .map(Object::toString)
                    .map(String::toLowerCase)
                    .anyMatch(allowed::contains);

            if (allowedRole) {
                ctx.next();
            } else {
                if (isOnlyCompute) {
                    ctx.fail(new DxForbiddenException("Please upgrade your role to access GPU-based compute.")); // HTTP 403
                } else {
                    ctx.fail(new DxForbiddenException("User does not have the required role.")); // HTTP 403
                }
            }
        };
    }

    public static Handler<RoutingContext> requireKycVerified() {
        return ctx -> {
            User user = ctx.user();
            if (user == null) {
                ctx.fail(new DxUnauthorizedException("User not authenticated.")); // HTTP 401
                return;
            }

            JsonObject principal = user.principal();

            if (principal == null || !principal.containsKey("kyc_verified")) {
                ctx.fail(new DxForbiddenException("Missing KYC verification status.")); // HTTP 403
                return;
            }

            boolean isKycVerified = principal.getBoolean("kyc_verified", false);
            if (!isKycVerified) {
                ctx.fail(new DxForbiddenException("User's KYC is not verified.")); // HTTP 403
                return;
            }

            ctx.next();
        };
    }
}
