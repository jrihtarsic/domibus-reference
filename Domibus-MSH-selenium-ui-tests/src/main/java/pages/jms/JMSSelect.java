package pages.jms;

import ddsl.dobjects.Select;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Catalin Comanici
 * @since 4.1
 */
public class JMSSelect extends Select {
    public JMSSelect(WebDriver driver, WebElement container) {
        super(driver, container);
    }

    public int selectQueueWithMessages() throws Exception {
        String qName = getQueueNameWithMessages("");
        if (StringUtils.isEmpty(qName)) {
            return 0;
        }

        selectOptionByText(qName);
        return getListedNoOfMessInQName(qName);
    }

    public int getListedNoOfMessInQName(String qName) {
        int startIndex = qName.lastIndexOf("(");
        int endIndex = qName.lastIndexOf(")");

        return Integer.valueOf(qName.substring(startIndex + 1, endIndex));
    }

    public int selectQueueWithMessagesNotDLQ() throws Exception {
        String qName = getQueueNameWithMessages("DLQ");
        if (StringUtils.isEmpty(qName)) {
            return 0;
        }
        selectOptionByText(qName);
        return Integer.valueOf(qName.replaceAll("\\D", ""));
    }

    public void selectDLQQueue() throws Exception {

        List<String> queues = getOptionsTexts();

        for (String queue : queues) {
            if (queue.contains("DLQ")) {
                selectOptionByText(queue);
                return;
            }
        }
        throw new RuntimeException(new Exception("DLQ queue not found"));
    }


    private String getQueueNameWithMessages(String excludePattern) throws Exception {
        List<String> queues = getOptionsTexts();
        List<String> filtered;
        if (null != excludePattern && !excludePattern.isEmpty()) {
            filtered = queues.stream().filter(queue -> !queue.contains(excludePattern)).collect((Collectors.toList()));
        } else {
            filtered = queues;
        }

        for (String queue : filtered) {
            int noOfmess = getListedNoOfMessInQName(queue);
            if (noOfmess > 0) {
                return queue;
            }
        }
        return null;
    }


}
