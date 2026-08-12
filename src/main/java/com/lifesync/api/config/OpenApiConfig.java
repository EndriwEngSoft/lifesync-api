package com.lifesync.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadados do Swagger UI/OpenAPI. So registra o esquema de seguranca
 * (Bearer JWT) aqui - quem efetivamente exige o token em cada endpoint e
 * o {@code @SecurityRequirement} nos controllers protegidos, nao uma
 * aplicacao global: os endpoints de auth (register/login/refresh) sao
 * publicos, e marcar seguranca global faria o Swagger sugerir Bearer
 * neles tambem, o que seria enganoso.
 *
 * Escolha deliberada: registrar o esquema via bean/modelo de objeto
 * ({@code SecurityScheme}, {@code Info}, {@code Contact}), nao via
 * anotacoes equivalentes ({@code @SecurityScheme}, {@code @OpenAPIDefinition},
 * {@code @Info}, {@code @Contact}) - as duas formas fazem a mesma coisa,
 * usar as duas ao mesmo tempo so duplicaria configuracao.
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("LifeSync API")
                        .description("API REST de organizacao e produtividade pessoal - tarefas, "
                                + "habitos com calculo de streak, metas com progresso mensuravel e, "
                                + "nas proximas fases, estudos e financas.")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Endriw Colvara Bento")
                                .url("https://github.com/EndriwEngSoft")))
                .externalDocs(new ExternalDocumentation()
                        .description("Repositorio no GitHub")
                        .url("https://github.com/EndriwEngSoft/lifesync-api"))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME_NAME, new SecurityScheme()
                                .name(BEARER_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Cole aqui so o token (sem o prefixo \"Bearer \") - "
                                        + "obtido via /api/auth/login ou /api/auth/register.")));
    }
}
