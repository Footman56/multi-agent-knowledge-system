-- 学习计划表
CREATE TABLE learning_plan (
                               id VARCHAR(36) PRIMARY KEY,
                               user_id VARCHAR(64) NOT NULL,
                               week INT NOT NULL,
                               rollover BOOLEAN DEFAULT TRUE,
                               status VARCHAR(20) DEFAULT 'ACTIVE',
                               created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                               updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                               completed_at TIMESTAMP NULL,
                               INDEX idx_user_id (user_id),
                               INDEX idx_week (week),
                               INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 计划主题表（一对多）
CREATE TABLE learning_plan_topics (
                                      plan_id VARCHAR(36) NOT NULL,
                                      topic VARCHAR(100) NOT NULL,
                                      PRIMARY KEY (plan_id, topic),
                                      FOREIGN KEY (plan_id) REFERENCES learning_plan(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 任务表
CREATE TABLE task (
                      id VARCHAR(36) PRIMARY KEY,
                      plan_id VARCHAR(36) NOT NULL,
                      name VARCHAR(200) NOT NULL,
                      how_to_learn TEXT,
                      estimated_hours INT NOT NULL,
                      done BOOLEAN DEFAULT FALSE,
                      deadline TIMESTAMP NULL,
                      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                      updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                      completed_at TIMESTAMP NULL,
                      INDEX idx_plan_id (plan_id),
                      INDEX idx_done (done),
                      INDEX idx_deadline (deadline),
                      FOREIGN KEY (plan_id) REFERENCES learning_plan(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 任务产出物表
CREATE TABLE task_deliverables (
                                   task_id VARCHAR(36) NOT NULL,
                                   deliverable VARCHAR(200) NOT NULL,
                                   PRIMARY KEY (task_id, deliverable),
                                   FOREIGN KEY (task_id) REFERENCES task(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;