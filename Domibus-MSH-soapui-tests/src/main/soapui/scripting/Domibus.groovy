/**
 * Created by testTeam on 16/09/2016.
 */

import groovy.sql.Sql

import javax.swing.JOptionPane
import java.sql.SQLException

import static javax.swing.JOptionPane.showConfirmDialog
import groovy.util.AntBuilder
import com.eviware.soapui.support.GroovyUtils
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import groovy.json.JsonBuilder


class Domibus{
    def messageExchange = null;
    def context = null;
    def log = null;

    def allDomainsProperties = null
    def allDomains = null

    // sleepDelay value is increased from 2000 to 6000 because of pull request take longer ...
    def sleepDelay = 6000

    def dbConnections = [:]
    def blueDomainID = null //"C2Default"
    def redDomainID = null //"C3Default"
    def greenDomainID = null //"thirdDefault"
    def thirdGateway = "false"; def multitenancyModeC2 = 0; def multitenancyModeC3 = 0;

    static def defaultPluginAdminC2Default = "pluginAdminC2Default"
    static def defaultAdminDefaultPassword = "adminDefaultPassword"

    static def backup_file_sufix = "_backup_for_soapui_tests";
    static def DEFAULT_LOG_LEVEL = 0;
    static def DEFAULT_PASSWORD = "8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92";
    static def SUPER_USER="super";
    static def SUPER_USER_PWD="123456";
    static def DEFAULT_ADMIN_USER="admin";
    static def DEFAULT_ADMIN_USER_PWD="123456";
    static def TRUSTSTORE_PASSWORD="test123";
    static def MAX_LOGIN_BEFORE_LOCK = 5;
    static def XSFRTOKEN_C2=null;
    static def XSFRTOKEN_C3=null;
    static def XSFRTOKEN_C_Other=null;
    static def CLEAR_CACHE_COMMAND_TOMCAT = $/rmdir /S /Q ..\work & rmdir /S /Q ..\logs & del /S /Q ..\temp\* & FOR /D %p IN ("..\temp\*.*") DO rmdir /s /q "%p"  & rmdir /S /Q ..\webapps\domibus & rmdir /S /Q ..\conf\domibus\work/$;

        // Short constructor of the Domibus Class
        Domibus(log, messageExchange, context) {
        this.log = log
        this.messageExchange = messageExchange
        this.context = context
        this.allDomainsProperties = parseDomainProperties(context.expand('${#Project#allDomainsProperties}'))
        this.thirdGateway = context.expand('${#Project#thirdGateway}')
        this.blueDomainID = context.expand('${#Project#defaultBlueDomainID}')
        this.redDomainID = context.expand('${#Project#defaultRedDomainId}')
        this.greenDomainID = context.expand('${#Project#defaultGreenDomainID}')

/* Still not added as previous values was used in static context
            this.SUPER_USER = context.expand('${#Project#superAdminUsername}')
        this.SUPER_USER_PWD = context.expand('${#Project#superAdminPassword}')
        this.DEFAULT_ADMIN_USER = context.expand('${#Project#defaultAdminUsername}')
        this.DEFAULT_ADMIN_USER_PWD = context.expand('${#Project#defaultAdminPassword}')
*/
        this.multitenancyModeC2 = getMultitenancyMode(context.expand('${#Project#multitenancyModeC2}'), log)
        this.multitenancyModeC3 = getMultitenancyMode(context.expand('${#Project#multitenancyModeC3}'), log)
    }

        // Class destructor
        void finalize() {
        closeAllDbConnections()
        log.debug "Domibus class not needed longer."
    }
//IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII
    // Log information wrapper
    static def void debugLog(logMsg, log,  logLevel = DEFAULT_LOG_LEVEL) {
        if (logLevel.toString() == "1" || logLevel.toString() == "true") log.info(logMsg)
    }

//---------------------------------------------------------------------------------------------------------------------------------
// Parse domain properties
        def parseDomainProperties(allDomainsPropertiesString) {
        debugLog("  ====  Calling \"returnDBproperties\".", log)
        debugLog("  parseDomainProperties  [][]  Parse properties for connection.", log)
        debugLog("  parseDomainProperties  [][]  All domain custom properties before parsing $allDomainsPropertiesString.", log)
        def mandatoryProperties = ["site", "domainName", "domNo", "dbType", "dbDriver", "dbJdbcUrl", "dbUser", "dbPassword"]

        def jsonSlurper = new JsonSlurper()
        def domPropMap = jsonSlurper.parseText(allDomainsPropertiesString)
        assert domPropMap != null
        // it's possible that the response wasn't in proper JSON format and is deserialized as empty
        assert !domPropMap.isEmpty()

        debugLog("  parseDomainProperties  [][]  Mandatory logs are: ${mandatoryProperties}.", log)

        domPropMap.each { domain, properties ->
            debugLog("  parseDomainProperties  [][]  Check mandatory properties are not null for domain ID: ${domain}", log)
            mandatoryProperties.each { propertyName ->
                assert(properties[propertyName] != null),"Error:returnDBproperties: \"${propertyName}\" property couldn't be retrieved for domain ID \"$domain\"." }
        }
        debugLog("  parseDomainProperties  [][]  DONE.", log)
        return domPropMap
    }
//---------------------------------------------------------------------------------------------------------------------------------
// Update Number of Domains for each site base on
// -------------------------------------------------------------------------------------------------------------------------------
def updateNumberOfDomain() {
     def numOfDomain = 0
     ["C2", "C3", "Third"].each {site ->
     	 numOfDomain=findNumberOfDomain(site)
     	 log.info "For ${site} number of defined additional domain is: ${numOfDomain}"
     	 context.testCase.testSuite.project.setPropertyValue("multitenancyMode${site}", numOfDomain as String)
     }
}


def findNumberOfDomain(String inputSite) {
	 def count=0
      debugLog( "  findNumberOfDomain  [][]  for site ID: ${inputSite}", log)
       allDomainsProperties.each { domain, properties ->
            if ((properties["site"].toLowerCase() == inputSite.toLowerCase()) && (properties["domNo"] != 0))
                 count++
        }
        return count
}


//IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII
//  DB Functions
//IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII
        // Connect to a schema
        def connectTo(String database, String driver, String url, String dbUser, String dbPassword) {
        debugLog("  ====  Calling \"connectTo\".", log)
        debugLog("  connectTo  [][]  Open connection to || DB: " + database + " || Url: " + url + " || Driver: " + driver + " ||", log)
        def sql = null;

        try {
            switch (database.toLowerCase()) {
            case  "mysql":
                GroovyUtils.registerJdbcDriver("com.mysql.cj.jdbc.Driver")
                sql = Sql.newInstance(url, dbUser, dbPassword, driver)
                break
            case "oracle":
                GroovyUtils.registerJdbcDriver("oracle.jdbc.driver.OracleDriver")
                sql = Sql.newInstance(url, dbUser, dbPassword, driver)
                break
            default:
                log.warn "Unknown type of DB"
                sql = Sql.newInstance(url, driver)
            }
            debugLog("  connectTo  [][]  Connection opened with success", log)
            return sql;
        } catch (SQLException ex) {
            log.error "  connectTo  [][]  Connection failed";
            assert 0,"SQLException occurred: " + ex;
        }
    }



//---------------------------------------------------------------------------------------------------------------------------------
        // Open all DB connections
        def openAllDbConnections() {
        debugLog("  ====  Calling \"openAllDbConnections\".", log)
        openDbConnections(allDomainsProperties.keySet())
    }

//---------------------------------------------------------------------------------------------------------------------------------
        // Open DB connections for provided list of domain defined by domain IDs
        def openDbConnections(domainIdList) {
        debugLog("  ====  Calling \"openDbConnections\" ${domainIdList}.", log)

        domainIdList.each { domainName ->
            def domain = retrieveDomainId(domainName)
            if (!dbConnections.containsKey(domain)) {
                debugLog("  openConnection  [][]  Open DB connection for domain ID: ${domain}", log)
                this.dbConnections[domain] = connectTo(allDomainsProperties[domain].dbType,
                                                       allDomainsProperties[domain].dbDriver,
                                                       allDomainsProperties[domain].dbJdbcUrl,
                                                       allDomainsProperties[domain].dbUser,
                                                       allDomainsProperties[domain].dbPassword)
            } else debugLog("  openConnection  [][]  DB connection for domain ID: ${domain} already open.", log)
        }
    }

//---------------------------------------------------------------------------------------------------------------------------------
        // Close all DB connections opened previously
        def closeAllDbConnections() {
        debugLog("  ====  Calling \"closeAllDbConnections\".", log)
        closeDbConnections(allDomainsProperties.keySet())
    }

//---------------------------------------------------------------------------------------------------------------------------------
        // Close all DB connections opened previously
        def closeDbConnections(domainIdList) {
        debugLog("  ====  Calling \"closeConnection\".", log)

        for (domainName in domainIdList) {
            def domID = retrieveDomainId(domainName)
            if (dbConnections.containsKey(domID)) {
                debugLog("  closeConnection  [][]  Close DB connection for domain ID: ${domID}", log)
                dbConnections[domID].connection.close()
                dbConnections.remove(domID)
            } else debugLog("  closeConnection  [][]  DB connection for domain ID: ${domID} was NOT open.", log)
        }
    }

//---------------------------------------------------------------------------------------------------------------------------------
        def executeListOfQueriesOnAllDB(String[] sqlQueriesList) {
        debugLog("  ====  Calling \"executeListOfQueriesOnAllDB\".", log)
        dbConnections.each { domainId, connection ->
            executeListOfSqlQueries(sqlQueriesList, domainId)
        }
    }
//---------------------------------------------------------------------------------------------------------------------------------
        // to be removed - invoked in SoapUI
        def executeListOfQueriesOnBlue(String[] sqlQueriesList) {
        debugLog("  ====  Calling \"executeListOfQueriesOnBlue\".", log)
        log.info "  executeListOfQueriesOnBlue  [][]  Executing SQL queries on sender/Blue"
        executeListOfSqlQueries(sqlQueriesList, blueDomainID)
    }
//---------------------------------------------------------------------------------------------------------------------------------
        // to be removed  - invoked in SoapUI
        def executeListOfQueriesOnRed(String[] sqlQueriesList) {
        debugLog("  ====  Calling \"executeListOfQueriesOnRed\".", log)
        log.info "  executeListOfQueriesOnRed  [][]  Executing SQL queries on receiver/Red"
        executeListOfSqlQueries(sqlQueriesList, redDomainID)
    }
//---------------------------------------------------------------------------------------------------------------------------------
        // to be removed  - NOT invoked in SoapUI
        def executeListOfQueriesOnGreen(String[] sqlQueriesList) {
        debugLog("  ====  Calling \"executeListOfQueriesOnGreen\".", log)
        log.info "  executeListOfQueriesOnGreen  [][]  Executing SQL queries on Third/Green"
        executeListOfSqlQueries(sqlQueriesList, greenDomainID)
    }
//---------------------------------------------------------------------------------------------------------------------------------
        def executeListOfQueriesOnMany(String[] sqlQueriesList, executeOnDomainIDList) {
        debugLog("  ====  Calling \"executeListOfQueriesOnMany\".", log)
        executeOnDomainIDList.each { domainID ->
            executeListOfSqlQueries(sqlQueriesList, domainID)
        }
    }
//---------------------------------------------------------------------------------------------------------------------------------
        def executeListOfSqlQueries(String[] sqlQueriesList, String inputTargetDomainID) {
        debugLog("  ====  Calling \"executeListOfSqlQueries\".", log)
        def connectionOpenedInsideMethod = false
        def targetDomainID = retrieveDomainId(inputTargetDomainID)

        if (!dbConnections.containsKey(targetDomainID)) {
            debugLog("  executeListOfSqlQueries  [][]  Method executed without DB connections open - try to open connection", log)
            openDbConnections([targetDomainID])
            connectionOpenedInsideMethod = true
        }

        for (query in sqlQueriesList) {
            debugLog("  executeListOfSqlQueries  [][]  Executing SQL query: " + query + " on domibus: " + targetDomainID, log)
            try {
                dbConnections[targetDomainID].execute query
            } catch (SQLException ex) {
                closeAllDbConnections()
                assert 0,"SQLException occured: " + ex;
            }
        }

        // Maybe this part is not needed as connection would be always close in class destructor
        if (connectionOpenedInsideMethod) {
            debugLog("  executeListOfSqlQueries  [][]  Connection to DB opened during method execution - close opened connection", log)
            closeDbConnections([targetDomainID])
        }
    }

//---------------------------------------------------------------------------------------------------------------------------------
        // Retrieve domain ID reference from provided name. When name exists use it
        def retrieveDomainId(String inputName) {
        debugLog("  ====  Calling \"retrieveDomainId\". With inputName: \"${inputName}\"", log)
        def domID = null;

        // For Backward compatibilty
        if (allDomainsProperties.containsKey(inputName)) {
            domID = inputName
        } else {
            switch (inputName.toUpperCase()) {
            case "C3":
            case "RED":
            case "RECEIVER":
                domID = this.redDomainID
                break
            case "C2":
            case "BLUE":
            case "SENDER":
                domID = this.blueDomainID
                break
            case "GREEN":
                assert(thirdGateway.toLowerCase().trim() == "true"), "\"GREEN\" schema is not active. Please set soapui project custom property \"thirdGateway\" to \"true\"."
                domID = this.greenDomainID
                break
            default:
                assert false, "Not supported domain ID ${inputName} provide for retrieveDomainId method. Not able to found it in allDomainsProperties nor common names list. "
                break
            }
        }
        debugLog("   retrieveDomainId  [][]  Input value ${inputName} translated to following domain ID: ${domID}", log)

        return domID as String
    }

