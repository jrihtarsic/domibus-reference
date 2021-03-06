package eu.domibus.common.services.impl;

import eu.domibus.api.message.UserMessageLogService;
import eu.domibus.api.multitenancy.DomainContextProvider;
import eu.domibus.api.multitenancy.DomainTaskExecutor;
import eu.domibus.api.pki.CertificateService;
import eu.domibus.api.property.DomibusPropertyProvider;
import eu.domibus.api.routing.BackendFilter;
import eu.domibus.api.usermessage.UserMessageService;
import eu.domibus.common.*;
import eu.domibus.common.dao.*;
import eu.domibus.common.exception.CompressionException;
import eu.domibus.common.exception.EbMS3Exception;
import eu.domibus.common.model.configuration.*;
import eu.domibus.common.model.logging.UserMessageLog;
import eu.domibus.common.services.MessagingService;
import eu.domibus.common.validators.MessagePropertyValidator;
import eu.domibus.common.validators.PayloadProfileValidator;
import eu.domibus.common.validators.PropertyProfileValidator;
import eu.domibus.core.message.fragment.MessageGroupDao;
import eu.domibus.core.message.fragment.MessageGroupEntity;
import eu.domibus.core.message.fragment.SplitAndJoinService;
import eu.domibus.core.nonrepudiation.NonRepudiationService;
import eu.domibus.core.payload.persistence.filesystem.PayloadFileStorageProvider;
import eu.domibus.core.pmode.PModeProvider;
import eu.domibus.core.replication.UIReplicationSignalService;
import eu.domibus.core.util.MessageUtil;
import eu.domibus.core.util.SoapUtil;
import eu.domibus.ebms3.common.model.ObjectFactory;
import eu.domibus.ebms3.common.model.Property;
import eu.domibus.ebms3.common.model.Service;
import eu.domibus.ebms3.common.model.*;
import eu.domibus.ebms3.common.model.mf.MessageFragmentType;
import eu.domibus.ebms3.receiver.BackendNotificationService;
import eu.domibus.logging.DomibusLogger;
import eu.domibus.logging.DomibusLoggerFactory;
import eu.domibus.plugin.validation.SubmissionValidationException;
import mockit.Expectations;
import mockit.Injectable;
import mockit.Tested;
import mockit.Verifications;
import mockit.integration.junit4.JMockit;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.soap.AttachmentPart;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.fail;

/**
 * @author Thomas Dussart
 * @author Catalin Enache
 * @since 3.3
 */
@RunWith(JMockit.class)
public class UserMessageHandlerServiceImplTest {

    private static final DomibusLogger LOGGER = DomibusLoggerFactory.getLogger(UserMessageHandlerServiceImplTest.class);

    @Tested
    UserMessageHandlerServiceImpl userMessageHandlerService;

    @Injectable
    SoapUtil soapUtil;

    @Injectable
    BackendNotificationService backendNotificationService;

    @Injectable
    protected NonRepudiationService nonRepudiationService;

    @Injectable
    MessagingDao messagingDao;

    @Injectable
    MessagingService messagingService;

    @Injectable
    SignalMessageDao signalMessageDao;

    @Injectable
    SignalMessageLogDao signalMessageLogDao;

    @Injectable
    MessageFactory messageFactory;

    @Injectable
    UserMessageLogDao userMessageLogDao;

    @Injectable
    UserMessageLogService userMessageLogService;

    @Injectable
    JAXBContext jaxbContextEBMS;

    @Injectable
    TransformerFactory transformerFactory;

    @Injectable
    PModeProvider pModeProvider;

    @Injectable
    TimestampDateFormatter timestampDateFormatter;

    @Injectable
    CompressionService compressionService;

    @Injectable
    MessageIdGenerator messageIdGenerator;

    @Injectable
    PayloadProfileValidator payloadProfileValidator;

    @Injectable
    PropertyProfileValidator propertyProfileValidator;

    @Injectable
    CertificateService certificateService;

    @Injectable
    SOAPMessage soapRequestMessage;

    @Injectable
    SOAPMessage soapResponseMessage;

    @Injectable
    RawEnvelopeLogDao rawEnvelopeLogDao;

    @Injectable
    protected UIReplicationSignalService uiReplicationSignalService;

    @Injectable
    protected MessageUtil messageUtil;

    @Injectable
    protected MessageGroupDao messageGroupDao;

    @Injectable
    protected UserMessageService userMessageService;

    @Injectable
    protected AS4ReceiptService as4ReceiptService;

    @Injectable
    DomainTaskExecutor domainTaskExecutor;

    @Injectable
    DomainContextProvider domainContextProvider;

    @Injectable
    DomibusPropertyProvider domibusPropertyProvider;

    @Injectable
    SplitAndJoinService splitAndJoinService;

    @Injectable
    PayloadFileStorageProvider storageProvider;

    @Injectable
    MessagePropertyValidator messagePropertyValidator;


    private static final String TEST_RESOURCES_DIR = "./src/test/resources";
    private static final String VALID_PMODE_CONFIG_URI = "samplePModes/domibus-configuration-valid.xml";
    private static final String PUSH_TESTCASE1_TC1ACTION = "pushTestcase1tc1Action";
    private static final String STRING_TYPE = "string";
    private static final String DEF_PARTY_TYPE = "urn:oasis:names:tc:ebcore:partyid-type:unregistered";
    private static final String RED = "red_gw";
    private static final String BLUE = "blue_gw";
    private static final String FINAL_RECEIPIENT_VALUE = "urn:oasis:names:tc:ebcore:partyid-type:unregistered:C4";

    protected Property createProperty(String name, String value, String type) {
        Property aProperty = new Property();
        aProperty.setValue(value);
        aProperty.setName(name);
        aProperty.setType(type);
        return aProperty;
    }

