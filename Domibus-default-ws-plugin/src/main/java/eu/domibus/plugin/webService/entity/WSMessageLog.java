package eu.domibus.plugin.webService.entity;

import eu.domibus.common.MessageStatus;
import eu.domibus.logging.DomibusLogger;
import eu.domibus.logging.DomibusLoggerFactory;

import javax.persistence.*;
import javax.xml.bind.annotation.XmlTransient;

/**
 * @author idragusa
 * @since 4.2
 */
@Entity
@Table(name = "WS_PLUGIN_TB_MESSAGE_LOG")
@NamedQueries({
        @NamedQuery(name = "WSMessageLog.findByMessageId",
                query = "select wsMessageLog from WSMessageLog wsMessageLog where wsMessageLog.messageId=:MESSAGE_ID"),
        @NamedQuery(name = "WSMessageLog.findAll",
                query = "from WSMessageLog"),
        @NamedQuery(name = "WSMessageLog.deleteByMessageId",
                query = "DELETE FROM WSMessageLog wsMessageLog where wsMessageLog.messageId=:MESSAGE_ID")
})
public class WSMessageLog {

    private static final DomibusLogger LOG = DomibusLoggerFactory.getLogger(WSMessageLog.class);

    @Id
    @XmlTransient
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "ID_PK")
    private long entityId;

    @Column(name = "MESSAGE_ID")
    private String messageId;

    public WSMessageLog() {
        this.messageId = null;
    }

    public WSMessageLog(MessageStatus messageStatus, String messageId) {
        this.messageId = messageId;
    }

    public long getEntityId() {
        return entityId;
    }

    public void setEntityId(long entityId) {
        this.entityId = entityId;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }
}
