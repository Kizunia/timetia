package com.kibootia.backend.platform.observability;

import com.kibootia.backend.TestcontainersConfiguration;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/** The console log is structured JSON and carries the ID of the active trace (observability baseline, ADR 0030). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class TraceLoggingIT {

	private static final Logger log = LoggerFactory.getLogger(TraceLoggingIT.class);

	@Autowired
	Tracer tracer;

	@Test
	@DisplayName("prints a JSON log line that carries the trace ID and span ID of the active span")
	void logLineCarriesTraceId(CapturedOutput output) {
		Span span = this.tracer.nextSpan().name("trace-logging-test").start();
		try (Tracer.SpanInScope ignored = this.tracer.withSpan(span)) {
			log.info("inside the span");
		}
		finally {
			span.end();
		}

		String line = output.getAll().lines().filter((l) -> l.contains("inside the span")).findFirst().orElseThrow();
		assertThat(line).startsWith("{").endsWith("}");
		assertThat(line).contains(span.context().traceId()).contains(span.context().spanId());
	}

}
