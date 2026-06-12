package com.example.labpay.camunda.state;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "camunda_process_states",
        indexes = {
                @Index(name = "idx_camunda_process_state_status", columnList = "status"),
                @Index(name = "idx_camunda_process_state_definition", columnList = "processDefinitionId")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_camunda_process_state_pi", columnNames = "processInstanceId")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 128)
    private String processInstanceId;

    @Column(nullable = false, length = 255)
    private String processDefinitionId;

    @Column(length = 255)
    private String businessKey;

    @Column(length = 255)
    private String currentActivityId;

    @Column(length = 255)
    private String currentActivityName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProcessStateStatus status;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String variablesJson;

    @Column(length = 255)
    private String ownerUsername;

    @Column(nullable = false)
    private Instant startedAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant endedAt;

    @Lob
    private String errorMessage;
}
