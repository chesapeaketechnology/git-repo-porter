# git-repo-porter
Tool for porting a group of repos from Bitbucket to GitLab

## Prerequisites
1. Ask your GitLab admin to [enable importing from Bitbucket Server](
   https://docs.gitlab.com/ee/user/project/import/bitbucket_server.html#import-your-bitbucket-repositories). If this is
   _not_ enabled, the REST call to import will return a 403 Forbidden.
2. Ensure you have the appropriate permissions for both Bitbucket and GitLab. Admin is required on Bitbucket and Owner
   is required on GitLab.
3. Check the Bitbucket project-level branch permissions to ensure they will not prevent you from changing the repo
   without a pull request. If there are such permissions, it should be sufficient to add yourself as an exclusion.

## Usage
1. Create a copy of _src/main/resources/application.conf_ and fill in all the settings with appropriate values.
   (Note: of course you could modify it in place as well, but take care not to commit the changes, as it will contain
   your plain text username and access token.) If you place the copy in the root of the repo, it will be ignored by git.

   For the access tokens:

   1. Create a personal access token in Bitbucket with a scope of **Project read** ([instructions here](
      https://confluence.atlassian.com/bitbucketserver/personal-access-tokens-939515499.html)) and add it to the 
      _application.conf_ as the `source.accessToken` setting.

   2. Create a personal access token in GitLab with a scope of **api** ([instructions here](
      https://docs.gitlab.com/ee/user/profile/personal_access_tokens.html)) and add it to the _application.conf_ as the
      `target.accessToken` setting.

2. If you only wish to port a single repo or some subset of the Bitbucket project, use the `reposToInclude` or
   `reposToExclude` settings:
   1. To port a single repo, set `reposToInclude` to the name of the repo, e.g., "my-repo".
   3. To port only a few repos, set `reposToInclude` to the names of the repos, separated by commas, e.g., 
      "my-repo,my-other-repo".
   4. To port most of the repos in the project, set `reposToExclude` to the names of the repos to ignore, separated by
      commas.

3. Execute the `run` task with the system property `config.file` set to the location of your local _application.conf_:
   ```
   gradlew run -Dconfig.file=path/to/local/application.conf
   ```
   
   For example, if the _application.conf_ file was copied to the root of the repo:
   ```
   gradlew run -Dconfig.file=application.conf
   ```

   This will port all repos from the specified Bitbucket project unless repos to include/exclude were specified via the
   `reposToInclude` or `reposToExclude` settings.
