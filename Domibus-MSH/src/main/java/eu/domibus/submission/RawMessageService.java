package eu.domibus.submission;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import eu.domibus.logging.DomibusLogger;
import eu.domibus.logging.DomibusLoggerFactory;
import eu.domibus.web.rest.testdb.TestDB;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


@Service
public class RawMessageService {

    private static final DomibusLogger LOG = DomibusLoggerFactory.getLogger(RawMessageService.class);

    @Autowired
    private MetricRegistry metricRegistry;

    @Autowired
    RawMessageDao rawMessageDao;

    public void updateRawMessage(RawMessage rawMessage, String type) {
        rawMessage.setRawEnvelope(rawMessage.getRawEnvelope().replace("HOLDER", "HOLDER" + type + System.currentTimeMillis()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveSync(RawMessage rawMessage, String name) {
        Counter counter = null;
        com.codahale.metrics.Timer.Context saveSyncTimerContext = null;
        saveSyncTimerContext = metricRegistry.timer(MetricRegistry.name(TestDB.class, name)).time();
        counter = metricRegistry.counter(MetricRegistry.name(TestDB.class, name + "_counter"));
        counter.inc();
        try {
            rawMessageDao.create(rawMessage);
        } finally {
            if (counter != null) {
                counter.dec();
            }
            if (saveSyncTimerContext != null) {
                saveSyncTimerContext.stop();
            }
        }
    }
}