    @Test
    public void testCheckCharset_HappyFlow() throws EbMS3Exception {
        final Messaging messaging = new Messaging();
        UserMessage userMessage = new UserMessage();
        PayloadInfo payloadInfo = new PayloadInfo();
        PartInfo partInfo = new PartInfo();

        PartProperties partProperties = new PartProperties();
        partProperties.getProperties().add(createProperty("MimeType", "text/xml", STRING_TYPE));
        partInfo.setPartProperties(partProperties);

        payloadInfo.getPartInfo().add(partInfo);
        userMessage.setPayloadInfo(payloadInfo);
        messaging.setUserMessage(userMessage);

        try {
            userMessageHandlerService.checkCharset(messaging);
        } catch (Exception e) {
            fail("No exception was expected!! Should have been handled successfully");
        }
    }

    @Test
    public void testCheckCharset_InvalidCharset() {
        final Messaging messaging = new Messaging();
        UserMessage userMessage = new UserMessage();
        PayloadInfo payloadInfo = new PayloadInfo();
        PartInfo partInfo = new PartInfo();

        PartProperties partProperties = new PartProperties();
        partProperties.getProperties().add(createProperty("CharacterSet", "!#InvalidCharSet", STRING_TYPE));
        partInfo.setPartProperties(partProperties);

        payloadInfo.getPartInfo().add(partInfo);
        userMessage.setPayloadInfo(payloadInfo);
        messaging.setUserMessage(userMessage);

        try {
            userMessageHandlerService.checkCharset(messaging);
            fail("EBMS3Exception was expected!!");
        } catch (EbMS3Exception e) {
            Assert.assertEquals(ErrorCode.EbMS3ErrorCode.EBMS_0003, e.getErrorCode());
        }
    }


    @Test
    public void testInvoke_tc1Process_HappyFlow(@Injectable final BackendFilter matchingBackendFilter,
                                                @Injectable MessageFragmentType messageFragment,
                                                @Injectable LegConfiguration legConfiguration,
                                                @Injectable UserMessage userMessage,
                                                @Injectable Messaging messaging) throws Exception {

        final String pmodeKey = "blue_gw:red_gw:testService1:tc1Action:OAE:pushTestcase1tc1Action";

        new Expectations(userMessageHandlerService) {{
            legConfiguration.getReliability().getReplyPattern();
            result = ReplyPattern.RESPONSE;

            messaging.getUserMessage();
            result = userMessage;

            backendNotificationService.getMatchingBackendFilter(messaging.getUserMessage());
            result = matchingBackendFilter;

            userMessageHandlerService.handlePayloads(soapRequestMessage, userMessage);
            result = any;

            compressionService.handleDecompression(userMessage, legConfiguration);
            result = true;

            as4ReceiptService.generateReceipt(withAny(soapRequestMessage), messaging, ReplyPattern.RESPONSE, anyBoolean, anyBoolean, anyBoolean);
            result = soapResponseMessage;

            userMessageHandlerService.checkSelfSending(pmodeKey);
            result = false;
        }};

        userMessageHandlerService.handleNewUserMessage(legConfiguration, pmodeKey, soapRequestMessage, messaging, false);

        new Verifications() {{
            userMessageHandlerService.persistReceivedMessage(soapRequestMessage, legConfiguration, pmodeKey, messaging, messageFragment, anyString);
            backendNotificationService.notifyMessageReceived(matchingBackendFilter, messaging.getUserMessage());
//            as4ReceiptService.generateReceipt(withAny(soapRequestMessage), messaging, reliability, anyBoolean, false, false);
        }};
    }

    @Test
    public void testInvoke_tc1Process_SelfSending_HappyFlow(@Injectable final BackendFilter matchingBackendFilter,
                                                            @Injectable MessageFragmentType messageFragment,
                                                            @Injectable Reliability reliability,
                                                            @Injectable LegConfiguration legConfiguration,
                                                            @Injectable UserMessage userMessage,
                                                            @Injectable Messaging messaging) throws Exception {

        final String pmodeKey = "blue_gw:red_gw:testService1:tc1Action:OAE:pushTestcase1tc1Action";

        new Expectations(userMessageHandlerService) {{
            backendNotificationService.getMatchingBackendFilter(messaging.getUserMessage());
            result = matchingBackendFilter;

            messaging.getUserMessage().getMessageInfo().getMessageId();
            result = "1234";

            userMessageHandlerService.handlePayloads(soapRequestMessage, userMessage);
            result = any;

            compressionService.handleDecompression(userMessage, legConfiguration);
            result = true;
        }};

        userMessageHandlerService.handleIncomingMessage(legConfiguration, pmodeKey, soapRequestMessage, messaging, true, false, false);

        new Verifications() {{
            String capturedId = null;
            messaging.getUserMessage().getMessageInfo().setMessageId(capturedId = withCapture());

            Assert.assertEquals("1234" + UserMessageHandlerService.SELF_SENDING_SUFFIX, capturedId);
            userMessageHandlerService.checkCharset(messaging);
            messagePropertyValidator.validate(messaging, MSHRole.RECEIVING);
            userMessageHandlerService.persistReceivedMessage(soapRequestMessage, legConfiguration, pmodeKey, messaging, messageFragment, anyString);
            backendNotificationService.notifyMessageReceived(matchingBackendFilter, messaging.getUserMessage());
        }};
    }

