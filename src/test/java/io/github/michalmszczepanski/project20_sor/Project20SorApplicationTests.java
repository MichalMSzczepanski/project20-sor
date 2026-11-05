package io.github.michalmszczepanski.project20_sor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Bez harmonogramu — test nie może odpytywać pacjent.gov.pl. */
@SpringBootTest(properties = "sor.scheduling.enabled=false")
@AutoConfigureMockMvc
class Project20SorApplicationTests {

	@Autowired
	private MockMvc mvc;

	@Test
	void contextLoads() {
	}

	@Test
	void healthIsDownUntilSorListIsLoaded() throws Exception {
		mvc.perform(get("/actuator/health"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.status").value("DOWN"))
				.andExpect(jsonPath("$.components.sorCatalog.status").value("DOWN"))
				.andExpect(jsonPath("$.components.sorQueues.status").value("UNKNOWN"))
				.andExpect(jsonPath("$.components.sorTypes.status").value("UNKNOWN"));
	}

}
