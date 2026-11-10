package com.lifesync.api.ai.config;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Impede, na subida, que o curinga da lista de permitidos seja usado com o
 * provedor real em producao.
 *
 * O curinga "*" existe para o provider fake local e para a avaliacao com
 * dados sinteticos, que criam usuarios na hora. Em producao (perfil "prod")
 * com o provider gemini ele abriria o modulo a todos os usuarios e enviaria
 * dados reais ao provedor, o que a decisao de uso proibe (ADR 0002). A
 * checagem vale mesmo com o modulo desligado: a combinacao nunca e' valida em
 * producao, e desligado hoje pode virar ligado amanha por uma variavel.
 *
 * Fica fora do AiProperties porque depende do perfil ativo, que o record de
 * configuracao nao conhece.
 */
@Component
public class AiAllowListGuard {

    static final String PRODUCTION_PROFILE = "prod";

    public AiAllowListGuard(AiProperties properties, Environment environment) {
        boolean production = environment.acceptsProfiles(Profiles.of(PRODUCTION_PROFILE));
        boolean realProvider = AiProperties.PROVIDER_GEMINI.equals(properties.provider());
        if (production && realProvider && properties.allowedUsers().contains(AiProperties.ANY_USER)) {
            throw new IllegalStateException(
                    "LIFESYNC_AI_ALLOWED_USERS (lifesync.ai.allowed-users) must not contain the wildcard '*' "
                            + "when lifesync.ai.provider=gemini in the 'prod' profile: "
                            + "list the allowed user ids (UUID) separated by commas, or leave it empty.");
        }
    }
}
