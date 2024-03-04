package uk.co.sainsburys.breaking.change.detect.Controller;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.co.sainsburys.breaking.change.detect.Service.SpecCompareService;

@RestController
@RequestMapping("/api/comparison")
public class SpecCompareController {
    private final SpecCompareService specCompareService;

    @Autowired
    public SpecCompareController(SpecCompareService specCompareService){
        this.specCompareService = specCompareService;
    }

    @GetMapping("/compare")
    public String compareSpecifications() {
        String jarSpecUrl = "http://localhost:8080/v3/api-docs";
        String liveApiSpecUrl = "http://localhost:8081/v3/api-docs";

        // Call the service to compare specifications
        specCompareService.compareSpecifications(jarSpecUrl, liveApiSpecUrl);

        return "Comparison completed. Check the logs for results.";
    }
}
