package eu.domibus.ebms3.sender;

import eu.domibus.api.message.attempt.MessageAttemptService;
import eu.domibus.common.dao.UserMessageLogDao;
import eu.domibus.common.services.MessageExchangeService;
import eu.domibus.common.services.ReliabilityService;
import eu.domibus.core.message.fragment.MessageGroupDao;
import eu.domibus.core.message.fragment.MessageGroupEntity;
import eu.domibus.core.pmode.PModeProvider;
import eu.domibus.ebms3.common.model.UserMessage;
import eu.domibus.pki.PolicyService;
import mockit.Expectations;
import mockit.Injectable;
import mockit.Tested;
import mockit.integration.junit4.JMockit;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Cosmin Baciu
 * @since 4.1
 */
@RunWith(JMockit.class)
public class MessageFragmentSenderTest {

    @Tested
    MessageFragmentSender messageFragmentSender;

    @Injectable
    protected PModeProvider pModeProvider;

    @Injectable
    protected MSHDispatcher mshDispatcher;

    @Injectable
    protected EbMS3MessageBuilder messageBuilder;

    @Injectable
    protected ReliabilityChecker reliabilityChecker;

    @Injectable
    protected ResponseHandler responseHandler;

    @Injectable
    protected MessageAttemptService messageAttemptService;

    @Injectable
    protected MessageExchangeService messageExchangeService;

    @Injectable
    protected PolicyService policyService;

    @Injectable
    protected ReliabilityService reliabilityService;

    @Injectable
    protected UserMessageLogDao userMessageLogDao;

    @Injectable
    protected MessageGroupDao messageGroupDao;


    @Test
    public void validateBeforeSendingSuccessful(@Injectable UserMessage userMessage,
                                      @Injectable MessageGroupEntity groupEntity) {
        String groupId = "123";

        new Expectations() {{
            userMessage.getMessageFragment().getGroupId();
            result = groupId;

            messageGroupDao.findByGroupId(groupId);
            result = groupEntity;
        }};

        messageFragmentSender.validateBeforeSending(userMessage);
    }

    @Test(expected = SplitAndJoinException.class)
    public void validateBeforeSendingExpiredGroup(@Injectable UserMessage userMessage,
                                                @Injectable MessageGroupEntity groupEntity) {
        String groupId = "123";

        new Expectations() {{
            userMessage.getMessageFragment().getGroupId();
            result = groupId;

            messageGroupDao.findByGroupId(groupId);
            result = groupEntity;

            groupEntity.getExpired();
            result = true;
        }};

        messageFragmentSender.validateBeforeSending(userMessage);
    }

    @Test(expected = SplitAndJoinException.class)
    public void validateBeforeSendingRejectedGroup(@Injectable UserMessage userMessage,
                                                  @Injectable MessageGroupEntity groupEntity) {
        String groupId = "123";

        new Expectations() {{
            userMessage.getMessageFragment().getGroupId();
            result = groupId;

            messageGroupDao.findByGroupId(groupId);
            result = groupEntity;

            groupEntity.getExpired();
            result = false;

            groupEntity.getRejected();
            result = true;
        }};

        messageFragmentSender.validateBeforeSending(userMessage);
    }
}