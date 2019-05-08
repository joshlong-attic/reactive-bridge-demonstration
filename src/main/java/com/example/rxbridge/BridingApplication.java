package com.example.rxbridge;

import org.reactivestreams.Publisher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

@SpringBootApplication
public class BridingApplication {

	public static void main(String[] args) {
		SpringApplication.run(BridingApplication.class, args);
	}
}

@RestController
class GreetingsController {

	private final ApplicationEventPublisher publisher;
	private final Bridge bridge;

	GreetingsController(ApplicationEventPublisher publisher, Bridge bridge) {
		this.publisher = publisher;
		this.bridge = bridge;
	}

	// curl http://localhost:8080/greet/World -XPOST
	@PostMapping("/publish/{name}")
	Mono<Void> publish(@PathVariable String name) {
		this.publisher.publishEvent(new GreetingsEvent("hello " + name + " @ " + Instant.now()));
		return Mono.empty();
	}

	// curl http://localhost:8080/subscribe
	@GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE, value = "/subscribe")
	Publisher<GreetingsEvent> subscribe() {
		return Flux.create(this.bridge);
	}
}

@Component
class Bridge implements
	ApplicationListener<GreetingsEvent>, // this is a thing that listens for external events
	Consumer<FluxSink<GreetingsEvent>> // this is the thing that lets us pump data into a Publisher<T>
{

	private final LinkedBlockingQueue<GreetingsEvent> queue = new LinkedBlockingQueue<>();

	@Override
	public void onApplicationEvent(GreetingsEvent greetingsEvent) {
		// whenever an event in the real world happens (in this case an ApplicationContextEvent)
		// then I add it to a LinkedBlockingQueue<T> which blocks
		this.queue.offer(greetingsEvent);
	}

	@Override
	public void accept(FluxSink<GreetingsEvent> sink) {
		try {
			// as the events come out of the queue, we pump them into the sink
			GreetingsEvent event;
			while ((event = this.queue.take()) != null) {
				sink.next(event);
			}
		}
		catch (InterruptedException e) {
			ReflectionUtils.rethrowRuntimeException(e);
		}
	}

}


class GreetingsEvent extends ApplicationEvent {

	public GreetingsEvent(String greeting) {
		super(greeting);
	}
}