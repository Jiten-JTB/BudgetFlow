package com.budgetflow.budget.it;

import static com.budgetflow.common.test.ApiTokens.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.budgetflow.budget.BudgetServiceApplication;
import com.budgetflow.common.test.ServiceContainers;
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

@SpringBootTest(classes = BudgetServiceApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ServiceContainers.class)
abstract class BudgetIntegrationTest {

    @Autowired protected MockMvc mvc;

    protected ResultActions create(UUID userId, String json) throws Exception {
        return mvc.perform(post("/api/v1/budgets").with(user(userId)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected static String budgetJson(String name, UUID categoryId, String period, String limit, String startsOn) {
        return "{\"name\":\"" + name + "\"," + (categoryId == null ? "" : "\"categoryId\":\"" + categoryId + "\",")
                + "\"period\":\"" + period + "\",\"limit\":\"" + limit + "\",\"currency\":\"INR\","
                + "\"alertThresholdPercent\":80,\"startsOn\":\"" + startsOn + "\"}";
    }

    protected static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }
}
