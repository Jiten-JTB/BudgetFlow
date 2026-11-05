package com.budgetflow.transaction.it;

import static com.budgetflow.common.test.ApiTokens.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.budgetflow.common.test.ServiceContainers;
import com.budgetflow.transaction.TransactionServiceApplication;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(classes = TransactionServiceApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ServiceContainers.class)
abstract class TransactionIntegrationTest {

    @Autowired protected MockMvc mvc;

    protected ResultActions postJson(UUID userId, String path, String json) throws Exception {
        return mvc.perform(post(path).with(user(userId)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected String createCategory(UUID userId, String name, String kind) throws Exception {
        return id(postJson(userId, "/api/v1/categories", "{\"name\":\"" + name + "\",\"kind\":\"" + kind + "\"}"));
    }

    protected static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }
}
