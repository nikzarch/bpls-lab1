package com.example.labpay.camunda.state;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProcessStateRepository extends JpaRepository<ProcessState, Long> {
    Optional<ProcessState> findByProcessInstanceId(String processInstanceId);

    List<ProcessState> findByStatus(ProcessStateStatus status);
}
