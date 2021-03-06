package ddsl.dcomponents.grid;

import ddsl.dcomponents.DComponent;
import ddsl.dobjects.DObject;
import javafx.beans.binding.BooleanExpression;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.pagefactory.AjaxElementLocatorFactory;
import org.testng.asserts.SoftAssert;
import utils.Order;
import utils.TestRunData;

import java.io.File;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.*;


/**
 * @author Catalin Comanici
 * @version 4.1
 */
public class DGrid extends DComponent {

    public DGrid(WebDriver driver, WebElement container) {
        super(driver);
        log.debug("init grid ...");
        PageFactory.initElements(new AjaxElementLocatorFactory(container, data.getTIMEOUT()), this);
    }

    @FindBy(tagName = "datatable-header-cell")
    protected List<WebElement> gridHeaders;

    @FindBy(css = "datatable-row-wrapper > datatable-body-row")
    protected List<WebElement> gridRows;

    protected By cellSelector = By.tagName("datatable-body-cell");

    @FindBy(id = "saveascsvbutton_id")
    protected WebElement downloadCSVButton;

    @FindBy(tagName = "datatable-progress")
    protected WebElement progressBar;

    //	------------------------------------------------
    public Pagination getPagination() {
        return new Pagination(driver);
    }

    public GridControls getGridCtrl() {
        return new GridControls(driver);
    }