        //---------------------------------------------------------------------------------------------------------------------------------
        // Clean all the messages from all defined for domains databases
        def cleanDatabaseAll() {
        debugLog("  ====  Calling \"cleanDatabaseAll\".", log)
        openAllDbConnections()
        cleanDatabaseForDomains(allDomainsProperties.keySet())
        closeAllDbConnections()
    }

//---------------------------------------------------------------------------------------------------------------------------------
        // Clean all the messages from the DB for provided list of domain defined by domain IDs
        def cleanDatabaseForDomains(domainIdList) {
        debugLog("  ====  Calling \"cleanDatabaseForDomains\" ${domainIdList}.", log)
        def sqlQueriesList = [
            "delete from TB_RAWENVELOPE_LOG",
            "delete from TB_RECEIPT_DATA",
            "delete from TB_PROPERTY",
            "delete from TB_PART_INFO",
            "delete from TB_PARTY_ID",
            "delete from TB_MESSAGING",
            "delete from TB_ERROR",
            "delete from TB_USER_MESSAGE",
            "delete from TB_SIGNAL_MESSAGE",
            "delete from TB_RECEIPT",
            "delete from TB_MESSAGE_INFO",
            "delete from TB_ERROR_LOG",
            "delete from TB_SEND_ATTEMPT",
            "delete from TB_MESSAGE_ACKNW_PROP",
            "delete from TB_MESSAGE_ACKNW",
            "delete from TB_MESSAGING_LOCK",
            "delete from TB_MESSAGE_LOG",
            "delete from TB_MESSAGE_UI"
        ] as String[]



        domainIdList.each { domainName ->
            def domain = retrieveDomainId(domainName)
            debugLog("  cleanDatabaseForDomains  [][]  Clean DB for domain ID: ${domain}", log)
            executeListOfSqlQueries(sqlQueriesList, domain)
        }

        log.info "  cleanDatabaseAll  [][]  Cleaning Done"
    }



//---------------------------------------------------------------------------------------------------------------------------------
        // Clean single message identified by messageID starting with provided value from ALL defined DBs
        def cleanDBMessageIDStartsWith(String messageID) {
        debugLog("  ====  Calling \"cleanDBMessageIDStartsWith\".", log)
        cleanDBMessageID(messageID, true)
    }

//---------------------------------------------------------------------------------------------------------------------------------
        // Clean single message identified by messageID starting with provided value from provided list of domains
        def cleanDBMessageIDStartsWithForDomains(String messageID, domainIdList) {
        debugLog("  ====  Calling \"cleanDBMessageIDStartsWith\".", log)
        cleanDBMessageIDForDomains(messageID, domainIdList, true)
    }

//---------------------------------------------------------------------------------------------------------------------------------
        // Clean single message identified by ID
        def cleanDBMessageID(String messageID, boolean  messgaeIDStartWithProvidedValue = false) {
        debugLog("  ====  Calling \"cleanDBMessageID\".", log)
        log.info "  cleanDBMessageID  [][]  Clean from DB information related to the message with ID: " + messageID
        openAllDbConnections()
        cleanDBMessageIDForDomains(messageID, allDomainsProperties.keySet(), messgaeIDStartWithProvidedValue)
        closeAllDbConnections()
    }
//---------------------------------------------------------------------------------------------------------------------------------
        // Clean single message identified by ID
        def cleanDBMessageIDForDomains(String messageID, domainIdList, boolean  messgaeIDStartWithProvidedValue = false) {
        debugLog("  ====  Calling \"cleanDBMessageIDForDomains\".", log)
        log.info "  cleanDBMessageIDForDomains  [][]  Clean from DB information related to the message with ID: " + messageID

        def messageIDCheck = "= '${messageID}'" //default comparison method use equal operator
        if (messgaeIDStartWithProvidedValue) messageIDCheck = "like '${messageID}%'" //if cleanDBMessageIDStartsWith method was called change method for comparison

        def select_ID_PK = "select ID_PK from TB_MESSAGE_INFO where MESSAGE_ID ${messageIDCheck}" //extracted as common part of queries bellow
        def sqlQueriesList = [
            "delete from TB_RAWENVELOPE_LOG where USERMESSAGE_ID_FK IN (select ID_PK from TB_USER_MESSAGE where messageInfo_ID_PK IN (" + select_ID_PK + "))",
            "delete from TB_RAWENVELOPE_LOG where SIGNALMESSAGE_ID_FK IN (select ID_PK from TB_SIGNAL_MESSAGE where messageInfo_ID_PK IN (" + select_ID_PK + " OR REF_TO_MESSAGE_ID " + messageIDCheck + "))",
			"delete from TB_RAWENVELOPE_LOG where MESSAGE_ID ${messageIDCheck}",
            "delete from TB_RECEIPT_DATA where RECEIPT_ID IN (select ID_PK from TB_RECEIPT where ID_PK IN(select receipt_ID_PK from TB_SIGNAL_MESSAGE where messageInfo_ID_PK IN (" + select_ID_PK + ")))",
			"delete from TB_RECEIPT_DATA where RECEIPT_ID IN (select ID_PK from TB_RECEIPT where ID_PK IN(select receipt_ID_PK from TB_SIGNAL_MESSAGE where messageInfo_ID_PK IN (select ID_PK from TB_MESSAGE_INFO where REF_TO_MESSAGE_ID ${messageIDCheck})))",
            "delete from TB_PROPERTY where MESSAGEPROPERTIES_ID IN (select ID_PK from TB_USER_MESSAGE where messageInfo_ID_PK IN (" + select_ID_PK + "))",
            "delete from TB_PROPERTY where PARTPROPERTIES_ID IN (select ID_PK from TB_PART_INFO where PAYLOADINFO_ID IN (select ID_PK from TB_USER_MESSAGE where messageInfo_ID_PK IN (" + select_ID_PK + ")))",
            "delete from TB_PART_INFO where PAYLOADINFO_ID IN (select ID_PK from TB_USER_MESSAGE where messageInfo_ID_PK IN (" + select_ID_PK + "))",
            "delete from TB_PARTY_ID where FROM_ID IN (select ID_PK from TB_USER_MESSAGE where messageInfo_ID_PK IN (" + select_ID_PK + "))",
            "delete from TB_PARTY_ID where TO_ID IN (select ID_PK from TB_USER_MESSAGE where messageInfo_ID_PK IN (" + select_ID_PK + "))",
            "delete from TB_MESSAGING where (SIGNAL_MESSAGE_ID IN (select ID_PK from TB_SIGNAL_MESSAGE where messageInfo_ID_PK IN (" + select_ID_PK + "))) OR (USER_MESSAGE_ID IN (select ID_PK from TB_USER_MESSAGE where messageInfo_ID_PK IN (" + select_ID_PK + ")))",
            "delete from TB_ERROR where SIGNALMESSAGE_ID IN (select ID_PK from TB_SIGNAL_MESSAGE where messageInfo_ID_PK IN (" + select_ID_PK + "))",
            "delete from TB_USER_MESSAGE where messageInfo_ID_PK IN (" + select_ID_PK + ")",
            "delete from TB_SIGNAL_MESSAGE where messageInfo_ID_PK IN (" + select_ID_PK + " OR REF_TO_MESSAGE_ID " + messageIDCheck + ")",
            "delete from TB_RECEIPT where ID_PK IN(select receipt_ID_PK from TB_SIGNAL_MESSAGE where messageInfo_ID_PK IN (" + select_ID_PK + "))",
            "delete from TB_MESSAGE_INFO where MESSAGE_ID " + messageIDCheck + " OR REF_TO_MESSAGE_ID " + messageIDCheck + "",
            "delete from TB_SEND_ATTEMPT where MESSAGE_ID " + messageIDCheck + "",
            "delete from TB_MESSAGE_ACKNW_PROP where FK_MSG_ACKNOWLEDGE IN (select ID_PK from TB_MESSAGE_ACKNW where MESSAGE_ID " + messageIDCheck + ")",
            "delete from TB_MESSAGE_ACKNW where MESSAGE_ID " + messageIDCheck + "",
            "delete from TB_MESSAGING_LOCK where MESSAGE_ID " + messageIDCheck + "",
            "delete from TB_MESSAGE_LOG where MESSAGE_ID " + messageIDCheck + "",
            "delete from TB_MESSAGE_UI where MESSAGE_ID " + messageIDCheck + ""
        ] as String[]

        domainIdList.each { domainName ->
            def domain = retrieveDomainId(domainName)
            debugLog("  cleanDBMessageIDForDomains  [][]  Clean DB for domain ID: ${domain}", log)
            executeListOfSqlQueries(sqlQueriesList, domain)
        }

    }
//IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII
//  Messages Info Functions
//IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII
        // Extract messageID from the request if it exists
        String findGivenMessageID() {
        debugLog("  ====  Calling \"findGivenMessageID\".", log)
        def messageID = null
        def requestContent = messageExchange.getRequestContentAsXml()
        def requestFile = new XmlSlurper().parseText(requestContent)
        requestFile.depthFirst().each {
            if (it.name() == "MessageId") {
                messageID = it.text().toLowerCase().trim()
            }
        }
        return (messageID)
    }
//---------------------------------------------------------------------------------------------------------------------------------
        // Extract messageID from the response
        String findReturnedMessageID() {
        debugLog("  ====  Calling \"findReturnedMessageID\".", log)
        def messageID = null
        def responseContent = messageExchange.getResponseContentAsXml()
        def responseFile = new XmlSlurper().parseText(responseContent)
        responseFile.depthFirst().each {
            if (it.name() == "messageID") {
                messageID = it.text()
            }
        }
        assert(messageID != null),locateTest(context) + "Error:findReturnedMessageID: The message ID is not found in the response"
        if ( (findGivenMessageID() != null) && (findGivenMessageID().trim() != "") ) {
            //if(findGivenMessageID()!=null){
            assert(messageID.toLowerCase() == findGivenMessageID().toLowerCase()),locateTest(context) + "Error:findReturnedMessageID: The message ID returned is (" + messageID + "), the message ID provided is (" + findGivenMessageID() + ")."
        }
        return (messageID.toLowerCase().trim())
    }
//---------------------------------------------------------------------------------------------------------------------------------
        // Verification of message existence
        def verifyMessagePresence(int presence1, int presence2, String IDMes = null, String senderDomainId = blueDomainID, String receiverDomanId =  redDomainID) {
        debugLog("  ====  Calling \"verifyMessagePresence\".", log)
        def messageID = null;
        def sqlSender = null; def sqlReceiver = null;
        sleep(sleepDelay)

        if (IDMes != null) {
            messageID = IDMes
        } else {
            messageID = findReturnedMessageID()
        }
        def total = 0
        debugLog("  verifyMessagePresence  [][]  senderDomainId=" + senderDomainId + " receiverDomaindId=" + receiverDomanId, log)
        sqlSender = retrieveSqlConnectionRefFromDomainId(senderDomainId)
        sqlReceiver = retrieveSqlConnectionRefFromDomainId(receiverDomanId)
        def usedDomains = [senderDomainId, receiverDomanId]
        openDbConnections(usedDomains)

        // Sender DB
        sqlSender.eachRow("Select count(*) lignes from TB_MESSAGE_LOG where REPLACE(LOWER(MESSAGE_ID),' ','') = REPLACE(LOWER(${messageID}),' ','')") {
            total = it.lignes
        }
        if (presence1 == 1) {
            //log.info "total = "+total
            assert(total > 0),locateTest(context) + "Error:verifyMessagePresence: Message with ID " + messageID + " is not found in sender side."
        }
        if (presence1 == 0) {
            assert(total == 0),locateTest(context) + "Error:verifyMessagePresence: Message with ID " + messageID + " is found in sender side."
        }

        // Receiver DB
        total = 0
        sleep(sleepDelay)
        sqlReceiver.eachRow("Select count(*) lignes from TB_MESSAGE_LOG where REPLACE(LOWER(MESSAGE_ID),' ','') = REPLACE(LOWER(${messageID}),' ','')") {
            total = it.lignes
        }
        if (presence2 == 1) {
            assert(total > 0),locateTest(context) + "Error:verifyMessagePresence: Message with ID " + messageID + " is not found in receiver side."
        }
        if (presence2 == 0) {
            assert(total == 0),locateTest(context) + "Error:verifyMessagePresence: Message with ID " + messageID + " is found in receiver side."
        }

        closeDbConnections(usedDomains)
    }
//---------------------------------------------------------------------------------------------------------------------------------
        // Verification of message unicity
        def verifyMessageUnicity(String IDMes = null, String senderDomainId = blueDomainID, String receiverDomanId =  redDomainID) {
        debugLog("  ====  Calling \"verifyMessageUnicity\".", log)
        sleep(sleepDelay)
        def messageID;
        def total = 0;
        def sqlSender = null; def sqlReceiver = null;

        if (IDMes != null) {
            messageID = IDMes
        } else {
            messageID = findReturnedMessageID()
        }
        debugLog("  verifyMessageUnicity  [][]  senderDomainId=" + senderDomainId + " receiverDomaindId=" + receiverDomanId, log)
        sqlSender = retrieveSqlConnectionRefFromDomainId(senderDomainId)
        sqlReceiver = retrieveSqlConnectionRefFromDomainId(receiverDomanId)
        def usedDomains = [senderDomainId, receiverDomanId]
        openDbConnections(usedDomains)

        sqlSender.eachRow("Select count(*) lignes from TB_MESSAGE_LOG where REPLACE(LOWER(MESSAGE_ID),' ','') = REPLACE(LOWER(${messageID}),' ','')") {
            total = it.lignes
        }
        assert(total == 1),locateTest(context) + "Error:verifyMessageUnicity: Message found " + total + " times in sender side."
        sleep(sleepDelay)
        sqlReceiver.eachRow("Select count(*) lignes from TB_MESSAGE_LOG where REPLACE(LOWER(MESSAGE_ID),' ','') = REPLACE(LOWER(${messageID}),' ','')") {
            total = it.lignes
        }
        assert(total == 1),locateTest(context) + "Error:verifyMessageUnicity: Message found " + total + " times in receiver side."
        closeDbConnections(usedDomains)
    }
//---------------------------------------------------------------------------------------------------------------------------------
        // Wait until status or timer expire
        def waitForStatus(String SMSH=null, String RMSH=null, String IDMes=null, String bonusTimeForSender=null, String bonusTimeForReceiver=null, String senderDomainId = blueDomainID, String receiverDomanId =  redDomainID) {
        debugLog("  ====  Calling \"waitForStatus\".", log)
        def MAX_WAIT_TIME=80_000; // Maximum time to wait to check the message status.
        def STEP_WAIT_TIME=1000; // Time to wait before re-checking the message status.
        def messageID = null;
        def numberAttempts = 0;
        def maxNumberAttempts = 5;
        def messageStatus = "INIT";
        def wait = false;
        def sqlSender = null; def sqlReceiver = null;

        //log.info "waitForStatus params: messageID: " + messageID + " RMSH: " + RMSH + " IDMes: " + IDMes + " bonusTimeForSender: " + bonusTimeForSender + " bonusTimeForReceiver: " + bonusTimeForReceiver
        if (IDMes != null) {
            messageID = IDMes
        } else {
            messageID = findReturnedMessageID()
        }

        log.info "  waitForStatus  [][]  params: messageID: " + messageID + " SMSH: " + SMSH + " RMSH: " + RMSH + " IDMes: " + IDMes + " bonusTimeForSender: " + bonusTimeForSender + " bonusTimeForReceiver: " + bonusTimeForReceiver

        if (bonusTimeForSender) {
            if (bonusTimeForSender.isInteger()) MAX_WAIT_TIME = (bonusTimeForSender as Integer) * 1000
            else MAX_WAIT_TIME = 500_000

            log.info "  waitForStatus  [][]  Waiting time for Sender extended to ${MAX_WAIT_TIME/1000} seconds"
        }

        debugLog("  waitForStatus  [][]  senderDomainId=" + senderDomainId + " receiverDomaindId=" + receiverDomanId, log)
        sqlSender = retrieveSqlConnectionRefFromDomainId(senderDomainId)
        sqlReceiver = retrieveSqlConnectionRefFromDomainId(receiverDomanId)
        def usedDomains = [senderDomainId, receiverDomanId]
        openDbConnections(usedDomains)

        if (SMSH) {
            while ( ( (messageStatus != SMSH) && (MAX_WAIT_TIME > 0) ) || (wait) ) {
                sleep(STEP_WAIT_TIME)
                if (MAX_WAIT_TIME > 0) {
                    MAX_WAIT_TIME = MAX_WAIT_TIME - STEP_WAIT_TIME
                }
                log.info "  waitForStatus  [][]  WAIT: " + MAX_WAIT_TIME
                sqlSender.eachRow("Select * from TB_MESSAGE_LOG where REPLACE(LOWER(MESSAGE_ID),' ','') = REPLACE(LOWER(${messageID}),' ','')") {
                    messageStatus = it.MESSAGE_STATUS
                    numberAttempts = it.SEND_ATTEMPTS
                }
                log.info "|MSG_ID: " + messageID + " | SENDER: Expected MSG Status =" + SMSH + "-- Current MSG Status = " + messageStatus + " | maxNumbAttempts: " + maxNumberAttempts + "-- numbAttempts: " + numberAttempts;
                if (SMSH == "SEND_FAILURE") {
                    if (messageStatus == "WAITING_FOR_RETRY") {
                        if ( ( (maxNumberAttempts - numberAttempts) > 0) && (!wait) ) {
                            wait = true
                        }
                        if ( (maxNumberAttempts - numberAttempts) <= 0) {
                            wait = false
                        }
                    } else {
                        if (messageStatus == SMSH) {
                            wait = false;
                        }
                    }
                }
            }
            log.info "  waitForStatus  [][]  finished checking sender, messageStatus: " + messageStatus + " MAX_WAIT_TIME: " + MAX_WAIT_TIME

            assert(messageStatus != "INIT"),locateTest(context) + "Error:waitForStatus: Message " + messageID + " is not present in the sender side."
            assert(messageStatus.toLowerCase() == SMSH.toLowerCase()),locateTest(context) + "Error:waitForStatus: Message in the sender side has status " + messageStatus + " instead of " + SMSH + "."
        }
        if (bonusTimeForReceiver) {
            if (bonusTimeForReceiver.isInteger()) MAX_WAIT_TIME = (bonusTimeForReceiver as Integer) * 1000
            else MAX_WAIT_TIME = 100_000

            log.info "  waitForStatus  [][]  Waiting time for Receiver extended to ${MAX_WAIT_TIME/1000} seconds"

        } else {
            MAX_WAIT_TIME = 30_000
        }
        messageStatus = "INIT"
        if (RMSH) {
            while ( (messageStatus != RMSH) && (MAX_WAIT_TIME > 0) ) {
                sleep(STEP_WAIT_TIME)
                MAX_WAIT_TIME = MAX_WAIT_TIME - STEP_WAIT_TIME
                sqlReceiver.eachRow("Select * from TB_MESSAGE_LOG where REPLACE(LOWER(MESSAGE_ID),' ','') = REPLACE(LOWER(${messageID}),' ','')") {
                    messageStatus = it.MESSAGE_STATUS
                }
                log.info "  waitForStatus  [][]  W:" + MAX_WAIT_TIME + " M:" + messageStatus
            }
            log.info "  waitForStatus  [][]  finished checking receiver, messageStatus: " + messageStatus + " MAX_WAIT_TIME: " + MAX_WAIT_TIME
            assert(messageStatus != "INIT"),locateTest(context) + "Error:waitForStatus: Message " + messageID + " is not present in the receiver side."
            assert(messageStatus.toLowerCase() == RMSH.toLowerCase()),locateTest(context) + "Error:waitForStatus: Message in the receiver side has status " + messageStatus + " instead of " + RMSH + "."
        }
        closeDbConnections(usedDomains)
    }
//---------------------------------------------------------------------------------------------------------------------------------
    // Check that an entry is created in the table TB_SEND_ATTEMPT
    def checkSendAttempt(String messageID, String targetSchema="BLUE"){
        debugLog("  ====  Calling \"checkSendAttempt\".", log)
        def MAX_WAIT_TIME=50_000
        def STEP_WAIT_TIME=2000
        def sqlSender = null
        int total = 0
        openAllDbConnections()

        sqlSender = retrieveSqlConnectionRefFromDomainId(targetSchema)

        while ( (MAX_WAIT_TIME > 0) && (total == 0) ) {
            sqlSender.eachRow("Select count(*) lignes from tb_send_attempt where REPLACE(LOWER(MESSAGE_ID),' ','') = REPLACE(LOWER(${messageID}),' ','')") {
                total = it.lignes
            }
            log.info "  checkSendAttempt  [][]  W: " + MAX_WAIT_TIME;
            sleep(STEP_WAIT_TIME)
            MAX_WAIT_TIME = MAX_WAIT_TIME - STEP_WAIT_TIME;
        }
        assert(total > 0),locateTest(context) + "Error: Message " + messageID + " is not present in the table tb_send_attempt."
        closeAllDbConnections()
    }
//---------------------------------------------------------------------------------------------------------------------------------
    static def getStatusRetriveStatus(log, context, messageExchange) {
        debugLog("  ====  Calling \"getStatusRetriveStatus\".", log)
        def outStatus = null
        def responseContent = messageExchange.getResponseContentAsXml()
        def requestFile = new XmlSlurper().parseText(responseContent)
        requestFile.depthFirst().each {
            if (it.name() == "getMessageStatusResponse") {
                outStatus = it.text()
            }
        }
        assert(outStatus != null),locateTest(context) + "Error:getStatusRetriveStatus: Not able to return status from response message"
        return outStatus
    }
//---------------------------------------------------------------------------------------------------------------------------------
        // Compare payloads order
        static def checkPayloadOrder(submitRequest, log, context, messageExchange){
        debugLog("  ====  Calling \"checkPayloadOrder\".", log)
        def requestAtts = [];
        def responseAtts = [];
        def i = 0;
        //def requestContent = messageExchange.getRequestContentAsXml()
        def requestContent = submitRequest;
        def responseContent = messageExchange.getResponseContentAsXml();;
        assert(requestContent != null),locateTest(context) + "Error: request is empty.";
        assert(responseContent != null),locateTest(context) + "Error: response is empty.";
        def parserFile = new XmlSlurper().parseText(requestContent)
        debugLog("===========================================", log)
        debugLog("  checkPayloadOrder  [][]  Attachments in request: ", log)
        parserFile.depthFirst().each {
            if (it.name() == "PartInfo") {
                requestAtts[i] = it.@href.text()
                debugLog("  checkPayloadOrder  [][]  Attachment: " + requestAtts[i] + " in position " + (i + 1) + ".", log)
                i++;
            }
        }
        debugLog("===========================================", log)
        debugLog("  checkPayloadOrder  [][]  Attachments in response: ", log)
        i = 0;
        parserFile = new XmlSlurper().parseText(responseContent)
        parserFile.depthFirst().each {
            if (it.name() == "PartInfo") {
                responseAtts[i] = it.@href.text()
                debugLog("  checkPayloadOrder  [][]  Attachment: " + responseAtts[i] + " in position " + (i + 1) + ".", log)
                i++;
            }
        }
        debugLog("===========================================", log)
        assert(requestAtts.size() == responseAtts.size()),locateTest(context) + "Error: request has " + requestAtts.size() + " attachements wheras response has " + responseAtts.size() + " attachements.";
        for (i = 0; i < requestAtts.size(); i++) {
            assert(requestAtts[i] == responseAtts[i]),locateTest(context) + "Error: in position " + (i + 1) + " request has attachment " + requestAtts[i] + " wheras response has attachment " + responseAtts[i] + ".";
        }
    }
//IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII
//  PopUP Functions
//IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII
    static def showPopUpForManualCheck(messagePrefix, log, testRunner) {
        debugLog("  ====  Calling \"showPopUpForManualCheck\".", log)
        def message = messagePrefix + """

		After the manual check of the expected result:
		- Click 'Yes' when result is correct.
		- Click 'No' when result is incorrect. 
		- Click 'Cancel' to skip this check."""

        def result = showConfirmDialog(null, message)
        if (result == JOptionPane.YES_OPTION)
        {
            log.info "PASS MANUAL TEST STEP: Result as expected, continuing the test."
        } else if (result == JOptionPane.NO_OPTION)
        {
            log.info "FAIL MANUAL TEST STEP: Manual check unsuccessful."
            testRunner.fail("Manual check indicated as unsuccessful by user.")
        } else if (result == JOptionPane.CANCEL_OPTION)
        {
            log.info "SKIP MANUAL TEST STEP: Check skipped bu user."
        }
    }
//---------------------------------------------------------------------------------------------------------------------------------
        static def showPopUpForManualConfigurationChange(messagePrefix, log, testRunner) {
        debugLog("  ====  Calling \"showPopUpForManualConfigurationChange\".", log)
        def message = messagePrefix + """

		Did configuration was changed?
		- Click 'Yes' when configuration was changed.
		- Click 'No' when configuration was not changed, this test step would be marked as failed.
		- Click 'Cancel' to skip this configuration change, the test would be continue from next test step."""

        def result = showConfirmDialog(null, message)
        if (result == JOptionPane.YES_OPTION)
        {
            log.info "User indicated configuration was changed as described in test step, continuing the test."
        } else if (result == JOptionPane.NO_OPTION)
        {
            log.info "User indicated configuration wasn't changed, this test step would be marked as failed."
            testRunner.fail("User indicated configuration wasn't changed, this test step would be marked as failed.")
        } else if (result == JOptionPane.CANCEL_OPTION)
        {
            log.info "This configuration changed was skipped, continue with next test step."
        }
    }
//IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII
//  Domibus Administration Functions
//IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII
        // Ping Gateway
        static def String pingMSH(String side, context, log) {
        debugLog("  ====  Calling \"pingMSH\".", log)
        def commandString = null;
        def commandResult = null;

        commandString = "curl -s -o /dev/null -w \"%{http_code}\" --noproxy localhost " + urlToDomibus(side, log, context) + "/services";
        commandResult = runCommandInShell(commandString, log)
        return commandResult[0].trim()
    }
//---------------------------------------------------------------------------------------------------------------------------------
        // Clear domibus cache
        static def clearCache(String side, context, log, String server = "tomcat") {
        debugLog("  ====  Calling \"clearCache\".", log)
        log.info "Cleaning cache for domibus " + side + " ...";
        def outputCatcher = new StringBuffer()
        def errorCatcher = new StringBuffer()
        def proc = null;
        def pathS = context.expand('${#Project#pathExeSender}')
        def pathR = context.expand('${#Project#pathExeReceiver}')
        def pathRG = context.expand('${#Project#pathExeGreen}')
        def commandToRun = null;
        switch (server.toLowerCase()) {
        case "tomcat":
            switch (side.toLowerCase()) {
            case "sender":
                log.info "PATH = " + pathS;
                commandToRun = "cmd /c cd ${pathS} && " + CLEAR_CACHE_COMMAND_TOMCAT;
                break;
            case "receiver":
                log.info "PATH = " + pathR;
                commandToRun = "cmd /c cd ${pathR} && " + CLEAR_CACHE_COMMAND_TOMCAT;
                break;
            case "receivergreen":
                log.info "PATH = " + pathRG;
                commandToRun = "cmd /c cd ${pathRG} && " + CLEAR_CACHE_COMMAND_TOMCAT;
                break;
            default:
                assert(false), "Unknown side.";
            }
            break;
        case "weblogic":
            log.info "  clearCache  [][]  I don't know how to clean in weblogic yet.";
            break;
        case "wildfly":
            log.info "  clearCache  [][]  I don't know how to clean in wildfly yet.";
            break;
        default:
            assert(false), "Unknown server.";
        }
        if (commandToRun) {
            proc = commandToRun.execute()
            if (proc != null) {
                proc.consumeProcessOutput(outputCatcher, errorCatcher)
                proc.waitFor()
            }
            debugLog("  clearCache  [][]  commandToRun = " + commandToRun, log)
            debugLog("  clearCache  [][]  outputCatcher = " + outputCatcher, log)
            debugLog("  clearCache  [][]  errorCatcher = " + errorCatcher, log)
            log.info "  clearCache  [][]  Cleaning should be done."
        }
    }
//---------------------------------------------------------------------------------------------------------------------------------
        // Start several gateways
        static def startSetMSHs(int dom1, int dom2, int dom3, context, log) {
        debugLog("  ====  Calling \"startSetMSHs\".", log)
        if (dom1 > 0) {
            startMSH("sender", context, log)
        }
        if (dom2 > 0) {
            startMSH("receiver", context, log)
        }
        if (dom3 > 0) {
            startMSH("receivergreen", context, log)
        }
    }
//---------------------------------------------------------------------------------------------------------------------------------
        // Start Gateway
        static def startMSH(String side, context, log){
        debugLog("  ====  Calling \"startMSH\".", log)
        def MAX_WAIT_TIME=150000; // Maximum time to wait for the domibus to start.
        def STEP_WAIT_TIME=2000; // Time to wait before re-checking the domibus status.
        def confirmation = 0;
        def outputCatcher = new StringBuffer()
        def errorCatcher = new StringBuffer()
        def pathS = context.expand('${#Project#pathExeSender}')
        def pathR = context.expand('${#Project#pathExeReceiver}')
        def pathRG = context.expand('${#Project#pathExeGreen}')
        def proc = null;
        def passedDuration = 0;

        // In case of ping failure try 2 times: from experience, sometimes domibus is running and for some reason the ping fails (trying 2 times could reduce the error occurence).
        while (confirmation <= 1) {
            if (pingMSH(side, context, log).equals("200")) {
                log.info "  startMSH  [][]  " + side.toUpperCase() + " is already running!";
                confirmation++;
            } else {
                if (confirmation > 0) {
                    log.info "  startMSH  [][]  Trying to start the " + side.toUpperCase()
                    if (side.toLowerCase() == "sender") {
                        proc = "cmd /c cd ${pathS} && startup.bat".execute()
                    } else {
                        if (side.toLowerCase() == "receiver") {
                            proc = "cmd /c cd ${pathR} && startup.bat".execute()
                        } else {
                            if ( (side.toLowerCase() == "receivergreen") ) {
                                proc = "cmd /c cd ${pathRG} && startup.bat".execute()
                            } else {
                                assert(false), "Incorrect side"
                            }
                        }
                    }
                    if (proc != null) {
                        proc.consumeProcessOutput(outputCatcher, errorCatcher)
                        proc.waitFor()
                    }
                    assert((!errorCatcher) && (proc != null)), locateTest(context) + "Error:startMSH: Error while trying to start the MSH."
                    while ( (!pingMSH(side, context, log).equals("200")) && (passedDuration < MAX_WAIT_TIME) ) {
                        passedDuration = passedDuration + STEP_WAIT_TIME
                        sleep(STEP_WAIT_TIME)
                    }
                    assert(pingMSH(side, context, log).equals("200")),locateTest(context) + "Error:startMSH: Error while trying to start the MSH."
                    log.info "  startMSH  [][]  DONE - " + side.toUpperCase() + " started."
                }
            }
            sleep(STEP_WAIT_TIME)
            confirmation++;
        }
    }
//---------------------------------------------------------------------------------------------------------------------------------
    // Stop several gateways
    static def stopSetMSHs(int dom1, int dom2, int dom3, context, log) {
        debugLog("  ====  Calling \"stopSetMSHs\".", log)
        if (dom1 > 0) {
            stopMSH("sender", context, log)
        }
        if (dom2 > 0) {
            stopMSH("receiver", context, log)
        }
        if (dom3 > 0) {
            stopMSH("receivergreen", context, log)
        }
    }
//---------------------------------------------------------------------------------------------------------------------------------
        // Stop Gateway
        static def stopMSH(String side, context, log){
        debugLog("  ====  Calling \"stopMSH\".", log)
        def MAX_WAIT_TIME=150000; // Maximum time to wait for the domibus to stop.
        def STEP_WAIT_TIME=2000; // Time to wait before re-checking the domibus status.
        def outputCatcher = new StringBuffer()
        def errorCatcher = new StringBuffer()
        def proc = null;
        def pathS = context.expand('${#Project#pathExeSender}')
        def pathR = context.expand('${#Project#pathExeReceiver}')
        def pathRG = context.expand('${#Project#pathExeGreen}')
        def path
        def passedDuration = 0;

        if (!pingMSH(side, context, log).equals("200")) {
            log.info "  stopMSH  [][]  " + side.toUpperCase() + " is not running!"
        } else {
            log.info "  stopMSH  [][]  Trying to stop the " + side.toUpperCase()
            switch (side.toLowerCase()) {
            case "sender":
			case "c2":
			case "blue":
                path = pathS
                break;
            case "receiver":
			case "c3":
			case "red":
                path = pathR
                break;
            case "receivergreen":
			case "green":
                path = pathRG
                break;
            default:
                assert(false), "Unknown side.";
            }
            proc = "cmd /c cd ${path} && shutdown.bat".execute()

            if (proc != null) {
                proc.consumeProcessOutput(outputCatcher, errorCatcher)
                proc.waitFor()
            }
            assert((!errorCatcher) && (proc != null)),locateTest(context) + "Error:stopMSH: Error while trying to stop the MSH."
            while ( (pingMSH(side, context, log).equals("200")) && (passedDuration < MAX_WAIT_TIME) ) {
                passedDuration = passedDuration + STEP_WAIT_TIME;
                sleep(STEP_WAIT_TIME)
            }
            assert(!pingMSH(side, context, log).equals("200")),locateTest(context) + "Error:startMSH: Error while trying to stop the MSH."
            log.info "  stopMSH  [][]  DONE - " + side.toUpperCase() + " stopped."
        }
    }
//---------------------------------------------------------------------------------------------------------------------------------
    static def uploadPmode(String side, String baseFilePath, String extFilePath, context, log, String domainValue = "Default", String outcome = "successfully", String message = null, String authUser = null, authPwd = null){
        debugLog("  ====  Calling \"uploadPmode\".", log)
        log.info "  uploadPmode  [][]  Start upload PMode for Domibus \"" + side + "\".";
        def commandString = null
        def commandResult = null
        def pmDescription = "SoapUI sample test description for PMode upload."
        def multitenancyOn = false
        def authenticationUser = authUser
        def authenticationPwd = authPwd
        def String pmodeFile = computePathRessources(baseFilePath, extFilePath, context, log)

        log.info "  uploadPmode  [][]  PMODE FILE PATH: " + pmodeFile;

        try{
            (authenticationUser, authenticationPwd) = retriveAdminCredentialsForDomain(context, log, side, domainValue, authenticationUser, authenticationPwd)


			commandString = ["curl", urlToDomibus(side, log, context) + "/rest/pmode",
							"--cookie", context.expand('${projectDir}') + File.separator + "cookie.txt",
							"-H","X-XSRF-TOKEN: " + returnXsfrToken(side, context, log, authenticationUser, authenticationPwd),
							"-F", "description=" + pmDescription,
							"-F", "file=@" + pmodeFile,
							"-v"]
            commandResult = runCommandInShell(commandString, log)
            assert(commandResult[0].contains(outcome)),"Error:uploadPmode: Error while trying to upload the PMode: response doesn't contain the expected outcome \"" + outcome + "\"."
            if (outcome.toLowerCase() == "successfully") {
                log.info "  uploadPmode  [][]  " + commandResult[0] + " Domibus: \"" + side + "\".";
                if (message != null) {
                    assert(commandResult[0].contains(message)),"Error:uploadPmode: Upload done but expected message \"" + message + "\" was not returned."
                }
            } else {
                log.info "  uploadPmode  [][]  Upload PMode was not done for Domibus: \"" + side + "\".";
                if (message != null) {
                    assert(commandResult[0].contains(message)),"Error:uploadPmode: Upload was not done but expected message \"" + message + "\" was not returned.\n Result:"  + commandResult[0]
                }
            }
        } finally {
            resetAuthTokens(log)
        }
    }
//---------------------------------------------------------------------------------------------------------------------------------
    static def uploadPmodeWithoutToken(String side, String baseFilePath, String extFilePath, context, log, String outcome = "successfully", String message =null, String userLogin=null, passwordLogin=null){
        debugLog("  ====  Calling \"uploadPmodeWithoutToken\".", log)
        log.info "  uploadPmodeWithoutToken  [][]  Start upload PMode for Domibus \""+side+"\".";
        def commandString = null;
        def commandResult = null;
        def pmDescription = "Dummy";

        def String output = fetchCookieHeader(side, context, log)
        def XXSRFTOKEN = null;
        def String pmodeFile = computePathRessources(baseFilePath, extFilePath, context, log)

		commandString = ["curl", urlToDomibus(side, log, context) + "/rest/pmode",
				"-F", "description=" + pmDescription,
				"-F", "file=@" + pmodeFile,
				"-v"]
        commandResult = runCommandInShell(commandString, log)
        assert(commandResult[0].contains(outcome)),"Error:uploadPmode: Error while trying to connect to domibus."
        if (outcome.toLowerCase() == "successfully") {
            log.info "  uploadPmodeWithoutToken  [][]  " + commandResult[0] + " Domibus: \"" + side + "\".";
            if (message != null) {
                assert(commandResult[0].contains(message)),"Error:uploadPmode: Upload done but expected message \"" + message + "\" was not returned."
            }
        } else {
            log.info "  uploadPmodeWithoutToken  [][]  Upload PMode was not done for Domibus: \"" + side + "\".";
            if (message != null) {
                assert(commandResult[0].contains(message)),"Error:uploadPmode: Upload was not done but expected message \"" + message + "\" was not returned."
            }
        }
    }
//---------------------------------------------------------------------------------------------------------------------------------
    static def uploadTruststore(String side, String baseFilePath, String extFilePath, context, log, String domainValue="Default", String outcome="successfully", String tsPassword=TRUSTSTORE_PASSWORD, String authUser=null, authPwd=null){
        debugLog("  ====  Calling \"uploadTruststore\".", log)
        log.info "  uploadTruststore  [][]  Start upload truststore for Domibus \""+side+"\".";
        def commandString = null;
        def commandResult = null;
        def multitenancyOn=false;
        def authenticationUser=authUser;
        def authenticationPwd=authPwd;
        def String truststoreFile=null;

        try{
            debugLog("  uploadTruststore  [][]  Fetch multitenancy mode on domibus $side.", log)
            (authenticationUser, authenticationPwd)=retriveAdminCredentialsForDomain(context, log, side, domainValue, authenticationUser, authenticationPwd)

            truststoreFile=computePathRessources(baseFilePath,extFilePath,context,log)

			commandString = ["curl", urlToDomibus(side, log, context) + "/rest/truststore/save",
				"--cookie", context.expand('${projectDir}') + File.separator + "cookie.txt",
				"-H","X-XSRF-TOKEN: " + returnXsfrToken(side, context, log, authenticationUser, authenticationPwd),
				"-F", "password=" + tsPassword,
				"-F", "truststore=@" + truststoreFile,
				"-v"]
            commandResult = runCommandInShell(commandString, log)

            assert(commandResult[0].contains(outcome)),"Error:uploadTruststore: Error while trying to upload the truststore to domibus. Returned: "+commandResult[0]
            log.info "  uploadTruststore  [][]  " + commandResult[0] + " Domibus: \"" + side + "\".";
        } finally {
            resetAuthTokens(log)
        }
    }
//---------------------------------------------------------------------------------------------------------------------------------
    // Change Domibus configuration file
    static def void changeDomibusProperties(color, propValueDict, log, context, testRunner){
        debugLog("  ====  Calling \"changeDomibusProperties\".", log)
        // Check that properties file exist and if yes create backup_file
        // For all properties name and new value pairs change value in file
        // to restore configuration use method restoreDomibusPropertiesFromBackup(domibusPath,  log, context, testRunner)
        def pathToPropertyFile = pathToDomibus(color, log, context) + context.expand('${#Project#subPathToDomibusProperties}')

        // Check file exists
        def testFile = new File(pathToPropertyFile)
        if (!testFile.exists()) {
            testRunner.fail("File [${pathToPropertyFile}] does not exist. Can't change value.")
            return null
        } else log.info "  changeDomibusProperties  [][]  File [${pathToPropertyFile}] exists."

        // Create backup file if already not created
        def backupFileName = "${pathToPropertyFile}${backup_file_sufix}"
        def backupFile = new File(backupFileName)
        if (backupFile.exists()) {
            log.info "  changeDomibusProperties  [][]  File [${backupFileName}] already exists and would not be overwrite - old backup file would be preserved."
        } else  {
            copyFile(pathToPropertyFile, backupFileName, log)
            log.info "  changeDomibusProperties  [][]  Backup copy of config file created: [${backupFile}]"
        }

        def fileContent = testFile.text
        //run in loop for all properties key values pairs
        for(item in propValueDict){
            def propertyToChangeName = item.key
            def newValueToAssign = item.value

            // Check that property exist in config file
            def found = 0
            def foundInCommentedRow = 0
            testFile.eachLine{
                line, n ->
                n++
                if(line =~ /^\s*$ { propertyToChangeName }
                   = /) {
                    log.info "  changeDomibusProperties  [][]  In line $n searched property was found. Line value is: $line"
                    found++
                }
                if(line =~ ~/#+\s*$ { propertyToChangeName }
                   = .*/) {
                    log.info "  changeDomibusProperties  [][]  In line $n commented searched property was found. Line value is: $line"
                    foundInCommentedRow++
                }
            }

            if (found > 1) {
                testRunner.fail("The search string ($propertyToChangeName=) was found ${found} times in file [${pathToPropertyFile}]. Expect only one assigment - check if configuration file is not corrupted.")
                return null
            }
            // If property is present in file change it value
            if(found)
            fileContent = fileContent.replaceAll(/(?m)^\s*($ { propertyToChangeName }
                                                           = )(.*)/) { all, paramName, value -> "${paramName}${newValueToAssign}" } else
            if(foundInCommentedRow)
            fileContent = fileContent.replaceFirst(/(?m)^#+\s*($ { propertyToChangeName }
                                                               = )(.*)/) { all, paramName, value -> "${paramName}${newValueToAssign}" } else {
                testRunner.fail("The search string ($propertyToChangeName) was not found in file [${pathToPropertyFile}]. No changes would be applied - properties file restored.")
                return null
            }
            log.info "  changeDomibusProperties  [][]  In [${pathToPropertyFile}] file property ${propertyToChangeName} was changed to value ${newValueToAssign}"
        } //loop end

