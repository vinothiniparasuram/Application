package com.lifeorganizer.common;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

	private final MutableClock clock = new MutableClock(Instant.parse("2026-10-15T10:00:00Z"));

	private final RateLimitFilter filter = new RateLimitFilter(new RateLimitProperties(5, Duration.ofMinutes(1)), clock);

	@Test
	void blocksRequestsOverTheLimitWithinTheWindow() throws Exception {
		for (int i = 0; i < 5; i++) {
			assertThat(register("10.0.0.1").getStatus()).isEqualTo(200);
		}

		MockHttpServletResponse blocked = register("10.0.0.1");

		assertThat(blocked.getStatus()).isEqualTo(429);
		assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
	}

	@Test
	void allowsRequestsAgainAfterTheWindow() throws Exception {
		for (int i = 0; i < 6; i++) {
			register("10.0.0.1");
		}

		clock.advance(Duration.ofMinutes(1));

		assertThat(register("10.0.0.1").getStatus()).isEqualTo(200);
	}

	@Test
	void countsEachClientSeparately() throws Exception {
		for (int i = 0; i < 6; i++) {
			register("10.0.0.1");
		}

		assertThat(register("10.0.0.2").getStatus()).isEqualTo(200);
	}

	@Test
	void ignoresOtherEndpoints() throws Exception {
		for (int i = 0; i < 10; i++) {
			MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
			request.setRemoteAddr("10.0.0.1");
			MockHttpServletResponse response = new MockHttpServletResponse();
			filter.doFilter(request, response, new MockFilterChain());
			assertThat(response.getStatus()).isEqualTo(200);
		}
	}

	private MockHttpServletResponse register(String ip) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/register");
		request.setRemoteAddr(ip);
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(request, response, new MockFilterChain());
		return response;
	}

	private static final class MutableClock extends Clock {

		private Instant now;

		MutableClock(Instant now) {
			this.now = now;
		}

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}

	}

}
