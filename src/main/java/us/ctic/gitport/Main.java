package us.ctic.gitport;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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

    public static void main(String[] args)
    {
        try
        {
            // Establish the GitLab service and get/create the group
            String gitlabHost = config.getString(CONFIG_ROOT + ".target.host");
            String accessToken = config.getString(CONFIG_ROOT + ".target.accessToken");
            GitLabService gitLabService = new GitLabService(gitlabHost, accessToken);
            logger.info("Created GitLab service for {}", gitlabHost);

            String groupName = config.getString(CONFIG_ROOT + ".target.groupName");
            int parentGroupId = config.getInt(CONFIG_ROOT + ".target.parentGroupId");
            int groupId = gitLabService.getOrCreateGroupId(groupName, parentGroupId);
            logger.info("Id for group {}: {}", groupName, groupId);

            // Establish the Bitbucket service and get the list of repos from the project
            String bitbucketHost = config.getString(CONFIG_ROOT + ".source.host");
            String bitbucketUsername = config.getString(CONFIG_ROOT + ".source.username");
            String bitbucketAccessToken = config.getString(CONFIG_ROOT + ".source.accessToken");
            BitbucketService bitbucketService = new BitbucketService(bitbucketHost, bitbucketUsername, bitbucketAccessToken);
            logger.info("Created Bitbucket service for {}", bitbucketHost);

            String projectKey = config.getString(CONFIG_ROOT + ".source.projectKey");
            Map<String, String> repoUrlMap = bitbucketService.getRepoUrls(projectKey);
            logger.debug("Repos in project {}: {}", projectKey, repoUrlMap.keySet());

            String reposToExcludeString = config.getString(CONFIG_ROOT + ".source.reposToExclude");
            final List<String> reposToExclude = reposToExcludeString == null ? Collections.emptyList() :
                    Arrays.asList(reposToExcludeString.split(","));
            logger.debug("Excluding repos: {}", reposToExclude);
            reposToExclude.forEach(repoUrlMap::remove);

            // Port the repos over to GitLab
            logger.info("Repos to port for project {}: {}", projectKey, repoUrlMap.keySet());

            String prefix = "https://" + URLEncoder.encode(bitbucketUsername, StandardCharsets.UTF_8) + ":" +
                    URLEncoder.encode(bitbucketAccessToken, StandardCharsets.UTF_8) + "@";
            for (Map.Entry<String, String> entry : repoUrlMap.entrySet())
            {
                String repoName = entry.getKey();
                logger.info("Porting repo {}...", repoName);
                // TODO: This didn't work... got a 403; not permitted for free tier?
                gitLabService.importFromBitbucket(bitbucketHost, bitbucketUsername, bitbucketAccessToken, projectKey,
                        repoName, groupId);

                // TODO: This didn't work either... got a 422 "is not a valid HTTP Git repository"
//                String urlWithCreds = entry.getValue().replace("https://", prefix);
//                gitLabService.createProjectFromUrl(repoName, urlWithCreds, groupId);

            }
        } catch (Exception e)
        {
            logger.error("Error porting repos", e);
        }
    }
}
