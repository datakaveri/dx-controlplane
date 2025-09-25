package org.cdpg.dx.aaa.item.util;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.common.exception.DxBadRequestException;

public class DataBankCreationRequest {
    private final String userId;
    private final String token;
    private final JsonObject dataDescriptor;
    private final JsonObject originalBody;

    public DataBankCreationRequest(String userId, String token, JsonObject dataDescriptor, JsonObject originalBody) {

        if (userId == null || userId.isBlank()) {
            throw new DxBadRequestException("User ID is required");
        }
        if (token == null || token.isBlank()) {
            throw new DxBadRequestException("Token is required");
        }
        if (dataDescriptor == null) {
            throw new DxBadRequestException("Data descriptor is required");
        }
        if (originalBody == null) {
            throw new DxBadRequestException("Original body is required");
        }
        
        this.userId = userId;
        this.token = token;
        this.dataDescriptor = dataDescriptor;
        this.originalBody = originalBody;
    }

    public String getUserId() {
        return userId;
    }

    public String getToken() {
        return token;
    }

    public JsonObject getDataDescriptor() {
        return dataDescriptor;
    }

    public JsonObject getOriginalBody() {
        return originalBody;
    }
}