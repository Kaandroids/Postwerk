package com.postwerk;

import com.postwerk.config.TestContainersConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
@Tag("integration")
class PostwerkApplicationTests {

    @Test
    void contextLoads() {
    }
}
