package pages.pmode;

import ddsl.dcomponents.DomibusPage;
import ddsl.dcomponents.grid.DGrid;
import ddsl.dobjects.DButton;
import ddsl.dobjects.DInput;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.pagefactory.AjaxElementLocatorFactory;
import utils.TestRunData;

/**
 * @author Catalin Comanici
 * @description:
 * @since 4.1
 */
public class PartyModal extends DomibusPage {
	public PartyModal(WebDriver driver) {
		super(driver);
		PageFactory.initElements(new AjaxElementLocatorFactory(driver, data.getTIMEOUT()), this);

		wait.forElementToBeEnabled(nameInput);
	}

	@FindBy(css = "app-party-details > md-dialog-content > form > button:nth-child(5)")
	protected WebElement okBtn;

	@FindBy(css = "md-dialog-container button:nth-of-type(6)")
	protected WebElement cancelBtn;

	@FindBy(css = "#name_id_detail")
	protected WebElement nameInput;

	@FindBy(css = "#endPoint_id_detail")
	protected WebElement endpointInput;

	@FindBy(css = "#subjectName_id")
	protected WebElement certSubjectNameInput;

	@FindBy(css = "#validityFrom_id")
	protected WebElement certValidFromInput;

	@FindBy(css = "#validityTo_id")
	protected WebElement certValidToInput;

	@FindBy(css = "#issuer_id")
	protected WebElement certIssuerInput;

	@FindBy(css = "#fingerPrint_id")
	protected WebElement certFingerPrintInput;

	@FindBy(css = "md-dialog-container div:nth-child(2) > md-card > md-card-content > div > label")
	protected WebElement importButton;

	@FindBy(css = "#identifierTable")
	protected WebElement identifierTable;

	@FindBy(css = "md-dialog-content div:nth-child(3) button:nth-child(1)")
	protected WebElement newIdentifierButton;

	@FindBy(css = "md-dialog-content div:nth-child(3) button:nth-child(2)")
	protected WebElement editIdentifierButton;

	@FindBy(css = "md-dialog-content div:nth-child(3) button:nth-child(3)")
	protected WebElement delIdentifierButton;

	@FindBy(css = "#processTable")
	protected WebElement processTable;


	public DInput getNameInput() {
		return new DInput(driver, nameInput);
	}

	public DInput getEndpointInput() {
		return new DInput(driver, endpointInput);
	}

	public DInput getCertSubjectNameInput() {
		return new DInput(driver, certSubjectNameInput);
	}

	public DInput getCertValidFromInput() {
		return new DInput(driver, certValidFromInput);
	}

	public DInput getCertValidToInput() {
		return new DInput(driver, certValidToInput);
	}

	public DInput getCertIssuerInput() {
		return new DInput(driver, certIssuerInput);
	}

	public DInput getCertFingerPrintInput() {
		return new DInput(driver, certFingerPrintInput);
	}

	public DButton getImportButton() {
		return new DButton(driver, importButton);
	}

	public DGrid getIdentifierTable() {
		return new DGrid(driver, identifierTable);
	}

	public DButton getNewIdentifierButton() {
		return new DButton(driver, newIdentifierButton);
	}

	public DButton getEditIdentifierButton() {
		return new DButton(driver,editIdentifierButton);
	}

	public DButton getDelIdentifierButton() {
		return new DButton(driver,delIdentifierButton);
	}

	public DGrid getProcessTable() {
		return new DGrid(driver, processTable);
	}

	public void fillNewPartyForm(String name, String endpoint, String partyId) throws Exception{
		getNameInput().fill(name);
		getEndpointInput().fill(endpoint);
		getNewIdentifierButton().click();

		PartyIdentifierModal pimodal = new PartyIdentifierModal(driver);
		pimodal.fillFileds(partyId);
		pimodal.clickOK();
	}

	public void clickOK() throws Exception{
		new DButton(driver, okBtn).click();
		wait.forElementToBeGone(okBtn);
	}



}
