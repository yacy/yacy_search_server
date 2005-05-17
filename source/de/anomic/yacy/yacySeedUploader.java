package de.anomic.yacy;

import java.io.File;

import de.anomic.server.serverSwitch;

public interface yacySeedUploader {
    public String uploadSeedFile(serverSwitch sb, yacySeedDB seedDB, File seedFile);
    public String[] getConfigurationOptions();
    public String[] getLibxDependences();
}
