
package eu.domibus.ebms3.sender;

import eu.domibus.api.message.attempt.MessageAttemptService;
import eu.domibus.api.property.DomibusPropertyProvider;
import eu.domibus.api.usermessage.UserMessageService;
import eu.domibus.common.MSHRole;
import eu.domibus.common.MessageStatus;
import eu.domibus.common.NotificationStatus;
import eu.domibus.common.dao.MessagingDao;
import eu.domibus.common.dao.RawEnvelopeLogDao;
import eu.domibus.common.dao.UserMessageLogDao;
import eu.domibus.common.model.configuration.LegConfiguration;
import eu.domibus.common.model.configuration.ReceptionAwareness;
import eu.domibus.common.model.configuration.RetryStrategy;
import eu.domibus.common.model.logging.MessageLog;
import eu.domibus.common.model.logging.UserMessageLog;
import eu.domibus.core.message.UserMessageLogDefaultService;
import eu.domibus.core.replication.UIReplicationSignalService;
import eu.domibus.ebms3.common.model.UserMessage;
import eu.domibus.ebms3.receiver.BackendNotificationService;
import mockit.*;
import mockit.integration.junit4.JMockit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Date;
import java.util.UUID;

import static eu.domibus.ebms3.sender.UpdateRetryLoggingService.DELETE_PAYLOAD_ON_SEND_FAILURE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(JMockit.class)
public class UpdateRetryLoggingServiceTest {

    private static final int RETRY_TIMEOUT_IN_MINUTES = 60;

    private static final int RETRY_COUNT = 4;

    private static final long SYSTEM_DATE_IN_MILLIS_FIRST_OF_JANUARY_2016 = 1451602800000L; //This is the reference time returned by System.correntTImeMillis() mock

    private static final long FIVE_MINUTES_BEFORE_FIRST_OF_JANUARY_2016 = SYSTEM_DATE_IN_MILLIS_FIRST_OF_JANUARY_2016 - (60 * 5 * 1000);

    private static final long ONE_HOUR_BEFORE_FIRST_OF_JANUARY_2016 = SYSTEM_DATE_IN_MILLIS_FIRST_OF_JANUARY_2016 - (60 * 60 * 1000);

    @Tested
    private UpdateRetryLoggingService updateRetryLoggingService;

    @Injectable
    private BackendNotificationService backendNotificationService;

    @Injectable
    private UserMessageLogDao messageLogDao;

    @Injectable
    private UserMessageLogDefaultService messageLogService;

    @Injectable
    private MessagingDao messagingDao;

    @Injectable
    private UIReplicationSignalService uiReplicationSignalService;

    @Injectable
    private RawEnvelopeLogDao rawEnvelopeLogDao;

    private LegConfiguration legConfiguration = new LegConfiguration();

    @Injectable
    DomibusPropertyProvider domibusPropertyProvider;

    @Injectable
    UserMessageService userMessageService;

    @Injectable
    MessageAttemptService messageAttemptService;

    @Before
    public void setupExpectations() {
        new NonStrictExpectations(legConfiguration) {{
            legConfiguration.getReceptionAwareness();
            result = new ReceptionAwareness();
            legConfiguration.getReceptionAwareness().getStrategy().getAlgorithm();
            result = RetryStrategy.CONSTANT.getAlgorithm();
            legConfiguration.getReceptionAwareness().getRetryTimeout();
            result = RETRY_TIMEOUT_IN_MINUTES;
            legConfiguration.getReceptionAwareness().getRetryCount();
            result = RETRY_COUNT;
            domibusPropertyProvider.getIntegerProperty(updateRetryLoggingService.MESSAGE_EXPIRATION_DELAY);
            result = 6000;
        }};
    }