        // Store new content of properties file after all changes
        testFile.text=fileContent
        log.info "  changeDomibusProperties  [][]  Property file [${pathToPropertyFile}] amended"
    }
//---------------------------------------------------------------------------------------------------------------------------------
    // Restor Domibus configuration file
    static def void restoreDomibusPropertiesFromBackup(color, log, context, testRunner) {
        debugLog("  ====  Calling \"restoreDomibusPropertiesFromBackup\".", log)
        // Restore from backup file domibus.properties file
        def pathToPropertyFile = pathToDomibus(color, log, context) + context.expand('${#Project#subPathToDomibusProperties}')
        def backupFile = "${pathToPropertyFile}${backup_file_sufix}"

        // Check backup file exists
        def backupFileHandler = new File(backupFile)
        if (!backupFileHandler.exists()) {
            testRunner.fail("CRITICAL ERROR: File [${backupFile}] does not exist.")
            return null
        } else {
            log.info "  restoreDomibusPropertiesFromBackup  [][]  Restore properties file from existing backup"
            copyFile(backupFile, pathToPropertyFile, log)
            if (backupFileHandler.delete()) {
                log.info "  restoreDomibusPropertiesFromBackup  [][]  Successufuly restory configuration from backup file and backup file was removed"
            } else {
                testRunner.fail "Not able to delete configuration backup file"
                return null
            }
        }
    }
//IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII
//  Domain Functions
//IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII
	// To be removed in the future after confirming that it is not needed
    /*static def getDomainName(String domainInfo, log) {
        debugLog("  ====  Calling \"getDomainName\".", log)
        assert((domainInfo != null) && (domainInfo != "")),"Error:getDomainName: provided domain info are empty.";
        debugLog("  getDomainName  [][]  Get current domain name from domain info: $domainInfo.", log)
        def jsonSlurper = new JsonSlurper()
        def domainMap = jsonSlurper.parseText(domainInfo)
        assert(domainMap.name != null),"Error:getDomain: Domain informations are corrupted: $domainInfo.";
		debugLog("  ====  End \"getDomainName\": returning current domain name value \""+domainMap.name+"\".", log)
        return domainMap.name;
    }*/
//---------------------------------------------------------------------------------------------------------------------------------
    static def getDomain(String side, context, log, String userLogin = SUPER_USER, String passwordLogin = SUPER_USER_PWD) {
        debugLog("  ====  Calling \"getDomain\".", log)
        assert(userLogin == SUPER_USER),"Error:getDomains: To manipulate domains, login must be done with user: \"$SUPER_USER\"."
        log.info "  getDomain  [][]  Get current domain for Domibus $side.";
        def commandString = null;
        def commandResult = null;

		commandString = ["curl", urlToDomibus(side, log, context) + "/rest/application/name",
						"--cookie", context.expand('${projectDir}') + File.separator + "cookie.txt",
						"-H", "Content-Type: application/json",
						"-H", "X-XSRF-TOKEN: " + returnXsfrToken(side, context, log, userLogin, passwordLogin),
						"-v"]
        commandResult = runCommandInShell(commandString, log)
        assert((commandResult[1]==~ /(?s).*HTTP\/\d.\d\s*204.*/)||(commandResult[1]==~ /(?s).*HTTP\/\d.\d\s*200.*/)),"Error:getDomain: Error in the getDomain response."
		debugLog("  ====  END \"getDomain\".", log)
        return commandResult[0].substring(5)
    }
//---------------------------------------------------------------------------------------------------------------------------------
    static def setDomain(String side, context, log, String domainValue, String userLogin=SUPER_USER, String passwordLogin=SUPER_USER_PWD){
        debugLog("  ====  Calling \"setDomain\".", log)
        def commandString = null;
        def commandResult = null;

        assert(userLogin==SUPER_USER),"Error:setDomain: To manipulate domains, login must be done with user: \"$SUPER_USER\"."
        debugLog("  setDomain  [][]  Set domain for Domibus $side.", log)
		if (domainValue == getDomain(side, context, log)) {
            debugLog("  setDomain  [][]  Requested domain is equal to the current value: no action needed", log)
        } else {
			debugLog("  setDomain  [][]  Calling curl command to switch to domain \"$domainValue\"", log)
            commandString=["curl", urlToDomibus(side, log, context) + "/rest/security/user/domain",
						"--cookie", context.expand('${projectDir}') + File.separator + "cookie.txt",
						"-H", "Content-Type: text/plain",
						"-H", "X-XSRF-TOKEN: " + returnXsfrToken(side, context, log, userLogin, passwordLogin),
						"-X", "PUT","-v",
						"--data-binary", "$domainValue"]
            commandResult = runCommandInShell(commandString, log)
            assert((commandResult[1]==~ /(?s).*HTTP\/\d.\d\s*204.*/)||(commandResult[1]==~ /(?s).*HTTP\/\d.\d\s*200.*/)),"Error:setDomain: Error while trying to set the domain: verify that domain $domainValue is correctly configured."
            debugLog("  setDomain  [][]  Domain set to $domainValue.",log)
        }
		debugLog("  ====  END \"setDomain\".", log)
    }
//---------------------------------------------------------------------------------------------------------------------------------
    // Return number of domains
    static def getMultitenancyMode(String inputValue, log) {
        debugLog("  ====  Calling \"getMultitenancyMode\".", log)
        if ( (inputValue == null) || (inputValue == "") ) {
            return 0;
        }
        if (inputValue.trim().isInteger()) {
            return (inputValue as Integer)
        } else {
            return 0;
        }
    }
//IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII
//  Users Functions
//IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII
    static def getAdminConsoleUsers(String side, context, log, String authUser=null, String authPwd=null){
        debugLog("  ====  Calling \"getAdminConsoleUsers\".", log)
        debugLog("  getAdminConsoleUsers  [][]  Get Admin Console users for Domibus \"$side\".", log)
        def commandString = null;
        def commandResult = null;
        def multitenancyOn=false;
        def authenticationUser=authUser;
        def authenticationPwd=authPwd;

        (authenticationUser, authenticationPwd) = retriveAdminCredentials(context, log, side, authenticationUser, authenticationPwd)

        commandString="curl "+urlToDomibus(side, log, context)+"/rest/user/users -b "+context.expand( '${projectDir}')+ File.separator + "cookie.txt -v -H \"Content-Type: application/json\" -H \"X-XSRF-TOKEN: "+ returnXsfrToken(side,context,log,authenticationUser,authenticationPwd) +"\" -X GET ";
        commandResult = runCommandInShell(commandString, log)
        assert((commandResult[1]==~ /(?s).*HTTP\/\d.\d\s*200.*/) || commandResult[1].contains("successfully")),"Error:getAdminConsoleUsers: Error while trying to connect to domibus.";
        return commandResult[0].substring(5)
    }
//---------------------------------------------------------------------------------------------------------------------------------
    static def addAdminConsoleUser(String side, context, log, String domainValue="Default", String userRole="ROLE_ADMIN", String userAC, String passwordAC="Domibus-123", String authUser=null, String authPwd=null,success=true){
        debugLog("  ====  Calling \"addAdminConsoleUser\".", log)
        def usersMap=null;
        def mapElement=null;
        def multitenancyOn=false;
        def commandString = null;
        def commandResult = null;
        def jsonSlurper = new JsonSlurper()
        def curlParams=null;
        def authenticationUser=authUser;
        def authenticationPwd=authPwd;

        try{
            (authenticationUser, authenticationPwd) = retriveAdminCredentialsForDomain(context, log, side, domainValue, authenticationUser, authenticationPwd)

            debugLog("  addAdminConsoleUser  [][]  Fetch users list and verify that user \"$userAC\" doesn't already exist.",log)
            usersMap = jsonSlurper.parseText(getAdminConsoleUsers(side, context, log))
            if (userExists(usersMap, userAC, log, false)) {
                log.error "Error:addAdminConsoleUser: Admin Console user \"$userAC\" already exist: usernames must be unique.";
            } else {
                debugLog("  addAdminConsoleUser  [][]  Users list before the update: " + usersMap, log)
                debugLog("  addAdminConsoleUser  [][]  Prepare user \"$userAC\" details to be added.", log)
                curlParams = "[ { \"roles\": \"$userRole\", \"userName\": \"$userAC\", \"password\": \"$passwordAC\", \"status\": \"NEW\", \"active\": true, \"suspended\": false, \"authorities\": [], \"deleted\": false } ]";
                debugLog("  addAdminConsoleUser  [][]  Inserting user \"$userAC\" in list.", log)
                debugLog("  addAdminConsoleUser  [][]  User \"$userAC\" parameters: $curlParams.", log)
				commandString = ["curl ",urlToDomibus(side, log, context) + "/rest/user/users",
								"--cookie", context.expand('${projectDir}') + File.separator + "cookie.txt",
								"-H", "Content-Type: application/json",
								"-H", "X-XSRF-TOKEN: " + returnXsfrToken(side, context, log, authenticationUser, authenticationPwd),
								"-X", "PUT",
								"--data-binary", formatJsonForCurl(curlParams, log),
								"-v"]
                commandResult = runCommandInShell(commandString, log)
				if(success){
					assert((commandResult[1]==~ /(?s).*HTTP\/\d.\d\s*200.*/)||(commandResult[1]==~ /(?s).*HTTP\/\d.\d\s*204.*/)),"Error:addAdminConsoleUser: Error while trying to add a user \"$userAC\".";
					log.info "  addAdminConsoleUser  [][]  Admin Console user \"$userAC\" added.";
				}else{
					assert((commandResult[1]==~ /(?s).*HTTP\/\d.\d\s*500.*/)&&(commandResult[0]==~ /(?s).*password of.*user.*not meet the minimum complexity requirements.*/)),"Error:addAdminConsoleUser: user \"$userAC\" was created .";
					log.info "  addAdminConsoleUser  [][]  Admin Console user \"$userAC\" was not added.";
				}
            }
        } finally {
            resetAuthTokens(log)
        }
    }
