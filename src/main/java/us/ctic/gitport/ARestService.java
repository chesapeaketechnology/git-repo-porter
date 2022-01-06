package us.ctic.gitport;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Abstract base class for any functionality common between the two REST services.
 */
public abstract class ARestService
{
    protected static final ObjectMapper objectMapper = new ObjectMapper();
    protected final String host;

    /**
     * Constructor.
     *
     * @param host The host address for the service (without http/https)
     */
    public ARestService(String host)
    {
        this.host = host;
    }

    /**
     * Gets the URI for the provided endpoint, appending query parameters for the pagination API if provided.
     *
     * @param endpoint     The REST endpoint
     * @param pageLimit    The number of results to include per page for the pagination API (-1 to use default)
     * @param startingPage The starting page for the pagination API (-1 to use default)
     * @param queryParams  Additional query params to include (optional)
     * @return The URI for the REST request
     * @throws URISyntaxException if an error occurred constructing the URI
     */
    protected URI getUri(String endpoint, int pageLimit, int startingPage, String... queryParams) throws URISyntaxException
    {
        List<String> queryParamList = new ArrayList<>(Arrays.asList(queryParams));
        if (pageLimit != -1) queryParamList.add(getPageLimitParamName() + "=" + pageLimit);
        if (startingPage != -1) queryParamList.add(getStartingPageParamName() + "=" + startingPage);

        String query = queryParamList.isEmpty() ? null : String.join("&", queryParamList);
        return new URI("https", null, host, -1, endpoint, query, null);
    }

    /**
     * @return The name of the parameter that specifies the number of results to return per page as part of the paging API.
     */
    protected abstract String getPageLimitParamName();

    /**
     * @return The name of the parameter that specifies the page number to start with as part of the paging API.
     */
    protected abstract String getStartingPageParamName();

    /**
     * Sends the HTTP request and gets the HTTP response as a string.
     *
     * @param request The HTTP request
     * @return The HTTP response as a string.
     * @throws IOException          if the request was not successful
     * @throws InterruptedException if the send operation is interrupted
     */
    protected HttpResponse<String> getStringHttpResponse(HttpRequest request) throws IOException, InterruptedException
    {
        final HttpResponse<String> response = HttpClient.newHttpClient().send(request,
                HttpResponse.BodyHandlers.ofString());

        final int statusCode = response.statusCode();
        if (statusCode != HttpURLConnection.HTTP_OK && statusCode != HttpURLConnection.HTTP_CREATED)
        {
            throw new IOException(request.method() + " from " + request.uri() + " was unsuccessful. Status code: " +
                    statusCode + ". Response body: " + response.body());
        }

        return response;
    }
}
