import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
	private String property;
	
	public void setRepoPath(final String repoPath) {
		this.repoPath = repoPath;
	}

    public void setProperty(String property) {
        this.property = property;
    }
	
	public void execute() {
		if (this.property==null || this.property.length() == 0) {
            log("svn entries file name property was not set properly",Project.MSG_ERR);
            return;
        }

        String revision = null;
		String lastTag = null;
		try {
			final File src = new File(repoPath);
			final Repository repo = new FileRepositoryBuilder().readEnvironment()
					.findGitDir(src).build();
			final ObjectId head = repo.resolve("HEAD");
			final String gitrev = head.getName().substring(0, 8);
			
			final Git git = new Git(repo);
			final List<RevTag> tags = git.tagList().call();
			
			final RevWalk walk = new RevWalk(repo);
			walk.markStart(walk.parseCommit(head));
			int distance = 0;
			for (final RevCommit commit : walk) {
				for (final RevTag tag : tags) {
					if (commit.equals(tag.getObject())) {
						lastTag = tag.getShortMessage();
						break;
					}
				}
				if (lastTag == null) lastTag = findRev(commit.getFullMessage());
				if (lastTag != null || distance++ > 99) break;
			}
			walk.dispose();
			if (lastTag == null) {
				revision = "dev" + "-" + gitrev;
			} else {
				revision = lastTag + "-" + distance + "-" + gitrev;
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        
        Project theProject = getProject();
        if (theProject != null) {
            theProject.setProperty(this.property, lastTag);
            log("Property '" + this.property + "' set to '" + revision + "'", Project.MSG_VERBOSE);                
        }
	}
	
	private String findRev(final String message) {
		final Pattern pattern = Pattern.compile("trunk@(\\d{4})\\s+");
		final Matcher matcher = pattern.matcher(message);
		if (matcher.find()) {
			return matcher.group(1);
		}
		return null;
	}
	
	public static void main(String[] args) {
		GitRevTask gitRevTask = new GitRevTask();
		gitRevTask.setRepoPath("/home/sgaebel/git/yacy.rc1");
		
		gitRevTask.execute();
	}
}
