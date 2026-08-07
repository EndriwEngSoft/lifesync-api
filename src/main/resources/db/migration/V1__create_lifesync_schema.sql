CREATE TABLE tb_habit_history
(
    id            UUID    NOT NULL,
    habit_id      UUID    NOT NULL,
    check_in_date date    NOT NULL,
    completed     BOOLEAN NOT NULL,
    CONSTRAINT pk_tb_habit_history PRIMARY KEY (id)
);

CREATE TABLE tb_habits
(
    id                UUID         NOT NULL,
    created_at        TIMESTAMP(6) WITHOUT TIME ZONE,
    updated_at        TIMESTAMP(6) WITHOUT TIME ZONE,
    name              VARCHAR(150) NOT NULL,
    description       VARCHAR(2000),
    frequency         VARCHAR(255) NOT NULL,
    target_per_period INTEGER      NOT NULL,
    current_streak    INTEGER      NOT NULL,
    longest_streak    INTEGER      NOT NULL,
    active            BOOLEAN      NOT NULL,
    user_id           UUID         NOT NULL,
    CONSTRAINT pk_tb_habits PRIMARY KEY (id)
);

CREATE TABLE tb_sub_tasks
(
    id        UUID         NOT NULL,
    title     VARCHAR(255) NOT NULL,
    completed BOOLEAN      NOT NULL,
    task_id   UUID         NOT NULL,
    CONSTRAINT pk_tb_sub_tasks PRIMARY KEY (id)
);

CREATE TABLE tb_tasks
(
    id           UUID         NOT NULL,
    created_at   TIMESTAMP(6) WITHOUT TIME ZONE,
    updated_at   TIMESTAMP(6) WITHOUT TIME ZONE,
    title        VARCHAR(150) NOT NULL,
    description  VARCHAR(2000),
    priority     VARCHAR(255) NOT NULL,
    status       VARCHAR(255) NOT NULL,
    due_date     date,
    completed_at TIMESTAMP(6) WITHOUT TIME ZONE,
    user_id      UUID         NOT NULL,
    CONSTRAINT pk_tb_tasks PRIMARY KEY (id)
);

CREATE TABLE tb_users
(
    id            UUID                                     NOT NULL,
    created_at    TIMESTAMP(6) WITHOUT TIME ZONE,
    updated_at    TIMESTAMP(6) WITHOUT TIME ZONE,
    name          VARCHAR(255)                             NOT NULL,
    username      VARCHAR(255)                             NOT NULL,
    email         VARCHAR(255)                             NOT NULL,
    password_hash VARCHAR(255)                             NOT NULL,
    role          VARCHAR(255)                             NOT NULL,
    active        BOOLEAN                                  NOT NULL,
    timezone      VARCHAR(255) DEFAULT 'America/Sao_Paulo' NOT NULL,
    CONSTRAINT pk_tb_users PRIMARY KEY (id)
);

ALTER TABLE tb_habit_history
    ADD CONSTRAINT uc_tb_habit_history_habit_id_check_in_date UNIQUE (habit_id, check_in_date);

ALTER TABLE tb_users
    ADD CONSTRAINT uc_tb_users_email UNIQUE (email);

ALTER TABLE tb_users
    ADD CONSTRAINT uc_tb_users_username UNIQUE (username);

ALTER TABLE tb_habits
    ADD CONSTRAINT FK_TB_HABITS_ON_USER FOREIGN KEY (user_id) REFERENCES tb_users (id);

ALTER TABLE tb_habit_history
    ADD CONSTRAINT FK_TB_HABIT_HISTORY_ON_HABIT FOREIGN KEY (habit_id) REFERENCES tb_habits (id);

ALTER TABLE tb_sub_tasks
    ADD CONSTRAINT FK_TB_SUB_TASKS_ON_TASK FOREIGN KEY (task_id) REFERENCES tb_tasks (id);

ALTER TABLE tb_tasks
    ADD CONSTRAINT FK_TB_TASKS_ON_USER FOREIGN KEY (user_id) REFERENCES tb_users (id);