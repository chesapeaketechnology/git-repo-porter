package com.debuggingsuccess.gitport;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for interacting with the Bitbucket REST API.
 *
 * @see <a href="https://developer.atlassian.com/server/bitbucket/reference/rest-api/">Bitbucket REST APIs</a>
 */
public class BitbucketService extends ARestService
{
    private static final String AUTHORIZATION_HEADER_NAME = "Authorization";
    private static final String REST_API_ROOT = "/rest/api/1.0";
    private static final String PROJECTS_ENDPOINT = REST_API_ROOT + "/projects";
    private final String authHeaderValue;

    public BitbucketService(String host, String username, String accessToken)
    {
        super(host);
        authHeaderValue = getBasicAuthHeaderValue(username, accessToken);
    }

    /**
     * @param username The username for accessing Bitbucket
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
            HttpRequest request = HttpRequest.newBuilder(getUri(reposEndpoint))
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
     * Convenience method that defaults the pagination limit to the max value.
     * @param endpoint The REST endpoint
     * @return The URI for the REST request
     * @throws URISyntaxException if an error occurred constructing the URI
     */
    private URI getUri(String endpoint) throws URISyntaxException
    {
        return getUri(endpoint, 1000, -1);
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
}
