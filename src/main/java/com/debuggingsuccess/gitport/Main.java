package com.debuggingsuccess.gitport;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public static final String CONFIG_ROOT = "com.debuggingsuccess.gitport";
    private static final Config config = ConfigFactory.load();

    public static void main(String[] args)
    {
        try
        {
            String bitbucketHost = config.getString(CONFIG_ROOT + ".source.host");
            String bitbucketUsername = config.getString(CONFIG_ROOT + ".source.username");
            String bitbucketPassword = config.getString(CONFIG_ROOT + ".source.password");
            BitbucketService bitbucketService = new BitbucketService(bitbucketHost, bitbucketUsername, bitbucketPassword);
            logger.info("Created Bitbucket service for {}", bitbucketHost);

            String projectKey = config.getString(CONFIG_ROOT + ".source.projectKey");
            Map<String, String> repoUrlMap = bitbucketService.getRepoUrls(projectKey);
            logger.debug("Repos in project {}: {}", projectKey, repoUrlMap.keySet());

            String reposToExcludeString = config.getString(CONFIG_ROOT + ".source.reposToExclude");
            final List<String> reposToExclude = reposToExcludeString == null ? Collections.emptyList() :
                    Arrays.asList(reposToExcludeString.split(","));
            logger.debug("Excluding repos: {}", reposToExclude);
            reposToExclude.forEach(repoUrlMap::remove);

            logger.info("Repos to port for project {}: {}", projectKey, repoUrlMap.keySet());
        } catch (Exception e)
        {
            logger.error("Error porting repos", e);
        }
    }
}
