package com.aicode.demo.infrastructure.persistence.repository;

import com.aicode.demo.infrastructure.persistence.entity.TokenRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Token 记录仓储。
 */
public interface TokenRecordRepository extends JpaRepository<TokenRecordEntity, Long> {

    List<TokenRecordEntity> findBySessionId(String sessionId);
}
