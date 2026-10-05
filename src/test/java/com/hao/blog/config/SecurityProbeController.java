package com.hao.blog.config;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@TestComponent
@RestController
public class SecurityProbeController {

    @GetMapping("/posts")
    String getPosts() {
        return "ok";
    }

    @PostMapping("/posts")
    String createPost() {
        return "created";
    }
}