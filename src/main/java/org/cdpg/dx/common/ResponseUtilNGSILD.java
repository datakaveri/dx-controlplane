package org.cdpg.dx.common;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.common.response.RestResponseNGSILD;


public class ResponseUtilNGSILD {
    public static JsonObject generateResponse(HttpStatusCode statusCode, String urn) {
        return generateResponse(statusCode, urn, statusCode.getDescription(),"");
    }

    public static JsonObject generateResponse(
            HttpStatusCode statusCode, String urn, String message, String instance) {
        String type = urn;

        return new RestResponseNGSILD.Builder()
                .withMessage(message)
                .withType(type)
                .withTitle(statusCode.getDescription())
                .withInstance(instance)
                .build()
                .toJson();
    }
}
