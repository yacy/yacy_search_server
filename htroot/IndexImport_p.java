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
//
//Using this software in any meaning (reading, learning, copying, compiling,
//running) means that you agree that the Author(s) is (are) not responsible
//for cost, loss of data or any harm that may be caused directly or indirectly
//by usage of this softare or this documentation. The usage of this software
//is on your own risk. The installation and usage (starting/running) of this
//software may allow other people or application to access your computer and
//any attached devices and is highly dependent on the configuration of the
//software which must be done by the user of the software; the author(s) is
//(are) also not responsible for proper configuration and usage of the
//software, even if provoked by documentation provided together with
//the software.
//
//Any changes to this file according to the GPL as documented in the file
//gpl.txt aside this file in the shipment you received can be done to the
//lines that follows this copyright notice here, but changes must not be
//done inside the copyright notive above. A re-distribution must contain
//the intact and unchanged copyright notice.
//Contributions and changes to the program code must be marked as such.

//You must compile this file with
//javac -classpath .:../Classes IndexControl_p.java
//if the shell's current path is HTROOT

import java.io.File;
import java.io.PrintStream;
import java.util.Date;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.dbImport.dbImporter;
import de.anomic.server.serverByteBuffer;
import de.anomic.server.serverDate;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public final class IndexImport_p {
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        // return variable that accumulates replacements
        plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
        serverObjects prop = new serverObjects();
        
        int activeCount = 0;
        
        if (post != null) {
            if (post.containsKey("startIndexDbImport")) {
                try {
                    // getting the import path
                    String importPlasmaPath = (String) post.get("importPlasmaPath");
                    String importIndexPrimaryPath = (String) post.get("importIndexPrimaryPath");
                    String importIndexSecondaryPath = (String) post.get("importIndexSecondaryPath");
                    String importType = (String) post.get("importType");
                    String cacheSizeStr = (String) post.get("cacheSize");
                    int cacheSize = 8*1024*1024;
                    try {
                        cacheSize = Integer.valueOf(cacheSizeStr).intValue();
                    } catch (NumberFormatException e) {}
                    boolean startImport = true;
                    
//                    // check if there is an already running thread with the same import path
//                    Thread[] importThreads = new Thread[plasmaDbImporter.runningJobs.activeCount()*2];
//                    activeCount = plasmaDbImporter.runningJobs.enumerate(importThreads);
//                    
//                    for (int i=0; i < activeCount; i++) {
//                        plasmaDbImporter currThread = (plasmaDbImporter) importThreads[i];
//                        if (currThread.getJobName().equals(new File(importPath))) {
//                            prop.put("error",2);
//                            startImport = false;
//                        }
//                    }                    
//                    
                    
                    if (startImport) {
                        dbImporter importerThread = switchboard.dbImportManager.getNewImporter(importType);
                        if (importerThread != null) {
                            importerThread.init(new File(importPlasmaPath), new File(importIndexPrimaryPath), new File(importIndexSecondaryPath), cacheSize, 100);
                            importerThread.startIt();                            
                        }
                        prop.put("LOCATION","");
                        return prop;
                    } 
                } catch (Exception e) { 
                    serverByteBuffer errorMsg = new serverByteBuffer(100);
                    PrintStream errorOut = new PrintStream(errorMsg);
                    e.printStackTrace(errorOut);
                    
                    prop.put("error",3);
                    prop.put("error_error_msg",e.toString());
                    prop.put("error_error_stackTrace",errorMsg.toString().replaceAll("\n","<br>"));
                    
                    errorOut.close();
                }
            } else if (post.containsKey("clearFinishedJobList")) {
                switchboard.dbImportManager.finishedJobs.clear();
                prop.put("LOCATION","");
                return prop;
            } else if (
                    (post.containsKey("stopIndexDbImport")) ||
                    (post.containsKey("pauseIndexDbImport")) ||
                    (post.containsKey("continueIndexDbImport"))
            ) {
                // getting the job nr of the thread
                String jobID = (String) post.get("jobNr");
                dbImporter importer = switchboard.dbImportManager.getImporterByID(Integer.valueOf(jobID).intValue());
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
        
        prop.put("wcount", Integer.toString(switchboard.wordIndex.size()));
        prop.put("ucount", Integer.toString(switchboard.wordIndex.loadedURL.size()));
        
        /*
         * Loop over all currently running jobs
         */
        dbImporter[] importThreads = switchboard.dbImportManager.getRunningImporter();
        activeCount = importThreads.length;
        
        for (int i=0; i < activeCount; i++) {
            dbImporter currThread = importThreads[i];

            // get import type
            prop.put("running.jobs_" + i + "_type",            currThread.getJobType());
            
            // root path of the source db
            String fullName = currThread.getJobName().toString();
            String shortName = (fullName.length()>30)?fullName.substring(0,12) + "..." + fullName.substring(fullName.length()-22,fullName.length()):fullName;
            prop.put("running.jobs_" + i + "_fullName",fullName);
            prop.put("running.jobs_" + i + "_shortName",shortName);
            
            // specifies if the importer is still running
            prop.put("running.jobs_" + i + "_stopped",         currThread.isStopped() ? 0:1);
            
            // specifies if the importer was paused
            prop.put("running.jobs_" + i + "_paused",          currThread.isPaused() ? 1:0);
            
            // setting the status
            prop.put("running.jobs_" + i + "_runningStatus",          currThread.isPaused() ? 2 : currThread.isStopped() ? 0 : 1);
            
            // other information
            prop.put("running.jobs_" + i + "_percent",         Integer.toString(currThread.getProcessingStatusPercent()));
            prop.put("running.jobs_" + i + "_elapsed",         serverDate.intervalToString(currThread.getElapsedTime()));
            prop.put("running.jobs_" + i + "_estimated",       serverDate.intervalToString(currThread.getEstimatedTime()));
            prop.put("running.jobs_" + i + "_status",          currThread.getStatus().replaceAll("\n", "<br>"));
            
            // job number of the importer thread
            prop.put("running.jobs_" + i + "_job_nr", Integer.toString(currThread.getJobID()));
        }
        prop.put("running.jobs",activeCount);
        
        /*
         * Loop over all finished jobs 
         */
        dbImporter[] finishedJobs = switchboard.dbImportManager.getFinishedImporter();
        for (int i=0; i<finishedJobs.length; i++) {
            dbImporter currThread = finishedJobs[i];
            String error = currThread.getError();
            String fullName = currThread.getJobName().toString();
            String shortName = (fullName.length()>30)?fullName.substring(0,12) + "..." + fullName.substring(fullName.length()-22,fullName.length()):fullName;            
            prop.put("finished.jobs_" + i + "_type", currThread.getJobType());
            prop.put("finished.jobs_" + i + "_fullName", fullName);
            prop.put("finished.jobs_" + i + "_shortName", shortName);
            if (error != null) { 
                prop.put("finished.jobs_" + i + "_runningStatus", 1);
                prop.put("finished.jobs_" + i + "_runningStatus_errorMsg", error.replaceAll("\n", "<br>"));
            } else {
                prop.put("finished.jobs_" + i + "_runningStatus", 0);
            }
            prop.put("finished.jobs_" + i + "_percent", Integer.toString(currThread.getProcessingStatusPercent()));
            prop.put("finished.jobs_" + i + "_elapsed", serverDate.intervalToString(currThread.getElapsedTime()));         
            prop.put("finished.jobs_" + i + "_status", currThread.getStatus().replaceAll("\n", "<br>"));
        }
        prop.put("finished.jobs",finishedJobs.length);
        
        prop.put("date",(new Date()).toString());
        return prop;
    }
    
    
}