    @Test
    public void testInvoke_TestMessage(@Injectable final BackendFilter matchingBackendFilter,
                                       @Injectable final LegConfiguration legConfiguration,
                                       @Injectable final Messaging messaging,
                                       @Injectable final UserMessage userMessage,
                                       @Injectable MessageFragmentType messageFragment) throws SOAPException, InvocationTargetException, NoSuchMethodException, IllegalAccessException, JAXBException, EbMS3Exception, TransformerException, IOException {

        final String pmodeKey = "blue_gw:red_gw:testService1:tc1Action:OAE:pushTestcase1tc1Action";
        new Expectations(userMessageHandlerService) {{
            legConfiguration.getReliability().getReplyPattern();
            result = ReplyPattern.RESPONSE;

            messaging.getUserMessage().getMessageInfo().getMessageId();
            result = "TestMessage123";

            messaging.getUserMessage();
            result = userMessage;

            userMessageHandlerService.checkCharset(withAny(messaging));
            result = any;

            legConfiguration.getReceptionAwareness().getDuplicateDetection();
            result = true;

            userMessageHandlerService.checkDuplicate(withAny(messaging));
            result = false;

            as4ReceiptService.generateReceipt(withAny(soapRequestMessage), messaging, ReplyPattern.RESPONSE, anyBoolean, anyBoolean, anyBoolean);
            result = soapResponseMessage;

            userMessageHandlerService.checkSelfSending(pmodeKey);
            result = false;
        }};

        userMessageHandlerService.handleNewUserMessage(legConfiguration, pmodeKey, soapRequestMessage, messaging, false);

        new Verifications() {{
            userMessageHandlerService.checkCharset(messaging);
            userMessageHandlerService.checkDuplicate(messaging);
            userMessageHandlerService.persistReceivedMessage(soapRequestMessage, legConfiguration, pmodeKey, messaging, messageFragment, anyString);
            times = 1;
//            backendNotificationService.notifyMessageReceived(matchingBackendFilter, messaging.getUserMessage());
//            times = 0;
            as4ReceiptService.generateReceipt(withAny(soapRequestMessage), messaging, ReplyPattern.RESPONSE, anyBoolean, false, false);
        }};
    }

    @Test
    public void test_HandlePayLoads_HappyFlowUsingEmptyCID(@Injectable final UserMessage userMessage,
                                                           @Injectable final Node bodyContent,
                                                           @Injectable final PartInfo partInfo,
                                                           @Injectable Property property1) throws SOAPException, TransformerConfigurationException {

        PartProperties partProperties = new PartProperties();
        partProperties.getProperties().add(property1);
        partInfo.setPartProperties(partProperties);
        List<Node> bodyContentNodeList = new ArrayList<>();
        bodyContentNodeList.add(bodyContent);
        final Iterator<Node> bodyContentNodeIterator = bodyContentNodeList.iterator();

        new Expectations() {{
            partInfo.getHref();
            result = "";
            userMessage.getPayloadInfo().getPartInfo();
            result = partInfo;
            soapRequestMessage.getSOAPBody().hasChildNodes();
            result = true;
            soapRequestMessage.getSOAPBody().getChildElements();
            result = bodyContentNodeIterator;
        }};

        try {
            userMessageHandlerService.handlePayloads(soapRequestMessage, userMessage);
            Assert.assertNotNull(partInfo.getPayloadDatahandler());
        } catch (EbMS3Exception | SOAPException | TransformerException e) {
            fail("No Errors expected in happy flow!");
        }
    }

    @Test
    public void test_HandlePayLoads_EmptyCIDAndBodyContent(@Injectable final UserMessage userMessage,
                                                           @Injectable final Node bodyContent,
                                                           @Injectable final PartInfo partInfo) throws SOAPException, TransformerConfigurationException {

        new Expectations() {{
            partInfo.getHref();
            result = "";
            userMessage.getPayloadInfo().getPartInfo();
            result = partInfo;
            soapRequestMessage.getSOAPBody().getChildElements();
            times = 0;
        }};
        try {
            userMessageHandlerService.handlePayloads(soapRequestMessage, userMessage);
            Assert.assertNull(partInfo.getPayloadDatahandler().getContentType());
        } catch (EbMS3Exception | SOAPException | TransformerException e) {
            fail("No Errors expected in the flow EmptyCID And BodyContent!");
        }
    }

    /**
     * For the Happy Flow the Unit test with full data is happening with the test - testInvoke_tc1Process().
     * This test is using mock objects.
     *
     * @param legConfiguration
     * @param messaging
     * @param userMessage
     * @param receiverParty
     * @param userMessageLog
     * @throws EbMS3Exception
     * @throws TransformerException
     * @throws SOAPException
     */
    @Test
    public void testPersistReceivedMessage_HappyFlow(@Injectable final LegConfiguration legConfiguration, @Injectable final Messaging messaging,
                                                     @Injectable final UserMessage userMessage, @Injectable final Party receiverParty,
                                                     @Injectable final UserMessageLog userMessageLog,
                                                     @Injectable MessageFragmentType messageFragment)
            throws EbMS3Exception, TransformerException, SOAPException {
        final String pmodeKey = "blue_gw:red_gw:testService1:tc1Action:OAE:pushTestcase1tc1Action";
        final String messageId = "TestMessageId123";

        new Expectations(userMessageHandlerService) {{
            messaging.getUserMessage();
            result = userMessage;

            compressionService.handleDecompression(userMessage, legConfiguration);
            result = true;

            pModeProvider.getReceiverParty(pmodeKey);
            result = receiverParty;

            userMessage.getMessageInfo().getMessageId();
            result = messageId;

        }};
        userMessageHandlerService.persistReceivedMessage(soapRequestMessage, legConfiguration, pmodeKey, messaging, null, "");

        new Verifications() {{
            userMessageHandlerService.handlePayloads(soapRequestMessage, userMessage);
            payloadProfileValidator.validate(messaging, pmodeKey);
            propertyProfileValidator.validate(messaging, pmodeKey);
            messagingService.storeMessage(messaging, MSHRole.RECEIVING, legConfiguration, anyString);
            uiReplicationSignalService.userMessageReceived(messageId);
        }};
    }

