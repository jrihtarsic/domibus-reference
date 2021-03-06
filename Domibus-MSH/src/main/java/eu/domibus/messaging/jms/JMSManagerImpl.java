package eu.domibus.messaging.jms;

import eu.domibus.api.configuration.DomibusConfigurationService;
import eu.domibus.api.jms.JMSDestination;
import eu.domibus.api.jms.JMSManager;
import eu.domibus.api.jms.JmsMessage;
import eu.domibus.api.multitenancy.Domain;
import eu.domibus.api.multitenancy.DomainContextProvider;
import eu.domibus.api.multitenancy.DomainService;
import eu.domibus.api.security.AuthUtils;
import eu.domibus.common.services.AuditService;
import eu.domibus.jms.spi.InternalJMSDestination;
import eu.domibus.jms.spi.InternalJMSManager;
import eu.domibus.jms.spi.InternalJmsMessage;
import eu.domibus.logging.DomibusLogger;
import eu.domibus.logging.DomibusLoggerFactory;
import eu.domibus.messaging.MessageConstants;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.Topic;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Cosmin Baciu
 * @since 3.2
 */
@Component
@Transactional
public class JMSManagerImpl implements JMSManager {

    private static final DomibusLogger LOG = DomibusLoggerFactory.getLogger(JMSManagerImpl.class);

    private static final String SELECTOR = "selector";

    /**
     * queue names to be skip from showing into GUI interface
     */
    private static final String[] SKIP_QUEUE_NAMES = {};

    /**
     * multi-tenancy mode - JMS plugin queues suffixes per domain
     */
    private static final String[] JMS_QUEUE_NAMES = {"domibus.backend.jms.outQueue", "domibus.backend.jms.replyQueue",
            "domibus.backend.jms.errorNotifyConsumer", "domibus.backend.jms.errorNotifyProducer"};

    @Autowired
    InternalJMSManager internalJmsManager;

    @Autowired
    JMSDestinationMapper jmsDestinationMapper;

    @Autowired
    JMSMessageMapper jmsMessageMapper;

    @Autowired
    private AuditService auditService;

    @Autowired
    protected DomainContextProvider domainContextProvider;

    @Autowired
    @Qualifier("jsonJmsTemplate")
    private JmsTemplate jmsTemplate;

    @Autowired
    protected DomibusConfigurationService domibusConfigurationService;

    @Autowired
    protected AuthUtils authUtils;

    @Autowired
    private DomainService domainService;

    @Override
    public SortedMap<String, JMSDestination> getDestinations() {
        Map<String, InternalJMSDestination> destinations = internalJmsManager.findDestinationsGroupedByFQName();
        Map<String, InternalJMSDestination> result = new HashMap<>();
        LOG.debug("JMS Messages Source Queues are [{}]", destinations);
        for (Map.Entry<String, InternalJMSDestination> mapEntry : destinations.entrySet()) {
            final String internalQueueName = mapEntry.getValue().getName();
            if (StringUtils.indexOfAny(internalQueueName, SKIP_QUEUE_NAMES) == -1 &&
                    !jmsQueueInOtherDomain(internalQueueName)) {
                result.put(mapEntry.getKey(), mapEntry.getValue());
            }
        }

        Map<String, JMSDestination> queues = jmsDestinationMapper.convert(result);
        return sortQueues(queues);
    }

    /**
     * in case of cluster environments, we reverse the name of the queue with the cluster name so that
     * the ordering shows all logical queues grouped:
     * <p>
     * Cluster1@inQueueX
     * Cluster2@inQueueX
     * Cluster1@inQueueY
     * Cluster2@inQueueY
     * in any case, we sort them by key = logicalName
     *
     * @param destinations map of {@code <String, JMSDestination>}
     * @return Sorted map of {@code <String, JMSDestination>}
     */
    protected SortedMap<String, JMSDestination> sortQueues(Map<String, JMSDestination> destinations) {
        SortedMap<String, JMSDestination> jmsDestinations = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (destinations == null) {
            return jmsDestinations;
        }

        destinations.values().forEach(queue -> {
            int ind = queue.getName().lastIndexOf('@');
            String logicalName;
            if (ind > 0) {
                logicalName = queue.getName().substring(ind + 1) + '@' + queue.getName().substring(0, ind);
            } else {
                logicalName = queue.getName();
            }
            jmsDestinations.put(logicalName, queue);
        });

        return jmsDestinations;
    }

    @Override
    public JmsMessage getMessage(String source, String messageId) {
        InternalJmsMessage internalJmsMessage = internalJmsManager.getMessage(source, messageId);
        return jmsMessageMapper.convert(internalJmsMessage);
    }

