package app.arete.arete_api.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves the hand-written OpenAPI specs to Swagger UI. All files share one folder so their relative
 * {@code $ref}s (e.g. {@code ./common.yaml}) resolve. Only active where Swagger UI is enabled (the dev profile).
 */
@Configuration
@ConditionalOnProperty(name = "springdoc.swagger-ui.enabled", havingValue = "true")
public class OpenApiDocsConfig implements WebMvcConfigurer {

    private static final String SPECS_PATH_PATTERN = "/openapi/**";
    private static final String SPECS_LOCATION = "classpath:/api/";

    @Override
    public void addResourceHandlers(final ResourceHandlerRegistry registry) {
        registry.addResourceHandler(SPECS_PATH_PATTERN).addResourceLocations(SPECS_LOCATION);
    }
}
