package com.mindbridge.core.controller;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;

import org.springframework.beans.factory.annotation.Autowired;

import com.mindbridge.core.seeder.DataSeeder;

@RestController
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private DataSeeder dataSeeder;

    @PostMapping("/seed")
    public String seed() {
        dataSeeder.seed();
        return "Demo data seeded successfully";
    }
}