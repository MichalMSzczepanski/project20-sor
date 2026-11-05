package io.github.michalmszczepanski.project20_sor.queue;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Odpowiedź GET /api/v1/datatopsor/{numer} (surowe pola, nazwy jak w źródle). */
@JsonIgnoreProperties(ignoreUnknown = true)
record TopsorResponse(Long t, List<Entry> data, Integer expecting) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Entry(String name, Integer count, @JsonProperty("wait") String waitText) {
	}

}
