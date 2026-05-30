package com.example.labpay.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Component
@RequiredArgsConstructor
public class BatchJobRunner {

    private final JobLauncher jobLauncher;
    private final JobExplorer jobExplorer;
    private final Map<String, Job> jobs;

    private final ConcurrentHashMap<String, ReentrantLock> jobLocks = new ConcurrentHashMap<>();

    public JobExecution run(String jobName) throws Exception {
        return run(jobName, "manual");
    }

    public JobExecution run(String jobName, String trigger) throws Exception {
        Job job = resolve(jobName);

        ReentrantLock lock = jobLocks.computeIfAbsent(job.getName(), k -> new ReentrantLock());

        if (!lock.tryLock()) {
            log.info("Job {} is already running locally, skipping trigger={}", job.getName(), trigger);
            return latestRunning(job.getName());
        }

        try {
            Set<JobExecution> running = jobExplorer.findRunningJobExecutions(job.getName());
            if (!running.isEmpty()) {
                JobExecution existing = running.iterator().next();
                log.info("Job {} already running in repository, executionId={}", job.getName(), existing.getId());
                return existing;
            }

            JobParameters params = new JobParametersBuilder()
                    .addLong("run.id", System.currentTimeMillis())
                    .addString("trigger", trigger)
                    .addString("now", Instant.now().toString())
                    .toJobParameters();

            log.info("Launching batch job {} trigger={}", job.getName(), trigger);
            return jobLauncher.run(job, params);
        } finally {
            lock.unlock();
        }
    }

    public Set<String> jobNames() {
        return new TreeSet<>(jobs.keySet());
    }

    private JobExecution latestRunning(String jobName) {
        Set<JobExecution> running = jobExplorer.findRunningJobExecutions(jobName);
        return running.isEmpty() ? null : running.iterator().next();
    }

    private Job resolve(String jobName) {
        Job job = jobs.get(jobName);
        if (job == null) {
            throw new IllegalArgumentException("Unknown batch job: " + jobName + ". Available: " + jobNames());
        }
        return job;
    }
}