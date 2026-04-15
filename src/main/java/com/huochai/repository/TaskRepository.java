package com.huochai.repository;

import com.huochai.domain.Task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, String> {

    /**
     * 根据计划 ID 查询所有任务
     */
    List<Task> findByPlanIdOrderByCreatedAtAsc(String planId);

    /**
     * 根据计划 ID 和完成状态查询
     */
    List<Task> findByPlanIdAndDone(String planId, Boolean done);

    /**
     * 查询计划中未完成的任务
     */
    @Query("SELECT t FROM Task t WHERE t.planId = :planId AND t.done = false ORDER BY t.deadline ASC")
    List<Task> findUnfinishedTasksByPlanId(@Param("planId") String planId);

    /**
     * 查询所有过期的未完成任务
     */
    @Query("SELECT t FROM Task t WHERE t.done = false AND t.deadline < CURRENT_TIMESTAMP")
    List<Task> findOverdueUnfinishedTasks();

    /**
     * 查询用户所有未完成任务（跨计划）
     */
    @Query("SELECT t FROM Task t WHERE t.planId IN " +
            "(SELECT lp.id FROM LearningPlan lp WHERE lp.userId = :userId) " +
            "AND t.done = false ORDER BY t.deadline ASC")
    List<Task> findAllUnfinishedTasksByUserId(@Param("userId") String userId);

    /**
     * 统计计划中已完成任务数
     */
    long countByPlanIdAndDone(String planId, Boolean done);

    /**
     * 批量更新任务完成状态
     */
    @Modifying
    @Transactional
    @Query("UPDATE Task t SET t.done = :done, t.completedAt = :completedAt WHERE t.id IN :ids")
    int batchUpdateDoneStatus(@Param("ids") List<String> ids,
                              @Param("done") Boolean done,
                              @Param("completedAt") java.util.Date completedAt);

    /**
     * 删除计划下的所有任务
     */
    @Modifying
    @Transactional
    void deleteByPlanId(String planId);
}