//---------------------------------------------------------------------------------------------------------------------------------
    static def removeAdminConsoleUser(String side, context, log, String domainValue="Default", String userAC, String authUser=null, String authPwd=null){
        debugLog("  ====  Calling \"removeAdminConsoleUser\".", log)
        def usersMap=null;
        def multitenancyOn=false;
        def commandString = null;
        def commandResult = null;
        def jsonSlurper = new JsonSlurper()
        def curlParams=null;
        def authenticationUser=authUser;
        def authenticationPwd=authPwd;
        def roleAC=null;
        def userDeleted=false;
        def i=0;

        try{
            (authenticationUser, authenticationPwd) = retriveAdminCredentialsForDomain(context, log, side, domainValue, authenticationUser, authenticationPwd)

            debugLog("  removeAdminConsoleUser  [][]  Fetch users list and verify that user \"$userAC\" exists.",log)
            usersMap = jsonSlurper.parseText(getAdminConsoleUsers(side, context, log))
            if (!userExists(usersMap, userAC, log, false)) {
                log.info "  removeAdminConsoleUser  [][]  Admin console user \"$userAC\" doesn't exist. No action needed.";
            } else {
                while (i < usersMap.size()) {
                    assert(usersMap[i] != null),"Error:removeAdminConsoleUser: Error while parsing the list of admin console users.";
                    if (usersMap[i].userName == userAC) {
                        roleAC = usersMap[i].roles;
                        userDeleted = usersMap[i].deleted;
                        i = usersMap.size()
                    }
                    i++;
                }
                assert(roleAC != null),"Error:removeAdminConsoleUser: Error while fetching the role of user \"$userAC\".";
                assert(userDeleted != null),"Error:removeAdminConsoleUser: Error while fetching the \"deleted\" status of user \"$userAC\".";
                if (userDeleted == false) {
                    curlParams = "[ { \"userName\": \"$userAC\", \"roles\": \"$roleAC\", \"active\": true, \"authorities\": [ \"$roleAC\" ], \"status\": \"REMOVED\", \"suspended\": false, \"deleted\": true } ]"
                    commandString = ["curl ", urlToDomibus(side, log, context) + "/rest/user/users", "--cookie", context.expand('${projectDir}') + File.separator + "cookie.txt", "-H", "\"Content-Type: application/json\"", "-H", "\"X-XSRF-TOKEN: " + returnXsfrToken(side, context, log, authenticationUser, authenticationPwd) + "\"", "-v","-X", "PUT", "--data-binary", formatJsonForCurl(curlParams, log)]
                    commandResult = runCommandInShell(commandString, log)
                    assert(commandResult[1]==~ /(?s).*HTTP\/\d.\d\s*200.*/),"Error:removeAdminConsoleUser: Error while trying to remove user $userAC.";
                    log.info "  removeAdminConsoleUser  [][]  User \"$userAC\" Removed."
                } else {
                    log.info "  removeAdminConsoleUser  [][]  User \"$userAC\" was already deleted. No action needed.";
                }
            }
        } finally {
            resetAuthTokens(log)
        }
    }
//-------------------------------------------------------------------------------------------------------------------------------
    static def loginAdminConsole(String side, context, log, String userLogin = SUPER_USER, passwordLogin = SUPER_USER_PWD,String domainValue = "Default", checkResp0=null,checkResp1=null,success=true){
        debugLog("  ====  Calling \"loginAdminConsole\".", log)
        def commandString = null;
        def commandResult = null;
		def authenticationUser = userLogin;
        def authenticationPwd = passwordLogin;
		def json = ifWindowsEscapeJsonString('{\"username\":\"' + "${userLogin}" + '\",\"password\":\"' + "${passwordLogin}" + '\"}')

		(authenticationUser, authenticationPwd) = retriveAdminCredentialsForDomain(context, log, side, domainValue, authenticationUser, authenticationPwd)

        commandString = ["curl", urlToDomibus(side, log, context) + "/rest/security/authentication",
						"-i",
						"-v",
						"-H",  "Content-Type: application/json",
						"--data-binary", json, "-c", context.expand('${projectDir}') + File.separator + "cookie.txt",
						"--trace-ascii", "-"]

        commandResult = runCommandInShell(commandString, log)
		if(success){
			assert(commandResult[0].contains("XSRF-TOKEN")),"Error:Authenticating user: Error while trying to connect to domibus.";
		}
		if(checkResp0!=null){
			assert(commandResult[0].contains(checkResp0)),"Error:Authenticating user: Error checking response for string \"$checkResp0\".";
		}
		if(checkResp1){
			assert(commandResult[0].contains(checkResp1)),"Error:Authenticating user: Error checking response for string \"$checkResp1\".";
		}
    }
//-------------------------------------------------------------------------------------------------------------------------------
    static def UpdateAdminConsoleUserPass(String side, context, log, String userLogin = SUPER_USER, oldPassword = SUPER_USER_PWD,newPassword="Domibus-1234",String domainValue = "Default",checkResp0=null,checkResp1=null,success=true){
        debugLog("  ====  Calling \"loginAdminConsole\".", log)
        def commandString = null;
        def commandResult = null;
		def authenticationUser = userLogin;
        def authenticationPwd = oldPassword;

		def json = ifWindowsEscapeJsonString('{\"currentPassword\":\"' + "${oldPassword}" + '\",\"newPassword\":\"' + "${newPassword}" + '\"}')


		try{
            (authenticationUser, authenticationPwd) = retriveAdminCredentialsForDomain(context, log, side, domainValue, authenticationUser, authenticationPwd)

			commandString = ["curl", urlToDomibus(side, log, context) + "/rest/security/user/password",
						"--cookie", context.expand('${projectDir}') + File.separator + "cookie.txt",
						"-H",  "Content-Type: application/json",
						"-H","X-XSRF-TOKEN: " + returnXsfrToken(side, context, log, authenticationUser, authenticationPwd),
						"-v",
						"-X", "PUT",
						"--data-binary", json,
						"--trace-ascii", "-"]
			commandResult = runCommandInShell(commandString, log)
			if(success){
				assert((commandResult[0]==~ /(?s).*HTTP\/\d.\d\s*200.*/) || (commandResult[0]==~ /(?s).*HTTP\/\d.\d\s*204.*/)),"Error:Authenticating user: Error while trying to connect to domibus.";
			}
			if(checkResp0!=null){
				assert(commandResult[0].contains(checkResp0)),"Error:UpdateAdminConsoleUserPass: Error checking response for string \"$checkResp0\".";
			}
			if(checkResp1){
				assert(commandResult[0].contains(checkResp1)),"Error:UpdateAdminConsoleUserPass: Error checking response for string \"$checkResp1\".";
			}
        } finally {
            resetAuthTokens(log)
        }
    }
//---------------------------------------------------------------------------------------------------------------------------------
    static def getPluginUsers(String side, context, log, String authUser=null, String authPwd=null){
        debugLog("  ====  Calling \"getPluginUsers\".", log)
        def commandString = null;
        def commandResult = null;
        def multitenancyOn=false;
        def authenticationUser=authUser;
        def authenticationPwd=authPwd;

        (authenticationUser, authenticationPwd) = retriveAdminCredentials(context, log, side, authenticationUser, authenticationPwd)

        commandString=["curl", urlToDomibus(side, log, context) + "/rest/plugin/users?pageSize=10000",
					   "--cookie", context.expand( '${projectDir}')+ File.separator + "cookie.txt",
					   "-H", 'Content-Type: application/json',
					   "-H", "\"X-XSRF-TOKEN: "+ returnXsfrToken(side,context,log,authenticationUser,authenticationPwd) +"\"",
					   "-v"]
        commandResult = runCommandInShell(commandString, log)
        assert((commandResult[1]==~ /(?s).*HTTP\/\d.\d\s*200.*/) || commandResult[1].contains("successfully")),"Error:getPluginUsers: Error while trying to connect to domibus.";
        return commandResult[0].substring(5)
    }
//---------------------------------------------------------------------------------------------------------------------------------
    static def addPluginUser(String side, context, log, String domainValue="Default", String userRole="ROLE_ADMIN", String userPl, String passwordPl="Domibus-123", String originalUser="urn:oasis:names:tc:ebcore:partyid-type:unregistered:C1",success=true, String authUser=null, String authPwd=null){
        debugLog("  ====  Calling \"addPluginUser\".", log)
        def usersMap=null;
        def mapElement=null;
        def multitenancyOn=false;
        def commandString = null;
        def commandResult = null;
        def jsonSlurper = new JsonSlurper()
        def curlParams=null;
        def authenticationUser=authUser;
        def authenticationPwd=authPwd;

        try{
            (authenticationUser, authenticationPwd) = retriveAdminCredentialsForDomain(context, log, side, domainValue, authenticationUser, authenticationPwd)

            debugLog("  addPluginUser  [][]  Fetch users list and verify that user $userPl doesn't already exist.",log)
            usersMap = jsonSlurper.parseText(getPluginUsers(side, context, log))
            if (userExists(usersMap, userPl, log, true)) {
                log.error "Error:addPluginUser: plugin user $userPl already exist: usernames must be unique."
            } else {
                debugLog("  addPluginUser  [][]  Users list before the update: " + usersMap, log)
                debugLog("  addPluginUser  [][]  Prepare user $userPl details to be added.", log)
                curlParams = '[ { \"status\": \"NEW\", \"userName\": \"' + "${userPl}" + '\", \"authenticationType\": \"BASIC\", ' +
							((originalUser != null && originalUser != "") ? ' \"originalUser\": \"' + "${originalUser}" + '\", ' : '') +
							' \"authRoles\": \"' + "${userRole}" + '\", \"password\": \"' + "${passwordPl}" + '\", \"active\": \"true\" } ]'
                debugLog("  addPluginUser  [][]  Inserting user $userPl in the list.", log)
                debugLog("  addPluginUser  [][]  curlParams: " + curlParams, log)
                commandString = ["curl", urlToDomibus(side, log, context) + "/rest/plugin/users",
								"--cookie", context.expand('${projectDir}') + File.separator + "cookie.txt",
								"-H", "Content-Type: application/json",
								"-H", "X-XSRF-TOKEN: " + returnXsfrToken(side, context, log, authenticationUser, authenticationPwd),
								"-X", "PUT",
								"--data-binary", formatJsonForCurl(curlParams, log),
								"-v"]
                commandResult = runCommandInShell(commandString, log)

				if(success){
					assert((commandResult[1]==~ /(?s).*HTTP\/\d.\d\s*200.*/)||(commandResult[1]==~ /(?s).*HTTP\/\d.\d\s*204.*/)),"Error:addPluginUser: Error while trying to add a user.";
					log.info "  addPluginUser  [][]  Plugin user $userPl added.";
				}else{
					assert((commandResult[1]==~ /(?s).*HTTP\/\d.\d\s*500.*/)&&(commandResult[0]==~ /(?s).*password of.*user.*not meet the minimum complexity requirements.*/)),"Error:addAdminConsoleUser: user \"$userPl\" was created .";
					log.info "  addPluginUser  [][]  Plugin user \"$userPl\" was not added.";
				}
            }
        } finally {
            resetAuthTokens(log)
        }
    }
//---------------------------------------------------------------------------------------------------------------------------------
    static def removePluginUser(String side, context, log, String domainValue="Default", String userPl, String authUser=null, String authPwd=null){
        debugLog("  ====  Calling \"removePluginUser\".", log)
        def usersMap=null;
        def mapElement=null;
        def multitenancyOn=false;
        def commandString = null;
        def commandResult = null;
        def jsonSlurper = new JsonSlurper()
        def curlParams=null;
        def authenticationUser=authUser;
        def authenticationPwd=authPwd;
        def originalUser=null;
        def rolePl=null;
        def entityId=null;
		def active=null;
		def suspended=null;
        def i=0;

        try{
            (authenticationUser, authenticationPwd) = retriveAdminCredentialsForDomain(context, log, side, domainValue, authenticationUser, authenticationPwd)

            debugLog("  removePluginUser  [][]  Fetch users list and verify that user $userPl exists.", log)
            usersMap = jsonSlurper.parseText(getPluginUsers(side, context, log))
            debugLog("  removePluginUser  [][]  usersMap:	$usersMap", log)
            if (!userExists(usersMap, userPl, log, true)) {
                log.info "  removePluginUser  [][]  Plugin user $userPl doesn't exist. No action needed.";
            } else {
                while (i < usersMap.entries.size()) {
                    assert(usersMap.entries[i] != null),"Error:removePluginUser: Error while parsing the list of plugin users.";
                    if (usersMap.entries[i].userName == userPl) {
                        rolePl = usersMap.entries[i].authRoles;
                        originalUser = usersMap.entries[i].originalUser;
                        entityId = usersMap.entries[i].entityId;
						active = usersMap.entries[i].active
						suspended = usersMap.entries[i].suspended;
                        i = usersMap.entries.size()
                    }
                    i++;
                }
                assert(rolePl != null),"Error:removePluginUser: Error while fetching the role of user \"$userPl\"."
                assert(entityId != null),"Error:removePluginUser: Error while fetching the \"entityId\" of user \"$userPl\" from the user list."

				curlParams = "[ { \"entityId\": \"$entityId\", \"userName\": \"$userPl\", \"password\": null, \"certificateId\": null,\"authRoles\": \"$rolePl\", \"authenticationType\": \"BASIC\", \"status\": \"REMOVED\", \"active\": $active, \"suspended\": $suspended } ]"

                debugLog("  removePluginUser  [][]  curlParams: " + curlParams, log)
                commandString = ["curl", urlToDomibus(side, log, context) + "/rest/plugin/users",
								"--cookie", context.expand('${projectDir}') + File.separator + "cookie.txt",
								"-H", "Content-Type: application/json",
								"-H","X-XSRF-TOKEN: " + returnXsfrToken(side, context, log, authenticationUser, authenticationPwd),
								"-X", "PUT",
								"--data-binary", formatJsonForCurl(curlParams, log),
								"-v"]

                commandResult = runCommandInShell(commandString, log);
                assert((commandResult[1]==~ /(?s).*HTTP\/\d.\d\s*200.*/)||(commandResult[1]==~ /(?s).*HTTP\/\d.\d\s*204.*/)),"Error:removePluginUser: Error while trying to remove user $userPl.";
                log.info "  removePluginUser  [][]  Plugin user $userPl removed.";
            }
        } finally {
            resetAuthTokens(log)
        }
    }
//---------------------------------------------------------------------------------------------------------------------------------
    static def updatePluginUserPass(String side, context, log, String userPl, newPassword="Domibus-1234", String domainValue="Default",checkResp0=null,checkResp1=null,success=true, String authUser=null, String authPwd=null){
        debugLog("  ====  Calling \"updatePluginUser\".", log)
        def usersMap=null;
        def mapElement=null;
        def multitenancyOn=false;
        def commandString = null;
        def commandResult = null;
        def jsonSlurper = new JsonSlurper()
        def curlParams=null;
        def authenticationUser=authUser;
        def authenticationPwd=authPwd;
        def originalUser=null;
        def rolePl=null;
        def entityId=null;
		def active=null;
		def suspended=null;
        def i=0;

        try{
            (authenticationUser, authenticationPwd) = retriveAdminCredentialsForDomain(context, log, side, domainValue, authenticationUser, authenticationPwd)

            debugLog("  updatePluginUser  [][]  Fetch users list and verify that user $userPl exists.", log)
            usersMap = jsonSlurper.parseText(getPluginUsers(side, context, log))
            debugLog("  updatePluginUser  [][]  usersMap:	$usersMap", log)
            assert(userExists(usersMap, userPl, log, true)),"Error: updatePluginUser  [][]  Plugin user $userPl doesn't exist.";
            while (i < usersMap.entries.size()) {
                assert(usersMap.entries[i] != null),"Error:updatePluginUser: Error while parsing the list of plugin users.";
                if (usersMap.entries[i].userName == userPl) {
                    rolePl = usersMap.entries[i].authRoles;
                    originalUser = usersMap.entries[i].originalUser;
                    entityId = usersMap.entries[i].entityId;
					active = usersMap.entries[i].active
					suspended = usersMap.entries[i].suspended;
                    i = usersMap.entries.size()
                }
                i++;
            }
            assert(rolePl != null),"Error:updatePluginUser: Error while fetching the role of user \"$userPl\"."
            assert(entityId != null),"Error:updatePluginUser: Error while fetching the \"entityId\" of user \"$userPl\" from the user list."

			curlParams = "[ { \"entityId\": \"$entityId\", \"userName\": \"$userPl\", \"password\": \"$newPassword\", \"certificateId\": null,\"authRoles\": \"$rolePl\", \"authenticationType\": \"BASIC\", \"status\": \"UPDATED\", \"active\": $active, \"suspended\": $suspended } ]"

            debugLog("  updatePluginUser  [][]  curlParams: " + curlParams, log)
            commandString = ["curl", urlToDomibus(side, log, context) + "/rest/plugin/users",
						"--cookie", context.expand('${projectDir}') + File.separator + "cookie.txt",
						"-H", "Content-Type: application/json",
						"-H","X-XSRF-TOKEN: " + returnXsfrToken(side, context, log, authenticationUser, authenticationPwd),
						"-X", "PUT",
						"--data-binary", formatJsonForCurl(curlParams, log),
						"-v"]

            commandResult = runCommandInShell(commandString, log);

			if(success){
				assert((commandResult[1]==~ /(?s).*HTTP\/\d.\d\s*200.*/)||(commandResult[1]==~ /(?s).*HTTP\/\d.\d\s*204.*/)),"Error:updatePluginUser: Error while trying to update the password of the user \"$userPl\"";
				log.info "  updatePluginUser  [][]  Password of plugin user \"$userPl\" was successfully updated.";
			}
			if(checkResp0!=null){
				assert(commandResult[0].contains(checkResp0)),"Error:updatePluginUser: Error checking response for string \"$checkResp0\".";
			}
			if(checkResp1){
				assert(commandResult[0].contains(checkResp1)),"Error:updatePluginUser: Error checking response for string \"$checkResp1\".";
			}
        } finally {
            resetAuthTokens(log)
        }
    }
//---------------------------------------------------------------------------------------------------------------------------------
    static def userExists(usersMap, String targetedUser, log, boolean plugin = false) {
        debugLog("  ====  Calling \"userExists\".", log)
        def i = 0;
        def userFound = false;
        if (plugin) {
            debugLog("  userExists  [][]  Checking if plugin user \"$targetedUser\" exists.", log)
            debugLog("  userExists  [][]  Plugin users map: $usersMap.", log)
            assert(usersMap.entries != null),"Error:userExists: Error while parsing the list of plugin users.";
            while ( (i < usersMap.entries.size()) && (userFound == false) ) {
                assert(usersMap.entries[i] != null),"Error:userExists: Error while parsing the list of plugin users.";
                debugLog("  userExists  [][]  Iteration $i: comparing --$targetedUser--and--" + usersMap.entries[i].userName + "--.", log)
                if (usersMap.entries[i].userName == targetedUser) {
                    userFound = true;
                }
                i++;
            }
        } else {
            debugLog("  userExists  [][]  Checking if admin console user \"$targetedUser\" exists.", log)
            debugLog("  userExists  [][]  Admin console users map: $usersMap.", log)
            assert(usersMap != null),"Error:userExists: Error while parsing the list of admin console users.";
            while ( (i < usersMap.size()) && (userFound == false) ) {
                assert(usersMap[i] != null),"Error:userExists: Error while parsing the list of admin console users.";
                if (usersMap[i].userName == targetedUser) {
                    userFound = true;
                }
                i++;
            }
        }

        return userFound;
    }
//---------------------------------------------------------------------------------------------------------------------------------
    static def resetAuthTokens(log) {
        debugLog("  ====  Calling \"resetAuthTokens\".", log)
        XSFRTOKEN_C2 = null;
        XSFRTOKEN_C3 = null;
        XSFRTOKEN_C_Other = null;
    }

//---------------------------------------------------------------------------------------------------------------------------------
//
static def ifWindowsEscapeJsonString(json) {
	if (System.properties['os.name'].toLowerCase().contains('windows'))
		json = json.replace("\"", "\\\"")
	return json
}
//---------------------------------------------------------------------------------------------------------------------------------
    // Insert wrong password
    static def insertWrongPassword(String side, context, log, String username, int attempts=1, String domainValue="Default", String wrongPass="zzzdumzzz", String authUser=null, String authPwd=null){
        debugLog("  ====  Calling \"insertWrongPassword\".", log)
        def usersMap=null;
        def multitenancyOn=false;
        def jsonSlurper = new JsonSlurper()
        def authenticationUser=authUser;
        def authenticationPwd=authPwd;
        def commandResult = null;
        def commandString = null;

        try{
            (authenticationUser, authenticationPwd) = retriveAdminCredentialsForDomain(context, log, side, domainValue, authenticationUser, authenticationPwd)

            debugLog("  insertWrongPassword  [][]  Fetch users list and verify that user $username exists.",log)
            usersMap = jsonSlurper.parseText(getAdminConsoleUsers(side, context, log))
            debugLog("  insertWrongPassword  [][]  usersMap:	$usersMap", log)
            assert(userExists(usersMap, username, log, false)),"Error:insertWrongPassword: user \"$username\" was not found.";

			def json = ifWindowsEscapeJsonString('{\"username\":\"' + "${username}" + '\",\"password\":\"' + "${wrongPass}" + '\"}')

			// Try to login with wrong password
			commandString = ["curl", urlToDomibus(side, log, context) + "/rest/security/authentication",
							"-H",  "Content-Type: application/json",
							"--data-binary", json, "-c", context.expand('${projectDir}') + File.separator + "cookie.txt",
							"-i"]

            for (def i = 1; i <= attempts; i++) {
                log.info("  insertWrongPassword  [][]  Try to login with wrong password: Attempt $i.")
                commandResult = runCommandInShell(commandString, log)
                assert((commandResult[0].contains("Bad credentials")) || (commandResult[0].contains("Suspended"))),"Error:Authenticating user: Error while trying to connect to domibus."
            }
        } finally {
            resetAuthTokens(log)
        }
    }
