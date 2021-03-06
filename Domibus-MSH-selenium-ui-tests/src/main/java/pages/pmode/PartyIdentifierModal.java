package pages.pmode;

import ddsl.dcomponents.DomibusPage;
import ddsl.dobjects.DButton;
import ddsl.dobjects.DInput;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.pagefactory.AjaxElementLocatorFactory;
import utils.Generator;
import utils.TestRunData;

/**
 * @author Catalin Comanici
 * @description:
 * @since 4.1
 */
public class PartyIdentifierModal extends DomibusPage {
	public PartyIdentifierModal(WebDriver driver) {
		super(driver);
		PageFactory.initElements(new AjaxElementLocatorFactory(driver, data.getTIMEOUT()), this);
	}

	@FindBy(css = "md-dialog-content > form > button:nth-child(2)")
	WebElement okBtn;

	@FindBy(css = "md-dialog-content > form > button:nth-child(3)")
	WebElement cancelBtn;

	public void clickOK() throws Exception {
		new DButton(driver, okBtn).click();
		wait.forElementToBeGone(okBtn);
	}

	@FindBy(css = "#partyId_id")
	WebElement partyIdInput;

	@FindBy(css = "#partyIdType_id")
	WebElement partyIdTypeInput;

	@FindBy(css = "#partyIdValue_id")
	WebElement partyIdValueInput;

	public DInput getPartyIdInput() {
		return new DInput(driver, partyIdInput);
	}

	public DInput getPartyIdTypeInput() {
		return new DInput(driver, partyIdTypeInput);
	}

	public DInput getPartyIdValueInput() {
		return new DInput(driver, partyIdValueInput);
	}

	public void fillFileds(String partyId) throws Exception {
		getPartyIdInput().fill(partyId);
		getPartyIdTypeInput().fill(Generator.randomAlphaNumeric(5));
		getPartyIdValueInput().fill("urn:oasis:names:tc:ebcore:partyid-type:unregistered");
	}
}
