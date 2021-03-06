package eu.domibus.web.rest;

import eu.domibus.api.security.AuthType;
import eu.domibus.api.user.UserManagementException;
import eu.domibus.api.user.UserState;
import eu.domibus.common.services.PluginUserService;
import eu.domibus.core.converter.DomainCoreConverter;
import eu.domibus.core.csv.CsvCustomColumns;
import eu.domibus.core.csv.CsvExcludedItems;
import eu.domibus.core.csv.CsvService;
import eu.domibus.core.csv.CsvServiceImpl;
import eu.domibus.core.security.AuthenticationEntity;
import eu.domibus.ext.rest.ErrorRO;
import eu.domibus.logging.DomibusLogger;
import eu.domibus.logging.DomibusLoggerFactory;
import eu.domibus.web.rest.error.ErrorHandlerService;
import eu.domibus.web.rest.ro.PluginUserFilterRequestRO;
import eu.domibus.web.rest.ro.PluginUserRO;
import eu.domibus.web.rest.ro.PluginUserResultRO;
import org.apache.cxf.common.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Ion Perpegel
 * @since 4.0
 */
@RestController
@RequestMapping(value = "/rest/plugin")
@Validated
public class PluginUserResource extends BaseResource {

    private static final DomibusLogger LOG = DomibusLoggerFactory.getLogger(UserResource.class);

    @Autowired
    private PluginUserService pluginUserService;

    @Autowired
    private DomainCoreConverter domainConverter;

    @Autowired
    private CsvServiceImpl csvServiceImpl;

    @Autowired
    private ErrorHandlerService errorHandlerService;

    @ExceptionHandler({UserManagementException.class})
    public ResponseEntity<ErrorRO> handleUserManagementException(UserManagementException ex) {
        return errorHandlerService.createResponse(ex, HttpStatus.CONFLICT);
    }

    @GetMapping(value = {"/users"})
    public PluginUserResultRO findUsers(PluginUserFilterRequestRO request) {
        LOG.debug("Retrieving plugin users");

        Long count = pluginUserService.countUsers(request.getAuthType(), request.getAuthRole(), request.getOriginalUser(), request.getUserName());

        List<AuthenticationEntity> users;
        if (count > 0) {
            users = pluginUserService.findUsers(request.getAuthType(), request.getAuthRole(), request.getOriginalUser(), request.getUserName(),
                    request.getPageStart(), request.getPageSize());
        } else {
            users = new ArrayList<>();
        }

        return prepareResponse(users, count, request.getPageStart(), request.getPageSize());
    }

    @PutMapping(value = {"/users"})
    @ResponseStatus(value = HttpStatus.NO_CONTENT)
    public void updateUsers(@RequestBody @Valid List<PluginUserRO> userROs) {
        LOG.debug("Update plugin users was called: {}", userROs);

        List<PluginUserRO> addedUsersRO = userROs.stream().filter(u -> UserState.NEW.name().equals(u.getStatus())).collect(Collectors.toList());
        List<PluginUserRO> updatedUsersRO = userROs.stream().filter(u -> UserState.UPDATED.name().equals(u.getStatus())).collect(Collectors.toList());
        List<PluginUserRO> removedUsersRO = userROs.stream().filter(u -> UserState.REMOVED.name().equals(u.getStatus())).collect(Collectors.toList());

        List<AuthenticationEntity> addedUsers = domainConverter.convert(addedUsersRO, AuthenticationEntity.class);
        List<AuthenticationEntity> updatedUsers = domainConverter.convert(updatedUsersRO, AuthenticationEntity.class);
        List<AuthenticationEntity> removedUsers = domainConverter.convert(removedUsersRO, AuthenticationEntity.class);

        pluginUserService.updateUsers(addedUsers, updatedUsers, removedUsers);
    }

    /**
     * This method returns a CSV file with the contents of Plugin User table
     *
     * @return CSV file with the contents of Plugin User table
     */
    @GetMapping(path = "/csv")
    public ResponseEntity<String> getCsv(PluginUserFilterRequestRO request) {

        request.setPageStart(0);
        request.setPageSize(csvServiceImpl.getMaxNumberRowsToExport());
        // get list of users
        final PluginUserResultRO pluginUserROList = findUsers(request);

        return exportToCSV(pluginUserROList.getEntries(),
                PluginUserRO.class,
                CsvCustomColumns.PLUGIN_USER_RESOURCE.getCustomColumns(),
                CsvExcludedItems.PLUGIN_USER_RESOURCE.getExcludedItems(),
                "pluginusers");
    }

    @Override
    public CsvService getCsvService() {
        return csvServiceImpl;
    }

    /**
     * convert plugin users to PluginUserROs.
     *
     * @return a list of PluginUserROs and the pagination info
     */
    private PluginUserResultRO prepareResponse(List<AuthenticationEntity> users, Long count, int pageStart, int pageSize) {
        List<PluginUserRO> userROs = domainConverter.convert(users, PluginUserRO.class);

        // this is business, should be located somewhere else
        for (int i = 0; i < users.size(); i++) {
            PluginUserRO userRO = userROs.get(i);
            AuthenticationEntity entity = users.get(i);

            userRO.setStatus(UserState.PERSISTED.name());
            userRO.setPassword(null);
            if (StringUtils.isEmpty(userRO.getCertificateId())) {
                userRO.setAuthenticationType(AuthType.BASIC.name());
            } else {
                userRO.setAuthenticationType(AuthType.CERTIFICATE.name());
            }

            boolean isSuspended = !entity.isActive() && entity.getSuspensionDate() != null;
            userRO.setSuspended(isSuspended);
        }

        PluginUserResultRO result = new PluginUserResultRO();

        result.setEntries(userROs);
        result.setCount(count);
        result.setPage(pageStart);
        result.setPageSize(pageSize);

        return result;
    }

}
