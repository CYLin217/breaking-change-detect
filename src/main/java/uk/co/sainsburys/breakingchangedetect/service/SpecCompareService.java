package uk.co.sainsburys.breakingchangedetect.service;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import uk.co.sainsburys.breakingchangedetect.entity.Entry;
import uk.co.sainsburys.breakingchangedetect.entity.Type;
import uk.co.sainsburys.breakingchangedetect.entity.dto.DifferenceCase;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
//@RequiredArgsConstructor
public class SpecCompareService {

    private static final Logger logger = LoggerFactory.getLogger(SpecCompareService.class);

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

    private final RestTemplate restTemplate;

    @Autowired
    public SpecCompareService(RestTemplate restTemplate, OpenAPIV3Parser parser){
        this.parser = parser;
        this.restTemplate = restTemplate;
    }


    /**
     * Compares the specifications of old and new endpoints.
     * <p>
     * There are 4 steps of checks
     * <li>compare path</li>
     * <li>compare request body</li>
     * <li>compare response body</li>
     * <li>compare parameters</li>
     * </p>
     *
     * @param oldSpecUrl The url of old endpoints.
     * @param newSpecUrl The url of new endpoints.
     * @return A list of DifferenceCase objects representing the breaking changes.
     */
    public List<DifferenceCase> compareSpecifications(String oldSpecUrl, String newSpecUrl) {

        List<DifferenceCase> result = new ArrayList<>();

        // Both specifications are valid, proceed with comparison
        OpenAPI oldOpenAPI = fetchSpecification(oldSpecUrl);
        OpenAPI newOpenAPI = fetchSpecification(newSpecUrl);


        var oldEndpoints = extractEndpoints(oldOpenAPI.getPaths(), oldOpenAPI.getComponents());
        var newEndpoints = extractEndpoints(newOpenAPI.getPaths(), newOpenAPI.getComponents());
        // ("/api/book GET", Endpoint)

        // Compare specifications and handle breaking changes
        result.addAll(comparePaths(oldEndpoints.keySet(), newEndpoints.keySet()));
        result.addAll(compareRequestBody(oldEndpoints, newEndpoints));
        result.addAll(compareResponseBody(oldEndpoints, newEndpoints));
        result.addAll(compareParameters(oldEndpoints, newEndpoints));

        return result;
    }



    private SwaggerParseResult parseSpecification(String spec) {
        return parser.readContents(spec, null, new ParseOptions());
    }

    private OpenAPI fetchSpecification(String specUrl) {

        ResponseEntity<String> responseEntity;

        try {
            responseEntity = restTemplate.getForEntity(specUrl, String.class);
        } catch (Exception e) {
            // Log the exception and rethrow it as a RuntimeException
            logger.error("Failed to fetch the specification from URL: " + specUrl, e);
            throw new RuntimeException("Failed to fetch the specification from URL: " + specUrl, e);
        }

        // Check if the request was successful (HTTP status code 200)
        if (responseEntity.getStatusCode().is2xxSuccessful()) {
            // Return the specification as a string
            return parseSpecification(responseEntity.getBody()).getOpenAPI();
        } else {
            // Handle the case when the request is not successful
            logger.warn("Failed to fetch the specification. Status code: " + responseEntity.getStatusCode());
            throw new RuntimeException("Failed to fetch the specification. Status code: " + responseEntity.getStatusCode());
        }
    }

    private List<DifferenceCase> comparePaths(Set<String> oldPaths, Set<String> newPaths) {
        Set<String> removedPaths = new HashSet<>(oldPaths);
        List<DifferenceCase> pathResult = new ArrayList<>();
        removedPaths.removeAll(newPaths);

        if (!removedPaths.isEmpty()) {
            System.out.println("Paths removed: " + removedPaths);
            System.out.println("Contains breaking change since path(s) have been removed!");
            removedPaths.forEach(path -> pathResult.add(DifferenceCase.builder()
                    .type(Type.REMOVED_PATH)
                    .entry(Entry.ENDPOINT)
                    .endPoint(path).build()));
        }

        return pathResult;
    }

    private List<DifferenceCase> compareRequestBody(Map<String, Endpoint> oldEndPoints, Map<String, Endpoint> newEndPoints){

        List<DifferenceCase> requestResult = new ArrayList<>();
        for (Map.Entry<String, Endpoint> entry : oldEndPoints.entrySet()){

            var oldEndPoint = entry.getValue();
            var newEndpoint = newEndPoints.get(entry.getKey());
            if (newEndpoint != null && !oldEndPoint.getRequestFields().equals(newEndpoint.getRequestFields()) ){
                System.out.println("Contains breaking changes since request field is difference!");
                requestResult.add(DifferenceCase.builder()
                        .type(Type.REQUEST_FIELD_DIFFER)
                        .entry(Entry.ENDPOINT)
                        .endPoint(entry.getValue().getPath())
                        .build());
            }
        }

        return requestResult;
    }

