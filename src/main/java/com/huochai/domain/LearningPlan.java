package com.huochai.domain;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "learning_plan",
        indexes = {
                @Index(name = "idx_user_id", columnList = "userId"),
                @Index(name = "idx_week", columnList = "week"),
                @Index(name = "idx_status", columnList = "status")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "week", nullable = false)
    private Integer week;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "learning_plan_topics",
            joinColumns = @JoinColumn(name = "plan_id"))
    @Column(name = "topic", length = 100)
    private List<String> topics = new ArrayList<>();

    @Column(name = "rollover", nullable = false)
    private Boolean rollover = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PlanStatus status = PlanStatus.ACTIVE;

    @CreationTimestamp
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", updatable = false)
    private Date createdAt;

    @UpdateTimestamp
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_at")
    private Date updatedAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "completed_at")
    private Date completedAt;

    // 一对多关联到 Task（可选，也可以单独维护）
    @OneToMany(mappedBy = "planId", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Task> tasks = new ArrayList<>();

    /**
     * 计划状态枚举
     */
    public enum PlanStatus {
        ACTIVE,      // 进行中
        COMPLETED,   // 已完成
        PAUSED,      // 暂停
        CANCELLED    // 取消
    }
}