package pages.pmode.parties;

import ddsl.dcomponents.DomibusPage;
import ddsl.dcomponents.grid.DGrid;
import ddsl.dobjects.DButton;
import ddsl.dobjects.DInput;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.pagefactory.AjaxElementLocatorFactory;
import org.w3c.dom.Document;
import pages.pmode.current.PModeCofirmationModal;
import pages.pmode.current.PModeCurrentPage;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;

import static javax.script.ScriptEngine.FILENAME;

/**
 * @author Catalin Comanici
 * @since 4.1
 */
public class PModePartiesPage extends DomibusPage {
    public PModePartiesPage(WebDriver driver) {
        super(driver);
        PageFactory.initElements(new AjaxElementLocatorFactory(driver, data.getTIMEOUT()), this);
    }

    @FindBy(css = "#partyTable")
    WebElement gridContainer;

    @FindBy(css = "app-party > table button:nth-child(1)")
    WebElement cancelButton;

    @FindBy(css = "app-party > table button:nth-child(2)")
    WebElement saveButton;

    @FindBy(css = "app-party > table button:nth-child(3)")
    WebElement newButton;

    @FindBy(css = "app-party > table button:nth-child(4)")
    WebElement editButton;

    @FindBy(css = "app-party > table button:nth-child(5)")
    WebElement deleteButton;


    @FindBy(id = "name_id")
    WebElement nameField;

    @FindBy(id = "searchbutton_id")
    WebElement searchButton;

    public DButton getCancelButton() {
        return new DButton(driver, cancelButton);
    }

    public DButton getSaveButton() {
        return new DButton(driver, saveButton);
    }

    public DButton getNewButton() {
        return new DButton(driver, newButton);
    }

    public DButton getEditButton() {
        return new DButton(driver, editButton);
    }

    public DButton getDeleteButton() {
        return new DButton(driver, deleteButton);
    }

    public DGrid grid() {
        return new DGrid(driver, gridContainer);
    }

    public PartiesFilters filters() {
        return new PartiesFilters(driver);
    }

    public PModeCurrentPage getPage() {
        return new PModeCurrentPage(driver);
    }

    public PModeCofirmationModal getModal() {
        return new PModeCofirmationModal(driver);
    }

    public DInput getNameField() {
        return new DInput(driver, nameField);
    }

    public DButton getSearchButton() {
        return new DButton(driver, searchButton);
    }


    public String printPmode(Document xml) throws Exception {
        Transformer tf = TransformerFactory.newInstance().newTransformer();
        tf.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        tf.setOutputProperty(OutputKeys.INDENT, "yes");
        Writer out = new StringWriter();
        tf.transform(new DOMSource(xml), new StreamResult(out));
        return out.toString();
    }


}
