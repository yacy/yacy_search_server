package de.anomic.yacy.seedUpload;

import java.io.File;
import java.net.URL;

import de.anomic.server.serverFileUtils;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacySeedDB;
import de.anomic.yacy.yacySeedUploader;

public class yacySeedUploadFile implements yacySeedUploader {
    
    public static final String CONFIG_FILE_PATH = "seedFilePath";

    public String uploadSeedFile(serverSwitch sb, yacySeedDB seedDB, File seedFile) throws Exception {
        
        String logt, seedFilePath = "";
        try {
            seedFilePath = sb.getConfig(CONFIG_FILE_PATH,"");
            if (seedFilePath.length() == 0) throw new Exception("Path to seed file is not configured properly");
            
            File publicSeedFile = new File(seedFilePath);            
            serverFileUtils.copy(seedFile,publicSeedFile);
            
            return "Seed-List file stored successfully";
        } catch (Exception e) {
            throw new Exception("Unable to store the seed-list file into the filesystem using path '" + seedFilePath + "'. " + e.getMessage());
        }
    }

    public String[] getConfigurationOptions() {
        return new String[]{CONFIG_FILE_PATH};
        }

    public String[] getLibxDependences() {
        return new String[]{};
    }
}