    /**
     * A single message having multiple PartInfo's with no or special cid.
     *
     * @param userMessage
     * @param bodyContent1
     * @throws SOAPException
     * @throws TransformerException
     */
    @Test
    public void test_HandlePayLoads_NullCIDMultiplePartInfo(@Injectable final UserMessage userMessage, @Injectable final Node bodyContent1)
            throws SOAPException, TransformerException {

        PartInfo partInfo1 = new PartInfo();
        partInfo1.setHref("");
        PartProperties partProperties = new PartProperties();
        Property property1 = new Property();
        property1.setName("MimeType");
        property1.setValue("text/xml");
        partProperties.getProperties().add(property1);
        partInfo1.setPartProperties(partProperties);

        PartInfo partInfo2 = new PartInfo();
        partInfo1.setHref("#1234");
        PartProperties partProperties2 = new PartProperties();
        Property property2 = new Property();
        property2.setName("MimeType");
        property2.setValue("text/xml");
        partProperties2.getProperties().add(property2);
        partInfo2.setPartProperties(partProperties2);

        final PayloadInfo payloadInfo = new PayloadInfo();
        payloadInfo.getPartInfo().add(partInfo1);
        payloadInfo.getPartInfo().add(partInfo2);

        List<Node> bodyContentNodeList = new ArrayList<>();
        bodyContentNodeList.add(bodyContent1);
        final Iterator<Node> bodyContentNodeIterator = bodyContentNodeList.iterator();

        new Expectations() {{
            userMessage.getPayloadInfo();
            result = payloadInfo;
            soapRequestMessage.getSOAPBody().hasChildNodes();
            result = true;
            soapRequestMessage.getSOAPBody().getChildElements();
            result = bodyContentNodeIterator;
        }};

        try {
            userMessageHandlerService.handlePayloads(soapRequestMessage, userMessage);
            fail("Expecting error that - More than one Partinfo referencing the soap body found!");
        } catch (EbMS3Exception e) {
            Assert.assertEquals(ErrorCode.EbMS3ErrorCode.EBMS_0003, e.getErrorCode());
        }
    }

    @Test
    public void testPersistReceivedMessage_ValidationException(@Injectable final LegConfiguration legConfiguration,
                                                               @Injectable final Messaging messaging,
                                                               @Injectable final UserMessage userMessage,
                                                               @Injectable final UserMessageLog userMessageLog,
                                                               @Injectable MessageFragmentType messageFragment)
            throws EbMS3Exception, TransformerException, SOAPException, JAXBException {
        final String pmodeKey = "blue_gw:red_gw:testService1:tc1Action:OAE:pushTestcase1tc1Action";

        new Expectations(userMessageHandlerService) {{
            messaging.getUserMessage();
            result = userMessage;

            compressionService.handleDecompression(userMessage, legConfiguration);
            result = true;

            propertyProfileValidator.validate(messaging, pmodeKey);
            result = new EbMS3Exception(ErrorCode.EbMS3ErrorCode.EBMS_0010, "Property missing exception", "Message Id", null);
        }};

        try {
            userMessageHandlerService.persistReceivedMessage(soapRequestMessage, legConfiguration, pmodeKey, messaging, null, "");
        } catch (Exception e) {
            Assert.assertTrue("Expecting Ebms3 exception", e instanceof EbMS3Exception);
            Assert.assertEquals(ErrorCode.EbMS3ErrorCode.EBMS_0010, ((EbMS3Exception) e).getErrorCode());
        }

        new Verifications() {{
            userMessageHandlerService.handlePayloads(soapRequestMessage, userMessage);
            payloadProfileValidator.validate(messaging, pmodeKey);
            propertyProfileValidator.validate(messaging, pmodeKey);
            messagingService.storeMessage(messaging, MSHRole.RECEIVING, legConfiguration, anyString);
            times = 0;
            userMessageLogDao.create(withAny(userMessageLog));
            times = 0;
        }};
    }


    @Test
    public void testPersistReceivedMessage_CompressionError(@Injectable final LegConfiguration legConfiguration,
                                                            @Injectable final Messaging messaging,
                                                            @Injectable final UserMessage userMessage,
                                                            @Injectable final Party receiverParty,
                                                            @Injectable final UserMessageLog userMessageLog,
                                                            @Injectable MessageFragmentType messageFragment)
            throws EbMS3Exception, TransformerException, SOAPException, JAXBException {
        final String pmodeKey = "blue_gw:red_gw:testService1:tc1Action:OAE:pushTestcase1tc1Action";

        new Expectations(userMessageHandlerService) {{
            messaging.getUserMessage();
            result = userMessage;

            compressionService.handleDecompression(userMessage, legConfiguration);
            result = true;

            messagingService.storeMessage(messaging, MSHRole.RECEIVING, legConfiguration, anyString);
            result = new CompressionException("Could not store binary data for message ", null);

            userMessage.getMessageInfo().getMessageId();
            result = "TestMessageId123";
        }};
        try {
            userMessageHandlerService.persistReceivedMessage(soapRequestMessage, legConfiguration, pmodeKey, messaging, null, "");
            fail("Exception for compression failure expected!");
        } catch (EbMS3Exception e) {
            Assert.assertEquals(ErrorCode.EbMS3ErrorCode.EBMS_0303, e.getErrorCode());
        }

        new Verifications() {{
            userMessageHandlerService.handlePayloads(soapRequestMessage, userMessage);
            payloadProfileValidator.validate(messaging, pmodeKey);
            propertyProfileValidator.validate(messaging, pmodeKey);
            messagingService.storeMessage(messaging, MSHRole.RECEIVING, legConfiguration, anyString);
            userMessageLogDao.create(withAny(userMessageLog));
            times = 0;
        }};
    }

    @Test
    public void test_HandlePayLoads_HappyFlowUsingCID(@Injectable final UserMessage userMessage, @Injectable final AttachmentPart attachmentPart1,
                                                      @Injectable final AttachmentPart attachmentPart2) {

        final PartInfo partInfo = new PartInfo();
        partInfo.setHref("cid:message");

        PartProperties partProperties = new PartProperties();
        Property property1 = new Property();
        property1.setName("MimeType");
        property1.setValue("text/xml");

        partProperties.getProperties().add(property1);
        partInfo.setPartProperties(partProperties);

        List<AttachmentPart> attachmentPartList = new ArrayList<>();
        attachmentPartList.add(attachmentPart1);
        attachmentPartList.add(attachmentPart2);
        final Iterator<AttachmentPart> attachmentPartIterator = attachmentPartList.iterator();

        new Expectations() {{
            userMessage.getPayloadInfo().getPartInfo();
            result = partInfo;

            soapRequestMessage.getAttachments();
            result = attachmentPartIterator;

            attachmentPart1.getContentId();
            result = "AnotherContentID";

            attachmentPart2.getContentId();
            result = "message";
        }};

        try {
            userMessageHandlerService.handlePayloads(soapRequestMessage, userMessage);
            Assert.assertNotNull(partInfo.getPayloadDatahandler());
            Assert.assertEquals(attachmentPart2.getDataHandler(), partInfo.getPayloadDatahandler());
        } catch (EbMS3Exception | SOAPException | TransformerException e) {
            fail("No Errors expected in happy flow!");
        }
    }

