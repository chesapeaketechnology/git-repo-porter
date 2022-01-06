package us.ctic.gitport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Deprecates repos on the original server by adding a deprecation banner to the readme, updating the description to
 * indicate deprecation, and locking down the repo to prevent commits on all branches.
 */
public class RepoDeprecator
{
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final String READ_ONLY_BRANCH_PERMISSION = "{\"type\":\"read-only\",\"matcher\":{\"id\": \"**/*\"," +
            "\"displayId\":\"**/*\",\"type\":{\"id\":\"PATTERN\",\"name\":Pattern\"},\"active\":true}}";

    private final BitbucketService bitbucketService;
    private final List<String> readmeBanner;
    private final String description;
    private final String projectKey;

    /**
     * Constructor.
     *
     * @param bitbucketService Instance of the Bitbucket service
     * @param projectKey       The key of the project in Bitbucket containing the repos
     * @param readmeBanner     The banner to add to the readme to indicate the repo is deprecated; may contain token $URL
     *                         which will be replaced with the new repo URL
     * @param description      The description to add to the repo to indicate it is deprecated; may contain token $URL
     */
    public RepoDeprecator(BitbucketService bitbucketService, String projectKey, List<String> readmeBanner, String description)
    {
        this.bitbucketService = bitbucketService;
        this.projectKey = projectKey;
        this.readmeBanner = readmeBanner;
        this.description = description;
    }

    /**
     * Deprecates the specified repo by updating the readme and repo description and making the repo read only.
     *
     * @param repoName   The name of the repo
     * @param newRepoUrl The URL for the new repo location
     * @throws IOException if errors occur accessing or updating the repo
     */
    public void deprecateRepo(String repoName, String newRepoUrl) throws IOException
    {
        logger.info("Deprecating repo {}...", repoName);

        updateReadme(repoName, newRepoUrl);
        updateDescription(repoName, newRepoUrl);
        bitbucketService.addBranchPermission(projectKey, repoName, READ_ONLY_BRANCH_PERMISSION);
    }

    /**
     * Updates the readme in the repo with a banner at the top indicating it has been deprecated.
     *
     * @param repoName   The name of the repo
     * @param newRepoUrl The URL for the new repo location
     * @throws IOException if errors occur accessing or updating the readme
     */
    private void updateReadme(String repoName, String newRepoUrl) throws IOException
    {
        final File readmeFile;
        String readmeFileName = bitbucketService.findFile(projectKey, repoName, "readme");
        if (readmeFileName == null)
        {
            // Make a new readme file with just the deprecation banner
            readmeFileName = "README.md";
            readmeFile = Files.createTempFile(null, readmeFileName).toFile();
            readmeFile.deleteOnExit();
        } else
        {
            // Get a file with the contents of the current readme
            readmeFile = bitbucketService.getFile(projectKey, repoName, readmeFileName);
        }

        final List<String> bannerWithUrl = readmeBanner.stream()
                .map(line -> line.replaceAll("\\$URL", newRepoUrl))
                .collect(Collectors.toList());

        // Make sure we haven't already updated this readme (perhaps manually or on a previous run of the script)
        try (BufferedReader reader = new BufferedReader(new FileReader(readmeFile)))
        {
            final String line = reader.readLine();
            if (line != null && line.equals(bannerWithUrl.get(0)))
            {
                logger.debug("Readme already has banner, so not adding again");
                return;
            }
        }

        // Note: we specify an open option of write to avoid the default, which truncates any existing file contents
        Files.write(readmeFile.toPath(), bannerWithUrl, StandardOpenOption.WRITE);

        bitbucketService.updateFile(projectKey, repoName, readmeFileName, readmeFile);
    }

    /**
     * Updates the repo description to indicate it has been deprecated.
     *
     * @param repoName   The name of the repo
     * @param newRepoUrl The URL for the new repo location
     * @throws IOException if an error occurs updating the repo description
     */
    private void updateDescription(String repoName, String newRepoUrl) throws IOException
    {
        final String descriptionWithUrl = description.replaceAll("\\$URL", newRepoUrl);

        bitbucketService.updateRepoDescription(projectKey, repoName, descriptionWithUrl);
    }
}
