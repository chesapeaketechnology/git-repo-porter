package us.ctic.gitport;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Main program for porting repos from Bitbucket to GitLab.
 */
public class Main
{
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public static final String CONFIG_ROOT = "us.ctic.gitport";
    private static final Config config = ConfigFactory.load();

    public static void main(String[] args) throws IOException
    {
        String bitbucketHost = config.getString(CONFIG_ROOT + ".source.host");
        String bitbucketUsername = config.getString(CONFIG_ROOT + ".source.username");
        String bitbucketAccessToken = config.getString(CONFIG_ROOT + ".source.accessToken");
        String projectKey = config.getString(CONFIG_ROOT + ".source.projectKey");
        String reposToExcludeString = config.getString(CONFIG_ROOT + ".source.reposToExclude");
        List<String> readmeBanner = config.getStringList(CONFIG_ROOT + ".source.readmeBanner");
        String description = config.getString(CONFIG_ROOT + ".source.description");
        String gitlabHost = config.getString(CONFIG_ROOT + ".target.host");
        String accessToken = config.getString(CONFIG_ROOT + ".target.accessToken");
        String groupName = config.getString(CONFIG_ROOT + ".target.groupName");
        int parentGroupId = config.getInt(CONFIG_ROOT + ".target.parentGroupId");

        GitLabService gitLabService = new GitLabService(gitlabHost, accessToken);
        logger.info("Created GitLab service for {}", gitlabHost);

        int groupId = gitLabService.getOrCreateGroupId(groupName, parentGroupId);
        logger.info("Id for GitLab group {}: {}", groupName, groupId);

        // Establish the Bitbucket service and get the list of repos from the project
        BitbucketService bitbucketService = new BitbucketService(bitbucketHost, bitbucketUsername, bitbucketAccessToken);
        logger.info("Created Bitbucket service for {}", bitbucketHost);

        Map<String, String> repoUrlMap = bitbucketService.getRepoUrls(projectKey);
        logger.debug("Repos in Bitbucket project {}: {}", projectKey, repoUrlMap.keySet());

        final List<String> reposToExclude = reposToExcludeString == null ? Collections.emptyList() :
                Arrays.asList(reposToExcludeString.split(","));
        logger.debug("Excluding repos: {}", reposToExclude);
        reposToExclude.forEach(repoUrlMap::remove);

        final RepoPorter repoPorter = new RepoPorter(bitbucketService, projectKey, gitLabService, groupId);
        final RepoDeprecator repoDeprecator = new RepoDeprecator(bitbucketService, projectKey, readmeBanner, description);

        // Port the repos over to GitLab
        logger.info("Repos to port from Bitbucket project {}: {}", projectKey, repoUrlMap.keySet());
        for (Map.Entry<String, String> entry : repoUrlMap.entrySet())
        {
            String repoName = entry.getKey();
            try
            {
                final GitLabRepo repo = repoPorter.portRepo(repoName);
                if (repo == null) continue;

                String newRepoUrl = "https://" + gitlabHost + repo.getFullPath();
                repoDeprecator.deprecateRepo(repoName, newRepoUrl);
            } catch (IOException e)
            {
                logger.error("Port failed for repo {}", repoName, e);
            }
        }
    }
}
