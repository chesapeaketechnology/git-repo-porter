# git-repo-porter
Tool for porting a group of repos from Bitbucket to GitLab

## Usage
1. Create a copy of _src/main/resources/application.conf_ and fill in all the settings with appropriate values.
   (Note: of course you could modify it in place as well, but take care not to commit the changes, as it will contain
   your plain text username and password.) If you place the copy in the root of the repo, it will be ignored by git.

2. Create a personal access token in GitLab with a scope of `api` ([see instructions here](
   https://docs.gitlab.com/ee/user/profile/personal_access_tokens.html)) and add it to the _application.conf_ for the
   `target.accessToken` setting.

3. Execute the `run` task with the system property `config.file` set to the location of your local _application.conf_:
   ```
   gradlew run -Dconfig.file=path/to/local/application.conf`
   ```
   
   For example, if the _application.conf_ file was copied to the root of the repo:
   ```
   gradlew run -Dconfig.file=application.conf
   ```

   This will port all repos from the specified Bitbucket project (minus any repos included in the CSV list of 
   `reposToExclude`) to GitLab in the specified group.