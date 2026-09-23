package com.hao.blog;

import com.hao.blog.testsupport.PostgresTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class BlogApiApplicationTests {

    @Test
    void contextLoads() {
    }
}
