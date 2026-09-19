package com.resume.analyzer.repository;

import com.resume.analyzer.model.InterviewSession;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface InterviewRepository extends MongoRepository<InterviewSession, String> {

    List<InterviewSession> findByUserId(String userId);

    Optional<InterviewSession> findByIdAndUserId(String id, String userId);
}