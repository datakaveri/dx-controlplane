package org.cdpg.dx.aaa.item.service;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.common.exception.DxBadRequestException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Service for generating Python scripts for OGC vector and STAC raster data onboarding
 */
public class ScriptGenerationService {
    private static final Logger LOGGER = LogManager.getLogger(ScriptGenerationService.class);
    
    // Paths to the script templates
    private static final String VECTOR_SCRIPT_PATH = "./scripts/vector_creation.py";
    private static final String RASTER_SCRIPT_PATH = "./scripts/raster_creation.py";
    
    // Output directory for generated scripts
    private static final String OUTPUT_DIR = "generated_scripts";

    /**
     * Reads a script file and replaces placeholders with actual values
     * 
     * @param scriptPath The path to the script file
     * @param authToken The bearer token for authentication
     * @param resourceId The resource ID for the item
     * @param title The title for the collection
     * @param description The description for the collection
     * @return The generated Python script as a string
     * @throws DxBadRequestException if the script file cannot be read
     */
    private String readAndCustomizeScript(String scriptPath, String authToken, String resourceId, String title, String description,String ogcDataPlaneUrl, String controlPlaneUrl) {
        try {
            Path path = Paths.get(scriptPath);
            String scriptContent = Files.readString(path);


            // Replace common placeholders
            scriptContent= scriptContent.replace("<OGC_DATA_PLANE_DOMAIN_HERE>", ogcDataPlaneUrl);
            scriptContent= scriptContent.replace("<CONTROL_PLANE_DOMAIN_HERE>", controlPlaneUrl);
            scriptContent = scriptContent.replace("<PUT_YOUR_BEARER_TOKEN_HERE>", authToken);
            scriptContent = scriptContent.replace("<PUT_RESOURCE_ID_HERE>", resourceId);
            
            // Replace file paths (different for vector vs raster)
            if (scriptPath.contains("vector")) {
                scriptContent = scriptContent.replace("<PUT_TITLE_HERE>", title);
                scriptContent = scriptContent.replace("<PUT_DESCRIPTION_HERE>", description);
            } else if (scriptPath.contains("raster")) {
                scriptContent = scriptContent.replace("Raster Data Collection", title);
                scriptContent = scriptContent.replace("Raster data collection for OGC onboarding", description);
            }
            
            return scriptContent;
        } catch (IOException e) {
            LOGGER.error("Failed to read script file: {}", scriptPath, e);
            throw new DxBadRequestException("Failed to read script template file: " + scriptPath, e);
        }
    }

    /**
     * Writes a customized script to a file and returns file information
     * 
     * @param scriptContent The customized script content
     * @param scriptType The type of script (vector/raster)
     * @param resourceId The resource ID
     * @return JsonObject containing file information
     */
    private JsonObject writeScriptToFile(String scriptContent, String scriptType, String resourceId) {
        try {
            // Create output directory if it doesn't exist
            Path outputDir = Paths.get(OUTPUT_DIR);
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
            }
            
            // Generate filename with timestamp
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = String.format("%s_%s_%s.py", resourceId, scriptType, timestamp);
            Path filePath = outputDir.resolve(filename);
            
            // Write script content to file
            Files.write(filePath, scriptContent.getBytes());
            
            LOGGER.info("Script written to file: {}", filePath);
            
            return new JsonObject()
                .put("filename", filename)
                .put("filePath", filePath.toString())
                .put("fileSize", Files.size(filePath))
                .put("createdAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .put("downloadUrl", "/items/scripts/download/" + filename);
                
        } catch (IOException e) {
            LOGGER.error("Failed to write script to file", e);
            throw new DxBadRequestException("Failed to write script file: " + e.getMessage());
        }
    }

    /**
     * Generates a vector creation script for OGC data onboarding and writes it to a file
     * 
     * @param authToken The bearer token for authentication
     * @param resourceId The resource ID for the item
     * @param title The title for the collection (from request body)
     * @param description The description for the collection (from request body)
     * @return JsonObject containing file information
     * @throws DxBadRequestException if the script template file cannot be read
     */
    public JsonObject generateVectorScriptFile(String authToken, String resourceId, String title, String description,String ogcDataPlaneUrl, String controlPlaneUrl) {
        LOGGER.debug("Generating vector script file for resourceId: {}", resourceId);
        String scriptContent = readAndCustomizeScript(VECTOR_SCRIPT_PATH, authToken, resourceId, title, description,ogcDataPlaneUrl,controlPlaneUrl);
        return writeScriptToFile(scriptContent, "vector", resourceId);
    }

    /**
     * Generates a raster creation script for OGC data onboarding and writes it to a file
     * 
     * @param authToken The bearer token for authentication
     * @param resourceId The resource ID for the item
     * @param title The title for the collection (from request body)
     * @param description The description for the collection (from request body)
     * @return JsonObject containing file information
     * @throws DxBadRequestException if the script template file cannot be read
     */
    public JsonObject generateRasterScriptFile(String authToken, String resourceId, String title, String description, String ogcDataPlaneUrl, String controlPlaneUrl) {
        LOGGER.debug("Generating raster script file for resourceId: {}", resourceId);
        
        String scriptContent = readAndCustomizeScript(RASTER_SCRIPT_PATH, authToken, resourceId, title, description, ogcDataPlaneUrl, controlPlaneUrl);
        return writeScriptToFile(scriptContent, "raster", resourceId);
    }

    /**
     * Determines the data type of the resource server based on accessType
     * 
     * @param resourceServer The resource server configuration
     * @return "vector" if vector data is detected, "raster" if raster data is detected, null otherwise
     */
    private String getDataType(JsonObject resourceServer) {
        LOGGER.debug("Checking resource server data type: {}", resourceServer);

        // Check for queryTypes (plural) first, then fallback to accessType (singular)
        JsonArray queryType = resourceServer.getJsonArray("queryTypes");

        List<Object> queryTypes = queryType.getList();
        // Check for vector data (FEATURES)
        if (queryTypes.contains("FEATURES")) {
            return "vector";
        }
        // Check for raster data (STAC)
        if (queryTypes.contains("STAC")) {
            return "raster";
        }
        return null;
    }

    /**
     * Determines if the resource server contains vector data based on accessType
     * 
     * @param resourceServer The resource server configuration
     * @return true if vector data is detected, false otherwise
     */
    public boolean isVectorData(JsonObject resourceServer) {
        return "vector".equals(getDataType(resourceServer));
    }

    /**
     * Determines if the resource server contains raster data based on accessType
     * 
     * @param resourceServer The resource server configuration
     * @return true if raster data is detected, false otherwise
     */
    public boolean isRasterData(JsonObject resourceServer) {
        return "raster".equals(getDataType(resourceServer));
    }

    /**
     * Creates a script response object containing the generated script file information
     * 
     * @param fileInfo The file information from writeScriptToFile
     * @param scriptType The type of script (vector/raster)
     * @param resourceId The resource ID
     * @return JsonObject containing file information and metadata
     */
    public JsonObject createScriptFileResponse(JsonObject fileInfo, String scriptType, String resourceId) {
        return new JsonObject()
            .put("scriptType", scriptType)
            .put("resourceId", resourceId)
            .put("file", fileInfo);
    }

}