//---------------------------------------------------------------------------------------------------------------------------------
    // Get Admin Console user Status (suspended or active)
    static def adminConsoleUserSuspended(String side, context, log, String username, String domainValue="Default", String authUser=null, String authPwd=null){
        debugLog("  ====  Calling \"adminConsoleUserSuspended\".", log)
        def usersMap=null;
        def multitenancyOn=false;
        def jsonSlurper = new JsonSlurper()
        def authenticationUser=authUser;
        def authenticationPwd=authPwd;
		def userStatus = null;
		def i = 0;

        try{
            (authenticationUser, authenticationPwd) = retriveAdminCredentialsForDomain(context, log, side, domainValue, authenticationUser, authenticationPwd)
            debugLog("  adminConsoleUserSuspended  [][]  Fetch users list and check user $username status: active or suspended.",log)
            usersMap = jsonSlurper.parseText(getAdminConsoleUsers(side, context, log))
            debugLog("  adminConsoleUserSuspended  [][]  Admin console users map: $usersMap.", log)
            assert(usersMap != null),"Error:adminConsoleUserSuspended: Error while parsing the list of admin console users.";
            while ( (i < usersMap.size()) && (userStatus == null) ) {
                assert(usersMap[i] != null),"Error:adminConsoleUserSuspended: Error while parsing the list of admin console users.";
                if (usersMap[i].userName == username) {
					userStatus=usersMap[i].suspended;
                }
                i++;
            }
        } finally {
            resetAuthTokens(log)
        }

		assert(userStatus!=null),"Error:adminConsoleUserSuspended: Error user $username was not found.";
		return userStatus;
    }
//---------------------------------------------------------------------------------------------------------------------------------
    // Get plugin user Status (suspended or active)
    static def pluginUserSuspended(String side, context, log, String username, String domainValue="Default", String authUser=null, String authPwd=null){
        debugLog("  ====  Calling \"pluginUserSuspended\".", log)
        def usersMap=null;
        def multitenancyOn=false;
        def jsonSlurper = new JsonSlurper()
        def authenticationUser=authUser;
        def authenticationPwd=authPwd;
		def userStatus = null;
		def i = 0;

        try{
            (authenticationUser, authenticationPwd) = retriveAdminCredentialsForDomain(context, log, side, domainValue, authenticationUser, authenticationPwd)
            debugLog("  pluginUserSuspended  [][]  Fetch users list and check user $username status: active or suspended.",log)
            usersMap = jsonSlurper.parseText(getPluginUsers(side, context, log))
            debugLog("  pluginUserSuspended  [][]  Plugin users map: $usersMap.", log)
            assert(usersMap != null),"Error:pluginUserSuspended: Error while parsing the list of plugin users.";
            while ( (i < usersMap.entries.size()) && (userStatus == null) ) {
                assert(usersMap.entries[i] != null),"Error:pluginUserSuspended: Error while parsing the list of plugin users.";
				debugLog("  pluginUserSuspended  [][]  Checking "+usersMap.entries[i].userName+" VS $username ...",log)
                if (usersMap.entries[i].userName == username) {
					debugLog("  pluginUserSuspended  [][]  User $username found.",log)
					userStatus=usersMap.entries[i].active;
                }
                i++;
            }

        } finally {
            resetAuthTokens(log)
        }

		assert(userStatus!=null),"Error:pluginUserSuspended: Error plugin user $username was not found.";
		return userStatus;
    }
//IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII
//  Message filter Functions
//IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII
    static def getMessageFilters(String side, context, log, String authUser = null, authPwd = null){
        debugLog("  ====  Calling \"getMessageFilters\".", log)
        log.info "  getMessageFilters  [][]  Get message filters for Domibus \"" + side + "\".";
        def commandString = null;
        def commandResult = null;
        def multitenancyOn = false;
        def authenticationUser = authUser;
        def authenticationPwd = authPwd;

        (authenticationUser, authenticationPwd) = retriveAdminCredentials(context, log, side, authenticationUser, authenticationPwd)

        commandString="curl "+urlToDomibus(side, log, context)+"/rest/messagefilters -b "+context.expand( '${projectDir}') + File.separator + "cookie.txt -v -H \"Content-Type: application/json\" -H \"X-XSRF-TOKEN: "+ returnXsfrToken(side,context,log,authenticationUser,authenticationPwd) +"\" -X GET ";
        commandResult = runCommandInShell(commandString, log)
        assert(commandResult[0].contains("messageFilterEntries") || commandResult[1].contains("successfully")),"Error:getMessageFilter: Error while trying to retrieve filters."
        return commandResult[0].substring(5)
    }
//---------------------------------------------------------------------------------------------------------------------------------
    static def formatFilters(filtersMap, String filterChoice, context, log, String extraCriteria = null) {
        debugLog("  ====  Calling \"formatFilters\".", log)
        log.info "  formatFilters  [][]  Analysing backends filters order ..."
        def swapBck = null;
        def i = 0;
        assert(filtersMap != null),"Error:formatFilters: Not able to get the backend details.";
        debugLog("  formatFilters  [][]  FILTERS:" + filtersMap, log)

        // Single backend: no action needed
        if (filtersMap.messageFilterEntries.size() == 1) {
            return "ok";
        }
        debugLog("  formatFilters  [][]  Loop over :" + filtersMap.messageFilterEntries.size() + " backend filters.", log);
		debugLog("  formatFilters  [][]  extraCriteria = --" + extraCriteria + "--.", log);
        while (i < filtersMap.messageFilterEntries.size()) {
            assert(filtersMap.messageFilterEntries[i] != null),"Error:formatFilters: Error while parsing filter details.";
            if (filtersMap.messageFilterEntries[i].backendName.toLowerCase() == filterChoice.toLowerCase()) {
                debugLog("  formatFilters  [][]  Comparing --" + filtersMap.messageFilterEntries[i].backendName + "-- and --" + filterChoice + "--", log)
                if ( (extraCriteria == null) || ( (extraCriteria != null) && filtersMap.messageFilterEntries[i].toString().contains(extraCriteria)) ) {
                    if (i == 0) {
                        return "correct";
                    }
                    debugLog("  formatFilters  [][]  switch $i element", log)
                    swapBck = filtersMap.messageFilterEntries[0];
                    filtersMap.messageFilterEntries[0] = filtersMap.messageFilterEntries[i];
                    filtersMap.messageFilterEntries[i] = swapBck;
                    return filtersMap;
                }
            }
            i++;
        }
        return "ko";
    }
//---------------------------------------------------------------------------------------------------------------------------------
        static def setMessageFilters(String side, String filterChoice, context, log, domainValue="Default", String authUser=null, authPwd=null, String extraCriteria=null){
        debugLog("  ====  Calling \"setMessageFilters\".", log)
        log.info "  setMessageFilters  [][]  Start setting message filters for Domibus \""+side+"\".";
        def String output=null;
        def commandString=null;
        def commandResult=null;
        def curlParams=null;
        def filtersMap=null;
        def multitenancyOn=false;
        def authenticationUser=authUser;
        def authenticationPwd=authPwd;
        def jsonSlurper=new JsonSlurper()

        try{
            (authenticationUser, authenticationPwd) = retriveAdminCredentialsForDomain(context, log, side, domainValue, authenticationUser, authenticationPwd)

            filtersMap=jsonSlurper.parseText(getMessageFilters(side,context,log))
            debugLog("  setMessageFilters  [][]  filtersMap:" + filtersMap, log)
            assert(filtersMap != null),"Error:setMessageFilter: Not able to get the backend details.";
            assert(filtersMap.toString().toLowerCase().contains(filterChoice.toLowerCase())),"Error:setMessageFilter: The backend you want to set is not installed.";
            filtersMap = formatFilters(filtersMap, filterChoice, context, log, extraCriteria)
            assert(filtersMap != "ko"),"Error:setMessageFilter: The backend you want to set is not installed."
            debugLog("  setMessageFilters  [][]  Backend filters order analyse done.", log)
            if (filtersMap.equals("ok")) {
                log.info "  setMessageFilters  [][]  Only one backend installed: Nothing to do.";
            } else {
                if (filtersMap.equals("correct")) {
                    log.info "  setMessageFilters  [][]  The requested backend is already selected: Nothing to do.";
                } else {
                    curlParams = JsonOutput.toJson(filtersMap).toString()
                    commandString = "curl " + urlToDomibus(side, log, context) + "/rest/messagefilters -b " + context.expand('${projectDir}') + File.separator + "cookie.txt -v -H \"Content-Type: application/json\" -H \"X-XSRF-TOKEN: " + returnXsfrToken(side, context, log, authenticationUser, authenticationPwd) + "\" -X PUT -d " + formatJsonForCurl(curlParams, log)
                    commandResult = runCommandInShell(commandString, log)
                    assert((commandResult[1]==~ /(?s).*HTTP\/\d.\d\s*200.*/) || commandResult[1].contains("successfully")),"Error:setMessageFilter: Error while trying to connect to domibus.";
                    log.info "  setMessageFilters  [][]  Message filters update done successfully for Domibus: \"" + side + "\".";
                }
            }
        } finally {
            resetAuthTokens(log)
        }
    }
//IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII
//  Curl related Functions
//IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII
    static def fetchCookieHeader(String side, context, log, String userLogin = SUPER_USER, passwordLogin = SUPER_USER_PWD) {
        debugLog("  ====  Calling \"fetchCookieHeader\".", log)
        def commandString = null;
        def commandResult = null;
		def json = ifWindowsEscapeJsonString('{\"username\":\"' + "${userLogin}" + '\",\"password\":\"' + "${passwordLogin}" + '\"}')

        commandString = ["curl", urlToDomibus(side, log, context) + "/rest/security/authentication",
						"-i",
						"-H",  "Content-Type: application/json",
						"--data-binary", json, "-c", context.expand('${projectDir}') + File.separator + "cookie.txt",
						"--trace-ascii", "-"]

        commandResult = runCommandInShell(commandString, log)
        assert(commandResult[0].contains("XSRF-TOKEN")),"Error:Authenticating user: Error while trying to connect to domibus."
        return commandResult[0];
    }

//---------------------------------------------------------------------------------------------------------------------------------
        static def String returnXsfrToken(String side, context, log, String userLogin = SUPER_USER, passwordLogin = SUPER_USER_PWD) {
        debugLog("  ====  Calling \"returnXsfrToken\".", log)
        debugLog("  returnXsfrToken  [][]  Call returnXsfrToken with values: --side=$side--XSFRTOKEN_C2=$XSFRTOKEN_C2--XSFRTOKEN_C3=$XSFRTOKEN_C3.", log)
        def String output = null;

        switch (side.toLowerCase()) {
        case "c2":
        case "blue":
        case "sender":
        case "c2default":
            if (XSFRTOKEN_C2 == null) {
                output = fetchCookieHeader(side, context, log, userLogin, passwordLogin)
                XSFRTOKEN_C2 = output.find("XSRF-TOKEN.*;").replace("XSRF-TOKEN=", "").replace(";", "")
            }
            return XSFRTOKEN_C2;
            break;
        case "c3":
        case "red":
        case "receiver":
        case "c3default":
            if (XSFRTOKEN_C3 == null) {
                output = fetchCookieHeader(side, context, log, userLogin, passwordLogin)
                XSFRTOKEN_C3 = output.find("XSRF-TOKEN.*;").replace("XSRF-TOKEN=", "").replace(";", "")
            }
            return XSFRTOKEN_C3;
            break;
        case "receivergreen":
        case "green":
        case "thirddefault":
            if (XSFRTOKEN_C_Other == null) {
                output = fetchCookieHeader(side, context, log, userLogin, passwordLogin)
                XSFRTOKEN_C_Other = output.find("XSRF-TOKEN.*;").replace("XSRF-TOKEN=", "").replace(";", "")
            }
            return XSFRTOKEN_C_Other;
            break;
        default:
            assert(false), "returnXsfrToken: Unknown side. Supported values: sender, receiver, receivergreen ...";
        }
        assert(false), "returnXsfrToken: Error while retrieving XSFRTOKEN ..."
    }
//---------------------------------------------------------------------------------------------------------------------------------
        static def formatJsonForCurl(input, log) {
        debugLog("  ====  Calling \"formatJsonForCurl\".", log)
		debugLog("  +++++++++++ Runned on: " + System.properties['os.name'], log)
		if (System.properties['os.name'].toLowerCase().contains('windows')) {
			def intermediate = null;
			assert(input != null),"Error:formatJsonForCurl: input string is null.";
			assert(input.contains("[") && input.contains("]")),"Error:formatJsonForCurl: input string is corrupted.";
			intermediate = input.substring(input.indexOf("[") + 1, input.lastIndexOf("]")).replace("\"", "\"\"\"")
			return "[" + intermediate + "]"
		}
		return input
    }
//---------------------------------------------------------------------------------------------------------------------------------
        static def computePathRessources(String type, String extension, context, log) {
        debugLog("  ====  Calling \"computePathRessources\".", log)
		def returnPath = null
		def basePathPropName = ""
		debugLog("Input extension: " + extension, log)

        switch (type.toLowerCase()) {
			case "special":
				basePathPropName = "specialPModesPath"
				break;
			case "default":
				basePathPropName = "defaultPModesPath"
				break;
			case "temp":
				basePathPropName = "tempFilesDir"
				break;
			default:
				assert 0, "Unknown type of path provided: ${type}. Supported types: special, default, temp."
		}

		returnPath = (context.expand("\${#Project#${basePathPropName}}") + extension).replace("\\\\", "\\")

		debugLog("  +++++++++++ Runned on: " + System.properties['os.name'], log)
		if (System.properties['os.name'].toLowerCase().contains('windows'))
        	returnPath = returnPath.replace("\\", "\\\\")
		else
			returnPath = returnPath.replace("\\", "/")

		debugLog("Output computePathRessources: " + returnPath.toString(), log)
        return returnPath.toString()
    }
//---------------------------------------------------------------------------------------------------------------------------------
        // Run curl command
        static def runCommandInShell(inputCommand, log) {
        debugLog("  ====  Calling \"runCommandInShell\".", log)
        def proc = null;
        def outputCatcher = new StringBuffer()
        def errorCatcher = new StringBuffer()
        debugLog("  runCommandInShell  [][]  Run curl command: " + inputCommand, log)
        if (inputCommand) {
            proc = inputCommand.execute()
            if (proc != null) {
                proc.consumeProcessOutput(outputCatcher, errorCatcher)
                proc.waitFor()
            }
        }
        debugLog("  runCommandInShell  [][]  outputCatcher: " + outputCatcher.toString(), log)
        debugLog("  runCommandInShell  [][]  errorCatcher: " + errorCatcher.toString(), log)
        return ([outputCatcher.toString(), errorCatcher.toString()])
    }
//IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII
//  Multitenancy Functions
//IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII
        // Return multitenancy mode
        static def getMultitenancyFromSide(String side, context, log) {
        debugLog("  ====  Calling \"getMultitenancyFromSide\".", log)
        def mode = 0;
        switch (side.toUpperCase()) {
        case "C2":
        case "BLUE":
        case "SENDER":
        case "C2DEFAULT":
            //mode=multitenancyModeC2;
            mode = getMultitenancyMode(context.expand('${#Project#multitenancyModeC2}'), log)
            debugLog("  getMultitenancyFromSide  [][]  mode on domibus $side set to $mode.", log)
            break;
        case "C3":
        case "RED":
        case "RECEIVER":
        case "C3DEFAULT":
            //mode=multitenancyModeC3;
            mode = getMultitenancyMode(context.expand('${#Project#multitenancyModeC3}'), log)
            debugLog("  getMultitenancyFromSide  [][]  mode on domibus $side set to $mode.", log)
            break;
        default:
            log.error "  getMultitenancyFromSide  [][]  ERROR:getMultitenancyFromSide: dominus $side not found.";
        }
        if (mode > 0) {
            return true;
        } else {
            return false;
        }
    }

// Return admin credentials for super user in multidomain configuration and admin user in single domaind situation with domain provided
        static def retriveAdminCredentialsForDomain(context, log, String side, String domainValue, String authUser, authPwd) {
		debugLog("  ====  Calling \"retriveAdminCredentialsForDomain\".", log)
        def authenticationUser = authUser
        def authenticationPwd = authPwd
        def multitenancyOn =  null
        multitenancyOn = getMultitenancyFromSide(side, context, log)
        if (multitenancyOn) {
            log.info("  retriveAdminCredentials  [][]  retriveAdminCredentials for Domibus $side and domain $domainValue.")
            debugLog("  retriveAdminCredentials  [][]  First, set domain to $domainValue.", log)
            setDomain(side, context, log, domainValue)
            // If authentication details are not fully provided, use default values
            if ( (authUser == null) || (authPwd == null) ) {
                authenticationUser = SUPER_USER;
                authenticationPwd = SUPER_USER_PWD;
            }
        } else {
            log.info("  retriveAdminCredentials  [][]  retriveAdminCredentials for Domibus $side.")
            // If authentication details are not fully provided, use default values
            if ( (authUser == null) || (authPwd == null) ) {
                authenticationUser = DEFAULT_ADMIN_USER;
                authenticationPwd = DEFAULT_ADMIN_USER_PWD;
            }
        }
		debugLog("  ====  END \"retriveAdminCredentialsForDomain\": returning credentials: ["+authenticationUser+", "+authenticationPwd+"].", log)
        return [authenticationUser, authenticationPwd]
}

// Return admin credentials for super user in multidomain configuration and admin user in single domaind situation without domain provided
    static def retriveAdminCredentials(context, log, String side, String authUser, authPwd) {
    def authenticationUser = authUser
    def authenticationPwd = authPwd
    def multitenancyOn =  null
// If authentication details are not fully provided, use default values
    if ( (authUser == null) || (authPwd == null) ) {
        multitenancyOn = getMultitenancyFromSide(side, context, log)
        if (multitenancyOn) {
            authenticationUser = SUPER_USER;
            authenticationPwd = SUPER_USER_PWD;
        } else {
            authenticationUser = DEFAULT_ADMIN_USER;
            authenticationPwd = DEFAULT_ADMIN_USER_PWD;
        }
    }
    return  [authenticationUser, authenticationPwd]
}

    //IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII
    //  Utilities Functions
    //IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII
    // Returns: "--TestCase--testStep--"
    static def String locateTest(context) {
    return ("--" + context.testCase.name + "--" + context.testCase.getTestStepAt(context.getCurrentStepIndex()).getLabel() + "--  ")
}
//---------------------------------------------------------------------------------------------------------------------------------
// Copy file from source to destination
static def void copyFile(String source, String destination, log, overwriteOpt=true){
    debugLog("  ====  Calling \"copyFile\".",log)
    // Check that destination folder exists.
    //def destFolder = new File("${destination}")
    //assert destFolder.exists(), "Error while trying to copy file to folder "+destination+": Destination folder doesn't exist.";

    def builder = new AntBuilder()
    try {
        builder.sequential {
            copy(tofile: destination, file:source, overwrite:overwriteOpt)
        }
        log.info "  copyFile  [][]  File ${source} was successfuly copied to ${destination}"
    } catch (Exception ex) {
        log.error "  copyFile  [][]  Error while trying to copy files: " + ex;
        assert 0;
    }
}

//---------------------------------------------------------------------------------------------------------------------------------
// replace slashes in project custom properties values
static def String formatPathSlashes(String source) {
    if ( (source != null) && (source != "") ) {
        return source.replaceAll("/", "\\\\")
    }
}

//---------------------------------------------------------------------------------------------------------------------------------
// Return path to domibus folder
static def String pathToDomibus(color, log, context) {
    debugLog("  ====  Calling \"pathToDomibus\".", log)
    // Return path to domibus folder base on the "color"
    def propName = ""
    switch (color.toLowerCase()) {
    case "blue":
        propName =  "pathBlue"
        break;
    case "red":
        propName = "pathRed"
        break;
    case "green":
        propName  = "pathGreen"
        break;
    default:
        assert(false), "Unknown side color. Supported values: BLUE, RED, GREEN"
    }

    return context.expand("\${#Project#${propName}}")
}

//---------------------------------------------------------------------------------------------------------------------------------
// Retrieve sql reference from domain ID if connection is not present try to open it
def retrieveSqlConnectionRefFromDomainId(String domainName) {
    debugLog("  ====  Calling \"retrieveSqlConnectionRefFromDomainId\".", log)
    def domain = retrieveDomainId(domainName)
    openDbConnections([domain])
    assert(dbConnections.containsKey(domain) && dbConnections[domain] != null),"Error: Selecting sql references failed: Null values found."
    return dbConnections[domain]
}

//---------------------------------------------------------------------------------------------------------------------------------
// Return url to specific domibus
static def String urlToDomibus(side, log, context) {
    debugLog("  ====  Calling \"urlToDomibus\".", log)
    // Return url to specific domibus base on the "side"
    def propName = ""
    switch (side.toLowerCase()) {
    case "c2":
    case "blue":
    case "sender":
    case "c2default":
        propName = "localUrl"
        break;
    case "c3":
    case "red":
    case "receiver":
    case "c3default":
        propName = "remoteUrl"
        break;
    case "green":
    case "receivergreen":
    case "thirddefault":
        propName  = "greenUrl"
        break;
    case "testEnv":
        propName = "testEnvUrl"
        break;
    default:
        assert(false), "Unknown side. Supported values: sender, receiver, receivergreen and testEnv"
    }
    return context.expand("\${#Project#${propName}}")
}

