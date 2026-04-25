package com.mindbridge.core.seeder;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.mindbridge.core.entity.Session;
import com.mindbridge.core.entity.User;
import com.mindbridge.core.repository.SessionRepository;
import com.mindbridge.core.repository.UserRepository;

@Component
public class DataSeeder {

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private SessionRepository sessionRepo;

    public void seed() {

        // Clear existing data
        sessionRepo.deleteAll();
        userRepo.deleteAll();

        // Create users
        User priya = new User("priya@student.com", "password123", "Priya Sharma");
User raj = new User("raj@company.com", "password123", "Raj Mehta");

        userRepo.saveAll(List.of(priya, raj));

        // Create sessions
        for (User user : List.of(priya, raj)) {

            for (int i = 1; i <= 3; i++) {

                Session session = new Session();
                session.setUser(user);
                session.setTitle("Session " + i);
                session.setMoodScore(5 + i);
                session.setStartedAt(Instant.now());

                sessionRepo.save(session);
            }
        }
    }
}