    @Override
    public List<JmsMessage> browseMessages(String source, String jmsType, Date fromDate, Date toDate, String selector) {

        List<InternalJmsMessage> messagesSPI = internalJmsManager.browseMessages(source, jmsType, fromDate, toDate, getDomainSelector(selector));
        LOG.debug("Jms Messages browsed from the source queue [{}] with the selector [{}]", source, selector);
        return jmsMessageMapper.convert(messagesSPI);
    }

    @Override
    public String getDomainSelector(String selector) {
        if (!domibusConfigurationService.isMultiTenantAware()) {
            return selector;
        }
        if (authUtils.isSuperAdmin()) {
            return selector;
        }
        final Domain currentDomain = domainContextProvider.getCurrentDomain();
        String domainClause = "DOMAIN ='" + currentDomain.getCode() + "'";

        String result;
        if (StringUtils.isBlank(selector)) {
            result = domainClause;
        } else {
            result = selector + " AND " + domainClause;
        }
        return result;
    }

    @Override
    public List<JmsMessage> browseClusterMessages(String source, String selector) {
        LOG.debug("browseClusterMessages using selector [{}]", selector);
        List<InternalJmsMessage> messagesSPI = internalJmsManager.browseClusterMessages(source, selector);
        return jmsMessageMapper.convert(messagesSPI);
    }

    @Override
    public void sendMessageToQueue(JmsMessage message, String destination) {
        sendMessageToQueue(message, destination, InternalJmsMessage.MessageType.TEXT_MESSAGE);
    }

    @Override
    public void sendMapMessageToQueue(JmsMessage message, String destination) {
        sendMessageToQueue(message, destination, InternalJmsMessage.MessageType.MAP_MESSAGE);
    }

    @Override
    public void convertAndSendToQueue(final Object message, final Queue destination, final String selector) {
        jmsTemplate.convertAndSend(destination, message, message1 -> {
            final Domain currentDomain = domainContextProvider.getCurrentDomainSafely();
            message1.setStringProperty(JmsMessage.PROPERTY_ORIGINAL_QUEUE, destination.getQueueName());
            //that scenario occurs when sending an event with super user... EG:Login failure with super user.
            if (currentDomain != null) {
                message1.setStringProperty(MessageConstants.DOMAIN, currentDomain.getCode());
            } else {
                LOG.debug("Sending event for super user, no current domain defined");
            }
            message1.setStringProperty(SELECTOR, selector);
            return message1;
        });
    }


    protected void sendMessageToQueue(JmsMessage message, String destination, InternalJmsMessage.MessageType messageType) {
        final Domain currentDomain = domainContextProvider.getCurrentDomain();
        message.getProperties().put(JmsMessage.PROPERTY_ORIGINAL_QUEUE, destination);
        message.getProperties().put(MessageConstants.DOMAIN, currentDomain.getCode());
        InternalJmsMessage internalJmsMessage = jmsMessageMapper.convert(message);
        internalJmsMessage.setMessageType(messageType);
        internalJmsManager.sendMessage(internalJmsMessage, destination);
    }

    @Override
    public void sendMessageToQueue(JmsMessage message, Queue destination) {
        sendMessageToQueue(message, destination, InternalJmsMessage.MessageType.TEXT_MESSAGE);
    }

    @Override
    public void sendMapMessageToQueue(JmsMessage message, Queue destination) {
        sendMessageToQueue(message, destination, InternalJmsMessage.MessageType.MAP_MESSAGE);
    }

    protected void sendMessageToQueue(JmsMessage message, Queue destination, InternalJmsMessage.MessageType messageType) {
        try {
            message.getProperties().put(JmsMessage.PROPERTY_ORIGINAL_QUEUE, destination.getQueueName());
        } catch (JMSException e) {
            LOG.warn("Could not add the property [" + JmsMessage.PROPERTY_ORIGINAL_QUEUE + "] on the destination", e);
        }
        sendMessageToDestination(message, destination, messageType);
    }

    protected void sendMessageToDestination(JmsMessage message, Destination destination, InternalJmsMessage.MessageType messageType) {
        InternalJmsMessage internalJmsMessage = getInternalJmsMessage(message, messageType);
        internalJmsManager.sendMessage(internalJmsMessage, destination);
    }

    private InternalJmsMessage getInternalJmsMessage(JmsMessage message, InternalJmsMessage.MessageType messageType) {
        final Domain currentDomain = domainContextProvider.getCurrentDomain();
        message.getProperties().put(MessageConstants.DOMAIN, currentDomain.getCode());
        InternalJmsMessage internalJmsMessage = jmsMessageMapper.convert(message);
        internalJmsMessage.setMessageType(messageType);
        return internalJmsMessage;
    }

