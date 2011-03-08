//ConsoleInterface.java
//-----------------------
//part of YaCy
//(C) by Detlef Reichl; detlef!reichl()gmx!org
//Pforzheim, Germany, 2008
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

package net.yacy.kelondro.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

import net.yacy.kelondro.logging.Log;


public class ConsoleInterface extends Thread {
    private final InputStream stream;
    private final List<String> output = new ArrayList<String>();
    private final Semaphore dataIsRead = new Semaphore(1);
    private final Log log;
    

    public ConsoleInterface(final InputStream stream, final Log log) {
        this.log = log;
        this.stream = stream;
        // block reading {@see getOutput()}
        try {
            dataIsRead.acquire();
        } catch (final InterruptedException e) {
            // this should never happen because this is a constructor
            Log.logException(e);
        }
    }

    @Override
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
                } catch (final InterruptedException e) {
                    // just stop sleeping
                }
                if (buffer.ready())
                    break;
            }
            while((line = buffer.readLine()) != null) {
                    output.add(line);
            }
            dataIsRead.release();
        } catch (final IOException ix) {
            log.logWarning("logpoint 6 " +  ix.getMessage());
        } catch (final Exception e) {
            Log.logException(e);
        }
    }
    
    /**
     * waits until the stream is read and returns all data
     * 
     * @return lines of text in stream
     */
    public List<String> getOutput() {
        // wait that data is ready
        try {
            dataIsRead.acquire();
        } catch (final InterruptedException e) {
            // after interrupt just return what is available (maybe nothing)
        }
        // is just for checking availability, so release it immediatly
        dataIsRead.release();
        return output;
    }
    
    /**
     * simple interface
     * @return console output
     * @throws IOException
     */
	public static List<String> getConsoleOutput(final List<String> processArgs, Log log) throws IOException {
	    final ProcessBuilder processBuilder = new ProcessBuilder(processArgs);
	    Process process = null;
	    ConsoleInterface inputStream = null;
	    ConsoleInterface errorStream = null;
	
	    try {
	        process = processBuilder.start();
	
	        inputStream = new ConsoleInterface(process.getInputStream(), log);
	        errorStream = new ConsoleInterface(process.getErrorStream(), log);
	
	        inputStream.start();
	        errorStream.start();
	
	        /*int retval =*/ process.waitFor();
	
	    } catch (final IOException iox) {
	        log.logWarning("logpoint 0 " + iox.getMessage());
	        throw new IOException(iox.getMessage());
	    } catch (final InterruptedException ix) {
	        log.logWarning("logpoint 1 " + ix.getMessage());
	        throw new IOException(ix.getMessage());
	    }
	    final List<String> list = inputStream.getOutput();
	    if (list.isEmpty()) {
	        final String error = errorStream.getOutput().toString();
	        log.logWarning("logpoint 2: "+ error);
	        throw new IOException("empty list: " + error);
	    }
	    return list;
	}
	
	public static String getLastLineConsoleOutput(final List<String> processArgs, Log log) throws IOException {
		List<String> lines = getConsoleOutput(processArgs, log);
        String line = "";
        for (int l = lines.size() - 1; l >= 0; l--) {
            line = lines.get(l).trim();
            if (line.length() > 0) break;
        }
        return line;
	}

}