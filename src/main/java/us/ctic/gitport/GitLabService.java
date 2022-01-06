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
            HttpRequest request = HttpRequest.newBuilder(getUri(GROUPS_ENDPOINT, queryParams))
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
            HttpRequest request = HttpRequest.newBuilder(getUri(NAMESPACES_ENDPOINT + "/" + groupId, new ArrayList<>()))
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

            HttpRequest request = HttpRequest.newBuilder(getUri(GROUPS_ENDPOINT, new ArrayList<>()))
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
     * Creates a project by importing the repo from the provided URL.
     *
     * @param projectName   The name of the project
     * @param importUrl     The import URL (which should include the username and password/access token)
     * @param parentGroupId The id of the GitLab group where the project should be created
     * @throws IOException if an error occurred creating the project
     */
    public void createProjectFromUrl(String projectName, String importUrl, int parentGroupId) throws IOException
    {
        try
        {
            String groupPath = getGroupPath(parentGroupId);

            Map<String, Object> jsonMap = new HashMap<>();
            jsonMap.put("name", projectName);
            jsonMap.put("import_url", importUrl);
            jsonMap.put("namespace_id", parentGroupId);

            HttpRequest request = HttpRequest.newBuilder(getUri(PROJECTS_ENDPOINT, new ArrayList<>()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(jsonMap)))
                    .build();

            final HttpResponse<String> response = getStringHttpResponse(request);
        } catch (Exception e)
        {
            throw new IOException("Error creating project " + projectName + ": " + e.getMessage(), e);
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
     * @see <a href="https://docs.gitlab.com/ee/api/import.html#import-repository-from-bitbucket-server">Import
     * repository from Bitbucket Server</a>
     */
    public void importFromBitbucket(String bbServerUrl, String bbUsername, String bbAccessToken, String bbProjectKey,
                                    String bbRepoName, int parentGroupId) throws IOException
    {
        try
        {
            String groupPath = getGroupPath(parentGroupId);

            Map<String, Object> jsonMap = new HashMap<>();
            jsonMap.put("bitbucket_server_url", bbServerUrl);
            jsonMap.put("bitbucket_server_username", bbUsername);
            jsonMap.put("personal_access_token", bbAccessToken);
            jsonMap.put("bitbucket_server_project", bbProjectKey);
            jsonMap.put("bitbucket_server_repo", bbRepoName);
            jsonMap.put("target_namespace", groupPath);

            HttpRequest request = HttpRequest.newBuilder(getUri(IMPORT_BITBUCKET_ENDPOINT, new ArrayList<>()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(jsonMap)))
                    .build();

            final HttpResponse<String> response = getStringHttpResponse(request);
        } catch (Exception e)
        {
            throw new IOException("Error importing repo " + bbRepoName + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method that defaults the pagination limit to the max value and includes the access token query param.
     *
     * @param endpoint    The REST endpoint
     * @param queryParams List of any additional query params to include in the request
     * @return The URI for the REST request
     * @throws URISyntaxException if an error occurred constructing the URI
     */
    private URI getUri(String endpoint, List<String> queryParams) throws URISyntaxException
    {
        queryParams.add(0, accessTokenParam);
        return getUri(endpoint, 100, -1, queryParams.toArray(new String[0]));
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