    @Test
    public void test_HandlePayLoads_NoPayloadFound(@Injectable final UserMessage userMessage, @Injectable final AttachmentPart attachmentPart1, @Injectable final AttachmentPart attachmentPart2) {

        final PartInfo partInfo = new PartInfo();
        partInfo.setHref("cid:message");

        PartProperties partProperties = new PartProperties();
        Property property1 = new Property();
        property1.setName("MimeType");
        property1.setValue("text/xml");

        partProperties.getProperties().add(property1);
        partInfo.setPartProperties(partProperties);

        List<AttachmentPart> attachmentPartList = new ArrayList<>();
        attachmentPartList.add(attachmentPart1);
        attachmentPartList.add(attachmentPart2);
        final Iterator<AttachmentPart> attachmentPartIterator = attachmentPartList.iterator();

        new Expectations() {{
            userMessage.getPayloadInfo().getPartInfo();
            result = partInfo;

            soapRequestMessage.getAttachments();
            result = attachmentPartIterator;

            attachmentPart1.getContentId();
            result = "AnotherContentID";

            attachmentPart2.getContentId();
            result = "message123";
        }};

        try {
            userMessageHandlerService.handlePayloads(soapRequestMessage, userMessage);
            fail("Expected Ebms3 exception that no matching payload was found!");
        } catch (EbMS3Exception e) {
            Assert.assertEquals(ErrorCode.EbMS3ErrorCode.EBMS_0011, e.getErrorCode());
        } catch (SOAPException | TransformerException e) {
            fail("Expected Ebms3 exception that no matching payload was found!");
        }

    }


    @Test
    public void testCheckTestMessage() {

        UserMessage userMessage = createSampleUserMessage();
        Assert.assertFalse("Expecting false for test message as valid data message is supplied ", userMessageHandlerService.checkTestMessage(userMessage));

        userMessage.getCollaborationInfo().getService().setValue(Ebms3Constants.TEST_SERVICE);
        userMessage.getCollaborationInfo().setAction(Ebms3Constants.TEST_ACTION);
        Assert.assertTrue("Expecting true for Check Test Message with modified data", userMessageHandlerService.checkTestMessage(userMessage));
    }

    @Test
    public void testGetFinalRecipientName() {
        final UserMessage userMessage = createSampleUserMessage();
        Assert.assertEquals(FINAL_RECEIPIENT_VALUE, userMessageHandlerService.getFinalRecipientName(userMessage));
    }

    @Test
    public void testCheckDuplicate(@Injectable final UserMessageLog userMessageLog) {
        new Expectations() {{
            userMessageLogDao.findByMessageId(withSubstring("1234"), MSHRole.RECEIVING);
            result = userMessageLog;

            userMessageLogDao.findByMessageId(anyString, MSHRole.RECEIVING);
            result = null;
        }};
        Messaging messaging1 = new Messaging();
        UserMessage userMessage1 = new UserMessage();
        MessageInfo messageInfo1 = new MessageInfo();
        messageInfo1.setMessageId("1234");
        userMessage1.setMessageInfo(messageInfo1);
        messaging1.setUserMessage(userMessage1);
        Assert.assertTrue("Expecting match in duplicate check", userMessageHandlerService.checkDuplicate(messaging1));

        Messaging messaging2 = new Messaging();
        UserMessage userMessage2 = new UserMessage();
        MessageInfo messageInfo2 = new MessageInfo();
        messageInfo2.setMessageId("4567");
        userMessage2.setMessageInfo(messageInfo2);
        messaging2.setUserMessage(userMessage2);
        Assert.assertFalse("Expecting not duplicate result", userMessageHandlerService.checkDuplicate(messaging2));
    }

    @Test
    public void testInvoke_DuplicateMessage(@Injectable final BackendFilter matchingBackendFilter,
                                            @Injectable final LegConfiguration legConfiguration,
                                            @Injectable final Messaging messaging,
                                            @Injectable final UserMessage userMessage,
                                            @Injectable MessageFragmentType messageFragment,
                                            @Injectable Reliability reliability)
            throws SOAPException, JAXBException, EbMS3Exception, TransformerException, IOException {

        final String pmodeKey = "blue_gw:red_gw:testService1:tc1Action:OAE:pushTestcase1tc1Action";

        new Expectations(userMessageHandlerService) {{
            legConfiguration.getReliability().getReplyPattern();
            result = ReplyPattern.RESPONSE;

            messaging.getUserMessage().getMessageInfo().getMessageId();
            result = "TestMessage123";

            userMessageHandlerService.checkCharset(withAny(messaging));
            result = any;

            legConfiguration.getReceptionAwareness().getDuplicateDetection();
            result = true;

            userMessageHandlerService.checkDuplicate(withAny(messaging));
            result = true;

            userMessageHandlerService.checkSelfSending(pmodeKey);
            result = false;

            as4ReceiptService.generateReceipt(withAny(soapRequestMessage), messaging, ReplyPattern.RESPONSE, anyBoolean, anyBoolean, anyBoolean);
            result = soapResponseMessage;
        }};

        userMessageHandlerService.handleNewUserMessage(legConfiguration, pmodeKey, soapRequestMessage, messaging, false);

        new Verifications() {{
            userMessageHandlerService.checkCharset(messaging);
            userMessageHandlerService.checkDuplicate(messaging);
            userMessageHandlerService.persistReceivedMessage(soapRequestMessage, legConfiguration, pmodeKey, messaging, messageFragment, anyString);
            times = 0;
            backendNotificationService.notifyMessageReceived(matchingBackendFilter, messaging.getUserMessage());
            times = 0;
            as4ReceiptService.generateReceipt(withAny(soapRequestMessage), messaging, ReplyPattern.RESPONSE, anyBoolean, anyBoolean, anyBoolean);
            backendNotificationService.notifyMessageReceivedFailure(withAny(new UserMessage()), (ErrorResult) any);
            times = 0;
        }};
    }

