package eu.domibus.core.alerts.model.service;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.Date;

/**
 * @author Thomas Dussart
 * @since 4.0
 */
@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id", scope = DatePropertyValue.class)
public class DatePropertyValue extends AbstractPropertyValue<Date> {


    private Date value;

    public DatePropertyValue() {
    }

    public DatePropertyValue(final String key, final Date date) {
        this.key = key;
        this.value = date;
    }

    @Override
    public Date getValue() {
        return value;
    }

    @Override
    public void setValue(Date value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SIMPLE_STYLE)
                .append("value", value)
                .toString();
    }
}
