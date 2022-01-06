package us.ctic.gitport;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Service for interacting with the GitLab REST API.
 *
 * @see <a href="https://docs.gitlab.com/ee/api/#rest-api">GitLab REST API</a>
 */
public class GitLabService extends ARestService
{
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final String REST_API_ROOT = "/api/v4";
    private static final String GROUPS_ENDPOINT = REST_API_ROOT + "/groups";
    private static final String IMPORT_BITBUCKET_ENDPOINT = REST_API_ROOT + "/import/bitbucket_server";
    private static final String NAMESPACES_ENDPOINT = REST_API_ROOT + "/namespaces";
    private static final String PROJECTS_ENDPOINT = REST_API_ROOT + "/projects";
    private final String accessTokenParam;

    public GitLabService(String host, String accessToken)
    {
        super(host);
        this.accessTokenParam = "private_token=" + accessToken;
    }

    /**
     * Gets or creates a group with the provided name and parent group.
     *
     * @param groupName     The name of the group to create
     * @param parentGroupId The id of the parent group if the new group is to be a sub-group, or -1 for a top-level group
     * @return The id of the group or -1 if it doesn't exist
     * @throws IOException if an error occurred getting or creating the group
     */
    public int getOrCreateGroupId(String groupName, int parentGroupId) throws IOException
    {
        try
        {
            int groupId = getGroupId(groupName);

            if (groupId != -1)
            {
                return groupId;
            } else
            {
                return createGroup(groupName, parentGroupId);
            }
        } catch (Exception e)
        {
            throw new IOException("Error getting or creating group " + groupName + ": " + e.getMessage(), e);
        }
    }

    /**
     * @param groupName The name of the group to find
     * @return The id of the group or -1 if it doesn't exist
     * @throws IOException if an error occurred querying for the group
     */
    public int getGroupId(String groupName) throws IOException
    {
        try
        {
            List<String> queryParams = new ArrayList<>();
            queryParams.add("search=" + groupName);
            HttpRequest request = HttpRequest.newBuilder(getUri(GROUPS_ENDPOINT, queryParams, true))
                    .GET()
                    .build();

            final HttpResponse<String> response = getStringHttpResponse(request);

            // TODO: Add support for paging if more than 100 matching groups (unlikely so low priority).

            JsonNode jsonNode = objectMapper.readTree(response.body());
            Iterator<JsonNode> nodeIterator = jsonNode.elements();
            while (nodeIterator.hasNext())
            {
                JsonNode node = nodeIterator.next();

                if (node.get("name").textValue().equalsIgnoreCase(groupName))
                {
                    return node.get("id").intValue();
                }
            }
        } catch (Exception e)
        {
            throw new IOException("Error getting id for group " + groupName + ": " + e.getMessage(), e);
        }

        return -1;
    }

