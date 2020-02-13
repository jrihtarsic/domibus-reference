package eu.domibus.sti;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.uuid.NoArgGenerator;
import eu.domibus.common.model.org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.*;
import eu.domibus.plugin.webService.generated.BackendInterface;
import eu.domibus.plugin.webService.generated.LargePayloadType;
import eu.domibus.plugin.webService.generated.SubmitMessageFault;
import eu.domibus.plugin.webService.generated.SubmitRequest;
import eu.domibus.rest.client.ApiException;
import eu.domibus.rest.client.api.UsermessageApi;
import eu.domibus.rest.client.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;

import javax.activation.DataHandler;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Session;
import javax.ws.rs.core.MediaType;
import java.io.UnsupportedEncodingException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SenderService {

    private static final Logger LOG = LoggerFactory.getLogger(SenderService.class);

    private final static String ORIGINAL_SENDER = "originalSender";

    private final static String FINAL_RECIPIENT = "finalRecipient";

    private static final String CID_MESSAGE = "cid:message";

    private static final String MIME_TYPE = "MimeType";

    private static final String TEXT_XML = "text/xml";

    private final static String HAPPY_FLOW_MESSAGE_TEMPLATE = "<?xml version='1.0' encoding='UTF-8'?>\n" +
            "<response_to_message_id>\n" +
            " $messId\n" +
            "</response_to_message_id>\n" +
            "<dataset>\n" +
            "<record><id>1</id><first_name>Belita</first_name><last_name>MacMeanma</last_name><email>bmacmeanma0@alexa.com</email><gender>Female</gender><ip_address>211.210.105.141</ip_address></record><record><id>2</id><first_name>Delainey</first_name><last_name>Sarll</last_name><email>dsarll1@xinhuanet.com</email><gender>Male</gender><ip_address>172.215.113.41</ip_address></record><record><id>3</id><first_name>Rafaela</first_name><last_name>Jandel</last_name><email>rjandel2@usda.gov</email><gender>Female</gender><ip_address>176.76.130.69</ip_address></record><record><id>4</id><first_name>Fredrika</first_name><last_name>Dunbabin</last_name><email>fdunbabin3@google.com.br</email><gender>Female</gender><ip_address>28.5.174.234</ip_address></record><record><id>5</id><first_name>Othilie</first_name><last_name>Braniff</last_name><email>obraniff4@redcross.org</email><gender>Female</gender><ip_address>135.122.142.137</ip_address></record><record><id>6</id><first_name>Filmer</first_name><last_name>Wands</last_name><email>fwands5@newsvine.com</email><gender>Male</gender><ip_address>245.77.82.100</ip_address></record><record><id>7</id><first_name>Bernie</first_name><last_name>Le feaver</last_name><email>blefeaver6@usda.gov</email><gender>Male</gender><ip_address>142.226.208.76</ip_address></record><record><id>8</id><first_name>Dacy</first_name><last_name>Di Antonio</last_name><email>ddiantonio7@bbb.org</email><gender>Female</gender><ip_address>235.214.118.96</ip_address></record><record><id>9</id><first_name>Hobey</first_name><last_name>Di Pietro</last_name><email>hdipietro8@nps.gov</email><gender>Male</gender><ip_address>166.30.27.83</ip_address></record><record><id>10</id><first_name>Catha</first_name><last_name>Denkel</last_name><email>cdenkel9@princeton.edu</email><gender>Female</gender><ip_address>102.60.69.38</ip_address></record><record><id>11</id><first_name>Jeralee</first_name><last_name>Gorling</last_name><email>jgorlinga@google.ca</email><gender>Female</gender><ip_address>217.169.183.180</ip_address></record><record><id>12</id><first_name>Henrietta</first_name><last_name>Aloshechkin</last_name><email>haloshechkinb@umich.edu</email><gender>Female</gender><ip_address>128.2.221.166</ip_address></record><record><id>13</id><first_name>Georges</first_name><last_name>Veregan</last_name><email>gvereganc@seattletimes.com</email><gender>Male</gender><ip_address>117.64.187.183</ip_address></record><record><id>14</id><first_name>Dara</first_name><last_name>Shottin</last_name><email>dshottind@weather.com</email><gender>Female</gender><ip_address>167.185.3.185</ip_address></record><record><id>15</id><first_name>Jerry</first_name><last_name>Attrill</last_name><email>jattrille@nps.gov</email><gender>Male</gender><ip_address>144.46.79.18</ip_address></record><record><id>16</id><first_name>Worth</first_name><last_name>Louche</last_name><email>wlouchef@vkontakte.ru</email><gender>Male</gender><ip_address>17.117.2.116</ip_address></record><record><id>17</id><first_name>Gabie</first_name><last_name>Fontel</last_name><email>gfontelg@nbcnews.com</email><gender>Female</gender><ip_address>94.216.217.36</ip_address></record><record><id>18</id><first_name>Stanton</first_name><last_name>Millott</last_name><email>smillotth@google.nl</email><gender>Male</gender><ip_address>6.194.119.179</ip_address></record><record><id>19</id><first_name>Hedi</first_name><last_name>Pele</last_name><email>hpelei@jiathis.com</email><gender>Female</gender><ip_address>198.140.7.33</ip_address></record><record><id>20</id><first_name>Nils</first_name><last_name>Klesl</last_name><email>nkleslj@woothemes.com</email><gender>Male</gender><ip_address>106.74.129.90</ip_address></record><record><id>21</id><first_name>Bucky</first_name><last_name>Hobbema</last_name><email>bhobbemak@livejournal.com</email><gender>Male</gender><ip_address>173.139.210.39</ip_address></record><record><id>22</id><first_name>Araldo</first_name><last_name>Claye</last_name><email>aclayel@elpais.com</email><gender>Male</gender><ip_address>116.15.8.224</ip_address></record><record><id>23</id><first_name>Jules</first_name><last_name>Heninghem</last_name><email>jheninghemm@biblegateway.com</email><gender>Male</gender><ip_address>196.24.132.34</ip_address></record><record><id>24</id><first_name>Trista</first_name><last_name>Kiloh</last_name><email>tkilohn@npr.org</email><gender>Female</gender><ip_address>108.148.209.172</ip_address></record><record><id>25</id><first_name>Clevie</first_name><last_name>Drinkall</last_name><email>cdrinkallo@blogtalkradio.com</email><gender>Male</gender><ip_address>63.122.167.93</ip_address></record><record><id>26</id><first_name>Monte</first_name><last_name>Deary</last_name><email>mdearyp@fc2.com</email><gender>Male</gender><ip_address>170.13.123.223</ip_address></record><record><id>27</id><first_name>Teresina</first_name><last_name>Keuning</last_name><email>tkeuningq@ask.com</email><gender>Female</gender><ip_address>29.193.166.64</ip_address></record><record><id>28</id><first_name>Noam</first_name><last_name>Muckley</last_name><email>nmuckleyr@cbc.ca</email><gender>Male</gender><ip_address>246.237.66.187</ip_address></record><record><id>29</id><first_name>Cordelia</first_name><last_name>Bussens</last_name><email>cbussenss@artisteer.com</email><gender>Female</gender><ip_address>102.234.75.160</ip_address></record><record><id>30</id><first_name>Henrik</first_name><last_name>Paffley</last_name><email>hpaffleyt@upenn.edu</email><gender>Male</gender><ip_address>246.79.215.136</ip_address></record><record><id>31</id><first_name>Branden</first_name><last_name>Stannett</last_name><email>bstannettu@yahoo.com</email><gender>Male</gender><ip_address>161.122.87.149</ip_address></record><record><id>32</id><first_name>Madelle</first_name><last_name>Drayton</last_name><email>mdraytonv@tmall.com</email><gender>Female</gender><ip_address>69.170.17.15</ip_address></record><record><id>33</id><first_name>Flemming</first_name><last_name>Hastie</last_name><email>fhastiew@statcounter.com</email><gender>Male</gender><ip_address>194.30.236.45</ip_address></record><record><id>34</id><first_name>Torrance</first_name><last_name>Mielnik</last_name><email>tmielnikx@home.pl</email><gender>Male</gender><ip_address>130.163.101.62</ip_address></record><record><id>35</id><first_name>Cinnamon</first_name><last_name>Trevor</last_name><email>ctrevory@boston.com</email><gender>Female</gender><ip_address>132.206.141.48</ip_address></record><record><id>36</id><first_name>Deanne</first_name><last_name>Gullen</last_name><email>dgullenz@rambler.ru</email><gender>Female</gender><ip_address>134.61.119.145</ip_address></record><record><id>37</id><first_name>Wyatan</first_name><last_name>Rudgard</last_name><email>wrudgard10@addthis.com</email><gender>Male</gender><ip_address>119.131.19.119</ip_address></record><record><id>38</id><first_name>Thomasa</first_name><last_name>Keme</last_name><email>tkeme11@storify.com</email><gender>Female</gender><ip_address>29.51.65.34</ip_address></record><record><id>39</id><first_name>Mead</first_name><last_name>Cobain</last_name><email>mcobain12@youtu.be</email><gender>Female</gender><ip_address>177.138.6.69</ip_address></record><record><id>40</id><first_name>Baillie</first_name><last_name>Sommerlie</last_name><email>bsommerlie13@home.pl</email><gender>Male</gender><ip_address>46.91.193.197</ip_address></record><record><id>41</id><first_name>Cindi</first_name><last_name>Waldocke</last_name><email>cwaldocke14@nature.com</email><gender>Female</gender><ip_address>211.123.179.43</ip_address></record><record><id>42</id><first_name>Sophie</first_name><last_name>Weddell</last_name><email>sweddell15@tiny.cc</email><gender>Female</gender><ip_address>92.79.6.93</ip_address></record><record><id>43</id><first_name>Faydra</first_name><last_name>Spata</last_name><email>fspata16@bloomberg.com</email><gender>Female</gender><ip_address>3.85.1.239</ip_address></record><record><id>44</id><first_name>Monte</first_name><last_name>Philipeau</last_name><email>mphilipeau17@examiner.com</email><gender>Male</gender><ip_address>49.233.30.244</ip_address></record><record><id>45</id><first_name>Garrott</first_name><last_name>Creer</last_name><email>gcreer18@webnode.com</email><gender>Male</gender><ip_address>253.166.143.212</ip_address></record><record><id>46</id><first_name>Harp</first_name><last_name>Wherrett</last_name><email>hwherrett19@squarespace.com</email><gender>Male</gender><ip_address>197.232.85.3</ip_address></record><record><id>47</id><first_name>Miller</first_name><last_name>Wilsee</last_name><email>mwilsee1a@wix.com</email><gender>Male</gender><ip_address>242.106.77.87</ip_address></record><record><id>48</id><first_name>Prentiss</first_name><last_name>Tucknott</last_name><email>ptucknott1b@wix.com</email><gender>Male</gender><ip_address>107.41.137.99</ip_address></record><record><id>49</id><first_name>Muffin</first_name><last_name>Mulkerrins</last_name><email>mmulkerrins1c@cisco.com</email><gender>Female</gender><ip_address>219.94.140.169</ip_address></record><record><id>50</id><first_name>Tamera</first_name><last_name>Skade</last_name><email>tskade1d@flavors.me</email><gender>Female</gender><ip_address>140.28.170.139</ip_address></record>\n" +
            "</dataset>";

    private JmsTemplate jmsTemplate;

    private BackendInterface backendInterface;

    private MetricRegistry metricRegistry;

    @Value("${send.with.jms}")
    private Boolean sendWithJms;

    protected UsermessageApi usermessageApi;

    protected NoArgGenerator uuidGenerator;

    public SenderService(JmsTemplate jmsTemplate,
                         BackendInterface backendInterface,
                         MetricRegistry metricRegistry,
                         UsermessageApi usermessageApi,
                         NoArgGenerator uuidGenerator) {
        this.jmsTemplate = jmsTemplate;
        this.backendInterface = backendInterface;
        this.metricRegistry = metricRegistry;
        this.usermessageApi = usermessageApi;
        this.uuidGenerator = uuidGenerator;
    }

    //@Async("threadPoolTaskExecutor")
    public void reverseAndSend(MapMessage mapMessage) {
        if (sendWithJms) {
            LOG.debug("Reverse and send message through jms in queue");
            jmsTemplate.send(session -> prepareResponse(mapMessage, session));
        } else {
            try {
                LOG.debug("Reverse and send message through webservice in queue");

                Submission submission = prepareSubmission(mapMessage);
                backendInterface.submitMessage(submission.getSubmitRequest(), submission.getMessaging());
            } catch (JMSException e) {
                LOG.error("Error preparing response message", e);
            } catch (SubmitMessageFault submitMessageFault) {
                LOG.error("Error submitting message", submitMessageFault);
            } catch (Exception e) {
                LOG.error("Error submitting message", e);
            }
        }
    }

    private MapMessage prepareResponse(MapMessage received, Session session) throws JMSException {

        MapMessage messageMap = session.createMapMessage();

        // Declare message as submit
        messageMap.setStringProperty("username", "plugin_admin");
        messageMap.setStringProperty("password", "123456");

        messageMap.setStringProperty("messageType", "submitMessage");
        messageMap.setStringProperty("messageId", uuidGenerator.generate().toString());
        final String messageId = received.getStringProperty("messageId");

        messageMap.setStringProperty("refToMessageId", messageId);

        messageMap.setStringProperty("service", "eu_ics2_c2t");
        messageMap.setStringProperty("agreementRef", "EU-ICS2-TI-V1.0");


        messageMap.setStringProperty("action", "IE3R01");
        messageMap.setStringProperty("fromPartyId", received.getStringProperty("toPartyId"));
        messageMap.setStringProperty("fromPartyType", received.getStringProperty("toPartyType")); // Mandatory

        messageMap.setStringProperty("fromRole", received.getStringProperty("toRole"));

        messageMap.setStringProperty("toPartyId", received.getStringProperty("fromPartyId"));
        messageMap.setStringProperty("toPartyType", received.getStringProperty("fromPartyType")); // Mandatory

        messageMap.setStringProperty("toRole", received.getStringProperty("fromRole"));

        messageMap.setStringProperty("originalSender", received.getStringProperty("finalRecipient"));
        messageMap.setStringProperty("finalRecipient", received.getStringProperty("originalSender"));
        messageMap.setStringProperty("protocol", "AS4");

        // messageMap.setJMSCorrelationID("12345");
        //Set up the payload properties
        messageMap.setStringProperty("totalNumberOfPayloads", "1");
        messageMap.setStringProperty("payload_1_description", "message");
        messageMap.setStringProperty("payload_1_mimeContentId", "cid:message");
        messageMap.setStringProperty("payload_1_mimeType", "text/xml");

        String response = HAPPY_FLOW_MESSAGE_TEMPLATE.replace("$messId", messageId);

        //messageMap.setStringProperty("p1InBody", "true"); // If true payload_1 will be sent in the body of the AS4 message. Only XML payloads may be sent in the AS4 message body. Optional

        //send the payload in the JMS message as byte array
        byte[] payload = response.getBytes();
        messageMap.setBytes("payload_1", payload);
        return messageMap;

    }

    private Submission prepareSubmission(MapMessage received) throws JMSException, ApiException {
        final String messageId = received.getStringProperty("messageId");

        UserMessageDTO userMessageDTO = usermessageApi.getUserMessage(messageId);//TODO put the payloads in the REST message
        List<PartInfoDTO> partInfo = userMessageDTO.getPayloadInfo().getPartInfo();
        for (PartInfoDTO partInfoDTO : partInfo) {
            String payload = partInfoDTO.getPayload();
            byte[] asBytes = Base64.getDecoder().decode(payload);
            try {
                String resultAsStringAgain = new String(asBytes, "utf-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }

        Optional<PropertyDTO> finalRecipientOptional = userMessageDTO.getMessageProperties().getProperty().stream().filter(propertyDTO -> "finalRecipient".equals(propertyDTO.getName())).findFirst();
        String finalRecipient = null;
        if(finalRecipientOptional.isPresent()) {
            finalRecipient = finalRecipientOptional.get().getValue();
        }

        Optional<PropertyDTO> originalSenderOptional = userMessageDTO.getMessageProperties().getProperty().stream().filter(propertyDTO -> "originalSender".equals(propertyDTO.getName())).findFirst();
        String originalSender = null;
        if(originalSenderOptional.isPresent()) {
            originalSender = originalSenderOptional.get().getValue();
        }
        FromDTO from = userMessageDTO.getPartyInfo().getFrom();
        String fromRole = from.getRole();
        ToDTO to = userMessageDTO.getPartyInfo().getTo();
        String toRole = to.getRole();

        String response = HAPPY_FLOW_MESSAGE_TEMPLATE.replace("$messId", messageId);


        //create payload.
        LargePayloadType payloadType = new LargePayloadType();
        payloadType.setPayloadId(CID_MESSAGE);
        payloadType.setContentType(MediaType.TEXT_XML);
        payloadType.setValue(getPayload(response, MediaType.TEXT_XML));

        //setup submit request.
        SubmitRequest submitRequest = new SubmitRequest();
        submitRequest.getPayload().add(payloadType);

        //setup messaging.
        Messaging messaging = new Messaging();
        UserMessage userMessage = new UserMessage();
        MessageInfo responseMessageInfo = new MessageInfo();
        //responseMessageInfo.setMessageId(UUID.randomUUID() + "@domibus");
        responseMessageInfo.setRefToMessageId(messageId);

        userMessage.setMessageInfo(responseMessageInfo);
        PartyInfo partyInfo = new PartyInfo();
        userMessage.setPartyInfo(partyInfo);


        From responseFrom = new From();
        responseFrom.setRole(toRole);
        partyInfo.setFrom(responseFrom);
        PartyId responseFromPartyId = new PartyId();

        PartyIdDTO toPartyIdDTO = to.getPartyId().stream().findFirst().get();
        final String toPartyId = toPartyIdDTO.getValue();
        final String toPartyIdType = toPartyIdDTO.getType();
        responseFromPartyId.setValue(toPartyId);
        responseFromPartyId.setType(toPartyIdType);
        responseFrom.setPartyId(responseFromPartyId);

        To responseTo = new To();
        responseTo.setRole(fromRole);
        partyInfo.setTo(responseTo);
        PartyId responseToPartyId = new PartyId();


        PartyIdDTO fromPartyIdDTO = from.getPartyId().stream().findFirst().get();
        responseToPartyId.setValue(fromPartyIdDTO.getValue());
        responseToPartyId.setType(fromPartyIdDTO.getType());
        responseTo.setPartyId(responseToPartyId);

        CollaborationInfo collaborationInfo = new CollaborationInfo();
        Service responseService = new Service();
        responseService.setValue("eu_ics2_c2t");
        AgreementRef agreementRef = new AgreementRef();
        agreementRef.setValue("EU-ICS2-TI-V1.0");
        collaborationInfo.setAgreementRef(agreementRef);
        collaborationInfo.setService(responseService);
        collaborationInfo.setAction("IE3R01");
        userMessage.setCollaborationInfo(collaborationInfo);

        MessageProperties responseMessageProperties = new MessageProperties();
        userMessage.setMessageProperties(responseMessageProperties);

        Property responseOriginalSender = new Property();
        responseMessageProperties.getProperty().add(responseOriginalSender);
        responseOriginalSender.setName(ORIGINAL_SENDER);
        responseOriginalSender.setValue(finalRecipient);

        Property responseFinalRecipient = new Property();
        responseMessageProperties.getProperty().add(responseFinalRecipient);
        responseFinalRecipient.setName(FINAL_RECIPIENT);
        responseFinalRecipient.setValue(originalSender);

        PayloadInfo responsePayloadInfo = new PayloadInfo();
        userMessage.setPayloadInfo(responsePayloadInfo);

        PartInfo responsePartInfo = new PartInfo();
        responsePayloadInfo.getPartInfo().add(responsePartInfo);
        responsePartInfo.setHref(CID_MESSAGE);
        PartProperties responsePartProperty = new PartProperties();
        Property responsePartInfoProperty = new Property();
        responsePartProperty.getProperty().add(responsePartInfoProperty);
        responsePartInfo.setPartProperties(responsePartProperty);
        responsePartInfoProperty.setName(MIME_TYPE);
        responsePartInfoProperty.setValue(TEXT_XML);
        messaging.setUserMessage(userMessage);
        return new Submission(submitRequest, messaging);
    }

    private DataHandler getPayload(final String payloadContent, final String mediaType) {
        javax.mail.util.ByteArrayDataSource dataSource = null;
        dataSource = new javax.mail.util.ByteArrayDataSource(org.apache.commons.codec.binary.Base64.encodeBase64(payloadContent.getBytes()), mediaType);
        dataSource.setName("content.xml");
        return new DataHandler(dataSource);
    }

    static class Submission {
        private SubmitRequest submitRequest;
        private Messaging messaging;

        public Submission(SubmitRequest submitRequest, Messaging messaging) {
            this.submitRequest = submitRequest;
            this.messaging = messaging;
        }

        public SubmitRequest getSubmitRequest() {
            return submitRequest;
        }

        public Messaging getMessaging() {
            return messaging;
        }
    }
}