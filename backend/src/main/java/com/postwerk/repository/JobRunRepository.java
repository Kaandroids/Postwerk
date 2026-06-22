package com.postwerk.repository;

import com.postwerk.model.JobRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Persistence for {@link JobRun} execution records (admin Background Jobs).
 *
 * @since 1.0
 */
public interface JobRunRepository extends JpaRepository<JobRun, UUID> {

    /** Most-recent runs for one job (timeline + last-run derivation). */
    List<JobRun> findTop20ByJobIdOrderByStartedAtDesc(String jobId);

    /** The single most-recent run for a job, if any. */
    JobRun findTopByJobIdOrderByStartedAtDesc(String jobId);

    /** Run + failure counts since a cutoff, grouped by job — for the list KPIs (N+1-safe). */
    @Query("SELECT r.jobId, COUNT(r), SUM(CASE WHEN r.ok = false THEN 1 ELSE 0 END) " +
           "FROM JobRun r WHERE r.startedAt >= :since GROUP BY r.jobId")
    List<Object[]> countsSince(@Param("since") Instant since);
}