//---------------------------------------------------------------------------------------------------------------------------------
// This method support JMS project
static def void addPluginCredentialsIfNeeded(context, log, messageMap, String propPluginUsername = defaultPluginAdminC2Default, String propPluginPassword = defaultAdminDefaultPassword) {
    debugLog("  ====  Calling \"addPluginCredentialsIfNeeded\".", log)
    def unsecureLoginAllowed = context.expand("\${#Project#unsecureLoginAllowed}").toLowerCase()
    if (unsecureLoginAllowed == "false" || unsecureLoginAllowed == 0) {
        debugLog("  addPluginCredentialsIfNeeded  [][]  passed values are propPluginUsername=${propPluginUsername} propPluginPasswor=${propPluginPassword} ", log)
        def u = context.expand("\${#Project#${propPluginUsername}}")
        def p = context.expand("\${#Project#${propPluginPassword}}")
        debugLog("  addPluginCredentialsIfNeeded  [][]  Username|Password=" + u + "|" + p, log)
        messageMap.setStringProperty("username", u)
        messageMap.setStringProperty("password", p)
    }
}

//---------------------------------------------------------------------------------------------------------------------------------
// Support fast failure approche and cancel execution when one of the smoke tests fail.

static def void initSmokeTestsResult(testSuite, log) {
	debugLog("  ====  Calling \"initSmokeTestsResult\".", log)
	testSuite.setPropertyValue("TestSuiteSmokeTestsResult", "OK")
}

static def void checkSmokeTestsResult(testRunner, testCase, log) {
	debugLog("  ====  Calling \"checkSmokeTestsResult\".", log)
	if (testRunner.getStatus().toString() == "FAILED") {
		testCase.testSuite.setPropertyValue("TestSuiteSmokeTestsResult", "FAILED")
		log.warn ("Test case CANCELED as one of the smoke tests failed.")
		}
}

static def void checkIfAnySmokeTestsFailed(testRunner, testCase, log) {
	debugLog("  ====  Calling \"checkIfAnySmokeTestsFailed\".", log)
	if (testCase.testSuite.getPropertyValue("TestSuiteSmokeTestsResult") == "FAILED") {
		debugLog("One of smoke tests failed. Now would cancel execution of all other test cases in current test suite.", log)
		testRunner.cancel( "One of smoke tests failed. Aborting whole test suite run." )
	}
}

//---------------------------------------------------------------------------------------------------------------------------------
// Support enabling and disabling the authentication for SOAP requests.

static def enableAuthenticationForTestSuite(filterForTestSuite, context, log, authProfile, authType, endpointPattern = /.*/) {
	updateAuthenticationForTestSuite(filterForTestSuite, context, log, true, authProfile, authType, endpointPattern)
}

static def disableAuthenticationForTestSuite(filterForTestSuite, context, log, authProfile = null, authType = null, endpointPattern = /.*/) {
	updateAuthenticationForTestSuite(filterForTestSuite, context, log, false, authProfile, authType, endpointPattern)
}

static def updateAuthenticationForTestSuite(filterForTestSuite, context, log, enableAuthentication, authProfile, authType, endpointPattern = /.*/) {
    debugLog("START: updateAuthenticationForTestSuite [] [] modyfication of test requests", log)
    if (enableAuthentication)
        log.info "Activating for all SOAP requests Basic Preemptive authentication in test suite ${filterForTestSuite} and endpoint matching pattern ${endpointPattern}.  Previously defined usernames and password would be used."
    else
        log.info "Disabling authentication for all SOAP requests in test suite ${filterForTestSuite} and endpoint matching pattern ${endpointPattern}."

    context.testCase.testSuite.project.getTestSuiteList().each { testSuite ->
            if (testSuite.getLabel() =~ filterForTestSuite) {
                debugLog("test suite: " + testSuite.getLabel(), log)
                testSuite.getTestCaseList().findAll{ ! it.isDisabled() }.each { testCase ->
                        debugLog("test label:" + testCase.getLabel(), log)
                         testCase.getTestStepList().findAll{ ! it.isDisabled() }.each { testStep ->
                            if (testStep instanceof com.eviware.soapui.impl.wsdl.teststeps.WsdlTestRequestStep) {
                                debugLog("Ammending test step: " + testStep.name, log)
                                def httpRequest = testStep.getHttpRequest()
								def endpoint = testStep.getPropertyValue("Endpoint")
								if ( endpoint =~ endpointPattern) {
									if (enableAuthentication)
										httpRequest.setSelectedAuthProfileAndAuthType(authProfile, authType)
									else {
										httpRequest.setSelectedAuthProfileAndAuthType(authProfile, authType)
										httpRequest.removeBasicAuthenticationProfile(authProfile)
										}
								}
								else
									debugLog("Endpoint is not refering to provided patern.", log)
                            }
                    }

                }
            }
    }
    log.info "Authentication update finished"
    debugLog("END: updateAuthenticationForTestSuite [] [] Modification of authentication finished", log)
}

//---------------------------------------------------------------------------------------------------------------------------------
// Start/Stop rest mock service selected by name.

static def void stopRestMockService(String restMockServiceName,log,testRunner) {
	log.info("  ====  Calling \"stopRestMockService\".");
	debugLog("  stopRestMockService  [][]  Rest mock service name:"+restMockServiceName,log);
	def mockService=null; def mockRunner=null;
	try{
		mockService=testRunner.testCase.testSuite.project.getRestMockServiceByName(restMockServiceName);
	}
	catch (Exception ex) {
            log.error "  stopRestMockService  [][]  Can't find rest mock service called: "+restMockServiceName;
            assert 0,"Exception occurred: " + ex;
    }
	mockRunner=mockService.getMockRunner();
	if(mockRunner!=null){
		mockRunner.stop();
		assert(!mockRunner.isRunning()),"  startRestMockService  [][]  Mock service is still running.";
		mockRunner.release() ;
	}	
	log.info ("  stopRestMockService  [][]  Rest mock service "+restMockServiceName+" is stopped.");
}


static def void stopAllRestMockService(log,testRunner) {
	log.info("  ====  Calling \"stopAllRestMockService\".");
    def project=testRunner.testCase.testSuite.project;
	def restMockServicesCount=project.getRestMockServiceCount();
    def restMockServiceName=null;
	for (i in 0..(restMockServicesCount-1)){
		// Stop each rest mock service
		restMockServiceName=project.getRestMockServiceAt(i).getName();
		debugLog("  stopAllRestMockService  [][]  Stopping Rest service: "+restMockServiceName,log);
		stopRestMockService(restMockServiceName,log,testRunner);
		i++;
	}
	log.info ("  stopAllRestMockService  [][]  All rest mock services are stopped.");
}

static def void startRestMockService(String restMockServiceName,log,testRunner,stopAll=1) {
	log.info("  ====  Calling \"startRestMockService\".");
	debugLog("  startRestMockService  [][]  Rest mock service name:"+restMockServiceName,log);
	if(stopAll==1){
		stopAllRestMockService(log,testRunner);
	}else{
		stopRestMockService(restMockServiceName,log,testRunner);
	}
	def mockService=null; def mockRunner=null;
	try{
		mockService=testRunner.testCase.testSuite.project.getRestMockServiceByName(restMockServiceName);
	}
	catch (Exception ex) {
            log.error "  startRestMockService  [][]  Can't find rest mock service called: "+restMockServiceName;
            assert 0,"Exception occurred: " + ex;
    }
	mockService.start();
	mockRunner=mockService.getMockRunner();
	assert(mockRunner!=null),"  startRestMockService  [][]  Can't get mock runner: mock service did not start.";
	assert(mockRunner.isRunning()),"  startRestMockService  [][]  Mock service did not start.";
	log.info ("  startRestMockService  [][]  Rest mock service "+restMockServiceName+" is running.");
}

//---------------------------------------------------------------------------------------------------------------------------------
// Methods handling Pmode properties overwriting
static def processFile(log, file, newFileSuffix, Closure processText) {
	 	 def text = file.text
		 debugLog("New file to be created: " + file.path.toString() + newFileSuffix, log)
   		 def outputTextFile = new File(file.path + newFileSuffix)
  		 outputTextFile.write(processText(text))
  		 if (outputTextFile.text == text)
  		 	log.warn "processFile method returned file with same content! filePath=${file.path}, newFileSuffix=${newFileSuffix}."
}

static def changeConfigurationFile(log, testRunner, filePath, newFileSuffix, Closure processText) {
		 // Checkfilefile exists
        def file = new File(filePath)
        if (!file.exists()) {
            testRunner.fail("File [${filePath}] does not exist. Can't change value.")
            return null
        } else log.info "  changeConfigurationFile  [][]  File [${filePath}] exists."

	processFile(log, file, newFileSuffix, processText)

   log.info "  changeDomibusProperties  [][]  Configuration file [${filePath}] amended"
}
static def updatePmodeEndpoints(log, context, testRunner, filePath, newFileSuffix) {
	def defaulEndpointBlue = 'http://localhost:8080/domibus'
	def newEndpointBlue = context.expand('${#Project#localUrl}')
	def defaulEndpointRed = 'http://localhost:8180/domibus'
	def newEndpointRed = context.expand('${#Project#remoteUrl}')

	debugLog("For file: ${filePath} change endpoint value ${defaulEndpointBlue} to ${newEndpointBlue} and change endpoint value: ${defaulEndpointRed} to ${newEndpointRed} value", log)
	changeConfigurationFile(log, testRunner, filePath, newFileSuffix) { text ->
	    text = text.replaceAll("${defaulEndpointBlue}", "${newEndpointBlue}")
	    text.replaceAll("${defaulEndpointRed}", "${newEndpointRed}")
	}
}

//---------------------------------------------------------------------------------------------------------------------------------

static def uploadPmodeIfStepFailedOrNotRun(log, context, testRunner, testStepToCheckName, pmodeUploadStepToExecuteName) {
	//Check status of step reverting Pmode configuration if needed run step
	Map resultOf = testRunner.getResults().collectEntries { result ->  [ (result.testStep): result.status ] }
	def myStep = context.getTestCase().getTestStepByName(testStepToCheckName)
	if (resultOf[myStep]?.toString() != "OK")  {
		log.info "As test step ${testStepToCheckName} failed or was not run reset PMode in tear down script using ${pmodeUploadStepToExecuteName} test step"
		def tStep = testRunner.testCase.testSuite.project.testSuites["Administration"].testCases["Pmode Update"].testSteps[pmodeUploadStepToExecuteName]
		tStep.run(testRunner, context)
	}
}
//IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII
// Handling domibus properties at runtime
//IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII
    static def changePropertyAtRuntime(String side, String propName, String propNewValue, context, log, String domainValue = "Default", String authUser = null, authPwd = null){
		def authenticationUser = authUser
        def authenticationPwd = authPwd

        debugLog("  ====  Calling \"changePropertyAtRuntime\".", log)
        log.info "  changePropertyAtRuntime  [][]  Start procedure to change property at runtime for Domibus \"" + side + "\"."
        log.info "  changePropertyAtRuntime  [][]  Property to change: " + propName + " new value: " + propNewValue

        try{
            (authenticationUser, authenticationPwd) = retriveAdminCredentialsForDomain(context, log, side, domainValue, authenticationUser, authenticationPwd)

			def commandString = ["curl", urlToDomibus(side, log, context) + "/rest/configuration/properties/" + propName,
							"--cookie", context.expand('${projectDir}') + File.separator + "cookie.txt",
							"-H",  "Content-Type: text/xml",
							"-H","X-XSRF-TOKEN: " + returnXsfrToken(side, context, log, authenticationUser, authenticationPwd),
							"-X", "PUT",
							"-v",
							"--data-binary", "\"" + propNewValue + "\""]
            def commandResult = runCommandInShell(commandString, log)

            assert((commandResult[1]==~ /(?s).*HTTP\/\d.\d\s*200.*/) || commandResult[1].contains("successfully")), "Error: changePropertyAtRuntime: Error while trying to change proeprty at runtime: response doesn't contain the expected outcome HTTP code 200.\nCommand output error: " + commandResult[1]
			log.info "  changePropertyAtRuntime  [][]  Property value was changed"

        } finally {
            resetAuthTokens(log)
        }
        debugLog("  ====  Finished \"changePropertyAtRuntime\".", log)
    }
//---------------------------------------------------------------------------------------------------------------------------------
	static def getPropertyAtRuntime(String side, String propName, context, log, String domainValue = "Default", String authUser = null, authPwd = null){
		def authenticationUser = authUser;
        def authenticationPwd = authPwd;
		def jsonSlurper = new JsonSlurper()
		def propMetadata=null;
		def usersMap=null;
		def i=0;
		def propValue=null;

        debugLog("  ====  Calling \"getPropertyAtRuntime\".", log)
        log.info "  getPropertyAtRuntime  [][]  Property to get: \"$propName\".";

        try{
            (authenticationUser, authenticationPwd) = retriveAdminCredentialsForDomain(context, log, side, domainValue, authenticationUser, authenticationPwd)

			def commandString = ["curl", urlToDomibus(side, log, context) + "/rest/configuration/properties?name=$propName&pageSize=10",
							"--cookie", context.expand('${projectDir}') + File.separator + "cookie.txt",
							"-H",  "Content-Type: text/xml",
							"-H","X-XSRF-TOKEN: " + returnXsfrToken(side, context, log, authenticationUser, authenticationPwd),
							"-v"]
            def commandResult = runCommandInShell(commandString, log)
			assert(commandResult[1]==~ /(?s).*HTTP\/\d.\d\s*200.*/),"Error:getPropertyAtRuntime: Error while trying to connect to domibus.";
			propMetadata=commandResult[0].substring(5);
			debugLog("  getPropertyAtRuntime  [][]  Property serach result: $propMetadata", log);
			usersMap = jsonSlurper.parseText(propMetadata);
			assert(usersMap != null),"Error:getPropertyAtRuntime: Error while parsing the returned property value: null value found.";
			assert(usersMap.items != null),"Error:getPropertyAtRuntime: Error while parsing the returned property value.";

            while ( (i < usersMap.items.size()) && (propValue == null) ) {
                assert(usersMap.items[i] != null),"Error:getPropertyAtRuntime: Error while parsing the list of returned properties.";
                debugLog("  getPropertyAtRuntime  [][]  Iteration $i: comparing --$propName--and--" + usersMap.items[i].metadata.name + "--.", log)
                if (usersMap.items[i].metadata.name == propName) {
					propValue = usersMap.items[i].value;
                }
                i++;
            }
            assert(propValue!=null), "Error: getPropertyAtRuntime: no property found matching name \"$propName\""
			log.info "  getPropertyAtRuntime  [][]  Property \"$propName\" value = \"$propValue\"."

        } finally {
            resetAuthTokens(log)
        }
        debugLog("  ====  Finished \"getPropertyAtRuntime\".", log)
		return propValue;
    }
//---------------------------------------------------------------------------------------------------------------------------------
	static def testPropertyAtRuntime(String side, String propName, String propTestValue, context, log, String domainValue = "Default", String authUser = null, authPwd = null){

		def returnedPropValue = null;

        debugLog("  ====  Calling \"testPropertyAtRuntime\".", log);
		returnedPropValue=getPropertyAtRuntime(side,propName,context,log,domainValue,authUser,authPwd);
        debugLog("  testPropertyAtRuntime  [][]  Comparing property fetched value \"$returnedPropValue\" against input value \"$propTestValue\".",log);
		assert(returnedPropValue.equals(propTestValue)),"Error: testPropertyAtRuntime: property fetched value = \"$returnedPropValue\" instead of \"$propTestValue\"";
        log.info "  testPropertyAtRuntime  [][]  Success: property fetched value \"$returnedPropValue\" and input value \"$propTestValue\" are equal.";
        debugLog("  ====  Finished \"testPropertyAtRuntime\".", log);
    }
//---------------------------------------------------------------------------------------------------------------------------------
    static def void setTestCaseCustProp(custPropName,custPropValue,log,context,testRunner){
        debugLog("  ====  Calling \"setTestCaseCustProp\".", log);
		testRunner.testCase.setPropertyValue(custPropName,custPropValue);
		log.info "Test case level custom property \"$custPropName\" set to \"$custPropValue\"."
		debugLog("  ====  End \"setTestCaseCustProp\".", log);
    }
//---------------------------------------------------------------------------------------------------------------------------------
    static def getTestCaseCustProp(custPropName,log, context, testRunner){
		def retPropVal=null;
        debugLog("  ====  Calling \"getTestCaseCustProp\".", log);
		retPropVal=testRunner.testCase.getPropertyValue(custPropName);
		assert(retPropVal!=null),"Error:getTestCaseCustProp: Couldn't fetch property \"$custPropName\" value";
		log.info "Test case level custom property fetched \"$custPropName\"= \"$retPropVal\"."
		debugLog("  ====  End \"getTestCaseCustProp\".", log);
		return retPropVal;
    }
//---------------------------------------------------------------------------------------------------------------------------------
    static def ifEmptyReturnDef(value1,value2="dummy"){
		if(value1==null){
			assert(value2!="dummy"),"Error: ifEmptyReturnDef: Both values were not set.";
			return value2;
		}
		else{
			if(value1.trim()==""){
				assert(value2!="dummy"),"Error: ifEmptyReturnDef: Both values were not set.";
				return value2;
			}
		}
		return value1;
    }
//---------------------------------------------------------------------------------------------------------------------------------
//---------------------------------------------------------------------------------------------------------------------------------
// Return path to domibus folder
static def String pathToLogFiles(side, log, context) {
    debugLog("  ====  Calling \"pathToDomibus\".", log)
    // Return path to domibus folder base on the "color"
    def propName = ""
    switch (side.toLowerCase()) {
   case "c2":
   case "blue":
   case "sender":
   case "c2default":
        propName =  "logsPathBlue"
        break;
   case "c3":
   case "red":
   case "receiver":
   case "c3default":
        propName = "logsPathRed"
        break;
   case "receivergreen":
   case "green":
   case "thirddefault":
        propName  = "logsPathGreen"
        break;
    default:
        assert(false), "Unknown side color. Supported values: BLUE, RED, GREEN"
    }
    def path = context.expand("\${#Project#${propName}}")
    return (path[-1]=='/' || path[-1]=='\\') ? path : (path + '/')
}

//---------------------------------------------------------------------------------------------------------------------------------
    static def void checkNumberOfLinesToSkipInLogFile(side, logFileToCheck, log, context, testRunner){
        debugLog("  ====  Calling \"checkNumberOfLinesToSkipInL\".", log)
        // Before checking that some action generate specific log entry method store information how many lines already in log to not search tested log entry in old logs

 		def pathToLogFile = pathToLogFiles(side, log, context) + logFileToCheck

		def skipNumberOfLines = context.expand('${#TestCase#skipNumberOfLines}')
		log.info "  checkNumberOfLinesToSkipInLogFile  [][]  skipNumberOfLines property set"

		  // Check file exists
		def testFile = new File(pathToLogFile)
		if (!testFile.exists()) {
					testRunner.fail("File [${pathToLogFile}] does not exist. Can't check logs.")
					return null
		} else debugLog("  checkLogFile  [][]  File [${pathToLogFile}] exists.", log)

		def lineCount = 0
		testFile.eachLine { lineCount++}
		debugLog("Line count = " + lineCount, log)

		testRunner.testCase.setPropertyValue( "skipNumberOfLines", lineCount.toString() )
		log.info "Test case level property skipNumberOfLine set to = " + lineCount
    }

//---------------------------------------------------------------------------------------------------------------------------------
    // Change Domibus configuration file
    static def void checkLogFile(side, logFileToCheck, logValueList, log, context, testRunner,checkPresent=true){
        debugLog("  ====  Calling \"checkLogFile\".", log)
        // Check that logs file contains specific entries specified in list logValueList
        // to set number of lines to skip configuration use method restoreDomibusPropertiesFromBackup(domibusPath,  log, context, testRunner)

 		def pathToLogFile = pathToLogFiles(side, log, context) + logFileToCheck

		def skipNumberOfLines = context.expand('${#TestCase#skipNumberOfLines}')
		if (skipNumberOfLines == "") {
			log.info "  checkLogFile  [][]  skipNumberOfLines property not defined on the test case level would start to search on first line"
			skipNumberOfLines = 0
		} else
		   skipNumberOfLines = skipNumberOfLines.toInteger()

			   // Check file exists
				def testFile = new File(pathToLogFile)
				if (!testFile.exists()) {
					testRunner.fail("File [${pathToLogFile}] does not exist. Can't check logs.")
					return null
				} else log.debug "  checkLogFile  [][]  File [${pathToLogFile}] exists."

			  //def skipNumberOfLines = 0
				def foundTotalNumber = 0
				def fileContent = testFile.text
			   log.info " checkLogFile  [][]  would skip ${skipNumberOfLines} lines"
			   def logSizeInLines = fileContent.readLines().size()
			   if (logSizeInLines < skipNumberOfLines) {
				log.info "Incorrect number of line to skip - it is higher than numbert of lines in log file (" + logSizeInLines + "). Maybe it is new log file would reset skipNumberOfLines value."
				skipNumberOfLines = 0
			   }

				for(logEntryToFind  in logValueList){
					  def found = false
					testFile.eachLine{
						line, lineNumber ->
						//lineNumber++
						if (lineNumber > skipNumberOfLines) {
							if(line =~ logEntryToFind) {
								log.info "  checkLogFile  [][]  In log line $lineNumber searched entry was found. Line value is: $line"
								found = true
							}
						}
						lineNumber++
					}
					if (! found ){
						if(checkPresent){
							log.warn " checkLogFile  [][]  The search string [$logEntryToFind] was NOT in file [${pathToLogFile}]"
						}
					}
					else{
						foundTotalNumber++
					}
				} //loop end
				if(checkPresent){
					if (foundTotalNumber != logValueList.size())
						testRunner.fail(" checkLogFile  [][]  Searching log file failed: Only ${foundTotalNumber} from ${logValueList.size()} entries found.")
					else
						log.info " checkLogFile  [][]  All ${logValueList.size()} entries were found in log file."
				}
				else{
					if (foundTotalNumber != 0)
						testRunner.fail(" checkLogFile  [][]  Searching log file failed: ${foundTotalNumber} from ${logValueList.size()} entries were found.")
					else
						log.info " checkLogFile  [][]  All ${logValueList.size()} entries were not found in log file."
				}
    }


