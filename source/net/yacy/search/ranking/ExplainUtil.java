package net.yacy.search.ranking;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.LinkedList;

import net.yacy.cora.util.ConcurrentLog;

import org.apache.solr.common.util.NamedList;
import org.apache.solr.request.LocalSolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.response.JSONResponseWriter;
import org.apache.solr.response.XMLWriter;

// Usage note: all of the methods of this class expect non-null arguments.

public class ExplainUtil {
    public static NamedList constant(String desc, Number val) {
        NamedList result = new NamedList();
        
        result.add("match", true);
        result.add("value", val.doubleValue() );
        result.add("description", desc);
        
        return result;
    }
    
    public static NamedList product2(String productDesc, NamedList var1, NamedList var2) {
        LinkedList<NamedList> list = new LinkedList<NamedList>();
        
        list.add(var1);
        list.add(var2);
        
        NamedList result = new NamedList();
        
        result.add("match", true);
        result.add("value", ( (Number) var1.get("value") ).doubleValue() * ( (Number) var2.get("value") ).doubleValue() );
        result.add("description", productDesc + ", product of:");
        result.add("details", list);
        
        return result;
    }

    public static NamedList product2(String productDesc, String var1Desc, Number var1Val, String var2Desc, Number var2Val) {
        NamedList var1 = constant(var1Desc, var1Val);
        NamedList var2 = constant(var2Desc, var2Val);
        
        return product2(productDesc, var1, var2);
    }
    
    public static NamedList product2(String productDesc, String var1Desc, Number var1Val, NamedList var2) {
        NamedList var1 = constant(var1Desc, var1Val);
        
        return product2(productDesc, var1, var2);
    }
    
    public static NamedList sum(String sumDesc, LinkedList<NamedList> list) {
        double val = 0.0;
        
        for(NamedList var : list) {
            val += ( (Number) var.get("value") ).doubleValue();
        }
        
        NamedList result = new NamedList();
        
        result.add("match", true);
        result.add("value", val);
        result.add("description", sumDesc + ", sum of:");
        result.add("details", list);
        
        return result;
    }
    
    public static NamedList sum(String sumDesc, NamedList[] vars) {
        LinkedList<NamedList> list = new LinkedList<NamedList>(Arrays.asList(vars));
        
        return sum(sumDesc, list);
    }
    
    /*
    Syntax note: 
    
    I'm unable to find a situation where Solr annotates the pow() function,
    so I can't figure out exactly how it's supposed to look.
    
    The behavior I get at the moment is:
    Giving YaCy a Boost Function of:
    pow(2.0,3.0)
    Results in the following explain data:
    {
                          "match":true,
                          "value":8.0,
                          "description":"pow(const(2.0),const(3.0))"}
    
    So as a result, I'm making up my own syntax, in a similar structure to sum and product.
    If anyone can point to a canonical syntax, please file a bug report!
    */
    public static NamedList power(String powerDesc, NamedList var1, NamedList var2) {
        LinkedList<NamedList> list = new LinkedList<NamedList>();
        
        list.add(var1);
        list.add(var2);
        
        NamedList result = new NamedList();
        
        result.add("match", true);
        result.add("value", ( (Number) Math.pow( ( (Number) var1.get("value") ).doubleValue(), ( (Number) var2.get("value") ).doubleValue() ) ).doubleValue() );
        result.add("description", powerDesc + ", power of:");
        result.add("details", list);
        
        return result;
    }
    
    public static NamedList power(String powerDesc, String var1Desc, Number var1Val, NamedList var2) {
        NamedList var1 = constant(var1Desc, var1Val);
        
        return power(powerDesc, var1, var2);
    }
    
    public static NamedList leftshift(String shiftDesc, NamedList var1, NamedList var2) {
        NamedList multiplier = power("exp2(" + ( (Number) var2.get("value") ).toString() + ")", "bitshiftBase", 2.0, var2);
        
        return product2(shiftDesc, multiplier, var1);
    }
    
    public static NamedList leftshift(String shiftDesc, String var1Desc, Number var1Val, String var2Desc, Number var2Val) {
        NamedList var1 = constant(var1Desc, var1Val);
        NamedList var2 = constant(var2Desc, var2Val);
        
        return leftshift(shiftDesc, var1, var2);
    }
    
    /*
    Syntax note: 
    
    I'm unable to find a situation where Solr annotates the floor() function,
    so I can't figure out exactly how it's supposed to look.
    (Same problem as with pow().)
    
    Furthermore, floor() isn't mentioned on https://cwiki.apache.org/confluence/display/solr/Function+Queries .
    There are a bunch of functions missing from there, so unclear if Solr doesn't support floor() or if it's just missing from docs.
    
    So as a result, I'm making up my own syntax.
    My syntax passes an array containing a single NamedList, rather than passing a NamedList directly.
    This seems to be more in spirit with Solr, given that Solr does something similar for "weight(...), result of:"
    
    If anyone can point to a canonical syntax, please file a bug report!
    */
    public static NamedList floor(String floorDesc, NamedList var1) {
        LinkedList<NamedList> list = new LinkedList<NamedList>();
        
        list.add(var1);
        
        NamedList result = new NamedList();
        
        result.add("match", true);
        result.add("value", Math.floor( ( (Number) var1.get("value") ).doubleValue() ) );
        result.add("description", floorDesc + ", floor of:");
        result.add("details", list);
        
        return result;
    }

    public static String stringXML(String name, NamedList theList) {
        try {
            LocalSolrQueryRequest explainRequest = new LocalSolrQueryRequest(null, new NamedList());
            SolrQueryResponse explainResponse = new SolrQueryResponse();

            StringWriter resultWriter = new StringWriter();

            XMLWriter explainWriter = new XMLWriter(resultWriter, explainRequest, explainResponse);
            explainWriter.setIndent(true); // TODO: maybe make this configurable?
            explainWriter.writeNamedList(name, theList);
            explainWriter.close();

            return resultWriter.toString();
        } catch(IOException e) {
            ConcurrentLog.logException(e);
            return "error";
        }
    }

    public static String stringJSON(NamedList theList) {
        try {
            NamedList reqParams = new NamedList();
            reqParams.add("json.nl", "map");
            reqParams.add("indent", "on");

            LocalSolrQueryRequest explainRequest = new LocalSolrQueryRequest(null, reqParams);
            SolrQueryResponse explainResponse = new SolrQueryResponse();

            explainResponse.setAllValues(theList);

            StringWriter resultWriter = new StringWriter();

            JSONResponseWriter explainWriter = new JSONResponseWriter();
            explainWriter.write(resultWriter, explainRequest, explainResponse);

            return resultWriter.toString();
        } catch(IOException e) {
            ConcurrentLog.logException(e);
            return "\"error\"";
        }
    }
}
