package uk.co.sainsburys.breaking.change.detect.Service;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SpecCompareService {

    public void compareSpecifications(String jarSpecUrl, String liveApiSpecUrl) {
        String jarSpec = fetchSpecification(jarSpecUrl);
        String liveApiSpec = fetchSpecification(liveApiSpecUrl);

        SwaggerParseResult jarParseResult = parseSpecification(jarSpec);
        SwaggerParseResult liveApiParseResult = parseSpecification(liveApiSpec);

//        if (jarParseResult.getMessages().isEmpty() && liveApiParseResult.getMessages().isEmpty()) {
            // Both specifications are valid, proceed with comparison
            OpenAPI jarOpenAPI = jarParseResult.getOpenAPI();
            OpenAPI liveApiOpenAPI = liveApiParseResult.getOpenAPI();

            // Compare specifications and handle breaking changes
            comparePaths(jarOpenAPI.getPaths(), liveApiOpenAPI.getPaths());
            compareComponents(jarOpenAPI.getComponents(), liveApiOpenAPI.getComponents());
//        } else {
//            // Handle parsing errors
//            System.out.println("Parsing error(s) occurred.");
//            System.out.println("Jar Specification Errors: " + jarParseResult.getMessages());
//            System.out.println("Live API Specification Errors: " + liveApiParseResult.getMessages());
//        }
    }

    private SwaggerParseResult parseSpecification(String spec) {
        return new OpenAPIV3Parser().readContents(spec, null, new ParseOptions());
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

    private void comparePaths(Paths jarPaths, Paths liveApiPaths) {
        // Paths added
        List<String> addedPaths = liveApiPaths.keySet().stream()
                .filter(path -> !jarPaths.containsKey(path))
                .collect(Collectors.toList());
        System.out.println("Paths added: " + addedPaths);

        // Paths removed
        List<String> removedPaths = jarPaths.keySet().stream()
                .filter(path -> !liveApiPaths.containsKey(path))
                .collect(Collectors.toList());
        System.out.println("Paths removed: " + removedPaths);
    }

    private void compareComponents(Components jarComponents, Components liveApiComponents) {

        compareSchemas(jarComponents.getSchemas(), liveApiComponents.getSchemas());
//        compareResponses(jarComponents.getResponses(), liveApiComponents.getResponses());
//        compareParameters(jarComponents.getParameters(), liveApiComponents.getParameters());
        // Add more comparisons as needed...
    }

    private void compareSchemas(Map<String, Schema> jarSchemas, Map<String, Schema> liveApiSchemas) {
        // Iterate through schemas in the JAR file
        for (Map.Entry<String, Schema> entry : jarSchemas.entrySet()) {
            String schemaName = entry.getKey();
            Schema jarSchema = entry.getValue();

            // Check if the same schema exists in the live API
            if (liveApiSchemas.containsKey(schemaName)) {
                Schema liveApiSchema = liveApiSchemas.get(schemaName);

                // Compare relevant properties of the schemas
                if (!jarSchema.equals(liveApiSchema)) {
                    System.out.println("Schema '" + schemaName + "' has differences.");
                    // Handle or log the differences based on your requirements
                }
            } else {
                System.out.println("Schema '" + schemaName + "' is missing in the live API.");
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
