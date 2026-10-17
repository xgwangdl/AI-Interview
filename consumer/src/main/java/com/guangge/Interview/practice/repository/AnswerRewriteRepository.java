package com.guangge.Interview.practice.repository;

import com.guangge.Interview.practice.entity.AnswerRewriteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AnswerRewriteRepository extends JpaRepository<AnswerRewriteEntity, UUID> {
    List<AnswerRewriteEntity> findAllByAnswerIdOrderByCreatedAtDesc(UUID answerId);
}
