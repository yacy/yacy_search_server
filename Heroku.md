# Yacy on Heroku

YaCy can be deployed on Heroku platform in various ways. There are currently limitations which make this deployment option better suited for demonstration or testing purposes.

## Limitations

### Ephemeral file system

Each Heroku container instance (aka ["dyno"](https://devcenter.heroku.com/articles/dynos#dynos)) file system is [ephemeral](https://devcenter.heroku.com/articles/dynos#ephemeral-filesystem) : all files are discarded each time the "dyno" is restarted (at least automatically once a day by the platform).

This is not a problem for applications storing their persistent data in an external cloud datastore. But YaCy is currently designed to use the local file system. So beware that when running YaCy on Heroku, all your settings and indexed data will be lost once a day.

### Build version

YaCy main git history is too large to be pushed on Heroku (more than 300MB Heroku "slug" limit), but it is normally used to produce the revision number suffix added to main version. So when building on Heroku platform, YaCy version in /Status.html will always appears with a default revision number (for example 1.91/9000).

## Deploy with the button

- Click on the 'Deploy to Heroku' button on the main YaCy [README.md](README.md).
- Log in with your Heroku account or create one.
- A preconfigured deploy page is proposed (configuration comes from the [app.json](app.json) file).
- Enter the name of your application (don't let Heroku choose a default one).
- Edit the environment variable `MAVEN_CUSTOM_OPTS` : insert ` -Dyacy.staticIP=your_app_name.herokuapp.com`, with `your_app_name` replaced with the name you choosed.
If you ignore the two previous steps, YaCy will run, but in junior mode : it will not be able to be reached by other peers and will not contribute to the global indexing.
- Edit the environment variable `MAVEN_CUSTOM_OPTS` : replace `-Dyacy.admin.passwd=MD5:1029a0355adffb6378ceed9936ca9be4`, with a custom admin password encoded with YaCy (you can get this encoded value by running a local YaCy peer, setting your custom admin password in /ConfigAccounts_p.html, and retrieving it in DATA/SETTINGS/yacy.conf at key `adminAccountBase64MD5`).
- Click on the deploy button.
- Heroku now build YaCy from sources.
- If everything went fine, you can open YaCy search page with the `View` button

## Deploy from command line

Here are some brief instructions to deploy YaCy on Heroku from command line. More detailed explanations can be found on related [Heroku documentation](https://devcenter.heroku.com/articles/getting-started-with-java#introduction).

- Install the Heroku Toolbelt
- Get YaCy sources from git as a zip archive, or be sure to remove the .git directory (if you cloned from git, the .git directory will be far too large and later you will not be able to push sources to Heroku).
- Optional steps (deploy locally to check everything is fine) :
   - build with maven : `mvn clean dependency:list install -DskipTests=true -f libbuild/pom.xml`
   - run locally : `heroku local`
   - check everything works fine at http://localhost:8090
   - stop the local YaCy
- Log in on heroku : `heroku login`
- Create an app on heroku : `heroku create`
- Set the `MAVEN_CUSTOM_OPTS` config var :
 - run : `heroku config:set MAVEN_CUSTOM_OPTS="-f libbuild/pom.xml -DskipTests=true -Dyacy.port=80 -Dyacy.admin.passwd=MD5:1029a0355adffb6378ceed9936ca9be4 -Dyacy.staticIP=your_app_name.herokuapp.com"` (fill `yacy.staticIP` and `yacy.admin.passwd` properties as described in the previous paragraph)
 - OR only set minimum required : `heroku config:set MAVEN_CUSTOM_OPTS="-f libbuild/pom.xml -DskipTests=true` and modify locally the defaults/yacy.init file (properties `port`, `staticIP` and `adminAccountBase64MD5`)
- Initialize a git repository : `git init`
- Add remote heroku git repository for this deployment : `heroku git:remote -a your_app_name`
- Add files to git index : `git add .`
- Commit : `git commit`
- Push to heroku : `git push heroku master`
- Open app on your browser  : `heroku open -a your_app_name` 

## Deploy with GitHub account

- Log in on [Heroku](https://www.heroku.com/)
- Click the 'New > Create new app' button
- Eventually choose your app name and your region, then click 'Create App'
- Go to the 'Settings' tab
- Click the 'Reveal Config Vars' button, and add a var with KEY `MAVEN_CUSTOM_OPTS` and value filled as described in the previous paragraph
- Go to the 'Deploy' tab
- Choose 'GitHub' deployment method
- Enter your GitHub account information
- Select your YaCy repository clone and 'Connect'
- Choose either automatic or manual deploy, and click the deploy button
- If everything went fine, you can open YaCy search page with the 'View' button
 

## Technical details

### Heroku specific configuration files :

- [app.json](app.json) : used when deploying with the button
- [.env](.env) : set up environment variables for local heroku run
- [Procfile](Procfile) : contain main process description used to launch YaCy

### Custom maven options

With any of the deployment methods described, setting the option `-f libbuild/pom.xml -DskipTests=true` in the `MAVEN_CUSTOM_OPTS` environment variable is the minimum required for a successfull build and deploy. If not set, build will fail because missing dependent submodules from libbuild directory.

What'smore, the only way for other YaCy peers to reach a peer running on Heroku is to use the "dyno" public URL (in the form of your_app_name.herokuapp.com). This is why `-Dyacy.port=80` and `-Dyacy.staticIP=your_app_name.herokuapp.com` options have to be set in the `MAVEN_CUSTOM_OPTS` variable, or else your YaCy peer will run in "junior" mode. These options are used to customise `port` and `staticIP` initial properties in the [yacy.init](defaults/yacy.init) file at build.

### HTTP local port

On heroku platform, you can not choose your application binding port. It is set by Heroku in the `PORT` environment variable, and at startup, the application has to bind to this port within 90 seconds. So YaCy has to bind to this port each time the "dyno" is started, and thus can not rely on its normal configuration file. This is done with the JVM system property `net.yacy.server.localPort` set in Procfile.