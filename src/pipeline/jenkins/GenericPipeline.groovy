package pipeline.jenkins


import pipeline.jenkins.JenkinsPipelineSteps

// same as BranchGeneric but with maven deploy goal
void initialize() {
    echo 'Initializing PipelineJenkinsDevelop.'
}

void mavenPipeline(String branch, String url, String nodeLabel='', String targetBranch=null) {
    def packaging
    pipelineSteps = new JenkinsPipelineSteps()
    node(nodeLabel) {
            try {
            properties([
                // Set number to keep Jenkins build history
                buildDiscarder(logRotator(daysToKeepStr: '45', artifactDaysToKeepStr: '15')),
                ])
            updateGitlabCommitStatus name: 'jenkins', state: 'running'
            stage('Checkout') {
                // pipelineSteps.cleanWorkspace()
                pipelineSteps.checkout(branch, url)
                script { 
                    artifactID = readMavenPom().getArtifactId()
                    version = readMavenPom().getVersion()
                    packaging = readMavenPom().getPackaging()
                }

            }

            stage('Configure') {
                pipelineSteps.setBuildName(version, branch)
            } 
            stage('Build') {
                pipelineSteps.runMavenBuild()
            }
            stage ('JUnit & Sonar') {                
                pipelineSteps.runJunitWithSonarAnalysis(artifactID, version)
            }

    } finally {
        stage ('Report') {
            pipelineSteps.email()
            // pipelineSteps.teams()        
            if (currentBuild.currentResult == 'SUCCESS') {
                pipelineSteps.archiveArtifact(packaging)
                updateGitlabCommitStatus name: 'jenkins', state: 'success'
                echo 'Target Branch'
                echo targetBranch
                if (targetBranch == 'develop') {
                    pipelineSteps.commentMergeRequest(artifactID)
                }
            } else {
                updateGitlabCommitStatus name: 'jenkins', state: 'failed'
                }
            }
    } 
    }
}

// Return the contents of this script as object so it can be re-used in Jenkinsfiles.
return this;