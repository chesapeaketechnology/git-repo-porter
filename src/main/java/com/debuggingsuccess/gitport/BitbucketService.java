package com.debuggingsuccess.gitport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
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
public class BitbucketService
{
    private static final String AUTHORIZATION_HEADER_NAME = "Authorization";
    private static final String REST_API_ROOT = "/rest/api/1.0";
    private static final String PROJECTS_ENDPOINT = REST_API_ROOT + "/projects";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final String authHeaderValue;
    private final String host;

    public BitbucketService(String host, String username, String password)
    {
        this.host = host;
        authHeaderValue = getBasicAuthHeaderValue(username, password);
    }

    /**
     * @param username The username for accessing Bitbucket
     * @param password The password for accessing Bitbucket
     * @return The header value for basic authentication.
     * @see <a href="https://developer.atlassian.com/server/bitbucket/how-tos/example-basic-authentication/">
     * Authenticating with the REST API</a>
     */
    private String getBasicAuthHeaderValue(String username, String password)
    {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
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
            HttpRequest request = HttpRequest.newBuilder(getUri(reposEndpoint, 1000, -1))
                    .GET()
                    .header(AUTHORIZATION_HEADER_NAME, authHeaderValue)
                    .build();

            final HttpResponse<String> response = HttpClient.newHttpClient().send(request,
                    HttpResponse.BodyHandlers.ofString());

            final int statusCode = response.statusCode();
            if (statusCode != HttpURLConnection.HTTP_OK)
            {
                throw new IOException("GET from " + reposEndpoint + " was unsuccessful. Status code: " + statusCode);
            }

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
     * Gets the URI for the provided endpoint, appending query parameters for the limit and start values if provided.
     *
     * @param endpoint The REST endpoint
     * @param limit    The limit for the paged API
     * @param start    The start for the paged API
     * @return The URI for the rest request
     * @throws URISyntaxException if an error occurred constructing the URI
     * @see <a href="https://docs.atlassian.com/bitbucket-server/rest/7.19.2/bitbucket-rest.html#paging-params"/>
     * Bitbucket Paged APIs</a>
     */
    private URI getUri(String endpoint, int limit, int start) throws URISyntaxException
    {
        StringBuilder stringBuilder = new StringBuilder();
        if (limit != -1)
        {
            stringBuilder.append("limit=").append(limit);
        }

        if (start != -1)
        {
            if (stringBuilder.length() != 0) stringBuilder.append("&");

            stringBuilder.append("start=").append(start);
        }

        String query = stringBuilder.length() == 0 ? null : stringBuilder.toString();

        return new URI("https", null, host, -1, endpoint, query, null);
    }
}
