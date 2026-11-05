package io.github.michalmszczepanski.project20_sor.config;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
class HttpClientsConfig {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	RestClient pacjentGovRestClient(RestClient.Builder builder, SorProperties properties) {
		SorProperties.PacjentGov pacjentGov = properties.pacjentGov();
		return client(builder, pacjentGov.baseUrl(), pacjentGov.timeout(), pacjentGov.userAgent());
	}

	@Bean
	RestClient rpwdlRestClient(RestClient.Builder builder, SorProperties properties) {
		SorProperties.Rpwdl rpwdl = properties.rpwdl();
		return client(builder, rpwdl.baseUrl(), rpwdl.timeout(), properties.pacjentGov().userAgent());
	}

	@Bean
	@ConditionalOnBooleanProperty("sor.travel.osrm.enabled")
	RestClient osrmRestClient(RestClient.Builder builder, SorProperties properties) {
		SorProperties.Osrm osrm = properties.travel().osrm();
		return client(builder, osrm.baseUrl(), osrm.timeout(), properties.pacjentGov().userAgent());
	}

	private static RestClient client(RestClient.Builder builder, String baseUrl, Duration timeout, String userAgent) {
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(timeout)
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(timeout);
		return builder.clone()
				.baseUrl(baseUrl)
				.requestFactory(requestFactory)
				.defaultHeader(HttpHeaders.USER_AGENT, userAgent)
				.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
				// Spring domyślnie prosi o „gzip” i „deflate”; serwer OSRM wybiera wtedy „deflate”, a odsyła gzip,
				// co kończy się ZipException. Prosimy jawnie tylko o gzip.
				.defaultHeader(HttpHeaders.ACCEPT_ENCODING, "gzip")
				.build();
	}

}
