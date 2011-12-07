import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.List;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;


public class GitRevTask extends org.apache.tools.ant.Task {
	
	private String repoPath;
	private String revprop;
	private String dateprop;
	
	public void setRepoPath(final String repoPath) {
		this.repoPath = repoPath;
	}

    public void setRevprop(String revprop) {
        this.revprop = revprop;
    }

    public void setDateprop(String dateprop) {
        this.dateprop = dateprop;
    }
	
	public void execute() {
		if (this.revprop==null || this.revprop.length() == 0) {
            log("git entries file name revprop was not set properly",Project.MSG_ERR);
            return;
        }
		if (this.dateprop==null || this.dateprop.length() == 0) {
            log("git entries file name dateprop was not set properly",Project.MSG_ERR);
            return;
        }

        String revision = null;
		String lastTag = null;
		String commitDate = null;
		try {
			final File src = new File(repoPath);
			final Repository repo = new FileRepositoryBuilder().readEnvironment()
					.findGitDir(src).build();
			final ObjectId head = repo.resolve("HEAD");
			
			final Git git = new Git(repo);
			final List<RevTag> tags = git.tagList().call();
			
			final RevWalk walk = new RevWalk(repo);
			final RevCommit headCommit = walk.parseCommit(head);
			final SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd");
			commitDate = df.format(headCommit.getAuthorIdent().getWhen());
			walk.markStart(headCommit);
			int distance = 0;
			for (final RevCommit commit : walk) {
				for (final RevTag tag : tags) {
					if (commit.equals(tag.getObject())) {
						lastTag = tag.getShortMessage();
						break;
					}
				}
				if (lastTag != null || distance++ > 999) break;
			}
			walk.dispose();
			if (lastTag == null) {
				revision = "0000";
			} else {
				revision = Integer.toString(distance + 9000);
			}
		} catch (final IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        
        Project theProject = getProject();
        if (theProject != null) {
            theProject.setProperty(this.revprop, revision);
            log("Property '" + this.revprop + "' set to '" + revision + "'", Project.MSG_VERBOSE);
            theProject.setProperty(this.dateprop, commitDate);
            log("Property '" + this.dateprop + "' set to '" + commitDate + "'", Project.MSG_VERBOSE);
        }
	}
	
	public static void main(String[] args) {
		GitRevTask gitRevTask = new GitRevTask();
		gitRevTask.setRepoPath("/home/sgaebel/git/yacy.rc1");
		gitRevTask.setRevprop("baseRevisionNr");
		gitRevTask.setDateprop("DSTAMP");
		
		gitRevTask.execute();
	}
}
