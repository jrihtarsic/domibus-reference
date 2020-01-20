package eu.domibus.core.nonrepudiation;

import eu.domibus.api.property.DomibusPropertyProvider;
import eu.domibus.common.dao.RawEnvelopeLogDao;
import eu.domibus.common.model.logging.RawEnvelopeLog;
import eu.domibus.core.util.SoapUtil;
import eu.domibus.ebms3.common.model.SignalMessage;
import eu.domibus.ebms3.common.model.UserMessage;
import eu.domibus.logging.DomibusLogger;
import eu.domibus.logging.DomibusLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.xml.soap.SOAPMessage;
import javax.xml.transform.TransformerException;

import static eu.domibus.api.property.DomibusPropertyMetadataManager.DOMIBUS_NONREPUDIATION_AUDIT_ACTIVE;

/**
 * @author Cosmin Baciu
 * @since 3.3
 */
@Service
public class NonRepudiationDefaultService implements NonRepudiationService {

    private static final DomibusLogger LOG = DomibusLoggerFactory.getLogger(NonRepudiationDefaultService.class);

    @Autowired
    protected DomibusPropertyProvider domibusPropertyProvider;

    @Autowired
    protected RawEnvelopeLogDao rawEnvelopeLogDao;

    @Autowired
    protected SoapUtil soapUtil;

    @Transactional(propagation = Propagation.SUPPORTS)
    @Override
    public void saveRequest(SOAPMessage request, UserMessage userMessage) {
        if (isNonRepudiationAuditDisabled()) {
            return;
        }

        try {
            String rawXMLMessage = soapUtil.getRawXMLMessage(request);
            LOG.debug("Persist raw XML envelope: " + rawXMLMessage);
            RawEnvelopeLog rawEnvelopeLog = new RawEnvelopeLog();
            if (userMessage != null) {
                rawEnvelopeLog.setMessageId(userMessage.getMessageInfo().getMessageId());
            }
            rawEnvelopeLog.setRawXML(rawXMLMessage);
            rawEnvelopeLog.setUserMessage(userMessage);
            LOG.info("saveRequest !!!!!!!!!!!!!!!!!!!!!!!!!! ********************************* ");
            rawEnvelopeLogDao.create(rawEnvelopeLog);
        } catch (TransformerException e) {
            LOG.warn("Unable to log the raw message XML due to: ", e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.SUPPORTS)
    public void saveResponse(SOAPMessage response, SignalMessage signalMessage) {
        if (isNonRepudiationAuditDisabled()) {
            return;
        }

        try {
            String rawXMLMessage = soapUtil.getRawXMLMessage(response);
            LOG.debug("Persist raw XML envelope: " + rawXMLMessage);
            RawEnvelopeLog rawEnvelopeLog = new RawEnvelopeLog();
            rawEnvelopeLog.setRawXML(rawXMLMessage);
            rawEnvelopeLog.setSignalMessage(signalMessage);
            LOG.info("saveResponse !!!!!!!!!!!!!!!!!!!!!!!!!! ********************************* ");
            rawEnvelopeLogDao.create(rawEnvelopeLog);
            LOG.info(" after create in saveResponse !!!!!!!!!!!!!!!!!!!!!!!!!! ********************************* ");
        } catch (TransformerException e) {
            LOG.warn("Unable to log the raw message XML due to: ", e);
        }
    }

    protected boolean isNonRepudiationAuditDisabled() {
        return !domibusPropertyProvider.getBooleanProperty(DOMIBUS_NONREPUDIATION_AUDIT_ACTIVE);
    }
}
