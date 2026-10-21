package com.lifeorganizer.common;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fixed-window rate limit on POST /api/register, keyed by client IP.
 *
 * <p>State is kept in memory, so each backend instance counts separately. If the app is
 * scaled out or runs behind a proxy, move this to a shared store (for example Redis) and
 * configure forwarded headers so {@code getRemoteAddr()} is the real client.
 */
public class RateLimitFilter extends OncePerRequestFilter {

	private static final int EVICTION_THRESHOLD = 10_000;

	private final Map<String, Window> windows = new ConcurrentHashMap<>();

	private final int maxRequests;

	private final long windowMillis;

	private final Clock clock;

	public RateLimitFilter(RateLimitProperties properties, Clock clock) {
		this.maxRequests = properties.maxRequests();
		this.windowMillis = properties.window().toMillis();
		this.clock = clock;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !("POST".equals(request.getMethod()) && "/api/register".equals(request.getRequestURI()));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		long now = clock.millis();
		if (windows.size() > EVICTION_THRESHOLD) {
			windows.values().removeIf((window) -> window.isExpired(now, windowMillis));
		}
		Window window = windows.compute(request.getRemoteAddr(),
				(key, current) -> (current == null || current.isExpired(now, windowMillis)) ? new Window(now, 1)
						: new Window(current.start(), current.count() + 1));
		if (window.count() > maxRequests) {
			long retryAfterSeconds = Math.max(1, (window.start() + windowMillis - now + 999) / 1000);
			response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
			response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
			response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
			response.getWriter()
				.write("{\"title\":\"Too many requests\",\"status\":429,\"detail\":\"Too many registration attempts. Try again in "
						+ retryAfterSeconds + " seconds.\"}");
			return;
		}
		chain.doFilter(request, response);
	}

	private record Window(long start, int count) {

		boolean isExpired(long now, long windowMillis) {
			return now - start >= windowMillis;
		}

	}

}