    public boolean isPresent() {

        boolean isPresent = false;
        try {
            isPresent = getColumnNames().size() >= 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return isPresent;
    }

    public ArrayList<String> getColumnNames() throws Exception {
        ArrayList<String> columnNames = new ArrayList<>();
        for (int i = 0; i < gridHeaders.size(); i++) {
            columnNames.add(new DObject(driver, gridHeaders.get(i)).getText());
        }
        return columnNames;
    }

    public void selectRow(int rowNumber) throws Exception {
        log.debug("selecting row with number ... " + rowNumber);
        if (rowNumber < gridRows.size()) {
            new DObject(driver, gridRows.get(rowNumber)).click();
            wait.forAttributeToContain(gridRows.get(rowNumber), "class", "active");
        }
    }

    public void doubleClickRow(int rowNumber) throws Exception {

        log.debug("double clicking row ... " + rowNumber);
        if (rowNumber < 0) {
            throw new Exception("Row number too low " + rowNumber);
        }
        if (rowNumber >= gridRows.size()) {
            throw new Exception("Row number too high " + rowNumber);
        }

        Actions action = new Actions(driver);
        action.doubleClick(gridRows.get(rowNumber)).perform();
    }

    public int getRowsNo() {
        return gridRows.size();
    }

    public void waitForRowsToLoad() {
        try {
            wait.forElementToBe(progressBar);
            wait.forElementToBeGone(progressBar);
            wait.forXMillis(100);
        } catch (Exception e) {

        }
    }

    public int getIndexOf(Integer columnIndex, String value) throws Exception {
        for (int i = 0; i < gridRows.size(); i++) {
            WebElement rowContainer = gridRows.get(i);
            String rowValue = new DObject(driver, rowContainer.findElements(cellSelector).get(columnIndex)).getText();
            if (StringUtils.equalsIgnoreCase(rowValue, value)) {
                return i;
            }
        }
        return -1;
    }

    public int scrollTo(String columnName, String value) throws Exception {
        ArrayList<String> columnNames = getColumnNames();
        if (!columnNames.contains(columnName)) {
            throw new Exception("Selected column name '" + columnName + "' is not visible in the present grid");
        }

        int columnIndex = -1;
        for (int i = 0; i < columnNames.size(); i++) {
            if (StringUtils.equalsIgnoreCase(columnNames.get(i), columnName)) {
                columnIndex = i;
            }
        }

        Pagination pagination = getPagination();
        pagination.skipToFirstPage();
        int index = getIndexOf(columnIndex, value);

        while (index < 0 && pagination.hasNextPage()) {
            pagination.goToNextPage();
            index = getIndexOf(columnIndex, value);
        }

        return index;
    }

    public void scrollToAndSelect(String columnName, String value) throws Exception {
        int index = scrollTo(columnName, value);
        if (index < 0) {
            throw new Exception("Cannot select row because it doesn't seem to be in grid");
        }
        selectRow(index);
    }

    public HashMap<String, String> getRowInfo(int rowNumber) throws Exception {
        if (rowNumber < 0) {
            throw new Exception("Row number too low " + rowNumber);
        }
        if (rowNumber > gridRows.size()) {
            throw new Exception("Row number too high " + rowNumber);
        }
        HashMap<String, String> info = new HashMap<>();

        List<String> columns = getColumnNames();
        List<WebElement> cells = gridRows.get(rowNumber).findElements(cellSelector);

        for (int i = 0; i < columns.size(); i++) {
            info.put(columns.get(i), new DObject(driver, cells.get(i)).getText());
        }
        return info;
    }

    public HashMap<String, String> getRowInfo(String columnName, String value) throws Exception {
        int index = scrollTo(columnName, value);
        return getRowInfo(index);
    }

    public void sortBy(String columnName) throws Exception {
        log.debug("column = " + columnName);
        for (int i = 0; i < gridHeaders.size(); i++) {
            DObject column = new DObject(driver, gridHeaders.get(i).findElement(By.cssSelector("div > span.datatable-header-cell-wrapper > span")));
            if (StringUtils.equalsIgnoreCase(column.getText(), columnName)) {
                column.click();
                wait.forAttributeNotEmpty(gridHeaders.get(i), "class");
                try {
                    wait.forAttributeToContain(gridHeaders.get(i), "class", "sort-active");
                } catch (Exception e) {
                }
                return;
            }
        }
        throw new Exception("Column name not present in the grid " + columnName);
    }

    public void scrollToAndDoubleClick(String columnName, String value) throws Exception {
        int index = scrollTo(columnName, value);
        doubleClickRow(index);

//		necessary wait if the method is to remain generic
//		otherwise we need to know what modal is going to be opened so we know what to expect
        wait.forXMillis(1000);
    }

    public List<HashMap<String, String>> getAllRowInfo() throws Exception {
        List<HashMap<String, String>> allRowInfo = new ArrayList<>();

        Pagination pagination = getPagination();
        pagination.skipToFirstPage();

        do {
            for (int i = 0; i < getRowsNo(); i++) {
                allRowInfo.add(getRowInfo(i));
            }
            if (pagination.hasNextPage()) {
                pagination.goToNextPage();
            } else {
                break;
            }
        } while (true);
        return allRowInfo;
    }

    public int getSelectedRowIndex() throws Exception {
        for (int i = 0; i < gridRows.size(); i++) {
            String classStr = new DObject(driver, gridRows.get(i)).getAttribute("class");
            if (null == classStr || classStr.isEmpty()) {
                continue;
            }
            if (classStr.contains("active")) {
                return i;
            }
        }
        return -1;
    }

    public boolean columnsVsCheckboxes() throws Exception {

        HashMap<String, Boolean> columnStatus = getGridCtrl().getAllCheckboxStatuses();
        ArrayList<String> visibleColumns = getColumnNames();

        List<String> checkedColumns = new ArrayList<>();
        for (String k : columnStatus.keySet()) {
            if (columnStatus.get(k) == true) {
                checkedColumns.add(k);
            }
        }

        if (visibleColumns.size() != checkedColumns.size()) {
            return false;
        }

        Collections.sort(visibleColumns);
        Collections.sort(checkedColumns);

        for (int i = 0; i < visibleColumns.size(); i++) {
            if (!StringUtils.equalsIgnoreCase(visibleColumns.get(i), checkedColumns.get(i))) {
                return false;
            }
        }

        return true;
    }

    public List<String> getValuesOnColumn(String columnName) throws Exception {
        List<HashMap<String, String>> allInfo = getAllRowInfo();
        List<String> values = new ArrayList<>();

        for (int i = 0; i < allInfo.size(); i++) {
            String val = allInfo.get(i).get(columnName);
            if (null != val) {
                values.add(val);
            }
        }
        return values;
    }


    public boolean isColumnSortable(String columnName) throws Exception {
        List<String> columns = getColumnNames();
        int index = columns.indexOf(columnName);
        if (index < 0) {
            throw new Exception("Column not visible,. cannot get sortable status");
        }
        WebElement header = gridHeaders.get(index);
        wait.forAttributeNotEmpty(header, "class");
        String classStr = header.getAttribute("class");
        return classStr.contains("sortable");
    }


    public void assertControls(SoftAssert soft) throws Exception {


        getGridCtrl().showCtrls();
        List<String> chkOptions = new ArrayList<>();
        chkOptions.addAll(getGridCtrl().getAllCheckboxStatuses().keySet());

        checkShowLink(soft);
        checkHideLink(soft);
        checkModifyVisibleColumns(soft, chkOptions);
        checkAllLink(soft);
        checkNoneLink(soft);
        checkChangeNumberOfRows(soft);
    }

    public void checkShowLink(SoftAssert soft) throws Exception {
        //-----------Show
        getGridCtrl().showCtrls();
        soft.assertTrue(columnsVsCheckboxes(), "Columns and checkboxes are in sync");
    }

    public void checkHideLink(SoftAssert soft) throws Exception {
        //-----------Hide
        getGridCtrl().hideCtrls();
        soft.assertTrue(!getGridCtrl().areCheckboxesVisible(), "Hide Columns hides checkboxes");
    }

    public void checkModifyVisibleColumns(SoftAssert soft, List<String> chkOptions) throws Exception {
        //-----------Show - Modify - Hide
        for (String colName : chkOptions) {
            log.info("checking checkbox for " + colName);
            getGridCtrl().showCtrls();
            getGridCtrl().checkBoxWithLabel(colName);
            soft.assertTrue(columnsVsCheckboxes());

            getGridCtrl().uncheckBoxWithLabel(colName);
            soft.assertTrue(columnsVsCheckboxes());
        }
    }

    public void checkAllLink(SoftAssert soft) throws Exception {
        //-----------All link
        getGridCtrl().showCtrls();
        log.info("clicking All link");
        getGridCtrl().getAllLnk().click();
        getGridCtrl().hideCtrls();

        List<String> visibleColumns = getColumnNames();
        soft.assertTrue(CollectionUtils.isEqualCollection(visibleColumns, getGridCtrl().getAllCheckboxLabels()), "All the desired columns are visible");
    }

    public void checkNoneLink(SoftAssert soft) throws Exception {
        //-----------None link
        getGridCtrl().showCtrls();
        log.info("clicking None link");
        getGridCtrl().getNoneLnk().click();
        getGridCtrl().hideCtrls();

        List<String> noneColumns = getColumnNames();
        soft.assertTrue(noneColumns.size() == 0, "All the desired columns are visible");
    }

    public void checkChangeNumberOfRows(SoftAssert soft) throws Exception {
        log.info("checking changing number of rows displayed");
        //----------Rows

        int rows = getPagination().getTotalItems();
        log.info("changing number of rows to 25");
        getPagination().getPageSizeSelect().selectOptionByText("25");
        waitForRowsToLoad();

        log.info("checking pagination reset to 1");
        soft.assertTrue(getPagination().getActivePage() == 1, "pagination is reset to 1 after changing number of items per page");

        log.info("check listed number of rows");
        if (rows > 10) {
            soft.assertTrue(getRowsNo() > 10, "Number of rows is bigger than 10");
            soft.assertTrue(getRowsNo() <= 25, "Number of rows is less or equal to 25");
        }

        log.info("check pagination");
        if (rows > 25) {
            soft.assertTrue(getPagination().hasNextPage(), "If there are more than 25 items there are more than one pages");
        }
    }

    public void checkCSVvsGridInfo(String filename, SoftAssert soft) throws Exception {
        log.info("Checking csv file vs grid content");

        Reader reader = Files.newBufferedReader(Paths.get(filename));
        CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader().withIgnoreHeaderCase()
                .withTrim());
        List<CSVRecord> records = csvParser.getRecords();
        List<HashMap<String, String>> gridInfo = getAllRowInfo();

        log.info("comparing number of items");
        soft.assertEquals(gridInfo.size(), records.size(), "Same number of records is listed in the page and in the file");

        log.info("checking listed data");
        for (int i = 0; i < gridInfo.size(); i++) {
            HashMap<String, String> gridRecord = gridInfo.get(i);
            CSVRecord record = records.get(i);
            soft.assertTrue(csvRowVsGridRow(record, gridRecord), "compared rows " + i);
        }
    }

