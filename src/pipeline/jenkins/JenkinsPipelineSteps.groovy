package pipeline.jenkins

import pipeline.jenkins.Constants
import groovy.json.JsonSlurper
import groovy.util.XmlSlurper


// Constructor, called from PipelineBuilder.initialize().
void initialize() {
    echo 'Initializing PipelineSteps.'
}


void cleanWorkspace() {
    sh "echo 'Cleaning workspace'"
    deleteDir()
}


void checkout(String branch, String repoURL) {
    deleteDir()
    checkout changelog: true, poll: true, scm: [
            $class           : 'GitSCM',
            branches         : [[name: "origin/${branch}"]],
            browser          : [$class: 'GitLab', repoUrl: repoURL , version: Constants.GITLAB_VERSION ],
            userRemoteConfigs: [[credentialsId: Constants.GITLAB_CREDENTIALS_ID, url: repoURL ]]
    ]
}


String getCommitId() {
    if (env.gitlabAfter != null) {
        def String commitId = env.gitlabAfter
    }
    else {
        def String commitId = sh(returnStdout: true, script: "git rev-parse HEAD").trim()
    }    
    return commitId;
}

String userInfo() {
    if (env.gitlabUserName != null) {
        def String userName = env.gitlabUserName
    }
    else {
        def String userName = sh(returnStdout: true, script: "git log -1 --pretty=format:'%an' | xarg").trim()
    }
    if (env.gitlabUserEmail != null) {
        def String userEmail = gitlabUserEmail
    }
    else {
        def String userEmail = sh(returnStdout: true, script: "git log -1 --pretty=format:'%ae' | xarg").trim()
    }
    return [userName, userEmail]
}

String artifactPath(String artifactID, String version, String pack) {
    finalName = null
    try {
        xmlparser = new XmlSlurper().parse(new File("pom.xml"))
        finalName = xmlparser.build.finalName
    } catch(Exception) { def finalName = null }
    if (finalName == null) {
        path = env.WORKSPACE + '/target/' + artifactID + '-' + version + '.' + pack 
    } else {
        path = env.WORKSPACE + '/target/' + finalName + + pack 
    }
    return path
}

void email() {
    emailext attachLog: true, body: '${BUILD_DISPLAY_NAME} : ${BUILD_STATUS}',  mimeType: 'text/html',             
             compressLog: true, recipientProviders: [developers(), upstreamDevelopers()], subject: "${env.JOB_BASE_NAME} - Build ${env.BUILD_DISPLAY_NAME} - ${currentBuild.currentResult}"
}

void teams(String user, String address) {
    office365ConnectorSend message: '"${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>)"', 
                        status: '${currentBuild.currentResult}', webhookUrl: Constants.TEAMS_WEBHOOK
}

void setBuildName(String version, String branch) {
    currentBuild.displayName = version
    if (branch != 'master') {
        currentBuild.displayName = "# ${env.BUILD_NUMBER} - branch: ${branch}"
    }
}


void runMavenBuild(upload=false) {
    // Apache Maven related side notes:
    // --batch-mode : recommended in CI to inform maven to not run in interactive mode (less logs)
    // -V : strongly recommended in CI, will display the JDK and Maven versions in use.
    //      Very useful to be quickly sure the selected versions were the ones you think.
    // -U : force maven to update snapshots each time (default : once an hour, makes no sense in CI).
    // -Dsurefire.useFile=false : useful in CI. Displays test errors in the logs directly (instead of
    //                            having to crawl the workspace files to see the cause).
    if (upload) {
        sh "'${Constants.MVN}' -B -V -U -DskipTests=true clean install verify deploy"    
    } else {
        sh "'${Constants.MVN}' -B -V -U -DskipTests=true clean install verify"
    }
}

void runUnitTest() { 
    sh "'${Constants.MVN}' -B -V -U -DskipITs=true clean verify"
    runSonarAnalysis() 
    archiveTestResults()
}

void runIntegrationTest() { 
    sh "'${Constants.MVN}' -B -V -U -DskipUTs=true clean verify"
    archiveTestResults()
}

void archiveTestResults() {
    step([$class: 'JUnitResultArchiver', testResults: '**/target/**/TEST*.xml, **/target/failsafe-reports/**.xml', allowEmptyResults: true])
   
}

void archiveArtifact(pack) {
    archiveArtifacts([artifacts: "target/*.${pack}", allowEmptyArchive: true, caseSensitive: false, onlyIfSuccessful: true])
}

void archiveHTML() {
    publishHTML([allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true, reportDir: './target/site/jacoco/', reportFiles: 'index.html', reportName: 'Cobertura', reportTitles: ''])
}

void runJunitWithSonarAnalysis(String projectKey, String projectVersion) {    
    println 'Running Junit and Sonar analysis';
    withSonarQubeEnv('SONAR') {
    sh " ${Constants.MVN} -B -V -U -DskipITs=true clean verify sonar:sonar -f pom.xml -Dsonar.projectKey=${projectKey} -Dsonar.projectVersion=${projectVersion} -Dsonar.language=java \
        -Dsonar.sources=src/main/webapp,pom.xml,src/main/java -Dsonar.tests=src/test/java \
        -Dsonar.java.binaries=target/classes -Dsonar.test.inclusions=**/*Test*/** -Dsonar.exclusions=**/*Test*/**"        
    }
       
    sonarQualityGate()
}


void runSonarAnalysis(String projectKey, String projectVersion) {    
    println 'Running Sonar analysis';
    withSonarQubeEnv('SONAR') {
    sh " ${Constants.MVN} -B -V -U -DskipTests=true clean verify sonar:sonar -f pom.xml -Dsonar.projectKey=${projectKey} -Dsonar.projectVersion=${projectVersion} -Dsonar.language=java \
        -Dsonar.sources=src/main/webapp,pom.xml,src/main/java -Dsonar.tests=src/test/java \
        -Dsonar.java.binaries=target/classes -Dsonar.test.inclusions=**/*Test*/** -Dsonar.exclusions=**/*Test*/**"        
    }
       
    sonarQualityGate()
}

void sonarQualityGate() {    
    sleep (75)
    timeout(1) {
        waitForQualityGate abortPipeline: true
    }
}

void checkJbossConfig(String serverGroup) {
    sleep(10)
    println 'Checking JBOSS datasource and server group state'
}


void deploy(String artifact, String env, String version, String group) {
    // TODO: Create deploy job    
    build job: 'deploy', parameters: [
             string(name: 'package', value: artifact), 
             string(name: 'environment', value: env), 
             string(name: 'version', value: version), 
             string(name: 'server-group', value: group)], wait: true
}

void securityTest() {
    echo 'Running security tests with OWASP ZAP'
    echo '...'
    echo 'WIP.'
}
 
void commentMergeRequest(artifactID) {
    addGitLabMRComment comment: "Build ${env.BUILD_NUMBER} - ${currentBuild.currentResult} \n [Sonar Result](${Constants.SONAR_URL}/dashboard/index/${artifactID}) \n More details: (jenkins)[${env.BUILD_URL}]"
}


@NonCPS
def parseJsonText(String jsonText) {
    final slurper = new JsonSlurper()
    return slurper.parseText(jsonText)
}


// Return the contents of this script as object so it can be re-used in Jenkinsfiles.
return this;
