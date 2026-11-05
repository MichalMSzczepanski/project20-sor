package io.github.michalmszczepanski.project20_sor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** Bez harmonogramu — test nie może odpytywać pacjent.gov.pl. */
@SpringBootTest(properties = "sor.scheduling.enabled=false")
class Project20SorApplicationTests {

	@Test
	void contextLoads() {
	}

}
