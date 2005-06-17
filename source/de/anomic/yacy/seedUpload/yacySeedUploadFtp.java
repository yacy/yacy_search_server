package de.anomic.yacy.seedUpload;

import java.io.File;

import de.anomic.net.ftpc;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacySeedDB;
import de.anomic.yacy.yacySeedUploader;

public class yacySeedUploadFtp implements yacySeedUploader {

    public static final String CONFIG_FTP_SERVER = "seedFTPServer";
    public static final String CONFIG_FTP_ACCOUNT = "seedFTPAccount";
    public static final String CONFIG_FTP_PASSWORD = "seedFTPPassword";
    public static final String CONFIG_FTP_PATH = "seedFTPPath";
    
    public String uploadSeedFile (serverSwitch sb, yacySeedDB seedDB, File seedFile) throws Exception {
        try {        
            if (sb == null) throw new NullPointerException("Reference to serverSwitch nut not be null.");
            if (seedDB == null) throw new NullPointerException("Reference to seedDB must not be null.");
            if ((seedFile == null)||(!seedFile.exists())) throw new Exception("Seed file does not exist.");
            
            String  seedFTPServer   = sb.getConfig(CONFIG_FTP_SERVER,null);
            String  seedFTPAccount  = sb.getConfig(CONFIG_FTP_ACCOUNT,null);
            String  seedFTPPassword = sb.getConfig(CONFIG_FTP_PASSWORD,null);
            File    seedFTPPath     = new File(sb.getConfig(CONFIG_FTP_PATH,null));       
        
            if ((seedFTPServer != null) && (seedFTPAccount != null) && (seedFTPPassword != null) && (seedFTPPath != null)) {
                String log = ftpc.put(seedFTPServer, seedFile, seedFTPPath.getParent(), seedFTPPath.getName(), seedFTPAccount, seedFTPPassword);
                return log;
            } 
            throw new Exception ("Seed upload settings not configured properly. password-len=" +
                    seedFTPPassword.length() + ", filePath=" +
                    seedFTPPath);
        } catch (Exception e) {
            throw e;
        }
    }

    public String[] getConfigurationOptions() {
        return new String[] {CONFIG_FTP_SERVER,CONFIG_FTP_ACCOUNT,CONFIG_FTP_PASSWORD,CONFIG_FTP_PATH};
    }

    public String[] getLibxDependences() {
        return new String[]{};
    }

}
