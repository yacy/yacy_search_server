
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.List;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTag;
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

        try {

            final File src = project.getBasedir(); // set Git root path to project root
            final Repository repo = new FileRepositoryBuilder().readEnvironment()
                    .findGitDir(src).build();
            branch = repo.getBranch();
            branch = "master".equals(branch) ? "" : "_" + branch;
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
                if (lastTag != null || distance++ > 999) {
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
