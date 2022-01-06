package us.ctic.gitport;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.mizosoft.methanol.MediaType;
import com.github.mizosoft.methanol.MultipartBodyPublisher;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for interacting with the Bitbucket REST API.
 *
 * @see <a href="https://developer.atlassian.com/server/bitbucket/reference/rest-api/">Bitbucket REST APIs</a>
 */
public class BitbucketService extends ARestService
{
    private static final String AUTHORIZATION_HEADER_NAME = "Authorization";
    private static final String BRANCH_PERMISSIONS_API_ROOT = "/rest/branch-permissions/2.0/projects";
    private static final String REST_API_ROOT = "/rest/api/1.0";
    private static final String PROJECTS_ENDPOINT = REST_API_ROOT + "/projects";
    private final String authHeaderValue;
    private final String username;
    private final String accessToken;

    public BitbucketService(String host, String username, String accessToken)
    {
        super(host);
        this.username = username;
        this.accessToken = accessToken;
        authHeaderValue = getBasicAuthHeaderValue(username, accessToken);
    }

    /**
     * @param username    The username for accessing Bitbucket
     * @param accessToken The personal access token for accessing Bitbucket
     * @return The header value for basic authentication.
     * @see <a href="https://developer.atlassian.com/server/bitbucket/how-tos/example-basic-authentication/">
     * Authenticating with the REST API</a>
     */
    private String getBasicAuthHeaderValue(String username, String accessToken)
    {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + accessToken).getBytes());
    }

    /**
     * Gets the default branch information for the specified repo.
     *
     * @param projectKey The key of the project
     * @param repoName   The name of the repo
     * @return The info for the default branch.
     * @throws IOException if an error occurred getting the branch.
     */
    public BitbucketBranch getDefaultBranch(String projectKey, String repoName) throws IOException
    {
        try
        {
            String endpoint = PROJECTS_ENDPOINT + "/" + projectKey + "/repos/" + repoName + "/default-branch";
            HttpRequest request = HttpRequest.newBuilder(getUri(endpoint))
                    .GET()
                    .header(AUTHORIZATION_HEADER_NAME, authHeaderValue)
                    .build();

            final HttpResponse<String> response = getStringHttpResponse(request);
            return objectMapper.readValue(response.body(), BitbucketBranch.class);
        } catch (Exception e)
        {
            throw new IOException("Error finding file: " + e.getMessage(), e);
        }
    }

    /**
     * Attempts to find a file with the provided name in the specified repo.
     *
     * @param projectKey The key of the project
     * @param repoName   The name of the repo to query for files
     * @param fileName   The name of the file (can be partial name if exact name unknown)
     * @return The name of the file or null if the file doesn't exist.
     * @throws IOException if an error occurred querying the files.
     */
    public String findFile(String projectKey, String repoName, String fileName) throws IOException
    {
        try
        {
            String endpoint = PROJECTS_ENDPOINT + "/" + projectKey + "/repos/" + repoName + "/browse";
            HttpRequest request = HttpRequest.newBuilder(getUri(endpoint, true))
                    .GET()
                    .header(AUTHORIZATION_HEADER_NAME, authHeaderValue)
                    .build();

            final HttpResponse<String> response = getStringHttpResponse(request);

            String lowercaseFileName = fileName.toLowerCase();
            JsonNode jsonNode = objectMapper.readTree(response.body());
            for (JsonNode valueNode : jsonNode.get("children").get("values"))
            {
                final JsonNode pathNode = valueNode.get("path");
                String name = pathNode.get("name").textValue();
                if (name.toLowerCase().contains(lowercaseFileName)) return name;
            }
        } catch (Exception e)
        {
            throw new IOException("Error finding file: " + e.getMessage(), e);
        }

        return null;
    }

    /**
     * Retrieves the contents of the file with the provided name in the specified repo.
     *
     * @param projectKey The key of the project
     * @param repoName   The name of the repo to query for files
     * @param fileName   The name of the file
     * @return A temp file containing the contents of the file.
     * @throws IOException if an error occurred getting the file.
     */
    public File getFile(String projectKey, String repoName, String fileName) throws IOException
    {
        try
        {
            String endpoint = PROJECTS_ENDPOINT + "/" + projectKey + "/repos/" + repoName + "/browse/" + fileName;
            HttpRequest request = HttpRequest.newBuilder(getUri(endpoint))
                    .GET()
                    .header(AUTHORIZATION_HEADER_NAME, authHeaderValue)
                    .build();

            final HttpResponse<String> response = getStringHttpResponse(request);

            final File file = Files.createTempFile(null, fileName).toFile();
            file.deleteOnExit();

            List<String> lines = new ArrayList<>();
            JsonNode jsonNode = objectMapper.readTree(response.body());
            for (JsonNode valueNode : jsonNode.get("lines"))
            {
                lines.add(valueNode.get("text").textValue());
            }

            Files.write(file.toPath(), lines);

            return file;
        } catch (Exception e)
        {
            throw new IOException("Error finding file: " + e.getMessage(), e);
        }
    }

    /**
     * Updates the file in the specified repo with the body of the provided file.
     *
     * @param projectKey    The key of the project
     * @param repoName      The name of the repo to query for files
     * @param fileName      The name of the file
     * @param fileBody      A file containing the contents to commit to the repo in the named file
     * @param commitMessage The message to use when committing the updated file
     * @param branchName    The name of the branch to update
     * @param commitId      The commit id of the file before it was edited (or null if this is a new file)
     * @throws IOException if an error occurred putting the file.
     */
    public void updateFile(String projectKey, String repoName, String fileName, File fileBody, String commitMessage,
                           String branchName, String commitId) throws IOException
    {
        try
        {
            final MultipartBodyPublisher.Builder builder = MultipartBodyPublisher.newBuilder()
                    .filePart("content", fileBody.toPath(), MediaType.TEXT_ANY)
                    .textPart("message", commitMessage)
                    .textPart("branch", branchName)
                    .boundary(UUID.randomUUID().toString());
            if (commitId != null) builder.textPart("sourceCommitId", commitId);

            final MultipartBodyPublisher multipartBodyPublisher = builder.build();
            String reposEndpoint = PROJECTS_ENDPOINT + "/" + projectKey + "/repos/" + repoName + "/browse/" + fileName;
            HttpRequest request = HttpRequest.newBuilder(getUri(reposEndpoint))
                    .PUT(multipartBodyPublisher)
                    .header(AUTHORIZATION_HEADER_NAME, authHeaderValue)
                    .header("Content-Type", "multipart/form-data; boundary=" + multipartBodyPublisher.boundary())
                    .build();

            final HttpResponse<String> response = getStringHttpResponse(request);
        } catch (Exception e)
        {
            throw new IOException("Error finding file: " + e.getMessage(), e);
        }
    }

    /**
     * Gets the description on the specified repo.
     *
     * @param projectKey The key of the project
     * @param repoName   The name of the repo to update
     * @return The description for the repo or an empty string if one isn't set.
     * @throws IOException if an error occurred getting the description.
     */
    public String getRepoDescription(String projectKey, String repoName) throws IOException
    {
        try
        {
            String endpoint = PROJECTS_ENDPOINT + "/" + projectKey + "/repos/" + repoName;
            HttpRequest request = HttpRequest.newBuilder(getUri(endpoint))
                    .header("Content-Type", "application/json")
                    .header(AUTHORIZATION_HEADER_NAME, authHeaderValue)
                    .GET()
                    .build();

            final HttpResponse<String> response = getStringHttpResponse(request);
            JsonNode jsonNode = objectMapper.readTree(response.body());
            final JsonNode descriptionNode = jsonNode.get("description");
            return descriptionNode == null ? "" : descriptionNode.textValue();
        } catch (Exception e)
        {
            throw new IOException("Error updating repo description: " + e.getMessage(), e);
        }
    }

    /**
     * Updates the description on the specified repo.
     *
     * @param projectKey  The key of the project
     * @param repoName    The name of the repo to update
     * @param description The new description
     * @throws IOException if an error occurred updating the description.
     */
    public void updateRepoDescription(String projectKey, String repoName, String description) throws IOException
    {
        try
        {
            Map<String, Object> jsonMap = new HashMap<>();
            jsonMap.put("description", description);

            String endpoint = PROJECTS_ENDPOINT + "/" + projectKey + "/repos/" + repoName;
            HttpRequest request = HttpRequest.newBuilder(getUri(endpoint))
                    .header("Content-Type", "application/json")
                    .header(AUTHORIZATION_HEADER_NAME, authHeaderValue)
                    .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(jsonMap)))
                    .build();

            final HttpResponse<String> response = getStringHttpResponse(request);
        } catch (Exception e)
        {
            throw new IOException("Error updating repo description: " + e.getMessage(), e);
        }
    }

    /**
     * @see <a href="https://docs.atlassian.com/bitbucket-server/rest/5.0.1/bitbucket-ref-restriction-rest.html">
     * REST Resources Provided By: Bitbucket Server - Ref Restriction</a>
     */
    public void addBranchPermission(String projectKey, String repoName, String permissionJson) throws IOException
    {
        try
        {
            String endpoint = BRANCH_PERMISSIONS_API_ROOT + "/" + projectKey + "/repos/" + repoName + "/restrictions";
            HttpRequest request = HttpRequest.newBuilder(getUri(endpoint))
                    .header("Content-Type", "application/json")
                    .header(AUTHORIZATION_HEADER_NAME, authHeaderValue)
                    .POST(HttpRequest.BodyPublishers.ofString(permissionJson))
                    .build();

            final HttpResponse<String> response = getStringHttpResponse(request);
        } catch (Exception e)
        {
            throw new IOException("Error updating repo description: " + e.getMessage(), e);
        }
    }

    /**
     * Queries the Bitbucket instance to get the URLs of the repos from the specified project key.
     *
     * @param projectKey The key of the project to query for repos
     * @return Map of repo name to Git URL for the repos from the specified project key.
     * @throws IOException if an error occurred querying Bitbucket.
     */
    public Map<String, String> getRepoUrls(String projectKey) throws IOException
    {
        Map<String, String> repoNameToUrlMap = new HashMap<>();

        try
        {
            String reposEndpoint = PROJECTS_ENDPOINT + "/" + projectKey + "/repos";
            HttpRequest request = HttpRequest.newBuilder(getUri(reposEndpoint, true))
                    .GET()
                    .header(AUTHORIZATION_HEADER_NAME, authHeaderValue)
                    .build();

            final HttpResponse<String> response = getStringHttpResponse(request);

            // TODO: Add support for paging if more than 1000 repos (unlikely so low priority). Note: the host could
            //  also have a lower limit for this resource, which would increase the priority, but the host I care about
            //  uses the default of 1000.

            JsonNode jsonNode = objectMapper.readTree(response.body());
            for (JsonNode valueNode : jsonNode.get("values"))
            {
                String repoName = valueNode.get("slug").textValue();
                for (JsonNode cloneNode : valueNode.get("links").get("clone"))
                {
                    if (cloneNode.get("name").textValue().equals("http"))
                    {
                        repoNameToUrlMap.put(repoName, cloneNode.get("href").textValue());
                        break;
                    }
                }
            }
        } catch (Exception e)
        {
            throw new IOException("Error getting repos: " + e.getMessage(), e);
        }

        return repoNameToUrlMap;
    }

    /**
     * Convenience method that doesn't include pagination parameters.
     *
     * @param endpoint The REST endpoint
     * @return The URI for the REST request
     * @throws URISyntaxException if an error occurred constructing the URI
     */
    private URI getUri(String endpoint) throws URISyntaxException
    {
        return getUri(endpoint, false);
    }

    /**
     * Convenience method that defaults the pagination limit to the max value for paged requests.
     *
     * @param endpoint     The REST endpoint
     * @param pagedRequest Indicates if the request is a paged request
     * @return The URI for the REST request
     * @throws URISyntaxException if an error occurred constructing the URI
     */
    private URI getUri(String endpoint, boolean pagedRequest) throws URISyntaxException
    {
        return getUri(endpoint, pagedRequest ? 1000 : -1, -1);
    }

    /**
     * {@inheritDoc}
     *
     * @see <a href="https://docs.atlassian.com/bitbucket-server/rest/7.19.2/bitbucket-rest.html#paging-params"/>
     * Bitbucket Paged APIs</a>
     */
    @Override
    protected String getPageLimitParamName()
    {
        return "limit";
    }

    /**
     * {@inheritDoc}
     *
     * @see <a href="https://docs.atlassian.com/bitbucket-server/rest/7.19.2/bitbucket-rest.html#paging-params"/>
     * Bitbucket Paged APIs</a>
     */
    @Override
    protected String getStartingPageParamName()
    {
        return "start";
    }

    public String getUsername()
    {
        return username;
    }

    public String getAccessToken()
    {
        return accessToken;
    }
}
