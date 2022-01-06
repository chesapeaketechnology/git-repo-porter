package us.ctic.gitport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
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
            "\"displayId\":\"**/*\",\"type\":{\"id\":\"PATTERN\",\"name\":\"Pattern\"},\"active\":true}}";

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
        final List<String> bannerWithUrl = readmeBanner.stream()
                .map(line -> line.replaceAll("\\$URL", newRepoUrl))
                .collect(Collectors.toList());

        final File readmeFile;
        String readmeFileName = bitbucketService.findFile(projectKey, repoName, "readme");
        if (readmeFileName == null)
        {
            readmeFileName = "README.md";
            readmeFile = null;
        } else
        {
            // Get a file with the contents of the current readme
            readmeFile = bitbucketService.getFile(projectKey, repoName, readmeFileName);

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
        }

        // There's no way to prepend to a file, so create a new temp file, write the banner, then write the content of
        // the original file
        File updatedReadmeFile = Files.createTempFile(null, readmeFileName).toFile();
        updatedReadmeFile.deleteOnExit();
        Files.write(updatedReadmeFile.toPath(), bannerWithUrl);

        if (readmeFile != null)
        {
            try (BufferedReader reader = new BufferedReader(new FileReader(readmeFile));
                 BufferedWriter writer = new BufferedWriter(new FileWriter(updatedReadmeFile, true)))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    writer.write(line);
                    writer.newLine();
                }
            }
        }

        // We need the info about the branch in order to update the file
        final BitbucketBranch defaultBranch = bitbucketService.getDefaultBranch(projectKey, repoName);

        // If this is a new file, pass null for the commit id so Bitbucket won't try to update a non-existent file
        final String commitId = readmeFile == null ? null : defaultBranch.getLatestCommit();
        String commitMessage = "Update " + readmeFileName + " with deprecation banner";
        bitbucketService.updateFile(projectKey, repoName, readmeFileName, updatedReadmeFile, commitMessage,
                defaultBranch.getDisplayId(), commitId);
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