    @Test
    public void testInvoke_ErrorInNotifyingIncomingMessage(@Injectable final BackendFilter matchingBackendFilter,
                                                           @Injectable final LegConfiguration legConfiguration,
                                                           @Injectable final Messaging messaging,
                                                           @Injectable final UserMessage userMessage,
                                                           @Injectable MessageFragmentType messageFragment,
                                                           @Injectable Reliability reliability) throws SOAPException, InvocationTargetException, NoSuchMethodException, IllegalAccessException, JAXBException, EbMS3Exception, TransformerException {

        final String pmodeKey = "blue_gw:red_gw:testService1:tc1Action:OAE:pushTestcase1tc1Action";

        new Expectations(userMessageHandlerService) {{
            messaging.getUserMessage().getMessageInfo().getMessageId();
            result = "TestMessage123";

            legConfiguration.getReceptionAwareness().getDuplicateDetection();
            result = true;

            userMessageHandlerService.checkDuplicate(withAny(messaging));
            result = false;

            userMessageHandlerService.persistReceivedMessage(soapRequestMessage, legConfiguration, pmodeKey, messaging, messageFragment, anyString);
            result = any;

            userMessageHandlerService.checkSelfSending(pmodeKey);
            result = false;

            backendNotificationService.notifyMessageReceived(matchingBackendFilter, withAny(userMessage));
            result = new SubmissionValidationException("Error while submitting the message!!");
        }};
        try {
            userMessageHandlerService.handleNewUserMessage(legConfiguration, pmodeKey, soapRequestMessage, messaging, false);
        } catch (Exception e) {
            Assert.assertTrue("Expecting Ebms3exception!", e instanceof EbMS3Exception);
        }

        new Verifications() {{
            backendNotificationService.notifyMessageReceived(matchingBackendFilter, messaging.getUserMessage());
            as4ReceiptService.generateReceipt(withAny(soapRequestMessage), messaging, ReplyPattern.CALLBACK, anyBoolean, false, false);
            times = 0;
        }};
    }

    @Test
    public void test_checkSelfSending_DifferentAPs_False() throws Exception {
        final String pmodeKey = "blue_gw:red_gw:testService1:tc1Action:OAE:pushTestcase1tc1Action";

        final Configuration configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);
        final Party senderParty = getPartyFromConfiguration(configuration, BLUE);
        final Party receiverParty = getPartyFromConfiguration(configuration, RED);

        new Expectations() {{
            pModeProvider.getReceiverParty(pmodeKey);
            result = receiverParty;

            pModeProvider.getSenderParty(pmodeKey);
            result = senderParty;

        }};

