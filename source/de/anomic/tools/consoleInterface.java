//consoleInterface.java
//-----------------------
//part of YaCy
//(C) by Detlef Reichl; detlef!reichl()gmx!org
//Pforzheim, Germany, 2008
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

package de.anomic.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

import de.anomic.server.logging.serverLog;

public class consoleInterface extends Thread
{
    private final InputStream stream;
    private final List<String> output = new ArrayList<String>();
    private final Semaphore dataIsRead = new Semaphore(1);
    /**
     * FIXME just for debugging 
     */
    private final String name;
    private serverLog log;
    

    public consoleInterface (final InputStream stream, String name, serverLog log)
    {
        this.log = log;
        this.stream = stream;
        this.name = name;
        // block reading {@see getOutput()}
        try {
            dataIsRead.acquire();
        } catch (InterruptedException e) {
            // this should never happen because this is a constructor
            e.printStackTrace();
        }
    }

    public void run() {
        // a second run adds data! a output.clear() maybe needed
        try {
            final InputStreamReader input = new InputStreamReader(stream);
            final BufferedReader buffer = new BufferedReader(input);
            String line = null;
            int tries = 0;
            while (tries < 1000) {
                tries++;
                try {
                    // may block!
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    // just stop sleeping
                }
                if (buffer.ready())
                    break;
            }
            log.logInfo("logpoint 3 "+ name +" needed " + tries + " tries");
            while((line = buffer.readLine()) != null) {
                    output.add(line);
            }
            dataIsRead.release();
        } catch(final IOException ix) { log.logWarning("logpoint 6 " +  ix.getMessage());}
    }
    
    /**
     * waits until the stream is read and returns all data
     * 
     * @return lines of text in stream
     */
    public List<String> getOutput(){
        // wait that data is ready
        try {
            log.logInfo("logpoint 4 waiting for data of '"+ name +"'");
            final long start = System.currentTimeMillis();
            dataIsRead.acquire();
            log.logInfo("logpoint 5 data ready for '"+ name +"' after "+ (System.currentTimeMillis() - start) +" ms");
        } catch (InterruptedException e) {
            // after interrupt just return what is available (maybe nothing)
        }
        // is just for checking availability, so release it immediatly
        dataIsRead.release();
        return output;
    }
}