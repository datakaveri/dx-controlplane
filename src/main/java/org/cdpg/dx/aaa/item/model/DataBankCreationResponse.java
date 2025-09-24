package org.cdpg.dx.aaa.item.model;

import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class DataBankCreationResponse {
    private List<ResourceServerResponse> resourceServers = new ArrayList<>();
    private String itemId;
    private List<String> itemType;
    private String itemName;

    public DataBankCreationResponse() {}

    public DataBankCreationResponse(Item item) {
        this.itemId = item.getId();this.itemType = item.getType();this.itemName = item.getName();
    }

    public List<ResourceServerResponse> getResourceServers() {
        return resourceServers;
    }

    public void setResourceServers(List<ResourceServerResponse> resourceServers) {
        this.resourceServers = resourceServers;
    }

    public void addResourceServer(ResourceServerResponse resourceServer) {
        this.resourceServers.add(resourceServer);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.put("itemId", itemId != null ? itemId : "");
        json.put("itemType", itemType != null ? itemType : "");
        json.put("itemName", itemName != null ? itemName : "");

        JsonObject resourceServersJson = new JsonObject();
        for (ResourceServerResponse rs : resourceServers) {
            resourceServersJson.put(rs.getType(), rs.toJson());
        }
        json.put("resourceServers", resourceServersJson);
        
        return json;
    }

    public static class ResourceServerResponse {
        private String type;
        private JsonObject responseData;
        public ResourceServerResponse() {}

        public ResourceServerResponse(String type, JsonObject responseData) {
            this.type = type;
            this.responseData = responseData;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public JsonObject getResponseData() {
            return responseData;
        }

        public void setResponseData(JsonObject responseData) {
            this.responseData = responseData;
        }


        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            if (responseData != null) {
                json.put("data", responseData);
            }
            return json;
        }
    }
}
