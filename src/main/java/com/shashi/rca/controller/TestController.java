package com.shashi.rca.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @GetMapping("/")
    public String checkAlive() {
        return "RCA Platform Engine Core is Running.";
    }
}