    private List<DifferenceCase> compareResponseBody(Map<String, Endpoint> oldEndPoints, Map<String, Endpoint> newEndPoints){

        List<DifferenceCase> responseResult = new ArrayList<>();
        for (Map.Entry<String, Endpoint> entry : oldEndPoints.entrySet()){

            var oldEndPoint = entry.getValue();
            var newEndpoint = newEndPoints.get(entry.getKey());
            if (newEndpoint != null){
                for(String key: oldEndPoint.getResponseFields().keySet()){
                    if (!newEndpoint.getResponseFields().containsKey(key)){
                        System.out.println("Contains breaking changes since some response field being removed!");
                        responseResult.add(DifferenceCase.builder()
                                .type(Type.RESPONSE_FIELD_REMOVED)
                                .entry(Entry.ENDPOINT)
                                .endPoint(entry.getValue().getPath())
                                .build());
                    }
                }
            }
        }

        return responseResult;
    }

    private boolean areParametersEqual(Parameter oldParam, Parameter newParam){
        return Objects.equals(oldParam.getName(), newParam.getName())
                && Objects.equals(oldParam.getIn(), newParam.getIn())
                && Objects.equals(oldParam.getRequired(), newParam.getRequired())
                && Objects.equals(oldParam.get$ref(), newParam.get$ref());
    }
    private List<DifferenceCase> compareParameters(Map<String, Endpoint> oldEndPoints, Map<String, Endpoint> newEndPoints){

        List<DifferenceCase> paramResult = new ArrayList<>();
        for (Map.Entry<String, Endpoint> entry : oldEndPoints.entrySet()) {

            var oldEndPointParams = entry.getValue().getRequestParams();
            var newEndpoint = newEndPoints.get(entry.getKey());

            if (oldEndPointParams == null || newEndpoint == null){
                continue;
            }

            List<Parameter> same = oldEndPointParams.stream().filter(param -> newEndpoint.getRequestParams().stream()
                            .anyMatch(newParam -> areParametersEqual(param, newParam)))
                    .collect(Collectors.toList());

            for (Parameter param : same){
                if (!newEndpoint.getRequestParams().stream().anyMatch(newParam -> areParametersEqual(param, newParam))
                        && param.getRequired()){
                    logger.warn("Contains breaking changes since some required parameters being changed!");
                    paramResult.add(DifferenceCase.builder()
                            .type(Type.REQUIRED_PARAM_CHANGED)
                            .entry(Entry.ENDPOINT)
                            .endPoint(entry.getValue().getPath())
                            .build());
                }
            }

            List<Parameter> removed = oldEndPointParams.stream()
                    .filter(param -> newEndpoint.getRequestParams().stream()
                    .noneMatch(newParam -> areParametersEqual(param, newParam)))
                    .collect(Collectors.toList());

            List <Parameter> added = newEndpoint.getRequestParams().stream()
                    .filter(newParam -> oldEndPointParams.stream()
                    .noneMatch(param -> areParametersEqual(param, newParam)))
                    .collect(Collectors.toList());

            if (removed.stream().anyMatch(Parameter::getRequired)){
                logger.warn("Contains breaking changes since some required parameters doesn't exist!");
                paramResult.add(DifferenceCase.builder()
                        .type(Type.REQUIRED_PARAM_NOT_EXIST)
                        .entry(Entry.ENDPOINT)
                        .endPoint(entry.getValue().getPath())
                        .build());
            }

            if (added.stream().anyMatch(Parameter::getRequired)){
                logger.warn("Contains breaking changes since some required parameters being added!");
                paramResult.add(DifferenceCase.builder()
                        .type(Type.REQUIRED_PARAM_ADDED)
                        .entry(Entry.ENDPOINT)
                        .endPoint(entry.getValue().getPath())
                        .build());
            }
        }

        return paramResult;
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

    private Schema extractSchemaByReference(String ref, Components components){

        return components.getSchemas()
                .get(ref.substring(ref.lastIndexOf("/") + 1));
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

        String ref = mediaType.getSchema().get$ref();
        Schema definition = extractSchemaByReference(ref, components);

        // todo: recursive build for path -> type map
        return buildPathTypeMap(definition, ""); // return result from here
    }


    private Map<String, String> extractResponseFields(Operation operation, Components components) {
        ApiResponse response = operation.getResponses().get("200");

        return Optional.ofNullable(response)
                .flatMap(resp -> Optional.ofNullable(resp.getContent()))
                .flatMap(content -> Optional.ofNullable(content.get("*/*")))
                .map(MediaType::getSchema)
                .flatMap(schema -> Optional.ofNullable(schema.get$ref()))
                .map(reference -> extractSchemaByReference(reference, components))
                .map(definition -> buildPathTypeMap(definition, ""))
                .orElse(Map.of());
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