        //tested method
        boolean selfSendingFlag = userMessageHandlerService.checkSelfSending(pmodeKey);
        Assert.assertFalse("expected result should be false", selfSendingFlag);
    }

    @Test
    public void test_checkSelfSending_SameAPs_True() throws Exception {
        final String pmodeKey = "blue_gw:red_gw:testService1:tc1Action:OAE:pushTestcase1tc1Action";

        final Configuration configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);
        final Party senderParty = getPartyFromConfiguration(configuration, BLUE);
        final Party receiverParty = getPartyFromConfiguration(configuration, BLUE);

        new Expectations() {{
            pModeProvider.getReceiverParty(pmodeKey);
            result = receiverParty;

            pModeProvider.getSenderParty(pmodeKey);
            result = senderParty;

        }};

        //tested method
        boolean selfSendingFlag = userMessageHandlerService.checkSelfSending(pmodeKey);
        Assert.assertTrue("expected result should be true", selfSendingFlag);
    }

    @Test
    public void test_checkSelfSending_DifferentAPsSameEndpoint_True() throws Exception {
        final String pmodeKey = "blue_gw:red_gw:testService1:tc1Action:OAE:pushTestcase1tc1Action";

        final Configuration configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);
        final Party senderParty = getPartyFromConfiguration(configuration, BLUE);
        final Party receiverParty = getPartyFromConfiguration(configuration, RED);
        receiverParty.setEndpoint(senderParty.getEndpoint().toLowerCase());

        new Expectations() {{
            pModeProvider.getReceiverParty(pmodeKey);
            result = receiverParty;

            pModeProvider.getSenderParty(pmodeKey);
            result = senderParty;
        }};

        //tested method
        boolean selfSendingFlag = userMessageHandlerService.checkSelfSending(pmodeKey);
        Assert.assertTrue("expected result should be true", selfSendingFlag);
    }

    public Configuration loadSamplePModeConfiguration(String samplePModeFileRelativeURI) throws JAXBException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        InputStream xmlStream = getClass().getClassLoader().getResourceAsStream(samplePModeFileRelativeURI);
        JAXBContext jaxbContext = JAXBContext.newInstance(Configuration.class);

        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        Configuration configuration = (Configuration) unmarshaller.unmarshal(xmlStream);
        Method m = configuration.getClass().getDeclaredMethod("preparePersist", null);
        m.setAccessible(true);
        m.invoke(configuration);

        return configuration;
    }

    public LegConfiguration getLegFromConfiguration(Configuration configuration, String legName) {
        LegConfiguration result = null;
        for (LegConfiguration legConfiguration1 : configuration.getBusinessProcesses().getLegConfigurations()) {
            if (StringUtils.equalsIgnoreCase(legName, legConfiguration1.getName())) {
                result = legConfiguration1;
            }
        }
        return result;
    }

    public Messaging createValidSampleResponseMessaging() throws ParserConfigurationException, IOException, SAXException, JAXBException {
        InputStream validAS4ResponseFile = getClass().getClassLoader().getResourceAsStream("dataset/as4/validAS4Response.xml");
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setNamespaceAware(true);
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        Document responseFileDocument = documentBuilder.parse(validAS4ResponseFile);
        Node messagingNode = responseFileDocument.getElementsByTagName("eb3:Messaging").item(0);

        Messaging messaging = JAXBContext.newInstance(Messaging.class).createUnmarshaller().unmarshal(messagingNode, Messaging.class).getValue();
        return messaging;
    }

    protected Messaging createDummyRequestMessaging() {
        Messaging messaging = new ObjectFactory().createMessaging();
        messaging.setUserMessage(createSampleUserMessage());
        messaging.getUserMessage().getMessageInfo().setMessageId("1234");
        return messaging;
    }

    protected UserMessage createSampleUserMessage() {
        UserMessage userMessage = new UserMessage();
        CollaborationInfo collaborationInfo = new CollaborationInfo();
        collaborationInfo.setAction("TC1Leg1");
        AgreementRef agreementRef = new AgreementRef();
        agreementRef.setValue("");
        collaborationInfo.setAgreementRef(agreementRef);
        Service service = new Service();
        service.setValue("bdx:noprocess");
        service.setType("tc1");
        collaborationInfo.setService(service);
        userMessage.setCollaborationInfo(collaborationInfo);
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.getProperty().add(createProperty("originalSender", "urn:oasis:names:tc:ebcore:partyid-type:unregistered:C1", STRING_TYPE));
        messageProperties.getProperty().add(createProperty("finalRecipient", FINAL_RECEIPIENT_VALUE, STRING_TYPE));
        userMessage.setMessageProperties(messageProperties);

        PartyInfo partyInfo = new PartyInfo();

        From from = new From();
        from.setRole("http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/initiator");

        PartyId sender = new PartyId();
        sender.setValue(BLUE);
        sender.setType(DEF_PARTY_TYPE);
        from.getPartyId().add(sender);
        partyInfo.setFrom(from);

        To to = new To();
        to.setRole("http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/responder");

        PartyId receiver = new PartyId();
        receiver.setValue(RED);
        receiver.setType(DEF_PARTY_TYPE);
        to.getPartyId().add(receiver);
        partyInfo.setTo(to);

        userMessage.setPartyInfo(partyInfo);

        PayloadInfo payloadInfo = new PayloadInfo();
        PartInfo partInfo = new PartInfo();
        partInfo.setHref("cid:message");

        PartProperties partProperties = new PartProperties();
        partProperties.getProperties().add(createProperty("text/xml", "MimeType", STRING_TYPE));
        partInfo.setPartProperties(partProperties);

        payloadInfo.getPartInfo().add(partInfo);
        userMessage.setPayloadInfo(payloadInfo);
        return userMessage;
    }

    public Party getPartyFromConfiguration(Configuration configuration, String partyName) {
        Party result = null;
        for (Party party : configuration.getBusinessProcesses().getParties()) {
            if (StringUtils.equalsIgnoreCase(partyName, party.getName())) {
                result = party;
            }
        }
        return result;
    }

    @Test
    public void testHandleIncomingSourceMessage(@Injectable final LegConfiguration legConfiguration,
                                                @Injectable final String pmodeKey,
                                                @Injectable final SOAPMessage request,
                                                @Injectable final Messaging messaging,
                                                @Injectable BackendFilter backendFilter
    ) throws SOAPException, TransformerException, EbMS3Exception, IOException {

        boolean selfSending = false;
        boolean messageExists = false;
        boolean testMessage = false;
        String backendName = "mybackend";

        new Expectations(userMessageHandlerService) {{
            backendNotificationService.getMatchingBackendFilter(messaging.getUserMessage());
            result = backendFilter;

            backendFilter.getBackendName();
            result = backendName;

            userMessageHandlerService.persistReceivedSourceMessage(request, legConfiguration, pmodeKey, messaging, null, backendName);
        }};

        userMessageHandlerService.handleIncomingSourceMessage(legConfiguration, pmodeKey, request, messaging, selfSending, messageExists, testMessage);

        new Verifications() {{
            backendNotificationService.notifyMessageReceived(backendFilter, messaging.getUserMessage());
        }};
    }

    @Test
    public void testPersistReceivedSourceMessage(@Injectable final LegConfiguration legConfiguration,
                                                 @Injectable final String pmodeKey,
                                                 @Injectable final SOAPMessage request,
                                                 @Injectable final Messaging messaging,
                                                 @Injectable final UserMessage userMessage,
                                                 @Injectable MessageFragmentType messageFragmentType) throws EbMS3Exception {
        String backendName = "mybackend";

        new Expectations(userMessageHandlerService) {{
            messaging.getUserMessage();
            result = userMessage;

            userMessageHandlerService.saveReceivedMessage(request, legConfiguration, pmodeKey, messaging, messageFragmentType, backendName, userMessage);
        }};

        userMessageHandlerService.persistReceivedSourceMessage(request, legConfiguration, pmodeKey, messaging, messageFragmentType, backendName);

        new Verifications() {{
            userMessage.setSplitAndJoin(true);
        }};
    }

    @Test
    public void testSaveReceivedMessage(@Injectable final LegConfiguration legConfiguration,
                                        @Injectable final String pmodeKey,
                                        @Injectable final SOAPMessage request,
                                        @Injectable final Messaging messaging,
                                        @Injectable final UserMessage userMessage,
                                        @Injectable Party to) throws EbMS3Exception {
        String backendName = "mybackend";
        String messageId = "123";
        String service = "myservice";
        String action = "myaction";
        String endpoint = "http://local";

        new Expectations() {{
            userMessage.getMessageInfo().getMessageId();
            result = messageId;

            userMessage.getCollaborationInfo().getService().getValue();
            result = service;

            userMessage.getCollaborationInfo().getAction();
            result = action;

            userMessage.isSourceMessage();
            result = true;

            userMessage.isUserMessageFragment();
            result = false;

            pModeProvider.getReceiverParty(pmodeKey);
            result = to;

            to.getEndpoint();
            result = endpoint;

            legConfiguration.getErrorHandling().isBusinessErrorNotifyConsumer();
            result = true;

            userMessage.getMpc();
            result = Ebms3Constants.DEFAULT_MPC;
        }};

        userMessageHandlerService.saveReceivedMessage(request, legConfiguration, pmodeKey, messaging, null, backendName, userMessage);

        new Verifications() {{
            payloadProfileValidator.validate(messaging, pmodeKey);
            times = 1;

            propertyProfileValidator.validate(messaging, pmodeKey);
            times = 1;

            messagingService.storeMessage(messaging, MSHRole.RECEIVING, legConfiguration, backendName);

            userMessageLogService.save(
                    messageId,
                    MessageStatus.RECEIVED.toString(),
                    NotificationStatus.REQUIRED.toString(),
                    MSHRole.RECEIVING.toString(),
                    0,
                    Ebms3Constants.DEFAULT_MPC,
                    backendName,
                    endpoint,
                    service,
                    action, true, false);
        }};

    }

    @Test
    public void testHandleMessageFragmentWithGroupAlreadyExisting(@Injectable UserMessage userMessage,
                                                                  @Injectable MessageFragmentType messageFragmentType,
                                                                  @Injectable MessageGroupEntity messageGroupEntity,
                                                                  @Injectable LegConfiguration legConfiguration) throws EbMS3Exception {
        new Expectations(userMessageHandlerService) {{
            messageGroupDao.findByGroupId(messageFragmentType.getGroupId());
            result = messageGroupEntity;

            userMessageHandlerService.validateUserMessageFragment(userMessage, messageGroupEntity, messageFragmentType, legConfiguration);
            userMessageHandlerService.addPartInfoFromFragment(userMessage, messageFragmentType);
        }};

        userMessageHandlerService.handleMessageFragment(userMessage, messageFragmentType, legConfiguration);

        new Verifications() {{
            messageGroupDao.create(messageGroupEntity);
            times = 0;

            userMessageHandlerService.validateUserMessageFragment(userMessage, messageGroupEntity, messageFragmentType, legConfiguration);
            times = 1;
        }};
    }

    @Test
    public void testHandleMessageFragmentWithGroupAlreadyExisting(@Injectable UserMessage userMessage,
                                                                  @Injectable MessageFragmentType messageFragmentType,
                                                                  @Injectable LegConfiguration legConfiguration) throws EbMS3Exception {
        new Expectations(userMessageHandlerService) {{
            messageGroupDao.findByGroupId(messageFragmentType.getGroupId());
            result = null;

            userMessageHandlerService.validateUserMessageFragment(userMessage, null, messageFragmentType, legConfiguration);
            userMessageHandlerService.addPartInfoFromFragment(userMessage, messageFragmentType);
        }};

        userMessageHandlerService.handleMessageFragment(userMessage, messageFragmentType, legConfiguration);

        new Verifications() {{
            messageGroupDao.create((MessageGroupEntity) any);

            userMessageHandlerService.validateUserMessageFragment(userMessage, null, messageFragmentType, legConfiguration);
            times = 1;
        }};
    }

    @Test
    public void testValidateUserMessageFragmentWithNoSplittingConfigured(@Injectable UserMessage userMessage,
                                                                         @Injectable MessageFragmentType messageFragmentType,
                                                                         @Injectable MessageGroupEntity messageGroupEntity,
                                                                         @Injectable LegConfiguration legConfiguration) {
        new Expectations() {{
            legConfiguration.getSplitting();
            result = null;
        }};

        try {
            userMessageHandlerService.validateUserMessageFragment(userMessage, messageGroupEntity, messageFragmentType, legConfiguration);
            fail("Not possible to use SplitAndJoin without PMode leg configuration");
        } catch (EbMS3Exception e) {
            Assert.assertEquals(e.getErrorCode(), ErrorCode.EbMS3ErrorCode.EBMS_0002);
        }
    }

    @Test
    public void testValidateUserMessageFragmentWithDatabaseStorage(@Injectable UserMessage userMessage,
                                                                   @Injectable MessageFragmentType messageFragmentType,
                                                                   @Injectable MessageGroupEntity messageGroupEntity,
                                                                   @Injectable LegConfiguration legConfiguration) {
        new Expectations() {{
            storageProvider.isPayloadsPersistenceInDatabaseConfigured();
            result = true;
        }};

        try {
            userMessageHandlerService.validateUserMessageFragment(userMessage, messageGroupEntity, messageFragmentType, legConfiguration);
            fail("Not possible to use SplitAndJoin with database payloads");
        } catch (EbMS3Exception e) {
            Assert.assertEquals(e.getErrorCode(), ErrorCode.EbMS3ErrorCode.EBMS_0002);
        }
    }

    @Test
    public void testValidateUserMessageFragmentWithRejectedGroup(@Injectable UserMessage userMessage,
                                                                 @Injectable MessageFragmentType messageFragmentType,
                                                                 @Injectable MessageGroupEntity messageGroupEntity,
                                                                 @Injectable LegConfiguration legConfiguration) {
        new Expectations() {{
            storageProvider.isPayloadsPersistenceInDatabaseConfigured();
            result = false;

            messageGroupEntity.getRejected();
            result = true;
        }};

        try {
            userMessageHandlerService.validateUserMessageFragment(userMessage, messageGroupEntity, messageFragmentType, legConfiguration);
        } catch (EbMS3Exception e) {
            Assert.assertEquals(e.getErrorCode(), ErrorCode.EbMS3ErrorCode.EBMS_0040);
        }
    }

    @Test
    public void testValidateUserMessageFragmentWithWrongFragmentsCount(@Injectable UserMessage userMessage,
                                                                       @Injectable MessageFragmentType messageFragmentType,
                                                                       @Injectable MessageGroupEntity messageGroupEntity,
                                                                       @Injectable LegConfiguration legConfiguration) {

        long totalFragmentCount = 5;
        new Expectations() {{
            storageProvider.isPayloadsPersistenceInDatabaseConfigured();
            result = false;

            messageGroupEntity.getRejected();
            result = false;

            messageGroupEntity.getFragmentCount();
            result = totalFragmentCount;

            messageFragmentType.getFragmentCount();
            result = 7;
        }};

        try {
            userMessageHandlerService.validateUserMessageFragment(userMessage, messageGroupEntity, messageFragmentType, legConfiguration);
        } catch (EbMS3Exception e) {
            Assert.assertEquals(e.getErrorCode(), ErrorCode.EbMS3ErrorCode.EBMS_0048);
        }
    }
}