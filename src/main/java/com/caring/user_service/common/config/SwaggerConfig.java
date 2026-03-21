package com.caring.user_service.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.core.env.Environment;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@RequiredArgsConstructor
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "API Document", description = "USER SERVICE 명세서", version = "v3")
)
public class SwaggerConfig {

    private final Environment env;

    @Bean
    public OpenAPI openAPI() {
        String jwtSchemeName = "JWT";
        // API 요청헤더에 인증정보 포함
        SecurityRequirement securityRequirement = new SecurityRequirement().addList(jwtSchemeName);
        // SecuritySchemes 등록
        Components components = new Components()
                .addSecuritySchemes(jwtSchemeName, new SecurityScheme()
                        .name(jwtSchemeName)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .in(SecurityScheme.In.HEADER)
                        .bearerFormat("Authorization"));


        return new OpenAPI()
                .addServersItem(new Server().url(env.getProperty("openapi.service.url")))
                .addSecurityItem(securityRequirement)
                .components(components);
    }

}
