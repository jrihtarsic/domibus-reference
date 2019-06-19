package eu.domibus.core.property.listeners;

import eu.domibus.property.DomibusPropertyChangeListener;
import eu.domibus.web.rest.validators.BaseBlacklistValidator;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BlacklistPropertyChangeListener implements DomibusPropertyChangeListener {

    @Autowired
    List<BaseBlacklistValidator> blacklistValidators;

    @Override
    public boolean handlesProperty(String propertyName) {
        return StringUtils.equalsIgnoreCase(propertyName, "domibus.userInput.blackList")
                || StringUtils.equalsIgnoreCase(propertyName, "domibus.userInput.whiteList");
    }

    @Override
    public void propertyValueChanged(String domainCode, String propertyName, String propertyValue) {
        blacklistValidators.forEach(v -> v.init());
    }
}