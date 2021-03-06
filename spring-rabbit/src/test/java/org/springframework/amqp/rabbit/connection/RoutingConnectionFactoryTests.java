/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.springframework.amqp.rabbit.connection;

import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.mockito.Mockito;

/**
 * @author Artem Bilan
 * @since 1.3
 */
public class RoutingConnectionFactoryTests {

	@Test
	public void testAbstractRoutingConnectionFactory() {
		ConnectionFactory connectionFactory1 = Mockito.mock(ConnectionFactory.class);
		ConnectionFactory connectionFactory2 = Mockito.mock(ConnectionFactory.class);
		Map<Object, ConnectionFactory> factories = new HashMap<Object, ConnectionFactory>(2);
		factories.put(Boolean.TRUE, connectionFactory1);
		factories.put(Boolean.FALSE, connectionFactory2);
		ConnectionFactory defaultConnectionFactory = Mockito.mock(ConnectionFactory.class);

		final AtomicBoolean lookupFlag = new AtomicBoolean(true);
		final AtomicInteger count = new AtomicInteger();

		AbstractRoutingConnectionFactory connectionFactory = new AbstractRoutingConnectionFactory() {

			@Override
			protected Object determineCurrentLookupKey() {
				return count.incrementAndGet() > 3 ? null : lookupFlag.getAndSet(!lookupFlag.get());
			}
		};

		connectionFactory.setDefaultTargetConnectionFactory(defaultConnectionFactory);
		connectionFactory.setTargetConnectionFactories(factories);

		for (int i = 0; i < 5; i++) {
			connectionFactory.createConnection();
		}

		Mockito.verify(connectionFactory1, Mockito.times(2)).createConnection();
		Mockito.verify(connectionFactory2).createConnection();
		Mockito.verify(defaultConnectionFactory, Mockito.times(2)).createConnection();
	}

	@Test
	public void testSimpleRoutingConnectionFactory() throws InterruptedException {
		ConnectionFactory connectionFactory1 = Mockito.mock(ConnectionFactory.class);
		ConnectionFactory connectionFactory2 = Mockito.mock(ConnectionFactory.class);
		Map<Object, ConnectionFactory> factories = new HashMap<Object, ConnectionFactory>(2);
		factories.put("foo", connectionFactory1);
		factories.put("bar", connectionFactory2);


		final AbstractRoutingConnectionFactory connectionFactory = new SimpleRoutingConnectionFactory();
		connectionFactory.setTargetConnectionFactories(factories);

		ExecutorService executorService = Executors.newFixedThreadPool(3);


		for (int i = 0; i < 3; i++) {
			final AtomicInteger count = new AtomicInteger(i);
			executorService.execute(new Runnable() {

				@Override
				public void run() {
					SimpleResourceHolder.bind(connectionFactory, count.get() % 2 == 0 ? "foo" : "bar");
					connectionFactory.createConnection();
					SimpleResourceHolder.unbind(connectionFactory);
				}
			});
		}

		executorService.shutdown();
		assertTrue(executorService.awaitTermination(10, TimeUnit.SECONDS));

		Mockito.verify(connectionFactory1, Mockito.times(2)).createConnection();
		Mockito.verify(connectionFactory2).createConnection();
	}

}
