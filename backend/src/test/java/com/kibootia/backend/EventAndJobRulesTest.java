package com.kibootia.backend;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.scheduling.annotation.Scheduled;

import com.kibootia.backend.platform.events.DomainEvent;
import com.kibootia.backend.platform.events.DurableEventListener;
import com.kibootia.backend.platform.events.EventContext;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Proves rules 9 to 11 fail for the mistakes they exist for and pass for correct code. */
class EventAndJobRulesTest {

	record SomeEvent(EventContext context) implements DomainEvent {

	}

	static class Violations {

		@Scheduled(fixedDelay = 1000)
		void unlockedJob() {
		}

		@ApplicationModuleListener
		void opensItsTransactionTooEarly(SomeEvent event) {
		}

		@DurableEventListener
		void takesSomethingElse(String notAnEvent) {
		}

		@DurableEventListener
		void takesNothing() {
		}

	}

	static class Correct {

		@Scheduled(fixedDelay = 1000)
		@SchedulerLock(name = "correct-job")
		void lockedJob() {
		}

		@DurableEventListener
		void handles(SomeEvent event) {
		}

	}

	private static JavaClasses of(Class<?>... classes) {
		return new ClassFileImporter().importClasses(classes);
	}

	@Test
	@DisplayName("rule 9 reports a @Scheduled method with no @SchedulerLock")
	void unlockedJobIsReported() {
		JavaClasses violations = of(Violations.class);

		assertThatThrownBy(() -> EventAndJobRules.scheduledMethodsTakeALock().check(violations))
			.isInstanceOf(AssertionError.class)
			.hasMessageContaining("unlockedJob");
	}

	@Test
	@DisplayName("rule 10 reports @ApplicationModuleListener")
	void applicationModuleListenerIsReported() {
		JavaClasses violations = of(Violations.class);

		assertThatThrownBy(() -> EventAndJobRules.durableListenersReplaceApplicationModuleListener().check(violations))
			.isInstanceOf(AssertionError.class)
			.hasMessageContaining("opensItsTransactionTooEarly");
	}

	@Test
	@DisplayName("rule 11 reports a durable listener that does not take a DomainEvent first")
	void listenerWithoutEventIsReported() {
		JavaClasses violations = of(Violations.class);

		assertThatThrownBy(() -> EventAndJobRules.durableListenersTakeADomainEvent().check(violations))
			.isInstanceOf(AssertionError.class)
			.hasMessageContaining("takesSomethingElse")
			.hasMessageContaining("takesNothing");
	}

	@Test
	@DisplayName("correct jobs and listeners pass all three rules")
	void correctCodePasses() {
		JavaClasses correct = of(Correct.class);

		assertThatCode(() -> {
			EventAndJobRules.scheduledMethodsTakeALock().check(correct);
			EventAndJobRules.durableListenersReplaceApplicationModuleListener().check(correct);
			EventAndJobRules.durableListenersTakeADomainEvent().check(correct);
		}).doesNotThrowAnyException();
	}

}
