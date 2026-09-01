package com.resume.analyzer.repository;

import com.resume.analyzer.model.Resume;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ResumeRepository extends MongoRepository<Resume, String> {
    List<Resume> findByUserId(String userId);
    Optional<Resume> findByIdAndUserId(String id, String userId);
}
