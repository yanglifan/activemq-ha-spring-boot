package com.github.yanglifan.spring.boot.activemq;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.Lifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.JmsException;
import org.springframework.jms.config.AbstractJmsListenerContainerFactory;
import org.springframework.jms.config.JmsListenerContainerFactory;
import org.springframework.jms.listener.AbstractMessageListenerContainer;
import org.springframework.jms.listener.DefaultMessageListenerContainer;

import javax.jms.ConnectionFactory;
import java.util.ArrayList;
import java.util.List;

@Configuration
@ConditionalOnClass({ConnectionFactory.class, ActiveMQConnectionFactory.class})
@ConditionalOnMissingBean(ConnectionFactory.class)
@EnableConfigurationProperties(ActiveMqClusterProperties.class)
//@EnableAutoConfiguration(exclude = {ActiveMQAutoConfiguration.class})
public class ActiveMqClusterAutoConfiguration {
	private final ActiveMqClusterProperties properties;

	public ActiveMqClusterAutoConfiguration(ActiveMqClusterProperties properties) {
		this.properties = properties;
	}

	@Bean
	List<ConnectionFactory> connectionFactories() {
		List<ConnectionFactory> connectionFactories = new ArrayList<>();

		for (ActiveMqClusterProperties.Broker broker : properties.getBrokers()) {
			ConnectionFactory cf = new ActiveMQConnectionFactory(
					broker.getUsername(), broker.getPassword(), broker.getUrl()
			);

			connectionFactories.add(cf);
		}

		return connectionFactories;
	}

	@Bean
	JmsListenerContainerFactory jmsListenerContainerFactory() {
		return new AbstractJmsListenerContainerFactory() {
			@Override
			protected AbstractMessageListenerContainer createContainerInstance() {
				MultiDcActiveMqListenerContainer multiDcActiveMqListenerContainer =
						new MultiDcActiveMqListenerContainer();
				multiDcActiveMqListenerContainer.setConnectionFactories(connectionFactories());
				return multiDcActiveMqListenerContainer;
			}
		};
	}
}

class MultiDcActiveMqListenerContainer extends DefaultMessageListenerContainer {
	private List<DefaultMessageListenerContainer> messageListenerContainers = new ArrayList<>();

	@Override
	public void initialize() {
		messageListenerContainers.forEach(DefaultMessageListenerContainer::initialize);
	}

	@Override
	public void afterPropertiesSet() {
		// Avoid ex since no connection factory
		validateConfiguration();
		initialize();
	}

	@Override
	public void setDestinationName(String destinationName) {
		super.setDestinationName(destinationName);
		messageListenerContainers.forEach(mlc -> mlc.setDestinationName(destinationName));
	}

	@Override
	public void start() throws JmsException {
		messageListenerContainers.forEach(Lifecycle::start);
	}

	@Override
	public void setMessageListener(Object messageListener) {
		this.messageListenerContainers.forEach(mlc -> mlc.setMessageListener(messageListener));
	}

	void setConnectionFactories(List<ConnectionFactory> connectionFactories) {
		connectionFactories.forEach(cf -> {
			DefaultMessageListenerContainer messageListenerContainer = new DefaultMessageListenerContainer();
			messageListenerContainer.setConnectionFactory(cf);
			messageListenerContainers.add(messageListenerContainer);
		});
	}
}