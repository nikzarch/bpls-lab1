package com.example.labpay.repository;

import com.example.labpay.domain.card.CardBindingSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CardBindingSessionRepository extends JpaRepository<CardBindingSession, Long> {
    Optional<CardBindingSession> findBySessionId(String sessionId);
}