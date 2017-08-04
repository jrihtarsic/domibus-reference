package eu.domibus.plugin.fs;

import eu.domibus.logging.DomibusLogger;
import eu.domibus.logging.DomibusLoggerFactory;
import eu.domibus.plugin.Submission;
import eu.domibus.plugin.fs.ebms3.*;
import eu.domibus.plugin.transformer.MessageRetrievalTransformer;
import eu.domibus.plugin.transformer.MessageSubmissionTransformer;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.VFS;
import org.apache.commons.vfs2.util.FileObjectDataSource;
import org.springframework.stereotype.Component;

import javax.activation.DataHandler;
import javax.annotation.Resource;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;
import java.util.Set;

/**
 * This class is responsible for transformations from {@link FSMessage} to
 * {@link eu.domibus.plugin.Submission} and vice versa
 *
 * @author @author FERNANDES Henrique, GONCALVES Bruno
 */
@Component
public class FSMessageTransformer
        implements MessageRetrievalTransformer<FSMessage>, MessageSubmissionTransformer<FSMessage> {

    private static final DomibusLogger LOG = DomibusLoggerFactory.getLogger(FSMessageTransformer.class);
    
    private static final String DEFAULT_CONTENT_ID = "cid:message";
    private static final String DEFAULT_MIME_TYPE =  "text/xml";
    public static final String MIME_TYPE = "MimeType";
    public static final String CHARSET = "CharacterSet";

    @Resource(name = "fsPluginProperties")
    private FSPluginProperties fsPluginProperties;

    /**
     * The default properties to be used
     */
    private final Properties properties;
    
    /**
     * Creates a new <code>FSMessageTransformer</code>.
     */
    public FSMessageTransformer() {
        properties = new Properties();
    }
    
    /**
     * Creates a new <code>FSMessageTransformer</code> with the given properties.
     * 
     * @param defaultProperties Default properties
     * @throws java.io.IOException
     */
    public FSMessageTransformer(String defaultProperties) throws IOException {
        // TODO check what are these props
        properties = new Properties();
        properties.load(new FileReader(defaultProperties));
    }

    /**
     * Transforms {@link eu.domibus.plugin.Submission} to {@link FSMessage}
     *
     * @param submission the message to be transformed
     * @param messageOut output target
     *
     * @return result of the transformation as {@link FSMessage}
     */
    @Override
    public FSMessage transformFromSubmission(final Submission submission, final FSMessage messageOut) {
        UserMessage metadata = new UserMessage();
        metadata.setPartyInfo(getPartyInfoFromSubmission(submission));
        metadata.setCollaborationInfo(getCollaborationInfoFromSubmission(submission));
        metadata.setMessageProperties(getMessagePropertiesFromSubmission(submission));

        try {
            // TODO Properly handle file path in the right place (probably not here!)
            String filePath = fsPluginProperties.getLocation() + "/IN/" + submission.getMessageId() + ".txt";

            FileObject fileObject = getPayloadFromSubmission(submission, filePath);
            return new FSMessage(fileObject, metadata);
        } catch (IOException e) {
            LOG.error("Could not get the file from submission " + submission.getMessageId(), e);
        }
        return null;
    }

    /**
     * Transforms {@link FSMessage} to {@link eu.domibus.plugin.Submission}
     *
     * @param messageIn the message ({@link FSMessage}) to be tranformed
     * @return the result of the transformation as
     * {@link eu.domibus.plugin.Submission}
     */
    @Override
    public Submission transformToSubmission(final FSMessage messageIn) {
        UserMessage metadata = messageIn.getMetadata();
        Submission submission = new Submission();
        
        setPartyInfo(submission, metadata.getPartyInfo());
        setCollaborationInfo(submission, metadata.getCollaborationInfo());
        setMessageProperties(submission, metadata.getMessageProperties());
        setPayload(submission, messageIn.getFile());
        
        return submission;
    }

    private void setPayload(Submission submission, final FileObject file) {
        FileObjectDataSource dataSource = new FileObjectDataSource(file);
        
        ArrayList<Submission.TypedProperty> payloadProperties = new ArrayList<>(1);
        payloadProperties.add(new Submission.TypedProperty(MIME_TYPE, DEFAULT_MIME_TYPE));
        
        submission.addPayload(DEFAULT_CONTENT_ID, new DataHandler(dataSource), payloadProperties);
    }

    private FileObject getPayloadFromSubmission(Submission submission, String filePath) throws IOException {
        FileSystemManager fsManager = VFS.getManager();
        FileObject fileObject = fsManager.resolveFile(filePath);

        Set<Submission.Payload> payloads = submission.getPayloads();
        if (payloads.size() == 1) {
            Submission.Payload payload = payloads.iterator().next();
            Collection<Submission.TypedProperty> payloadProperties = payload.getPayloadProperties();

            DataHandler payloadDatahandler = payload.getPayloadDatahandler();
            payloadDatahandler.writeTo(fileObject.getContent().getOutputStream());
        } else {
            LOG.warn("Payloads size should be 1");
        }
        return fileObject;
    }

    private void setMessageProperties(Submission submission, MessageProperties messageProperties) {
        for (Property messageProperty : messageProperties.getProperty()) {
            String name = messageProperty.getName();
            String value = messageProperty.getValue();
            String type = messageProperty.getType();
            
            if (type != null) {
                submission.addMessageProperty(name, value, type);
            } else {
                submission.addMessageProperty(name, value);
            }
        }
    }

    private MessageProperties getMessagePropertiesFromSubmission(Submission submission) {
        MessageProperties messageProperties = new MessageProperties();

        for (Submission.TypedProperty typedProperty: submission.getMessageProperties()) {
            Property messageProperty = new Property();
            messageProperty.setType(typedProperty.getType());
            messageProperty.setName(typedProperty.getKey());
            messageProperty.setValue(typedProperty.getValue());
            messageProperties.getProperty().add(messageProperty);
        }
        return messageProperties;
    }

    private void setCollaborationInfo(Submission submission, CollaborationInfo collaborationInfo) {
        AgreementRef agreementRef = collaborationInfo.getAgreementRef();
        Service service = collaborationInfo.getService();
        
        if (agreementRef != null) {
            submission.setAgreementRef(agreementRef.getValue());
            submission.setAgreementRefType(agreementRef.getType());
        }
        submission.setService(service.getValue());
        submission.setServiceType(service.getType());
        submission.setAction(collaborationInfo.getAction());
        
        // TODO: is this bit needed?
        if (collaborationInfo.getConversationId() != null) {
            submission.setConversationId(collaborationInfo.getConversationId());
        }
    }

    private CollaborationInfo getCollaborationInfoFromSubmission(Submission submission) {
        AgreementRef agreementRef = new AgreementRef();
        agreementRef.setType(submission.getAgreementRef());
        agreementRef.setType(submission.getAgreementRefType());

        Service service = new Service();
        service.setType(submission.getServiceType());
        service.setValue(submission.getService());

        CollaborationInfo collaborationInfo = new CollaborationInfo();
        collaborationInfo.setAgreementRef(agreementRef);
        collaborationInfo.setService(service);
        collaborationInfo.setAction(submission.getAction());
        collaborationInfo.setConversationId(submission.getConversationId());

        return collaborationInfo;
    }

    private void setPartyInfo(Submission submission, PartyInfo partyInfo) {
        From from = partyInfo.getFrom();
        To to = partyInfo.getTo();
        
        submission.addFromParty(from.getPartyId().getValue(), from.getPartyId().getType());
        submission.setFromRole(from.getRole());
        if (to != null) {
            submission.addToParty(to.getPartyId().getValue(), to.getPartyId().getType());
            submission.setToRole(to.getRole());
        }
    }

    private PartyInfo getPartyInfoFromSubmission(Submission submission) {
        // From
        Submission.Party fromParty = submission.getFromParties().iterator().next();
        String fromRole = submission.getFromRole();

        PartyId fromPartyId = new PartyId();
        fromPartyId.setType(fromParty.getPartyIdType());
        fromPartyId.setValue(fromParty.getPartyId());

        From from = new From();
        from.setPartyId(fromPartyId);
        from.setRole(fromRole);

        // To
        Submission.Party toParty = submission.getToParties().iterator().next();
        String toRole = submission.getToRole();

        PartyId toPartyId = new PartyId();
        toPartyId.setType(toParty.getPartyIdType());
        toPartyId.setValue(toParty.getPartyId());

        To to = new To();
        to.setPartyId(toPartyId);
        to.setRole(toRole);

        // PartyInfo
        PartyInfo partyInfo = new PartyInfo();
        partyInfo.setFrom(from);
        partyInfo.setTo(to);

        return partyInfo;
    }
}
