//IndexTransfer_p.java 
//-----------------------
//part of the AnomicHTTPD caching proxy
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//
//This file is contributed by Martin Thelian
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

//You must compile this file with
//javac -classpath .:../Classes IndexControl_p.java
//if the shell's current path is HTROOT

import java.io.PrintStream;
import java.util.Date;

import net.yacy.kelondro.util.ByteBuffer;
import net.yacy.kelondro.util.DateFormatter;

import de.anomic.crawler.Importer;
import de.anomic.crawler.NoticeURLImporter;
import de.anomic.http.metadata.RequestHeader;
import de.anomic.search.Segment;
import de.anomic.search.Segments;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public final class IndexImport_p {
    
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        
        int activeCount = 0;
    
        // get segment
        Segment indexSegment = null;
        if (post != null && post.containsKey("segment")) {
            String segmentName = post.get("segment");
            if (sb.indexSegments.segmentExist(segmentName)) {
                indexSegment = sb.indexSegments.segment(segmentName);
            }
        } else {
            // take default segment
            indexSegment = sb.indexSegments.segment(Segments.Process.PUBLIC);
        }
        
        if (post != null) {
            if (post.containsKey("startIndexDbImport")) {
                try {
                    final boolean startImport = true;                    
                    if (startImport) {
                        final Importer importerThread = new NoticeURLImporter(
                                sb.queuesRoot,
                                sb.crawlQueues,
                                sb.crawler.profilesActiveCrawls,
                                sb.dbImportManager);

                        if (importerThread != null) {
                            importerThread.setJobID(sb.dbImportManager.generateUniqueJobID());
                            importerThread.startIt();                            
                        }
                        prop.put("LOCATION","");
                        return prop;
                    } 
                } catch (final Exception e) { 
                    final ByteBuffer errorMsg = new ByteBuffer(100);
                    final PrintStream errorOut = new PrintStream(errorMsg);
                    e.printStackTrace(errorOut);
                    
                    prop.put("error", "3");
                    prop.putHTML("error_error_msg",e.toString());
                    prop.putHTML("error_error_stackTrace",errorMsg.toString().replaceAll("\n","<br>"));
                    
                    errorOut.close();
                }
            } else if (post.containsKey("clearFinishedJobList")) {
                sb.dbImportManager.finishedJobs.clear();
                prop.put("LOCATION", "");
                return prop;
            } else if (
                    (post.containsKey("stopIndexDbImport")) ||
                    (post.containsKey("pauseIndexDbImport")) ||
                    (post.containsKey("continueIndexDbImport"))
            ) {
                // get the job nr of the thread
                final String jobID = post.get("jobNr");
                final Importer importer = sb.dbImportManager.getImporterByID(Integer.valueOf(jobID).intValue());
                if (importer != null) {
                    if (post.containsKey("stopIndexDbImport")) {
                        try {
                            importer.stopIt();
                        } catch (final InterruptedException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }                        
                    } else if (post.containsKey("pauseIndexDbImport")) {
                        importer.pauseIt();
                    } else if (post.containsKey("continueIndexDbImport")) {
                        importer.continueIt();
                    }
                }                    
                prop.put("LOCATION","");
                return prop;
            }
        }
        
        prop.putNum("wcount", indexSegment.termIndex().sizesMax());
        prop.putNum("ucount", indexSegment.urlMetadata().size());
        
        /*
         * Loop over all currently running jobs
         */
        final Importer[] importThreads = sb.dbImportManager.getRunningImporter();
        activeCount = importThreads.length;
        
        for (int i=0; i < activeCount; i++) {
            final Importer currThread = importThreads[i];

            // get import type
            prop.put("running.jobs_" + i + "_type", currThread.getJobType());
            
            // root path of the source db
            final String fullName = currThread.getJobName();
            final String shortName = (fullName.length()>30)?fullName.substring(0,12) + "..." + fullName.substring(fullName.length()-22,fullName.length()):fullName;
            prop.put("running.jobs_" + i + "_fullName",fullName);
            prop.put("running.jobs_" + i + "_shortName",shortName);
            
            // specifies if the importer is still running
            prop.put("running.jobs_" + i + "_stopped", currThread.isStopped() ? "0" : "1");
            
            // specifies if the importer was paused
            prop.put("running.jobs_" + i + "_paused", currThread.isPaused() ? "1" : "0");
            
            // setting the status
            prop.put("running.jobs_" + i + "_runningStatus", currThread.isPaused() ? "2" : currThread.isStopped() ? "0" : "1");
            
            // other information
            prop.putNum("running.jobs_" + i + "_percent", currThread.getProcessingStatusPercent());
            prop.put("running.jobs_" + i + "_elapsed", DateFormatter.formatInterval(currThread.getElapsedTime()));
            prop.put("running.jobs_" + i + "_estimated", DateFormatter.formatInterval(currThread.getEstimatedTime()));
            prop.putHTML("running.jobs_" + i + "_status", currThread.getStatus().replaceAll("\n", "<br>"));
            
            // job number of the importer thread
            prop.put("running.jobs_" + i + "_job_nr", currThread.getJobID());
        }
        prop.put("running.jobs", activeCount);
        
        /*
         * Loop over all finished jobs 
         */
        final Importer[] finishedJobs = sb.dbImportManager.getFinishedImporter();
        for (int i=0; i<finishedJobs.length; i++) {
            final Importer currThread = finishedJobs[i];
            final String error = currThread.getError();
            final String fullName = currThread.getJobName();
            final String shortName = (fullName.length()>30)?fullName.substring(0,12) + "..." + fullName.substring(fullName.length()-22,fullName.length()):fullName;            
            prop.put("finished.jobs_" + i + "_type", currThread.getJobType());
            prop.put("finished.jobs_" + i + "_fullName", fullName);
            prop.put("finished.jobs_" + i + "_shortName", shortName);
            if (error != null) { 
                prop.put("finished.jobs_" + i + "_runningStatus", "1");
                prop.putHTML("finished.jobs_" + i + "_runningStatus_errorMsg", error.replaceAll("\n", "<br>"));
            } else {
                prop.put("finished.jobs_" + i + "_runningStatus", "0");
            }
            prop.putNum("finished.jobs_" + i + "_percent", currThread.getProcessingStatusPercent());
            prop.put("finished.jobs_" + i + "_elapsed", DateFormatter.formatInterval(currThread.getElapsedTime()));
            prop.putHTML("finished.jobs_" + i + "_status", currThread.getStatus().replaceAll("\n", "<br>"));
        }
        prop.put("finished.jobs",finishedJobs.length);
        
        prop.put("date",(new Date()).toString());
        return prop;
    }
}