//---------------------------------------------------------------------------------------------------------------------------------
// REST PUT request to test blacklisted characters
    static def curlBlackList_PUT(String side, context, log, String userAC, String domainValue="Default", String userRole="ROLE_ADMIN", String passwordAC="Domibus-123", String authUser=null, String authPwd=null){
        debugLog("  ====  Calling \"curlBlackList_PUT\".", log)
        def usersMap=null;
        def mapElement=null;
        def multitenancyOn=false;
        def commandString = null;
        def commandResult = null;
        def jsonSlurper = new JsonSlurper()
        def curlParams=null;
        def authenticationUser=authUser;
        def authenticationPwd=authPwd;

        try{
            (authenticationUser, authenticationPwd) = retriveAdminCredentialsForDomain(context, log, side, domainValue, authenticationUser, authenticationPwd);
            usersMap = jsonSlurper.parseText(getAdminConsoleUsers(side, context, log))
            if (userExists(usersMap, userAC, log, false)) {
                log.error "Error:curlBlackList_PUT: Admin Console user \"$userAC\" already exist: usernames must be unique.";
            } else {
                curlParams = "[ { \"roles\": \"$userRole\", \"userName\": \"$userAC\", \"password\": \"$passwordAC\", \"status\": \"NEW\", \"active\": true, \"suspended\": false, \"authorities\": [], \"deleted\": false } ]";
                debugLog("  curlBlackList_PUT  [][]  User \"$userAC\" parameters: $curlParams.", log)
				commandString = ["curl ",urlToDomibus(side, log, context) + "/rest/user/users",
								"--cookie", context.expand('${projectDir}') + File.separator + "cookie.txt",
								"-H", "Content-Type: application/json",
								"-H", "X-XSRF-TOKEN: " + returnXsfrToken(side, context, log, authenticationUser, authenticationPwd),
								"-X", "PUT",
								"--data-binary", formatJsonForCurl(curlParams, log),
								"-v"]
                commandResult = runCommandInShell(commandString, log)
                assert((commandResult[1]==~ /(?s).*HTTP\/\d.\d\s*400.*/)&&(commandResult[0]==~ /(?s).*Forbidden character detected.*/)),"Error:curlBlackList_PUT: Forbidden character not detected.";
                log.info "  curlBlackList_PUT  [][]  Forbidden character detected in property value \"$userAC\".";
            }
        } finally {
            resetAuthTokens(log)
        }
    }


//---------------------------------------------------------------------------------------------------------------------------------
// REST GET request to test blacklisted characters
	static def curlBlackList_GET(String side, context, log, String data="\$%25%5E%26\$%25%26\$%26\$", domainValue="Default", String authUser=null, String authPwd=null){
        debugLog("  ====  Calling \"curlBlackList_GET\".", log)
        debugLog("  curlBlackList_GET  [][]  Get Admin Console users for Domibus \"$side\".", log)
        def commandString = null;
        def commandResult = null;
        def multitenancyOn=false;
        def authenticationUser=authUser;
        def authenticationPwd=authPwd;

		try{
			//(authenticationUser, authenticationPwd) = retriveAdminCredentials(context, log, side, authenticationUser, authenticationPwd)
			(authenticationUser, authenticationPwd) = retriveAdminCredentialsForDomain(context, log, side, domainValue, authenticationUser, authenticationPwd)
			commandString="curl "+urlToDomibus(side, log, context)+"/rest/messagelog?orderBy=received&asc=false&messageId="+data+"&messageType=USER_MESSAGE&page=0&pageSize=10 -b "+context.expand( '${projectDir}')+ File.separator + "cookie.txt -v -H \"Content-Type: application/json\" -H \"X-XSRF-TOKEN: "+ returnXsfrToken(side,context,log,authenticationUser,authenticationPwd)+"\" -X GET ";
			commandResult = runCommandInShell(commandString, log)
			assert(commandResult[0]==~ /(?s).*Forbidden character detected.*/),"Error:curlBlackList_GET: Forbidden character not detected.";
			log.info "  curlBlackList_GET  [][]  Forbidden character detected in property value \"$data\".";
		} finally {
            resetAuthTokens(log)
        }
    }

//---------------------------------------------------------------------------------------------------------------------------------
// REST POST request to test blacklisted characters
	static def curlBlackList_POST(String side, context, log, String userLogin = DEFAULT_USER, passwordLogin = DEFAULT_USER_PWD) {
        debugLog("  ====  Calling \"curlBlackList_POST\".", log)
        def commandString = null;
        def commandResult = null;
		def json = ifWindowsEscapeJsonString('{\"username\":\"' + "${userLogin}" + '\",\"password\":\"' + "${passwordLogin}" + '\"}')

        commandString = ["curl", urlToDomibus(side, log, context) + "/rest/security/authentication",
						"-i",
						"-H",  "Content-Type: application/json",
						"--data-binary", json, "-c", context.expand('${projectDir}') + File.separator + "cookie.txt",
						"--trace-ascii", "-"]
		try{
        commandResult = runCommandInShell(commandString, log)
		} finally {
            resetAuthTokens(log)
        }
        assert(commandResult[0]==~ /(?s).*Forbidden character detected.*/),"Error:curlBlackList_POST: Forbidden character not detected."
        log.info "  curlBlackList_POST  [][]  Forbidden character detected in property value \"$userLogin\".";
    }

//---------------------------------------------------------------------------------------------------------------------------------
	static def retrieveQueueNameFromDomibus(String queuesList,queueName, context,log){
		debugLog("  ====  Calling \"retrieveQueueNameFromDomibus\".", log);
		debugLog("  retrieveQueueNameFromDomibus  [][]  Queue names list \"" + queuesList+"\".", log);
		def jsonSlurper=new JsonSlurper();
		def queuesMap=null;
		def i=0;
		def detailedName=null;
		def found=false;

		queuesMap=jsonSlurper.parseText(queuesList);
		assert(queuesMap.jmsDestinations != null),"Error:retrieveQueueNameFromDomibus: Not able to get the jms queue details.";
		debugLog("  retrieveQueueNameFromDomibus  [][]  queuesMap.jmsDestinations map: \"" + queuesMap.jmsDestinations+"\".", log);

		queuesMap.jmsDestinations.find{ queues ->
			queues.value.collect{ properties ->
				if(properties.key.equals("name")){
					if(properties.value.equals(queueName)){
						detailedName=properties.value;
					}
				}
				//debugLog("=="+properties.key+"=="+properties.value+"==",log);
			}
			if(detailedName!=null){
				return true;
			}
			return false;
		}
		if(detailedName!=null){
			log.info("  retrieveQueueNameFromDomibus  [][]  Retrieved queue name from domibus: \"$detailedName\"");
			return(detailedName);
		}
		else{
			log.error "  retrieveQueueNameFromDomibus  [][]  Verified queue name not found: will use input queue name."
			return(queueName);
		}
	}
//---------------------------------------------------------------------------------------------------------------------------------
// Browse jms queue defined by queueName
	static def browseJmsQueue(String side, context, log,queueName="domibus.backend.jms.errorNotifyConsumer", domainValue="Default", String authUser=null, String authPwd=null){
        debugLog("  ====  Calling \"browseJmsQueue\".", log);
        debugLog("  browseJmsQueue  [][]  Browse jms queue \"$queueName\" for Domibus \"$side\" (Domain=\"$domainValue\").", log);
		def detailedQueueName=null;
        def commandString = null;
        def commandResult = null;
        def multitenancyOn=false;
        def authenticationUser=authUser;
        def authenticationPwd=authPwd;
		def json = null;

		(authenticationUser, authenticationPwd) = retriveAdminCredentialsForDomain(context, log, side, domainValue, authenticationUser, authenticationPwd);

		try{
			// Try to retrieve the queue name from domibus to avoid problems like in case of cluster
			commandString="curl "+urlToDomibus(side, log, context)+"/rest/jms/destinations -b "+context.expand( '${projectDir}')+ File.separator + "cookie.txt -v -H \"Content-Type: application/json\" -H \"X-XSRF-TOKEN: "+ returnXsfrToken(side,context,log,authenticationUser,authenticationPwd) +"\" -X GET ";
			commandResult = runCommandInShell(commandString, log);
			detailedQueueName=retrieveQueueNameFromDomibus(commandResult[0].substring(5),queueName,context,log);
			debugLog("  browseJmsQueue  [][]  Queue name set to \"" + detailedQueueName+"\".", log);

			json = ifWindowsEscapeJsonString('{\"source\":\"' + "${detailedQueueName}" + '\"}');
			commandString = null;
			commandResult = null;
			commandString = ["curl", urlToDomibus(side, log, context) + "/rest/jms/messages",
						"-H",  "Content-Type: application/json",
						"-H", "X-XSRF-TOKEN: " + returnXsfrToken(side, context, log, authenticationUser, authenticationPwd),
						"--data-binary", json,
						"-b", context.expand('${projectDir}') + File.separator + "cookie.txt"]

			commandResult = runCommandInShell(commandString, log);
			assert(commandResult[0].contains("{\"messages\"")),"Error:browseJmsQueue: Wrong response.";
		} finally {
            resetAuthTokens(log);
        }
		return commandResult[0].substring(5);
    }

//---------------------------------------------------------------------------------------------------------------------------------

// Search for message in a jms queue identified by its ID
	static def SearchMessageJmsQueue(String side, context, log,searchKey=null,pattern=null,queueName="domibus.backend.jms.errorNotifyConsumer", outcome=true,domainValue="Default",String authUser=null, String authPwd=null){
        debugLog("  ====  Calling \"SearchMessageJmsQueue\".", log)
        debugLog("  SearchMessageJmsQueue  [][]  In Domibus \"$side\", search for message with key \"$searchKey\" and pattern \"$pattern\" in queue \"$queueName\" (Domain=\"$domainValue\").", log)

		def i=0;
		def found=false;
		def jmsMessagesMap=null;
		def jsonSlurper=new JsonSlurper();

		jmsMessagesMap=jsonSlurper.parseText(browseJmsQueue(side,context,log,queueName,domainValue,authUser,authPwd));
		debugLog("  SearchMessageJmsQueue  [][]  jmsMessagesMap:" + jmsMessagesMap, log);
		assert(jmsMessagesMap != null),"Error:SearchMessageJmsQueue: Not able to get the jms queue details.";
		log.info ("jmsMessagesMap size = "+jmsMessagesMap.size());

		switch(queueName.toLowerCase()){
			case "domibus.backend.jms.replyqueue":
				while ((i < jmsMessagesMap.messages.size())&&(!found)) {
					assert(jmsMessagesMap.messages[i] != null),"Error:SearchMessageJmsQueue: Error while parsing jms queue details.";
					if(jmsMessagesMap.messages[i].customProperties.messageId!=null){
						if (jmsMessagesMap.messages[i].customProperties.messageId.toLowerCase() == searchKey.toLowerCase()) {
							debugLog("  SearchMessageJmsQueue  [][]  Found message ID \"" + jmsMessagesMap.messages[i].customProperties.messageId+"\".", log);
							if(jmsMessagesMap.messages[i].customProperties.ErrorMessage!=null){
								if(jmsMessagesMap.messages[i].customProperties.ErrorMessage.contains(pattern)){
									found=true;
								}
							}
							else{
								log.error "  SearchMessageJmsQueue  [][]  jmsMessagesMap.messages[i] has a null ErrorMessage: not possible to use this entry ...";
							}
						}
					}
					else{
						log.error "  SearchMessageJmsQueue  [][]  jmsMessagesMap.messages[i] has a null message ID: not possible to use this entry ...";
					}
					i++;
				}
				break;
			case "domibus.backend.jms.errornotifyconsumer":
				while ((i < jmsMessagesMap.messages.size())&&(!found)) {
					assert(jmsMessagesMap.messages[i] != null),"Error:SearchMessageJmsQueue: Error while parsing jms queue details.";
					if(jmsMessagesMap.messages[i].customProperties.messageId!=null){
						if (jmsMessagesMap.messages[i].customProperties.messageId.toLowerCase() == searchKey.toLowerCase()) {
							debugLog("  SearchMessageJmsQueue  [][]  Found message ID \"" + jmsMessagesMap.messages[i].customProperties.messageId+"\".", log);
							if(jmsMessagesMap.messages[i].customProperties.errorDetail!=null){
								if(jmsMessagesMap.messages[i].customProperties.errorDetail.contains(pattern)){
									found=true;
								}
							}
							else{
								log.error "  SearchMessageJmsQueue  [][]  jmsMessagesMap.messages[i] has a null errorDetail: not possible to use this entry ...";
							}
						}
					}
					else{
						log.error "  SearchMessageJmsQueue  [][]  jmsMessagesMap.messages[i] has a null message ID: not possible to use this entry ...";
					}
					i++;
				}
				break;

			// Put here other cases (queues ...)
			// ...

			default:
                log.error "Unknown queue \"$queueName\"";
        }

		if(outcome){
			assert(found),"Error:SearchMessageJmsQueue: Message with key \"$searchKey\" and pattern \"$pattern\" not found in queue \"$queueName\".";
			log.info("  SearchMessageJmsQueue  [][]  Success: Message with key \"$searchKey\" and pattern \"$pattern\" was found in queue \"$queueName\".");
		}else{
			assert(!found),"Error:SearchMessageJmsQueue: Message with key \"$searchKey\" and pattern \"$pattern\" found in queue \"$queueName\".";
			log.info("  SearchMessageJmsQueue  [][]  Success: Message with key \"$searchKey\" and pattern \"$pattern\" was not found in queue \"$queueName\".");
		}
    }

//---------------------------------------------------------------------------------------------------------------------------------
    static def setLogLevel(String side,context,log,packageName,logLevel,String domainValue = "Default", String outcome = "Success", String authUser = null, authPwd = null){
        debugLog("  ====  Calling \"setLogLevel\".", log)
        def commandString = null
        def commandResult = null
        def multitenancyOn = false
        def authenticationUser = authUser
        def authenticationPwd = authPwd
		def json = null;

        log.info "  setLogLevel  [][]  setting Log level of Package/Class \"$packageName\" for Domibus \"$side\".";
		if((logLevel==null)||(logLevel=="")||(logLevel==" ")){
			logLevel="WARN";
		}
		
        try{
            (authenticationUser, authenticationPwd) = retriveAdminCredentialsForDomain(context, log, side, domainValue, authenticationUser, authenticationPwd)

			json = ifWindowsEscapeJsonString('{\"name\":\"' + "${packageName}" + '\",\"level\":\"' + "${logLevel}" + '\"}')
			commandString = ["curl", urlToDomibus(side, log, context) + "/rest/logging/loglevel",
							"--cookie", context.expand('${projectDir}') + File.separator + "cookie.txt",
							"-H","X-XSRF-TOKEN: " + returnXsfrToken(side, context, log, authenticationUser, authenticationPwd),
							"-H",  "Content-Type: application/json",
							"--data-binary", json,
							"-v"]
            commandResult = runCommandInShell(commandString, log)
            assert(commandResult[0].contains(outcome)),"Error:setLogLevel: Error while trying to set the log level of Package/Class \"$packageName\" for Domibus \"$side\"";
			log.info "  setLogLevel  [][]  Log level successfully set to \"$logLevel\" for Package/Class \"$packageName\" in Domibus \"$side\".";
        } finally {
            resetAuthTokens(log);
        }
    }
//---------------------------------------------------------------------------------------------------------------------------------
    static def getLogLevel(String side,context,log,packageName,String domainValue = "Default", String authUser = null, authPwd = null){
        debugLog("  ====  Calling \"getLogLevel\".", log)
        def commandString = null;
        def commandResult = null;
        def multitenancyOn = false;
        def authenticationUser = authUser;
        def authenticationPwd = authPwd;
		def packagesMetadata=null;
		def packagesMap=null;
		def logValue=null;
		def i=0;
		def jsonSlurper = new JsonSlurper();

        log.info "  getLogLevel  [][]  getting Log level of Package/Class \"$packageName\" for Domibus \"$side\".";

        try{
            (authenticationUser, authenticationPwd) = retriveAdminCredentialsForDomain(context, log, side, domainValue, authenticationUser, authenticationPwd)

			commandString = ["curl", urlToDomibus(side, log, context) + "/rest/logging/loglevel?orderBy=loggerName&asc=false&loggerName=$packageName&page=0&pageSize=500", 
							"--cookie", context.expand('${projectDir}') + File.separator + "cookie.txt",							
							"-H","X-XSRF-TOKEN: " + returnXsfrToken(side, context, log, authenticationUser, authenticationPwd),
							"-H",  "Content-Type: text/xml",							
							"-v"]
            commandResult = runCommandInShell(commandString, log)
            assert(commandResult[1]==~ /(?s).*HTTP\/\d.\d\s*200.*/),"Error:getLogLevel: Error while trying to connect to domibus.";
			packagesMetadata=commandResult[0].substring(5);
			debugLog("  getLogLevel  [][]  Package serach result: $packagesMetadata", log);
			packagesMap = jsonSlurper.parseText(packagesMetadata);
			assert(packagesMap != null),"Error:getLogLevel: Error while parsing the returned packages map: null value found.";
			assert(packagesMap.loggingEntries != null),"Error:getLogLevel: rror while parsing the returned packages map: empty list found.";
			
			while ( (i < packagesMap.loggingEntries.size()) && (logValue == null) ) {
                assert(packagesMap.loggingEntries[i] != null),"Error:getLogLevel: Error while parsing the list of returned entries.";
                debugLog("  getLogLevel  [][]  Iteration $i: comparing --$packageName--and--" + packagesMap.loggingEntries[i].name + "--.", log)
                if (packagesMap.loggingEntries[i].name == packageName) {
					logValue = packagesMap.loggingEntries[i].level;
                }
                i++;
            }
            assert(logValue!=null), "Error: getLogLevel: no package found matching name \"$packageName\"" 														
			log.info "  getLogLevel  [][]  Package \"$packageName\" log level value = \"$logValue\"."
        } finally {
            resetAuthTokens(log);
        }
		debugLog("  ====  Finished \"getLogLevel\".", log)		
		return logValue;
    }

//---------------------------------------------------------------------------------------------------------------------------------
// Alerts in DB verification
//---------------------------------------------------------------------------------------------------------------------------------
	// Verification that mesage status change alert exist for specific message_id
    def verifyMessageStatusChangeAlerts(domainId, propertyValue, eventType, alertStatus, alertLevel, expectNumberOfAlerts = 1, filterEventType = "%") {
        debugLog("  ====  Calling \"verifyMessageStatusChangeAlerts\".", log)
        genericAlertValidation(domainId, "MESSAGE_ID", propertyValue, eventType, alertStatus, alertLevel, expectNumberOfAlerts, filterEventType)
		debugLog("  ====  Ending \"verifyMessageStatusChangeAlerts\".", log)
    }	
	
	 // Verification of user iminnent expiration and expired
    def verifyUserAlerts(domainId, propertyValue, eventType, alertStatus, alertLevel, expectNumberOfAlerts = 1, filterEventType = "%") {
        debugLog("  ====  Calling \"verifyUserAlerts\".", log)
        genericAlertValidation(domainId, "USER", propertyValue, eventType, alertStatus, alertLevel, expectNumberOfAlerts, filterEventType)
		debugLog("  ====  Ending \"verifyUserAlerts\".", log)
    }

    def verifyCertAlerts(domainId, propertyValue, eventType, alertStatus, alertLevel, expectNumberOfAlerts = 1, filterEventType = "%") {
        debugLog("  ====  Calling \"verifyCertAlerts\".", log)
        genericAlertValidation(domainId, "ALIAS", propertyValue, eventType, alertStatus, alertLevel, expectNumberOfAlerts, filterEventType)
		debugLog("  ====  Ending \"verifyCertAlerts\".", log)
    }
	 // Verification of user iminnent expiration and expired
    def genericAlertValidation(domainId, propertyType, propertyValue, eventType, alertStatus, alertLevel, expectNumberOfAlerts = 1, filterEventType = "%") {
        debugLog("  ====  Calling \"genericAlertValidation\".", log)
        log.info"  verifyUserAlerts  [][] Alert to be found propertyType=${propertyType}, propertyValue=${propertyValue}, eventType=${eventType}, alertStatus=${alertStatus}, alertLevel=${alertLevel}"

        def sqlHandler = null
        sqlHandler = retrieveSqlConnectionRefFromDomainId(domainId)

        openDbConnections([domainId])

        // Query DB
		def sqlQuery = """SELECT e.EVENT_TYPE, A.ALERT_STATUS, A.ALERT_LEVEL 
		FROM TB_EVENT_PROPERTY P 
		JOIN TB_EVENT E ON P.FK_EVENT = E.ID_PK 
		JOIN TB_ALERT A ON P.FK_EVENT = A.ID_PK 
		where P.PROPERTY_TYPE = '${propertyType}' 
		  and LOWER(P.STRING_VALUE) = LOWER('${propertyValue}')  
		  and E.EVENT_TYPE LIKE '${filterEventType}'
		  ORDER BY CREATION_TIME DESC"""
		List alerts = sqlHandler.rows(sqlQuery)

		assert alerts.size() == expectNumberOfAlerts, "Error:genericAlertValidation: Incorrect number for alerts expected number was ${expectNumberOfAlerts} and got ${alerts.size()} for specific property type and value ${propertyType}: ${propertyValue} "
		if (expectNumberOfAlerts == 0)
			return ;

		debugLog("Alert found for specific property type and value ${propertyType}: ${propertyValue}. ", log)

		// Check returned alert
		assert alerts[0].EVENT_TYPE.toUpperCase() == eventType.toUpperCase(), "Incorrect event type returned. Expected ${eventType} returned value: ${alerts[0].EVENT_TYPE}"
		assert alerts[0].ALERT_STATUS.toUpperCase() == alertStatus.toUpperCase(), "Incorrect alert status returned. Expected ${alertStatus} returned value: ${alerts[0].ALERT_STATUS}"
		assert alerts[0].ALERT_LEVEL.toUpperCase() == alertLevel.toUpperCase(), "Incorrect alert level returned. Expected ${alertLevel} returned value: ${alerts[0].ALERT_LEVEL}"

        closeDbConnections([domainId])
		log.info "Alert data checked successfully"
		debugLog("  ====  Ending \"genericAlertValidation\".", log)
    }

