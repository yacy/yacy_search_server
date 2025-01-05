<h3>Referrer Policy Settings</h3>

<p>
	When loading pages and navigating through links, a web browser sends some information about the origin of the request, 
by filling the <a href="https://tools.ietf.org/html/rfc7231#section-5.5.2" title="'Referer' section from the standard IETF specification">"Referer"</a> HTTP header.
Visited websites can process this information as they whish, so this can become a privacy concern, for example when coming from a page which contains searched terms in its URL.
</p>

<p>
	This page offers some configuration settings to instruct your browser how it should fill this referrer information. 
	Beware that every browser behaves differently : some settings may be unsupported by your particular browser and therefore ignored. 
	If you are really concerned about privacy, please check what is really sent by your browser by using its embedded developers tools network console, or with the network traffic analyzer of your choice. 
</p>

<form action="SettingsAck_p.html" method="post" class="form-horizontal">
	<input type="hidden" name="transactionToken" value="#[transactionToken]#"/>
	<fieldset>
		<legend>Global policy</legend>
		
		<p>
			This referrer policy applies for every page on this peer. It is set by the "meta" HTML tag.
		</p> 
		<p> Values are sorted by decreasing privacy level. 
			See the related <a href="https://www.w3.org/TR/referrer-policy/#referrer-policies">W3C recommendation</a> for full details and available values.
		</p>
		
		<div class="form-group">
			<div class="col-sm-4">
				<div class="radio">
					<label>
						<input name="metaPolicy" id="metaPolicyNoReferrer" 
							type="radio" #(metaPolicyNoReferrerChecked)#::checked#(/metaPolicyNoReferrerChecked)#
							value="no-referrer" aria-describedby="metaPolicyNoReferrerInfo"/>
						no-referrer
					</label>
				</div>
			</div>
			<div class="col-sm-8" id="metaPolicyNoReferrerInfo">
	 			Highest privacy setting : referrer information should never be sent, even when navigating on this peer internal links. 
	 			Be careful with this : some websites might reject requests with no referrer.
            </div>
		</div>
		<div class="form-group">
			<div class="col-sm-4">
				<div class="radio">
					<label>
						<input name="metaPolicy" id="metaPolicySameOrigin" 
							type="radio" #(metaPolicySameOriginChecked)#::checked#(/metaPolicySameOriginChecked)# 
							value="same-origin" aria-describedby="metaPolicySameOriginInfo"/>
						same-origin
					</label>
				</div>
			</div>
			<div class="col-sm-8" id="metaPolicySameOriginInfo">
	 			Peer internal links : referrer information should be stripped from any private data and contain only this peer host name.<br/>
	 			External links : referrer information should never be sent.
            </div>
		</div>
		<div class="form-group">
			<div class="col-sm-4">
				<div class="radio">
					<label>
						<input name="metaPolicy" id="metaPolicyStrictOrigin" 
							type="radio" #(metaPolicyStrictOriginChecked)#::checked#(/metaPolicyStrictOriginChecked)# 
							value="strict-origin" aria-describedby="metaPolicyStrictOriginInfo"/>
						strict-origin
					</label>
				</div>
			</div>
			<div class="col-sm-8" id="metaPolicyStrictOriginInfo">
	 			Peer internal and external links : referrer information should be stripped from any private data and contain only this peer host name.<br/>
	 			Restriction : when a link downgrades from a TLS secured connection (https) on this peer to an unsecured target (http), no referrer information at all should be sent.
            </div>
		</div>
		<div class="form-group">
			<div class="col-sm-4">
				<div class="radio">
					<label>
						<input name="metaPolicy" id="metaPolicyOrigin" 
							type="radio" #(metaPolicyOriginChecked)#::checked#(/metaPolicyOriginChecked)# 
							value="origin" aria-describedby="metaPolicyOriginInfo"/>
						origin
					</label>
				</div>
			</div>
			<div class="col-sm-8" id="metaPolicyOriginInfo">
	 			Peer internal and external links : referrer information should be stripped from any private data and contain only this peer host name.<br/>
	 			Note : this value is also compatible with legacy values from the older <a href="https://wiki.whatwg.org/wiki/Meta_referrer">specification draft</a>.
            </div>
		</div>
		<div class="form-group">
			<div class="col-sm-4">
				<div class="radio">
					<label>
						<input name="metaPolicy" id="metaPolicyStrictOriginWhenCrossOrigin" 
							type="radio" #(metaPolicyStrictOriginWhenCrossOriginChecked)#::checked#(/metaPolicyStrictOriginWhenCrossOriginChecked)# 
							value="strict-origin-when-cross-origin" aria-describedby="metaPolicyStrictOriginWhenCrossOriginInfo"/>
						strict-origin-when-cross-origin
					</label>
				</div>
			</div>
			<div class="col-sm-8" id="metaPolicyStrictOriginWhenCrossOriginInfo">
	 			Peer internal links : referrer information should contain full URLs.<br/>
	 			External links : referrer information should be stripped from any private data and contain only this peer host name.<br/>
	 			Restriction : when an external link downgrades from a TLS secured connection (https) on this peer to an unsecured target (http), no referrer information at all should be sent.
            </div>
		</div>
		<div class="form-group">
			<div class="col-sm-4">
				<div class="radio">
					<label>
						<input name="metaPolicy" id="metaPolicyOriginWhenCrossOrigin" 
							type="radio" #(metaPolicyOriginWhenCrossOriginChecked)#::checked#(/metaPolicyOriginWhenCrossOriginChecked)# 
							value="origin-when-cross-origin" aria-describedby="metaPolicyOriginWhenCrossOriginInfo"/>
						origin-when-cross-origin
					</label>
				</div>
			</div>
			<div class="col-sm-8" id="metaPolicyOriginWhenCrossOriginInfo">
	 			Peer internal links : referrer information should contain full URLs.<br/>
	 			External links : referrer information should be stripped from any private data and contain only this peer host name.
            </div>
		</div>
		<div class="form-group">
			<div class="col-sm-4">
				<div class="radio">
					<label>
						<input name="metaPolicy" id="metaPolicyNoReferrerWhenDowngrade" 
							type="radio" #(metaPolicyNoReferrerWhenDowngradeChecked)#::checked#(/metaPolicyNoReferrerWhenDowngradeChecked)# 
							value="no-referrer-when-downgrade" aria-describedby="metaPolicyNoReferrerWhenDowngradeInfo"/>
						no-referrer-when-downgrade
					</label>
				</div>
			</div>
			<div class="col-sm-8" id="metaPolicyNoReferrerWhenDowngradeInfo">
	 			Referrer information should contain full URLs, except when a link downgrades from a TLS secured connection (https) on this peer to an unsecured target (http).
            </div>
		</div>
		<div class="form-group">
			<div class="col-sm-4">
				<div class="radio">
					<label>
						<input name="metaPolicy" id="metaPolicyEmpty" 
							type="radio" #(metaPolicyEmptyChecked)#::checked#(/metaPolicyEmptyChecked)# 
							value="" aria-describedby="metaPolicyEmptyInfo"/>
						empty value
					</label>
				</div>
			</div>
			<div class="col-sm-8" id="metaPolicyEmptyInfo">
	 			Default browser behavior : it should corresponds to "no-referrer-when-downgrade".
            </div>
		</div>
		<div class="form-group">
			<div class="col-sm-4">
				<div class="radio">
					<label>
						<input name="metaPolicy" id="metaPolicyUnsafeUrl" 
							type="radio" #(metaPolicyUnsafeUrlChecked)#::checked#(/metaPolicyUnsafeUrlChecked)# 
							value="unsafe-url" aria-describedby="metaPolicyUnsafeUrlInfo"/>
						unsafe-url
					</label>
				</div>
			</div>
			<div class="col-sm-8" id="metaPolicyUnsafeUrlInfo">
	 			Unsafe setting : referrer information should always contain full URLs. 
            </div>
		</div>
		#(metaPolicyCustom)#::
		<div class="form-group">
			<div class="col-sm-4">
				<div class="radio">
					<label>
						<input name="metaPolicy" id="metaPolicyCustom" 
							type="radio" #(checked)#::checked#(/checked)# 
							value="#[value]#" aria-describedby="metaPolicyCustomInfo"/>
						#[value]#
					</label>
				</div>
			</div>
			<div class="col-sm-7 alert alert-warning" id="metaPolicyCustomInfo" role="alert">
	 			Custom setting : probably manually edited, be sure this value is the desired one.
            </div>
		</div>
		#(/metaPolicyCustom)#
	</fieldset>
	
	<fieldset>
		<legend>Search results links</legend>
		
		<div class="form-group">
			<div class="col-sm-4">
				<div class="checkbox">
					<label>
						<input name="searchResultNoReferrer" id="searchResultNoReferrer" 
							type="checkbox" #(searchResultNoReferrerChecked)#::checked#(/searchResultNoReferrerChecked)# 
							aria-describedby="searchResultNoReferrerInfo"/>
						Add the "noreferrer" link type to search results links
					</label>
				</div>
			</div>
			<div class="col-sm-8" id="searchResultNoReferrerInfo">
	 			<p>When checked, this overrides the global referrer policy and adds the standard "noreferrer" 
	 			<a href="https://www.w3.org/TR/html/links.html#allowed-keywords-and-their-meanings" title="Link types section at W3C HTML specification">link type</a> to search results links, 
	 			thus instructing the browser that it should not send any referrer information at all when visiting them.</p> 
	 			<p>Be careful with this : some websites might reject requests with no referrer.</p>
	 			<p>It is a standard HTML5 attribute value, 
	 			supported by much more browsers than the meta tag : if you want a higher level of privacy but use an old or incompatible browser, 
	 			this can be a valuable option.</p>
            </div>
		</div>
	</fieldset>
	
    <div class="col-sm-6">
    	<input type="submit" class="btn btn-primary" name="referrerPolicySettings" value="Submit" aria-describedby="submitInfo"/>
		<em id="submitInfo">Changes will take effect immediately.</em>
    </div>
</form>