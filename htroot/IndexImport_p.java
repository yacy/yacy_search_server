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
import java.util.Date;
import java.util.Vector;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaDbImporter;
import de.anomic.plasma.plasmaSwitchboard;
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
                    String importPath = (String) post.get("importPath");
                    boolean startImport = true;
                    
                    // check if there is an already running thread with the same import path
                    Thread[] importThreads = new Thread[plasmaDbImporter.runningJobs.activeCount()*2];
                    activeCount = plasmaDbImporter.runningJobs.enumerate(importThreads);
                    
                    for (int i=0; i < activeCount; i++) {
                        plasmaDbImporter currThread = (plasmaDbImporter) importThreads[i];
                        if (currThread.getImportRoot().equals(new File(importPath))) {
                            prop.put("error",2);
                            startImport = false;
                        }
                    }                    
                    
                    if (startImport) {
                        plasmaDbImporter newImporter = new plasmaDbImporter(switchboard.wordIndex,switchboard.urlPool.loadedURL,importPath);
                        newImporter.start();
                        
                        prop.put("LOCATION","");
                        return prop;
                    }
                } catch (Exception e) {
                    prop.put("error",1);
                    prop.put("error_error_msg",e.toString());
                }
            } else if (post.containsKey("clearFinishedJobList")) {
                plasmaDbImporter.finishedJobs.clear();
                prop.put("LOCATION","");
                return prop;
            } else if (
                    (post.containsKey("stopIndexDbImport")) ||
                    (post.containsKey("pauseIndexDbImport")) ||
                    (post.containsKey("continueIndexDbImport"))
            ) {
                // getting the job nr of the thread
                String jobNr = (String) post.get("jobNr");
                
                Thread[] importThreads = new Thread[plasmaDbImporter.runningJobs.activeCount()*2];
                activeCount = plasmaDbImporter.runningJobs.enumerate(importThreads);
                
                for (int i=0; i < activeCount; i++) {
                    plasmaDbImporter currThread = (plasmaDbImporter) importThreads[i];
                    if (currThread.getJobNr() == Integer.valueOf(jobNr).intValue()) {
                        if (post.containsKey("stopIndexDbImport")) {
                            currThread.stoppIt();
                            try { currThread.join(); } catch (InterruptedException e) {e.printStackTrace();}                            
                        } else if (post.containsKey("pauseIndexDbImport")) {
                            currThread.pauseIt();
                        } else if (post.containsKey("continueIndexDbImport")) {
                            currThread.continueIt();
                        }
                        break;
                    }                    
                }
                prop.put("LOCATION","");
                return prop;
            }
        }
        
        prop.put("wcount", Integer.toString(switchboard.wordIndex.size()));
        prop.put("ucount", Integer.toString(switchboard.urlPool.loadedURL.size()));
        
        /*
         * Loop over all currently running jobs
         */
        Thread[] importThreads = new Thread[plasmaDbImporter.runningJobs.activeCount()*2];
        activeCount = plasmaDbImporter.runningJobs.enumerate(importThreads);
        
        for (int i=0; i < activeCount; i++) {
            plasmaDbImporter currThread = (plasmaDbImporter) importThreads[i];

            // root path of the source db
            prop.put("running.jobs_" + i + "_path",            currThread.getImportRoot().toString());
            
            // specifies if the importer is still running
            prop.put("running.jobs_" + i + "_stopped",         currThread.isAlive() ? 1:0);
            
            // specifies if the importer was paused
            prop.put("running.jobs_" + i + "_paused",          currThread.isPaused() ? 1:0);
            
            // setting the status
            prop.put("running.jobs_" + i + "_status",          currThread.isPaused() ? 2 : currThread.isAlive() ? 1 : 0);
            
            // other information
            prop.put("running.jobs_" + i + "_percent",         Integer.toString(currThread.getProcessingStatus()));
            prop.put("running.jobs_" + i + "_elapsed",         serverDate.intervalToString(currThread.getElapsedTime()));
            prop.put("running.jobs_" + i + "_estimated",       serverDate.intervalToString(currThread.getEstimatedTime()));
            prop.put("running.jobs_" + i + "_wordHash",        currThread.getCurrentWordhash());
            prop.put("running.jobs_" + i + "_url_num",         Long.toString(currThread.getUrlCounter()));
            prop.put("running.jobs_" + i + "_word_entity_num", Long.toString(currThread.getWordEntityCounter()));
            prop.put("running.jobs_" + i + "_word_entry_num",  Long.toString(currThread.getWordEntryCounter()));
            
            // job number of the importer thread
            prop.put("running.jobs_" + i + "_job_nr", Integer.toString(currThread.getJobNr()));
        }
        prop.put("running.jobs",activeCount);
        
        /*
         * Loop over all finished jobs 
         */
        Vector finishedJobs = (Vector) plasmaDbImporter.finishedJobs.clone();
        for (int i=0; i<finishedJobs.size(); i++) {
            plasmaDbImporter currThread = (plasmaDbImporter) finishedJobs.get(i);
            String error = currThread.getError();
            prop.put("finished.jobs_" + i + "_path", currThread.getImportRoot().toString());
            if (error != null) {
                prop.put("finished.jobs_" + i + "_status", 2);
                prop.put("finished.jobs_" + i + "_status_errorMsg", error);
            } else {
                prop.put("finished.jobs_" + i + "_status", 0);
            }
            prop.put("finished.jobs_" + i + "_percent", Integer.toString(currThread.getProcessingStatus()));
            prop.put("finished.jobs_" + i + "_elapsed", serverDate.intervalToString(currThread.getElapsedTime()));         
            prop.put("finished.jobs_" + i + "_wordHash", currThread.getCurrentWordhash());
            prop.put("finished.jobs_" + i + "_url_num", Long.toString(currThread.getUrlCounter()));
            prop.put("finished.jobs_" + i + "_word_entity_num", Long.toString(currThread.getWordEntityCounter()));
            prop.put("finished.jobs_" + i + "_word_entry_num", Long.toString(currThread.getWordEntryCounter()));           
        }
        prop.put("finished.jobs",finishedJobs.size());
        
        prop.put("date",(new Date()).toString());
        return prop;
    }
    
    
}