//---------------------------------------------------------------------------------------------------------------------------------
// UIreplication verification
//---------------------------------------------------------------------------------------------------------------------------------
    static def uireplicationCount(String side, context, log, enabled=true, String domainValue="Default", String authUser=null, String authPwd=null){
        debugLog("  ====  Calling \"uireplicationCount\".", log)
        def usersMap=null;
        def commandString = null;
        def commandResult = null;
        def jsonSlurper = new JsonSlurper()
        def curlParams=null;
        def authenticationUser=authUser;
        def authenticationPwd=authPwd;
		def countValue="";
		def i=0;

        try{
            (authenticationUser, authenticationPwd) = retriveAdminCredentialsForDomain(context, log, side, domainValue, authenticationUser, authenticationPwd);
            commandString = ["curl", urlToDomibus(side, log, context) + "/rest/uireplication/count",
							"--cookie", context.expand('${projectDir}') + File.separator + "cookie.txt",
							"-H","X-XSRF-TOKEN: " + returnXsfrToken(side, context, log, authenticationUser, authenticationPwd),
							"-H",  "Content-Type: application/json",
							"-v"]
            commandResult = runCommandInShell(commandString, log);
			assert(commandResult[1]==~ /(?s).*HTTP\/\d.\d\s*200.*/),"Error:uireplicationCount: UIreplication count command returned an error.";
			if(enabled){
				assert(commandResult[0].substring(7)[0].isNumber()),"Error:uireplicationCount: UIreplication count command response has an unusual format: "+commandResult[0].substring(6);
				while(commandResult[0].substring(7)[i].isNumber()){
					countValue=countValue+commandResult[0].substring(7)[i];
					i=i+1;
				}
			}
			else{
				assert(!(commandResult[0]==~ /(?s).*[Uu][Ii][Rr]eplication.*disabled.*/)||(commandResult[0].substring(7)[0].isNumber())),"Error:uireplicationCount: UIreplication is disabled and must not process any request.";
			}
        } finally {
            resetAuthTokens(log)
        }
		debugLog("  ====  Ending \"uireplicationCount\".", log);
		return countValue;
    }
//---------------------------------------------------------------------------------------------------------------------------------
	static def uireplicationCount_Check(String side, context, log, expectedValue="0", String domainValue="Default", String authUser=null, String authPwd=null){
        debugLog("  ====  Calling \"uireplicationCount_Check\".", log)
		def returnedValue="0";
		returnedValue=uireplicationCount(side,context,log,true,domainValue,authUser,authPwd);
		assert(expectedValue==returnedValue),"Error:uireplicationCount_Check: UIreplication count returned $returnedValue instead of $expectedValue.";
		log.info "Number of records to be synched = $returnedValue";
		debugLog("  ====  Ending \"uireplicationCount_Check\".", log);
    }
//---------------------------------------------------------------------------------------------------------------------------------
    static def uireplicationSync(String side, context, log, enabled=true,String domainValue="Default", String authUser=null, String authPwd=null){
        debugLog("  ====  Calling \"uireplicationSync\".", log);
        def usersMap=null;
        def commandString = null;
        def commandResult = null;
        def jsonSlurper = new JsonSlurper()
        def curlParams=null;
        def authenticationUser=authUser;
        def authenticationPwd=authPwd;
		def retCountValue="0";

        try{
            (authenticationUser, authenticationPwd) = retriveAdminCredentialsForDomain(context, log, side, domainValue, authenticationUser, authenticationPwd);
            commandString = ["curl", urlToDomibus(side, log, context) + "/rest/uireplication/sync",
							"--cookie", context.expand('${projectDir}') + File.separator + "cookie.txt",
							"-H","X-XSRF-TOKEN: " + returnXsfrToken(side, context, log, authenticationUser, authenticationPwd),
							"-H",  "Content-Type: application/json",
							"-v"]
            commandResult = runCommandInShell(commandString, log);
			assert(commandResult[1]==~ /(?s).*HTTP\/\d.\d\s*200.*/),"Error:uireplicationSync: UIreplication sync command returned an error.";
			if(enabled){
				retCountValue=uireplicationCount(side,context,log,true,domainValue,authUser,authPwd);
				if(retCountValue=="0"){
					assert((commandResult[0]==~ /(?s).*[Nn]o records were.*/)&&(!commandResult[0].substring(7)[0].isNumber())),"Error:uireplicationSync: UIreplication sync must not be done because the number of records to be synched is equal to 0.";
				}
				else{
					assert(commandResult[0].substring(7)[0].isNumber()),"Error:uireplicationSync: UIreplication Sync command response has an unusual format: "+commandResult[0].substring(6);
				}
				log.info commandResult[0].substring(6);
			}
			else{
				assert(commandResult[0]==~ /(?s).*[Uu][Ii][Rr]eplication.*disabled.*/),"Error:uireplicationSync: UIreplication is disabled and must not process any request.";
				log.info "  uireplicationSync  [][] UIreplication is disabled."
			}
        } finally {
            resetAuthTokens(log)
        }
		debugLog("  ====  Ending \"uireplicationSync\".", log);
    }
//---------------------------------------------------------------------------------------------------------------------------------
// Load tests verification
//---------------------------------------------------------------------------------------------------------------------------------
    // Wait until all messages are submitted/received (to be used for load tests ...)
    def waitMessagesExchangedNumber(countToReachStrC2="0", countToReachStrC3="0",C2Status="acknowledged", C3Status="received",String senderDomainId=blueDomainID, String receiverDomanId=redDomainID,duration=20,stepDuration=4){
        debugLog("  ====  Calling \"waitMessagesExchangedNumber\".", log)
        def MAX_WAIT_TIME=(duration*60000); // Maximum time to wait to check that all messages are received.
        def STEP_WAIT_TIME=(stepDuration*15000); // Time to wait before re-checking the message status.
		def sqlSender = null; def sqlReceiver = null;
		def countToReachC2=countToReachStrC2.toInteger();def countToReachC3=countToReachStrC3.toInteger();
		def currentCount=0;

		sqlSender = retrieveSqlConnectionRefFromDomainId(senderDomainId)
        sqlReceiver = retrieveSqlConnectionRefFromDomainId(receiverDomanId)
        def usedDomains = [senderDomainId, receiverDomanId]
		openDbConnections(usedDomains);

		if(countToReachC2>0){
			log.info "  waitMessagesExchangedNumber  [][]  Start checking C2 for $countToReachC2 messages. MAX_WAIT_TIME: " + MAX_WAIT_TIME;
			while ( (currentCount < countToReachC2) && (MAX_WAIT_TIME > 0) ) {
				sleep(STEP_WAIT_TIME)
				MAX_WAIT_TIME = MAX_WAIT_TIME - STEP_WAIT_TIME
				sqlSender.eachRow("Select count(*) lignes from TB_MESSAGE_LOG where (LOWER(MESSAGE_TYPE) = 'user_message') and (LOWER(MESSAGE_STATUS) = ${C2Status})") {
					currentCount = it.lignes
				}
				log.info "  waitMessagesExchangedNumber  [][]  Waiting C2:" + MAX_WAIT_TIME + " -- Current:" + currentCount + " -- Target:" + countToReachC2;
			}
		}
        log.info "  waitMessagesExchangedNumber  [][]  finished checking C2 for $countToReachC2 messages. MAX_WAIT_TIME: " + MAX_WAIT_TIME;
        assert(countToReachC2 == currentCount),locateTest(context) + "Error:waitMessagesExchangedNumber: Number of Messages in C2 side is $currentCount instead of $countToReachC2";

		currentCount=0;

		if(countToReachC3>0){
			log.info "  waitMessagesExchangedNumber  [][]  Start checking C3 for $countToReachC3 messages. MAX_WAIT_TIME: " + MAX_WAIT_TIME;
			while ( (currentCount < countToReachC3) && (MAX_WAIT_TIME > 0) ) {
				sleep(STEP_WAIT_TIME)
				MAX_WAIT_TIME = MAX_WAIT_TIME - STEP_WAIT_TIME
				sqlReceiver.eachRow("Select count(*) lignes from TB_MESSAGE_LOG where (LOWER(MESSAGE_TYPE) = 'user_message') and (LOWER(MESSAGE_STATUS) = ${C3Status})") {
					currentCount = it.lignes
				}
				log.info "  waitMessagesExchangedNumber  [][]  Waiting C3:" + MAX_WAIT_TIME + " -- Current:" + currentCount + " -- Target:" + countToReachC3;
			}
		}
        log.info "  waitMessagesExchangedNumber  [][]  finished checking C3 for $countToReachC3 messages. MAX_WAIT_TIME: " + MAX_WAIT_TIME;
        assert(countToReachC3 == currentCount),locateTest(context) + "Error:waitMessagesExchangedNumber: Number of Messages in C3 side is $currentCount instead of $countToReachC3";

        closeDbConnections(usedDomains);
		debugLog("  ====  Ending \"waitMessagesExchangedNumber\".", log)
    }
//---------------------------------------------------------------------------------------------------------------------------------
        // Count total number of messages in C2 and C3 sides (to be used for load tests ...)
    def countCurrentMessagesNumber(testRunner,C2Status="acknowledged", C3Status="received",String senderDomainId=blueDomainID, String receiverDomanId=redDomainID){
        debugLog("  ====  Calling \"countCurrentMessagesNumber\".", log)
		def sqlSender = null; def sqlReceiver = null;
		def countC2=0; def countC3=0;

		def propertyCountC2 = context.expand('${#TestCase#propertyCountC2}');
		def propertyCountC3 = context.expand('${#TestCase#propertyCountC3}');

		sqlSender = retrieveSqlConnectionRefFromDomainId(senderDomainId)
        sqlReceiver = retrieveSqlConnectionRefFromDomainId(receiverDomanId)
        def usedDomains = [senderDomainId, receiverDomanId]
		openDbConnections(usedDomains);

		sqlSender.eachRow("Select count(*) lignes from TB_MESSAGE_LOG where (LOWER(MESSAGE_TYPE) = 'user_message') and (LOWER(MESSAGE_STATUS) = ${C2Status})") {
			countC2 = it.lignes
		}

		sqlReceiver.eachRow("Select count(*) lignes from TB_MESSAGE_LOG where (LOWER(MESSAGE_TYPE) = 'user_message') and (LOWER(MESSAGE_STATUS) = ${C3Status})") {
			countC3 = it.lignes
		}

        closeDbConnections(usedDomains);

		testRunner.testCase.setPropertyValue( "propertyCountC2", countC2.toString() );
		log.info "Setting property \"propertyCountC2\" value: $countC2";
		testRunner.testCase.setPropertyValue( "propertyCountC3", countC3.toString() );
		log.info "Setting property \"propertyCountC3\" value: $countC3";
		debugLog("  ====  Ending \"countCurrentMessagesNumber\".", log);
    }
//---------------------------------------------------------------------------------------------------------------------------------
// DB operations
//---------------------------------------------------------------------------------------------------------------------------------
    // Clean Certificates to be revoked
    def cleanToBeRevCertificates(String senderDomainId = blueDomainID, String receiverDomanId =  redDomainID) {
        debugLog("  ====  Calling \"cleanToBeRevCertificates\".", log)
        def sqlSender = null; def sqlReceiver = null;

        sqlSender = retrieveSqlConnectionRefFromDomainId(senderDomainId)
        sqlReceiver = retrieveSqlConnectionRefFromDomainId(receiverDomanId)
        def usedDomains = [senderDomainId, receiverDomanId]
        openDbConnections(usedDomains)
		sqlSender.execute("DELETE FROM TB_CERTIFICATE WHERE REVOKE_NOTIFICATION_DATE IS NOT NULL");
		sqlReceiver.execute("DELETE FROM TB_CERTIFICATE WHERE REVOKE_NOTIFICATION_DATE IS NOT NULL");
		closeDbConnections(usedDomains)
    }
//---------------------------------------------------------------------------------------------------------------------------------
	// Set user's password default parameter
    def setPasswordDefaultValue(String targetDomainId,String username ,valueToSet=false) {
        debugLog("  ====  Calling \"setPasswordDefaultValue\".", log)
        def sqlDB = null;
		assert((targetDomainId!=null)&&(targetDomainId.trim()!="")),"Error: DomainId provided must not be empty."
        sqlDB = retrieveSqlConnectionRefFromDomainId(targetDomainId)
        def usedDomains = [targetDomainId]
        openDbConnections(usedDomains)
		if(valueToSet){
			sqlDB.execute("UPDATE TB_USER set DEFAULT_PASSWORD=1 WHERE USER_NAME = '${username}'");
		}else{
			sqlDB.execute("UPDATE TB_USER set DEFAULT_PASSWORD=0 WHERE USER_NAME = '${username}'");
		}
		closeDbConnections(usedDomains)
    }



	// Set plugin user's password default parameter
    def setPluginPasswordDefaultValue(String targetDomainId,String username ,valueToSet=false) {
        debugLog("  ====  Calling \"setPluginPasswordDefaultValue\".", log)
        def sqlDB = null;
		assert((targetDomainId!=null)&&(targetDomainId.trim()!="")),"Error: DomainId provided must not be empty."
        sqlDB = retrieveSqlConnectionRefFromDomainId(targetDomainId)
        def usedDomains = [targetDomainId]
        openDbConnections(usedDomains)
		if(valueToSet){
			sqlDB.execute("UPDATE TB_AUTHENTICATION_ENTRY set DEFAULT_PASSWORD=1 WHERE USERNAME = '${username}'");
		}else{
			sqlDB.execute("UPDATE TB_AUTHENTICATION_ENTRY set DEFAULT_PASSWORD=0 WHERE USERNAME = '${username}'");
		}
		closeDbConnections(usedDomains)
    }
//---------------------------------------------------------------------------------------------------------------------------------
// Keystroes and trustores support methods
//---------------------------------------------------------------------------------------------------------------------------------
// Creates a new keystore. The name of the keystore will be "gateway_keystore.jks" unless the optional domain name
// argument is provided - in this case the name of the keystore will be "gateway_keystore_DOMAIN.jks" -.
static def generateKeyStore(context, log, workingDirectory, keystoreAlias, keystorePassword, privateKeyPassword, validityOfKey = 300, keystoreFileName = "gateway_keystore.jks") {

	assert (keystoreAlias?.trim()), "Please provide the alias of the keystore entry as the 3rd parameter (e.g. 'red_gw', 'blue_gw'}"
	assert (keystorePassword?.trim()), "Please provide keystore password"
	assert (privateKeyPassword?.trim()), "Please provide not empty private key password"

	log.info """Generating keystore using: 
	keystoreAlias=${keystoreAlias},  
	keystorePassword=${keystorePassword}, 
	privateKeyPassword=${privateKeyPassword}, 
	keystoreFileName=${keystoreFileName}, 
	validityOfKey=${validityOfKey}"""

    def commandString = null
    def commandResult = null
	def keystoreFile = workingDirectory + keystoreFileName
	log.info keystoreFile

	def startDate = 0
	def defaultValidity = 1 // 1 days is minimal validity for Key and Certificate Management Tool - keytool
	if (validityOfKey<=0) {
		startDate = validityOfKey - defaultValidity
		validityOfKey = defaultValidity
	}

	commandString =  ["keytool", "-genkeypair",
							"-dname",  "C=BE,O=eDelivery,CN=${keystoreAlias}",
							"-alias", "${keystoreAlias}",
							"-keyalg", "RSA",
							"-keysize", "2048",
							"-keypass", "${privateKeyPassword}",
							"-validity", validityOfKey.toString(),
							"-storetype", "JKS",
							"-keystore", "${keystoreFile}",
							"-storepass", "${keystorePassword}" ,
							"-v"]
	if (startDate != 0)
		commandString << "-startdate" << startDate.toString() + "d"

	commandResult = runCommandInShell(commandString, log)
	assert!(commandResult[0].contains("error")),"Error: Output of keytool execution, generating key, should not contain an error. Returned message: " +  commandResult[0] + "||||" +  commandResult[1]

	def pemPath = workingDirectory + returnDefaultPemFileName(keystoreFileName, keystoreAlias)
	def pemFile = new File(pemPath)

	assert !(pemFile.exists()), "The certificate file: ${pemPath} shouldn't already exist"

	commandString =  ["keytool", "-exportcert",
						"-alias", "${keystoreAlias}",
						"-file", pemPath,
						"-keystore", "${keystoreFile}",
						"-storetype", "JKS",
						"-storepass", "${keystorePassword}",
						"-rfc", "-v"]

	commandResult = runCommandInShell(commandString, log)
	assert!(commandResult[0].contains("error")),"Error: Output of keytool execution, generating *.pem file, should not contain an error. Returned message: " +  commandResult[0] + "||" +  commandResult[1]

	pemFile = new File(pemPath)
	pemFile.setWritable(true)

}

// Shared method for creating pem filename
static def String returnDefaultPemFileName(String keystoreFileName, String keystoreAlias) {
	return "${keystoreFileName}_${keystoreAlias}.pem"
}
// Remove files with filenames containing filter string in it
static def void deleteFiles(log, path, filter) {
	log.info "  deleteFiles  [][]  Delete files from [${path}] with filenames containg [${filter}] string."
	try {
		new File(path).eachFile (groovy.io.FileType.FILES) { file ->
		if (file.name.contains(filter)) {
				log.info "Deleting file: " + file.name
				file.delete()
			}
		}
	} catch (Exception ex) {
        log.error "  deleteFiles  [][]  Error while trying to delete files, exception: " + ex;
        assert 0;
    }
}

// Imports an existing public-key certificate into a truststore. If the truststore is missing, it will be created. The
// name of the truststore chosen as destination will be "gateway_truststore.jks" unless the optional truststoreFileName
// argument is provided - in this case the name of the truststore used will be exactly as provided truststoreFileName
// (you need to include extension, example value "gateway_truststore_domain1.jks")
static def updateTrustStore(context, log, workingDirectory, keystoreAlias, keystorePassword, privateKeyPassword, keystoreFileName, truststoreFileName = "gateway_truststore.jks") {

	assert (keystoreAlias?.trim()), "Please provide the alias of the keystore entry as the 3rd parameter (e.g. 'red_gw', 'blue_gw'}"
	assert (keystorePassword?.trim()), "Please provide keystore password"
	assert (privateKeyPassword?.trim()), "Please provide not empty private key password"

	log.info """Updating truststore using: 
	keystoreAlias=${keystoreAlias}, 
	keystorePassword=${keystorePassword}, 
	privateKeyPassword=${privateKeyPassword}, 
	truststoreFileName=${truststoreFileName}, 
	keystoreFileName=${keystoreFileName}"""

	 def commandString = null
     def commandResult = null

	 def truststoreFile = workingDirectory  + truststoreFileName
	 def pemFilePath = workingDirectory  + returnDefaultPemFileName(keystoreFileName, keystoreAlias)

	 def pemFile = new File(pemFilePath)
	 assert (pemFile.exists()), "The certificate ${pemFile} shouldn't already exist"

	commandString =  ["keytool", "-importcert",
							"-alias", "${keystoreAlias}",
							"-file", pemFilePath,
							"-keypass", "${privateKeyPassword}",
							"-keystore", truststoreFile,
							"-storetype", "JKS",
							"-storepass", "${keystorePassword}",
							"-noprompt ", "-v"]

	  commandResult = runCommandInShell(commandString, log)
	  assert!(commandResult[0].contains("error")),"Error: Output of keytool execution, importing *.pem data to truststre, should not contain an error. Returned message: " +  commandResult[0] + "||" +  commandResult[1]

      def trustFile = new File(truststoreFile)
	  trustFile.setWritable(true)
}


//---------------------------------------------------------------------------------------------------------------------------------

// 	Retrieve domain name from project custom property "allDomainsProperties" and store it in test suite level propect property for easy access
    def parseDomainsNamesIntoTSproperty(testRunner) {
        debugLog("  ====  Calling \"parseDomainsNamesIntoTSpropert\".", log);
		def domainNamesMap=[:];
        allDomainsProperties.each { domain, properties ->
            debugLog("  parseDomainsNamesIntoTSpropert  [][]  Parsing domain name for domain ID: \"${domain}\".", log);
			domainNamesMap[properties["site"]+properties["domNo"]]=properties["domainName"];
        }
		testRunner.testCase.testSuite.setPropertyValue("domainsNamesList",JsonOutput.toJson(domainNamesMap).toString());
    }
//---------------------------------------------------------------------------------------------------------------------------------

// 	Retrieve domain name from test suite custom property
    def static String retDomName(side,number,testRunner) {
		def stringValue=testRunner.testCase.testSuite.getPropertyValue("domainsNamesList");
		def jsonSlurper = new JsonSlurper();
        def mapValue = jsonSlurper.parseText(stringValue);
		return mapValue[side+number];
    }



} // Domibus class end