    /**
     * Max retries limit reached
     * Timeout limit not reached
     * Notification is enabled
     * Expected result: MessageLogDao#setAsNotified() is called
     * MessageLogDao#setMessageAsSendFailure is called
     * MessagingDao#clearPayloadData() is called
     *
     * @throws Exception
     */
    @Test
    public void testUpdateRetryLogging_maxRetriesReachedNotificationEnabled_ExpectedMessageStatus(@Injectable UserMessage userMessage,
                                                                                                  @Injectable UserMessageLog userMessageLog) throws Exception {
        new SystemMockFirstOfJanuary2016(); //current timestamp

        final String messageId = UUID.randomUUID().toString();
        final long receivedTime = FIVE_MINUTES_BEFORE_FIRST_OF_JANUARY_2016; //Received 5 min ago

        new Expectations() {{
            domibusPropertyProvider.getBooleanDomainProperty(DELETE_PAYLOAD_ON_SEND_FAILURE);
            result = true;

            userMessageLog.getSendAttempts();
            result = 2;

            userMessageLog.getSendAttemptsMax();
            result = 3;

            userMessageLog.getReceived();
            result = new Date(receivedTime);

            userMessageLog.getNotificationStatus();
            result = NotificationStatus.REQUIRED;

            userMessageLog.getMessageId();
            result = messageId;


            messageLogDao.findByMessageId(messageId, MSHRole.SENDING);
            result = userMessageLog;
        }};


        updateRetryLoggingService.updatePushedMessageRetryLogging(messageId, legConfiguration, null);

        assertEquals(3, userMessageLog.getSendAttempts());
        assertEquals(new Date(FIVE_MINUTES_BEFORE_FIRST_OF_JANUARY_2016), userMessageLog.getNextAttempt());

        new Verifications() {{
            messageLogService.setMessageAsSendFailure(userMessage, userMessageLog);
            messagingDao.clearPayloadData(messageId);
        }};

    }

    /**
     * Message was restored
     * Max retries limit reached
     * Expected result: MessageLogDao#setAsNotified() is called
     * MessageLogDao#setMessageAsSendFailure is called
     * MessagingDao#clearPayloadData() is called
     *
     * @throws Exception
     */
    @Test
    public void testUpdateRetryLogging_Restored_maxRetriesReached(@Injectable UserMessage userMessage,
                                                                  @Injectable UserMessageLog userMessageLog) throws Exception {
        new SystemMockFirstOfJanuary2016(); //current timestamp

        final String messageId = UUID.randomUUID().toString();
        final long receivedTime = ONE_HOUR_BEFORE_FIRST_OF_JANUARY_2016; //Received one hour ago
        final long restoredTime = FIVE_MINUTES_BEFORE_FIRST_OF_JANUARY_2016; //Restored 5 min ago

        new Expectations() {{
            domibusPropertyProvider.getBooleanDomainProperty(DELETE_PAYLOAD_ON_SEND_FAILURE);
            result = true;

            userMessageLog.getSendAttempts();
            result = 4;

            userMessageLog.getSendAttemptsMax();
            result = 5;

            userMessageLog.getReceived();
            result = new Date(receivedTime);

            userMessageLog.getNotificationStatus();
            result = NotificationStatus.REQUIRED;

            userMessageLog.getRestored();
            result = new Date(restoredTime);

            userMessageLog.getMessageId();
            result = messageId;

            messageLogDao.findByMessageId(messageId, MSHRole.SENDING);
            result = userMessageLog;
        }};

        updateRetryLoggingService.updatePushedMessageRetryLogging(messageId, legConfiguration, null);

        new Verifications() {{
            userMessageLog.setSendAttempts(5);
            userMessageLog.setNextAttempt(new Date(FIVE_MINUTES_BEFORE_FIRST_OF_JANUARY_2016));;
//            messageLogService.setMessageAsSendFailure(userMessage, userMessageLog);
//            messagingDao.clearPayloadData(messageId);
        }};

    }

    /**
     * Message was restored
     * NextAttempt is set correctly
     *
     * @throws Exception
     */
    @Test
    public void testUpdateRetryLogging_Restored() throws Exception {
        new SystemMockFirstOfJanuary2016(); //current timestamp

        final String messageId = UUID.randomUUID().toString();
        final long receivedTime = ONE_HOUR_BEFORE_FIRST_OF_JANUARY_2016; //Received one hour ago
        final long restoredTime = FIVE_MINUTES_BEFORE_FIRST_OF_JANUARY_2016; //Restored 5 min ago

        final UserMessageLog userMessageLog = new UserMessageLog();
        userMessageLog.setSendAttempts(2);
        userMessageLog.setSendAttemptsMax(6);
        userMessageLog.setReceived(new Date(receivedTime));
        userMessageLog.setNotificationStatus(NotificationStatus.REQUIRED);
        userMessageLog.setMessageId(messageId);
        userMessageLog.setRestored(new Date(restoredTime));

        new Expectations() {{
            messageLogDao.findByMessageId(messageId, MSHRole.SENDING);
            result = userMessageLog;
        }};


        updateRetryLoggingService.updatePushedMessageRetryLogging(messageId, legConfiguration, null);

        assertEquals(3, userMessageLog.getSendAttempts());
        assertEquals(new Date(FIVE_MINUTES_BEFORE_FIRST_OF_JANUARY_2016 + (RETRY_TIMEOUT_IN_MINUTES / RETRY_COUNT * 60 * 1000)), userMessageLog.getNextAttempt());

    }

