package com.budgetflow.orchestrator.it;

import com.budgetflow.common.test.ServiceContainers;
import com.budgetflow.orchestrator.OrchestratorServiceApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = OrchestratorServiceApplication.class)
@ActiveProfiles("test")
@Import(ServiceContainers.class)
abstract class OrchestratorIntegrationTest {}
