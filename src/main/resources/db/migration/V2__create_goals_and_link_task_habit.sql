CREATE TABLE tb_goals
(
    id            UUID           NOT NULL,
    created_at    TIMESTAMP(6) WITHOUT TIME ZONE,
    updated_at    TIMESTAMP(6) WITHOUT TIME ZONE,
    title         VARCHAR(150)   NOT NULL,
    description   VARCHAR(2000),
    current_value NUMERIC(19, 2) NOT NULL,
    target_value  NUMERIC(19, 2) NOT NULL,
    unit          VARCHAR(30)    NOT NULL,
    target_date   date,
    status        VARCHAR(255)   NOT NULL,
    completed_at  TIMESTAMP(6) WITHOUT TIME ZONE,
    user_id       UUID           NOT NULL,
    CONSTRAINT pk_tb_goals PRIMARY KEY (id)
);

CREATE TABLE tb_goal_progress
(
    id          UUID           NOT NULL,
    goal_id     UUID           NOT NULL,
    value       NUMERIC(19, 2) NOT NULL,
    note        VARCHAR(500),
    recorded_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_tb_goal_progress PRIMARY KEY (id)
);

ALTER TABLE tb_goals
    ADD CONSTRAINT FK_TB_GOALS_ON_USER FOREIGN KEY (user_id) REFERENCES tb_users (id);

ALTER TABLE tb_goal_progress
    ADD CONSTRAINT FK_TB_GOAL_PROGRESS_ON_GOAL FOREIGN KEY (goal_id) REFERENCES tb_goals (id);

ALTER TABLE tb_tasks
    ADD goal_id UUID;

ALTER TABLE tb_tasks
    ADD CONSTRAINT FK_TB_TASKS_ON_GOAL FOREIGN KEY (goal_id) REFERENCES tb_goals (id);

ALTER TABLE tb_habits
    ADD goal_id UUID;

ALTER TABLE tb_habits
    ADD CONSTRAINT FK_TB_HABITS_ON_GOAL FOREIGN KEY (goal_id) REFERENCES tb_goals (id);