    /**
     * Max retries limit reached
     * Timeout limit not reached
     * Notification is disabled
     * Expected result: MessageLogDao#setAsNotified() is not called
     * MessageLogDao#setMessageAsSendFailure is called
     * MessagingDao#clearPayloadData() is called
     *
     * @throws Exception
     */
    @Test
    public void testUpdateRetryLogging_maxRetriesReachedNotificationDisabled_ExpectedMessageStatus(@Injectable UserMessage userMessage,
                                                                                                   @Injectable UserMessageLog userMessageLog) throws Exception {
        new SystemMockFirstOfJanuary2016();

        final String messageId = UUID.randomUUID().toString();
        final long receivedTime = FIVE_MINUTES_BEFORE_FIRST_OF_JANUARY_2016; //Received 5 min ago

        new Expectations() {{
            domibusPropertyProvider.getBooleanDomainProperty(DELETE_PAYLOAD_ON_SEND_FAILURE);
            result = true;

            userMessageLog.getSendAttempts();
            result = 2;

            userMessageLog.getSendAttemptsMax();
            result = 3;

            userMessageLog.getReceived();
            result = new Date(receivedTime);

            userMessageLog.getNotificationStatus();
            result = NotificationStatus.NOT_REQUIRED;

            userMessageLog.getMessageId();
            result = messageId;

            messageLogDao.findByMessageId(messageId, MSHRole.SENDING);
            result = userMessageLog;
        }};

        updateRetryLoggingService.updatePushedMessageRetryLogging(messageId, legConfiguration, null);

        new Verifications() {{
            messagingDao.clearPayloadData(messageId);
            messageLogService.setMessageAsSendFailure(userMessage, userMessageLog);
            messageLogDao.setAsNotified(userMessageLog);
            times = 0;
        }};

    }

    /**
     * Max retries limit reached
     * Notification is disabled
     * Clear payload is default (false)
     * Expected result: MessagingDao#clearPayloadData is not called
     * MessageLogDao#setMessageAsSendFailure is called
     * MessageLogDao#setAsNotified() is not called
     *
     * @throws Exception
     */
    @Test
    public void testUpdateRetryLogging_maxRetriesReachedNotificationDisabled_ExpectedMessageStatus_ClearPayloadDisabled(@Injectable UserMessage userMessage,
                                                                                                                        @Injectable UserMessageLog userMessageLog) throws Exception {
        new SystemMockFirstOfJanuary2016();

        final String messageId = UUID.randomUUID().toString();
        final long receivedTime = FIVE_MINUTES_BEFORE_FIRST_OF_JANUARY_2016; //Received 5 min ago

        new Expectations() {{
            userMessageLog.getSendAttempts();
            result = 2;

            userMessageLog.getSendAttemptsMax();
            result = 3;

//            userMessageLog.getReceived();
//            result = new Date(receivedTime);

            userMessageLog.getNotificationStatus();
            result = NotificationStatus.NOT_REQUIRED;

            userMessageLog.getMessageId();
            result = messageId;

            messageLogDao.findByMessageId(messageId, MSHRole.SENDING);
            result = userMessageLog;
        }};

        updateRetryLoggingService.updatePushedMessageRetryLogging(messageId, legConfiguration, null);

        new Verifications() {{
            messagingDao.clearPayloadData(messageId);
            times = 0;
            messageLogService.setMessageAsSendFailure(userMessage, userMessageLog);
            messageLogDao.setAsNotified(userMessageLog);
            times = 0;
        }};

    }

    /**
     * Max retries limit not reached
     * Timeout limit reached
     * Notification is enabled
     * Expected result: MessagingDao#clearPayloadData is called
     * MessageLogDao#setMessageAsSendFailure is called
     * MessageLogDao#setAsNotified() is called
     *
     * @throws Exception
     */
    @Test
    public void testUpdateRetryLogging_timeoutNotificationEnabled_ExpectedMessageStatus(@Injectable UserMessage userMessage,
                                                                                        @Injectable UserMessageLog userMessageLog) throws Exception {
        new SystemMockFirstOfJanuary2016();

        final String messageId = UUID.randomUUID().toString();
        final long received = ONE_HOUR_BEFORE_FIRST_OF_JANUARY_2016; // received one hour ago

        new Expectations() {{
            domibusPropertyProvider.getBooleanDomainProperty(DELETE_PAYLOAD_ON_SEND_FAILURE);
            result = true;

            userMessageLog.getSendAttempts();
            result = 0;

            userMessageLog.getSendAttemptsMax();
            result = 3;

            userMessageLog.getNotificationStatus();
            result = NotificationStatus.REQUIRED;

            userMessageLog.getMessageId();
            result = messageId;

            messageLogDao.findByMessageId(messageId, MSHRole.SENDING);
            result = userMessageLog;
        }};


        updateRetryLoggingService.updatePushedMessageRetryLogging(messageId, legConfiguration, null);


        new Verifications() {{
            messageLogService.setMessageAsSendFailure(userMessage, userMessageLog);
            messagingDao.clearPayloadData(userMessage);
        }};

    }


