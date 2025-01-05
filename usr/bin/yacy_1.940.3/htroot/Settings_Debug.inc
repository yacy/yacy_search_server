<h3>Debug/Analysis Settings</h3>

<div class="alert alert-warning" role="alert">
	Be careful with these advanced settings, they can deeply affect the search process! You probably don't need to modify them for normal use.
</div>

<form action="SettingsAck_p.html" method="post" enctype="multipart/form-data" class="form-horizontal">
	<input type="hidden" name="transactionToken" value="#[transactionToken]#"/>
	<fieldset>
		<legend>Solr communication</legend>
		
		<div class="form-group">
			<div class="col-sm-4">
				<div class="checkbox">
					<label>
						<input name="solrBinaryResponse" id="solrBinaryResponse" 
							type="checkbox" #(solrBinaryResponseChecked)#::checked#(/solrBinaryResponseChecked)# 
							aria-describedby="solrBinaryResponseInfo"/>
						Enable remote Solr binary responses
					</label>
				</div>
			</div>
			<div class="col-sm-8" id="solrBinaryResponseInfo">
	 			When checked (default), responses from remote Solr index instances are transfered using an efficient binary data format.
	 			When unchecked, responses are transferred as <abbr title="Extensible Markup Language">XML</abbr>, 
	 			which can be captured and parsed by any external XML aware tool for debug/analysis.
            </div>
		</div>
	</fieldset>
	
	<fieldset>
		<legend>Search data sources</legend>
		<p>By default all data sources are enabled to obtain search results, 
			but you can here disable one or more ones to check the behavior of the process.</p>

		<div class="form-group">
			<div class="col-sm-12">
				<div class="checkbox">
					<label>
						<input name="searchLocalDHT" id="searchLocalDHT"
							type="checkbox" #(searchLocalDHTChecked)#::checked#(/searchLocalDHTChecked)# />
						Local <abbr title="Distributed Hash Table">DHT</abbr>/<abbr title="Reverse Word Index">RWI</abbr>
					</label>
				</div>
			</div>
		</div>
		
		<div class="form-group">
			<div class="col-sm-12">
				<div class="checkbox">
					<label>
						<input name="searchLocalSolr" id="searchLocalSolr"
							type="checkbox" #(searchLocalSolrChecked)#::checked#(/searchLocalSolrChecked)# />
						Local Solr index
					</label>
				</div>
			</div>
		</div>
		
		<div class="form-group">
			<div class="col-sm-12">
				<div class="checkbox">
					<label>
						<input name="searchRemoteDHT" id="searchRemoteDHT"
							type="checkbox" #(searchRemoteDHTChecked)#::checked#(/searchRemoteDHTChecked)# />
						Remote <abbr title="Distributed Hash Table">DHT</abbr>/<abbr title="Reverse Word Index">RWI</abbr>
					</label>
				</div>
			</div>
		</div>
		
		<div class="form-group">
			<div class="col-sm-12">
				<div class="checkbox">
					<label>
						<input name="searchRemoteSolr" id="searchRemoteSolr"
							type="checkbox" #(searchRemoteSolrChecked)#::checked#(/searchRemoteSolrChecked)# />
						Remote Solr indexes
					</label>
				</div>
			</div>
		</div>
	</fieldset>
	
	<fieldset>
		<legend>Search testing tweaks</legend>
		
		<div class="form-group">
			<div class="col-sm-4">
				<div class="checkbox">
					<label>
						<input name="searchTestLocalDHT" id="searchTestLocalDHT" 
							type="checkbox" #(searchTestLocalDHTChecked)#::checked#(/searchTestLocalDHTChecked)#
							aria-describedby="searchTestLocalDHTInfo"/>
						Override <abbr title="Distributed Hash Table">DHT</abbr> peers selection by local only
					</label>
				</div>
			</div>
			<div class="col-sm-8" id="searchTestLocalDHTInfo">
	 			When checked, the remote <abbr title="Distributed Hash Table">DHT</abbr> peers selection is overriden and only the local peer is selected to provide remote DHT search results.
            </div>
		</div>
		
		<div class="form-group">
			<div class="col-sm-4">
				<div class="checkbox">
					<label>
						<input name="searchTestLocalSolr" id="searchTestLocalSolr" 
							type="checkbox" #(searchTestLocalSolrChecked)#::checked#(/searchTestLocalSolrChecked)#
							aria-describedby="searchTestLocalSolrInfo"/>
						Override Solr peers selection by local only
					</label>
				</div>
			</div>
			<div class="col-sm-8" id="searchTestLocalSolrInfo">
	 			When checked, the remote Solr peers selection is overriden and only this peer is selected to provide remote Solr search results.
            </div>
		</div>
  
	</fieldset>
	
	<fieldset>
		<legend>Ranking information</legend>
		
		<div class="form-group">
			<div class="col-sm-4">
				<div class="checkbox">
					<label>
						<input name="searchShowRanking" id="searchShowRanking" 
							type="checkbox" #(searchShowRankingChecked)#::checked#(/searchShowRankingChecked)#
							aria-describedby="searchShowRankingInfo"/>
						Show search results scores
					</label>
				</div>
			</div>
			<div class="col-sm-8" id="searchShowRankingInfo">
	 			When checked, the raw ranking score value is displayed for each text search result in the HTML results page.
            </div>
		</div>
	</fieldset>
	
	<fieldset>
		<legend>Text snippets statistics</legend>
		
		<div class="form-group">
			<div class="col-sm-4">
				<div class="checkbox">
					<label>
						<input name="debug.snippets.statistics.enabled" id="snippetsStatsEnabled" 
							type="checkbox" #(debug.snippets.statistics.enabled)#::checked#(/debug.snippets.statistics.enabled)#
							aria-describedby="snippetStatisticsInfo"/>
						Enable text snippets statistics
					</label>
				</div>
			</div>
			<div class="col-sm-8" id="snippetStatisticsInfo">
	 			When checked, statistics are collected on text snippets generation for search results. The are resumed in the <a href="ConfigPortal_p.html">Portal Configuration</a> page.
            </div>
		</div>
	</fieldset>
	
    <div class="col-sm-6">
    	<input type="submit" class="btn btn-primary" name="debugAnalysisSettings" value="Submit" aria-describedby="submitInfo"/>
		<em id="submitInfo">Changes will take effect immediately.</em>
    </div>
</form>