    /**
     * @param groupId The id of the group to find
     * @return The path of the group
     * @throws IOException if an error occurred querying for the group
     */
    public String getGroupPath(int groupId) throws IOException
    {
        try
        {
            HttpRequest request = HttpRequest.newBuilder(getUri(NAMESPACES_ENDPOINT + "/" + groupId))
                    .GET()
                    .build();

            final HttpResponse<String> response = getStringHttpResponse(request);

            JsonNode jsonNode = objectMapper.readTree(response.body());
            return jsonNode.get("full_path").textValue();
        } catch (Exception e)
        {
            throw new IOException("Error getting path for group " + groupId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Creates a group with the provided name and parent group.
     *
     * @param groupName     The name of the group to create
     * @param parentGroupId The id of the parent group if the new group is to be a sub-group, or -1 for a top-level group
     * @return The id of the new group
     * @throws IOException if an error occurred creating the group
     */
    public int createGroup(String groupName, int parentGroupId) throws IOException
    {
        try
        {
            Map<String, Object> jsonMap = new HashMap<>();
            jsonMap.put("name", groupName);
            jsonMap.put("path", groupName.replaceAll("//s+", "-").toLowerCase());
            if (parentGroupId != -1) jsonMap.put("parent_id", parentGroupId);

            HttpRequest request = HttpRequest.newBuilder(getUri(GROUPS_ENDPOINT))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(jsonMap)))
                    .build();

            final HttpResponse<String> response = getStringHttpResponse(request);

            JsonNode jsonNode = objectMapper.readTree(response.body());
            int groupId = jsonNode.get("id").intValue();

            logger.info("Created group {}. Id = {}", groupName, groupId);
            return groupId;
        } catch (Exception e)
        {
            throw new IOException("Error creating group " + groupName + ": " + e.getMessage(), e);
        }
    }

    /**
     * Imports a repo from a Bitbucket instance into GitLab.
     *
     * @param bbServerUrl   The Bitbucket server URL
     * @param bbUsername    The Bitbucket username
     * @param bbAccessToken The Bitbucket personal access token or password
     * @param bbProjectKey  The Bitbucket project key
     * @param bbRepoName    The name of the Bitbucket repo
     * @param parentGroupId The id of the target group in GitLab
     * @return An object containing the details of the repo that was imported or null if the repo was not imported
     * @throws IOException if an error occurred importing the repo
     * @see <a href="https://docs.gitlab.com/ee/api/import.html#import-repository-from-bitbucket-server">Import
     * repository from Bitbucket Server</a>
     */
    public GitLabRepo importFromBitbucket(String bbServerUrl, String bbUsername, String bbAccessToken, String bbProjectKey,
                                          String bbRepoName, int parentGroupId) throws IOException
    {
        try
        {
            // First double check that there isn't already a repo with that name
            if (isRepoInGroup(bbRepoName, parentGroupId))
            {
                logger.info("Repo {} already exists in group; not porting", bbRepoName);
                return null;
            }

            String groupPath = getGroupPath(parentGroupId);

            Map<String, Object> jsonMap = new HashMap<>();
            jsonMap.put("bitbucket_server_url", "https://" + bbServerUrl);
            jsonMap.put("bitbucket_server_username", bbUsername);
            jsonMap.put("personal_access_token", bbAccessToken);
            jsonMap.put("bitbucket_server_project", bbProjectKey);
            jsonMap.put("bitbucket_server_repo", bbRepoName);
            jsonMap.put("target_namespace", groupPath);

            HttpRequest request = HttpRequest.newBuilder(getUri(IMPORT_BITBUCKET_ENDPOINT))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(jsonMap)))
                    .build();

            final HttpResponse<String> response = getStringHttpResponse(request);
            return objectMapper.readValue(response.body(), GitLabRepo.class);
        } catch (Exception e)
        {
            throw new IOException("Error importing repo " + bbRepoName + ": " + e.getMessage(), e);
        }
    }

    /**
     * Attempts to find a repo with the specified name in the group.
     *
     * @param repoName The name of the repo
     * @param groupId  The id of the group in GitLab
     * @return True if the repo already exists in the group
     * @throws IOException if an error occurred querying the group
     */
    public boolean isRepoInGroup(String repoName, int groupId) throws IOException
    {
        try
        {
            final String endpoint = GROUPS_ENDPOINT + "/" + groupId + "/projects";
            HttpRequest request = HttpRequest.newBuilder(getPagedUri(endpoint))
                    .GET()
                    .build();

            final HttpResponse<String> response = getStringHttpResponse(request);

            JsonNode jsonNode = objectMapper.readTree(response.body());
            Iterator<JsonNode> nodeIterator = jsonNode.elements();
            while (nodeIterator.hasNext())
            {
                JsonNode node = nodeIterator.next();

                if (node.get("name").textValue().equalsIgnoreCase(repoName))
                {
                    return true;
                }
            }
        } catch (Exception e)
        {
            throw new IOException("Error getting projects in group " + groupId + ": " + e.getMessage(), e);
        }

        return false;
    }

    /**
     * Configures the project with the settings recommended for our team.
     * TODO: make this configurable in the future.
     *
     * @param projectId The id of the project
     * @throws IOException if an error occurred configuring the project
     */
    public void configureProjectSettings(int projectId) throws IOException
    {
        try
        {
            Map<String, Object> jsonMap = new HashMap<>();
            jsonMap.put("squash_option", "never");
            jsonMap.put("only_allow_merge_if_all_discussions_are_resolved", true);
            jsonMap.put("suggestion_commit_message", "%{branch_name}: Apply %{suggestions_count} suggestion(s) to %{files_count} file(s)");

            HttpRequest request = HttpRequest.newBuilder(getUri(PROJECTS_ENDPOINT + "/" + projectId))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(jsonMap)))
                    .build();

            getStringHttpResponse(request);
        } catch (Exception e)
        {
            throw new IOException("Error configuring repo " + projectId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Updates the description on the specified repo.
     *
     * @param projectId   The id of the project
     * @param description The new description
     * @throws IOException if an error occurred updating the description.
     */
    public void updateProjectDescription(int projectId, String description) throws IOException
    {
        try
        {
            Map<String, Object> jsonMap = new HashMap<>();
            jsonMap.put("description", description);

            HttpRequest request = HttpRequest.newBuilder(getUri(PROJECTS_ENDPOINT + "/" + projectId))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(jsonMap)))
                    .build();

            getStringHttpResponse(request);
        } catch (Exception e)
        {
            throw new IOException("Error updating repo description: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method that doesn't include pagination parameters or other query params besides access token.
     *
     * @param endpoint    The REST endpoint
     * @return The URI for the REST request
     * @throws URISyntaxException if an error occurred constructing the URI
     */
    private URI getUri(String endpoint) throws URISyntaxException
    {
        return getUri(endpoint, new ArrayList<>(), false);
    }

    /**
     * Convenience method that includes pagination parameters but no other query params besides access token.
     *
     * @param endpoint    The REST endpoint
     * @return The URI for the REST request
     * @throws URISyntaxException if an error occurred constructing the URI
     */
    private URI getPagedUri(String endpoint) throws URISyntaxException
    {
        return getUri(endpoint, new ArrayList<>(), true);
    }

    /**
     * Convenience method that defaults the pagination limit to the max value and includes the access token query param.
     *
     * @param endpoint    The REST endpoint
     * @param queryParams List of any additional query params to include in the request
     * @param pagedRequest Indicates if the request is a paged request
     * @return The URI for the REST request
     * @throws URISyntaxException if an error occurred constructing the URI
     */
    private URI getUri(String endpoint, List<String> queryParams, boolean pagedRequest) throws URISyntaxException
    {
        queryParams.add(0, accessTokenParam);
        return getUri(endpoint, pagedRequest ? 100 : -1, -1, queryParams.toArray(new String[0]));
    }

    /**
     * {@inheritDoc}
     *
     * @see <a href="https://docs.gitlab.com/ee/api/#pagination"/>GitLab Pagination</a>
     */
    @Override
    protected String getPageLimitParamName()
    {
        return "per_page";
    }

    /**
     * {@inheritDoc}
     *
     * @see <a href="https://docs.gitlab.com/ee/api/#pagination"/>GitLab Pagination</a>
     */
    @Override
    protected String getStartingPageParamName()
    {
        return "page";
    }
}
