package com.huochai.domain;


import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.util.Date;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "task",
        indexes = {
                @Index(name = "idx_plan_id", columnList = "plan_id"),
                @Index(name = "idx_done", columnList = "done"),
                @Index(name = "idx_deadline", columnList = "deadline")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "plan_id", nullable = false, length = 36)
    private String planId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "how_to_learn", columnDefinition = "TEXT")
    private String howToLearn;

    @Column(name = "estimated_hours", nullable = false)
    private Integer estimatedHours;

    @Column(name = "done", nullable = false)
    private Boolean done = false;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "deadline")
    private Date deadline;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "task_deliverables",
            joinColumns = @JoinColumn(name = "task_id"))
    @Column(name = "deliverable", length = 200)
    private List<String> deliverables;

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

    /**
     * 标记任务完成
     */
    public void markAsDone() {
        this.done = true;
        this.completedAt = new Date();
    }
}