    @Override
    public void sendMessageToTopic(JmsMessage message, Topic destination) {
        sendMessageToTopic(message, destination, false);
    }

    @Override
    public void sendMessageToTopic(JmsMessage message, Topic destination, boolean excludeOrigin) {
        InternalJmsMessage internalJmsMessage = getInternalJmsMessage(message, InternalJmsMessage.MessageType.TEXT_MESSAGE);
        internalJmsManager.sendMessageToTopic(internalJmsMessage, destination, excludeOrigin);
    }

    @Override
    public void deleteMessages(String source, String[] messageIds) {
        List<JMSMessageDomainDTO> jmsMessageDomains = getJMSMessageDomain(source, messageIds);

        internalJmsManager.deleteMessages(source, messageIds);
        LOG.debug("Jms Message Ids [{}] deleted from the source queue [{}] ", messageIds, source);
        jmsMessageDomains.forEach(jmsMessageDomainDTO -> auditService.addJmsMessageDeletedAudit(jmsMessageDomainDTO.getJmsMessageId(),
                source, jmsMessageDomainDTO.getDomainCode()));
    }

    @Override
    public void moveMessages(String source, String destination, String[] messageIds) {
        List<JMSMessageDomainDTO> jmsMessageDomains = getJMSMessageDomain(source, messageIds);

        internalJmsManager.moveMessages(source, destination, messageIds);
        LOG.debug("Jms Message Ids [{}] Moved from the source queue [{}] to the destination queue [{}]", messageIds, source, destination);
        jmsMessageDomains.forEach(jmsMessageDomainDTO -> auditService.addJmsMessageMovedAudit(jmsMessageDomainDTO.getJmsMessageId(),
                source, destination, jmsMessageDomainDTO.getDomainCode()));
    }

    protected List<JMSMessageDomainDTO> getJMSMessageDomain(String source, String[] messageIds) {
        return Arrays.stream(messageIds).map(jmsMessageId -> new JMSMessageDomainDTO(jmsMessageId,
                retrieveDomainFromJMSMessage(source, jmsMessageId))).collect(Collectors.toList());
    }

    protected String retrieveDomainFromJMSMessage(String source, String jmsMessageId) {
        if (domibusConfigurationService.isSingleTenantAware()) {
            LOG.trace("JMS message [{}] doesn't have a domain property", jmsMessageId);
            return null;
        }
        //retrieve the domain
        JmsMessage jmsMessage = getMessage(source, jmsMessageId);
        return jmsMessage.getProperty(MessageConstants.DOMAIN);
    }

    @Override
    public JmsMessage consumeMessage(String source, String messageId) {
        messageId = StringUtils.replaceAll(messageId, "'", "''");
        InternalJmsMessage internalJmsMessage = internalJmsManager.consumeMessage(source, messageId);
        return jmsMessageMapper.convert(internalJmsMessage);
    }

    @Override
    public long getDestinationSize(final String nameLike) {
        final Map<String, InternalJMSDestination> destinationsGroupedByFQName = internalJmsManager.findDestinationsGroupedByFQName();
        for (Map.Entry<String, InternalJMSDestination> entry : destinationsGroupedByFQName.entrySet()) {
            if (StringUtils.containsIgnoreCase(entry.getKey(), nameLike)) {
                final InternalJMSDestination value = entry.getValue();
                return value.getNumberOfMessages();
            }
        }
        return 0;
    }

    /**
     * tests if the given queue {@code jmsQueueInternalName} should be excluded from current queues of JMS Monitoring page
     * - when the user is logged as Admin domain and queue is defined as JMS Plugin queue
     *
     * @param jmsQueueInternalName
     * @return
     */
    protected boolean jmsQueueInOtherDomain(final String jmsQueueInternalName) {
        /** multi-tenancy but not super-admin*/
        if (domibusConfigurationService.isMultiTenantAware() && !authUtils.isSuperAdmin()) {
            List<Domain> domainsList = domainService.getDomains();
            Domain currentDomain = domainContextProvider.getCurrentDomainSafely();

            List<Domain> domainsToCheck = domainsList.stream().filter(domain -> !domain.equals(DomainService.DEFAULT_DOMAIN) &&
                    !domain.equals(currentDomain)).collect(Collectors.toList());

            List<String> queuesToCheck = new ArrayList<>();
            for (Domain domain : domainsToCheck) {
                for (String jmsQueue : JMS_QUEUE_NAMES) {
                    queuesToCheck.add(domain.getCode() + "." + jmsQueue);
                }
            }

            return StringUtils.indexOfAny(jmsQueueInternalName, queuesToCheck.stream().toArray(String[]::new)) >= 0;
        }
        return false;
    }
}
