package us.ctic.gitport;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POJO for representing a GitLab repo.
 */
@SuppressWarnings("unused")
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitLabRepo
{
    private int id;
    private String name;
    private String fullPath;
    private String importSource;

    public int getId()
    {
        return id;
    }

    public void setId(int id)
    {
        this.id = id;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    @JsonProperty("full_path")
    public String getFullPath()
    {
        return fullPath;
    }

    public void setFullPath(String fullPath)
    {
        this.fullPath = fullPath;
    }

    @JsonProperty("import_status")
    public String getImportSource()
    {
        return importSource;
    }

    public void setImportSource(String importSource)
    {
        this.importSource = importSource;
    }
}
