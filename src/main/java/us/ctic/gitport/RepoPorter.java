package us.ctic.gitport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;

/**
 * Ports repos from Bitbucket to GitLab.
 */
public class RepoPorter
{
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final BitbucketService bitbucketService;
    private final GitLabService gitLabService;
    private final int groupId;
    private final String projectKey;

    public RepoPorter(BitbucketService bitbucketService, String projectKey, GitLabService gitLabService, int groupId)
    {
        this.bitbucketService = bitbucketService;
        this.projectKey = projectKey;
        this.gitLabService = gitLabService;
        this.groupId = groupId;
    }

    /**
     * Ports the specified repo from Bitbucket to GitLab and configures the GitLab repo with preferred settings.
     *
     * @param repoName The name of the repo to port
     * @return An object containing the details of the repo or null if the repo wasn't ported.
     * @throws IOException if errors occur porting or configuring the repo
     */
    public GitLabRepo portRepo(String repoName) throws IOException
    {
        logger.info("Porting repo {}...", repoName);

        // If this fails with a 403, check the readme for instructions on enabling import from Bitbucket
        GitLabRepo repo = gitLabService.importFromBitbucket(bitbucketService.getHost(), bitbucketService.getUsername(),
                bitbucketService.getAccessToken(), projectKey, repoName, groupId);
        if (repo == null) return null;

        // Configure repo with our preferred settings
        gitLabService.configureProjectSettings(repo.getId());

        // If the Bitbucket repo had a description, copy it to GitLab. Otherwise, just set it to an empty string since
        // the project description is used instead of the repo description during import
        final String repoDescription = bitbucketService.getRepoDescription(projectKey, repoName);
        gitLabService.updateProjectDescription(repo.getId(), repoDescription);

        return repo;
    }
}
