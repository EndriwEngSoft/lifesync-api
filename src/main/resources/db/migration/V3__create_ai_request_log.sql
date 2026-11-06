-- Log de chamadas ao modulo de IA. Guarda so metadados (quem, quando, qual
-- recurso, modelo, tokens, tempo e resultado): nunca o texto do prompt nem
-- da resposta, que contem dados pessoais. Tambem e a base da cota diaria
-- por usuario.
CREATE TABLE tb_ai_request_logs
(
    id             UUID         NOT NULL,
    created_at     TIMESTAMP(6) WITHOUT TIME ZONE,
    updated_at     TIMESTAMP(6) WITHOUT TIME ZONE,
    user_id        UUID         NOT NULL,
    feature        VARCHAR(50)  NOT NULL,
    prompt_version VARCHAR(20)  NOT NULL,
    provider       VARCHAR(50)  NOT NULL,
    model          VARCHAR(100) NOT NULL,
    input_tokens   INTEGER      NOT NULL,
    output_tokens  INTEGER      NOT NULL,
    latency_ms     BIGINT       NOT NULL,
    status         VARCHAR(30)  NOT NULL,
    CONSTRAINT pk_tb_ai_request_logs PRIMARY KEY (id)
);

-- ON DELETE CASCADE: se a conta for apagada de verdade (direito de
-- eliminacao da LGPD), o historico de uso dela vai junto.
ALTER TABLE tb_ai_request_logs
    ADD CONSTRAINT FK_TB_AI_REQUEST_LOGS_ON_USER FOREIGN KEY (user_id) REFERENCES tb_users (id) ON DELETE CASCADE;

-- Consulta da cota: por usuario, recurso e janela de created_at.
CREATE INDEX idx_tb_ai_request_logs_user_id_created_at ON tb_ai_request_logs (user_id, created_at);
