package eu.domibus.core.payload.persistence.filesystem;

import eu.domibus.api.multitenancy.Domain;
import eu.domibus.api.property.DomibusPropertyProvider;
import mockit.Expectations;
import mockit.Injectable;
import mockit.Tested;
import mockit.Verifications;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.nio.file.Path;

/**
 * @author Soumya Chandran (azhikso)
 * @since 4.2
 */
public class PayloadFileStorageTest {

    @Injectable
    DomibusPropertyProvider domibusPropertyProvider;

    @Injectable
    File storageDirectory;

    @Tested
    PayloadFileStorage payloadFileStorage;

    @Test
    public void initFileSystemStorage(@Injectable Domain domain, @Injectable Path path) {

        final String location = "../domibus_blue/domibus/payload_storage";
        final String ATTACHMENT_STORAGE_LOCATION = "domibus.attachment.storage.location";

        new Expectations(payloadFileStorage) {{
            domibusPropertyProvider.getProperty(domain, ATTACHMENT_STORAGE_LOCATION);
            result = location;

            payloadFileStorage.createLocation(location);
            result = path;
        }};

        payloadFileStorage.initFileSystemStorage();

        new Verifications() {{
            path.toFile();
            times = 1;
        }};

    }

    @Test
    public void createLocationWithRelativePath() {

        final String location = "../domibus_blue/domibus/payload_storage";

        try {
            payloadFileStorage.createLocation(location);
        } catch (Exception ex) {
            Assert.assertEquals(ex.getMessage(), "Relative path ..\\domibus_blue\\domibus\\payload_storage is forbidden. Please provide absolute path for payload storage");
        }
    }

    @Test
    public void createLocationWithAbsolutePath() {

        final String location = "D:/domibus_tomcat/domibus_blue/domibus/payload_storage";

        try {
            payloadFileStorage.createLocation(location);
        } catch (Exception ex) {
            Assert.assertEquals(ex.getMessage(), "Write permission for payload folder D:\\domibus_tomcat\\domibus_blue\\domibus\\payload_storage is not granted.");
        }
    }

    @Test
    public void getStorageDirectory() {
        Assert.assertNull(payloadFileStorage.getStorageDirectory());
    }

    @Test
    public void getDomain(@Injectable Domain domain) {
        Assert.assertNotNull(payloadFileStorage.getDomain());

    }
}