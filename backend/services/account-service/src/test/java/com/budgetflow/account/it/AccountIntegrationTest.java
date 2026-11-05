package com.budgetflow.account.it;

import com.budgetflow.account.AccountServiceApplication;
import com.budgetflow.common.test.ServiceContainers;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = AccountServiceApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ServiceContainers.class)
abstract class AccountIntegrationTest {}
