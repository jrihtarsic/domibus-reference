package eu.domibus.core.alerts.configuration.password.imminent.console;

import eu.domibus.core.alerts.configuration.AlertConfigurationManager;
import eu.domibus.core.alerts.configuration.password.PasswordExpirationAlertModuleConfiguration;
import eu.domibus.core.alerts.model.common.AlertType;
import eu.domibus.core.alerts.model.service.ConfigurationLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Manages the reading of console user account password imminebt expiration alert configuration
 *
 * @author Ion Perpegel
 * @since 4.2
 */
@Service
public class ConsolePasswordImminentExpirationAlertConfigurationManager implements AlertConfigurationManager {

    @Autowired
    private ConsolePasswordImminentExpirationAlertConfigurationReader reader;

    @Autowired
    private ConfigurationLoader<PasswordExpirationAlertModuleConfiguration> loader;

    @Override
    public AlertType getAlertType() {
        return reader.getAlertType();
    }

    @Override
    public PasswordExpirationAlertModuleConfiguration getConfiguration() {
        return loader.getConfiguration(reader::readConfiguration);
    }

    @Override
    public void reset() {
        loader.resetConfiguration();
    }
}
