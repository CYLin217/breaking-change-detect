package uk.co.sainsburys.breaking.change.detect.Service;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
//@RequiredArgsConstructor
public class SpecCompareService {

    private final OpenAPIV3Parser parser;

    public SpecCompareService(OpenAPIV3Parser parser){
        this.parser = parser;
    }

    public void compareSpecifications(String oldSpecUrl, String newSpecUrl) {
        String oldSpec = fetchSpecification(oldSpecUrl);
        String newSpec = fetchSpecification(newSpecUrl);

        SwaggerParseResult oldParseResult = parseSpecification(oldSpec);
        SwaggerParseResult newParseResult = parseSpecification(newSpec);

//        if (oldParseResult.getMessages().isEmpty() && newParseResult.getMessages().isEmpty()) {
        // Both specifications are valid, proceed with comparison
        OpenAPI oldOpenAPI = oldParseResult.getOpenAPI();
        OpenAPI newOpenAPI = newParseResult.getOpenAPI();

        // Compare specifications and handle breaking changes
        comparePaths(oldOpenAPI.getPaths(), newOpenAPI.getPaths());
        compareComponents(oldOpenAPI.getComponents(), newOpenAPI.getComponents());

        Map<String, Operation> oldPathsOperations = buildPathMethodMap(oldOpenAPI.getPaths());

        Map<String, Operation> newPathsOperations = buildPathMethodMap(newOpenAPI.getPaths());

        Map< Map<String, Operation>, Map<String, String>> oldFinalMap = new HashMap<>();
        Map< Map<String, Operation>, Map<String, String>> newFinalMap = new HashMap<>();

        for (Map.Entry<String, Operation> entry : oldPathsOperations.entrySet()){

            oldFinalMap.put(Map.of(entry.getKey(), entry.getValue()), extractRequestFields(entry.getValue(), oldOpenAPI.getComponents()));
        }

        for (Map.Entry<String, Operation> entry : newPathsOperations.entrySet()){

            newFinalMap.put(Map.of(entry.getKey(), entry.getValue()), extractRequestFields(entry.getValue(), newOpenAPI.getComponents()));
        }

        List<Map<String, Operation>> removedPaths = oldFinalMap.keySet().stream()
                .filter(path -> !newFinalMap.containsKey(path))
                .collect(Collectors.toList());

        if (!removedPaths.isEmpty()){
            System.out.println("Paths removed: " + removedPaths.toString());
            System.out.println("Contains breaking change since path(s) been removed!");
        }

//        } else {
//            // Handle parsing errors
//            System.out.println("Parsing error(s) occurred.");
//            System.out.println("Jar Specification Errors: " + oldParseResult.getMessages());
//            System.out.println("Live API Specification Errors: " + newParseResult.getMessages());
//        }
    }

    private SwaggerParseResult parseSpecification(String spec) {
        return parser.readContents(spec, null, new ParseOptions());
    }

    private String fetchSpecification(String specUrl) {

        RestTemplate restTemplate = new RestTemplate();

        ResponseEntity<String> responseEntity = restTemplate.getForEntity(specUrl, String.class);

        // Check if the request was successful (HTTP status code 200)
        if (responseEntity.getStatusCode().is2xxSuccessful()) {
            // Return the specification as a string
            return responseEntity.getBody();
        } else {
            // Handle the case when the request is not successful
            System.out.println("Failed to fetch the specification. Status code: " + responseEntity.getStatusCode());
            return "";  // Return an empty string or handle it based on your requirements
        }
    }

    private void comparePaths(Paths oldPaths, Paths newPaths) {


        Map<String, Operation> oldPathsOperations = buildPathMethodMap(oldPaths);

        Map<String, Operation> newPathsOperations = buildPathMethodMap(newPaths);

        if (oldPathsOperations.equals(newPathsOperations)){
            System.out.println("No breaking change");
        }else {
            List<String> removedPaths = oldPaths.keySet().stream()
                    .filter(path -> !newPaths.containsKey(path))
                    .collect(Collectors.toList());
            System.out.println("Paths removed: " + removedPaths);
            System.out.println("Contains breaking change since path(s) been removed!");
        }
        // Paths added
//        List<String> addedPaths = newPaths.keySet().stream()
//                .filter(path -> !oldPaths.containsKey(path))
//                .collect(Collectors.toList());
//        System.out.println("Paths added: " + addedPaths);
//
//        // Paths removed
//        List<String> removedPaths = oldPaths.keySet().stream()
//                .filter(path -> !newPaths.containsKey(path))
//                .collect(Collectors.toList());
//        System.out.println("Paths removed: " + removedPaths);
//
//        if (!removedPaths.isEmpty()) {
//            System.out.println("Contains breaking change since path(s) been removed!");
//        }
    }

