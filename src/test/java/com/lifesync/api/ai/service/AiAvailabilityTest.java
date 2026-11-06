package com.lifesync.api.ai.service;

import com.lifesync.api.ai.client.LlmClient;
import com.lifesync.api.ai.config.AiProperties;
import com.lifesync.api.ai.exception.AiDisabledException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiAvailabilityTest {

    private AiProperties props(boolean enabled) {
        return new AiProperties(enabled, "fake", "", "http://localhost", "m", Duration.ofSeconds(5),
                Duration.ofSeconds(5), 100, 5, 3, Duration.ZERO);
    }

    @SuppressWarnings("unchecked")
    private final ObjectProvider<LlmClient> provider = mock(ObjectProvider.class);

    @Test
    void disabled_ThrowsWithoutTouchingTheProvider() {
        AiAvailability availability = new AiAvailability(props(false), provider);

        assertThatThrownBy(availability::requireClient).isInstanceOf(AiDisabledException.class);
        verifyNoInteractions(provider);
    }

    @Test
    void enabledWithoutClient_Throws() {
        when(provider.getIfAvailable()).thenReturn(null);
        AiAvailability availability = new AiAvailability(props(true), provider);

        assertThatThrownBy(availability::requireClient).isInstanceOf(AiDisabledException.class);
    }

    @Test
    void enabledWithClient_ReturnsIt() {
        LlmClient client = mock(LlmClient.class);
        when(provider.getIfAvailable()).thenReturn(client);
        AiAvailability availability = new AiAvailability(props(true), provider);

        assertThat(availability.requireClient()).isSameAs(client);
    }
}
