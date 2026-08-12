package com.lifesync.api.goal.repository;

import com.lifesync.api.goal.entity.GoalProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GoalProgressRepository extends JpaRepository<GoalProgress, UUID> {

    /**
     * A checagem de dono acontece uma camada acima, em GoalService: ele
     * primeiro busca a Goal por findByIdAndUserId (garantindo posse) e so
     * entao usa o goalId, ja validado, aqui.
     */
    List<GoalProgress> findByGoalIdOrderByRecordedAtDesc(UUID goalId);

}
