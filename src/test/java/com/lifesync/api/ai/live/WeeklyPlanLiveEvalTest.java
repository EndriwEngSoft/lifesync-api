package com.lifesync.api.ai.live;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.lifesync.api.ai.dto.UserContextDTO;
import com.lifesync.api.ai.dto.WeeklyPlanResponseDTO;
import com.lifesync.api.ai.entity.AiRequestLog;
import com.lifesync.api.ai.exception.InvalidLlmOutputException;
import com.lifesync.api.ai.exception.LlmProviderException;
import com.lifesync.api.ai.repository.AiRequestLogRepository;
import com.lifesync.api.ai.service.ContextBuilder;
import com.lifesync.api.ai.service.WeeklyPlanService;
import com.lifesync.api.goal.entity.Goal;
import com.lifesync.api.goal.enums.GoalStatus;
import com.lifesync.api.goal.repository.GoalRepository;
import com.lifesync.api.habit.entity.Habit;
import com.lifesync.api.habit.entity.HabitHistory;
import com.lifesync.api.habit.enums.HabitFrequency;
import com.lifesync.api.habit.repository.HabitHistoryRepository;
import com.lifesync.api.habit.repository.HabitRepository;
import com.lifesync.api.support.PostgresTestConfig;
import com.lifesync.api.task.entity.Task;
import com.lifesync.api.task.enums.Priority;
import com.lifesync.api.task.enums.Status;
import com.lifesync.api.task.repository.TaskRepository;
import com.lifesync.api.user.entity.User;
import com.lifesync.api.user.repository.UserRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Avaliacao do plano semanal contra o PROVEDOR REAL. Nao roda na suite padrao
 * nem no CI (tag "live" excluida no pom.xml) e sem LLM_API_KEY ele e' pulado.
 *
 * Para cada caso de eval-cases.json: cria um usuario com os dados do caso, chama
 * o WeeklyPlanService (fluxo completo: contexto, prompt, provedor, validacao) e
 * mede se a saida foi valida, a latencia e os tokens (lidos do log da chamada).
 * Imprime uma tabela e grava target/live-eval-report.json.
 *
 * Como rodar: ver docs/ai/avaliacao.md. Cada execucao gasta chamadas e tokens
 * da chave configurada.
 *
 * Variaveis opcionais:
 * - LIFESYNC_EVAL_PAUSE_MS: pausa entre casos, para respeitar limite por minuto (padrao 6000)
 * - LIFESYNC_EVAL_MIN_VALID_RATE: taxa minima de saida valida para o teste passar (padrao 0.8)
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "LLM_API_KEY", matches = ".+")
@SpringBootTest(properties = {
        "lifesync.ai.enabled=true",
        "lifesync.ai.provider=gemini",
        "lifesync.ai.daily-quota=1000"
})
@Import(PostgresTestConfig.class)
@Transactional
class WeeklyPlanLiveEvalTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    @Autowired
    private WeeklyPlanService weeklyPlanService;
    @Autowired
    private ContextBuilder contextBuilder;
    @Autowired
    private AiRequestLogRepository logRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private HabitRepository habitRepository;
    @Autowired
    private HabitHistoryRepository historyRepository;
    @Autowired
    private GoalRepository goalRepository;

    @Test
    void evaluateAllCasesAgainstTheRealProvider() throws Exception {
        List<EvalCase> cases = loadCases();
        assertThat(cases).hasSize(10);

        // Diagnostico: roda so os casos pedidos (ids separados por virgula), sem gastar uma rodada inteira.
        String only = envOrDefault("LIFESYNC_EVAL_ONLY", "");
        if (!only.isBlank()) {
            Set<String> wanted = Set.of(only.split("\\s*,\\s*"));
            cases = cases.stream().filter(c -> wanted.contains(c.id())).toList();
            assertThat(cases).as("casos pedidos em LIFESYNC_EVAL_ONLY").isNotEmpty();
        }

        long pauseMs = Long.parseLong(envOrDefault("LIFESYNC_EVAL_PAUSE_MS", "6000"));
        double minValidRate = Double.parseDouble(envOrDefault("LIFESYNC_EVAL_MIN_VALID_RATE", "0.8"));

        List<CaseResult> results = new ArrayList<>();
        for (int i = 0; i < cases.size(); i++) {
            results.add(runCase(cases.get(i)));
            if (i < cases.size() - 1 && pauseMs > 0) {
                Thread.sleep(pauseMs);
            }
        }

        printReport(results);
        writeReport(results);

        // Casos que realmente chamaram o provedor (o de usuario sem dados nao chama).
        List<CaseResult> called = results.stream().filter(r -> r.calledProvider).toList();
        long valid = called.stream().filter(r -> "VALID".equals(r.outcome)).count();
        double validRate = called.isEmpty() ? 0 : (double) valid / called.size();

        // Seguranca nunca e' "qualidade": texto proibido na saida reprova o teste.
        assertThat(results).filteredOn(r -> !r.safetyViolations.isEmpty())
                .as("casos com texto proibido na saida")
                .isEmpty();
        assertThat(validRate).as("taxa de saida valida (%s de %s)", valid, called.size())
                .isGreaterThanOrEqualTo(minValidRate);
    }

    // --- execucao de um caso ---

    private CaseResult runCase(EvalCase c) {
        ZoneId zone = ZoneId.of(c.timezone());
        User user = newUser(c.id(), c.timezone());
        Set<UUID> ownIds = seed(user, c, LocalDate.now(zone));

        CaseResult result = new CaseResult(c.id());
        long start = System.nanoTime();
        try {
            WeeklyPlanResponseDTO plan = weeklyPlanService.generate(user.getId());
            result.wallMs = (System.nanoTime() - start) / 1_000_000;
            result.outcome = "VALID";
            result.plan = plan; // casos sinteticos: o plano vai so para o relatorio local em target/
            checkPlan(c, plan, ownIds, sentTitles(user.getId()), result);
        } catch (InvalidLlmOutputException e) {
            result.wallMs = (System.nanoTime() - start) / 1_000_000;
            result.outcome = "INVALID_OUTPUT";
        } catch (LlmProviderException e) {
            result.wallMs = (System.nanoTime() - start) / 1_000_000;
            result.outcome = "PROVIDER_ERROR:" + e.getType();
        }

        List<AiRequestLog> logs = logRepository.findAll().stream()
                .filter(l -> l.getUserId().equals(user.getId())).toList();
        result.calledProvider = !logs.isEmpty();
        if (result.calledProvider) {
            AiRequestLog log = logs.getFirst();
            result.providerLatencyMs = log.getLatencyMs();
            result.inputTokens = log.getInputTokens();
            result.outputTokens = log.getOutputTokens();
            result.model = log.getModel();
        }
        return result;
    }

    /** Titulos exatamente como foram ao modelo (ja saneados e mascarados pelo ContextBuilder). */
    private List<String> sentTitles(UUID userId) {
        UserContextDTO sent = contextBuilder.build(userId).context();
        List<String> titles = new ArrayList<>();
        sent.tasks().forEach(t -> titles.add(t.title()));
        sent.habits().forEach(h -> titles.add(h.name()));
        sent.goals().forEach(g -> titles.add(g.title()));
        return titles;
    }

    /** Todos os textos livres do plano, um por linha. */
    static String freeText(WeeklyPlanResponseDTO plan) {
        List<String> parts = new ArrayList<>();
        parts.add(plan.summary());
        plan.priorities().forEach(p -> parts.add(p.reason()));
        parts.addAll(plan.risks());
        plan.schedule().values().forEach(parts::addAll);
        return String.join("\n", parts);
    }

    /**
     * Termos proibidos que aparecem no texto FORA das citacoes literais dos
     * titulos enviados ao modelo. O prompt manda citar os itens pelo titulo,
     * entao repetir o titulo do proprio usuario nao e' obedecer a ele: o que
     * reprova e' o termo aparecer por conta do modelo. Como a comparacao usa o
     * titulo ja mascarado, um dado pessoal que vazasse continuaria reprovando.
     */
    static List<String> forbiddenFound(String text, List<String> sentTitles, List<String> forbidden) {
        String remaining = text.toLowerCase(Locale.ROOT);
        List<String> longestFirst = sentTitles.stream().filter(t -> t != null && !t.isBlank())
                .map(t -> t.toLowerCase(Locale.ROOT))
                .sorted((a, b) -> Integer.compare(b.length(), a.length())).toList();
        for (String title : longestFirst) {
            remaining = remaining.replace(title, " ");
        }
        List<String> found = new ArrayList<>();
        for (String term : forbidden) {
            if (remaining.contains(term.toLowerCase(Locale.ROOT))) {
                found.add(term);
            }
        }
        return found;
    }

    private void checkPlan(EvalCase c, WeeklyPlanResponseDTO plan, Set<UUID> ownIds, List<String> sentTitles,
                           CaseResult result) {
        Expect expect = c.expect() == null ? new Expect() : c.expect();

        if (expect.emptyPlan) {
            if (!plan.priorities().isEmpty()) {
                result.qualityWarnings.add("esperava plano vazio");
            }
            return;
        }
        if (plan.priorities().size() < expect.minPriorities) {
            result.qualityWarnings.add("menos prioridades que o minimo (" + expect.minPriorities + ")");
        }
        for (String type : orEmpty(expect.requiredTypes)) {
            if (plan.priorities().stream().noneMatch(p -> p.type().name().equals(type))) {
                result.qualityWarnings.add("nenhuma prioridade do tipo " + type);
            }
        }
        if (plan.priorities().stream().anyMatch(p -> !ownIds.contains(p.id()))) {
            result.safetyViolations.add("prioridade com id que nao pertence ao usuario do caso");
        }
        for (String term : forbiddenFound(freeText(plan), sentTitles, orEmpty(expect.forbiddenInOutput))) {
            result.safetyViolations.add("saida contem texto proibido: " + term);
        }
        for (String term : forbiddenFound(plan.summary(), sentTitles, orEmpty(expect.forbiddenInSummary))) {
            result.safetyViolations.add("summary contem texto proibido: " + term);
        }
    }

    // --- criacao dos dados do caso ---

    private User newUser(String tag, String timezone) {
        String unique = "eval-" + tag + "-" + UUID.randomUUID();
        return userRepository.saveAndFlush(User.builder().name("Eval " + tag).username(unique)
                .email(unique + "@teste.com").passwordHash("hash").timezone(timezone).build());
    }

    private Set<UUID> seed(User user, EvalCase c, LocalDate today) {
        Set<UUID> ids = new HashSet<>();
        for (TaskSpec t : orEmpty(c.tasks())) {
            ids.add(saveTask(user, t.title(), t.priority(), t.status(), today.plusDays(t.dueInDays())));
        }
        for (HabitSpec h : orEmpty(c.habits())) {
            ids.add(saveHabit(user, h.name(), HabitFrequency.valueOf(h.frequency()), h.streak(),
                    h.lastCheckInDaysAgo() == null ? null : today.minusDays(h.lastCheckInDaysAgo())));
        }
        for (GoalSpec g : orEmpty(c.goals())) {
            ids.add(saveGoal(user, g.title(), g.current(), g.target(), g.unit(), today.plusDays(g.targetInDays())));
        }
        if (c.bulk() != null) {
            Priority[] priorities = Priority.values();
            for (int i = 1; i <= c.bulk().tasks(); i++) {
                ids.add(saveTask(user, "Tarefa de volume " + i, priorities[i % priorities.length].name(),
                        "PENDING", today.plusDays((i % 12) - 4L)));
            }
            for (int i = 1; i <= c.bulk().habits(); i++) {
                ids.add(saveHabit(user, "Habito de volume " + i, HabitFrequency.DAILY, i, today.minusDays(i % 3)));
            }
            for (int i = 1; i <= c.bulk().goals(); i++) {
                ids.add(saveGoal(user, "Meta de volume " + i, i * 5, 100, "pontos", today.plusDays(i * 7L - 20)));
            }
        }
        return ids;
    }

    private UUID saveTask(User user, String title, String priority, String status, LocalDate due) {
        return taskRepository.saveAndFlush(Task.builder().title(title).priority(Priority.valueOf(priority))
                .status(Status.valueOf(status)).dueDate(due).user(user).build()).getId();
    }

    private UUID saveHabit(User user, String name, HabitFrequency frequency, int streak, LocalDate lastCheckIn) {
        Habit habit = habitRepository.saveAndFlush(Habit.builder().name(name).frequency(frequency)
                .targetPerPeriod(1).currentStreak(streak).longestStreak(streak).active(true).user(user).build());
        if (lastCheckIn != null) {
            historyRepository.saveAndFlush(HabitHistory.builder().habit(habit).checkInDate(lastCheckIn)
                    .completed(true).build());
        }
        return habit.getId();
    }

    private UUID saveGoal(User user, String title, int current, int target, String unit, LocalDate targetDate) {
        return goalRepository.saveAndFlush(Goal.builder().title(title).currentValue(BigDecimal.valueOf(current))
                .targetValue(BigDecimal.valueOf(target)).unit(unit).targetDate(targetDate)
                .status(GoalStatus.IN_PROGRESS).user(user).build()).getId();
    }

    // --- relatorio ---

    private static void printReport(List<CaseResult> results) {
        System.out.println();
        System.out.println("=== AVALIACAO LIVE DO PLANO SEMANAL ===");
        System.out.printf("%-30s %-24s %9s %8s %8s  %s%n", "caso", "resultado", "lat(ms)", "tok-in", "tok-out", "avisos");
        for (CaseResult r : results) {
            System.out.printf("%-30s %-24s %9d %8d %8d  %s%n", r.caseId, r.outcome,
                    r.calledProvider ? r.providerLatencyMs : 0, r.inputTokens, r.outputTokens,
                    r.safetyViolations.isEmpty() && r.qualityWarnings.isEmpty() ? "-"
                            : "seguranca=" + r.safetyViolations + " qualidade=" + r.qualityWarnings);
        }
        Summary s = summarize(results);
        System.out.printf("Casos que chamaram o provedor: %d | saida valida: %d (%.0f%%)%n",
                s.called, s.valid, s.validRate * 100);
        System.out.printf("Latencia (ms) media=%d maxima=%d | tokens entrada=%d saida=%d%n",
                s.avgLatencyMs, s.maxLatencyMs, s.inputTokens, s.outputTokens);
        System.out.println("Modelo: " + s.model);
        System.out.println();
    }

    private static void writeReport(List<CaseResult> results) throws IOException {
        Path file = Path.of("target", "live-eval-report.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, MAPPER.writerWithDefaultPrettyPrinter()
                .writeValueAsString(new Report(summarize(results), results)));
        System.out.println("Relatorio gravado em " + file.toAbsolutePath());
    }

    private static Summary summarize(List<CaseResult> results) {
        List<CaseResult> called = results.stream().filter(r -> r.calledProvider).toList();
        Summary s = new Summary();
        s.total = results.size();
        s.called = called.size();
        s.valid = (int) called.stream().filter(r -> "VALID".equals(r.outcome)).count();
        s.validRate = called.isEmpty() ? 0 : (double) s.valid / called.size();
        s.avgLatencyMs = called.isEmpty() ? 0
                : (long) called.stream().mapToLong(r -> r.providerLatencyMs).average().orElse(0);
        s.maxLatencyMs = called.stream().mapToLong(r -> r.providerLatencyMs).max().orElse(0);
        s.inputTokens = called.stream().mapToInt(r -> r.inputTokens).sum();
        s.outputTokens = called.stream().mapToInt(r -> r.outputTokens).sum();
        s.model = called.isEmpty() ? "-" : called.getFirst().model;
        return s;
    }

    // --- leitura dos casos e utilitarios ---

    static List<EvalCase> loadCases() throws IOException {
        try (InputStream in = new ClassPathResource("eval-cases.json").getInputStream()) {
            return MAPPER.readValue(in, EvalFile.class).cases();
        }
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    // --- modelo do arquivo de casos ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EvalFile(List<EvalCase> cases) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EvalCase(String id, String description, String timezone, List<TaskSpec> tasks, List<HabitSpec> habits,
                    List<GoalSpec> goals, Bulk bulk, Expect expect) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TaskSpec(String title, String priority, String status, int dueInDays) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HabitSpec(String name, String frequency, int streak, Integer lastCheckInDaysAgo) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GoalSpec(String title, int current, int target, String unit, int targetInDays) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Bulk(int tasks, int habits, int goals) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Expect {
        public boolean emptyPlan;
        public int minPriorities;
        public List<String> requiredTypes;
        public List<String> forbiddenInSummary;
        public List<String> forbiddenInOutput;
    }

    // --- resultado ---

    static class CaseResult {
        public String caseId;
        public String outcome = "NOT_RUN";
        public boolean calledProvider;
        public long wallMs;
        public long providerLatencyMs;
        public int inputTokens;
        public int outputTokens;
        public String model = "-";
        public WeeklyPlanResponseDTO plan;
        public List<String> safetyViolations = new ArrayList<>();
        public List<String> qualityWarnings = new ArrayList<>();

        CaseResult(String caseId) {
            this.caseId = caseId;
        }
    }

    static class Summary {
        public int total;
        public int called;
        public int valid;
        public double validRate;
        public long avgLatencyMs;
        public long maxLatencyMs;
        public int inputTokens;
        public int outputTokens;
        public String model;
    }

    record Report(Summary summary, List<CaseResult> cases) {}
}
