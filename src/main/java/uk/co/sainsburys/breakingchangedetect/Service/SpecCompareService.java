package uk.co.sainsburys.breakingchangedetect.Service;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
//@RequiredArgsConstructor
public class SpecCompareService {

    @NoArgsConstructor
    @Data
    private static class Endpoint {
        private String path;
        private Operation operation;
        private Map<String, String> requestFields;
        private Map<String, String> responseFields;
        private List<Parameter> requestParams;
    }

    private final OpenAPIV3Parser parser;

    public SpecCompareService(OpenAPIV3Parser parser){
        this.parser = parser;
    }

    public void compareSpecifications(String oldSpecUrl, String newSpecUrl) {
        String oldSpec = fetchSpecification(oldSpecUrl);
        String newSpec = fetchSpecification(newSpecUrl);

        SwaggerParseResult oldParseResult = parseSpecification(oldSpec);
        SwaggerParseResult newParseResult = parseSpecification(newSpec);

        // Both specifications are valid, proceed with comparison
        OpenAPI oldOpenAPI = oldParseResult.getOpenAPI();
        OpenAPI newOpenAPI = newParseResult.getOpenAPI();


        var oldEndpoints = extractEndpoints(oldOpenAPI.getPaths(), oldOpenAPI.getComponents());
        var newEndpoints = extractEndpoints(newOpenAPI.getPaths(), newOpenAPI.getComponents());
        // ("/api/book GET", Endpoint)

        // Compare specifications and handle breaking changes
        comparePaths(oldEndpoints.keySet(), newEndpoints.keySet());
        compareRequestBody(oldEndpoints, newEndpoints);
        compareResponseBody(oldEndpoints, newEndpoints);
        compareParameters(oldEndpoints, newEndpoints);


        // for each path in the old endpoints keyset:
        //    check that it is still there in newEndpoints, if it isn't, log an error
        //    if it is, compare the value for that path from oldEndpoints and newEndpoints, in some function where you
        //    check the operation, request fields, response fields, and request query params have not changed

        // path -- e.g., GET /clusters/something/path/blah/etc
        // the actual Operation object
        // the request body
        // the response body
        // the request parameters


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

    private void comparePaths(Set<String> oldPaths, Set<String> newPaths) {
        Set<String> removedPaths = new HashSet<>(oldPaths);
        removedPaths.removeAll(newPaths);

        if (!removedPaths.isEmpty()) {
            System.out.println("Paths removed: " + removedPaths);
            System.out.println("Contains breaking change since path(s) have been removed!");
        }
    }

    private void compareRequestBody(Map<String, Endpoint> oldEndPoints, Map<String, Endpoint> newEndPoints){

        for (Map.Entry<String, Endpoint> entry : oldEndPoints.entrySet()){

            var oldEndPoint = entry.getValue();
            var newEndpoint = newEndPoints.get(entry.getKey());
            if (newEndpoint != null && !oldEndPoint.getRequestFields().equals(newEndpoint.getRequestFields()) ){
                System.out.println("Contains breaking changes since request field is difference!");
            }
        }
    }

    private void compareResponseBody(Map<String, Endpoint> oldEndPoints, Map<String, Endpoint> newEndPoints){

        for (Map.Entry<String, Endpoint> entry : oldEndPoints.entrySet()){

            var oldEndPoint = entry.getValue();
            var newEndpoint = newEndPoints.get(entry.getKey());
            if (newEndpoint != null){
                for(String key: oldEndPoint.getResponseFields().keySet()){
                    if (!newEndpoint.getResponseFields().containsKey(key)){
                        System.out.println("Contains breaking changes since some response field being removed!");
                    }
                }
            }
        }
    }

    private boolean areParametersEqual(Parameter oldParam, Parameter newParam){
        return Objects.equals(oldParam.getName(), newParam.getName())
                && Objects.equals(oldParam.getIn(), newParam.getIn())
                && Objects.equals(oldParam.getRequired(), newParam.getRequired())
                && Objects.equals(oldParam.get$ref(), newParam.get$ref());
    }
    private void compareParameters(Map<String, Endpoint> oldEndPoints, Map<String, Endpoint> newEndPoints){

        for (Map.Entry<String, Endpoint> entry : oldEndPoints.entrySet()) {

            var oldEndPointParams = entry.getValue().getRequestParams();
            var newEndpoint = newEndPoints.get(entry.getKey());

            if (oldEndPointParams != null && newEndpoint != null){
                List<Parameter> same = oldEndPointParams.stream().filter(param -> newEndpoint.getRequestParams().stream()
                                .anyMatch(newParam -> areParametersEqual(param, newParam)))
                        .collect(Collectors.toList());

                for (Parameter param : same){
                    if (!newEndpoint.getRequestParams().stream().anyMatch(newParam -> areParametersEqual(param, newParam))
                            && param.getRequired()){
                        System.out.println("Contains breaking changes since some required parameters being changed!");
                    }
                }

                List<Parameter> removed = oldEndPointParams.stream().filter(param -> newEndpoint.getRequestParams().stream()
                        .noneMatch(newParam -> areParametersEqual(param, newParam))).collect(Collectors.toList());

                List <Parameter> added = newEndpoint.getRequestParams().stream().filter(newParam -> oldEndPointParams.stream()
                        .noneMatch(param -> areParametersEqual(param, newParam))).collect(Collectors.toList());

                if (removed.stream().anyMatch(Parameter::getRequired)){
                    System.out.println("Contains breaking changes since some required parameters doesn't exist!");
                }

                if (added.stream().anyMatch(Parameter::getRequired)){
                    System.out.println("Contains breaking changes since some required parameters being added!");
                }
            }


        }
    }

    private Map<String, Endpoint> extractEndpoints(Paths paths, Components components) {
        return paths.entrySet().stream()
                .map(it -> extractEndpoints(it, components))
                .flatMap(Collection::stream)
                .collect(Collectors.toMap(Endpoint::getPath, Function.identity()));
    }

    private List<Endpoint> extractEndpoints(Map.Entry<String, PathItem> pathItem, Components components) {
        var pathMethods = buildPathMethodMap(pathItem);
        return pathMethods.entrySet().stream()
                .map(it -> {
                    var endpoint = new Endpoint();
                    var value = it.getValue();
                    endpoint.setPath(it.getKey());
                    endpoint.setOperation(value);
                    endpoint.setRequestFields(extractRequestFields(value, components));
                    endpoint.setResponseFields(extractResponseFields(value, components));
                    endpoint.setRequestParams(value.getParameters());
                    return endpoint;
                })
                .toList();
    }


    private Map<String, Operation> buildPathMethodMap(Map.Entry<String, PathItem> pathItem){
        Map<String, Function<PathItem, Operation>> mappings = Map.of(
                "GET", PathItem::getGet,
                "POST", PathItem::getPost,
                "PUT", PathItem::getPut,
                "PATCH", PathItem::getPatch,
                "DELETE", PathItem::getDelete
        );

        return mappings.entrySet().stream()
                .filter(mappingEntry -> mappingEntry.getValue().apply(pathItem.getValue()) != null)
                .collect(Collectors.toMap(
                        method -> pathItem.getKey() + " " + method.getKey(),
                        method -> method.getValue().apply(pathItem.getValue())));
    }

    //todo: build same method but for response
    private Map<String, String> extractRequestFields(Operation operation, Components components) {
        if (operation.getRequestBody() == null) {
            return Map.of();
        }

        MediaType mediaType = operation.getRequestBody().getContent().get("application/json");
        if (mediaType == null) {
            return Map.of();
        }

        Schema schema = mediaType.getSchema();
        Schema definition = components.getSchemas().get(schema.get$ref().substring(schema.get$ref().lastIndexOf("/") + 1));

        // todo: recursive build for path -> type map
        return buildPathTypeMap(definition, ""); // return result from here
    }


    private Map<String, String> extractResponseFields(Operation operation, Components components){
        ApiResponse response = operation.getResponses().get("200");

        if (response == null || response.getContent() == null || response.getContent().get("*/*") == null
                || response.getContent().get("*/*").getSchema().get$ref() == null){
            return Map.of();
        }

        Schema schema = response.getContent().get("*/*").getSchema();
        Schema definition = components.getSchemas().get(schema.get$ref()
                    .substring(schema.get$ref().lastIndexOf("/") + 1));
        return buildPathTypeMap(definition, "");

    }

    private Map<String, String> buildPathTypeMap(Schema<?> schema, String currentPath) {
        Map<String, String> pathTypeMap = new HashMap<>();

        if (schema == null) {
            return pathTypeMap;
        }

        if ("object".equals(schema.getType()) && schema.getProperties() != null) {
            for (Map.Entry<String, Schema> entry : schema.getProperties().entrySet()) {
                String propertyName = entry.getKey();
                Schema propertySchema = entry.getValue();
                String propertyPath = currentPath + "." + propertyName;

                // Recursively build pathTypeMap for nested properties
                Map<String, String> nestedMap = buildPathTypeMap(propertySchema, propertyPath);
                pathTypeMap.putAll(nestedMap);
            }
        } else {
            pathTypeMap.put(currentPath, schema.getType());
        }

        return pathTypeMap;
    }

}