    private Map<String, Operation> buildPathMethodMap(Paths paths){
        Map<String, Function<PathItem, Operation>> mappings = Map.of(
                "GET", PathItem::getGet,
                "POST", PathItem::getPost,
                "PUT", PathItem::getPut,
                "PATCH", PathItem::getPatch,
                "DELETE", PathItem::getDelete
        );

        Map<String, Operation> resultMap= paths.entrySet().stream()
                .flatMap(
                        entry ->
                                mappings.entrySet().stream()
                                        .filter(mappingEntry -> mappingEntry.getValue().apply(entry.getValue()) != null)
                                        .map(
                                                mappingEntry -> new AbstractMap.SimpleEntry<>(
                                                        entry.getKey() + " " + mappingEntry.getKey(),
                                                        mappingEntry.getValue().apply(entry.getValue())
                                                )
                                        )
                )
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                ));

        return resultMap;
    }

    private void compareComponents(Components oldComponents, Components newComponents) {

        compareSchemas(oldComponents.getSchemas(), newComponents.getSchemas());
//        compareResponses(oldComponents.getResponses(), newComponents.getResponses());
//        compareParameters(oldComponents.getParameters(), newComponents.getParameters());

    }

    //todo: build same method but for response
    private  Map<String, String> extractRequestFields(Operation operation, Components components) {
        if(operation.getRequestBody() != null){
            MediaType mediaType = operation.getRequestBody().getContent()
                    .get("application/json");
            if (mediaType == null) {
                return Map.of();
            }

            Schema schema = mediaType.getSchema();
            Schema definition = components.getSchemas().get(schema.get$ref().substring(20));
            //todo: recursive build for path -> type map
            return buildPathTypeMap(definition, ""); // return result from here
        }
        return Map.of();
    }

    private Map<String, String> extractResponseFields(Operation operation, Components components){
        ApiResponse response = operation.getResponses().get("200");

        if (response == null){
            return Map.of();
        }

        Schema schema = response.getContent().get("application/json").getSchema();
        Schema definition = components.getSchemas().get(schema.get$ref().substring(20));
        return buildPathTypeMap(definition, "");
    }

    private Map<String, String> buildPathTypeMap(Schema<?> schema, String currentPath){
        Map<String, String> pathTypeMap = new HashMap<>();

        if (schema != null) {
            if("object".equals(schema.getType()) && schema.getProperties() != null){
                for (Map.Entry<String, Schema> entry : schema.getProperties().entrySet()) {
                    String propertyName = entry.getKey();
                    Schema propertySchema = entry.getValue();
                    String propertyPath = currentPath + "." + propertyName;

                    // Recursively build pathTypeMap for nested properties
                    Map<String, String> nestedMap = buildPathTypeMap(propertySchema, propertyPath);
                    pathTypeMap.putAll(nestedMap);
                } }
            else{
                pathTypeMap.put(currentPath, schema.getType());
            }
        }

        return pathTypeMap;
    }

    private void compareSchemas(Map<String, Schema> oldSchemas, Map<String, Schema> newSchemas) {
        // Iterate through schemas in the JAR file
        for (Map.Entry<String, Schema> entry : oldSchemas.entrySet()) {
            String schemaName = entry.getKey();
            Schema oldSchema = entry.getValue();

            // Check if the same schema exists in the live API
            if (newSchemas.containsKey(schemaName)) {
                Schema newSchema = newSchemas.get(schemaName);
                System.out.println(newSchema.contentSchema(newSchema));

                // Compare relevant properties of the schemas
                if (!oldSchema.equals(newSchema)) {
                    System.out.println("Schema '" + schemaName + "' has differences.");
                    // Handle or log the differences based on your requirements
                }
            } else {
                System.out.println("Schema '" + schemaName + "' is missing in the new version service.");
                // Handle or log the missing schema based on your requirements
            }
        }
    }

    private void compareResponses(Map<String, ApiResponse> jarResponses, Map<String, ApiResponse> liveApiResponses) {
        // Iterate through responses in the JAR file
        for (Map.Entry<String, ApiResponse> entry : jarResponses.entrySet()) {
            String responseName = entry.getKey();
            ApiResponse jarResponse = entry.getValue();

            // Check if the same response exists in the live API
            if (liveApiResponses.containsKey(responseName)) {
                ApiResponse liveApiResponse = liveApiResponses.get(responseName);

                // Compare relevant properties of the responses
                if (!jarResponse.equals(liveApiResponse)) {
                    System.out.println("Response '" + responseName + "' has differences.");
                    // Handle or log the differences based on your requirements
                }
            } else {
                System.out.println("Response '" + responseName + "' is missing in the live API.");
                // Handle or log the missing response based on your requirements
            }
        }
    }

    private void compareParameters(Map<String, Parameter> jarParameters, Map<String, Parameter> liveApiParameters) {
        // Iterate through parameters in the JAR file
        for (Map.Entry<String, Parameter> entry : jarParameters.entrySet()) {
            String parameterName = entry.getKey();
            Parameter jarParameter = entry.getValue();

            // Check if the same parameter exists in the live API
            if (liveApiParameters.containsKey(parameterName)) {
                Parameter liveApiParameter = liveApiParameters.get(parameterName);

                // Compare relevant properties of the parameters
                if (!jarParameter.equals(liveApiParameter)) {
                    System.out.println("Parameter '" + parameterName + "' has differences.");
                    // Handle or log the differences based on your requirements
                }
            } else {
                System.out.println("Parameter '" + parameterName + "' is missing in the live API.");
                // Handle or log the missing parameter based on your requirements
            }
        }
    }

}
