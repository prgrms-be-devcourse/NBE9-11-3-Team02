package com.back.together02be.support

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc

@AutoConfigureMockMvc
abstract class ControllerTestSupport : IntegrationTestSupport() {

    @Autowired
    protected lateinit var mockMvc: MockMvc
}
