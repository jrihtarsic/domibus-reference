package ddsl.dobjects;

import org.openqa.selenium.ElementNotInteractableException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;


/**
 * @author Catalin Comanici
 * @version 4.1
 */


public class Checkbox extends DObject {

	WebElement labelElement;

	public Checkbox(WebDriver driver, WebElement element) {
		this(driver, element, null);
	}

    public Checkbox(WebDriver driver, WebElement element, WebElement labelElement) {
        super(driver, element);

        this.labelElement = labelElement;
    }

    public boolean isChecked() throws Exception {
        if (isPresent()) {
            return (null != element.getAttribute("checked"));
        }
        throw new DObjectNotPresentException();
    }

    public void check() throws Exception {
        if (isChecked()) return;
        if (isEnabled()) {
			clickCheckbox();
            return;
        }
        throw new Exception("Checkbox is not enabled");
    }

    public void uncheck() throws Exception {
        if (!isChecked()) return;
        if (isEnabled()) {
			clickCheckbox();
            return;
        }
        throw new Exception("Checkbox is not enabled");
    }

    private void clickCheckbox() {
        try {
            element.click();
        } catch (ElementNotInteractableException ex) {
        	// in mat-checkbox the input is actually hidden, and the user has to click on the label to interact with it
            if (this.labelElement != null)
				this.labelElement.click();
		}
    }


}
