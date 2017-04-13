
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

/**
 * Maven plugin to create property with YaCy release number,
 * a 4 digit number based on commits to the Git repository
 *
 * @phase initialize
 */
@Mojo(name = "create")
public class GitRevMavenTask extends AbstractMojo {

    @Component
    private MavenProject project;
    /**
     * Name of the buildNumber property
     *
     * parameter expression="${maven.buildNumber.buildNumberPropertyName}"
     * default-value="releaseNr"
     */
    @Parameter
    private String branchPropertyName = "branch";
    @Parameter
    private String buildNumberPropertyName = "releaseNr";
    @Parameter
    private String commitDatePropertyName = "DSTAMP";

    Log log = this.getLog();

    public void setBuildNumberPropertyName(String revprop) {
        this.buildNumberPropertyName = revprop;
    }

    public void setCommitDatePropertyName(String dateprop) {
        this.commitDatePropertyName = dateprop;
    }

    @Override
    public void execute() throws MojoExecutionException {

        String branch = null;
        String revision = null;
        String lastTag = null;
        String commitDate = null;

        Repository repo = null;
        Git git = null;
        RevWalk walk = null;
        try {

            final File src = project.getBasedir(); // set Git root path to project root
            repo = new FileRepositoryBuilder().readEnvironment()
                    .findGitDir(src).build();
            branch = repo.getBranch();
            branch = "master".equals(branch) ? "" : "_" + branch;
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
                for (final Ref tag : tags) {
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
        if (project != null) {
            project.getProperties().put(this.branchPropertyName, branch);
            log.info("GitrevMavenTask: set property " + this.branchPropertyName + "='" + branch + "'");            
            project.getProperties().put(this.buildNumberPropertyName, revision);
            log.info("GitrevMavenTask: set property " + this.buildNumberPropertyName + "=" + revision);
            project.getProperties().put(this.commitDatePropertyName, commitDate);
            log.info("GitrevMavenTask: set property " + this.commitDatePropertyName + "=" + commitDate);
        } else {
            log.error("GitrevMavenTask: no Maven project");
            System.out.println(this.branchPropertyName + "=" + branch);
            System.out.println(this.buildNumberPropertyName + "=" + revision);
            System.out.println(this.commitDatePropertyName + "=" + commitDate);
        }
    }


}
