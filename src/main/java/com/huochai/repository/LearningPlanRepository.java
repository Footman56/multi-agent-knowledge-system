package com.huochai.repository;


import com.huochai.domain.LearningPlan;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface LearningPlanRepository extends JpaRepository<LearningPlan, String> {

    /**
     * 根据用户 ID 查询所有学习计划（按周升序）
     */
    List<LearningPlan> findByUserIdOrderByWeekAsc(String userId);

    /**
     * 根据用户 ID 和状态查询
     */
    List<LearningPlan> findByUserIdAndStatus(String userId, LearningPlan.PlanStatus status);

    /**
     * 根据用户 ID 和周数查询
     */
    Optional<LearningPlan> findByUserIdAndWeek(String userId, Integer week);

    /**
     * 查询用户的最大已完成周数
     */
    @Query("SELECT MAX(lp.week) FROM LearningPlan lp WHERE lp.userId = :userId AND lp.status = 'COMPLETED'")
    Optional<Integer> findMaxWeekByUserId(@Param("userId") String userId);

    /**
     * 查询用户当前进行中的计划（状态为 ACTIVE）
     */
    @Query("SELECT lp FROM LearningPlan lp WHERE lp.userId = :userId AND lp.status = 'ACTIVE' ORDER BY lp.week ASC")
    List<LearningPlan> findActivePlansByUserId(@Param("userId") String userId);

    /**
     * 统计用户已完成的任务数（跨所有计划）
     */
    @Query("SELECT COUNT(t) FROM Task t WHERE t.planId IN " +
            "(SELECT lp.id FROM LearningPlan lp WHERE lp.userId = :userId) AND t.done = true")
    long countCompletedTasksByUserId(@Param("userId") String userId);

    /**
     * 统计用户的总任务数
     */
    @Query("SELECT COUNT(t) FROM Task t WHERE t.planId IN " +
            "(SELECT lp.id FROM LearningPlan lp WHERE lp.userId = :userId)")
    long countTotalTasksByUserId(@Param("userId") String userId);

    /**
     * 更新计划状态
     */
    @Modifying
    @Transactional
    @Query("UPDATE LearningPlan lp SET lp.status = :status, lp.completedAt = :completedAt WHERE lp.id = :id")
    int updatePlanStatus(@Param("id") String id,
                         @Param("status") LearningPlan.PlanStatus status,
                         @Param("completedAt") java.util.Date completedAt);

    /**
     * 批量更新状态
     */
    @Modifying
    @Transactional
    @Query("UPDATE LearningPlan lp SET lp.status = :status WHERE lp.userId = :userId AND lp.week < :week")
    int updateStatusForWeeksBefore(@Param("userId") String userId,
                                   @Param("week") Integer week,
                                   @Param("status") LearningPlan.PlanStatus status);
}