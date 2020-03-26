package eu.domibus.wildfly12.server;

import eu.domibus.api.property.DomibusPropertyManager;
import eu.domibus.api.property.DomibusPropertyMetadata;
import eu.domibus.api.property.DomibusPropertyServiceDelegateAbstract;
import eu.domibus.ext.domain.Module;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static eu.domibus.jms.wildfly.InternalJMSManagerWildFlyArtemis.JMS_BROKER_PROPERTY;

/**
 * @author Ion Perpegel
 * @since 4.2
 * <p>
 * Property manager for the Wildfly Artemis specific properties.
 */
@Service("serverPropertyManager")
public class WildflyPropertyManager extends DomibusPropertyServiceDelegateAbstract
        implements DomibusPropertyManager {

    private Map<String, DomibusPropertyMetadata> knownProperties = Arrays.stream(new String[]{
            JMS_BROKER_PROPERTY
    })
            .map(name -> DomibusPropertyMetadata.getReadOnlyGlobalProperty(name, Module.WILDFLY_ARTEMIS))
            .collect(Collectors.toMap(x -> x.getName(), x -> x));

    @Override
    public Map<String, DomibusPropertyMetadata> getKnownProperties() {
        return knownProperties;
    }

}