    public void checkCSVvsGridHeaders(String filename, SoftAssert soft) throws Exception {
        log.info("Checking csv file vs grid content");

        Reader reader = Files.newBufferedReader(Paths.get(filename));
        CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader().withIgnoreHeaderCase()
                .withTrim());

        log.info("removing Actions from the list of columns");
        List<String> columnNames = getColumnNames();
        columnNames.remove("Actions");

        List<String> csvFileHeaders = new ArrayList<>();
        csvFileHeaders.addAll(csvParser.getHeaderMap().keySet());
        log.info("removing $jacoco Data from the list of CSV file headers columns");
        csvFileHeaders.remove("$jacoco Data");


        log.info("checking file headers against column names");

        soft.assertTrue(CollectionUtils.isEqualCollection(columnNames, csvFileHeaders), "Headers between grid and CSV file match");

    }

    public boolean csvRowVsGridRow(CSVRecord record, HashMap<String, String> gridRow) throws ParseException {
        log.debug("record: " + record);
        log.debug("gridRow: " + gridRow);
        for (String key : gridRow.keySet()) {
            if (StringUtils.equalsIgnoreCase(key, "Actions")) {
                continue;
            }

            if (isUIDate(gridRow.get(key))) {
                if (!csvVsUIDate(record.get(key), gridRow.get(key))) {
                    return false;
                }
            } else {
                String gridValue = gridRow.get(key).replaceAll("\\s", "");
                String csvValue = record.get(key).replaceAll("\\s", "");
                if (!StringUtils.equalsIgnoreCase(gridValue, csvValue)) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean csvVsUIDate(String csvDateStr, String uiDateStr) throws ParseException {
        Date csvDate = TestRunData.CSV_DATE_FORMAT.parse(csvDateStr);
        Date uiDate = TestRunData.UI_DATE_FORMAT.parse(uiDateStr);

        return csvDate.equals(uiDate);
    }

    public boolean isUIDate(String string) {
        Date uiDate = null;
        try {
            uiDate = TestRunData.UI_DATE_FORMAT.parse(string);
        } catch (ParseException e) {
            return false;
        }
        return true;
    }

    public String getSortedColumnName() throws Exception {
        String sortClassName = "sort-active";
        for (WebElement gridHeader : gridHeaders) {
            DObject headerObj = weToDobject(gridHeader);
            String classes = headerObj.getAttribute("class");
            if (classes.contains(sortClassName)) {
                return headerObj.getText();
            }
        }
        return null;
    }

    public Order getSortOrder() throws Exception {
        String sortIndicatorDesc = "sort-desc";
        String sortIndicatorAsc = "sort-asc";
        String columnName = getSortedColumnName();
        if (null == columnName) {
            return null;
        }

        for (WebElement gridHeader : gridHeaders) {
            DObject headerObj = weToDobject(gridHeader);
            if (StringUtils.equalsIgnoreCase(headerObj.getText(), columnName)) {
                String classes = headerObj.getAttribute("class");
                if (classes.contains(sortIndicatorDesc)) {
                    return Order.DESC;
                }
                if (classes.contains(sortIndicatorAsc)) {
                    return Order.ASC;
                }
            }

        }
        throw new Exception("Sort order cannot be determined");
    }

    public void checkCSVvsGridDataForSpecificRow(String filename, SoftAssert soft, int i) throws Exception {
        log.info("Checking csv file vs grid content for specific row");

        Reader reader = Files.newBufferedReader(Paths.get(filename));
        CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader().withIgnoreHeaderCase()
                .withTrim());
        List<CSVRecord> records = csvParser.getRecords();
        int csvRowCount = records.size();
        Boolean isdataPresent = false;

        log.info("verifying presence of grid row data for  row " + i + "in csv");
        for (int j = 0; j < csvRowCount; j++) {
            CSVRecord record = records.get(j);
            HashMap<String, String> gridInfo = getRowInfo(i);
            if (csvRowVsGridRow(record, gridInfo)) {
                isdataPresent = true;
            }
        }
        soft.assertTrue(isdataPresent, "Grid row data is present in csv irrespective of row order");


    }

    public String getRowSpecificColumnVal(int rowNumber, String columnName) throws Exception {
        HashMap<String, String> gridInfo = getRowInfo(rowNumber);
        String colName = columnName;
        if (gridInfo.containsKey(columnName)) {
            String val = gridInfo.get(columnName);
            return val;
        }
        return "";
    }

    //This method will specifically verify csv headers for plugin user page for both authentication type
    public void checkCSVvsGridHeadersWithAdditionalHeaders(String filename, SoftAssert soft, String authType) throws Exception {
        log.info("Checking csv file vs grid content");

        Reader reader = Files.newBufferedReader(Paths.get(filename));
        CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader().withIgnoreHeaderCase()
                .withTrim());

        log.info("removing Actions from the list of columns");
        List<String> columnNames = getColumnNames();
        columnNames.remove("Actions");

        List<String> csvFileHeaders = new ArrayList<>();
        csvFileHeaders.addAll(csvParser.getHeaderMap().keySet());
        log.info("removing $jacoco Data from the list of CSV file headers columns");
        csvFileHeaders.remove("$jacoco Data");

        List<String> additionalFieldBasic = new ArrayList<>();
        additionalFieldBasic.add("Certificate Id");
        additionalFieldBasic.add("Authentication Type");
        additionalFieldBasic.add("Suspended");

        List<String> additionalFieldCert = new ArrayList<>();
        additionalFieldCert.add("Username");
        additionalFieldCert.add("Authentication Type");
        additionalFieldCert.add("Active");
        additionalFieldCert.add("Suspended");

        if (authType.equals("BASIC")) {
            log.info("checking additional field headers");
            soft.assertTrue(csvFileHeaders.containsAll(additionalFieldBasic), "Csv file contaisn all mandatory additional fields");
            log.info("Removing additional field headers for comparison");
            csvFileHeaders.removeAll(additionalFieldBasic);

        }
        if (authType.equals("CERTIFICATE")) {
            log.info("Checking additional field headers for Certificae authentication type");
            soft.assertTrue(csvFileHeaders.containsAll(additionalFieldCert), "Mandatory additional fields are available for CERT type auth");
            log.info("Removing additional headers for comparison");
            csvFileHeaders.removeAll(additionalFieldCert);
        }

        log.info("checking file headers against column names");

        soft.assertTrue(CollectionUtils.isEqualCollection(columnNames, csvFileHeaders), "Headers between grid and CSV file match");

    }

    /**
     * This method will return all headers present in csv
     *
     * @param filename Csv file name to identify header
     * @return It will return all headers in list
     */
    public List<String> getCsvHeaders(String filename) throws Exception {
        Reader reader = Files.newBufferedReader(Paths.get(filename));

        CSVParser csvParser = new CSVParser(reader,
                CSVFormat.DEFAULT.withFirstRecordAsHeader().withIgnoreHeaderCase()
                        .withTrim());
        List<String> csvFileHeaders = new ArrayList<>();

        csvFileHeaders.addAll(csvParser.getHeaderMap().keySet());
        return csvFileHeaders;
    }

    /**
     * This method will return records available in provided csv file
     *
     * @param filename Csv file name to extract records
     * @return It will return available records for all rows
     */
    public List<CSVRecord> getCsvRecords(String filename) throws Exception {

        Reader reader = Files.newBufferedReader(Paths.get(filename));
        CSVParser csvParser = new CSVParser(reader,
                CSVFormat.DEFAULT.withFirstRecordAsHeader().withIgnoreHeaderCase()
                        .withTrim());

        return csvParser.getRecords();

    }

}