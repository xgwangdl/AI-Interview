package com.guangge.Interview.repository;

import com.guangge.Interview.data.Candidates;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidatesRepository extends JpaRepository<Candidates, Long> {
    Candidates findByNameAndCode(String name, String code);
    Candidates findByName(String name);
}
