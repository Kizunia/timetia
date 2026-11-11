package com.kibootia.backend;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.scheduling.annotation.Scheduled;

import com.kibootia.backend.platform.events.DomainEvent;
import com.kibootia.backend.platform.events.DurableEventListener;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/** Architecture rules 9 to 11 for scheduled jobs and durable events (ADR 0031, ADR 0039). */
final class EventAndJobRules {

	private EventAndJobRules() {
	}

	/** Rule 9: with several instances, an unlocked job runs on all of them at once. */
	static ArchRule scheduledMethodsTakeALock() {
		return methods().that()
			.areAnnotatedWith(Scheduled.class)
			.should()
			.beAnnotatedWith(SchedulerLock.class)
			.allowEmptyShould(true)
			.because("a @Scheduled method without @SchedulerLock runs on every instance at once");
	}

	/**
	 * Rule 10: {@code @ApplicationModuleListener} opens its transaction before the tenant is known, so Hibernate would
	 * bind the session to no tenant. {@code @DurableEventListener} binds the tenant first.
	 */
	static ArchRule durableListenersReplaceApplicationModuleListener() {
		return noMethods().should()
			.beAnnotatedWith(ApplicationModuleListener.class)
			.allowEmptyShould(true)
			.because("@ApplicationModuleListener starts its transaction before the tenant is bound; use @DurableEventListener");
	}

	/** Rule 11: the wrapper needs the event's context to restore the tenant and trace. */
	static ArchRule durableListenersTakeADomainEvent() {
		return methods().that()
			.areAnnotatedWith(DurableEventListener.class)
			.should(haveADomainEventAsFirstParameter())
			.allowEmptyShould(true)
			.because("the tenant and trace are restored from the event's context");
	}

	private static ArchCondition<JavaMethod> haveADomainEventAsFirstParameter() {
		return new ArchCondition<>("take a DomainEvent as the first parameter") {
			@Override
			public void check(JavaMethod method, ConditionEvents events) {
				boolean ok = !method.getRawParameterTypes().isEmpty()
						&& method.getRawParameterTypes().get(0).isAssignableTo(DomainEvent.class);
				if (!ok) {
					events.add(SimpleConditionEvent.violated(method,
							method.getFullName() + " must take a DomainEvent as its first parameter ("
									+ method.getSourceCodeLocation() + ")"));
				}
			}
		};
	}

}