    /**
     * Max retries limit not reached
     * Timeout limit reached
     * Notification is disableds
     * Expected result: MessagingDao#clearPayloadData is called
     * MessageLogDao#setMessageAsSendFailure is called
     * MessageLogDao#setAsNotified() is called
     *
     * @throws Exception
     */
    @Test
    public void testUpdateRetryLogging_timeoutNotificationDisabled_ExpectedMessageStatus(@Injectable UserMessage userMessage,
                                                                                         @Injectable UserMessageLog userMessageLog) throws Exception {
        new SystemMockFirstOfJanuary2016();

        final String messageId = UUID.randomUUID().toString();
        final long received = ONE_HOUR_BEFORE_FIRST_OF_JANUARY_2016; // received one hour ago

        new Expectations() {{
            domibusPropertyProvider.getBooleanDomainProperty(DELETE_PAYLOAD_ON_SEND_FAILURE);
            result = true;

            userMessageLog.getSendAttempts();
            result = 0;

            userMessageLog.getSendAttemptsMax();
            result = 3;

            userMessageLog.getReceived();
            result = new Date(received);

            userMessageLog.getNotificationStatus();
            result = NotificationStatus.REQUIRED;

            userMessageLog.getMessageId();
            result = messageId;

            messageLogDao.findByMessageId(messageId, MSHRole.SENDING);
            result = userMessageLog;
        }};


        updateRetryLoggingService.updatePushedMessageRetryLogging(messageId, legConfiguration, null);


        new Verifications() {{
            messageLogService.setMessageAsSendFailure(userMessage, userMessageLog);
            messagingDao.clearPayloadData(messageId);
        }};

    }

    /**
     * Max retries limit not reached
     * Timeout limit not reached
     * Expected result:
     * UserMessageLog#getMessageStatus() == WAITING_FOR_RETRY
     * UserMessageLog#getSendAttempts() == 1
     *
     * @throws Exception
     */
    @Test
    public void testUpdateRetryLogging_success_ExpectedMessageStatus() throws Exception {
        new SystemMockFirstOfJanuary2016();

        final String messageId = UUID.randomUUID().toString();
        final long received = FIVE_MINUTES_BEFORE_FIRST_OF_JANUARY_2016;


        final UserMessageLog userMessageLog = new UserMessageLog();
        userMessageLog.setSendAttempts(0);
        userMessageLog.setSendAttemptsMax(3);
        userMessageLog.setReceived(new Date(received));

        new Expectations() {{
            messageLogDao.findByMessageId(messageId, MSHRole.SENDING);
            result = userMessageLog;
        }};

        updateRetryLoggingService.updatePushedMessageRetryLogging(messageId, legConfiguration, null);

        assertEquals(MessageStatus.WAITING_FOR_RETRY, userMessageLog.getMessageStatus());
        assertEquals(1, userMessageLog.getSendAttempts());

    }

    @Test
    public void testMessageExpirationDate(@Mocked final MessageLog userMessageLog, @Mocked final LegConfiguration legConfiguration) throws InterruptedException {
        final int timeOut = 10;
        final long timeOutInMillis = 60000 * timeOut;
        int delay = domibusPropertyProvider.getIntegerProperty(updateRetryLoggingService.MESSAGE_EXPIRATION_DELAY);
        final long currentTime = System.currentTimeMillis() - (timeOutInMillis + delay);
        final Date expectedDate = new Date(currentTime + timeOutInMillis);
        new NonStrictExpectations() {{
            userMessageLog.getRestored();
            result = currentTime;
            legConfiguration.getReceptionAwareness().getRetryTimeout();
            result = timeOut;

        }};
        assertEquals(expectedDate, updateRetryLoggingService.getMessageExpirationDate(userMessageLog, legConfiguration));
        assertTrue(updateRetryLoggingService.isExpired(legConfiguration, userMessageLog));
    }

    private static class SystemMockFirstOfJanuary2016 extends MockUp<System> {
        @Mock
        public static long currentTimeMillis() {
            return SYSTEM_DATE_IN_MILLIS_FIRST_OF_JANUARY_2016;
        }
    }
}