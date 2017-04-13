import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.tools.ant.Project;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;


public class GitRevTask extends org.apache.tools.ant.Task {
	
	private String repoPath;
	private String branchprop;
	private String revprop;
	private String dateprop;
	
	public void setRepoPath(final String repoPath) {
		this.repoPath = repoPath;
	}
	
	public void setBranchprop(final String branchprop) {
		this.branchprop = branchprop;
	}

    public void setRevprop(final String revprop) {
        this.revprop = revprop;
    }

    public void setDateprop(final String dateprop) {
        this.dateprop = dateprop;
    }
	
	public void execute() {
		if (this.revprop==null || this.revprop.isEmpty()) {
            log("git entries file name revprop was not set properly",Project.MSG_ERR);
            return;
        }
		if (this.dateprop==null || this.dateprop.isEmpty()) {
            log("git entries file name dateprop was not set properly",Project.MSG_ERR);
            return;
        }

		String branch = null;
        String revision = null;
		String lastTag = null;
		String commitDate = null;
		
        Repository repo = null;
        Git git = null;
        RevWalk walk = null;
		try {
			final File src = new File(repoPath);
			repo = new FileRepositoryBuilder().readEnvironment()
					.findGitDir(src).build();
			branch = repo.getBranch();
			branch = "master".equals(branch)? "" : "_" + branch;
			final ObjectId head = repo.resolve("HEAD");
			
			git = new Git(repo);
			final List<Ref> tags = git.tagList().call();
			
			walk = new RevWalk(repo);
			final RevCommit headCommit = walk.parseCommit(head);
			final SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd");
			commitDate = df.format(headCommit.getAuthorIdent().getWhen());
			walk.markStart(headCommit);
			int distance = 0;
			
			/* Peel known tags */
			final List<Ref> peeledTags = new ArrayList<>();
			for(final Ref tag : tags) {
				peeledTags.add(repo.peel(tag));
			}
			
			/* Look for the last tag commit and calculate distance with the HEAD commit */
			for (final RevCommit commit : walk) {
				for (final Ref tag : peeledTags) {
					if (commit.equals(tag.getPeeledObjectId()) || commit.equals(tag.getObjectId())) {
						lastTag = commit.getShortMessage();
						break;
					}
				}
                if (lastTag != null || distance++ > 90999) {
                    break;
                }
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
		} catch (GitAPIException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			/* In all cases, properly release resources */
			if(walk != null) {
				walk.close();
			}
			if(git != null) {
				git.close();
			}
			if(repo != null) {
				repo.close();
			}
		}
        
        Project theProject = getProject();
        if (theProject != null) {
        	theProject.setProperty(this.branchprop, branch);
            log("Property '" + this.branchprop + "' set to '" + branch + "'", Project.MSG_VERBOSE);
        	theProject.setProperty(this.revprop, revision);
            log("Property '" + this.revprop + "' set to '" + revision + "'", Project.MSG_VERBOSE);
            theProject.setProperty(this.dateprop, commitDate);
            log("Property '" + this.dateprop + "' set to '" + commitDate + "'", Project.MSG_VERBOSE);
        }
	}

        /** use: GitRevTask.jar pathtoGitRepro  outputfile
         * optional parameter
         * 1st parameter = path to Git repository (default ..)
         * 2nd parameter = ouputfile (default gitbuild.properties)
         * */
    public static void main(String[] args) {
        GitRevTask gitRevTask = new GitRevTask();
        if (args.length == 0) {
            gitRevTask.setRepoPath(".."); // path to root of git repository
        } else {
            gitRevTask.setRepoPath(args[0]);
        }
        gitRevTask.setBranchprop("branch");
        gitRevTask.setRevprop("baseRevisionNr");
        gitRevTask.setDateprop("DSTAMP");

        Project p = new Project();
        gitRevTask.setProject(p);
        gitRevTask.execute();
        String branch = gitRevTask.getProject().getProperty("branch");
        String version = gitRevTask.getProject().getProperty("baseRevisionNr");
        String commitDate = gitRevTask.getProject().getProperty("DSTAMP");

        File f;
        if (args.length > 1) {
            f = new File (args[1]);
        } else {
            f = new File("gitbuildnumber.properties");
        }
        try {
            f.createNewFile();
            FileWriter w = new FileWriter(f);
            w.append("branch=" + branch + "\n");
            w.append("releaseNr=" + version + "\n");
            w.append("DSTAMP=" + commitDate + "\n");
            w.close();

        } catch (final IOException ex) {}
    }
}
