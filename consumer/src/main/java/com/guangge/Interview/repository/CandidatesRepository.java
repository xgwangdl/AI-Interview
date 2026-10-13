package com.guangge.Interview.repository;

import com.guangge.Interview.data.Candidates;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CandidatesRepository extends JpaRepository<Candidates, Long> {

    Optional<Candidates> findFirstByName(String name);

    boolean existsByName(String name);
}
