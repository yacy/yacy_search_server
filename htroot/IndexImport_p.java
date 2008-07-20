//IndexTransfer_p.java 
//-----------------------
//part of the AnomicHTTPD caching proxy
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//
//This file is contributed by Martin Thelian
//
// $LastChangedDate: 2005-10-17 17:46:12 +0200 (Mo, 17 Okt 2005) $
// $LastChangedRevision: 947 $
// $LastChangedBy: borg-0300 $
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

import de.anomic.crawler.Importer;
import de.anomic.crawler.NoticeURLImporter;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverByteBuffer;
import de.anomic.server.serverDate;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public final class IndexImport_p {
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch<?> env) {
        // return variable that accumulates replacements
        plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
        serverObjects prop = new serverObjects();
        
        int activeCount = 0;
        
        if (post != null) {
            if (post.containsKey("startIndexDbImport")) {
                try {
                    boolean startImport = true;                    
                    if (startImport) {
                        Importer importerThread = new NoticeURLImporter(switchboard.plasmaPath, switchboard.crawlQueues, switchboard.webIndex.profilesActiveCrawls, switchboard.dbImportManager);

                        if (importerThread != null) {
                            importerThread.setJobID(switchboard.dbImportManager.generateUniqueJobID());
                            importerThread.startIt();                            
                        }
                        prop.put("LOCATION","");
                        return prop;
                    } 
                } catch (Exception e) { 
                    serverByteBuffer errorMsg = new serverByteBuffer(100);
                    PrintStream errorOut = new PrintStream(errorMsg);
                    e.printStackTrace(errorOut);
                    
                    prop.put("error", "3");
                    prop.putHTML("error_error_msg",e.toString());
                    prop.putHTML("error_error_stackTrace",errorMsg.toString().replaceAll("\n","<br>"));
                    
                    errorOut.close();
                }
            } else if (post.containsKey("clearFinishedJobList")) {
                switchboard.dbImportManager.finishedJobs.clear();
                prop.put("LOCATION", "");
                return prop;
            } else if (
                    (post.containsKey("stopIndexDbImport")) ||
                    (post.containsKey("pauseIndexDbImport")) ||
                    (post.containsKey("continueIndexDbImport"))
            ) {
                // getting the job nr of the thread
                String jobID = post.get("jobNr");
                Importer importer = switchboard.dbImportManager.getImporterByID(Integer.valueOf(jobID).intValue());
                if (importer != null) {
                    if (post.containsKey("stopIndexDbImport")) {
                        try {
                            importer.stopIt();
                        } catch (InterruptedException e) {
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
        
        prop.putNum("wcount", switchboard.webIndex.size());
        prop.putNum("ucount", switchboard.webIndex.countURL());
        
        /*
         * Loop over all currently running jobs
         */
        Importer[] importThreads = switchboard.dbImportManager.getRunningImporter();
        activeCount = importThreads.length;
        
        for (int i=0; i < activeCount; i++) {
            Importer currThread = importThreads[i];

            // get import type
            prop.put("running.jobs_" + i + "_type", currThread.getJobType());
            
            // root path of the source db
            String fullName = currThread.getJobName().toString();
            String shortName = (fullName.length()>30)?fullName.substring(0,12) + "..." + fullName.substring(fullName.length()-22,fullName.length()):fullName;
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
            prop.put("running.jobs_" + i + "_elapsed", serverDate.formatInterval(currThread.getElapsedTime()));
            prop.put("running.jobs_" + i + "_estimated", serverDate.formatInterval(currThread.getEstimatedTime()));
            prop.putHTML("running.jobs_" + i + "_status", currThread.getStatus().replaceAll("\n", "<br>"));
            
            // job number of the importer thread
            prop.put("running.jobs_" + i + "_job_nr", currThread.getJobID());
        }
        prop.put("running.jobs", activeCount);
        
        /*
         * Loop over all finished jobs 
         */
        Importer[] finishedJobs = switchboard.dbImportManager.getFinishedImporter();
        for (int i=0; i<finishedJobs.length; i++) {
            Importer currThread = finishedJobs[i];
            String error = currThread.getError();
            String fullName = currThread.getJobName().toString();
            String shortName = (fullName.length()>30)?fullName.substring(0,12) + "..." + fullName.substring(fullName.length()-22,fullName.length()):fullName;            
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
            prop.put("finished.jobs_" + i + "_elapsed", serverDate.formatInterval(currThread.getElapsedTime()));
            prop.putHTML("finished.jobs_" + i + "_status", currThread.getStatus().replaceAll("\n", "<br>"));
        }
        prop.put("finished.jobs",finishedJobs.length);
        
        prop.put("date",(new Date()).toString());
        return prop;
    }
}
