package com.example.lowcode;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LowCodeApplicationTest {
    @Test
    void applicationEntryPointExists() {
        assertThat(LowCodeApplication.class).isNotNull();
    }
}
