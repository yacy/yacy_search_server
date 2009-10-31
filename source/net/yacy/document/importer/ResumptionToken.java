package net.yacy.document.importer;

import java.text.Collator;
import java.text.ParseException;
import java.util.Date;
import java.util.Locale;
import java.util.TreeMap;

import net.yacy.kelondro.util.DateFormatter;

public class ResumptionToken  extends TreeMap<String, String> {
    
    private static final long serialVersionUID = -8389462290545629792L;

    // use a collator to relax when distinguishing between lowercase und uppercase letters
    private static final Collator insensitiveCollator = Collator.getInstance(Locale.US);
    static {
        insensitiveCollator.setStrength(Collator.SECONDARY);
        insensitiveCollator.setDecomposition(Collator.NO_DECOMPOSITION);
    }
    
    public ResumptionToken(
            Date expirationDate,
            int completeListSize,
            int cursor,
            int token
            ) {
        super((Collator) insensitiveCollator.clone());
        this.put("expirationDate", DateFormatter.formatISO8601(expirationDate));
        this.put("completeListSize", Integer.toString(completeListSize));
        this.put("cursor", Integer.toString(cursor));
        this.put("token", Integer.toString(token));
    }
    
    public ResumptionToken(
            String expirationDate,
            int completeListSize,
            int cursor,
            int token
            ) {
        super((Collator) insensitiveCollator.clone());
        this.put("expirationDate", expirationDate);
        this.put("completeListSize", Integer.toString(completeListSize));
        this.put("cursor", Integer.toString(cursor));
        this.put("token", Integer.toString(token));
    }
    
    public Date getExpirationDate() {
        String d = this.get("expirationDate");
        if (d == null) return null;
        try {
            return DateFormatter.parseISO8601(d);
        } catch (ParseException e) {
            e.printStackTrace();
            return new Date();
        }
    }
    
    public int getCompleteListSize() {
        String t = this.get("completeListSize");
        if (t == null) return 0;
        return Integer.parseInt(t);
    }
    
    public int getCursor() {
        String t = this.get("cursor");
        if (t == null) return 0;
        return Integer.parseInt(t);
    }
    
    public int getToken() {
        String t = this.get("token");
        if (t == null) return 0;
        return Integer.parseInt(t);
    }
    
    public String toString() {
        return "expirationDate=" + DateFormatter.formatISO8601(this.getExpirationDate()) + ", completeListSize=" + getCompleteListSize() +
        ", cursor=" + this.getCursor() + ", token=" + this.getToken();
    }
    
}
