// plasmaRankingDistribution.java 
// -------------------------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// created 9.11.2005
//
// $LastChangedDate: 2005-11-04 14:41:51 +0100 (Fri, 04 Nov 2005) $
// $LastChangedRevision: 1026 $
// $LastChangedBy: borg-0300 $
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

package de.anomic.plasma;

import java.io.IOException;
import java.io.File;
import java.util.Random;

import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeedDB;
import de.anomic.yacy.yacyClient;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacyVersion;
import de.anomic.server.logging.serverLog;
import de.anomic.server.serverFileUtils;

public final class plasmaRankingDistribution {

    public static final String CR_OWN   = "GLOBAL/010_owncr";
    public static final String CR_OTHER = "GLOBAL/014_othercr/";

    public static final int METHOD_NONE           =  0;
    public static final int METHOD_ANYSENIOR      =  1;
    public static final int METHOD_ANYPRINCIPAL   =  2;
    public static final int METHOD_MIXEDSENIOR    =  9;
    public static final int METHOD_MIXEDPRINCIPAL = 10;
    public static final int METHOD_FIXEDADDRESS   = 99;
    
    private final serverLog log;
    private File sourcePath;     // where to load cr-files
    private int method;          // of peer selection
    private int percentage;      // to select any other peer
    private String address[];      // of fixed other peer
    private static Random random = new Random(System.currentTimeMillis());
    
    public plasmaRankingDistribution(serverLog log, File sourcePath, int method, int percentage, String address[]) {
        this.log        = log;
        this.sourcePath = sourcePath;
        this.method     = method;
        this.percentage = percentage;
        this.address    = address;
    }

    public void setMethod(int method, int percentage, String address[]) {
        this.method     = method;
        this.percentage = percentage;
        this.address    = address;
    }
    
    public int size() {
        if ((sourcePath.exists()) && (sourcePath.isDirectory())) return sourcePath.list().length; else return 0;
    }

    public boolean transferRanking(int count) {

        if (method == METHOD_NONE) {
            log.logFine("no ranking distribution: no transfer method given");
            return false;
        }
        if (yacyCore.seedDB == null) {
            log.logFine("no ranking distribution: seedDB == null");
            return false;
        }
        if (yacyCore.seedDB.mySeed == null) {
            log.logFine("no ranking distribution: mySeed == null");
            return false;
        }
        if (yacyCore.seedDB.mySeed.isVirgin()) {
            log.logFine("no ranking distribution: status is virgin");
            return false;
        }
        
        String[] outfiles = sourcePath.list();
        
        if (outfiles == null) {
            log.logFine("no ranking distribution: source path does not exist");
            return false;
        }
        if (outfiles.length == 0) {
            log.logFine("no ranking distribution: source path does not contain any file");
            return false;
        }
        
        if (outfiles.length > count) count = outfiles.length;
        File crfile = null;
        
        for (int i = 0; i < count; i++) {
            crfile = new File(sourcePath, outfiles[i]);
            
            if ((method == METHOD_ANYSENIOR) || (method == METHOD_ANYPRINCIPAL)) {
                transferRankingAnySeed(crfile, 5);
            }
            if (method == METHOD_FIXEDADDRESS) {
                transferRankingAddress(crfile);
            }
            if ((method == METHOD_MIXEDSENIOR) || (method == METHOD_MIXEDPRINCIPAL)) {
                if (random.nextInt(100) > percentage) {
                    if (!(transferRankingAddress(crfile))) transferRankingAnySeed(crfile, 5);
                } else {
                    if (!(transferRankingAnySeed(crfile, 5))) transferRankingAddress(crfile);
                }
            }
            
        }
        log.logFine("no ranking distribution: no target available");
        return false;
    }
    
    private boolean transferRankingAnySeed(File crfile, int trycount) {
        yacySeed target = null;
        for (int j = 0; j < trycount; j++) {
            target = yacyCore.seedDB.anySeedVersion(yacyVersion.YACY_ACCEPTS_RANKING_TRANSMISSION);
            
            if (target == null) continue;
            String targetaddress = target.getAddress();
            if (transferRankingAddress(crfile, targetaddress)) return true;
        }
        return false;
    }
    
    private boolean transferRankingAddress(File crfile) {
        // try all addresses
        for (int i = 0; i < address.length; i++) {
            if (transferRankingAddress(crfile, address[i])) return true;
        }
        return false;
    }
    
    private boolean transferRankingAddress(File crfile, String address) {
        // do the transfer
        long starttime = System.currentTimeMillis();
        String result = "unknown";
        try {
            byte[] b = serverFileUtils.read(crfile);
            result = yacyClient.transfer(address, crfile.getName(), b);
            if (result == null) {
                log.logInfo("RankingDistribution - transmitted file " + crfile + " to " + address + " successfully in " + ((System.currentTimeMillis() - starttime) / 1000) + " seconds");
                crfile.delete(); // the file is not needed any more locally
            } else {
                log.logInfo("RankingDistribution - error transmitting file " + crfile + " to " + address + ": " + result);
            }
        } catch (IOException e) {
            log.logInfo("RankingDistribution - could not read file " + crfile + ": " + e.getMessage());
            result = "input file error: " + e.getMessage();
        }
        
        // show success
        return result == null;
    }

}