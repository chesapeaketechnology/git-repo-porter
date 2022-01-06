package us.ctic.gitport;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * POJO for representing a Bitbucket branch.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketBranch
{
    private String id;
    private String displayId;
    private String latestCommit;

    public String getId()
    {
        return id;
    }

    public void setId(String id)
    {
        this.id = id;
    }

    public String getDisplayId()
    {
        return displayId;
    }

    public void setDisplayId(String displayId)
    {
        this.displayId = displayId;
    }

    public String getLatestCommit()
    {
        return latestCommit;
    }

    public void setLatestCommit(String latestCommit)
    {
        this.latestCommit = latestCommit;
    }
}
