package net.yacy.interaction;

import java.nio.charset.Charset;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.data.meta.DigestURI;


public class AugmentHtmlStream {
	
	public static StringBuffer process (StringBuffer data, Charset charset, DigestURI url, RequestHeader requestHeader) {
		
		boolean augmented = false;
		
		String Doc = data.toString();
		
		if (augmented) {
			
			return (new StringBuffer (Doc));
		} else {
			return (data);
		}
	}

}
