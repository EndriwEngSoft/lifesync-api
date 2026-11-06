package com.lifesync.api.ai.client;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class FakeLlmClientTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final FakeLlmClient fake = new FakeLlmClient();

    private JsonNode plan(String contextJson) {
        return mapper.readTree(fake.generatePlan(new LlmRequest("sys", contextJson, null)).content());
    }

    @Test
    void referencesTheFirstItemOfEachTypeThatExistsInTheContext() {
        JsonNode plan = plan("{\"tasks\":[{\"shortIndex\":\"T1\"},{\"shortIndex\":\"T2\"}],"
                + "\"habits\":[{\"shortIndex\":\"H1\"}],\"goals\":[{\"shortIndex\":\"G1\"}]}");

        assertThat(plan.at("/priorities").size()).isEqualTo(3);
        assertThat(plan.at("/priorities/0/referenceId").asString()).isEqualTo("T1");
        assertThat(plan.at("/priorities/1/referenceId").asString()).isEqualTo("H1");
        assertThat(plan.at("/priorities/2/referenceId").asString()).isEqualTo("G1");
    }

    @Test
    void neverInventsAnIndexThatIsNotInTheContext() {
        JsonNode plan = plan("{\"tasks\":[],\"habits\":[{\"shortIndex\":\"H1\"}],\"goals\":[]}");

        assertThat(plan.at("/priorities").size()).isEqualTo(1);
        assertThat(plan.at("/priorities/0/type").asString()).isEqualTo("HABIT");
    }

    @Test
    void emptyOrNullContext_StillReturnsValidJson() {
        assertThat(plan("{}").at("/priorities").size()).isZero();
        assertThat(plan(null).at("/summary").asString()).isNotBlank();
    }
}
