package cc.xfl12345.mybigdata.server.model.api.database.result;

import cc.xfl12345.mybigdata.server.model.database.table.IntegerContent;
import cc.xfl12345.mybigdata.server.model.database.table.StringContent;
import lombok.Getter;

import java.math.BigDecimal;

public class NumberTypeResult extends SingleDataResultBase {
    @Getter
    protected BigDecimal number = null;

    public void setNumber(BigDecimal number) {
        this.number = number;
    }

    public void setNumber(IntegerContent integerContent) {
        number = new BigDecimal(integerContent.getContent());
    }

    public void setNumber(Long integer) {
        number = new BigDecimal(integer);
    }

    public void setNumber(StringContent stringContent) {
        number = new BigDecimal(stringContent.getContent());
    }

    public void setNumber(String string) {
        number = new BigDecimal(string);
    }

    public Double getDouble() {
        return number.doubleValue();
    }

    public Long getLong() {
        return number.longValue();
    }

    public String getString() {
        return number.toPlainString();
    }
}
