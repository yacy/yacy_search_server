// TextSnippetStatistics.java
// ---------------------------
// Copyright 2018 by luccioman; https://github.com/luccioman
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// LICENSE
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.search.snippet;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongBinaryOperator;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.snippet.TextSnippet.ResultClass;

/**
 * Handle statistics on TextSnippet processing.
 */
public class TextSnippetStatistics {
	
	/** Logs handler */
	private static final ConcurrentLog logger = new ConcurrentLog(TextSnippetStatistics.class.getName());

	/** Total number of TextSnippet instances created since last JVM start */
	private AtomicLong totalSnippets = new AtomicLong(0);

	/**
	 * Total number of TextSnippet instances with resultStatus of type fail created
	 * since last JVM start
	 */
	private AtomicLong totalFailures = new AtomicLong(0);

	/**
	 * Total number of TextSnippet instances with resultStatus of type
	 * ResultClass.SOURCE_DOLR created since last JVM start
	 */
	private AtomicLong totalFromSolr = new AtomicLong(0);

	/**
	 * Total number of TextSnippet instances with resultStatus of type
	 * ResultClass.SOURCE_CACHE created since last JVM start
	 */
	private AtomicLong totalFromCache = new AtomicLong(0);

	/**
	 * Total number of TextSnippet instances with resultStatus of type
	 * ResultClass.SOURCE_WEB created since last JVM start
	 */
	private AtomicLong totalFromWeb = new AtomicLong(0);

	/**
	 * Total number of TextSnippet instances with resultStatus of type
	 * ResultClass.SOURCE_METADATA created since last JVM start
	 */
	private AtomicLong totalFromMetadata = new AtomicLong(0);

	/**
	 * Total time (in milliseconds) spent in TextSnippet initialization since last
	 * JVM start
	 */
	private AtomicLong totalInitTime = new AtomicLong(0);

	/**
	 * Maximum time (in milliseconds) spent in a single TextSnippet initialization
	 * since last JVM start
	 */
	private AtomicLong maxInitTime = new AtomicLong(0);

	/**
	 * Statistics are effectively computed and stored only when this boolean is true
	 */
	private AtomicBoolean enabled = new AtomicBoolean(SwitchboardConstants.DEBUG_SNIPPETS_STATISTICS_ENABLED_DEFAULT);

	/**
	 * @return the total number of TextSnippet instances created since last JVM
	 *         start
	 */
	public long getTotalSnippets() {
		return this.totalSnippets.get();
	}

	/**
	 * @return the total number of TextSnippet instances with resultStatus of type
	 *         fail created since last JVM start
	 */
	public long getTotalFailures() {
		return this.totalFailures.get();
	}

	/**
	 * @return the total number of TextSnippet instances with resultStatus of type
	 *         ResultClass.SOURCE_SOLR created since last JVM start
	 */
	public long getTotalFromSolr() {
		return this.totalFromSolr.get();
	}

	/**
	 * @return the total number of TextSnippet instances with resultStatus of type
	 *         ResultClass.SOURCE_CACHE created since last JVM start
	 */
	public long getTotalFromCache() {
		return this.totalFromCache.get();
	}

	/**
	 * @return the total number of TextSnippet instances with resultStatus of type
	 *         ResultClass.SOURCE_METADATA created since last JVM start
	 */
	public long getTotalFromMetadata() {
		return this.totalFromMetadata.get();
	}

	/**
	 * @return the total number of TextSnippet instances with resultStatus of type
	 *         ResultClass.SOURCE_WEB created since last JVM start
	 */
	public long getTotalFromWeb() {
		return this.totalFromWeb.get();
	}

	/**
	 * Update statistics after a new TextSnippet instance has been initialized. Do
	 * nothing when text snippet statistics are not enabled.
	 * 
	 * @param initTime
	 *            the time in milliseconds used for the snippet initialization
	 * @param resultStatus
	 *            the snippet result status.
	 */
	public void addTextSnippetStatistics(final DigestURL url, final long initTime, final ResultClass resultStatus) {
		if (this.enabled.get() && resultStatus != null) {
			this.totalSnippets.incrementAndGet();
			this.totalInitTime.addAndGet(initTime);
			if(initTime == this.maxInitTime.accumulateAndGet(initTime, new LongBinaryOperator() {
				
				@Override
				public long applyAsLong(long currentValue, long updateValue) {
					return currentValue < updateValue ? updateValue : currentValue;
				}
			})) {
				if(logger.isFine()) {
					logger.fine("New max snippet init time : status " + resultStatus + " in " + initTime + " ms for URL " + url);
				}
			}
			
			if (resultStatus != null) {
				switch (resultStatus) {
				case SOURCE_SOLR:
					this.totalFromSolr.incrementAndGet();
					break;
				case SOURCE_CACHE:
					this.totalFromCache.incrementAndGet();
					break;
				case SOURCE_METADATA:
					this.totalFromMetadata.incrementAndGet();
					break;
				case SOURCE_WEB:
					this.totalFromWeb.incrementAndGet();
					break;
				default:
					if (resultStatus.fail()) {
						this.totalFailures.incrementAndGet();
					}
					break;
				}
			}
		}

	}

	/**
	 * @return the total time (in milliseconds) spent in TextSnippet initialization
	 *         since last JVM start
	 */
	public long getTotalInitTime() {
		return this.totalInitTime.get();
	}

	/**
	 * @return the maximum time (in milliseconds) spent in a single TextSnippet
	 *         initialization since last JVM start
	 */
	public long getMaxInitTime() {
		return this.maxInitTime.get();
	}

	/**
	 * @return true when statistics are effectively computed and stored
	 */
	public boolean isEnabled() {
		return this.enabled.get();
	}

	/**
	 * @param newValue
	 *            set to true to effectively compute and store statistics
	 */
	public void setEnabled(final boolean newValue) {
		this.enabled.set(newValue);
	}

}
