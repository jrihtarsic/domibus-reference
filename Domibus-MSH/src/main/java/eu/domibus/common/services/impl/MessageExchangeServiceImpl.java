package eu.domibus.common.services.impl;

import eu.domibus.common.MessageStatus;
import eu.domibus.common.dao.ConfigurationDAO;
import eu.domibus.common.dao.MessagingDao;
import eu.domibus.common.dao.ProcessDao;
import eu.domibus.common.model.configuration.Configuration;
import eu.domibus.common.model.configuration.Party;
import eu.domibus.common.model.configuration.Process;
import eu.domibus.common.services.MessageExchangeService;
import eu.domibus.ebms3.common.context.MessageExchangeContext;
import eu.domibus.ebms3.common.model.MessagePullDto;
import eu.domibus.ebms3.common.model.UserMessage;
import eu.domibus.logging.DomibusLogger;
import eu.domibus.logging.DomibusLoggerFactory;
import eu.domibus.plugin.BackendConnector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessagePostProcessor;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import java.util.List;
import java.util.Map;

import static eu.domibus.common.MessageStatus.READY_TO_PULL;
import static eu.domibus.common.services.impl.PullContext.MPC;
import static eu.domibus.common.services.impl.PullContext.PMODE_KEY;
import static eu.domibus.common.services.impl.PullRequestStatus.ONE_MATCHING_PROCESS;

/**
 * Created by dussath on 5/19/17.
 * {@inheritDoc}
 */
@Service
public class MessageExchangeServiceImpl implements MessageExchangeService {


    @Autowired
    private ProcessDao processDao;
    @Autowired
    private ConfigurationDAO configurationDAO;
    @Autowired
    private MessagingDao messagingDao;

    @Autowired
    @Qualifier("pullMessageQueue")
    private Queue pullMessageQueue;
    @Autowired
    private JmsTemplate jmsPullTemplate;


    private final static DomibusLogger LOG = DomibusLoggerFactory.getLogger(MessageExchangeService.class);

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public void upgradeMessageExchangeStatus(MessageExchangeContext messageExchangeContext) {
        List<Process> processes = processDao.findProcessByMessageContext(messageExchangeContext);
        messageExchangeContext.updateStatus(MessageStatus.SEND_ENQUEUED);
        for (Process process : processes) {
            boolean pullProcess = BackendConnector.Mode.PULL.getFileMapping().equals(Process.getBindingValue(process));
            boolean oneWay = BackendConnector.Mep.ONE_WAY.getFileMapping().equals(Process.getMepValue(process));
            //@question wich exception should be throwned here.
            if (pullProcess) {
                if (!oneWay) {
                    throw new RuntimeException("We only support oneway/pull at the moment");
                }
                if (processes.size() > 1) {
                    throw new RuntimeException("This configuration is also mapping another process!");
                }
                PullContext pullContext = new PullContext();
                pullContext.setProcess(process);
                pullContext.checkProcessValidity();
                if (!pullContext.isValid()) {
                    throw new RuntimeException(pullContext.createProcessWarningMessage());
                }
                messageExchangeContext.updateStatus(READY_TO_PULL);
            }
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void initiatePullRequest() {
        LOG.info("Check for pull PMODE");
        Configuration configuration = configurationDAO.read();
        List<Process> pullProcesses = processDao.findPullProcessesByResponder(configuration.getParty());
        LOG.info(pullProcesses.size() + " pull PMODE found!");
        for (Process pullProcess : pullProcesses) {
            PullContext pullContext=new PullContext();
            pullContext.setResponder(configuration.getParty());
            pullContext.setProcess(pullProcess);
            pullContext.checkProcessValidity();
            if (!pullContext.isValid()) {
                continue;
            }
            pullContext.send(new PullContextCommand() {
                @Override
                public void execute(final Map<String,String> messageMap) {
                    jmsPullTemplate.convertAndSend(pullMessageQueue, messageMap, new MessagePostProcessor() {
                        public Message postProcessMessage(Message message) throws JMSException {
                            message.setStringProperty(MPC, messageMap.get("mpc"));
                            message.setStringProperty(PMODE_KEY, messageMap.get("mpc"));
                            return message;
                        }
                    });
                }
            });
        }
    }





    @Override
    public UserMessage retrieveUserReadyToPullMessages(final String mpc, final Party responder) {
        List<MessagePullDto> messagingOnStatusReceiverAndMpc = messagingDao.findMessagingOnStatusReceiverAndMpc(responder.getEntityId(), MessageStatus.READY_TO_PULL, mpc);
        if (!messagingOnStatusReceiverAndMpc.isEmpty()) {
            MessagePullDto messagePullDto = messagingOnStatusReceiverAndMpc.get(0);
            return messagingDao.findUserMessageByMessageId(messagePullDto.getMessageId());
            //@thom change the status of the message in a new transaction. Set it back to ready_to_pull after a time.
        }
        return null;


    }

    /**
     * {@inheritDoc}
     *
     * @thom test this method
     */
    @Override
    public PullContext extractProcessOnMpc(final String mpcQualifiedName) {
        PullContext pullContext = new PullContext();
        pullContext.addRequestStatus(ONE_MATCHING_PROCESS);
        pullContext.setMpcQualifiedName(mpcQualifiedName);
        findCurrentAccesPoint(pullContext);
        finMpcProcess(pullContext);
        pullContext.checkProcessValidity();
        pullContext.setResponder(pullContext.getProcess().getResponderParties().iterator().next());
        return pullContext;
    }

    /**
     * Retrieve process information based on the information contained in the pullRequest.
     *
     * @param pullContext the context of the request.
     */
    //@thom test this method
    private void finMpcProcess(PullContext pullContext) {
        List<Process> processes = processDao.findPullProcessBytMpc(pullContext.getMpcQualifiedName());
        if (processes.size() > 1) {
            pullContext.addRequestStatus(PullRequestStatus.TOO_MANY_PROCESSES);
        } else if (processes.size() == 0) {
            pullContext.addRequestStatus(PullRequestStatus.NO_PROCESSES);
        } else {
            pullContext.setProcess(processes.get(0));
        }
    }

    /**
     * Extract initiator and responder information based on the pullrequest.
     *
     * @param pullContext the context of the pull request.
     */
    //@thom test this method
    private void findCurrentAccesPoint(PullContext pullContext) {
        Configuration configuration = configurationDAO.read();
        pullContext.setInitiator(configuration.getParty());
    }


}

