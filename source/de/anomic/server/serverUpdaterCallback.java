package de.anomic.server;

public interface serverUpdaterCallback {
	public boolean updateYaCyIsPossible();
	public void grantYaCyUpdate();
	public String getYaCyUpdateReleaseVersion();
	//public File getYaCyUpdateReleaseFile();
	public String getYaCyUpdateSource();
	/** Signal a user initiated YaCy shutdown (not restart!) to the updater so it can terminate itself */
	public void signalYaCyShutdown();
	public void signalYaCyRestart();
}