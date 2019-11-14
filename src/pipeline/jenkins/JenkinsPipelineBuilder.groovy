package pipeline.jenkins



import pipeline.jenkins.GenericPipeline
import pipeline.jenkins.PipelineBranchDevelop
import pipeline.jenkins.PipelineBranchRelease
// import pipeline.jenkins.PipelineBranchMaster


def pipelineSteps;

// Constructor, called from PipelineBootstrap.createBuilder().
void initialize() {
    echo 'Initializing PipelineBuilder.'
    pipelineSteps = new JenkinsPipelineSteps()
    pipelineSteps.initialize();
}


String getRepoName() {
    if (env.gitlabSourceRepoName != null){
        repoName = env.gitlabSourceRepoName
    } else {
        repoName = 'gedoss-api'
    }
    return repoName
}

String getBranch() {
    String branchName;
    // When Gitlab triggers the build, we can read the source branch from gitlab.
    if (env.gitlabSourceBranch != null) {
        branchName = env.gitlabSourceBranch        
    } else {        
        branchName = sh(returnStdout: true, script: "git branch --list |grep '*' |cut -d ' ' -f2").toString().trim()    
        }
    echo 'Building branch \'' + branchName + '\'.'
    return branchName;
}


String getSourceRepoURL() {
    String sourceRepoURL;
    // When Gitlab triggers the build, we can read the source branch from gitlab.
    if (env.gitlabSourceRepoURL != null) {
        sourceRepoURL = env.gitlabSourceRepoURL
        echo 'Gitlab repo URL: ' + env.gitlabSourceRepoURL
    } else {        
        sourceRepoURL = sh(returnStdout: true, script: "git config --get remote.origin.url").toString().trim()
    }
    echo 'Building Repo \'' + sourceRepoURL + '\'.'    
    return sourceRepoURL;
}

String getArtifact() {
    def (artifact, version, finalName, pack) = pipelineSteps.readPom()
    if (finalName == null){
        artifact = env.WORKSPACE+'/target/'+ finalName + '.' + pack
    } else {
        artifact = env.WORKSPACE+'/target/'+ artifact + '-' + version + '.' + pack
    }
    return [artifact, version]
}

// Merge request
String getTargetBranch() {
    String targetBranch;
    // When Gitlab triggers the build, we can read the source branch from gitlab.
    if (env.gitlabTargetBranch != null) {
        targetBranch = env.gitlabTargetBranch
        echo 'Gitlab target branch: ' + targetBranch
    } else {        
        targetBranch = null
    }    
    return targetBranch;
}

void createBuilder() {
    branch = getBranch()
    println 'Branch name'
    println branch
    if (getRepoName().contains('commons')) {
        def pipeline
        pipeline = new PipelineJavaLibrary()
        pipeline.mavenPipeline(branch, getSourceRepoURL(), nodeLabel='')    
    }
    target = getTargetBranch() 
    def pipeline
    // Different pipeline for develop and stage branch
    switch(target) {
        case 'develop':
            println 'Building develop branch'
            pipeline = new PipelineBranchDevelop()
            pipeline.mavenPipeline('develop', getSourceRepoURL(), nodeLabel='')
            break
        case 'release':
            println 'Building release branch'
            pipeline = new PipelineBranchRelease()
            pipeline.mavenPipeline('release', getSourceRepoURL(), nodeLabel='')
            break
        default:
            println "Building ${branch} branch"
            pipeline = new GenericPipeline()
            pipeline.mavenPipeline(branch, getSourceRepoURL(), nodeLabel='', getTargetBranch())
            break
        }
}


void mavenApplicationPipeline() {
    createBuilder()
}

// Return the contents of this script as object so it can be re-used in Jenkinsfiles.
return this;



