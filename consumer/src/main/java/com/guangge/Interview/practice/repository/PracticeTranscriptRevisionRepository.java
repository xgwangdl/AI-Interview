package com.guangge.Interview.practice.repository;

import com.guangge.Interview.practice.entity.PracticeTranscriptRevisionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PracticeTranscriptRevisionRepository
        extends JpaRepository<PracticeTranscriptRevisionEntity, UUID> {

    long countByAnswerId(UUID answerId);

    List<PracticeTranscriptRevisionEntity> findAllByAnswerIdOrderByEditedAtDesc(UUID answerId);
}
